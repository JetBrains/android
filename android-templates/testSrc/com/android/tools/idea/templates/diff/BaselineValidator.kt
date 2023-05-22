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
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.wizard.template.Recipe
import com.android.tools.idea.wizard.template.Template
import com.intellij.openapi.project.Project
import java.io.File
import java.nio.file.Path

/**
 * Generates files from a template and performs checks on them to ensure they're valid and can be
 * checked in as golden files
 */
class BaselineValidator(
  template: Template,
  private val gradleProjectRule: AndroidGradleProjectRule
) : ProjectRenderer(template) {
  override fun handleDirectories(moduleName: String, goldenDir: Path, projectDir: Path) {
    // TODO: build
    // TODO: lint
    // TODO: other checks
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
}
