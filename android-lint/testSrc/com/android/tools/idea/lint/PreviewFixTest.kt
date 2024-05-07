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
package com.android.tools.idea.lint

import com.android.testutils.TestUtils
import com.android.tools.idea.lint.common.AnnotateQuickFix
import com.android.tools.idea.lint.common.DefaultLintQuickFix
import com.android.tools.idea.lint.common.LintExternalAnnotator
import com.android.tools.idea.lint.common.ModCommandLintQuickFix
import com.android.tools.idea.lint.common.SuppressLintIntentionAction
import com.android.tools.idea.lint.common.toIdeFix
import com.android.tools.idea.lint.intentions.AndroidAddStringResourceQuickFix
import com.android.tools.idea.lint.quickFixes.AddTargetVersionCheckQuickFix
import com.android.tools.idea.lint.quickFixes.ConvertToDpQuickFix
import com.android.tools.lint.checks.ApiDetector
import com.android.tools.lint.checks.DuplicateResourceDetector
import com.android.tools.lint.detector.api.ApiConstraint
import com.android.tools.lint.detector.api.DefaultPosition
import com.android.tools.lint.detector.api.ExtensionSdk
import com.android.tools.lint.detector.api.LintFix.Companion.create
import com.android.tools.lint.detector.api.Location
import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorSettings
import com.intellij.openapi.editor.impl.ImaginaryEditor
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import java.io.File
import org.jetbrains.android.intentions.AndroidExtractColorAction
import org.jetbrains.android.intentions.AndroidExtractDimensionAction

class PreviewFixTest : AbstractAndroidLintTest() {

  fun testIntentionPreviewAddTargetVersion() {
    // Test that the intention preview for AddTargetVersionCheckQuickFix works as expected; in
    // particular,
    // it makes edits to the preview non-physical file, and does not modify the physical file.
    val file =
      myFixture.configureByText(
        "X.java", /*language=JAVA */
        """
      package com.example;
      import java.io.FileReader;
      import java.io.IOException;
      import java.util.Properties;
      public class X {
        public static void foo() throws IOException {
          FileReader reader = new FileReader("../local.properties");
          Properties props = new Properties();
          props.load(reader);
          reader.close();
        }
      }
      """
          .trimIndent(),
      )

    checkPreviewFix(
      file,
      "^props.load",
      {
        val fix =
          AddTargetVersionCheckQuickFix(project, 9, ExtensionSdk.ANDROID_SDK_ID, ApiConstraint.ALL)
        assertThat(fix.name)
          .isEqualTo("Surround with if (VERSION.SDK_INT >= VERSION_CODES.GINGERBREAD) { ... }")
        fix
      },
      """
      @@ -9 +9
      -     props.load(reader);
      +     if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.GINGERBREAD) {
      + props.load(reader);
      +     }
      """,
    )
  }

  fun testIntentionPreviewExtractString() {
    // Test that the intention preview for AndroidAddStringResourceQuickFix works as expected
    val file =
      myFixture.configureByText(
        "layout.xml", /*language=XML */
        """
      <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android">
          <Button android:text="Hello World"/>
      </LinearLayout>
      """
          .trimIndent(),
      )
    checkPreviewAction(
      file,
      "Hello ^World",
      { AndroidAddStringResourceQuickFix(it) },
      """
      @@ -2 +2
      -     <Button android:text="Hello World"/>
      +     <Button android:text="@string/hello_world"/>
      """,
    )
  }

  fun testIntentionPreviewExtractDimension() {
    // Test that the intention preview for AndroidExtractDimensionAction works as expected
    val file =
      myFixture.configureByText(
        "layout.xml", /*language=XML */
        """
        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android">
            <Button android:textSize="50px"  />
        </LinearLayout>
        """
          .trimIndent(),
      )
    checkPreviewAction(
      file,
      "50^px",
      { AndroidExtractDimensionAction() },
      """
      @@ -2 +2
      -     <Button android:textSize="50px"  />
      +     <Button android:textSize="@dimen/dimen_name"  />
      """,
    )
  }

  fun testIntentionPreviewConvertToDp() {
    // Test that the intention preview for ConvertToDpQuickFix works as expected
    val file =
      myFixture.configureByText(
        "layout.xml", /*language=XML */
        """
        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android">
            <Button android:textSize="50px"  />
        </LinearLayout>
        """
          .trimIndent(),
      )
    checkPreviewFix(
      file,
      "50^px",
      { ConvertToDpQuickFix() },
      """
      @@ -2 +2
      -     <Button android:textSize="50px"  />
      +     <Button android:textSize="50dp"  />
      """,
    )
  }

