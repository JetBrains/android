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
package org.jetbrains.android.refactoring

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.DisposerExplorer
import com.android.tools.idea.testing.TestProjectPaths.MIGRATE_TO_ANDROID_X
import com.android.tools.idea.testing.TestProjectPaths.MIGRATE_TO_ANDROID_X_KTS
import com.google.common.truth.Truth
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.ThrowableRunnable
import kotlin.text.Regex.Companion.escapeReplacement

/**
 * Remove any version numbers from the string to make testing comparison easier
 */
private fun String.removeVersions(): String =
  replace(Regex("\\d+\\.\\d+(\\.\\d+)?(-\\p{Alnum}+)?"), "V.V.V")

private fun String.replaceCompileSdkWith(version: String, isGroovy: Boolean) =
  replace(Regex("compileSdkVersion[(\\s].*"),
          escapeReplacement(if (isGroovy) "compileSdkVersion $version" else "compileSdkVersion($version)"))

abstract class MigrateToAndroidxGradleTestBase : AndroidGradleTestCase() {
  // Temporary instrumentation for b/148676784.
  override fun tearDown() {
    super.tearDown()
    val leak1 = DisposerExplorer.findFirst {
      it is TextEditor
    }
    Truth.assertThat(leak1).isNull()

    val leak2 = DisposerExplorer.findFirst {
      it.javaClass.name.startsWith("com.android.tools.idea.gradle.notification.ProjectSyncStatusNotificationProvider")
    }
    Truth.assertThat(leak2).isNull()
  }

  protected fun doTestVerifyPrerequisites(mainBuildScript: String, appBuildScript: String, isGroovy: Boolean) {
    val appGradleFile = myFixture.project.baseDir.findFileByRelativePath(appBuildScript)!!
    val appGradleContent = getTextForFile(appBuildScript)

    arrayOf("\"\$version_27\"", if (isGroovy) "ext.version_27" else "extra[\"version_27\"]").forEach {
      setFileContent(appGradleFile, appGradleContent.replaceCompileSdkWith(it, isGroovy))
      try {
        runProcessor(checkPrerequisites = true)
        fail("Prerequisites check should have failed for compileSdkVersion \"$it\"")
      }
      catch (e: RuntimeException) {
        assertEquals("You need to have compileSdk set to at least 28 in your module build.gradle to migrate to AndroidX.",
                     e.localizedMessage)
      }
    }

    setFileContent(appGradleFile, appGradleContent.replaceCompileSdkWith("28", isGroovy))

    // Downgrade the gradle plugin and check it fails
    val rootGradleFile = VfsUtil.findRelativeFile(myFixture.project.baseDir, mainBuildScript)!!

    // Use the GradleBuildModel to update property accordingly to the build language.
    val buildModel = GradleBuildModel.parseBuildFile(rootGradleFile, myFixture.project)
    val ext = buildModel.buildscript().ext()
    val gradle_version = ext.findProperty("gradle_version")
    gradle_version.setValue("3.0.1")
    WriteCommandAction.runWriteCommandAction(project) {
      buildModel.applyChanges()
    }

    setFileContent(rootGradleFile, getTextForFile(mainBuildScript)
      .replace(Regex("com.android.tools.build:gradle:.*${if (isGroovy) "'" else '"'}"),
               "com.android.tools.build:gradle:\\\$gradle_version${if (isGroovy) "'" else '"'}"))
    try {
      runProcessor(checkPrerequisites = true)
      fail("Prerequisites check should have failed for gradle version 3.0.1")
    }
    catch (e: RuntimeException) {
      assertEquals(
        "The gradle plugin version in your project build.gradle file needs to be set to at least com.android.tools.build:gradle:3.2.0 in order to migrate to AndroidX.",
        e.localizedMessage)
    }
  }

  protected fun doTestWarning() {
    loadProject(MIGRATE_TO_ANDROID_X)

    val warningMessage = """Before proceeding, we recommend that you make a backup of your project.
      |
      |Depending on your project dependencies, you might need to manually fix
      |some errors after the refactoring in order to successfully compile your project.
      |
      |Do you want to proceed with the migration?""".trimMargin()

    assertThrows(RuntimeException::class.java, warningMessage, ThrowableRunnable<RuntimeException> {
      // Do not disable the warning dialog to make sure it displays
      runProcessor(showWarningDialog = true)
    })
  }

  protected fun doTestExistingGradleProperties() {

    runWriteAction {
      // gradle.properties is created by test framework. We do not care about its content here since we never sync the project again
      // so we can simply overwrite it.
      val gradlePropertiesFile = project.baseDir.findChild("gradle.properties")!!
      gradlePropertiesFile.setBinaryContent("""
      # Preserve this comment and variable
      random.variable=true
      """.trimIndent().toByteArray(Charsets.UTF_8))
    }

    runProcessor()

    val gradleProperties = getTextForFile("gradle.properties")
    assertTrue(gradleProperties.contains("android.useAndroidX=true") &&
               gradleProperties.contains("android.enableJetifier=true"))
    assertTrue(gradleProperties.contains("# Preserve this comment and variable") &&
               gradleProperties.contains("random.variable=true"))
  }

