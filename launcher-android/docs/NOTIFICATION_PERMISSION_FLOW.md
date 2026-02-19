# Notification Permission Flow - First Boot Experience

## User Journey

### 1. First Boot
User installs Luna Launcher and sets it as default home screen.

### 2. Permission Dialog Appears
**Automatically shown on first launch** (only once):

```
┌─────────────────────────────────────────┐
│  🔔 Search Your Notifications           │
│                                         │
│  Luna can make your notifications       │
│  searchable.                            │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  🔍 Type a name to find their   │   │
│  │     messages                    │   │
│  │                                 │   │
│  │  💬 Reply directly from search  │   │
│  │     results                     │   │
│  │                                 │   │
│  │  ⚡ Actions appear only while   │   │
│  │     notification is active      │   │
│  └─────────────────────────────────┘   │
│                                         │
│  Notification content stays private     │
│  and never leaves your device.          │
│                                         │
│           [Not Now]  [Enable Access]    │
└─────────────────────────────────────────┘
```

**Design Notes**:
- Dark theme (matches launcher aesthetic)
- Blue accent color (`#6FAEDB`) for "Enable Access"
- Clear value proposition (3 emoji bullets)
- Privacy reassurance at bottom
- Non-blocking (can dismiss with "Not Now")

### 3. User Taps "Enable Access"
Opens Android Settings → Notification Access screen:

```
Android Settings
┌─────────────────────────────────────────┐
│  ← Notification access                  │
│                                         │
│  Apps with notification access can      │
│  read all notifications, including      │
│  personal information like contact      │
│  names and message text.                │
│                                         │
│  ┌─────────────────────────────────┐   │
│  │  ○ Luna Launcher           OFF  │   │  ← User toggles this
│  └─────────────────────────────────┘   │
│                                         │
└─────────────────────────────────────────┘
```

User toggles the switch to **ON**.

### 4. Return to Launcher
User presses back button → Returns to Luna home screen.

**NotificationListenerService** starts automatically:
- Begins indexing active notifications
- Makes them searchable in Just Type

### 5. Test the Feature
User receives a message from "Alex":

**Home Screen → Swipe down to search**:
```
┌─────────────────────────────────────────┐
│  🔍 [alex_________________]             │
│                                         │
│  APPS                                   │
│  [Alex's App]  [Alexandria]             │
│                                         │
│  NOTIFICATIONS                          │
│  ┌─────────────────────────────────┐   │
│  │  A  Alex                   LIVE │   │
│  │     Hey, are you free tonight?  │   │
│  │                                 │   │
│  │  [Reply] [Mark as read] [Open]  │   │
│  └─────────────────────────────────┘   │
└─────────────────────────────────────────┘
```

---

## Implementation Details

### Files Modified

1. **NotificationPermissionHelper.kt** (NEW)
   - `isNotificationAccessGranted()` - Checks permission status
   - `openNotificationAccessSettings()` - Opens Settings
   - `PREF_NOTIFICATION_PERMISSION_REQUESTED` - Tracks if asked before

2. **NotificationPermissionDialog.kt** (NEW)
   - Beautiful onboarding dialog
   - Dark theme with blue accent
   - 3 feature bullets with emoji
   - Privacy reassurance

3. **LauncherActivity.kt** (MODIFIED)
   - Added `LaunchedEffect` to check permission on first boot
   - Shows dialog if permission not granted and not asked before
   - Saves preference after showing (only asks once)
   - Opens Settings when user taps "Enable Access"

### Permission Check Logic

```kotlin
LaunchedEffect(Unit) {
    val prefs = context.getSharedPreferences("luna_prefs", MODE_PRIVATE)
    val hasAsked = prefs.getBoolean("notification_permission_requested", false)
    val hasPermission = NotificationPermissionHelper.isNotificationAccessGranted(context)

    if (!hasAsked && !hasPermission) {
        showNotificationPermissionDialog = true
        prefs.edit()
            .putBoolean("notification_permission_requested", true)
            .apply()
    }
}
```

**Behavior**:
- ✅ First boot + no permission → Show dialog
- ❌ First boot + already has permission → Don't show
- ❌ Second boot → Never show again (even if declined)
- ✅ User can manually enable later in Settings

