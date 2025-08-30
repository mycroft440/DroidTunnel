package com.example.droidtunnel

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.droidtunnel.ui.theme.DroidTunnelTheme
import kotlinx.coroutines.launch
import java.io.Serializable
import kotlin.math.roundToInt

// --- ESTRUTURAS DE DADOS ---

enum class SshConnectionType(val displayName: String) {
    SSHPROXY_PAYLOAD("SSHPROXY+PAYLOAD"),
    SSHPROXY_PAYLOAD_SSL("SSHPROXY+PAYLOAD+SSL"),
    SSHPROXY_SSL("SSHPROXY + SSL"),
    SOCKS5("SOCKS5") // NOVO TIPO
}

data class TunnelConfig(
    val id: Int,
    val name: String,
    val sshHost: String = "",
    val sshPort: String = "22",
    val sshUser: String = "",
    val sshPassword: String = "",
    val connectionType: SshConnectionType = SshConnectionType.SSHPROXY_PAYLOAD,
    val proxyHost: String = "",
    val proxyPort: String = "",
    val payload: String = "",
    val sni: String = ""
) : Serializable

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

// --- Funções de Serviço ---

fun startVpnService(
    context: Context,
    config: TunnelConfig,
    useCompression: Boolean,
    useTcpNoDelay: Boolean,
    useKeepAlive: Boolean,
    useAutoReconnect: Boolean,
    mtu: Int // <-- NOVO PARÂMETRO
) {
    val intent = Intent(context, DroidTunnelVpnService::class.java).apply {
        action = "start"
        putExtra("CONFIG", config)
        putExtra("USE_COMPRESSION", useCompression)
        putExtra("USE_TCP_NO_DELAY", useTcpNoDelay)
        putExtra("USE_KEEP_ALIVE", useKeepAlive)
        putExtra("USE_AUTO_RECONNECT", useAutoReconnect)
        putExtra("MTU", mtu) // <-- ADICIONADO AO INTENT
    }
    context.startService(intent)
}

fun stopVpnService(context: Context) {
    val intent = Intent(context, DroidTunnelVpnService::class.java).apply {
        action = "stop"
    }
    context.startService(intent)
}

// --- Componente Principal da Aplicação ---

@Composable
fun DroidTunnelApp() {
    val context = LocalContext.current
    val configManager = remember { ConfigManager(context) }
    var currentScreen by remember { mutableStateOf(Screen.Main) }
    val configurations = remember { mutableStateListOf<TunnelConfig>().apply { addAll(configManager.loadConfigs()) } }
    var selectedConfig by remember { mutableStateOf(configurations.firstOrNull()) }
    var configToEdit by remember { mutableStateOf<TunnelConfig?>(null) }

    when (currentScreen) {
        Screen.Main -> {
            MainScreen(
                configurations = configurations,
                selectedConfig = selectedConfig,
                onConfigSelected = { selectedConfig = it },
                onAddConfig = {
                    configToEdit = null
                    currentScreen = Screen.AddEditConfig
                },
                onEditConfig = { config ->
                    configToEdit = config
                    currentScreen = Screen.AddEditConfig
                },
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
                initialConfig = configToEdit,
                onSave = { updatedConfig ->
                    if (configToEdit == null) {
                        val newId = (configurations.maxOfOrNull { it.id } ?: 0) + 1
                        configurations.add(updatedConfig.copy(id = newId))
                    } else {
                        val index = configurations.indexOfFirst { it.id == updatedConfig.id }
                        if (index != -1) {
                            configurations[index] = updatedConfig
                        }
                    }
                    configManager.saveConfigs(configurations)
                    selectedConfig = configurations.find { it.id == updatedConfig.id } ?: configurations.lastOrNull()
                    currentScreen = Screen.Main
                },
                onBack = { currentScreen = Screen.Main }
            )
        }
    }
}

