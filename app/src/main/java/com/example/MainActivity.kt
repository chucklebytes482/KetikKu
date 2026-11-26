package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.AndroidDeviceBackupStorage
import com.example.data.DeviceDictionaryBackupManager
import com.example.data.SettingsRepository
import com.example.ui.ImeActivationStatus
import com.example.ui.MainViewModel
import com.example.ui.screens.DeviceBackupSheetContent
import com.example.ui.screens.DictionaryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.KetikKuTheme
import kotlinx.coroutines.flow.collectLatest

const val ROUTE_SETTINGS = "settings"
const val ROUTE_DICTIONARY = "dictionary"
const val EXTRA_START_ROUTE = "extra_start_route"

class MainActivity : ComponentActivity() {
    private var requestedRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedRoute = resolveRouteFromIntent(intent)
        setContent {
            KetikKuTheme {
                KetikKuAppRoot(
                    requestedRoute = requestedRoute,
                    onConsumeRequestedRoute = { requestedRoute = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedRoute = resolveRouteFromIntent(intent)
    }

    private fun resolveRouteFromIntent(intent: Intent?): String? {
        val raw = intent?.getStringExtra(EXTRA_START_ROUTE) ?: return null
        return when (raw) {
            "playground" -> ROUTE_SETTINGS // Compatibility with old shortcuts/intents.
            ROUTE_SETTINGS, ROUTE_DICTIONARY -> raw
            else -> null
        }
    }
}

@Composable
fun KetikKuAppRoot(
    requestedRoute: String? = null,
    onConsumeRequestedRoute: () -> Unit = {}
) {
    KetikKuMainScaffold(
        requestedRoute = requestedRoute,
        onConsumeRequestedRoute = onConsumeRequestedRoute
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KetikKuMainScaffold(
    requestedRoute: String? = null,
    onConsumeRequestedRoute: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mainViewModel: MainViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val repo = SettingsRepository(app)
                val backupManager = DeviceDictionaryBackupManager(
                    settingsRepository = repo,
                    storage = AndroidDeviceBackupStorage(app)
                )
                MainViewModel(
                    settingsRepository = repo,
                    deviceBackupManager = backupManager
                )
            }
        }
    )

    val settings by mainViewModel.settings.collectAsStateWithLifecycle()
    val dictionary by mainViewModel.dictionary.collectAsStateWithLifecycle()
    val folders by mainViewModel.folders.collectAsStateWithLifecycle()
    val syncLocalState by mainViewModel.syncLocalState.collectAsStateWithLifecycle()
    val deviceSyncUiState by mainViewModel.deviceSyncUiState.collectAsStateWithLifecycle()
    val imeStatus by mainViewModel.imeStatus.collectAsStateWithLifecycle()

    var showDeviceBackupSheet by remember { mutableStateOf(false) }
    val backupSnackbar = remember { SnackbarHostState() }
    LaunchedEffect(mainViewModel) {
        mainViewModel.uiMessages.collectLatest { backupSnackbar.showSnackbar(it) }
    }

    DisposableEffect(lifecycleOwner) {
        mainViewModel.refreshImeStatus(context)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                mainViewModel.refreshImeStatus(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_SETTINGS

    DisposableEffect(navController) {
        mainViewModel.onNavigateToSettings = {
            navController.popBackStack(ROUTE_SETTINGS, inclusive = false)
        }
        onDispose {
            mainViewModel.onNavigateToSettings = {}
        }
    }

    LaunchedEffect(requestedRoute) {
        val target = requestedRoute ?: return@LaunchedEffect
        if (target != ROUTE_SETTINGS) {
            navController.navigate(target) {
                launchSingleTop = true
            }
        } else {
            navController.popBackStack(ROUTE_SETTINGS, inclusive = false)
        }
        onConsumeRequestedRoute()
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { if (!showDeviceBackupSheet) SnackbarHost(backupSnackbar) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = when (currentRoute) {
                            ROUTE_DICTIONARY -> "Kamus Pribadi"
                            else -> "Setelan KetikKu"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (currentRoute != ROUTE_SETTINGS) {
                        IconButton(
                            onClick = { navController.popBackStack() },
                            modifier = Modifier.testTag("back_to_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Kembali ke Setelan"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            KetikKuNavHost(
                navController = navController,
                mainViewModel = mainViewModel,
                onOpenDeviceBackupSheet = { showDeviceBackupSheet = true },
                settings = settings,
                dictionary = dictionary,
                folders = folders,
                imeStatus = imeStatus
            )
        }
    }

    if (showDeviceBackupSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showDeviceBackupSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            DeviceBackupSheetContent(
                folderUri = syncLocalState.deviceBackupTreeUri,
                state = deviceSyncUiState,
                snackbarState = backupSnackbar,
                onFolderAuthorized = mainViewModel::onDeviceBackupFolderGranted,
                onUnlock = mainViewModel::unlockDeviceBackup,
                onSync = { mainViewModel.syncDictionaryWithDevice(notify = true) },
                onLock = mainViewModel::lockDeviceBackup,
                onMessage = mainViewModel::showBackupMessage
            )
        }
    }
}

@Composable
private fun KetikKuNavHost(
    navController: NavHostController,
    mainViewModel: MainViewModel,
    onOpenDeviceBackupSheet: () -> Unit,
    settings: com.example.model.KeyboardSettings,
    dictionary: List<com.example.model.PersonalDictionaryEntry>,
    folders: List<String>,
    imeStatus: ImeActivationStatus
) {
    NavHost(
        navController = navController,
        startDestination = ROUTE_SETTINGS
    ) {
        composable(ROUTE_SETTINGS) {
            SettingsScreen(
                viewModel = mainViewModel,
                settings = settings,
                dictionary = dictionary,
                imeStatus = imeStatus,
                onOpenDictionary = {
                    navController.navigate(ROUTE_DICTIONARY) {
                        launchSingleTop = true
                    }
                },
                onOpenDeviceBackupSheet = onOpenDeviceBackupSheet
            )
        }

        composable(ROUTE_DICTIONARY) {
            DictionaryScreen(
                settings = settings,
                dictionary = dictionary,
                folders = folders,
                onUpsertEntry = { word, shortcut, language, existingId, folder, secretCipher, email, website ->
                    mainViewModel.upsertDictionaryEntry(
                        word = word,
                        shortcut = shortcut,
                        language = language,
                        existingId = existingId,
                        folder = folder,
                        secretCipher = secretCipher,
                        email = email,
                        website = website
                    )
                },
                onRestoreEntries = { entryIds ->
                    mainViewModel.restoreDictionaryEntries(entryIds)
                },
                onPermanentDeleteEntries = { entryIds ->
                    mainViewModel.permanentlyDeleteDictionaryEntries(entryIds)
                },
                onRegenerateShortcuts = {
                    mainViewModel.regenerateDictionaryShortcuts()
                },
                onCreateFolder = { path ->
                    mainViewModel.createDictionaryFolder(path)
                },
                onRenameFolder = { oldPath, newName ->
                    mainViewModel.renameDictionaryFolder(oldPath, newName)
                },
                onDeleteItems = { entryIds, folderPaths ->
                    mainViewModel.deleteDictionaryItems(entryIds, folderPaths)
                }
            )
        }
    }
}
