package io.github.arpit92k.fortuna.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import io.github.arpit92k.fortuna.BuildConfig
import io.github.arpit92k.fortuna.report.DeviceInfo
import io.github.arpit92k.fortuna.report.EmailDraft

/**
 * Opens the user's email app with [draft] addressed and filled in. Returns
 * false if the phone has no email app. Nothing is sent until the user sends it.
 */
fun openEmail(context: Context, draft: EmailDraft): Boolean {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(draft.to))
        putExtra(Intent.EXTRA_SUBJECT, draft.subject)
        putExtra(Intent.EXTRA_TEXT, draft.body)
    }
    return try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}

/** This app and phone, for a report. */
fun deviceInfo(): DeviceInfo = DeviceInfo(
    appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})" + if (BuildConfig.DEBUG) " debug" else "",
    androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
    model = "${Build.MANUFACTURER} ${Build.MODEL}",
)
