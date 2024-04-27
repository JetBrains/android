/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.templates.diff

import com.android.tools.idea.templates.diff.TemplateDiffTestUtils.getPinnedAgpVersion
import com.android.tools.idea.templates.recipe.DefaultRecipeExecutor
import com.android.tools.idea.templates.recipe.RenderingContext
import com.android.tools.idea.templates.verifyLanguageFiles
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.injectBuildOutputDumpingBuildViewManager
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.Recipe
import com.android.tools.idea.wizard.template.Template
import com.android.tools.idea.wizard.template.Thumb
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import java.nio.file.Path

/**
 * Generates files from a template and performs checks on them to ensure they're valid and can be
 * checked in as golden files
 *
 * For context and instructions on running and generating golden files, see go/template-diff-tests
 */
class BaselineValidator(
  template: Template,
  goldenDirName: String,
  private val gradleProjectRule: AndroidGradleProjectRule
) : ProjectRenderer(template, goldenDirName) {
  override fun handleDirectories(moduleName: String, goldenDir: Path, projectDir: Path) {
    checkProjectProperties(projectDir)
    performBuild()
    // TODO: lint
  }

  /**
   * Overrides ProjectRenderer's renderTemplate to wrap it inside the AndroidGradleProjectRule's
   * load(), which syncs the project
   */
  override fun renderTemplate(
    project: Project,
    moduleRecipe: Recipe,
    context: RenderingContext,
    moduleRecipeExecutor: DefaultRecipeExecutor,
    templateRecipeExecutor: DefaultRecipeExecutor
  ) {
    gradleProjectRule.load(TestProjectPaths.NO_MODULES, getPinnedAgpVersion()) {
      super.renderTemplate(
        project,
        moduleRecipe,
        context,
        moduleRecipeExecutor,
        templateRecipeExecutor
      )
    }
  }

  /** Build the project to ensure it compiles */
  private fun performBuild() {
    @Suppress("IncorrectParentDisposable")
    injectBuildOutputDumpingBuildViewManager(gradleProjectRule.project, gradleProjectRule.project)
    gradleProjectRule.invokeTasks("compileDebugSources").apply { // "assembleDebug" is too slow
      buildError?.printStackTrace()
      assertTrue("Project didn't compile correctly", isBuildSuccessful)
    }
  }

  /** Other checks outside of building and Linting */
  private fun checkProjectProperties(projectDir: Path) {
    // Check that a thumbnail is specified
    assertNotEquals(template.thumb(), Thumb.NoThumb)

    // Check that project root is set up correctly
    assertEquals(projectDir, gradleProjectRule.project.guessProjectDir()!!.toNioPath())

    // Check that the file extensions are of the correct language
    val language = Language.valueOf(moduleState.projectTemplateDataBuilder.language!!.toString())
    verifyLanguageFiles(projectDir, language)
  }
}
