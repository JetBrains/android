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
import com.google.common.io.Files
import com.intellij.build.events.MessageEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

/**
 * Generates files from a template and performs checks on them to ensure they're valid and can be
 * checked in as golden files
 *
 * For context and instructions on running and generating golden files, see go/template-diff-tests
 */
class GoldenFileValidator(
  template: Template,
  goldenDirName: String,
  private val gradleProjectRule: AndroidGradleProjectRule,
) : ProjectRenderer(template, goldenDirName) {
  override fun handleDirectories(moduleName: String, goldenDir: Path, projectDir: Path) {
    checkProjectProperties(projectDir)
    performBuild()

    // Although this is generating files, it's run as part of GoldenFileValidator instead of
    // GoldenFileGenerator to avoid Gradle syncing twice
    generateLintBaseline(projectDir)
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
    templateRecipeExecutor: DefaultRecipeExecutor,
  ) {
    gradleProjectRule.load(TestProjectPaths.NO_MODULES, getPinnedAgpVersion()) {
      super.renderTemplate(
        project,
        moduleRecipe,
        context,
        moduleRecipeExecutor,
        templateRecipeExecutor,
      )
    }
  }

  private val warningsToIgnore =
    mapOf(
      // These will apply to all templates
      "*" to
        setOf(
          "platform-tools package is not installed.", // In every TemplateDiffTest
          "Unable to initialize metrics", // In remote config
        ),
      // TODO: b/390508868
      "testAutomotiveMessagingServiceWithKotlin" to
        setOf(
          "'class UnreadConversation : Any' is deprecated. Deprecated in Java.",
          "'fun setUnreadConversation(p0: NotificationCompat.CarExtender.UnreadConversation?): NotificationCompat.CarExtender' is deprecated. Deprecated in Java.",
        ),
      // TODO: b/390509438
      "testNewSettingsActivityWithKotlinMultipleScreens" to
        setOf(
          "'fun setTargetFragment(p0: Fragment?, p1: Int): Unit' is deprecated. Deprecated in Java."
        ),
      // TODO: b/390509533
      "testNewTabbedActivityWithKotlin" to
        setOf(
          "'class FragmentPagerAdapter : PagerAdapter' is deprecated. Deprecated in Java.",
          "'constructor(p0: FragmentManager): FragmentPagerAdapter' is deprecated. Deprecated in Java.",
          "'class FragmentPagerAdapter : PagerAdapter' is deprecated. Deprecated in Java.",
        ),
      // TODO: b/390508796
      "testGameActivityWithKotlin" to
        setOf(
          "'var systemUiVisibility: Int' is deprecated. Deprecated in Java.",
          "'static field SYSTEM_UI_FLAG_IMMERSIVE_STICKY: Int' is deprecated. Deprecated in Java.",
          "'static field SYSTEM_UI_FLAG_LAYOUT_STABLE: Int' is deprecated. Deprecated in Java.",
          "'static field SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION: Int' is deprecated. Deprecated in Java.",
          "'static field SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN: Int' is deprecated. Deprecated in Java.",
          "'static field SYSTEM_UI_FLAG_HIDE_NAVIGATION: Int' is deprecated. Deprecated in Java.",
          "'static field SYSTEM_UI_FLAG_FULLSCREEN: Int' is deprecated. Deprecated in Java.",
        ),
      // TODO: b/390509166
      "testNewIntentServiceWithKotlin" to
        setOf(
          "'class IntentService : Service' is deprecated. Deprecated in Java.",
          "'constructor(p0: String!): IntentService' is deprecated. Deprecated in Java.",
          "This declaration overrides a deprecated member but is not marked as deprecated itself. Please add the '@Deprecated' annotation or suppress the diagnostic.",
        ),
      // TODO: b/390510059
      "testNewGoogleAdMobFragmentWithKotlin" to
        setOf(
          "This declaration overrides a deprecated member but is not marked as deprecated itself. Please add the '@Deprecated' annotation or suppress the diagnostic.",
          "'fun onActivityCreated(p0: Bundle?): Unit' is deprecated. Deprecated in Java.",
        ),
      // TODO: b/390509936
      "testNewFullscreenFragmentWithKotlin" to
        setOf(
          "'static field SYSTEM_UI_FLAG_LOW_PROFILE: Int' is deprecated. Deprecated in Java.",
          "'static field SYSTEM_UI_FLAG_FULLSCREEN: Int' is deprecated. Deprecated in Java.",
          "'static field SYSTEM_UI_FLAG_LAYOUT_STABLE: Int' is deprecated. Deprecated in Java.",
          "'static field SYSTEM_UI_FLAG_IMMERSIVE_STICKY: Int' is deprecated. Deprecated in Java.",
          "'static field SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION: Int' is deprecated. Deprecated in Java.",
          "'static field SYSTEM_UI_FLAG_HIDE_NAVIGATION: Int' is deprecated. Deprecated in Java.",
          "'var systemUiVisibility: Int' is deprecated. Deprecated in Java.",
          "'static field SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN: Int' is deprecated. Deprecated in Java.",
        ),
      // TODO: b/390510577
      "testNewPrimaryDetailFlowWithKotlin" to
        setOf(
          "'fun startDrag(p0: ClipData!, p1: View.DragShadowBuilder!, p2: Any!, p3: Int): Boolean' is deprecated. Deprecated in Java."
        ),
      // TODO: b/390509550
      "testAutomotiveMediaService" to
        setOf(
          "Java compiler has deprecated support for compiling with source/target compatibility version 8."
        ),
      // TODO: b/390509164
      "testNewFullscreenActivityWithKotlin" to
        setOf(
          "'var systemUiVisibility: Int' is deprecated. Deprecated in Java.",
          "'static field SYSTEM_UI_FLAG_LOW_PROFILE: Int' is deprecated. Deprecated in Java.",
          "'static field SYSTEM_UI_FLAG_FULLSCREEN: Int' is deprecated. Deprecated in Java.",
          "'static field SYSTEM_UI_FLAG_LAYOUT_STABLE: Int' is deprecated. Deprecated in Java.",
          "'static field SYSTEM_UI_FLAG_IMMERSIVE_STICKY: Int' is deprecated. Deprecated in Java.",
          "'static field SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION: Int' is deprecated. Deprecated in Java.",
          "'static field SYSTEM_UI_FLAG_HIDE_NAVIGATION: Int' is deprecated. Deprecated in Java.",
          "'static field SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN: Int' is deprecated. Deprecated in Java.",
        ),
      // TODO: b/390509164
      "testNewFullscreenActivityWithKotlin_activityNotInRootPackage" to
        setOf(
          "'var systemUiVisibility: Int' is deprecated. Deprecated in Java.",
          "'static field SYSTEM_UI_FLAG_LOW_PROFILE: Int' is deprecated. Deprecated in Java.",
          "'static field SYSTEM_UI_FLAG_FULLSCREEN: Int' is deprecated. Deprecated in Java.",
          "'static field SYSTEM_UI_FLAG_LAYOUT_STABLE: Int' is deprecated. Deprecated in Java.",
          "'static field SYSTEM_UI_FLAG_IMMERSIVE_STICKY: Int' is deprecated. Deprecated in Java.",
          "'static field SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION: Int' is deprecated. Deprecated in Java.",
          "'static field SYSTEM_UI_FLAG_HIDE_NAVIGATION: Int' is deprecated. Deprecated in Java.",
          "'static field SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN: Int' is deprecated. Deprecated in Java.",
        ),
      // TODO: b/390510579
      "testAutomotiveMediaServiceWithKotlin" to
        setOf(
          "'static field FLAG_HANDLES_MEDIA_BUTTONS: Int' is deprecated. Deprecated in Java.",
          "'static field FLAG_HANDLES_TRANSPORT_CONTROLS: Int' is deprecated. Deprecated in Java.",
        ),
      // TODO: b/390510067
      "testNewTvActivityWithKotlin" to
        setOf(
          "'class SimpleTarget<Z : Any!> : BaseTarget<Z!>' is deprecated. Deprecated in Java.",
          "'val defaultDisplay: Display!' is deprecated. Deprecated in Java.",
          "'fun getMetrics(p0: DisplayMetrics!): Unit' is deprecated. Deprecated in Java.",
          "'fun getSerializableExtra(p0: String!): Serializable?' is deprecated. Deprecated in Java.",
        ),
    )

  private fun isValidWarning(message: String): Boolean {
    // If the message doesn't match any known baseline warnings, we should fail the test
    return warningsToIgnore
      .filterKeys { key -> key == "*" || key == goldenDirName }
      .values
      .flatten()
      .none { warning -> warning in message }
  }

  /** Build the project to ensure it compiles and that there are no warnings */
  private fun performBuild() {
    var failed: MessageEvent? = null
    injectBuildOutputDumpingBuildViewManager(
      gradleProjectRule.project,
      gradleProjectRule.project,
    ) { buildEvent ->
      if (buildEvent is MessageEvent && buildEvent.kind == MessageEvent.Kind.WARNING) {
        if (isValidWarning(buildEvent.message)) {
          println("Build Warning: $buildEvent")
          if (failed == null) {
            // Only throw the first warning, but print all of them to test log
            failed = buildEvent
          }
        } else {
          println("(Ignored) Build Warning: $buildEvent")
        }
      }
    }
    gradleProjectRule.invokeTasks("compileDebugSources").apply { // "assembleDebug" is too slow
      buildError?.printStackTrace()
      assertTrue("Project didn't compile correctly", isBuildSuccessful)
      assertNull("Project has warnings. Earliest warning: " + failed?.message, failed)
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
   * Runs Gradle Lint on the project and generates the baseline files to the output directory. The
   * baseline files should be unzipped to the source tree along with the golden files, and the
   * resulting CL acts as a manual diff to ensure that potentially added warnings are intentional.
   *
   * See go/template-diff-tests for more details
   */
  private fun generateLintBaseline(projectDir: Path) {
    // The baseline for each TemplateDiffTest#testMethodName is stored as
    // android-templates/testData/lintBaseline/testMethodName.xml
    val projectBaseline = projectDir.resolve("Template test module").resolve("lint-baseline.xml")

    setLintOptions()

    gradleProjectRule.invokeTasks("lint").apply {
      println("\n================================================================\n")

      val lintReportParser = LintReportParser(System.out)
      val numErrors = lintReportParser.parseLintReportInProject(projectDir)
      copyBaselineFile(projectBaseline)
      println("$numErrors lint warnings exist! Generated baseline file $goldenDirName.xml")

      println("\n================================================================\n")
    }
  }

  /** Modifies gradle.build's lint block to include the options we need in order to run lint */
  private fun setLintOptions() {
    val gradleBuildModel =
      ProjectBuildModel.get(gradleProjectRule.project)
        .getModuleBuildModel(gradleProjectRule.project.findModule("Template_test_module"))
    val lintBlock = gradleBuildModel!!.android().lint()

    // Since we intend to generate the baseline, we need to set the file path, which is where the
    // new baseline will be generated to, relative to the module directory. The same path is also
    // used to provide the baseline to Lint, but we aren't doing that
    lintBlock.baseline().setValue("lint-baseline.xml")

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
   * Bazel's outputs.zip. Replace the AGP version in the XML with placeholders because it creates
   * noise in Git diffs
   */
  private fun copyBaselineFile(projectBaseline: Path) {
    val outputBaselineDir = TemplateDiffTestUtils.getOutputDir("lintBaseline")
    FileUtils.mkdirs(outputBaselineDir.toFile())
    val outputBaselinePath = outputBaselineDir.resolve("$goldenDirName.xml")

    var newContent = ""
    val issuesTagRegex = Regex("<issues.*by=\"lint (.*?)\".*>")
    val lines = Files.readLines(projectBaseline.toFile(), Charset.defaultCharset())

    // Read the entire baseline file, replacing the AGP version in the <issues> tag attributes with
    // a placeholder. Also insert a comment near the top of the file that this should not be
    // edited manually
    lines.forEach { line ->
      val issuesMatch = issuesTagRegex.matchEntire(line)
      if (issuesMatch != null) {
        newContent +=
          "<!-- This file should not be edited manually! See go/template-diff-tests -->\n"
        val agpVersion = issuesMatch.groups[1]!!.value
        newContent += line.replace(agpVersion, "%AGP_VERSION_PLACEHOLDER%") + "\n"
        println("Replacing AGP version $agpVersion with placeholder in $outputBaselinePath")
      } else {
        newContent += line + "\n"
      }
    }

    FileUtils.writeToFile(outputBaselinePath.toFile(), newContent)
  }
}
