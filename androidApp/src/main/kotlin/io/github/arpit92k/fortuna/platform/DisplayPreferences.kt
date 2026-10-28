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

    private companion object {
        const val AMOUNTS_HIDDEN = "amounts_hidden"
        const val DASHBOARD_CURRENCY = "dashboard_currency"
    }
}
