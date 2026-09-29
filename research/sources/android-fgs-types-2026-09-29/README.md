# Android foreground service types (shortService, specialUse, dataSync)

Retrieved 2026-09-29 from https://developer.android.com/develop/background-work/services/fgs/service-types
(summary of the page; quotes as returned).

- shortService: "About 3 minutes", counted from `startForeground()`; the app should stop it before
  then, otherwise `Service.onTimeout()` is called, and a little later the app is cached and, if the
  service has not stopped, gets an ANR. No type-specific permission, only `FOREGROUND_SERVICE`. It
  cannot be started from the background (`ForegroundServiceStartNotAllowedException`).
- specialUse: needs `FOREGROUND_SERVICE_SPECIAL_USE` and a use case declared in the manifest,
  reviewed in the Play Console.
- dataSync: needs `FOREGROUND_SERVICE_DATA_SYNC`; restricted further from Android 15.

Chosen for TASK-048: shortService, since a turn is already capped at 3 minutes and replies are
started by the user with Bruce on screen.
