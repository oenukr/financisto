package ru.orangesoftware.financisto.dialog

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.webkit.WebView

import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.utils.Utils

object WebViewDialog {
    @JvmStatic
    fun checkVersionAndShowWhatsNewIfNeeded(activity: Activity): String {
        try {
            val info: PackageInfo = Utils.getPackageInfo(activity)
            val preferences: SharedPreferences = activity.getPreferences(0)
            val newVersionCode = info.longVersionCode
            val oldVersionCode = preferences.getInt("longVersionCode", -1)
            if (newVersionCode > oldVersionCode) {
                preferences.edit().putLong("longVersionCode", newVersionCode).apply()
                showWhatsNew(activity)
            }
            return "v. ${info.versionName}"
        } catch (ex: Exception) {
            return "Free"
        }
    }

    @JvmStatic
    fun showWhatsNew(context: Context) {
        showHTMDialog(context, "whatsnew.htm", R.string.whats_new)
    }

    @JvmStatic
    fun showHTMDialog(context: Context, fileName: String, dialogTitleResId: Int) {
        val webView = WebView(context)
        webView.loadUrl("file:///android_asset/$fileName")
        AlertDialog.Builder(context)
            .setView(webView)
            .setTitle(dialogTitleResId)
            .setPositiveButton(R.string.ok, null)
            .show()
    }
}
