#include "vulkan_devices.h"

#include <map>
#include <mutex>
#include <vector>

#include <vulkan/vulkan.h>

namespace bruce {

namespace {

std::map<std::string, uint32_t> queryDeviceApiVersions() {
    std::map<std::string, uint32_t> versions;
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
        versions[properties.deviceName] = properties.apiVersion;
    }
    vkDestroyInstance(instance, nullptr);
    return versions;
}

}  // namespace

uint32_t vulkanDeviceApiVersion(const std::string &deviceName) {
    static std::once_flag queried;
    static std::map<std::string, uint32_t> versions;
    std::call_once(queried, [] { versions = queryDeviceApiVersions(); });
    auto found = versions.find(deviceName);
    return found == versions.end() ? 0 : found->second;
}

}  // namespace bruce
