# The build must be memory-capped

Established: 2026-09-28.

On 2026-09-27 at 23:46 the kernel OOM killer killed a `java` process holding 10 GB of
anonymous memory on the 31 GB, 32-core development machine, and the Claude Code session
died with it. At the time two Gradle daemons were running (the normal one and a second
with a separate `GRADLE_USER_HOME` for a cold-cache check), Ninja defaulted to about one
native compile per core, the Kotlin daemon had no heap cap, and Chrome was using memory
too. The kernel log does not say which Java process it was.

Caps now in place:
- `gradle.properties`: daemon `-Xmx3g -XX:MaxMetaspaceSize=1g`, Kotlin daemon `-Xmx2g`,
  `org.gradle.workers.max=8`.
- `app/src/main/cpp/CMakeLists.txt`: Ninja job pools of 8 compiles and 4 links.

`/tmp` on this machine is tmpfs, so files there are held in RAM. During the crash, about
4.7 GB of scratch files sat there (1.6 GB of benchmark models, a 1.3 GB cold-cache Gradle
home, a Python virtual environment, a Rust tool build). Put large downloads and Gradle
homes on disk, not under `/tmp`. The owner's rule (2026-09-28): never use tmpfs for anything;
scratch files go in the git-ignored `build/scratch/`.

Profiled after the caps (warm Gradle cache, full native rebuild): at most 8 compiler
processes ran at once, and available memory fell from about 14 GB to 4.6 GB at the lowest.

Also: never leave a second Gradle daemon running. After a build with a different
`GRADLE_USER_HOME`, run `./gradlew --stop` with that same home, or use `--no-daemon`.

Evidence: `journalctl -k`, "Out of memory: Killed process 482040 (java) ... anon-rss:10187148kB".
