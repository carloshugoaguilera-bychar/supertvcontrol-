package com.claudia.controltv

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val KEY_LABEL: Map<String, String> = mapOf(
    "power" to "⏻",
    "input" to "Entrada",
    "mute" to "Mute",
    "vol_up" to "Vol +",
    "vol_dn" to "Vol −",
    "ch_up" to "Canal +",
    "ch_dn" to "Canal −",
    "up" to "▲",
    "down" to "▼",
    "left" to "◀",
    "right" to "▶",
    "ok" to "OK",
    "menu" to "Menú",
    "back" to "Atrás",
    "home" to "Inicio",
    "exit" to "Salir",
    "info" to "Info",
    "d0" to "0", "d1" to "1", "d2" to "2", "d3" to "3", "d4" to "4",
    "d5" to "5", "d6" to "6", "d7" to "7", "d8" to "8", "d9" to "9"
)

/** Orden en que se muestran los botones al verificar un codigo. */
val KEY_ORDER: List<String> = listOf(
    "power", "input", "mute", "vol_up", "vol_dn", "ch_up", "ch_dn",
    "up", "down", "left", "right", "ok", "menu", "back", "home", "exit", "info",
    "d1", "d2", "d3", "d4", "d5", "d6", "d7", "d8", "d9", "d0"
)

val GREEN = Color(0xFF2E7D32)
val RED = Color(0xFFC62828)

fun code4(n: Int): String = "%04d".format(n)

/** Boton tactil de la botonera. Con repeat=true repite mientras se mantiene presionado. */
@Composable
fun IrKey(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    repeatOnHold: Boolean = false,
    onPress: () -> Unit
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val currentOnPress by rememberUpdatedState(onPress)
    Box(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) container else container.copy(alpha = 0.35f))
            .pointerInput(enabled, repeatOnHold) {
                if (!enabled) return@pointerInput
                detectTapGestures(onPress = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    currentOnPress()
                    var job: Job? = null
                    if (repeatOnHold) {
                        job = scope.launch {
                            delay(450)
                            while (true) {
                                currentOnPress()
                                delay(160)
                            }
                        }
                    }
                    tryAwaitRelease()
                    job?.cancel()
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (enabled) content else content.copy(alpha = 0.4f),
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )
    }
}

/** Boton de la botonera de un TV guardado (ocupa su parte de la fila). */
@Composable
fun RowScope.RemoteKey(
    profile: Profile,
    key: String,
    press: (String) -> Unit,
    repeatOnHold: Boolean = false,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    IrKey(
        label = KEY_LABEL[key] ?: key,
        enabled = profile.buttons.containsKey(key),
        modifier = Modifier
            .weight(1f)
            .height(60.dp),
        container = container,
        content = content,
        repeatOnHold = repeatOnHold,
        onPress = { press(key) }
    )
}
