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

import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.DisposerExplorer
import com.android.tools.idea.testing.TestProjectPaths.MIGRATE_TO_ANDROID_X
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

private fun String.replaceCompileSdkWith(version: String) =
  replace(Regex("compileSdkVersion .*"), escapeReplacement("compileSdkVersion $version"))

/**
 * This class tests Migration to AndroidX for a Gradle project.
 */
class MigrateToAndroidxGradleTest : AndroidGradleTestCase() {
  // Temporary instrumentation for b/129308279.
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

  fun testMigrationRefactoring() {
    loadProject(MIGRATE_TO_ANDROID_X)
    runProcessor()

    val activityMain = getTextForFile("app/src/main/res/layout/activity_main.xml")
    val mainGradle = getTextForFile("build.gradle")
    val appGradle = getTextForFile("app/build.gradle")

    assertEquals(2, mainGradle.lineSequence().filter { it.contains("google()") }.count())

    val implementationLines = appGradle.lineSequence()
      .map { it.trim() }
      .filter { it.startsWith("implementation") || it.startsWith("def testVariable") }
      .filterNot { it.contains("fileTree") }
      .map { it.removeVersions() } // Remove versions
      .sorted()
      .joinToString(separator = "\n")
      .trimIndent()

    assertEquals("""
    def testVariable = 'com.google.android.material:material:V.V.V'
    implementation "org.jetbrains.kotlin:kotlin-stdlib-jdk7:+"
    implementation 'androidx.constraintlayout:constraintlayout:V.V.V'
    implementation group: 'androidx.appcompat', name: 'appcompat', version: 'V.V.V'
    implementation testVariable
      """.trimIndent(), implementationLines)

    val mainActivityKt = getTextForFile("app/src/main/java/com/example/google/migratetoandroidx/MainActivity.kt")
    assertFalse(mainActivityKt.contains("android.support"))
    assertFalse(activityMain.contains("android.support"))
    val gradleProperties = getTextForFile("gradle.properties")
    assertTrue(gradleProperties.contains("android.useAndroidX=true") &&
               gradleProperties.contains("android.enableJetifier=true"))
    assertTrue(mainActivityKt.contains("import kotlinx.android.synthetic.main.activity_main.*"))
  }

  fun testExistingGradleProperties() {
    loadProject(MIGRATE_TO_ANDROID_X)

    runWriteAction {
      val gradlePropertiesFile = project.baseDir.createChildData(this, "gradle.properties")
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

  fun testVerifyPrerequisites() {
    loadProject(MIGRATE_TO_ANDROID_X)

    val appGradleFile = myFixture.project.baseDir.findFileByRelativePath("app/build.gradle")!!
    val appGradleContent = getTextForFile("app/build.gradle")

    arrayOf("\"\$version_27\"", "ext.version_27").forEach {
      setFileContent(appGradleFile, appGradleContent.replaceCompileSdkWith(it))
      try {
        runProcessor(checkPrerequisites = true)
        fail("Prerequisites check should have failed for compileSdkVersion \"$it\"")
      }
      catch (e: RuntimeException) {
        assertEquals("You need to have compileSdk set to at least 28 in your module build.gradle to migrate to AndroidX.",
                     e.localizedMessage)
      }
    }

    setFileContent(appGradleFile, appGradleContent.replaceCompileSdkWith("28"))

    // Downgrade the gradle plugin and check it fails
    val rootGradleFile = VfsUtil.findRelativeFile(myFixture.project.baseDir, "build.gradle")!!
    val rootGradleContent = getTextForFile("build.gradle")
    setFileContent(rootGradleFile, rootGradleContent
      .replace("gradle_version = '+", "gradle_version = '3.0.1")
      .replace(Regex("com.android.tools.build:gradle:.*'"), "com.android.tools.build:gradle:\\\$gradle_version'"))
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

  fun testWarning() {
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

  /**
   * Regression test for b/123303598
   */
  fun testBug123303598() {
    loadProject(MIGRATE_TO_ANDROID_X)

    val appGradleFile = myFixture.project.baseDir.findFileByRelativePath("build.gradle")!!
    var appGradleContent = getTextForFile("build.gradle")

    // Remove repositories blocks and check that we do not throw an NPE when evaluating the block
    appGradleContent = appGradleContent.replace(Regex("repositories \\{.*?AndroidGradleTestCase[\\n\\s]+}\n",
                                                      setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL)),
                                                "")
    setFileContent(appGradleFile, appGradleContent)
    runProcessor(checkPrerequisites = false)
  }

  private fun setFileContent(file: VirtualFile, content: String) {
    WriteCommandAction.runWriteCommandAction(project) {
      myFixture.openFileInEditor(file)
      val document = myFixture.editor.document
      document.setText(content)
      PsiDocumentManager.getInstance(project).commitDocument(document)
    }
  }

  private fun runProcessor(showWarningDialog: Boolean = false, checkPrerequisites: Boolean = false) {
    MigrateToAndroidxHandler(showWarningDialog = showWarningDialog,
                             callSyncAfterMigration = false,
                             checkPrerequisites = checkPrerequisites
    )
      .invoke(project, null, null, null)
  }
}
