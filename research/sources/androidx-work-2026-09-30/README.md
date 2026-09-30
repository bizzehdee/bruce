# androidx.work (WorkManager) for TASK-095

Retrieved 2026-09-30 with curl:

- `maven-metadata.xml`: https://dl.google.com/android/maven2/androidx/work/work-runtime/maven-metadata.xml
  — latest stable 2.12.0.
- `work-runtime-2.12.0.pom` — Apache-2.0. Brings androidx room-runtime 2.7, lifecycle-service and
  livedata 2.6.2, core 1.12, startup-runtime 1.1.1, tracing-ktx 1.2, concurrent-futures-ktx 1.1,
  annotation-experimental 1.4.1, guava listenablefuture 1.0 (an empty-API artifact), jspecify 1.0,
  kotlinx-coroutines-android 1.9 and kotlin-stdlib 2.1.20; Bruce already has newer versions of
  most, which win.
- `osv-work-runtime-2.12.0.json`: https://api.osv.dev/v1/query — no advisories.

Owner's choice (2026-09-30): WorkManager rather than Bruce's own alarm-based scheduling.
