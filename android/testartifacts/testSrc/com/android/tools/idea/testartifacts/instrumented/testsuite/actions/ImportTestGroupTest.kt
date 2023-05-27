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
package com.android.tools.idea.testartifacts.instrumented.testsuite.actions

import com.android.flags.junit.FlagRule
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.flags.StudioFlags
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.android.tools.idea.protobuf.TextFormat
import com.google.testing.platform.proto.api.core.TestSuiteResultProto
import com.intellij.execution.TestStateStorage
import com.intellij.execution.testframework.sm.TestHistoryConfiguration
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.rootManager
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.writeChild
import com.intellij.util.text.DateFormatUtil
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import java.util.Date

/**
 * Unit test for [ImportTestGroup].
 */
@RunWith(JUnit4::class)
@RunsInEdt
class ImportTestGroupTest {

  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()
  private val temporaryFolder = TemporaryFolder()

  private val importTestGroup = ImportTestGroup(MoreExecutors.newDirectExecutorService())

  @get:Rule
  val rules: RuleChain = RuleChain
    .outerRule(projectRule)
    .around(EdtRule())
    .around(disposableRule)
    .around(temporaryFolder)
    .around(FlagRule(StudioFlags.UTP_TEST_RESULT_SUPPORT))

  @get:Rule
  var mockitoRule: MockitoRule = MockitoJUnit.rule()

  @Mock
  private lateinit var mockActionEvent: AnActionEvent

  @Before
  fun setupMocks() {
    TestStateStorage.getTestHistoryRoot(projectRule.project).mkdirs()
    whenever(mockActionEvent.project).thenReturn(projectRule.project)
  }

  @Test
  fun historyShouldBeSortedByTimestamp() {
    createIntelliJHistoryXml("intelliJHistory1", startTimeMillis = 3000)
    createIntelliJHistoryXml("intelliJHistory2", startTimeMillis = 1000)
    createTestResultsProto("utpProto", startTimeMillis = 2000)

    importTestGroup.getChildren(mockActionEvent) // Timestamp map will be updated asynchronously.
    val actions = importTestGroup.getChildren(mockActionEvent).map {
      it.templateText ?: ""
    }

    assertThat(actions).containsExactly(
      "intelliJHistory1",
      "utpProto - connected (${DateFormatUtil.formatDateTime(Date(2000))})",
      "intelliJHistory2",
    )
  }

  @Test
  fun historyItemShouldBeDeduplicatedByTimestamp() {
    createIntelliJHistoryXml("intelliJHistory1", startTimeMillis = 1000)
    createTestResultsProto("utpProto", startTimeMillis = 1000)

    importTestGroup.getChildren(mockActionEvent) // Timestamp map will be updated asynchronously.
    val actions = importTestGroup.getChildren(mockActionEvent).map {
      it.templateText ?: ""
    }

    assertThat(actions).containsExactly("intelliJHistory1")
  }

  private fun createIntelliJHistoryXml(name: String, startTimeMillis: Long) {
    TestStateStorage.getTestHistoryRoot(projectRule.project).resolve(name).writeText(
      """<?xml version="1.0" encoding="UTF-8"?>
      <testrun duration="3" name="ExampleInstrumentedTest">
          <count name="total" value="1"/>
          <count name="passed" value="1"/>
          <androidTestMatrix executionDuration="9380">
              <device id="emulator-5556" deviceName="Pixel_5_API_31" deviceType="LOCAL_EMULATOR" version="31">
              </device>
              <testsuite deviceId="emulator-5556" testCount="1" result="PASSED">
                  <testcase
                      id="com.example.gmdshardedtestexample.ExampleInstrumentedTest.useAppContext"
                      methodName="useAppContext"
                      className="ExampleInstrumentedTest"
                      packageName="com.example.gmdshardedtestexample"
                      result="PASSED"
                      logcat=""
                      errorStackTrace=""
                      startTimestampMillis="$startTimeMillis"
                      endTimestampMillis="${startTimeMillis + 1}"
                      benchmark=""/>
              </testsuite>
          </androidTestMatrix>
      </testrun>
      """
    )
    TestHistoryConfiguration.getInstance(projectRule.project).registerHistoryItem(name, "configName", "configId")
  }

  private fun createTestResultsProto(name: String, startTimeMillis: Long) {
    val resultProto = TextFormat.parse(
      """
      test_result {
        test_case {
          test_class: "$name"
          test_package: "com.example.myapplication"
          test_method: "useAppContext"
          start_time {
            nanos: ${startTimeMillis * 1000L * 1000L}
          }
          end_time {
            nanos: ${(startTimeMillis + 1) * 1000L * 1000L}
          }
        }
        test_status: PASSED
      }
      """,
      TestSuiteResultProto.TestSuiteResult::class.java
    )
    projectRule.module.rootManager.contentRoots[0]
      .writeChild(
        "build/outputs/androidTest-results/connected/test-result.pb",
        resultProto.toByteArray()
      )
  }
}