package com.diandi.bookkeeping.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Add : Screen("add")
    data object Edit : Screen("edit/{id}") {
        fun create(id: Long) = "edit/$id"
    }
    data object Charts : Screen("charts")
    data object Goals : Screen("goals")
    data object Budget : Screen("budget")
    data object AiAnalysis : Screen("ai_analysis")
    data object AiProviders : Screen("ai_providers")
    data object AiModelGroup : Screen("ai_model_group")
    data object Correction : Screen("correction")
    data object About : Screen("about")
    data object CategoryEdit : Screen("category_edit")
}
