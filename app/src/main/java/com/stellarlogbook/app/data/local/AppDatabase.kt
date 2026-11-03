package com.stellarlogbook.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.stellarlogbook.app.data.local.dao.BodyDao
import com.stellarlogbook.app.data.local.dao.DiscoveryDao
import com.stellarlogbook.app.data.local.dao.JumpDao
import com.stellarlogbook.app.data.local.dao.SyncCheckpointDao
import com.stellarlogbook.app.data.local.dao.SystemDao
import com.stellarlogbook.app.data.local.entity.BodyEntity
import com.stellarlogbook.app.data.local.entity.DiscoveryEntity
import com.stellarlogbook.app.data.local.entity.JumpEntity
import com.stellarlogbook.app.data.local.entity.SyncCheckpointEntity
import com.stellarlogbook.app.data.local.entity.SystemEntity

@Database(
    entities = [
        SystemEntity::class,
        BodyEntity::class,
        JumpEntity::class,
        DiscoveryEntity::class,
        SyncCheckpointEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun systemDao(): SystemDao
    abstract fun bodyDao(): BodyDao
    abstract fun jumpDao(): JumpDao
    abstract fun discoveryDao(): DiscoveryDao
    abstract fun syncCheckpointDao(): SyncCheckpointDao

    companion object {
        const val NAME = "stellarlogbook.db"
    }
}
