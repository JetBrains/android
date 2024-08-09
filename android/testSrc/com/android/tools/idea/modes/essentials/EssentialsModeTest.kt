package com.android.tools.idea.modes.essentials

import com.android.tools.idea.flags.StudioFlags
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.google.common.truth.Truth
import com.intellij.ide.EssentialHighlightingMode
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.notification.NotificationsManager
import com.intellij.testFramework.LightPlatform4TestCase
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class EssentialsModeTest : LightPlatform4TestCase() {
  @Test
  fun `setEnabled turns on and off Essentials Mode`() {
    // initial clean-up
    val tracker = TestUsageTracker(VirtualTimeScheduler())
    UsageTracker.setWriterForTest(tracker)
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
    UsageTracker.cleanAfterTesting()
  }

  @Test
  fun `Essentials mode toggles Essential highlighting`() {
    val tracker = TestUsageTracker(VirtualTimeScheduler())
    UsageTracker.setWriterForTest(tracker)
    StudioFlags.ESSENTIALS_HIGHLIGHTING_MODE.override(true)
    EssentialsMode.setEnabled(false, project)
    Truth.assertThat(EssentialHighlightingMode.isEnabled()).isFalse()

    EssentialsMode.setEnabled(true, project)
    Truth.assertThat(EssentialHighlightingMode.isEnabled()).isTrue()

    EssentialsMode.setEnabled(false, project)
    Truth.assertThat(EssentialHighlightingMode.isEnabled()).isFalse()
    UsageTracker.cleanAfterTesting()
  }
  @Test
  fun `switching essentials mode state sends a usage metric`() {
    EssentialsMode.setEnabled(false, project)

    val tracker = TestUsageTracker(VirtualTimeScheduler())
    UsageTracker.setWriterForTest(tracker)

    EssentialsMode.setEnabled(true, project)

    Truth.assertThat(tracker.usages.size).isGreaterThan(0)
    Truth.assertThat(tracker.usages[0].studioEvent.kind == AndroidStudioEvent.EventKind.ESSENTIALS_MODE_EVENT).isTrue()
    Truth.assertThat(tracker.usages[0].studioEvent.essentialsModeEvent.enabled).isTrue()

    EssentialsMode.setEnabled(false, project)
    UsageTracker.cleanAfterTesting()
  }

  private fun getAmountOfNotifications() = NotificationsManager.getNotificationsManager()
    .getNotificationsOfType(EssentialsModeNotifier.EssentialsModeNotification::class.java, project).size

}