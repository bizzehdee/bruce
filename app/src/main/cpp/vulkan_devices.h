#pragma once

#include <cstdint>
#include <string>

namespace bruce {

// The Vulkan API version the named physical device supports, or 0 if no device has that name
// or Vulkan is unavailable.
uint32_t vulkanDeviceApiVersion(const std::string &deviceName);

// The PCI vendor ID of the named physical device, or 0 if no device has that name or Vulkan is
// unavailable.
uint32_t vulkanDeviceVendorId(const std::string &deviceName);

}  // namespace bruce
