package com.mikmy.meldpdf

import android.app.Activity
import android.os.SystemClock
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.PreloadConfiguration
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdPreloader
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * Interstitial ads shown occasionally after a tool finishes, frequency-capped so
 * the app never nags. Every call is wrapped: no ad failure, missing consent or
 * absent Play Services may ever break a tool — if ads don't work, there are
 * simply no ads. Ad ids come from BuildConfig (test ids in debug, real ids from
 * CI secrets in release). Adapted from the Chroma Core / android-app-dev pattern.
 */
class Ads(private val activity: Activity) {

    companion object {
        private const val TAG = "Ads"
        /** The first couple of operations are always ad-free. */
        private const val OPS_BEFORE_FIRST_AD = 2
        /** Then at most one ad per this many operations... */
        private const val OPS_BETWEEN_ADS = 3
        /** ...and never more often than this many seconds. */
        private const val SECONDS_BETWEEN_ADS = 90L
    }

    private val adUnitId = BuildConfig.ADMOB_INTERSTITIAL_ID

    private var initialised = false
    private var ops = 0
    private var opsSinceAd = 0
    private var lastAdAt = 0L
    private var showing = false

    /** Call once from onCreate: gather consent, then initialise and preload. */
    fun start() {
        try {
            val consent = UserMessagingPlatform.getConsentInformation(activity)
            val params = ConsentRequestParameters.Builder().build()
            consent.requestConsentInfoUpdate(
                activity,
                params,
                {
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                        if (formError != null) Log.w(TAG, "consent form: ${formError.message}")
                        if (consent.canRequestAds()) initialise()
                    }
                },
                { requestError ->
                    Log.w(TAG, "consent update: ${requestError.message}")
                    initialise()
                }
            )
        } catch (e: Throwable) {
            Log.w(TAG, "consent setup failed, continuing without ads", e)
        }
    }

    private fun initialise() {
        if (initialised) return
        initialised = true
        Thread {
            try {
                MobileAds.initialize(
                    activity,
                    InitializationConfig.Builder(BuildConfig.ADMOB_APP_ID).build()
                ) { preload() }
            } catch (e: Throwable) {
                Log.w(TAG, "ads init failed, continuing without ads", e)
            }
        }.apply { isDaemon = true; name = "ads-init" }.start()
    }

    private fun preload() {
        try {
            val request = AdRequest.Builder(adUnitId).build()
            InterstitialAdPreloader.start(adUnitId, PreloadConfiguration(request))
        } catch (e: Throwable) {
            Log.w(TAG, "preload failed", e)
        }
    }

    /** Call after a tool successfully produces a result. */
    fun onOperationDone() {
        ops++
        opsSinceAd++
        if (!shouldShow()) return
        try {
            val ad = InterstitialAdPreloader.pollAd(adUnitId) ?: return
            ad.adEventCallback = object : InterstitialAdEventCallback {
                override fun onAdDismissedFullScreenContent() { showing = false }
            }
            showing = true
            lastAdAt = SystemClock.elapsedRealtime()
            opsSinceAd = 0
            ad.show(activity)
        } catch (e: Throwable) {
            showing = false
            Log.w(TAG, "show failed", e)
        }
    }

    private fun shouldShow(): Boolean {
        if (showing) return false
        if (ops <= OPS_BEFORE_FIRST_AD) return false
        if (opsSinceAd < OPS_BETWEEN_ADS) return false
        if (lastAdAt == 0L) return true
        return (SystemClock.elapsedRealtime() - lastAdAt) / 1000L >= SECONDS_BETWEEN_ADS
    }
}
