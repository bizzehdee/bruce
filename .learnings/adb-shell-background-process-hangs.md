# Starting a background process with `adb shell` hangs unless `-T` is used

Established: 2026-09-28, adb from platform-tools on Fedora, both test phones.

`adb shell "nohup server … > log 2>&1 &"` never returns: the background process keeps
adb's pty open. Redirecting stdin from `/dev/null` as well does not help. Either of these
returns at once:

- `adb shell -T "nohup server < /dev/null > log 2>&1 &"` (no pty);
- `adb shell "setsid nohup server < /dev/null > log 2>&1 &"`.

Symptom: a script that starts a phone-side server and then talks to it stalls after the
start line, while the server's own log shows it listening and receiving no requests.

Evidence: `timeout 15 adb shell "nohup sleep 30 … &"` exits 124 with and without the stdin
redirect; the `-T` and `setsid` forms exit 0.

**`pkill -f` kills its own shell.** `adb shell "pkill -f bruce-tc/llama-server"` matches the
`sh -c` running that very command, kills it, and leaves the server running. In TASK-033 this
left one server alive for hours: every later server failed to bind the port, and every
"model" in the phone results was really that first model (identical output and prompt token
counts across models). Kill by process name (`pkill llama-server`), and check the server
reports the expected model (`/props` `model_path`) before measuring. The same trap applies
to `pkill -f` on the host inside a shell whose command line contains the pattern.

On the Pixel 11 (Android 17), `-T` alone did not return; `setsid` together with `-T` did, so
the TASK-033 phone runner uses both.
