package com.example.utils

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.Date
import kotlinx.coroutines.flow.MutableStateFlow

object AdsManager : Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {
    private const val TAG = "AdsManager"

    // Real Production Ad Unit IDs
    const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-2424129289119816/4019403537"
    const val APP_OPEN_AD_UNIT_ID = "ca-app-pub-2424129289119816/8012313387"
    const val NATIVE_AD_UNIT_ID = "ca-app-pub-2424129289119816/5390999414"

    private var currentActivity: Activity? = null
    private var applicationContext: Context? = null
    private var isInitialized = false
    
    // UMP Consent
    private var consentInformation: ConsentInformation? = null
    val isConsentGathered = MutableStateFlow(false)

    // Interstitial
    private var interstitialAd: InterstitialAd? = null
    private var isInterstitialLoading = false
    private var interstitialRetryAttempt = 0
    private var actionCount = 0
    private const val ACTIONS_BEFORE_INTERSTITIAL = 4
    
    // App Open
    private var appOpenAd: AppOpenAd? = null
    private var isAppOpenLoading = false
    private var appOpenRetryAttempt = 0
    private var loadTime: Long = 0
    private var isShowingAd = false

    private val mainHandler = Handler(Looper.getMainLooper())

    fun initialize(application: Application, activity: Activity) {
        if (isInitialized) return
        applicationContext = application.applicationContext
        application.registerActivityLifecycleCallbacks(this)
        try {
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error registering ProcessLifecycleOwner observer", e)
        }
        
        requestConsent(activity) {
            try {
                MobileAds.initialize(application) {
                    Log.d(TAG, "Google Mobile Ads SDK initialized successfully")
                    isInitialized = true
                    preloadInterstitial(application)
                    loadAppOpenAd(application)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing MobileAds", e)
            }
        }
    }

    private fun requestConsent(activity: Activity, onConsentGathered: () -> Unit) {
        try {
            val params = ConsentRequestParameters.Builder().build()
            val info = UserMessagingPlatform.getConsentInformation(activity)
            consentInformation = info
            info.requestConsentInfoUpdate(activity, params, {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "Consent form error: ${formError.message}")
                    }
                    if (info.canRequestAds()) {
                        isConsentGathered.value = true
                        onConsentGathered()
                    }
                }
            }, { requestConsentError ->
                Log.w(TAG, "Consent request error: ${requestConsentError.message}")
                if (info.canRequestAds()) {
                    isConsentGathered.value = true
                    onConsentGathered()
                } else {
                    // Fallback to allow ads if consent status allows
                    isConsentGathered.value = true
                    onConsentGathered()
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting consent", e)
            isConsentGathered.value = true
            onConsentGathered()
        }
    }

    fun preloadInterstitial(context: Context) {
        if (interstitialAd != null || isInterstitialLoading) return
        isInterstitialLoading = true
        
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context.applicationContext,
            INTERSTITIAL_AD_UNIT_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    Log.d(TAG, "Interstitial ad loaded")
                    interstitialAd = ad
                    isInterstitialLoading = false
                    interstitialRetryAttempt = 0
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "Interstitial ad failed to load: ${error.message}")
                    interstitialAd = null
                    isInterstitialLoading = false
                    
                    // Exponential backoff retry logic
                    interstitialRetryAttempt++
                    val delayMillis = (1000L * Math.pow(2.0, interstitialRetryAttempt.toDouble()).toLong())
                        .coerceAtMost(60000L)
                    mainHandler.postDelayed({
                        preloadInterstitial(context)
                    }, delayMillis)
                }
            }
        )
    }

    private fun Context.findActivity(): Activity? {
        var context = this
        while (context is ContextWrapper) {
            if (context is Activity) return context
            context = context.baseContext
        }
        return null
    }

    private fun isSensitiveScreen(activity: Activity?): Boolean {
        if (activity == null) return false
        val className = activity.javaClass.simpleName.lowercase()
        return className.contains("login") ||
               className.contains("signup") ||
               className.contains("payment") ||
               className.contains("auth")
    }

    fun showInterstitial(context: Context, onAdDismissed: () -> Unit) {
        val activity = context.findActivity()
        if (activity == null || isShowingAd || isSensitiveScreen(activity)) {
            onAdDismissed()
            return
        }
        
        val ad = interstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Interstitial ad dismissed")
                    isShowingAd = false
                    interstitialAd = null
                    preloadInterstitial(activity.applicationContext)
                    onAdDismissed()
                }

                override fun onAdFailedToShowFullScreenContent(e: AdError) {
                    Log.w(TAG, "Interstitial ad failed to show: ${e.message}")
                    isShowingAd = false
                    interstitialAd = null
                    preloadInterstitial(activity.applicationContext)
                    onAdDismissed()
                }

                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "Interstitial ad showed")
                    isShowingAd = true
                }
            }
            ad.show(activity)
        } else {
            preloadInterstitial(activity.applicationContext)
            onAdDismissed()
        }
    }

    fun registerActionAndShowInterstitialIfNeeded(context: Context, onActionCompleted: () -> Unit) {
        actionCount++
        if (actionCount >= ACTIONS_BEFORE_INTERSTITIAL) {
            actionCount = 0
            showInterstitial(context, onActionCompleted)
        } else {
            onActionCompleted()
        }
    }

    private fun loadAppOpenAd(context: Context) {
        if (isAppOpenLoading || isAppOpenAdAvailable()) return
        isAppOpenLoading = true
        
        val adRequest = AdRequest.Builder().build()
        AppOpenAd.load(
            context.applicationContext,
            APP_OPEN_AD_UNIT_ID,
            adRequest,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    Log.d(TAG, "App open ad loaded")
                    appOpenAd = ad
                    isAppOpenLoading = false
                    loadTime = Date().time
                    appOpenRetryAttempt = 0
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "App open ad failed to load: ${error.message}")
                    appOpenAd = null
                    isAppOpenLoading = false
                    
                    // Exponential backoff retry logic
                    appOpenRetryAttempt++
                    val delayMillis = (1000L * Math.pow(2.0, appOpenRetryAttempt.toDouble()).toLong())
                        .coerceAtMost(60000L)
                    mainHandler.postDelayed({
                        loadAppOpenAd(context)
                    }, delayMillis)
                }
            }
        )
    }

    private fun isAppOpenAdAvailable(): Boolean {
        return appOpenAd != null && (Date().time - loadTime) < 14400000 // 4 hours validity
    }

    fun showAppOpenAdIfAvailable(activity: Activity) {
        if (isSensitiveScreen(activity)) {
            return
        }
        
        if (!isShowingAd && isAppOpenAdAvailable()) {
            val ad = appOpenAd
            ad?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "App open ad dismissed")
                    appOpenAd = null
                    isShowingAd = false
                    loadAppOpenAd(activity.applicationContext)
                }

                override fun onAdFailedToShowFullScreenContent(e: AdError) {
                    Log.w(TAG, "App open ad failed to show: ${e.message}")
                    appOpenAd = null
                    isShowingAd = false
                    loadAppOpenAd(activity.applicationContext)
                }

                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "App open ad showed")
                    isShowingAd = true
                }
            }
            ad?.show(activity)
        } else {
            loadAppOpenAd(activity.applicationContext)
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        currentActivity?.let {
            showAppOpenAdIfAvailable(it)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {
        currentActivity = activity
    }
    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
    }
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity == activity) {
            currentActivity = null
        }
    }
}

