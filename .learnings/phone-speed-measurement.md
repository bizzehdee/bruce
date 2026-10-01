# Measuring speed on the test phones: cores, screen state and noise

Established: 2026-10-01 (model binning, TASK-105). Data: `research/experiments/model-bins-2026-09-30/speed/`.

- **Off screen, a test process does not get every core.** An instrumentation run with no
  activity is in the `foreground` cpuset: the Pixel 11 allows cores 0–5 (its 4.1 GHz core 6 is
  left out), the XZ Premium 0–3 and 6–7 (two of its four big cores left out); the Xperia 1 II
  allows all 8. With an activity on screen the process is `top-app` and gets every core. Benchmarks
  must put the app on screen (`ModelBinDeviceTest -e onscreen true`, debug-only
  `BenchmarkScreenActivity`); MainActivity cannot be used, because it loads the owner's model.
- **The Pixel 11 is noisy.** The same test (Qwen3-0.6B Q8_0, 3 threads, on screen) gave 37.7 tokens
  a second in one run and 61.6 fifteen minutes later; within one run, 5 threads ranged 27–61. The
  Xperia 1 II repeats within a few per cent. Take several repeats and report the spread.
- **Thread counts (generation, on screen, median of 5):** Xperia 1 II: 2–4 threads equal for
  Qwen3-0.6B Q8_0 (32), 4 best for Qwen3-1.7B Q4_K_M (19.8); 5 or more slower. Pixel 11: 3 threads
  best for Qwen3-0.6B (61.6), 5 best for Qwen3-1.7B (15.5); 6 threads collapsed (13.8 for 0.6B).
  Bruce's default then (cores above the slowest cluster: 4 and 5) was at or near the best on
  both. Owner, 2026-10-01: the default is now 4 threads (every core on a phone with fewer), and
  the user can change it in Settings.
- **The Pixel 11 is not faster than the Xperia 1 II on a 1.7B Q4_K_M model** (15.5 against 19.8
  tokens a second, prompt 65–76 against 76), although it loads the armv9 CPU code (SVE, i8mm).
  Cause not established.
- **On USB power the XZ Premium's battery stays at 40–41 °C,** above the harness's 40 °C
  cool-down limit, so `ModelBinDeviceTest` waited its full 20-minute cap before every case: one
  model's quick pass took hours. Pass `-e cool_wait_s 120` on that phone; `battery_c` is still
  recorded with every result.
- **Transient slow runs happen on the Xperia 1 II too.** Three models in a row (gemma-2-2b
  IQ4_XS, Qwen3.5-2B Q6_K, LFM2.5-2.6B Q4_0) measured 2.4–4 tokens a second where the Pixel gave
  6.8–11.2, while the next model ran at its normal speed. Rerun any result far from the other
  phones before binning on it.
