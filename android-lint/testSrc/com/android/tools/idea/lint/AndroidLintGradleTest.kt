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
import com.android.tools.idea.lint.common.AndroidLintGradleDependencyInspection
import com.android.tools.idea.lint.common.AndroidLintInspectionBase
import com.android.tools.idea.lint.common.AndroidLintSimilarGradleDependencyInspection
import com.android.tools.idea.lint.common.AndroidLintUseTomlInsteadInspection
import com.android.tools.idea.lint.common.AndroidLintUseValueOfInspection
import com.android.tools.idea.lint.common.AndroidLintWrongGradleMethodInspection
import com.android.tools.idea.lint.inspections.AndroidLintAligned16KBInspection
import com.android.tools.idea.lint.inspections.AndroidLintDuplicateActivityInspection
import com.android.tools.idea.lint.inspections.AndroidLintMinSdkTooLowInspection
import com.android.tools.idea.lint.inspections.AndroidLintMockLocationInspection
import com.android.tools.idea.lint.inspections.AndroidLintNewApiInspection
import com.android.tools.idea.lint.inspections.AndroidLintSdCardPathInspection
import com.android.tools.idea.lint.inspections.AndroidLintUnusedResourcesInspection
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.lint.checks.GradleDetector
import com.intellij.analysis.AnalysisScope
import com.intellij.codeInspection.CommonProblemDescriptor
import com.intellij.codeInspection.GlobalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper
import com.intellij.codeInspection.reference.RefEntity
import com.intellij.codeInspection.ui.util.SynchronizedBidiMultiMap
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.InspectionTestUtil
import com.intellij.testFramework.createGlobalContextForTool
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
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

  fun `test lint warning in tests`() {
    // Test that, in a test project which enables lintOptions.checkTestSources, warnings
    // are flagged in unit test sources. This ensures that we're properly syncing lint options
    // into the driver; b/139490306.
    loadProject(TestProjectPaths.TEST_ARTIFACTS_LINT)
    val file = myFixture.loadFile("app/src/androidTest/java/google/testartifacts/ExampleTest.java")
    myFixture.checkLint(
      file,
      AndroidLintSdCardPathInspection(),
      "/sd|card",
      """
      Warning: Do not hardcode "/sdcard/"; use `Environment.getExternalStorageDirectory().getPath()` instead
          private String path = "/sdcard/foo"; // Deliberate lint warning
                                ~~~~~~~~~~~~~
          Fix: Suppress SdCardPath with an annotation
      """,
    )
  }

  fun `test mock locations`() {
    // MOCK locations are okay in debug manifests; they're not okay in main manifests.
    // This tests that the Gradle builder model is properly passed through to lint.
    loadProject(TestProjectPaths.TEST_ARTIFACTS_LINT)

    val debug = myFixture.loadFile("app/src/debug/AndroidManifest.xml")
    myFixture.checkLint(
      debug,
      AndroidLintMockLocationInspection(),
      "android.permission.ACCESS_|MOCK_LOCATION",
      "No warnings.",
    )
    val main = myFixture.loadFile("app/src/main/AndroidManifest.xml")

    myFixture.checkLint(
      main,
      AndroidLintMockLocationInspection(),
      "android.permission.ACCESS_|MOCK_LOCATION",
      """
      Error: Mock locations should only be requested in a test or debug-specific manifest file (typically `src/debug/AndroidManifest.xml`)
          <uses-permission android:name="android.permission.ACCESS_MOCK_LOCATION" />
                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          Fix: Move to debug-specific manifest
          Fix: Suppress: Add tools:ignore="MockLocation" attribute
      """,
    )
  }

  fun `test desugaring in library project`() {
    // Regression test for https://issuetracker.google.com/158189490
    loadProject(TestProjectPaths.TEST_ARTIFACTS_LINT)

    val debug = myFixture.loadFile("lib/src/main/java/com/example/lib/MyClass.kt")
    myFixture.checkLint(debug, AndroidLintNewApiInspection(), "LocalDate.n|ow", "No warnings.")
  }

  // app project with global desugaring disabled and android test desugaring enabled
  fun `test desugaring for instrumentation tests`() {
    loadProject(TestProjectPaths.TEST_ARTIFACTS_LINT_DESUGARING_ANDROID_TEST)

    val mainFile = myFixture.loadFile("app/src/main/java/google/testartifacts/ExampleMain.java")
    myFixture.checkLint(
      mainFile,
      AndroidLintNewApiInspection(),
      "LocalDate.n|ow",
      """
    Error: Call requires API level 26 (current min is 16): `java.time.LocalDate#now`
          LocalDate date = LocalDate.now();
                                     ~~~
        Fix: Surround with if (VERSION.SDK_INT >= VERSION_CODES.O) { ... }
        Fix: Add @RequiresApi(O) Annotation
        Fix: Suppress NewApi with an annotation
    """,
    )

    val unitTestFile = myFixture.loadFile("app/src/test/java/google/testartifacts/ExampleTest.java")
    myFixture.checkLint(
      unitTestFile,
      AndroidLintNewApiInspection(),
      "collection.st|ream",
      """
    Error: Call requires API level 24, or core library desugaring (current min is 16): `java.util.Collection#stream`
        java.util.stream.Stream<String> streamOfCollection = collection.stream();
                                                                        ~~~~~~
        Fix: Surround with if (VERSION.SDK_INT >= VERSION_CODES.N) { ... }
        Fix: Add @RequiresApi(N) Annotation
        Fix: Suppress NewApi with an annotation
    """,
    )

    val androidTestFile =
      myFixture.loadFile("app/src/androidTest/java/google/testartifacts/ExampleTest.java")
    myFixture.checkLint(
      androidTestFile,
      AndroidLintNewApiInspection(),
      "LocalDate.n|ow",
      "No warnings.",
    )
    myFixture.checkLint(
      androidTestFile,
      AndroidLintNewApiInspection(),
      "collection.st|ream",
      "No warnings.",
    )
  }

  fun testWarningsInNonAndroidLibrary() {
    // Make sure we get lint violations in java library modules as well
    loadProject(TestProjectPaths.TEST_ARTIFACTS_LINT)

    val debug = myFixture.loadFile("lib/src/main/java/com/example/lib/UseValueOf.java")
    myFixture.checkLint(
      debug,
      AndroidLintUseValueOfInspection(),
      "new Int|eger",
      """
      Warning: Use `Integer.valueOf(5)` instead
              Integer myInt = new Integer(5);
                              ~~~~~~~~~~~~~~
          Fix: Replace with valueOf()
          Fix: Suppress UseValueOf with an annotation
      """
        .trimIndent(),
    )
  }

  fun testUnusedResources() {
    loadProject(TestProjectPaths.UNUSED_RESOURCES_MULTI_MODULE)
    val inspection = AndroidLintUnusedResourcesInspection()
    myFixture.enableInspections(inspection)
    doGlobalInspectionTest(inspection, AnalysisScope(myFixture.project))
  }

  fun testVersionCatalogNestedProjects() {
    loadProject(TestProjectPaths.TEST_ARTIFACTS_VERSION_CATALOG_NESTED_PROJECTS)
    val appBuildFile = myFixture.loadFile("app/build.gradle.kts")
    myFixture.checkLint(
      appBuildFile,
      AndroidLintUseTomlInsteadInspection(),
      "com.android.support:appcompat-v|7:28.0.0",
      """
        Warning: Use version catalog instead
            implementation("com.android.support:appcompat-v7:28.0.0")
                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            Fix: Replace with new library catalog declaration for appcompat-v7
            Fix: Suppress UseTomlInstead with a comment
      """
        .trimIndent(),
    )
    val nestedAppBuildFile = myFixture.loadFile("app/nested/build.gradle.kts")
    myFixture.checkLint(
      nestedAppBuildFile,
      AndroidLintUseTomlInsteadInspection(),
      "com.android.support:appcompat-v|7:28.0.0",
      """
        Warning: Use version catalog instead
            implementation("com.android.support:appcompat-v7:28.0.0")
                           ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            Fix: Replace with new library catalog declaration for appcompat-v7
            Fix: Suppress UseTomlInstead with a comment
      """
        .trimIndent(),
    )
  }

  fun testVersionCatalogForSimilarDependencies() {
    loadProject(TestProjectPaths.TEST_SIMILAR_DEPENDENCIES_IN_VERSION_CATALOG)
    val appBuildFile = myFixture.loadFile("gradle/libs.versions.toml")
    myFixture.checkLint(
      appBuildFile,
      AndroidLintSimilarGradleDependencyInspection(),
      "androidx-core-ktx = { group = \"androidx.|core\", name = \"core-ktx\", version.ref = \"coreKtx\" }\n",
      """
        No warnings.
      """
        .trimIndent(),
    )
  }

  fun testTomlWarningFor16KbAlignment() {
    loadProject(TestProjectPaths.TEST_SIMILAR_DEPENDENCIES_IN_VERSION_CATALOG)
    val appBuildFile = myFixture.loadFile("gradle/libs.versions.toml")
    myFixture.checkLint(
      appBuildFile,
      AndroidLintAligned16KBInspection(),
      "module = \"androidx.datastore:|datastore-core-android\", version.ref = \"datastoreCoreAndroid\" }\n",
      """
      Warning: The native library `arm64-v8a/libdatastore_shared_counter.so` (from `androidx.datastore:datastore-core-android:1.1.0-alpha04`) is not 16 KB aligned
      androidx-datastore-core-android = { module = "androidx.datastore:datastore-core-android", version.ref = "datastoreCoreAndroid" }
                                         ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          Fix: Suppress Aligned16KB
      """
        .trimIndent(),
    )
  }

  fun testDuplicateActivityInspection() {
    loadProject(TestProjectPaths.TEST_LINT_DUPLICATE_ACTIVITY)
    val appBuildFile = myFixture.loadFile("app/src/main/AndroidManifest.xml")
    myFixture.checkLint(
      appBuildFile,
      AndroidLintDuplicateActivityInspection(),
      "android:name=\".Main|Activity\"> <!-- second -->",
      """
      Error: Duplicate registration for activity `com.example.myapplication.MainActivity`
                  android:name=".MainActivity"> <!-- second -->
                  ~~~~~~~~~~~~~~~~~~~~~~~~~~~~
          Fix: Suppress: Add tools:ignore="DuplicateActivity" attribute
      """
        .trimIndent(),
    )
  }

  fun testWrongGradleMethod() {
    loadProject(TestProjectPaths.TEST_LINT_DSL_ERRORS)
    val appBuildFile = myFixture.loadFile("app/build.gradle.kts")
    myFixture.checkLint(
      appBuildFile,
      AndroidLintWrongGradleMethodInspection(),
      "dep|endencies { // Wrong place: product flavor" to
        """
        Error: Suspicious receiver type; this does not apply to product flavors. This will apply to a receiver of type `Project`, found in one of the enclosing lambdas. Make sure it's declared in the right place in the file. If you wanted a product flavor specific dependency, use `demoImplementation` rather than `implementation` in the top level `dependencies` block.
                    dependencies { // Wrong place: product flavor
                    ~~~~~~~~~~~~
            Fix: Suppress WrongGradleMethod with a comment
        """,
      "dep|endencies { // Wrong place: build type" to
        """
        Error: Suspicious receiver type; this does not apply to build types. This will apply to a receiver of type `Project`, found in one of the enclosing lambdas. Make sure it's declared in the right place in the file. If you wanted a build type specific dependency, use `releaseImplementation` rather than `implementation` in the top level `dependencies` block.
                    dependencies { // Wrong place: build type
                    ~~~~~~~~~~~~
            Fix: Suppress WrongGradleMethod with a comment
        """,
      "kotlin|Options { // Wrong place: options" to
        """
        Error: Suspicious receiver type; this does not apply to the current receiver of type `ApplicationDefaultConfig`. This will apply to a receiver of type `BaseAppModuleExtension`, found in one of the enclosing lambdas. Make sure it's declared in the right place in the file.
                kotlinOptions { // Wrong place: options
                ~~~~~~~~~~~~~
            Fix: Suppress WrongGradleMethod with a comment
        """,
      "dep|endencies { // OK" to
        """
        No warnings.
        """,
    )
  }

  fun testCatalogMin() {
    // Checks that if we run batch inspection (called "global" inspection in the test infrastructure),
    // but the file scope is a single file, we end up taking the isolated mode path in the lint
    // checks.
    loadProject(TestProjectPaths.TEST_LINT_DSL_ERRORS)
    val tomlFile = myFixture.loadFile("gradle/libs.versions.toml")
    myFixture.checkLintBatch(
      AndroidLintMinSdkTooLowInspection(),
      """
      libs.versions.toml:3: Error: The value of minSdkVersion (15) is too low. It can be incremented without noticeably reducing the number of supported devices.
          Fix: Update minSdkVersion to 16
      """,
      AnalysisScope(tomlFile)
    )
  }


  fun testCompileSdkLocation() {
    loadProject(TestProjectPaths.TEST_LINT_DSL_ERRORS)
    val appBuildFile = myFixture.loadFile("app/build.gradle.kts")
    myFixture.checkLint(
      appBuildFile,
      AndroidLintGradleDependencyInspection(),
      "compileSdk|Version" to
        """
        Warning: A newer version of `compileSdkVersion` than 34 is available: ${GradleDetector.HIGHEST_KNOWN_STABLE_ANDROID_API}
            compileSdkVersion(34)
            ~~~~~~~~~~~~~~~~~~~~~
            Fix: Set compileSdkVersion to ${GradleDetector.HIGHEST_KNOWN_STABLE_ANDROID_API}
            Fix: Suppress GradleDependency with a comment
        """,
    )
  }

  fun doGlobalInspectionTest(
    tool: GlobalInspectionTool,
    scope: AnalysisScope,
  ): SynchronizedBidiMultiMap<RefEntity, CommonProblemDescriptor> {
    // We can't just override
    //    getTestDataDirectoryWorkspaceRelativePath() = "tools/adt/idea/android-lint/testData"
    // here because that interferes with the loadProject() operations running initially
    myFixture.testDataPath =
      File(File(getAndroidPluginHome()).parentFile, "android-lint/testData").path
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

fun JavaCodeInsightTestFixture.checkLint(
  psiFile: PsiFile,
  inspection: AndroidLintInspectionBase,
  caret: String,
  expected: String,
) {
  checkLint(psiFile, inspection, caret to expected)
}

fun JavaCodeInsightTestFixture.checkLint(
  psiFile: PsiFile,
  inspection: AndroidLintInspectionBase,
  vararg caretToExpectations: Pair<String, String>,
) {
  AndroidLintInspectionBase.setRegisterDynamicToolsFromTests(false)
  enableInspections(inspection)
  val fileText = psiFile.text

  for ((caret, expected) in caretToExpectations) {
    val sb = StringBuilder()
    val target = psiFile.findCaretOffset(caret)
    editor.caretModel.moveToOffset(target)
    val highlights =
      doHighlighting(HighlightSeverity.WARNING).asSequence().sortedBy { it.startOffset }
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

      highlight.findRegisteredQuickFix { desc, range ->
        val action = desc.action
        sb.append("    ")
        if (action.isAvailable(project, editor, psiFile)) {
          sb.append("Fix: ")
          sb.append(action.text)
        } else {
          sb.append("Disabled Fix: ")
          sb.append(action.text)
        }
        sb.append("\n")
        null
      }
    }

    if (sb.isEmpty()) {
      sb.append("No warnings.")
    }

    AndroidGradleTestCase.assertEquals(
      expected.trimIndent().trim(),
      sb.toString().trimIndent().trim(),
    )
  }
}


