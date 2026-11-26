// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import org.koin.androidx.viewmodel.ext.android.viewModel
import ru.orangesoftware.financisto.activity.RequestPermissionActivity.Constants.REQUESTED_PERMISSION
import ru.orangesoftware.financisto.ui.PermissionsPreferenceContent
import ru.orangesoftware.financisto.ui.PermissionsViewModel
import ru.orangesoftware.financisto.utils.MyPreferences

class RequestPermissionActivity : ComponentActivity() {

    private val viewModel: PermissionsViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val uiState by viewModel.uiState
            PermissionsPreferenceContent(
                uiState = uiState,
                onBackupFolderSelected = viewModel::onBackupFolderSelected,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions()
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(MyPreferences.switchLocale(newBase))
    }

    object Constants {
        const val REQUESTED_PERMISSION = "requestedPermission"
    }

    companion object {

        @JvmStatic
        fun intent(context: Context) = IntentBuilder(context)
    }

    class IntentBuilder(private val context: Context) {
        val intent: Intent = Intent(context, RequestPermissionActivity::class.java)

        fun requestedPermission(requestedPermission: String): IntentBuilder {
            intent.putExtra(REQUESTED_PERMISSION, requestedPermission)
            return this
        }

        fun start() {
            context.startActivity(intent)
        }
    }
}
