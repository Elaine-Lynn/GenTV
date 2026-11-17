package com.gentv.mobile.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.AlertDialog
import com.gentv.mobile.ui.AppText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.gentv.mobile.UiState
import com.gentv.mobile.WizardViewModel
import com.gentv.mobile.ui.screens.ConnectScreen
import com.gentv.mobile.ui.screens.DoneScreen
import com.gentv.mobile.ui.screens.AllAppsScreen
import com.gentv.mobile.ui.screens.AdBlockScreen
import com.gentv.mobile.ui.screens.AppsScreen
import com.gentv.mobile.ui.screens.SpaceScreen
import com.gentv.mobile.ui.screens.HubScreen
import com.gentv.mobile.ui.screens.CheckScreen
import com.gentv.mobile.ui.screens.RescueScreen
import com.gentv.mobile.ui.screens.RemoteScreen
import com.gentv.mobile.ui.screens.SendFilesScreen
import com.gentv.core.ops.isOff
import com.gentv.core.ops.offByUs
import com.gentv.mobile.ui.screens.GetReadyScreen
import com.gentv.mobile.ui.screens.HomeScreenStep
import com.gentv.mobile.ui.screens.ReviewScreen
import com.gentv.mobile.ui.screens.TidyingScreen
import com.gentv.mobile.ui.screens.UnsupportedScreen
import com.gentv.mobile.ui.screens.WelcomeScreen
import com.gentv.core.flow.Mode
import com.gentv.core.flow.Step

/** What the activity does for the app: things that need a Context or another app. */
class Host(
    val sendFeedback: suspend (com.gentv.mobile.data.FeedbackDraft, includeLog: Boolean) -> Boolean,
    val renderCard: (com.gentv.mobile.data.ShareStats) -> android.graphics.Bitmap,
    val shareCard: (com.gentv.mobile.data.ShareStats) -> Unit,
    val openStar: () -> Unit,
    val openUrl: (String) -> Unit = {},
    /** The rating card's memory and the official website action; null in previews and tests. */
    val rating: com.gentv.mobile.data.RatingStore? = null,
    val requestReview: () -> Unit = {},
    val analyticsEnabled: () -> Boolean = { false },
    val setAnalyticsEnabled: (Boolean) -> Unit = {},
    val recordScreen: (String) -> Unit = {},
    val selfUpdate: com.gentv.mobile.data.SelfUpdate? = null,
    val installUpdate: () -> Unit = {},
)

@Composable
fun GenTVApp(s: UiState, vm: WizardViewModel, onShareLogs: () -> Unit, host: Host) {
    var feedback by remember { mutableStateOf<FeedbackRequest?>(null) }
    var card by remember { mutableStateOf<Pair<com.gentv.mobile.data.ShareStats, android.graphics.Bitmap>?>(null) }
    var showRating by remember { mutableStateOf(false) }
    var showSettings by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val actions = remember(s.tidy, s.done, s.tv, s.labels) {
        AppActions(
            settings = { host.recordScreen("settings"); showSettings = true },
            downloadUpdate = { host.selfUpdate?.download(it) },
            rate = { showRating = true },
            feedback = { feedback = it },
            shareCard = { val st = shareStats(s); card = st to host.renderCard(st) },
            openUrl = host.openUrl,
        )
    }
    host.selfUpdate?.let { SelfUpdateDialog(it, host.installUpdate) }
    CompositionLocalProvider(LocalAppActions provides actions) {
        if (showSettings) com.gentv.mobile.ui.screens.SettingsScreen(
            s.settingsUpdate, { showSettings = false }, vm::checkForUpdates, host.openUrl,
            onFeedback = { feedback = FeedbackRequest(com.gentv.mobile.data.FeedbackKind.FEATURE, "settings") },
            analyticsEnabled = host.analyticsEnabled(), onAnalyticsChanged = host.setAnalyticsEnabled,
            onDownloadUpdate = { host.selfUpdate?.download(it) },
        ) else AnimatedContent(s.wizard.step, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "step") { step ->
            if (s.desktop != null) CompositionLocalProvider(LocalTvIcons provides s.icons) { com.gentv.mobile.ui.screens.DesktopScreen(
                s.desktop, vm::closeDesktop, vm::openDesktop, vm::saveDesktop, vm::undoDesktop, vm::desktopReport, vm::desktopEvidence, onInstallPreview = vm::installDesktopPreview,
            ) } else Screen(step, s, vm, onShareLogs)
        }
    }
    // Ask right after a clean-up that really turned something off, once the success has landed.
    val goodMoment = s.wizard.step == com.gentv.core.flow.Step.DONE && s.tidy.results.values.any { it.isOff } &&
        s.done.restore is com.gentv.mobile.RestoreUi.Idle && s.problem == null
    androidx.compose.runtime.LaunchedEffect(goodMoment) {
        val store = host.rating
        if (goodMoment && store != null) {
            kotlinx.coroutines.delay(1_200)
            val busy = feedback != null || card != null || showRating
            if (com.gentv.mobile.data.RatingPolicy.shouldAsk(store.read(), System.currentTimeMillis(), goodMoment, busy)) {
                store.recordAsk()
                showRating = true
            }
        }
    }
    if (showRating) RatingPrompt(
        onRate = { stars ->
            showRating = false
            host.rating?.recordDone()
            if (stars >= HAPPY_STARS) host.requestReview()
            else feedback = FeedbackRequest(com.gentv.mobile.data.FeedbackKind.BUG, "rating", "[$stars/5 stars] ")
        },
        onLater = { showRating = false },
    )
    s.problem?.let { msg ->
        AlertDialog(
            onDismissRequest = vm::dismissProblem,
            confirmButton = { TextButton(vm::dismissProblem) { Text("OK") } },
            dismissButton = {
                TextButton({ vm.dismissProblem(); feedback = FeedbackRequest(com.gentv.mobile.data.FeedbackKind.BUG, "problem", "$msg\n\n") }) { Text("Report a problem") }
            },
            text = { Text(msg) },
        )
    }
    card?.let { (st, bmp) ->
        ShareCardDialog(
            bmp,
            onShare = { host.shareCard(st) },
            onStar = host.openStar,
            onTellUs = { card = null; feedback = FeedbackRequest(com.gentv.mobile.data.FeedbackKind.PRAISE, "share-card") },
            onDismiss = { card = null },
        )
    }
    feedback?.let { req -> FeedbackSheet(req, host.sendFeedback) { feedback = null } }
}

