/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.editors.literals

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.deleteText
import com.android.tools.idea.testing.executeAndSave
import com.android.tools.idea.testing.moveCaretToFirstOccurrence
import com.android.tools.idea.testing.replaceText
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.hasErrorElementInRange
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

fun LiteralReference.toDebugString(withUniqueId: Boolean): String {
  val usagesString = if (usages.distinct().count() == 1) {
    usages.first().fqName.toString()
  }
  else {
    usages.joinToString(", ", "[", "]") { it.fqName.toString() }
  }
  val elementText = "text='$text' value='$constantValue' usages='$usagesString'"
  return if (withUniqueId) {
    "[$uniqueId] $elementText"
  }
  else elementText
}

fun Collection<LiteralReference>.toDebugString(withUniqueId: Boolean = false): String =
  sortedWith(compareBy({ it.initialTextRange.startOffset }, { it.initialTextRange.endOffset }))
    .joinToString("\n") { it.toDebugString(withUniqueId) }

class LiteralsTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  private fun populateKotlinFile(): PsiFile = projectRule.fixture.addFileToProject(
    "/src/test/app/LiteralsTest.kt",
    // language=kotlin
    """
        package test.app

        private val TOP_LEVEL_CONSTANT = "TOP_LEVEL"

        class LiteralsTest {
          inner class Inner {
            private val STR_EXPR = "S2" + "S3"
            private val STR_COMPLEX_EXPR = "S4" + STR_EXPR
          }

          private val INT = 100
          private val INT_EXPR = 100 + 1 + 2 + 3
          private val INT_COMPLEX_EXPR = INT_EXPR + 1
          private val STR = "S1"
          private val OF_BOOLEAN_STR = if (true) "S5" else 3

          fun method(parameter: String, default: String = "DEF1") = parameter + 22 + "S6"

          fun testCall() {
              val variable = "S7"

              method("S8")
              method("${'$'}STR S9")
              method(variable)
              method(variable, "DEF2")
          }
      }
      """.trimIndent())

  private fun populateJavaFile(): PsiFile = projectRule.fixture.addFileToProject(
    "/src/test/app/LiteralsTest.java",
    // language=Java
    """
        package test.app;

        public class LiteralsTest {
          private class Inner {
            private static final String STR_EXPR = "S2" + "S3";
            private static final String STR_COMPLEX_EXPR = "S4" + STR_EXPR;
          }

          private static final int INT = 100;
          private static final int INT_EXPR = 100 + 1 + 2 + 3;
          private static final int INT_COMPLEX_EXPR = INT_EXPR + 1;
          private static final String STR = "S1";

          private String method(String parameter) {
            return parameter + 22 + "S5";
          }

          @SuppressWarnings("ResultOfMethodCallIgnored")
          public void testCall() {
            String variable = "S6";

            method("S7");
            method(STR + "S8");
            method(variable);
          }
        }
      """.trimIndent())

  private fun PsiFile.configureEditor(): PsiFile = this.also {
    projectRule.fixture.configureFromExistingVirtualFile(it.virtualFile)
  }

  @Ignore("b/159582001")
  @Test
  fun `Java literals finder`() {
    val literalsManager = LiteralsManager()
    val file = populateJavaFile().configureEditor()
    ReadAction.run<Throwable> {
      assertFalse(file.hasErrorElementInRange(file.textRange))
    }
    val snapshot = literalsManager.findLiterals(file)
    assertEquals("""
      text='"S2" + "S3"' value='S2S3' usages='test.app.LiteralsTest.Inner.<init>'
      text='"S4" + STR_EXPR' value='S4S2S3' usages='test.app.LiteralsTest.Inner.<init>'
      text='100' value='100' usages='test.app.LiteralsTest.<init>'
      text='100 + 1 + 2 + 3' value='106' usages='test.app.LiteralsTest.<init>'
      text='INT_EXPR + 1' value='107' usages='test.app.LiteralsTest.<init>'
      text='"S1"' value='S1' usages='test.app.LiteralsTest.<init>'
      text='22' value='22' usages='test.app.LiteralsTest.method'
      text='"S5"' value='S5' usages='test.app.LiteralsTest.method'
      text='"S6"' value='S6' usages='test.app.LiteralsTest.testCall'
      text='"S7"' value='S7' usages='test.app.LiteralsTest.testCall'
      text='STR + "S8"' value='S1S8' usages='test.app.LiteralsTest.testCall'
    """.trimIndent(), snapshot.all.toDebugString())

    projectRule.fixture.editor.executeAndSave {
      projectRule.fixture.editor.moveCaretToFirstOccurrence("S4")
      projectRule.fixture.type("Modified")
      projectRule.fixture.editor.moveCaretToFirstOccurrence("+ 3")
      projectRule.fixture.type("+5")
    }
    assertEquals("""
      text='"ModifiedS4" + STR_EXPR' value='ModifiedS4S2S3' usages='test.app.LiteralsTest.Inner.<init>'
      text='100 + 1 + 2 +5+ 3' value='111' usages='test.app.LiteralsTest.<init>'
      text='INT_EXPR + 1' value='112' usages='test.app.LiteralsTest.<init>'
    """.trimIndent(), snapshot.modified.toDebugString())
  }

  @Test
  fun `Kotlin literals finder`() {
    val literalsManager = LiteralsManager()
    val file = populateKotlinFile().configureEditor()
    ReadAction.run<Throwable> {
      assertFalse(file.hasErrorElementInRange(file.textRange))
    }
    val snapshot = literalsManager.findLiterals(file)
    assertEquals("""
      text='"TOP_LEVEL"' value='TOP_LEVEL' usages='[]'
      text='"S2" + "S3"' value='S2S3' usages='test.app.LiteralsTest.Inner.<init>'
      text='"S4" + STR_EXPR' value='S4S2S3' usages='[]'
      text='100' value='100' usages='[]'
      text='100 + 1 + 2 + 3' value='106' usages='test.app.LiteralsTest.<init>'
      text='INT_EXPR + 1' value='107' usages='[]'
      text='"S1"' value='S1' usages='test.app.LiteralsTest.testCall'
      text='true' value='true' usages='test.app.LiteralsTest.<init>'
      text='"S5"' value='S5' usages='test.app.LiteralsTest.<init>'
      text='3' value='3' usages='test.app.LiteralsTest.<init>'
      text='"DEF1"' value='DEF1' usages='test.app.LiteralsTest.method'
      text='22' value='22' usages='test.app.LiteralsTest.method'
      text='"S6"' value='S6' usages='test.app.LiteralsTest.method'
      text='"S7"' value='S7' usages='[test.app.LiteralsTest.testCall, test.app.LiteralsTest.testCall]'
      text='"S8"' value='S8' usages='test.app.LiteralsTest.testCall'
      text='"${'$'}STR S9"' value='S1 S9' usages='test.app.LiteralsTest.testCall'
      text='"DEF2"' value='DEF2' usages='test.app.LiteralsTest.testCall'
    """.trimIndent(), snapshot.all.toDebugString())

    projectRule.fixture.editor.executeAndSave {
      replaceText("\"S1\"", "\"\"\"\nS1_MULTILINE\n\"\"\"")
      deleteText("100 +")
    }
    assertEquals("""
      text='1 + 2 + 3' value='6' usages='test.app.LiteralsTest.<init>'
      text='INT_EXPR + 1' value='7' usages='[]'
      text='""${'"'}
      S1_MULTILINE
      ""${'"'}' value='
      S1_MULTILINE
      ' usages='test.app.LiteralsTest.testCall'
      text='"${'$'}STR S9"' value='
      S1_MULTILINE
       S9' usages='test.app.LiteralsTest.testCall'
    """.trimIndent(), snapshot.modified.toDebugString())
  }


  @Test
  fun `Kotlin literals finder for string interpolation`() {
    val literalsManager = LiteralsManager()
    val file = projectRule.fixture.addFileToProject(
      "/src/test/app/LiteralsTest.kt",
      // language=kotlin
      """
        package test.app

        private val testVal = "TEST"

        fun method(name: String) {
          println("Template ${'$'}name!!")
        }

        fun testCall() {
            method(name = "NAME ${'$'}testVal")
        }
      """).configureEditor()
    ReadAction.run<Throwable> {
      assertFalse(file.hasErrorElementInRange(file.textRange))
    }
    val snapshot = literalsManager.findLiterals(file)
    assertEquals("""
      text='"TEST"' value='TEST' usages='test.app.LiteralsTestKt.testCall'
      text='Template ' value='Template ' usages='test.app.LiteralsTestKt.method'
      text='!!' value='!!' usages='test.app.LiteralsTestKt.method'
      text='"NAME ${'$'}testVal"' value='NAME TEST' usages='test.app.LiteralsTestKt.testCall'
    """.trimIndent(), snapshot.all.toDebugString())

    projectRule.fixture.editor.executeAndSave {
      replaceText("Template", "New modified template")
    }
    assertEquals("""
      text='New modified template ' value='New modified template ' usages='test.app.LiteralsTestKt.method'
    """.trimIndent(), snapshot.modified.toDebugString())
  }


  @Test
  fun `removed literal does not show in the modified snapshot`() {
    val literalsManager = LiteralsManager()
    val file = populateKotlinFile().configureEditor()
    ReadAction.run<Throwable> {
      assertFalse(file.hasErrorElementInRange(file.textRange))
    }
    val snapshot = literalsManager.findLiterals(file)

    projectRule.fixture.editor.executeAndSave {
      deleteText("private val INT = 100")
    }
    assertTrue(snapshot.modified.toDebugString(), snapshot.modified.isEmpty())
    assertEquals("Only one literal was invalidated by the modification", 1, snapshot.all.filter { !it.isValid }.count())
    assertTrue("no invalid literals are in a new snapshot", snapshot.newSnapshot().all.none { !it.isValid })
  }

  @Test
  fun `find literals in a kotlin method`() {
    val literalsManager = LiteralsManager()
    val file = populateKotlinFile().configureEditor()
    ReadAction.run<Throwable> {
      assertFalse(file.hasErrorElementInRange(file.textRange))
    }
    val method = ReadAction.compute<KtFunction, Throwable> {
      projectRule.fixture.findElementByText("method", KtFunction::class.java)
    }
    val snapshot = literalsManager.findLiterals(method)
    assertEquals("""
      text='"DEF1"' value='DEF1' usages='test.app.LiteralsTest.method'
      text='22' value='22' usages='test.app.LiteralsTest.method'
      text='"S6"' value='S6' usages='test.app.LiteralsTest.method'
    """.trimIndent(), snapshot.all.toDebugString())
  }

  @Test
  fun `find usages in lambdas`() {
    val literalsManager = LiteralsManager()
    val lambdaFile = projectRule.fixture.addFileToProject(
      "LambdaTest.kt",
      // language=kotlin
      """
        private const val CONSTANT = "constant"

        class AClass {
            fun withLambda(a: () -> Unit) {
                a()
            }

            fun AMethod() {
                fun BMethod() {
                  withLambda {
                      withLambda {
                        println(CONSTANT)
                      }
                  }
                }
                BMethod()
            }
        }
      """.trimIndent())
    val file = lambdaFile.configureEditor()
    val snapshot = literalsManager.findLiterals(lambdaFile)
    assertEquals("""
      text='"constant"' value='constant' usages='AClass.AMethod.BMethod.<anonymous>.<anonymous>'
    """.trimIndent(), snapshot.all.toDebugString())
  }

  @Test
  fun `Test simple unique id generator`() {
    val file = populateKotlinFile().configureEditor()
    val children = ReadAction.compute<Array<SmartPsiElementPointer<PsiElement>>, Throwable> {
      PsiTreeUtil.findChildrenOfType(file, KtExpression::class.java)
        .take(2)
        .map {
          SmartPointerManager.createPointer<PsiElement>(it)
        }
        .toTypedArray()
    }

    var children0Id = ""
    var children1Id = ""
    ReadAction.run<Throwable> {
      children0Id = SimplePsiElementUniqueIdProvider.getUniqueId(children[0].element!!)
      children1Id = SimplePsiElementUniqueIdProvider.getUniqueId(children[1].element!!)
      assertTrue(children0Id != children1Id)
    }

    // Insert a new method
    projectRule.fixture.editor.executeAndSave {
      replaceText("fun method",
                  """
                    fun method2(a: Int) { println("added") }
                    fun method""".trimIndent())
    }

    ReadAction.run<Throwable> {
      // The id is maintained as part of the object
      assertEquals(children0Id, SimplePsiElementUniqueIdProvider.getUniqueId(children[0].element!!))
      assertEquals(children1Id, SimplePsiElementUniqueIdProvider.getUniqueId(children[1].element!!))
    }
  }
}