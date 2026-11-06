# Hugging Face Hub API: discovering LiteRT-LM models

Retrieved 2026-11-06. TASK-120 research: whether the Hub API can discover
`.litertlm` repositories the same way Bruce already discovers GGUF ones
(`filter=gguf`), for the LiteRT pipeline's Hugging Face integration.

URLs read:
- `https://huggingface.co/api/models?filter=litert-lm&sort=downloads&direction=-1&limit=5&expand[]=siblings&expand[]=tags`
- `https://huggingface.co/api/models?filter=litert-lm&search=gemma&limit=3&expand[]=siblings`
- `https://huggingface.co/api/models/litert-community/gemma-4-E2B-it-litert-lm?expand[]=siblings&expand[]=tags`

## `filter=litert-lm` works like `filter=gguf`

Every result's `tags` array carries the literal string `"litert-lm"`. Top
results by downloads: `litert-community/gemma-4-E2B-it-litert-lm` (1,381,472),
`litert-community/gemma-4-E4B-it-litert-lm` (448,983),
`litert-community/embeddinggemma-2-740m-litert-lm` (163,641),
`litert-community/Qwen2.5-1.5B-Instruct` (91,762),
`litert-community/DeepSeek-R1-Distill-Qwen-1.5B` (69,593). `search=` combines
with the filter normally (`search=gemma` narrowed to three Gemma repositories).
267 models carry this tag as of this check (per the website's
`?library=litert-lm` filter count, not re-verified against the API's own total).

## No GGUF-equivalent parsed metadata exists

GGUF repositories get a Hub-computed `gguf` object in the search response
(`architecture`, `total` parameter count, `context_length`, `chat_template`,
`bos_token`, `eos_token` — see `expand[]=gguf`). **No equivalent object exists
for `litert-lm`.** The fields actually present on a `litert-lm`-tagged result
are only: `_id`, `id`, `downloads`, `tags`, `siblings`, `trendingScore`
(search only). `architecture`, parameter count, context length and chat
template are not reported by the Hub for this format — Bruce cannot read
them from the search response the way it does for GGUF. Any of
`HubModel`'s GGUF-sourced fields that are reused for `.litertlm` results must
be left null, not guessed or filled from `tags`.

## File listing is per-repository, extensions vary

A `litert-lm`-tagged repository does not necessarily contain only
`.litertlm` files. From `litert-community/gemma-4-E2B-it-litert-lm`'s 14
siblings: 9 `.litertlm` files (`gemma-4-E2B-it.litertlm`,
`gemma-4-E2B-it-gpu.litertlm`, `gemma-4-E2B-it-web.litertlm`,
`gemma-4-E2B-it_Google_Tensor_G5.litertlm`,
`gemma-4-E2B-it_Google_Tensor_G6.litertlm`,
`gemma-4-E2B-it_NPU_Uncompiled.litertlm`,
`gemma-4-E2B-it_Qualcomm_QCS8275.litertlm`, `gemma-4-E2B-it_intel_LNL.litertlm`,
`gemma-4-E2B-it_intel_PTL.litertlm`), one `.task` file
(`gemma-4-E2B-it-web.task`), and support files (`.gitattributes`, `README.md`,
`chat_template.jinja`, an image, a notebook). Other `litert-lm`-tagged repos
(Qwen2.5, DeepSeek-R1-Distill, `litert-community/Gemma3-1B-IT`) also carry
`.task` and `.tflite` files (and in `Gemma3-1B-IT`'s case a `.pb` file,
`Gemma3-1B-IT_Google_Tensor_G6.pb`) alongside `.litertlm` ones. **Any client
code must filter siblings by the `.litertlm` suffix itself**, the same way
Bruce's existing `ggufSiblings()` filters by `.gguf` — tag-matching alone is
not enough to know which files in a matched repository are the ones Bruce
can actually load.

## Implication for Bruce's `HubClient`

`HubClient.search()`'s GGUF-specific logic (hardcoded `filter=gguf`, the
`gguf` expand field, architecture/parameter/context/template extraction) does
not carry over as-is. A LiteRT-LM search needs: `filter=litert-lm` instead of
`filter=gguf`; siblings filtered by `.litertlm` instead of `.gguf`; and
`architecture`/`parameterCount`/`contextLength`/`chatTemplate`/`bosToken`/
`eosToken` left null (the Hub does not supply them for this format) rather
than fabricated from file names or tags.
