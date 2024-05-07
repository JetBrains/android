/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.lint.common

import com.android.tools.lint.checks.BuiltinIssueRegistry
import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix.Companion.create
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiMethod
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.testFramework.replaceService
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression

// Integration test for Lint quickfixes that exercises the highlighting machinery and doesn't once
// reference or cast to ModCommandAnything.
class InspectionWithQuickFixesTest : JavaCodeInsightFixtureTestCase() {

  fun testSortingPriority() {
    val source =
      myFixture.addFileToProject(
        "src/Test.java", /*language=Java */
        """
        class <caret>Test {
          private static void fix() { }

          public static void main(String[] args) {
            fix();
          }
        }
        """
          .trimIndent(),
      )
    myFixture.configureFromExistingVirtualFile(source.virtualFile)
    ApplicationManager.getApplication()
      .replaceService(
        LintIdeSupport::class.java,
        TestInspectionLintIdeSupport(),
        testRootDisposable,
      )
    myFixture.enableInspections(TestInspection())

    // We should have a dozen intentions available, including creating subclass, making the class
    // public, generating a test, etc. Note that when properly registered, our fixes will have the
    // default intentions attached by IntentionManagerImpl.getStandardIntentionOptions, and a
    // little kebab menu will show up with these options (navigating to the inspection settings,
    // running the inspection on some other scope, etc.
    // The important part is that the top intention is our first quick fix.
    val intentions = myFixture.availableIntentions
    assertTrue(intentions.size > 12)
    assertEquals("First Fix", intentions[0].text)
    assertEquals("Edit inspection profile setting", intentions[1].text)
    assertNotNull(intentions.find { it.text == "Create subclass" })

    val quickFixes = myFixture.getAllQuickFixes()
    assertEquals("First Fix", quickFixes[0].text)
    assertEquals("Second Fix", quickFixes[1].text)
    assertEquals("Third Fix", quickFixes[2].text)
    assertEquals("Suppress TestInspection with an annotation", quickFixes[3].text)

    myFixture.checkPreviewAndLaunchAction(intentions[0])
    myFixture.checkResult(
      /*language=Java */
      """
      class Test {
        private static void b0rk() { }

        public static void main(String[] args) {
          fix();
        }
      }
      """
        .trimIndent()
    )
  }
}

class TestDetector : Detector(), SourceCodeScanner {
  override fun getApplicableMethodNames(): List<String> = listOf("fix")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val location = context.getLocation(node.sourcePsi?.containingFile)

    val fixes =
      listOf("First Fix", "Second Fix", "Third Fix").map {
        create().name(it).replace().range(location).text("fix").with("b0rk").build()
      }
    val fix = create().alternatives(*fixes.toTypedArray())

    context.report(ISSUE, method, location, "N/A", fix)
  }

  companion object {
    @JvmField
    val ISSUE =
      Issue.create(
        id = "TestInspection",
        briefDescription = "N/A",
        explanation = "N/A",
        implementation = Implementation(TestDetector::class.java, EnumSet.of(Scope.JAVA_FILE)),
      )
  }
}

class TestInspection : AndroidLintInspectionBase("Test Inspection", TestDetector.ISSUE)

class TestInspectionLintIdeSupport : LintIdeSupport() {
  override fun getIssueRegistry(): IssueRegistry =
    object : BuiltinIssueRegistry() {
      override val issues: List<Issue>
        get() {
          reset()
          return listOf(TestDetector.ISSUE)
        }
    }
}
