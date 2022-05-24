package p1.p2

import android.annotation.SuppressLint
import android.app.Activity
import android.location.LocationManager

@Suppress("unused")
@SuppressLint("Registered")
class LocationTest : Activity() {
    fun test(manager: LocationManager, provider: String) {
        <error>manager.get<caret>LastKnownLocation(provider)</error>
    }
}
