package com.lightledger.app.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lightledger.app.ui.app.AppViewModel
import com.lightledger.app.ui.home.HomeScreen
import com.lightledger.app.ui.record.RecordScreen
import com.lightledger.app.ui.stats.StatsScreen
import com.lightledger.app.ui.settings.SettingsScreen
import com.lightledger.app.ui.books.BooksScreen
import com.lightledger.app.ui.detail.BillDetailScreen
import com.lightledger.app.ui.members.MembersScreen

/** 路由常量 */
object Routes {
    const val HOME = "home"
    const val RECORD = "record"
    const val STATS = "stats"
    const val ME = "me"
    const val BOOKS = "books"
    const val DETAIL = "detail/{txId}"
    const val EDIT = "edit/{txId}"
    const val MEMBERS = "members/{bookId}"

    fun detail(txId: Long) = "detail/$txId"

    fun edit(txId: Long) = "edit/$txId"

    fun members(bookId: Long) = "members/$bookId"

    val tabs = listOf(HOME, RECORD, STATS, ME)
}

private data class TabItem(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
)

private val bottomTabs = listOf(
    TabItem(Routes.HOME, com.lightledger.app.R.string.nav_home, Icons.Outlined.Home),
    TabItem(Routes.RECORD, com.lightledger.app.R.string.nav_record, Icons.Outlined.EditNote),
    TabItem(Routes.STATS, com.lightledger.app.R.string.nav_stats, Icons.Outlined.PieChart),
    TabItem(Routes.ME, com.lightledger.app.R.string.nav_me, Icons.Outlined.Person),
)

/** 切换底部 Tab：保留各页状态 */
private fun NavHostController.selectTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun LightLedgerRoot(appViewModel: AppViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in Routes.tabs

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    bottomTabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.selectTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = androidx.compose.ui.res.stringResource(tab.labelRes)) },
                            label = { Text(androidx.compose.ui.res.stringResource(tab.labelRes), style = MaterialTheme.typography.labelMedium) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
            // 关闭默认的淡入淡出页面过渡：两页同时渲染会掉帧（切页卡顿），
            // 改为瞬时切换，体感更快更稳
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    appViewModel = appViewModel,
                    onOpenBooks = { navController.navigate(Routes.BOOKS) },
                    onOpenDetail = { navController.navigate(Routes.detail(it)) },
                    onGoRecord = { navController.selectTab(Routes.RECORD) },
                    onOpenMembers = { navController.navigate(Routes.members(it)) },
                )
            }
            composable(Routes.RECORD) {
                RecordScreen(appViewModel = appViewModel)
            }
            composable(
                route = Routes.EDIT,
                arguments = listOf(navArgument("txId") { type = NavType.LongType }),
            ) { entry ->
                val txId = entry.arguments?.getLong("txId") ?: -1L
                RecordScreen(
                    appViewModel = appViewModel,
                    editTxId = txId,
                    onDone = { navController.popBackStack() },
                )
            }
            composable(Routes.STATS) {
                StatsScreen(
                    appViewModel = appViewModel,
                    onOpenDetail = { navController.navigate(Routes.detail(it)) },
                )
            }
            composable(Routes.ME) {
                SettingsScreen(
                    appViewModel = appViewModel,
                    onOpenBooks = { navController.navigate(Routes.BOOKS) },
                )
            }
            composable(Routes.BOOKS) {
                BooksScreen(
                    appViewModel = appViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenMembers = { navController.navigate(Routes.members(it)) },
                )
            }
            composable(
                route = Routes.MEMBERS,
                arguments = listOf(navArgument("bookId") { type = NavType.LongType }),
            ) { entry ->
                val bookId = entry.arguments?.getLong("bookId") ?: -1L
                MembersScreen(
                    bookId = bookId,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.DETAIL,
                arguments = listOf(navArgument("txId") { type = NavType.LongType }),
            ) { entry ->
                val txId = entry.arguments?.getLong("txId") ?: -1L
                BillDetailScreen(
                    txId = txId,
                    appViewModel = appViewModel,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.edit(txId)) },
                )
            }
        }
    }
}
