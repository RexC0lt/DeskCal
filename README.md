# DeskCal

DeskCal turns an old Android phone into an **always-on desk calendar**. The phone stands on a
stand, stays plugged in, and continuously shows the current week, synced directly from a
self-hosted [Radicale](https://radicale.org/) CalDAV server over the local network. No third-party
sync app (DAVx5 etc.) is required — the CalDAV client is built in.

## Features

- **Week view** (Monday–Sunday), one section per day, swipe left/right to move between weeks,
  auto-advances to the new week when the current week ends.
- Each event shows start–end time (24-hour by default, 12-hour optional), title and location.
  All-day events are marked **All day**; today is highlighted; past events of today are dimmed.
- Days without events can be hidden (settings).
- Large, high-contrast typography on a dark theme, readable from ~1 m; adapts to portrait and
  landscape (days reflow into columns in landscape).
- **CalDAV sync built in** (OkHttp + ical4j): calendar discovery via `/.well-known/caldav` →
  principal → calendar-home-set, then `calendar-query` REPORT with a time-range filter for the
  visible week plus a configurable look-ahead (default 8 weeks).
- **Local cache in Room**: the week renders instantly on launch and keeps showing the last known
  data with an **offline** indicator when the server is unreachable. The cache is never cleared on
  a failed sync.
- **Refresh**: on launch, every 5/15/30/60 minutes (configurable), pull-to-refresh, and an
  immediate retry when connectivity returns. One periodic sync (in-app coroutine loop plus a
  WorkManager backstop) — no aggressive polling.
- **Always-on desk behaviour**: screen stays on while the app is visible (`FLAG_KEEP_SCREEN_ON`),
  night mode dims the screen between configurable hours, optional launch-on-boot, and a subtle
  1–2 px content shift every 60 s to guard against OLED burn-in (toggleable).
- **First-run wizard**: server URL, username, password, self-signed-certificate toggle, then a
  calendar picker. Every step is validated against the live server with clear error messages
  (unreachable host, TLS failure, wrong credentials).
- Credentials are stored in **EncryptedSharedPreferences** (Android Keystore) and are never logged.

## Requirements

- Android phone with **Android 7.0 (API 24)** or newer; the app is optimised for old, low-end
  hardware (2 GB RAM or less).
- A Radicale server reachable over the LAN (plain HTTP or HTTPS, including self-signed).
- Build machine: **JDK 17** and Android Studio (or just the Android SDK + Gradle 8.7 via the
  included wrapper).

## Building the APK

```bash
./gradlew assembleDebug      # debug APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # release APK (minified): app/build/outputs/apk/release/app-release-unsigned.apk
./gradlew test               # runs the unit tests (week logic, iCalendar parsing, RRULE expansion)
```

No `local.properties` is required if `ANDROID_HOME` / `ANDROID_SDK_ROOT` is set, otherwise create
`local.properties` with `sdk.dir=/path/to/android-sdk` (the file is git-ignored).

Project setup: Gradle 8.7 (wrapper included), AGP 8.5.2, Kotlin 1.9.24, KSP, Jetpack Compose
(Material 3, BOM 2024.06.00), Room 2.6.1, WorkManager 2.9.1, OkHttp 4.12.0, ical4j 3.2.19.
`minSdk 24`, `targetSdk/compileSdk 34`. Core library desugaring is enabled so ical4j's `java.time`
usage works on API 24–25.

## Radicale setup and finding the base URL

1. Install and start Radicale (default port **5232**), e.g.:

   ```ini
   # /etc/radicale/config
   [server]
   hosts = 0.0.0.0:5232
   [auth]
   type = htpasswd
   htpasswd_filename = /etc/radicale/users
   htpasswd_encryption = bcrypt
   ```

2. Create a user (`htpasswd -B -c /etc/radicale/users alice`) and a calendar. Radicale creates a
   calendar collection per user at `/<username>/<calendar-name>/`, e.g. `alice/personal/`.
3. In the DeskCal first-run wizard enter the **base URL** of the server, for example
   `http://192.168.1.50:5232` (plain HTTP on the LAN) or `https://calendar.example.lan`.
   The app appends `/.well-known/caldav` itself, follows Radicale's redirect to the principal,
   reads the calendar-home-set and lists the calendars for you to pick.
4. If the server uses HTTPS with a **self-signed certificate**, enable
   "Trust self-signed certificates" in the wizard/settings (a warning is shown; certificate
   validation is disabled for that connection only).

## Sideloading the APK onto the old phone

1. On the phone: **Settings → About phone → tap "Build number" 7 times** to enable Developer
   options, then **Settings → System → Developer options → enable "USB debugging"**.
2. Connect the phone via USB and authorise the computer when the dialog appears.
3. Install the debug APK:

   ```bash
   adb devices                 # verify the phone is listed
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

   Alternatively copy `app-debug.apk` to the phone (USB file transfer, SD card, or a LAN share)
   and tap it in a file manager to install; allow "Install unknown apps" for that source when
   prompted.
4. Open **DeskCal** and complete the setup wizard. The week view opens directly afterwards.
5. To keep the screen on while charging, leave the app open on the stand. Optionally enable
   "Launch on boot" in settings so the app starts after a reboot (on Android 10+ the system may
   block background activity starts on some devices; on Android 8 it works directly).

## Settings overview

Server URL / username / password / trust-self-signed toggle, calendar selection, refresh interval
(5/15/30/60 min), 12/24-hour time, hide empty days, look-ahead weeks, night-mode hours, launch on
boot, burn-in protection, and a **Test connection** button. All settings persist across reboots
(plain `SharedPreferences` for UI settings, `EncryptedSharedPreferences` for credentials).

## Architecture

MVVM with manual dependency injection (`di/AppContainer`), no Hilt:

- `caldav/` — OkHttp-based CalDAV client: PROPFIND discovery, REPORT calendar-query, Basic auth,
  optional trust-all TLS for self-signed LAN servers, typed errors.
- `ical/` — iCalendar parsing (ical4j for the structure, own date-time/duration parsing for
  timezone correctness on API 24) and RRULE expansion (daily/weekly/monthly/yearly, INTERVAL,
  COUNT, UNTIL, BYDAY, BYMONTHDAY, EXDATE). Malformed events are skipped individually.
- `data/` — `SettingsRepository` (EncryptedSharedPreferences + SharedPreferences), Room
  (`occurrences`, `calendars`), `DeskCalRepository` (sync orchestration; replaces only the fetched
  range per calendar and never wipes the cache on failure).
- `week/` — pure date logic (week boundaries incl. month/year/DST edges, night-mode hours) and
  day grouping; covered by unit tests.
- `sync/` — in-app periodic sync loop + `SyncWorker` (WorkManager) backstop; connectivity callback
  triggers an immediate re-sync when the network returns.
- `ui/` — Compose screens: setup wizard, week view (horizontal week pager, adaptive day grid,
  pull-to-refresh, status line with date / next sync / sync state), settings.

Sync flow: sync on launch → every N minutes → pull-to-refresh → connectivity-restored retry.
The sync range is `[start of displayed week − 1 day, now + look-ahead weeks]`, so swiping ahead
also fetches data for the weeks you look at.

## Security notes

- HTTP Basic auth over LAN HTTP is sent in cleartext by design (the server is trusted LAN
  infrastructure); use HTTPS if the server is reachable that way. The network security config
  permits cleartext so `http://…:5232` works.
- The "trust self-signed certificates" toggle disables certificate and hostname validation for the
  CalDAV connection only, and shows a warning when enabled. It is off by default.
- Credentials live only in EncryptedSharedPreferences (Android Keystore, AES-256-GCM) and are
  never written to logs.

## Known limitations

- Recurrence overrides (`RECURRENCE-ID`) are not applied; the master event's rule is expanded and
  `EXDATE` exceptions are honoured. Unsupported RRULE parts (e.g. `BYWEEKNO`, `BYSETPOS`, monthly
  ordinal `BYDAY` like `2MO`) fall back to the base pattern.
- `RDATE` extra dates are not expanded.
- WorkManager's minimum periodic interval is 15 minutes; the 5-minute option is honoured by the
  in-app loop while the app is running (which, on a desk phone, is always).

## License

MIT
