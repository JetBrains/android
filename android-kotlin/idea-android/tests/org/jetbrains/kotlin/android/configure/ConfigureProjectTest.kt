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

import com.android.testutils.TestUtils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.io.FileUtilRt.loadFile
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.testFramework.LightCodeInsightTestCase
import org.jetbrains.android.refactoring.setAndroidxProperties
import org.jetbrains.kotlin.idea.configuration.createConfigureKotlinNotificationCollector
import org.jetbrains.kotlin.android.InTextDirectivesUtils.findStringWithPrefixes
import org.jetbrains.kotlin.android.KotlinTestUtils.assertEqualsToFile
import java.io.File

abstract class ConfigureProjectTest : LightCodeInsightTestCase() {

  companion object {
    private const val DEFAULT_VERSION = "default_version"
    private const val GRADLE_DIR = "idea-android/testData/configuration/android-gradle"
    private const val GSK_DIR = "idea-android/testData/configuration/android-gsk"
  }

  fun doTest(path: String, extension: String, useAndroidX: Boolean = false) {
    val testRoot = TestUtils.getWorkspaceFile("tools/adt/idea/android-kotlin")
    val file = File(testRoot, "${path}_before.$extension")
    val fileText = loadFile(file, CharsetToolkit.UTF8, true)
    configureFromFileText(file.name, fileText)

    val versionFromFile = findStringWithPrefixes(getFile().text, "// VERSION:")
    val version = versionFromFile ?: DEFAULT_VERSION

    val project = getProject()
    val collector = createConfigureKotlinNotificationCollector(project)

    if (useAndroidX) {
      // Enable AndroidX
      ApplicationManager.getApplication().runWriteAction {
        project.setAndroidxProperties()
      }
    }

    val configurator = KotlinAndroidGradleModuleConfigurator()
    configurator.configureModule(getModule(), getFile(), true, version, collector, mutableListOf())
    configurator.configureModule(getModule(), getFile(), false, version, collector, mutableListOf())

    collector.showNotification()

    val afterFile = File(testRoot, "${path}_after.$extension")
    assertEqualsToFile(afterFile, getFile().text.replace(version, "\$VERSION$"))

    if (useAndroidX) {
      // Disable AndroidX
      ApplicationManager.getApplication().runWriteAction {
        project.setAndroidxProperties("false")
      }
    }
  }

  class AndroidGradle : ConfigureProjectTest() {
    fun testAndroidStudioDefault()                 = doTest("$GRADLE_DIR/androidStudioDefault", "gradle")
    fun testAndroidStudioDefaultShapshot()         = doTest("$GRADLE_DIR/androidStudioDefaultShapshot", "gradle")
    fun testAndroidStudioDefaultWithAndroidX()     = doTest("$GRADLE_DIR/androidStudioDefaultWithAndroidX", "gradle", true)
    fun testBuildConfigs()                         = doTest("$GRADLE_DIR/buildConfigs", "gradle")
    fun testEmptyDependencyList()                  = doTest("$GRADLE_DIR/emptyDependencyList", "gradle")
    fun testEmptyFile()                            = doTest("$GRADLE_DIR/emptyFile", "gradle")
    fun testHelloWorld()                           = doTest("$GRADLE_DIR/helloWorld", "gradle")
    fun testLibraryFile()                          = doTest("$GRADLE_DIR/libraryFile", "gradle")
    fun testMissedApplyAndroidStatement()          = doTest("$GRADLE_DIR/missedApplyAndroidStatement", "gradle")
    fun testMissedBuildscriptBlock()               = doTest("$GRADLE_DIR/missedBuildscriptBlock", "gradle")
    fun testMissedRepositoriesInBuildscriptBlock() = doTest("$GRADLE_DIR/missedRepositoriesInBuildscriptBlock", "gradle")
    fun testProductFlavor()                        = doTest("$GRADLE_DIR/productFlavor", "gradle")
  }

  class GradleExamples : ConfigureProjectTest() {
    fun testGradleExample0()  = doTest("$GRADLE_DIR/gradleExamples/gradleExample0", "gradle")
    fun testGradleExample18() = doTest("$GRADLE_DIR/gradleExamples/gradleExample18", "gradle")
    fun testGradleExample22() = doTest("$GRADLE_DIR/gradleExamples/gradleExample22", "gradle")
    fun testGradleExample44() = doTest("$GRADLE_DIR/gradleExamples/gradleExample44", "gradle")
    fun testGradleExample5()  = doTest("$GRADLE_DIR/gradleExamples/gradleExample5", "gradle")
    fun testGradleExample50() = doTest("$GRADLE_DIR/gradleExamples/gradleExample50", "gradle")
    fun testGradleExample58() = doTest("$GRADLE_DIR/gradleExamples/gradleExample58", "gradle")
    fun testGradleExample65() = doTest("$GRADLE_DIR/gradleExamples/gradleExample65", "gradle")
    fun testGradleExample8()  = doTest("$GRADLE_DIR/gradleExamples/gradleExample8", "gradle")
  }

  class AndroidGsk : ConfigureProjectTest() {
    fun testEmptyFile()  = doTest("$GSK_DIR/emptyFile", "gradle.kts")
    fun testHelloWorld() = doTest("$GSK_DIR/helloWorld", "gradle.kts")
  }
}
