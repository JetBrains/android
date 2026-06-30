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
import java.time.Duration
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@RunsInEdt
class ImportUtilsTest {
  private val projectRule = ProjectRule()
  private val disposableRule = DisposableRule()
  private val temporaryDirectoryRule = TemporaryDirectory()

  @get:Rule val rules: RuleChain = RuleChain.outerRule(projectRule).around(EdtRule()).around(disposableRule).around(temporaryDirectoryRule)

  @Test
  fun importAndroidTestMatrixResultXmlFile_disallowsDtd() {
    val xxeFile = runWriteAction {
      val file = temporaryDirectoryRule.createVirtualDir("testDirXXE").createChildData(this, "xxe.xml")
      file.setBinaryContent(
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <!DOCTYPE foo [ <!ENTITY xxe SYSTEM "http://google.com"> ]>
        <androidTestMatrix>
          <device id="&xxe;" deviceName="Pixel_3a_XL_API_23" deviceType="LOCAL_EMULATOR" version="23">
          </device>
        </androidTestMatrix>
        """
          .trimIndent()
          .toByteArray(Charsets.UTF_8)
      )
      file
    }

    val succeeded = importAndroidTestMatrixResultXmlFile(projectRule.project, xxeFile)
    assertThat(succeeded).isFalse()
  }

  @Test
  fun importTestHistory() {
    val view = importXmlFile("testHistory")
    assertThat(view!!.myIsImportedResult).isTrue()
  }

  @Test
  fun importTestHistoryWithExecutionDuration() {
    val view = requireNotNull(importXmlFile("testHistoryWithExecutionDuration"))
    assertTrue(Duration.ofSeconds(5)) { view.testExecutionDurationOverride == Duration.ofMillis(2934) }
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
        Resources.toByteArray(Resources.getResource("com/android/tools/idea/testartifacts/instrumented/testsuite/export/${fileName}.xml"))
      )
    }

    lateinit var testSuiteView: AndroidTestSuiteView
    val succeeded =
      importAndroidTestMatrixResultXmlFile(projectRule.project, xmlFile) { env ->
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

  @Test
  fun importAndroidTestMatrixResultXmlFile_filtersAdditionalTestCaseArtifact() {
    val xmlFile = runWriteAction {
      val file = temporaryDirectoryRule.createVirtualDir("testDirImport").createChildData(this, "import.xml")
      file.setBinaryContent(
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <testrun name="Tests">
          <config configId="AndroidTestRunConfigurationType" name="Tests">
            <module name="app"/>
          </config>
          <androidTestMatrix>
            <device id="Pixel_3" deviceName="Pixel 3" deviceType="LOCAL_EMULATOR" version="28">
            </device>
            <testsuite deviceId="Pixel_3" testCount="1" result="PASSED">
              <testcase id="com.example.MyTestClass.testMethod" methodName="testMethod" className="MyTestClass" packageName="com.example" result="PASSED" logcat="" errorStackTrace="" startTimestampMillis="0" endTimestampMillis="100" benchmark="">
                <additionalTestCaseArtifact key="PreviewScreenshot.newImagePath" value="safe_relative/image.png" />
                <additionalTestCaseArtifact key="unauthorizedKey" value="safe_relative/image.png" />
                <additionalTestCaseArtifact key="PreviewScreenshot.refImagePath" value="\\attacker.evil\share\image.png" />
                <additionalTestCaseArtifact key="PreviewScreenshot.diffImagePath" value="../../../etc/passwd" />
                <additionalTestCaseArtifact key="deviceId" value="192.168.1.5:5555" />
              </testcase>
            </testsuite>
          </androidTestMatrix>
        </testrun>
        """
          .trimIndent()
          .toByteArray(Charsets.UTF_8)
      )
      file
    }

    var testSuiteView: AndroidTestSuiteView? = null
    val succeeded =
      importAndroidTestMatrixResultXmlFile(projectRule.project, xmlFile) { env ->
        testSuiteView = env.contentToReuse?.executionConsole as? AndroidTestSuiteView
      }
    assertThat(succeeded).isTrue()
    assertThat(testSuiteView).isNotNull()

    val results = testSuiteView!!.myResultsTableView.rootResultsNode.results
    val timeoutTime = System.currentTimeMillis() + 5000
    while (timeoutTime > System.currentTimeMillis()) {
      if (results.getAllTestCases().isNotEmpty()) {
        break
      }
      com.intellij.testFramework.PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
      Thread.sleep(10)
    }
    val testCase = results.getAllTestCases().first()

    // Allowed and safe keys should be imported successfully
    assertThat(testCase.additionalTestArtifacts["PreviewScreenshot.newImagePath"]).isEqualTo("safe_relative/image.png")
    // Unauthorized keys are allowed by design as they are arbitrary
    assertThat(testCase.additionalTestArtifacts["unauthorizedKey"]).isEqualTo("safe_relative/image.png")
    // Malicious/UNC paths in values should be blocked and ignored (applies to ALL keys)
    assertThat(testCase.additionalTestArtifacts["PreviewScreenshot.refImagePath"]).isNull()
    // Path traversal paths are allowed at ingestion-time (resolved and validated later by ScreenshotTestUtils.resolvePath)
    assertThat(testCase.additionalTestArtifacts["PreviewScreenshot.diffImagePath"]).isEqualTo("../../../etc/passwd")
    // Non-path keys containing colons (like deviceId) should be successfully imported
    assertThat(testCase.additionalTestArtifacts["deviceId"]).isEqualTo("192.168.1.5:5555")
  }
}
