package com.android.tools.idea.editors.build

import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.executeAndSave
import com.android.tools.idea.testing.insertText
import com.android.tools.idea.testing.replaceText
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PsiCodeFileChangeDetectorServiceTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()
  private val fixture: CodeInsightTestFixture
    get() = projectRule.fixture

  private lateinit var psiCodeFileChangeDetectorService: PsiCodeFileChangeDetectorService
  private lateinit var kotlinFile: PsiFile
  private lateinit var secondKotlinFile: PsiFile
  private lateinit var javaFile: PsiFile
  private lateinit var xmlFile: PsiFile

  @Before
  fun setUp() {
    psiCodeFileChangeDetectorService = PsiCodeFileChangeDetectorService.getInstance(projectRule.project)

    kotlinFile = fixture.addFileToProject(
      "src/a/test.kt",
      //language=kotlin
      """
        package a

        fun test() {
          // INSERT CODE

          /*
           * LONG COMMENT
           */
        }
      """.trimIndent())
    secondKotlinFile = fixture.addFileToProject(
      "src/b/otherFile.kt",
      //language=kotlin
      """
        package b

        fun otherTest() {
          // INSERT CODE
        }
      """.trimIndent())
    javaFile = fixture.addFileToProject(
      "src/c/MyClass.java",
      //language=java
      """
        package c;

        class MyClass {
            // INSERT METHOD
        }
      """.trimIndent())
    xmlFile = fixture.addFileToProject(
      "res/test.xml",
      //language=xml
      """
        <hello>Hello<!--INSERT XML--></hello>
      """.trimIndent())
  }

  @Test
  fun `new service has no changes`() {
    // New files are not part of the existing build so they can not immediately be used for previews. Because of this, we do not want
    // to mark them as out of date until the user modifies them.

    assertTrue(psiCodeFileChangeDetectorService.outOfDateFiles.isEmpty())
  }

  @Test
  fun `file changes are detected in relevant languages`() = runBlocking {
    val flowUpdates = StringBuilder()
    val flowJob = launch(workerThread) {
      psiCodeFileChangeDetectorService.fileUpdatesFlow
        .take(6) // We expect 6 changes
        .collect {
          flowUpdates.appendLine(it)
        }
    }

    WriteCommandAction.runWriteCommandAction(projectRule.project) {
      fixture.openFileInEditor(kotlinFile.virtualFile)
      fixture.editor.executeAndSave {
        insertText("""

          fun hello() {}

        """.trimIndent())
      }
    }
    assertEquals("test.kt", psiCodeFileChangeDetectorService.outOfDateFiles.joinToString { it.name })
    psiCodeFileChangeDetectorService.markAsUpToDate(setOf(kotlinFile))
    assertTrue(psiCodeFileChangeDetectorService.outOfDateFiles.isEmpty())

    WriteCommandAction.runWriteCommandAction(projectRule.project) {
      fixture.openFileInEditor(kotlinFile.virtualFile)
      fixture.editor.executeAndSave {
        insertText("\nfun newMethod() {}")
      }
      fixture.openFileInEditor(secondKotlinFile.virtualFile)
      fixture.editor.executeAndSave {
        insertText("\nfun newMethod() {}")
      }
      fixture.openFileInEditor(javaFile.virtualFile)
      fixture.editor.executeAndSave {
        replaceText("// INSERT METHOD", "public void test() {}")
      }
      fixture.openFileInEditor(xmlFile.virtualFile)
      fixture.editor.executeAndSave {
        replaceText("<!--INSERT XML-->", "<a></a>")
      }
    }
    assertEquals("""
      MyClass.java
      otherFile.kt
      test.kt
    """.trimIndent(), psiCodeFileChangeDetectorService.outOfDateFiles.map { it.name }.sorted().joinToString("\n"))

    // Wait for all the changes to have been collected
    flowJob.join()
    assertEquals("""
      []
      [KtFile: test.kt]
      []
      [KtFile: test.kt]
      [KtFile: test.kt, KtFile: otherFile.kt]
      [KtFile: test.kt, KtFile: otherFile.kt, PsiJavaFile:MyClass.java]
    """.trimIndent(), flowUpdates.toString().trim())
  }

  @Test
  fun `comment changes are ignored`() {
    WriteCommandAction.runWriteCommandAction(projectRule.project) {
      fixture.openFileInEditor(kotlinFile.virtualFile)
      fixture.editor.executeAndSave {
        replaceText("// INSERT CODE", "// INSERT CODE MORE COMMENT")
      }
      fixture.editor.executeAndSave {
        replaceText("LONG COMMENT", "LONGER COMMENT")
      }
      fixture.openFileInEditor(javaFile.virtualFile)
      fixture.editor.executeAndSave {
        replaceText("// INSERT METHOD", "// INSERT METHOD MORE COMMENT")
      }
    }
    assertTrue(psiCodeFileChangeDetectorService.outOfDateFiles.isEmpty())
  }
}