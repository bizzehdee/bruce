# Robolectric 4.17 cannot run targetSdk 37

Established: 2026-09-27.

With `targetSdk = 37`, every Robolectric test fails with
`NoSuchMethodException: android.hardware.input.InputManager.getInstance()`
from `Espresso.onIdle`. With `targetSdk = 36` the same tests pass.

Consequence: `targetSdk` stays at 36 until a Robolectric release supports
SDK 37. Lint reports `OldTargetApi` meanwhile. `compileSdk` is 37 because
`androidx.core:core-ktx:1.19.1` requires it; that does not affect Robolectric.

Re-check when upgrading Robolectric.
