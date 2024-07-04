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
package com.android.tools.idea.lint.common

import com.android.tools.idea.testing.moveCaret
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.detector.api.DefaultPosition
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Location
import com.android.tools.tests.AdtTestProjectDescriptors
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandAction
import com.intellij.modcommand.Presentation
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.android.JavaCodeInsightFixtureAdtTestCase

class AnnotateQuickFixTest : JavaCodeInsightFixtureAdtTestCase() {
  init {
    LintClient.clientName = LintClient.CLIENT_UNIT_TESTS
  }

  override fun getProjectDescriptor() = AdtTestProjectDescriptors.kotlin()

  fun testKotlinAnnotationArgs() {
    check(
      fileName = "src/p1/p2/AnnotateTest.kt",
      // language=kotlin
      originalSource =
        """
        package p1.p2

        const val someProperty = ""
        """,
      selected = "someProperty",
      name = "Add @Suppress(SomeInspection)",
      annotationSource = "@kotlin.Suppress(\"SomeInspection\")",
      replace = true,
      // language=kotlin
      expected =
        """
        package p1.p2

        @Suppress("SomeInspection")
        const val someProperty = ""
        """,
    )
  }

  fun testKotlinClass() {
    check(
      fileName = "src/p1/p2/AnnotateTest.kt",
      // language=kotlin
      originalSource =
        """
        /** Class */
        class AnnotateTest
        """,
      selected = "class",
      name = "Add @Suppress(SomeInspection)",
      annotationSource = "@kotlin.Suppress(\"SomeInspection\")",
      replace = false,
      // language=kotlin
      expected =
        """
        /** Class */
        @Suppress("SomeInspection")
        class AnnotateTest
        """,
    )
  }

  fun testJavaClass() {
    check(
      fileName = "src/p1/p2/AnnotateTest.java",
      // language=java
      originalSource =
        """
        package p1.p2;

        class AnnotateTest {
        }
        """,
      selected = "class AnnotateTest",
      name = "Add @SuppressWarnings",
      annotationSource = "@java.lang.SuppressWarnings(\"SomeIssueId\")",
      replace = true,
      // language=java
      expected =
        """
        package p1.p2;

        @SuppressWarnings("SomeIssueId")
        class AnnotateTest {
        }
        """,
    )
  }

  fun testJavaClassReplace() {
    check(
      fileName = "src/p1/p2/AnnotateTest.java",
      // language=java
      originalSource =
        """
        package p1.p2;

        @SuppressWarnings("SomeIssueId1")
        class AnnotateTest {
        }
        """,
      selected = "class AnnotateTest",
      name = "Add @SuppressWarnings",
      annotationSource = "@java.lang.SuppressWarnings(\"SomeIssueId2\")",
      replace = true,
      // language=java
      expected =
        """
        package p1.p2;

        @SuppressWarnings("SomeIssueId2")
        class AnnotateTest {
        }
        """,
    )
  }

  fun testKotlinUseSiteTarget() {
    check(
      fileName = "src/p1/p2/AnnotateTest.kt",
      // language=kotlin
      originalSource =
        """
        package p1.p2

        const val someProperty = ""
        """,
      selected = "someProperty",
      name = "Add @get:JvmSynthetic",
      annotationSource = "@get:JvmSynthetic",
      replace = true,
      // language=kotlin
      expected =
        """
        package p1.p2

        @get:JvmSynthetic
        const val someProperty = ""
        """,
    )
  }

  fun testKotlinMultipleAnnotations() {
    check(
      fileName = "src/p1/p2/AnnotateTest.kt",
      // language=kotlin
      originalSource =
        """
        package p1.p2

        const val someProperty = ""
        """,
      selected = "someProperty",
      annotations =
        listOf(
          Triple("Add @Suppress(SomeInspection1)", "@kotlin.Suppress(\"SomeInspection1\")", false),
          Triple("Add @Suppress(SomeInspection2)", "@kotlin.Suppress(\"SomeInspection2\")", false),
        ),
      // language=kotlin
      expected =
        """
        package p1.p2

        @Suppress("SomeInspection2")
        @Suppress("SomeInspection1")
        const val someProperty = ""
        """,
    )
  }

