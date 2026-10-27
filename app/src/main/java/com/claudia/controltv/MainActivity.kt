package com.claudia.controltv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppRoot() }
    }
}

sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data class Remote(val tvId: Long) : Screen
}

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val store = remember { TvStore(ctx) }
    var tvs by remember { mutableStateOf(store.load()) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    val hasIr = remember { IrEngine.hasEmitter(ctx) }

    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (val s = screen) {
                is Screen.Home -> HomeScreen(
                    tvs = tvs,
                    hasIr = hasIr,
                    onAdd = { screen = Screen.Search },
                    onOpen = { screen = Screen.Remote(it.id) },
                    onRename = { tv, name ->
                        tvs = tvs.map { if (it.id == tv.id) SavedTv(it.id, name, it.brand, it.profile) else it }
                        store.save(tvs)
                    },
                    onDelete = { tv ->
                        tvs = tvs.filter { it.id != tv.id }
                        store.save(tvs)
                    }
                )

                is Screen.Search -> SearchScreen(
                    store = store,
                    hasIr = hasIr,
                    onSaved = {
                        tvs = store.load()
                        screen = Screen.Home
                    },
                    onBack = { screen = Screen.Home }
                )

                is Screen.Remote -> {
                    val tv = tvs.firstOrNull { it.id == s.tvId }
                    if (tv == null) {
                        LaunchedEffect(Unit) { screen = Screen.Home }
                    } else {
                        RemoteScreen(tv = tv, onBack = { screen = Screen.Home })
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    tvs: List<SavedTv>,
    hasIr: Boolean,
    onAdd: () -> Unit,
    onOpen: (SavedTv) -> Unit,
    onRename: (SavedTv, String) -> Unit,
    onDelete: (SavedTv) -> Unit
) {
    var renaming by remember { mutableStateOf<SavedTv?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<SavedTv?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Control TV", fontSize = 28.sp, fontWeight = FontWeight.Bold)

        if (!hasIr) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(
                    "Este celular no tiene emisor infrarrojo (IR). Puedes buscar y guardar códigos, " +
                        "pero no se enviarán señales al TV.",
                    modifier = Modifier.padding(14.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        Button(onClick = onAdd, modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)) {
            Text("＋ Agregar TV", fontSize = 18.sp)
        }

        if (tvs.isEmpty()) {
            Text(
                "Todavía no hay TVs guardados. Toca “Agregar TV”, escribe la marca y prueba códigos hasta que tu TV responda.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        for (tv in tvs) {
            Card(modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpen(tv) }) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(tv.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        tv.brand + " · código " + code4(tv.profile.n),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row {
                        TextButton(onClick = {
                            renameText = tv.name
                            renaming = tv
                        }) { Text("Renombrar") }
                        TextButton(onClick = { deleting = tv }) { Text("Borrar") }
                    }
                }
            }
        }
    }

    renaming?.let { tv ->
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Renombrar TV") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Nombre") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) onRename(tv, renameText.trim())
                    renaming = null
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancelar") } }
        )
    }

    deleting?.let { tv ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Borrar TV") },
            text = { Text("¿Borrar “" + tv.name + "”?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(tv)
                    deleting = null
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
fun RemoteScreen(tv: SavedTv, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var showNumbers by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    val p = tv.profile

    BackHandler { onBack() }

    fun press(key: String) {
        val s = p.buttons[key] ?: return
        msg = if (IrEngine.send(ctx, s)) null else "No se pudo enviar: ¿el celular tiene emisor IR?"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row {
            TextButton(onClick = onBack) { Text("← Volver") }
        }
        Text(tv.name, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            tv.brand + " · código " + code4(p.n),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RemoteKey(p, "power", ::press, container = RED, content = Color.White)
            RemoteKey(p, "input", ::press)
            RemoteKey(p, "mute", ::press)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RemoteKey(p, "vol_up", ::press, repeatOnHold = true)
            RemoteKey(p, "ch_up", ::press, repeatOnHold = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RemoteKey(p, "vol_dn", ::press, repeatOnHold = true)
            RemoteKey(p, "ch_dn", ::press, repeatOnHold = true)
        }

        Spacer(Modifier.height(6.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Spacer(Modifier.weight(1f))
            RemoteKey(p, "up", ::press, repeatOnHold = true)
            Spacer(Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RemoteKey(p, "left", ::press, repeatOnHold = true)
            RemoteKey(p, "ok", ::press, container = MaterialTheme.colorScheme.primary, content = MaterialTheme.colorScheme.onPrimary)
            RemoteKey(p, "right", ::press, repeatOnHold = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Spacer(Modifier.weight(1f))
            RemoteKey(p, "down", ::press, repeatOnHold = true)
            Spacer(Modifier.weight(1f))
        }

        Spacer(Modifier.height(6.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RemoteKey(p, "back", ::press)
            RemoteKey(p, "home", ::press)
            RemoteKey(p, "menu", ::press)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RemoteKey(p, "exit", ::press)
            RemoteKey(p, "info", ::press)
            Spacer(Modifier.weight(1f))
        }

        TextButton(onClick = { showNumbers = !showNumbers }) {
            Text(if (showNumbers) "Ocultar números" else "Mostrar números")
        }
        if (showNumbers) {
            for (row in listOf(listOf("d1", "d2", "d3"), listOf("d4", "d5", "d6"), listOf("d7", "d8", "d9"))) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (k in row) RemoteKey(p, k, ::press)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Spacer(Modifier.weight(1f))
                RemoteKey(p, "d0", ::press)
                Spacer(Modifier.weight(1f))
            }
        }

        msg?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
