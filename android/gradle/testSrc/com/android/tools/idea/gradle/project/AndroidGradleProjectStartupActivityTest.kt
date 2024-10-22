/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project

import com.android.tools.idea.IdeInfo
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.sync.AutoSyncBehavior
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.GradleSyncListener
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.NotificationRule
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.execution.RunConfigurationProducerService
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.execution.junit.JUnitConfigurationType
import com.intellij.ide.util.PropertiesComponent
import com.intellij.mock.MockModule
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests for [AndroidGradleProjectStartupActivity].
 */
class AndroidGradleProjectStartupActivityTest {
  private val myProjectRule = AndroidProjectRule.inMemory()

  @Mock
  private lateinit var myInfo: Info
  private lateinit var myStartupActivity: AndroidGradleProjectStartupActivity
  private var myRequest: GradleSyncInvoker.Request? = null
  private val myProject: Project
    get() = myProjectRule.project
  private val notificationRule = NotificationRule(myProjectRule)

  @get:Rule
  val ruleChain = RuleChain(myProjectRule, notificationRule)

  @Before
  fun setUp() {
    StudioFlags.SHOW_GRADLE_AUTO_SYNC_SETTING_UI.override(true)
    val syncInvoker = object : GradleSyncInvoker.FakeInvoker() {
      override fun requestProjectSync(
        project: Project,
        request: GradleSyncInvoker.Request,
        listener: GradleSyncListener?
      ) {
        super.requestProjectSync(project, request, listener)
        assertThat(myRequest).isNull()
        myRequest = request
      }
    }
    ApplicationManager.getApplication().replaceService(GradleSyncInvoker::class.java, syncInvoker, myProjectRule.testRootDisposable)
    myInfo = mock()
    myStartupActivity = AndroidGradleProjectStartupActivity()
    myProject.replaceService(Info::class.java, myInfo, myProjectRule.testRootDisposable)
  }

  @After
  fun tearDown() {
    myRequest = null
    GradleExperimentalSettings.getInstance().AUTO_SYNC_BEHAVIOR = AutoSyncBehavior.Default
    StudioFlags.SHOW_GRADLE_AUTO_SYNC_SETTING_UI.clearOverride()
    PropertiesComponent.getInstance().unsetValue(SYNC_DUE_DIALOG_SHOWN)
  }

  @Test
  fun testRunActivityWithImportedProject() {
    // this test only works in AndroidStudio due to a number of isAndroidStudio checks inside AndroidGradleProjectStartupActivity
    if (!IdeInfo.getInstance().isAndroidStudio) return
    whenever(myInfo.isBuildWithGradle).thenReturn(true)
    runBlocking { myStartupActivity.execute(myProject) }
    val request = GradleSyncInvoker.Request(GradleSyncStats.Trigger.TRIGGER_PROJECT_REOPEN)
    assertThat(myRequest).isEqualTo(request)
  }

  @Test
  fun testRunActivityWithExistingGradleProject() {
    whenever(myInfo.isBuildWithGradle).thenReturn(true)
    whenever(myInfo.androidModules).thenReturn(listOf<Module>(MockModule(myProjectRule.testRootDisposable)))
    runBlocking { myStartupActivity.execute(myProject) }
    val request = GradleSyncInvoker.Request(GradleSyncStats.Trigger.TRIGGER_PROJECT_REOPEN)
    assertThat(myRequest).isEqualTo(request)
  }

  @Test
  fun testRunActivityWithNonGradleProject() {
    whenever(myInfo.isBuildWithGradle).thenReturn(false)
    runBlocking { myStartupActivity.execute(myProject) }
    assertThat(myRequest).isNull()
  }

  @Test
  fun testJunitProducersAreIgnored() {
    // this test only works in AndroidStudio due to a number of isAndroidStudio checks inside AndroidGradleProjectStartupActivity
    if (!IdeInfo.getInstance().isAndroidStudio) return
    whenever(myInfo.isBuildWithGradle).thenReturn(true)
    runBlocking { myStartupActivity.execute(myProject) }
    val ignoredProducersService = RunConfigurationProducerService.getInstance(myProject).state.ignoredProducers
    val allJUnitProducers =
      RunConfigurationProducer.EP_NAME.extensionList
        .filter { it.configurationType == JUnitConfigurationType.getInstance() }
        .map { it.javaClass.name }
        .toList()
    assertThat(ignoredProducersService).containsAllIn(allJUnitProducers)
  }

