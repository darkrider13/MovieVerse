package com.trackerxx.movieverse.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trackerxx.movieverse.ui.theme.Mv
import com.trackerxx.movieverse.ui.theme.MvFixed
import com.trackerxx.movieverse.ui.theme.MvText
import com.trackerxx.movieverse.ui.theme.mvTextStyle

/** Page = input (15px, 10/12), Dialog = #dlg inputs (13px, 8/10), Settings = #sdlg inputs (13px, 9/11). */
enum class FieldSize(val font: TextUnit, val padH: Dp, val padV: Dp) {
    Page(15.sp, 12.dp, 10.dp),
    Dialog(13.sp, 10.dp, 8.dp),
    Settings(13.sp, 11.dp, 9.dp)
}

/** input[type=text|search|password|number], textarea: 1px --line border, --s2 fill, radius 10, border turns --mut on focus. */
@Composable
fun MvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    size: FieldSize = FieldSize.Page,
    singleLine: Boolean = true,
    minLines: Int = 1,
    password: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    endExtra: Dp = 0.dp,
    trailing: (@Composable BoxScope.() -> Unit)? = null
) {
    val c = Mv.c
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        textStyle = mvTextStyle(size.font, FontWeight.Normal, c.ink),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else minLines,
        cursorBrush = SolidColor(c.ink),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interaction,
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(c.s2)
                    .border(1.dp, if (focused) c.mut else c.line, shape)
                    .padding(
                        start = size.padH + 1.dp, end = size.padH + 1.dp + endExtra,
                        top = size.padV + 1.dp, bottom = size.padV + 1.dp
                    )
            ) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    MvText(placeholder, size = size.font, color = MvFixed.Placeholder, maxLines = 1)
                }
                inner()
                trailing?.invoke(this)
            }
        }
    )
}
