# Device tests that wipe the app's data

Observed 2026-09-29 on the Xperia 1 II.

`MainActivityDeviceTest.setUp` calls `DataReset.clearAll()` before every test. It deletes the
installed models, chats, memory, grants, skill states and every setting, including a Hugging Face
sign-in. Running the class with `am instrument` (which otherwise keeps app data) wipes a phone
that was set up for manual testing. Model files kept in `files/test-models` survive and can be
copied back with `run-as com.bizzeh.bruce cp files/test-models/<file> files/models/<file>`.

Run it only on a phone whose app state does not matter, or restore afterwards.

The same run showed why the class had started failing: its model, stories260K, reads roughly one
token per character, so the skills' descriptions (1,870 tokens) overfilled its 2,048-token context
and every send ended in "conversation too long". The test now declines all skills in `setUp`.

Read before running device tests on a phone someone uses, or when a stories260K test fails
with no reply.