/** The numbers for the share card, from what this run actually did. */
fun shareStats(s: UiState): com.gentv.mobile.data.ShareStats {
    val labels = s.tidy.order.associate { it.pkg to it.label } + s.labels
    val off = s.tidy.results.filterValues { it.isOff }.keys
    // Apps people recognise first: a real icon, then a brand mark, then the rest.
    val apps = off.map { it to (labels[it] ?: com.gentv.core.lists.AppLabels.label(it)) }
        .sortedBy { (pkg, _) -> if (pkg in s.icons) 0 else if (com.gentv.core.lists.AppLabels.iconSlug(pkg) != null) 1 else 2 }
    return com.gentv.mobile.data.ShareStats(
        appsOff = off.size,
        tvName = s.tv?.model?.takeIf { it.isNotBlank() && !it.contains("demo", ignoreCase = true) } ?: s.tv?.title,
        freedMb = s.done.freedMb,
        newHome = s.done.newHome,
        apps = apps,
    )
}

/** One screen per wizard step. Also used directly by the screenshot tests. */
@Composable
fun Screen(step: Step, s: UiState, vm: WizardViewModel?, onShareLogs: () -> Unit) =
    androidx.compose.runtime.CompositionLocalProvider(LocalTvIcons provides s.icons) { StepScreen(step, s, vm, onShareLogs) }

