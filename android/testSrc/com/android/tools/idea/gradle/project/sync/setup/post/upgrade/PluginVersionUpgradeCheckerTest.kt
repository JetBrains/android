/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.project.sync.setup.post.upgrade

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.GradleProjectInfo
import com.android.tools.idea.gradle.project.sync.setup.post.PluginVersionUpgrade
import com.android.tools.idea.testing.IdeComponents
import com.intellij.notification.NotificationsManager
import com.intellij.testFramework.PlatformTestCase
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations.initMocks

class PluginVersionUpgradeCheckerTest : PlatformTestCase() {
  @Mock private lateinit var projectInfo: GradleProjectInfo
  @Mock private lateinit var upgradeReminder: TimeBasedUpgradeReminder

  override fun setUp() {
    super.setUp()
    StudioFlags.BALLOON_UPGRADE_NOTIFICATION.override(true)

    initMocks(this)

    Mockito.`when`<Boolean>(projectInfo.isBuildWithGradle).thenReturn(true)
    Mockito.`when`<Boolean>(upgradeReminder.shouldAskForUpgrade(Mockito.any())).thenReturn(true)

    // RecommendedPluginVersionUpgradeChecker is a StartActivity, which may be executed during setup.
    // We clean all possible notifications before test.
    cleanNotification()
  }

  override fun tearDown() {
    cleanNotification()

    StudioFlags.BALLOON_UPGRADE_NOTIFICATION.clearOverride()
    super.tearDown()
  }

  private fun cleanNotification() {
    val notifications = NotificationsManager
      .getNotificationsManager()
      .getNotificationsOfType<ProjectUpgradeNotification>(ProjectUpgradeNotification::class.java, project)
    notifications.forEach { it.expire() }
  }

  fun testNoNotificationWhenProjectIsNotUpgradable() {
    Mockito.`when`<Boolean>(projectInfo.isBuildWithGradle).thenReturn(true)

    replaceUpgradeService(upgradable = false)

    checkUpgrade(myProject, upgradeReminder)

    val notifications = NotificationsManager
      .getNotificationsManager()
      .getNotificationsOfType<ProjectUpgradeNotification>(ProjectUpgradeNotification::class.java, project)

    assertEmpty(notifications)
  }

  fun testShowNotificationWhenProjectIsUpgradable() {
    Mockito.`when`<Boolean>(projectInfo.isBuildWithGradle).thenReturn(true)

    replaceUpgradeService(upgradable = true)

    checkUpgrade(myProject, upgradeReminder)

    val notifications = NotificationsManager
      .getNotificationsManager()
      .getNotificationsOfType<ProjectUpgradeNotification>(ProjectUpgradeNotification::class.java, project)

    assertSize(1, notifications)
  }

  private fun replaceUpgradeService(upgradable: Boolean) {
    val upgrade = Mockito.mock(PluginVersionUpgrade::class.java)
    @Suppress("UsePropertyAccessSyntax")
    Mockito.`when`<Boolean>(upgrade.isRecommendedUpgradable()).thenReturn(upgradable)

    val ideComponents = IdeComponents(myProject)
    ideComponents.replaceProjectService(PluginVersionUpgrade::class.java, upgrade)
  }
}
