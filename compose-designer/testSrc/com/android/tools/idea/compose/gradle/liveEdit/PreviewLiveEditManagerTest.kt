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
package com.android.tools.idea.compose.gradle.liveEdit

import com.android.testutils.ImageDiffUtil
import com.android.tools.idea.compose.gradle.ComposeGradleProjectRule
import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.compose.preview.liveEdit.PreviewLiveEditManager
import com.android.tools.idea.compose.preview.util.SinglePreviewElementInstance
import com.android.tools.idea.testing.moveCaret
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test


class PreviewLiveEditManagerTest {
  @get:Rule
  val projectRule = ComposeGradleProjectRule(SIMPLE_COMPOSE_PROJECT_PATH)
  lateinit var psiMainFile: PsiFile
  lateinit var liveEditManager: PreviewLiveEditManager

  @Before
  fun setUp() {
    val mainFile = projectRule.project.guessProjectDir()!!
      .findFileByRelativePath("app/src/main/java/google/simpleapplication/MainActivity.kt")!!
    psiMainFile = runReadAction { PsiManager.getInstance(projectRule.project).findFile(mainFile)!! }
    liveEditManager = PreviewLiveEditManager.getInstance(projectRule.project)
    invokeAndWaitIfNeeded {
      assertTrue(projectRule.build().isBuildSuccessful)
    }
    runWriteActionAndWait {
      projectRule.fixture.openFileInEditor(mainFile)
      projectRule.fixture.moveCaret("Text(\"Hello 2\")|")
      projectRule.fixture.type("\n")
    }
  }

  @Test
  fun testSingleFileCompileSuccessfully() {
    val module = ModuleUtilCore.findModuleForPsiElement(psiMainFile)!!
    runWriteActionAndWait {
      projectRule.fixture.type("Text(\"Hello 3\")\n")
      PsiDocumentManager.getInstance(projectRule.project).commitAllDocuments()
    }
    runBlocking {
      val (result, _) = liveEditManager.compileRequest(psiMainFile, module)
      assertTrue("Compilation must pass", result)
    }
  }

  @Ignore // b/204995693 Flaky
  @Test
  fun testDaemonIsRestartedAutomatically() {
    val module = ModuleUtilCore.findModuleForPsiElement(psiMainFile)!!
    runWriteActionAndWait {
      projectRule.fixture.type("Text(\"Hello 3\")\n")
      PsiDocumentManager.getInstance(projectRule.project).commitAllDocuments()
    }
    runBlocking {
      val (result, _) = liveEditManager.compileRequest(psiMainFile, module)
      assertTrue("Compilation must pass", result)
      liveEditManager.stopAllDaemons()
    }
    runBlocking {
      val (result, _) = liveEditManager.compileRequest(psiMainFile, module)
      assertTrue("Compilation must pass", result)
    }
  }
}