package com.example.pdfmaker
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object AppLock {
    private const val GRACE_MS = 30_000L
    private val AUTH = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    @Volatile private var sessionUnlocked = false
    @Volatile private var lastStop = 0L
    @Volatile private var gateShowing = false
    private fun prefs(c: Context) = c.getSharedPreferences("app_lock", Context.MODE_PRIVATE)
    fun isEnabled(c: Context) = prefs(c).getBoolean("enabled", false)
    fun setEnabled(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean("enabled", on).remove("files").apply()
        sessionUnlocked = on; if (!on) gateShowing = false
    }
    fun canAuthenticate(c: Context) = BiometricManager.from(c).canAuthenticate(AUTH) == BiometricManager.BIOMETRIC_SUCCESS
    val lifecycleCallbacks = object : android.app.Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(a: Activity) = maybeGate(a)
        override fun onActivityStopped(a: Activity) { lastStop = System.currentTimeMillis() }
        override fun onActivityCreated(a: Activity, b: Bundle?) {}
        override fun onActivityStarted(a: Activity) {}
        override fun onActivityPaused(a: Activity) {}
        override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
        override fun onActivityDestroyed(a: Activity) {}
    }
    private fun maybeGate(a: Activity) {
        if (a is LockActivity) return
        if (gateShowing || sessionUnlocked || !isEnabled(a)) return
        val since = if (lastStop == 0L) Long.MAX_VALUE else System.currentTimeMillis() - lastStop
        if (since < GRACE_MS) return
        gateShowing = true; a.startActivity(Intent(a, LockActivity::class.java))
    }
    fun promptVerify(activity: FragmentActivity, title: String, onOk: () -> Unit, onFail: () -> Unit = {}) {
        if (!canAuthenticate(activity)) { onFail(); return }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) = onOk()
                override fun onAuthenticationError(code: Int, err: CharSequence) = onFail()
            })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(title)
            .setSubtitle("Use your fingerprint, face, or screen lock").setAllowedAuthenticators(AUTH).build())
    }
    internal fun markUnlocked() { sessionUnlocked = true; gateShowing = false }
}
