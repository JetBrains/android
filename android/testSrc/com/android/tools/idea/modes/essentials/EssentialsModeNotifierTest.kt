package com.android.tools.idea.modes.essentials

import com.google.common.truth.Truth
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.components.service
import com.intellij.testFramework.LightPlatform4TestCase
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class EssentialsModeNotifierTest : LightPlatform4TestCase() {

  @Before
  fun setup() {
    PropertiesComponent.getInstance().setValue(EssentialsModeNotifier().ignore, false)
    EssentialsMode.setEnabled(false, project)
    expireNotifications()
    EssentialsMode.setEnabled(false, project)
  }

  @Test
  fun `notification does not show when property set to ignore`() {
    PropertiesComponent.getInstance().setValue(EssentialsModeNotifier().ignore, true)
    project.service<EssentialsModeNotifier>().notifyProject()
    Truth.assertThat(notificationCount()).isEqualTo(0)
    PropertiesComponent.getInstance().setValue(EssentialsModeNotifier().ignore, false)
  }

  @Test
  fun `notification not sent out if Essentials Mode is disabled`() {
    project.service<EssentialsModeNotifier>().notifyProject()
    Truth.assertThat(notificationCount()).isEqualTo(0)
  }

  @Test
  fun `notification gets sent out`() {
    EssentialsMode.setEnabled(true, project)
    expireNotifications()
    project.service<EssentialsModeNotifier>().notifyProject()

    Truth.assertThat(notificationCount()).isEqualTo(1)
  }

  private fun expireNotifications() {
    for (essentialsModeNotification in NotificationsManager.getNotificationsManager()
      .getNotificationsOfType(EssentialsModeNotifier.EssentialsModeNotification::class.java, project)) {
      essentialsModeNotification.expire()
    }
  }

  private fun notificationCount(): Int = NotificationsManager.getNotificationsManager()
    .getNotificationsOfType(EssentialsModeNotifier.EssentialsModeNotification::class.java, project).size

}