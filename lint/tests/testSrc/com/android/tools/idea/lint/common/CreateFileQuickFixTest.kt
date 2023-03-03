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
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.util.Base64
import com.intellij.util.ThrowableRunnable
import java.io.File

class CreateFileQuickFixTest : JavaCodeInsightFixtureTestCase() {
  override fun tuneFixture(builder: JavaModuleFixtureBuilder<*>) {
    builder.addJdk(TestUtils.getMockJdk().toString())
  }

  fun testCreateFileFix() {
    // Test creating new text files, new binary files, and deleting files.
    val keepFile =
      myFixture.addFileToProject(
        "/src/android/support/annotation/Keep.java",
        """
          package android.support.annotation;
          import static java.lang.annotation.ElementType.METHOD;
          import static java.lang.annotation.RetentionPolicy.CLASS;
          import java.lang.annotation.Documented;
          import java.lang.annotation.Retention;
          import java.lang.annotation.Target;
          @Documented
          @Retention(CLASS)
          @Target({METHOD})
          public @interface CheckResult {
              String suggest() default "";
          }
      """
          .trimIndent()
      )
    val newFile = File(VfsUtilCore.virtualToIoFile(keepFile.virtualFile).parentFile, "new.txt")
    val fix =
      CreateFileQuickFix(
        newFile,
        "New file\ncontents.",
        null,
        null,
        true,
        "Create ${newFile.name}",
        null
      )
    val context = AndroidQuickfixContexts.BatchContext.getInstance()
    assertTrue(fix.isApplicable(keepFile, keepFile, context.type))

    WriteCommandAction.writeCommandAction(myFixture.project)
      .run(ThrowableRunnable { fix.apply(keepFile, keepFile, context) })

    assertEquals("New file\ncontents.", keepFile.parent?.findFile("new.txt")?.text ?: "<ERROR>")

    // Make sure deletion works too
    val deleteFix = CreateFileQuickFix(newFile, null, null, null, false, "Delete", null)

    assertTrue(deleteFix.isApplicable(keepFile, keepFile, context.type))

    WriteCommandAction.writeCommandAction(myFixture.project)
      .run(ThrowableRunnable { deleteFix.apply(keepFile, keepFile, context) })

    assertNull(keepFile.parent?.findFile("new.txt"))

    val binary = byteArrayOf(0, 1, 2, 3, 4)
    val binFile = File(newFile.parentFile, "new.bin")
    val binaryFix =
      CreateFileQuickFix(binFile, null, binary, null, true, "Create ${newFile.name}", null)
    assertTrue(binaryFix.isApplicable(keepFile, keepFile, context.type))

    WriteCommandAction.writeCommandAction(myFixture.project)
      .run(ThrowableRunnable { binaryFix.apply(keepFile, keepFile, context) })

    val virtualBinFile = LocalFileSystem.getInstance().findFileByIoFile(binFile)
    val contents = virtualBinFile?.contentsToByteArray()
    assertEquals(Base64.encode(binary), Base64.encode(contents))
  }
}
