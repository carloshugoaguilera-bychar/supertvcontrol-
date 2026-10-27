package com.claudia.controltv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PROTOCOLS = listOf(
    "NEC", "NECext", "Samsung32", "SIRC", "SIRC15", "SIRC20", "RC5", "RC6", "RCA", "Kaseikyo"
)

private val MANUAL_KEYS = listOf(
    "power" to "⏻ encender",
    "vol_up" to "Vol +",
    "vol_dn" to "Vol −",
    "ch_up" to "Canal +",
    "ch_dn" to "Canal −",
    "mute" to "Mute",
    "input" to "Entrada"
)

/** Un codigo que respondio al encendido, con el resultado de cada boton probado. */
class Candidate(val profile: Profile) {
    val results = mutableStateMapOf<String, Boolean>()
    fun ok(): Int = results.values.count { it }
    fun bad(): Int = results.values.count { !it }
}

@Composable
fun SearchScreen(store: TvStore, hasIr: Boolean, onSaved: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var suggestions by remember { mutableStateOf<List<Brand>>(emptyList()) }
    var brand by remember { mutableStateOf<BrandData?>(null) }

    var fromTxt by remember { mutableStateOf("1") }
    var toTxt by remember { mutableStateOf("") }
    var delayTxt by remember { mutableStateOf("1500") }
    var directTxt by remember { mutableStateOf("") }

    var running by remember { mutableStateOf(false) }
    var paused by remember { mutableStateOf(false) }
    var skip by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf<Profile?>(null) }
    var previous by remember { mutableStateOf<Profile?>(null) }
    var progress by remember { mutableStateOf(0 to 0) }
    var job by remember { mutableStateOf<Job?>(null) }

    var selected by remember { mutableStateOf<Candidate?>(null) }
    val candidates = remember { mutableStateListOf<Candidate>() }
    var saving by remember { mutableStateOf<Candidate?>(null) }
    var showManual by remember { mutableStateOf(false) }
    var protoMenu by remember { mutableStateOf(false) }
    var mProto by remember { mutableStateOf("NEC") }
    var mAddr by remember { mutableStateOf("") }
    val mCmds = remember { mutableStateMapOf<String, String>() }
    var saveName by remember { mutableStateOf("") }

    fun openBrand(b: Brand) {
        scope.launch {
            loading = true
            error = null
            suggestions = emptyList()
            try {
                val d = Library.brand(ctx, b)
                brand = d
                candidates.clear()
                selected = null
                finished = false
                current = null
                previous = null
                fromTxt = "1"
                toTxt = d.profiles.size.toString()
                directTxt = ""
            } catch (e: Exception) {
                error = "No se pudo descargar la marca " + b.name + " (" + (e.message ?: "error") +
                    "). Revisa tu internet y que el repositorio sea público."
            }
            loading = false
        }
    }

    fun searchBrand() {
        scope.launch {
            loading = true
            error = null
            suggestions = emptyList()
            brand = null
            var single: Brand? = null
            try {
                val found = Library.search(Library.index(ctx), query)
                if (found.isEmpty()) {
                    error = "No encontré esa marca. Revisa cómo está escrita."
                } else if (found.size == 1) {
                    single = found[0]
                } else {
                    suggestions = found
                }
            } catch (e: Exception) {
                error = "No se pudo descargar la lista de marcas (" + (e.message ?: "error") +
                    "). Revisa tu internet y que el repositorio sea público."
            }
            loading = false
            single?.let { openBrand(it) }
        }
    }

    fun openCandidate(p: Profile) {
        val c = candidates.firstOrNull { it.profile.n == p.n } ?: Candidate(p).also { candidates.add(it) }
        selected = c
    }

    fun startScan() {
        val data = brand ?: return
        val total = data.profiles.size
        val from = (fromTxt.toIntOrNull() ?: 1).coerceIn(1, total)
        val to = (toTxt.toIntOrNull() ?: total).coerceIn(from, total)
        val pause = (delayTxt.toLongOrNull() ?: 1500L).coerceIn(300L, 10000L)
        job?.cancel()
        finished = false
        paused = false
        skip = false
        previous = null
        job = scope.launch {
            running = true
            // los encendidos que ya respondieron (candidatos) no se vuelven a probar
            val sent = HashSet<String>()
            for (c in candidates) c.profile.buttons["power"]?.let { sent.add(it.key) }
            val list = data.profiles.filter { it.n in from..to }
            var done = 0
            for (p in list) {
                done++
                val power = p.buttons["power"] ?: continue
                if (!sent.add(power.key)) continue // mismo encendido ya enviado: se salta
                previous = current
                current = p
                progress = done to list.size
                IrEngine.send(ctx, power)
                var waited = 0L
                while (waited < pause && !skip) {
                    delay(50)
                    if (!paused) waited += 50
                }
                skip = false
            }
            running = false
            finished = true
            current = null
        }
    }

    fun pick(p: Profile) {
        job?.cancel()
        running = false
        paused = false
        openCandidate(p)
    }

    fun direct() {
        val data = brand ?: return
        val n = directTxt.toIntOrNull()
        val p = data.profiles.firstOrNull { it.n == n }
        if (p == null) {
            error = "Esa marca tiene códigos del 0001 al " + code4(data.profiles.size)
            return
        }
        error = null
        p.buttons["power"]?.let { IrEngine.send(ctx, it) }
        openCandidate(p)
    }

    fun hex(t: String?): Long? = t?.trim()?.replace(" ", "")?.toLongOrNull(16)

    fun manualTest() {
        val a = hex(mAddr)
        if (a == null) {
            error = "Escribe la dirección en hexadecimal (ej. 04)"
            return
        }
        val buttons = LinkedHashMap<String, Signal>()
        for ((key, _) in MANUAL_KEYS) {
            val c = hex(mCmds[key])
            if (c != null) buttons[key] = Signal(mProto, a = a, c = c)
        }
        val power = buttons["power"]
        if (power == null) {
            error = "Falta el comando de encendido (⏻)"
            return
        }
        error = null
        IrEngine.send(ctx, power)
        val cand = Candidate(Profile(0, "Código manual " + mProto, buttons))
        candidates.add(cand)
        selected = cand
    }

    fun brandLabelOf(c: Candidate): String = when {
        c.profile.n == 0 -> "Manual"
        brand?.name == "Todas las marcas" -> c.profile.model.substringBefore(" · ")
        else -> brand?.name ?: ""
    }

    BackHandler {
        if (selected != null) {
            selected = null
        } else if (running) {
            job?.cancel()
            running = false
        } else {
            onBack()
        }
    }

    val sel = selected
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (sel != null) {
            val data = brand
            val myPower = sel.profile.buttons["power"]?.key
            val variants = data?.profiles?.filter {
                it.n != sel.profile.n && it.buttons["power"]?.key == myPower
            } ?: emptyList()
            VerifyPanel(
                c = sel,
                brandName = brandLabelOf(sel),
                variantCount = variants.size,
                onBackToList = { selected = null },
                onVariant = {
                    val next = variants.firstOrNull { it.n > sel.profile.n } ?: variants.firstOrNull()
                    if (next != null) openCandidate(next)
                },
                onKeepSearching = {
                    val total = data?.profiles?.size ?: 0
                    selected = null
                    if (sel.profile.n < total) {
                        fromTxt = (sel.profile.n + 1).toString()
                        startScan()
                    }
                },
                onSave = {
                    saveName = "TV " + brandLabelOf(sel)
                    saving = sel
                }
            )
        } else {
            Row { TextButton(onClick = onBack) { Text("← Volver") } }
            Text("Agregar TV", fontSize = 26.sp, fontWeight = FontWeight.Bold)

            if (!hasIr) {
                Text(
                    "Este celular no tiene emisor IR: puedes buscar códigos, pero no se enviarán señales.",
                    color = MaterialTheme.colorScheme.error
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Marca de tu TV (ej. LG, Samsung)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { searchBrand() })
            )
            Button(
                onClick = { searchBrand() },
                enabled = !loading && query.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Buscar marca") }
            OutlinedButton(
                onClick = { openBrand(Brand("Todas las marcas", "todas", 0)) },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            ) { Text("No está mi marca: probar con todas las marcas") }
            TextButton(onClick = { showManual = !showManual }) {
                Text(if (showManual) "Ocultar código manual" else "Escribir un código manual")
            }
            if (showManual) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Código manual (valores en hexadecimal)", fontWeight = FontWeight.Bold)
                        Box {
                            OutlinedButton(onClick = { protoMenu = true }) { Text("Protocolo: " + mProto + " ▾") }
                            DropdownMenu(expanded = protoMenu, onDismissRequest = { protoMenu = false }) {
                                for (pr in PROTOCOLS) {
                                    DropdownMenuItem(
                                        text = { Text(pr) },
                                        onClick = {
                                            mProto = pr
                                            protoMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = mAddr,
                            onValueChange = { mAddr = it.filter { ch -> ch.isDigit() || ch.lowercaseChar() in 'a'..'f' }.take(8) },
                            label = { Text("Dirección (ej. 04)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        for ((key, label) in MANUAL_KEYS) {
                            OutlinedTextField(
                                value = mCmds[key] ?: "",
                                onValueChange = {
                                    mCmds[key] = it.filter { ch -> ch.isDigit() || ch.lowercaseChar() in 'a'..'f' }.take(8)
                                },
                                label = { Text("Comando " + label + " (ej. 08)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Button(onClick = { manualTest() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Probar encendido")
                        }
                    }
                }
            }

            if (loading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    Text("Descargando…")
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            for (b in suggestions) {
                OutlinedButton(onClick = { openBrand(b) }, modifier = Modifier.fillMaxWidth()) {
                    Text(b.name + " (" + b.count + " códigos)")
                }
            }

            brand?.let { d ->
                val total = d.profiles.size
                HorizontalDivider()
                Text(
                    d.name + ": " + total + " códigos (0001 al " + code4(total) + ")",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )

                Text("¿Ya sabes tu código?")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = directTxt,
                        onValueChange = { directTxt = it.filter { ch -> ch.isDigit() }.take(4) },
                        label = { Text("Número") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { direct() }, enabled = directTxt.isNotBlank()) { Text("Probar") }
                }

                HorizontalDivider()
                Text("O busca tu código probando un rango:")

                if (!running) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = fromTxt,
                            onValueChange = { fromTxt = it.filter { ch -> ch.isDigit() }.take(4) },
                            label = { Text("Desde") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = toTxt,
                            onValueChange = { toTxt = it.filter { ch -> ch.isDigit() }.take(4) },
                            label = { Text("Hasta") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = delayTxt,
                            onValueChange = { delayTxt = it.filter { ch -> ch.isDigit() }.take(5) },
                            label = { Text("Pausa (ms)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Button(
                        onClick = { startScan() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) { Text("Iniciar prueba", fontSize = 18.sp) }
                    Text(
                        "Apunta el celular al TV (con el TV encendido). La app envía “encender/apagar” " +
                            "código por código; cuando el TV responda, toca “¡Responde!”.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (finished) {
                        Text(
                            "Terminó el rango. Si ninguno respondió, prueba otro rango o acerca más el celular al TV.",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Probando el código")
                            Text(
                                current?.let { code4(it.n) } ?: "…",
                                fontSize = 56.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(current?.model ?: "", textAlign = TextAlign.Center)
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
                            .height(68.dp)
                    ) { Text("¡Responde!", fontSize = 22.sp, fontWeight = FontWeight.Bold) }

                    previous?.let { prev ->
                        OutlinedButton(onClick = { pick(prev) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Fue el anterior (" + code4(prev.n) + ")")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { current?.buttons?.get("power")?.let { IrEngine.send(ctx, it) } },
                            modifier = Modifier.weight(1f)
                        ) { Text("Reenviar") }
                        OutlinedButton(onClick = { paused = !paused }, modifier = Modifier.weight(1f)) {
                            Text(if (paused) "Seguir" else "Pausar")
                        }
                        OutlinedButton(onClick = { skip = true }, modifier = Modifier.weight(1f)) { Text("Siguiente") }
                    }
                    TextButton(onClick = {
                        job?.cancel()
                        running = false
                    }) { Text("Detener la prueba") }
                }

                if (candidates.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Candidatos (el mejor primero)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    val ranked = candidates.sortedWith(
                        compareByDescending<Candidate> { it.ok() }.thenBy { it.bad() }
                    )
                    for ((i, c) in ranked.withIndex()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = c }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    (if (i == 0 && c.ok() > 0) "★ " else "") + "Código " + code4(c.profile.n) +
                                        " · " + c.profile.model,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "✓ " + c.ok() + "   ✗ " + c.bad() + "   (de " + c.profile.buttons.size + " botones)",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    saving?.let { c ->
        AlertDialog(
            onDismissRequest = { saving = null },
            title = { Text("Guardar código " + code4(c.profile.n)) },
            text = {
                OutlinedTextField(
                    value = saveName,
                    onValueChange = { saveName = it },
                    singleLine = true,
                    label = { Text("Nombre (ej. TV sala)") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val brandName = brandLabelOf(c)
                    val name = saveName.trim().ifBlank { "TV " + brandName }
                    store.save(store.load() + SavedTv(System.currentTimeMillis(), name, brandName, c.profile))
                    saving = null
                    onSaved()
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { saving = null }) { Text("Cancelar") } }
        )
    }
}

/** Pantalla para probar todos los botones de un codigo y marcar cuales responden. */
@Composable
fun VerifyPanel(
    c: Candidate,
    brandName: String,
    variantCount: Int,
    onBackToList: () -> Unit,
    onVariant: () -> Unit,
    onKeepSearching: () -> Unit,
    onSave: () -> Unit
) {
    val ctx = LocalContext.current
    var lastKey by remember(c) { mutableStateOf<String?>(null) }
    var sendError by remember { mutableStateOf<String?>(null) }
    val keys = KEY_ORDER.filter { c.profile.buttons.containsKey(it) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row { TextButton(onClick = onBackToList) { Text("← Volver") } }
        Text(
            if (c.profile.n == 0) "Código manual" else "Código " + code4(c.profile.n),
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )
        Text(brandName + " · " + c.profile.model, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Toca cada botón y marca si el TV respondió.")

        for (row in keys.chunked(3)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (k in row) {
                    val r = c.results[k]
                    IrKey(
                        label = KEY_LABEL[k] ?: k,
                        enabled = true,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        container = when (r) {
                            true -> GREEN
                            false -> RED
                            null -> MaterialTheme.colorScheme.secondaryContainer
                        },
                        content = if (r == null) MaterialTheme.colorScheme.onSecondaryContainer else Color.White,
                        onPress = {
                            val s = c.profile.buttons[k]
                            if (s != null) {
                                sendError = if (IrEngine.send(ctx, s)) null else "No se pudo enviar (¿emisor IR?)"
                                lastKey = k
                            }
                        }
                    )
                }
                for (i in 0 until (3 - row.size)) Spacer(Modifier.weight(1f))
            }
        }

        lastKey?.let { k ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("¿Respondió “" + (KEY_LABEL[k] ?: k) + "”?", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                c.results[k] = true
                                lastKey = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GREEN, contentColor = Color.White),
                            modifier = Modifier.weight(1f)
                        ) { Text("Sí") }
                        Button(
                            onClick = {
                                c.results[k] = false
                                lastKey = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RED, contentColor = Color.White),
                            modifier = Modifier.weight(1f)
                        ) { Text("No") }
                    }
                }
            }
        }
        sendError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Text(
            "Responden: " + c.ok() + " · No responden: " + c.bad() + " · Sin probar: " + (keys.size - c.results.size),
            fontWeight = FontWeight.SemiBold
        )

        Button(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) { Text("Guardar este código") }
        if (variantCount > 0) {
            OutlinedButton(onClick = onVariant, modifier = Modifier.fillMaxWidth()) {
                Text("Probar otra variante con el mismo encendido (" + variantCount + ")")
            }
        }
        OutlinedButton(onClick = onKeepSearching, modifier = Modifier.fillMaxWidth()) {
            Text("Dejar como candidato y seguir buscando")
        }
        Spacer(Modifier.height(24.dp))
    }
}
