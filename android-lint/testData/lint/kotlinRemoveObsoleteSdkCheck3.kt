package p1.p2

import android.content.Context
import android.os.Build
import android.provider.Settings

fun isDevMode(context: Context): Boolean {
    return when {
        <warning descr="Unnecessary; `SDK_INT` is always >= 21">Build.VERSION.SDK_INT > <caret>Build.VERSION_CODES.JELLY_BEAN</warning> -> {
            Settings.Secure.getInt(context.contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0
        }
        <warning descr="Unnecessary; `Build.VERSION.SDK_INT == Build.VERSION_CODES.JELLY_BEAN` is never true here">Build.VERSION.SDK_INT == Build.VERSION_CODES.JELLY_BEAN</warning> -> {
            @Suppress("DEPRECATION")
            Settings.Secure.getInt(context.contentResolver,
                Settings.Secure.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0
        }
        else -> false
    }
}