# DataStore work is not driven by the coroutine test scheduler

Established: 2026-09-28, androidx DataStore Preferences, kotlinx-coroutines-test.

A file-backed `PreferenceDataStoreFactory.create(scope = backgroundScope)` still reads and
writes the file on its own IO thread. `advanceUntilIdle()` therefore returns before a
launched `edit` has landed or before a flow built on `dataStore.data` has emitted.

- Calling a repository's suspend function directly and awaiting it is fine.
- When a view model launches the write, or its `StateFlow` combines a DataStore flow, wait on
  the observable result instead: `viewModel.state.first { it.step == expected }`, or a
  `CompletableDeferred` completed by a callback. `runTest` then waits in real time.
- Two DataStores on the same file in one process fail; give each fixture its own file or
  one fixture per test.

Evidence: `SetupViewModelTest` (TASK-030) failed three ways with `advanceUntilIdle()`
(state stuck at its initial value, write not visible) and passed after waiting on state.