  fun testIntentionPreviewExtractColor() {
    // Test that the intention preview for AndroidExtractColorAction works as expected
    val file =
      myFixture.configureByText(
        "states.xml", /*language=XML */
        """
        <selector xmlns:android="http://schemas.android.com/apk/res/android">
            <item android:state_pressed="true"
                  android:color="#ffff0000"/> <!-- pressed -->
            <item android:color="#ff000000"/>
        </selector>
        """
          .trimIndent(),
      )
    checkPreviewAction(
      file,
      "#ff^ff",
      { AndroidExtractColorAction() },
      """
      @@ -3 +3
      -           android:color="#ffff0000"/> <!-- pressed -->
      +           android:color="@color/color_name"/> <!-- pressed -->
      """,
    )
  }

  fun testReplaceStringPreview() {
    // Test that the intention preview for ReplaceStringQuickfix works as expected
    val file =
      myFixture.configureByText(
        "foo.kt", /*language=KT */
        """
        fun test() {
          val f<caret>oo = "bar"
        }
        """
          .trimIndent(),
      )

    val fix =
      create().replace().pattern("(foo)").with("foo: Foo").build().toIdeFix(file)
        as ModCommandLintQuickFix
    myFixture.checkPreviewAndLaunchAction(fix.rawIntention())

    myFixture.checkResult(
      /*language=KT */
      """
      fun test() {
        val foo: Foo = "bar"
      }
      """
        .trimIndent()
    )
  }

  fun testAnnotatePreviewJava() {
    // Test that the intention preview for annotate works as expected
    val file =
      myFixture.configureByText(
        "Foo.java", /*language=Java */
        """
        class Foo {
          public void t<caret>est() {
          }
        }
        """
          .trimIndent(),
      )

    @Suppress("DEPRECATION")
    val fix =
      create()
        .annotate("@java.lang.SuppressWarnings(\"something\")", false)
        .range(getLocationAtCaret())
        .build()
        .toIdeFix(file)

    myFixture.checkPreviewAndLaunchAction((fix as ModCommandLintQuickFix).rawIntention())
    myFixture.checkResult(
      /* language=Java */
      """
      class Foo {
        @java.lang.SuppressWarnings("something")
        public void test() {
        }
      }
    """
        .trimIndent()
    )
  }

  fun testAnnotatePreviewJavaPsi() {
    // Test that the intention preview for annotate works as expected (using PSI implementation)
    val file =
      myFixture.configureByText(
        "Foo.java", /*language=Java */
        """
        class Foo {
          public void t<caret>est() {
          }
        }
        """
          .trimIndent(),
      )
    val annotateFix =
      AnnotateQuickFix(
        file.project,
        "annotate",
        null,
        "@java.lang.SuppressWarnings(\"something\")",
        false,
        getLocationAtCaret(),
      )
    myFixture.checkPreviewAndLaunchAction(annotateFix.asIntention())
    myFixture.checkResult(
      /* language=Java */
      """
      class Foo {
        @java.lang.SuppressWarnings("something")
        public void test() {
        }
      }
    """
        .trimIndent()
    )
  }

  fun testAnnotatePreviewKotlinPsi() {
    // Test that the intention preview for annotate works as expected (using PSI implementation)
    val file =
      myFixture.configureByText(
        "Foo.kt", /* language=kotlin */
        """
        const val <caret>someProperty = ""
        """
          .trimIndent(),
      )
    val annotateFix =
      AnnotateQuickFix(
        file.project,
        "annotate",
        null,
        "@kotlin.Suppress(\"SomeInspection\")",
        false,
        getLocationAtCaret(),
      )
    myFixture.checkPreviewAndLaunchAction(annotateFix.asIntention())
    myFixture.checkResult(
      /* language=kotlin */
      """
       @Suppress("SomeInspection")
       const val someProperty = ""
    """
        .trimIndent()
    )
  }

  fun testReplaceExplicitRangePreview() {
    // Regression test for b/271575376.
    val file =
      myFixture.configureByText(
        "foo.kt", /*language=KT */
        """
        fun test() {
          val f<caret>oo = "bar"
        }
        """
          .trimIndent(),
      )

    val fix =
      create()
        .replace()
        .range(getLocationAtCaret())
        .pattern("foo")
        .with("foo:   Foo  ")
        .reformat(true)
        .build()
        .toIdeFix(file) as ModCommandLintQuickFix

    myFixture.checkPreviewAndLaunchAction(fix.rawIntention())
    myFixture.checkResult(
      /*language=KT */
      """
      fun test() {
        val foo: Foo = "bar"
      }
      """
        .trimIndent()
    )
  }

