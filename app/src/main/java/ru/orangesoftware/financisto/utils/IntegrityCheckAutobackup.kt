// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.utils

import android.content.Context
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.datetime.DateUtils
import ru.orangesoftware.financisto.utils.IntegrityCheck.Level
import ru.orangesoftware.financisto.utils.IntegrityCheck.Result
import java.time.Clock
import java.util.Date

class IntegrityCheckAutobackup @JvmOverloads constructor(
    private val threshold: Long,
    private val clock: Clock = Clock.systemDefaultZone()
) : IntegrityCheck {

    override fun check(context: Context): Result {
        if (MyPreferences.isAutoBackupEnabled(context)) {
            if (MyPreferences.isAutoBackupWarningEnabled(context)) {
                val status: MyPreferences.AutobackupStatus =
                    MyPreferences.getAutobackupStatus(context)
                if (status.notify) {
                    MyPreferences.notifyAutobackupSucceeded(context)
                    return Result(
                        Level.ERROR,
                        context.getString(
                            R.string.autobackup_failed_message,
                            DateUtils.getTimeFormat(context).format(Date(status.timestamp)),
                            status.errorMessage
                        )
                    )
                }
            }
        } else {
            if (MyPreferences.isAutoBackupReminderEnabled(context)) {
                val lastCheck: Long = MyPreferences.getLastAutobackupCheck(context)
                val now = clock.millis()
                if (lastCheck == 0L) {
                    MyPreferences.updateLastAutobackupCheck(context, now)
                } else {
                    val delta: Long = now - lastCheck
                    if (delta > threshold) {
                        MyPreferences.updateLastAutobackupCheck(context, now)
                        return Result(
                            Level.INFO,
                            context.getString(R.string.auto_backup_is_not_enabled)
                        )
                    }
                }
            }
        }
        return Result.OK
    }

}
