package br.com.porteirinho.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.porteirinho.data.local.AlertEntity
import br.com.porteirinho.data.local.UserEntity
import br.com.porteirinho.data.local.UserRole
import br.com.porteirinho.domain.ActivePatrolSnapshot
import br.com.porteirinho.domain.AvailablePatrol
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

@Composable
fun PorteirinhoApp(viewModel: AppViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val users by viewModel.users.collectAsStateWithLifecycle()
    val alerts by viewModel.alerts.collectAsStateWithLifecycle()
    val pendingSync by viewModel.pendingSync.collectAsStateWithLifecycle()
    val permanentFailures by viewModel.permanentFailures.collectAsStateWithLifecycle()
    val executions by viewModel.executionCount.collectAsStateWithLifecycle()
    val problems by viewModel.problemExecutionCount.collectAsStateWithLifecycle()
    val unresolved by viewModel.unresolvedAlerts.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    state.message?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }
    BackHandler(enabled = state.screen != AppScreen.AreaChoice, onBack = viewModel::back)

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val screen = state.screen) {
                AppScreen.AreaChoice -> AreaChoiceScreen(viewModel::chooseArea)
                is AppScreen.ProfileChoice -> ProfileChoiceScreen(
                    role = screen.role,
                    users = users.filter { it.role == screen.role },
                    onProfile = viewModel::chooseProfile,
                    onBack = viewModel::back,
                )
                is AppScreen.PinLogin -> PinLoginScreen(
                    user = users.firstOrNull { it.id == screen.userId },
                    busy = state.busy,
                    onLogin = { viewModel.login(screen.userId, it) },
                    onBack = viewModel::back,
                )
                AppScreen.GatekeeperHome -> GatekeeperHomeScreen(
                    user = state.authenticatedUser,
                    shiftActive = state.shiftActive,
                    patrols = state.availablePatrols,
                    busy = state.busy,
                    pendingSync = pendingSync,
                    onStartShift = viewModel::startShift,
                    onFinishShift = viewModel::finishShift,
                    onStartPatrol = viewModel::startPatrol,
                    onExit = viewModel::back,
                )
                AppScreen.Patrol -> PatrolScreen(
                    patrol = state.activePatrol,
                    busy = state.busy,
                    onScan = viewModel::openScanner,
                    onFinish = viewModel::finishPatrol,
                )
                AppScreen.Scanner -> QrScannerView(viewModel::processScan, viewModel::cancelScanner)
                AppScreen.AdminDashboard -> AdminDashboardScreen(
                    executionCount = executions,
                    problemCount = problems,
                    unresolvedAlerts = unresolved,
                    pendingSync = pendingSync,
                    permanentFailures = permanentFailures,
                    onAlerts = viewModel::openAdminAlerts,
                    onExit = viewModel::back,
                )
                AppScreen.AdminAlerts -> AlertsScreen(alerts, viewModel::resolveAlert, viewModel::back)
            }

            if (state.busy) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun AreaChoiceScreen(onChoose: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(18.dp), modifier = Modifier.size(64.dp)) {
            Box(contentAlignment = Alignment.Center) { Text("P", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.height(24.dp))
        Text("Porteirinho", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text("Rondas comprovadas, mesmo sem internet.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(48.dp))
        AreaCard("PORTARIA", "Iniciar turno e executar rondas") { onChoose(UserRole.GATEKEEPER) }
        Spacer(Modifier.height(16.dp))
        AreaCard("ADMINISTRAÇÃO", "Acompanhar operação, alertas e cadastros") { onChoose(UserRole.ADMIN) }
        Spacer(Modifier.height(28.dp))
        Text("Dados operacionais permanecem salvos neste aparelho até a confirmação do servidor.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AreaCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(22.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProfileChoiceScreen(role: String, users: List<UserEntity>, onProfile: (String) -> Unit, onBack: () -> Unit) {
    ScreenColumn(title = if (role == UserRole.ADMIN) "Administração" else "Quem está na portaria?", onBack = onBack) {
        if (users.isEmpty()) Text("Nenhum perfil ativo disponível.")
        users.forEach { user ->
            Card(Modifier.fillMaxWidth().clickable { onProfile(user.id) }.padding(vertical = 6.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Initials(user.displayName)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(user.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(if (user.role == UserRole.ADMIN) "Administrador" else "Porteiro", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun PinLoginScreen(user: UserEntity?, busy: Boolean, onLogin: (String) -> Unit, onBack: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    ScreenColumn(title = "Acesso seguro", onBack = onBack) {
        Spacer(Modifier.height(28.dp))
        user?.let {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Initials(it.displayName, 76.dp) }
            Text(it.displayName, Modifier.fillMaxWidth().padding(top = 12.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { value -> pin = value.filter(Char::isDigit).take(8) },
            label = { Text("PIN pessoal") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onLogin(pin) }, enabled = pin.length >= 4 && !busy, modifier = Modifier.fillMaxWidth().height(54.dp)) {
            Text("Entrar")
        }
        Text("Ambiente de demonstração: PIN 1234", Modifier.padding(top = 14.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GatekeeperHomeScreen(
    user: UserEntity?,
    shiftActive: Boolean,
    patrols: List<AvailablePatrol>,
    busy: Boolean,
    pendingSync: Int,
    onStartShift: () -> Unit,
    onFinishShift: () -> Unit,
    onStartPatrol: (String) -> Unit,
    onExit: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Olá, ${user?.displayName?.substringBefore(' ') ?: "porteiro"}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(if (shiftActive) "Turno em andamento" else "Turno ainda não iniciado", color = if (shiftActive) Color(0xFF16875A) else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onExit) { Text("Sair") }
            }
        }
        item { SyncBadge(pendingSync) }
        if (!shiftActive) {
            item {
                Button(onClick = onStartShift, enabled = !busy, modifier = Modifier.fillMaxWidth().height(58.dp)) { Text("Iniciar turno") }
            }
        } else {
            item { Text("Rondas disponíveis", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            if (patrols.isEmpty()) item { Text("Nenhuma ronda disponível para este perfil e horário.") }
            items(patrols, key = { it.schedule.id }) { patrol ->
                PatrolCard(patrol, onStartPatrol)
            }
            item {
                OutlinedButton(onClick = onFinishShift, modifier = Modifier.fillMaxWidth()) { Text("Encerrar turno") }
            }
        }
        item { Spacer(Modifier.navigationBarsPadding()) }
    }
}

@Composable
private fun PatrolCard(patrol: AvailablePatrol, onStart: (String) -> Unit) {
    Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(patrol.schedule.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${patrol.windowLabel}  •  ${patrol.pointCount} pontos", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            Button(onClick = { onStart(patrol.schedule.id) }, enabled = patrol.availableNow, modifier = Modifier.fillMaxWidth()) {
                Text(if (patrol.availableNow) "Iniciar ronda" else "Fora do horário")
            }
        }
    }
}

@Composable
private fun PatrolScreen(patrol: ActivePatrolSnapshot?, busy: Boolean, onScan: () -> Unit, onFinish: () -> Unit) {
    if (patrol == null) return
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(patrol.executionId) {
        while (true) {
            elapsed = System.currentTimeMillis() - patrol.startedAtEpochMillis
            delay(1_000)
        }
    }
    val minutes = elapsed / 60_000
    val seconds = (elapsed / 1_000) % 60
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text(patrol.scheduleName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Em andamento • %02d:%02d".format(minutes, seconds), color = MaterialTheme.colorScheme.primary)
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text("${patrol.completedPoints} de ${patrol.totalPoints} pontos concluídos", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(progress = { patrol.progress }, modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape))
                    Text("${(patrol.progress * 100).toInt()}%", Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = TextAlign.End)
                }
            }
        }
        items(patrol.checkpointNames, key = { it.first }) { (id, name) ->
            val done = id in patrol.visitedPointIds
            Row(Modifier.fillMaxWidth().background(if (done) Color(0xFFE0F4EA) else MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp)).padding(16.dp)) {
                Text(if (done) "✓" else "○", color = if (done) Color(0xFF16875A) else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(12.dp))
                Text(name, fontWeight = if (done) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
        item {
            Button(onClick = onScan, enabled = !busy && patrol.completedPoints < patrol.totalPoints, modifier = Modifier.fillMaxWidth().height(58.dp)) { Text("Escanear QR Code") }
        }
        item {
            OutlinedButton(onClick = onFinish, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (patrol.completedPoints < patrol.totalPoints) "Finalizar como incompleta" else "Finalizar ronda")
            }
        }
        item { Spacer(Modifier.navigationBarsPadding()) }
    }
}

@Composable
private fun AdminDashboardScreen(
    executionCount: Int,
    problemCount: Int,
    unresolvedAlerts: Int,
    pendingSync: Int,
    permanentFailures: Int,
    onAlerts: () -> Unit,
    onExit: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Administração", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Visão operacional deste dispositivo", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onExit) { Text("Sair") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KpiCard("Rondas", executionCount.toString(), Modifier.weight(1f))
                KpiCard("Com alerta", problemCount.toString(), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                KpiCard("Pendentes", pendingSync.toString(), Modifier.weight(1f))
                KpiCard("Falhas", permanentFailures.toString(), Modifier.weight(1f), permanentFailures > 0)
            }
        }
        item { AdminMenuCard("Central de alertas", "$unresolvedAlerts alertas não resolvidos", onAlerts) }
        item { AdminMenuCard("Locais e pontos", "Condomínio → bloco → andar → local → QR", {}) }
        item { AdminMenuCard("Programações", "Horários, tolerância, pontos e responsáveis", {}) }
        item { AdminMenuCard("Porteiros e dispositivos", "Perfis, bloqueios e aparelhos autorizados", {}) }
        item { AdminMenuCard("Histórico e auditoria", "Linha do tempo e alterações administrativas", {}) }
        item {
            Text(
                "Os cadastros completos serão habilitados quando o projeto Supabase for conectado. O painel já acompanha a operação offline deste aparelho.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { Spacer(Modifier.navigationBarsPadding()) }
    }
}

@Composable
private fun AlertsScreen(alerts: List<AlertEntity>, onResolve: (String) -> Unit, onBack: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { HeaderRow("Central de alertas", onBack) }
        if (alerts.isEmpty()) item { Text("Nenhum alerta registrado.") }
        items(alerts, key = { it.id }) { alert ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (alert.resolved) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text(alert.type.replace('_', ' '), fontWeight = FontWeight.Bold)
                    Text(alert.description)
                    Text(DateFormat.getDateTimeInstance().format(Date(alert.createdAtEpochMillis)), style = MaterialTheme.typography.bodySmall)
                    if (!alert.resolved) TextButton(onClick = { onResolve(alert.id) }) { Text("Marcar como resolvido") }
                }
            }
        }
    }
}

@Composable
private fun KpiCard(label: String, value: String, modifier: Modifier, danger: Boolean = false) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = if (danger) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AdminMenuCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SyncBadge(count: Int) {
    Surface(color = if (count == 0) Color(0xFFE0F4EA) else Color(0xFFFFF1CC), shape = CircleShape) {
        Text(if (count == 0) "Tudo sincronizado" else "$count eventos aguardando internet", Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Initials(name: String, size: androidx.compose.ui.unit.Dp = 52.dp) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(size)) {
        Box(contentAlignment = Alignment.Center) {
            Text(name.split(' ').take(2).mapNotNull { it.firstOrNull()?.uppercase() }.joinToString(""), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun ScreenColumn(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp)) {
        HeaderRow(title, onBack)
        Spacer(Modifier.height(16.dp))
        content()
    }
}

@Composable
private fun HeaderRow(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text("Voltar") }
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
}
