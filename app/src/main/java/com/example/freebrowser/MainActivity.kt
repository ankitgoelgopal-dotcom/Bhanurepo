package com.example.freebrowser

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        val ext = ExtensionManager(this, prefs)
        ext.startBackground()
        val startUrl = intent?.data?.toString()
        setContent {
            MaterialTheme { BrowserApp(prefs, ext, startUrl) }
        }
    }
}

@Composable
fun BrowserApp(prefs: Prefs, ext: ExtensionManager, startUrl: String?) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val tabs = remember {
        mutableStateListOf(BrowserTab(ctx, prefs, ext).also { it.load(startUrl ?: prefs.home) })
    }
    var current by remember { mutableStateOf(0) }
    var screen by remember { mutableStateOf("browser") }
    val tab = tabs[current.coerceIn(0, tabs.lastIndex)]

    fun newTab(url: String? = null) {
        tabs.add(BrowserTab(ctx, prefs, ext).also { it.load(url ?: prefs.home) })
        current = tabs.lastIndex; screen = "browser"
    }

    BackHandler(enabled = screen != "browser" || tab.canBack) {
        if (screen != "browser") screen = "browser" else tab.web.goBack()
    }

    Surface(Modifier.fillMaxSize().systemBarsPadding()) {
        when (screen) {
            "tabs" -> TabsScreen(tabs, current, { current = it; screen = "browser" },
                { i -> if (tabs.size > 1) { tabs.removeAt(i).web.destroy(); current = current.coerceIn(0, tabs.lastIndex) } },
                { newTab() }, { screen = "browser" })
            "settings" -> SettingsScreen(prefs, ext) { screen = "browser" }
            else -> Column(Modifier.fillMaxSize()) {
                AddressBar(tab)
                if (tab.progress in 1..99) LinearProgressIndicator(progress = { tab.progress / 100f }, modifier = Modifier.fillMaxWidth())
                Box(Modifier.weight(1f)) {
                    key(tab) { AndroidView(factory = { tab.web }, modifier = Modifier.fillMaxSize()) }
                }
                BottomBar(tab, prefs, tabs.size, { screen = "tabs" }, { screen = "settings" })
            }
        }
    }
}

@Composable
fun AddressBar(tab: BrowserTab) {
    var text by remember(tab, tab.url) { mutableStateOf(tab.url) }
    OutlinedTextField(
        value = text, onValueChange = { text = it },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        singleLine = true, placeholder = { Text("Search or enter address") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, keyboardType = KeyboardType.Uri),
        keyboardActions = KeyboardActions(onGo = { tab.load(text) })
    )
}

@Composable
fun BottomBar(tab: BrowserTab, prefs: Prefs, count: Int, onTabs: () -> Unit, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { tab.web.goBack() }, enabled = tab.canBack) { Icon(Icons.Default.ArrowBack, "Back") }
        IconButton(onClick = { tab.web.goForward() }, enabled = tab.canForward) { Icon(Icons.Default.ArrowForward, "Forward") }
        IconButton(onClick = { tab.web.reload() }) { Icon(Icons.Default.Refresh, "Refresh") }
        IconButton(onClick = { tab.load(prefs.home) }) { Icon(Icons.Default.Home, "Home") }
        TextButton(onClick = onTabs) { Text("$count") }
        IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsScreen(
    tabs: List<BrowserTab>, current: Int, onSelect: (Int) -> Unit,
    onClose: (Int) -> Unit, onNew: () -> Unit, onBack: () -> Unit
) {
    Column {
        TopAppBar(title = { Text("Tabs") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
            actions = { IconButton(onNew) { Icon(Icons.Default.Add, "New tab") } })
        LazyColumn {
            itemsIndexed(tabs) { i, t ->
                ListItem(
                    modifier = Modifier.clickable { onSelect(i) },
                    headlineContent = { Text(t.title.ifBlank { "New tab" }, maxLines = 1) },
                    supportingContent = { Text(t.url, maxLines = 1) },
                    leadingContent = { if (i == current) Icon(Icons.Default.Check, null) },
                    trailingContent = { IconButton({ onClose(i) }) { Icon(Icons.Default.Close, "Close") } }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(prefs: Prefs, ext: ExtensionManager, onBack: () -> Unit) {
    var bypass by remember { mutableStateOf(prefs.bypass) }
    var home by remember { mutableStateOf(prefs.home) }
    var search by remember { mutableStateOf(prefs.search) }
    var headers by remember { mutableStateOf(prefs.headersText) }
    var msg by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) msg = if (ext.importZip(uri)) "Extension imported" else "Import failed (needs a .zip with manifest.json)"
    }
    Column {
        TopAppBar(title = { Text("Settings") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Back") } })
        LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Strip X-Frame-Options / CSP, add CORS", style = MaterialTheme.typography.titleSmall)
                        Text("Disables page security headers. Only browse sites you trust.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(bypass, { bypass = it; prefs.bypass = it })
                }
            }
            item { OutlinedTextField(home, { home = it; prefs.home = it }, label = { Text("Home page") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(search, { search = it; prefs.search = it }, label = { Text("Search URL (%s = query)") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item {
                OutlinedTextField(headers, { headers = it; prefs.headersText = it },
                    label = { Text("Custom request headers (Name: value per line)") },
                    modifier = Modifier.fillMaxWidth(), minLines = 3)
            }
            item { HorizontalDivider() }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Extensions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Button({ picker.launch(arrayOf("application/zip", "application/octet-stream")) }) { Text("Import .zip") }
                }
                if (msg.isNotEmpty()) Text(msg, style = MaterialTheme.typography.bodySmall)
            }
            if (ext.list.isEmpty()) item { Text("No extensions installed.", style = MaterialTheme.typography.bodySmall) }
            itemsIndexed(ext.list.toList()) { _, e ->
                var on by remember(e.id) { mutableStateOf(prefs.extEnabled(e.id)) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(e.name); Text("v${e.version}", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(on, { on = it; prefs.setExtEnabled(e.id, it); ext.startBackground() })
                    IconButton({ ext.delete(e) }) { Icon(Icons.Default.Delete, "Delete") }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
