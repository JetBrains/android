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

import com.android.testutils.TestUtils
import com.android.tools.lint.detector.api.LintFix
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import com.intellij.util.ThrowableRunnable

class ReplaceStringQuickFixTest : JavaCodeInsightFixtureTestCase() {
  override fun tuneFixture(builder: JavaModuleFixtureBuilder<*>) {
    builder.addJdk(TestUtils.getMockJdk().toString())
  }

  fun testImportsJava() {
    // Unit test for [ReplaceStringQuickFix]' import handling of Java files.
    val lintFix =
      LintFix.create()
        .replace()
        .text("oldName")
        .with("newName")
        // Import class, method and field respectively
        .imports("java.util.ArrayList", "java.lang.Math.abs", "java.lang.Integer.MAX_VALUE")
        .build()

    // Test Java
    val file =
      myFixture.addFileToProject(
        "src/p1/p2/ImportTest.java",
        // language=Java
        """
      package p1.p2;
      public class ImportTest {
          public void oldName() {
          }
      }
      """
          .trimIndent()
      )
    myFixture.configureFromExistingVirtualFile(file.virtualFile)

    val fix = ReplaceStringQuickFix.create(file, lintFix as LintFix.ReplaceString)
    val context = AndroidQuickfixContexts.BatchContext.getInstance()
    val element = (file as PsiJavaFile).classes[0].methods[0].nameIdentifier!!

    assertTrue(fix.isApplicable(element, element, context.type))

    WriteCommandAction.writeCommandAction(myFixture.project)
      .run(ThrowableRunnable { fix.apply(element, element, context) })

    assertEquals(
      """
      package p1.p2;

      import java.util.ArrayList;

      import static java.lang.Integer.MAX_VALUE;
      import static java.lang.Math.abs;

      public class ImportTest {
          public void newName() {
          }
      }
      """
        .trimIndent(),
      file.text
    )

    // Try importing again and make sure we don't double-import
    val vFile = file.virtualFile
    val afterFirst = file.text
    WriteCommandAction.writeCommandAction(myFixture.project)
      .run(
        ThrowableRunnable {
          val manager = PsiDocumentManager.getInstance(project)
          val document = manager.getDocument(file)!!
          // Revert source text back from newName to oldName such that fix can work again
          val start = document.text.indexOf("newName")
          val end = start + "newName".length
          document.replaceString(start, end, "oldName")
          manager.commitAllDocuments()

          val sameFile = PsiManager.getInstance(project).findFile(vFile)!!
          assertTrue(fix.isApplicable(sameFile, sameFile, context.type))
          fix.apply(sameFile, sameFile, context)
        }
      )

    assertEquals(afterFirst, file.text)
  }

  fun testImportsKotlin() {
    // Like testImportsJava but for Kotlin (since the import management
    // code has completely separate implementations for Java and Kotlin).
    // Same scenario: we test importing classes, methods and fields, into
    // a Kotlin file this time.
    val lintFix =
      LintFix.create()
        .replace()
        .text("oldName")
        .with("newName")
        // Import class, method and field respectively
        .imports(
          "java.util.ArrayList",
          "java.lang.Math.abs",
          "java.lang.Integer.MAX_VALUE",
          "test.pkg.MyTest",
          "test.pkg.MyTest.MY_CONSTANT",
          // Extension methods. For now, work around this
          // by supplying the actual class containing the declaration
          // since the search doesn't find methods from packages on its
          // own.
          "test.pkg.MyUtil.myMethod",
          "test.pkg.Kotlin2Kt.myMethod2",
          "test.pkg.myMethod3"
        )
        .build()

    // Test Java
    val file =
      myFixture.configureByText(
        "/src/p1/p2/ImportTest.kt",
        // language=Kt
        """
      package p1.p2
      class ImportTest {
          fun oldName() {
          }
      }
      """
          .trimIndent()
      )

    myFixture.addFileToProject(
      "/src/test/pkg/kotlin.kt",
      // language=Kt
      """
      @file:JvmName("MyUtil")
      package test.pkg
      class MyTest {
        companion object {
          const val MY_CONSTANT = 42
        }
      }
      fun String.myMethod(): String = this
      """
        .trimIndent()
    )

    myFixture.addFileToProject(
      "/src/test/pkg/kotlin2.kt",
      // language=Kt
      """
      package test.pkg
      class MyTest2
      fun String.myMethod2(): String = this
      fun String.myMethod3(): String = this
      """
        .trimIndent()
    )

    val fix = ReplaceStringQuickFix.create(file, lintFix as LintFix.ReplaceString)
    val context = AndroidQuickfixContexts.BatchContext.getInstance()
    assertTrue(fix.isApplicable(file, file, context.type))
    WriteCommandAction.writeCommandAction(myFixture.project)
      .run(ThrowableRunnable { fix.apply(file, file, context) })

    assertEquals(
      """
      package p1.p2

      import test.pkg.MyTest
      import test.pkg.MyTest.Companion.MY_CONSTANT
      import test.pkg.myMethod
      import test.pkg.myMethod2
      import test.pkg.myMethod3
      import java.lang.Integer.MAX_VALUE
      import java.lang.Math.abs
      import java.util.ArrayList

      class ImportTest {
          fun newName() {
          }
      }
      """
        .trimIndent(),
      file.text
    )

    // Try importing again and make sure we don't double-import
    val afterFirst = file.text
    WriteCommandAction.writeCommandAction(myFixture.project)
      .run(
        ThrowableRunnable {
          val manager = PsiDocumentManager.getInstance(project)
          val document = manager.getDocument(file)!!
          // Revert source text back from newName to oldName such that fix can work again
          val start = document.text.indexOf("newName")
          val end = start + "newName".length
          document.replaceString(start, end, "oldName")
          manager.commitAllDocuments()

          assertTrue(fix.isApplicable(file, file, context.type))
          fix.apply(file, file, context)
        }
      )

    assertEquals(afterFirst, file.text)
  }

