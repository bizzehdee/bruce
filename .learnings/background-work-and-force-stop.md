# Background work, force stops and device tests

Established: 2026-09-30, TASK-094/095, Xperia 1 II.

- `am instrument` ends by force-stopping the app. A force stop cancels its JobScheduler jobs, so
  WorkManager work queued by a device test is gone until the app next starts, when WorkManager
  says "Application was force-stopped, rescheduling". Work queued in the last moment before the
  stop can be lost entirely (WorkManager saves it on its own thread).
- Bruce therefore queues every scheduled task again from its own table at app start and after a
  reboot or update, as it does reminders' alarms; replacing queued work is harmless.
- An exact alarm set by a device test did survive the test's end and fired on time.
- To test a scheduled run on a phone: schedule from the test, wait a few seconds before it ends,
  start Bruce once, go Home, and watch `adb logcat` for `BruceTasks` and `WM-WorkerWrapper`.
- The app's own model restore at start and a scheduled run can race to load the model; a run
  waits for a load under way before starting one.
