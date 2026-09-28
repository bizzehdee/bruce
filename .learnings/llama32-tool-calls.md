# Llama 3.2 1B and tool calls

Observed 2026-09-29 (host llama-server and the Xperia 1 II); details in
research/experiments/tool-calling-2026-09-28/README.md, addendum.

- It writes malformed calls (`"parameters": {"}}`) in about a third of cases, in its own format,
  even through llama.cpp's grammar path. Bruce refuses them as invalid arguments.
- llama.cpp's templates parse stored tool-call arguments as JSON; one bad call left in the history
  made every later turn fail to format. Bruce now sends such arguments as `{}`.
- While skills are offered it calls the same skill again after every result. Bruce answers a
  repeated call with the earlier result and offers no skills for the rest of the turn.
- A prompt that cannot be formatted is a generation failure, not "no model loaded".

Read when a model's tool calls fail in the app, or when adding a model to recommendations.
