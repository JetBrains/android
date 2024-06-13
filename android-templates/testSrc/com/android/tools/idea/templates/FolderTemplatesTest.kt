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

import com.android.tools.idea.npw.project.GradleAndroidModuleTemplate
import com.android.tools.idea.npw.model.render
import com.android.tools.idea.npw.template.TemplateResolver
import com.android.tools.idea.templates.recipe.DefaultRecipeExecutor
import com.android.tools.idea.templates.recipe.RenderingContext
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.wizard.template.BooleanParameter
import com.android.tools.idea.wizard.template.StringParameter
import com.android.tools.idea.wizard.template.WizardParameterData
import com.android.utils.FileUtils
import com.intellij.openapi.command.WriteCommandAction
import groovy.json.StringEscapeUtils
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertTrue

class FolderTemplatesTest {
  @get:Rule val projectRule = AndroidProjectRule.withAndroidModels()

  private fun checkResourcesTemplate(
    name: String,
    remapFolder: Boolean,
    location: String,
    expectedLine: String,
    expectedFolderLocation: String = location
  ) {
    val template =
      TemplateResolver.getTemplateByName(name) ?: throw RuntimeException("Invalid template")

    // These represent the options in the dialog
    val remapParam = template.parameters.find { it.name == "Change Folder Location" }
    (remapParam as BooleanParameter).value = remapFolder
    val locationParam = template.parameters.find { it.name == "New Folder Location" }
    (locationParam as StringParameter).value = location

    // The default module state and template
    val moduleStateBuilder = getDefaultModuleState(projectRule.project, template)
    val projectRoot = moduleStateBuilder.projectTemplateDataBuilder.topOut!!
    val moduleRoot =
      GradleAndroidModuleTemplate.createDefaultTemplateAt(File(projectRoot.path, defaultModuleName))
        .paths
        .moduleRoot!!
        .toPath()
    println("Module root: $moduleRoot")

    val templateData = moduleStateBuilder.build()
    val context =
      RenderingContext(
        project = projectRule.project,
        module = null,
        commandName = "Run TemplateTest",
        templateData = templateData,
        moduleRoot = moduleRoot.toFile(),
        dryRun = false,
        showErrors = true
      )
    val moduleRecipeExecutor = DefaultRecipeExecutor(context)

    // Add a build.gradle file ourselves, which we check later to see if the correct sections have
    // been added
    writeBuildGradleKtsFile(moduleRoot)

    WizardParameterData(templateData.packageName, false, "main", template.parameters)
    template.render(context, moduleRecipeExecutor)

    // Applying changes is necessary to write the Gradle model to disk
    WriteCommandAction.writeCommandAction(projectRule.project).run<IOException> {
      moduleRecipeExecutor.applyChanges()
    }

    // Check the folder exists and that relevant content is in build.gradle file
    assertTrue(moduleRoot.resolve(expectedFolderLocation).toFile().isDirectory)

    val lines = Files.readAllLines(moduleRoot.resolve("build.gradle.kts"))
    assertContains(lines, expectedLine)
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

  @Test
  fun testAIDLFolder() {
    val expectedLine =
      "                srcDirs(\"" +
        StringEscapeUtils.escapeJava("src${File.separator}main${File.separator}aidl") +
        "\", \"" +
        StringEscapeUtils.escapeJava("my${File.separator}aidl${File.separator}folder") +
        "\")"

    checkResourcesTemplate("AIDL Folder", true, "my/aidl/folder", expectedLine)
  }

  @Test
  fun testAssetsFolder() {
    val expectedLine =
      "                srcDirs(\"" +
        StringEscapeUtils.escapeJava("src${File.separator}main${File.separator}assets") +
        "\", \"" +
        StringEscapeUtils.escapeJava("my${File.separator}assets${File.separator}folder") +
        "\")"

    checkResourcesTemplate("Assets Folder", true, "my/assets/folder", expectedLine)
  }

  @Test
  fun testFontFolder() {
    val expectedLine = "android {}"

    checkResourcesTemplate("Font Folder", true, "my/font/folder", expectedLine)
  }

  @Test
  fun testJavaFolder() {
    val expectedLine =
      "                srcDirs(\"" +
        StringEscapeUtils.escapeJava("src${File.separator}main${File.separator}java") +
        "\", \"" +
        StringEscapeUtils.escapeJava("my${File.separator}JavaNotKotlin${File.separator}folder") +
        "\")"

    checkResourcesTemplate("Java Folder", true, "my/JavaNotKotlin/folder", expectedLine)
  }

  @Test
  fun testJNIFolder() {
    val expectedLine =
      "                srcDirs(\"" +
        StringEscapeUtils.escapeJava("src${File.separator}main${File.separator}jni") +
        "\", \"" +
        StringEscapeUtils.escapeJava("my${File.separator}jni${File.separator}folder") +
        "\")"

    checkResourcesTemplate("JNI Folder", true, "my/jni/folder", expectedLine)
  }

  @Test
  fun testRawResourcesFolder() {
    val expectedLine = "android {}"

    checkResourcesTemplate("Raw Resources Folder", true, "my/raw/resources/folder", expectedLine)
  }

  @Test
  fun testResFolder() {
    val expectedLine =
      "                srcDirs(\"" +
        StringEscapeUtils.escapeJava("src${File.separator}main${File.separator}res") +
        "\", \"" +
        StringEscapeUtils.escapeJava("my${File.separator}res${File.separator}folder") +
        "\")"

    checkResourcesTemplate("Res Folder", true, "my/res/folder", expectedLine)
  }

  @Test
  fun testDefaultResFolder() {
    // If not using the "Change Folder Location" option, no change should be made to the source set,
    // but the folder should still get created
    val expectedLine = "android {}"

    checkResourcesTemplate("Res Folder", false, "unused/parameter", expectedLine, "src/main/res")
  }

  @Test
  fun testJavaResourcesFolder() {
    val expectedLine =
      "                srcDirs(\"" +
        StringEscapeUtils.escapeJava("src${File.separator}main${File.separator}resources") +
        "\", \"" +
        StringEscapeUtils.escapeJava("my${File.separator}resources${File.separator}folder") +
        "\")"

    checkResourcesTemplate("Java Resources Folder", true, "my/resources/folder", expectedLine)
  }

  @Test
  fun testRenderScriptFolder() {
    val expectedLine =
      "                srcDirs(\"" +
        StringEscapeUtils.escapeJava("src${File.separator}main${File.separator}rs") +
        "\", \"" +
        StringEscapeUtils.escapeJava("my${File.separator}renderscript${File.separator}folder") +
        "\")"

    checkResourcesTemplate("RenderScript Folder", true, "my/renderscript/folder", expectedLine)
  }

  @Test
  fun testXMLResourcesFolder() {
    val expectedLine = "android {}"

    checkResourcesTemplate("XML Resources Folder", true, "my/xml/folder", expectedLine)
  }
}
