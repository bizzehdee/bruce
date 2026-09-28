# Android 17 grants ACCESS_LOCAL_NETWORK to Bruce by itself

Observed 2026-09-28 on the Pixel 11 (Android 17), Bruce at targetSdk 36.

`dumpsys package com.bizzeh.bruce` lists `android.permission.ACCESS_LOCAL_NETWORK` among the
requested permissions and as a granted runtime permission with the flag `REVOKE_WHEN_REQUESTED`,
although no manifest (Bruce's or a library's) asks for it. Android adds it to apps that hold
`INTERNET` and target an older SDK. Its system label is "access local network devices".

- Bruce does not need it. The Permissions screen names it and says it can be turned off.
- Raising `targetSdk` to 37 should stop the automatic grant (unverified; `targetSdk` is held at 36
  by Robolectric, see robolectric-max-sdk.md).

Read when the Permissions screen shows a permission Bruce's manifest does not declare, or when
raising `targetSdk`.
