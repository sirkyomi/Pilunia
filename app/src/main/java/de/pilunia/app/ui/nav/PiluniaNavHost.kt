package de.pilunia.app.ui.nav

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import de.pilunia.app.ui.components.MainTab
import de.pilunia.app.ui.main.MainScreen
import de.pilunia.app.ui.meds.MedDetailScreen
import de.pilunia.app.ui.meds.MedWizardScreen
import de.pilunia.app.ui.meds.StockLowScreen
import de.pilunia.app.ui.more.NotificationsScreen
import de.pilunia.app.ui.more.SettingsPageScreen
import de.pilunia.app.ui.onboarding.OnboardingScreen
import de.pilunia.app.ui.onboarding.WelcomeScreen
import de.pilunia.app.ui.reminder.ConfirmedFlow
import de.pilunia.app.ui.reminder.ReminderFlow
import java.time.LocalDate

@Composable
fun PiluniaNavHost(nav: NavHostController, startDestination: String) {
    NavHost(
        navController = nav,
        startDestination = startDestination,
        enterTransition = { slideInHorizontally(tween(260)) { it / 4 } + fadeIn(tween(260)) },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(220)) },
        popExitTransition = { slideOutHorizontally(tween(240)) { it / 4 } + fadeOut(tween(240)) },
    ) {
        composable(Routes.WELCOME) { WelcomeScreen(nav) }
        composable(Routes.ONBOARDING) { OnboardingScreen(nav) }
        composable(
            Routes.MAIN,
            arguments = listOf(navArgument("tab") { type = NavType.StringType; defaultValue = MainTab.HEUTE.name }),
            enterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(200)) },
        ) { entry ->
            val tab = runCatching { MainTab.valueOf(entry.arguments?.getString("tab") ?: "") }.getOrDefault(MainTab.HEUTE)
            MainScreen(nav, tab)
        }
        composable(Routes.MED_DETAIL, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
            MedDetailScreen(nav, it.arguments!!.getLong("id"))
        }
        composable(Routes.STOCK_LOW, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
            StockLowScreen(nav, it.arguments!!.getLong("id"))
        }
        composable(
            Routes.WIZARD,
            arguments = listOf(
                navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                navArgument("step") { type = NavType.IntType; defaultValue = 0 },
            ),
        ) {
            MedWizardScreen(nav, it.arguments!!.getLong("id"), it.arguments!!.getInt("step"))
        }
        composable(
            Routes.REMINDER,
            arguments = listOf(navArgument("day") { type = NavType.LongType }, navArgument("minute") { type = NavType.IntType }),
        ) {
            ReminderFlow(
                day = LocalDate.ofEpochDay(it.arguments!!.getLong("day")),
                minute = it.arguments!!.getInt("minute"),
                onFinish = { nav.backOrMain() },
            )
        }
        composable(
            Routes.CONFIRMED,
            arguments = listOf(navArgument("day") { type = NavType.LongType }, navArgument("minute") { type = NavType.IntType }),
        ) {
            ConfirmedFlow(
                day = LocalDate.ofEpochDay(it.arguments!!.getLong("day")),
                minute = it.arguments!!.getInt("minute"),
                onFinish = { nav.backOrMain() },
            )
        }
        composable(Routes.NOTIFICATIONS) { NotificationsScreen(nav) }
        composable(Routes.SETTINGS, arguments = listOf(navArgument("page") { type = NavType.StringType })) {
            SettingsPageScreen(nav, SettingsPage.valueOf(it.arguments!!.getString("page")!!))
        }
    }
}
