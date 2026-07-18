package kaf.audiobookshelfwearos.app.activities

import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.ExperimentalWearMaterialApi
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.RevealValue
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.SwipeToRevealCard
import androidx.wear.compose.material.SwipeToRevealPrimaryAction
import androidx.wear.compose.material.SwipeToRevealUndoAction
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.compose.material.rememberRevealState
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.input.wearableExtender
import coil.compose.AsyncImage
import kaf.audiobookshelfwearos.R
import kaf.audiobookshelfwearos.app.ApiHandler
import kaf.audiobookshelfwearos.app.MainApp
import kaf.audiobookshelfwearos.app.data.Library
import kaf.audiobookshelfwearos.app.data.LibraryItem
import kaf.audiobookshelfwearos.app.services.MyDownloadService
import kaf.audiobookshelfwearos.app.services.PlayerService
import kaf.audiobookshelfwearos.app.userdata.UserDataManager
import kaf.audiobookshelfwearos.app.utils.BookTapRouter
import kaf.audiobookshelfwearos.app.utils.ContinueListeningSelector
import kaf.audiobookshelfwearos.app.utils.ItemTrackResolver
import kaf.audiobookshelfwearos.app.viewmodels.ApiViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

class BookListActivity : ComponentActivity() {
    companion object {
        /**
         * How long the swipe-to-reveal "Undo" affordance stays up after a delete is
         * committed before silently finalizing (§2's "official SwipeToReveal undo
         * pattern" -- a few seconds, matching the order of magnitude used by
         * `androidx.wear.compose.integration.demos`' own SwipeToReveal sample).
         */
        private const val UNDO_WINDOW_MILLIS = 5000L
    }

    private val viewModel: ApiViewModel by viewModels {
        ApiViewModel.ApiViewModelFactory(
            ApiHandler(
                this
            )
        )
    }

