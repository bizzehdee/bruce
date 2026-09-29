package com.bizzeh.bruce.skills.automatic

import com.bizzeh.bruce.skills.Capability
import com.bizzeh.bruce.skills.DenialCode
import com.bizzeh.bruce.skills.InputSchema
import com.bizzeh.bruce.skills.Parameter
import com.bizzeh.bruce.skills.ParameterType
import com.bizzeh.bruce.skills.Skill
import com.bizzeh.bruce.skills.SkillOutcome
import com.bizzeh.bruce.skills.SkillState
import com.bizzeh.bruce.ui.Format
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class BatteryStatus(val percent: Int?, val charging: Boolean?, val temperatureCelsius: Double?)

/** Only what identifies the kind of phone, never the phone itself: no serials or other identifiers. */
data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val sdk: Int,
    val processor: String?,
    val totalMemoryBytes: Long,
)

data class StorageStatus(val totalBytes: Long, val freeBytes: Long)

enum class Transport { WIFI, MOBILE, ETHERNET, OTHER }

/** Whether the phone is online and how; never network names, addresses or hardware identifiers. */
data class NetworkStatus(val online: Boolean, val transport: Transport?)

/** Reads the phone's state for the automatic skills (AndroidPhoneReaders on a phone). */
interface PhoneReaders {
    fun now(): ZonedDateTime
    fun battery(): BatteryStatus
    fun device(): DeviceInfo
    fun storage(): StorageStatus
    fun network(): NetworkStatus
}

/**
 * The six automatic skills (product spec §29): read-only, no Android runtime permissions, Accepted
 * on a fresh install. Their ids are those measured in TASK-033; the time and battery descriptions
 * are the owner's shorter wording (2026-09-29).
 */
object AutomaticSkills {
    fun create(phone: PhoneReaders): List<Skill> = listOf(
        skill("get_datetime", "Get the date/time/day of week on the current device", Capability.TIME) { dateTime(phone.now()) },
        Skill(
            id = "calculate",
            version = 1,
            description = "Evaluate an arithmetic expression exactly. Supports + - * / ^ %, parentheses and decimals.",
            input = InputSchema(listOf(Parameter("expression", ParameterType.STRING, "The arithmetic expression, for example (12.5 * 4) / 3", maxLength = Calculator.MAX_LENGTH))),
            capabilities = emptySet(),
            defaultState = SkillState.ACCEPTED,
        ) { arguments ->
            when (val result = Calculator.evaluate(arguments.string("expression").orEmpty())) {
                is Calculation.Value -> SkillOutcome.Done(Calculator.format(result.value))
                is Calculation.Error -> SkillOutcome.Failed(DenialCode.TOOL_FAILED, "Cannot calculate that: ${result.reason}.", retryable = true)
            }
        },
        skill("get_battery_status", "get the devices battery level, temperature and charge state", Capability.BATTERY) { battery(phone.battery()) },
        skill("get_device_info", "Get the phone's manufacturer, model, Android version, processor and total memory.", Capability.DEVICE) { device(phone.device()) },
        skill("get_storage_status", "Get the phone's total, used and free storage space.", Capability.STORAGE_STATUS) { storage(phone.storage()) },
        skill("get_network_status", "Get whether the phone is online and whether it uses Wi-Fi or mobile data.", Capability.NETWORK_STATUS) { network(phone.network()) },
    )

    private fun skill(id: String, description: String, capability: Capability, read: () -> String) =
        Skill(id, 1, description, InputSchema(), setOf(capability), SkillState.ACCEPTED) { SkillOutcome.Done(read()) }

    private val DATE_TIME = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm:ss", Locale.ENGLISH)

    fun dateTime(now: ZonedDateTime): String = "${DATE_TIME.format(now)}, time zone ${now.zone.id} (UTC${now.offset.id.replace("Z", "+00:00")})"

    fun battery(status: BatteryStatus): String = listOfNotNull(
        status.percent?.let { "Battery level: $it%" } ?: "Battery level: unknown",
        status.charging?.let { if (it) "Charging" else "Not charging" },
        status.temperatureCelsius?.let { "Temperature: ${String.format(Locale.ROOT, "%.1f", it)} °C" },
    ).joinToString("\n")

    fun device(info: DeviceInfo): String = listOfNotNull(
        "Manufacturer: ${info.manufacturer}",
        "Model: ${info.model}",
        "Android: ${info.androidVersion} (API ${info.sdk})",
        info.processor?.let { "Processor: $it" },
        "Memory: ${Format.bytes(info.totalMemoryBytes)}",
    ).joinToString("\n")

    fun storage(status: StorageStatus): String =
        "Total: ${Format.bytes(status.totalBytes)}\nUsed: ${Format.bytes((status.totalBytes - status.freeBytes).coerceAtLeast(0))}\nFree: ${Format.bytes(status.freeBytes)}"

    fun network(status: NetworkStatus): String {
        if (!status.online) return "Offline: no working internet connection."
        val how = when (status.transport) {
            Transport.WIFI -> "Wi-Fi"
            Transport.MOBILE -> "mobile data"
            Transport.ETHERNET -> "Ethernet"
            Transport.OTHER, null -> "another kind of connection"
        }
        return "Online, using $how."
    }
}
