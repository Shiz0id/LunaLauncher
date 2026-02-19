# Just Type × Notifications: Live Verb Surfaces

## Philosophy

Notifications aren't just messages—they're **temporarily available, app-owned actions**.

While a notification exists:
- **Noun**: "Alex sent a message"
- **Verbs**: Reply, Mark as Read, Open

Once dismissed:
- Verbs disappear immediately
- No dead buttons, ever

This is first-class Just Type integration: **intent-backed, expiring, searchable actions**.

## Architecture

### 1. NotificationIndexer (In-Memory, Live)
```kotlin
class NotificationIndexer {
    // Ephemeral index: only exists while notification is active
    search(query: String): List<NotificationActionSurface>
    put(surface: NotificationActionSurface)
    remove(key: String)
}
```

**Key principle**: Index disappears when notification dismissed.

### 2. LunaNotificationListenerService (Feed)
```kotlin
class LunaNotificationListenerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification)
    override fun onNotificationRemoved(sbn: StatusBarNotification)
}
```

**Security model**:
- Requires `BIND_NOTIFICATION_LISTENER_SERVICE` permission
- User must explicitly grant access in Settings
- Uses Android's PendingIntent capability tokens (no privilege escalation)

### 3. NotificationsProvider (Just Type Integration)
```kotlin
object NotificationsProvider {
    fun itemsFor(
        query: String,
        indexer: NotificationIndexer,
    ): List<JustTypeItemUi.NotificationItem>
}
```

**Search behavior**:
- Matches: title, text, extracted names
- Sorted by recency (newest first)
- Max 5 results (avoid UI noise)
- Empty query = no results (show apps/actions instead)

### 4. NotificationActionExecutor (Execution)
```kotlin
class NotificationActionExecutor {
    suspend fun executeContentIntent(key: String): ExecutionResult
    suspend fun executeAction(key: String, index: Int, replyText: String?): ExecutionResult
}
```

**Error handling**:
```kotlin
sealed class ExecutionResult {
    object Success
    data class NotificationDismissed(message: String)  // Graceful degradation
    data class IntentCancelled(message: String)        // App uninstalled, etc.
    data class Error(message: String, cause: Throwable?)
}
```

**Critical rule**: Never execute without verifying notification still exists.

## Priority Model

### First-Class (Live Notifications)
```
alex
→ "Alex - Message" (LIVE)
  ├─ Reply                    [RemoteInput inline]
  ├─ Mark as read            [PendingIntent.send()]
  └─ Open conversation       [contentIntent.send()]
```

**Characteristics**:
- Returns actions
- Executes inline
- Highest priority
- **Disappears immediately when notification dismissed**

### Second-Class (History - Future)
```
alex
→ "Alex messaged you 2h ago" (history)
  └─ Open Messages app       [Deep link fallback]
```

**Characteristics**:
- Read-only
- No actions
- App deep link fallback
- Not yet implemented

### Third-Class (App Launch)
```
alex
→ Messages                   [Launch app root]
```

**Characteristics**:
- Standard app launch
- Always available

## Data Flow

```
1. App posts notification
   ↓
2. LunaNotificationListenerService.onNotificationPosted()
   ↓
3. Extract: title, text, names, actions, PendingIntents
   ↓
4. NotificationIndexer.put(surface)
   ↓
5. User types "alex" in Just Type
   ↓
6. NotificationsProvider.itemsFor(query="alex")
   ↓
7. Returns: NotificationItem(title="Alex", actions=["Reply", "Mark as Read"])
   ↓
8. User taps "Reply"
   ↓
9. NotificationActionExecutor.executeAction(key, actionIndex=0, replyText="Sure!")
   ↓
10. PendingIntent.send() with RemoteInput
    ↓
11. Success or graceful error (NotificationDismissed, IntentCancelled)
```

## Setup

### 1. Initialize in Application.onCreate()
```kotlin
class LunaLauncherApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val indexer = NotificationIndexer()
        LunaNotificationListenerService.initializeIndexer(indexer)
    }
}
```

### 2. Declare Service in AndroidManifest.xml
```xml
<service
    android:name=".core.justtype.notifications.LunaNotificationListenerService"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
    android:exported="true">
    <intent-filter>
        <action android:name="android.service.notification.NotificationListenerService" />
    </intent-filter>
</service>
```

### 3. Request User Permission
```kotlin
// Prompt user to grant notification access
val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
startActivity(intent)
```

