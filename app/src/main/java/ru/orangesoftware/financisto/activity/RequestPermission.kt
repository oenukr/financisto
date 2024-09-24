package ru.orangesoftware.financisto.activity

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import ru.orangesoftware.financisto.app.DependenciesHolder
import ru.orangesoftware.financisto.persistance.BackupPreferences
import java.util.Objects

object RequestPermission {
    private val dependencies = DependenciesHolder()

    @JvmStatic
    fun isRequestingPermission(context: Context, permission: String): Boolean {
        if (!checkPermission(context, permission)) {
            RequestPermissionActivity.intent(context).requestedPermission(permission).start()
            return true
        }
        return false
    }

    @JvmStatic
    fun checkPermission(ctx: Context, permission: String): Boolean {
        if (Objects.equals(permission, Manifest.permission.WRITE_EXTERNAL_STORAGE))
            return checkWritablePath(ctx)
        return ContextCompat.checkSelfPermission(
            ctx,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    @JvmStatic
    fun isRequestingPermissions(context: Context, vararg permissions: String): Boolean {
        permissions.forEach { permission ->
            if (isRequestingPermission(context, permission)) return true
        }
        return false
    }

    private fun checkWritablePath(ctx: Context): Boolean {
        val backupPreferences: BackupPreferences =
            dependencies.preferencesStore.backupPreferencesRx.blockingFirst()
        val isWritable: Boolean = ctx.contentResolver.persistedUriPermissions.stream()
            .anyMatch { persistedUriPermission ->
                persistedUriPermission.uri.equals(backupPreferences.folder)
            }
        return isWritable
    }
}
