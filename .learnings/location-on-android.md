# Getting the phone's location

Established: 2026-09-30, TASK-091, Xperia 1 II (Android 12, Google location services).

- Location is granted as app-op mode "foreground": with nothing of Bruce's on screen, requests
  are rejected and return null at once (`appops get com.bizzeh.bruce` shows `rejectTime`). The
  skill therefore works only while Bruce is on screen.
- A request made as the activity starts can still be rejected; the app counts as foreground a
  moment after the screen resumes (seen in a device test; it waits 2 s).
- `getCurrentLocation` only delivers a fresh fix. Indoors none came in 30 s, from the platform
  call or LocationManagerCompat alike, while the last known location (fused, network) was a few
  minutes old; a minute earlier both answered in about 2 s because a fix had just been made.
  Bruce falls back to a last known location up to 15 minutes old and says how old it is.
- Do not judge a location API by one run: whether a fix is fresh decides the result. Compare
  variants in the same run (see the elimination in this task's history).
