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
package com.android.tools.idea.testartifacts.instrumented.testsuite.export

import com.android.tools.idea.testartifacts.instrumented.AndroidTestRunConfiguration
import com.android.tools.idea.testartifacts.instrumented.testsuite.view.AndroidTestSuiteView
import com.google.common.io.Resources
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TemporaryDirectory
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.time.Duration

@RunWith(JUnit4::class)
@RunsInEdt
class ImportUtilsTest {
  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()
  private val temporaryDirectoryRule = TemporaryDirectory()

  @get:Rule
  val rules: RuleChain = RuleChain
    .outerRule(projectRule)
    .around(EdtRule())
    .around(disposableRule)
    .around(temporaryDirectoryRule)

  @Test
  fun importTestHistory() {
    val view = importXmlFile("testHistory")
    assertThat(view!!.myIsImportedResult).isTrue()
  }

  @Test
  fun importTestHistoryWithExecutionDuration() {
    val view = requireNotNull(importXmlFile("testHistoryWithExecutionDuration"))
    assertTrue(Duration.ofSeconds(5)) {
      view.testExecutionDurationOverride == Duration.ofMillis(2934)
    }
  }

  @Test
  fun importTestHistoryContainsInvalidChar() {
    importXmlFile("testHistoryContainsInvalidChar", expectedToSuccess = false)
  }

  private fun assertTrue(timeout: Duration, conditionFunc: () -> Boolean) {
    val timeoutTime = System.currentTimeMillis() + timeout.toMillis()
    while (timeoutTime > System.currentTimeMillis()) {
      if (conditionFunc()) {
        return
      }
      Thread.sleep(10)
    }
    assertThat(conditionFunc()).isTrue()
  }

  private fun importXmlFile(fileName: String, expectedToSuccess: Boolean = true): AndroidTestSuiteView? {
    lateinit var xmlFile: VirtualFile
    runWriteAction {
      val inputDir = temporaryDirectoryRule.createVirtualDir("inputDir")
      xmlFile = inputDir.createChildData(this, "${fileName}.xml")
      xmlFile.setBinaryContent(
        Resources.toByteArray(
          Resources.getResource("com/android/tools/idea/testartifacts/instrumented/testsuite/export/${fileName}.xml")))
    }

    lateinit var testSuiteView: AndroidTestSuiteView
    val succeeded = importAndroidTestMatrixResultXmlFile(projectRule.project, xmlFile) { env ->
      testSuiteView = requireNotNull(env.contentToReuse?.executionConsole as? AndroidTestSuiteView)
      val runProfile = requireNotNull(env.runProfile as? ImportAndroidTestMatrixRunProfile)
      assert(runProfile.initialConfiguration is AndroidTestRunConfiguration)
    }
    return if (expectedToSuccess) {
      assertThat(succeeded).isTrue()
      testSuiteView
    } else {
      assertThat(succeeded).isFalse()
      null
    }
  }
}