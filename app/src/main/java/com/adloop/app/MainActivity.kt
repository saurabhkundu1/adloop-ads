package com.adloop.app

import android.app.Activity
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Ink = Color(0xFF172522)
private val Page = Color(0xFFF5F5F0)
private val Muted = Color(0xFF75817B)
private val Lime = Color(0xFFD9F56D)
private val White = Color.White

private enum class AppTab { Feed, Wallet }

class MainActivity : Activity() {
    private var appVisible by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        setContentView(ComposeView(this).apply {
            setContent { AdLoopApp(appVisible = appVisible) }
        })
    }

    override fun onStart() {
        super.onStart()
        appVisible = true
    }

    override fun onStop() {
        appVisible = false
        super.onStop()
    }
}

@Composable
private fun AdLoopApp(appVisible: Boolean) {
    val context = LocalContext.current
    val api = remember(context) { AdLoopApi(context.applicationContext) }
    val frequencyManager = remember(context) { AdFrequencyManager(context.applicationContext) }
    var frequencyMode by remember { mutableStateOf(frequencyManager.mode) }
    val scope = rememberCoroutineScope()
    var campaigns by remember { mutableStateOf(emptyList<Campaign>()) }
    var wallet by remember { mutableStateOf(Wallet(0, emptyList())) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var tab by remember { mutableStateOf(AppTab.Feed) }

    suspend fun refresh() {
        campaigns = api.feed()
        wallet = api.wallet()
    }

    LaunchedEffect(api) {
        try {
            api.register()
            refresh()
            error = null
        } catch (cause: Exception) {
            error = cause.message ?: "Could not connect to the local demo server"
        } finally {
            loading = false
        }
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = Ink, background = Page, surface = White, onSurface = Ink)) {
        androidx.compose.material3.Scaffold(
            containerColor = Page,
            bottomBar = {
                NavigationBar(containerColor = White, tonalElevation = 0.dp) {
                    NavigationBarItem(
                        selected = tab == AppTab.Feed,
                        onClick = { tab = AppTab.Feed },
                        icon = { TabIcon("▶", tab == AppTab.Feed) },
                        label = { Text("Feed") },
                    )
                    NavigationBarItem(
                        selected = tab == AppTab.Wallet,
                        onClick = { tab = AppTab.Wallet },
                        icon = { TabIcon("₹", tab == AppTab.Wallet) },
                        label = { Text("Wallet") },
                    )
                }
            },
        ) { insets ->
            when (tab) {
                AppTab.Feed -> FeedScreen(
                    modifier = Modifier.padding(insets),
                    campaigns = campaigns,
                    balance = wallet.balance,
                    appVisible = appVisible,
                    loading = loading,
                    error = error,
                    onWallet = { tab = AppTab.Wallet },
                    onStart = api::startView,
                    onComplete = { sessionId ->
                        val credited = api.completeView(sessionId)
                        frequencyManager.recordAdCompleted()
                        refresh()
                        credited
                    },
                    frequencyMode = frequencyMode,
                    onFrequencyChange = { mode ->
                        frequencyManager.mode = mode
                        frequencyMode = mode
                    },
                    onCancel = { sessionId -> scope.launch { runCatching { api.cancelView(sessionId) } } },
                    onRetry = {
                        scope.launch {
                            loading = true
                            try {
                                api.register()
                                refresh()
                                error = null
                            } catch (cause: Exception) {
                                error = cause.message ?: "Could not connect to the local demo server"
                            } finally {
                                loading = false
                            }
                        }
                    },
                )
                AppTab.Wallet -> WalletScreen(
                    modifier = Modifier.padding(insets),
                    wallet = wallet,
                    error = error,
                )
            }
        }
    }
}

