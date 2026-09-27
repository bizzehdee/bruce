#include <gtest/gtest.h>

#include "cpu_features.h"

using namespace bruce;

TEST(DecodeCpuFeatures, NotArm64ReportsNothing) {
    EXPECT_EQ(0u, decodeCpuFeatures(false, ~0ull, ~0ull));
}

TEST(DecodeCpuFeatures, BaselineArm64WithoutSimd) {
    EXPECT_EQ(kCpuArm64, decodeCpuFeatures(true, 0, 0));
}

TEST(DecodeCpuFeatures, Armv80WithNeonOnly) {
    EXPECT_EQ(kCpuArm64 | kCpuNeon, decodeCpuFeatures(true, kHwcapAsimd, 0));
}

TEST(DecodeCpuFeatures, Armv82WithFp16AndDotProd) {
    const uint64_t hwcap = kHwcapAsimd | kHwcapAsimdHp | kHwcapAsimdDp;
    EXPECT_EQ(kCpuArm64 | kCpuNeon | kCpuFp16 | kCpuDotProd, decodeCpuFeatures(true, hwcap, 0));
}

TEST(DecodeCpuFeatures, I8mmComesFromHwcap2) {
    EXPECT_EQ(kCpuArm64 | kCpuI8mm, decodeCpuFeatures(true, 0, kHwcap2I8mm));
    EXPECT_EQ(kCpuArm64, decodeCpuFeatures(true, kHwcap2I8mm, 0));
}

TEST(DecodeCpuFeatures, UnrelatedBitsAreIgnored) {
    EXPECT_EQ(kCpuArm64, decodeCpuFeatures(true, 1ull << 0, 1ull << 0));
}
