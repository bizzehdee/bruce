# Choosing "the most popular" small models from the Hub

Established: 2026-09-30, TASK-103. Evidence and scripts: `research/experiments/model-bins-2026-09-30/`.

- **Download counts on one repository are not popularity.** The top 1–3B GGUF repository by
  downloads (2.5 million) was a single author's experimental model with one other copy. Grouping
  by base model and requiring copies from two or more publishers removed 958 of 1,862
  base models, and left the known families (Qwen, Gemma, LFM, Llama, SmolLM, MiniCPM) on top.
- **A `num_parameters` filter lets in parts of big models.** 66 repositories under "3B" were 27B
  models listed by a draft or layer-split file; the base model's own name (`27B`, `E4B`) catches
  them.
- **A third of GGUFs up to 3B are not chat models** (embedding, reranking, speech, OCR, no chat
  template). The task tag misses many: check the architecture and the chat template too.
- **Bruce gives phones without DOTPROD 2–3-bit files.** `ModelFit.rank` orders by speed band before
  precision, and the baseline speed constant makes only files under about 0.39 GB "fast", so the
  XZ Premium picks IQ2_M for a 0.8B model and Q2_K–IQ2_XS for 1.5–2.6B ones. Whether that hurts
  answers is measured in TASK-108.
- **Publishers spell float files "fp16"** (Qwen's own repositories); Bruce's name parser now
  maps it to F16.
