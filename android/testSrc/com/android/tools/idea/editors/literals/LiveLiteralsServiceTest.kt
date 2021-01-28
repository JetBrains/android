package com.android.tools.idea.editors.literals

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.util.ui.UIUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

internal class LiveLiteralsServiceTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()
  lateinit var project: Project
  lateinit var file1: PsiFile
  lateinit var  file2: PsiFile

  @Before
  fun setup() {
    StudioFlags.COMPOSE_LIVE_LITERALS.override(true)
    project = projectRule.project
    file1 = projectRule.fixture.addFileToProject("src/main/java/com/literals/test/Test.kt", """
      package com.literals.test

      fun method() {
        val a = "Hello"
        val b = 2
        val c = true
      }

      class Test {
        val a = "ClassHello"
        val b = 2

        fun methodWithParameters(a: Int, b: Float) {
          println("${'$'}a ${'$'}b")
        }

        fun method() {
          val c = 123

          method(9, 999f)
        }
      }
    """.trimIndent())
    file2 = projectRule.fixture.addFileToProject("src/main/java/com/literals/test/Test2.kt", """
      package com.literals.test

      fun method2() {
        val a = 3.0
      }
    """.trimIndent())
  }

  @Test
  fun `check that already open editors register constants`() {
    projectRule.fixture.configureFromExistingVirtualFile(file1.virtualFile)
    val liveLiteralsService = LiveLiteralsService.getInstance(project)
    assertTrue(liveLiteralsService.allConstants().isEmpty())
    liveLiteralsService.isEnabled = true
    assertEquals(9, liveLiteralsService.allConstants().size)
  }

  @Test
  fun `check that constants are registered after a new editor is opened`() {
    val liveLiteralsService = LiveLiteralsService.getInstance(project)
    assertTrue(liveLiteralsService.allConstants().isEmpty())
    liveLiteralsService.isEnabled = true
    assertTrue(liveLiteralsService.allConstants().isEmpty())
    projectRule.fixture.configureFromExistingVirtualFile(file1.virtualFile)
    assertEquals(9, liveLiteralsService.allConstants().size)

    // Open second editor
    projectRule.fixture.configureFromExistingVirtualFile(file2.virtualFile)
    assertEquals(10, liveLiteralsService.allConstants().size)

    // Close the second editor
    UIUtil.invokeAndWaitIfNeeded(Runnable { FileEditorManager.getInstance(project).closeFile(file2.virtualFile) })
    assertEquals(9, liveLiteralsService.allConstants().size)
  }
}