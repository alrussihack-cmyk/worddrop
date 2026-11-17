package com.wordpop.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import com.google.android.gms.ads.MobileAds
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
    private var adsAllowed by mutableStateOf(false)
    private var privacyOptionsRequired by mutableStateOf(false)
    private var mobileAdsInitializationStarted = false
    private var ageGroup by mutableStateOf<WordPopAgeGroup?>(null)
    private lateinit var adsConsentManager: AdsConsentManager

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
        ageGroup = WordPopAgeProfile.get(this)
        adsConsentManager = AdsConsentManager(this)
        setContent {
            WordPopTheme {
                var showStartup by remember {
                    mutableStateOf(savedInstanceState == null && wordFromIntent == null)
                }
                val selectedAge = ageGroup
                if (selectedAge == null) {
                    WordPopAgeGate { selected ->
                        WordPopAgeProfile.save(this, selected)
                        ageGroup = selected
                        (application as WordPopApplication).startAgeAppropriateServices(selected)
                        configureAdsForAge(selected)
                        adsConsentManager.gatherConsent(this) { canRequestAds, optionsRequired ->
                            runOnUiThread {
                                privacyOptionsRequired = optionsRequired
                                if (canRequestAds) initializeMobileAds()
                            }
                        }
                    }
                } else if (showStartup) {
                    WordPopStartup(onFinished = { showStartup = false })
                } else {
                    WordPopApp(
                        overlayGranted = overlayGranted,
                        exactAlarmGranted = exactAlarmGranted,
                        adsAllowed = adsAllowed,
                        privacyOptionsRequired = privacyOptionsRequired,
                        onShowPrivacyOptions = {
                            adsAllowed = false
                            adsConsentManager.showPrivacyOptions(this) { canRequestAds, optionsRequired ->
                                runOnUiThread {
                                    privacyOptionsRequired = optionsRequired
                                    if (canRequestAds) initializeMobileAds()
                                }
                            }
                        },
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
        ageGroup?.let { selected ->
            (application as WordPopApplication).startAgeAppropriateServices(selected)
            configureAdsForAge(selected)
            adsConsentManager.gatherConsent(this) { canRequestAds, optionsRequired ->
                runOnUiThread {
                    privacyOptionsRequired = optionsRequired
                    if (canRequestAds) initializeMobileAds()
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

    private fun configureAdsForAge(group: WordPopAgeGroup) {
        val builder = com.google.android.gms.ads.RequestConfiguration.Builder()
            .setMaxAdContentRating(com.google.android.gms.ads.RequestConfiguration.MAX_AD_CONTENT_RATING_G)
        // SDK 23.6.0 predates the newer CHILD/TEEN age-treatment API.
        // Treat child and teen requests conservatively as child-directed; never send an
        // unverified adult signal for a minor. Adults remain non-child-directed.
        if (group != WordPopAgeGroup.ADULT) {
            builder
                .setTagForChildDirectedTreatment(
                    com.google.android.gms.ads.RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE
                )
                .setTagForUnderAgeOfConsent(
                    com.google.android.gms.ads.RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_TRUE
                )
        }
        MobileAds.setRequestConfiguration(builder.build())
    }

    private fun initializeMobileAds() {
        if (mobileAdsInitializationStarted) {
            adsAllowed = true
            return
        }
        mobileAdsInitializationStarted = true
        MobileAds.initialize(this) {
            runOnUiThread { adsAllowed = true }
        }
    }

    private fun handleIntent(i: Intent?) {
        if (i?.hasExtra(OverlayService.EXTRA_WORD_ID) == true) {
            // Cloud words use negative IDs to stay separate from local words.
            wordFromIntent = i.getIntExtra(OverlayService.EXTRA_WORD_ID, 0)
            i.removeExtra(OverlayService.EXTRA_WORD_ID)
        }
    }
}
