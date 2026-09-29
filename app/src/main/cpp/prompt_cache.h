// How much of an already evaluated prompt a new prompt can reuse (TASK-056). Kept free of
// llama.cpp types so it can be unit-tested on the build machine.
#pragma once

#include <cstdint>
#include <vector>

namespace bruce {

struct ReusePlan {
    // Tokens kept in the context; decoding starts at this position.
    int32_t start;
    // Restore the saved checkpoint first: the context could not otherwise be cut back to [start].
    bool restoreCheckpoint;
};

// [cached] is what the context holds, in order; [prompt] the new prompt. [canCut] says whether the
// context's memory can drop its newest positions (plain attention); recurrent, hybrid and
// sliding-window memory cannot, so for them only an exact extension or [checkpoint] (the number
// of tokens a saved checkpoint covers, or -1) can be reused. At least the prompt's last token is
// always decoded, since the next token is sampled from its logits.
ReusePlan planReuse(const std::vector<int32_t> &cached, const std::vector<int32_t> &prompt, bool canCut, int32_t checkpoint);

// Where to save a checkpoint while decoding positions [start, count), or -1 for none: [offset]
// tokens before the end, so the next prompt, which usually differs only after this one's end,
// can restore it.
int32_t checkpointPosition(int32_t start, int32_t count, bool canCut, int32_t offset);

}  // namespace bruce