  fun testKotlinReplaceAnnotation() {
    check(
      fileName = "src/p1/p2/AnnotateTest.kt",
      // language=kotlin
      originalSource =
        """
        package p1.p2

        @Suppress("SomeInspection1")
        const val someProperty = ""
        """,
      selected = "someProperty",
      name = "Add @Suppress(SomeInspection2)",
      annotationSource = "@kotlin.Suppress(\"SomeInspection2\")",
      replace = true,
      // language=kotlin
      expected =
        """
        package p1.p2

        @Suppress("SomeInspection2")
        const val someProperty = ""
        """,
    )
  }

  fun testAnnotateDifferentFile() {
    // Make sure that the annotate quickfix range works properly (such that
    // an inspection result can annotate a result in a different file or part
    // of the current file than the error location)

    var unrelatedFile: PsiFile? = null

    check(
      fileName = "src/p1/p2/AnnotateTest.kt",
      // language=kotlin
      originalSource =
        """
        package p1.p2
        class Test {
          fun test() {
          }
        }
        """,
      extraSetup = {
        unrelatedFile =
          myFixture.configureByText(
            "src/p1/p2/AnnotateTest.kt",
            // language="Java
            """
            package p1.p2
            class Unrelated {
                fun method() {
                }
            }
           """
              .trimIndent(),
          )
      },
      selected = "test",
      name = "Add @Suppress(SomeInspection2)",
      annotationSource = "@kotlin.Suppress(\"SomeInspection2\")",
      replace = false,
      // language=kotlin
      // Make sure we *don't* insert the annotation here
      expected =
        """
        package p1.p2
        class Test {
          fun test() {
          }
        }
        """,
      rangeFactory = { annotationSource ->
        val targetFile = unrelatedFile!!
        assertEquals("@kotlin.Suppress(\"SomeInspection2\")", annotationSource)
        val text = targetFile.text
        val offset = text.indexOf("fun method")
        Location.create(
          targetFile.virtualFile.toNioPath().toFile(),
          DefaultPosition(-1, -1, offset),
          DefaultPosition(-1, -1, text.indexOf('\n', offset)),
        )
      },
      extraVerify = {
        assertEquals(
          // language="Java
          """
          package p1.p2
          class Unrelated {
              @Suppress("SomeInspection2")
              fun method() {
              }
          }
          """
            .trimIndent(),
          unrelatedFile?.text,
        )
      },
    )
  }

  fun testTomlSuppressFix() {
    val file =
      myFixture.configureByText(
        "src/test.toml",
        // language=TOML
        """
        key1="value1"
        key<caret>2="value2"
        #noinspection MyId2
        key3="value3"
        """
          .trimIndent(),
      )

    val element = myFixture.elementAtCaret
    val intention = SuppressLintIntentionAction("MyId1", element).asIntention()
    myFixture.checkPreviewAndLaunchAction(intention)

    assertEquals(
      // language=TOML
      """
      key1="value1"
      #noinspection MyId1
      key2="value2"
      #noinspection MyId2
      key3="value3"
      """
        .trimIndent(),
      file.text,
    )
  }

  // Test infrastructure below

  private fun PsiElement.getIncident(): Incident {
    val file = containingFile!!
    val location =
      Location.create(
        VfsUtilCore.virtualToIoFile(file.virtualFile),
        DefaultPosition(-1, -1, startOffset),
        DefaultPosition(-1, -1, endOffset),
      )
    return Incident().location(location)
  }

