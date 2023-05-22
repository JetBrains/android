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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.npw.template.TemplateResolver
import com.android.tools.idea.templates.ProjectStateCustomizer
import com.android.tools.idea.templates.TemplateStateCustomizer
import com.android.tools.idea.templates.diff.TemplateDiffTestUtils.getPinnedAgpVersion
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.StringParameter
import com.intellij.openapi.project.Project
import com.intellij.testFramework.DisposableRule
import kotlin.system.measureTimeMillis
import org.jetbrains.android.AndroidTestBase
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Template test that generates the template files and diffs them against golden files located in
 * android-templates/testData/golden
 */
@RunWith(Parameterized::class)
class TemplateDiffTest(private val testMode: TestMode) {
  @get:Rule
  val projectRule: TestRule =
    if (shouldUseGradle()) AndroidGradleProjectRule() else AndroidProjectRule.withAndroidModels()

  @get:Rule val disposableRule = DisposableRule()

  companion object {
    /**
     * Utilizes parameterized test to decide which modes to run the test in. When DIFFING the
     * template-generated files against golden files, we do not run Gradle sync, to keep the test
     * fast.
     *
     * When we need to validate and generate the golden files however, we run the first part,
     * VALIDATING, with Gradle sync, which calls into BaselineValidator that also builds and Lints.
     * Then, after the template is validated, we generate the golden files WITHOUT Gradle sync, to
     * have them be diff-able without syncing.
     */
    @JvmStatic
    @Parameterized.Parameters
    fun data(): List<TestMode> {
      return if (shouldGenerateGolden()) {
        listOf(TestMode.VALIDATING, TestMode.GENERATING)
      } else {
        listOf(TestMode.DIFFING)
      }
    }

    /**
     * Gets the system property for whether to generate and overwrite the golden files. This can be
     * run from Bazel with the option: --test_env=GENERATE_GOLDEN=true
     *
     * Or from IDEA by setting the environment variable: GENERATE_GOLDEN=true
     */
    private fun shouldGenerateGolden(): Boolean {
      return System.getenv("GENERATE_GOLDEN")?.equals("true") ?: false
    }
  }

  @Before
  fun setUp() {
    getPinnedAgpVersion().agpVersion?.let { StudioFlags.AGP_VERSION_TO_USE.override(it) }
    println("Current test mode: $testMode")
  }

  @After
  fun tearDown() {
    StudioFlags.AGP_VERSION_TO_USE.clearOverride()
  }

  /**
   * Checks the given template in the given category. Supports overridden template values.
   *
   * @param name the template name
   * @param customizers An instance of [ProjectStateCustomizer]s used for providing template and
   *   project overrides.
   */
  private fun checkCreateTemplate(
    name: String,
    vararg customizers: ProjectStateCustomizer,
    templateStateCustomizer: TemplateStateCustomizer = mapOf(),
    category: Category? = null,
    formFactor: FormFactor? = null,
    avoidModifiedModuleName: Boolean = false,
  ) {
    AndroidTestBase.ensureSdkManagerAvailable(disposableRule.disposable)
    val template = TemplateResolver.getTemplateByName(name, category, formFactor)!!

    templateStateCustomizer.forEach { (parameterName: String, overrideValue: String) ->
      val p = template.parameters.find { it.name == parameterName }!! as StringParameter
      p.value = overrideValue
    }

    val msToCheck = measureTimeMillis {
      val projectName = "${template.name}_default"
      val project: Project =
        if (shouldUseGradle()) {
          (projectRule as AndroidGradleProjectRule).project
        } else {
          (projectRule as AndroidProjectRule).project
        }
      val projectRenderer: ProjectRenderer =
        when (testMode) {
          TestMode.DIFFING -> ProjectDiffer(template)
          TestMode.VALIDATING ->
            BaselineValidator(template, projectRule as AndroidGradleProjectRule)
          TestMode.GENERATING -> BaselineGenerator(template)
        }

      // TODO: We need to check more combinations of different moduleData/template params here.
      // Running once to make it as easy as possible.
      projectRenderer.renderProject(project, projectName, avoidModifiedModuleName, *customizers)
    }
    println("Checked $name successfully in ${msToCheck}ms")
  }

  @MustBeDocumented
  @Retention(AnnotationRetention.RUNTIME)
  @Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER
  )
  annotation class TemplateCheck

  // --- Activity templates ---
  @TemplateCheck
  @Test
  fun testNewEmptyViewActivity() {
    checkCreateTemplate("Empty Views Activity")
  }

  enum class TestMode {
    DIFFING,
    VALIDATING,
    GENERATING
  }

  private fun shouldUseGradle(): Boolean {
    return when (testMode) {
      TestMode.DIFFING -> false
      TestMode.VALIDATING -> true
      TestMode.GENERATING -> false
    }
  }
}
