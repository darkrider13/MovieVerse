package com.trackerxx.movieverse.net

import com.trackerxx.movieverse.data.Ls
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Currency rates for Budget / Collection (the web version's fxLoad / cCur / fxRate).
 * Live rates (open.er-api.com, USD base) are cached in "library.fx" for 12 hours; built-in rates are the fallback.
 */
object Fx {

    /** country name -> currency code */
    private val CCUR: Map<String, String> = HashMap<String, String>().also { m ->
        m["india"] = "INR"
        m["bangladesh"] = "BDT"
        m["usa"] = "USD"
        m["uk"] = "GBP"
        m["canada"] = "CAD"
        m["australia"] = "AUD"
        m["new zealand"] = "NZD"
        m["japan"] = "JPY"
        m["south korea"] = "KRW"
        m["china"] = "CNY"
        m["pakistan"] = "PKR"
        m["nepal"] = "NPR"
        m["sri lanka"] = "LKR"
        m["russia"] = "RUB"
        m["brazil"] = "BRL"
        m["mexico"] = "MXN"
        m["turkey"] = "TRY"
        m["thailand"] = "THB"
        m["indonesia"] = "IDR"
        m["uae"] = "AED"
        m["saudi arabia"] = "SAR"
        m["hong kong"] = "HKD"
        m["taiwan"] = "TWD"
        m["sweden"] = "SEK"
        m["denmark"] = "DKK"
        m["norway"] = "NOK"
        m["switzerland"] = "CHF"
        m["poland"] = "PLN"
        m["south africa"] = "ZAR"
        m["egypt"] = "EGP"
        m["nigeria"] = "NGN"
        m["singapore"] = "SGD"
        m["malaysia"] = "MYR"
        m["philippines"] = "PHP"
        m["vietnam"] = "VND"
        m["israel"] = "ILS"
        for (n in listOf("france", "germany", "italy", "spain", "netherlands", "belgium", "austria", "ireland", "portugal", "finland", "greece", "luxembourg", "estonia", "latvia", "lithuania", "slovakia", "slovenia", "croatia", "malta", "cyprus")) m[n] = "EUR"
    }

    /** built-in rates (units per 1 USD) */
    private val FXD: Map<String, Double> = mapOf(
        "INR" to 88.0,
        "BDT" to 122.0,
        "GBP" to 0.75,
        "EUR" to 0.86,
        "CAD" to 1.38,
        "AUD" to 1.53,
        "NZD" to 1.7,
        "JPY" to 150.0,
        "KRW" to 1400.0,
        "CNY" to 7.1,
        "PKR" to 281.0,
        "NPR" to 140.0,
        "LKR" to 300.0,
        "RUB" to 80.0,
        "BRL" to 5.4,
        "MXN" to 18.5,
        "TRY" to 41.0,
        "THB" to 33.0,
        "IDR" to 16500.0,
        "AED" to 3.67,
        "SAR" to 3.75,
        "HKD" to 7.8,
        "TWD" to 31.0,
        "SEK" to 9.5,
        "DKK" to 6.4,
        "NOK" to 10.2,
        "CHF" to 0.8,
        "PLN" to 3.7,
        "ZAR" to 17.5,
        "EGP" to 48.0,
        "NGN" to 1500.0,
        "SGD" to 1.3,
        "MYR" to 4.2,
        "PHP" to 58.0,
        "VND" to 26000.0,
        "ILS" to 3.3
    )

    @Volatile
    private var live: Map<String, Double>? = null

    private fun ratesOf(o: JSONObject): Map<String, Double> {
        val out = HashMap<String, Double>()
        val it = o.keys()
        while (it.hasNext()) {
            val k = it.next()
            val v = o.optDouble(k, Double.NaN)
            if (!v.isNaN()) out[k] = v
        }
        return out
    }

    /** fxLoad(): reads the cache, and asks for fresh rates when the cache is older than 12 hours */
    fun load() {
        try {
            val raw = Ls.getItem("library.fx")
            if (raw != null) {
                val c = JSONObject(raw)
                val r = c.optJSONObject("r")
                if (r != null) {
                    live = ratesOf(r)
                    if (System.currentTimeMillis() - c.optDouble("t", 0.0) < 432e5) return
                }
            }
        } catch (e: Exception) {
        }
        com.trackerxx.movieverse.net.AppScope.launch {
            try {
                val r = Http.get("https://open.er-api.com/v6/latest/USD", mapOf("Accept" to "application/json"), 8000, 8000)
                if (r.status != 200) return@launch
                val j = JSONObject(r.body)
                val rates = j.optJSONObject("rates")
                if (rates != null && rates.optDouble("INR", 0.0) != 0.0) {
                    live = ratesOf(rates)
                    try {
                        val o = JSONObject()
                        o.put("t", System.currentTimeMillis())
                        o.put("r", rates)
                        Ls.setItem("library.fx", o.toString())
                    } catch (e: Exception) {
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
            }
        }
    }

    private val SPLIT = Regex("[,/&]")

    /** cCur(m): currency of the movie's (first) country, USD when unknown */
    fun currencyOf(country: String?): String {
        val c = (country ?: "").split(SPLIT)[0].trim().lowercase()
        return CCUR[c] ?: "USD"
    }

    /** fxRate(c): units of currency c per 1 USD */
    fun rate(c: String): Double {
        if (c == "USD") return 1.0
        val l = live?.get(c)
        if (l != null && l != 0.0) return l
        val d = FXD[c]
        if (d != null && d != 0.0) return d
        return 1.0
    }
}
