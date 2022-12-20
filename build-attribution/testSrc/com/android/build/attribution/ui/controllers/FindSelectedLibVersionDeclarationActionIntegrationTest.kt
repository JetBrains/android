/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.build.attribution.ui.controllers

import com.android.build.attribution.ui.analytics.BuildAttributionUiAnalytics
import com.android.build.attribution.ui.view.details.JetifierWarningDetailsView
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunsInEdt
import com.intellij.usageView.UsageViewContentManager
import com.intellij.usages.UsageViewManager
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.awt.Dimension
import java.util.UUID

/**
 * This test verifies what happens when action is triggered and how it passes request to UsageViewManager.
 */
@RunsInEdt
class FindSelectedLibVersionDeclarationActionIntegrationTest {
  private val projectRule = AndroidProjectRule.onDisk()

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())


  private val tracker = TestUsageTracker(VirtualTimeScheduler())
  val buildSessionId = UUID.randomUUID().toString()
  lateinit var analytics: BuildAttributionUiAnalytics

  @Before
  fun setUp() {
    UsageTracker.setWriterForTest(tracker)
    analytics = BuildAttributionUiAnalytics(projectRule.project, uiSizeProvider = { Dimension(300, 200) })
    analytics.newReportSessionId(buildSessionId)
  }

  @After
  fun tearDown() {
    UsageTracker.cleanAfterTesting()
  }

  @Test
  fun testSingleFindingNavigatesToFile() {
    var gradleBuildFile: VirtualFile? = null
    runWriteAction {
      gradleBuildFile = projectRule.fixture.tempDirFixture.createFile(
        "build.gradle", """
dependencies {
    implementation "org.jetbrains.kotlin:kotlin-stdlib:1.5.31"
}
      """.trimIndent()
      )
    }
    val selectedDependency = JetifierWarningDetailsView.DirectDependencyDescriptor(
      fullName = "org.jetbrains.kotlin:kotlin-stdlib:1.5.31",
      projects = listOf(":"),
      pathToSupportLibrary = listOf() // Does not get involved here.
    )
    val action = FindSelectedLibVersionDeclarationAction({ selectedDependency }, projectRule.project, analytics)
    ActionManager.getInstance().tryToExecute(action, null, null, null, true)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    Truth.assertThat(FileEditorManager.getInstance(projectRule.project).openFiles.asList()).containsExactly(gradleBuildFile)
    val caretLine = FileEditorManager.getInstance(projectRule.project).selectedTextEditor?.caretModel?.logicalPosition?.line
    Truth.assertThat(caretLine).isEqualTo(1)

    // Verify metrics sent
    val buildAttributionEvents = tracker.usages.filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT }
    buildAttributionEvents.single().studioEvent.buildAttributionUiEvent.apply {
      Truth.assertThat(eventType).isEqualTo(BuildAttributionUiEvent.EventType.FIND_LIBRARY_DECLARATION_CLICKED)
    }
  }

  @Test
  fun testMultipleFindingOpenFindWindow() {
    runWriteAction {
      projectRule.fixture.tempDirFixture.createFile("build.gradle")
      projectRule.fixture.tempDirFixture.createFile(
        "app/build.gradle", """
dependencies {
  implementation "org.jetbrains.kotlin:kotlin-stdlib:1.5.31"
}
      """.trimIndent()
      )
      projectRule.fixture.tempDirFixture.createFile(
        "lib/build.gradle", """
dependencies {
  implementation "org.jetbrains.kotlin:kotlin-stdlib:1.5.31"
}
      """.trimIndent()
      )
      projectRule.fixture.tempDirFixture.createFile(
        "settings.gradle", """
include(":app")
include(":lib")
      """.trimIndent()
      )
    }
    val selectedDependency = JetifierWarningDetailsView.DirectDependencyDescriptor(
      fullName = "org.jetbrains.kotlin:kotlin-stdlib:1.5.31",
      projects = listOf(":app", ":lib"),
      pathToSupportLibrary = listOf() // Does not get involved here.
    )
    val action = FindSelectedLibVersionDeclarationAction({ selectedDependency }, projectRule.project, analytics)
    ActionManager.getInstance().tryToExecute(action, null, null, null, true)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    Truth.assertThat(FileEditorManager.getInstance(projectRule.project).openFiles.asList()).isEmpty()

    val selectedContent = UsageViewContentManager.getInstance(projectRule.project).selectedContent
    Truth.assertThat(selectedContent.tabName).isEqualTo("Dependency Version Declaration")
    val usagesView = UsageViewManager.getInstance(projectRule.project).selectedUsageView!!
    Truth.assertThat(usagesView.usagesCount).isEqualTo(2)

    // Verify metrics sent
    val buildAttributionEvents = tracker.usages.filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT }
    buildAttributionEvents.single().studioEvent.buildAttributionUiEvent.apply {
      Truth.assertThat(eventType).isEqualTo(BuildAttributionUiEvent.EventType.FIND_LIBRARY_DECLARATION_CLICKED)
    }
  }

  @Test
  fun testMultipleEntriesWIthSearchOnlyInOneFile() {
    var gradleAppBuildFile: VirtualFile? = null

    runWriteAction {
      projectRule.fixture.tempDirFixture.createFile("build.gradle")
      gradleAppBuildFile = projectRule.fixture.tempDirFixture.createFile(
        "app/build.gradle", """
dependencies {
  implementation "org.jetbrains.kotlin:kotlin-stdlib:1.5.31"
}
      """.trimIndent()
      )
      projectRule.fixture.tempDirFixture.createFile(
        "lib/build.gradle", """
dependencies {
  implementation "org.jetbrains.kotlin:kotlin-stdlib:1.5.31"
}
      """.trimIndent()
      )
      projectRule.fixture.tempDirFixture.createFile(
        "settings.gradle", """
include(":app")
include(":lib")
      """.trimIndent()
      )
    }
    val selectedDependency = JetifierWarningDetailsView.DirectDependencyDescriptor(
      fullName = "org.jetbrains.kotlin:kotlin-stdlib:1.5.31",
      projects = listOf(":app"),
      pathToSupportLibrary = listOf() // Does not get involved here.
    )

    val action = FindSelectedLibVersionDeclarationAction({ selectedDependency }, projectRule.project, analytics)
    ActionManager.getInstance().tryToExecute(action, null, null, null, true)
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    Truth.assertThat(FileEditorManager.getInstance(projectRule.project).openFiles.asList()).containsExactly(gradleAppBuildFile)
    val caretLine = FileEditorManager.getInstance(projectRule.project).selectedTextEditor?.caretModel?.logicalPosition?.line
    Truth.assertThat(caretLine).isEqualTo(1)

    // Verify metrics sent
    val buildAttributionEvents = tracker.usages.filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT }
    buildAttributionEvents.single().studioEvent.buildAttributionUiEvent.apply {
      Truth.assertThat(eventType).isEqualTo(BuildAttributionUiEvent.EventType.FIND_LIBRARY_DECLARATION_CLICKED)
    }
  }
}