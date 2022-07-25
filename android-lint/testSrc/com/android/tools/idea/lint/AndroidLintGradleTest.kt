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
package com.android.tools.idea.lint

import com.android.tools.analytics.AnalyticsSettings.setInstanceForTest
import com.android.tools.analytics.AnalyticsSettingsData
import com.android.tools.idea.lint.common.AndroidLintInspectionBase
import com.android.tools.idea.lint.common.AndroidLintUseValueOfInspection
import com.android.tools.idea.lint.inspections.AndroidLintMockLocationInspection
import com.android.tools.idea.lint.inspections.AndroidLintNewApiInspection
import com.android.tools.idea.lint.inspections.AndroidLintSdCardPathInspection
import com.android.tools.idea.lint.inspections.AndroidLintUnusedResourcesInspection
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.intellij.analysis.AnalysisScope
import com.intellij.codeInspection.CommonProblemDescriptor
import com.intellij.codeInspection.GlobalInspectionTool
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper
import com.intellij.codeInspection.reference.RefEntity
import com.intellij.codeInspection.ui.util.SynchronizedBidiMultiMap
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import java.io.File
import java.util.Locale

class AndroidLintGradleTest : AndroidGradleTestCase() {
  override fun setUp() {
    super.setUp()
    val analyticsSettings = AnalyticsSettingsData()
    analyticsSettings.optedIn = false
    setInstanceForTest(analyticsSettings)
    myFixture.allowTreeAccessForAllFiles()
  }

  fun testLintWarningInTests() {
    // Test that, in a test project which enables lintOptions.checkTestSources, warnings
    // are flagged in unit test sources. This ensures that we're properly syncing lint options
    // into the driver; b/139490306.
    loadProject(TestProjectPaths.TEST_ARTIFACTS_LINT)
    val file = myFixture.loadFile("app/src/androidTest/java/google/testartifacts/ExampleTest.java")
    myFixture.checkLint(file, AndroidLintSdCardPathInspection(), "/sd|card",
      """
      Warning: Do not hardcode "/sdcard/"; use `Environment.getExternalStorageDirectory().getPath()` instead
          private String path = "/sdcard/foo"; // Deliberate lint warning
                                ~~~~~~~~~~~~~
          Fix: Suppress SdCardPath with an annotation
      """
    )
  }

  fun testMockLocations() {
    // MOCK locations are okay in debug manifests; they're not okay in main manifests.
    // This tests that the Gradle builder model is properly passed through to lint.
    loadProject(TestProjectPaths.TEST_ARTIFACTS_LINT)

    val debug = myFixture.loadFile("app/src/debug/AndroidManifest.xml")
    myFixture.checkLint(debug, AndroidLintMockLocationInspection(), "android.permission.ACCESS_|MOCK_LOCATION",
                        "No warnings."
    )
    val main = myFixture.loadFile("app/src/main/AndroidManifest.xml")

    myFixture.checkLint(main, AndroidLintMockLocationInspection(), "android.permission.ACCESS_|MOCK_LOCATION",
      """
      Error: Mock locations should only be requested in a test or debug-specific manifest file (typically `src/debug/AndroidManifest.xml`)
          <uses-permission android:name="android.permission.ACCESS_MOCK_LOCATION" />
                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          Fix: Move to debug-specific manifest
          Fix: Suppress: Add tools:ignore="MockLocation" attribute
      """
    )
  }

  fun testLibraryDesugaringInLibrary() {
    // Regression test for https://issuetracker.google.com/158189490
    loadProject(TestProjectPaths.TEST_ARTIFACTS_LINT)

    val debug = myFixture.loadFile("lib/src/main/java/com/example/lib/MyClass.kt")
    myFixture.checkLint(debug, AndroidLintNewApiInspection(), "LocalDate.n|ow",
                        "No warnings."
    )
  }

  fun testWarningsInNonAndroidLibrary() {
    // Make sure we get lint violations in java library modules as well
    loadProject(TestProjectPaths.TEST_ARTIFACTS_LINT)

    val debug = myFixture.loadFile("lib/src/main/java/com/example/lib/UseValueOf.java")
    myFixture.checkLint(
      debug, AndroidLintUseValueOfInspection(), "new Int|eger",
      """
      Warning: Use `Integer.valueOf(5)` instead
              Integer myInt = new Integer(5);
                              ~~~~~~~~~~~~~~
          Fix: Replace with valueOf()
          Fix: Suppress UseValueOf with an annotation
      """.trimIndent()
    )
  }

