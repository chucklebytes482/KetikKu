package com.example

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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.SettingsRepository
import com.example.ui.MainViewModel
import com.example.ui.screens.DictionaryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.KetikKuTheme

const val ROUTE_SETTINGS = "settings"
const val ROUTE_DICTIONARY = "dictionary"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KetikKuTheme {
                KetikKuAppRoot()
            }
        }
    }
}

@Composable
fun KetikKuAppRoot() {
    KetikKuMainScaffold()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KetikKuMainScaffold() {
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
    val searchQuery by mainViewModel.searchQuery.collectAsStateWithLifecycle()
    val imeStatus by mainViewModel.imeStatus.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        mainViewModel.refreshImeStatus(context)
        onDispose { }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_SETTINGS

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
                    if (currentRoute == ROUTE_DICTIONARY) {
                        IconButton(
                            onClick = { navController.popBackStack() },
                            modifier = Modifier.testTag("back_from_dictionary_button")
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
            NavHost(
                navController = navController,
                startDestination = ROUTE_SETTINGS
            ) {
                composable(ROUTE_SETTINGS) {
                    SettingsScreen(
                        viewModel = mainViewModel,
                        settings = settings,
                        dictionary = dictionary,
                        searchQuery = searchQuery,
                        imeStatus = imeStatus,
                        onOpenDictionary = {
                            navController.navigate(ROUTE_DICTIONARY) {
                                launchSingleTop = true
                            }
                        }
                    )
                }

                composable(ROUTE_DICTIONARY) {
                    DictionaryScreen(
                        settings = settings,
                        dictionary = dictionary,
                        onUpsertEntry = { word, shortcut, language, existingId ->
                            mainViewModel.upsertDictionaryEntry(
                                word = word,
                                shortcut = shortcut,
                                language = language,
                                existingId = existingId
                            )
                        },
                        onDeleteEntry = { entryId ->
                            mainViewModel.deleteDictionaryEntry(entryId)
                        }
                    )
                }
            }
        }
    }
}
