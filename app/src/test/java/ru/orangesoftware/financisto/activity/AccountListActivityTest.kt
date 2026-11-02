// SPDX-License-Identifier: GPL-2.0-only
package ru.orangesoftware.financisto.activity

import android.view.View
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import org.greenrobot.eventbus.Subscribe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.app.DependenciesHolder
import ru.orangesoftware.financisto.bus.SwitchToMenuTabEvent
import ru.orangesoftware.financisto.db.AbstractDbTest
import ru.orangesoftware.financisto.model.Account
import ru.orangesoftware.financisto.model.Currency
import ru.orangesoftware.financisto.test.AccountBuilder
import ru.orangesoftware.financisto.test.CurrencyBuilder
import ru.orangesoftware.financisto.test.DateTime
import ru.orangesoftware.financisto.test.TransactionBuilder
import ru.orangesoftware.financisto.utils.CurrencyCache

@Suppress("DEPRECATION")
class AccountListActivityTest : AbstractDbTest() {

    private lateinit var homeCurrency: Currency
    private lateinit var account1: Account
    private lateinit var account2: Account

    @Before
    override fun setUp() {
        super.setUp()
        homeCurrency = CurrencyBuilder.withDb(db)
            .name("USD")
            .title("Dollar")
            .symbol("$")
            .separators("','", "'.'")
            .makeDefault()
            .create()

        account1 = AccountBuilder.createDefault(db, homeCurrency)
        account2 = AccountBuilder.createDefault(db, homeCurrency)
        CurrencyCache.initialize(db)
    }

    @Test
    fun activity_loadsAccountsAndCalculatesTotals() {
        TransactionBuilder.withDb(db)
            .account(account1)
            .dateTime(DateTime.date(2026, 1, 10).atNoon())
            .amount(1200L)
            .create()

        val controller = Robolectric.buildActivity(AccountListActivity::class.java).setup()
        val activity = controller.get()
        ShadowLooper.idleMainLooper()

        val listView = activity.findViewById<ListView>(android.R.id.list)
        assertNotNull(listView)
        assertEquals(2, listView.count)

        val totalView = activity.findViewById<TextView>(R.id.total)
        assertNotNull(totalView)
        val text = totalView.text.toString()
        assertTrue("Expected total text to contain 12.00, got: $text", text.contains("12.00"))

        controller.destroy()
    }

    @Test
    fun totalView_click_launchesAccountListTotalsDetailsActivity() {
        val controller = Robolectric.buildActivity(AccountListActivity::class.java).setup()
        val activity = controller.get()
        ShadowLooper.idleMainLooper()

        val totalView = activity.findViewById<TextView>(R.id.total)
        totalView.performClick()

        val shadowActivity = shadowOf(activity)
        val startedIntent = shadowActivity.nextStartedActivity
        assertNotNull(startedIntent)
        assertEquals(AccountListTotalsDetailsActivity::class.java.name, startedIntent.component?.className)

        controller.destroy()
    }

    @Test
    fun recreateCursor_recalculatesTotals() {
        val controller = Robolectric.buildActivity(AccountListActivity::class.java).setup()
        val activity = controller.get()
        ShadowLooper.idleMainLooper()

        TransactionBuilder.withDb(db)
            .account(account1)
            .dateTime(DateTime.date(2026, 1, 10).atNoon())
            .amount(4500L)
            .create()

        activity.recreateCursor()
        ShadowLooper.idleMainLooper()

        val totalView = activity.findViewById<TextView>(R.id.total)
        val text = totalView.text.toString()
        assertTrue("Expected recalculated total text to contain 45.00, got: $text", text.contains("45.00"))

        controller.destroy()
    }

    @Test
    fun menuButton_whenEnabled_handlesPopupAndDispatchesMenuTabEvent() {
        android.preference.PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean("show_menu_button_on_accounts_screen", true)
            .commit()

        var eventReceived = false
        val bus = DependenciesHolder().greenRobotBus
        val subscriber = object {
            @Subscribe
            fun onSwitchToMenu(ignored: SwitchToMenuTabEvent) {
                eventReceived = true
            }
        }
        bus.register(subscriber)

        val controller = Robolectric.buildActivity(AccountListActivity::class.java).setup()
        val activity = controller.get()
        ShadowLooper.idleMainLooper()

        try {
            val bMenu = activity.findViewById<ImageButton>(R.id.bMenu)
            assertEquals(View.VISIBLE, bMenu.visibility)

            val method = AccountListActivity::class.java.getDeclaredMethod(
                "handlePopupMenu",
                Int::class.javaPrimitiveType,
            )
            method.isAccessible = true
            method.invoke(activity, R.id.go_to_menu)

            assertTrue("Expected SwitchToMenuTabEvent to be dispatched", eventReceived)
        } finally {
            bus.unregister(subscriber)
            controller.destroy()
        }
    }
}
