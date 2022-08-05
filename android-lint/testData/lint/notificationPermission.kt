package test.pkg

import android.app.Notification
import android.app.NotificationManager

fun notify(manager: NotificationManager, notification: Notification) {
    <error descr="When targeting Android 13 or higher, posting a permission requires holding the `POST_NOTIFICATIONS` permission">manager.not<caret>ify(1, notification)</error>
}
