package com.leaf.hyperdragshare.codex

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.view.ViewConfiguration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.layout.ContentScale
import android.content.ClipboardManager
import android.content.ClipData
import android.app.WallpaperManager
import kotlin.math.max
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.border
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Slider
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.abs
import kotlin.math.floor
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.zIndex
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import androidx.core.view.WindowCompat
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.ui.NavDisplay
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBarState
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.android.appiconloader.AppIconLoader
import java.util.LinkedHashSet
import java.util.Locale
import kotlin.math.roundToInt

/** 菜单排序页：图标模式每页 4 列 × 5 行。 */
private const val ORDER_ICON_COLUMNS = 4
private const val ORDER_ICON_ROWS = 5

/** 菜单排序页：长条模式每页 1 列 × 5 行。 */
private const val ORDER_BAR_COLUMNS = 1
private const val ORDER_BAR_ROWS = 5

/** 拖动换位后冷却（ms），避免相邻格子抖动。 */
private const val DRAG_SWAP_COOLDOWN_MS = 60L

/** 拖动换位滞回：需接近目标格中心到一定程度才换位。 */
private const val ORDER_DRAG_HYSTERESIS = 0.3f

/** 「已移除」区最高显示高度（dp）：超过就纵向滚动。 */
private const val ORDER_REMOVED_AREA_MAX_DP = 190

/** 长按松手后的误触保护窗口（ms）。 */
private const val ORDER_TAP_GUARD_MS = 350L

/** 菜单排序页：图标模式单格高度（dp）——图标 34 + 名称两行 + 内边距。 */
private const val ORDER_CELL_HEIGHT_ICON_DP = 76

/** 菜单排序页：长条模式单行高度（dp）。 */
private const val ORDER_CELL_HEIGHT_BAR_DP = 54



private sealed interface SettingsRoute : NavKey {
    data object Main : SettingsRoute
    data object Visibility : SettingsRoute
    data object Order : SettingsRoute
    data object OrderAdd : SettingsRoute
    data object Blacklist : SettingsRoute
    data object TranslateApp : SettingsRoute
    data object Licenses : SettingsRoute
}

private enum class MainTab(val label: String, val icon: ImageVector) {
    Home("主页", MiuixIcons.Home),
    Settings("设置", MiuixIcons.Settings),
    About("关于", MiuixIcons.Info),
}

@Composable
internal fun DragShareSettingsApp(context: Context) {
    var settings by remember { mutableStateOf(DragShareSettings.readLocal(context)) }
    val backStack = remember { mutableStateListOf<NavKey>(SettingsRoute.Main) }
    // Hoisted above the navigation container so pushing and popping a sub page keeps the
    // selected tab, the scroll offsets and the collapsed title state of every tab.
    val pagerState = rememberPagerState(pageCount = { MainTab.entries.size })
    val homeListState = rememberLazyListState()
    val homeTopAppBarState = rememberTopAppBarState()
    val settingsListState = rememberLazyListState()
    val settingsTopAppBarState = rememberTopAppBarState()
    val aboutListState = rememberLazyListState()
    val dark = settings.colorMode == DragShareSettings.COLOR_DARK
    val themeController = remember(dark) {
        ThemeController(if (dark) ColorSchemeMode.Dark else ColorSchemeMode.Light)
    }

    val persist: (DragShareSettings) -> Unit = { next ->
        next.saveLocal(context)
        if (next.preloadTextSegmenter && !settings.preloadTextSegmenter) {
            TextSegmenter.preload(context)
        }
        settings = next
    }
    val currentSettings = rememberUpdatedState(settings)
    val currentPersist = rememberUpdatedState(persist)
    val currentDark = rememberUpdatedState(dark)
    val settingsEntryProvider = remember(backStack) {
        entryProvider<NavKey> {
            entry(SettingsRoute.Main) {
                MainShell(
                    context = context,
                    settings = currentSettings.value,
                    dark = currentDark.value,
                    pagerState = pagerState,
                    homeListState = homeListState,
                    homeTopAppBarState = homeTopAppBarState,
                    settingsListState = settingsListState,
                    settingsTopAppBarState = settingsTopAppBarState,
                    aboutListState = aboutListState,
                    isTopEntry = backStack.size == 1,
                    onOpenVisibility = { backStack.add(SettingsRoute.Visibility) },
                    onOpenOrder = { backStack.add(SettingsRoute.Order) },
                    onOpenBlacklist = { backStack.add(SettingsRoute.Blacklist) },
                    onOpenTranslateApp = { backStack.add(SettingsRoute.TranslateApp) },
                    onOpenLicenses = { backStack.add(SettingsRoute.Licenses) },
                    persist = currentPersist.value,
                )
            }
            entry(SettingsRoute.Visibility) {
                VisibilityPage(
                    context = context,
                    settings = currentSettings.value,
                    onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                    persist = currentPersist.value,
                )
            }
            entry(SettingsRoute.Order) {
                OrderPage(
                    context = context,
                    settings = currentSettings.value,
                    onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                    onAddTargets = { backStack.add(SettingsRoute.OrderAdd) },
                    persist = currentPersist.value,
                )
            }
            entry(SettingsRoute.OrderAdd) {
                OrderAddPage(
                    context = context,
                    settings = currentSettings.value,
                    onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                    persist = currentPersist.value,
                )
            }
            entry(SettingsRoute.Blacklist) {
                AccessibilityBlacklistPage(
                    context = context,
                    settings = currentSettings.value,
                    onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                    persist = currentPersist.value,
                )
            }
            entry(SettingsRoute.TranslateApp) {
                TranslateAppPage(
                    context = context,
                    settings = currentSettings.value,
                    onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                    persist = currentPersist.value,
                )
            }
            entry(SettingsRoute.Licenses) {
                DragShareOpenSourceLicensePage(
                    dark = currentDark.value,
                    onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                )
            }
        }
    }
    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryProvider = settingsEntryProvider,
    )

    MiuixTheme(controller = themeController) {
        SyncSystemBars(dark)
        NavDisplay(
            entries = entries,
            modifier = Modifier.fillMaxSize(),
            onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
        )
    }
}

@Stable
private class MainTabState(
    val pagerState: PagerState,
    private val coroutineScope: CoroutineScope,
) {
    // Tracked separately from the pager so the navigation bar highlights the target tab as soon
    // as it is tapped instead of waiting for the scroll animation to settle.
    var selectedPage by mutableIntStateOf(pagerState.currentPage)
        private set

    fun syncPage() {
        selectedPage = pagerState.currentPage
    }

    fun animateToPage(page: Int) {
        selectedPage = page
        coroutineScope.launch { pagerState.animateScrollToPage(page) }
    }
}

/** Window insets for a page inside the pager: the shell owns the top and bottom bars. */
@Composable
internal fun pageWindowInsets(): WindowInsets =
    WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)

