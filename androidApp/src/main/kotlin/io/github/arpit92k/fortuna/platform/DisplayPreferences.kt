package io.github.arpit92k.fortuna.platform

import android.content.Context

/**
 * How the user likes the screens shown, kept on the phone outside the
 * encrypted data: it is not secret, and changing it is not a change to the
 * data, so it never makes a backup out of date.
 */
interface DisplayPreferences {
    var amountsHidden: Boolean

    /** The code of the currency the dashboard shows its figures in, or null for the base currency. */
    var dashboardCurrency: String?
        get() = null
        set(_) {}

    /** Whether accounts whose latest value is zero are left off the dashboard and out of updates. */
    var zeroAccountsHidden: Boolean
        get() = false
        set(_) {}
}

/** [DisplayPreferences] in the app's own shared preferences. */
class AndroidDisplayPreferences(context: Context) : DisplayPreferences {
    private val preferences = context.applicationContext.getSharedPreferences("display", Context.MODE_PRIVATE)

    override var amountsHidden: Boolean
        get() = preferences.getBoolean(AMOUNTS_HIDDEN, false)
        set(value) {
            preferences.edit().putBoolean(AMOUNTS_HIDDEN, value).apply()
        }

    override var dashboardCurrency: String?
        get() = preferences.getString(DASHBOARD_CURRENCY, null)
        set(value) {
            preferences.edit().putString(DASHBOARD_CURRENCY, value).apply()
        }

    override var zeroAccountsHidden: Boolean
        get() = preferences.getBoolean(ZERO_ACCOUNTS_HIDDEN, false)
        set(value) {
            preferences.edit().putBoolean(ZERO_ACCOUNTS_HIDDEN, value).apply()
        }

    private companion object {
        const val ZERO_ACCOUNTS_HIDDEN = "zero_accounts_hidden"
        const val AMOUNTS_HIDDEN = "amounts_hidden"
        const val DASHBOARD_CURRENCY = "dashboard_currency"
    }
}
