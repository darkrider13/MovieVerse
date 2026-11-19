package com.trackerxx.movieverse.util

import org.json.JSONArray
import org.json.JSONObject

/** Country codes and names (the COUNTRIES list of the web version) + the helpers that turn TMDB codes into a country name. */
object Countries {
    val LIST: List<Pair<String, String>> = listOf(
        "BD" to "Bangladesh",
        "IN" to "India",
        "IN-BN" to "India (Bengali)",
        "US" to "United States",
        "GB" to "United Kingdom",
        "KR" to "South Korea",
        "JP" to "Japan",
        "CN" to "China",
        "FR" to "France",
        "DE" to "Germany",
        "IT" to "Italy",
        "ES" to "Spain",
        "TR" to "Turkey",
        "TH" to "Thailand",
        "PK" to "Pakistan",
        "NP" to "Nepal",
        "LK" to "Sri Lanka",
        "ID" to "Indonesia",
        "MY" to "Malaysia",
        "PH" to "Philippines",
        "VN" to "Vietnam",
        "HK" to "Hong Kong",
        "TW" to "Taiwan",
        "RU" to "Russia",
        "UA" to "Ukraine",
        "PL" to "Poland",
        "SE" to "Sweden",
        "NO" to "Norway",
        "DK" to "Denmark",
        "FI" to "Finland",
        "NL" to "Netherlands",
        "BE" to "Belgium",
        "CH" to "Switzerland",
        "AT" to "Austria",
        "IE" to "Ireland",
        "PT" to "Portugal",
        "GR" to "Greece",
        "CZ" to "Czechia",
        "HU" to "Hungary",
        "RO" to "Romania",
        "IL" to "Israel",
        "IR" to "Iran",
        "EG" to "Egypt",
        "SA" to "Saudi Arabia",
        "AE" to "United Arab Emirates",
        "NG" to "Nigeria",
        "ZA" to "South Africa",
        "KE" to "Kenya",
        "MA" to "Morocco",
        "BR" to "Brazil",
        "MX" to "Mexico",
        "AR" to "Argentina",
        "CO" to "Colombia",
        "CL" to "Chile",
        "PE" to "Peru",
        "CA" to "Canada",
        "AU" to "Australia",
        "NZ" to "New Zealand"
    )

    /** effC(oc, pc): the country that counts: the first origin country that is also a production country */
    fun effC(oc: JSONArray?, pc: JSONArray?): String {
        val o = ArrayList<String>()
        if (oc != null) for (i in 0 until oc.length()) o.add(oc.opt(i)?.toString() ?: "")
        val p = ArrayList<JSONObject>()
        if (pc != null) for (i in 0 until pc.length()) pc.optJSONObject(i)?.let { p.add(it) }
        if (p.isEmpty()) return o.firstOrNull() ?: ""
        for (k in o) {
            if (p.any { it.opt("iso_3166_1") == k }) return k
        }
        val first = p[0].opt("iso_3166_1")
        return if (Js.truthy(first)) first.toString() else ""
    }

    /** cName(code, pc): TMDB's own name first, then the list, otherwise the code itself */
    fun cName(code: String, pc: JSONArray?): String {
        if (pc != null) {
            for (i in 0 until pc.length()) {
                val x = pc.optJSONObject(i) ?: continue
                if (x.opt("iso_3166_1") == code) return if (Js.truthy(x.opt("name"))) x.opt("name").toString() else ""
            }
        }
        return LIST.firstOrNull { it.first == code }?.second ?: code
    }
}
