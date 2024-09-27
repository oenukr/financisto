package ru.orangesoftware.financisto.utils

import android.content.Context
import android.content.Intent

import ru.orangesoftware.financisto.activity.PinActivity
import java.util.concurrent.TimeUnit

private const val MIN_DELTA_TIME_MS: Long = 3000

private interface LockState {
    fun lock(context: Context): LockState
    fun unlock(context: Context): LockState
}

object PinProtection {

    private val LOCKED: LockState = object : LockState {
        override fun lock(context: Context): LockState {
            return this
        }

        override fun unlock(context: Context): LockState {
            if (MyPreferences.isPinProtected(context)) {
                askForPin(context)
                return this
            }
            return UNLOCKED
        }
    }

    private val UNLOCKED: LockState = object : LockState {
        private var lockTime: Long = 0L

        override fun lock(context: Context): LockState {
            lockTime = System.currentTimeMillis()
            return this
        }

        override fun unlock(context: Context): LockState {
            val lockWaitTime: Long = MyPreferences.getLockTimeSeconds(context).toLong()
            if (lockWaitTime > 0) {
                val curTime: Long = System.currentTimeMillis()
                val lockTimeMs: Long = MIN_DELTA_TIME_MS.coerceAtLeast(
                    TimeUnit.MILLISECONDS.convert(
                        lockWaitTime,
                        TimeUnit.SECONDS
                    )
                )
                val deltaTimeMs: Long = curTime - lockTime
                if (deltaTimeMs > lockTimeMs) {
                    askForPin(context)
                    return LOCKED
                }
            }
            return this
        }
    }

    private var currentState: LockState = LOCKED

    private fun askForPin(context: Context) {
        val intent = Intent(context, PinActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        context.startActivity(intent)
    }

    @JvmStatic
    fun lock(context: Context) {
        currentState = currentState.lock(context)
    }

    @JvmStatic
    fun unlock(context: Context) {
        currentState = currentState.unlock(context)
    }

    @JvmStatic
    fun immediateLock(context: Context) {
        currentState = LOCKED
    }

    @JvmStatic
    fun pinUnlock(context: Context) {
        currentState = UNLOCKED
        // little hack to reset lockTime in the state
        currentState.lock(context)
    }

    @JvmStatic
    fun isUnlocked(): Boolean = currentState == UNLOCKED
}
