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

import com.android.tools.idea.editors.literals.internal.LiveLiteralsDeploymentReportService
import com.android.tools.idea.editors.literals.internal.LiveLiteralsFinder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.deleteText
import com.android.tools.idea.testing.executeAndSave
import com.android.tools.idea.testing.replaceText
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.DumbServiceImpl
import com.intellij.openapi.rd.util.withUiContext
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.hasErrorElementInRange
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test

fun LiteralUsageReference.toDebugString() = "${fqName}-${range.startOffset}"

fun LiteralReference.toDebugString(withUniqueId: Boolean): String {
  val usagesString = if (usages.distinct().count() == 1) {
    val usage = usages.single()
    "${usage.fqName}-${usage.range.startOffset}"
  }
  else {
    usages
      .map { it.toDebugString() }
      .sorted()
      .joinToString(", ", "[", "]")
  }
  val elementText = "text='$text' location='$fileName $initialTextRange' value='$constantValue' usages='$usagesString'"
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

  private fun PsiFile.configureEditor(): PsiFile = this.also {
    projectRule.fixture.configureFromExistingVirtualFile(it.virtualFile)
  }

  private fun LiteralsManager.findLiteralsBlocking(root : PsiElement) = runBlocking {
    var savedException: Throwable? = null
    repeat(3) {
      try {
        return@runBlocking findLiterals(root)
      } catch (e: ProcessCanceledException) {
        // After 222.2889.14 the visitor can throw ProcessCanceledException instead of IndexNotReadyException if in dumb mode.
        savedException = e
      }
      delay(500L * it)
    }
    throw savedException!!
  }

  @Before
  fun setup() {
    Logger.getInstance(LiveLiteralsService::class.java).setLevel(LogLevel.ALL)
    Logger.getInstance(LiteralsManager::class.java).setLevel(LogLevel.ALL)
    Logger.getInstance(LiveLiteralsFinder::class.java).setLevel(LogLevel.ALL)
    Logger.getInstance(LiveLiteralsDeploymentReportService::class.java).setLevel(LogLevel.ALL)
  }

  @Test
  fun `Kotlin literals finder`() {
    val literalsManager = LiteralsManager()
    val file = populateKotlinFile().configureEditor()
    ReadAction.run<Throwable> {
      assertFalse(file.hasErrorElementInRange(file.textRange))
    }
    val snapshot = literalsManager.findLiteralsBlocking(file)
    assertEquals("""
      text='TOP_LEVEL' location='LiteralsTest.kt (56,65)' value='TOP_LEVEL' usages='test.app.LiteralsTestKt.<init>-56'
      text='S2' location='LiteralsTest.kt (145,147)' value='S2' usages='test.app.LiteralsTest.Inner.<init>-145'
      text='S3' location='LiteralsTest.kt (152,154)' value='S3' usages='test.app.LiteralsTest.Inner.<init>-152'
      text='S4' location='LiteralsTest.kt (194,196)' value='S4' usages='test.app.LiteralsTest.Inner.<init>-194'
      text='100' location='LiteralsTest.kt (238,241)' value='100' usages='test.app.LiteralsTest.<init>-238'
      text='100' location='LiteralsTest.kt (269,272)' value='100' usages='test.app.LiteralsTest.<init>-269'
      text='1' location='LiteralsTest.kt (275,276)' value='1' usages='test.app.LiteralsTest.<init>-275'
      text='2' location='LiteralsTest.kt (279,280)' value='2' usages='test.app.LiteralsTest.<init>-279'
      text='3' location='LiteralsTest.kt (283,284)' value='3' usages='test.app.LiteralsTest.<init>-283'
      text='1' location='LiteralsTest.kt (331,332)' value='1' usages='test.app.LiteralsTest.<init>-331'
      text='S1' location='LiteralsTest.kt (356,358)' value='S1' usages='test.app.LiteralsTest.<init>-356'
      text='true' location='LiteralsTest.kt (397,401)' value='true' usages='test.app.LiteralsTest.<init>-397'
      text='S5' location='LiteralsTest.kt (404,406)' value='S5' usages='test.app.LiteralsTest.<init>-404'
      text='3' location='LiteralsTest.kt (413,414)' value='3' usages='test.app.LiteralsTest.<init>-413'
      text='DEF1' location='LiteralsTest.kt (469,473)' value='DEF1' usages='test.app.LiteralsTest.method-469'
      text='22' location='LiteralsTest.kt (490,492)' value='22' usages='test.app.LiteralsTest.method-490'
      text='S6' location='LiteralsTest.kt (496,498)' value='S6' usages='test.app.LiteralsTest.method-496'
      text='S7' location='LiteralsTest.kt (546,548)' value='S7' usages='test.app.LiteralsTest.testCall-546'
      text='S8' location='LiteralsTest.kt (567,569)' value='S8' usages='test.app.LiteralsTest.testCall-567'
      text=' S9' location='LiteralsTest.kt (592,595)' value=' S9' usages='test.app.LiteralsTest.testCall-592'
      text='DEF2' location='LiteralsTest.kt (649,653)' value='DEF2' usages='test.app.LiteralsTest.testCall-649'
    """.trimIndent(), snapshot.all.toDebugString())

    projectRule.fixture.editor.executeAndSave {
      replaceText("\"S1\"", "\"\"\"\nS1_MULTILINE\n\"\"\"")
      deleteText("100 +")
    }
    assertEquals("""
      text='
      ' location='LiteralsTest.kt (356,358)' value='
      ' usages='test.app.LiteralsTest.<init>-356'
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
    val snapshot = literalsManager.findLiteralsBlocking(file)
    assertEquals("""
      text='TEST' location='LiteralsTest.kt (58,62)' value='TEST' usages='test.app.LiteralsTestKt.<init>-58'
      text='Template ' location='LiteralsTest.kt (119,128)' value='Template ' usages='test.app.LiteralsTestKt.method-119'
      text='!!' location='LiteralsTest.kt (133,135)' value='!!' usages='test.app.LiteralsTestKt.method-133'
      text='NAME ' location='LiteralsTest.kt (201,206)' value='NAME ' usages='test.app.LiteralsTestKt.testCall-201'
    """.trimIndent(), snapshot.all.toDebugString())

    projectRule.fixture.editor.executeAndSave {
      replaceText("Template", "New modified template")
    }
    assertEquals("""
      text='New modified template ' location='LiteralsTest.kt (119,128)' value='New modified template ' usages='test.app.LiteralsTestKt.method-119'
    """.trimIndent(), snapshot.modified.toDebugString())
  }


  @Test
  fun `removed literal does not show in the modified snapshot`() {
    val literalsManager = LiteralsManager()
    val file = populateKotlinFile().configureEditor()
    ReadAction.run<Throwable> {
      assertFalse(file.hasErrorElementInRange(file.textRange))
    }
    val snapshot = literalsManager.findLiteralsBlocking(file)

    println(snapshot.all.toDebugString())

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
    val snapshot = literalsManager.findLiteralsBlocking(method)
    assertEquals("""
      text='DEF1' location='LiteralsTest.kt (469,473)' value='DEF1' usages='test.app.LiteralsTest.method-469'
      text='22' location='LiteralsTest.kt (490,492)' value='22' usages='test.app.LiteralsTest.method-490'
      text='S6' location='LiteralsTest.kt (496,498)' value='S6' usages='test.app.LiteralsTest.method-496'
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
    val snapshot = literalsManager.findLiteralsBlocking(lambdaFile)
    assertEquals("""
      text='constant' location='LambdaTest.kt (30,38)' value='constant' usages='LambdaTestKt.<init>-30'
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


  @Test
  fun `test literal deletion and reattach`() {
    val literalsManager = LiteralsManager()
    val file = projectRule.fixture.addFileToProject(
      "/src/test/app/LiteralsTest.kt",
      // language=kotlin
      """
        package test.app

        class LiteralsTest {
          private val STR = "S1"

          fun testCall() {
            method(STR)
          }
      }
      """.trimIndent()).configureEditor()
    ReadAction.run<Throwable> {
      assertFalse(file.hasErrorElementInRange(file.textRange))
    }
    val snapshot = literalsManager.findLiteralsBlocking(file)
    assertEquals(
      "text='S1' location='LiteralsTest.kt (66,68)' value='S1' usages='test.app.LiteralsTest.<init>-66'",
      snapshot.all.toDebugString())

    projectRule.fixture.editor.executeAndSave {
      // Delete the literal. This will invalidate the current value.
      replaceText("S1", "")
    }
    assertEquals(
      "text='<null>' location='LiteralsTest.kt (66,68)' value='null' usages='test.app.LiteralsTest.<init>-66'",
      snapshot.modified.toDebugString())
    projectRule.fixture.editor.executeAndSave {
      // Delete the literal
      replaceText("\"\"", "\"S3\"")
    }
    // Now we will have reattached to the new literal that is in the same position.
    assertEquals(
      "text='S3' location='LiteralsTest.kt (66,68)' value='S3' usages='test.app.LiteralsTest.<init>-66'",
      snapshot.modified.toDebugString())
  }

  @Test
  fun `check multiline string`() {
    val literalsManager = LiteralsManager()
    val file = projectRule.fixture.addFileToProject(
      "/src/test/app/LiteralsTest.kt",
      // language=kotlin
      """
        package test.app

        class LiteralsTest {
          private val STR = ""${'"'}
            {
              "margin" : 150,
              "width" : 20
            }
          ""${'"'}

          private val SIMPLE = ""${'"'}
              Hello world!
          ""${'"'}
  
          private val ONE_LINE = ""${'"'}In one line""${'"'}

          fun testCall() {
            method(STR)
          }
      }
      """.trimIndent()).configureEditor()

    val snapshot = literalsManager.findLiteralsBlocking(file)
    assertEquals(
      """
      text='
            {
              "margin" : 150,
              "width" : 20
            }
          ' location='LiteralsTest.kt (68,134)' value='
            {
              "margin" : 150,
              "width" : 20
            }
          ' usages='test.app.LiteralsTest.<init>-68'
      text='
              Hello world!
          ' location='LiteralsTest.kt (167,193)' value='
              Hello world!
          ' usages='test.app.LiteralsTest.<init>-167'
      text='In one line' location='LiteralsTest.kt (228,239)' value='In one line' usages='test.app.LiteralsTest.<init>-228'
      """.trimIndent(),
      snapshot.all.toDebugString())
  }

  @Test
  fun `check highlights`() {
    val literalsManager = LiteralsManager()
    val file = projectRule.fixture.addFileToProject(
      "/src/test/app/LiteralsTest.kt",
      // language=kotlin
      """
        package test.app

        class LiteralsTest {
          private val SIMPLE = "S1"
          private val STR = ""${'"'}
            {
              "margin" : 150,
              "width" : 20
            }
          ""${'"'}
          private val ONE_LINE = ""${'"'}In one line""${'"'}

          fun testCall() {
            method(STR)
          }
      }
      """.trimIndent()).configureEditor()

    val snapshot = literalsManager.findLiteralsBlocking(file)
    val outHighlighters = mutableSetOf<RangeHighlighter>()
    UIUtil.invokeAndWaitIfNeeded(Runnable {
      snapshot.highlightSnapshotInEditor(
        projectRule.project,
        projectRule.fixture.editor,
        LITERAL_TEXT_ATTRIBUTE_KEY,
        outHighlighters)
    })

    val output = ReadAction.compute<String, Throwable> {
      outHighlighters.joinToString("\n") {
        "${it.startOffset}-${it.endOffset}"
      }
    }
    assertEquals(
      """
        69-71
        95-167
        198-209
      """.trimIndent(),
      output
    )
  }

  @Test
  fun `check negative int literals`() {
    val literalsManager = LiteralsManager()
    val file = projectRule.fixture.addFileToProject(
      "/src/test/app/LiteralsTest.kt",
      // language=kotlin
      """
        package test.app

        class LiteralsTest {
          private val SIMPLE = -120

          fun testCall() {
            method(SIMPLE)
          }
      }
      """.trimIndent()).configureEditor()

    val snapshot = literalsManager.findLiteralsBlocking(file)
    assertEquals(
      "text='-120' location='LiteralsTest.kt (68,72)' value='-120' usages='test.app.LiteralsTest.<init>-68'",
      snapshot.all.toDebugString())

    projectRule.fixture.editor.executeAndSave {
      // Negative to negative
      replaceText("E = -12", "E = -15")
    }
    assertEquals(
      "text='-150' location='LiteralsTest.kt (68,72)' value='-150' usages='test.app.LiteralsTest.<init>-68'",
      snapshot.modified.toDebugString())

    projectRule.fixture.editor.executeAndSave {
      // Change negative to positive (without touching the full node since that would invalidate it)
      replaceText("E = -15", "E = 10")
    }
    assertEquals(
      "text='100' location='LiteralsTest.kt (68,72)' value='100' usages='test.app.LiteralsTest.<init>-68'",
      snapshot.modified.toDebugString())

    projectRule.fixture.editor.executeAndSave {
      // And back to a negative
      replaceText("E = ", "E = -")
    }
    assertEquals(
      "text='-100' location='LiteralsTest.kt (68,72)' value='-100' usages='test.app.LiteralsTest.<init>-68'",
      snapshot.modified.toDebugString())
  }

  @Test
  fun `test parallel literal finding`() {
    val literalsManager = LiteralsManager()
    val files = (1..100).map {
      val fileId = it.toString().padStart(4, '0')
      projectRule.fixture.addFileToProject(
        "/src/test/app/LiteralsTest$fileId.kt",
        // language=kotlin
        """
        package test.app

        class LiteralsTest$fileId {
          private val SIMPLE = -120

          fun testCall() {
            method(SIMPLE)
          }
      }
      """.trimIndent())
    }

    runBlocking {
      val asyncLiterals = files.map {
        it.name to async { literalsManager.findLiteralsBlocking(it) }
      }

      asyncLiterals.forEach { (fileName, async) ->
        val className = fileName.substringBefore(".")
        val snapshot = async.await()
        assertEquals(
          "text='-120' location='$fileName (72,76)' value='-120' usages='test.app.$className.<init>-72'",
          snapshot.all.toDebugString())
      }
    }
  }

  @Suppress("UnstableApiUsage")
  @Test
  fun `read literal constant in non-smart mode`() {
    val literalsManager = LiteralsManager()
    val files = (1..100).map {
      val fileId = it.toString().padStart(4, '0')
      projectRule.fixture.addFileToProject(
        "/src/test/app/LiteralsTest$fileId.kt",
        // language=kotlin
        """
        package test.app

        class LiteralsTest$fileId {
          private val SIMPLE = -120

          fun testCall() {
            method(SIMPLE)
          }
      }
      """.trimIndent())
    }

    runBlocking {
      val asyncLiterals = files.map {
        it.name to async { literalsManager.findLiteralsBlocking(it) }
      }

      val literals = asyncLiterals.map { it.second.await() }.toList()

      withUiContext {
        (DumbService.getInstance(projectRule.project) as DumbServiceImpl).isDumb = true
      }
      val contents = literals.flatMap {
        it.all
      }.map {
        it.constantValue
      }.distinct().joinToString("\n")
      assertEquals("-120", contents)
    }
  }

  @Suppress("UnstableApiUsage")
  @Test
  fun `find literals in non-smart mode`() {
    val literalsManager = LiteralsManager()
    val file = projectRule.fixture.addFileToProject(
      "/src/test/app/LiteralsTest.kt",
      // language=kotlin
      """
      package test.app

      class LiteralsTest {
        private val SIMPLE = -120

        fun testCall() {
          method(SIMPLE)
        }
    }
    """.trimIndent())

    runBlocking {
      withUiContext {
        (DumbService.getInstance(projectRule.project) as DumbServiceImpl).isDumb = true
      }

      try {
        literalsManager.findLiteralsBlocking(file)
        fail("findLiterals will throw ProcessCanceledException when called not called in smart mode")
      } catch (_: ProcessCanceledException) {
      }
    }
  }
}
