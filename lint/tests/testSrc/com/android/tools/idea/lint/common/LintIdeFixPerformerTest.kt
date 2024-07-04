/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * U…nless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.lint.common

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.TOOLS_URI
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Location
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiFile
import org.jetbrains.android.JavaCodeInsightFixtureAdtTestCase

/**
 * Miscellaneous set of tests for lint quickfixes in the IDE handled by [LintIdeFixPerformer].
 *
 * There are additional related tests elsewhere:
 * * [LintIdeFixPerformerReplaceStringTest] does comprehensive testing of the string replacement
 *   quickfix (the most common and powerful one)
 * * [LintIdeFixPerformerCreateFileTest] does comprehensive testing of the create-file fixes --
 *   create new text file, binary files, and delete files
 * * [AnnotateQuickFixTest] checks the annotation-based quick fixes
 * * PreviewFixTest in the android-lint module checks that these fixes are working in preview
 *   context
 * * [LintIdeTest] has a handful of tests that involve quickfixes with IntelliJ's before and after
 *   diffs. Similarly, in the android-lint plugin, AndroidLintTest has a handful of inspection tests
 *   with fixes.
 */
class LintIdeFixPerformerTest : JavaCodeInsightFixtureAdtTestCase() {
  init {
    LintClient.clientName = LintClient.CLIENT_UNIT_TESTS
  }

  fun testCompositeString() {
    // Simulates a more complex quickfix which is updating a code snippet with
    // more individual edits; this forces us to properly handle edit ordering
    // and handling PSI element invalidation after running things like
    // reference shortening.
    val file =
      myFixture.addFileToProject(
        "src/CanvasTest.kt",
        """
        package test.pkg

        import android.graphics.Canvas
        import android.graphics.Paint

        @Suppress("unused")
        fun canvas1(canvas: Canvas, paint: Paint) {
            canvas.save()
            canvas.translate(200f, 300f)
            canvas.drawCircle(10f, 10f, 10f, paint) // drawn on the translated canvas
            canvas.restore()
        }
        """
          .trimIndent(),
      )
    val ioFile = VfsUtilCore.virtualToIoFile(file.virtualFile)
    myFixture.configureFromExistingVirtualFile(file.virtualFile)

    fun replace(
      oldText: String,
      start: Int,
      end: Int,
      replacement: String,
    ): LintFix.ReplaceStringBuilder {
      val text = file.text
      val actual = text.substring(start, end)
      assertEquals(actual, oldText)
      val range = Location.create(ioFile, text, start, end)
      return LintFix.create().replace().range(range).text(oldText).with(replacement).autoFix()
    }

    val deleteSave =
      replace("save(", 155, 160, "withTranslation(200f, 300f")
        .reformat(true)
        .imports("androidx.core.graphics.withTranslation")
        .select("with(Translation)")
    val insertRParen = replace(")", 160, 161, ") {")
    val deleteRestore = replace("canvas.restore()", 277, 293, "}")
    val deleteReceiver = replace("canvas.", 199, 206, "")
    val deleteTranslate = replace("    canvas.translate(200f, 300f)\n", 162, 195, "")
    val indent = replace("", 195, 195, "    ")

    val group =
      LintFix.create()
        .name("Replace with the withTranslation extension function")
        .composite(
          deleteSave.build(),
          insertRParen.build(),
          deleteRestore.build(),
          deleteReceiver.build(),
          deleteTranslate.build(),
          indent.build(),
        )
    val incident =
      Incident().location(Location.create(VfsUtilCore.virtualToIoFile(file.virtualFile)))
    val fixes = AndroidLintInspectionBase.createFixes(project, file, incident, group)
    assertEquals(1, fixes.size)
    val offset = file.text.indexOf(".save(")
    assertTrue(offset != -1)
    val startElement = file.findElementAt(offset)!!
    assertNotNull(startElement)

    val fix = fixes[0] as ModCommandLintQuickFix
    myFixture.checkPreviewAndLaunchAction(fix.rawIntention())

    assertEquals(
      """
      package test.pkg

      import android.graphics.Canvas
      import android.graphics.Paint
      import androidx.core.graphics.withTranslation

      @Suppress("unused")
      fun canvas1(canvas: Canvas, paint: Paint) {
          canvas.withTranslation(200f, 300f) {
              drawCircle(10f, 10f, 10f, paint) // drawn on the translated canvas
          }
      }
      """
        .trimIndent(),
      myFixture.editor.document.text,
    )
    // Make sure we've selected the right thing
    assertEquals(file.text, myFixture.editor.document.text)
    assertEquals("Translation", myFixture.editor.selectionModel.selectedText)
    assertEquals(
      file.text.indexOf("withTranslation(200") + "with".length,
      myFixture.editor.caretModel.offset,
    )
  }

