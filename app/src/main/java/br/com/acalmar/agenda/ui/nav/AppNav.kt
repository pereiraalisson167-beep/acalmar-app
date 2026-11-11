package br.com.acalmar.agenda.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import br.com.acalmar.agenda.ui.LocalNotifier
import br.com.acalmar.agenda.ui.Notifier
import br.com.acalmar.agenda.ui.agenda.AgendaScreen
import br.com.acalmar.agenda.ui.agenda.AppointmentFormScreen
import br.com.acalmar.agenda.ui.agenda.BlockFormScreen
import br.com.acalmar.agenda.ui.cash.CashScreen
import br.com.acalmar.agenda.ui.clients.AnamnesisScreen
import br.com.acalmar.agenda.ui.clients.ClientDetailScreen
import br.com.acalmar.agenda.ui.clients.ClientFormScreen
import br.com.acalmar.agenda.ui.clients.ClientsScreen
import br.com.acalmar.agenda.ui.clients.ConsentScreen
import br.com.acalmar.agenda.ui.clients.PhotosScreen
import br.com.acalmar.agenda.ui.clients.RecallScreen
import br.com.acalmar.agenda.ui.clients.SellPackageScreen
import br.com.acalmar.agenda.ui.more.AnamnesisTemplateScreen
import br.com.acalmar.agenda.ui.more.BackupScreen
import br.com.acalmar.agenda.ui.more.BusinessScreen
import br.com.acalmar.agenda.ui.more.ConsentTextScreen
import br.com.acalmar.agenda.ui.more.FormsScreen
import br.com.acalmar.agenda.ui.more.MoreScreen
import br.com.acalmar.agenda.ui.more.ServiceFormScreen
import br.com.acalmar.agenda.ui.more.ServicesScreen
import br.com.acalmar.agenda.ui.more.WorkHoursScreen
import br.com.acalmar.agenda.ui.quotes.QuoteFormScreen
import br.com.acalmar.agenda.ui.quotes.QuoteViewScreen
import br.com.acalmar.agenda.ui.quotes.QuotesScreen
import br.com.acalmar.agenda.ui.theme.Brand

/** Endereços das telas. */
object Routes {
    const val AGENDA = "agenda"
    const val CLIENTS = "clients"
    const val CASH = "cash"
    const val MORE = "more"

    const val APPT_FORM = "appt?id={id}&date={date}&start={start}&clientId={clientId}"
    const val BLOCK_FORM = "block?id={id}&date={date}&start={start}"
    const val CLIENT_FORM = "clientForm?id={id}"
    const val CLIENT_DETAIL = "client/{id}"
    const val SELL_PACKAGE = "client/{id}/package"
    const val SERVICES = "services"
    const val SERVICE_FORM = "service?id={id}"
    const val HOURS = "hours"
    const val BACKUP = "backup"

    const val CLIENT_ANAMNESIS = "client/{id}/anamnesis"
    const val CLIENT_CONSENT = "client/{id}/consent"
    const val CLIENT_PHOTOS = "client/{id}/photos"
    const val RECALL = "recall"
    const val QUOTES = "quotes?clientId={clientId}"
    const val QUOTE_FORM = "quoteForm?id={id}&clientId={clientId}"
    const val QUOTE_VIEW = "quote/{id}"
    const val FORMS = "forms"
    const val FORMS_ANAMNESIS = "forms/anamnesis"
    const val FORMS_CONSENT = "forms/consent"
    const val BUSINESS = "business"

    fun apptForm(id: Long = 0, date: Long = -1, start: Int = -1, clientId: Long = 0) =
        "appt?id=$id&date=$date&start=$start&clientId=$clientId"

    fun blockForm(id: Long = 0, date: Long = -1, start: Int = -1) = "block?id=$id&date=$date&start=$start"
    fun clientForm(id: Long = 0) = "clientForm?id=$id"
    fun clientDetail(id: Long) = "client/$id"
    fun sellPackage(clientId: Long) = "client/$clientId/package"
    fun serviceForm(id: Long = 0) = "service?id=$id"
    fun clientAnamnesis(id: Long) = "client/$id/anamnesis"
    fun clientConsent(id: Long) = "client/$id/consent"
    fun clientPhotos(id: Long) = "client/$id/photos"
    fun quotes(clientId: Long = 0) = "quotes?clientId=$clientId"
    fun quoteForm(id: Long = 0, clientId: Long = 0) = "quoteForm?id=$id&clientId=$clientId"
    fun quoteView(id: Long) = "quote/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.AGENDA, "Agenda", Icons.Outlined.CalendarMonth),
    Tab(Routes.CLIENTS, "Clientes", Icons.Outlined.Groups),
    Tab(Routes.CASH, "Caixa", Icons.Outlined.AccountBalanceWallet),
    Tab(Routes.MORE, "Mais", Icons.Outlined.Spa)
)

