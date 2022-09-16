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

import com.android.tools.idea.compose.preview.COMPOSITE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.compose.preview.SimpleComposeAppPaths
import com.android.tools.idea.compose.preview.TEST_DATA_PATH
import com.android.tools.idea.compose.preview.runAndWaitForBuildToComplete
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.projectsystem.hasBeenBuiltSuccessfully
import com.android.tools.idea.projectsystem.hasExistingClassFile
import com.android.tools.idea.projectsystem.requestBuild
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor.Companion.AGP_CURRENT
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.AndroidGradleTests.defaultPatchPreparedProject
import com.android.tools.idea.testing.withKotlin
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiUtil
import com.intellij.testFramework.EdtRule
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

// TODO(b/231401347): Move compose-agnostic testing to com.android.tools.idea.projectsystem.
class BuildTest {
  @get:Rule val projectRule = AndroidGradleProjectRule(TEST_DATA_PATH)

  @get:Rule val edtRule = EdtRule()

  private fun doTestHasBeenBuiltSuccessfully(project: Project, filePaths: List<String>) {
    val activityFiles =
      filePaths.map {
        VfsUtil.findRelativeFile(it, ProjectRootManager.getInstance(project).contentRoots[0])!!
      }
    val psiFiles = activityFiles.map { runReadAction { PsiUtil.getPsiFile(project, it) } }
    val psiFilePointers = psiFiles.map { runReadAction { SmartPointerManager.createPointer(it) } }

    assertTrue(psiFilePointers.none { hasBeenBuiltSuccessfully(it) })
    assertTrue(psiFiles.none { hasExistingClassFile(it) })
    runAndWaitForBuildToComplete(projectRule) {
      // Regression test for http://b/192223556
      val files = activityFiles + activityFiles + activityFiles
      project.requestBuild(files)
    }
    assertTrue(psiFilePointers.all { hasBeenBuiltSuccessfully(it) })
    assertTrue(psiFiles.all { hasExistingClassFile(it) })

    runAndWaitForBuildToComplete(projectRule) {
      GradleBuildInvoker.getInstance(project).cleanProject()
    }
    // Ensure that the VFS is up to date, so the .class file is not cached when removed.
    ApplicationManager.getApplication().invokeAndWait {
      runWriteAction { VirtualFileManager.getInstance().syncRefresh() }
    }
    assertTrue(psiFilePointers.none { hasBeenBuiltSuccessfully(it) })
    assertTrue(psiFiles.none { hasExistingClassFile(it) })
  }

  @Test
  fun testHasBeenBuiltSuccessfully() {
    projectRule.load(SIMPLE_COMPOSE_PROJECT_PATH, AGP_CURRENT.withKotlin(DEFAULT_KOTLIN_VERSION))
    doTestHasBeenBuiltSuccessfully(
      projectRule.project,
      listOf(SimpleComposeAppPaths.APP_MAIN_ACTIVITY.path)
    )
    doTestHasBeenBuiltSuccessfully(
      projectRule.project,
      listOf(SimpleComposeAppPaths.APP_PREVIEWS_ANDROID_TEST.path)
    )
    doTestHasBeenBuiltSuccessfully(
      projectRule.project,
      listOf(
        SimpleComposeAppPaths.APP_MAIN_ACTIVITY.path,
        SimpleComposeAppPaths.APP_PREVIEWS_ANDROID_TEST.path
      )
    )
  }

  @Test
  fun testCompositeBuildsCorrectly() {
    projectRule.load(
      COMPOSITE_COMPOSE_PROJECT_PATH,
      AGP_CURRENT.withKotlin(DEFAULT_KOTLIN_VERSION),
      preLoad = { projectRoot ->
        // Copy SimpleComposableApplication to this project to be able to make a composite. The
        // composite project settings.gradle
        // will point to the SimpleComposableApplication
        val simpleComposableAppPath = projectRule.resolveTestDataPath(SIMPLE_COMPOSE_PROJECT_PATH)
        val destination = File(projectRoot, "SimpleComposeApplication")
        FileUtil.copyDir(simpleComposableAppPath, destination)
        defaultPatchPreparedProject(
          File(projectRule.project.basePath!!),
          AGP_CURRENT.withKotlin(DEFAULT_KOTLIN_VERSION),
          null,
          *listOf<File>().toTypedArray()
        )
      }
    )
    val project = projectRule.project
    val activityFile =
      VfsUtil.findRelativeFile(
        "SimpleComposeApplication/${SimpleComposeAppPaths.APP_MAIN_ACTIVITY.path}",
        ProjectRootManager.getInstance(project).contentRoots[0]
      )!!
    val psiFilePointer =
      ReadAction.compute<SmartPsiElementPointer<PsiFile>, Throwable> {
        SmartPointerManager.createPointer(PsiUtil.getPsiFile(project, activityFile))
      }
    runAndWaitForBuildToComplete(projectRule) { project.requestBuild(listOf(activityFile)) }
    assertTrue(hasBeenBuiltSuccessfully(psiFilePointer))
  }
}
