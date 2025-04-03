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
package com.android.tools.compose

import androidx.compose.compiler.plugins.kotlin.ComposeFqNames
import com.android.tools.idea.project.DefaultModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.getEnclosing
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.psi.PsiElement
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.usages.PsiElementUsageTarget
import com.intellij.usages.Usage
import com.intellij.usages.UsageGroup
import com.intellij.usages.UsageTarget
import com.intellij.usages.impl.rules.UsageGroupingRulesDefaultRanks
import com.intellij.usages.rules.PsiElementUsage
import com.intellij.usages.rules.UsageGroupingRuleEx
import org.jetbrains.android.compose.addComposeRuntimeDep
import org.jetbrains.android.compose.addComposeUiToolingPreviewDep
import org.jetbrains.kotlin.psi.KtExpression
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.runners.Parameterized
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.kotlin.mock

/** Test basic cases for the [ComposeUsageGroupingRuleProvider]. */
@RunWith(JUnit4::class)
class ComposeUsageGroupingRuleProviderTest {
  @get:Rule val projectRule = AndroidProjectRule.onDisk()

  private val project by lazy { projectRule.project }

  private val groupingRuleProvider = ComposeUsageGroupingRuleProvider()
  private val groupingRule by lazy {
    groupingRuleProvider.getActiveRules(project).single() as UsageGroupingRuleEx
  }

  @Test
  fun activeRulesAreAllRules() {
    assertThat(groupingRuleProvider.getActiveRules(project))
      .isEqualTo(groupingRuleProvider.getAllRules(project, null))
  }

  @Test
  fun groupingRuleHasCorrectIcon() {
    // TODO(b/279446921): Replace with @Preview icon when available.
    assertThat(groupingRule.icon).isNull()
  }

  @Test
  fun groupingRuleHasCorrectTitle() {
    assertThat(groupingRule.title).isEqualTo(ComposeBundle.message("separate.preview.usages"))
  }

  @Test
  fun groupingRuleHasCorrectRank() {
    assertThat(groupingRule.rank)
      .isGreaterThan(UsageGroupingRulesDefaultRanks.AFTER_SCOPE.absoluteRank)
    assertThat(groupingRule.rank)
      .isAtMost(UsageGroupingRulesDefaultRanks.BEFORE_USAGE_TYPE.absoluteRank)
  }

  @Test
  fun previewGroupHasCorrectIcon() {
    // TODO(b/279446921): Replace with @Preview icon when available.
    assertThat(PreviewUsageGroup.icon).isNull()
  }

  @Test
  fun productionGroupHasCorrectIcon() {
    assertThat(ProductionUsageGroup.icon).isNull()
  }

  @Test
  fun previewGroupHasCorrectText() {
    assertThat(PreviewUsageGroup.presentableGroupText)
      .isEqualTo(ComposeBundle.message("usage.group.in.preview.function"))
  }

  @Test
  fun productionGroupHasCorrectText() {
    assertThat(ProductionUsageGroup.presentableGroupText)
      .isEqualTo(ComposeBundle.message("usage.group.in.nonpreview.function"))
  }

  @Test
  fun nonPsiElementUsage() {
    val usage: Usage = mock()
    val targets: Array<UsageTarget> = arrayOf(mock(), mock(), mock())
    assertThat(groupingRule.getParentGroupsFor(usage, targets)).isEmpty()
    verifyNoInteractions(usage, *targets)
  }
}

/**
 * Test more complex cases for the [ComposeUsageGroupingRuleProvider], covering all relevant
 * potential annotations.
 */
@RunWith(Parameterized::class)
class ComposeUsageGroupingRuleProviderParameterizedTest(private val testConfig: TestConfig) {
  @get:Rule val projectRule = AndroidProjectRule.onDisk().onEdt()

  private val fixture by lazy { projectRule.fixture }
  private val project by lazy { projectRule.project }

  private val groupingRuleProvider = ComposeUsageGroupingRuleProvider()
  private val groupingRule by lazy {
    groupingRuleProvider.getActiveRules(project).single() as UsageGroupingRuleEx
  }

  companion object {
    data class TestConfig(val targetAnnotations: List<String>, val usageAnnotations: List<String>) {
      override fun toString(): String = "target: $targetAnnotations usage: $usageAnnotations"
    }

    @Parameterized.Parameters(name = "{0}")
    @JvmStatic
    fun data(): List<TestConfig> =
      listOf(
        TestConfig(listOf(), listOf()),
        TestConfig(listOf(), listOf(COMPOSABLE)),
        TestConfig(listOf(COMPOSABLE), listOf()),
        TestConfig(listOf(COMPOSABLE), listOf(COMPOSABLE)),
      ) +
        PREVIEW_ANNOTATIONS.flatMap { preview ->
          listOf(
            TestConfig(listOf(), listOf(preview)),
            TestConfig(listOf(), listOf(preview, COMPOSABLE)),
            TestConfig(listOf(COMPOSABLE), listOf(preview)),
            TestConfig(listOf(COMPOSABLE), listOf(preview, COMPOSABLE)),
          )
        }

    private val PREVIEW_ANNOTATIONS =
      listOf(
        "Preview",
        "PreviewDynamicColors",
        "PreviewFontScale",
        "PreviewLightDark",
        "PreviewParameter",
        "PreviewScreenSizes",
      )

    private const val COMPOSABLE = "Composable"
  }