private fun longArg(name: String, default: Long) = navArgument(name) {
    type = NavType.LongType
    defaultValue = default
}

private fun intArg(name: String, default: Int) = navArgument(name) {
    type = NavType.IntType
    defaultValue = default
}

@Composable
fun AcalmarRoot() {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notifier = remember { Notifier(scope, snackbar) }
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = tabs.any { it.route == currentRoute }

    CompositionLocalProvider(LocalNotifier provides notifier) {
        Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (showBar) {
                    NavigationBar(containerColor = Brand.Card, tonalElevation = androidx.compose.ui.unit.Dp(0f)) {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = currentRoute == tab.route,
                                onClick = { nav.switchTab(tab.route) },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Brand.BronzeDark,
                                    selectedTextColor = Brand.BronzeDark,
                                    indicatorColor = Brand.Beige,
                                    unselectedIconColor = Brand.BrownSoft,
                                    unselectedTextColor = Brand.BrownSoft
                                )
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
            ) {
                AcalmarNavHost(nav)
            }
        }
        // Avisos curtos aparecem no topo, para não cobrir os botões de ação.
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = Brand.Brown,
                contentColor = Brand.Cream,
                shape = MaterialTheme.shapes.small
            )
        }
        }
    }
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun AcalmarNavHost(nav: NavHostController) {
    val back: () -> Unit = { nav.popBackStack() }
    NavHost(navController = nav, startDestination = Routes.AGENDA) {
        composable(Routes.AGENDA) {
            AgendaScreen(
                onNewAppointment = { date, start -> nav.navigate(Routes.apptForm(date = date, start = start)) },
                onEditAppointment = { id -> nav.navigate(Routes.apptForm(id = id)) },
                onNewBlock = { date, start -> nav.navigate(Routes.blockForm(date = date, start = start)) },
                onEditBlock = { id -> nav.navigate(Routes.blockForm(id = id)) },
                onOpenClient = { id -> nav.navigate(Routes.clientDetail(id)) },
                onOpenHours = { nav.navigate(Routes.HOURS) }
            )
        }
        composable(Routes.CLIENTS) {
            ClientsScreen(
                onOpenClient = { id -> nav.navigate(Routes.clientDetail(id)) },
                onNewClient = { nav.navigate(Routes.clientForm()) },
                onOpenRecall = { nav.navigate(Routes.RECALL) }
            )
        }
        composable(Routes.CASH) {
            CashScreen(onOpenClient = { id -> nav.navigate(Routes.clientDetail(id)) })
        }
        composable(Routes.MORE) {
            MoreScreen(
                onQuotes = { nav.navigate(Routes.quotes()) },
                onServices = { nav.navigate(Routes.SERVICES) },
                onHours = { nav.navigate(Routes.HOURS) },
                onForms = { nav.navigate(Routes.FORMS) },
                onBusiness = { nav.navigate(Routes.BUSINESS) },
                onBackup = { nav.navigate(Routes.BACKUP) }
            )
        }

        composable(
            Routes.APPT_FORM,
            arguments = listOf(longArg("id", 0), longArg("date", -1), intArg("start", -1), longArg("clientId", 0))
        ) { entry ->
            val args = entry.arguments
            AppointmentFormScreen(
                appointmentId = args?.getLong("id") ?: 0L,
                initialDate = args?.getLong("date") ?: -1L,
                initialStart = args?.getInt("start") ?: -1,
                initialClientId = args?.getLong("clientId") ?: 0L,
                onDone = back
            )
        }
        composable(
            Routes.BLOCK_FORM,
            arguments = listOf(longArg("id", 0), longArg("date", -1), intArg("start", -1))
        ) { entry ->
            val args = entry.arguments
            BlockFormScreen(
                blockId = args?.getLong("id") ?: 0L,
                initialDate = args?.getLong("date") ?: -1L,
                initialStart = args?.getInt("start") ?: -1,
                onDone = back
            )
        }
        composable(Routes.CLIENT_FORM, arguments = listOf(longArg("id", 0))) { entry ->
            ClientFormScreen(clientId = entry.arguments?.getLong("id") ?: 0L, onDone = back)
        }
        composable(Routes.CLIENT_DETAIL, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            ClientDetailScreen(
                clientId = id,
                onBack = back,
                onEdit = { nav.navigate(Routes.clientForm(id)) },
                onSchedule = { nav.navigate(Routes.apptForm(clientId = id)) },
                onSellPackage = { nav.navigate(Routes.sellPackage(id)) },
                onEditAppointment = { apptId -> nav.navigate(Routes.apptForm(id = apptId)) },
                onAnamnesis = { nav.navigate(Routes.clientAnamnesis(id)) },
                onConsent = { nav.navigate(Routes.clientConsent(id)) },
                onPhotos = { nav.navigate(Routes.clientPhotos(id)) },
                onQuotes = { nav.navigate(Routes.quotes(id)) }
            )
        }
        composable(Routes.SELL_PACKAGE, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            SellPackageScreen(clientId = entry.arguments?.getLong("id") ?: 0L, onDone = back)
        }
        composable(Routes.SERVICES) {
            ServicesScreen(onBack = back, onOpen = { id -> nav.navigate(Routes.serviceForm(id)) })
        }
        composable(Routes.SERVICE_FORM, arguments = listOf(longArg("id", 0))) { entry ->
            ServiceFormScreen(serviceId = entry.arguments?.getLong("id") ?: 0L, onDone = back)
        }
        composable(Routes.HOURS) { WorkHoursScreen(onBack = back) }
        composable(Routes.BACKUP) { BackupScreen(onBack = back) }

        // ---- Ficha da cliente
        composable(Routes.CLIENT_ANAMNESIS, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            AnamnesisScreen(clientId = entry.arguments?.getLong("id") ?: 0L, onDone = back)
        }
        composable(Routes.CLIENT_CONSENT, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            ConsentScreen(clientId = entry.arguments?.getLong("id") ?: 0L, onBack = back)
        }
        composable(Routes.CLIENT_PHOTOS, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            PhotosScreen(clientId = entry.arguments?.getLong("id") ?: 0L, onBack = back)
        }
        composable(Routes.RECALL) {
            RecallScreen(
                onBack = back,
                onOpenClient = { id -> nav.navigate(Routes.clientDetail(id)) },
                onSchedule = { id -> nav.navigate(Routes.apptForm(clientId = id)) }
            )
        }

        // ---- Orçamentos
        composable(Routes.QUOTES, arguments = listOf(longArg("clientId", 0))) { entry ->
            val clientId = entry.arguments?.getLong("clientId") ?: 0L
            QuotesScreen(
                clientId = clientId,
                onBack = back,
                onOpen = { id -> nav.navigate(Routes.quoteView(id)) },
                onNew = { nav.navigate(Routes.quoteForm(clientId = clientId)) }
            )
        }
        composable(Routes.QUOTE_FORM, arguments = listOf(longArg("id", 0), longArg("clientId", 0))) { entry ->
            val quoteId = entry.arguments?.getLong("id") ?: 0L
            QuoteFormScreen(
                quoteId = quoteId,
                initialClientId = entry.arguments?.getLong("clientId") ?: 0L,
                onBack = back,
                onSaved = { savedId ->
                    if (quoteId != 0L) {
                        // Editando a partir do orçamento aberto: volta para ele.
                        nav.popBackStack()
                    } else {
                        // Orçamento novo: abre o orçamento pronto no lugar do formulário.
                        nav.navigate(Routes.quoteView(savedId)) {
                            popUpTo(Routes.QUOTE_FORM) { inclusive = true }
                        }
                    }
                }
            )
        }
        composable(Routes.QUOTE_VIEW, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            val id = entry.arguments?.getLong("id") ?: 0L
            QuoteViewScreen(quoteId = id, onBack = back, onEdit = { nav.navigate(Routes.quoteForm(id = id)) })
        }

        // ---- Configurações
        composable(Routes.FORMS) {
            FormsScreen(
                onBack = back,
                onAnamnesis = { nav.navigate(Routes.FORMS_ANAMNESIS) },
                onConsent = { nav.navigate(Routes.FORMS_CONSENT) }
            )
        }
        composable(Routes.FORMS_ANAMNESIS) { AnamnesisTemplateScreen(onBack = back) }
        composable(Routes.FORMS_CONSENT) { ConsentTextScreen(onBack = back) }
        composable(Routes.BUSINESS) { BusinessScreen(onBack = back) }
    }
}
