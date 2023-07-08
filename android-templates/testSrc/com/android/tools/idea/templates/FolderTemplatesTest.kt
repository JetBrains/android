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
package com.android.tools.idea.templates

import com.android.tools.idea.gradle.npw.project.GradleAndroidModuleTemplate
import com.android.tools.idea.npw.template.TemplateResolver
import com.android.tools.idea.templates.recipe.DefaultRecipeExecutor
import com.android.tools.idea.templates.recipe.RenderingContext
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.wizard.template.SourceSetType
import com.android.tools.idea.wizard.template.impl.other.folders.generateResourcesFolder
import com.android.utils.FileUtils
import com.intellij.openapi.command.WriteCommandAction
import groovy.json.StringEscapeUtils
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test

class FolderTemplatesTest {
  @get:Rule val projectRule = AndroidProjectRule.withAndroidModels()

  @Test
  fun testGenerateResourcesFolder() {
    val template =
      TemplateResolver.getTemplateByName("Res Folder") ?: throw RuntimeException("Invalid template")

    // The default module state and template
    val moduleStateBuilder = getDefaultModuleState(projectRule.project, template)
    val projectRoot = moduleStateBuilder.projectTemplateDataBuilder.topOut!!
    val moduleRoot =
      GradleAndroidModuleTemplate.createDefaultTemplateAt(File(projectRoot.path, defaultModuleName))
        .paths
        .moduleRoot!!
        .toPath()

    println("Module root: $moduleRoot")

    val context =
      RenderingContext(
        project = projectRule.project,
        module = null,
        commandName = "Run TemplateTest",
        templateData = moduleStateBuilder.build(),
        moduleRoot = moduleRoot.toFile(),
        dryRun = false,
        showErrors = true
      )
    val moduleRecipeExecutor = DefaultRecipeExecutor(context)

    // Add a build.gradle file ourselves, which we check later to see if the correct sections have
    // been added
    writeBuildGradleKtsFile(moduleRoot)

    moduleRecipeExecutor.generateResourcesFolder(
      moduleStateBuilder.build(),
      true,
      "my/res/folder",
      { "main" },
      SourceSetType.RESOURCES,
      "res"
    )

    // Applying changes is necessary to write the Gradle model to disk
    WriteCommandAction.writeCommandAction(projectRule.project).run<IOException> {
      moduleRecipeExecutor.applyChanges()
    }

    val lines = Files.readAllLines(moduleRoot.resolve("build.gradle.kts"))

    assertTrue(moduleRoot.resolve("my/res/folder").toFile().isDirectory)
    val expectedSrcDirsLine =
      "                srcDirs(\"" +
        StringEscapeUtils.escapeJava("src${File.separator}main${File.separator}res") +
        "\", \"" +
        StringEscapeUtils.escapeJava("my${File.separator}res${File.separator}folder") +
        "\")"
    assertContains(lines, expectedSrcDirsLine)
  }

  /**
   * Writes a build.gradle.kts file containing only the android {} section to the module's root
   * folder. This ensures it gets picked up by the Gradle model so the test generateResourcesFolder
   * has a place to add a sourceSets {} section
   */
  private fun writeBuildGradleKtsFile(moduleRoot: Path) {
    FileUtils.mkdirs(moduleRoot.toFile())
    FileUtils.writeToFile(moduleRoot.resolve("build.gradle.kts").toFile(), "android {}")
  }
}