  @Test
  fun testJunitProducersAreNotIgnoredInNonGradleProjects() {
    // this test only works in AndroidStudio due to a number of isAndroidStudio checks inside AndroidGradleProjectStartupActivity
    if (!IdeInfo.getInstance().isAndroidStudio) return
    whenever(myInfo.isBuildWithGradle).thenReturn(false)
    val ignoredProducers = RunConfigurationProducerService.getInstance(myProject).state.ignoredProducers
    assertThat(ignoredProducers).isEmpty() // arguably this test is too strong, but it works.
    runBlocking { myStartupActivity.execute(myProject) }
    assertThat(RunConfigurationProducerService.getInstance(myProject).state.ignoredProducers).isEmpty()
  }

  @Test
  fun testAutoSyncDisabledResultsInNoRequest() {
    // this test only works in AndroidStudio due to a number of isAndroidStudio checks inside AndroidGradleProjectStartupActivity
    if (!IdeInfo.getInstance().isAndroidStudio) return
    GradleExperimentalSettings.getInstance().AUTO_SYNC_BEHAVIOR = AutoSyncBehavior.Manual
    whenever(myInfo.isBuildWithGradle).thenReturn(true)
    runBlocking { myStartupActivity.execute(myProject) }
    assertThat(myRequest).isNull()
  }

  @Test
  fun testAutoSyncReEnabledResultsInARequest() {
    // this test only works in AndroidStudio due to a number of isAndroidStudio checks inside AndroidGradleProjectStartupActivity
    if (!IdeInfo.getInstance().isAndroidStudio) return
    GradleExperimentalSettings.getInstance().AUTO_SYNC_BEHAVIOR = AutoSyncBehavior.Manual
    whenever(myInfo.isBuildWithGradle).thenReturn(true)
    runBlocking { myStartupActivity.execute(myProject) }
    assertThat(myRequest).isNull()
    GradleExperimentalSettings.getInstance().AUTO_SYNC_BEHAVIOR = AutoSyncBehavior.Default
    runBlocking { myStartupActivity.execute(myProject) }
    assertThat(myRequest).isNotNull()
  }

  @Test
  @RunsInEdt
  fun testDialogShowsOnFirstSyncSuppression() {
    // this test only works in AndroidStudio due to a number of isAndroidStudio checks inside AndroidGradleProjectStartupActivity
    if (!IdeInfo.getInstance().isAndroidStudio) return;
    PropertiesComponent.getInstance().setValue(SYNC_DUE_DIALOG_SHOWN, false)
    GradleExperimentalSettings.getInstance().AUTO_SYNC_BEHAVIOR = AutoSyncBehavior.Manual
    whenever(myInfo.isBuildWithGradle).thenReturn(true)

    try {
      runBlocking { myStartupActivity.execute(myProject) }
    }
    catch (e: Exception) {
      assertThat(e.message).isEqualTo("Some of the Android Studio features using Gradle require syncing so it has up-to-date information about your project. Sync the project to ensure the best Android Studio experience. You can snooze sync notifications for this session.")
    }
  }

  @Test
  @RunsInEdt
  fun testNotificationShowsOnConsequentSuppression() {
    // this test only works in AndroidStudio due to a number of isAndroidStudio checks inside AndroidGradleProjectStartupActivity
    if (!IdeInfo.getInstance().isAndroidStudio) return;
    PropertiesComponent.getInstance().setValue(SYNC_DUE_DIALOG_SHOWN, true)
    GradleExperimentalSettings.getInstance().AUTO_SYNC_BEHAVIOR = AutoSyncBehavior.Manual
    whenever(myInfo.isBuildWithGradle).thenReturn(true)
    runBlocking { myStartupActivity.execute(myProject) }

    val notification = notificationRule.notifications.find { it.groupId == SYNC_DUE_BUT_AUTO_SYNC_DISABLED_ID }

    assertWithMessage("Should show a notification").that(notification).isNotNull()
    assertWithMessage("Should offer three notification actions")
      .that(notification?.actions?.map { it.templatePresentation.text }).isEqualTo(
        listOf("Sync now", "Automatically sync this project", "Snooze"))
  }
}
