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
    "power" to "Encender",
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
    "d5" to "5", "d6" to "6", "d7" to "7", "d8" to "8", "d9" to "9",
    "netflix" to "Netflix",
    "youtube" to "YouTube",
    "prime_video" to "Prime",
    "play" to "Play",
    "pause" to "Pausa",
    "stop" to "Stop",
    "play_pause" to "Play/Pausa",
    "rewind" to "Retroceder",
    "ff" to "Adelantar",
    "next" to "Siguiente",
    "prev" to "Anterior",
    "red" to "Rojo",
    "green" to "Verde",
    "yellow" to "Amarillo",
    "blue" to "Azul",
    "guide" to "Guía",
    "ch_list" to "Lista canales",
    "subtitle" to "Subtítulos",
    "sleep" to "Sleep",
    "settings" to "Ajustes",
    "tools" to "Tools",
    "aspect" to "Aspecto",
    "audio" to "Audio"
)

val KEYS_NUMBERS: List<String> = listOf("d1", "d2", "d3", "d4", "d5", "d6", "d7", "d8", "d9", "d0")
val KEYS_APPS: List<String> = listOf("netflix", "youtube", "prime_video")
val KEYS_PLAY: List<String> = listOf("play", "pause", "stop", "play_pause", "rewind", "ff", "prev", "next")
val KEYS_COLORS: List<String> = listOf("red", "green", "yellow", "blue", "guide", "ch_list", "subtitle")
val KEYS_SETTINGS: List<String> = listOf("settings", "tools", "aspect", "audio", "sleep")

val PROTOCOLS: List<String> = listOf(
    "NEC", "NECext", "Samsung32", "SIRC", "SIRC15", "SIRC20", "RC5", "RC6", "RCA", "Kaseikyo"
)

/** Convierte texto hexadecimal ("04", "0x1F", "07 00") en numero, o null si no es valido. */
fun hexOrNull(t: String?): Long? {
    val clean = t?.trim()?.replace(" ", "")?.removePrefix("0x")?.removePrefix("0X") ?: return null
    if (clean.isEmpty()) return null
    return clean.toLongOrNull(16)
}

/** Orden en que se muestran los botones al verificar un codigo. */
val KEY_ORDER: List<String> = listOf(
    "power", "input", "mute", "vol_up", "vol_dn", "ch_up", "ch_dn",
    "up", "down", "left", "right", "ok", "menu", "back", "home", "exit", "info",
    "d1", "d2", "d3", "d4", "d5", "d6", "d7", "d8", "d9", "d0",
    "netflix", "youtube", "prime_video",
    "play", "pause", "stop", "play_pause", "rewind", "ff", "prev", "next",
    "red", "green", "yellow", "blue", "guide", "ch_list", "subtitle",
    "settings", "tools", "aspect", "audio", "sleep"
)

val GREEN = Color(0xFF2E7D32)
val RED = Color(0xFFC62828)

fun code4(n: Int): String = if (n == 0) "manual" else "%04d".format(n)

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

/** Colores de fondo y texto de cada boton (los de colores del control van en su color). */
@Composable
fun keyColors(key: String): Pair<Color, Color> = when (key) {
    "red" -> RED to Color.White
    "green" -> GREEN to Color.White
    "yellow" -> Color(0xFFF9A825) to Color.Black
    "blue" -> Color(0xFF1565C0) to Color.White
    else -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
}

/** Cuadricula de 3 columnas con los botones que el TV tiene (los que faltan no se muestran). */
@Composable
fun KeyGrid(profile: Profile, keys: List<String>, press: (String) -> Unit) {
    val present = keys.filter { profile.buttons.containsKey(it) }
    for (row in present.chunked(3)) {
        androidx.compose.foundation.layout.Row(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
        ) {
            for (k in row) {
                val (bg, fg) = keyColors(k)
                RemoteKey(profile, k, press, container = bg, content = fg)
            }
            for (i in 0 until (3 - row.size)) {
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            }
        }
    }
}
