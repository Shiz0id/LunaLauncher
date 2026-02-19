# LaunchPoint Contract

## What it represents
`LaunchPoint` is a runtime-agnostic description of something the user can launch.

It is **not** “an Android app” or “a webOS app”. Those are producers/runtimes that *emit* launch points.

## What it does not represent
- Platform package metadata (e.g. `PackageManager` details)
- Implementation/runtime internals (Android components, webOS app ids, etc.)

## Public API (UI-visible)
UI code must only depend on:
- `id`
- `type`
- `title`
- `iconKey`
- `pinned`
- `hidden`
- `lastLaunchedAtEpochMs`

No UI code should branch on Android/webOS specifics directly.

## Guarantees / invariants
- **Stable id**: once written to the DB, the `id` must never change.
- **Immutable type**: `type` for a given `id` must not change.
- **Mutable user state**: `pinned`, `hidden`, `lastLaunchedAtEpochMs` may change via user actions and launch events.

## ID normalization
Android launch points use:

`android:<package>/<activity>`

This is the canonical identity used for storage, syncing, and intent resolution.

## Icon keys (Android)
Android icon keys are deterministic and include a cache-busting version suffix:

`android:<package>/<activity>@<versionCode>`

This keeps `id` stable while ensuring icons refresh correctly after app updates.

## Why this matters
If `LaunchPoint` stays clean, webOS apps later are just “another producer of LaunchPoints” and the launcher UI does not need to change.
