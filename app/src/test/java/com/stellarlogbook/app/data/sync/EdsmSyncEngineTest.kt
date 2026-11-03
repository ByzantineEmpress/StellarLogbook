package com.stellarlogbook.app.data.sync

import com.stellarlogbook.app.data.remote.EdsmApi
import com.stellarlogbook.app.testutil.FakeExplorerStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.time.Instant

class EdsmSyncEngineTest {

    private lateinit var server: MockWebServer
    private lateinit var api: EdsmApi
    private lateinit var store: FakeExplorerStore

    private val credentials = object : SyncCredentials {
        override suspend fun get() = SyncCredentials.Credentials("TEST CMDR", "test-key")
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(EdsmApi::class.java)
        store = FakeExplorerStore()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun entry(day: Int, i: Int): String {
        val d = day.toString().padStart(2, '0')
        val h = (i % 24).toString().padStart(2, '0')
        val m = (i % 60).toString().padStart(2, '0')
        return """{"system":"Sys$day-$i","date":"2026-10-$d $h:$m:00","coordinates":{"x":1.0,"y":2.0,"z":3.0}}"""
    }

    private fun page(day: Int, count: Int): String =
        """{"msgnum":100,"msg":"OK","logs":[${(0 until count).joinToString(",") { entry(day, it) }}]}"""

    @Test
    fun pagesThroughHistoryAndStoresCheckpoint() = runTest {
        server.enqueue(MockResponse().setBody(page(1, 100)))
        server.enqueue(MockResponse().setBody(page(2, 100)))
        server.enqueue(MockResponse().setBody(page(3, 50)))

        val engine = EdsmSyncEngine(api, credentials, store)
        val result = engine.sync().getOrThrow()

        assertEquals(3, result.pages)
        assertEquals(250, result.entries)
        assertEquals(250, store.systems.size)
        assertEquals(250, store.jumps.size)
        assertNotNull(store.checkpoints[EdsmSyncEngine.SOURCE]?.cursor)

        // Drain the three requests of the first sync.
        repeat(3) { server.takeRequest() }

        // Second sync: no new data, resumes from checkpoint.
        server.enqueue(MockResponse().setBody("""{"msgnum":100,"msg":"OK","logs":[]}"""))
        val second = engine.sync().getOrThrow()
        assertEquals(0, second.entries)
        assertEquals(0, second.pages)

        val resumeRequest = server.takeRequest().requestUrl!!
        val checkpoint = store.checkpoints[EdsmSyncEngine.SOURCE]!!.cursor!!
        assertEquals(checkpoint, resumeRequest.queryParameter("startDateTime"))
    }

    @Test
    fun mergesRepeatVisits() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"msgnum":100,"msg":"OK","logs":[
                    {"system":"Sol","date":"2026-10-01 10:00:00","coordinates":{"x":0,"y":0,"z":0}},
                    {"system":"Sol","date":"2026-10-02 12:00:00","coordinates":{"x":0,"y":0,"z":0}}
                ]}"""
            )
        )
        EdsmSyncEngine(api, credentials, store).sync().getOrThrow()

        val sol = store.systems["Sol"]!!
        assertEquals(2, sol.visitCount)
        assertEquals(Instant.parse("2026-10-01T10:00:00Z").toEpochMilli(), sol.firstVisitedAt)
        assertEquals(Instant.parse("2026-10-02T12:00:00Z").toEpochMilli(), sol.lastVisitedAt)
        assertTrue(sol.knownToCommunity)
        assertEquals(2, store.jumps.size)
    }

    @Test
    fun msgnumErrorFailsCleanly() = runTest {
        server.enqueue(MockResponse().setBody("""{"msgnum":203,"msg":"Commander name/API Key not found"}"""))
        val result = EdsmSyncEngine(api, credentials, store).sync()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("203"))
        assertTrue(store.systems.isEmpty())
    }

    @Test
    fun stopsWhenServerMakesNoProgress() = runTest {
        val body = page(1, 100)
        repeat(5) { server.enqueue(MockResponse().setBody(body)) }

        EdsmSyncEngine(api, credentials, store).sync().getOrThrow()

        // Same page repeated: engine stops after detecting no forward progress.
        assertTrue(server.requestCount <= 2)
        assertEquals(100, store.systems.size)
        assertEquals(2, store.systems.values.first().visitCount)
        assertEquals(100, store.jumps.size)
    }

    @Test
    fun missingCredentialsFailEarly() = runTest {
        val noCreds = object : SyncCredentials {
            override suspend fun get(): SyncCredentials.Credentials? = null
        }
        val result = EdsmSyncEngine(api, noCreds, store).sync()
        assertTrue(result.isFailure)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun emptyWideRangeFallsBackToLast30Days() = runTest {
        // First request (start = 2014-12-01) returns msgnum 100 with no logs;
        // the fallback narrows the range to the last 30 days and finds entries.
        server.enqueue(MockResponse().setBody("""{"msgnum":100,"msg":"OK","logs":[]}"""))
        server.enqueue(
            MockResponse().setBody(
                """{"msgnum":100,"msg":"OK","logs":[
                    {"system":"Sol","date":"2026-10-01 10:00:00","coordinates":{"x":0,"y":0,"z":0}},
                    {"system":"Deciat","date":"2026-10-02 12:00:00","coordinates":{"x":1,"y":2,"z":3}}
                ]}"""
            )
        )

        val result = EdsmSyncEngine(api, credentials, store).sync().getOrThrow()
        assertEquals(2, result.entries)
        assertEquals(1, result.pages)
        assertEquals(2, store.systems.size)

        // Two requests were made; the second used a ~30-day-old startDateTime.
        val first = server.takeRequest().requestUrl!!
        val second = server.takeRequest().requestUrl!!
        assertTrue(first.queryParameter("startDateTime")!!.startsWith("2014-12-01"))
        val narrowStart = second.queryParameter("startDateTime")!!
        val narrowInstant = Instant.parse(narrowStart.replace(" ", "T") + "Z")
        val ageDays = java.time.Duration.between(narrowInstant, Instant.now()).toDays()
        assertTrue("narrow start was $narrowStart ($ageDays days ago)", ageDays in 28..31)

        // A checkpoint was stored so the next sync resumes from the newest entry.
        assertNotNull(store.checkpoints[EdsmSyncEngine.SOURCE]?.cursor)
    }
}
