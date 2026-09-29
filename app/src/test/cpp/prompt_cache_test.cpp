#include "prompt_cache.h"

#include <gtest/gtest.h>

using bruce::checkpointPosition;
using bruce::planReuse;

namespace {
const std::vector<int32_t> kCached = {1, 2, 3, 4, 5, 6};
}

TEST(PromptCache, CuttableMemoryKeepsTheSharedPrefix) {
    const auto plan = planReuse(kCached, {1, 2, 3, 9, 9}, true, -1);
    EXPECT_EQ(3, plan.start);
    EXPECT_FALSE(plan.restoreCheckpoint);
}

TEST(PromptCache, TheLastPromptTokenIsAlwaysDecoded) {
    EXPECT_EQ(5, planReuse(kCached, kCached, true, -1).start);
    EXPECT_EQ(2, planReuse(kCached, {1, 2, 3}, true, -1).start);
    EXPECT_EQ(0, planReuse(kCached, {}, true, -1).start);
}

TEST(PromptCache, NothingSharedStartsAgain) {
    EXPECT_EQ(0, planReuse(kCached, {7, 8}, true, -1).start);
    EXPECT_EQ(0, planReuse({}, {7, 8}, false, -1).start);
}

TEST(PromptCache, UncuttableMemoryReusesAnExactExtension) {
    const auto plan = planReuse(kCached, {1, 2, 3, 4, 5, 6, 7, 8}, false, -1);
    EXPECT_EQ(6, plan.start);
    EXPECT_FALSE(plan.restoreCheckpoint);
}

TEST(PromptCache, UncuttableMemoryRestoresACheckpointWithinTheSharedPrefix) {
    const auto plan = planReuse(kCached, {1, 2, 3, 4, 9, 9}, false, 3);
    EXPECT_EQ(3, plan.start);
    EXPECT_TRUE(plan.restoreCheckpoint);
}

TEST(PromptCache, UncuttableMemoryWithoutAUsableCheckpointStartsAgain) {
    EXPECT_EQ(0, planReuse(kCached, {1, 2, 9}, false, 4).start);
    EXPECT_EQ(0, planReuse(kCached, {1, 2, 9}, false, -1).start);
    EXPECT_EQ(0, planReuse(kCached, {1, 2, 9}, false, 0).start);
    // The same prompt again: the context holds more than the prompt minus its last token.
    EXPECT_EQ(0, planReuse(kCached, kCached, false, -1).start);
    EXPECT_EQ(4, planReuse(kCached, kCached, false, 4).start);
}

TEST(PromptCache, CheckpointsAreForUncuttableMemoryOnly) {
    EXPECT_EQ(92, checkpointPosition(0, 100, false, 8));
    EXPECT_EQ(-1, checkpointPosition(0, 100, true, 8));
    EXPECT_EQ(-1, checkpointPosition(95, 100, false, 8));
    EXPECT_EQ(-1, checkpointPosition(0, 5, false, 8));
}
