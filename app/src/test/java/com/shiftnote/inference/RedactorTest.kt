package com.shiftnote.inference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactorTest {

    @Test
    fun `redact replaces full client name with CLIENT placeholder`() {
        val redactor = Redactor("John Smith")
        val result = redactor.redact("Took John Smith to the shops")
        assertEquals("Took [CLIENT] to the shops", result)
        assertEquals("John Smith", redactor.getMapping()["[CLIENT]"])
    }

    @Test
    fun `redact replaces partial client name (first name only)`() {
        val redactor = Redactor("John Smith")
        val result = redactor.redact("John was in good spirits today")
        assertEquals("[CLIENT] was in good spirits today", result)
    }

    @Test
    fun `redact replaces partial client name (last name only)`() {
        val redactor = Redactor("John Smith")
        val result = redactor.redact("Spoke with Smith about goals")
        assertEquals("Spoke with [CLIENT] about goals", result)
    }

    @Test
    fun `redact is case insensitive for client name`() {
        val redactor = Redactor("John Smith")
        val result = redactor.redact("Met JOHN at the park")
        assertEquals("Met [CLIENT] at the park", result)
    }

    @Test
    fun `redact replaces NZ phone numbers`() {
        val redactor = Redactor("")
        val result = redactor.redact("Call me on 021 123 4567")
        assertTrue(result.contains("[PHONE_1]"))
        assertFalse(result.contains("021 123 4567"))
    }

    @Test
    fun `redact replaces international NZ phone numbers`() {
        val redactor = Redactor("")
        val result = redactor.redact("Number is +64 21 123 4567")
        assertTrue(result.contains("[PHONE_1]"))
        assertFalse(result.contains("+64"))
    }

    @Test
    fun `redact replaces NHI numbers`() {
        val redactor = Redactor("")
        val result = redactor.redact("NHI: ABC1234")
        assertTrue(result.contains("[NHI_1]"))
        assertFalse(result.contains("ABC1234"))
    }

    @Test
    fun `redact replaces street addresses`() {
        val redactor = Redactor("")
        val result = redactor.redact("Lives at 42 Willis Street")
        assertTrue(result.contains("[ADDRESS_1]"))
        assertFalse(result.contains("42 Willis Street"))
    }

    @Test
    fun `redact handles text with no PII`() {
        val redactor = Redactor("")
        val input = "Had a good day at the community centre"
        val result = redactor.redact(input)
        assertEquals(input, result)
        assertTrue(redactor.getMapping().isEmpty())
    }

    @Test
    fun `restore replaces placeholders with original values`() {
        val mapping = mapOf(
            "[CLIENT]" to "John Smith",
            "[PHONE_1]" to "021 123 4567"
        )
        val result = Redactor.restore(
            "[CLIENT] can be reached on [PHONE_1]",
            mapping
        )
        assertEquals("John Smith can be reached on 021 123 4567", result)
    }

    @Test
    fun `redact and restore roundtrip preserves original text`() {
        val redactor = Redactor("John Smith")
        val original = "Took John Smith to Pak'nSave, called 021 555 1234"
        val redacted = redactor.redact(original)
        val restored = Redactor.restore(redacted, redactor.getMapping())
        assertEquals(original, restored)
    }

    @Test
    fun `redact maintains consistent placeholders across multiple calls`() {
        val redactor = Redactor("Alice Brown")
        val note = redactor.redact("Alice went shopping")
        val goals = redactor.redact("Call Alice on 021 111 2222")
        assertTrue(note.contains("[CLIENT]"))
        assertTrue(goals.contains("[CLIENT]"))
        assertTrue(goals.contains("[PHONE_1]"))
    }

    @Test
    fun `redact does not replace single character name parts`() {
        val redactor = Redactor("J Smith")
        val result = redactor.redact("I went to the park")
        assertEquals("I went to the park", result)
    }
}
