package com.lightledger.app.ui.navigation

import android.content.res.Configuration
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lightledger.app.ui.app.AppViewModel
import com.lightledger.app.ui.calendar.CalendarScreen
import com.lightledger.app.ui.home.HomeScreen
import com.lightledger.app.ui.record.RecordScreen
import com.lightledger.app.ui.stats.StatsScreen
import com.lightledger.app.ui.settings.SettingsScreen
import com.lightledger.app.ui.books.BooksScreen
import com.lightledger.app.ui.detail.BillDetailScreen
import com.lightledger.app.ui.members.MembersScreen
import com.lightledger.app.ui.search.SearchScreen

/** 路由常量 */
object Routes {
    const val HOME = "home"
    const val CALENDAR = "calendar"
    const val RECORD = "record"
    const val STATS = "stats"
    const val ME = "me"
    const val BOOKS = "books"
    const val SEARCH = "search"
    const val DETAIL = "detail/{txId}"
    const val EDIT = "edit/{txId}"
    const val MEMBERS = "members/{bookId}"

    fun detail(txId: Long) = "detail/$txId"

    fun edit(txId: Long) = "edit/$txId"

    fun members(bookId: Long) = "members/$bookId"

    val tabs = listOf(HOME, CALENDAR, RECORD, STATS, ME)
}

private data class TabItem(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
)

private val bottomTabs = listOf(
    TabItem(Routes.HOME, com.lightledger.app.R.string.nav_home, Icons.Outlined.Home),
    TabItem(Routes.CALENDAR, com.lightledger.app.R.string.nav_calendar, Icons.Outlined.CalendarMonth),
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

    // ---------- 快捷入口（长按桌面图标）：跳到对应页面后清空待处理路由 ----------
    val pendingShortcutRoute by appViewModel.pendingShortcutRoute.collectAsStateWithLifecycle()
    LaunchedEffect(pendingShortcutRoute) {
        val route = pendingShortcutRoute ?: return@LaunchedEffect
        when (route) {
            Routes.RECORD, Routes.CALENDAR, Routes.STATS -> navController.selectTab(route)
            Routes.SEARCH -> navController.navigate(Routes.SEARCH)
            else -> return@LaunchedEffect
        }
        appViewModel.clearShortcutRoute()
    }

    // ---------- 横屏 / 平板：底部导航栏换到左侧 ----------
    // 横屏可用高度通常只有 360~410dp，底部栏要吃掉 ~80dp，
    // 换成侧边导航栏（NavigationRail）后这部分纵向空间全部还给内容区。
    val configuration = LocalConfiguration.current
    val useSideRail = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
        configuration.screenWidthDp >= 600
    // 侧栏显示时，左/起点方向的系统栏与挖孔 inset 由 NavigationRail 自己吃掉，
    // 内容区只需处理上、下、右（三键导航在横屏下系统栏位于右侧）
    val contentInsets = if (useSideRail && showBottomBar) {
        WindowInsets.systemBars.only(
            WindowInsetsSides.Top + WindowInsetsSides.Bottom + WindowInsetsSides.End,
        )
    } else {
        // 二级页（详情/搜索等）横屏下没有侧栏遮挡左缘：挖孔/刘海安全区要自己让开
        WindowInsets.displayCutout.union(WindowInsets.systemBars)
    }

    if (useSideRail) {
        // 横屏不经过 Scaffold，这里用 Surface 一次性承担两件事：
        // 1) 铺主题背景色。窗口背景是 XML 主题的浅米色，不铺色的话状态栏/导航栏
        //    inset 区与页面未铺色的间隙会整片透出浅底，深色模式下表现为
        //    「卡片深、背景白」的花屏感。
        // 2) 提供 LocalContentColor。MaterialTheme 自身不提供该值（只有
        //    Surface/Card/Scaffold 会提供），缺失时 Compose 回退到纯黑，
        //    导致横屏下所有未显式指定 color 的文字在深色模式里看不见
        //    （例如日历页的大标题与月份）。竖屏走 Scaffold，本就不受影响。
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
            ) {
                if (showBottomBar) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        // 挖孔/刘海屏横屏时摄像头多落在左边缘，正是导航栏的位置：
                        // 把 Start 方向的 displayCutout 安全区并入 inset，
                        // 导航内容整体避开摄像头，不再被挡
                        windowInsets = WindowInsets.displayCutout
                            .union(WindowInsets.systemBars)
                            .only(WindowInsetsSides.Start + WindowInsetsSides.Vertical),
                    ) {
                        // 极矮横屏（部分机型横屏可用高度不足 340dp）下可滚动，保证 5 个入口都点得到
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            bottomTabs.forEach { tab ->
                                val selected = currentRoute == tab.route
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navController.selectTab(tab.route) },
                                    icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                                    label = {
                                        Text(
                                            stringResource(tab.labelRes),
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                        )
                                    },
                                    colors = NavigationRailItemDefaults.colors(
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
                NavHost(
                    navController = navController,
                    startDestination = Routes.HOME,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .windowInsetsPadding(contentInsets),
                    // 关闭默认的淡入淡出页面过渡：两页同时渲染会掉帧（切页卡顿），
                    // 改为瞬时切换，体感更快更稳
                    enterTransition = { EnterTransition.None },
                    exitTransition = { ExitTransition.None },
                    popEnterTransition = { EnterTransition.None },
                    popExitTransition = { ExitTransition.None },
                ) {
                    appGraph(appViewModel = appViewModel, navController = navController)
                }
            }
        }
    } else {
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
                                icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                                label = { Text(stringResource(tab.labelRes), style = MaterialTheme.typography.labelMedium) },
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
                appGraph(appViewModel = appViewModel, navController = navController)
            }
        }
    }
}

/**
 * 页面路由表。抽成 NavGraphBuilder 扩展是为了让「竖屏 Scaffold / 横屏侧边栏」两种外壳
 * 复用同一份路由，避免两处各写一遍导致新增页面时漏加。
 */
private fun NavGraphBuilder.appGraph(
    appViewModel: AppViewModel,
    navController: NavHostController,
) {
        composable(Routes.HOME) {
            HomeScreen(
                appViewModel = appViewModel,
                onOpenBooks = { navController.navigate(Routes.BOOKS) },
                onOpenDetail = { navController.navigate(Routes.detail(it)) },
                onGoRecord = { navController.selectTab(Routes.RECORD) },
                onOpenMembers = { navController.navigate(Routes.members(it)) },
                onOpenSearch = { navController.navigate(Routes.SEARCH) },
            )
        }
        composable(Routes.CALENDAR) {
            CalendarScreen(
                appViewModel = appViewModel,
                onOpenDetail = { navController.navigate(Routes.detail(it)) },
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
        composable(Routes.SEARCH) {
            SearchScreen(
                appViewModel = appViewModel,
                onBack = { navController.popBackStack() },
                onOpenDetail = { navController.navigate(Routes.detail(it)) },
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