  fun testAttributes() {
    val file =
      myFixture.addFileToProject(
        "res/layout/main.xml",
        // language=xml
        """
        <?xml version="1.0" encoding="utf-8"?>
        <GridLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="match_parent" android:layout_height="match_parent">
            <TextView
                android:id="@+id/textView"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_row="0"
                android:layout_column="0"
                android:text="TextView" />
        </GridLayout>
        """
          .trimIndent(),
      )
    myFixture.configureFromExistingVirtualFile(file.virtualFile)

    val group =
      LintFix.create()
        .name("Set attributes")
        .composite(
          LintFix.create().replaceAttribute(ANDROID_URI, "layout_row", "0", AUTO_URI).build(),
          LintFix.create().set(ANDROID_URI, "hint", "My hint").selectAll().build(),
          LintFix.create().set(TOOLS_URI, "ignore", "SdCardPath").selectAll().build(),
        )
    val incident = Incident().location(getLocation(file, "<[TextView]"))
    val fixes = AndroidLintInspectionBase.createFixes(project, file, incident, group)
    assertEquals(1, fixes.size)
    val offset = file.text.indexOf("<TextView")
    assertTrue(offset != -1)
    val startElement = file.findElementAt(offset)!!
    assertNotNull(startElement)

    val fix = fixes[0] as ModCommandLintQuickFix
    myFixture.checkPreviewAndLaunchAction(fix.rawIntention())

    assertEquals(
      // language=xml
      """
      <?xml version="1.0" encoding="utf-8"?>
      <GridLayout xmlns:android="http://schemas.android.com/apk/res/android"
          xmlns:app="http://schemas.android.com/apk/res-auto"
          xmlns:tools="http://schemas.android.com/tools"
          android:layout_width="match_parent"
          android:layout_height="match_parent">

          <TextView
              android:id="@+id/textView"
              android:layout_width="wrap_content"
              android:layout_height="wrap_content"
              android:layout_column="0"
              android:hint="My hint"
              android:text="TextView"
              app:layout_row="0"
              tools:ignore="SdCardPath" />
      </GridLayout>
      """
        .trimIndent(),
      myFixture.editor.document.text,
    )
    // Make sure we've selected the right thing
    assertEquals(file.text, myFixture.editor.document.text)
    assertEquals("My hint", myFixture.editor.selectionModel.selectedText)
    assertEquals(file.text.indexOf("My hint"), myFixture.editor.caretModel.offset)
  }

  fun testAnnotation() {
    val file =
      myFixture.addFileToProject(
        "src/Test.kt",
        // language=Kt
        """
        package p1.p2
        /** My Property */
        class MyTest {
            /** My test */
            fun test() {
            }
        }
        """
          .trimIndent(),
      )
    myFixture.configureFromExistingVirtualFile(file.virtualFile)

    @Suppress("DEPRECATION")
    val group =
      LintFix.create()
        .name("Fix code")
        .composite(
          LintFix.create()
            .annotate("kotlin.Suppress(\"SdCardPath\")")
            .range(getLocation(file, "[fun] test"))
            .build(),
          LintFix.create()
            .replace()
            .text("test()")
            .with("test(foo: Int)")
            .select("()foo: Int")
            .range(getLocation(file, "[fun test()] {"))
            .build(),
        )
    val incident = Incident().location(getLocation(file, "[class MyTest]"))
    val fixes = AndroidLintInspectionBase.createFixes(project, file, incident, group)
    assertEquals(1, fixes.size)
    val offset = file.text.indexOf("class MyTest")
    assertTrue(offset != -1)
    val startElement = file.findElementAt(offset)!!
    assertNotNull(startElement)

    val fix = fixes[0] as ModCommandLintQuickFix
    myFixture.checkPreviewAndLaunchAction(fix.rawIntention())

    assertEquals(
      // language=Kt
      """
      package p1.p2
      /** My Property */
      class MyTest {
          /** My test */
          @Suppress("SdCardPath")
          fun test(foo: Int) {
          }
      }
      """
        .trimIndent(),
      myFixture.editor.document.text,
    )
    // Make sure we've selected the right thing
    assertEquals(file.text, myFixture.editor.document.text)
    assertNull(myFixture.editor.selectionModel.selectedText)
    assertEquals(file.text.indexOf("foo: Int"), myFixture.editor.caretModel.offset)
  }

  private fun getLocation(file: PsiFile, window: String): Location {
    val ioFile = file.virtualFile.toNioPath().toFile()
    val start = window.indexOf("[")
    val end = window.indexOf("]", start + 1)
    if (start == -1 || end == -1) {
      fail("Couldn't find range markers [ and ] in the window")
    }
    val substring =
      window.substring(0, start) + window.substring(start + 1, end) + window.substring(end + 1)
    val index = file.text.indexOf(substring)
    assertTrue("Couldn't find $substring in $ioFile", index != -1)
    return Location.create(ioFile, file.text, index + start, index + end)
  }
}