    private fun launchRemoteSearchInput(
        launcher: ActivityResultLauncher<Intent>,
        remoteInputs: List<RemoteInput>
    ) {
        val intent: Intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, remoteInputs)
        launcher.launch(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep the screen on while this activity is in the foreground
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        viewModel.loginResult.observe(
            this
        ) { user ->
            Timber.d(user.token)
        }

        viewModel.getLibraries(this, true, UserDataManager(this).offlineMode)

        setContent {
            val libraries by viewModel.libraries.observeAsState()
            val filteredLibraries by viewModel.filteredLibraries.collectAsState()
            val isSearchActive by viewModel.isSearchActive.collectAsState()
            val searchQuery by viewModel.searchQuery.collectAsState()

            val inputTextKey = "input_text"
            val remoteInputs: List<RemoteInput> = remember {
                listOf(
                    RemoteInput.Builder(inputTextKey)
                        .setLabel("Search")
                        .wearableExtender {
                            setEmojisAllowed(true)
                            setInputActionType(EditorInfo.IME_ACTION_DONE)
                        }.build()
                )
            }

            val launcher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                result.data?.let { data ->
                    val resultsBundle: Bundle = RemoteInput.getResultsFromIntent(data)
                    val newInputText: CharSequence? = resultsBundle.getCharSequence(inputTextKey)
                    val userInput = newInputText?.toString() ?: ""
                    viewModel.updateSearchQuery(userInput)
                }
            }
            
            val displayLibraries = if (isSearchActive) filteredLibraries else libraries
            
            // Main content without fixed header
            if (!isSearchActive) {
                ManualLoadView(displayLibraries, isSearchActive)
            }
            Libraries(
                displayLibraries, 
                isSearchActive = isSearchActive,
                searchQuery = searchQuery,
                onSearchToggle = {
                    if (!isSearchActive) {
                        launchRemoteSearchInput(launcher, remoteInputs)
                    }
                    viewModel.toggleSearch()
                }
            )

        }
    }

    @Composable
    private fun SearchHeader(
        isSearchActive: Boolean,
        searchQuery: String,
        onSearchToggle: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isSearchActive) {
                // Back button
                Button(
                    onClick = onSearchToggle,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }

                // Search query display
                Text(
                    text = if (searchQuery.isNotEmpty()) "\"$searchQuery\"" else "Enter search...",
                    style = MaterialTheme.typography.body2,
                    color = if (searchQuery.isNotEmpty())
                        MaterialTheme.colors.onSurface
                    else
                        MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .padding(horizontal = 16.dp)
                )
            } else {
                // Button row with search and settings
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Search button
                    Button(
                        onClick = onSearchToggle,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    }
                    
                    // Settings button
                    Button(
                        onClick = {
                            val intent = Intent(this@BookListActivity, SettingsActivity::class.java)
                            startActivity(intent)
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun ManualLoadView(libraries: List<Library>?, isSearchActive: Boolean = false) {
        val isLoading by viewModel.isLoading.collectAsState()

        if (isLoading) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center, modifier = Modifier
                    .fillMaxSize()
            ) {
                CircularProgressIndicator(
                    startAngle = 0f,
                    modifier = Modifier
                        .width(80.dp)
                        .height(80.dp),
                    indicatorColor = MaterialTheme.colors.secondary,
                    trackColor = MaterialTheme.colors.onBackground.copy(
                        alpha = 0.1f
                    ),
                    strokeWidth = 8.dp
                )
            }
        }

        if (libraries?.isEmpty() == true && !isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(0.dp)
            ) {

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center, modifier = Modifier
                        .fillMaxSize()
                ) {
                    Text(
                        text = if (isSearchActive) "No results found" else "There was some problem. Try again.",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(10.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    if (!isSearchActive) {
                        Button(onClick = {
                            viewModel.getLibraries(this@BookListActivity, true, UserDataManager(this@BookListActivity).offlineMode)
                        }) {
                            Text(text = "LOAD")
                        }
                    }
                }
            }

        }
    }

    @Composable
    private fun Libraries(
        libraries: List<Library>?,
        isSearchActive: Boolean = false,
        searchQuery: String = "",
        onSearchToggle: () -> Unit = {}
    ) {
        val scalingLazyListState = rememberScalingLazyListState(0)
        val focusRequester = remember { FocusRequester() }

        // Request focus when the composable is first displayed
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }

        Scaffold(
            modifier = Modifier.onGloballyPositioned {},
            positionIndicator = {
                PositionIndicator(scalingLazyListState = scalingLazyListState)
            },
            vignette = {
                Vignette(vignettePosition = VignettePosition.TopAndBottom)
            }
        ) {
            ScalingLazyColumn(
                state = scalingLazyListState,
                scalingParams = ScalingLazyColumnDefaults.scalingParams(
                    edgeScale = 0.5f,
                    minTransitionArea = 0.5f,
                    maxTransitionArea = 0.5f
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .rotaryScrollable(
                        behavior = RotaryScrollableDefaults.behavior(scrollableState = scalingLazyListState),
                        focusRequester = focusRequester
                    )
                    .focusRequester(focusRequester)
                    .focusable(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Add search header as first item
                item {
                    SearchHeader(
                        isSearchActive = isSearchActive,
                        searchQuery = searchQuery,
                        onSearchToggle = onSearchToggle
                    )
                }
                
                libraries?.let { libraryList ->
                    val hasResults = libraryList.any { it.libraryItems.isNotEmpty() }

                    // "Continue Listening" mirrors the official Audiobookshelf clients:
                    // in-progress books, most recently listened first, shown ahead of
                    // the rest of the library. Not shown while actively searching --
                    // search results should just be the matches, not re-sectioned.
                    val continueListening = if (isSearchActive) emptyList() else
                        ContinueListeningSelector.select(libraryList.flatMap { it.libraryItems })
                    val continueListeningIds = continueListening.map { it.id }.toSet()

                    if (isSearchActive && !hasResults && searchQuery.isNotEmpty()) {
                        // Show "No results found" message in search mode
                        item {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp)
                            ) {
                                Text(
                                    text = "No results found",
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.body1
                                )
                            }
                        }
                    } else {
                        if (continueListening.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Continue Listening",
                                    fontSize = 10.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp, bottom = 4.dp)
                                )
                            }
                            itemsIndexed(
                                continueListening,
                                key = { _, item -> "continue_${item.id}" }
                            ) { _, item ->
                                Column {
                                    BookItem(item)
                                    HorizontalDivider()
                                }
                            }
                        }

                        // Show remaining library items (already-shown "Continue
                        // Listening" books excluded so they don't render twice).
                        for ((libIndex, library) in libraryList.withIndex()) {
                            val remainingItems =
                                library.libraryItems.filter { it.id !in continueListeningIds }
                            itemsIndexed(
                                remainingItems,
                                key = { _, item -> item.id }
                            ) { index, item ->
                                Column {
                                    BookItem(item)
                                    val showDivider =
                                        (index != remainingItems.size - 1 || libIndex != libraryList.size - 1)
                                    if (showDivider) {
                                        HorizontalDivider()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun saveAudiobookToDB(item: LibraryItem) {
        lifecycleScope.launch {
            val db = (applicationContext as MainApp).database
            db.libraryItemDao().insertLibraryItem(item)
        }
    }

    /**
     * Returns a LibraryItem guaranteed to carry full `media.tracks` data, fetching the
     * expanded single-item endpoint if [item] doesn't already have it.
     *
     * `getLibraries()`'s main-list items come from `ApiHandler.getLibraryItems()`
     * (`/api/libraries/:id/items`, no `expanded=1`), unlike `ApiHandler.getItem()`
     * (`/api/items/:id?expanded=1`) which `ChapterListActivity` always fetches before
     * anything there can act on tracks -- so a book that's never been locally touched
     * before (never opened via ChapterListActivity, never downloaded) can reach a
     * main-list row handler with an empty `media.tracks`. Confirmed via two real
     * crash traces (`TrackPositionResolver.resolve()`'s "tracks must not be empty",
     * from the tap-to-play path) and a silent no-op (the swipe-to-download path's
     * `for (track in item.media.tracks)` looping zero times, no error, no download).
     * Returns null if a full item couldn't be obtained (offline, server error, etc.).
     */
    private suspend fun resolveItemWithTracks(item: LibraryItem): LibraryItem? =
        ItemTrackResolver.resolveItemWithTracks(item) { id -> ApiHandler(this).getItem(id) }

    /** Queues every track of [item] for download via [MyDownloadService]. */
    private fun downloadItem(item: LibraryItem) {
        for (track in item.media.tracks) {
            MyDownloadService.sendAddDownload(this, track)
        }
    }

    /** Removes every downloaded track of [item] via [MyDownloadService]. */
    private fun deleteItem(item: LibraryItem) {
        for (track in item.media.tracks) {
            MyDownloadService.sendRemoveDownload(this, track)
        }
    }

    /**
     * Main-list row, wrapped in [SwipeToRevealCard] (§2's "complementary fast path" --
     * Google's recommended Wear OS pattern for reachable-without-opening-the-book
     * secondary/destructive actions: https://developer.android.com/design/ui/wear/guides/m2-5/components/swipe-to-reveal).
     * Primary action is Download when the book isn't downloaded, Delete when it is.
     * Deleting follows the library's own undo pattern (`undoPrimaryAction`) rather than
     * a confirm dialog: committing the delete keeps the row revealed showing "Undo" for
     * [UNDO_WINDOW_MILLIS], reverting (re-queues the download) if tapped in that window,
     * or silently finalizing (nothing further to do -- the delete already ran) once the
     * window elapses. No custom state machine was extracted for this: `RevealState`
     * (from `androidx.wear.compose.material`) already tracks the covered/revealed/
     * undo-visible transitions itself -- `revealState.currentValue`/`animateTo()` are
     * the same primitives the library's own official sample
     * (`SwipeToRevealCardExpandable` in `androidx.wear.compose.integration.demos`) uses
     * for this exact commit-then-auto-finalize-or-undo flow, so there is no bespoke
     * pure logic here worth a JUnit seam -- the only per-row decision this code makes
     * is a one-line "which action is primary" branch on `isDownloaded`, not a
     * multi-step transition function.
     */
    @OptIn(ExperimentalWearMaterialApi::class)
    @Composable
    private fun BookItem(item: LibraryItem) {
        var isDownloaded by remember(item.id) {
            mutableStateOf(item.isDownloaded(this))
        }
        val revealState = rememberRevealState()
        val coroutineScope = rememberCoroutineScope()

        // Auto-finalize a committed delete if "Undo" isn't tapped within the window --
        // the delete already happened when the primary action fired, so finalizing here
        // just closes the row back up; nothing further needs to run.
        LaunchedEffect(revealState.currentValue) {
            if (revealState.currentValue == RevealValue.RightRevealed && !isDownloaded) {
                delay(UNDO_WINDOW_MILLIS)
                if (revealState.currentValue == RevealValue.RightRevealed) {
                    revealState.animateTo(RevealValue.Covered)
                }
            }
        }

        val onPrimaryAction: () -> Unit = {
            if (isDownloaded) {
                deleteItem(item)
                isDownloaded = false
                // Keep the row revealed so the undoPrimaryAction slot (below) shows.
                coroutineScope.launch { revealState.animateTo(RevealValue.RightRevealed) }
            } else {
                // See resolveItemWithTracks() -- a never-locally-touched book's main-list
                // item can have an empty media.tracks, which silently no-ops
                // downloadItem() (an empty for-loop, no error) instead of actually
                // starting anything. Fetch full track data first when needed.
                coroutineScope.launch {
                    val fullItem = resolveItemWithTracks(item)
                    if (fullItem == null) {
                        Toast.makeText(
                            this@BookListActivity,
                            "Couldn't start download -- try opening the book first",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        downloadItem(fullItem)
                    }
                    // No undo affordance for starting a download -- close the row back up
                    // either way.
                    revealState.animateTo(RevealValue.Covered)
                }
            }
        }

        SwipeToRevealCard(
            revealState = revealState,
            onFullSwipe = onPrimaryAction,
            primaryAction = {
                SwipeToRevealPrimaryAction(
                    revealState = revealState,
                    onClick = onPrimaryAction,
                    icon = {
                        Icon(
                            imageVector = if (isDownloaded) Icons.Filled.Delete else Icons.Filled.Download,
                            contentDescription = if (isDownloaded) "Delete" else "Download"
                        )
                    },
                    label = { Text(if (isDownloaded) "Delete" else "Download") }
                )
            },
            undoPrimaryAction = {
                SwipeToRevealUndoAction(
                    revealState = revealState,
                    onClick = {
                        // Undo the delete: re-queue the download and close the row.
                        downloadItem(item)
                        isDownloaded = true
                        coroutineScope.launch { revealState.animateTo(RevealValue.Covered) }
                    },
                    label = { Text("Undo") }
                )
            }
        ) {
            Column(modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (BookTapRouter.shouldJumpStraightToPlayback(PlayerService.currentlyPlayingItemId)) {
                        // Nothing is currently playing: skip the chapter/detail screen and
                        // jump straight into playback for the tapped book.
                        //
                        // `item` here may be a LIST-level LibraryItem -- getLibraryItems()
                        // (ApiHandler.kt) hits /api/libraries/:id/items with no
                        // expanded=1, unlike getItem()'s /api/items/:id?expanded=1, so a
                        // book that's never been locally touched before (never opened via
                        // ChapterListActivity, which always fetches the expanded item on
                        // entry, never downloaded) can reach here with an EMPTY
                        // media.tracks list. PlayerService.setAudiobook() ->
                        // TrackPositionResolver.resolve() requires a non-empty tracks
                        // list and throws IllegalArgumentException otherwise -- confirmed
                        // via a real crash trace. Fetch the expanded item first when
                        // tracks are missing; skip the extra network round-trip when this
                        // item already has full data (e.g. from local DB / a prior visit).
                        // See resolveItemWithTracks() for the shared fetch-if-needed logic.
                        lifecycleScope.launch {
                            val fullItem = resolveItemWithTracks(item)
                            if (fullItem == null) {
                                // Couldn't get track data (offline, server error, etc.) --
                                // fall back to the chapter/detail screen instead of
                                // silently doing nothing or crashing.
                                val intent = Intent(this@BookListActivity, ChapterListActivity::class.java).apply {
                                    putExtra("id", item.id)
                                }
                                startActivity(intent)
                                return@launch
                            }
                            saveAudiobookToDB(fullItem)
                            PlayerService.setAudiobook(this@BookListActivity, fullItem, action = "continue")
                            val intent = Intent(this@BookListActivity, PlayerActivity::class.java)
                            startActivity(intent)
                        }
                    } else {
                        val intent = Intent(this, ChapterListActivity::class.java).apply {
                            putExtra(
                                "id",
                                item.id
                            )
                        }
                        startActivity(intent)
                    }
                }
                .padding(16.dp)) {
                CoverImage(itemId = item.id)
                Text(
                    text = item.title,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(start = 10.dp, end = 10.dp)
                        .fillMaxWidth()
                )
                Text(
                    text = item.author,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(start = 10.dp, end = 10.dp)
                        .fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }

    @Composable
    private fun CoverImage(itemId: String) {
        val coverUrls by viewModel.coverImages.observeAsState()
        LaunchedEffect(itemId) {
            viewModel.getCoverImage(itemId, this@BookListActivity)
        }

        AsyncImage(
            model = coverUrls?.get(itemId) ?: "",
            contentDescription = null,
            placeholder = painterResource(R.drawable.placeholder),
            error = painterResource(R.drawable.placeholder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .height(100.dp) // Adjusted height for better display
        )
    }
}
