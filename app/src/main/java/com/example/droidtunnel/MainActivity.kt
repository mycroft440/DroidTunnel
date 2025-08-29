package com.example.droidtunnel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.droidtunnel.ui.theme.DroidTunnelTheme
import kotlinx.coroutines.launch

// --- ESTRUTURAS DE DADOS ---

enum class SshConnectionType(val displayName: String) {
    SSHPROXY_PAYLOAD("SSHPROXY+PAYLOAD"),
    SSHPROXY_PAYLOAD_SSL("SSHPROXY+PAYLOAD+SSL"),
    SSHPROXY_SSL("SSHPROXY + SSL")
}

data class TunnelConfig(
    val id: Int,
    val name: String,
    val connectionType: SshConnectionType = SshConnectionType.SSHPROXY_PAYLOAD,
    val proxyHost: String = "",
    val proxyPort: String = "",
    val payload: String = "",
    val sni: String = ""
)

enum class Screen {
    Main,
    AddEditConfig
}


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DroidTunnelTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DroidTunnelApp()
                }
            }
        }
    }
}

@Composable
fun DroidTunnelApp() {
    val context = LocalContext.current
    val configManager = remember { ConfigManager(context) }
    var currentScreen by remember { mutableStateOf(Screen.Main) }
    val configurations = remember { mutableStateListOf<TunnelConfig>().apply { addAll(configManager.loadConfigs()) } }
    var selectedConfig by remember { mutableStateOf(configurations.firstOrNull()) }

    when (currentScreen) {
        Screen.Main -> {
            MainScreen(
                configurations = configurations,
                selectedConfig = selectedConfig,
                onConfigSelected = { selectedConfig = it },
                onAddConfig = { currentScreen = Screen.AddEditConfig },
                onDeleteConfig = { configToDelete ->
                    configurations.remove(configToDelete)
                    configManager.saveConfigs(configurations)
                    if (selectedConfig == configToDelete) {
                        selectedConfig = configurations.firstOrNull()
                    }
                }
            )
        }
        Screen.AddEditConfig -> {
            AddEditConfigScreen(
                onSave = { newConfig ->
                    val newId = (configurations.maxOfOrNull { it.id } ?: 0) + 1
                    configurations.add(newConfig.copy(id = newId))
                    configManager.saveConfigs(configurations)
                    currentScreen = Screen.Main
                },
                onBack = { currentScreen = Screen.Main }
            )
        }
    }
}


@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    configurations: List<TunnelConfig>,
    selectedConfig: TunnelConfig?,
    onConfigSelected: (TunnelConfig) -> Unit,
    onAddConfig: () -> Unit,
    onDeleteConfig: (TunnelConfig) -> Unit
) {
    val sshCompressionState = remember { mutableStateOf(false) }
    val tcpNoDelayState = remember { mutableStateOf(true) }
    val keepAliveState = remember { mutableStateOf(true) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val tabs = listOf("Início", "Configurações")
    val pagerState = rememberPagerState(pageCount = { tabs.size })

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                sshCompression = sshCompressionState.value,
                onSshCompressionChange = { sshCompressionState.value = it },
                tcpNoDelay = tcpNoDelayState.value,
                onTcpNoDelayChange = { tcpNoDelayState.value = it },
                keepAlive = keepAliveState.value,
                onKeepAliveChange = { keepAliveState.value = it }
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("DroidTunnel") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.apply { if (isClosed) open() else close() } } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Abrir menu")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding)) {
                TabRow(selectedTabIndex = pagerState.currentPage) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = { Text(title) }
                        )
                    }
                }
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().weight(1f)) { page ->
                    when (page) {
                        0 -> HomeScreen(selectedConfig, configurations, onConfigSelected)
                        1 -> ConfigScreen(configurations, onAddConfig, onDeleteConfig)
                    }
                }
            }
        }
    }
}