  @Before
  fun setUp() {
    (fixture.module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
    fixture.addComposeRuntimeDep()
    fixture.addComposeUiToolingPreviewDep(version = "1.7.5")
  }

  @RunsInEdt
  @Test
  fun getParentGroupsFor() {
    val (usage, targets) =
      fixture.configureCode(
        testConfig.targetAnnotations,
        testConfig.usageAnnotations,
        "target|Function() // usage",
        "fun target|Function()",
      )
    checkUsageGroups(groupingRule.getParentGroupsFor(usage, targets))
  }

  @RunsInEdt
  @Test
  fun getParentGroupsFor_nested() {
    val (usage, targets) =
      fixture.configureCode(
        testConfig.targetAnnotations,
        testConfig.usageAnnotations,
        "target|Function() // nested usage",
        "fun target|Function()",
      )
    checkUsageGroups(groupingRule.getParentGroupsFor(usage, targets))
  }

  @RunsInEdt
  @Test
  fun ignoresNonKtFunctionTarget() {
    val (usage, targets) =
      fixture.configureCode(
        testConfig.targetAnnotations,
        testConfig.usageAnnotations,
        "target|Function() // usage",
        "PROP|ERTY",
        "fun target|Function()",
      )
    checkUsageGroups(groupingRule.getParentGroupsFor(usage, targets))
  }

  @RunsInEdt
  @Test
  fun ignoresNonPsiElementTarget() {
    val (usage, targets) =
      fixture.configureCode(
        testConfig.targetAnnotations,
        testConfig.usageAnnotations,
        "target|Function() // usage",
        "fun target|Function()",
      )
    val usageTarget: UsageTarget = mock()
    checkUsageGroups(groupingRule.getParentGroupsFor(usage, arrayOf(usageTarget, *targets)))
    verifyNoInteractions(usageTarget)
  }

  /**
   * Asserts that we get [PreviewUsageGroup] iff all the annotations are present, else
   * [ProductionUsageGroup].
   */
  private fun checkUsageGroups(usageGroups: List<UsageGroup>) {
    when {
      COMPOSABLE !in testConfig.targetAnnotations -> assertThat(usageGroups).isEmpty()
      COMPOSABLE in testConfig.usageAnnotations &&
        PREVIEW_ANNOTATIONS.any(testConfig.usageAnnotations::contains) ->
        assertThat(usageGroups).containsExactly(PreviewUsageGroup)
      else -> assertThat(usageGroups).containsExactly(ProductionUsageGroup)
    }
  }

  /** Adds code to the project with annotated function and function usage. */
  private fun CodeInsightTestFixture.configureCode(
    targetAnnotations: List<String>,
    usageAnnotations: List<String>,
    elementWindow: String,
    vararg targetWindows: String,
  ): Pair<Usage, Array<out UsageTarget>> {
    val previewImports =
      usageAnnotations.filter(PREVIEW_ANNOTATIONS::contains).joinToString("\n") {
        "import androidx.compose.ui.tooling.preview.$it"
      }
    // language=kotlin
    val contents =
      """
      package the.regrettes
      import ${ComposeFqNames.Composable.asString()}
      $previewImports
      private const val PROPERTY = 3
      ${targetAnnotations.joinToString(" ") { "@$it" }}
      fun targetFunction() {}
      ${usageAnnotations.joinToString(" ") { "@$it" }}
      fun usageFunction() {
        fun nestedUsageFunction() {
          fun nestedNestedUsageFunction() {
            targetFunction() // nested usage
          }
        }
        targetFunction() // usage
      }
      """
        .trimIndent()

    val file = addFileToProject("/src/the/regrettes/LaDiDa.kt", contents)
    openFileInEditor(file.virtualFile)
    val element = getEnclosing<KtExpression>(elementWindow)
    val targets =
      targetWindows.map { getEnclosing<KtExpression>(it) }.map(::TestUsageTarget).toTypedArray()

    return TestUsage(element) to targets
  }

  private class TestUsage(private val psiElement: PsiElement) : PsiElementUsage {
    override fun navigate(requestFocus: Boolean) {}

    override fun canNavigate() = false

    override fun canNavigateToSource() = false

    override fun getPresentation() = throw NotImplementedError()

    override fun isValid() = true

    override fun isReadOnly() = false

    override fun getLocation() = null

    override fun selectInEditor() {}

    override fun highlightInEditor() {}

    override fun getElement() = psiElement

    override fun isNonCodeUsage() = false
  }

  /** Test class so we can easily create instances of [PsiElementUsageTarget]. */
  private class TestUsageTarget(private val psiElement: PsiElement) : PsiElementUsageTarget {
    override fun navigate(requestFocus: Boolean) {}

    override fun canNavigate() = true

    override fun canNavigateToSource() = true

    override fun getName(): String = psiElement.text

    override fun getPresentation() = null

    override fun isValid() = true

    override fun findUsages() {}

    override fun getElement() = psiElement
  }
}
