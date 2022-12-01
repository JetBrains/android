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
package com.android.tools.idea.compose.gradle.preview

import com.android.testutils.TestUtils
import com.android.tools.idea.compose.gradle.ComposeGradleProjectRule
import com.android.tools.idea.compose.preview.AnnotationFilePreviewElementFinder
import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.compose.preview.SimpleComposeAppPaths
import com.android.tools.idea.testing.executeAndSave
import com.android.tools.idea.testing.insertText
import com.android.tools.idea.testing.moveCaret
import com.android.tools.idea.testing.moveCaretLines
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AnnotationFileComposePreviewElementFinderGradleTest {

  @get:Rule val projectRule = ComposeGradleProjectRule(SIMPLE_COMPOSE_PROJECT_PATH)

  private val project: Project
    get() = projectRule.project

  private val fixture: CodeInsightTestFixture
    get() = projectRule.fixture

  private fun getPsiFile(path: String): PsiFile {
    val vFile = project.guessProjectDir()!!.findFileByRelativePath(path)!!
    return runReadAction { PsiManager.getInstance(project).findFile(vFile)!! }
  }

  @Test
  fun testFindMultiPreviewFromLibrary() {
    DumbService.getInstance(project).waitForSmartMode()

    // Add library dependency to build.gradle
    val buildGradleFile = getPsiFile(SimpleComposeAppPaths.APP_BUILD_GRADLE.path)
    invokeAndWaitIfNeeded { fixture.openFileInEditor(buildGradleFile.virtualFile) }
    runWriteActionAndWait {
      fixture.moveCaret("dependencies {|")
      fixture.editor.executeAndSave {
        insertText(
          "\nimplementation files('${
          TestUtils.resolveWorkspacePath("tools/adt/idea/compose-designer/testData/classloader/multiPreviewTestLibrary")
            .resolve("multiPreviewTestLibrary.aar")}')"
        )
      }
      PsiDocumentManager.getInstance(projectRule.project).commitAllDocuments()
      FileDocumentManager.getInstance().saveAllDocuments()
    }

    // Use MultiPreview from the added library
    val mainFile = getPsiFile(SimpleComposeAppPaths.APP_MAIN_ACTIVITY.path)
    invokeAndWaitIfNeeded { fixture.openFileInEditor(mainFile.virtualFile) }
    runWriteActionAndWait {
      fixture.moveCaret("|class MainActivity")
      fixture.editor.moveCaretLines(-1)
      fixture.editor.executeAndSave {
        insertText(
          """
          import com.example.mytestlibrary.MyMultiPreviewFromMyLibrary

          @MyMultiPreviewFromMyLibrary
          @Composable
          fun MyNewTestFun() {
          }

        """.trimIndent()
        )
      }
      PsiDocumentManager.getInstance(projectRule.project).commitAllDocuments()
      FileDocumentManager.getInstance().saveAllDocuments()
    }

    projectRule.requestSyncAndWait()
    projectRule.buildAndAssertIsSuccessful()

    val previewElement = runBlocking {
      AnnotationFilePreviewElementFinder.findPreviewMethods(project, mainFile.virtualFile).first()
    }
    assertEquals("MyNewTestFun - test name", previewElement.displaySettings.name)
    assertEquals("test group", previewElement.displaySettings.group)
    assertTrue(previewElement.displaySettings.showBackground)
    assertEquals("#FF00FF00", previewElement.displaySettings.backgroundColor!!.uppercase())
    assertEquals("id:pixel_5", previewElement.configuration.deviceSpec)
  }
}
