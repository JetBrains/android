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
package com.android.tools.idea.compose.gradle.preview.runconfiguration

import com.android.tools.idea.compose.gradle.DEFAULT_KOTLIN_VERSION
import com.android.tools.idea.compose.preview.SIMPLE_COMPOSE_PROJECT_PATH
import com.android.tools.idea.compose.preview.SimpleComposeAppPaths
import com.android.tools.idea.compose.preview.TEST_DATA_PATH
import com.android.tools.idea.compose.preview.message
import com.android.tools.idea.compose.preview.runconfiguration.ComposePreviewRunConfiguration
import com.android.tools.idea.compose.preview.runconfiguration.ComposePreviewRunConfigurationProducer
import com.android.tools.idea.compose.preview.runconfiguration.ComposePreviewRunConfigurationType
import com.android.tools.idea.run.ValidationError
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.util.PsiTreeUtil
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ComposePreviewRunConfigurationGradleTest {

  private val noValidComposableErrorMessage =
    message("run.configuration.no.valid.composable.set", "")

  @get:Rule val projectRule = AndroidGradleProjectRule(TEST_DATA_PATH)

  @Before
  fun setUp() {
    projectRule.load(SIMPLE_COMPOSE_PROJECT_PATH, kotlinVersion = DEFAULT_KOTLIN_VERSION)
  }

  @Test
  fun testValidatePreview_app_main() {
    val errors =
      validatePreview(
        projectRule.project,
        SimpleComposeAppPaths.APP_MAIN_ACTIVITY.path,
        expectedSetupResult = true
      )
    assertTrue(errors.isEmpty())
  }

  @Test
  fun testValidatePreview_app_androidTest() {
    val errors =
      validatePreview(
        projectRule.project,
        SimpleComposeAppPaths.APP_PREVIEWS_ANDROID_TEST.path,
        expectedSetupResult = false
      )
    assertTrue(errors.isNotEmpty())
    assertTrue(errors.any { it.message == noValidComposableErrorMessage })
  }

  @Test
  fun testValidatePreview_app_unitTest() {
    val errors =
      validatePreview(
        projectRule.project,
        SimpleComposeAppPaths.APP_PREVIEWS_UNIT_TEST.path,
        expectedSetupResult = false
      )
    assertTrue(errors.isNotEmpty())
    assertTrue(errors.any { it.message == noValidComposableErrorMessage })
  }

  @Test
  fun testValidatePreview_lib_main() {
    val errors =
      validatePreview(
        projectRule.project,
        SimpleComposeAppPaths.LIB_PREVIEWS.path,
        expectedSetupResult = true
      )
    assertTrue(errors.isEmpty())
  }

  @Test
  fun testValidatePreview_lib_androidTest() {
    val errors =
      validatePreview(
        projectRule.project,
        SimpleComposeAppPaths.LIB_PREVIEWS_ANDROID_TEST.path,
        expectedSetupResult = false
      )
    assertTrue(errors.isNotEmpty())
    assertTrue(errors.any { it.message == noValidComposableErrorMessage })
  }

  @Test
  fun testValidatePreview_lib_unitTest() {
    val errors =
      validatePreview(
        projectRule.project,
        SimpleComposeAppPaths.LIB_PREVIEWS_UNIT_TEST.path,
        expectedSetupResult = false
      )
    assertTrue(errors.isNotEmpty())
    assertTrue(errors.any { it.message == noValidComposableErrorMessage })
  }
}

private fun validatePreview(
  project: Project,
  filePath: String,
  expectedSetupResult: Boolean
): MutableList<ValidationError> {
  val previewRunConfigurationProducer = ComposePreviewRunConfigurationProducer()
  val previewRunConfiguration =
    ComposePreviewRunConfiguration(
      project,
      ComposePreviewRunConfigurationType().configurationFactories[0]
    )

  val vFile =
    VfsUtil.findRelativeFile(filePath, ProjectRootManager.getInstance(project).contentRoots[0])!!
  return runReadAction {
    val file = vFile.toPsiFile(project)
    // Always picking the last function in the file
    val previewFun = PsiTreeUtil.findChildrenOfType(file, KtNamedFunction::class.java).last()
    val context = ConfigurationContext(previewFun)

    val setupResult =
      previewRunConfigurationProducer.setupConfigurationFromContext(
        previewRunConfiguration,
        context,
        Ref(previewFun)
      )
    assertEquals(expectedSetupResult, setupResult)

    previewRunConfiguration.validate(null)
  }
}
