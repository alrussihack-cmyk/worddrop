package com.wordpop.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {

    private var overlayGranted by mutableStateOf(false)
    private var exactAlarmGranted by mutableStateOf(false)
    private var lastReady: Boolean? = null
    private var wordFromIntent by mutableStateOf<Int?>(null)

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT
            )
        )
        handleIntent(intent)
        setContent {
            WordPopTheme {
                var showStartup by remember {
                    mutableStateOf(savedInstanceState == null && wordFromIntent == null)
                }
                if (showStartup) {
                    WordPopStartup(onFinished = { showStartup = false })
                } else {
                    WordPopApp(
                        overlayGranted = overlayGranted,
                        exactAlarmGranted = exactAlarmGranted,
                        openWordId = wordFromIntent,
                        onConsumeOpenWord = { wordFromIntent = null },
                        onRequestOverlay = {
                            startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName")
                                )
                            )
                        },
                        onRequestNotif = {
                            if (Build.VERSION.SDK_INT >= 33) {
                                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onRequestExactAlarm = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                val request = Intent(
                                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    Uri.parse("package:$packageName")
                                )
                                try {
                                    startActivity(request)
                                } catch (e: Exception) {
                                    startActivity(
                                        Intent(
                                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            Uri.parse("package:$packageName")
                                        )
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        overlayGranted = Settings.canDrawOverlays(this)
        exactAlarmGranted = Scheduler.hasExactAlarmAccess(this)
        val ready = overlayGranted && exactAlarmGranted
        if (ready && lastReady != true && SettingsStore(this).enabled) {
            sendToService(this, OverlayService.ACTION_START)
        }
        lastReady = ready
    }

    private fun handleIntent(i: Intent?) {
        val id = i?.getIntExtra(OverlayService.EXTRA_WORD_ID, -1) ?: -1
        if (id >= 0) {
            wordFromIntent = id
            i?.removeExtra(OverlayService.EXTRA_WORD_ID)
        }
    }
}
