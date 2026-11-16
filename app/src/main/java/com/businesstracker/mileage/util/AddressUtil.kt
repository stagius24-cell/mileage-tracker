package com.businesstracker.mileage.util

import java.util.regex.Pattern

object AddressUtil {

    private val STREET_SUFFIXES = mapOf(
        "avenue" to "Ave",
        "street" to "St",
        "road" to "Rd",
        "boulevard" to "Blvd",
        "drive" to "Dr",
        "lane" to "Ln",
        "court" to "Ct",
        "circle" to "Cir",
        "place" to "Pl",
        "way" to "Way",
        "parkway" to "Pkwy",
        "highway" to "Hwy",
        "expressway" to "Expy",
        "terrace" to "Ter",
        "trail" to "Trl",
        "loop" to "Loop"
    )

    private val US_STATES = mapOf(
        "alabama" to "AL", "alaska" to "AK", "arizona" to "AZ", "arkansas" to "AR", "california" to "CA",
        "colorado" to "CO", "connecticut" to "CT", "delaware" to "DE", "florida" to "FL", "georgia" to "GA",
        "hawaii" to "HI", "idaho" to "ID", "illinois" to "IL", "indiana" to "IN", "iowa" to "IA",
        "kansas" to "KS", "kentucky" to "KY", "louisiana" to "LA", "maine" to "ME", "maryland" to "MD",
        "massachusetts" to "MA", "michigan" to "MI", "minnesota" to "MN", "mississippi" to "MS", "missouri" to "MO",
        "montana" to "MT", "nebraska" to "NE", "nevada" to "NV", "new hampshire" to "NH", "new jersey" to "NJ",
        "new mexico" to "NM", "new york" to "NY", "north carolina" to "NC", "north dakota" to "ND", "ohio" to "OH",
        "oklahoma" to "OK", "oregon" to "OR", "pennsylvania" to "PA", "rhode island" to "RI", "south carolina" to "SC",
        "south dakota" to "SD", "tennessee" to "TN", "texas" to "TX", "utah" to "UT", "vermont" to "VT",
        "virginia" to "VA", "washington" to "WA", "west virginia" to "WV", "wisconsin" to "WI", "wyoming" to "WY",
        "district of columbia" to "DC"
    )

    private val SUFFIX_PATTERNS = STREET_SUFFIXES.map { (full, abbr) ->
        Pattern.compile("\\b${Pattern.quote(full)}\\b", Pattern.CASE_INSENSITIVE) to abbr
    }

    /**
     * Shortens street suffixes (e.g. Avenue -> Ave, Street -> St) and replaces full US state names
     * with standard 2-letter postal abbreviations (e.g. California -> CA).
     * State matching only applies to the final address segment (after the last comma) or trailing state,
     * ensuring city names like "New York" in "New York, NY" are preserved.
     */
    fun abbreviateAddress(address: String?): String {
        if (address.isNullOrBlank()) return address.orEmpty()
        var result: String = address

        // Abbreviate street suffixes
        for ((pattern, abbr) in SUFFIX_PATTERNS) {
            result = pattern.matcher(result).replaceAll(abbr)
        }

        // Abbreviate state only in the final segment (or trailing text)
        if (result.contains(",")) {
            val parts = result.split(",").map { it.trim() }.toMutableList()
            if (parts.size >= 2) {
                val lastIdx = parts.lastIndex
                val lastLower = parts[lastIdx].lowercase()
                for ((stateName, stateAbbr) in US_STATES) {
                    if (lastLower == stateName) {
                        parts[lastIdx] = stateAbbr
                        break
                    } else if (lastLower.startsWith("$stateName ")) {
                        val remainder = parts[lastIdx].substring(stateName.length).trim()
                        parts[lastIdx] = "$stateAbbr $remainder"
                        break
                    }
                }
                result = parts.joinToString(", ")
            }
        } else {
            // No commas, check end of string
            for ((stateName, stateAbbr) in US_STATES) {
                val stateRegex = Pattern.compile("\\b${Pattern.quote(stateName)}\\b$", Pattern.CASE_INSENSITIVE)
                result = stateRegex.matcher(result).replaceAll(stateAbbr)
            }
        }

        return result
    }
}
