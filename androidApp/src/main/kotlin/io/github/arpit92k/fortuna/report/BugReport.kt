package io.github.arpit92k.fortuna.report

// What a bug report holds (UF-18): the user's description, the app's
// version, the Android version and the phone's model, and any saved crash
// records. Never amounts, names, dates of entries, the PIN or the phrase:
// nothing here is taken from the user's data.

/** Decided: a dedicated address for Fortuna, not a personal one. */
const val SUPPORT_EMAIL = "support.fortuna@gmail.com"

/** The app and phone a report comes from. */
data class DeviceInfo(val appVersion: String, val androidVersion: String, val model: String)

/** An email ready for the user's email app: addressed and filled in. */
data class EmailDraft(val to: String, val subject: String, val body: String)

/** The whole report, as the user sees it before sending. */
fun reportText(description: String, device: DeviceInfo, crashes: List<String>): String = buildString {
    appendLine(description.trim().ifEmpty { "(No description)" })
    appendLine()
    appendLine("App version: ${device.appVersion}")
    appendLine("Android: ${device.androidVersion}")
    appendLine("Phone: ${device.model}")
    for (crash in crashes) {
        appendLine()
        appendLine(crash)
    }
}.trimEnd()

fun emailDraftOf(description: String, device: DeviceInfo, crashes: List<String>): EmailDraft =
    EmailDraft(
        to = SUPPORT_EMAIL,
        subject = if (crashes.isEmpty()) "Fortuna bug report (${device.appVersion})" else "Fortuna crash report (${device.appVersion})",
        body = reportText(description, device, crashes),
    )