  fun testNavigatePreview() {
    // Test preview for navigation action, which will be rendered in HTML
    val file =
      myFixture.configureByText(
        "foo.kt", /*language=KT */
        """
      fun test() {
        val foo = "bar"
      }
      """
          .trimIndent(),
      )
    val otherFile = myFixture.createFile("other.xml", "<Resources/>")

    val fix =
      create().newFile(File(otherFile.path), "<Resources/>").build().toIdeFix(file)
        as ModCommandLintQuickFix

    myFixture.checkIntentionPreviewHtml(
      fix.rawIntention(),
      """
      <p>&rarr; <icon src="icon"/>&nbsp;other.xml, line #1</p>
      """
        .trimIndent(),
    )
  }

  fun testPreviewComposite() {
    // Test that a composite fix which targets multiple files is treated properly
    val file =
      myFixture.configureByText(
        "foo.kt", /*language=KT */
        """
        fun test() {
          val f<caret>oo = "bar"
        }
        """
          .trimIndent(),
      )
    val otherFile = myFixture.createFile("other.xml", "<Resources/>")

    val fix =
      create()
        .composite(
          create()
            .replace()
            .range(Location.create(File(otherFile.path)))
            .text("Resources")
            .with("resources")
            .build(),
          create().replace().pattern("(foo)").with("foo: Foo").build(),
        )
        .toIdeFix(file) as ModCommandLintQuickFix

    val preview = myFixture.getIntentionPreviewText(fix.rawIntention())
    assertEquals(
      """
      fun test() {
        val foo: Foo = "bar"
      }
      ----------
      <resources/>
      """
        .trimIndent(),
      preview,
    )

    myFixture.launchAction(fix.rawIntention())
    myFixture.checkResult(
      /*language=KT */
      """
      fun test() {
        val foo: Foo = "bar"
      }
      """
        .trimIndent()
    )
  }

  fun testPreviewIgnoreUnrelated1() {
    // Test a fix which is targeting a different file than the one we're looking at
    val file =
      myFixture.configureByText(
        "foo.kt", /*language=KT */
        """
        fun test() {
          val f<caret>oo = "bar"
        }
        """
          .trimIndent(),
      )
    val otherFile = myFixture.createFile("other.xml", "<Resources/>")

    val fix =
      create()
        .replace()
        .range(Location.create(File(otherFile.path)))
        .text("Resources")
        .with("resources")
        .build()
        .toIdeFix(file) as ModCommandLintQuickFix

    val preview = myFixture.getIntentionPreviewText(fix.rawIntention())
    assertEquals("<resources/>", preview)

    myFixture.launchAction(fix.rawIntention())
    myFixture.checkResult(
      /*language=KT */
      """
      fun test() {
        val foo = "bar"
      }
      """
        .trimIndent()
    )
  }

  fun testPreviewIgnoreUnrelated2() {
    // Like testPreviewIgnoreUnrelated1, but tests a different fix -- creating a new file, instead
    // of editing an existing unrelated file
    // (since there are different quickfixes involved.)
    val file =
      myFixture.configureByText(
        "foo.kt", /*language=KT */
        """
        fun test() {
          val f<caret>oo = "bar"
        }
        """
          .trimIndent(),
      )
    val otherFile = myFixture.createFile("other.xml", "<Resources/>")

    val fix =
      create().newFile(File(otherFile.path), "<new>").build().toIdeFix(file)
        as ModCommandLintQuickFix

    val preview = myFixture.getIntentionPreviewText(fix.rawIntention())
    assertEquals("<new>", preview)

    myFixture.launchAction(fix.rawIntention())
    myFixture.checkResult(
      /*language=KT */
      """
      fun test() {
        val foo = "bar"
      }
      """
        .trimIndent()
    )
  }

  fun testIntentionPreviewSuppressQuickFix() {
    // Test that the intention preview for SuppressQuickFix works exactly as the quickfix
    myFixture.configureByText(
      "strings.xml", /* language=XML */
      """
      <resources>
          <string name="test">Test</string>
          <string nam<caret>e="test">Duplicate</string>
      </resources>
      """
        .trimIndent(),
    )

    val intention =
      SuppressLintIntentionAction(DuplicateResourceDetector.ISSUE, myFixture.elementAtCaret)
        .asIntention()

    myFixture.checkPreviewAndLaunchAction(intention)
    myFixture.checkResult(
      /* language=XML */
      """
      <resources xmlns:tools="http://schemas.android.com/tools">
          <string name="test">Test</string>
          <string name="test" tools:ignore="DuplicateDefinition">Duplicate</string>
      </resources>
      """
        .trimIndent()
    )
  }

