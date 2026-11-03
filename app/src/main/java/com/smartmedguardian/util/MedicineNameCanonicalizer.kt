package com.smartmedguardian.util

import java.util.Locale

object MedicineNameCanonicalizer {

    val CANONICAL_NAMES = listOf(
        "Adol",
        "Augmentin",
        "Brufen",
        "Cataflam",
        "Claritine",
        "Dolo-650",
        "Flagyl",
        "Glucophage",
        "Paramol",
        "Rennie",
        "Zantac",
        "Zyrtec"
    )

    private val ALIAS_MAP = mapOf(
        "dolo650" to "Dolo-650",
        "dolo 650" to "Dolo-650",
        "dolo-650" to "Dolo-650",
        "adol" to "Adol",
        "augmentin" to "Augmentin",
        "brufen" to "Brufen",
        "cataflam" to "Cataflam",
        "claritine" to "Claritine",
        "claritin" to "Claritine",
        "flagyl" to "Flagyl",
        "glucophage" to "Glucophage",
        "paramol" to "Paramol",
        "rennie" to "Rennie",
        "zantac" to "Zantac",
        "zyrtec" to "Zyrtec"
    )

    /**
     * Maps any medicine name, model label, or explicit alias to its canonical display name.
     * Uses normalized exact matching and explicit alias mapping only.
     * Unrecognized or ambiguous inputs remain safely unresolved.
     */
    fun canonicalize(inputName: String?): String {
        if (inputName.isNullOrBlank()) return "Unknown"
        val cleanInput = inputName.trim()
        val normalizedKey = cleanInput.lowercase(Locale.ROOT)
            .replace("[^a-z0-9]".toRegex(), "")

        if (normalizedKey.isBlank()) return cleanInput

        // 1. Direct check in explicit ALIAS_MAP
        ALIAS_MAP[normalizedKey]?.let { return it }

        // 2. Exact match (case/punctuation insensitive) with canonical list
        for (canonical in CANONICAL_NAMES) {
            val normCanonical = canonical.lowercase(Locale.ROOT).replace("[^a-z0-9]".toRegex(), "")
            if (normalizedKey == normCanonical) {
                return canonical
            }
        }

        // 3. Unrecognized/ambiguous input is preserved as clean original string
        return cleanInput
    }

    /**
     * Checks if two medicine names match when canonicalized or normalized.
     */
    fun isSameMedicine(nameA: String?, nameB: String?): Boolean {
        if (nameA.isNullOrBlank() || nameB.isNullOrBlank()) return false
        val canonA = canonicalize(nameA)
        val canonB = canonicalize(nameB)
        if (canonA.equals(canonB, ignoreCase = true)) return true

        val normA = canonA.lowercase(Locale.ROOT).replace("[^a-z0-9]".toRegex(), "")
        val normB = canonB.lowercase(Locale.ROOT).replace("[^a-z0-9]".toRegex(), "")
        return normA.isNotBlank() && normB.isNotBlank() && normA == normB
    }
}
