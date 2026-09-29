#include "prompt_cache.h"

#include <algorithm>

namespace bruce {

ReusePlan planReuse(const std::vector<int32_t> &cached, const std::vector<int32_t> &prompt, bool canCut, int32_t checkpoint) {
    const size_t limit = std::min(cached.size(), prompt.size());
    size_t shared = 0;
    while (shared < limit && cached[shared] == prompt[shared]) {
        ++shared;
    }
    const auto usable = static_cast<int32_t>(prompt.empty() ? 0 : std::min(shared, prompt.size() - 1));
    if (canCut) {
        return {usable, false};
    }
    if (static_cast<size_t>(usable) == cached.size()) {
        return {usable, false};
    }
    if (checkpoint > 0 && checkpoint <= usable) {
        return {checkpoint, true};
    }
    return {0, false};
}

int32_t checkpointPosition(int32_t start, int32_t count, bool canCut, int32_t offset) {
    if (canCut) {
        return -1;
    }
    const int32_t position = count - offset;
    return position > start ? position : -1;
}

}  // namespace bruce
