package p1.p2

import android.content.Context
import android.os.Build
import android.provider.Settings

fun isDevMode(context: Context): Boolean {
    return when {
        true -> {
            Settings.Secure.getInt(context.contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0
        }
        Build.VERSION.SDK_INT == Build.VERSION_CODES.JELLY_BEAN -> {
            @Suppress("DEPRECATION")
            Settings.Secure.getInt(context.contentResolver,
                Settings.Secure.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0
        }
        else -> false
    }
}