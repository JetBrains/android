/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.instrumented.testsuite.actions

import com.android.flags.junit.RestoreFlagRule
import com.android.tools.idea.flags.StudioFlags
import com.google.common.truth.Truth.assertThat
import com.android.tools.idea.protobuf.TextFormat
import com.google.testing.platform.proto.api.core.TestSuiteResultProto
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.writeChild
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@RunsInEdt
class ImportUtpResultActionTest {
  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()
  private val temporaryFolder = TemporaryFolder()

  @get:Rule
  val rules: RuleChain = RuleChain
    .outerRule(projectRule)
    .around(EdtRule())
    .around(disposableRule)
    .around(temporaryFolder)
    .around(RestoreFlagRule(StudioFlags.UTP_TEST_RESULT_SUPPORT))

  @Test
  fun importUtpResults() {
    val importUtpResultAction = ImportUtpResultAction()

    importUtpResultAction.parseResultsAndDisplay(temporaryFolder.newFile(), disposableRule.disposable, projectRule.project)

    val toolWindow = importUtpResultAction.getToolWindow(projectRule.project)
    assertThat(toolWindow.contentManager.contents).hasLength(1)
    assertThat(toolWindow.contentManager.contents[0].displayName).isEqualTo("Imported Android Test Results")
  }

  @Test
  fun importUtpResultPreCreateContentManager() {
    RunContentManager.getInstance(projectRule.project)
    val toolWindow = ToolWindowManager.getInstance(projectRule.project)
      .getToolWindow(ImportUtpResultAction.IMPORTED_TEST_WINDOW_ID)

    assertThat(toolWindow).isNull()

    val importUtpResultAction = ImportUtpResultAction()

    importUtpResultAction.parseResultsAndDisplay(temporaryFolder.newFile(), disposableRule.disposable, projectRule.project)
    val newToolWindow = importUtpResultAction.getToolWindow(projectRule.project)

    assertThat(newToolWindow.contentManager.contents).hasLength(1)
    assertThat(newToolWindow.contentManager.contents[0].displayName).isEqualTo("Imported Android Test Results")
  }

  @Test
  fun enableUtpResultSupport() {
    StudioFlags.UTP_TEST_RESULT_SUPPORT.override(true)
    val anActionEvent = AnActionEvent(null, { projectRule.project },
                                      ActionPlaces.UNKNOWN, Presentation(),
                                      ActionManager.getInstance(), 0)
    ImportUtpResultAction().update(anActionEvent)
    assertThat(anActionEvent.presentation.isEnabled).isTrue()
  }

  @Test
  fun createImportUtpResultAction() {
    projectRule.module.rootManager.contentRoots[0]
      .writeChild(
        "build/outputs/androidTest-results/connected/test-result.pb",
        createTestResultsProto().toByteArray()
      )

    val importActions = createImportUtpResultActionFromAndroidGradlePluginOutput(projectRule.project)

    assertThat(importActions).hasSize(1)
    assertThat(importActions[0].action.templateText).contains("ExampleInstrumentedTest - connected")
    assertThat(importActions[0].action.toolWindowDisplayName).contains("ExampleInstrumentedTest - connected")
  }

  @Test
  fun createImportUtpResultActionShouldPreferMergedResult() {
    projectRule.module.rootManager.contentRoots[0]
      .writeChild(
        "build/outputs/androidTest-results/connected/test-result.pb",
        createTestResultsProto("MergedResult").toByteArray()
      )
    projectRule.module.rootManager.contentRoots[0]
      .writeChild(
        "build/outputs/androidTest-results/connected/device1/test-result.pb",
        createTestResultsProto("Device1Result").toByteArray()
      )
    projectRule.module.rootManager.contentRoots[0]
      .writeChild(
        "build/outputs/androidTest-results/connected/device2/test-result.pb",
        createTestResultsProto("Device2Result").toByteArray()
      )

    val importActions = createImportUtpResultActionFromAndroidGradlePluginOutput(projectRule.project)

    assertThat(importActions).hasSize(1)
    assertThat(importActions[0].action.templateText).contains("MergedResult - connected")
  }

  @Test
  fun createImportUtpResultActionShouldPreferMergedResultButFallbackToIndividualResult() {
    projectRule.module.rootManager.contentRoots[0]
      .writeChild(
        "build/outputs/androidTest-results/connected/device1/test-result.pb",
        createTestResultsProto("Device1Result").toByteArray()
      )
    projectRule.module.rootManager.contentRoots[0]
      .writeChild(
        "build/outputs/androidTest-results/connected/device2/test-result.pb",
        createTestResultsProto("Device2Result").toByteArray()
      )

    val importActions = createImportUtpResultActionFromAndroidGradlePluginOutput(projectRule.project)

    assertThat(importActions).hasSize(2)
    assertThat(importActions[0].action.templateText).contains("Device1Result - connected")
    assertThat(importActions[1].action.templateText).contains("Device2Result - connected")
  }

  @Test
  fun createImportUtpResultActionWithFlavor() {
    projectRule.module.rootManager.contentRoots[0]
      .writeChild(
        "build/outputs/androidTest-results/connected/flavors/demo/test-result.pb",
        createTestResultsProto().toByteArray()
      )

    val importActions = createImportUtpResultActionFromAndroidGradlePluginOutput(projectRule.project)

    assertThat(importActions).hasSize(1)
    assertThat(importActions[0].action.templateText).contains("ExampleInstrumentedTest - demo - connected")
  }


  @Test
  fun createImportGradleManagedDeviceUtpResultAction() {
    projectRule.module.rootManager.contentRoots[0]
      .writeChild(
        "build/outputs/androidTest-results/managedDevice/test-result.pb",
        createTestResultsProto().toByteArray()
      )

    val importActions = createImportGradleManagedDeviceUtpResults(projectRule.project)

    assertThat(importActions).hasSize(1)
    assertThat(importActions[0].action.templateText).contains("ExampleInstrumentedTest - managed")
  }

  @Test
  fun createImportGradleManagedDeviceUtpResultActionWithFlavor() {
    projectRule.module.rootManager.contentRoots[0]
      .writeChild(
        "build/outputs/androidTest-results/managedDevice/flavors/demo/test-result.pb",
        createTestResultsProto().toByteArray()
      )

    val importActions = createImportGradleManagedDeviceUtpResults(projectRule.project)

    assertThat(importActions).hasSize(1)
    assertThat(importActions[0].action.templateText).contains("ExampleInstrumentedTest - demo - managed")
  }

  private fun createTestResultsProto(
    testClassName: String = "ExampleInstrumentedTest"
  ): TestSuiteResultProto.TestSuiteResult {
    return TextFormat.parse(
      """
      test_result {
        test_case {
          test_class: "$testClassName"
          test_package: "com.example.myapplication"
          test_method: "useAppContext"
          start_time {
            seconds: 1643664452
            nanos: 792000000
          }
          end_time {
            seconds: 1643664452
            nanos: 834000000
          }
        }
        test_status: PASSED
      }
      """,
      TestSuiteResultProto.TestSuiteResult::class.java
    )
  }
}