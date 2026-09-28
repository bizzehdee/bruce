# Build a ViewModel after Dispatchers.setMain in Robolectric tests

Observed 2026-09-28 in `GrantsViewModelTest`.

A ViewModel created in a test class field initializer is built before `@Before` calls
`Dispatchers.setMain(...)`. Its `stateIn(viewModelScope, ...)` then starts on the real main looper.
The test waits with `runBlocking` on that same thread, so the upstream flow never runs and the
wait times out, although the database holds the rows (checked: Room's flow re-emitted normally
when collected directly).

Create the ViewModel inside the test or `by lazy`, after `setMain`.

Read when a ViewModel test times out waiting on a StateFlow that should have changed.
