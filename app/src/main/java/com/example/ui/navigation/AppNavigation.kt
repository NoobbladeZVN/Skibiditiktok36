package com.example.ui.navigation

import android.app.Application
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.repository.StudyRepository
import com.example.ui.screens.HistoryDetailScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.SolveScreen
import com.example.ui.viewmodel.HistoryDetailViewModel
import com.example.ui.viewmodel.HistoryViewModel
import com.example.ui.viewmodel.SolveViewModel
import kotlinx.coroutines.launch

const val ROUTE_SOLVE = "solve"
const val ROUTE_HISTORY = "history"
const val ROUTE_HISTORY_DETAIL = "history_detail/{sessionId}"

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val repository = remember(application) {
        StudyRepository(AppDatabase.getInstance(application).studyDao())
    }

    val solveViewModel: SolveViewModel = viewModel(
        factory = SolveViewModel.Factory(application, repository)
    )
    val historyViewModel: HistoryViewModel = viewModel(
        factory = HistoryViewModel.Factory(repository)
    )

    val historyCount by historyViewModel.totalCount.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val isTopLevelRoute = currentDestination?.hierarchy?.any {
        it.route == ROUTE_SOLVE || it.route == ROUTE_HISTORY
    } ?: true

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets.statusBars,
        bottomBar = {
            if (isTopLevelRoute) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    windowInsets = WindowInsets.navigationBars
                ) {
                    val solveSelected = currentDestination?.hierarchy?.any { it.route == ROUTE_SOLVE } == true
                    NavigationBarItem(
                        selected = solveSelected,
                        onClick = {
                            navController.navigate(ROUTE_SOLVE) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = if (solveSelected) {
                                    Icons.Rounded.AutoAwesome
                                } else {
                                    Icons.Outlined.AutoAwesome
                                },
                                contentDescription = stringResource(R.string.nav_solve)
                            )
                        },
                        label = { Text(stringResource(R.string.nav_solve)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("nav_tab_solve")
                    )

                    val historySelected = currentDestination?.hierarchy?.any { it.route == ROUTE_HISTORY } == true
                    NavigationBarItem(
                        selected = historySelected,
                        onClick = {
                            navController.navigate(ROUTE_HISTORY) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            BadgedBox(
                                badge = {
                                    if (historyCount > 0) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ) {
                                            Text(if (historyCount > 99) "99+" else historyCount.toString())
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (historySelected) {
                                        Icons.Rounded.History
                                    } else {
                                        Icons.Outlined.History
                                    },
                                    contentDescription = stringResource(R.string.nav_history)
                                )
                            }
                        },
                        label = { Text(stringResource(R.string.nav_history)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("nav_tab_history")
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_SOLVE,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(ROUTE_SOLVE) {
                SolveScreen(
                    viewModel = solveViewModel,
                    onShowMessage = { msg ->
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar(msg)
                        }
                    }
                )
            }
            composable(ROUTE_HISTORY) {
                HistoryScreen(
                    viewModel = historyViewModel,
                    onOpenSession = { sessionId ->
                        navController.navigate("history_detail/$sessionId")
                    }
                )
            }
            composable(
                route = ROUTE_HISTORY_DETAIL,
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
            ) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: return@composable
                val detailViewModel: HistoryDetailViewModel = viewModel(
                    key = "detail_$sessionId",
                    factory = HistoryDetailViewModel.Factory(sessionId, repository)
                )
                HistoryDetailScreen(
                    viewModel = detailViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
