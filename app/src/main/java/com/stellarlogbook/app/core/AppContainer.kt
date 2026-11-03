package com.stellarlogbook.app.core

import android.content.Context
import androidx.room.Room
import com.stellarlogbook.app.BuildConfig
import com.stellarlogbook.app.data.local.AppDatabase
import com.stellarlogbook.app.data.local.ExplorerStore
import com.stellarlogbook.app.data.local.RoomExplorerStore
import com.stellarlogbook.app.data.prefs.UserPrefs
import com.stellarlogbook.app.data.remote.EdsmApi
import com.stellarlogbook.app.data.remote.JournalParser
import com.stellarlogbook.app.data.repo.ExplorerRepository
import com.stellarlogbook.app.data.sync.EdsmSyncEngine
import com.stellarlogbook.app.data.sync.JournalImporter
import com.stellarlogbook.app.data.sync.SyncCredentials
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Manual DI container for the scaffold phase. Hilt replaces this in Phase 1
 * once the graph grows. Workers and ViewModels obtain it via [container].
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "StellarLogbook/0.1 (Android) EDSM companion")
                .build()
            chain.proceed(request)
        }
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            }
        }
        .build()

    val api: EdsmApi = Retrofit.Builder()
        .baseUrl("https://www.edsm.net/")
        .client(httpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(EdsmApi::class.java)

    val db: AppDatabase =
        Room.databaseBuilder(appContext, AppDatabase::class.java, AppDatabase.NAME).build()

    val store: ExplorerStore = RoomExplorerStore(db)

    val userPrefs: UserPrefs = UserPrefs(appContext)
    val journalParser: JournalParser = JournalParser(json)
    val repository: ExplorerRepository = ExplorerRepository(db, userPrefs)

    private val credentialsProvider = object : SyncCredentials {
        override suspend fun get(): SyncCredentials.Credentials? {
            val commander = userPrefs.commanderName.first()?.takeIf { it.isNotBlank() } ?: return null
            val key = userPrefs.apiKey()?.takeIf { it.isNotBlank() } ?: return null
            return SyncCredentials.Credentials(commander, key)
        }
    }

    val syncEngine: EdsmSyncEngine = EdsmSyncEngine(api, credentialsProvider, store)
    val journalImporter: JournalImporter = JournalImporter(journalParser, store)
}

fun Context.container(): AppContainer =
    (applicationContext as com.stellarlogbook.app.StellarLogbookApp).container
