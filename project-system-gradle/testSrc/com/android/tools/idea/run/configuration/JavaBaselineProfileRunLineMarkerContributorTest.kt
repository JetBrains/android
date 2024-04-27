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
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.RunsInEdt
import junit.framework.TestCase.assertNull
import org.jetbrains.kotlin.analysis.api.KtAnalysisApiInternals
import org.jetbrains.kotlin.analysis.api.lifetime.KtReadActionConfinementLifetimeToken
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertNotNull

@OptIn(KtAnalysisApiInternals::class)
class JavaBaselineProfileRunLineMarkerContributorTest {

  @get:Rule
  val projectRule = AndroidProjectRule.inMemory().onEdt()

  private val expectedGenerateBaselineProfileAction by lazy(LazyThreadSafetyMode.PUBLICATION) {
    ActionManager.getInstance().getAction("AndroidX.BaselineProfile.RunGenerate")
  }

  private val contributor by lazy(LazyThreadSafetyMode.PUBLICATION) {
    BaselineProfileRunLineMarkerContributor()
  }

  companion object {
    private val JAVA_SRC_FILE_HEADER = """
      package com.example.baselineprofile;

      import androidx.benchmark.macro.junit4.BaselineProfileRule;
      import androidx.test.ext.junit.runners.AndroidJUnit4;
      import org.junit.Rule;
      import org.junit.Test;
      import org.junit.rules.TemporaryFolder;
      import org.junit.runner.RunWith;

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
  fun `when Java class does NOT have BaselineProfileRule, it should NOT show contributor`() {
    val sourceFile = addJavaBaselineProfileGeneratorToProject("""
        $JAVA_SRC_FILE_HEADER
        public class BaselineProfileGenerator {
          @Test
          public void generate() { }
        }
      """.trimIndent())

    assertContributorInfoNull(sourceFile.classIdentifierNamed("BaselineProfileGenerator"))
  }

  @Test
  @RunsInEdt
  fun `when Java class has BaselineProfileRule, it should show contributor`() {
    val sourceFile = addJavaBaselineProfileGeneratorToProject("""
        $JAVA_SRC_FILE_HEADER
        public class BaselineProfileGenerator {

          @Rule
          public BaselineProfileRule rule = new BaselineProfileRule();

          @Test
          public void generate() { }

          public void notGenerate() { }
        }
      """.trimIndent())

    assertContributorInfo(sourceFile.classIdentifierNamed("BaselineProfileGenerator"))

    assertTestContributorInfo(sourceFile.methodIdentifierNamed("generate"))

    assertContributorInfoNull(sourceFile.methodIdentifierNamed("notGenerate"))
  }

  @Test
  @RunsInEdt
  fun `when Java inner class has BaselineProfileRule, outer class methods should NOT show contributor`() {
    val sourceFile = addJavaBaselineProfileGeneratorToProject("""
        $JAVA_SRC_FILE_HEADER
        public class BaselineProfileGenerator {

          public class SomeInnerClass {
            @Rule
            public BaselineProfileRule rule = new BaselineProfileRule();
          }

          @Test
          public void generate() { }
        }
      """.trimIndent())

    // Outer class
    assertContributorInfoNull(sourceFile.classIdentifierNamed("BaselineProfileGenerator"))

    // Inner class
    assertContributorInfo(sourceFile.classIdentifierNamed("SomeInnerClass"))
  }

  @Test
  @RunsInEdt
  fun `when Java outer class has BaselineProfileRule, inner class methods should not show contributor`() {
    val sourceFile = addJavaBaselineProfileGeneratorToProject("""
        $JAVA_SRC_FILE_HEADER
        public class BaselineProfileGenerator {

          @Rule
          public BaselineProfileRule rule = new BaselineProfileRule();

          public class SomeInnerClass {
            @Test
            public void generate() { }
          }
        }
      """.trimIndent())

    // Outer class
    assertContributorInfo(sourceFile.classIdentifierNamed("BaselineProfileGenerator"))

    // Inner class
    assertContributorInfoNull(sourceFile.classIdentifierNamed("SomeInnerClass"))
  }

  @Test
  @RunsInEdt
  fun `when Java class has a rule before BaselineProfileRule, it should still show contributor`() {
    val sourceFile = addJavaBaselineProfileGeneratorToProject("""
        $JAVA_SRC_FILE_HEADER
        public class BaselineProfileGenerator {

          @Rule
          public TemporaryFolder folder = new TemporaryFolder();

          @Rule
          public BaselineProfileRule rule = new BaselineProfileRule();
        }
      """.trimIndent())

    // Outer class
    assertContributorInfo(sourceFile.classIdentifierNamed("BaselineProfileGenerator"))
  }

  @Test
  @RunsInEdt
  fun `when Java class has MacroBenchmarkRule, it should show, it should not show contributor`() {
    val sourceFile = addJavaBaselineProfileGeneratorToProject("""
        package com.example.baselineprofile;

        import androidx.benchmark.macro.junit4.MacrobenchmarkRule;
        import androidx.test.ext.junit.runners.AndroidJUnit4;

        import org.junit.Rule;
        import org.junit.Test;
        import org.junit.runner.RunWith;

        @RunWith(AndroidJUnit4.class)
        public class StartupBenchmarks {

          @Rule
          MacrobenchmarkRule rule = new MacrobenchmarkRule();

          @Test
          public void foo() {}

          public void bar() {}
        }
      """.trimIndent())

    // Outer class
    assertTestContributorInfo(sourceFile.classIdentifierNamed("StartupBenchmarks"))

    // @Test
    assertTestContributorInfo(sourceFile.methodIdentifierNamed("foo"))

    // not @Test
    assertContributorInfoNull(sourceFile.methodIdentifierNamed("bar"))
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
      "Line marker contributor should contain a single BaselineProfileAction."
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
    val info = contributor.getInfo(psiElement)
    assertNull(info)
  }

  private fun addJavaBaselineProfileGeneratorToProject(content: String): JavaBaselineProfileGeneratorSourceFile =
    JavaBaselineProfileGeneratorSourceFile(
      projectRule.fixture.addFileToProject(
        "src/com/example/baselineprofile/BaselineProfileGenerator.java",
        content
      )
    )

  private class JavaBaselineProfileGeneratorSourceFile(private val psiFile: PsiFile) {
    fun classIdentifierNamed(name: String): PsiElement {
      val el = PsiTreeUtil.collectElements(psiFile) { it is PsiClass }
        .toList()
        .firstOrNull { (it as PsiClass).name == name }
      assertNotNull(el) { "No class named `$name` was found." }
      val identifier = el.collectDescendantsOfType<PsiElement> { it is PsiIdentifier }.firstOrNull { it.text == name }
      assertNotNull(identifier) { "Identifier PsiElement `$name` was not found." }
      return identifier
    }

    fun methodIdentifierNamed(name: String): PsiElement {
      val el = PsiTreeUtil.collectElements(psiFile) { it is PsiMethod }
        .toList()
        .firstOrNull { (it as PsiMethod).name == name }
      assertNotNull(el) { "No class named `$name` was found." }
      val identifier = el.collectDescendantsOfType<PsiElement> { it is PsiIdentifier }.firstOrNull {it.text == name }
      assertNotNull(identifier) { "Identifier PsiElement `$name` was not found." }
      return identifier
    }
  }
}