/**
 * Like [checkLint], but runs lint in batch mode (e.g. as a "global inspection")
 */
@Suppress("UnstableApiUsage")
fun JavaCodeInsightTestFixture.checkLintBatch(
  inspection: AndroidLintInspectionBase,
  expected: String,
  scope: AnalysisScope = AnalysisScope(project),
) {
  enableInspections(inspection)
  AndroidLintInspectionBase.setRegisterDynamicToolsFromTests(false)

  scope.invalidate();

  val wrapper = GlobalInspectionToolWrapper(inspection)
  val globalContext = createGlobalContextForTool(scope, project, listOf(wrapper));
  InspectionTestUtil.runTool(wrapper, scope, globalContext);

  fun PsiElement.getLineNumber(): Int {
    val doc = PsiDocumentManager.getInstance(project).getDocument(containingFile)
    return doc?.getLineNumber(textRange.startOffset) ?: -1
  }

  val descriptors = globalContext.getPresentation(wrapper).problemDescriptors;
  val sb = StringBuilder()
  for (descriptor in descriptors) {
    if (descriptor is ProblemDescriptor) {
      val element = descriptor.psiElement
      sb.append(element.containingFile.virtualFile.name).append(":").append(element.getLineNumber()).append(": ")
      val highlightType = descriptor.highlightType
      val severity = when (highlightType) {
        ProblemHighlightType.ERROR,
        ProblemHighlightType.GENERIC_ERROR,
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING -> "Error"
        ProblemHighlightType.WARNING -> "Warning"
        ProblemHighlightType.INFORMATION,
        ProblemHighlightType.WEAK_WARNING -> "Info"
        else -> highlightType.name.lowercase(Locale.ROOT).capitalize()
      }
      sb.append(severity).append(": ")
      sb.append(descriptor.toString().removePrefix("<html>").removeSuffix("</html>").trim()).append("\n")
      val fixes = descriptor.fixes
      if (fixes != null) {
        for (fix in fixes) {
          sb.append("    Fix: ")
          sb.append(fix.name)
          sb.append("\n")
        }
      }
      if (sb.isEmpty()) {
        sb.append("No warnings.")
      }
    } else {
      fail("Unexpected descriptor type $descriptor)")
    }
  }
  assertEquals(expected.trimIndent().trim(), sb.toString().trim())
}
