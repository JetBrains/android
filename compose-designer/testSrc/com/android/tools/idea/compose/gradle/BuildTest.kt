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
package com.android.tools.idea.compose.gradle

import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.compose.preview.TEST_DATA_PATH
import com.android.tools.idea.compose.preview.util.hasBeenBuiltSuccessfully
import com.android.tools.idea.compose.preview.util.hasExistingClassFile
import com.android.tools.idea.compose.preview.util.requestBuild
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.gradle.project.build.GradleProjectBuilder
import com.android.tools.idea.projectsystem.BuildListener
import com.android.tools.idea.projectsystem.setupBuildListener
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiUtil
import com.intellij.testFramework.EdtRule
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class BuildTest {
  @get:Rule
  val projectRule = AndroidGradleProjectRule(TEST_DATA_PATH)

  @get:Rule
  val edtRule = EdtRule()

  /**
   * Runs the [action] and waits for a build to happen. It returns the number of builds triggered by [action].
   */
  private fun runAndWaitForBuildToComplete(action: () -> Unit) = runBlocking(AndroidDispatchers.workerThread) {
    val buildComplete = CompletableDeferred<Unit>()
    val buildsStarted = AtomicInteger(0)
    val disposable = Disposer.newDisposable(projectRule.fixture.testRootDisposable, "Build Listener disposable")
    try {
      setupBuildListener(projectRule.project, object : BuildListener {
        override fun buildStarted() {
          buildsStarted.incrementAndGet()
        }

        override fun buildFailed() {
          buildComplete.complete(Unit)
        }

        override fun buildSucceeded() {
          buildComplete.complete(Unit)
        }
      }, disposable)
      action()
      buildComplete.await()
    } finally {
      Disposer.dispose(disposable)
    }
    return@runBlocking buildsStarted.get()
  }

  @Test
  fun testHasBeenBuiltSuccessfully() {
    projectRule.load(SIMPLE_COMPOSE_PROJECT_PATH, kotlinVersion = DEFAULT_KOTLIN_VERSION)
    val project = projectRule.project
    val activityFile = VfsUtil.findRelativeFile("app/src/main/java/google/simpleapplication/MainActivity.kt",
                                                ProjectRootManager.getInstance(project).contentRoots[0])!!
    val psiFile = ReadAction.compute<PsiFile, Throwable> {
      PsiUtil.getPsiFile(project, activityFile)
    }
    val psiFilePointer = ReadAction.compute<SmartPsiElementPointer<PsiFile>, Throwable> {
      SmartPointerManager.createPointer(psiFile)
    }

    assertFalse(hasBeenBuiltSuccessfully(psiFilePointer))
    assertFalse(hasExistingClassFile(psiFile))
    val buildsTriggered = runAndWaitForBuildToComplete {
      // Regression test for http://b/192223556
      val files = listOf(activityFile, activityFile, activityFile)
      requestBuild(project, files, false)
    }
    assertEquals(1, buildsTriggered)
    assertTrue(hasBeenBuiltSuccessfully(psiFilePointer))
    assertTrue(hasExistingClassFile(psiFile))

    runAndWaitForBuildToComplete {
      GradleProjectBuilder.getInstance(project).clean()
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
}