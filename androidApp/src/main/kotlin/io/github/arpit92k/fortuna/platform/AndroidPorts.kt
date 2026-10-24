package io.github.arpit92k.fortuna.platform

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import io.github.arpit92k.fortuna.core.ports.Clock
import io.github.arpit92k.fortuna.core.ports.OkioPrivateFiles
import io.github.arpit92k.fortuna.core.ports.PrivateFiles
import kotlinx.datetime.LocalDate
import okio.FileSystem
import okio.Path.Companion.toOkioPath

/**
 * The Private files port on the app's no-backup directory. Only this app can
 * read it, and the system never copies it to the cloud or to a new phone;
 * the manifest's backup settings say the same again.
 */
fun androidPrivateFiles(context: Context, folder: String = FOLDER): PrivateFiles =
    OkioPrivateFiles(FileSystem.SYSTEM, context.noBackupFilesDir.toOkioPath() / folder)

private const val FOLDER = "fortuna"

/**
 * The Clock port: the phone's date, `SystemClock.elapsedRealtime` for the
 * time since start-up, which changing the time or date does not affect, and
 * the system's boot count to tell start-ups apart.
 */
class AndroidClock(context: Context) : Clock {
    private val resolver = context.applicationContext.contentResolver

    override fun today(): LocalDate {
        val now = java.time.LocalDate.now()
        return LocalDate(now.year, now.monthValue, now.dayOfMonth)
    }

    override fun sinceStartMillis(): Long = SystemClock.elapsedRealtime()

    // Every Android version the app supports keeps this count. Should a phone
    // not, restarts would look alike, and a wait for a wrong PIN would run on
    // from where it was instead of beginning again.
    override fun startUp(): Long = Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT, -1).toLong()
}
