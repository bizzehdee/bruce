#include "vulkan_devices.h"

#include <map>
#include <mutex>
#include <vector>

#include <vulkan/vulkan.h>

namespace bruce {

namespace {

struct DeviceIdentity {
    uint32_t apiVersion = 0;
    uint32_t vendorId = 0;
};

std::map<std::string, DeviceIdentity> queryDevices() {
    std::map<std::string, DeviceIdentity> versions;
    VkApplicationInfo app{};
    app.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    app.apiVersion = VK_API_VERSION_1_0;
    VkInstanceCreateInfo info{};
    info.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    info.pApplicationInfo = &app;

    VkInstance instance = VK_NULL_HANDLE;
    if (vkCreateInstance(&info, nullptr, &instance) != VK_SUCCESS) {
        return versions;
    }
    uint32_t count = 0;
    vkEnumeratePhysicalDevices(instance, &count, nullptr);
    std::vector<VkPhysicalDevice> devices(count);
    vkEnumeratePhysicalDevices(instance, &count, devices.data());
    for (VkPhysicalDevice device : devices) {
        VkPhysicalDeviceProperties properties{};
        vkGetPhysicalDeviceProperties(device, &properties);
        versions[properties.deviceName] = {properties.apiVersion, properties.vendorID};
    }
    vkDestroyInstance(instance, nullptr);
    return versions;
}

DeviceIdentity deviceIdentity(const std::string &deviceName) {
    static std::once_flag queried;
    static std::map<std::string, DeviceIdentity> devices;
    std::call_once(queried, [] { devices = queryDevices(); });
    auto found = devices.find(deviceName);
    return found == devices.end() ? DeviceIdentity{} : found->second;
}

}  // namespace

uint32_t vulkanDeviceApiVersion(const std::string &deviceName) { return deviceIdentity(deviceName).apiVersion; }

uint32_t vulkanDeviceVendorId(const std::string &deviceName) { return deviceIdentity(deviceName).vendorId; }

}  // namespace bruce
