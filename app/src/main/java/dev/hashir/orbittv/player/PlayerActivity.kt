package dev.hashir.orbittv.player

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.focusable
import androidx.activity.compose.BackHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.media3.exoplayer.DefaultRenderersFactory
import dev.hashir.orbittv.ui.components.isTv
import dev.hashir.orbittv.ui.components.tvClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import dev.hashir.orbittv.Graph
import dev.hashir.orbittv.R
import dev.hashir.orbittv.data.CatalogState
import dev.hashir.orbittv.data.Channel
import dev.hashir.orbittv.data.StreamSource
import dev.hashir.orbittv.ui.components.ChannelTile
import dev.hashir.orbittv.ui.theme.Accent
import dev.hashir.orbittv.ui.theme.OrbitTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Full-screen live playback.
 *
 * Streams in this catalogue fail often, so the player treats failure as
 * routine: it walks the channel's other sources automatically, records what
 * died, and only surfaces an error once everything has been tried.
 */
@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onTv = isTv()

        val channelId = intent.getStringExtra(EXTRA_CHANNEL_ID).orEmpty()
        val sourceIndex = intent.getIntExtra(EXTRA_SOURCE_INDEX, 0)

        val ready = Graph.catalog.state.value as? CatalogState.Ready
        val channel = ready?.catalog?.byId?.get(channelId)

        if (channel == null || channel.streams.isEmpty()) {
            finish()
            return
        }

        val siblings = ready.catalog.byCountry[channel.countryCode]
            .orEmpty()
            .filter { it.id != channel.id && it.streams.isNotEmpty() }
            .take(40)

        setContent {
            OrbitTheme {
                PlayerScreen(
                    initialChannel = channel,
                    initialSourceIndex = sourceIndex,
                    siblings = siblings,
                    isTv = onTv,
                    onBack = { finish() },
                    onPip = { enterPipIfPossible() },
                    onToggleRotation = { toggleRotationLock() },
                )
            }
        }
    }

    private fun toggleRotationLock() {
        requestedOrientation = if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    private fun enterPipIfPossible() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
            )
        }
    }

    // Voice text typed while watching: collect the burst, then hand it to search.
    private val voice = dev.hashir.orbittv.ui.VoiceCatcher()
    private val voiceText = StringBuilder()
    private val voiceHandoff = Runnable {
        val text = voiceText.toString().trim()
        voiceText.clear()
        if (text.isNotEmpty()) {
            dev.hashir.orbittv.ui.MainActivity.pendingVoiceQuery = text
            startActivity(
                android.content.Intent(this, dev.hashir.orbittv.ui.MainActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
            finish()
        }
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val text = voice.printable(event)
        if (text != null) {
            if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                if (voice.isNewBurst(event)) voiceText.clear()
                voiceText.append(text)
                window.decorView.removeCallbacks(voiceHandoff)
                window.decorView.postDelayed(voiceHandoff, 800)
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    // On a TV, HOME means "leave", not "keep watching in a corner".
    private var onTv = false

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (!onTv) enterPipIfPossible()
    }

    companion object {
        const val EXTRA_CHANNEL_ID = "channel_id"
        const val EXTRA_SOURCE_INDEX = "source_index"
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerScreen(
    initialChannel: Channel,
    initialSourceIndex: Int,
    siblings: List<Channel>,
    isTv: Boolean,
    onBack: () -> Unit,
    onPip: () -> Unit,
    onToggleRotation: () -> Unit,
) {
    val context = LocalContext.current
    val health = Graph.health
    val prefs = Graph.prefs

    var channel by remember { mutableStateOf(initialChannel) }
    var sourceIndex by remember { mutableIntStateOf(initialSourceIndex.coerceIn(0, maxOf(0, initialChannel.streams.lastIndex))) }
    var buffering by remember { mutableStateOf(true) }
    var fatal by remember { mutableStateOf<String?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var fillMode by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }

    val source: StreamSource? = channel.streams.getOrNull(sourceIndex)
    val scope = rememberCoroutineScope()
    // Guards against rewriting the health file on every rebuffer.
    val confirmedAlive = remember { mutableSetOf<String>() }

    val exoPlayer = remember {
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        // Fall back to another decoder when the hardware one rejects a stream;
        // the Fire TV's MediaTek decoders are picky about some H.264 profiles.
        val renderers = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
        ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http)))
            .build()
            .apply { playWhenReady = true }
    }

    // (Re)build the pipeline whenever the channel, the chosen source or a
    // manual retry changes. Headers are per-source, so the data source factory
    // has to be rebuilt too.
    LaunchedEffect(channel.id, sourceIndex, attempt) {
        val s = channel.streams.getOrNull(sourceIndex) ?: return@LaunchedEffect
        buffering = true
        fatal = null

        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(s.userAgent ?: DEFAULT_USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        s.referrer?.let { http.setDefaultRequestProperties(mapOf("Referer" to it)) }

        exoPlayer.setMediaSource(
            DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http))
                .createMediaSource(MediaItem.fromUri(s.url))
        )
        exoPlayer.prepare()
        exoPlayer.play()
        prefs.noteWatched(channel.id)
    }

    // Same handling for a hard error and for a source that never starts.
    fun failCurrentSource() {
        val dead = channel.streams.getOrNull(sourceIndex)
        if (dead != null) scope.launch { health.reportPlaybackFailure(dead.url) }
        val next = sourceIndex + 1
        if (next <= channel.streams.lastIndex) {
            sourceIndex = next
        } else {
            buffering = false
            exoPlayer.stop()
            fatal = "None of this channel's ${channel.streams.size} source" +
                (if (channel.streams.size == 1) "" else "s") + " responded."
        }
    }

    // Some dead streams never answer and never error; ExoPlayer would keep
    // retrying for minutes. Give each source 15 s to produce its first frame.
    var startedKey by remember { mutableStateOf("") }
    val sourceKey = "${channel.id}#$sourceIndex#$attempt"
    LaunchedEffect(sourceKey) {
        delay(15_000)
        if (startedKey != sourceKey && fatal == null) failCurrentSource()
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    startedKey = "${channel.id}#$sourceIndex#$attempt"
                    channel.streams.getOrNull(sourceIndex)?.let { s ->
                        if (confirmedAlive.add(s.url)) {
                            scope.launch { health.reportPlaybackSuccess(s.url) }
                        }
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // Fell behind the live edge (slow start, long buffer): not a dead
                // stream. Jump back to live instead of failing over.
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    exoPlayer.seekToDefaultPosition()
                    exoPlayer.prepare()
                    return
                }
                // Walk to the next untried source before giving up.
                failCurrentSource()
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Any remote press counts as activity and pushes the auto-hide back.
    var lastInput by remember { mutableIntStateOf(0) }

    // Auto-hide the chrome while playback is healthy.
    LaunchedEffect(controlsVisible, buffering, fatal, lastInput) {
        if (controlsVisible && !buffering && fatal == null) {
            delay(if (isTv) 6000 else 3800)
            controlsVisible = false
        }
    }

    // --- Remote control -----------------------------------------------------
    val rootFocus = remember { FocusRequester() }
    val zapperFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    var focusZapper by remember { mutableStateOf(false) }
    val zapList = remember(initialChannel.id) { listOf(initialChannel) + siblings }

    fun zap(step: Int) {
        if (zapList.size < 2) return
        val i = zapList.indexOfFirst { it.id == channel.id }.coerceAtLeast(0)
        channel = zapList[(i + step + zapList.size) % zapList.size]
        sourceIndex = 0
        attempt++
        focusZapper = false
        controlsVisible = true
    }

    if (isTv) {
        // Focus follows the overlay: the strip when it's open, the video otherwise.
        LaunchedEffect(controlsVisible, focusZapper, fatal) {
            delay(60)
            runCatching {
                when {
                    fatal != null -> retryFocus.requestFocus()
                    controlsVisible && focusZapper && siblings.isNotEmpty() -> zapperFocus.requestFocus()
                    else -> rootFocus.requestFocus()
                }
            }
        }
        BackHandler(enabled = controlsVisible && fatal == null) {
            controlsVisible = false
            focusZapper = false
        }
    }

    val configuration = LocalConfiguration.current
    val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { e ->
                lastInput++
                // CH+/CH- always zap; so do Up/Down on the "stream unavailable" screen.
                if (isTv && e.type == KeyEventType.KeyDown) {
                    val up = e.key == Key.ChannelUp || (fatal != null && e.key == Key.DirectionUp)
                    val down = e.key == Key.ChannelDown || (fatal != null && e.key == Key.DirectionDown)
                    if (up) { zap(-1); return@onPreviewKeyEvent true }
                    if (down) { zap(+1); return@onPreviewKeyEvent true }
                }
                false
            }
            .then(
                if (isTv) {
                    Modifier
                        .focusRequester(rootFocus)
                        .onKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                            when (e.key) {
                                Key.DirectionUp, Key.ChannelUp -> { zap(-1); true }
                                Key.DirectionDown, Key.ChannelDown -> { zap(+1); true }
                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter,
                                Key.DirectionLeft, Key.DirectionRight, Key.Menu -> {
                                    controlsVisible = true
                                    focusZapper = true
                                    true
                                }
                                else -> false
                            }
                        }
                        .focusable()
                } else {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { controlsVisible = !controlsVisible }
                }
            )
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    player = exoPlayer
                }
            },
            update = { view ->
                view.player = exoPlayer
                view.resizeMode = if (fillMode) {
                    AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } else {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (buffering && fatal == null) {
            CircularProgressIndicator(
                color = Accent,
                strokeWidth = 3.dp,
                modifier = Modifier.align(Alignment.Center).size(40.dp),
            )
        }

        fatal?.let { message ->
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Stream unavailable",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionChip("Retry", R.drawable.ic_refresh, Modifier.focusRequester(retryFocus)) {
                        sourceIndex = 0
                        attempt++
                    }
                    ActionChip("Back", R.drawable.ic_arrow_back, onClick = onBack)
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible || fatal != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                // Top scrim + title
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                            )
                        )
                        .padding(start = 8.dp, end = 12.dp, top = 12.dp, bottom = 26.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundIcon(R.drawable.ic_arrow_back, "Back", onBack)
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(
                            channel.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOfNotNull(
                                channel.flag.takeIf { it.isNotBlank() },
                                source?.quality,
                                "Source ${sourceIndex + 1} of ${channel.streams.size}",
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.65f),
                            maxLines = 1,
                        )
                    }
                    RoundIcon(R.drawable.ic_aspect_ratio, "Change zoom") { fillMode = !fillMode }
                    if (!isTv) {
                        Spacer(Modifier.width(6.dp))
                        RoundIcon(R.drawable.ic_screen_rotation, "Rotate", onToggleRotation)
                    }
                    if (!isTv && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Spacer(Modifier.width(6.dp))
                        RoundIcon(R.drawable.ic_picture_in_picture_alt, "Pop out", onPip)
                    }
                }

                // Bottom zapper
                if (siblings.isNotEmpty() && (isTv || !landscape)) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                )
                            )
                            .padding(top = 30.dp, bottom = 14.dp),
                    ) {
                        Text(
                            "More from ${channel.countryName.ifBlank { "here" }}" +
                                if (isTv) "   ·   ▲▼ change channel" else "",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            itemsIndexed(siblings, key = { _, it -> it.id }) { index, other ->
                                ChannelTile(
                                    channel = other,
                                    onClick = {
                                        channel = other
                                        sourceIndex = 0
                                        attempt++
                                        focusZapper = false
                                        controlsVisible = false
                                    },
                                    modifier = Modifier
                                        .width(120.dp)
                                        .then(if (index == 0) Modifier.focusRequester(zapperFocus) else Modifier),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundIcon(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f))
            .tvClickable(CircleShape, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = label, tint = Color.White, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun ActionChip(
    label: String,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.16f))
            .tvClickable(CircleShape, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}

private const val DEFAULT_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