  fun testImportEdited() {
    // Unit test for [ReplaceStringQuickFix]'s support for importing and shortening in the same fix
    val lintFix =
      LintFix.create()
        .replace()
        .text("println()")
        .with("println(java.lang.Integer.MAX_VALUE)")
        .imports("java.lang.Integer.MAX_VALUE")
        .shortenNames()
        .reformat(true)
        .build()

    // Test Java
    @Suppress("StringOperationCanBeSimplified")
    val file =
      myFixture.addFileToProject(
        "src/p1/p2/ShortenTest.java",
        // language=Java
        """
      package p1.p2;
      import static System.out.println;
      public class ShortenTest {
          public void test() {
              println();
          }
      }
      """
          .trimIndent()
      )
    myFixture.configureFromExistingVirtualFile(file.virtualFile)

    val fix = ReplaceStringQuickFix.create(file, lintFix as LintFix.ReplaceString)
    val context = AndroidQuickfixContexts.BatchContext.getInstance()
    val element = file.findElementAt(file.text.indexOf("println()"))?.parent?.parent!!
    assertEquals("println()", element.text)

    assertTrue(fix.isApplicable(element, element, context.type))

    WriteCommandAction.writeCommandAction(myFixture.project)
      .run(ThrowableRunnable { fix.apply(element, element, context) })

    // Like the original file, but String replaced with java.util.ArrayList and
    // then ArrayList imported and the fully qualified name replaced with the simple name.
    assertEquals(
      """
      package p1.p2;
      import static System.out.println;
      import static java.lang.Integer.MAX_VALUE;

      public class ShortenTest {
          public void test() {
              println(MAX_VALUE);
          }
      }
      """
        .trimIndent(),
      file.text
    )
  }

  fun testShortenJava() {
    // Unit test for [ReplaceStringQuickFix]'s support for symbol shortening in Java
    val lintFix =
      LintFix.create()
        .replace()
        .text("new String")
        .with("new java.util.ArrayList")
        .shortenNames()
        .reformat(true)
        .build()

    // Test Java
    @Suppress("StringOperationCanBeSimplified")
    val file =
      myFixture.addFileToProject(
        "src/p1/p2/ShortenTest.java",
        // language=Java
        """
      package p1.p2;
      public class ShortenTest {
          public void test() {
              Object o = new String();
          }
      }
      """
          .trimIndent()
      )
    myFixture.configureFromExistingVirtualFile(file.virtualFile)

    val fix = ReplaceStringQuickFix.create(file, lintFix as LintFix.ReplaceString)
    val context = AndroidQuickfixContexts.BatchContext.getInstance()
    val element = file.findElementAt(file.text.indexOf("new String"))?.parent!!
    assertEquals("new String()", element.text)

    assertTrue(fix.isApplicable(element, element, context.type))

    WriteCommandAction.writeCommandAction(myFixture.project)
      .run(ThrowableRunnable { fix.apply(element, element, context) })

    // Like the original file, but String replaced with java.util.ArrayList and
    // then ArrayList imported and the fully qualified name replaced with the simple name.
    assertEquals(
      """
      package p1.p2;

      import java.util.ArrayList;

      public class ShortenTest {
          public void test() {
              Object o = new ArrayList();
          }
      }
      """
        .trimIndent(),
      file.text
    )
  }

  fun testShortenKotlin() {
    // Unit test for [ReplaceStringQuickFix]'s support for symbol shortening in Kotlin
    val lintFix =
      LintFix.create()
        .replace()
        .text("String")
        .with("java.util.ArrayList")
        .shortenNames()
        .reformat(true)
        .build()

    // Test Java
    @Suppress("StringOperationCanBeSimplified")
    val file =
      myFixture.addFileToProject(
        "src/p1/p2/ShortenTest.kt",
        // language=KT
        """
      package p1.p2
      fun test() {
        val o = String()
      }
      """
          .trimIndent()
      )
    myFixture.configureFromExistingVirtualFile(file.virtualFile)

    val fix = ReplaceStringQuickFix.create(file, lintFix as LintFix.ReplaceString)
    val context = AndroidQuickfixContexts.BatchContext.getInstance()
    val element = file.findElementAt(file.text.indexOf("String"))?.parent!!
    assertEquals("String", element.text)

    assertTrue(fix.isApplicable(element, element, context.type))

    WriteCommandAction.writeCommandAction(myFixture.project)
      .run(ThrowableRunnable { fix.apply(element, element, context) })

    // Like the original file, but String replaced with java.util.ArrayList and
    // then ArrayList imported and the fully qualified name replaced with the simple name.
    assertEquals(
      """
      package p1.p2

      import java.util.ArrayList

      fun test() {
        val o = ArrayList()
      }
      """
        .trimIndent(),
      file.text
    )
  }
}
