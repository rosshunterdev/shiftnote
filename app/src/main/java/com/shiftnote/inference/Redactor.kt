package com.shiftnote.inference

class Redactor(private val clientName: String) {

    private val mapping = mutableMapOf<String, String>()
    private var phoneCounter = 0
    private var addressCounter = 0
    private var nhiCounter = 0

    fun redact(text: String): String {
        var result = text

        if (clientName.isNotBlank()) {
            result = replaceClientName(result)
        }
        result = replaceNhiNumbers(result)
        result = replacePhoneNumbers(result)
        result = replaceAddresses(result)

        return result
    }

    fun getMapping(): Map<String, String> = mapping.toMap()

    private fun replaceClientName(text: String): String {
        var result = text
        // Replace full name first (longest match)
        result = result.replace(clientName, "[CLIENT]", ignoreCase = true)
        mapping["[CLIENT]"] = clientName

        // Replace individual name parts (2+ characters, word boundary)
        val parts = clientName.split("\\s+".toRegex()).filter { it.length > 1 }
        for (part in parts) {
            val pattern = "\\b${Regex.escape(part)}\\b".toRegex(RegexOption.IGNORE_CASE)
            result = result.replace(pattern, "[CLIENT]")
        }
        return result
    }

    private fun replaceNhiNumbers(text: String): String {
        val pattern = "\\b[A-Z]{3}\\d{4}\\b".toRegex()
        return pattern.replace(text) { match ->
            nhiCounter++
            val placeholder = "[NHI_$nhiCounter]"
            mapping[placeholder] = match.value
            placeholder
        }
    }

    private fun replacePhoneNumbers(text: String): String {
        val pattern = "(\\+64|0)[\\d\\s\\-]{7,12}\\b".toRegex()
        return pattern.replace(text) { match ->
            phoneCounter++
            val placeholder = "[PHONE_$phoneCounter]"
            mapping[placeholder] = match.value
            placeholder
        }
    }

    private fun replaceAddresses(text: String): String {
        val streetTypes = "Street|St|Road|Rd|Avenue|Ave|Drive|Dr|Lane|Ln|" +
            "Terrace|Tce|Place|Pl|Way|Crescent|Cres|Close|Cl"
        val pattern = "\\b\\d+\\s+[A-Z][a-z]+(?:\\s+[A-Z][a-z]+)*\\s+(?:$streetTypes)\\b".toRegex()
        return pattern.replace(text) { match ->
            addressCounter++
            val placeholder = "[ADDRESS_$addressCounter]"
            mapping[placeholder] = match.value
            placeholder
        }
    }

    companion object {
        fun restore(text: String, mapping: Map<String, String>): String {
            var result = text
            for ((placeholder, original) in mapping) {
                result = result.replace(placeholder, original)
            }
            return result
        }
    }
}
