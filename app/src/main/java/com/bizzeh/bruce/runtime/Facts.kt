package com.bizzeh.bruce.runtime

/**
 * Remembered facts as the model reads them (TASK-047). Facts are saved in the user's words ("I live
 * in Leeds"); in the prompt a first-person fact read by a small model is often taken as the
 * model's own ("My name is Sam", said by Bruce). Written about "the user" they are not (Qwen3.5-0.8B,
 * host test 2026-09-29), so common first-person forms are turned round; anything else is quoted.
 */
object Facts {
    private val BE = Regex("^(i am|i'm)\\b", RegexOption.IGNORE_CASE)
    private val HAVE = Regex("^(i have|i've)\\b", RegexOption.IGNORE_CASE)
    private val MY = Regex("^my\\b", RegexOption.IGNORE_CASE)
    private val I_VERB = Regex("^i ([a-z]+)\\b", RegexOption.IGNORE_CASE)
    private val MY_INSIDE = Regex("\\bmy\\b", RegexOption.IGNORE_CASE)
    private val ME_INSIDE = Regex("\\bme\\b", RegexOption.IGNORE_CASE)
    private val MODALS = setOf("can", "can't", "cannot", "will", "won't", "would", "should", "could", "must", "might", "may", "did", "didn't", "was")

    fun aboutUser(fact: String): String {
        val text = fact.trim()
        val turned = when {
            BE.containsMatchIn(text) -> BE.replace(text, "The user is")
            HAVE.containsMatchIn(text) -> HAVE.replace(text, "The user has")
            MY.containsMatchIn(text) -> MY.replace(text, "The user's")
            I_VERB.containsMatchIn(text) -> {
                val verb = I_VERB.find(text)!!.groupValues[1]
                I_VERB.replace(text, "The user ${thirdPerson(verb)}")
            }
            else -> return "The user said: \"$text\""
        }
        return ME_INSIDE.replace(MY_INSIDE.replace(turned, "their"), "them")
    }

    private fun thirdPerson(verb: String): String {
        val lower = verb.lowercase()
        return when {
            lower in MODALS -> verb
            lower == "do" -> "does"
            lower.endsWith("y") && lower.length > 2 && lower[lower.length - 2] !in "aeiou" -> verb.dropLast(1) + "ies"
            lower.endsWith("s") || lower.endsWith("sh") || lower.endsWith("ch") || lower.endsWith("x") || lower.endsWith("o") -> verb + "es"
            else -> verb + "s"
        }
    }
}