### 4. Integrate with Just Type Engine
```kotlin
val notificationItems = NotificationsProvider.itemsFor(
    query = query,
    indexer = indexer,
)

val section = JustTypeSectionUi(
    providerId = "notifications",
    title = "NOTIFICATIONS",
    category = JustTypeCategory.NOTIFICATIONS,
    items = notificationItems,
)
```

## UI Rendering

### NotificationBarRow
```kotlin
@Composable
fun NotificationBarRow(
    title: String,              // "Alex"
    subtitle: String?,          // "Hey, are you free tonight?"
    actions: List<String>,      // ["Reply", "Mark as Read", "Open"]
    timestamp: Long,
    onClick: () -> Unit,
) {
    // Header with LIVE badge
    // Subtitle (message preview)
    // Action pills (Reply, Mark as Read, etc.)
}
```

**Visual cues**:
- Blue highlight badge (indicates "LIVE")
- "LIVE" label in top-right
- Action pills below message preview

## RemoteInput (Inline Reply)

### Execution
```kotlin
val result = executor.executeAction(
    notificationKey = item.notificationKey,
    actionIndex = 0,  // "Reply" action
    replyText = "Sure, I'm free!",
)

when (result) {
    is ExecutionResult.Success -> { /* Show toast */ }
    is ExecutionResult.NotificationDismissed -> { /* "Notification no longer available" */ }
    is ExecutionResult.IntentCancelled -> { /* "Action unavailable" */ }
    is ExecutionResult.Error -> { /* "Failed to send reply" */ }
}
```

### Remote Input Formatting
```kotlin
val intent = Intent()
val bundle = Bundle()
bundle.putCharSequence(remoteInput.resultKey, replyText)
RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, bundle)
action.intent.send(context, 0, intent)
```

## Security Considerations

### ✅ What's Safe
- **PendingIntent** = Already authorized by notification sender
- **RemoteInput** = Structured input (no arbitrary commands)
- **Ephemeral** = Actions disappear when notification dismissed
- **No persistence** = Can't replay old intents

### ⚠️ What to Avoid
- **Never** persist PendingIntents to database
- **Never** show actions after notification dismissed
- **Never** execute without verifying notification exists
- **Never** bypass Android's permission model

## Testing

### Manual Test Flow
1. Send yourself a test message (SMS, WhatsApp, etc.)
2. Open launcher, swipe down to search
3. Type sender name ("alex")
4. Verify notification appears with "LIVE" badge
5. Tap "Reply" action
6. Verify inline reply works
7. Dismiss notification from shade
8. Verify notification disappears from Just Type immediately

### Edge Cases
- App uninstalled mid-execution → `IntentCancelled`
- Notification dismissed during action → `NotificationDismissed`
- RemoteInput without text → Skip RemoteInput, send plain intent
- Multiple notifications from same person → Group by conversation thread (future)

## Why This is Powerful

Most Android launchers treat notifications as:
- ❌ Passive badges
- ❌ Shade-only problem
- ❌ Not searchable

Luna treats them as:
- ✅ **Temporary verb surfaces**
- ✅ **Searchable actions**
- ✅ **First-class Just Type results**

This is genuinely novel: **intent-backed, expiring, searchable actions**.

## WebOS Heritage

This design is deeply WebOS-faithful:
- **Just Type** = Universal search (WebOS 2.0+)
- **Notifications as verbs** = Synergy-inspired action surfaces
- **Ephemeral** = No dead buttons (learned on Pre/TouchPad)
- **First-class integration** = Not bolted on, designed in

## Future Enhancements (Phase 2+)

### History Database (Read-Only)
```kotlin
data class NotificationHistoryRecord(
    val title: String,
    val text: String?,
    val packageName: String,
    val timestamp: Long,
    // NO PendingIntents (security)
    // NO actions (ephemeral only)
)
```

### Smart Ranking
- Recency + engagement
- "You usually mark Alex read"
- Context: time of day, location

### Cross-App Suggestions
- "Reply in Signal instead?" (if contact exists in multiple apps)
- "Mark all messages from Alex read"

### Conversation Threading
- Group notifications by person/thread
- "3 messages from Alex" → expandable

## Performance Notes

### Memory Footprint
- Index size: ~1KB per notification
- Typical: 20-50 active notifications = 20-50KB
- Max: 100 notifications = 100KB (negligible)

### Search Performance
- In-memory search: O(n) where n = active notifications
- Typical: <1ms for 50 notifications
- No disk I/O, no database queries

### Recomposition
- `indexVersion` StateFlow triggers recomposition
- Only when notifications added/removed
- Efficient: doesn't re-render entire Just Type panel

## License

Copyright © 2025 Luna System Manager
WebOS-inspired, Android-native implementation.
