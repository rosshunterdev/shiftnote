package com.shiftnote.inference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptAssemblerTest {

    private val assembler = PromptAssembler()

    private fun assemble(
        role: String = "Community support worker",
        style: ImproveStyle = ImproveStyle.PARAGRAPH,
        generalContext: String = "",
        clientGoals: String = "",
        clientContext: String = "",
        extraInstructions: String = "",
        note: String = "Helped client with dishes.",
        keepPlaceholders: Boolean = false
    ) = assembler.assemble(
        role = role,
        style = style,
        generalContext = generalContext,
        clientGoals = clientGoals,
        clientContext = clientContext,
        extraInstructions = extraInstructions,
        note = note,
        keepPlaceholders = keepPlaceholders
    )

    @Test
    fun `assemble includes role, context and note when all fields provided`() {
        val result = assemble(
            generalContext = "Use person-first language",
            clientGoals = "Building independence with daily tasks",
            clientContext = "Client uses a wheelchair"
        )

        assertTrue(result.contains("helping a Community support worker"))
        assertTrue(result.contains("Active goals: Building independence with daily tasks"))
        assertTrue(result.contains("Additional context: Client uses a wheelchair"))
        assertTrue(result.contains("General context: Use person-first language"))
        assertTrue(result.contains("Helped client with dishes."))
    }

    @Test
    fun `assemble omits empty context sections entirely`() {
        val result = assemble()

        assertFalse(result.contains("Active goals"))
        assertFalse(result.contains("Additional context"))
        assertFalse(result.contains("General context"))
        assertFalse(result.contains("\n\n\n"))
    }

    @Test
    fun `assemble omits goals section but keeps context when only goals empty`() {
        val result = assemble(clientContext = "Client prefers outdoor activities")

        assertFalse(result.contains("Active goals"))
        assertTrue(result.contains("Additional context: Client prefers outdoor activities"))
    }

    @Test
    fun `assemble includes all seven professional language rules`() {
        val result = assemble()

        listOf(
            "seen or heard, not what is assumed",
            "client's own words",
            "non-judgemental",
            "choice and support",
            "plain English",
            "writer's voice",
            "Never add, upgrade or diagnose"
        ).forEach { phrase ->
            assertTrue("Missing rule: $phrase", result.contains(phrase))
        }
    }

    @Test
    fun `assemble keeps review flag and no-guessing rules`() {
        val result = assemble()

        assertTrue(result.contains("[REVIEW: short reason]"))
        assertTrue(result.contains("Do not fill in missing details"))
        assertTrue(result.contains("background only"))
    }

    @Test
    fun `paragraph style asks for paragraphs and no section headings`() {
        val result = assemble(style = ImproveStyle.PARAGRAPH)

        assertTrue(result.contains("Write plain paragraphs"))
        assertFalse(result.contains("Wellbeing:"))
    }

    @Test
    fun `sections style lists the three headings in order and no paragraph block`() {
        val result = assemble(style = ImproveStyle.SECTIONS)

        val activities = result.indexOf("\"Activities:\"")
        val wellbeing = result.indexOf("\"Wellbeing:\"")
        val followUp = result.indexOf("\"Follow-up:\"")
        assertTrue(activities >= 0)
        assertTrue(activities < wellbeing)
        assertTrue(wellbeing < followUp)
        assertTrue(result.contains("Leave out a heading with nothing under it"))
        assertFalse(result.contains("Write plain paragraphs"))
    }

    @Test
    fun `placeholder rule only included when keepPlaceholders is true`() {
        val onDevice = assemble(keepPlaceholders = false)
        val cloud = assemble(keepPlaceholders = true)

        assertFalse(onDevice.contains("[CLIENT]"))
        assertFalse(onDevice.contains("exactly as written"))
        assertTrue(cloud.contains("[CLIENT]"))
        assertTrue(cloud.contains("exactly as written"))
    }

    @Test
    fun `extra instructions included only when not blank`() {
        val withExtras = assemble(extraInstructions = "Use NZ spelling.")
        val blankExtras = assemble(extraInstructions = "  \n ")

        assertTrue(withExtras.contains("Also follow these instructions: Use NZ spelling."))
        assertFalse(blankExtras.contains("Also follow these instructions"))
    }

    @Test
    fun `blank or whitespace role falls back to support worker`() {
        assertTrue(assemble(role = "").contains("helping a support worker"))
        assertTrue(assemble(role = "   ").contains("helping a support worker"))
    }

    @Test
    fun `note text is the last thing in the prompt`() {
        val result = assemble(
            extraInstructions = "Use NZ spelling.",
            note = "Went for a walk."
        )

        assertTrue(result.endsWith("NOTE TO REWRITE:\nWent for a walk."))
    }

    @Test
    fun `fromStored reads known style names and falls back to paragraph`() {
        assertEquals(ImproveStyle.SECTIONS, ImproveStyle.fromStored("SECTIONS"))
        assertEquals(ImproveStyle.PARAGRAPH, ImproveStyle.fromStored("PARAGRAPH"))
        assertEquals(ImproveStyle.PARAGRAPH, ImproveStyle.fromStored(null))
        assertEquals(ImproveStyle.PARAGRAPH, ImproveStyle.fromStored("garbage"))
    }
}
