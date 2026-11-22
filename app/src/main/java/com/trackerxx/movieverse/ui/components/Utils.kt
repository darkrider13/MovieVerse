package com.trackerxx.movieverse.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Modifier

/** one shared source: nothing listens to it (no ripple), so every card does not need its own */
private val NoInteraction = MutableInteractionSource()

/**
 * Click without ripple: the web version has no tap highlight (-webkit-tap-highlight-color:transparent).
 * No `composed {}` here: composed modifiers are slow, and every card of the list used one.
 */
fun Modifier.plainClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    clickable(enabled = enabled, interactionSource = NoInteraction, indication = null, onClick = onClick)
