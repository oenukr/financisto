// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.utils.BiometricPromptUtils
import ru.orangesoftware.financisto.utils.MyPreferences
import ru.orangesoftware.financisto.utils.PinProtection
import ru.orangesoftware.financisto.view.PinView

private const val SUCCESS: String = "PIN_SUCCESS"

class PinActivity : AppCompatActivity(), PinView.PinListener {


    private lateinit var biometricPrompt: BiometricPrompt

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(MyPreferences.switchLocale(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pin: String? = MyPreferences.getPin(this)
        if (pin == null) {
            onSuccess(null)
        } else if (BiometricPromptUtils.canUseBiometrics(applicationContext) && MyPreferences.isPinLockUseFingerprint(this)) {
            showBiometricPrompt()
        } else {
            usePinLock()
        }
    }

    private fun usePinLock() {
        val pin: String? = MyPreferences.getPin(this)
        val v = PinView(this, this, pin, R.layout.lock)
        setContentView(v.view)
    }

    private fun decryptServerTokenFromStorage(authResult: BiometricPrompt.AuthenticationResult) {
        if (authResult.authenticationType == BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC) {
            onSuccess(null)
        }
    }

    private fun showBiometricPrompt() {
        biometricPrompt =
            BiometricPromptUtils.createBiometricPrompt(
                this,
                ::decryptServerTokenFromStorage
            )
        val promptInfo = BiometricPromptUtils.createPromptInfo(this)
        biometricPrompt.authenticate(promptInfo)
    }

    override fun onConfirm(pinBase64: String) {}

    override fun onSuccess(pinBase64: String?) {
        PinProtection.pinUnlock(this)
        val data = Intent()
        data.putExtra(SUCCESS, true)
        setResult(RESULT_OK, data)
        finish()
    }

    override fun moveTaskToBack(nonRoot: Boolean): Boolean {
        return super.moveTaskToBack(true)
    }
}
