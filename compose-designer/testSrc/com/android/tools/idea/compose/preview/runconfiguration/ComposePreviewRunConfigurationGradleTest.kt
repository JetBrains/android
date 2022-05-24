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
package com.android.tools.idea.compose.preview.runconfiguration

import com.android.flags.junit.SetFlagRule
import com.android.tools.idea.compose.gradle.DEFAULT_KOTLIN_VERSION
import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.compose.preview.TEST_DATA_PATH
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.run.ValidationError
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

private const val APP_MAIN_ACTIVITY_FILE_PATH = "app/src/main/java/google/simpleapplication/MainActivity.kt"
private const val LIB_PREVIEWS_FILE_PATH = "lib/src/main/java/google/simpleapplicationlib/Previews.kt"

class ComposePreviewRunConfigurationGradleTest {

  @get:Rule
  val projectRule = AndroidGradleProjectRule(TEST_DATA_PATH)

  @get:Rule
  val liveEditFlagRule = SetFlagRule(StudioFlags.COMPOSE_PREVIEW_RUN_CONFIGURATION, true)

  @Before
  fun setUp() {
    projectRule.load(SIMPLE_COMPOSE_PROJECT_PATH, kotlinVersion = DEFAULT_KOTLIN_VERSION)
  }

  @Test
  fun testValidatePreview_app() {
    val errors = validatePreview(projectRule.project, APP_MAIN_ACTIVITY_FILE_PATH)
    assertTrue(errors.isEmpty())
  }

  @Test
  fun testValidatePreview_lib() {
    val errors = validatePreview(projectRule.project, LIB_PREVIEWS_FILE_PATH)
    assertTrue(errors.isEmpty())
  }
}

private fun validatePreview(project: Project, filePath: String): MutableList<ValidationError> {
  val previewRunConfigurationProducer = ComposePreviewRunConfigurationProducer()
  val previewRunConfiguration = ComposePreviewRunConfiguration(project, ComposePreviewRunConfigurationType().configurationFactories[0])

  val vFile = VfsUtil.findRelativeFile(filePath, ProjectRootManager.getInstance(project).contentRoots[0])!!
  return runReadAction {
    val file = vFile.toPsiFile(project)
    // Always picking the last function in the file
    val previewFun = PsiTreeUtil.findChildrenOfType(file, KtNamedFunction::class.java).last()
    val context = ConfigurationContext(previewFun)
    previewRunConfigurationProducer.setupConfigurationFromContext(previewRunConfiguration, context, Ref(previewFun))
    previewRunConfiguration.validate(null)
  }
}
