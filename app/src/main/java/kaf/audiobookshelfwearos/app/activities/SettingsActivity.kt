package kaf.audiobookshelfwearos.app.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import kaf.audiobookshelfwearos.app.theme.AudiobookshelfWearOSTheme
import kaf.audiobookshelfwearos.app.userdata.UserDataManager

class SettingsActivity : ComponentActivity() {
    private lateinit var userDataManager: UserDataManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        userDataManager = UserDataManager(this)

        setContent {
            AudiobookshelfWearOSTheme {
                SettingsScreen()
            }
        }
    }

    @Composable
    private fun SettingsScreen() {
        val listState = rememberScalingLazyListState()
        var smartDeleteEnabled by remember { mutableStateOf(userDataManager.smartDeleteEnabled) }
        var maxDownloads by remember { mutableStateOf(userDataManager.smartDeleteMaxDownloads) }
        var maxBytesGb by remember { mutableStateOf((userDataManager.smartDeleteMaxBytes / BYTES_PER_GB).toInt()) }
        var jumpBackwardSeconds by remember { mutableStateOf(userDataManager.jumpBackwardSeconds) }
        var jumpForwardSeconds by remember { mutableStateOf(userDataManager.jumpForwardSeconds) }
        var bezelModeIndex by remember {
            mutableStateOf(BEZEL_MODES.indexOf(userDataManager.bezelMode).let { if (it < 0) 0 else it })
        }

        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.title2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }

            item {
                Text(
                    text = "Jump Backward (seconds)",
                    style = MaterialTheme.typography.body1,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    // Minus button
                    Button(
                        onClick = {
                            if (jumpBackwardSeconds > JUMP_SECONDS_MIN) {
                                jumpBackwardSeconds -= JUMP_SECONDS_STEP
                                userDataManager.jumpBackwardSeconds = jumpBackwardSeconds
                            }
                        },
                        modifier = Modifier.size(40.dp),
                        enabled = jumpBackwardSeconds > JUMP_SECONDS_MIN
                    ) {
                        Text("-")
                    }

                    // Current value
                    Text(
                        text = "$jumpBackwardSeconds",
                        style = MaterialTheme.typography.title1,
                        modifier = Modifier.width(40.dp),
                        textAlign = TextAlign.Center
                    )

                    // Plus button
                    Button(
                        onClick = {
                            if (jumpBackwardSeconds < JUMP_SECONDS_MAX) {
                                jumpBackwardSeconds += JUMP_SECONDS_STEP
                                userDataManager.jumpBackwardSeconds = jumpBackwardSeconds
                            }
                        },
                        modifier = Modifier.size(40.dp),
                        enabled = jumpBackwardSeconds < JUMP_SECONDS_MAX
                    ) {
                        Text("+")
                    }
                }
            }

            item {
                Text(
                    text = "Jump Forward (seconds)",
                    style = MaterialTheme.typography.body1,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    // Minus button
                    Button(
                        onClick = {
                            if (jumpForwardSeconds > JUMP_SECONDS_MIN) {
                                jumpForwardSeconds -= JUMP_SECONDS_STEP
                                userDataManager.jumpForwardSeconds = jumpForwardSeconds
                            }
                        },
                        modifier = Modifier.size(40.dp),
                        enabled = jumpForwardSeconds > JUMP_SECONDS_MIN
                    ) {
                        Text("-")
                    }

                    // Current value
                    Text(
                        text = "$jumpForwardSeconds",
                        style = MaterialTheme.typography.title1,
                        modifier = Modifier.width(40.dp),
                        textAlign = TextAlign.Center
                    )

                    // Plus button
                    Button(
                        onClick = {
                            if (jumpForwardSeconds < JUMP_SECONDS_MAX) {
                                jumpForwardSeconds += JUMP_SECONDS_STEP
                                userDataManager.jumpForwardSeconds = jumpForwardSeconds
                            }
                        },
                        modifier = Modifier.size(40.dp),
                        enabled = jumpForwardSeconds < JUMP_SECONDS_MAX
                    ) {
                        Text("+")
                    }
                }
            }

            item {
                Text(
                    text = "Bezel Input",
                    style = MaterialTheme.typography.body1,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                Button(
                    onClick = {
                        bezelModeIndex = (bezelModeIndex + 1) % BEZEL_MODES.size
                        userDataManager.bezelMode = BEZEL_MODES[bezelModeIndex]
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text(BEZEL_MODES[bezelModeIndex])
                }
            }

            item {
                ToggleChip(
                    checked = smartDeleteEnabled,
                    onCheckedChange = { enabled ->
                        smartDeleteEnabled = enabled
                        userDataManager.smartDeleteEnabled = enabled
                    },
                    label = {
                        Text("Smart Delete")
                    },
                    toggleControl = {
                        Switch(
                            checked = smartDeleteEnabled,
                            onCheckedChange = null
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (smartDeleteEnabled) {
                item {
                    Text(
                        text = "Old audiobooks will be deleted once this amount is reached:",
                        style = MaterialTheme.typography.body2,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                item {
                    Text(
                        text = "Maximum Downloads",
                        style = MaterialTheme.typography.body1,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        // Minus button
                        Button(
                            onClick = {
                                if (maxDownloads > 1) {
                                    maxDownloads--
                                    userDataManager.smartDeleteMaxDownloads = maxDownloads
                                }
                            },
                            modifier = Modifier.size(40.dp),
                            enabled = maxDownloads > 1
                        ) {
                            Text("-")
                        }

                        // Current value
                        Text(
                            text = "$maxDownloads",
                            style = MaterialTheme.typography.title1,
                            modifier = Modifier.width(40.dp),
                            textAlign = TextAlign.Center
                        )

                        // Plus button
                        Button(
                            onClick = {
                                if (maxDownloads < 20) {
                                    maxDownloads++
                                    userDataManager.smartDeleteMaxDownloads = maxDownloads
                                }
                            },
                            modifier = Modifier.size(40.dp),
                            enabled = maxDownloads < 20
                        ) {
                            Text("+")
                        }
                    }
                }

                item {
                    Text(
                        text = "Maximum Storage (GB)",
                        style = MaterialTheme.typography.body1,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        // Minus button
                        Button(
                            onClick = {
                                if (maxBytesGb > MAX_BYTES_GB_MIN) {
                                    maxBytesGb--
                                    userDataManager.smartDeleteMaxBytes = maxBytesGb.toLong() * BYTES_PER_GB
                                }
                            },
                            modifier = Modifier.size(40.dp),
                            enabled = maxBytesGb > MAX_BYTES_GB_MIN
                        ) {
                            Text("-")
                        }

                        // Current value
                        Text(
                            text = "$maxBytesGb",
                            style = MaterialTheme.typography.title1,
                            modifier = Modifier.width(40.dp),
                            textAlign = TextAlign.Center
                        )

                        // Plus button
                        Button(
                            onClick = {
                                if (maxBytesGb < MAX_BYTES_GB_MAX) {
                                    maxBytesGb++
                                    userDataManager.smartDeleteMaxBytes = maxBytesGb.toLong() * BYTES_PER_GB
                                }
                            },
                            modifier = Modifier.size(40.dp),
                            enabled = maxBytesGb < MAX_BYTES_GB_MAX
                        ) {
                            Text("+")
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val JUMP_SECONDS_MIN = 5
        const val JUMP_SECONDS_MAX = 60
        const val JUMP_SECONDS_STEP = 5
        const val BYTES_PER_GB = 1_000_000_000L
        const val MAX_BYTES_GB_MIN = 1
        const val MAX_BYTES_GB_MAX = 50
        val BEZEL_MODES = listOf("Scrub", "Volume", "Off")
    }
}
