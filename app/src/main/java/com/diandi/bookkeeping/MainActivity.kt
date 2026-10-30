package com.diandi.bookkeeping

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.diandi.bookkeeping.ui.navigation.Screen
import com.diandi.bookkeeping.ui.screens.*
import com.diandi.bookkeeping.ui.theme.DiandiTheme
import com.diandi.bookkeeping.viewmodel.MainViewModel
import com.diandi.bookkeeping.viewmodel.MainViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as DiandiApp
        setContent {
            DiandiTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DiandiNav(app)
                }
            }
        }
    }
}

@Composable
fun DiandiNav(app: DiandiApp) {
    val navController = rememberNavController()
    val viewModel: MainViewModel = viewModel(
        factory = MainViewModelFactory(app.repository)
    )

    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = viewModel,
                onAddClick = { navController.navigate(Screen.Add.route) },
                onChartsClick = { navController.navigate(Screen.Charts.route) },
                onGoalsClick = { navController.navigate(Screen.Goals.route) },
                onCorrectionClick = { navController.navigate(Screen.Correction.route) }
            )
        }
        composable(Screen.Add.route) {
            AddScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
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
    }
}
