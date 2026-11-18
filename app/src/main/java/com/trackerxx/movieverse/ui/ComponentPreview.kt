package com.trackerxx.movieverse.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.trackerxx.movieverse.data.AppData
import com.trackerxx.movieverse.data.Movie
import com.trackerxx.movieverse.enrich.Enrich
import com.trackerxx.movieverse.net.Fx
import com.trackerxx.movieverse.net.Http
import com.trackerxx.movieverse.net.Net
import com.trackerxx.movieverse.net.Wiki
import com.trackerxx.movieverse.ui.components.BtnKind
import com.trackerxx.movieverse.ui.components.BtnSize
import com.trackerxx.movieverse.ui.components.FieldSize
import com.trackerxx.movieverse.ui.components.LocalToast
import com.trackerxx.movieverse.ui.components.MvApiLoadingDialog
import com.trackerxx.movieverse.ui.components.MvButton
import com.trackerxx.movieverse.ui.components.MvDialog
import com.trackerxx.movieverse.ui.components.MvOkDialog
import com.trackerxx.movieverse.ui.components.MvPill
import com.trackerxx.movieverse.ui.components.MvSeg
import com.trackerxx.movieverse.ui.components.MvSegc
import com.trackerxx.movieverse.ui.components.MvSmallSpinner
import com.trackerxx.movieverse.ui.components.MvTextField
import com.trackerxx.movieverse.ui.components.MvXWrap
import com.trackerxx.movieverse.ui.theme.LocalThemeState
import com.trackerxx.movieverse.ui.theme.Mv
import com.trackerxx.movieverse.ui.theme.MvH2
import com.trackerxx.movieverse.ui.theme.MvIcon
import com.trackerxx.movieverse.ui.theme.MvIcons
import com.trackerxx.movieverse.ui.theme.MvLabel
import com.trackerxx.movieverse.ui.theme.MvNote
import com.trackerxx.movieverse.ui.theme.MvText
import kotlinx.coroutines.launch

/**
 * TEMPORARY screen for Step 2: shows every shared component so the design can be checked.
 * It is replaced by the real Library / Trending / Releases screens in the next steps.
 */
