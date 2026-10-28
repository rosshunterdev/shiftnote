package com.shiftnote.inference

enum class ImproveStyle {
    PARAGRAPH,
    SECTIONS;

    companion object {
        // Unknown or missing stored values fall back to the default style.
        fun fromStored(value: String?): ImproveStyle =
            entries.firstOrNull { it.name == value } ?: PARAGRAPH
    }
}

class PromptAssembler {

    fun assemble(
        role: String,
        style: ImproveStyle,
        generalContext: String,
        clientGoals: String,
        clientContext: String,
        extraInstructions: String,
        note: String,
        keepPlaceholders: Boolean
    ): String {
        val roleText = role.trim().ifBlank { DEFAULT_ROLE }

        val intro = listOfNotNull(
            "You are helping a $roleText write professional support notes.",
            clientGoals.takeIf { it.isNotBlank() }?.let { "Active goals: $it" },
            clientContext.takeIf { it.isNotBlank() }?.let { "Additional context: $it" },
            generalContext.takeIf { it.isNotBlank() }?.let { "General context: $it" }
        ).joinToString("\n")

        val rules = if (keepPlaceholders) RULES + PLACEHOLDER_RULE else RULES

        val format = when (style) {
            ImproveStyle.PARAGRAPH -> PARAGRAPH_FORMAT
            ImproveStyle.SECTIONS -> SECTIONS_FORMAT
        }

        return listOfNotNull(
            intro,
            "Rewrite the note below in professional language for a $roleText.",
            "Rules:\n" + rules.joinToString("\n") { "- $it" },
            "Format:\n$format",
            extraInstructions.takeIf { it.isNotBlank() }
                ?.let { "Also follow these instructions: ${it.trim()}" },
            "Output only the rewritten note.",
            "NOTE TO REWRITE:\n$note"
        ).joinToString("\n\n").trim()
    }

    companion object {
        private const val DEFAULT_ROLE = "support worker"

        private val RULES = listOf(
            "Write what was seen or heard, not what is assumed. \"X was tired\" becomes \"X appeared tired\".",
            "Keep the client's own words. Do not reword what X said.",
            "Use respectful, non-judgemental words. \"refused\" becomes \"declined\". If a judgemental word cannot be replaced without guessing, keep it and add [REVIEW: short reason].",
            "Show choice and support. \"took X to the gym\" becomes \"supported X to go to the gym\".",
            "Use plain English. Expand shorthand and slang: \"w/\" becomes \"with\", \"maccas\" becomes \"McDonald's\".",
            "Keep the writer's voice (I, we) and the order events happened.",
            "Never add, upgrade or diagnose. \"happy\" never becomes \"elevated mood\". \"sore\" never becomes \"injured\".",
            "If something is unclear, keep it and add [REVIEW: short reason] after it.",
            "Do not fill in missing details such as times, outcomes or reasons.",
            "Use the goals and context above as background only. Do not write about them."
        )

        private const val PLACEHOLDER_RULE =
            "Copy any text in square brackets, such as [CLIENT] or [PHONE_1], exactly as written."

        private const val PARAGRAPH_FORMAT =
            "Write plain paragraphs in the order events happened. No headings, bullet points or markdown."

        private const val SECTIONS_FORMAT =
            """Use these plain-text headings, in this order: "Activities:", "Wellbeing:", "Follow-up:". Put each fact under one heading, on a line starting with "- ". Keep the order events happened within each heading. Leave out a heading with nothing under it. No markdown such as ** or #."""
    }
}
