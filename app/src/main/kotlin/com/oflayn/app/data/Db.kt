package com.oflayn.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction

@Entity(tableName = "stops") data class StopEntity(@PrimaryKey val id: String, val name: String, val lat: Double, val lon: Double)

@Entity(tableName = "routes")
data class RouteEntity(@PrimaryKey val id: String, val shortName: String, val longName: String?, val vehicleType: String, val stopIds: String)

/** One row (key = "network") describing where the installed dataset came from. */
@Entity(tableName = "dataset_meta")
data class DatasetMetaEntity(
    @PrimaryKey val key: String,
    val source: String, val sourceType: String, val url: String, val fetchedAtMs: Long,
    val verification: String, val stopCount: Int, val routeCount: Int, val attribution: String,
)

@Entity(tableName = "favorites", primaryKeys = ["kind", "refId"]) data class FavoriteEntity(val kind: String, val refId: String)

@Entity(tableName = "recent_trips") data class RecentTripEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val text: String, val atMs: Long)

@Dao
abstract class TransitDao {
    @Query("SELECT * FROM stops ORDER BY name") abstract suspend fun stops(): List<StopEntity>
    @Query("SELECT * FROM routes ORDER BY shortName") abstract suspend fun routes(): List<RouteEntity>
    @Query("SELECT * FROM dataset_meta WHERE `key` = 'network'") abstract suspend fun meta(): DatasetMetaEntity?

    @Query("DELETE FROM stops") abstract suspend fun clearStops()
    @Query("DELETE FROM routes") abstract suspend fun clearRoutes()
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertStops(items: List<StopEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun insertRoutes(items: List<RouteEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun putMeta(m: DatasetMetaEntity)

    /** Atomic: either the whole validated dataset replaces the old one or nothing changes. */
    @Transaction
    open suspend fun replaceNetwork(stops: List<StopEntity>, routes: List<RouteEntity>, meta: DatasetMetaEntity) {
        clearStops(); clearRoutes()
        insertStops(stops); insertRoutes(routes); putMeta(meta)
    }

    @Query("SELECT refId FROM favorites WHERE kind = :kind") abstract suspend fun favorites(kind: String): List<String>
    @Insert(onConflict = OnConflictStrategy.REPLACE) abstract suspend fun addFavorite(f: FavoriteEntity)
    @Query("DELETE FROM favorites WHERE kind = :kind AND refId = :refId") abstract suspend fun removeFavorite(kind: String, refId: String)

    @Query("SELECT text FROM recent_trips ORDER BY atMs DESC LIMIT 10") abstract suspend fun recentTrips(): List<String>
    @Insert abstract suspend fun addRecentTrip(t: RecentTripEntity)
}

@Database(
    entities = [StopEntity::class, RouteEntity::class, DatasetMetaEntity::class, FavoriteEntity::class, RecentTripEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): TransitDao

    companion object {
        fun create(ctx: Context): AppDb = Room.databaseBuilder(ctx, AppDb::class.java, "oflayn.db").build()
    }
}
