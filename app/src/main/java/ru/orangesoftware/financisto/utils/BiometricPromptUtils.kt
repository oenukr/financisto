package ru.orangesoftware.financisto.utils

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.app.DependenciesHolder

object BiometricPromptUtils {
    private val logger = DependenciesHolder().logger

    fun createBiometricPrompt(
        activity: AppCompatActivity,
        processSuccess: (BiometricPrompt.AuthenticationResult) -> Unit,
    ): BiometricPrompt {
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {

            override fun onAuthenticationError(errCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errCode, errString)
                logger.d("errCode is $errCode and errString is: $errString")
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                logger.d("User biometric rejected.")
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                logger.d("Authentication was successful")
                processSuccess(result)
            }
        }
        return BiometricPrompt(activity, executor, callback)
    }

    fun createPromptInfo(activity: AppCompatActivity): BiometricPrompt.PromptInfo =
        BiometricPrompt.PromptInfo.Builder().apply {
            setTitle(activity.getString(R.string.fingerprint_hint))
            setSubtitle(activity.getString(R.string.fingerprint_description))
            setDescription(activity.getString(R.string.fingerprint_description))
            setConfirmationRequired(false)
            setNegativeButtonText(activity.getString(R.string.use_pin))
        }.build()

    fun canUseBiometrics(context: Context) =
        when (BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> true
            else -> false
        }

    fun reasonFingerprintUnavailable(context: Context) =
        when (BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> "Success"
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
                -> context.getString(R.string.fingerprint_unavailable_hardware)
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> context.getString(R.string.fingerprint_unavailable_enrolled_fingerprints)
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> context.getString(R.string.fingerprint_security_update_required)
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED,
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN,
                -> context.getString(R.string.fingerprint_unavailable_unknown)
            else -> context.getString(R.string.fingerprint_unavailable_unknown)
        }
}