// --- Tela Principal ---

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    configurations: List<TunnelConfig>,
    selectedConfig: TunnelConfig?,
    onConfigSelected: (TunnelConfig) -> Unit,
    onAddConfig: () -> Unit,
    onEditConfig: (TunnelConfig) -> Unit,
    onDeleteConfig: (TunnelConfig) -> Unit
) {
    val sshCompressionState = remember { mutableStateOf(false) }
    val tcpNoDelayState = remember { mutableStateOf(true) }
    val keepAliveState = remember { mutableStateOf(true) }
    val autoReconnectState = remember { mutableStateOf(true) }
    val mtuState = remember { mutableStateOf(1500) } // <-- VALOR PADRÃO ATUALIZADO

    var vpnState by remember { mutableStateOf(DroidTunnelVpnService.currentState) }
    var logs by remember { mutableStateOf("Bem-vindo ao DroidTunnel!\n") }
    
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val tabs = listOf("Início", "Configurações")
    val pagerState = rememberPagerState(pageCount = { tabs.size })

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedConfig?.let {
                startVpnService(context, it, sshCompressionState.value, tcpNoDelayState.value, keepAliveState.value, autoReconnectState.value, mtuState.value)
            }
        } else {
            logs += "Erro: Permissão de VPN negada pelo utilizador.\n"
        }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == VpnServiceState.ACTION_STATE_UPDATE) {
                    val stateName = intent.getStringExtra(VpnServiceState.EXTRA_STATE)
                    vpnState = stateName?.let { VpnState.valueOf(it) } ?: VpnState.IDLE
                    
                    val message = intent.getStringExtra(VpnServiceState.EXTRA_MESSAGE)
                    if (message != null) {
                        logs += "$message\n"
                    }
                }
            }
        }
        LocalBroadcastManager.getInstance(context).registerReceiver(
            receiver, IntentFilter(VpnServiceState.ACTION_STATE_UPDATE)
        )
        onDispose {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(receiver)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                sshCompression = sshCompressionState.value,
                onSshCompressionChange = { sshCompressionState.value = it },
                tcpNoDelay = tcpNoDelayState.value,
                onTcpNoDelayChange = { tcpNoDelayState.value = it },
                keepAlive = keepAliveState.value,
                onKeepAliveChange = { keepAliveState.value = it },
                autoReconnect = autoReconnectState.value,
                onAutoReconnectChange = { autoReconnectState.value = it },
                mtu = mtuState.value, // <-- PASSADO PARA O DRAWER
                onMtuChange = { mtuState.value = it } // <-- PASSADO PARA O DRAWER
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
                        0 -> HomeScreen(
                            selectedConfig = selectedConfig,
                            configurations = configurations,
                            onConfigSelected = onConfigSelected,
                            vpnState = vpnState,
                            logs = logs,
                            onConnectClick = {
                                if (vpnState in listOf(VpnState.CONNECTED, VpnState.CONNECTING, VpnState.RECONNECTING)) {
                                    stopVpnService(context)
                                } else {
                                    val vpnIntent = VpnService.prepare(context)
                                    if (vpnIntent != null) {
                                        logs += "A solicitar permissão de VPN...\n"
                                        vpnPermissionLauncher.launch(vpnIntent)
                                    } else {
                                        selectedConfig?.let {
                                            startVpnService(context, it, sshCompressionState.value, tcpNoDelayState.value, keepAliveState.value, autoReconnectState.value, mtuState.value)
                                        }
                                    }
                                }
                            }
                        )
                        1 -> ConfigScreen(configurations, onAddConfig, onEditConfig, onDeleteConfig)
                    }
                }
            }
        }
    }
}

// --- Componentes da UI (Telas e Peças) ---

