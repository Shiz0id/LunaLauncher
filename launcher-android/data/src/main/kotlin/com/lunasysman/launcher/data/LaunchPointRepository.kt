package com.lunasysman.launcher.data

import com.lunasysman.launcher.core.model.LaunchPoint
import com.lunasysman.launcher.core.model.LaunchPointType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.room.withTransaction

class LaunchPointRepository(
    private val database: LauncherDatabase,
    private val dao: LaunchPointDao,
    private val dockDao: DockDao,
    private val homeSlotsDao: HomeSlotsDao,
    private val homeIconPositionsDao: HomeIconPositionsDao,
) {
    fun observeVisibleLaunchPoints(): Flow<List<LaunchPointRecord>> =
        dao.observeVisible().map { list -> list.map { it.toRecord() } }

    fun observeDockEntries(): Flow<List<DockEntryEntity>> = dockDao.observeDock()

    fun observeHomeSlots(): Flow<List<HomeSlotEntity>> = homeSlotsDao.observeHomeSlots()

    fun observeHomeIconPositions(): Flow<List<HomeIconEntity>> = homeIconPositionsDao.observeAll()

    suspend fun getHomeSlots(): List<HomeSlotEntity> = homeSlotsDao.getHomeSlots()

    suspend fun getHomeIconPositions(): List<HomeIconEntity> = homeIconPositionsDao.getAll()

    suspend fun upsertHomeIconPosition(entry: HomeIconEntity) {
        homeIconPositionsDao.upsert(entry)
    }

    suspend fun removeHomeIcon(launchPointId: String) {
        homeIconPositionsDao.deleteByLaunchPointId(launchPointId)
    }

    suspend fun updateHomeIconPosition(launchPointId: String, xNorm: Double, yNorm: Double, rotationDeg: Float, nowEpochMs: Long) {
        database.withTransaction {
            val all = homeIconPositionsDao.getAll()
            // Preserve existing zIndex if present, otherwise use maxZ + 1 for new entries.
            // This prevents unnecessary zIndex inflation on position updates.
            val existingEntry = all.find { it.launchPointId == launchPointId }
            val zIndex = existingEntry?.zIndex ?: ((all.maxOfOrNull { it.zIndex } ?: 0) + 1)

            homeIconPositionsDao.upsert(
                HomeIconEntity(
                    launchPointId = launchPointId,
                    xNorm = xNorm.coerceIn(0.0, 1.0),
                    yNorm = yNorm.coerceIn(0.0, 1.0),
                    rotationDeg = rotationDeg,
                    zIndex = zIndex,
                    updatedAtEpochMs = nowEpochMs,
                ),
            )
        }
    }

    suspend fun upsertAll(items: List<LaunchPoint>) {
        // Not used for launcher core sync; reserved for future multi-runtime producers.
        dao.upsertAll(items.map { it.toEntityForInsert() })
    }

    suspend fun syncAndroidApps(items: List<LaunchPoint>) {
        val android = items.filter { it.type == LaunchPointType.ANDROID_APP }
        val ids = android.map { it.id }
        if (ids.isEmpty()) return

        database.withTransaction {
            val existingById = dao.getByIds(ids).associateBy { it.id }
            val merged = android.map { scanned ->
                val existing = existingById[scanned.id]
                val (pkg, activity) = parseAndroidIdOrNull(scanned.id) ?: (null to null)

                LaunchPointEntity(
                    id = scanned.id,
                    type = LaunchPointType.ANDROID_APP.name,
                    title = scanned.title,
                    sortKey = scanned.title.lowercase(),
                    iconKey = scanned.iconKey,
                    installSource = existing?.installSource ?: "ANDROID",
                    badges = existing?.badges ?: 0,
                    androidPackageName = pkg ?: existing?.androidPackageName,
                    androidActivityName = activity ?: existing?.androidActivityName,
                    lastLaunchedAtEpochMs = existing?.lastLaunchedAtEpochMs,
                    pinned = existing?.pinned ?: false,
                    pinnedRank = existing?.pinnedRank,
                    hidden = existing?.hidden ?: false,
                )
            }

            dao.deleteMissingOfType(LaunchPointType.ANDROID_APP.name, ids)
            val changedOrNew = merged.filter { candidate -> existingById[candidate.id] != candidate }
            if (changedOrNew.isNotEmpty()) {
                dao.upsertAll(changedOrNew)
            }
        }
    }

    suspend fun markLaunched(id: String, epochMs: Long) {
        dao.setLastLaunchedAt(id, epochMs)
    }

    suspend fun setPinned(id: String, pinned: Boolean) {
        dao.setPinned(id, pinned)
    }

    suspend fun ensurePinnedRank(id: String, epochMs: Long) {
        val existing = dao.getByIds(listOf(id)).firstOrNull() ?: return
        if (existing.pinnedRank == null) {
            dao.setPinnedRank(id, epochMs)
        }
    }

    suspend fun setHidden(id: String, hidden: Boolean) {
        dao.setHidden(id, hidden)
    }

    suspend fun addToDock(launchPointId: String, maxSlots: Int = 5) {
        database.withTransaction {
            // If it already exists in home/dock, remove first (move semantics).
            dockDao.deleteByLaunchPointId(launchPointId)
            homeIconPositionsDao.deleteByLaunchPointId(launchPointId)

            val current = dockDao.getDock()
            if (current.size >= maxSlots) return@withTransaction
            val nextPos = (current.maxOfOrNull { it.position } ?: -1) + 1
            dockDao.upsert(DockEntryEntity(position = nextPos, launchPointId = launchPointId))
        }
    }

    suspend fun removeFromDock(launchPointId: String) {
        database.withTransaction {
            dockDao.deleteByLaunchPointId(launchPointId)
        }
    }

    suspend fun placeInHomeAbsolute(
        launchPointId: String,
        xNorm: Double,
        yNorm: Double,
        rotationDeg: Float = 0f,
        nowEpochMs: Long,
    ) {
        database.withTransaction {
            // Move semantics: remove from other placements.
            dockDao.deleteByLaunchPointId(launchPointId)
            homeIconPositionsDao.deleteByLaunchPointId(launchPointId)

            val maxZ = homeIconPositionsDao.getAll().maxOfOrNull { it.zIndex } ?: 0
            homeIconPositionsDao.upsert(
                HomeIconEntity(
                    launchPointId = launchPointId,
                    xNorm = xNorm.coerceIn(0.0, 1.0),
                    yNorm = yNorm.coerceIn(0.0, 1.0),
                    rotationDeg = rotationDeg,
                    zIndex = maxZ + 1,
                    updatedAtEpochMs = nowEpochMs,
                ),
            )
        }
    }

    suspend fun removeFromHome(launchPointId: String) {
        database.withTransaction {
            homeIconPositionsDao.deleteByLaunchPointId(launchPointId)
        }
    }
}