@Composable
private fun MainShell(
    context: Context,
    settings: DragShareSettings,
    dark: Boolean,
    pagerState: PagerState,
    homeListState: LazyListState,
    homeTopAppBarState: TopAppBarState,
    settingsListState: LazyListState,
    settingsTopAppBarState: TopAppBarState,
    aboutListState: LazyListState,
    isTopEntry: Boolean,
    onOpenVisibility: () -> Unit,
    onOpenOrder: () -> Unit,
    onOpenBlacklist: () -> Unit,
    onOpenTranslateApp: () -> Unit,
    onOpenLicenses: () -> Unit,
    persist: (DragShareSettings) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val tabState = remember(pagerState, coroutineScope) { MainTabState(pagerState, coroutineScope) }
    val currentPage = pagerState.currentPage
    LaunchedEffect(currentPage) { tabState.syncPage() }

    // The bottom bar samples the pager content only. Each page owns a separate backdrop for its
    // own top bar; capturing a tree that consumes the same LayerBackdrop is what previously
    // caused RenderThread recursion on HyperOS.
    val barBackdrop = rememberDragShareBarBackdrop()
    val barColor = if (barBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface

    BackHandler(enabled = isTopEntry && tabState.selectedPage != 0) {
        tabState.animateToPage(0)
    }

    Scaffold(
        bottomBar = {
            DragShareBlurredTopBar(backdrop = barBackdrop, blurActive = barBackdrop != null) {
                NavigationBar(
                    color = barColor,
                    showDivider = barBackdrop == null,
                ) {
                    MainTab.entries.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = tabState.selectedPage == index,
                            onClick = { tabState.animateToPage(index) },
                            icon = tab.icon,
                            label = tab.label,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        val bottomInnerPadding = innerPadding.calculateBottomPadding()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (barBackdrop != null) Modifier.layerBackdrop(barBackdrop) else Modifier),
        ) {
            HorizontalPager(state = pagerState) { page ->
                when (page) {
                    MainTab.Home.ordinal -> HomePage(
                        context = context,
                        settings = settings,
                        dark = dark,
                        listState = homeListState,
                        topAppBarState = homeTopAppBarState,
                        bottomInnerPadding = bottomInnerPadding,
                    )

                    MainTab.Settings.ordinal -> SettingsPage(
                        context = context,
                        settings = settings,
                        listState = settingsListState,
                        topAppBarState = settingsTopAppBarState,
                        bottomInnerPadding = bottomInnerPadding,
                        onOpenVisibility = onOpenVisibility,
                        onOpenOrder = onOpenOrder,
                        onOpenBlacklist = onOpenBlacklist,
                        onOpenTranslateApp = onOpenTranslateApp,
                        persist = persist,
                    )

                    else -> DragShareAboutPage(
                        context = context,
                        dark = dark,
                        listState = aboutListState,
                        bottomInnerPadding = bottomInnerPadding,
                        onOpenLicenses = onOpenLicenses,
                    )
                }
            }
        }
    }
}

@Composable
private fun HomePage(
    context: Context,
    settings: DragShareSettings,
    dark: Boolean,
    listState: LazyListState,
    topAppBarState: TopAppBarState,
    bottomInnerPadding: Dp,
) {
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)
    val barBackdrop = rememberDragShareBarBackdrop()
    val barColor = if (barBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface
    val accessibilityMode = settings.isAccessibilityCaptureMode()
    // Detection lives in ActivationMonitor, so returning to this tab shows the retained result
    // instead of probing root and the portal again.
    val snapshot by rememberActivationSnapshot(context, settings.contentCaptureMode)

    Scaffold(
        topBar = {
            DragShareBlurredTopBar(backdrop = barBackdrop, blurActive = barBackdrop != null) {
                TopAppBar(
                    title = "HyperDragShare",
                    largeTitle = "HyperDragShare",
                    color = barColor,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        popupHost = { },
        contentWindowInsets = pageWindowInsets(),
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (barBackdrop != null) Modifier.layerBackdrop(barBackdrop) else Modifier),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = paddingValues,
            ) {
                item(key = "activation-status") {
                    ActivationStatusCard(
                        snapshot = snapshot,
                        dark = dark,
                        onClick = if (accessibilityMode) {
                            { openAccessibilitySettings(context) }
                        } else {
                            // In portal mode the card is the manual re-check: a stopped portal
                            // is started again and the reports are awaited once more.
                            { ActivationMonitor.refresh(context, false) }
                        },
                    )
                }
                item(key = "activation-checks-title") {
                    SmallTitle(text = "检测项")
                }
                item(key = "activation-checks") {
                    ActivationChecksCard(snapshot = snapshot)
                }
                item(key = "home-bottom-inset") {
                    Spacer(modifier = Modifier.height(bottomInnerPadding + 20.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingsPage(
    context: Context,
    settings: DragShareSettings,
    listState: LazyListState,
    topAppBarState: TopAppBarState,
    bottomInnerPadding: Dp,
    onOpenVisibility: () -> Unit,
    onOpenOrder: () -> Unit,
    onOpenBlacklist: () -> Unit,
    onOpenTranslateApp: () -> Unit,
    persist: (DragShareSettings) -> Unit,
) {
    var showAccessibilityDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val exportLogLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { destination ->
        if (destination != null) {
            coroutineScope.launch {
                val failure = withContext(Dispatchers.IO) {
                    try {
                        DragShareLog.exportTo(context, destination)
                        null
                    } catch (error: Throwable) {
                        error.message ?: error.javaClass.simpleName
                    }
                }
                Toast.makeText(
                    context,
                    if (failure == null) "日志已导出" else "导出日志失败：$failure",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    var accessibilityLongPressTimeout by remember(settings.accessibilityLongPressTimeoutMillis) {
        mutableFloatStateOf(
            settings.resolveAccessibilityLongPressTimeoutMillis(
                ViewConfiguration.getLongPressTimeout(),
            ).toFloat(),
        )
    }
    var accessibilitySensitivity by remember(settings.accessibilityRecognitionSensitivityPercent) {
        mutableFloatStateOf(settings.accessibilityRecognitionSensitivityPercent.toFloat())
    }
    val scrollBehavior = MiuixScrollBehavior(state = topAppBarState)
    val barBackdrop = rememberDragShareBarBackdrop()
    val barColor = if (barBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface

    Scaffold(
        topBar = {
            DragShareBlurredTopBar(backdrop = barBackdrop, blurActive = barBackdrop != null) {
                TopAppBar(
                    title = "设置",
                    largeTitle = "设置",
                    color = barColor,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        popupHost = { },
        contentWindowInsets = pageWindowInsets(),
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                // The list itself is the snapshot source for the top bar blur.
                .then(if (barBackdrop != null) Modifier.layerBackdrop(barBackdrop) else Modifier)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = paddingValues,
        ) {
            item(key = "content-title") {
                SmallTitle(text = "分享内容")
            }
            item(key = "content-preferences") {
                Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                    OverlayDropdownPreference(
                        title = "内容获取方式",
                        summary = if (settings.isPortalCaptureMode()) {
                            "由传送门识别长按内容"
                        } else {
                            "由无障碍读取文字并截取图片区域"
                        },
                        items = listOf("传送门", "无障碍"),
                        selectedIndex = settings.contentCaptureMode.coerceIn(0, 1),
                        onSelectedIndexChange = { selected ->
                            val mode = if (selected == DragShareSettings.CONTENT_CAPTURE_ACCESSIBILITY) {
                                DragShareSettings.CONTENT_CAPTURE_ACCESSIBILITY
                            } else {
                                DragShareSettings.CONTENT_CAPTURE_PORTAL
                            }
                            persist(copySettings(settings, contentCaptureMode = mode))
                            if (mode == DragShareSettings.CONTENT_CAPTURE_ACCESSIBILITY
                                && !AccessibilityRuntimeStatus.isServiceEnabled(context)
                            ) {
                                showAccessibilityDialog = true
                            }
                        },
                    )
                    ArrowPreference(
                        title = "应用黑名单",
                        summary = if (settings.isPortalCaptureMode()) {
                            "使用传送门的系统黑名单设置"
                        } else {
                            "无障碍识别时跳过指定应用"
                        },
                        onClick = {
                            if (settings.isPortalCaptureMode()) {
                                coroutineScope.launch {
                                    val started = withContext(Dispatchers.IO) {
                                        ModuleActivation.openPortalBlacklistSettings()
                                    }
                                    if (!started) {
                                        Toast.makeText(
                                            context,
                                            "无法打开传送门应用黑名单",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                            } else {
                                onOpenBlacklist()
                            }
                        },
                    )
                    if (settings.isAccessibilityCaptureMode()) {
                        SwitchPreference(
                            title = "横屏启用识别",
                            summary = "横屏时也允许无障碍读取长按内容",
                            checked = settings.accessibilityLandscapeRecognitionEnabled,
                            onCheckedChange = { checked ->
                                persist(
                                    copySettings(
                                        settings,
                                        accessibilityLandscapeRecognitionEnabled = checked,
                                    ),
                                )
                            },
                        )
                        SliderPreference(
                            title = "长按时间",
                            summary = "按住超过设定时间后开始识别",
                            value = accessibilityLongPressTimeout,
                            valueText = "${accessibilityLongPressTimeout.roundToInt()} ms",
                            valueRange = DragShareSettings.MIN_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS.toFloat()
                                ..DragShareSettings.MAX_ACCESSIBILITY_LONG_PRESS_TIMEOUT_MILLIS.toFloat(),
                            steps = 18,
                            showKeyPoints = true,
                            keyPoints = listOf(250f, 400f, 500f, 600f, 800f, 1000f, 1200f),
                            onValueChange = { accessibilityLongPressTimeout = it },
                            onValueChangeFinished = {
                                persist(
                                    copySettings(
                                        settings,
                                        accessibilityLongPressTimeoutMillis =
                                            accessibilityLongPressTimeout.roundToInt(),
                                    ),
                                )
                            },
                        )
                        SliderPreference(
                            title = "识别灵敏度",
                            summary = "提高后允许长按期间有更大的手指位移",
                            value = accessibilitySensitivity,
                            valueText = "${accessibilitySensitivity.roundToInt()}%",
                            valueRange = DragShareSettings
                                .MIN_ACCESSIBILITY_RECOGNITION_SENSITIVITY_PERCENT.toFloat()
                                ..DragShareSettings
                                    .MAX_ACCESSIBILITY_RECOGNITION_SENSITIVITY_PERCENT.toFloat(),
                            steps = 5,
                            showKeyPoints = true,
                            keyPoints = listOf(50f, 75f, 100f, 125f, 150f, 175f, 200f),
                            onValueChange = { accessibilitySensitivity = it },
                            onValueChangeFinished = {
                                persist(
                                    copySettings(
                                        settings,
                                        accessibilityRecognitionSensitivityPercent =
                                            accessibilitySensitivity.roundToInt(),
                                    ),
                                )
                            },
                        )
                    }
                    SwitchPreference(
                        title = "启用文字分享",
                        summary = "长按文字时显示分享菜单",
                        checked = settings.textSharingEnabled,
                        onCheckedChange = { checked ->
                            persist(copySettings(settings, textSharingEnabled = checked))
                        },
                    )
                    SwitchPreference(
                        title = "预加载分词库",
                        summary = "启动时在后台加载词典，缩短首次打开文本分词的等待",
                        checked = settings.preloadTextSegmenter,
                        onCheckedChange = { checked ->
                            persist(copySettings(settings, preloadTextSegmenter = checked))
                        },
                    )
                    SwitchPreference(
                        title = "启用图片分享",
                        summary = "长按图片时显示分享菜单",
                        checked = settings.imageSharingEnabled,
                        onCheckedChange = { checked ->
                            persist(copySettings(settings, imageSharingEnabled = checked))
                        },
                    )
                    AnimatedVisibility(visible = settings.imageSharingEnabled) {
                        OverlayDropdownPreference(
                            title = "图片存放位置",
                            summary = if (settings.sharedCopyLocation
                                == DragShareSettings.SHARED_COPY_LOCATION_MODULE
                            ) {
                                "解决相册里出现临时副本：图片只留在模块私有目录。" +
                                    "代价是少数接收方读不到，会提示“资源不存在”"
                            } else {
                                "解决少数接收方提示“资源不存在”：分享时额外放一份公共目录里的" +
                                    "副本给它读。代价是副本存在期间相册能看到它，最长 10 分钟" +
                                    "后自动删除；预览、取消的拖动和保存到本地不会生成副本"
                            },
                            items = listOf("公共目录", "模块目录（默认）"),
                            selectedIndex = settings.sharedCopyLocation.coerceIn(
                                DragShareSettings.SHARED_COPY_LOCATION_PUBLIC,
                                DragShareSettings.SHARED_COPY_LOCATION_MODULE,
                            ),
                            onSelectedIndexChange = { selected ->
                                persist(copySettings(settings, sharedCopyLocation = selected))
                            },
                        )
                    }
                }
            }

            item(key = "appearance-title") {
                SmallTitle(text = "磨砂菜单")
            }
            item(key = "appearance-preferences") {
                Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                    OverlayDropdownPreference(
                        title = "颜色",
                        items = listOf("浅色", "深色"),
                        selectedIndex = if (settings.colorMode == DragShareSettings.COLOR_DARK) 1 else 0,
                        onSelectedIndexChange = { selected ->
                            persist(
                                copySettings(
                                    settings,
                                    colorMode = if (selected == 1) {
                                        DragShareSettings.COLOR_DARK
                                    } else {
                                        DragShareSettings.COLOR_LIGHT
                                    },
                                ),
                            )
                        },
                    )
                    ArrowPreference(
                        title = "翻译应用",
                        summary = settings.translateAppPackage
                            .takeIf { it.isNotBlank() }
                            ?.let { "已选择：${appLabel(context, it)}；点翻译会先复制文字再打开它" }
                            ?: "不选则保持现状：复制文字并提示",
                        onClick = onOpenTranslateApp,
                    )
                }
            }



            item(key = "menu-title") {
                SmallTitle(text = "菜单管理")
            }
            item(key = "menu-preferences") {
                Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                    ArrowPreference(
                        title = "菜单可见性",
                        summary = "按应用分别控制每个分享入口",
                        onClick = onOpenVisibility,
                    )
                    ArrowPreference(
                        title = "菜单排序",
                        summary = "拖动应用条目调整显示顺序",
                        onClick = onOpenOrder,
                    )
                }
            }


            item(key = "logging-title") {
                SmallTitle(text = "日志")
            }
            item(key = "logging-preferences") {
                Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                    OverlayDropdownPreference(
                        title = "日志输出等级",
                        summary = when (settings.logLevel) {
                            DragShareSettings.LOG_LEVEL_DISABLED -> "不输出模块日志"
                            DragShareSettings.LOG_LEVEL_DEBUG -> "记录运行环境与输入诊断步骤"
                            else -> "记录当前常规运行信息"
                        },
                        items = listOf("禁用", "信息（默认）", "调试"),
                        selectedIndex = settings.logLevel.coerceIn(
                            DragShareSettings.LOG_LEVEL_DISABLED,
                            DragShareSettings.LOG_LEVEL_DEBUG,
                        ),
                        onSelectedIndexChange = { selected ->
                            val next = copySettings(settings, logLevel = selected)
                            persist(next)
                            DragShareDiagnostics.captureRuntimeOnce(
                                context,
                                "logging level changed",
                                null,
                            )
                            DragShareDiagnostics.captureInputInventory(
                                context,
                                "logging level changed",
                                null,
                                null,
                            )
                        },
                    )
                    OverlayDropdownPreference(
                        title = "日志保存位置",
                        summary = if (settings.logDestination
                            == DragShareSettings.LOG_DESTINATION_FILE
                        ) {
                            "存储到 ${DragShareLog.LOG_DIRECTORY}"
                        } else {
                            "写入系统日志（Logcat）"
                        },
                        items = listOf("系统日志（默认）", "文件"),
                        selectedIndex = settings.logDestination.coerceIn(
                            DragShareSettings.LOG_DESTINATION_SYSTEM,
                            DragShareSettings.LOG_DESTINATION_FILE,
                        ),
                        onSelectedIndexChange = { selected ->
                            val next = copySettings(settings, logDestination = selected)
                            persist(next)
                            DragShareDiagnostics.captureRuntimeOnce(
                                context,
                                "logging destination changed",
                                null,
                            )
                            DragShareDiagnostics.captureInputInventory(
                                context,
                                "logging destination changed",
                                null,
                                null,
                            )
                        },
                    )
                    AnimatedVisibility(
                        visible = settings.logDestination == DragShareSettings.LOG_DESTINATION_FILE,
                    ) {
                        ArrowPreference(
                            title = "导出日志",
                            summary = "将当前日志保存到所选位置",
                            onClick = {
                                exportLogLauncher.launch(DragShareLog.exportFileName())
                            },
                        )
                    }
                }
            }

            item(key = "settings-bottom-inset") {
                Spacer(modifier = Modifier.height(bottomInnerPadding + 20.dp))
            }
        }
        OverlayDialog(
            show = showAccessibilityDialog,
            title = "开启无障碍服务",
            summary = "无障碍内容获取需要开启“HyperDragShare”服务。",
            onDismissRequest = { showAccessibilityDialog = false },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = "暂不",
                    onClick = { showAccessibilityDialog = false },
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "打开设置",
                    onClick = {
                        showAccessibilityDialog = false
                        openAccessibilitySettings(context)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

private fun openAccessibilitySettings(context: Context) {
    context.startActivity(
        Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

@Composable
private fun ActivationStatusCard(
    snapshot: ActivationSnapshot,
    dark: Boolean,
    onClick: (() -> Unit)?,
) {
    val level = snapshot.level
    val active = level == ActivationLevel.Active
    val containerColor = when {
        level == ActivationLevel.Checking -> MiuixTheme.colorScheme.surfaceContainer
        active && dark -> Color(0xFF1A3825)
        active -> Color(0xFFDFFAE4)
        level == ActivationLevel.Partial && dark -> Color(0xFF3A3018)
        level == ActivationLevel.Partial -> Color(0xFFFAF3DF)
        dark -> Color(0xFF381A1A)
        else -> Color(0xFFFAEEEE)
    }
    val title = when (level) {
        ActivationLevel.Checking -> "正在检测"
        ActivationLevel.Inactive -> "未激活"
        ActivationLevel.Partial -> "部分激活"
        ActivationLevel.Active -> "已激活"
    }
    val iconTint = when (level) {
        ActivationLevel.Active -> Color(0xFF36D167)
        ActivationLevel.Partial -> Color(0xFFD1A336)
        ActivationLevel.Inactive -> Color(0xFFD13636)
        ActivationLevel.Checking -> {
            MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.35f)
        }
    }
    val textContentColor = MiuixTheme.colorScheme.onSurface
    val descTextColor = textContentColor.copy(alpha = 0.8f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        colors = CardDefaults.defaultColors(color = containerColor),
        onClick = onClick,
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .offset(x = 50.dp, y = 38.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Icon(
                    imageVector = if (active) {
                        Icons.Rounded.CheckCircleOutline
                    } else {
                        Icons.Rounded.ErrorOutline
                    },
                    contentDescription = null,
                    modifier = Modifier.size(170.dp),
                    tint = iconTint,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textContentColor,
                )
                Spacer(modifier = Modifier.height(36.dp))
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = snapshot.activationMethod,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = descTextColor,
                )
            }
        }
    }
}

/** Detection rows below the status card, laid out like KernelSU's home info card. */
@Composable
private fun ActivationChecksCard(snapshot: ActivationSnapshot) {
    if (snapshot.checks.isEmpty()) return
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            snapshot.checks.forEachIndexed { index, check ->
                Text(
                    text = check.title,
                    fontSize = MiuixTheme.textStyles.headline1.fontSize,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    modifier = Modifier.padding(
                        top = 2.dp,
                        bottom = if (index == snapshot.checks.lastIndex) 0.dp else 24.dp,
                    ),
                    text = check.content,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = if (check.failed) {
                        Color(0xFFD13636)
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                )
            }
        }
    }
}

@Composable
private fun VisibilityPage(
    context: Context,
    settings: DragShareSettings,
    onBack: () -> Unit,
    persist: (DragShareSettings) -> Unit,
) {
    var pageData by remember(context) { mutableStateOf<VisibilityPageData?>(null) }
    var expandedGroupKeys by remember { mutableStateOf(emptySet<String>()) }
    val scrollBehavior = MiuixScrollBehavior()
    val groups = pageData?.groups.orEmpty()
    val allKeys = pageData?.targets?.mapNotNull { it.key() }.orEmpty()
    val bulkEntry = DropdownEntry(
        items = listOf(
            DropdownItem(
                text = "全选",
                onClick = { persist(copySettings(settings, hiddenTargetKeys = emptySet())) },
            ),
            DropdownItem(
                text = "全不选",
                onClick = {
                    persist(copySettings(settings, hiddenTargetKeys = LinkedHashSet(allKeys)))
                },
            ),
            DropdownItem(
                text = "全展开",
                onClick = { expandedGroupKeys = groups.map { it.key }.toSet() },
            ),
            DropdownItem(
                text = "全折叠",
                onClick = { expandedGroupKeys = emptySet() },
            ),
        ),
    )

    LaunchedEffect(context) {
        pageData = null
        pageData = withContext(Dispatchers.IO) {
            val targets = querySettingsTargets(context)
            VisibilityPageData(
                targets = targets,
                groups = buildGroups(context, targets),
                iconBitmaps = loadSettingsIcons(context, targets),
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "菜单可见性",
                largeTitle = "菜单可见性",
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackNavigationIcon(onClick = onBack) },
                actions = {
                    if (pageData != null) {
                        OverlayIconDropdownMenu(entry = bulkEntry) {
                            Icon(
                                imageVector = MiuixIcons.SelectAll,
                                contentDescription = "批量设置可见性",
                                tint = MiuixTheme.colorScheme.onBackground,
                            )
                        }
                    }
                },
            )
        },
    ) { paddingValues ->
        if (pageData == null) {
            LoadingContent(paddingValues)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = paddingValues,
            ) {
                if (groups.isEmpty()) {
                    item(key = "visibility-empty") {
                        Text(
                            text = "没有找到可用的分享应用",
                            modifier = Modifier.padding(28.dp),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                } else {
                    groups.forEach { group ->
                        item(key = "visibility-group:${group.key}") {
                            val expanded = expandedGroupKeys.contains(group.key)
                            VisibilityGroup(
                                group = group,
                                settings = settings,
                                expanded = expanded,
                                appIconBitmap = pageData?.iconBitmaps?.get(group.key),
                                onToggleExpanded = {
                                    expandedGroupKeys = if (expanded) {
                                        expandedGroupKeys - group.key
                                    } else {
                                        expandedGroupKeys + group.key
                                    }
                                },
                                onGroupCheckedChange = { checked ->
                                    val hidden = LinkedHashSet(settings.hiddenTargetKeys)
                                    group.targets.forEach { target ->
                                        if (checked) hidden.remove(target.key()) else hidden.add(target.key())
                                    }
                                    persist(copySettings(settings, hiddenTargetKeys = hidden))
                                },
                                onTargetCheckedChange = { target, checked ->
                                    val hidden = LinkedHashSet(settings.hiddenTargetKeys)
                                    if (checked) hidden.remove(target.key()) else hidden.add(target.key())
                                    persist(copySettings(settings, hiddenTargetKeys = hidden))
                                },
                            )
                        }
                    }
                }
                item(key = "visibility-spacer") {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun VisibilityGroup(
    group: TargetGroup,
    settings: DragShareSettings,
    expanded: Boolean,
    appIconBitmap: Bitmap?,
    onToggleExpanded: () -> Unit,
    onGroupCheckedChange: (Boolean) -> Unit,
    onTargetCheckedChange: (ShareTarget, Boolean) -> Unit,
) {
    val visibleCount = group.targets.count { settings.isTargetVisible(it.key()) }
    val groupState = when (visibleCount) {
        0 -> ToggleableState.Off
        group.targets.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }

    Column {
        Card(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .padding(bottom = 12.dp),
            onClick = onToggleExpanded,
            showIndication = true,
            insideMargin = PaddingValues(start = 10.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TargetIcon(
                    target = group.targets.first(),
                    modifier = Modifier.padding(end = 10.dp),
                    size = 48.dp,
                    normalizedBitmap = appIconBitmap,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.title,
                        modifier = Modifier.basicMarquee(),
                        fontWeight = FontWeight(550),
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(
                        text = "已显示${visibleCount}个",
                        modifier = Modifier.basicMarquee(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight(550),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                Checkbox(
                    state = groupState,
                    onClick = {
                        onGroupCheckedChange(groupState != ToggleableState.On)
                    },
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                group.targets.forEach { target ->
                    val checked = settings.isTargetVisible(target.key())
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(start = 12.dp)
                                .width(6.dp)
                                .height(24.dp)
                                .squircleBackground(
                                    color = if (checked) {
                                        MiuixTheme.colorScheme.primary
                                    } else {
                                        MiuixTheme.colorScheme.primaryContainer
                                    },
                                    cornerRadius = 16.dp,
                                ),
                        )
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 6.dp, end = 12.dp, bottom = 6.dp),
                        ) {
                            BasicComponent(
                                startAction = {
                                    TargetIcon(
                                        target = target,
                                        modifier = Modifier.padding(end = 2.dp),
                                        size = 40.dp,
                                        normalizedBitmap = appIconBitmap,
                                    )
                                },
                                endActions = {
                                    Checkbox(
                                        state = ToggleableState(checked),
                                        onClick = { onTargetCheckedChange(target, !checked) },
                                    )
                                },
                                insideMargin = PaddingValues(horizontal = 9.dp),
                                onClick = { onTargetCheckedChange(target, !checked) },
                            ) {
                                Text(
                                    text = target.label?.toString() ?: target.key().orEmpty(),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = targetSummary(target),
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
            }
        }
    }
}

private data class AccessibilityBlacklistApp(
    val packageName: String,
    val label: String,
    val iconBitmap: Bitmap?,
    val builtInReason: String?,
)

@Composable
private fun AccessibilityBlacklistPage(
    context: Context,
    settings: DragShareSettings,
    onBack: () -> Unit,
    persist: (DragShareSettings) -> Unit,
) {
    var apps by remember(context) { mutableStateOf<List<AccessibilityBlacklistApp>?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    val scrollBehavior = MiuixScrollBehavior()

    LaunchedEffect(context) {
        apps = null
        apps = withContext(Dispatchers.IO) {
            queryAccessibilityBlacklistApps(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "应用黑名单",
                largeTitle = "应用黑名单",
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackNavigationIcon(onClick = onBack) },
            )
        },
    ) { paddingValues ->
        val allApps = apps
        if (allApps == null) {
            LoadingContent(paddingValues)
        } else {
            val query = searchQuery.trim()
            val filteredApps = allApps.filter { app ->
                query.isEmpty()
                        || app.label.contains(query, ignoreCase = true)
                        || app.packageName.contains(query, ignoreCase = true)
            }
            val blacklistedApps = filteredApps.filter { app ->
                app.builtInReason != null
                        || settings.isAccessibilityPackageBlacklisted(app.packageName)
            }
            val availableApps = filteredApps.filter { app ->
                app.builtInReason == null
                        && !settings.isAccessibilityPackageBlacklisted(app.packageName)
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = paddingValues,
            ) {
                item(key = "blacklist-search") {
                    SearchBar(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        inputField = {
                            InputField(
                                query = searchQuery,
                                onQueryChange = { searchQuery = it },
                                onSearch = { searchExpanded = false },
                                expanded = searchExpanded,
                                onExpandedChange = { searchExpanded = it },
                                label = "搜索 ${allApps.size} 个应用",
                            )
                        },
                        expanded = searchExpanded,
                        onExpandedChange = { searchExpanded = it },
                    ) {}
                }
                if (filteredApps.isEmpty()) {
                    item(key = "blacklist-empty") {
                        Text(
                            text = "没有匹配的应用",
                            modifier = Modifier.padding(28.dp),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                } else {
                    if (blacklistedApps.isNotEmpty()) {
                        item(key = "blacklist-enabled-title") {
                            SmallTitle(text = "${blacklistedApps.size} 个应用已加入黑名单")
                        }
                        items(
                            items = blacklistedApps,
                            key = { app -> "blacklist:${app.packageName}" },
                        ) { app ->
                            AccessibilityBlacklistAppRow(
                                app = app,
                                blacklisted = true,
                                onCheckedChange = { checked ->
                                    if (app.builtInReason == null) {
                                        val packages = LinkedHashSet(
                                            settings.accessibilityBlacklistedPackages,
                                        )
                                        if (checked) {
                                            packages.add(app.packageName)
                                        } else {
                                            packages.remove(app.packageName)
                                        }
                                        persist(
                                            copySettings(
                                                settings,
                                                accessibilityBlacklistedPackages = packages,
                                            ),
                                        )
                                    }
                                },
                            )
                        }
                    }
                    if (availableApps.isNotEmpty()) {
                        item(key = "blacklist-available-title") {
                            SmallTitle(text = "${availableApps.size} 个应用未加入黑名单")
                        }
                        items(
                            items = availableApps,
                            key = { app -> "blacklist:${app.packageName}" },
                        ) { app ->
                            AccessibilityBlacklistAppRow(
                                app = app,
                                blacklisted = false,
                                onCheckedChange = { checked ->
                                    val packages = LinkedHashSet(
                                        settings.accessibilityBlacklistedPackages,
                                    )
                                    if (checked) {
                                        packages.add(app.packageName)
                                    } else {
                                        packages.remove(app.packageName)
                                    }
                                    persist(
                                        copySettings(
                                            settings,
                                            accessibilityBlacklistedPackages = packages,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
                item(key = "blacklist-spacer") {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun AccessibilityBlacklistAppRow(
    app: AccessibilityBlacklistApp,
    blacklisted: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val editable = app.builtInReason == null
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
    ) {
        BasicComponent(
            startAction = {
                AccessibilityBlacklistAppIcon(
                    app = app,
                    modifier = Modifier.padding(end = 10.dp),
                )
            },
            endActions = {
                Switch(
                    checked = blacklisted,
                    onCheckedChange = if (editable) onCheckedChange else null,
                    enabled = editable,
                )
            },
            insideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            onClick = if (editable) {
                { onCheckedChange(!blacklisted) }
            } else {
                null
            },
        ) {
            Text(
                text = app.label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = app.builtInReason ?: app.packageName,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AccessibilityBlacklistAppIcon(
    app: AccessibilityBlacklistApp,
    modifier: Modifier = Modifier,
) {
    if (app.iconBitmap != null) {
        Image(
            bitmap = app.iconBitmap.asImageBitmap(),
            contentDescription = null,
            modifier = modifier.size(40.dp),
        )
    } else {
        Box(
            modifier = modifier.size(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = app.label.firstOrNull()?.toString() ?: "?")
        }
    }
}

@Composable
private fun OrderPage(
    context: Context,
    settings: DragShareSettings,
    onBack: () -> Unit,
    onAddTargets: () -> Unit,
    persist: (DragShareSettings) -> Unit,
) {
    val visible = remember { mutableStateListOf<ShareTarget>() }
    val removed = remember { mutableStateListOf<ShareTarget>() }
    var loading by remember { mutableStateOf(true) }
    var iconBitmaps by remember { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    var editMode by rememberSaveable { mutableStateOf(false) }
    var barMode by rememberSaveable { mutableStateOf(false) }
    var plateMode by rememberSaveable { mutableStateOf(false) }

    val columns = if (barMode) ORDER_BAR_COLUMNS else ORDER_ICON_COLUMNS
    val rows = if (barMode) ORDER_BAR_ROWS else ORDER_ICON_ROWS
    val perPage = (columns * rows).coerceAtLeast(1)
    val pageCount = ((visible.size + perPage - 1) / perPage).coerceAtLeast(1)
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val cellHeightDp = if (barMode) ORDER_CELL_HEIGHT_BAR_DP else ORDER_CELL_HEIGHT_ICON_DP
    val pagerHeightDp = cellHeightDp * rows

    var cellSize by remember { mutableStateOf(Size.Zero) }
    var draggingIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var lastSwapUptime by remember { mutableStateOf(0L) }
    val dragTracker = remember { DragTracker() }
    val currentSettings = rememberUpdatedState(settings)
    val currentPersist = rememberUpdatedState(persist)

    var bottomDragTarget by remember { mutableStateOf<ShareTarget?>(null) }
    var hoverSlot by remember { mutableStateOf(-1) }
    var ghostOffset by remember { mutableStateOf(Offset.Zero) }
    var lastBottomDragEndUptime by remember { mutableStateOf(0L) }

    val plateAlpha = (255 * settings.frostedPlateAlphaPercent / 100).coerceIn(0, 255)
    val plateShade = (255 * (100 - settings.frostedDarknessPercent) / 100).coerceIn(0, 255)
    val plateColor = Color(plateShade, plateShade, plateShade, plateAlpha)
    val platePerceived = plateShade * (plateAlpha / 255f) + 255f * (1f - plateAlpha / 255f)
    val plateLight = platePerceived >= 150f
    val plateStrong = if (plateLight) Color(0xFF1F1F1F) else Color(0xFFF1EFE8)
    val plateWeak = if (plateLight) Color(0xFF444441) else Color(0xFFB4B2A9)
    val plateDivider = if (plateLight) Color(0x24000000) else Color(0x29FFFFFF)
    val dotIdle = if (plateLight) Color(0x47000000) else Color(0x52FFFFFF)
    val dotActive = if (plateLight) Color(0xFF1F1F1F) else Color(0xFFF1EFE8)
    val badgeRing = if (plateLight) Color(0xD9FFFFFF) else Color(0x8C000000)

    fun persistOrder() {
        val order = visible.mapNotNull { it.key() } + removed.mapNotNull { it.key() }
        currentPersist.value(copySettings(currentSettings.value, targetOrder = order))
    }

    fun setHidden(key: String, hidden: Boolean) {
        val next = LinkedHashSet(currentSettings.value.hiddenTargetKeys)
        if (hidden) {
            next.add(key)
        } else {
            next.remove(key)
        }
        currentPersist.value(copySettings(currentSettings.value, hiddenTargetKeys = next))
    }

    fun removeAt(index: Int) {
        val target = visible.getOrNull(index) ?: return
        val key = target.key() ?: return
        visible.removeAt(index)
        if (removed.none { it.key() == key }) {
            removed.add(target)
        }
        setHidden(key, true)
        persistOrder()
    }

    fun addBack(target: ShareTarget, index: Int = -1) {
        val key = target.key() ?: return
        removed.remove(target)
        val at = if (index in 0..visible.size) index else visible.size
        visible.add(at, target)
        setHidden(key, false)
        persistOrder()
    }

    fun copyPackageName(target: ShareTarget) {
        val text = target.packageName()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("package", text))
        Toast.makeText(context, "已复制包名 " + text, Toast.LENGTH_SHORT).show()
    }

    fun indexAt(position: Offset): Int {
        if (cellSize.width <= 0f || cellSize.height <= 0f) {
            return -1
        }
        val column = floor(position.x / cellSize.width).toInt().coerceIn(0, columns - 1)
        val row = floor(position.y / cellSize.height).toInt().coerceIn(0, rows - 1)
        return pagerState.currentPage * perPage + row * columns + column
    }

    fun cellCenter(index: Int): Offset {
        val inPage = ((index % perPage) + perPage) % perPage
        val column = inPage % columns
        val row = inPage / columns
        return Offset(
            (column + 0.5f) * cellSize.width,
            (row + 0.5f) * cellSize.height,
        )
    }

    fun isDeepInside(point: Offset, index: Int): Boolean {
        val center = cellCenter(index)
        val insetX = cellSize.width * ORDER_DRAG_HYSTERESIS / 2f
        val insetY = cellSize.height * ORDER_DRAG_HYSTERESIS / 2f
        return abs(point.x - center.x) <= cellSize.width / 2f - insetX &&
            abs(point.y - center.y) <= cellSize.height / 2f - insetY
    }

    fun syncDragOffset() {
        val index = draggingIndex
        if (index < 0 || cellSize.width <= 0f) {
            return
        }
        dragOffset = (dragTracker.finger + dragTracker.grabDelta) - cellCenter(index)
    }

    fun trySwap() {
        val from = draggingIndex
        if (from < 0 || cellSize.width <= 0f) {
            return
        }
        val now = SystemClock.uptimeMillis()
        if (now - lastSwapUptime < DRAG_SWAP_COOLDOWN_MS) {
            return
        }
        val visualCenter = dragTracker.finger + dragTracker.grabDelta
        val target = indexAt(visualCenter)
        if (target < 0 || target == from || target !in visible.indices) {
            return
        }
        if (!isDeepInside(visualCenter, target)) {
            return
        }
        visible.add(target, visible.removeAt(from))
        draggingIndex = target
        lastSwapUptime = now
        syncDragOffset()
    }

    fun updateBottomDrag(fingerLocal: Offset) {
        if (bottomDragTarget == null || cellSize.width <= 0f) {
            return
        }
        val fingerInGrid = dragTracker.chipTopLeft + fingerLocal - dragTracker.gridTopLeft
        val gridWidth = cellSize.width * columns
        val gridHeight = cellSize.height * rows
        val inside = fingerInGrid.x >= 0f && fingerInGrid.x <= gridWidth &&
            fingerInGrid.y >= 0f && fingerInGrid.y <= gridHeight
        val raw = if (inside) indexAt(fingerInGrid) else -1
        val pageStart = pagerState.currentPage * perPage
        hoverSlot = if (raw in pageStart until pageStart + perPage) raw - pageStart else -1
        ghostOffset = fingerInGrid - Offset(22f, 22f)
    }

    fun finishBottomDrag() {
        val target = bottomDragTarget
        if (target != null && hoverSlot >= 0) {
            addBack(target, pagerState.currentPage * perPage + hoverSlot)
        }
        bottomDragTarget = null
        hoverSlot = -1
        ghostOffset = Offset.Zero
        lastBottomDragEndUptime = SystemClock.uptimeMillis()
    }

    LaunchedEffect(context, settings.hiddenTargetKeys) {
        loading = true
        val loadedSettings = settings
        val (all, loadedIcons) = withContext(Dispatchers.IO) {
            val targets = querySettingsTargets(context).filterNot { it.isBuiltIn() }
            targets to loadSettingsIcons(context, targets)
        }
        val ordered = ShareTargetRepository.orderForSettings(all, loadedSettings)
        visible.clear()
        removed.clear()
        ordered.forEach { target ->
            if (loadedSettings.isTargetVisible(target.key())) {
                visible.add(target)
            } else {
                removed.add(target)
            }
        }
        iconBitmaps = loadedIcons
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "菜单排序",
                largeTitle = "菜单排序",
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackNavigationIcon(onClick = onBack) },
                actions = {
                    TextButton(
                        text = if (plateMode) "收起" else "背板",
                        onClick = { plateMode = !plateMode },
                    )
                    TextButton(
                        text = if (barMode) "图标" else "长条",
                        onClick = { barMode = !barMode },
                    )
                    TextButton(
                        text = if (editMode) "完成" else "编辑",
                        onClick = {
                            editMode = !editMode
                            draggingIndex = -1
                            dragOffset = Offset.Zero
                        },
                    )
                },
            )
        },
    ) { paddingValues ->
        if (loading) {
            LoadingContent(paddingValues)
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(paddingValues),
            ) {
                SmallTitle(
                    text = when {
                        plateMode -> "调整背板：透明度 / 磨砂 / 暗黑（菜单同步生效）"
                        editMode -> "点 − 移除 · 长按拖动排序 · 长按下方图标可拖回"
                        visible.isEmpty() -> "菜单里还没有应用"
                        else -> "共 ${visible.size} 个应用 · 左右滑动翻页"
                    },
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .onGloballyPositioned { dragTracker.gridTopLeft = it.positionInRoot() },
                ) {
                    WallpaperBackdrop(modifier = Modifier.matchParentSize())
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(plateColor, RoundedCornerShape(14.dp))
                                .padding(4.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(pagerHeightDp.dp)
                                    .onGloballyPositioned { coordinates ->
                                        dragTracker.gridTopLeft = coordinates.positionInRoot()
                                    }
                                    .onSizeChanged { size ->
                                        cellSize = Size(
                                            size.width / columns.toFloat(),
                                            size.height / rows.toFloat(),
                                        )
                                    }
                                    .pointerInput(editMode, barMode, perPage, visible.size) {
                                        if (!editMode || plateMode) {
                                            return@pointerInput
                                        }
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { position ->
                                                val index = indexAt(position)
                                                if (index in visible.indices) {
                                                    draggingIndex = index
                                                    dragTracker.finger = position
                                                    dragTracker.grabDelta = cellCenter(index) - position
                                                    lastSwapUptime = 0L
                                                    syncDragOffset()
                                                }
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                if (draggingIndex >= 0) {
                                                    dragTracker.finger = change.position
                                                    syncDragOffset()
                                                    trySwap()
                                                }
                                            },
                                            onDragEnd = {
                                                if (draggingIndex >= 0) {
                                                    persistOrder()
                                                }
                                                draggingIndex = -1
                                                dragOffset = Offset.Zero
                                            },
                                            onDragCancel = {
                                                draggingIndex = -1
                                                dragOffset = Offset.Zero
                                            },
                                        )
                                    },
                            ) {
                                HorizontalPager(
                                    state = pagerState,
                                    userScrollEnabled = draggingIndex < 0 && bottomDragTarget == null,
                                    pageSpacing = 0.dp,
                                    modifier = Modifier.fillMaxSize(),
                                ) { page ->
                                    val pageStart = page * perPage
                                    val splicing = bottomDragTarget != null &&
                                        hoverSlot >= 0 && page == pagerState.currentPage
                                    val slots: List<ShareTarget?> = List(perPage) { slot ->
                                        when {
                                            splicing && slot == hoverSlot -> null
                                            splicing && slot > hoverSlot ->
                                                visible.getOrNull(pageStart + slot - 1)
                                            else -> visible.getOrNull(pageStart + slot)
                                        }
                                    }
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        for (row in 0 until rows) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(cellHeightDp.dp),
                                            ) {
                                                for (column in 0 until columns) {
                                                    val slot = row * columns + column
                                                    val index = pageStart + slot
                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .fillMaxHeight(),
                                                    ) {
                                                        val target = slots.getOrNull(slot)
                                                        if (target != null) {
                                                            OrderCell(
                                                                target = target,
                                                                normalizedBitmap = iconBitmaps[
                                                                    target.packageName(),
                                                                ],
                                                                barMode = barMode,
                                                                editMode = editMode,
                                                                dragging = index == draggingIndex,
                                                                dragOffsetProvider = { dragOffset },
                                                                badgeRing = badgeRing,
                                                                onRemove = { removeAt(index) },
                                                                onCopy = { copyPackageName(target) },
                                                            )
                                                        } else if (splicing && slot == hoverSlot) {
                                                            OrderPlaceholderCell(barMode = barMode)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                if (bottomDragTarget != null) {
                                    val dragged = bottomDragTarget!!
                                    Box(
                                        modifier = Modifier
                                            .offset {
                                                IntOffset(
                                                    ghostOffset.x.roundToInt(),
                                                    ghostOffset.y.roundToInt(),
                                                )
                                            }
                                            .zIndex(3f),
                                    ) {
                                        TargetIcon(
                                            target = dragged,
                                            size = 40.dp,
                                            normalizedBitmap = iconBitmaps[dragged.packageName()],
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(
                                    text = "‹",
                                    onClick = {
                                        val previous = pagerState.currentPage - 1
                                        if (previous >= 0) {
                                            scope.launch { pagerState.animateScrollToPage(previous) }
                                        }
                                    },
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                for (page in 0 until pageCount) {
                                    Box(
                                        modifier = Modifier
                                            .padding(horizontal = 3.dp)
                                            .size(if (page == pagerState.currentPage) 7.dp else 6.dp)
                                            .background(
                                                if (page == pagerState.currentPage) {
                                                    dotActive
                                                } else {
                                                    dotIdle
                                                },
                                                CircleShape,
                                            ),
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                TextButton(
                                    text = "›",
                                    onClick = {
                                        val next = pagerState.currentPage + 1
                                        if (next <= pageCount - 1) {
                                            scope.launch { pagerState.animateScrollToPage(next) }
                                        }
                                    },
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(plateColor, RoundedCornerShape(14.dp))
                                .padding(4.dp),
                        ) {
                            if (plateMode) {
                                OrderPlatePanel(
                                    settings = settings,
                                    persist = persist,
                                    strong = plateStrong,
                                    weak = plateWeak,
                                )
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 12.dp, end = 6.dp, top = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "已移除",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = plateStrong,
                                    )
                                    Text(
                                        text = if (removed.isEmpty()) "" else "  ${removed.size} 个",
                                        fontSize = 12.sp,
                                        color = plateWeak,
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    TextButton(text = "＋ 更多应用", onClick = onAddTargets)
                                }
                                if (removed.isEmpty()) {
                                    Text(
                                        text = "没有移除的应用",
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                                        fontSize = 12.sp,
                                        color = plateWeak,
                                    )
                                } else {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = ORDER_REMOVED_AREA_MAX_DP.dp)
                                            .verticalScroll(rememberScrollState())
                                            .padding(horizontal = 14.dp, vertical = 2.dp),
                                    ) {
                                        removed.toList()
                                            .chunked(ORDER_ICON_COLUMNS)
                                            .forEach { rowItems ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 3.dp),
                                                ) {
                                                    for (column in 0 until ORDER_ICON_COLUMNS) {
                                                        val target = rowItems.getOrNull(column)
                                                        Box(
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .height(56.dp),
                                                            contentAlignment = Alignment.Center,
                                                        ) {
                                                            if (target != null) {
                                                                RemovedAppChip(
                                                                    target = target,
                                                                    normalizedBitmap = iconBitmaps[
                                                                        target.packageName(),
                                                                    ],
                                                                    dragEnabled = editMode && !plateMode,
                                                                    onPositioned = { offset ->
                                                                        target.key()?.let { key ->
                                                                            dragTracker.chipTopLefts[key] =
                                                                                offset
                                                                        }
                                                                    },
                                                                    onDragStart = {
                                                                        target.key()?.let { key ->
                                                                            dragTracker.chipTopLeft =
                                                                                dragTracker
                                                                                    .chipTopLefts[key]
                                                                                    ?: Offset.Zero
                                                                        }
                                                                        bottomDragTarget = target
                                                                        hoverSlot = -1
                                                                    },
                                                                    onDrag = { fingerLocal ->
                                                                        updateBottomDrag(fingerLocal)
                                                                    },
                                                                    onDragEnd = { finishBottomDrag() },
                                                                    onDragCancel = { finishBottomDrag() },
                                                                    onClick = {
                                                                        val usable =
                                                                            SystemClock.uptimeMillis() -
                                                                                lastBottomDragEndUptime >=
                                                                                ORDER_TAP_GUARD_MS
                                                                        if (usable) {
                                                                            addBack(target)
                                                                        }
                                                                    },
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
                    }
                }
            }
        }
    }
}
/**
 * 拖动过程中的实时数据。
 *
 * 刻意用**普通字段**而不是 Compose State：手指每移动一帧都会写它，
 * 若用 State 会让读取方每帧重组，反而更卡。
 */
/**
 * 拖动过程中的实时数据。
 *
 * 刻意用**普通字段**而不是 Compose State：手指每移动一帧都会写它，
 * 若用 State 会让读取方每帧重组，反而更卡。
 */
private class DragTracker {
    /** 当前手指位置（网格视口坐标）。 */
    var finger: Offset = Offset.Zero

    /** 抓取瞬间"手指 → 被拖格子中心"的固定偏移，用来把格子稳定地跟在手指下方。 */
    var grabDelta: Offset = Offset.Zero

    /** 网格容器在窗口坐标系里的左上角。 */
    var gridTopLeft: Offset = Offset.Zero

    /** 正在拖动的「已移除」chip 在窗口坐标系里的左上角。 */
    var chipTopLeft: Offset = Offset.Zero

    /** 所有 chip 的窗口坐标（按包名键），布局变化时刷新。 */
    val chipTopLefts = HashMap<String, Offset>()
}

/** 排序页小字：固定白色 + 细描影，不随背板/主题变色。 */
@Composable
private fun OrderLabel(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 11.sp,
    maxLines: Int = 2,
) {
    Text(
        text = text,
        modifier = modifier,
        fontSize = fontSize,
        color = Color.White,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        style = TextStyle(
            shadow = Shadow(color = Color(0x99000000), offset = Offset(0f, 1f), blurRadius = 3f),
        ),
    )
}

/** 「已移除」里的图标 + 名称小字；点一下加回菜单末尾，长按可拖回网格。 */
@Composable
private fun RemovedAppChip(
    target: ShareTarget,
    normalizedBitmap: Bitmap?,
    dragEnabled: Boolean,
    onPositioned: (Offset) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .onGloballyPositioned { onPositioned(it.positionInRoot()) }
            .pointerInput(target.key(), dragEnabled) {
                if (!dragEnabled) {
                    return@pointerInput
                }
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDrag = { change, _ ->
                        change.consume()
                        onDrag(change.position)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragCancel() },
                )
            }
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TargetIcon(target = target, size = 30.dp, normalizedBitmap = normalizedBitmap)
        Spacer(modifier = Modifier.height(3.dp))
        OrderLabel(
            text = target.label?.toString() ?: target.key().orEmpty(),
            maxLines = 1,
        )
    }
}

@Composable
private fun OrderCell(
    target: ShareTarget,
    normalizedBitmap: Bitmap?,
    barMode: Boolean,
    editMode: Boolean,
    dragging: Boolean,
    dragOffsetProvider: () -> Offset,
    badgeRing: Color,
    onRemove: () -> Unit,
    onCopy: () -> Unit,
) {
    val label = target.label?.toString()?.takeIf { it.isNotBlank() }
        ?: target.key().orEmpty()
    val badgeRingColor = badgeRing
    Box(
        modifier = Modifier
            .padding(if (barMode) 4.dp else 6.dp)
            .graphicsLayer {
                if (dragging) {
                    val offset = dragOffsetProvider()
                    translationX = offset.x
                    translationY = offset.y
                    scaleX = 1.06f
                    scaleY = 1.06f
                }
            }
            .zIndex(if (dragging) 1f else 0f),
    ) {
        if (barMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCopy)
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TargetIcon(target = target, size = 30.dp, normalizedBitmap = normalizedBitmap)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    OrderLabel(
                        text = label,
                        fontSize = 14.sp,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OrderLabel(
                        text = target.packageName(),
                        fontSize = 12.sp,
                        maxLines = 1,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (editMode) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(Color(0x1AE24B4A), CircleShape)
                            .clickable(onClick = onRemove),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = "−", color = Color(0xFFA32D2D), fontSize = 17.sp)
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box {
                    TargetIcon(target = target, size = 34.dp, normalizedBitmap = normalizedBitmap)
                    if (editMode) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .size(21.dp)
                                .background(Color(0xFFE24B4A), CircleShape)
                                .border(2.dp, badgeRingColor, CircleShape)
                                .clickable(onClick = onRemove),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = "−", color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                OrderLabel(text = label)
            }
        }
    }
}

/** 拖回网格时的插入占位（虚线框）。 */
@Composable
private fun OrderPlaceholderCell(barMode: Boolean) {
    Box(
        modifier = Modifier
            .padding(if (barMode) 5.dp else 6.dp)
            .fillMaxSize()
            .border(1.5.dp, Color(0xFF7F77DD), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "放这里", fontSize = 12.sp, color = Color(0xFF534AB7))
    }
}

/** 「背板」设置面板：三个滑杆，与磨砂菜单共用同一份设置。 */
@Composable
private fun OrderPlatePanel(
    settings: DragShareSettings,
    persist: (DragShareSettings) -> Unit,
    strong: Color,
    weak: Color,
) {
    fun update(transform: (DragShareSettings) -> DragShareSettings) = persist(transform(settings))
    val sliderColors = SliderDefaults.colors(
        thumbColor = strong,
        activeTrackColor = strong,
        inactiveTrackColor = weak,
    )
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "透明度", fontSize = 12.sp, color = strong)
            Spacer(modifier = Modifier.weight(1f))
            Text(text = "${settings.frostedPlateAlphaPercent}%", fontSize = 12.sp, color = weak)
        }
        Slider(
            value = settings.frostedPlateAlphaPercent.toFloat(),
            onValueChange = { value ->
                update { current ->
                    copySettings(current, frostedPlateAlphaPercent = value.roundToInt())
                }
            },
            onValueChangeFinished = {},
            valueRange = DragShareSettings.MIN_FROSTED_PLATE_ALPHA_PERCENT.toFloat()
                ..DragShareSettings.MAX_FROSTED_PLATE_ALPHA_PERCENT.toFloat(),
            colors = sliderColors,
        )
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "磨砂程度", fontSize = 12.sp, color = strong)
            Spacer(modifier = Modifier.weight(1f))
            Text(text = "${settings.frostedBlurRadiusDp} dp", fontSize = 12.sp, color = weak)
        }
        Slider(
            value = settings.frostedBlurRadiusDp.toFloat(),
            onValueChange = { value ->
                update { current ->
                    copySettings(current, frostedBlurRadiusDp = value.roundToInt())
                }
            },
            onValueChangeFinished = {},
            valueRange = DragShareSettings.MIN_FROSTED_BLUR_RADIUS_DP.toFloat()
                ..DragShareSettings.MAX_FROSTED_BLUR_RADIUS_DP.toFloat(),
            colors = sliderColors,
        )
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "暗黑程度", fontSize = 12.sp, color = strong)
            Spacer(modifier = Modifier.weight(1f))
            Text(text = "${settings.frostedDarknessPercent}%", fontSize = 12.sp, color = weak)
        }
        Slider(
            value = settings.frostedDarknessPercent.toFloat(),
            onValueChange = { value ->
                update { current ->
                    copySettings(current, frostedDarknessPercent = value.roundToInt())
                }
            },
            onValueChangeFinished = {},
            valueRange = DragShareSettings.MIN_FROSTED_DARKNESS_PERCENT.toFloat()
                ..DragShareSettings.MAX_FROSTED_DARKNESS_PERCENT.toFloat(),
            colors = sliderColors,
        )
    }
}

/** 壁纸背景：取当前桌面壁纸铺满。 */
@Composable
private fun WallpaperBackdrop(modifier: Modifier) {
    val context = LocalContext.current
    val wallpaper by produceState<Bitmap?>(initialValue = null, context) {
        value = withContext(Dispatchers.IO) {
            try {
                WallpaperManager.getInstance(context).drawable?.toBitmap()
            } catch (_: Throwable) {
                null
            }
        }
    }
    val imageBitmap = remember(wallpaper) { wallpaper?.asImageBitmap() }
    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        Box(modifier = modifier.background(Color(0xFF16324F)))
    }
}

@Composable
private fun OrderAddPage(
    context: Context,
    settings: DragShareSettings,
    onBack: () -> Unit,
    persist: (DragShareSettings) -> Unit,
) {
    var targets by remember(context) { mutableStateOf<List<ShareTarget>?>(null) }
    var iconBitmaps by remember(context) { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    val scrollBehavior = MiuixScrollBehavior()

    LaunchedEffect(context) {
        targets = null
        val (loaded, loadedIcons) = withContext(Dispatchers.IO) {
            val all = querySettingsTargets(context).filterNot { it.isBuiltIn() }
            all to loadSettingsIcons(context, all)
        }
        targets = loaded
        iconBitmaps = loadedIcons
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "添加应用",
                largeTitle = "添加应用",
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackNavigationIcon(onClick = onBack) },
            )
        },
    ) { paddingValues ->
        val all = targets
        if (all == null) {
            LoadingContent(paddingValues)
        } else {
            val query = searchQuery.trim()
            val filtered = all.filter { target ->
                val label = target.label?.toString().orEmpty()
                query.isEmpty() ||
                    label.contains(query, ignoreCase = true) ||
                    target.packageName().contains(query, ignoreCase = true)
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = paddingValues,
            ) {
                item(key = "order-add-search") {
                    SearchBar(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        inputField = {
                            InputField(
                                query = searchQuery,
                                onQueryChange = { searchQuery = it },
                                onSearch = { searchExpanded = false },
                                expanded = searchExpanded,
                                onExpandedChange = { searchExpanded = it },
                                label = "搜索 ${all.size} 个应用",
                            )
                        },
                        expanded = searchExpanded,
                        onExpandedChange = { searchExpanded = it },
                    ) {}
                }
                if (filtered.isEmpty()) {
                    item(key = "order-add-empty") {
                        Text(
                            text = "没有匹配的应用",
                            modifier = Modifier.padding(28.dp),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                } else {
                    items(
                        items = filtered,
                        key = { target -> "order-add:" + (target.key() ?: target.hashCode()) },
                    ) { target ->
                        val key = target.key()
                        val visible = settings.isTargetVisible(key)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 3.dp),
                        ) {
                            BasicComponent(
                                title = target.label?.toString() ?: key.orEmpty(),
                                summary = target.packageName(),
                                insideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                startAction = {
                                    TargetIcon(
                                        target = target,
                                        modifier = Modifier.padding(end = 10.dp),
                                        size = 40.dp,
                                        normalizedBitmap = iconBitmaps[target.packageName()],
                                    )
                                },
                                endActions = {
                                    Switch(
                                        checked = visible,
                                        onCheckedChange = { checked ->
                                            if (key == null) {
                                                return@Switch
                                            }
                                            val hidden = LinkedHashSet(settings.hiddenTargetKeys)
                                            if (checked) {
                                                hidden.remove(key)
                                            } else {
                                                hidden.add(key)
                                            }
                                            persist(
                                                copySettings(
                                                    settings,
                                                    hiddenTargetKeys = hidden,
                                                ),
                                            )
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
                item(key = "order-add-spacer") {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun LoadingContent(paddingValues: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
internal fun BackNavigationIcon(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = MiuixIcons.Back,
            contentDescription = "返回",
            tint = MiuixTheme.colorScheme.onBackground,
        )
    }
}

private data class TargetGroup(
    val key: String,
    val title: String,
    val targets: List<ShareTarget>,
)

private data class VisibilityPageData(
    val targets: List<ShareTarget>,
    val groups: List<TargetGroup>,
    val iconBitmaps: Map<String, Bitmap>,
)

@Suppress("DEPRECATION")
private fun queryAccessibilityBlacklistApps(context: Context): List<AccessibilityBlacklistApp> {
    val packageManager = context.packageManager
    val builtInReasons = AccessibilityBlacklist.builtInReasons(context)
    val packageNames = LinkedHashSet<String>()
    try {
        packageManager.getInstalledApplications(0).forEach { applicationInfo ->
            applicationInfo.packageName
                ?.takeIf { it.isNotBlank() }
                ?.let(packageNames::add)
        }
    } catch (_: Throwable) {
        // The built-in exclusions below are still shown when package queries are restricted.
    }
    packageNames.addAll(builtInReasons.keys)
    if (packageNames.isEmpty()) return emptyList()

    val targetSizePx = (48f * context.resources.displayMetrics.density)
        .roundToInt()
        .coerceAtLeast(1)
    val loader = AppIconLoader(targetSizePx, false, context.applicationContext)
    val apps = mutableListOf<AccessibilityBlacklistApp>()
    packageNames.forEach { packageName ->
        val builtInReason = builtInReasons[packageName]
        val info = try {
            packageManager.getApplicationInfo(packageName, 0)
        } catch (_: Throwable) {
            null
        }
        if (info == null) {
            if (builtInReason != null) {
                apps += AccessibilityBlacklistApp(
                    packageName = packageName,
                    label = packageName,
                    iconBitmap = null,
                    builtInReason = builtInReason,
                )
            }
            return@forEach
        }
        val icon = try {
            loader.loadIcon(info).also { it.prepareToDraw() }
        } catch (_: Throwable) {
            null
        }
        apps += AccessibilityBlacklistApp(
            packageName = packageName,
            label = info.loadLabel(packageManager).toString().ifBlank { packageName },
            iconBitmap = icon,
            builtInReason = builtInReason,
        )
    }
    return apps.sortedWith(
        compareBy<AccessibilityBlacklistApp> {
            it.label.lowercase(Locale.getDefault())
        }.thenBy { it.packageName },
    )
}


private fun querySettingsTargets(context: Context): List<ShareTarget> {
    val targets = try {
        ShareTargetRepository.queryAll(context)
    } catch (_: Throwable) {
        emptyList()
    }
    return targets + listOf(
        ShareTarget.copyTextToClipboard(ShareTargetRepository.loadCopyIcon(context)),
        ShareTarget.copyImageToClipboard(ShareTargetRepository.loadCopyIcon(context)),
        ShareTarget.saveToLocal(ShareTargetRepository.loadSaveIcon(context)),
        ShareTarget.textSegmentation(ShareTargetRepository.loadTextSegmentationIcon(context)),
    )
}

private fun buildGroups(context: Context, targets: List<ShareTarget>): List<TargetGroup> {
    return targets
        .groupBy { it.packageName() }
        .map { (packageName, values) ->
            TargetGroup(
                key = packageName,
                title = if (values.firstOrNull()?.isBuiltIn() == true) {
                    "内置功能"
                } else {
                    applicationLabel(context, packageName, packageName)
                },
                targets = values.sortedWith(
                    compareBy<ShareTarget>({ !it.isBuiltIn() }, { it.label?.toString() ?: it.key() }),
                ),
            )
        }
        .sortedWith(compareBy<TargetGroup>({ it.key != "builtin" }, { it.title }))
}

private fun applicationLabel(context: Context, packageName: String, fallback: String): String {
    return try {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        info.loadLabel(context.packageManager).toString()
    } catch (_: Throwable) {
        fallback
    }
}

private fun loadSettingsIcons(
    context: Context,
    targets: List<ShareTarget>,
): Map<String, Bitmap> {
    val packageNames = targets
        .asSequence()
        .filterNot { it.isBuiltIn() }
        .map { it.packageName() }
        .filter { it.isNotBlank() }
        .distinct()
        .toList()
    if (packageNames.isEmpty()) return emptyMap()

    val targetSizePx = (48f * context.resources.displayMetrics.density)
        .roundToInt()
        .coerceAtLeast(1)
    val loader = AppIconLoader(targetSizePx, false, context.applicationContext)
    val packageManager = context.packageManager
    val result = LinkedHashMap<String, Bitmap>()
    packageNames.forEach { packageName ->
        try {
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            result[packageName] = loader.loadIcon(applicationInfo).also { it.prepareToDraw() }
        } catch (_: Throwable) {
            // Keep the ResolveInfo drawable as a per-item fallback.
        }
    }
    return result
}

private fun targetSummary(target: ShareTarget): String {
    return when {
        target.isCopyTextToClipboard() -> "将文字复制到剪贴板"
        target.isCopyImageToClipboard() -> "将图片复制到剪贴板"
        target.isSaveToLocal() -> "仅在图片分享菜单中显示"
        target.isTextSegmentation() -> "仅在文字分享菜单中显示"
        else -> {
        target.component?.let { component ->
            component.className
                .removePrefix("${component.packageName}.")
        } ?: target.key().orEmpty()
        }
    }
}

@Composable
private fun TargetIcon(
    target: ShareTarget,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    normalizedBitmap: Bitmap? = null,
) {
    val builtInAction = target.isCopyToClipboard()
        || target.isSaveToLocal()
        || target.isTextSegmentation()
    val targetSizePx = with(LocalDensity.current) {
        size.roundToPx().coerceAtLeast(1)
    }
    val bitmap = remember(target, normalizedBitmap, targetSizePx, builtInAction) {
        if (builtInAction) {
            drawableBitmap(ShareTargetRepository.iconForDisplay(target), targetSizePx)
        } else {
            normalizedBitmap ?: drawableBitmap(target.icon)
        }
    }
    val imageBitmap = remember(bitmap) { bitmap?.asImageBitmap() }
    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            modifier = modifier.size(size),
        )
    } else {
        Box(
            modifier = modifier.size(size),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = target.label?.firstOrNull()?.toString() ?: "?")
        }
    }
}

private fun drawableBitmap(drawable: Drawable?, squareSizePx: Int? = null): Bitmap? {
    if (drawable == null) return null
    val snapshot = try {
        drawable.constantState?.newDrawable()?.mutate() ?: drawable.mutate()
    } catch (_: Throwable) {
        drawable
    }
    val width = squareSizePx ?: snapshot.intrinsicWidth.coerceAtLeast(1)
    val height = squareSizePx ?: snapshot.intrinsicHeight.coerceAtLeast(1)
    return try {
        createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            snapshot.setBounds(0, 0, width, height)
            snapshot.draw(canvas)
        }
    } catch (_: Throwable) {
        null
    }
}

/** 取应用显示名，失败时回退成包名。 */
private fun appLabel(context: Context, packageName: String): String = try {
    val info = context.packageManager.getApplicationInfo(packageName, 0)
    info.loadLabel(context.packageManager)?.toString()?.trim().orEmpty().ifBlank { packageName }
} catch (_: Throwable) {
    packageName
}

/**
 * 翻译候选应用：能处理「分享文本 / 文本处理(PROCESS_TEXT) / 系统翻译」任一 Intent 的已安装应用。
 * 返回 (显示名, 包名)，按显示名排序。
 */
private data class TranslateAppInfo(
    val packageName: String,
    val label: String,
    val iconBitmap: Bitmap?,
)

/** 应用显示名排序（忽略大小写，再按包名兜底）。 */
private fun sortedApps(apps: List<TranslateAppInfo>): List<TranslateAppInfo> =
    apps.sortedWith(
        compareBy<TranslateAppInfo> { it.label.lowercase(Locale.getDefault()) }
            .thenBy { it.packageName },
    )

/** 加载一批包名的 (显示名, 图标)，用于翻译选择器。 */
private fun loadTranslateApps(
    context: Context,
    packageNames: Collection<String>,
): List<TranslateAppInfo> {
    val packageManager = context.packageManager
    val targetSizePx = (48f * context.resources.displayMetrics.density)
        .roundToInt()
        .coerceAtLeast(1)
    val loader = AppIconLoader(targetSizePx, false, context.applicationContext)
    val apps = packageNames
        .filter { it.isNotBlank() && it != context.packageName }
        .mapNotNull { packageName ->
            val info = try {
                packageManager.getApplicationInfo(packageName, 0)
            } catch (_: Throwable) {
                null
            } ?: return@mapNotNull null
            val icon = try {
                loader.loadIcon(info).also { it.prepareToDraw() }
            } catch (_: Throwable) {
                null
            }
            TranslateAppInfo(
                packageName = packageName,
                label = info.loadLabel(packageManager)?.toString()?.trim().orEmpty()
                    .ifBlank { packageName },
                iconBitmap = icon,
            )
        }
    return sortedApps(apps)
}

/**
 * 推荐的翻译应用：能处理「分享文本 / 文本处理(PROCESS_TEXT) / 系统翻译」任一 Intent 的已安装应用。
 *
 * ACTION_TRANSLATE 也必须带 text/plain（很多 ROM 的翻译入口 filter 带该 mime，不带 type 查不到），
 * 并统一用 MATCH_DEFAULT_ONLY。
 */
private fun queryTranslateCandidates(context: Context): List<TranslateAppInfo> {
    val packageManager = context.packageManager
    val packageNames = LinkedHashSet<String>()

    fun collect(intent: Intent) {
        val resolved = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        for (info in resolved) {
            info.activityInfo?.packageName?.takeIf { it.isNotBlank() }?.let(packageNames::add)
        }
    }

    collect(Intent(Intent.ACTION_SEND).setType("text/plain"))
    collect(Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain"))
    collect(Intent(Intent.ACTION_TRANSLATE).setType("text/plain"))
    return loadTranslateApps(context, packageNames)
}

/** 全部已安装应用（含没有启动器的），作为兜底选择源——保证"小爱翻译"这类也能选到。 */
private fun queryAllApps(context: Context): List<TranslateAppInfo> {
    val packageManager = context.packageManager
    val packageNames = LinkedHashSet<String>()
    try {
        packageManager.getInstalledApplications(0).forEach { applicationInfo ->
            applicationInfo.packageName
                ?.takeIf { it.isNotBlank() }
                ?.let(packageNames::add)
        }
    } catch (_: Throwable) {
        return emptyList()
    }
    return loadTranslateApps(context, packageNames)
}

@Composable
private fun TranslateAppPage(
    context: Context,
    settings: DragShareSettings,
    onBack: () -> Unit,
    persist: (DragShareSettings) -> Unit,
) {
    var recommended by remember(context) { mutableStateOf<List<TranslateAppInfo>?>(null) }
    var allApps by remember(context) { mutableStateOf<List<TranslateAppInfo>?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    val scrollBehavior = MiuixScrollBehavior()

    LaunchedEffect(context) {
        recommended = null
        allApps = null
        recommended = withContext(Dispatchers.IO) { queryTranslateCandidates(context) }
        allApps = withContext(Dispatchers.IO) { queryAllApps(context) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "翻译应用",
                largeTitle = "翻译应用",
                scrollBehavior = scrollBehavior,
                navigationIcon = { BackNavigationIcon(onClick = onBack) },
            )
        },
    ) { paddingValues ->
        val rec = recommended
        val all = allApps
        if (rec == null || all == null) {
            LoadingContent(paddingValues)
        } else {
            val query = searchQuery.trim()
            fun matches(app: TranslateAppInfo): Boolean =
                query.isEmpty() ||
                    app.label.contains(query, ignoreCase = true) ||
                    app.packageName.contains(query, ignoreCase = true)
            val filteredRec = rec.filter(::matches)
            val filteredAll = all.filter(::matches)
            val selectedPackage = settings.translateAppPackage
            val pick: (String) -> Unit = { packageName ->
                persist(copySettings(settings, translateAppPackage = packageName))
                onBack()
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = paddingValues,
            ) {
                item(key = "translate-search") {
                    SearchBar(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        inputField = {
                            InputField(
                                query = searchQuery,
                                onQueryChange = { searchQuery = it },
                                onSearch = { searchExpanded = false },
                                expanded = searchExpanded,
                                onExpandedChange = { searchExpanded = it },
                                label = "搜索 ${all.size} 个应用",
                            )
                        },
                        expanded = searchExpanded,
                        onExpandedChange = { searchExpanded = it },
                    ) {}
                }
                item(key = "translate-none") {
                    TranslateAppRow(
                        app = null,
                        selected = selectedPackage.isBlank(),
                        onClick = { pick("") },
                    )
                }
                if (filteredRec.isNotEmpty()) {
                    item(key = "translate-recommended-title") {
                        SmallTitle(text = "推荐的翻译应用（${filteredRec.size}）")
                    }
                    items(
                        items = filteredRec,
                        key = { app -> "translate-rec:${app.packageName}" },
                    ) { app ->
                        TranslateAppRow(
                            app = app,
                            selected = app.packageName == selectedPackage,
                            onClick = { pick(app.packageName) },
                        )
                    }
                }
                if (filteredAll.isNotEmpty()) {
                    item(key = "translate-all-title") {
                        SmallTitle(text = "全部应用（${filteredAll.size}）")
                    }
                    items(
                        items = filteredAll,
                        key = { app -> "translate-all:${app.packageName}" },
                    ) { app ->
                        TranslateAppRow(
                            app = app,
                            selected = app.packageName == selectedPackage,
                            onClick = { pick(app.packageName) },
                        )
                    }
                } else if (filteredRec.isEmpty()) {
                    item(key = "translate-empty") {
                        Text(
                            text = "没有匹配的应用",
                            modifier = Modifier.padding(28.dp),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
                item(key = "translate-spacer") {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

/** app == null 表示"无（复制文字 + 提示）"这一项。 */
@Composable
private fun TranslateAppRow(
    app: TranslateAppInfo?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val bitmap = app?.iconBitmap
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                )
            } else {
                Spacer(modifier = Modifier.size(40.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app?.label ?: "无（复制文字 + 提示）",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = app?.packageName ?: "只把文字放进剪贴板并提示",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) {
                Text(
                    text = "已选",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun copySettings(
    current: DragShareSettings,
    colorMode: Int = current.colorMode,
    textSharingEnabled: Boolean = current.textSharingEnabled,
    imageSharingEnabled: Boolean = current.imageSharingEnabled,
    hiddenTargetKeys: Set<String> = current.hiddenTargetKeys,
    targetOrder: List<String> = current.targetOrder,
    contentCaptureMode: Int = current.contentCaptureMode,
    accessibilityLandscapeRecognitionEnabled: Boolean =
        current.accessibilityLandscapeRecognitionEnabled,
    accessibilityBlacklistedPackages: Set<String> = current.accessibilityBlacklistedPackages,
    accessibilityLongPressTimeoutMillis: Int = current.accessibilityLongPressTimeoutMillis,
    accessibilityRecognitionSensitivityPercent: Int =
        current.accessibilityRecognitionSensitivityPercent,
    preloadTextSegmenter: Boolean = current.preloadTextSegmenter,
    logLevel: Int = current.logLevel,
    logDestination: Int = current.logDestination,
    sharedCopyLocation: Int = current.sharedCopyLocation,
    frostedPlateAlphaPercent: Int = current.frostedPlateAlphaPercent,
    frostedBlurRadiusDp: Int = current.frostedBlurRadiusDp,
    frostedDarknessPercent: Int = current.frostedDarknessPercent,
    translateAppPackage: String = current.translateAppPackage,
): DragShareSettings = DragShareSettings(
    colorMode = colorMode,
    textSharingEnabled = textSharingEnabled,
    imageSharingEnabled = imageSharingEnabled,
    hiddenTargetKeys = hiddenTargetKeys,
    targetOrder = targetOrder,
    contentCaptureMode = contentCaptureMode,
    accessibilityLandscapeRecognitionEnabled = accessibilityLandscapeRecognitionEnabled,
    accessibilityBlacklistedPackages = accessibilityBlacklistedPackages,
    accessibilityLongPressTimeoutMillis = accessibilityLongPressTimeoutMillis,
    accessibilityRecognitionSensitivityPercent = accessibilityRecognitionSensitivityPercent,
    preloadTextSegmenter = preloadTextSegmenter,
    logLevel = logLevel,
    logDestination = logDestination,
    sharedCopyLocation = sharedCopyLocation,
    frostedPlateAlphaPercent = frostedPlateAlphaPercent,
    frostedBlurRadiusDp = frostedBlurRadiusDp,
    frostedDarknessPercent = frostedDarknessPercent,
    translateAppPackage = translateAppPackage,
)

@Suppress("DEPRECATION")
@Composable
private fun SyncSystemBars(dark: Boolean) {
    val view = LocalView.current
    SideEffect {
        val activity = view.context as? Activity ?: return@SideEffect
        val window = activity.window
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        val insetsController = WindowCompat.getInsetsController(window, view)
        insetsController.isAppearanceLightStatusBars = !dark
        insetsController.isAppearanceLightNavigationBars = !dark
    }
}