@Composable
fun HomeScreen(
    selectedConfig: TunnelConfig?,
    configurations: List<TunnelConfig>,
    onConfigSelected: (TunnelConfig) -> Unit,
    vpnState: VpnState,
    logs: String,
    onConnectClick: () -> Unit
) {
    val (buttonColor, buttonText) = when (vpnState) {
        VpnState.CONNECTED -> Color(0xFF2E7D32) to "LIGADO"
        VpnState.CONNECTING -> Color(0xFFF9A825) to "A LIGAR..."
        VpnState.RECONNECTING -> Color(0xFFFFA000) to "A RECONECTAR"
        else -> Color(0xFFC62828) to "DESLIGADO"
    }
    
    val isConnecting = vpnState in listOf(VpnState.CONNECTING, VpnState.CONNECTED, VpnState.RECONNECTING)

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ConfigSelector(selectedConfig, configurations, onConfigSelected, enabled = !isConnecting)
        Spacer(modifier = Modifier.weight(1.5f))
        Box(modifier = Modifier.padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            Button(
                onClick = onConnectClick,
                modifier = Modifier.size(220.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
                enabled = selectedConfig != null
            ) {
                Text(text = buttonText, style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.weight(0.5f))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Logs", style = MaterialTheme.typography.titleMedium); Spacer(modifier = Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth().height(200.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                val scrollState = rememberScrollState()
                LaunchedEffect(logs) {
                    scrollState.animateScrollTo(scrollState.maxValue)
                }
                Text(text = logs, modifier = Modifier.fillMaxSize().padding(8.dp).verticalScroll(scrollState), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun ConfigScreen(
    configurations: List<TunnelConfig>,
    onAddConfig: () -> Unit,
    onEditConfig: (TunnelConfig) -> Unit,
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
                            IconButton(onClick = { onEditConfig(config) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Editar Configuração", tint = MaterialTheme.colorScheme.primary)
                            }
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
    initialConfig: TunnelConfig?,
    onSave: (TunnelConfig) -> Unit,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf(initialConfig?.name ?: "") }
    var sshHost by remember { mutableStateOf(initialConfig?.sshHost ?: "") }
    var sshPort by remember { mutableStateOf(initialConfig?.sshPort ?: "22") }
    var sshUser by remember { mutableStateOf(initialConfig?.sshUser ?: "") }
    var sshPassword by remember { mutableStateOf(initialConfig?.sshPassword ?: "") }
    var selectedType by remember { mutableStateOf(initialConfig?.connectionType ?: SshConnectionType.SSHPROXY_PAYLOAD) }
    var proxyHost by remember { mutableStateOf(initialConfig?.proxyHost ?: "") }
    var proxyPort by remember { mutableStateOf(initialConfig?.proxyPort ?: "") }
    var payload by remember { mutableStateOf(initialConfig?.payload ?: "CONNECT [host_port] [protocol][crlf]Host: [ssh_host][crlf][crlf]") }
    var sni by remember { mutableStateOf(initialConfig?.sni ?: "") }
    val isEditing = initialConfig != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditing) "Editar Configuração" else "Adicionar Configuração") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        if (name.isNotBlank()) {
                            onSave(
                                (initialConfig?.copy(
                                    name = name, sshHost = sshHost, sshPort = sshPort, sshUser = sshUser,
                                    sshPassword = sshPassword, connectionType = selectedType, proxyHost = proxyHost,
                                    proxyPort = proxyPort, payload = payload, sni = sni
                                ) ?: TunnelConfig(
                                    id = 0, name = name, sshHost = sshHost, sshPort = sshPort, sshUser = sshUser,
                                    sshPassword = sshPassword, connectionType = selectedType, proxyHost = proxyHost,
                                    proxyPort = proxyPort, payload = payload, sni = sni
                                ))
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
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome da Configuração") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("Detalhes da Ligação SSH", style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = sshHost, onValueChange = { sshHost = it }, label = { Text("Servidor SSH") }, modifier = Modifier.weight(2f), singleLine = true)
                OutlinedTextField(value = sshPort, onValueChange = { sshPort = it }, label = { Text("Porta") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            }
             OutlinedTextField(value = sshUser, onValueChange = { sshUser = it }, label = { Text("Utilizador") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
             OutlinedTextField(value = sshPassword, onValueChange = { sshPassword = it }, label = { Text("Senha") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
            Divider(modifier = Modifier.padding(vertical=8.dp))
            Text("Detalhes do Proxy/Payload", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(value = "SSH (Secure Shell)", onValueChange = {}, label = { Text("Protocolo") }, readOnly = true, modifier = Modifier.fillMaxWidth())
            TypeSelector(selectedType = selectedType, onTypeSelected = { selectedType = it })
            // MOSTRAR CAMPOS DE PROXY PARA TODOS OS TIPOS, EXCETO LIGAÇÃO DIRETA (que não existe de momento)
            if (selectedType in listOf(SshConnectionType.SSHPROXY_PAYLOAD, SshConnectionType.SSHPROXY_PAYLOAD_SSL, SshConnectionType.SSHPROXY_SSL, SshConnectionType.SOCKS5)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = proxyHost, onValueChange = { proxyHost = it }, label = { Text("Proxy/SOCKS Host") }, modifier = Modifier.weight(2f), singleLine = true)
                    OutlinedTextField(value = proxyPort, onValueChange = { proxyPort = it }, label = { Text("Porta") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                }
            }
            if (selectedType in listOf(SshConnectionType.SSHPROXY_PAYLOAD_SSL, SshConnectionType.SSHPROXY_SSL)) {
                OutlinedTextField(value = sni, onValueChange = { sni = it }, label = { Text("SNI (Server Name Indication)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            if (selectedType in listOf(SshConnectionType.SSHPROXY_PAYLOAD, SshConnectionType.SSHPROXY_PAYLOAD_SSL)) {
                OutlinedTextField(value = payload, onValueChange = { payload = it }, label = { Text("Payload") }, modifier = Modifier.fillMaxWidth().height(150.dp), maxLines = 5)
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
    keepAlive: Boolean, onKeepAliveChange: (Boolean) -> Unit,
    autoReconnect: Boolean, onAutoReconnectChange: (Boolean) -> Unit,
    mtu: Int, onMtuChange: (Int) -> Unit // <-- NOVOS PARÂMETROS
) {
    ModalDrawerSheet {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Opções Avançadas", style = MaterialTheme.typography.titleLarge); Spacer(modifier = Modifier.height(8.dp)); Divider(); Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = sshCompression, onCheckedChange = onSshCompressionChange); Text("Compressão SSH") }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = tcpNoDelay, onCheckedChange = onTcpNoDelayChange); Text("TCP No Delay") }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = keepAlive, onCheckedChange = onKeepAliveChange); Text("KeepAlive") }
            Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked = autoReconnect, onCheckedChange = onAutoReconnectChange); Text("Reconexão Automática") }
            
            // --- NOVO SLIDER DE MTU ---
            Spacer(modifier = Modifier.height(16.dp))
            Text("Ajuste de MTU: $mtu", style = MaterialTheme.typography.bodyLarge)
            Slider(
                value = mtu.toFloat(),
                onValueChange = { onMtuChange(it.roundToInt()) },
                valueRange = 1280f..1500f,
                steps = (1500 - 1280) / 10 - 1 // Passos de 10 em 10
            )
        }
    }
}

@Composable
fun ConfigSelector(
    selectedConfig: TunnelConfig?,
    configurations: List<TunnelConfig>,
    onConfigSelected: (TunnelConfig) -> Unit,
    enabled: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedCard(onClick = { if (configurations.isNotEmpty() && enabled) expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = selectedConfig?.name ?: "Selecione uma Configuração",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) LocalContentColor.current else Color.Gray
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown", tint = if (enabled) LocalContentColor.current else Color.Gray)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.fillMaxWidth(0.9f)) {
            configurations.forEach { config ->
                DropdownMenuItem(text = { Text(config.name) }, onClick = { onConfigSelected(config); expanded = false })
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



