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
import com.android.tools.idea.gradle.project.build.GradleProjectBuilder
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiUtil
import com.intellij.testFramework.EdtRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BuildTest {
  @get:Rule
  val projectRule = AndroidGradleProjectRule(TEST_DATA_PATH)

  @get:Rule
  val edtRule = EdtRule()

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
    ApplicationManager.getApplication().invokeAndWait {
      projectRule.invokeTasks("compileDebugSources").apply {
        buildError?.printStackTrace()
        assertTrue("The project must compile correctly for the test to pass", isBuildSuccessful)
      }
    }
    assertTrue(hasBeenBuiltSuccessfully(psiFilePointer))
    assertTrue(hasExistingClassFile(psiFile))

    GradleProjectBuilder.getInstance(project).clean()
    assertFalse(hasBeenBuiltSuccessfully(psiFilePointer))
    assertFalse(hasExistingClassFile(psiFile))
  }
}