package com.android.tools.idea.modes.essentials

import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.flags.StudioFlags
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.ui.UIUtil
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito
import java.util.concurrent.atomic.AtomicInteger

@RunWith(JUnit4::class)
class EssentialsModeRecommenderTest : LightPlatformTestCase() {

  val recommender = EssentialsModeRecommender()
  private var notificationCounter: AtomicInteger = AtomicInteger(0)
  private val usageTracker = TestUsageTracker(VirtualTimeScheduler())

  @Before
  fun additionalSetUp() {
    UsageTracker.cleanAfterTesting()
    ApplicationManager.getApplication().messageBus.connect(testRootDisposable).subscribe(Notifications.TOPIC, object : Notifications {
      override fun notify(notification: Notification) {
        if (notification.groupId == recommender.notificationGroup) {
          notificationCounter.getAndIncrement()
        }
      }
    })
    UsageTracker.setWriterForTest(usageTracker)
    StudioFlags.ESSENTIALS_MODE_VISIBLE.clearOverride()
    StudioFlags.ESSENTIALS_MODE_GETS_RECOMMENDED.override(true)
    StudioFlags.ESSENTIALS_MODE_VISIBLE.override(true)
    PropertiesComponent.getInstance().setValue(recommender.ignoreEssentialsMode, false)
    EssentialsMode.setEnabled(false, project)
  }

  @Test
  fun `should not recommend if the mode is not visible`() {
    StudioFlags.ESSENTIALS_MODE_VISIBLE.override(false)
    Truth.assertThat(recommender.shouldRecommend()).isFalse()
  }

  @Test
  fun `should not recommend if mode is ignored`() {
    PropertiesComponent.getInstance().setValue(recommender.ignoreEssentialsMode, true)

    Truth.assertThat(recommender.shouldRecommend()).isFalse()
  }

  @Test
  fun `should not recommend with recommendation flag set to false`() {
    StudioFlags.ESSENTIALS_MODE_GETS_RECOMMENDED.override(false)

    Truth.assertThat(recommender.shouldRecommend()).isFalse()
  }

  @Test
  fun `should not recommend if in essentials mode`() {
    EssentialsMode.setEnabled(true, project)

    Truth.assertThat(recommender.shouldRecommend()).isFalse()
  }

  @Test
  fun `recommendation gets sent when it should`() {
    recommender.recommendEssentialsMode()
    UIUtil.dispatchAllInvocationEvents()

    assertNotificationIsTracked()
    Truth.assertThat(notificationCounter.get()).isEqualTo(1)
  }

  @Test
  fun `do not send recommendation if it should not`() {
    EssentialsMode.setEnabled(true, project)

    recommender.recommendEssentialsMode()
    UIUtil.dispatchAllInvocationEvents()

    assertNotificationIsNotTracked()
  }

  private fun assertNotificationIsNotTracked() {
    usageTracker.usages.forEach { Truth.assertThat(it.studioEvent.kind == AndroidStudioEvent.EventKind.EDITOR_NOTIFICATION).isFalse() }
  }

  private fun assertNotificationIsTracked() {
    Truth.assertThat(usageTracker.usages.size).isGreaterThan(0)
    Truth.assertThat(usageTracker.usages[0].studioEvent.editorNotification).isNotNull()
  }

  @Test
  fun `yes response turns on essentials mode`() {
    Truth.assertThat(EssentialsMode.isEnabled()).isFalse()
    val mockEvent = Mockito.mock(AnActionEvent::class.java)
    val notification = Notification(recommender.notificationGroup, "", "", NotificationType.INFORMATION)

    EssentialsModeRecommender().EssentialsModeResponseYes().actionPerformed(mockEvent, notification)

    Truth.assertThat(EssentialsMode.isEnabled()).isTrue()
    Truth.assertThat(notification.isExpired).isTrue()
  }

  @Test
  fun `not right now expires notification`() {
    val mockEvent = Mockito.mock(AnActionEvent::class.java)
    val notification = Notification(recommender.notificationGroup, "", "", NotificationType.INFORMATION)

    EssentialsModeRecommender().EssentialsModeResponseNotNow().actionPerformed(mockEvent, notification)

    Truth.assertThat(EssentialsMode.isEnabled()).isFalse()
    Truth.assertThat(notification.isExpired).isTrue()
  }

  @Test
  fun `do not show again sets property to no longer display notifications`() {
    Truth.assertThat(PropertiesComponent.getInstance().getBoolean(recommender.ignoreEssentialsMode)).isFalse()
    val mockEvent = Mockito.mock(AnActionEvent::class.java)
    val notification = Notification(recommender.notificationGroup, "", "", NotificationType.INFORMATION)

    EssentialsModeRecommender().EssentialsModeResponseDoNotShowAgain().actionPerformed(mockEvent, notification)

    Truth.assertThat(PropertiesComponent.getInstance().getBoolean(recommender.ignoreEssentialsMode)).isTrue()
    Truth.assertThat(EssentialsMode.isEnabled()).isFalse()
    Truth.assertThat(notification.isExpired).isTrue()
  }
}