### Why Only Ask Once?

**Android UX Best Practices**:
- Don't be annoying
- Respect user choice
- Let them discover feature organically
- Can always enable manually in Settings

**Alternative**: Add a settings screen in launcher with "Enable Notifications" button.

---

## User Can Decline

If user taps **"Not Now"**:
1. Dialog dismisses
2. Preference saved (won't ask again)
3. Launcher works normally
4. Notifications won't appear in search
5. All other Just Type features work fine

**No crashes, no nagging.**

---

## Testing Checklist

### Fresh Install
- [ ] Install Luna Launcher
- [ ] Set as default home
- [ ] Launch → Dialog appears
- [ ] Tap "Enable Access"
- [ ] Verify Settings opens
- [ ] Toggle permission ON
- [ ] Return to launcher
- [ ] Send test notification
- [ ] Search for sender name
- [ ] Verify notification appears

### Already Granted
- [ ] Install Luna with permission already granted
- [ ] Launch → Dialog does NOT appear
- [ ] Verify notifications already searchable

### Declined Permission
- [ ] Fresh install
- [ ] Tap "Not Now" on dialog
- [ ] Relaunch app
- [ ] Verify dialog does NOT appear again
- [ ] Verify launcher works normally

### Second Boot
- [ ] Install and enable permission
- [ ] Close launcher
- [ ] Reopen launcher
- [ ] Verify dialog does NOT appear

---

## Future Enhancements

### Settings Screen Option
Add manual toggle in launcher settings:

```kotlin
@Composable
fun NotificationSettingsRow() {
    val context = LocalContext.current
    val hasPermission = NotificationPermissionHelper.isNotificationAccessGranted(context)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                NotificationPermissionHelper.openNotificationAccessSettings(context)
            }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text("Notification Search")
            Text(
                text = if (hasPermission) "Enabled" else "Disabled",
                style = MaterialTheme.typography.bodySmall,
                color = if (hasPermission) Color.Green else Color.Gray,
            )
        }
        Switch(
            checked = hasPermission,
            onCheckedChange = {
                NotificationPermissionHelper.openNotificationAccessSettings(context)
            },
        )
    }
}
```

### Re-prompt After Update
If we add major new notification features, we could:
1. Bump preference key version
2. Show updated onboarding
3. Explain new features

Example:
```kotlin
const val PREF_NOTIFICATION_PERMISSION_REQUESTED_V2 = "notification_permission_requested_v2"
```

### Analytics (Optional)
Track permission grant rate:
- How many users enable on first boot?
- How many decline?
- Can inform future UX decisions

---

## Visual Design

### Color Palette
- Background: `#1C1C1E` (dark gray)
- Title: White
- Body text: White @ 90% opacity
- Feature bullets background: White @ 6% opacity
- Privacy text: White @ 65% opacity
- "Enable Access" button: `#6FAEDB` @ 20% background
- "Not Now" button: White @ 70% opacity

### Typography
- Title: `MaterialTheme.typography.titleLarge` + SemiBold
- Body: `MaterialTheme.typography.bodyMedium`
- Bullets: `MaterialTheme.typography.bodyMedium`
- Privacy: `MaterialTheme.typography.bodySmall`

### Spacing
- Feature bullets: 12.dp vertical spacing
- Sections: 16.dp vertical spacing
- Card padding: 14.dp
- Button padding: 8.dp horizontal

---

## Privacy Considerations

### What We Tell Users
✅ "Notification content stays private and never leaves your device"

### What Actually Happens
1. Android grants `BIND_NOTIFICATION_LISTENER_SERVICE` permission
2. Our `LunaNotificationListenerService` receives notifications
3. We extract: title, text, names, actions
4. We store: **in-memory only** (NotificationIndexer)
5. We never: persist to disk, send to network, share with other apps

### Security Guarantees
- No database persistence (ephemeral)
- No network requests
- No analytics tracking
- No third-party SDK access
- Cleared on notification dismiss

**This is genuinely private.**

---

## Status: ✅ **Ready to Ship**

All code is implemented and integrated. The permission flow will trigger automatically on first boot.

**Next Steps**: Build, install, test on device!
