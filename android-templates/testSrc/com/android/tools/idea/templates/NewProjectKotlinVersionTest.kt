/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.templates

import com.android.tools.idea.npw.project.DEFAULT_KOTLIN_VERSION_FOR_NEW_PROJECTS
import com.android.tools.idea.npw.project.determineKotlinVersion
import com.android.tools.idea.npw.template.ModuleTemplateDataBuilder
import com.android.tools.idea.npw.template.ProjectTemplateDataBuilder
import com.android.tools.idea.npw.template.TemplateResolver
import com.android.tools.idea.templates.diff.TemplateDiffTestUtils.getPinnedAgpVersion
import com.android.tools.idea.templates.diff.activity.ProjectRenderer
import com.android.tools.idea.templates.recipe.DefaultRecipeExecutor
import com.android.tools.idea.templates.recipe.RenderingContext
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.Recipe
import com.android.tools.idea.wizard.template.Template
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import java.nio.file.Path
import org.junit.Rule
import org.junit.Test

class NewProjectKotlinVersionTest {
  @get:Rule val projectRule = AndroidGradleProjectRule(getPinnedAgpVersion())

  @Test
  fun latestKotlinVersionUsedForNewProjects() {
    val template = TemplateResolver.getTemplateByName("Empty Activity")!!
    val renderer = SyncOnlyRenderer(template, goldenDirName = "testComposeActivityMaterial3", projectRule)

    renderer.renderProject(
      projectRule.project,
      getPinnedAgpVersion(),
      { _: ModuleTemplateDataBuilder, projectData: ProjectTemplateDataBuilder -> projectData.language = Language.Kotlin },
    )

    val resolvedKotlinVersion = determineKotlinVersion(projectRule.project)!!
    assertThat(resolvedKotlinVersion.toString()).isEqualTo(DEFAULT_KOTLIN_VERSION_FOR_NEW_PROJECTS)
  }
}

/**
 * Android Merge patch:
 * Renders a template with a real Gradle sync (so the resolved Kotlin version can be read back), without the build/lint/golden-diff
 * machinery of GoldenFileValidator, which this test doesn't need and which requires a real Bazel test sandbox to write its outputs.
 */
private class SyncOnlyRenderer(template: Template, goldenDirName: String, private val gradleProjectRule: AndroidGradleProjectRule) :
  ProjectRenderer(template, goldenDirName) {
  override fun renderTemplate(
    project: Project,
    moduleRecipe: Recipe,
    context: RenderingContext,
    moduleRecipeExecutor: DefaultRecipeExecutor,
    templateRecipeExecutor: DefaultRecipeExecutor,
  ) {
    gradleProjectRule.load(TestProjectPaths.NO_MODULES, getPinnedAgpVersion()) {
      super.renderTemplate(project, moduleRecipe, context, moduleRecipeExecutor, templateRecipeExecutor)
    }
  }

  override fun handleDirectories(moduleName: String, goldenDir: Path, projectDir: Path) {}
}