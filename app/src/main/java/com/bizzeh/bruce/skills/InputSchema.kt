package com.bizzeh.bruce.skills

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

enum class ParameterType(val jsonName: String) { STRING("string"), INTEGER("integer"), NUMBER("number"), BOOLEAN("boolean") }

/** One argument. Bounds apply to its type: [maxLength] to strings, [minimum]/[maximum] to numbers. */
data class Parameter(
    val name: String,
    val type: ParameterType,
    val description: String,
    val required: Boolean = true,
    val maxLength: Int = DEFAULT_MAX_LENGTH,
    val allowed: List<String>? = null,
    val minimum: Double? = null,
    val maximum: Double? = null,
) {
    companion object {
        /** Arguments come from the model; nothing a skill takes needs more. */
        const val DEFAULT_MAX_LENGTH = 1000
    }
}

/** Validated arguments, by name. Optional arguments that were not given are absent. */
class SkillArguments(private val values: Map<String, Any>) {
    fun string(name: String): String? = values[name] as? String
    fun long(name: String): Long? = values[name] as? Long
    fun double(name: String): Double? = values[name] as? Double
    fun boolean(name: String): Boolean? = values[name] as? Boolean

    override fun equals(other: Any?) = other is SkillArguments && other.values == values
    override fun hashCode() = values.hashCode()
    override fun toString() = "SkillArguments($values)"
}

sealed interface ArgumentCheck {
    data class Valid(val arguments: SkillArguments) : ArgumentCheck

    /** [reason] names the problem for the model; it never echoes the raw input. */
    data class Invalid(val reason: String) : ArgumentCheck
}

/**
 * A flat JSON object of primitive arguments: the subset of JSON Schema that Bruce's skills need.
 * The model's arguments are untrusted input, so anything not described here is refused: unknown
 * names, nesting, wrong types, missing required values and out-of-range values. The exception is a
 * skill with no parameters, which ignores whatever object it is sent: nothing in it is read, and
 * small models often send their tools' schema back as arguments (Llama 3.2 1B, 2026-09-29).
 */
data class InputSchema(val parameters: List<Parameter> = emptyList()) {
    init {
        require(parameters.map { it.name }.toSet().size == parameters.size) { "parameter names must be unique" }
    }

    fun check(raw: String): ArgumentCheck {
        if (raw.length > MAX_RAW_LENGTH) return ArgumentCheck.Invalid("arguments too long")
        val json = try {
            val value = JSONTokener(raw.ifBlank { "{}" }).nextValue()
            value as? JSONObject ?: return ArgumentCheck.Invalid("arguments must be a JSON object")
        } catch (e: JSONException) {
            return ArgumentCheck.Invalid("arguments are not valid JSON")
        }
        if (parameters.isEmpty()) return ArgumentCheck.Valid(SkillArguments(emptyMap()))
        val byName = parameters.associateBy { it.name }
        json.keys().forEach { if (it !in byName) return ArgumentCheck.Invalid("unknown argument '${safeName(it)}'") }
        val values = mutableMapOf<String, Any>()
        for (parameter in parameters) {
            val value = json.opt(parameter.name)
            if (value == null || value == JSONObject.NULL) {
                if (parameter.required) return ArgumentCheck.Invalid("missing argument '${parameter.name}'")
                continue
            }
            values[parameter.name] = convert(parameter, value) ?: return ArgumentCheck.Invalid(problem(parameter))
        }
        return ArgumentCheck.Valid(SkillArguments(values))
    }

    /**
     * JSON Schema for the full skill description sent to the model. String lengths are left out:
     * Llama 3.2 1B copied `maxLength` into its calls as an argument, and [check] enforces them anyway.
     */
    fun toJson(): JSONObject = JSONObject()
        .put("type", "object")
        .put(
            "properties",
            JSONObject().apply {
                parameters.forEach { p ->
                    put(
                        p.name,
                        JSONObject().put("type", p.type.jsonName).put("description", p.description).apply {
                            p.allowed?.let { put("enum", JSONArray(it)) }
                            p.minimum?.let { put("minimum", it) }
                            p.maximum?.let { put("maximum", it) }
                        },
                    )
                }
            },
        )
        .put("required", JSONArray(parameters.filter { it.required }.map { it.name }))

    private fun convert(parameter: Parameter, value: Any): Any? = when (parameter.type) {
        ParameterType.STRING -> (value as? String)?.takeIf { it.length <= parameter.maxLength && (parameter.allowed == null || it in parameter.allowed) }
        ParameterType.BOOLEAN -> value as? Boolean
        ParameterType.INTEGER -> when (value) {
            is Int, is Long -> (value as Number).toLong()
            is Double -> value.takeIf { it == Math.floor(it) && !it.isInfinite() && kotlin.math.abs(it) < MAX_SAFE_INTEGER }?.toLong()
            else -> null
        }?.takeIf { inRange(parameter, it.toDouble()) }
        ParameterType.NUMBER -> (value as? Number)?.toDouble()?.takeIf { it.isFinite() && inRange(parameter, it) }
    }

    private fun inRange(parameter: Parameter, value: Double) =
        (parameter.minimum == null || value >= parameter.minimum) && (parameter.maximum == null || value <= parameter.maximum)

    private fun problem(parameter: Parameter): String = buildString {
        append("'${parameter.name}' must be ${if (parameter.type == ParameterType.INTEGER) "an" else "a"} ${parameter.type.jsonName}")
        parameter.allowed?.let { append(" (one of ${it.joinToString()})") }
        if (parameter.type == ParameterType.STRING && parameter.allowed == null) append(" of at most ${parameter.maxLength} characters")
        if (parameter.minimum != null || parameter.maximum != null) append(" between ${parameter.minimum ?: "-∞"} and ${parameter.maximum ?: "∞"}")
    }

    /** Argument names come from the model; only a short, plain form is repeated back. */
    private fun safeName(name: String) = name.filter { it.isLetterOrDigit() || it == '_' }.take(40)

    private companion object {
        const val MAX_RAW_LENGTH = 16 * 1024

        /** Beyond this a JSON number cannot hold every integer exactly. */
        const val MAX_SAFE_INTEGER = 9_007_199_254_740_992.0
    }
}
