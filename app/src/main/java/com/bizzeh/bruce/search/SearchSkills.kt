package com.bizzeh.bruce.search

import com.bizzeh.bruce.skills.Capability
import com.bizzeh.bruce.skills.DenialCode
import com.bizzeh.bruce.skills.InputSchema
import com.bizzeh.bruce.skills.Parameter
import com.bizzeh.bruce.skills.ParameterType
import com.bizzeh.bruce.skills.Skill
import com.bizzeh.bruce.skills.SkillOutcome
import com.bizzeh.bruce.skills.SkillRequirement
import com.bizzeh.bruce.skills.SkillState

/**
 * The file search skill (TASK-097): the passages of the user's granted files that best match a
 * question, each with its path, so the model can answer from them or read the file with read_file.
 * Offered only once something is indexed; the passages are the user's files, passed as untrusted
 * data like any skill result. Off by default, like the file skills.
 */
class SearchSkills(private val search: FileSearch) {
    fun create(): List<Skill> = listOf(searchFiles())

    private fun searchFiles() = Skill(
        id = "search_my_files",
        version = 1,
        description = "Search the text of the user's granted files by meaning. Returns the best passages with each file's path.",
        input = InputSchema(listOf(Parameter(QUERY, ParameterType.STRING, "What to look for, as a question or a few words", maxLength = MAX_QUERY))),
        capabilities = setOf(Capability.FILE_READ),
        defaultState = SkillState.DECLINED,
        requires = setOf(SkillRequirement.FILE_INDEX),
    ) { arguments ->
        val query = arguments.string(QUERY)?.trim().orEmpty().ifEmpty { return@Skill SkillOutcome.Failed(DenialCode.INVALID_ARGUMENTS, "Say what to look for.", retryable = true) }
        val hits = search.search(query, RESULTS).filter { it.score >= MIN_SCORE }
        if (hits.isEmpty()) return@Skill SkillOutcome.Done("Nothing in the user's files matches that.")
        SkillOutcome.Done(hits.joinToString("\n\n") { "From ${it.path}:\n${it.text}" })
    }

    private companion object {
        const val QUERY = "query"
        const val MAX_QUERY = 500
        /** Three passages, about 900 tokens, keep room in a small context. */
        const val RESULTS = 3
        /**
         * Right notes scored 0.35 and up, questions no note answers 0.18 at most
         * (research/experiments/embedding-models-2026-09-30, threshold-results.json).
         */
        const val MIN_SCORE = 0.25f
    }
}
