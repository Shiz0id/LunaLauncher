package com.lunasysman.launcher

import android.content.Intent
import android.net.Uri
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.content.ContentUris
import android.app.SearchManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lunasysman.launcher.apps.android.AndroidAppScanner
import com.lunasysman.launcher.core.justtype.engine.JustTypeEngine
import com.lunasysman.launcher.core.justtype.model.JustTypeItemUi
import com.lunasysman.launcher.core.justtype.model.JustTypeUiState
import com.lunasysman.launcher.core.justtype.model.JustTypeCategory
import com.lunasysman.launcher.core.justtype.model.JustTypeNotificationsOptions
import com.lunasysman.launcher.core.justtype.registry.JustTypeProviderConfig
import com.lunasysman.launcher.core.justtype.notifications.NotificationIndexer
import com.lunasysman.launcher.core.model.LaunchPoint
import com.lunasysman.launcher.core.model.LaunchPointAction
import com.lunasysman.launcher.core.model.LaunchPointType
import com.lunasysman.launcher.core.model.HomeIconPlacement
import com.lunasysman.launcher.data.LaunchPointRepository
import com.lunasysman.launcher.data.LaunchPointRecord
import com.lunasysman.launcher.data.JustTypeRegistry
import com.lunasysman.launcher.data.HomeIconEntity
import java.net.URLEncoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LauncherViewModel(
    private val repository: LaunchPointRepository,
    private val scanner: AndroidAppScanner,
    private val appContext: Context,
    private val justTypeRegistry: JustTypeRegistry,
    private val notificationIndexer: NotificationIndexer,
) : ViewModel() {
    private val homeGridSlotCount = 7 * 9

    private var initialScanTriggered: Boolean = false

    private val allItems: StateFlow<List<LaunchPointRecord>> =
        repository.observeVisibleLaunchPoints()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val appsItems: StateFlow<List<LaunchPoint>> =
        allItems
            .map { list -> list.sortedWith(compareBy<LaunchPointRecord> { it.sortKey }.thenBy { it.id }) }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favoritesItems: StateFlow<List<LaunchPoint>> =
        allItems
            .map { list ->
                list.asSequence()
                    .filter { it.pinned }
                    .sortedWith(compareBy<LaunchPointRecord> { it.pinnedRank ?: Long.MAX_VALUE }.thenBy { it.sortKey })
                    .toList()
            }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val dockItems: StateFlow<List<LaunchPoint>> =
        combine(
            repository.observeDockEntries(),
            allItems,
        ) { dock, all ->
            val byId = all.associateBy { it.id }
            dock.sortedBy { it.position }
                .mapNotNull { byId[it.launchPointId] }
                .take(5)
        }.distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val homeIcons: StateFlow<List<Pair<LaunchPoint, HomeIconPlacement>>> =
        combine(
            repository.observeHomeIconPositions(),
            allItems,
        ) { positions, all ->
            val byId = all.associateBy { it.id }
            positions
                .mapNotNull { p ->
                    val lp = byId[p.launchPointId] ?: return@mapNotNull null
                    lp to p.toPlacement()
                }
                .sortedWith(
                    compareBy<Pair<LaunchPoint, HomeIconPlacement>> { it.second.zIndex }
                        .thenBy { it.second.updatedAtEpochMs }
                        .thenBy { it.first.title.lowercase() }
                        .thenBy { it.first.id },
                )
        }.distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    /** True when any search panel (home or deck) is visible. */
    private val _searchActive = MutableStateFlow(false)

    private val _events = MutableSharedFlow<LauncherEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<LauncherEvent> = _events.asSharedFlow()

    private val _contactsPermissionGranted =
        MutableStateFlow(hasContactsPermission(appContext))

    fun setContactsPermissionGranted(granted: Boolean) {
        _contactsPermissionGranted.value = granted
    }

    private val rawProviderConfigs: StateFlow<List<JustTypeProviderConfig>> =
        justTypeRegistry.providers
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val providerConfigs: StateFlow<List<JustTypeProviderConfig>> =
        rawProviderConfigs
            .map { providers -> if (providers.isEmpty()) builtInProviders() else providers }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), builtInProviders())

    private val defaultSearchProviderId: StateFlow<String?> =
        justTypeRegistry.defaultSearchProviderId
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val notificationsOptions: StateFlow<JustTypeNotificationsOptions> =
        justTypeRegistry.notificationsOptions
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JustTypeNotificationsOptions())

    val launchPointsById: StateFlow<Map<String, LaunchPoint>> =
        appsItems
            .map { list -> list.associateBy { it.id } }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val contactsItems: StateFlow<List<JustTypeItemUi>> =
        combine(
            _searchQuery
                .debounce(150)
                .map { it.trim() }
                .distinctUntilChanged(),
            _contactsPermissionGranted,
            providerConfigs
                .map { providers -> providers.any { it.enabled && it.category == JustTypeCategory.DBSEARCH && it.id == "contacts" } }
                .distinctUntilChanged(),
        ) { query, granted, contactsEnabled -> Triple(query, granted, contactsEnabled) }
            .flatMapLatest { (query, granted, contactsEnabled) ->
                flow {
                    if (!contactsEnabled || query.isBlank()) {
                        emit(emptyList())
                        return@flow
                    }
                    if (!granted) {
                        emit(
                            listOf(
                                JustTypeItemUi.ActionItem(
                                    actionId = "enable_contacts_search",
                                    title = "Enable Contacts Search",
                                    subtitle = "Allow access to contacts",
                                    argument = null,
                                ),
                            ),
                        )
                        return@flow
                    }
                    emit(queryContacts(query))
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val registryState: StateFlow<Triple<List<JustTypeProviderConfig>, String?, JustTypeNotificationsOptions>> =
        combine(providerConfigs, defaultSearchProviderId, notificationsOptions) { providers, defaultSearchId, notifOptions ->
            Triple(providers, defaultSearchId, notifOptions)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            Triple(emptyList(), null, JustTypeNotificationsOptions()),
        )

    private data class SearchInputs(
        val query: String,
        val allApps: List<LaunchPoint>,
        val favorites: List<LaunchPoint>,
        val active: Boolean,
    )

    private val searchInputs: StateFlow<SearchInputs> =
        combine(_searchQuery, appsItems, favoritesItems, _searchActive) { query, apps, favs, active ->
            SearchInputs(query, apps, favs, active)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchInputs("", emptyList(), emptyList(), false))

    private val emptyJustTypeState = JustTypeUiState(query = "", sections = emptyList())

    val justTypeState: StateFlow<JustTypeUiState> =
        combine(
            searchInputs,
            registryState,
            contactsItems,
            notificationIndexer.indexVersion, // Observe notification changes
        ) { inputs, registry, contactsItems, _ ->
            // Skip expensive buildState() when no search panel is visible.
            if (!inputs.active) return@combine emptyJustTypeState
            val (providers, defaultSearchId, notifOptions) = registry
            JustTypeEngine.buildState(
                query = inputs.query,
                allApps = inputs.allApps,
                favorites = inputs.favorites,
                providers = providers,
                defaultSearchProviderId = defaultSearchId,
                contactsItems = contactsItems,
                notificationIndexer = notificationIndexer,
                notificationsOptions = notifOptions,
                nowEpochMs = System.currentTimeMillis(),
            )
        }
        .flowOn(Dispatchers.Default) // scoring + sorting off the main thread
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyJustTypeState)

    fun setSearchQuery(value: String) {
        _searchQuery.value = value
    }

    fun setSearchActive(active: Boolean) {
        _searchActive.value = active
    }

    fun onJustTypeItemClick(item: JustTypeItemUi): Intent? =
        when (item) {
            is JustTypeItemUi.LaunchPointItem -> intentFor(item.lpId)
            is JustTypeItemUi.ActionItem -> {
                if (item.actionId == "enable_contacts_search") {
                    _events.tryEmit(LauncherEvent.RequestContactsPermission)
                    null
                } else {
                    intentForAction(item.actionId, item.argument)
                }
            }
            is JustTypeItemUi.DbRowItem -> intentForDbRow(item)
            is JustTypeItemUi.SearchTemplateItem -> intentForSearchTemplate(item.providerId, item.query)
            is JustTypeItemUi.NotificationItem -> {
                // Notification execution is handled in the UI layer (LauncherActivity) via NotificationActionExecutor,
                // since it needs coroutine + UI prompts (RemoteInput reply text).
                null
            }
        }

    fun intentFor(item: JustTypeItemUi): Intent? = onJustTypeItemClick(item)

    private fun intentForAction(actionId: String, argument: String?): Intent? =
        when (actionId) {
            "new_email" -> Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            "new_event" -> Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
            "new_message" -> Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))
            "set_alarm" -> Intent(AlarmClock.ACTION_SET_ALARM)
            "set_timer" ->
                Intent(AlarmClock.ACTION_SET_TIMER)
                    .putExtra(AlarmClock.EXTRA_LENGTH, 60)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            "open_settings" -> Intent(Settings.ACTION_SETTINGS)
            "wifi_settings" -> Intent(Settings.ACTION_WIFI_SETTINGS)
            "bluetooth_settings" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "manage_apps" -> Intent(Settings.ACTION_APPLICATION_SETTINGS)
            "call" -> {
                val num = argument?.trim().orEmpty()
                if (num.isBlank()) null
                else Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num"))
            }
            "text" -> {
                val num = argument?.trim().orEmpty()
                if (num.isBlank()) null
                else Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$num"))
            }
            "search_web" -> {
                val q = argument?.trim().orEmpty()
                if (q.isBlank()) {
                    null
                } else {
                    val providerId = defaultSearchProviderId.value
                    val template = if (providerId == null) null else urlTemplateForSearchProvider(providerId)
                    val fallback = template ?: "https://www.google.com/search?q={searchTerms}"
                    val encoded = URLEncoder.encode(q, Charsets.UTF_8)
                    Intent(Intent.ACTION_VIEW, Uri.parse(fallback.replace("{searchTerms}", encoded)))
                }
            }
            else -> null
        }

    private fun intentForDbRow(item: JustTypeItemUi.DbRowItem): Intent? {
        if (item.providerId != "contacts") return null
        val id = item.stableId.toLongOrNull() ?: return null
        val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id)
        return Intent(Intent.ACTION_VIEW, uri)
    }

    private fun intentForSearchTemplate(providerId: String, query: String): Intent? {
        if (providerId == "google") {
            val google = intentForGoogleSearch(query)
            if (google != null) return google
        }
        if (providerId == "google_maps") {
            val maps = intentForGoogleMapsSearch(query)
            if (maps != null) return maps
        }
        if (providerId == "reddit") {
            val reddit = intentForRedditSearch(query)
            if (reddit != null) return reddit
        }
        if (providerId == "wikipedia") {
            val wiki = intentForWikipediaSearch(query)
            if (wiki != null) return wiki
        }
        val template = urlTemplateForSearchProvider(providerId) ?: return null
        val encoded =
            if (template.startsWith("geo:", ignoreCase = true)) {
                Uri.encode(query)
            } else {
                URLEncoder.encode(query, Charsets.UTF_8)
            }
        val url = template.replace("{searchTerms}", encoded)
        return Intent(Intent.ACTION_VIEW, Uri.parse(url))
    }

    private fun urlTemplateForSearchProvider(providerId: String): String? =
        providerConfigs.value
            .firstOrNull { it.enabled && it.category == JustTypeCategory.SEARCH && it.id == providerId }
            ?.urlTemplate

    private fun intentForGoogleSearch(query: String): Intent? {
        val q = query.trim()
        if (q.isBlank()) return null

        val gsaPackage = "com.google.android.googlequicksearchbox"
        val intent =
            Intent(Intent.ACTION_WEB_SEARCH)
                .putExtra(SearchManager.QUERY, q)
                .setPackage(gsaPackage)

        return if (intent.resolveActivity(appContext.packageManager) != null) intent else null
    }

    private fun intentForGoogleMapsSearch(query: String): Intent? {
        val q = query.trim()
        if (q.isBlank()) return null

        val mapsPackage = "com.google.android.apps.maps"
        val encoded = Uri.encode(q)
        val gmmIntentUri = Uri.parse("geo:0,0?q=$encoded")
        val intent = Intent(Intent.ACTION_VIEW, gmmIntentUri).setPackage(mapsPackage)
        return if (intent.resolveActivity(appContext.packageManager) != null) intent else null
    }

    private fun intentForWikipediaSearch(query: String): Intent? {
        val q = query.trim()
        if (q.isBlank()) return null

        val wikiPackage = "org.wikipedia"
        val encoded = Uri.encode(q)

        val deepLink = Intent(Intent.ACTION_VIEW, Uri.parse("wikipedia://search?query=$encoded")).setPackage(wikiPackage)
        if (deepLink.resolveActivity(appContext.packageManager) != null) return deepLink

        val httpsLink =
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://en.wikipedia.org/wiki/Special:Search?search=$encoded"),
            ).setPackage(wikiPackage)
        if (httpsLink.resolveActivity(appContext.packageManager) != null) return httpsLink

        return null
    }

    private fun intentForRedditSearch(query: String): Intent? {
        val q = query.trim()
        if (q.isBlank()) return null

        val redditPackage = "com.reddit.frontpage"
        val encoded = Uri.encode(q)

        val deepLink = Intent(Intent.ACTION_VIEW, Uri.parse("reddit://search?q=$encoded")).setPackage(redditPackage)
        if (deepLink.resolveActivity(appContext.packageManager) != null) return deepLink

        val searchIntent =
            Intent(Intent.ACTION_SEARCH)
                .putExtra(SearchManager.QUERY, q)
                .setPackage(redditPackage)
        if (searchIntent.resolveActivity(appContext.packageManager) != null) return searchIntent

        return null
    }

    private suspend fun queryContacts(query: String): List<JustTypeItemUi.DbRowItem> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<JustTypeItemUi.DbRowItem>()
            val projection =
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                    ContactsContract.Contacts.HAS_PHONE_NUMBER,
                )
            val selection = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?"
            val args = arrayOf("%$query%")
            val sort = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} COLLATE NOCASE ASC"

            val cursor =
                appContext.contentResolver.query(
                    ContactsContract.Contacts.CONTENT_URI,
                    projection,
                    selection,
                    args,
                    sort,
                ) ?: return@withContext emptyList()

            cursor.use { c ->
                val idIdx = c.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
                val nameIdx = c.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                val hasPhoneIdx = c.getColumnIndexOrThrow(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                while (c.moveToNext() && results.size < 20) {
                    val id = c.getLong(idIdx)
                    val name = c.getString(nameIdx) ?: "(No name)"
                    val hasPhone = c.getInt(hasPhoneIdx) > 0
                    val phone =
                        if (hasPhone) {
                            queryFirstPhoneNumber(id)
                        } else {
                            null
                        }
                    results.add(
                        JustTypeItemUi.DbRowItem(
                            providerId = "contacts",
                            stableId = id.toString(),
                            title = name,
                            subtitle = phone,
                        ),
                    )
                }
            }
            results
        }

    private fun queryFirstPhoneNumber(contactId: Long): String? {
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val selection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID}=?"
        val args = arrayOf(contactId.toString())
        val cursor =
            appContext.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                args,
                null,
            ) ?: return null
        cursor.use { c ->
            val numIdx = c.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            return if (c.moveToFirst()) c.getString(numIdx) else null
        }
    }

    private fun hasContactsPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private fun builtInProviders(): List<JustTypeProviderConfig> =
        listOf(
            JustTypeProviderConfig(
                id = "new_email",
                category = JustTypeCategory.ACTION,
                displayName = "New Email",
                enabled = true,
                orderIndex = 10,
                version = 1,
                source = "builtin",
            ),
            JustTypeProviderConfig(
                id = "new_event",
                category = JustTypeCategory.ACTION,
                displayName = "New Event",
                enabled = true,
                orderIndex = 20,
                version = 1,
                source = "builtin",
            ),
            JustTypeProviderConfig(
                id = "new_message",
                category = JustTypeCategory.ACTION,
                displayName = "New Message",
                enabled = true,
                orderIndex = 30,
                version = 1,
                source = "builtin",
            ),
            JustTypeProviderConfig(
                id = "set_timer",
                category = JustTypeCategory.ACTION,
                displayName = "Set Timer",
                enabled = true,
                orderIndex = 40,
                version = 1,
                source = "builtin",
            ),
            JustTypeProviderConfig(
                id = "set_alarm",
                category = JustTypeCategory.ACTION,
                displayName = "Set Alarm",
                enabled = true,
                orderIndex = 50,
                version = 1,
                source = "builtin",
            ),
            JustTypeProviderConfig(
                id = "open_settings",
                category = JustTypeCategory.ACTION,
                displayName = "Open Settings",
                enabled = true,
                orderIndex = 60,
                version = 1,
                source = "builtin",
            ),
            JustTypeProviderConfig(
                id = "wifi_settings",
                category = JustTypeCategory.ACTION,
                displayName = "Wi‑Fi Settings",
                enabled = true,
                orderIndex = 70,
                version = 1,
                source = "builtin",
            ),
            JustTypeProviderConfig(
                id = "bluetooth_settings",
                category = JustTypeCategory.ACTION,
                displayName = "Bluetooth Settings",
                enabled = true,
                orderIndex = 80,
                version = 1,
                source = "builtin",
            ),
            JustTypeProviderConfig(
                id = "manage_apps",
                category = JustTypeCategory.ACTION,
                displayName = "Manage Apps",
                enabled = true,
                orderIndex = 90,
                version = 1,
                source = "builtin",
            ),
            JustTypeProviderConfig(
                id = "call",
                category = JustTypeCategory.ACTION,
                displayName = "Call",
                enabled = true,
                orderIndex = 110,
                version = 1,
                source = "builtin",
            ),
            JustTypeProviderConfig(
                id = "text",
                category = JustTypeCategory.ACTION,
                displayName = "Text",
                enabled = true,
                orderIndex = 120,
                version = 1,
                source = "builtin",
            ),
            JustTypeProviderConfig(
                id = "apps",
                category = JustTypeCategory.APPS,
                displayName = null,
                enabled = true,
                orderIndex = 100,
                version = 1,
                source = "builtin",
            ),
            JustTypeProviderConfig(
                id = "notifications",
                category = JustTypeCategory.NOTIFICATIONS,
                displayName = "Notifications",
                enabled = true,
                orderIndex = 150,
                version = 1,
                source = "builtin",
            ),
            JustTypeProviderConfig(
                id = "contacts",
                category = JustTypeCategory.DBSEARCH,
                displayName = "Contacts",
                enabled = true,
                orderIndex = 200,
                version = 1,
                source = "builtin",
            ),
            JustTypeProviderConfig(
                id = "google",
                category = JustTypeCategory.SEARCH,
                displayName = "Google",
                enabled = true,
                orderIndex = 900,
                version = 1,
                source = "builtin",
                urlTemplate = "https://www.google.com/search?q={searchTerms}",
                canPromotePrimaryResult = true,
            ),
            JustTypeProviderConfig(
                id = "google_maps",
                category = JustTypeCategory.SEARCH,
                displayName = "Google Maps",
                enabled = true,
                orderIndex = 905,
                version = 1,
                source = "builtin",
                urlTemplate = "geo:0,0?q={searchTerms}",
            ),
            JustTypeProviderConfig(
                id = "reddit",
                category = JustTypeCategory.SEARCH,
                displayName = "Reddit",
                enabled = true,
                orderIndex = 915,
                version = 1,
                source = "builtin",
                urlTemplate = "https://reddit.com/search?q={searchTerms}",
            ),
            JustTypeProviderConfig(
                id = "wikipedia",
                category = JustTypeCategory.SEARCH,
                displayName = "Wikipedia",
                enabled = true,
                orderIndex = 910,
                version = 1,
                source = "builtin",
                urlTemplate = "https://en.wikipedia.org/wiki/Special:Search?search={searchTerms}",
            ),
        )

    fun refreshInstalledApps(force: Boolean = false) {
        if (!force && initialScanTriggered) return
        initialScanTriggered = true
        viewModelScope.launch {
            val apps = withContext(Dispatchers.Default) { scanner.scanLaunchableActivities() }
            repository.syncAndroidApps(apps)
        }
    }

    fun findById(id: String): LaunchPointRecord? = allItems.value.firstOrNull { it.id == id }

    fun actionsFor(id: String): List<LaunchPointAction> {
        val lp = findById(id) ?: return emptyList()
        val base = buildList {
            add(if (lp.pinned) LaunchPointAction.Unpin else LaunchPointAction.Pin)
            add(if (lp.hidden) LaunchPointAction.Unhide else LaunchPointAction.Hide)
        }
        return when (lp.type) {
            LaunchPointType.ANDROID_APP -> base + listOf(LaunchPointAction.AppInfo, LaunchPointAction.Uninstall)
            LaunchPointType.WEBOS_APP -> base
        }
    }

    fun intentFor(id: String): Intent {
        val lp = requireNotNull(findById(id)) { "LaunchPoint not found: $id" }
        return when (lp.type) {
            LaunchPointType.ANDROID_APP -> scanner.resolveIntentOrThrow(lp.id)
            LaunchPointType.WEBOS_APP -> throw UnsupportedOperationException("WEBOS_APP intent resolution not implemented yet")
        }
    }

    fun appInfoIntent(id: String): Intent {
        val (pkg, _) = scanner.parseAndroidIdOrThrow(id)
        val uri = Uri.fromParts("package", pkg, null)
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun uninstallIntent(id: String): Intent {
        val (pkg, _) = scanner.parseAndroidIdOrThrow(id)
        val uri = Uri.parse("package:$pkg")
        return Intent(Intent.ACTION_DELETE, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun recordLaunched(id: String, epochMs: Long = System.currentTimeMillis()) {
        viewModelScope.launch { repository.markLaunched(id, epochMs) }
    }

    fun pin(id: String, pinned: Boolean) {
        viewModelScope.launch {
            if (pinned) repository.ensurePinnedRank(id, System.currentTimeMillis())
            repository.setPinned(id, pinned)
        }
    }

    fun hide(id: String, hidden: Boolean) {
        viewModelScope.launch { repository.setHidden(id, hidden) }
    }

    fun addToDock(id: String) {
        viewModelScope.launch { repository.addToDock(id, maxSlots = 5) }
    }

    fun placeInHome(slotIndex: Int, id: String) {
        viewModelScope.launch {
            // Legacy grid placement entrypoint retained for callsites; map into normalized coordinates.
            val col = (slotIndex % 7).coerceIn(0, 6)
            val row = (slotIndex / 7).coerceIn(0, 8)
            val xNorm = col.toDouble() / 6.0
            val yNorm = row.toDouble() / 8.0
            repository.placeInHomeAbsolute(
                launchPointId = id,
                xNorm = xNorm,
                yNorm = yNorm,
                rotationDeg = 0f,
                nowEpochMs = System.currentTimeMillis(),
            )
        }
    }

    fun placeInHomeAbsolute(id: String, xNorm: Double, yNorm: Double, rotationDeg: Float = 0f) {
        viewModelScope.launch {
            repository.placeInHomeAbsolute(
                launchPointId = id,
                xNorm = xNorm,
                yNorm = yNorm,
                rotationDeg = rotationDeg,
                nowEpochMs = System.currentTimeMillis(),
            )
        }
    }

    fun updateHomeIconPosition(id: String, xNorm: Double, yNorm: Double, rotationDeg: Float) {
        viewModelScope.launch {
            repository.updateHomeIconPosition(
                launchPointId = id,
                xNorm = xNorm,
                yNorm = yNorm,
                rotationDeg = rotationDeg,
                nowEpochMs = System.currentTimeMillis(),
            )
        }
    }

    fun removeFromDock(id: String) {
        viewModelScope.launch { repository.removeFromDock(id) }
    }

    fun removeFromHome(id: String) {
        viewModelScope.launch { repository.removeFromHome(id) }
    }

    companion object {
        fun factory(container: LauncherContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return LauncherViewModel(
                        repository = container.repository,
                        scanner = container.scanner,
                        appContext = container.appContext,
                        justTypeRegistry = container.justTypeRegistry,
                        notificationIndexer = container.notificationIndexer,
                    ) as T
                }
            }
    }
}

private fun HomeIconEntity.toPlacement(): HomeIconPlacement =
    HomeIconPlacement(
        launchPointId = launchPointId,
        xNorm = xNorm,
        yNorm = yNorm,
        rotationDeg = rotationDeg,
        zIndex = zIndex,
        updatedAtEpochMs = updatedAtEpochMs,
    )
