/*******************************************************************************
 * Copyright (c) 2010 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * https://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 *
 * Contributors:
 *     Denis Solonenko - initial API and implementation
 ******************************************************************************/
package ru.orangesoftware.financisto.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast

import androidx.core.content.ContextCompat

import com.mtramin.rxfingerprint.RxFingerprint
import com.mtramin.rxfingerprint.data.FingerprintResult

import hu.akarnokd.rxjava3.bridge.RxJavaBridge
import io.reactivex.rxjava3.disposables.Disposable
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.utils.MyPreferences
import ru.orangesoftware.financisto.utils.PinProtection
import ru.orangesoftware.financisto.view.PinView

private const val SUCCESS: String = "PIN_SUCCESS"

class PinActivity : Activity(), PinView.PinListener {


    private lateinit var disposable: Disposable

    private val handler: Handler = Handler()

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(MyPreferences.switchLocale(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pin: String? = MyPreferences.getPin(this)
        if (pin == null) {
            onSuccess(null)
        } else if (RxFingerprint.isAvailable(this) && MyPreferences.isPinLockUseFingerprint(this)) {
            setContentView(R.layout.lock_fingerprint)
            askForFingerprint()
        } else {
            usePinLock()
        }
    }

    private fun usePinLock() {
        val pin: String? = MyPreferences.getPin(this)
        val v: PinView = PinView(this, this, pin, R.layout.lock)
        setContentView(v.view)
    }

    private fun askForFingerprint() {
        val usePinButton: View = findViewById(R.id.use_pin)
        if (MyPreferences.isUseFingerprintFallbackToPinEnabled(this)) {
            usePinButton.setOnClickListener {
                disposeFingerprintListener()
                usePinLock()
            }
        } else {
            usePinButton.visibility = View.GONE
        }
        disposable = RxJavaBridge.toV3Observable(RxFingerprint.authenticate(this)).subscribe(
                { result ->
                when (result.result) {
                        FingerprintResult.AUTHENTICATED -> {
                            setFingerprintStatus(
                                R.string.fingerprint_auth_success,
                                R.drawable.ic_check_circle_black_48dp,
                                R.color.material_teal
                            )

                            handler.postDelayed(
                                { onSuccess(null) },
                                200
                            )
                        }
                    FingerprintResult.FAILED ->
                        setFingerprintStatus(R.string.fingerprint_auth_failed, R.drawable.ic_error_black_48dp, R.color.material_orange)
                    FingerprintResult.HELP ->
                            Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                    }
                },
                {  throwable ->
                    setFingerprintStatus(R.string.fingerprint_error, R.drawable.ic_error_black_48dp, R.color.holo_red_dark)
                    Toast.makeText(this, throwable.message, Toast.LENGTH_LONG).show()
                }
        )
    }

    private fun setFingerprintStatus(messageResId: Int, iconResId: Int, colorResId: Int) {
        val status: TextView = findViewById(R.id.fingerprint_status)
        val icon: ImageView = findViewById(R.id.fingerprint_icon)
        val color: Int = ContextCompat.getColor(this, colorResId)
        status.setText(messageResId)
        status.setTextColor(color)
        icon.setImageResource(iconResId)
        icon.setColorFilter(color)
    }

    override fun onConfirm(pinBase64: String) { }

    override fun onSuccess(pinBase64: String?) {
        disposeFingerprintListener()
        PinProtection.pinUnlock(this)
        val data: Intent = Intent()
        data.putExtra(SUCCESS, true)
        setResult(RESULT_OK, data)
        finish()
    }

    private fun disposeFingerprintListener() {
        disposable.dispose()
    }

    @Deprecated("Deprecated in Java", ReplaceWith("moveTaskToBack(true)"))
    override fun onBackPressed() {
        moveTaskToBack(true)
    }

}
