package com.claudia.controltv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Comando mas alto que admite cada protocolo. */
private fun maxCmd(proto: String): Int = when (proto) {
    "SIRC", "SIRC15", "SIRC20" -> 0x7F
    "RC5", "RC5X" -> 0x3F
    "Kaseikyo" -> 0x3FF
    else -> 0xFF
}

private fun hex2(n: Int): String = "%02X".format(n)

/**
 * Busca el comando de un boton: envia comandos uno por uno (con el protocolo y la direccion
 * del encendido del TV) y, cuando el TV responde, lo guarda en el boton elegido.
 */
@Composable
fun ScanCommandDialog(profile: Profile, onSave: (String, Signal) -> Unit, onDismiss: () -> Unit) {
    val power = profile.buttons["power"]
    if (power == null || power.proto == "RAW") {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Buscar comando") },
            text = { Text("Este código usa una señal cruda (RAW): no se pueden buscar comandos con él.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
        )
    } else {
        ScanBody(profile, power, onSave, onDismiss)
    }
}

@Composable
private fun ScanBody(profile: Profile, power: Signal, onSave: (String, Signal) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val proto = power.proto
    val maxC = maxCmd(proto)

    var key by remember { mutableStateOf(KEY_ORDER.firstOrNull { !profile.buttons.containsKey(it) } ?: "d0") }
    var keyMenu by remember { mutableStateOf(false) }
    var fromTxt by remember { mutableStateOf("0") }
    var toTxt by remember { mutableStateOf(maxC.toString(16).uppercase()) }
    var pauseTxt by remember { mutableStateOf("800") }

    var running by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var skip by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf<Int?>(null) }
    var previous by remember { mutableStateOf<Int?>(null) }
    var progress by remember { mutableStateOf(0 to 0) }
    var job by remember { mutableStateOf<Job?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    // En NECext el comando son 2 bytes: el comando y su inverso (asi vienen los controles reales).
    fun sig(cmd: Int): Signal =
        if (proto == "NECext") {
            Signal(proto, a = power.a, c = (cmd.toLong() and 0xFFL) or ((cmd.toLong().inv() and 0xFFL) shl 8))
        } else {
            Signal(proto, a = power.a, c = cmd.toLong())
        }

    fun startScan() {
        val from = (hexOrNull(fromTxt)?.toInt() ?: 0).coerceIn(0, maxC)
        val to = (hexOrNull(toTxt)?.toInt() ?: maxC).coerceIn(from, maxC)
        val pause = (pauseTxt.toLongOrNull() ?: 800L).coerceIn(300L, 5000L)
        // los comandos que ya usa otro boton de este TV no se vuelven a probar
        val known = profile.buttons.values
            .filter { it.proto == proto && it.a == power.a }
            .map { it.c }
            .toSet()
        val list = (from..to).filter { !known.contains(sig(it).c) }
        if (list.isEmpty()) {
            notice = "No hay comandos nuevos por probar en ese rango."
            return
        }
        job?.cancel()
        notice = null
        paused = false
        skip = false
        previous = null
        job = scope.launch {
            running = true
            var done = 0
            for (cmd in list) {
                done++
                previous = current
                current = cmd
                progress = done to list.size
                IrEngine.send(ctx, sig(cmd))
                var waited = 0L
                while (waited < pause && !skip) {
                    delay(50)
                    if (!paused) waited += 50
                }
                skip = false
            }
            running = false
            current = null
            notice = "Terminó el rango. Si nada respondió, prueba otro rango o acerca el celular al TV."
        }
    }

    fun pick(cmd: Int) {
        job?.cancel()
        running = false
        paused = false
        onSave(key, sig(cmd))
        notice = "Guardado: " + (KEY_LABEL[key] ?: key) + " = comando " + hex2(cmd) +
            ". Puedes buscar otro botón."
        key = KEY_ORDER.firstOrNull { it != key && !profile.buttons.containsKey(it) } ?: key
    }

    AlertDialog(
        onDismissRequest = {
            job?.cancel()
            onDismiss()
        },
        title = { Text("Buscar comando") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!running) {
                    Text(
                        "Protocolo " + proto + " · dirección " + "%X".format(power.a) +
                            " (los de tu código). Se prueban solo los comandos."
                    )
                    Box {
                        OutlinedButton(onClick = { keyMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Botón a buscar: " + (KEY_LABEL[key] ?: key) + " ▾")
                        }
                        DropdownMenu(expanded = keyMenu, onDismissRequest = { keyMenu = false }) {
                            for (k in KEY_ORDER) {
                                DropdownMenuItem(
                                    text = {
                                        Text((KEY_LABEL[k] ?: k) + if (profile.buttons.containsKey(k)) " (ya tiene)" else "")
                                    },
                                    onClick = {
                                        key = k
                                        keyMenu = false
                                    }
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = fromTxt,
                            onValueChange = { fromTxt = it.filter { ch -> ch.isDigit() || ch.lowercaseChar() in 'a'..'f' }.take(3) },
                            label = { Text("Desde (hex)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = toTxt,
                            onValueChange = { toTxt = it.filter { ch -> ch.isDigit() || ch.lowercaseChar() in 'a'..'f' }.take(3) },
                            label = { Text("Hasta (hex)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = pauseTxt,
                            onValueChange = { pauseTxt = it.filter { ch -> ch.isDigit() }.take(4) },
                            label = { Text("Pausa ms") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Button(onClick = { startScan() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Iniciar búsqueda")
                    }
                    Text(
                        "Apunta al TV encendido. Cuando el TV haga lo que busca el botón, toca “¡Responde!”. " +
                            "Consejo: los números suelen estar entre 00 y 0F; prueba ese rango primero.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Buscando: " + (KEY_LABEL[key] ?: key))
                            Text(
                                current?.let { hex2(it) } ?: "…",
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                progress.first.toString() + " de " + progress.second,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Button(
                        onClick = { current?.let { pick(it) } },
                        colors = ButtonDefaults.buttonColors(containerColor = GREEN, contentColor = Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                    ) { Text("¡Responde!", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                    previous?.let { prev ->
                        OutlinedButton(onClick = { pick(prev) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Fue el anterior (" + hex2(prev) + ")")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { current?.let { IrEngine.send(ctx, sig(it)) } },
                            modifier = Modifier.weight(1f)
                        ) { Text("Reenviar") }
                        OutlinedButton(onClick = { paused = !paused }, modifier = Modifier.weight(1f)) {
                            Text(if (paused) "Seguir" else "Pausar")
                        }
                        OutlinedButton(onClick = { skip = true }, modifier = Modifier.weight(1f)) {
                            Text("Siguiente")
                        }
                    }
                    TextButton(onClick = {
                        job?.cancel()
                        running = false
                    }) { Text("Detener la búsqueda") }
                }
                notice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                job?.cancel()
                onDismiss()
            }) { Text("Cerrar") }
        }
    )
}