@Composable
fun ComponentPreview(onBack: (() -> Unit)? = null) {
    val c = Mv.c
    val themeState = LocalThemeState.current
    val toast = LocalToast.current
    var seg by remember { mutableIntStateOf(0) }
    var segc by remember { mutableIntStateOf(0) }
    var pill by remember { mutableIntStateOf(0) }
    var text by remember { mutableStateOf("") }
    var dlg by remember { mutableIntStateOf(0) }
    var uname by remember { mutableStateOf(AppData.uname) }
    var testMsg by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(c.bg)) {
        // top bar (.nav): brand + theme button
        Row(
            Modifier
                .fillMaxWidth()
                .background(c.nav)
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MvText(
                buildAnnotatedString {
                    append("Movie")
                    withStyle(SpanStyle(color = c.acc)) { append("Verse") }
                },
                Modifier.weight(1f),
                size = 20.sp, weight = FontWeight.ExtraBold, letterSpacing = (-0.03).em
            )
            if (onBack != null) MvButton("Back", onBack, size = BtnSize.Nav)
            MvButton(onClick = { themeState.toggle() }, size = BtnSize.Nav, icon = true) {
                MvIcon(if (c.dark) MvIcons.Moon else MvIcons.Sun, 20.dp)
            }
            MvButton(onClick = { toast.show("Settings icon") }, size = BtnSize.Nav, icon = true) {
                MvIcon(MvIcons.Gear, 20.dp)
            }
        }
        Row(Modifier.fillMaxWidth().background(c.line).padding(top = 1.dp)) {}

        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            MvH2("Step 2 : design check", top = 0.dp)
            MvNote("Buttons, pills, segmented controls, inputs, dialogs, toast and spinners.")

            MvH2("Buttons")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                MvButton("Normal", { toast.show("Normal button") })
                MvButton("Primary", { toast.show("Primary button") }, kind = BtnKind.Primary)
                MvButton("Red", { toast.show("Red button") }, kind = BtnKind.Red)
                MvButton("Delete", { toast.show("Delete button") }, danger = true)
                MvButton("Disabled", {}, enabled = false)
            }

            MvH2("Pills")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Watched", "Plan", "Watching").forEachIndexed { i, t ->
                    MvPill(t, selected = pill == i, onClick = { pill = i })
                }
            }

            MvH2("Segmented")
            MvSeg(listOf("Library", "Trending", "Releases"), seg, { seg = it })
            Row(Modifier.padding(top = 10.dp)) {
                MvSegc(listOf("Movie", "TV Series"), segc, { segc = it })
            }

            MvH2("Input")
            MvTextField(text, { text = it }, placeholder = "Search...")
            MvLabel("Dialog size input")
            MvTextField(text, { text = it }, placeholder = "Search...", size = FieldSize.Dialog)

            MvH2("Spinner")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                MvSmallSpinner()
                MvNote("Loading")
            }

            MvH2("Saved data")
            MvLabel("Your name (saved)", top = 0.dp)
            MvTextField(uname, { uname = it; AppData.setName(it) }, placeholder = "Name")
            MvNote("Statuses: " + AppData.statuses.joinToString(", ") { it.name }, Modifier.padding(top = 8.dp))
            MvNote("APIs: " + AppData.apis.apis.joinToString(", ") { a -> a.name + " (" + a.keys.count { k -> k.isNotEmpty() } + ")" }, Modifier.padding(top = 4.dp))
            MvNote("Theme is saved too: switch it, close the app, open again.", Modifier.padding(top = 4.dp))
            Row(Modifier.padding(top = 10.dp)) {
                MvButton("Test database", { scope.launch { toast.show("Database ok, movies: " + AppData.db.count()) } })
            }

            MvH2("Network check")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MvButton("Internet", {
                    scope.launch {
                        try {
                            val r = Http.get("https://open.er-api.com/v6/latest/USD")
                            toast.show("Internet ok, status " + r.status)
                        } catch (e: Exception) {
                            toast.show("No internet: " + (e.message ?: ""))
                        }
                    }
                })
                MvButton("Rates", {
                    toast.show("1 USD = " + Fx.rate("INR") + " INR, India/USA = " + Fx.currencyOf("India, USA"))
                })
                MvButton("TMDB key", {
                    scope.launch {
                        if (AppData.apis.key.isEmpty()) {
                            toast.show("Add a TMDB key in Settings first")
                        } else {
                            try {
                                Net.tmdb.get("/configuration")
                                toast.show("TMDB key works")
                            } catch (e: Exception) {
                                toast.show(e.message ?: "TMDB failed", 4000)
                            }
                        }
                    }
                })
                MvButton("Enrich test", {
                    scope.launch {
                        if (AppData.apis.key.isEmpty()) {
                            toast.show("Add a TMDB key in Settings first")
                        } else {
                            toast.show("Running...", 60000)
                            try {
                                val t = Movie()
                                t.id = "test"; t.tmdbId = 27205; t.title = "Inception"; t.year = "2010"
                                t.status = "watched"; t.added = System.currentTimeMillis()
                                val a = Enrich.enrichOtt(t, true)
                                val b = Enrich.enrich(t, true)
                                val c = Enrich.enrichMd(t, true)
                                val d = Enrich.enrichWiki(t, true)
                                val e = Enrich.enrichPoster(t, true)
                                testMsg = "OTT:" + a + " IMDb:" + b + " MDBList:" + c + " Wiki:" + d + " Poster:" + e +
                                    "\nruntime=" + t.str("runtime") + " cert=" + t.str("cert") + " rel=" + t.str("rel") +
                                    "\nIMDb=" + Enrich.imdbRating(t) + " RT=" + t.str("rt") + " TMDB=" + t.str("tm") +
                                    "\nwiki=" + t.str("wpg") + " budget=" + t.str("wbud") + " gross=" + t.str("wrev") + " " + t.str("wcur")
                                toast.show("Done")
                            } catch (e: Exception) {
                                toast.show("Failed: " + (e.message ?: ""), 4000)
                            }
                        }
                    }
                })
                MvButton("Wikipedia", {
                    scope.launch {
                        try {
                            val c = Wiki.find("Inception", 2010, 27205, "tt1375666")
                            toast.show(if (c == null) "Wikipedia: no page" else c.page + " | " + c.bud + " | " + c.gross, 5000)
                        } catch (e: Exception) {
                            toast.show("Wikipedia failed: " + (e.message ?: ""))
                        }
                    }
                })
            }

            MvH2("Dialogs and toast")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MvButton("Dialog", { dlg = 1 })
                MvButton("OK dialog", { dlg = 2 })
                MvButton("API loading", { dlg = 3 })
                MvButton("Toast", { toast.show("Saved to your library") })
            }
        }
    }

    testMsg?.let { MvOkDialog(title = "Enrichment test", message = it, onOk = { testMsg = null }) }

    when (dlg) {
        1 -> MvDialog(onDismiss = { dlg = 0 }, padding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            MvXWrap { dlg = 0 }
            MvText("Sample dialog", size = 16.sp, weight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp, end = 40.dp))
            MvNote("Glass dialog with blur behind (Android 12+).", size = 12.sp)
            MvLabel("Name", size = 13.sp, top = 10.dp)
            MvTextField(text, { text = it }, placeholder = "Type here", size = FieldSize.Dialog)
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MvButton("Cancel", { dlg = 0 }, Modifier.weight(1f), size = BtnSize.Dialog)
                MvButton("Save", { dlg = 0 }, Modifier.weight(1f), kind = BtnKind.Primary, size = BtnSize.Dialog)
            }
        }
        2 -> MvOkDialog(title = "Done", message = "Everything worked.", onOk = { dlg = 0 }, code = "tmdb_api_key_example")
        3 -> MvApiLoadingDialog(caption = "Please wait", onDismiss = { dlg = 0 })
    }
}