  fun testUnusedResources() {
    loadProject(TestProjectPaths.UNUSED_RESOURCES_MULTI_MODULE)
    val inspection = AndroidLintUnusedResourcesInspection()
    myFixture.enableInspections(inspection)
    doGlobalInspectionTest(inspection, AnalysisScope(myFixture.project))
  }

  fun doGlobalInspectionTest(
    tool: GlobalInspectionTool,
    scope: AnalysisScope): SynchronizedBidiMultiMap<RefEntity, CommonProblemDescriptor> {
    // We can't just override
    //    getTestDataDirectoryWorkspaceRelativePath() = "tools/adt/idea/android-lint/testData"
    // here because that interferes with the loadProject() operations running initially
    myFixture.testDataPath = File(File(getAndroidPluginHome()).parentFile, "android-lint/testData").path
    val testDir = "/lint/global/${getTestName(true)}"
    return super.doGlobalInspectionTest(GlobalInspectionToolWrapper(tool), testDir, scope)
  }
}

fun JavaCodeInsightTestFixture.loadFile(filePath: String): PsiFile {
  val file = project.guessProjectDir()!!.findFileByRelativePath(filePath)
  AndroidGradleTestCase.assertNotNull("$filePath not found in project", file)
  openFileInEditor(file!!)
  val psiFile = PsiManager.getInstance(project).findFile(file)
  AndroidGradleTestCase.assertNotNull(psiFile)
  return psiFile!!
}

fun PsiFile.findCaretOffset(caret: String): Int {
  val delta = caret.indexOf("|")
  if (delta == -1) AndroidGradleTestCase.fail("$name does not contain caret marker, |")
  val context = caret.substring(0, delta) + caret.substring(delta + 1)
  val index = text.indexOf(context)
  if (index == -1) AndroidGradleTestCase.fail("$name does not contain $context")
  return index + delta
}

fun JavaCodeInsightTestFixture.checkLint(psiFile: PsiFile, inspection: AndroidLintInspectionBase, caret: String, expected: String) {
  AndroidLintInspectionBase.setRegisterDynamicToolsFromTests(false)
  enableInspections(inspection)
  val fileText = psiFile.text
  val sb = StringBuilder()
  val target = psiFile.findCaretOffset(caret)
  val highlights = doHighlighting(HighlightSeverity.WARNING).asSequence().sortedBy { it.startOffset }
  for (highlight in highlights) {
    val startIndex = highlight.startOffset
    val endOffset = highlight.endOffset
    if (target < startIndex || target > endOffset) {
      continue
    }
    val description = highlight.description
    val severity = highlight.severity
    sb.append(severity.name.lowercase(Locale.ROOT).capitalize()).append(": ")
    sb.append(description).append("\n")

    val lineStart = fileText.lastIndexOf("\n", startIndex).let { if (it == -1) 0 else it + 1 }
    val lineEnd = fileText.indexOf("\n", startIndex).let { if (it == -1) fileText.length else it }
    sb.append(fileText.substring(lineStart, lineEnd)).append("\n")
    val rangeEnd = if (lineEnd < endOffset) lineEnd else endOffset
    for (i in lineStart until startIndex) sb.append(" ")
    for (i in startIndex until rangeEnd) sb.append("~")
    sb.append("\n")

    for (pair in highlight.quickFixActionRanges) {
      val action = pair.first.action
      sb.append("    ")
      if (action.isAvailable(project, editor, psiFile)) {
        sb.append("Fix: ")
        sb.append(action.text)
      }
      else {
        sb.append("Disabled Fix: ")
        sb.append(action.text)
      }
      sb.append("\n")
    }
  }

  if (sb.isEmpty()) {
    sb.append("No warnings.")
  }

  AndroidGradleTestCase.assertEquals(expected.trimIndent().trim(), sb.toString().trimIndent().trim())
}

