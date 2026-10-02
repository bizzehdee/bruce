# A Pixel chat that never finished (2026-10-02)

The Pixel 11, MiniCPM5 2B Q4_K_M at context 49,152 (Auto), with Search Wikipedia and Read web
pages on, ran "what happened today in history" for over 20 minutes at about 400% CPU. Stop did
nothing and the 3-minute turn limit never fired. The saved system prompt was written as the reply
began, so the hang came later.

`ChatReplayDeviceTest` (app/src/androidTest) replays the question on another phone with the Pixel's
model, context and skill states, logging each step with its time. Run on the Xperia 1 II.

## What happened

1. search_wikipedia "October 2 history", then fetch_page on the October 2 article: its start
   (2,000 characters of HTML text) was all calendar tables, and the note after it said "Call
   fetch_page again with full set to true to read the whole page".
2. The model asked for the whole page: 25,000 characters, about 8,500 tokens. Reading that prompt
   took 500 s on the Xperia. The time limit fired at 180 s but took effect only when the native
   prompt evaluation returned (552 s): coroutine cancellation cannot reach a blocking JNI call, and
   Stop is only checked between generated tokens.

## Changes, each checked with the replay

| Change | Replay result |
|---|---|
| Native abort (llama_set_abort_callback): Stop and coroutine cancellation abort a prompt being read | Turn ends at 180 s, not 552 s |
| Wikipedia articles read as their TextExtracts plain text | Start of the page is the lead and the first events; still asked for more every time |
| Note after the start reworded, then "more" cut to 8,000 characters | Still asked for more; timed out reading it |
| Note leads with "answer now" (`read-more-test.py`, below) | Seeds 2 and 3 answer from the start of the page |
| The time limit waits while a visible answer streams | Seed 0 (the app's seed) answers too, in 4 minutes |

`read-more-test.py`: the phone's own skill results (`xperia-1-ii-final.replay.jsonl` holds them),
replayed on llama-server, temperature 0.5, 20 seeds, after the start of the October 2 extract:

| Note after the start | Answered | Read more |
|---|---|---|
| "…Answer from this if you can; only if it does not answer the question, call fetch_page again with full set to true to read more." | 7 | 13 |
| "The page goes on for N more characters." | 14 | 6 |
| No note | 19 | 1 |
| "This is enough to answer most questions: answer now. The page goes on for N more characters, which fetch_page with full set to true reads, but only if this does not answer." | 18 | 2 |
| Count only, and no `full` parameter | 15 | 5 |

Final replay (`xperia-1-ii-final.replay.jsonl`, seeds 0, 2 and 3; seeds 1 and 4 did not search):
all three answer with events from the article, in 244 s, 135 s and 181 s. The phone was throttling
after an hour of runs: the last answer streamed at 2.5 tokens per second (16 when cool).
