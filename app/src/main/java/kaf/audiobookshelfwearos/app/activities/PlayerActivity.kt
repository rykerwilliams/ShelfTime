package kaf.audiobookshelfwearos.app.activities

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.InlineSlider
import androidx.wear.compose.material.InlineSliderDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.TimeText
import androidx.wear.tooling.preview.devices.WearDevices
import kaf.audiobookshelfwearos.app.data.Chapter
import kaf.audiobookshelfwearos.app.services.PlayerService
import kaf.audiobookshelfwearos.app.services.SleepTimerOption
import kaf.audiobookshelfwearos.app.userdata.UserDataManager
import kaf.audiobookshelfwearos.app.utils.BackPressCoordinator
import kaf.audiobookshelfwearos.app.utils.PlayerBroadcastActions
import kaf.audiobookshelfwearos.app.utils.RotaryScrubCalculator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.roundToInt


class PlayerActivity : ComponentActivity() {
    private var playerService: PlayerService? = null
    private var isBound = false

    // Double-press-Back-to-toggle-play/pause (see BackPressCoordinator): a single
    // press doesn't navigate back immediately -- it waits DEFAULT_WINDOW_MILLIS to
    // see whether a second press arrives. If one does, the pending navigation is
    // cancelled and playback toggles instead; otherwise the delayed navigation
    // fires like a normal single back-press. The Home button's single/double-press
    // behavior is reserved by Wear OS itself and isn't interceptable at all, which
    // is why this uses Back instead.
    private var lastBackPressMillis: Long? = null
    private var pendingBackNavigationJob: Job? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as PlayerService.LocalBinder
            playerService = binder.getService()
            isBound = true
            playerService!!.updateUIMetadata()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Start the PlayerService
        val intent = Intent(this, PlayerService::class.java)
        startForegroundService(intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val now = System.currentTimeMillis()
                if (BackPressCoordinator.isDoublePress(lastBackPressMillis, now)) {
                    lastBackPressMillis = null
                    pendingBackNavigationJob?.cancel()
                    val playPauseIntent = Intent(this@PlayerActivity, PlayerService::class.java)
                    playPauseIntent.action = "ACTION_PLAY_PAUSE"
                    startForegroundService(playPauseIntent)
                } else {
                    lastBackPressMillis = now
                    pendingBackNavigationJob = lifecycleScope.launch {
                        delay(BackPressCoordinator.DEFAULT_WINDOW_MILLIS)
                        lastBackPressMillis = null
                        finish()
                    }
                }
            }
        })

        setContent {
            PlaybackControls()
        }
    }

    @Composable
    fun PlaybackControls(isPreview: Boolean = false) {
        var isPlaying by remember { mutableStateOf(false) }
        var isBuffering by remember { mutableStateOf(false) }
        var currentPosition by remember { mutableLongStateOf(0L) }
        var duration by remember { mutableLongStateOf(0L) }
        var chapterTitle by remember { mutableStateOf("") }
        // Backlog item 4: full current Chapter (start/end), not just its title, so
        // the top-of-screen time display can compute chapter-relative elapsed/remaining
        // time. Polled from the same existing 1s loop below -- no new timer.
        var currentChapter by remember { mutableStateOf<Chapter?>(null) }
        // Absolute book-timeline position (seconds), matching the coordinate space
        // chapter.start/end use -- deliberately NOT currentPosition/1000 above, since
        // currentPosition comes from PlayerService.getCurrentPosition() (exoPlayer's
        // raw track-relative position), which resets at every track boundary and would
        // silently miscompute chapter-relative time for any multi-file audiobook.
        var chapterAnchorPositionSeconds by remember { mutableDoubleStateOf(0.0) }
        // Cycles chapter-remaining (default, index 0) -> chapter-elapsed/total (1) ->
        // book-elapsed/total (2) -> back to chapter-remaining, same idiom as
        // BottomLayout's sleepTimerIndex cycling below. Reset to 0 whenever the
        // current chapter changes (see the poll loop).
        var timeDisplayModeIndex by remember { mutableIntStateOf(0) }

        val context = LocalContext.current
        // Backlog item 3: configurable jump amounts -- read once per screen
        // composition, same convention as the speed slider's initial value below
        // (`playerService?.getSpeed() ?: 1f`); Settings changes take effect the
        // next time this screen is opened.
        val jumpBackwardSeconds = remember { UserDataManager(context).jumpBackwardSeconds }
        val jumpForwardSeconds = remember { UserDataManager(context).jumpForwardSeconds }

        // Backlog item 7: physical rotary bezel/crown input. "Scrub"/"Volume"/"Off"
        // is a Settings-configurable preference (read once per screen composition,
        // same convention as jumpBackwardSeconds/jumpForwardSeconds above); watches
        // with no rotary hardware simply never emit the scroll events this modifier
        // listens for, so "no rotary hardware" already degrades to a silent no-op
        // without any extra guard code here.
        val bezelMode = remember { UserDataManager(context).bezelMode }
        val bezelFocusRequester = remember { FocusRequester() }
        val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

        LaunchedEffect(Unit) {
            bezelFocusRequester.requestFocus()
        }

        val rotaryModifier = when (bezelMode) {
            "Scrub" -> Modifier.rotaryScrollable(
                behavior = RotaryScrollableDefaults.behavior(
                    scrollableState = rememberScrollableState { delta ->
                        // Seeks by an arbitrary (not fixed jump-button) amount derived
                        // from the raw scroll delta -- see RotaryScrubCalculator for
                        // the pixels-per-second mapping and why it's pulled out as a
                        // pure, unit-tested function.
                        playerService?.seekRelativeSeconds(
                            RotaryScrubCalculator.secondsForRotaryDelta(delta)
                        )
                        delta
                    }
                ),
                focusRequester = bezelFocusRequester
            )

            "Volume" -> {
                // Same AudioManager getStreamVolume/setStreamVolume calls BottomLayout's
                // volume InlineSlider already uses below -- reused here instead of a
                // second volume-control mechanism (e.g. adjustStreamVolume). Rotary
                // scroll deltas arrive as small, frequent pixel amounts, so they're
                // accumulated until they cross a one-volume-step threshold rather than
                // adjusting the stream volume on every callback.
                var volumeScrollAccumulator by remember { mutableFloatStateOf(0f) }
                Modifier.rotaryScrollable(
                    behavior = RotaryScrollableDefaults.behavior(
                        scrollableState = rememberScrollableState { delta ->
                            volumeScrollAccumulator += delta
                            val pixelsPerVolumeStep = 50f
                            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            var newVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            while (volumeScrollAccumulator >= pixelsPerVolumeStep) {
                                newVolume = (newVolume + 1).coerceAtMost(maxVolume)
                                volumeScrollAccumulator -= pixelsPerVolumeStep
                            }
                            while (volumeScrollAccumulator <= -pixelsPerVolumeStep) {
                                newVolume = (newVolume - 1).coerceAtLeast(0)
                                volumeScrollAccumulator += pixelsPerVolumeStep
                            }
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                            delta
                        }
                    ),
                    focusRequester = bezelFocusRequester
                )
            }

            else -> Modifier // "Off" (or any unrecognized value): no rotary handling at all.
        }

        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(lifecycleOwner) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    if (isBound) {
                        currentPosition = playerService?.getCurrentPosition() ?: 0L
                        playerService?.getDuration()?.let {
                            if (it > 0) duration = it
                        }
                        // Backlog item 4: recompute the chapter title every tick of
                        // this existing poll loop, not just from the discrete
                        // UPDATE_METADATA broadcast -- for a single-file audiobook
                        // with embedded chapters, PlayerService's listener callbacks
                        // that drive that broadcast never fire again mid-book, which
                        // otherwise froze the title after the first chapter.
                        playerService?.getCurrentChapterTitle()?.let {
                            chapterTitle = it
                        }
                        // Backlog item 4: recompute the current chapter every tick too
                        // (same loop, no new timer) and reset the cycling display mode
                        // back to chapter-remaining whenever the chapter changes --
                        // Chapter is a data class so structural equality (start/end/
                        // title) is enough to detect a change tick to tick.
                        val newChapter = playerService?.getCurrentChapter()
                        if (newChapter != currentChapter) {
                            timeDisplayModeIndex = 0
                        }
                        currentChapter = newChapter
                        playerService?.getCurrentTotalPositionInS()?.let {
                            chapterAnchorPositionSeconds = it
                        }
                    }
                    delay(1000)
                }
            }
        }

        TimeText()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .then(rotaryModifier)
                .focusRequester(bezelFocusRequester)
                .focusable(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(), contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = chapterTitle,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        fontSize = 15.sp,
                        maxLines = 2,
                        modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 10.dp)
                    )
                    // Backlog item 4: chapter-relative time display, moved up from
                    // BottomLayout and made tappable to cycle display modes. Defaults
                    // to chapter-remaining ("usually I want to finish a chapter").
                    val chapter = currentChapter
                    val positionSeconds = chapterAnchorPositionSeconds
                    val timeDisplayText = if (chapter == null) {
                        // No chapter metadata resolved yet -- fall back to book-wide
                        // elapsed/total regardless of the selected mode.
                        "${timeToString(currentPosition / 1000)} / ${timeToString(duration / 1000)}"
                    } else when (timeDisplayModeIndex) {
                        0 -> {
                            val remaining = (chapter.end - positionSeconds).coerceAtLeast(0.0)
                            "-${timeToString(remaining.toLong())}"
                        }
                        1 -> {
                            val elapsedInChapter = (positionSeconds - chapter.start).coerceAtLeast(0.0)
                            val chapterDuration = (chapter.end - chapter.start).coerceAtLeast(0.0)
                            "${timeToString(elapsedInChapter.toLong())} / ${timeToString(chapterDuration.toLong())}"
                        }
                        else -> "${timeToString(currentPosition / 1000)} / ${timeToString(duration / 1000)}"
                    }
                    Text(
                        text = timeDisplayText,
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clickable {
                                timeDisplayModeIndex = (timeDisplayModeIndex + 1) % 3
                            }
                    )
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(), contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(12.dp)
                            .weight(1f),
                        onClick = {
                            val intent = Intent(this@PlayerActivity, PlayerService::class.java)
                            intent.action = "ACTION_REWIND"
                            startForegroundService(intent)
                        }) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                tint = Color.White,
                                modifier = Modifier.weight(1f),
                                imageVector = Icons.Filled.FastRewind,
                                contentDescription = "Rewind"
                            )
                            Text(
                                text = "${jumpBackwardSeconds}s",
                                color = Color.White,
                                fontSize = 9.sp
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(0.dp)
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        var progressBar = 0f
                        if (duration > 0) progressBar = currentPosition.toFloat() / duration
                        Timber.d("duration $duration")
                        Timber.d("currentPosition $currentPosition")
                        Timber.d("progressBar $progressBar")
                        if (isBuffering) CircularProgressIndicator(
                            modifier = Modifier.fillMaxSize(),
                            startAngle = 0f,
                            indicatorColor = MaterialTheme.colors.secondary,
                            trackColor = MaterialTheme.colors.onBackground.copy(alpha = 0.1f),
                            strokeWidth = 3.dp
                        ) else CircularProgressIndicator(
                            modifier = Modifier.fillMaxSize(),
                            progress = progressBar,
                            startAngle = 0f,
                            indicatorColor = MaterialTheme.colors.secondary,
                            trackColor = MaterialTheme.colors.onBackground.copy(alpha = 0.1f),
                            strokeWidth = 3.dp
                        )
                        IconButton(modifier = Modifier.fillMaxSize(), onClick = {
                            val intent = Intent(this@PlayerActivity, PlayerService::class.java)
                            intent.action = "ACTION_PLAY_PAUSE"
                            startForegroundService(intent)
                            isPlaying = !isPlaying
                        }) {
                            Icon(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                tint = Color.White,
                                imageVector = if (isPlaying || isBuffering) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play"
                            )
                        }
                    }

                    IconButton(modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f), onClick = {
                        val intent = Intent(this@PlayerActivity, PlayerService::class.java)
                        intent.action = "ACTION_FAST_FORWARD"
                        startForegroundService(intent)
                    }) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                modifier = Modifier.weight(1f),
                                tint = Color.White,
                                imageVector = Icons.Filled.FastForward,
                                contentDescription = "Fast Forward"
                            )
                            Text(
                                text = "${jumpForwardSeconds}s",
                                color = Color.White,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = 10.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                BottomLayout()
            }
        }

        if (!isPreview) DisposableEffect(Unit) {
            val playerReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        "$packageName.${PlayerBroadcastActions.PLAYING}" -> {
                            isPlaying = true // Update the UI state
                            isBuffering = false
                        }

                        "$packageName.${PlayerBroadcastActions.BUFFERING}" -> {
                            isPlaying = false
                            isBuffering = true
                        }

                        "$packageName.${PlayerBroadcastActions.PAUSED}" -> {
                            isPlaying = false // Update the UI state
                            isBuffering = false
                        }

                        "$packageName.${PlayerBroadcastActions.UPDATE_METADATA}" -> {
                            intent.getStringExtra("CHAPTER_TITLE")?.let {
                                chapterTitle = it
                                Timber.d("chapterTitle = %s", chapterTitle)
                            }
                        }
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction("$packageName.${PlayerBroadcastActions.BUFFERING}")
                addAction("$packageName.${PlayerBroadcastActions.PLAYING}")
                addAction("$packageName.${PlayerBroadcastActions.PAUSED}")
                addAction("$packageName.${PlayerBroadcastActions.UPDATE_METADATA}")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                this@PlayerActivity.registerReceiver(playerReceiver, filter, RECEIVER_EXPORTED)
            } else {
                this@PlayerActivity.registerReceiver(playerReceiver, filter)
            }
            playerService?.updateUIMetadata()

            onDispose {
                this@PlayerActivity.unregisterReceiver(playerReceiver)
            }
        }

    }

    @Composable
    private fun BottomLayout() {
        var showVolumeSlider by remember { mutableStateOf(false) }
        var showSpeedSlider by remember { mutableStateOf(false) }
        var showSleepTimerConfirmation by remember { mutableStateOf(false) }
        val sleepTimerPresets = listOf(
            SleepTimerOption.Off,
            SleepTimerOption.Minutes(15),
            SleepTimerOption.Minutes(30),
            SleepTimerOption.Minutes(45),
            SleepTimerOption.Minutes(60),
            SleepTimerOption.EndOfChapter
        )
        var sleepTimerIndex by remember { mutableIntStateOf(0) }

        if (showSpeedSlider) {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                var speed by remember {
                    mutableFloatStateOf(
                        playerService?.getSpeed() ?: 1f
                    )
                }
                Text(
                    text = "Speed: ${speed}x",
                    color = Color.LightGray,
                    fontSize = 11.sp
                )
                InlineSlider(
                    modifier = Modifier.padding(start = 10.dp, end = 10.dp),
                    value = speed,
                    onValueChange = { v ->
                        speed = (v * 10f).roundToInt() / 10f
                        playerService?.setSpeed(speed)
                    },
                    increaseIcon = { Icon(InlineSliderDefaults.Increase, "Increase") },
                    decreaseIcon = { Icon(InlineSliderDefaults.Decrease, "Decrease") },
                    valueRange = 0.5f..2f,
                    steps = 14,
                    segmented = false
                )
            }
        } else if (showVolumeSlider) {
            val context = LocalContext.current
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            var volume by remember {
                mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
            }
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                InlineSlider(
                    modifier = Modifier.padding(10.dp),
                    value = volume.toFloat(),
                    onValueChange = { v ->
                        volume = v.toInt()
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
                    },
                    increaseIcon = { Icon(InlineSliderDefaults.Increase, "Increase") },
                    decreaseIcon = { Icon(InlineSliderDefaults.Decrease, "Decrease") },
                    steps = maxVolume,
                    segmented = false
                )
            }
        } else if (showSleepTimerConfirmation) {
            val option = sleepTimerPresets[sleepTimerIndex]
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = when (option) {
                        is SleepTimerOption.Off -> "Sleep timer off"
                        is SleepTimerOption.Minutes -> "Sleep timer: ${option.minutes} min"
                        is SleepTimerOption.EndOfChapter -> "Sleep timer: End of chapter"
                    },
                    color = Color.LightGray,
                    fontSize = 13.sp
                )
            }
        } else Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 14.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 15.dp, end = 15.dp)
            ) {
                IconButton(modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f), onClick = {
                    showVolumeSlider = true
                }) {
                    Icon(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(2.dp),
                        tint = Color.Gray,
                        imageVector = Icons.Filled.VolumeUp,
                        contentDescription = "Volume"
                    )
                }
                IconButton(modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f), onClick = {
                    showSpeedSlider = true
                }) {
                    Icon(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(2.dp),
                        tint = Color.Gray,
                        imageVector = Icons.Filled.Speed,
                        contentDescription = "Speed"
                    )
                }
                IconButton(modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f), onClick = {
                    sleepTimerIndex = (sleepTimerIndex + 1) % sleepTimerPresets.size
                    when (val option = sleepTimerPresets[sleepTimerIndex]) {
                        is SleepTimerOption.Off -> playerService?.cancelSleepTimer()
                        is SleepTimerOption.Minutes -> playerService?.setSleepTimer(option.minutes)
                        is SleepTimerOption.EndOfChapter -> playerService?.setSleepTimerAtChapterEnd()
                    }
                    showSleepTimerConfirmation = true
                }) {
                    Icon(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(2.dp),
                        tint = if (sleepTimerPresets[sleepTimerIndex] !is SleepTimerOption.Off) Color.White else Color.Gray,
                        imageVector = Icons.Filled.Bedtime,
                        contentDescription = "Sleep timer"
                    )
                }
            }
        }


        LaunchedEffect(showVolumeSlider) {
            if (showVolumeSlider) {
                delay(5000)  // Wait for 5 seconds
                showVolumeSlider = false  // Show Button A after 5 seconds
            }
        }

        LaunchedEffect(showSpeedSlider) {
            if (showSpeedSlider) {
                delay(5000)  // Wait for 5 seconds
                showSpeedSlider = false  // Show Button A after 5 seconds
            }
        }

        LaunchedEffect(showSleepTimerConfirmation) {
            if (showSleepTimerConfirmation) {
                delay(2000)
                showSleepTimerConfirmation = false
            }
        }
    }

    @Preview(device = WearDevices.LARGE_ROUND)
    @Composable
    fun PlaybackControlsPreview() {
        PlaybackControls(isPreview = true)
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(connection)
        isBound = false
    }

    private fun timeToString(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        val timeString = if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%02d:%02d", minutes, secs)
        }
        return timeString
    }
}