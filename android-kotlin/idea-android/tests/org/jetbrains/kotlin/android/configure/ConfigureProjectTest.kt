/*
 * Copyright (C) 2018 The Android Open Source Project
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
package org.jetbrains.kotlin.android.configure

import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.idea.testing.AndroidProjectRule.Companion.withSdk
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.io.FileUtilRt.loadFile
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.android.refactoring.setAndroidxProperties
import org.jetbrains.kotlin.android.InTextDirectivesUtils.findStringWithPrefixes
import org.jetbrains.kotlin.android.KotlinTestUtils.assertEqualsToFile
import org.jetbrains.kotlin.idea.compiler.configuration.IdeKotlinVersion
import org.jetbrains.kotlin.idea.configuration.NotificationMessageCollector
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.junit.Assert
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File

@Ignore
@RunWith(JUnit4::class)
abstract class ConfigureProjectTest {

    protected val projectRule = withSdk() //onDisk()
    @get:Rule
    val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())

    protected lateinit var buildFile: VirtualFile

  companion object {
    // Note: this default version was chosen arbitrarily based on current test expectations.
    private const val DEFAULT_VERSION = "1.1.0"
    private const val GRADLE_DIR = "idea-android/testData/configuration/android-gradle"
    private const val GSK_DIR = "idea-android/testData/configuration/android-gsk"
  }

  fun doTest(path: String, extension: String, useAndroidX: Boolean = false) {
    runWriteAction {
      buildFile = projectRule.fixture.tempDirFixture.createFile("build.${extension}")
      Assert.assertTrue(buildFile.isWritable)
    }
    val testRoot = resolveWorkspacePath("tools/adt/idea/android-kotlin").toFile()
    val file = File(testRoot, "${path}_before.$extension")
    val fileText = loadFile(file, CharsetToolkit.UTF8, true)
    runWriteAction {
      VfsUtil.saveText(buildFile, fileText)
    }

    val versionFromFile = findStringWithPrefixes(fileText, "// VERSION:")
    val rawVersion = versionFromFile ?: DEFAULT_VERSION
    val version = IdeKotlinVersion.get(rawVersion)

    val project = projectRule.project
    val collector = NotificationMessageCollector.create(project)

    if (useAndroidX) {
      // Enable AndroidX
      WriteCommandAction.runWriteCommandAction(project) {
        project.setAndroidxProperties()
      }
    }

    val configurator = KotlinAndroidGradleModuleConfigurator()
    configurator.configureModule(projectRule.module, buildFile.toPsiFile(project)!!, true, version, collector, mutableListOf())
    configurator.configureModule(projectRule.module, buildFile.toPsiFile(project)!!, false, version, collector, mutableListOf())

    collector.showNotification()

    val afterFile = File(testRoot, "${path}_after.$extension")
    assertEqualsToFile(afterFile, VfsUtil.loadText(buildFile).replace(rawVersion, "\$VERSION$"))

    if (useAndroidX) {
      // Disable AndroidX
      WriteCommandAction.runWriteCommandAction(project) {
        project.setAndroidxProperties("false")
      }
    }
  }

  @RunsInEdt
  class AndroidGradle : ConfigureProjectTest() {
    @Test fun testAndroidStudioDefault()                 = doTest("$GRADLE_DIR/androidStudioDefault", "gradle")
    @Test fun testAndroidStudioDefaultShapshot()         = doTest("$GRADLE_DIR/androidStudioDefaultShapshot", "gradle")
    @Test fun testAndroidStudioDefaultWithAndroidX()     = doTest("$GRADLE_DIR/androidStudioDefaultWithAndroidX", "gradle", true)
    @Test fun testBuildConfigs()                         = doTest("$GRADLE_DIR/buildConfigs", "gradle")
    @Test fun testEmptyDependencyList()                  = doTest("$GRADLE_DIR/emptyDependencyList", "gradle")
    @Test fun testEmptyFile()                            = doTest("$GRADLE_DIR/emptyFile", "gradle")
    @Test fun testHelloWorld()                           = doTest("$GRADLE_DIR/helloWorld", "gradle")
    @Test fun testLibraryFile()                          = doTest("$GRADLE_DIR/libraryFile", "gradle")
    @Test fun testMissedApplyAndroidStatement()          = doTest("$GRADLE_DIR/missedApplyAndroidStatement", "gradle")
    @Test fun testMissedBuildscriptBlock()               = doTest("$GRADLE_DIR/missedBuildscriptBlock", "gradle")
    @Test fun testMissedRepositoriesInBuildscriptBlock() = doTest("$GRADLE_DIR/missedRepositoriesInBuildscriptBlock", "gradle")
    @Test fun testProductFlavor()                        = doTest("$GRADLE_DIR/productFlavor", "gradle")
  }

  @RunsInEdt
  class GradleExamples : ConfigureProjectTest() {
    @Test fun testGradleExample0()  = doTest("$GRADLE_DIR/gradleExamples/gradleExample0", "gradle")
    @Test fun testGradleExample18() = doTest("$GRADLE_DIR/gradleExamples/gradleExample18", "gradle")
    @Test fun testGradleExample22() = doTest("$GRADLE_DIR/gradleExamples/gradleExample22", "gradle")
    @Test fun testGradleExample44() = doTest("$GRADLE_DIR/gradleExamples/gradleExample44", "gradle")
    @Test fun testGradleExample5()  = doTest("$GRADLE_DIR/gradleExamples/gradleExample5", "gradle")
    @Test fun testGradleExample50() = doTest("$GRADLE_DIR/gradleExamples/gradleExample50", "gradle")
    @Test fun testGradleExample58() = doTest("$GRADLE_DIR/gradleExamples/gradleExample58", "gradle")
    @Test fun testGradleExample65() = doTest("$GRADLE_DIR/gradleExamples/gradleExample65", "gradle")
    @Test fun testGradleExample8()  = doTest("$GRADLE_DIR/gradleExamples/gradleExample8", "gradle")
  }

  @RunsInEdt
  class AndroidGsk : ConfigureProjectTest() {
    @Test fun testEmptyFile()  = doTest("$GSK_DIR/emptyFile", "gradle.kts")
    @Test fun testHelloWorld() = doTest("$GSK_DIR/helloWorld", "gradle.kts")
  }
}
