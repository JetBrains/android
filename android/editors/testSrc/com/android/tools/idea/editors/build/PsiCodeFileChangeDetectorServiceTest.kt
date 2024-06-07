package com.android.tools.idea.editors.build

import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.executeAndSave
import com.android.tools.idea.testing.insertText
import com.android.tools.idea.testing.replaceText
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import org.jetbrains.kotlin.psi.KtBlockCodeFragment
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.scripting.definitions.runReadAction
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PsiCodeFileChangeDetectorServiceTest {
  @get:Rule val projectRule = AndroidProjectRule.inMemory()
  private val fixture: CodeInsightTestFixture
    get() = projectRule.fixture

  private lateinit var myPsiCodeFileOutOfDateStatusReporter: PsiCodeFileOutOfDateStatusReporter
  private lateinit var myPsiCodeFileUpToDateStatusRecorder: PsiCodeFileUpToDateStatusRecorder
  private lateinit var kotlinFile: PsiFile
  private lateinit var secondKotlinFile: PsiFile
  private lateinit var javaFile: PsiFile
  private lateinit var xmlFile: PsiFile

  @Before
  fun setUp() {
    myPsiCodeFileOutOfDateStatusReporter =
      PsiCodeFileOutOfDateStatusReporter.getInstance(projectRule.project)

    myPsiCodeFileUpToDateStatusRecorder =
      PsiCodeFileUpToDateStatusRecorder.getInstance(projectRule.project)

    kotlinFile =
      fixture.addFileToProject(
        "src/a/declarations.kt",
        // language=kotlin
        """
        package a

        annotation class Annotation(value: String)
      """
          .trimIndent()
      )

    kotlinFile =
      fixture.addFileToProject(
        "src/a/test.kt",
        // language=kotlin
        """
        package a

        @Annotation(value = "AnnotationContent")
        fun test() {
          // INSERT CODE

          /*
           * LONG COMMENT
           */
        }

        // Method to simulate a top level theme declaration
        private fun lightColorSchema(primary: Int, secondary: Int): Int {
          primary + secondary
        }

        private val LightColorScheme = lightColorScheme(
            primary = 1,
            secondary = 2,
        )
      """
          .trimIndent()
      )
    secondKotlinFile =
      fixture.addFileToProject(
        "src/b/otherFile.kt",
        // language=kotlin
        """
        package b

        fun otherTest() {
          // INSERT CODE
        }
      """
          .trimIndent()
      )
    javaFile =
      fixture.addFileToProject(
        "src/c/MyClass.java",
        // language=java
        """
        package c;

        class MyClass {
            // INSERT METHOD
        }
      """
          .trimIndent()
      )
    xmlFile =
      fixture.addFileToProject(
        "res/test.xml",
        // language=xml
        """
        <hello>Hello<!--INSERT XML--></hello>
      """
          .trimIndent()
      )
  }

  @Test
  fun `new service has no changes`() {
    // New files are not part of the existing build so they can not immediately be used for
    // previews. Because of this, we do not want
    // to mark them as out of date until the user modifies them.

    assertThat(myPsiCodeFileOutOfDateStatusReporter.outOfDateFiles).isEmpty()
  }

  @Test
  fun `file changes are detected in relevant languages`() = runBlocking {
    val flowUpdates: MutableList<List<String>> = mutableListOf()

    // Make sure that each change is collected before we move onto the next one. Otherwise, some
    // changes are coalesced and may be skipped.
    val semaphore = Semaphore(permits = 1, acquiredPermits = 1)

    val flowJob =
      launch(workerThread) {
        myPsiCodeFileOutOfDateStatusReporter.fileUpdatesFlow
          .take(6) // We expect 6 changes
          .collect {
            flowUpdates.add(it.map(PsiFile::toString))
            semaphore.release()
          }
      }

    semaphore.acquire()
    WriteCommandAction.runWriteCommandAction(projectRule.project) {
      fixture.openFileInEditor(kotlinFile.virtualFile)
      fixture.editor.executeAndSave {
        insertText(
          """

          fun hello() {}

        """
            .trimIndent()
        )
      }
    }
    assertThat(myPsiCodeFileOutOfDateStatusReporter.outOfDateFiles.map { it.name })
      .containsExactly("test.kt")

    semaphore.acquire()
    myPsiCodeFileUpToDateStatusRecorder.markAsUpToDate(setOf(kotlinFile))
    assertThat(myPsiCodeFileOutOfDateStatusReporter.outOfDateFiles).isEmpty()

    // The XML file should not trigger an update, since only Java and Kotlin files are tracked.
    // Don't acquire the semaphore, since it won't
    // be released above. (And if it does trigger an update, verifications below should catch it.)
    WriteCommandAction.runWriteCommandAction(projectRule.project) {
      fixture.openFileInEditor(xmlFile.virtualFile)
      fixture.editor.executeAndSave { replaceText("<!--INSERT XML-->", "<a></a>") }
    }

    semaphore.acquire()
    WriteCommandAction.runWriteCommandAction(projectRule.project) {
      fixture.openFileInEditor(kotlinFile.virtualFile)
      fixture.editor.executeAndSave { insertText("\nfun newMethod() {}") }
    }

    semaphore.acquire()
    WriteCommandAction.runWriteCommandAction(projectRule.project) {
      fixture.openFileInEditor(secondKotlinFile.virtualFile)
      fixture.editor.executeAndSave { insertText("\nfun newMethod() {}") }
    }

    semaphore.acquire()
    WriteCommandAction.runWriteCommandAction(projectRule.project) {
      fixture.openFileInEditor(javaFile.virtualFile)
      fixture.editor.executeAndSave { replaceText("// INSERT METHOD", "public void test() {}") }
    }

    assertThat(myPsiCodeFileOutOfDateStatusReporter.outOfDateFiles.map { it.name })
      .containsExactly("MyClass.java", "otherFile.kt", "test.kt")

    // Wait for all the changes to have been collected
    flowJob.join()
    assertThat(flowUpdates)
      .containsExactly(
        listOf<String>(),
        listOf("KtFile: test.kt"),
        listOf<List<String>>(),
        listOf("KtFile: test.kt"),
        listOf("KtFile: test.kt", "KtFile: otherFile.kt"),
        listOf("KtFile: test.kt", "KtFile: otherFile.kt", "PsiJavaFile:MyClass.java"),
      )
      .inOrder()
  }

  @Test
  fun `comment changes are ignored`() {
    WriteCommandAction.runWriteCommandAction(projectRule.project) {
      fixture.openFileInEditor(kotlinFile.virtualFile)
      fixture.editor.executeAndSave { replaceText("// INSERT CODE", "// INSERT CODE MORE COMMENT") }
      fixture.editor.executeAndSave { replaceText("LONG COMMENT", "LONGER COMMENT") }
      fixture.openFileInEditor(javaFile.virtualFile)
      fixture.editor.executeAndSave {
        replaceText("// INSERT METHOD", "// INSERT METHOD MORE COMMENT")
      }
    }
    assertThat(myPsiCodeFileOutOfDateStatusReporter.outOfDateFiles).isEmpty()
  }

  @Test
  fun `kotlin declaration change`() {
    WriteCommandAction.runWriteCommandAction(projectRule.project) {
      fixture.openFileInEditor(kotlinFile.virtualFile)
      fixture.editor.executeAndSave { replaceText("primary = 1", "primary = 2") }
    }
    assertThat(myPsiCodeFileOutOfDateStatusReporter.outOfDateFiles.map { it.name })
      .containsExactly("test.kt")
  }

  @Test
  fun `kotlin annotation does not invalidate the file`() {
    WriteCommandAction.runWriteCommandAction(projectRule.project) {
      fixture.openFileInEditor(kotlinFile.virtualFile)
      fixture.editor.executeAndSave { replaceText("AnnotationContent", "AnnotationContent2") }
    }
    assertThat(myPsiCodeFileOutOfDateStatusReporter.outOfDateFiles).isEmpty()
  }

  @Test
  fun `virtual fragment changes are ignored`() {
    val psiFactory = KtPsiFactory(projectRule.project)
    val file = runReadAction {
      KtBlockCodeFragment(projectRule.project, "fragment.kt", """
        fun test() {}
      """, "", kotlinFile)
    }

    // Modify the fragment to generate a PSI change event that should be ignored.
    val methodToAdd = runReadAction { psiFactory.createFunction("fun second() {}") }
    WriteCommandAction.runWriteCommandAction(projectRule.project) { file.add(methodToAdd) }

    assertThat(myPsiCodeFileOutOfDateStatusReporter.outOfDateFiles).isEmpty()
  }
}
