# Size Auto from the phone's RAM, not from free memory (2026-10-02)

`ActivityManager.MemoryInfo.availMem` swings with whatever else is open, and it counts memory
Android only gets back by dropping file pages and swapping. Auto sized from it gave MiniCPM5 2B a
49,152 context on the Pixel 11: the 1.56 GB of mapped weights were dropped and read back from
storage 218,000 times in one reply, and Android killed Bruce (`MemoryLimiter:AnonSwap`) even in the
foreground. Up to 32,768 nothing was read back.

Bruce's real use is the estimate (weights plus KV cache) plus about 0.65 GB: llama.cpp's compute
buffer (60–100 MiB for a 2B model) and the app. Auto now keeps that within 30% of total RAM.
Apps cannot lock memory on Android (RLIMIT_MEMLOCK is 64 KB), so staying small is the only guard.

Measure memory trouble with the process's major faults (`/proc/<pid>/stat` field 12) and `VmSwap`;
swap alone is normal on Android (zram), and the whole phone's swap figure says little about Bruce.
Data: `research/experiments/pixel-chat-replay-2026-10-02/`.
