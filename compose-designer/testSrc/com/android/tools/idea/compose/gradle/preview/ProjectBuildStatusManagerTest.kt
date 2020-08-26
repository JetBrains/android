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
package com.android.tools.idea.compose.gradle.preview

import com.android.tools.idea.compose.gradle.ComposeGradleProjectRule
import com.android.tools.idea.compose.preview.NeedsBuild
import com.android.tools.idea.compose.preview.OutOfDate

import com.android.tools.idea.compose.preview.ProjectBuildStatusManager
import com.android.tools.idea.compose.preview.Ready
import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProjectBuildStatusManagerTest {
  @get:Rule
  val edtRule = EdtRule()

  @get:Rule
  val projectRule = ComposeGradleProjectRule(SIMPLE_COMPOSE_PROJECT_PATH)
  val project: Project
    get() = projectRule.project

  @RunsInEdt
  @Test
  fun testProjectStatusManagerStates() {
    val mainFile = projectRule.project.guessProjectDir()!!
      .findFileByRelativePath("app/src/main/java/google/simpleapplication/MainActivity.kt")!!
    // Force clean
    projectRule.clean()
    WriteAction.run<Throwable> {
      projectRule.fixture.openFileInEditor(mainFile)
    }

    val statusManager = ProjectBuildStatusManager(projectRule.fixture.testRootDisposable, projectRule.fixture.file)

    assertEquals(NeedsBuild, statusManager.status)
    assertTrue("Project must compile correctly", projectRule.build().isBuildSuccessful)
    assertTrue("Builds status is not Ready after successful build", statusManager.status is Ready)

    val documentManager = PsiDocumentManager.getInstance(projectRule.project)
    WriteCommandAction.runWriteCommandAction(project) {
      documentManager.getDocument(projectRule.fixture.file)!!.insertString(0, "// A change")
      documentManager.commitAllDocuments()
    }
    FileDocumentManager.getInstance().saveAllDocuments()
    assertEquals(OutOfDate, statusManager.status)
    projectRule.clean()
    assertEquals(NeedsBuild, statusManager.status)
  }

  @RunsInEdt
  @Test
  fun testProjectStatusManagerStatesFailureModes() {
    val mainFile = projectRule.project.guessProjectDir()!!
      .findFileByRelativePath("app/src/main/java/google/simpleapplication/MainActivity.kt")!!

    val documentManager = PsiDocumentManager.getInstance(projectRule.project)

    // Force clean
    projectRule.clean()
    WriteCommandAction.runWriteCommandAction(project) {
      projectRule.fixture.openFileInEditor(mainFile)

      // Break the compilation
      documentManager.getDocument(projectRule.fixture.file)!!.insertString(0, "<<Invalid>>")
      documentManager.commitAllDocuments()
    }
    FileDocumentManager.getInstance().saveAllDocuments()

    val statusManager = ProjectBuildStatusManager(projectRule.fixture.testRootDisposable, projectRule.fixture.file)
    assertEquals(NeedsBuild, statusManager.status)
    assertFalse(projectRule.build().isBuildSuccessful)
    assertEquals(NeedsBuild, statusManager.status)

    WriteCommandAction.runWriteCommandAction(project) {
      // Fix the build
      documentManager.getDocument(projectRule.fixture.file)!!.deleteString(0, "<<Invalid>>".length)
      documentManager.commitAllDocuments()
    }
    FileDocumentManager.getInstance().saveAllDocuments()

    assertEquals(OutOfDate, statusManager.status)
    assertTrue(projectRule.build().isBuildSuccessful)
    assertTrue("Builds status is not Ready after successful build", statusManager.status is Ready)
  }
}