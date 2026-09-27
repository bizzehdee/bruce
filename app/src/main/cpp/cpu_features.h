#pragma once

#include <cstdint>

namespace bruce {

// Bit layout shared with CpuFeatures.kt.
constexpr uint32_t kCpuArm64 = 1u << 0;
constexpr uint32_t kCpuNeon = 1u << 1;
constexpr uint32_t kCpuFp16 = 1u << 2;
constexpr uint32_t kCpuDotProd = 1u << 3;
constexpr uint32_t kCpuI8mm = 1u << 4;

// Linux arm64 hwcap bits, from the kernel's uapi asm/hwcap.h.
constexpr uint64_t kHwcapAsimd = 1ull << 1;
constexpr uint64_t kHwcapAsimdHp = 1ull << 10;
constexpr uint64_t kHwcapAsimdDp = 1ull << 20;
constexpr uint64_t kHwcap2I8mm = 1ull << 13;

uint32_t decodeCpuFeatures(bool arm64, uint64_t hwcap, uint64_t hwcap2);

}  // namespace bruce
