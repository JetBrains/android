/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.navigation

import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION_VERSION_CATALOG_KTS
import com.android.tools.idea.testing.findAppModule
import com.android.tools.idea.testing.moveCaret
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessModuleDir
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore.loadText
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findFile
import org.junit.Test

class VersionCatalogRenamingKtsTest: AndroidGradleTestCase()  {

  @Test
  fun testRenameDependencyInMainCatalog() {
    loadProject(SIMPLE_APPLICATION_VERSION_CATALOG_KTS)
    myFixture.configureFromExistingVirtualFile(project.findPrimaryCatalog())

    val editor = myFixture.editor
    val catalogSnapshot = editor.document.text
    val buildFileSnapshot = loadText(project.findAppGradleBuild())

    myFixture.moveCaret("[libraries]\nconstraint-lay|out")
    myFixture.renameElementAtCaret("my-constraint-layout")

    runWriteAction {
      FileDocumentManager.getInstance().saveAllDocuments()
    }

    // Check expected results
    assertEquals(catalogSnapshot.replaceFirst("[libraries]\nconstraint-layout", "[libraries]\nmy-constraint-layout"),
                        loadText(project.findPrimaryCatalog()))
    assertEquals(buildFileSnapshot.replaceFirst("libs.constraint.layout", "libs.my.constraint.layout"),
                        loadText(project.findAppGradleBuild()))
  }

  @Test
  fun testRenameDependencyInTestCatalog() {
    loadProject(SIMPLE_APPLICATION_VERSION_CATALOG_KTS)
    myFixture.configureFromExistingVirtualFile(project.findTestCatalog())

    val editor = myFixture.editor
    val catalogSnapshot = editor.document.text
    val buildFileSnapshot = loadText(project.findAppGradleBuild())

    myFixture.moveCaret("[libraries]\nju|nit")
    myFixture.renameElementAtCaret("junit4")

    runWriteAction {
      FileDocumentManager.getInstance().saveAllDocuments()
    }

    // Check expected results
    assertEquals(catalogSnapshot.replaceFirst("[libraries]\njunit", "[libraries]\njunit4"),
                        loadText(project.findTestCatalog()))
    assertEquals(buildFileSnapshot.replaceFirst("junit", "junit4"),
                        loadText(project.findAppGradleBuild()))
  }

  @Test
  fun testRenamePluginInCatalog() {
    loadProject(SIMPLE_APPLICATION_VERSION_CATALOG_KTS)
    myFixture.configureFromExistingVirtualFile(project.findPrimaryCatalog())

    val editor = myFixture.editor
    val catalogSnapshot = editor.document.text
    val buildFileSnapshot = loadText(project.findAppGradleBuild())
    val projectBuildFileSnapshot = loadText(findGradleBuild())

    myFixture.moveCaret("android-appl|ication")
    myFixture.renameElementAtCaret("android-application-new")

    runWriteAction {
      FileDocumentManager.getInstance().saveAllDocuments()
    }

    // Check expected results
    assertEquals(catalogSnapshot.replaceFirst("android-application", "android-application-new"),
                        loadText(project.findPrimaryCatalog()))
    assertEquals(buildFileSnapshot.replaceFirst("libs.plugins.android.application", "libs.plugins.android.application.new"),
                        loadText(project.findAppGradleBuild()))
    assertEquals(projectBuildFileSnapshot.replaceFirst("libs.plugins.android.application", "libs.plugins.android.application.new"),
                        loadText(findGradleBuild()))
  }

  private fun Project.findAppGradleBuild(): VirtualFile = findAppModule().guessModuleDir()!!.findChild("build.gradle.kts")!!
  private fun Project.findPrimaryCatalog(): VirtualFile = guessProjectDir()!!.findFile("gradle/libs.versions.toml")!!
  private fun Project.findTestCatalog(): VirtualFile = guessProjectDir()!!.findFile("gradle/libsTest.versions.toml")!!
  private fun findGradleBuild(): VirtualFile = VfsUtil.findFileByIoFile(projectFolderPath.resolve("build.gradle.kts"), true)!!
}