  fun testIntentionPreviewXMLFormatting() {
    val layout =
      myFixture.addFileToProject(
        "res/layout/layout.xml", /* language=XML */
        """
        <Grid<caret>Layout
            xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:columnCount="2"/>
        """
          .trimIndent(),
      )
    myFixture.configureFromExistingVirtualFile(layout.virtualFile)

    val intention =
      SuppressLintIntentionAction(ApiDetector.UNSUPPORTED, myFixture.elementAtCaret).asIntention()

    myFixture.checkPreviewAndLaunchAction(intention)
    myFixture.checkResult(
      /* language=XML */
      """
      <GridLayout
          xmlns:android="http://schemas.android.com/apk/res/android"
          xmlns:tools="http://schemas.android.com/tools"
          android:layout_width="match_parent"
          android:layout_height="match_parent"
          android:columnCount="2"
          tools:ignore="NewApi" />
      """
        .trimIndent()
    )
  }

  // --- Test fixtures below ---

  private fun getLocationAtCaret(): Location {
    val element = myFixture.elementAtCaret
    return Location.create(
      VfsUtilCore.virtualToIoFile(myFixture.file.virtualFile),
      DefaultPosition(-1, -1, element.textRange.startOffset),
      DefaultPosition(-1, -1, element.textRange.endOffset),
    )
  }

  private fun findElement(file: PsiFile, caret: String): PsiElement {
    val offset = getCaretOffset(file.text, caret)
    myFixture.editor.caretModel.moveToOffset(offset)
    return file.findElementAt(offset) ?: error("No element at offset $offset")
  }

  private fun getCaretOffset(fileContent: String, caretLocation: String): Int {
    assertTrue(caretLocation, caretLocation.contains("^"))
    val caretDelta = caretLocation.indexOf('^')
    assertTrue(caretLocation, caretDelta != -1)

    // String around caret/range without the range and caret marker characters
    val caretContext: String =
      caretLocation.substring(0, caretDelta) + caretLocation.substring(caretDelta + 1)
    val caretContextIndex = fileContent.indexOf(caretContext)
    assertTrue("Caret content $caretContext not found in file", caretContextIndex != -1)
    return caretContextIndex + caretDelta
  }

  private fun checkPreviewFix(
    file: PsiFile,
    caret: String,
    createFix: (element: PsiElement) -> DefaultLintQuickFix,
    expected: String,
  ) {
    val element = findElement(file, caret)
    val fix = createFix(element)
    val action = LintExternalAnnotator.MyFixingIntention(fix, project, file, element.textRange)
    checkPreview(expected, action, file)
  }

  private fun checkPreviewAction(
    file: PsiFile,
    caret: String,
    createAction: (element: PsiElement) -> IntentionAction,
    expected: String,
  ) {
    val element = findElement(file, caret)
    val action = createAction(element)
    checkPreview(expected, action, file)
  }

  @Suppress("UnstableApiUsage")
  private fun checkPreview(expected: String, intentionAction: IntentionAction, file: PsiFile) {
    // Test preview
    // Like file.copy() as PsiFile, but also adds user data such that
    // IntentionPreviewUtils.isPreviewElement
    // will return true.
    val psiFileCopy = IntentionPreviewUtils.obtainCopyForPreview(file)
    val originalEditor: Editor = myFixture.editor
    // Inspired by (internal) com.intellij.codeInsight.intention.impl.preview.IntentionPreviewEditor
    val editorCopy: ImaginaryEditor =
      object : ImaginaryEditor(project, psiFileCopy.viewProvider.document) {
        override fun getSettings(): EditorSettings {
          return originalEditor.settings
        }
      }
    editorCopy.caretModel.moveToOffset(originalEditor.caretModel.offset)
    assertFalse(psiFileCopy.isPhysical)

    IntentionPreviewUtils.previewSession(editorCopy) {
      val preview = intentionAction.generatePreview(project, editorCopy, psiFileCopy)
      val documentManager = PsiDocumentManager.getInstance(project)
      documentManager.commitDocument(editorCopy.document)
      if (expected.isEmpty()) {
        assertEquals(IntentionPreviewInfo.EMPTY, preview)
      } else {
        assertEquals(IntentionPreviewInfo.DIFF, preview)
      }
    }
    assertEquals(
      expected.trimIndent().trim(),
      TestUtils.getDiff(file.text, psiFileCopy.text).trim(),
    )
  }
}
