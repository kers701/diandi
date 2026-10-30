package com.diandi.bookkeeping

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.diandi.bookkeeping.service.KeepAliveService
import com.diandi.bookkeeping.util.AppPrefs
import com.diandi.bookkeeping.ui.navigation.Screen
import com.diandi.bookkeeping.ui.screens.*
import com.diandi.bookkeeping.ui.theme.DiandiTheme
import com.diandi.bookkeeping.viewmodel.MainViewModel
import com.diandi.bookkeeping.viewmodel.MainViewModelFactory
import java.util.concurrent.atomic.AtomicReference

class MainActivity : ComponentActivity() {

    private val pendingAddType = AtomicReference<String?>(null)
    private var navControllerRef: NavHostController? = null

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { KeepAliveService.start(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        requestNotifAndStartService()
        val app = application as DiandiApp
        setContent {
            DiandiTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val nav = rememberNavController()
                    LaunchedEffect(nav) { navControllerRef = nav }
                    var tick by remember { mutableIntStateOf(0) }
                    // 监听 pending 变化：用 tick 驱动重组
                    LaunchedEffect(Unit) {
                        // no-op holder
                    }
                    DiandiNav(
                        app = app,
                        navController = nav,
                        pendingAddTypeProvider = { pendingAddType.get() },
                        onPendingConsumed = { pendingAddType.set(null) },
                        intentTick = tick
                    )
                    // when activity resumes from notification, force re-read
                    DisposableEffect(Unit) {
                        onDispose { }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
        // recreate content path by navigating
        when (intent.action) {
            KeepAliveService.ACTION_ADD_EXPENSE,
            KeepAliveService.ACTION_ADD_INCOME -> {
                navControllerRef?.navigate(Screen.Add.route) {
                    launchSingleTop = true
                }
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            KeepAliveService.ACTION_ADD_EXPENSE -> pendingAddType.set("EXPENSE")
            KeepAliveService.ACTION_ADD_INCOME -> pendingAddType.set("INCOME")
        }
    }

    private fun requestNotifAndStartService() {
        if (!AppPrefs.isNotifyEnabled(this)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                KeepAliveService.start(this)
            } else {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            KeepAliveService.start(this)
        }
    }
}

@Composable
fun DiandiNav(
    app: DiandiApp,
    navController: NavHostController,
    pendingAddTypeProvider: () -> String?,
    onPendingConsumed: () -> Unit,
    intentTick: Int = 0
) {
    val viewModel: MainViewModel = viewModel(
        factory = MainViewModelFactory(app.repository)
    )
    val pending = pendingAddTypeProvider()

    LaunchedEffect(pending, intentTick) {
        if (pending != null) {
            navController.navigate(Screen.Add.route) { launchSingleTop = true }
        }
    }

    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = viewModel,
                onAddClick = { navController.navigate(Screen.Add.route) },
                onChartsClick = { navController.navigate(Screen.Charts.route) },
                onGoalsClick = { navController.navigate(Screen.Goals.route) },
                onCorrectionClick = { navController.navigate(Screen.Correction.route) },
                onAboutClick = { navController.navigate(Screen.About.route) }
            )
        }
        composable(Screen.Add.route) {
            AddScreen(
                viewModel = viewModel,
                initialType = pendingAddTypeProvider(),
                onBack = {
                    onPendingConsumed()
                    navController.popBackStack()
                }
            )
        }
        composable(Screen.Charts.route) {
            ChartsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable(Screen.Goals.route) {
            GoalsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable(Screen.Correction.route) {
            CorrectionScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }
        composable(Screen.About.route) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
    }
}
