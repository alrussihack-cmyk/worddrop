package com.wordpop.app

import android.app.Activity
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * Centralizes Google's UMP flow. Ads may only be requested when UMP says they are allowed.
 * Consent state is refreshed on each app launch; UMP itself persists the user's choices.
 */
class AdsConsentManager(activity: Activity) {
    private val consentInformation = UserMessagingPlatform.getConsentInformation(activity)

    val isPrivacyOptionsRequired: Boolean
        get() = consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun gatherConsent(
        activity: Activity,
        onComplete: (canRequestAds: Boolean, privacyOptionsRequired: Boolean) -> Unit
    ) {
        val parameters = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            parameters,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    onComplete(consentInformation.canRequestAds(), isPrivacyOptionsRequired)
                }
            },
            {
                // If the network is unavailable, UMP may still have a valid decision from
                // the previous session. Never invent a consent result or bypass UMP.
                onComplete(consentInformation.canRequestAds(), isPrivacyOptionsRequired)
            }
        )
    }

    fun showPrivacyOptions(
        activity: Activity,
        onComplete: (canRequestAds: Boolean, privacyOptionsRequired: Boolean) -> Unit
    ) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) {
            onComplete(consentInformation.canRequestAds(), isPrivacyOptionsRequired)
        }
    }
}
