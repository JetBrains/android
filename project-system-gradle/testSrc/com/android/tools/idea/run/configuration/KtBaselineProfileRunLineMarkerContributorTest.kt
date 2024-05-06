/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.run.configuration

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.RunsInEdt
import junit.framework.TestCase.assertNull
import org.jetbrains.kotlin.analysis.api.KtAnalysisApiInternals
import org.jetbrains.kotlin.analysis.api.lifetime.KtReadActionConfinementLifetimeToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertNotNull

@OptIn(KtAnalysisApiInternals::class)
class KtBaselineProfileRunLineMarkerContributorTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory().onEdt()

  private val expectedGenerateBaselineProfileAction by lazy(LazyThreadSafetyMode.PUBLICATION) {
    ActionManager.getInstance().getAction("AndroidX.BaselineProfile.RunGenerate")
  }
  private val contributor by lazy(LazyThreadSafetyMode.PUBLICATION) {
    BaselineProfileRunLineMarkerContributor()
  }

  companion object {
    private val KT_SRC_FILE_HEADER = """
      package com.example.baselineprofile

      import androidx.benchmark.macro.junit4.BaselineProfileRule
      import androidx.test.ext.junit.runners.AndroidJUnit4
      import org.junit.Rule
      import org.junit.Test
      import org.junit.runner.RunWith

    """.trimIndent()
  }

  @Before
  fun setUp() {

    StudioFlags.GENERATE_BASELINE_PROFILE_GUTTER_ICON.override(true)

    // BaselineProfileRunLineMarkerContributor utilizes KtAnalysisSession that is not allowed to run on edt.
    // Since this is a test, we can enable this here.
    KtReadActionConfinementLifetimeToken.allowOnEdt.set(true)

    data class FileAndContent(val projectFilePath: String, val content: String)
    listOf(
      FileAndContent(
        projectFilePath = "src/androidx/benchmark/macro/junit4/BaselineProfileRule.kt",
        content = """
          package androidx.benchmark.macro.junit4
          open class BaselineProfileRule
      """.trimIndent()
      ),
      FileAndContent(
        projectFilePath = "src/androidx/benchmark/macro/junit4/MacrobenchmarkRule.kt",
        content = """
          package androidx.benchmark.macro.junit4
          open class MacrobenchmarkRule
      """.trimIndent()
      ),
      FileAndContent(
        projectFilePath = "src/org/junit/Annotations.kt",
        content = """
          package org.junit
          annotation class Rule
          annotation class Test
      """.trimIndent()
      ),
      FileAndContent(
        projectFilePath = "src/org/junit/runner/RunWith.kt",
        content = """
          package org.junit.runner
          open class RunWith
      """.trimIndent()
      )
    ).forEach { projectRule.fixture.addFileToProject(it.projectFilePath, it.content) }
  }

  @Test
  @RunsInEdt
  fun `when Kotlin class does NOT have BaselineProfileRule, it should NOT show contributor`() {
    val sourceFile = addKtBaselineProfileGeneratorToProject("""
        $KT_SRC_FILE_HEADER
        class BaselineProfileGenerator {
          @Test
          fun generate() { }
        }
      """.trimIndent())

    assertContributorInfoNull(sourceFile.classIdentifierNamed("BaselineProfileGenerator"))
    assertContributorInfoNull(sourceFile.funNamed("generate"))
  }

  @Test
  @RunsInEdt
  fun `when Kotlin class has BaselineProfileRule, it should show contributor`() {
    val sourceFile = addKtBaselineProfileGeneratorToProject("""
        $KT_SRC_FILE_HEADER
        class BaselineProfileGenerator {

          @get:Rule
          val rule = BaselineProfileRule()

          @Test
          fun generate() { }
        }
      """.trimIndent())

    assertContributorInfo(sourceFile.classIdentifierNamed("BaselineProfileGenerator"))

    assertTestContributorInfo(sourceFile.funNamed("generate"))
  }

  @Test
  @RunsInEdt
  fun `when Kotlin inner class has BaselineProfileRule, outer class methods should NOT show contributor`() {
    val sourceFile = addKtBaselineProfileGeneratorToProject("""
        $KT_SRC_FILE_HEADER
        class BaselineProfileGenerator {

          class SomeInnerClass {
            @get:Rule
            val rule = BaselineProfileRule()
          }

          @Test
          fun test() { }
        }
      """.trimIndent())

    // Outer class
    assertContributorInfoNull(sourceFile.classIdentifierNamed("BaselineProfileGenerator"))
    assertContributorInfoNull(sourceFile.funNamed("test"))

    // Inner class
    assertContributorInfo(sourceFile.classIdentifierNamed("SomeInnerClass"))
  }

  @Test
  @RunsInEdt
  fun `when Kotlin outer class has BaselineProfileRule, inner class methods should not show contributor`() {
    val sourceFile = addKtBaselineProfileGeneratorToProject("""
        $KT_SRC_FILE_HEADER
        class BaselineProfileGenerator {

          @get:Rule
          val rule = BaselineProfileRule()

          class SomeInnerClass {
            @Test
            fun test() { }
          }
        }
      """.trimIndent())

    // Outer class
    assertContributorInfo(sourceFile.classIdentifierNamed("BaselineProfileGenerator"))

    // Inner class
    assertContributorInfoNull(sourceFile.classIdentifierNamed("SomeInnerClass"))
    assertContributorInfoNull(sourceFile.funNamed("test"))
  }

  @Test
  @RunsInEdt
  fun `when Java class has MacroBenchmarkRule, it should show, it should not show contributor`() {
    val sourceFile = addKtBaselineProfileGeneratorToProject("""
        package com.example.baselineprofile

        import androidx.benchmark.macro.junit4.MacrobenchmarkRule
        import androidx.test.ext.junit.runners.AndroidJUnit4

        import org.junit.Rule
        import org.junit.Test
        import org.junit.runner.RunWith

        @RunWith(AndroidJUnit4::class)
        class StartupBenchmarks {

          @Rule
          val rule = MacrobenchmarkRule()

          @Test
          public fun foo() {}

          public fun bar() {}
        }
      """.trimIndent())

    // Outer class
    assertTestContributorInfo(sourceFile.classIdentifierNamed("StartupBenchmarks"))

    // @Test
    assertTestContributorInfo(sourceFile.funNamed("foo"))

    // not @Test
    assertContributorInfoNull(sourceFile.funNamed("bar"))
  }

  private fun assertContributorInfo(psiElement: PsiElement) {
    val info = contributor.getInfo(psiElement)
    assertNotNull(info) {
      "No line marker contributor was produced for psi element."
    }
    assert(info.actions.size == 5) {
      "Unexpected number of actions for line marker contributor."
    }
    assert(info.actions[0].equals(expectedGenerateBaselineProfileAction)) {
      "Line marker contributor should contains a single BaselineProfileAction."
    }
  }

  private fun assertTestContributorInfo(psiElement: PsiElement) {
    val info = contributor.getInfo(psiElement)
    assertNotNull(info) {
      "No line marker contributor was produced for psi element."
    }
    assert(info.actions.size == 3) {
      "Unexpected number of actions for line marker contributor."
    }
    assert(!info.actions[0].equals(expectedGenerateBaselineProfileAction)) {
      "Line marker contributor should not contain a BaselineProfileAction."
    }
  }

  private fun assertContributorInfoNull(psiElement: PsiElement) {
    assertNull(contributor.getInfo(psiElement))
  }

  private fun addKtBaselineProfileGeneratorToProject(content: String): KtBaselineProfileGeneratorSourceFile =
    KtBaselineProfileGeneratorSourceFile(
      projectRule.fixture.addFileToProject(
        "src/com/example/baselineprofile/BaselineProfileGenerator.kt",
        content
      )
    )

  private class KtBaselineProfileGeneratorSourceFile(private val psiFile: PsiFile) {
     fun classIdentifierNamed(name: String): PsiElement {
      val el = PsiTreeUtil.collectElements(psiFile) { it is KtClass }
        .toList()
        .map { it as KtClass }
        .firstOrNull { it.getClassId()?.shortClassName?.identifier == name }
      assertNotNull(el) { "No class named `$name` was found." }
      val identifier = el
        .collectDescendantsOfType<PsiElement> { it.node.elementType == KtTokens.IDENTIFIER }
        .firstOrNull { it.node.text == name }
      assertNotNull(identifier) { "Identifier PsiElement `$name` was not found." }
      return identifier
    }

    fun funNamed(name: String): PsiElement {
      val el = PsiTreeUtil.collectElements(psiFile) { it is KtNamedFunction }
        .toList()
        .map { it as KtNamedFunction }
        .firstOrNull { it.name == name }
      assertNotNull(el) { "No fun named `$name` was found."}
      val identifier = el
        .collectDescendantsOfType<PsiElement> { it.node.elementType == KtTokens.IDENTIFIER }
        .firstOrNull { it.node.text == name }
      assertNotNull(identifier) { "No fun named `$name` was found." }
      return identifier
    }
  }
}
