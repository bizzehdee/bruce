#include "cpu_features.h"

namespace bruce {

uint32_t decodeCpuFeatures(bool arm64, uint64_t hwcap, uint64_t hwcap2) {
    if (!arm64) {
        return 0;
    }
    uint32_t features = kCpuArm64;
    if (hwcap & kHwcapAsimd) features |= kCpuNeon;
    if (hwcap & kHwcapAsimdHp) features |= kCpuFp16;
    if (hwcap & kHwcapAsimdDp) features |= kCpuDotProd;
    if (hwcap2 & kHwcap2I8mm) features |= kCpuI8mm;
    return features;
}

}  // namespace bruce