@Composable
private fun TabIcon(symbol: String, selected: Boolean) {
    Box(
        modifier = Modifier.size(25.dp).clip(RoundedCornerShape(8.dp)).background(if (selected) Lime else Page),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FeedScreen(
    modifier: Modifier,
    campaigns: List<Campaign>,
    balance: Int,
    appVisible: Boolean,
    loading: Boolean,
    error: String?,
    onWallet: () -> Unit,
    onStart: suspend (String) -> String,
    onComplete: suspend (String) -> Int,
    frequencyMode: AdFrequencyMode,
    onFrequencyChange: (AdFrequencyMode) -> Unit,
    onCancel: (String) -> Unit,
    onRetry: () -> Unit,
) {
    var index by remember { mutableIntStateOf(0) }
    var showFrequencyDialog by remember { mutableStateOf(false) }
    val ad = campaigns.getOrNull(index)

    LaunchedEffect(campaigns.size) {
        if (index >= campaigns.size) index = 0
    }

    if (showFrequencyDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showFrequencyDialog = false },
            title = { Text("Ad Pacing & Frequency", fontWeight = FontWeight.Bold, color = Ink) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select your preferred pacing for ad reel playback and transitions:", color = Muted, fontSize = 13.sp)
                    AdFrequencyMode.entries.forEach { mode ->
                        val selected = mode == frequencyMode
                        Surface(
                            onClick = {
                                onFrequencyChange(mode)
                                showFrequencyDialog = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) Lime.copy(alpha = 0.35f) else Page,
                            border = if (selected) androidx.compose.foundation.BorderStroke(1.5.dp, Ink) else null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(mode.title, fontWeight = FontWeight.Bold, color = Ink, fontSize = 14.sp)
                                    if (selected) {
                                        Spacer(Modifier.weight(1f))
                                        Text("✓ Active", fontWeight = FontWeight.Bold, color = Ink, fontSize = 11.sp)
                                    }
                                }
                                Text(mode.description, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showFrequencyDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = Ink)) {
                    Text("Done")
                }
            }
        )
    }

    Column(
        modifier = modifier.fillMaxSize().background(Page).padding(horizontal = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(Lime), contentAlignment = Alignment.Center) {
                    Text("A", color = Ink, fontWeight = FontWeight.Black, fontSize = 18.sp)
                }
                Column {
                    Text("ADLOOP", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                    Text("SPONSOR + PROVIDER ADS", color = Muted, fontSize = 8.sp, letterSpacing = 1.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    onClick = { showFrequencyDialog = true },
                    shape = RoundedCornerShape(50),
                    color = White,
                    shadowElevation = 1.dp
                ) {
                    Text(
                        "⚡ ${frequencyMode.title}",
                        color = Ink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
                    )
                }
                Surface(onClick = onWallet, shape = RoundedCornerShape(50), color = White, shadowElevation = 1.dp) {
                    Text("$balance demo credits  ↗", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("The good stuff", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
                Text("Sponsor and provider ads share this feed.", color = Muted, fontSize = 11.sp)
            }
            if (ad != null) Text("${index + 1} / ${campaigns.size}", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 3.dp))
        }

        when {
            loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("Loading campaigns…", color = Muted, fontSize = 13.sp)
            }
            error != null -> Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Demo server unavailable", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(error, color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                Button(onClick = onRetry, modifier = Modifier.padding(top = 14.dp)) { Text("Retry") }
            }
            ad == null -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("No sponsor or provider ads available in this demo region.", color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            else -> ReelCard(
                ad = ad,
                index = index,
                count = campaigns.size,
                appVisible = appVisible,
                modifier = Modifier.fillMaxWidth().weight(1f),
                onPrevious = {
                    index = (index - 1).coerceAtLeast(0)
                },
                onNext = {
                    index = (index + 1).coerceAtMost(campaigns.lastIndex)
                },
                onStart = onStart,
                onComplete = onComplete,
                onCancel = onCancel,
                onFinished = {
                    index = (index + 1).coerceAtMost(campaigns.lastIndex)
                },
            )
        }

        Text(
            if (ad == null) "SPONSOR + PROVIDER INVENTORY · LOCAL DEMO" else "SPONSOR + PROVIDER INVENTORY · SWIPE UP",
            color = Muted,
            fontSize = 9.sp,
            letterSpacing = 1.1.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ReelCard(
    ad: Campaign,
    index: Int,
    count: Int,
    appVisible: Boolean,
    modifier: Modifier,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onStart: suspend (String) -> String,
    onComplete: suspend (String) -> Int,
    onCancel: (String) -> Unit,
    onFinished: () -> Unit,
) {
    var drag by remember(index) { mutableFloatStateOf(0f) }
    var sessionId by remember(ad.id) { mutableStateOf<String?>(null) }
    var secondsLeft by remember(ad.id) { mutableIntStateOf(ad.durationSeconds) }
    var completionError by remember(ad.id) { mutableStateOf<String?>(null) }
    var completedView by remember(ad.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    DisposableEffect(sessionId) {
        onDispose {
            if (!completedView) {
                sessionId?.let(onCancel)
            }
        }
    }

    LaunchedEffect(ad.id, sessionId, appVisible) {
        val session = sessionId ?: return@LaunchedEffect
        if (!appVisible) return@LaunchedEffect
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft -= 1
        }
        delay(400)
        try {
            onComplete(session)
            completedView = true
            sessionId = null
            onFinished()
        } catch (cause: Exception) {
            completionError = cause.message ?: "Could not record this view"
            completedView = true
            sessionId = null
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(ad.top, ad.bottom)))
            .pointerInput(index) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        drag += amount
                    },
                    onDragEnd = {
                        if (drag < -45f) onNext() else if (drag > 45f) onPrevious()
                        drag = 0f
                    },
                )
            },
    ) {
        Box(Modifier.fillMaxSize().padding(16.dp)) {
            Column(Modifier.align(Alignment.TopCenter), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(count) { i ->
                        Box(
                            Modifier.weight(1f).height(3.dp).clip(CircleShape)
                                .background(if (i <= index) White else White.copy(alpha = .30f)),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(ad.sourceLabel.uppercase(), color = White.copy(alpha = .8f), fontSize = 9.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (sessionId != null) "${secondsLeft}s LEFT" else "${ad.durationSeconds}s  ·  +${ad.rewardCredits} CREDITS",
                        color = Ink,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Lime).padding(horizontal = 10.dp, vertical = 7.dp),
                    )
                }
            }

            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.size(154.dp).border(1.dp, White.copy(alpha = .28f), CircleShape)
                        .padding(10.dp).clip(CircleShape).background(ad.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(ad.mark, color = ad.bottom, fontSize = 88.sp, fontWeight = FontWeight.Black, letterSpacing = (-7).sp)
                }
                Spacer(Modifier.height(19.dp))
                Text(ad.artLine, color = White.copy(alpha = .72f), fontSize = 9.sp, letterSpacing = 2.4.sp, fontWeight = FontWeight.Bold)
            }

            Column(Modifier.align(Alignment.BottomStart).padding(bottom = 2.dp)) {
                Text(ad.brand, color = ad.accent, fontSize = 10.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp))
                Text(ad.headline, color = White, fontSize = 27.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.6).sp)
                Spacer(Modifier.height(7.dp))
                Text(ad.description, color = White.copy(alpha = .75f), fontSize = 12.sp, lineHeight = 17.sp)
                val issue = completionError
                if (issue != null) Text(issue, color = Color(0xFFFFD0C7), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        scope.launch {
                            completionError = null
                            completedView = false
                            try {
                                sessionId = onStart(ad.id)
                            } catch (cause: Exception) {
                                completionError = cause.message ?: "Could not start this ad"
                            }
                        }
                    },
                    enabled = sessionId == null,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink, disabledContainerColor = White.copy(alpha = .22f), disabledContentColor = White.copy(alpha = .72f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (sessionId == null) "WATCH ${ad.durationSeconds}s SAMPLE  ·  EARN ${ad.rewardCredits} DEMO CREDITS" else "VIEWING AD  ·  $secondsLeft LEFT",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = .4.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun WalletScreen(modifier: Modifier, wallet: Wallet, error: String?) {
    Column(modifier = modifier.fillMaxSize().background(Page).padding(horizontal = 18.dp)) {
        Text("Your wallet", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
        Text("Rewards from completed sponsor and provider ads.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp, bottom = 18.dp))

        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Ink)) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text("AVAILABLE DEMO CREDITS", color = White.copy(alpha = .65f), fontSize = 9.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Bold)
                Text("${wallet.balance}", color = White, fontSize = 38.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp))
                Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(Lime))
                    Text("Recorded by the local demo server", color = White.copy(alpha = .76f), fontSize = 11.sp)
                }
            }
        }

        Text("REDEMPTIONS", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, modifier = Modifier.padding(top = 24.dp, bottom = 9.dp))
        RedemptionRow(symbol = "↗", title = "Bank transfer", subtitle = "Unavailable in this preview")
        Spacer(Modifier.height(9.dp))
        RedemptionRow(symbol = "✳", title = "Gift cards", subtitle = "Unavailable in this preview")

        Text("RECENT EARNINGS", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
        if (error != null) {
            Text(error, color = Muted, fontSize = 12.sp)
        } else if (wallet.history.isEmpty()) {
            Text("Completed views will show up here.", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 12.dp))
        } else {
            wallet.history.forEach { earning ->
                Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(earning.name, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text("${earning.source} · completed view", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    Text("+${earning.amount} credits", color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Text("LOCAL DEMO · NO REAL MONEY", color = Muted, fontSize = 9.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp))
    }
}

@Composable
private fun RedemptionRow(symbol: String, title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(White).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(Page), contentAlignment = Alignment.Center) {
            Text(symbol, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Column {
            Text(title, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
