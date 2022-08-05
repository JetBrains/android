package test.pkg

import android.app.Notification
import android.app.NotificationManager

fun notify(manager: NotificationManager, notification: Notification) {
    manager.notify(1, notification)
}
