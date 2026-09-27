# Robolectric needs `jdk.internal.access` exported on JDK 17+

Established: 2026-09-27.

Robolectric 4.17 (android-all SDK 36) fails every test on JDK 25 with
`IllegalAccessException: ... cannot access class jdk.internal.access.SharedSecrets
... module java.base does not export jdk.internal.access to unnamed module`,
thrown from `AndroidInterceptors$FileDescriptorInterceptor.setInt`.

Fix: the unit-test JVM gets
`--add-exports=java.base/jdk.internal.access=ALL-UNNAMED`
(`app/build.gradle.kts`, `testOptions.unitTests.all`).

Evidence: all 4 Robolectric tests failed before the flag and passed after it,
with no other change.