  private fun createMultipleAnnotationFixes(
    element: PsiElement,
    annotations: List<Triple<String, String, Boolean>>,
    rangeFactory: ((String) -> Location?)?,
    useLintFix: Boolean,
  ): Array<LintIdeQuickFix> {
    if (useLintFix) {
      val fixes = mutableListOf<LintFix>()
      for ((name, annotationSource, replace) in annotations) {
        fixes.add(
          LintFix.create()
            .name(name)
            .annotate(annotationSource, null, null, replace)
            .apply {
              val range = rangeFactory?.invoke(annotationSource)
              if (range != null) {
                range(range)
              }
            }
            .build()
        )
      }
      val composite: LintFix = LintFix.create().name("Add multiple annotations").composite(fixes)
      return LintIdeFixPerformer.createIdeFixes(
        project,
        element.containingFile,
        element.getIncident(),
        composite,
        true,
      )
    } else {
      val fixes = mutableListOf<AnnotateQuickFix>()
      for ((name, annotationSource, replace) in annotations) {
        val range = rangeFactory?.invoke(annotationSource)
        fixes.add(AnnotateQuickFix(project, name, null, annotationSource, replace, range))
      }

      class CompositeLintFix(
        private val displayName: String,
        private val familyName: String,
        private val myFixes: Array<AnnotateQuickFix>,
      ) : ModCommandAction {

        override fun getPresentation(context: ActionContext): Presentation? =
          if (myFixes.any { it.getPresentation(context) == null }) null
          else Presentation.of(displayName)

        @Suppress("UnstableApiUsage")
        override fun perform(context: ActionContext) =
          // This illustrates composition for our quick fixes. Because they depend on the order in
          // which they are applied (e.g. one quick fix could add an annotation, and another would
          // add or replace that annotation depending on context), we cannot directly compose the
          // corresponding ModCommands via ModCompositeCommand or .andThen() chaining. We first
          // collect all the elements to be updated (important because some of the quick fixes are
          // non-local, they edit different files), pass them through getWritable() to make copies,
          // and then apply each quick fix in sequence, within a single ModCommand.psiUpdate() call.
          ModCommand.psiUpdate(context) { updater ->
            val targets = myFixes.map { updater.getWritable(it.findPsiTarget(context)) }
            myFixes.zip(targets).map { (fix, target) -> fix.applyFixFun(target!!) }
          }

        override fun getFamilyName(): @IntentionFamilyName String = familyName
      }

      return arrayOf(ModCommandLintQuickFix(CompositeLintFix(name, "Fix", fixes.toTypedArray())))
    }
  }

  private fun check(
    fileName: String,
    originalSource: String,
    selected: String,
    name: String,
    annotationSource: String,
    replace: Boolean,
    expected: String,
    extraSetup: (() -> Unit)? = null,
    rangeFactory: ((String) -> Location?)? = null,
    extraVerify: (() -> Unit)? = null,
  ) {
    check(
      fileName,
      originalSource,
      selected,
      listOf(Triple(name, annotationSource, replace)),
      extraSetup,
      rangeFactory,
      extraVerify,
      expected,
    )
  }

  private fun check(
    fileName: String,
    originalSource: String,
    selected: String,
    annotations: List<Triple<String, String, Boolean>>,
    extraSetup: (() -> Unit)? = null,
    rangeFactory: ((String) -> Location?)? = null,
    extraVerify: (() -> Unit)? = null,
    expected: String,
  ) {
    // Test with PSI-based annotation insertion (AnnotateQuickFix)
    check(
      fileName,
      originalSource,
      selected,
      expected,
      annotations,
      extraSetup,
      rangeFactory,
      extraVerify,
      false,
    )
    // Test with lint-based annotation insertion (LintIdeFixPerformer)
    check(
      fileName,
      originalSource,
      selected,
      expected,
      annotations,
      extraSetup,
      rangeFactory,
      extraVerify,
      true,
    )
  }

  private fun check(
    fileName: String,
    originalSource: String,
    selected: String,
    expected: String,
    annotations: List<Triple<String, String, Boolean>>,
    extraSetup: (() -> Unit)? = null,
    rangeFactory: ((String) -> Location?)?,
    extraVerify: (() -> Unit)?,
    useLintFix: Boolean,
  ) {
    extraSetup?.invoke()

    val file = myFixture.configureByText(fileName, originalSource.trimIndent())

    // Modify document slightly to make sure we're not holding on to stale offsets
    WriteCommandAction.runWriteCommandAction(project) {
      val documentManager = PsiDocumentManager.getInstance(project)
      val document = myFixture.editor.document
      documentManager.doPostponedOperationsAndUnblockDocument(document)
      document.insertString(0, "/*prefix*/")
      documentManager.commitDocument(document)
    }

    val element = myFixture.findElementByText(selected, PsiElement::class.java)
    val fixes = createMultipleAnnotationFixes(element, annotations, rangeFactory, useLintFix)

    myFixture.moveCaret("|$selected")

    for (fix in fixes) {
      myFixture.launchAction((fix as ModCommandLintQuickFix).rawIntention())
    }

    assertEquals(expected.trimIndent(), file.text.removePrefix("/*prefix*/"))
    extraVerify?.invoke()
  }
}
