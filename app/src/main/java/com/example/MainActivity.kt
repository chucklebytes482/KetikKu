package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.FirestoreSyncRepository
import com.example.data.GoogleDriveClient
import com.example.data.SettingsRepository
import com.example.ui.AuthHelper
import com.example.ui.CloudSyncViewModel
import com.example.ui.MainViewModel
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DriveBackupScreen
import com.example.ui.screens.DriveBackupUnauthenticatedScreen
import com.example.ui.screens.PlaygroundScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.KetikKuTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

const val ROUTE_PLAYGROUND = "playground"
const val ROUTE_SETTINGS = "settings"
const val ROUTE_BACKUP = "backup"

private data class NavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val navTabs = listOf(
    NavTab(
        route = ROUTE_PLAYGROUND,
        label = "Uji Ketik",
        selectedIcon = Icons.Filled.Keyboard,
        unselectedIcon = Icons.Outlined.Keyboard,
        testTag = "nav_tab_playground"
    ),
    NavTab(
        route = ROUTE_SETTINGS,
        label = "Setelan",
        selectedIcon = Icons.Filled.Tune,
        unselectedIcon = Icons.Outlined.Tune,
        testTag = "nav_tab_settings"
    ),
    NavTab(
        route = ROUTE_BACKUP,
        label = "Cadangan",
        selectedIcon = Icons.Filled.CloudUpload,
        unselectedIcon = Icons.Outlined.CloudUpload,
        testTag = "nav_tab_backup"
    )
)

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KetikKuTheme(darkTheme = true) {
                KetikKuAppRoot()
            }
        }
    }
}

@Composable
fun KetikKuAppRoot(auth: FirebaseAuth = Firebase.auth) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)
    var allowLocalOnlyMode by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (auth.currentUser == null) {
            AuthHelper.attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {},
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    if (currentUser == null && !allowLocalOnlyMode) {
        AuthScreen(
            onAuthSuccess = {},
            onContinueLocalOnly = { allowLocalOnlyMode = true }
        )
    } else {
        KetikKuMainScaffold(
            currentUser = currentUser,
            onEnterLocalOnly = { allowLocalOnlyMode = true },
            onSignedOut = { allowLocalOnlyMode = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KetikKuMainScaffold(
    currentUser: FirebaseUser?,
    onEnterLocalOnly: () -> Unit,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
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
    val clipboard by mainViewModel.clipboard.collectAsStateWithLifecycle()
    val syncLocalState by mainViewModel.syncLocalState.collectAsStateWithLifecycle()
    val searchQuery by mainViewModel.searchQuery.collectAsStateWithLifecycle()
    val imeStatus by mainViewModel.imeStatus.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        mainViewModel.refreshImeStatus(context)
        onDispose { }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_PLAYGROUND

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 700.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = when (currentRoute) {
                                ROUTE_SETTINGS -> "Setelan Keyboard"
                                ROUTE_BACKUP -> "Cadangan Drive"
                                else -> "KetikKu"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.navigationBarsPadding()
                    ) {
                        navTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        navTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_PLAYGROUND
                    ) {
                        composable(ROUTE_PLAYGROUND) {
                            PlaygroundScreen(
                                viewModel = mainViewModel,
                                settings = settings,
                                dictionary = dictionary,
                                clipboardItems = clipboard,
                                imeStatus = imeStatus
                            )
                        }

                        composable(ROUTE_SETTINGS) {
                            SettingsScreen(
                                viewModel = mainViewModel,
                                settings = settings,
                                dictionary = dictionary,
                                searchQuery = searchQuery
                            )
                        }

                        composable(ROUTE_BACKUP) {
                            if (currentUser == null) {
                                DriveBackupUnauthenticatedScreen(
                                    onAuthSuccess = onEnterLocalOnly
                                )
                            } else {
                                val uid = currentUser.uid
                                key(uid) {
                                    val cloudViewModel: CloudSyncViewModel = viewModel(
                                        key = uid,
                                        factory = viewModelFactory {
                                            initializer {
                                                val app = checkNotNull(this[APPLICATION_KEY])
                                                CloudSyncViewModel(
                                                    currentUserId = uid,
                                                    userEmail = currentUser.email.orEmpty(),
                                                    settingsRepository = SettingsRepository(app),
                                                    firestoreRepo = FirestoreSyncRepository(app),
                                                    driveClient = GoogleDriveClient(),
                                                    appContext = app
                                                )
                                            }
                                        }
                                    )
                                    DriveBackupScreen(
                                        userEmail = currentUser.email.orEmpty(),
                                        mainViewModel = mainViewModel,
                                        cloudViewModel = cloudViewModel,
                                        settings = settings,
                                        dictionary = dictionary,
                                        syncLocalState = syncLocalState,
                                        onSignedOut = onSignedOut
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
