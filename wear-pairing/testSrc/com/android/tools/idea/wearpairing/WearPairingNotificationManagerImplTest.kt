package com.android.tools.idea.wearpairing

import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.notification.ActionCenter
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WearPairingNotificationManagerImplTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @After
  fun tearDown() {
    ActionCenter.getNotifications(projectRule.project).forEach { it.expire() }
  }

  @Test
  fun dismissNotificationsClearsNotifications() {
    val notificationManager = WearPairingNotificationManagerImpl()

    val pairing =
      WearPairingManager.PhoneWearPair(
        phone = PairingDevice("phone1", "Phone", 34, true, false, true, ConnectionState.ONLINE),
        wear = PairingDevice("wear1", "Wear", 30, true, true, true, ConnectionState.ONLINE),
      )
    assertTrue(
      "No notifications are expected at this point",
      notificationManager.pendingNotifications.isEmpty(),
    )
    notificationManager.showReconnectMessageBalloon(pairing, null)
    assertEquals(
      """
      Wear OS emulator reconnected
      Wear reconnected with Phone.<br/>

    """
        .trimIndent(),
      notificationManager.pendingNotifications.joinToString("\n") { "${it.title}\n${it.content}\n" },
    )

    val secondPairing =
      WearPairingManager.PhoneWearPair(
        phone = PairingDevice("phone2", "Phone", 34, true, false, true, ConnectionState.ONLINE),
        wear = PairingDevice("wear2", "Wear", 30, true, true, true, ConnectionState.ONLINE),
      )
    notificationManager.dismissNotifications(secondPairing)
    assertFalse(
      "Notifications should not have been dismissed using the wrong pairing",
      notificationManager.pendingNotifications.isEmpty(),
    )

    notificationManager.dismissNotifications(pairing)
    assertTrue(
      "Notifications should have been dismissed",
      notificationManager.pendingNotifications.isEmpty(),
    )
  }

  @Test
  fun `new notifications dismiss previous ones`() {
    val notificationManager = WearPairingNotificationManagerImpl()

    val pairing =
      WearPairingManager.PhoneWearPair(
        phone = PairingDevice("phone1", "Phone", 34, true, false, true, ConnectionState.ONLINE),
        wear = PairingDevice("wear1", "Wear", 30, true, true, true, ConnectionState.ONLINE),
      )
    notificationManager.showReconnectMessageBalloon(pairing, null)
    assertTrue(
      "A notification should have been triggered",
      notificationManager.pendingNotifications.isNotEmpty(),
    )
    notificationManager.showConnectionDroppedBalloon("offline", pairing, null)

    // Only the last notification will remain
    assertEquals(
      """
      Wear OS emulator connection dropped
      offline has gone offline. Wear will reconnect with Phone when it returns.<br/>

    """
        .trimIndent(),
      notificationManager.pendingNotifications.joinToString("\n") { "${it.title}\n${it.content}\n" },
    )
  }
}
