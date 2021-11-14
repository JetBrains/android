/*
 * Copyright (C) 2021 The Android Open Source Project
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
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.gradle.GradleFileModelTestCase
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.BuildAttributionUiEvent
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue
import com.intellij.testFramework.RunsInEdt
import com.intellij.usageView.UsageViewContentManager
import com.intellij.usages.UsageViewManager
import org.jetbrains.android.AndroidTestBase
import org.jetbrains.kotlin.idea.core.util.getLineNumber
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.awt.Dimension
import java.util.UUID

/**
 * This test covers search logic works as expected on different options of dependency declarations.
 */
@RunsInEdt
class FindSelectedLibVersionDeclarationActionTest : GradleFileModelTestCase() {

  @Before
  fun setUpTestDataPath() {
    testDataPath = AndroidTestBase.getModulePath("build-attribution") + "/testData/buildFiles"
  }

  @Test
  fun testVersionInLiteral() {
    writeToBuildFile(TestFileName("libraries/versionInLiteral"))
    val arrayOfUsageInfos = findVersionDeclarations(project, "org.jetbrains.kotlin:kotlin-stdlib:1.5.31")
    Truth.assertThat(arrayOfUsageInfos).hasLength(1)
    Truth.assertThat(arrayOfUsageInfos[0].element?.getLineNumber()).isEqualTo(1)
  }

  @Test
  fun testVersionInMapNotation() {
    writeToBuildFile(TestFileName("libraries/versionInMapNotation"))
    val arrayOfUsageInfos = findVersionDeclarations(project, "org.jetbrains.kotlin:kotlin-stdlib:1.5.31")
    Truth.assertThat(arrayOfUsageInfos).hasLength(1)
    Truth.assertThat(arrayOfUsageInfos[0].element?.getLineNumber()).isEqualTo(1)
  }

  @Test
  fun testVersionInVariable() {
    writeToBuildFile(TestFileName("libraries/versionInVariable"))
    val arrayOfUsageInfos = findVersionDeclarations(project, "org.jetbrains.kotlin:kotlin-stdlib:1.5.31")
    Truth.assertThat(arrayOfUsageInfos).hasLength(1)
    Truth.assertThat(arrayOfUsageInfos[0].element?.getLineNumber()).isEqualTo(0)
  }

  @Test
  fun testVersionInUnknownVariable() {
    writeToBuildFile(TestFileName("libraries/versionInUnknownVariable"))
    val arrayOfUsageInfos = findVersionDeclarations(project, "org.jetbrains.kotlin:kotlin-stdlib:1.5.31")
    Truth.assertThat(arrayOfUsageInfos).hasLength(1)
    // In case of problems with resolving version return declaration itself.
    Truth.assertThat(arrayOfUsageInfos[0].element?.getLineNumber()).isEqualTo(2)
  }

  @Test
  fun testSeveralDeclarationsVersionInLiteral() {
    writeToBuildFile(TestFileName("libraries/versionInLiteralSeveralDeclarations"))
    val arrayOfUsageInfos = findVersionDeclarations(project, "org.jetbrains.kotlin:kotlin-stdlib:1.5.31")
    // Return several all declarations when they are defined separately.
    Truth.assertThat(arrayOfUsageInfos).hasLength(2)
  }

  @Test
  fun testSeveralDeclarationsVersionInOneVariable() {
    writeToBuildFile(TestFileName("libraries/versionInVariableSeveralDeclarations"))
    val arrayOfUsageInfos = findVersionDeclarations(project, "org.jetbrains.kotlin:kotlin-stdlib:1.5.31")
    // Return one single version variable declaration references from both dependency declarations.
    Truth.assertThat(arrayOfUsageInfos).hasLength(1)
    Truth.assertThat(arrayOfUsageInfos[0].element?.getLineNumber()).isEqualTo(0)
  }

  @Test
  fun testDependencyInVariable() {
    writeToBuildFile(TestFileName("libraries/dependencyInVariable"))
    val arrayOfUsageInfos = findVersionDeclarations(project, "org.jetbrains.kotlin:kotlin-stdlib:1.5.31")
    Truth.assertThat(arrayOfUsageInfos).hasLength(1)
    Truth.assertThat(arrayOfUsageInfos[0].element?.getLineNumber()).isEqualTo(0)
  }

