# Which small models Bruce recommends, and why (TASK-103 to TASK-106)

Established 2026-10-02. Data and rule: `research/experiments/model-bins-2026-09-30/` (`bins.py`,
`bins.md`, README).

- **8 and 12 GB phones:** Gemma 4 E2B Q3_K_M, MiniCPM5-2B Q4_K_M and Qwen3-1.7B Q5_K_M are
  Recommended: 0.82–0.91 on skill cases, 0.83–0.93 on plain ones, 11.5–18.8 tokens a second, a
  follow-up answer within about 1 s. Qwen3.5-0.8B (and Qwen3.5-2B on the Xperia) are good only with
  skills off.
- **4 GB without dot product (XZ Premium):** nothing is Recommended. Generation is 4.8–6.4 tokens a
  second for 1.2–1.7B models; the processor, not the memory, is the limit. Qwen3-1.7B Q4_K_M is the
  usable choice (skill score 1.0). Budget phones with A55/A75-class cores have dot product and are
  untested; the XZ Premium stands in for Snapdragon 680-class phones only.
- **Ruled out:** Llama 3.2 1B, Qwen2.5 1.5B and its Coder, gemma-2 and gemma-3 (weak skill use and
  invented answers); LFM2.5-2.6B is the most accurate but always thinks (13–38 s to an answer), and
  breaks with thinking forced off.
- **Measure carefully:** host scores matched the phones within a few points, so quality comes from
  the development machine; speed must come from on-screen phone runs, rechecked when one phone is far
  from the others (see `phone-speed-measurement.md`).
