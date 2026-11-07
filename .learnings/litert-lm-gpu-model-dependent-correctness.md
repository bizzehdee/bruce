LiteRT-LM's GPU delegate (OpenCL/`LITERT_CL`, confirmed loading cleanly — all nodes accepted,
no exception) gives **wrong output for one model and correct output for another**, on the same
Pixel 11 (Tensor G6, PowerVR C-Series GPU) that already has a confirmed PowerVR/Vulkan
correctness bug for llama.cpp (`.learnings/gpu-backends-on-test-phones.md`). Tested via
`LiteRtGpuReferenceOutputDeviceTest.kt`, prompt "What is the capital of France?", temperature 0:

| Model | GPU backend | CPU backend |
|---|---|---|
| `gemma-3-270m-it.litertlm` | garbage: `⁇ <unused28><unused1><unused9>...` (raw SentencePiece unused-token ids, never decoded to text) | "Paris" (correct) |
| `gemma-4-E2B-it.litertlm` | "The capital of France is Paris." (correct) | "The capital of France is Paris." (correct) |

So this is not "GPU is broken on this device" categorically — it is model-dependent, the same
character of failure llama.cpp's own Vulkan backend showed on this GPU (most ops pass, some
don't, whole-graph output varies). Not yet isolated which op, layer config, or quantisation
difference between the two models causes the 270M model's garbage output specifically (model
size, head count, or a specific unsupported fused op are all untested candidates).

Consequence: LiteRT-LM's GPU backend cannot be trusted by default for an arbitrary model on this
device class, same conclusion as llama.cpp's GPU backends. `LiteRtBackendSelection.plan()`
already keeps Auto off the GPU/NPU for exactly this kind of unproven-reliability reason — this
confirms that caution was warranted for LiteRT-LM's GPU path too, not just llama.cpp's. Before
ever defaulting any model to LiteRT-LM's GPU, it needs a real-output check per model class, not
just "did it load."

Read before: enabling GPU as a default or Auto choice for `LiteRtBackend`, or benchmarking/
recommending LiteRT-LM's GPU backend.
