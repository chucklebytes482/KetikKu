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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.example.data.FirestoreSyncRepository
import com.example.data.GoogleDriveClient
import com.example.data.SettingsRepository
import com.example.ui.CloudSyncViewModel
import com.example.ui.MainViewModel
import com.example.ui.screens.DictionaryScreen
import com.example.ui.screens.DriveBackupSheetContent
import com.example.ui.screens.DriveBackupUnauthenticatedSheetContent
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.KetikKuTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth

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
                MainViewModel(SettingsRepository(app))
            }
        }
    )

    val settings by mainViewModel.settings.collectAsStateWithLifecycle()
    val dictionary by mainViewModel.dictionary.collectAsStateWithLifecycle()
    val folders by mainViewModel.folders.collectAsStateWithLifecycle()
    val clipboard by mainViewModel.clipboard.collectAsStateWithLifecycle()
    val dismissedClipboardId by mainViewModel.dismissedClipboardId.collectAsStateWithLifecycle()
    val syncLocalState by mainViewModel.syncLocalState.collectAsStateWithLifecycle()
    val imeStatus by mainViewModel.imeStatus.collectAsStateWithLifecycle()

    var currentUser by remember {
        mutableStateOf<FirebaseUser?>(runCatching { Firebase.auth.currentUser }.getOrNull())
    }
    var showDriveSyncSheet by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        mainViewModel.refreshImeStatus(context)
        mainViewModel.syncSystemClipboardFromDevice(context)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                mainViewModel.refreshImeStatus(context)
                mainViewModel.syncSystemClipboardFromDevice(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(Unit) {
        val auth = runCatching { Firebase.auth }.getOrNull()
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        auth?.addAuthStateListener(listener)
        onDispose {
            auth?.removeAuthStateListener(listener)
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
                currentUser = currentUser,
                onOpenDriveSyncSheet = { showDriveSyncSheet = true },
                settings = settings,
                dictionary = dictionary,
                folders = folders,
                clipboard = clipboard,
                dismissedClipboardId = dismissedClipboardId,
                syncLocalState = syncLocalState,
                imeStatus = imeStatus
            )
        }
    }

    if (showDriveSyncSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showDriveSyncSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            val signedInUser = currentUser
            if (signedInUser == null) {
                DriveBackupUnauthenticatedSheetContent(
                    settings = settings,
                    dictionary = dictionary,
                    onAuthSuccess = {
                        currentUser = runCatching { Firebase.auth.currentUser }.getOrNull()
                    }
                )
            } else {
                val uid = signedInUser.uid
                key(uid) {
                    val cloudViewModel: CloudSyncViewModel = viewModel(
                        key = uid,
                        factory = viewModelFactory {
                            initializer {
                                val app = checkNotNull(this[APPLICATION_KEY])
                                CloudSyncViewModel(
                                    currentUserId = uid,
                                    userEmail = signedInUser.email.orEmpty(),
                                    settingsRepository = SettingsRepository(app),
                                    firestoreRepo = FirestoreSyncRepository(app),
                                    driveClient = GoogleDriveClient(),
                                    appContext = app
                                )
                            }
                        }
                    )
                    DriveBackupSheetContent(
                        userEmail = signedInUser.email.orEmpty(),
                        mainViewModel = mainViewModel,
                        cloudViewModel = cloudViewModel,
                        settings = settings,
                        dictionary = dictionary,
                        syncLocalState = syncLocalState,
                        onSignedOut = {
                            currentUser = runCatching { Firebase.auth.currentUser }.getOrNull()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun KetikKuNavHost(
    navController: NavHostController,
    mainViewModel: MainViewModel,
    currentUser: FirebaseUser?,
    onOpenDriveSyncSheet: () -> Unit,
    settings: com.example.model.KeyboardSettings,
    dictionary: List<com.example.model.PersonalDictionaryEntry>,
    folders: List<String>,
    clipboard: List<com.example.model.ClipboardItem>,
    dismissedClipboardId: String?,
    syncLocalState: com.example.model.SyncLocalState,
    imeStatus: com.example.ui.ImeActivationStatus
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
                onOpenDriveSyncSheet = onOpenDriveSyncSheet,
                connectedUserEmail = currentUser?.email,
                syncLocalState = syncLocalState
            )
        }

        composable(ROUTE_DICTIONARY) {
            DictionaryScreen(
                settings = settings,
                dictionary = dictionary,
                folders = folders,
                onUpsertEntry = { word, shortcut, language, existingId, folder ->
                    mainViewModel.upsertDictionaryEntry(
                        word = word,
                        shortcut = shortcut,
                        language = language,
                        existingId = existingId,
                        folder = folder
                    )
                },
                onDeleteEntry = { entryId ->
                    mainViewModel.deleteDictionaryEntry(entryId)
                },
                onCreateFolder = { path ->
                    mainViewModel.createDictionaryFolder(path)
                },
                onRenameFolder = { oldPath, newName ->
                    mainViewModel.renameDictionaryFolder(oldPath, newName)
                },
                onMoveItems = { entryIds, folderPaths, targetFolder ->
                    mainViewModel.moveDictionaryItems(entryIds, folderPaths, targetFolder)
                },
                onDeleteItems = { entryIds, folderPaths ->
                    mainViewModel.deleteDictionaryItems(entryIds, folderPaths)
                }
            )
        }
    }
}
