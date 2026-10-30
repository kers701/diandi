package com.diandi.bookkeeping.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Add : Screen("add")
    data object Charts : Screen("charts")
    data object Goals : Screen("goals")
    data object Correction : Screen("correction")
    data object About : Screen("about")
}
