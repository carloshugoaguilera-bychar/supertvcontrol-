package com.claudia.controltv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Ventana para agregar o reemplazar a mano un boton de un TV guardado.
 * El protocolo, la direccion y el comando se escriben en hexadecimal (ej. NEC, 04, 08).
 */
@Composable
fun AddButtonDialog(
    initialKey: String,
    protoHint: String,
    addrHint: String,
    onTest: (Signal) -> Unit,
    onSave: (String, Signal) -> Unit,
    onDismiss: () -> Unit
) {
    var key by remember { mutableStateOf(initialKey) }
    var proto by remember { mutableStateOf(if (protoHint in PROTOCOLS) protoHint else "NEC") }
    var addr by remember { mutableStateOf(addrHint) }
    var cmd by remember { mutableStateOf("") }
    var keyMenu by remember { mutableStateOf(false) }
    var protoMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun build(): Signal? {
        val a = hexOrNull(addr)
        val c = hexOrNull(cmd)
        if (a == null || c == null) {
            error = "Escribe la dirección y el comando en hexadecimal (0-9 y A-F)"
            return null
        }
        error = null
        return Signal(proto, a = a, c = c)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar botón a mano") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box {
                    OutlinedButton(onClick = { keyMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Botón: " + (KEY_LABEL[key] ?: key) + " ▾")
                    }
                    DropdownMenu(expanded = keyMenu, onDismissRequest = { keyMenu = false }) {
                        for (k in KEY_ORDER) {
                            DropdownMenuItem(
                                text = { Text(KEY_LABEL[k] ?: k) },
                                onClick = {
                                    key = k
                                    keyMenu = false
                                }
                            )
                        }
                    }
                }
                Box {
                    OutlinedButton(onClick = { protoMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Protocolo: " + proto + " ▾")
                    }
                    DropdownMenu(expanded = protoMenu, onDismissRequest = { protoMenu = false }) {
                        for (pr in PROTOCOLS) {
                            DropdownMenuItem(
                                text = { Text(pr) },
                                onClick = {
                                    proto = pr
                                    protoMenu = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = addr,
                    onValueChange = { addr = it.filter { ch -> ch.isDigit() || ch.lowercaseChar() in 'a'..'f' }.take(8) },
                    label = { Text("Dirección (ej. 04)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = cmd,
                    onValueChange = { cmd = it.filter { ch -> ch.isDigit() || ch.lowercaseChar() in 'a'..'f' }.take(8) },
                    label = { Text("Comando (ej. 08)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedButton(
                    onClick = { build()?.let { onTest(it) } },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Probar este botón") }
            }
        },
        confirmButton = {
            TextButton(onClick = { build()?.let { onSave(key, it) } }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
