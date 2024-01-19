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
import com.android.testutils.MockitoKt.mock
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
import org.jetbrains.android.compose.stubComposableAnnotation
import org.jetbrains.android.compose.stubPreviewAnnotation
import org.jetbrains.kotlin.psi.KtExpression
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.runners.Parameterized
import org.mockito.Mockito.verifyNoInteractions

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
class ComposeUsageGroupingRuleProviderParameterizedTest(
  private val targetAnnotations: List<String>,
  private val usageAnnotations: List<String>,
) {
  @get:Rule val projectRule = AndroidProjectRule.onDisk().onEdt()

  private val fixture by lazy { projectRule.fixture }
  private val project by lazy { projectRule.project }

  private val groupingRuleProvider = ComposeUsageGroupingRuleProvider()
  private val groupingRule by lazy {
    groupingRuleProvider.getActiveRules(project).single() as UsageGroupingRuleEx
  }

  companion object {
    @Parameterized.Parameters(name = "{0}_target_{1}_element")
    @JvmStatic
    fun data(): List<Array<List<String>>> =
      listOf(
        arrayOf(listOf(), listOf()),
        arrayOf(listOf(), listOf(COMPOSABLE)),
        arrayOf(listOf(), listOf(PREVIEW)),
        arrayOf(listOf(), listOf(PREVIEW, COMPOSABLE)),
        arrayOf(listOf(COMPOSABLE), listOf()),
        arrayOf(listOf(COMPOSABLE), listOf(COMPOSABLE)),
        arrayOf(listOf(COMPOSABLE), listOf(PREVIEW)),
        arrayOf(listOf(COMPOSABLE), listOf(PREVIEW, COMPOSABLE)),
      )

    private const val COMPOSABLE = "Composable"
    private const val PREVIEW = "Preview"
  }

  @RunsInEdt
  @Test
  fun getParentGroupsFor() {
    val (usage, targets) =
      fixture.configureCode(
        targetAnnotations,
        usageAnnotations,
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
        targetAnnotations,
        usageAnnotations,
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
        targetAnnotations,
        usageAnnotations,
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
        targetAnnotations,
        usageAnnotations,
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
      COMPOSABLE !in targetAnnotations -> assertThat(usageGroups).isEmpty()
      COMPOSABLE in usageAnnotations && PREVIEW in usageAnnotations ->
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
    (module.getModuleSystem() as DefaultModuleSystem).usesCompose = true
    stubComposableAnnotation()
    stubPreviewAnnotation()
    // language=kotlin
    val contents =
      """
      package the.regrettes
      import ${ComposeFqNames.Composable.asString()}
      import androidx.compose.ui.tooling.preview.Preview
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
