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

import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.templates.diff.TemplateDiffTestUtils.getPinnedAgpVersion
import com.android.tools.idea.templates.recipe.DefaultRecipeExecutor
import com.android.tools.idea.templates.recipe.RenderingContext
import com.android.tools.idea.templates.verifyLanguageFiles
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.findModule
import com.android.tools.idea.testing.injectBuildOutputDumpingBuildViewManager
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.Recipe
import com.android.tools.idea.wizard.template.Template
import com.android.tools.idea.wizard.template.Thumb
import com.android.utils.FileUtils
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import java.io.IOException
import java.nio.file.Path

/**
 * Generates files from a template and performs checks on them to ensure they're valid and can be
 * checked in as golden files
 *
 * For context and instructions on running and generating golden files, see go/template-diff-tests
 */
class GoldenFileValidator(
  template: Template,
  goldenDirName: String,
  private val generateLintBaseline: Boolean,
  private val gradleProjectRule: AndroidGradleProjectRule
) : ProjectRenderer(template, goldenDirName) {
  override fun handleDirectories(moduleName: String, goldenDir: Path, projectDir: Path) {
    checkProjectProperties(projectDir)
    performBuild()
    //checkLint(projectDir)
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

  /**
   * Runs Gradle Lint on the project, failing validation if there are any warnings not ignored by
   * the baseline file. To generate and update the baseline file, run the test again with
   * --test_env=GENERATE_LINT_BASELINE
   *
   * See go/template-diff-tests for more details
   */
  private fun checkLint(projectDir: Path) {
    // The baseline for each TemplateDiffTest#testMethodName is stored as
    // android-templates/testData/lintBaseline/testMethodName.xml
    val testDataBaseline =
      TemplateDiffTestUtils.getTestDataRoot().resolve("lintBaseline").resolve("$goldenDirName.xml")
    val projectBaseline = projectDir.resolve("Template test module").resolve("lint-baseline.xml")
    val setBaselinePath: Boolean

    // If GENERATE_LINT_BASELINE is specified, we need to not have the existing baseline file. When
    // there is no baseline but there are Lint errors, the baseline file will be generated
    if (generateLintBaseline) {
      setBaselinePath = true
    } else {
      // Otherwise, if we have a Lint baseline, we should copy it into the project.
      if (testDataBaseline.toFile().exists()) {
        FileUtils.copyFile(testDataBaseline, projectBaseline)
        setBaselinePath = true
        println("Using $goldenDirName.xml as lint baseline")
      } else {
        setBaselinePath = false
        println("No lint baseline for $goldenDirName found; assuming there should be no errors")
      }
    }

    setLintOptions(setBaselinePath)

    gradleProjectRule.invokeTasks("lint").apply {
      println("\n================================================================\n")

      val lintReportParser = LintReportParser(System.out)
      when (val numErrors = lintReportParser.parseLintReportInProject(projectDir)) {
        0 -> println("0 errors found")
        else -> {
          if (generateLintBaseline) {
            copyBaselineFile(projectBaseline)
            println("$numErrors lint warnings exist! Generated baseline file $goldenDirName.xml")
            println(
              "To update these files, unzip lintBaseline/ from outputs.zip to the android-templates/testData/lintBaseline directory.\n" +
                "For a remote invocation, download and unzip lintBaseline/ from outputs.zip:\n" +
                "    unzip outputs.zip \"lintBaseline/*\" -d \"$(bazel info workspace)/tools/adt/idea/android-templates/testData/\"\n" +
                "\n" +
                "For a local invocation, outputs.zip will be in bazel-testlogs:\n" +
                "    unzip $(bazel info bazel-testlogs)/tools/adt/idea/android-templates/intellij.android.templates.tests_tests__TemplateDiffTest/test.outputs/outputs.zip \\\n" +
                "    \"lintBaseline/*\" -d \"$(bazel info workspace)/tools/adt/idea/android-templates/testData/\""
            )
            // TODO: we shouldn't generate the golden files if we generate lint, but we also don't want to fail the entire test
          } else {
            fail(
              "$numErrors lint warnings exist! Either there is no baseline, or there are new warnings not in the baseline.\n" +
                "To generate the baseline file, re-run the generator with --test_env=GENERATE_LINT_BASELINE=true"
            )
          }
        }
      }
      // TODO: fail validation if a fixed warning isn't removed from the baseline (may require
      // parsing the XML?)
      println("\n================================================================\n")
    }
  }

  /**
   * Modifies gradle.build's lint block to include the options we need in order to run lint
   *
   * @param setBaselinePath whether to add a baseline option. This should only be false when there
   *   is no checked-in baseline; it needs to be true when generating one
   */
  private fun setLintOptions(setBaselinePath: Boolean) {
    val gradleBuildModel =
      ProjectBuildModel.get(gradleProjectRule.project)
        .getModuleBuildModel(gradleProjectRule.project.findModule("Template_test_module"))
    val lintBlock = gradleBuildModel!!.android().lint()

    // If we have a baseline or if we intend to generate the baseline, we need to set the file path.
    // If the baseline already exists, it's used to ignore those errors. If it doesn't exist, this
    // path is where the new baseline will be generated to
    if (setBaselinePath) {
      lintBlock.baseline().setValue("lint-baseline.xml")
    }

    // Set "warningsAsErrors" to turn the lint warnings into errors, since invokeTasks doesn't have
    // warnings output, only buildError
    lintBlock.warningsAsErrors().setValue(true)

    // Disable checks for GradleDependency, because dependencies can have new versions at any time
    // TODO: are there other issues to ignore?
    val disableList = lintBlock.disable().convertToEmptyList()
    disableList.addListValue()!!.setValue("GradleDependency")

    WriteCommandAction.writeCommandAction(gradleProjectRule.project).run<IOException> {
      gradleBuildModel.applyChanges()
    }
  }

  /**
   * Copies the baseline file generated by the Gradle Lint task from the project module files to
   * Bazel's outputs.zip
   */
  private fun copyBaselineFile(projectBaseline: Path) {
    val outputBaselineDir = TemplateDiffTestUtils.getOutputDir("lintBaseline")
    FileUtils.mkdirs(outputBaselineDir.toFile())
    FileUtils.copyFile(projectBaseline, outputBaselineDir.resolve("$goldenDirName.xml"))
  }
}
