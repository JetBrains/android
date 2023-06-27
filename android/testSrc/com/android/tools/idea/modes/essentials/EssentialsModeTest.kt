package com.android.tools.idea.modes.essentials

import com.google.common.truth.Truth
import com.intellij.notification.NotificationsManager
import com.intellij.testFramework.LightPlatform4TestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class EssentialsModeTest : LightPlatform4TestCase() {
  @Test
  fun `setEnabled turns on and off Essentials Mode`() {
    // initial clean-up
    for (essentialsModeNotification in NotificationsManager.getNotificationsManager().getNotificationsOfType(
      EssentialsModeNotifier.EssentialsModeNotification::class.java,
      project
    )) {
      essentialsModeNotification.expire()
    }

    EssentialsMode.setEnabled(false, project)
    Truth.assertThat(EssentialsMode.isEnabled()).isFalse()

    EssentialsMode.setEnabled(true, project)
    Truth.assertThat(EssentialsMode.isEnabled()).isTrue()
    Truth.assertThat(getAmountOfNotifications()).isEqualTo(1)


    EssentialsMode.setEnabled(false, project)
    Truth.assertThat(EssentialsMode.isEnabled()).isFalse()
    Truth.assertThat(getAmountOfNotifications()).isEqualTo(0)
  }

  private fun getAmountOfNotifications() = NotificationsManager.getNotificationsManager()
    .getNotificationsOfType(EssentialsModeNotifier.EssentialsModeNotification::class.java, project).size

}