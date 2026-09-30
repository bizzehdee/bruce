# llama.cpp multimodal (mtmd) on the phone (TASK-082)

Date: 2026-09-30. llama.cpp 0.5.0 (7fe450e19), Bruce debug build with `-DCMAKE_BUILD_TYPE=Release`.

## Build

- `LLAMA_BUILD_MTMD=ON` builds `tools/mtmd` on its own; `MTMD_VIDEO=OFF`, because video runs
  ffmpeg through `sheredom/subprocess.h`. With video off, `mtmd-helper.cpp` compiles none of the
  subprocess code.
- `libmtmd.so` is 1.3 MB (arm64). The APK grew from about 56 MB to 57.4 MB.
- `check_no_network.cmake` now runs on libbruce and libmtmd and also refuses imports of
  `fork`/`exec*`/`posix_spawn`/`popen`/`system`; both libraries pass.

## Run (Xperia 1 II, Snapdragon 865, 8 GB, Android 12)

Model: ggml-org/SmolVLM-256M-Instruct-GGUF, `SmolVLM-256M-Instruct-Q8_0.gguf` (166.9 MB) with
`mmproj-SmolVLM-256M-Instruct-f16.gguf` (181.2 MB), CPU, 8K context.
Image: llama.cpp's `tools/mtmd/test-1.jpeg` (640x488, a 1969 newspaper front page), stored by
Bruce as a JPEG copy, sent RGB.

| Measure | Value |
|---|---|
| "What is the headline in this image?" | "Men walk on moon." (correct) |
| Prompt with the image | 383 tokens (the image about 150 of them) |
| Prompt evaluation with the image (encode + decode) | about 4.8 s (logcat 09:06:17.3 → 09:06:22.1) |
| Follow-up text question, image encoded again | 405 tokens |
| App PSS after the image turn | 845 MB (native heap 586 MB) |
| Bruce's estimate for model + projector + 8K context | 528 MB |

The follow-up ("What date is on the paper?") answered without the date, which the 256M model
may simply not read; not a Bruce fault.

## Findings

- `mtmd_input_text` is `{text, text_len, add_special, parse_special}`. A brace initialiser written
  for the older three-field layout set `text_len` to 1, so mtmd saw no marker and
  `mtmd_tokenize` returned 1 (bitmap count mismatch). Set the fields by name.
- mtmd logs through its own callback (`mtmd_log_set`); without it errors go to stderr, which
  Android drops.
- A prompt with images cannot reuse the prompt cache: its positions do not line up with text
  tokens. Bruce clears the context before and after, so each turn of a chat with images
  encodes them again (about 5 s per image on this phone for SmolVLM's projector).
- The measured PSS (845 MB) is well above the file-size estimate (528 MB); the app's own base
  and the projector's compute buffers are not in the estimate. Not yet separated.
