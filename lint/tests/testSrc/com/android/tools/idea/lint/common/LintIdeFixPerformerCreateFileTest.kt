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

import com.android.testutils.TestUtils
import com.android.tools.lint.detector.api.LintFix
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiFile
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.util.ThrowableRunnable
import java.io.File
import java.util.Base64

// Test creating new text files, new binary files, and deleting files. Note that the intention
// preview supports either multiple file diffs or navigation (rendered in HTML format), but not
// both; because of this, we can't use the simple checkPreviewAndLaunchAction().
class LintIdeFixPerformerCreateFileTest : JavaCodeInsightFixtureTestCase() {
  override fun tuneFixture(builder: JavaModuleFixtureBuilder<*>) {
    builder.addJdk(TestUtils.getMockJdk().toString())
  }

  private fun createCurrentFile(): PsiFile {
    val keepFile =
      myFixture.addFileToProject(
        "/src/android/support/annotation/Keep.java",
        """
        package android.support.annotation;
        public @interface CheckResult {
        }
        """
          .trimIndent(),
      )
    myFixture.configureFromExistingVirtualFile(keepFile.virtualFile)
    return keepFile
  }

  fun testTextNoFormatting() {
    val currentFile = createCurrentFile()
    val newFile = File(VfsUtilCore.virtualToIoFile(currentFile.virtualFile).parentFile, "MyFile.kt")

    val source =
      """
      class MyFile {
          fun test() {
                // your code here
            }
        }
      """
        .trimIndent()
    val lintFix =
      LintFix.create()
        .name("Create ${newFile.name}")
        .newFile(newFile, source)
        .select("// ()your code here")
        .build()

    val fix = lintFix.toIdeFix(currentFile) as ModCommandLintQuickFix
    myFixture.launchAction(fix.rawIntention())

    val editor = FileEditorManager.getInstance(project).selectedTextEditor
    assertNotNull(editor)
    // Ensure contents has been created and is identical to the (not formatted) intended text
    assertEquals(source, editor?.document?.text)
    assertNull(editor?.selectionModel?.selectedText)
    assertEquals(source.indexOf("your"), editor?.caretModel?.offset)
  }

  fun testCreateTextWithFormattingAndSelection() {
    val currentFile = createCurrentFile()
    val newFile = File(VfsUtilCore.virtualToIoFile(currentFile.virtualFile).parentFile, "MyFile.kt")

    val source =
      """
      class MyFile {
          fun test() {
                // your code here
            }
        }
      """
        .trimIndent()
    val lintFix =
      LintFix.create()
        .name("Create ${newFile.name}")
        .newFile(newFile, source)
        .reformat(true)
        .select("// (your code here)")
        .build()

    val fix = lintFix.toIdeFix(currentFile) as ModCommandLintQuickFix
    myFixture.launchAction(fix.rawIntention())

    val editor = FileEditorManager.getInstance(project).selectedTextEditor
    assertEquals(
      // Notice how this code has been formatted
      """
      class MyFile {
          fun test() {
              // your code here
          }
      }
      """
        .trimIndent(),
      editor?.document?.text,
    )
    assertEquals("your code here", editor?.selectionModel?.selectedText)
  }

  fun testCreateBinary() {
    // Create a binary file
    val currentFile = createCurrentFile()
    val newFile = File(VfsUtilCore.virtualToIoFile(currentFile.virtualFile).parentFile, "new.bin")

    val binary = byteArrayOf(0, 1, 2, 3, 4)
    val lintFix = LintFix.create().name("Create ${newFile.name}").newFile(newFile, binary).build()

    val fix = lintFix.toIdeFix(currentFile) as ModCommandLintQuickFix
    myFixture.launchAction(fix.rawIntention())

    val virtualBinFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(newFile)
    val contents = virtualBinFile?.contentsToByteArray()
    assertEquals(
      Base64.getEncoder().encodeToString(binary),
      Base64.getEncoder().encodeToString(contents),
    )
  }

  fun testDeletion() {
    // Create a binary file
    val currentFile = createCurrentFile()
    val folder = currentFile.parent!!

    WriteCommandAction.writeCommandAction(myFixture.project)
      .run(
        ThrowableRunnable {
          folder.virtualFile.createChildData(this, "delete.txt").apply {
            setBinaryContent("contents".toByteArray())
          }
        }
      )

    val deleteVirtualFile = folder.findFile("delete.txt")?.virtualFile!!
    val deleteFile = VfsUtilCore.virtualToIoFile(deleteVirtualFile)
    assertTrue(deleteFile.isFile)

    val lintFix =
      LintFix.create().name("Delete ${deleteVirtualFile.name}").deleteFile(deleteFile).build()

    val fix = lintFix.toIdeFix(currentFile) as ModCommandLintQuickFix
    myFixture.launchAction(fix.rawIntention())

    assertNull(folder.findFile("delete.txt"))
    assertFalse(deleteFile.isFile)
  }
}
