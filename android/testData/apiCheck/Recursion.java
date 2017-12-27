package p1.p2;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.RemoteViews;

public class Class {
    public static class Builder {
        private Notification.Builder b;

        public Builder(Context context, Notification n,
                       CharSequence contentTitle, CharSequence contentText, CharSequence contentInfo,
                       RemoteViews tickerView, int number,
                       PendingIntent contentIntent, PendingIntent fullScreenIntent, Bitmap largeIcon,
                       int mProgressMax, int mProgress, boolean mProgressIndeterminate,
                       boolean useChronometer, int priority, CharSequence subText, Bundle extras) {
            b = <error descr="Call requires API level 11 (current min is 1): new android.app.Notification.Builder">new Notification.Builder</error>(context)
                    .<error descr="Call requires API level 11 (current min is 1): android.app.Notification.Builder#setWhen">setWhen</error>(n.when)
                    .<error descr="Call requires API level 11 (current min is 1): android.app.Notification.Builder#setSmallIcon">setSmallIcon</error>(n.icon, n.iconLevel)
                    .<error descr="Call requires API level 11 (current min is 1): android.app.Notification.Builder#setContent">setContent</error>(n.contentView)
                    .<error descr="Call requires API level 11 (current min is 1): android.app.Notification.Builder#setTicker">setTicker</error>(n.tickerText, tickerView)
                    .<error descr="Call requires API level 11 (current min is 1): android.app.Notification.Builder#setSound">setSound</error>(n.sound, n.audioStreamType)
                    .<error descr="Call requires API level 11 (current min is 1): android.app.Notification.Builder#setVibrate">setVibrate</error>(n.vibrate)
                    .<error descr="Call requires API level 11 (current min is 1): android.app.Notification.Builder#setLights">setLights</error>(n.ledARGB, n.ledOnMS, n.ledOffMS)
                    .<error descr="Call requires API level 11 (current min is 1): android.app.Notification.Builder#setOngoing">setOngoing</error>((n.flags & Notification.FLAG_ONGOING_EVENT) != 0)
                    .<error descr="Call requires API level 11 (current min is 1): android.app.Notification.Builder#setOnlyAlertOnce">setOnlyAlertOnce</error>((n.flags & Notification.FLAG_ONLY_ALERT_ONCE) != 0)
                    .<error descr="Call requires API level 11 (current min is 1): android.app.Notification.Builder#setAutoCancel">setAutoCancel</error>((n.flags & Notification.FLAG_AUTO_CANCEL) != 0)
                    .<error descr="Call requires API level 11 (current min is 1): android.app.Notification.Builder#setDefaults">setDefaults</error>(n.defaults)
                    .<error descr="Call requires API level 11 (current min is 1): android.app.Notification.Builder#setContentTitle">setContentTitle</error>(contentTitle)
                    .<error descr="Call requires API level 11 (current min is 1): android.app.Notification.Builder#setContentText">setContentText</error>(contentText)
                    .<error descr="Call requires API level 16 (current min is 1): android.app.Notification.Builder#setSubText">setSubText</error>(subText)
                    .<error descr="Call requires API level 11 (current min is 1): android.app.Notification.Builder#setContentInfo">setContentInfo</error>(contentInfo)
                    .<error descr="Call requires API level 11 (current min is 1): android.app.Notification.Builder#setContentIntent">setContentIntent</error>(contentIntent)
                    .<error descr="Call requires API level 11 (current min is 1): android.app.Notification.Builder#setDeleteIntent">setDeleteIntent</error>(n.deleteIntent)
                    .<error descr="Call requires API level 11 (current min is 1): android.app.Notification.Builder#setFullScreenIntent">setFullScreenIntent</error>(fullScreenIntent,
                            (n.flags & Notification.FLAG_HIGH_PRIORITY) != 0)
                    .<error descr="Call requires API level 11 (current min is 1): android.app.Notification.Builder#setLargeIcon">setLargeIcon</error>(largeIcon)
                    .<error descr="Call requires API level 11 (current min is 1): android.app.Notification.Builder#setNumber">setNumber</error>(number)
                    .<error descr="Call requires API level 16 (current min is 1): android.app.Notification.Builder#setUsesChronometer">setUsesChronometer</error>(useChronometer)
                    .<error descr="Call requires API level 16 (current min is 1): android.app.Notification.Builder#setPriority">setPriority</error>(priority)
                    .<error descr="Call requires API level 14 (current min is 1): android.app.Notification.Builder#setProgress">setProgress</error>(mProgressMax, mProgress, mProgressIndeterminate);
        }
    }
}