  @Test
  fun testDependencyInMap() {
    writeToBuildFile(TestFileName("libraries/dependencyInMap"))
    val arrayOfUsageInfos = findVersionDeclarations(project, "org.jetbrains.kotlin:kotlin-stdlib:1.5.31")
    Truth.assertThat(arrayOfUsageInfos).hasLength(1)
    Truth.assertThat(arrayOfUsageInfos[0].element?.getLineNumber()).isEqualTo(1)
  }

  @Test
  fun testDependencyAsReferenceMap() {
    writeToBuildFile(TestFileName("libraries/dependencyAsReferenceMap"))
    val arrayOfUsageInfos = findVersionDeclarations(project, "org.jetbrains.kotlin:kotlin-stdlib:1.5.31")
    Truth.assertThat(arrayOfUsageInfos).hasLength(1)
    Truth.assertThat(arrayOfUsageInfos[0].element?.getLineNumber()).isEqualTo(0)
  }

  @Test
  fun testDependencyInVariableVersionInVariable() {
    writeToBuildFile(TestFileName("libraries/dependencyInVariableVersionInVariable"))
    val arrayOfUsageInfos = findVersionDeclarations(project, "org.jetbrains.kotlin:kotlin-stdlib:1.5.31")
    Truth.assertThat(arrayOfUsageInfos).hasLength(1)
    Truth.assertThat(arrayOfUsageInfos[0].element?.getLineNumber()).isEqualTo(0)
  }
}

/**
 * This test verifies what happens when action is triggered and how it passes request to UsageViewManager.
 */
@RunsInEdt
class FindActionIntegrationTest {
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
      gradleBuildFile = projectRule.fixture.tempDirFixture.createFile("build.gradle", """
dependencies {
    implementation "org.jetbrains.kotlin:kotlin-stdlib:1.5.31"
}
      """.trimIndent())
    }
    val action = FindSelectedLibVersionDeclarationAction({ "org.jetbrains.kotlin:kotlin-stdlib:1.5.31" }, projectRule.project, analytics)
    ActionManager.getInstance().tryToExecute(action, null, null, null, true)
    dispatchAllInvocationEventsInIdeEventQueue()
    Truth.assertThat(FileEditorManagerEx.getInstance(projectRule.project).openFiles.asList()).containsExactly(gradleBuildFile)
    val caretLine = FileEditorManagerEx.getInstance(projectRule.project).selectedTextEditor?.caretModel?.logicalPosition?.line
    Truth.assertThat(caretLine).isEqualTo(1)

    // Verify metrics sent
    val buildAttributionEvents = tracker.usages.filter { use -> use.studioEvent.kind == AndroidStudioEvent.EventKind.BUILD_ATTRIBUTION_UI_EVENT }
    buildAttributionEvents.single().studioEvent.buildAttributionUiEvent.apply {
      Truth.assertThat(eventType).isEqualTo(BuildAttributionUiEvent.EventType.FIND_LIBRARY_DECLARATION_CLICKED)
    }
  }

  @Test
  fun testMultipleFindingOpenFindWindow() {
    var gradleBuildFile: VirtualFile? = null
    runWriteAction {
      gradleBuildFile = projectRule.fixture.tempDirFixture.createFile("build.gradle", """
dependencies {
  implementation "org.jetbrains.kotlin:kotlin-stdlib:1.5.31"
}
dependencies {
  implementation "org.jetbrains.kotlin:kotlin-stdlib:1.5.31"
}
      """.trimIndent())
    }
    val action = FindSelectedLibVersionDeclarationAction({ "org.jetbrains.kotlin:kotlin-stdlib:1.5.31" }, projectRule.project, analytics)
    ActionManager.getInstance().tryToExecute(action, null, null, null, true)
    dispatchAllInvocationEventsInIdeEventQueue()
    Truth.assertThat(FileEditorManagerEx.getInstance(projectRule.project).openFiles.asList()).isEmpty()

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
}