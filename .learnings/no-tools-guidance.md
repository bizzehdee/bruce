# Do not tell a model to use tools it has not been given

Established: 2026-10-01, model-binning host screen (TASK-108), fixed in c3ba23b.

With every skill off, Bruce's system prompt still said "Use a tool for anything a tool can look
up… do not guess those". Gemma 4 E2B then answered "What time is it?" with made-up tool code
(`<tool_code> print(datetime.now()) </tool_code>`), which the user would see as the reply, and
LFM2.5-2.6B wrote its own tool-call markup with invented tool names. With no skill offered the
system prompt now says the model has no tools, cannot check the time, date or phone status, and
should say so rather than guess or write tool calls (`BruceRuntime.NO_SKILLS_GUIDANCE`).

Results in `research/experiments/model-bins-2026-09-30/` taken before c3ba23b used the old
skills-off prompt; the skills-off cases were run again on the host and the phones.