private fun LaunchPointEntity.toRecord(): LaunchPointRecord =
    LaunchPointRecord(
        id = id,
        type = LaunchPointType.valueOf(type),
        title = title,
        iconKey = iconKey,
        pinned = pinned,
        hidden = hidden,
        lastLaunchedAtEpochMs = lastLaunchedAtEpochMs,
        sortKey = sortKey,
        pinnedRank = pinnedRank,
        installSource = installSource,
        badges = badges,
        androidPackageName = androidPackageName,
        androidActivityName = androidActivityName,
    )

private fun LaunchPoint.toEntityForInsert(): LaunchPointEntity =
    LaunchPointEntity(
        id = id,
        type = type.name,
        title = title,
        sortKey = title.lowercase(),
        iconKey = iconKey,
        installSource = null,
        badges = 0,
        androidPackageName = null,
        androidActivityName = null,
        lastLaunchedAtEpochMs = lastLaunchedAtEpochMs,
        pinned = pinned,
        pinnedRank = null,
        hidden = hidden,
    )

private fun parseAndroidIdOrNull(id: String): Pair<String, String>? {
    val base = id.substringBefore("@")
    if (!base.startsWith("android:")) return null
    val remainder = base.removePrefix("android:")
    val split = remainder.split("/", limit = 2)
    if (split.size != 2) return null
    val pkg = split[0].trim()
    val activity = split[1].trim()
    if (pkg.isEmpty() || activity.isEmpty()) return null
    return pkg to activity
}
