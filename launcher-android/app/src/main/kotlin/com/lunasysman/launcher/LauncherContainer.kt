package com.lunasysman.launcher

import android.content.Context
import com.lunasysman.launcher.apps.android.AndroidAppScanner
import com.lunasysman.launcher.data.DeckRepository
import com.lunasysman.launcher.data.LauncherDatabase
import com.lunasysman.launcher.data.LaunchPointRepository
import com.lunasysman.launcher.data.JustTypePrefs
import com.lunasysman.launcher.data.JustTypeRegistry
import com.lunasysman.launcher.core.justtype.notifications.NotificationIndexer
import com.lunasysman.launcher.deck.DeckBitmapCache
import com.lunasysman.launcher.deck.DeckWidgetHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LauncherContainer private constructor(
    val appContext: Context,
    val appScope: CoroutineScope,
    val database: LauncherDatabase,
    val repository: LaunchPointRepository,
    val scanner: AndroidAppScanner,
    val packageChangeHandler: PackageChangeHandler,
    val iconRepository: IconRepository,
    val justTypeRegistry: JustTypeRegistry,
    val notificationIndexer: NotificationIndexer,
    val deckRepository: DeckRepository,
    val deckWidgetHost: DeckWidgetHost,
    val deckBitmapCache: DeckBitmapCache,
) {
    companion object {
        fun create(context: Context): LauncherContainer {
            val appContext = context.applicationContext
            val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val database = LauncherDatabase.create(appContext)
            val repository = LaunchPointRepository(
                database = database,
                dao = database.launchPointDao(),
                dockDao = database.dockDao(),
                homeSlotsDao = database.homeSlotsDao(),
                homeIconPositionsDao = database.homeIconPositionsDao(),
            )
            val scanner = AndroidAppScanner(appContext)
            val packageChangeHandler = PackageChangeHandler(
                scope = appScope,
                scanner = scanner,
                repository = repository,
            )
            val iconRepository = IconRepository(appContext, scanner)
            val justTypePrefs = JustTypePrefs(appContext)
            val justTypeRegistry =
                JustTypeRegistry(
                    context = appContext,
                    dao = database.justTypeProviderDao(),
                    prefs = justTypePrefs,
                )
            appScope.launch {
                try {
                    justTypeRegistry.initialize()
                } catch (_: Exception) {
                    // Keep launcher usable even if registry init fails.
                }
            }
            // Get notification indexer from Application
            val notificationIndexer = (appContext as? LauncherApplication)?.notificationIndexer
                ?: NotificationIndexer()

            val deckRepository = DeckRepository(deckDao = database.deckDao())
            val deckWidgetHost = DeckWidgetHost(appContext)
            val deckBitmapCache = DeckBitmapCache()

            return LauncherContainer(
                appContext = appContext,
                appScope = appScope,
                database = database,
                repository = repository,
                scanner = scanner,
                packageChangeHandler = packageChangeHandler,
                iconRepository = iconRepository,
                justTypeRegistry = justTypeRegistry,
                notificationIndexer = notificationIndexer,
                deckRepository = deckRepository,
                deckWidgetHost = deckWidgetHost,
                deckBitmapCache = deckBitmapCache,
            )
        }
    }
}
