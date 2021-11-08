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

import com.android.flags.junit.SetFlagRule
import com.android.tools.idea.compose.preview.COMPOSITE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.compose.preview.SimpleComposeAppPaths
import com.android.tools.idea.compose.preview.TEST_DATA_PATH
import com.android.tools.idea.compose.preview.util.hasBeenBuiltSuccessfully
import com.android.tools.idea.compose.preview.util.hasExistingClassFile
import com.android.tools.idea.compose.preview.util.requestBuild
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.projectsystem.BuildListener
import com.android.tools.idea.projectsystem.setupBuildListener
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.AndroidGradleTests.defaultPatchPreparedProject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiUtil
import com.intellij.testFramework.EdtRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@RunWith(Parameterized::class)
class BuildTest(private val onlyKotlinBuildFlag: Boolean) {
  companion object {
    @Suppress("unused") // Used by JUnit via reflection
    @JvmStatic
    @get:Parameterized.Parameters(name = "onlyKotlinBuildFlag = {0}")
    val flagValues = listOf(true, false)
  }

  @get:Rule
  val projectRule = AndroidGradleProjectRule(TEST_DATA_PATH)

  @get:Rule
  val onlyKotlinBuildFlagRule = SetFlagRule(StudioFlags.COMPOSE_PREVIEW_ONLY_KOTLIN_BUILD, onlyKotlinBuildFlag)

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

  private fun doTestHasBeenBuiltSuccessfully(project: Project, filePaths: List<String>, expectedBuildsTriggered: Int) {
    val activityFiles = filePaths.map { VfsUtil.findRelativeFile(it, ProjectRootManager.getInstance(project).contentRoots[0])!! }
    val psiFiles = activityFiles.map { runReadAction { PsiUtil.getPsiFile(project, it) } }
    val psiFilePointers = psiFiles.map { runReadAction { SmartPointerManager.createPointer(it) } }

    assertTrue(psiFilePointers.none { hasBeenBuiltSuccessfully(it) })
    assertTrue(psiFiles.none { hasExistingClassFile(it) })
    val buildsTriggered = runAndWaitForBuildToComplete {
      // Regression test for http://b/192223556
      val files = activityFiles + activityFiles + activityFiles
      requestBuild(project, files, false)
    }
    assertEquals(expectedBuildsTriggered, buildsTriggered)
    assertTrue(psiFilePointers.all { hasBeenBuiltSuccessfully(it) })
    assertTrue(psiFiles.all { hasExistingClassFile(it) })

    runAndWaitForBuildToComplete {
      GradleBuildInvoker.getInstance(project).cleanProject()
    }
    // Ensure that the VFS is up to date, so the .class file is not cached when removed.
    ApplicationManager.getApplication().invokeAndWait {
      runWriteAction {
        VirtualFileManager.getInstance().syncRefresh()
      }
    }
    assertTrue(psiFilePointers.none { hasBeenBuiltSuccessfully(it) })
    assertTrue(psiFiles.none { hasExistingClassFile(it) })
  }

  @Test
  fun testHasBeenBuiltSuccessfully_main() {
    projectRule.load(SIMPLE_COMPOSE_PROJECT_PATH, kotlinVersion = DEFAULT_KOTLIN_VERSION)
    doTestHasBeenBuiltSuccessfully(projectRule.project, listOf(SimpleComposeAppPaths.APP_MAIN_ACTIVITY.path), expectedBuildsTriggered = 1)
  }

  @Test
  fun testHasBeenBuiltSuccessfully_androidTest() {
    projectRule.load(SIMPLE_COMPOSE_PROJECT_PATH, kotlinVersion = DEFAULT_KOTLIN_VERSION)
    doTestHasBeenBuiltSuccessfully(projectRule.project, listOf(SimpleComposeAppPaths.APP_PREVIEWS_ANDROID_TEST.path),
                                   expectedBuildsTriggered = 1)
  }

  @Test
  fun testHasBeenBuiltSuccessfully_both() {
    projectRule.load(SIMPLE_COMPOSE_PROJECT_PATH, kotlinVersion = DEFAULT_KOTLIN_VERSION)
    doTestHasBeenBuiltSuccessfully(projectRule.project, listOf(SimpleComposeAppPaths.APP_MAIN_ACTIVITY.path,
                                                               SimpleComposeAppPaths.APP_PREVIEWS_ANDROID_TEST.path),
                                   expectedBuildsTriggered = if (onlyKotlinBuildFlag) 2 else 1)
  }

  @Test
  fun testCompositeBuildsCorrectly() {
    projectRule.load(COMPOSITE_COMPOSE_PROJECT_PATH, kotlinVersion = DEFAULT_KOTLIN_VERSION, preLoad = { projectRoot ->
      // Copy SimpleComposableApplication to this project to be able to make a composite. The composite project settings.gradle
      // will point to the SimpleComposableApplication
      val simpleComposableAppPath = projectRule.resolveTestDataPath(SIMPLE_COMPOSE_PROJECT_PATH)
      val destination = File(projectRoot, "SimpleComposeApplication")
      FileUtil.copyDir(simpleComposableAppPath, destination)
      defaultPatchPreparedProject(File(projectRule.project.basePath), null, null, DEFAULT_KOTLIN_VERSION, *listOf<File>().toTypedArray())
    })
    val project = projectRule.project
    val activityFile = VfsUtil.findRelativeFile("SimpleComposeApplication/${SimpleComposeAppPaths.APP_MAIN_ACTIVITY.path}",
                                                ProjectRootManager.getInstance(project).contentRoots[0])!!
    val psiFilePointer = ReadAction.compute<SmartPsiElementPointer<PsiFile>, Throwable> {
      SmartPointerManager.createPointer(PsiUtil.getPsiFile(project, activityFile))
    }
    runAndWaitForBuildToComplete { requestBuild(project, listOf(activityFile), false) }
    assertTrue(hasBeenBuiltSuccessfully(psiFilePointer))
  }
}