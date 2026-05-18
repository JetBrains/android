// This file should not be edited manually! See go/template-diff-tests
package template.test.`in`

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput

const val CHANNEL_ID = "template.test.in.CHANNEL_ID"
const val READ_ACTION = "template.test.in.ACTION_MESSAGE_READ"
const val REPLY_ACTION = "template.test.in.ACTION_MESSAGE_REPLY"
const val CONVERSATION_ID = "conversation_id"
const val EXTRA_VOICE_REPLY = "extra_voice_reply"

class MyMessagingService : Service() {

    private val mMessenger = Messenger(IncomingHandler())
    private lateinit var mNotificationManager: NotificationManagerCompat

    override fun onCreate() {
        mNotificationManager = NotificationManagerCompat.from(applicationContext)
    }

    override fun onBind(intent: Intent): IBinder? {
        return mMessenger.binder
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        return Service.START_STICKY
    }

    private fun createIntent(conversationId: Int, action: String): Intent {
        return Intent().apply {
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            setAction(action)
            putExtra(CONVERSATION_ID, conversationId)
        }
    }

    private fun sendNotification(
        conversationId: Int,
        message: String,
        participant: String,
        timestamp: Long
    ) {
        // A pending Intent for reads
        val readPendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            conversationId,
            createIntent(conversationId, READ_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Build a RemoteInput for receiving voice input in a Car Notification
        val remoteInput = RemoteInput.Builder(EXTRA_VOICE_REPLY)
            .setLabel("Reply by voice")
            .build()

        // Building a Pending Intent for the reply action to trigger
        val replyIntent = PendingIntent.getBroadcast(
            applicationContext,
            conversationId,
            createIntent(conversationId, REPLY_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Create a Person object for the sender
        val sender = Person.Builder()
            .setName(participant)
            .build()

        // Create a Person object for the user
        val user = Person.Builder()
            .setName("Me")
            .build()

        // Create the MessagingStyle
        val messagingStyle = NotificationCompat.MessagingStyle(user)
            .addMessage(message, timestamp, sender)

        // Build the read action
        val readAction = NotificationCompat.Action.Builder(
            0, // No icon
            "Mark as Read",
            readPendingIntent
        )
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()

        // Build the reply action
        val replyAction = NotificationCompat.Action.Builder(
            0, // No icon
            "Reply",
            replyIntent
        )
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .addRemoteInput(remoteInput)
            .build()

        val channel = NotificationChannelCompat
            .Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
            .setName(resources.getText(R.string.app_name))
            .build()
        NotificationManagerCompat.from(applicationContext).createNotificationChannel(channel)

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            // Set the application notification icon:
            //.setSmallIcon(R.drawable.notification_icon)

            // Set the large icon, for example a picture of the other recipient of the message
            //.setLargeIcon(personBitmap)

            .setContentText(message)
            .setWhen(timestamp)
            .setContentTitle(participant)
            .setContentIntent(readPendingIntent)
            .setStyle(messagingStyle)
            .addAction(readAction)
            .addAction(replyAction)

        mNotificationManager.notify(conversationId, builder.build())
    }

    /**
     * Handler of incoming messages from clients.
     */
    internal inner class IncomingHandler : Handler(Looper.myLooper()!!) {
        override fun handleMessage(msg: Message) {
            sendNotification(1, "This is a sample message", "John Doe", System.currentTimeMillis())
        }
    }
}