package com.rutina.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activities WHERE date BETWEEN :from AND :to ORDER BY date, startMinute")
    fun observeRange(from: Long, to: Long): Flow<List<PlannedActivity>>

    @Query("SELECT * FROM activities WHERE date BETWEEN :from AND :to ORDER BY date, startMinute")
    suspend fun getRange(from: Long, to: Long): List<PlannedActivity>

    @Query("SELECT * FROM activities WHERE date >= :from")
    suspend fun getFrom(from: Long): List<PlannedActivity>

    @Query("SELECT * FROM activities WHERE id = :id")
    suspend fun getById(id: Long): PlannedActivity?

    @Insert
    suspend fun insert(activity: PlannedActivity): Long

    @Insert
    suspend fun insertAll(activities: List<PlannedActivity>): List<Long>

    @Update
    suspend fun update(activity: PlannedActivity)

    @Delete
    suspend fun delete(activity: PlannedActivity)
}

@Dao
interface ReflectionDao {
    @Query("SELECT * FROM reflections ORDER BY weekStart DESC")
    fun observeAll(): Flow<List<Reflection>>

    @Query("SELECT * FROM reflections ORDER BY weekStart DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<Reflection>

    @Upsert
    suspend fun upsert(reflection: Reflection)
}

@Database(
    entities = [PlannedActivity::class, Reflection::class, PenaltyRule::class, AppBlock::class],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun activityDao(): ActivityDao
    abstract fun reflectionDao(): ReflectionDao
    abstract fun penaltyDao(): PenaltyDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `reflections` (`weekStart` INTEGER NOT NULL, `text` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`weekStart`))",
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `penalty_rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`trigger` TEXT NOT NULL, `packages` TEXT NOT NULL, `appLabels` TEXT NOT NULL, `minutes` INTEGER NOT NULL, " +
                        "`challenge` TEXT NOT NULL, `steps` INTEGER NOT NULL, `topics` TEXT NOT NULL, `exercise` TEXT NOT NULL, " +
                        "`enabled` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `app_blocks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`ruleId` INTEGER NOT NULL, `activityId` INTEGER NOT NULL, `packageName` TEXT NOT NULL, " +
                        "`appLabel` TEXT NOT NULL, `reason` TEXT NOT NULL, `until` INTEGER NOT NULL, `challenge` TEXT NOT NULL, " +
                        "`stepsRequired` INTEGER NOT NULL, `topics` TEXT NOT NULL, `exercise` TEXT NOT NULL, `question` TEXT NOT NULL, " +
                        "`stepBaseline` INTEGER NOT NULL, `stepsDone` INTEGER NOT NULL, `solved` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)",
                )
            }
        }

        /** El tiempo de castigo pasa a contarse como tiempo restante (inmune a apagar el móvil o cambiar la hora). */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `app_blocks` ADD COLUMN `remainingMs` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE `app_blocks` SET `remainingMs` = MAX(0, `until` - ${System.currentTimeMillis()})")
            }
        }

        /** Retos nuevos (lugar, QR, conteo, doble reto...): sus ajustes y su avance. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `penalty_rules` ADD COLUMN `options` TEXT NOT NULL DEFAULT ''")
                listOf(
                    "`options` TEXT NOT NULL DEFAULT ''",
                    "`stage` INTEGER NOT NULL DEFAULT 0",
                    "`progress` INTEGER NOT NULL DEFAULT 0",
                    "`stageData` TEXT NOT NULL DEFAULT ''",
                    "`windowStart` INTEGER NOT NULL DEFAULT 0",
                    "`windowEnd` INTEGER NOT NULL DEFAULT 0",
                    "`verdict` TEXT NOT NULL DEFAULT ''",
                    "`addedMs` INTEGER NOT NULL DEFAULT 0",
                ).forEach { db.execSQL("ALTER TABLE `app_blocks` ADD COLUMN $it") }
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "rutina.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
    }
}
