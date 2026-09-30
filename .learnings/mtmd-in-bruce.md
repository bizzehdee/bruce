# Images through llama.cpp's mtmd in Bruce

Established: 2026-09-30, TASK-082. Evidence: `research/experiments/mtmd-android-2026-09-30.md`.

- Built as llama.cpp's standalone `mtmd` library (`LLAMA_BUILD_MTMD=ON`), with video off: video
  starts ffmpeg as a subprocess. The import check refuses process-starting functions in
  libbruce and libmtmd.
- `mtmd_input_text` has a `text_len` field after `text`; set its fields by name. A positional
  initialiser from an older layout silently tokenised one character.
- Route mtmd's log with `mtmd_log_set`, or its errors vanish on Android.
- Prompts with images are evaluated whole (`evaluatePromptWithImages`); the prompt cache is marked
  untracked afterwards, so the next prompt starts from a cleared context and generated tokens are
  not added to the cache.
- Kotlin decodes images (ImageDecoder, EXIF-aware), stores a JPEG copy up to 1024 px, and sends
  RGB bytes; mtmd's own file decoding (stb, ffmpeg for webp) is not used.
- Memory: the estimate counts the projector's file size only; a SmolVLM image turn measured well
  above the estimate. Measure before relying on the figure for large projectors.
