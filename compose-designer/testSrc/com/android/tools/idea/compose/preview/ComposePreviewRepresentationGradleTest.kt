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
package com.android.tools.idea.compose.preview

import com.android.tools.idea.compose.gradle.DEFAULT_KOTLIN_VERSION
import com.android.tools.idea.compose.preview.util.hasBeenBuiltSuccessfully
import com.android.tools.idea.compose.preview.util.hasExistingClassFile
import com.android.tools.idea.compose.preview.util.requestBuild
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.moveCaret
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiUtil
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComposePreviewRepresentationGradleTest {
  @get:Rule
  val projectRule = AndroidGradleProjectRule(TEST_DATA_PATH)
  private val previewProvider = ComposePreviewRepresentationProvider { AnnotationFilePreviewElementFinder }
  private lateinit var vFile: VirtualFile
  private lateinit var composePreviewRepresentation: ComposePreviewRepresentation
  private lateinit var psiFile: PsiFile
  private lateinit var psiFilePointer: SmartPsiElementPointer<PsiFile>

  @Before
  fun setUp() {
    projectRule.load(SIMPLE_COMPOSE_PROJECT_PATH, kotlinVersion = DEFAULT_KOTLIN_VERSION)
    vFile = VfsUtil.findRelativeFile(SimpleComposeAppPaths.APP_MAIN_ACTIVITY.path,
                                     ProjectRootManager.getInstance(projectRule.project).contentRoots[0])!!
    psiFile = runReadAction {
      PsiUtil.getPsiFile(projectRule.project, vFile)
    }
    psiFilePointer = runReadAction {
      SmartPointerManager.createPointer(psiFile)
    }
    composePreviewRepresentation = getActiveComposePreviewRepresentation(vFile)

    // The first time building a project will frequently cause some resource modifications, and as a consequence,
    // the 'needsRefreshOnSuccessfulBuild' flag present on the composePreviewRepresentation will be set to true.
    // To avoid problems on the test executions, and avoid flaky tests, the first build is performed here.
    requestBuild()
  }

  @After
  fun tearDown() {
    runAndWaitForBuildToComplete(projectRule) {
      GradleBuildInvoker.getInstance(projectRule.project).cleanProject()
    }
    // Ensure that the VFS is up to date, so the .class file is not cached when removed.
    ApplicationManager.getApplication().invokeAndWait {
      runWriteAction {
        VirtualFileManager.getInstance().syncRefresh()
      }
    }
    assertFalse(hasBeenBuiltSuccessfully(psiFilePointer))
    assertFalse(hasExistingClassFile(psiFile))
  }

  @Test
  fun testNeedsRefresh_buildClean() {
    requestBuild()
    assertFalse(composePreviewRepresentation.needsRefreshOnSuccessfulBuild())

    runAndWaitForBuildToComplete(projectRule) {
      GradleBuildInvoker.getInstance(projectRule.project).cleanProject()
    }
    assertTrue(composePreviewRepresentation.needsRefreshOnSuccessfulBuild())
    assertTrue(composePreviewRepresentation.buildWillTriggerRefresh())
  }

  @Test
  fun testNeedsRefresh_localModification() {
    requestBuild()
    assertFalse(composePreviewRepresentation.needsRefreshOnSuccessfulBuild())

    runWriteActionAndWait {
      projectRule.fixture.openFileInEditor(vFile)
      projectRule.fixture.moveCaret("Text(\"Only a text\")|")
      projectRule.fixture.type("\nText(\"added during test execution\")")
      PsiDocumentManager.getInstance(projectRule.project).commitAllDocuments()
      FileDocumentManager.getInstance().saveAllDocuments()
    }
    assertTrue(composePreviewRepresentation.buildWillTriggerRefresh())
    requestBuild()
    assertFalse(composePreviewRepresentation.needsRefreshOnSuccessfulBuild())
  }

  @Test
  fun testNeedsRefresh_otherFileModification() {
    val otherFile = VfsUtil.findRelativeFile(SimpleComposeAppPaths.APP_OTHER_PREVIEWS.path,
                                             ProjectRootManager.getInstance(projectRule.project).contentRoots[0])!!

    requestBuild()
    assertFalse(composePreviewRepresentation.needsRefreshOnSuccessfulBuild())

    runWriteActionAndWait {
      projectRule.fixture.openFileInEditor(otherFile)
      projectRule.fixture.moveCaret("Text(\"Line3\")|")
      projectRule.fixture.type("\nText(\"added during test execution\")")
      PsiDocumentManager.getInstance(projectRule.project).commitAllDocuments()
      FileDocumentManager.getInstance().saveAllDocuments()
    }
    assertTrue(composePreviewRepresentation.buildWillTriggerRefresh())
    requestBuild()
    assertFalse(composePreviewRepresentation.needsRefreshOnSuccessfulBuild())
  }

  private fun requestBuild() {
    runAndWaitForBuildToComplete(projectRule) {
      requestBuild(projectRule.project, listOf(vFile), false)
    }
    assertTrue(hasBeenBuiltSuccessfully(psiFilePointer))
    assertTrue(hasExistingClassFile(psiFile))
  }

  private fun getActiveComposePreviewRepresentation(vFile: VirtualFile): ComposePreviewRepresentation {
    val psiFile = runReadAction {
      PsiUtil.getPsiFile(projectRule.project, vFile)
    }
    val previewRepresentation = getRepresentationForFile(psiFile, projectRule.project, projectRule.fixture, previewProvider)
    assertTrue(previewRepresentation is ComposePreviewRepresentation)
    previewRepresentation.onActivate()
    assertTrue(previewRepresentation.buildWillTriggerRefresh())
    return previewRepresentation
  }
}