  protected fun doTestMigrationRefactoring(
    mainBuildScript: String, appBuildScript: String, activityMain: String, mainActivityPath: String, expectedSequence: String) {
    runProcessor()

    val activityMain = getTextForFile(activityMain)
    val mainGradle = getTextForFile(mainBuildScript)
    val appGradle = getTextForFile(appBuildScript)

    assertEquals(2, mainGradle.lineSequence().filter { it.contains("google()") }.count())

    val implementationLines = appGradle.lineSequence()
      .map { it.trim() }
      .filter { it.startsWith("implementation") || it.contains("testVariable =") }
      .filterNot { it.contains("fileTree") }
      .map { it.removeVersions() } // Remove versions
      .joinToString(separator = "\n")
      .trimIndent()

    assertEquals(expectedSequence.trimIndent(), implementationLines)

    val mainActivityKt = getTextForFile(mainActivityPath)
    assertFalse(mainActivityKt.contains("android.support"))
    assertFalse(activityMain.contains("android.support"))
    val gradleProperties = getTextForFile("gradle.properties")
    assertTrue(gradleProperties.contains("android.useAndroidX=true") &&
               gradleProperties.contains("android.enableJetifier=true"))
    assertTrue(mainActivityKt.contains("import kotlinx.android.synthetic.main.activity_main.*"))
  }

  protected fun setFileContent(file: VirtualFile, content: String) {
    WriteCommandAction.runWriteCommandAction(project) {
      myFixture.openFileInEditor(file)
      val document = myFixture.editor.document
      document.setText(content)
      PsiDocumentManager.getInstance(project).commitDocument(document)
    }
  }

  protected fun runProcessor(showWarningDialog: Boolean = false, checkPrerequisites: Boolean = false) {
    MigrateToAndroidxHandler(showWarningDialog = showWarningDialog,
                             callSyncAfterMigration = false,
                             checkPrerequisites = checkPrerequisites
    )
      .invoke(project, null, null, null)
  }

  /**
   * Regression test for b/123303598
   */
  protected fun doTestBug123303598(buildFileName: String) {
    loadProject(MIGRATE_TO_ANDROID_X_KTS)

    val mainGradleFile = myFixture.project.baseDir.findFileByRelativePath(buildFileName)!!
    var mainGradleContent = getTextForFile(buildFileName)

    // Remove repositories blocks and check that we do not throw an NPE when evaluating the block
    mainGradleContent = mainGradleContent.replace(Regex("repositories \\{.*?AndroidGradleTestCase[\\n\\s]+}\n",
                                                        setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL)),
                                                  "")
    setFileContent(mainGradleFile, mainGradleContent)
    runProcessor(checkPrerequisites = false)
  }
}

/**
 * This class tests Migration to AndroidX for a Gradle project.
 */
class MigrateToAndroidxGradleKtsTest : MigrateToAndroidxGradleTestBase() {
  fun testMigrationRefactoringKts() {
    loadProject(MIGRATE_TO_ANDROID_X_KTS)
    val activityMain = "app/src/main/res/layout/activity_main.xml"
    val mainBuildScript = "build.gradle.kts"
    val appBuildScript = "app/build.gradle.kts"
    val mainActivityKt = "app/src/main/java/com/example/google/migratetoandroidxkts/MainActivity.kt"
    val expectedSequence = """
    val testVariable = "com.google.android.material:material:V.V.V"
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:+")
    implementation(mapOf("group" to "androidx.appcompat", "name" to "appcompat", "version" to "V.V.V"))
    implementation("androidx.constraintlayout:constraintlayout:V.V.V")
    implementation(testVariable)
      """
    doTestMigrationRefactoring(mainBuildScript, appBuildScript, activityMain, mainActivityKt, expectedSequence)
  }

  fun testExistingGradlePropertiesKts() {
    loadProject(MIGRATE_TO_ANDROID_X_KTS)
    doTestExistingGradleProperties()
  }

  fun testVerifyPrerequisitesKts() {
    loadProject(MIGRATE_TO_ANDROID_X_KTS)

    val mainBuildScript = "build.gradle.kts"
    val appBuildScript = "app/build.gradle.kts"
    doTestVerifyPrerequisites(mainBuildScript, appBuildScript, false)
  }

  fun testWarningKts() {
    loadProject(MIGRATE_TO_ANDROID_X_KTS)
    doTestWarning()
  }

  fun testBug123303598Kts() {
    loadProject(MIGRATE_TO_ANDROID_X_KTS)
    doTestBug123303598("build.gradle.kts")
  }
}

/**
 * This class tests Migration to AndroidX for a Gradle project.
 */
class MigrateToAndroidxGradleGroovyTest : MigrateToAndroidxGradleTestBase() {
  fun testMigrationRefactoringGroovy() {
    loadProject(MIGRATE_TO_ANDROID_X)
    val activityMain = "app/src/main/res/layout/activity_main.xml"
    val mainBuildScript = "build.gradle"
    val appBuildScript = "app/build.gradle"
    val mainActivityKt = "app/src/main/java/com/example/google/migratetoandroidx/MainActivity.kt"
    val expectedSequence = """
    def testVariable = 'com.google.android.material:material:V.V.V'
    implementation "org.jetbrains.kotlin:kotlin-stdlib-jdk7:+"
    implementation group: 'androidx.appcompat', name: 'appcompat', version: 'V.V.V'
    implementation 'androidx.constraintlayout:constraintlayout:V.V.V'
    implementation testVariable
      """
    doTestMigrationRefactoring(mainBuildScript, appBuildScript, activityMain,mainActivityKt, expectedSequence)
  }

  fun testExistingGradlePropertiesGroovy() {
    loadProject(MIGRATE_TO_ANDROID_X)
    doTestExistingGradleProperties()
  }

  fun testVerifyPrerequisitesGroovy() {
    loadProject(MIGRATE_TO_ANDROID_X)

    val mainBuildScript = "build.gradle"
    val appBuildScript = "app/build.gradle"
    doTestVerifyPrerequisites(mainBuildScript, appBuildScript, true)
  }

  fun testWarningGroovy() {
    loadProject(MIGRATE_TO_ANDROID_X)
    doTestWarning()
  }

  fun testBug123303598Groovy() {
    loadProject(MIGRATE_TO_ANDROID_X)
    doTestBug123303598("build.gradle")
  }
}