@Composable
fun ConfigScreen(
    configurations: List<TunnelConfig>,
    onAddConfig: () -> Unit,
    onDeleteConfig: (TunnelConfig) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (configurations.isEmpty()) {
            Text(text = "Nenhuma configuração adicionada.", modifier = Modifier.align(Alignment.Center), style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(bottom = 72.dp)) {
                items(configurations) { config ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.padding(start = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = config.name, modifier = Modifier.weight(1f))
                            IconButton(onClick = { onDeleteConfig(config) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Apagar Configuração", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
        Button(onClick = onAddConfig, modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)) {
            Text("+ Adicionar Configuração")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditConfigScreen(
    onSave: (TunnelConfig) -> Unit,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(SshConnectionType.SSHPROXY_PAYLOAD) }
    var proxyHost by remember { mutableStateOf("") }
    var proxyPort by remember { mutableStateOf("") }
    var payload by remember { mutableStateOf("") }
    var sni by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Adicionar Configuração") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        if (name.isNotBlank()) {
                            onSave(
                                TunnelConfig(
                                    id = 0, name = name, connectionType = selectedType,
                                    proxyHost = proxyHost, proxyPort = proxyPort, payload = payload, sni = sni
                                )
                            )
                        }
                    }) {
                        Text("GUARDAR")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome da Configuração") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = "SSH (Secure Shell)",
                onValueChange = {},
                label = { Text("Protocolo") },
                readOnly = true,
                modifier = Modifier.fillMaxWidth()
            )
            TypeSelector(
                selectedType = selectedType,
                onTypeSelected = { selectedType = it }
            )

            if (selectedType in listOf(SshConnectionType.SSHPROXY_PAYLOAD, SshConnectionType.SSHPROXY_PAYLOAD_SSL, SshConnectionType.SSHPROXY_SSL)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = proxyHost,
                        onValueChange = { proxyHost = it },
                        label = { Text("Proxy Remoto") },
                        modifier = Modifier.weight(2f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = proxyPort,
                        onValueChange = { proxyPort = it },
                        label = { Text("Porta") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            }

            if (selectedType in listOf(SshConnectionType.SSHPROXY_PAYLOAD_SSL, SshConnectionType.SSHPROXY_SSL)) {
                OutlinedTextField(
                    value = sni,
                    onValueChange = { sni = it },
                    label = { Text("SNI (Server Name Indication)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            if (selectedType in listOf(SshConnectionType.SSHPROXY_PAYLOAD, SshConnectionType.SSHPROXY_PAYLOAD_SSL)) {
                OutlinedTextField(
                    value = payload,
                    onValueChange = { payload = it },
                    label = { Text("Payload") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    maxLines = 5
                )
            }
        }
    }
}

@Composable
fun TypeSelector(
    selectedType: SshConnectionType,
    onTypeSelected: (SshConnectionType) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val options = SshConnectionType.values()

    Column {
        Text("Tipo de Configuração", style = MaterialTheme.typography.labelLarge)
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedCard(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = selectedType.displayName, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                options.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type.displayName) },
                        onClick = {
                            onTypeSelected(type)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AppDrawerContent(
    sshCompression: Boolean, onSshCompressionChange: (Boolean) -> Unit,
    tcpNoDelay: Boolean, onTcpNoDelayChange: (Boolean) -> Unit,
    keepAlive: Boolean, onKeepAliveChange: (Boolean) -> Unit
) {
    ModalDrawerSheet {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Opções Avançadas", style = MaterialTheme.typography.titleLarge); Spacer(modifier = Modifier.height(8.dp)); Divider(); Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = sshCompression, onCheckedChange = onSshCompressionChange); Text("Compressão SSH") }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = tcpNoDelay, onCheckedChange = onTcpNoDelayChange); Text("TCP No Delay") }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = keepAlive, onCheckedChange = onKeepAliveChange); Text("KeepAlive") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigSelector(
    selectedConfig: TunnelConfig?,
    configurations: List<TunnelConfig>,
    onConfigSelected: (TunnelConfig) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedCard(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = selectedConfig?.name ?: "Selecione uma Configuração", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.fillMaxWidth(0.9f)) {
            configurations.forEach { config ->
                DropdownMenuItem(text = { Text(config.name) }, onClick = { onConfigSelected(config); expanded = false })
            }
        }
    }
}

@Composable
fun HomeScreen(
    selectedConfig: TunnelConfig?,
    configurations: List<TunnelConfig>,
    onConfigSelected: (TunnelConfig) -> Unit
) {
    var isConnected by remember { mutableStateOf(false) }
    var logs by remember { mutableStateOf("Bem-vindo ao DroidTunnel!\n") }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ConfigSelector(selectedConfig, configurations, onConfigSelected)
        Spacer(modifier = Modifier.weight(1.5f))
        Box(modifier = Modifier.padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            Button(
                onClick = {
                    isConnected = !isConnected
                    logs += if (isConnected) "A iniciar ligação com '${selectedConfig?.name}'...\n" else "A parar ligação...\n"
                },
                modifier = Modifier.size(220.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = if (isConnected) Color(0xFF2E7D32) else Color(0xFFC62828)),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
                enabled = selectedConfig != null
            ) {
                Text(text = if (isConnected) "LIGADO" else "DESLIGADO", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.weight(0.5f))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Logs", style = MaterialTheme.typography.titleMedium); Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth().height(200.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                val scrollState = rememberScrollState()
                LaunchedEffect(logs) { scrollState.animateScrollTo(scrollState.maxValue) }
                Text(text = logs, modifier = Modifier.fillMaxSize().padding(8.dp).verticalScroll(scrollState), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    DroidTunnelTheme {
        DroidTunnelApp()
    }
}