@Composable
private fun StepScreen(step: Step, s: UiState, vm: WizardViewModel?, onShareLogs: () -> Unit) {
    when (step) {
        Step.WELCOME -> WelcomeScreen({ vm?.choose(it) }, { vm?.restoreTapped() }, s.update, { vm?.dismissUpdate() }, s.savedTvs, { vm?.connectSaved(it) }, { vm?.startRescue(it) }, s.online, { vm?.connectNew() })
        Step.CHECK -> CheckScreen(s.check, s.tv?.title, s.labels, { vm?.retidy() }, { vm?.rebootTest() }, { vm?.recheck() }, { vm?.toHub() }, { vm?.turnOnRemoteHelper(it) })
        Step.RESCUE -> RescueScreen(s.rescue, { vm?.stopRescue() }, { s.rescue.tv?.let { vm?.startRescue(it) } }, { vm?.stopRescue() })
        Step.HUB -> HubScreen(
            s.tv, s.done.records.offByUs().count { !com.gentv.core.lists.Launchers.isHomeApp(it) }, s.wizard.kind == com.gentv.core.flow.TvKind.FIRE_TV,
            { vm?.openTidy() }, { vm?.openRemote() }, { vm?.openFiles() }, { vm?.openHomeScreen() }, { vm?.openRestore() }, { vm?.navigateMainTab(2) }, { vm?.openCheck() }, { vm?.openSpace() }, { vm?.openAds() }, { vm?.openApps() }, { vm?.openDesktop() }, onPush = { vm?.navigateMainTab(3) }, onSettings = LocalAppActions.current.settings, tvScreen = s.tvScreen, onRefreshScreen = { vm?.refreshTvScreen() },
        )
        Step.REMOTE -> RemoteScreen(s.remote, s.tv?.title, { vm?.press(it) }, { vm?.type(it) }, { vm?.launchApp(it) }, { vm?.dismissRemoteMessage() }, { vm?.toHub() }, { vm?.togglePin(it) }, { p, d -> vm?.movePin(p, d) }, onNavigate = { vm?.navigateMainTab(it) })
        Step.SEND_FILES -> SendFilesScreen(s.files, s.tv?.title, { vm?.sendFiles(it) }, { vm?.cancelFile(it) }, { vm?.retryFile(it) }, { vm?.toHub() },
            remote = s.remote, initialApps = s.filesShowApps, onLaunch = { vm?.launchApp(it) },
            onManage = { vm?.toHub(); vm?.openApps() }, onRefresh = { vm?.refreshLaunchableApps() },
            onType = { vm?.type(it) }, onNavigate = { vm?.navigateMainTab(it) }, onDismissMessage = { vm?.dismissRemoteMessage() })
        Step.UNSUPPORTED -> UnsupportedScreen { vm?.back() }
        Step.GET_READY -> GetReadyScreen(s.wizard.kind, s.wizard.mode, onBack = { vm?.back() }) { vm?.ready() }
        Step.CONNECT -> ConnectScreen(s.connect, { vm?.connect(it) }, onShareLogs, s.tv, { vm?.navigateMainTab(it) }, onBack = { vm?.back() })
        Step.REVIEW -> if (s.review.all != null) AllAppsScreen(
            s.review, s.labels, { vm?.toggleApp(it) }, { vm?.setUninstall(it) }, { vm?.startTidy() }, { vm?.closeAllApps() },
            { vm?.confirmPermanent() }, { vm?.cancelPermanent() },
        ) else ReviewScreen(
            s.review, { vm?.toggleApp(it) }, { g, on -> vm?.toggleGroup(g, on) }, { vm?.startTidy() },
            { vm?.retryBackup() }, { vm?.toDone() }, { vm?.showMoreApps() }, { vm?.openAllApps() }, { vm?.setUninstall(it) },
            onBack = { vm?.back() }, tvName = s.tv?.title,
        )
        Step.APPS -> AppsScreen(s.apps, s.labels, s.tv?.title, { vm?.openApp(it) }, { vm?.closeApp() }, { vm?.changeApp(it, false) }, { vm?.changeApp(it, true) }, { vm?.toHub() }, onReload = { vm?.refreshApps() })
        Step.ADS -> AdBlockScreen(s.ads, s.tv?.title, { p, ok -> vm?.blockAds(p, ok) }, { vm?.unblockAds() }, { vm?.cancelReplaceDns() }, { vm?.toHub() }, onRetry = { vm?.refreshAds() })
        Step.SPACE -> SpaceScreen(s.space, s.tv?.title, s.labels, { vm?.clearCaches() }, { vm?.toHub() }, onRetry = { vm?.refreshSpace() })
        Step.TIDYING -> TidyingScreen(s.tidy, { vm?.toDone(); vm?.restoreEverything() }, { vm?.toDone() }, onShareLogs)
        Step.HOME_SCREEN -> HomeScreenStep(
            s.home, s.tidy.disabledCount, { vm?.toggleDock(it) }, { vm?.chooseHome(it) }, { vm?.installGenTVHome() }, { vm?.skipHome() },
            { a, eth -> vm?.homeAction(a, eth) }, { vm?.homeContinue() }, onShareLogs, { vm?.cancelPlayStore() },
            { vm?.openLook() }, { vm?.closeLook() }, { vm?.applyLook(it) }, { vm?.applyPhoto(it) }, onBack = { vm?.back() },
            onPreview = { vm?.installGenTVHome(previewOnly = true) }, onMakeHome = { vm?.makeGenTVHome() },
        )
        Step.DONE -> DoneScreen(
            s.tv, s.done, s.tidy.disabledCount, s.wizard.mode == Mode.RESTORE, s.labels,
            { vm?.restoreEverything() }, { vm?.checkDebuggingOff() }, onShareLogs, { vm?.startOver() },
            { vm?.turnOn(it) }, { vm?.toHub() },
        )
    }
}
