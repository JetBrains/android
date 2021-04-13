package com.android.tools.idea.editors.literals

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.executeAndSave
import com.android.tools.idea.testing.replaceText
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.util.ui.UIUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

internal class LiveLiteralsServiceTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()
  private val project: Project
    get() = projectRule.project
  private val rootDisposable: Disposable
    get() = projectRule.fixture.testRootDisposable
  lateinit var file1: PsiFile
  lateinit var file2: PsiFile

  private var isAvailable = false

  private fun getTestLiveLiteralsService(): LiveLiteralsService =
    LiveLiteralsService.getInstanceForTest(project, rootDisposable).also {
      it.addAvailabilityListener(rootDisposable) { available -> isAvailable = available }
    }

  @Before
  fun setup() {
    StudioFlags.COMPOSE_LIVE_LITERALS.override(true)
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
    val liveLiteralsService = getTestLiveLiteralsService()
    assertTrue(liveLiteralsService.allConstants().isEmpty())
    assertFalse(isAvailable)
    ApplicationManager.getApplication().invokeAndWait {
      // We run it and wait to ensure this has executed before the next assert
      liveLiteralsService.liveLiteralsMonitorStarted("TestDevice", LiveLiteralsMonitorHandler.DeviceType.PREVIEW)
    }
    assertTrue(isAvailable)
    assertEquals(9, liveLiteralsService.allConstants().size)
  }

  @Test
  fun `check that constants are registered after a new editor is opened`() {
    val liveLiteralsService = getTestLiveLiteralsService()
    assertTrue(liveLiteralsService.allConstants().isEmpty())
    assertFalse(isAvailable)
    ApplicationManager.getApplication().invokeAndWait {
      // We run it and wait to ensure this has executed before the next assert
      liveLiteralsService.liveLiteralsMonitorStarted("TestDevice", LiveLiteralsMonitorHandler.DeviceType.PREVIEW)
    }
    assertTrue(isAvailable)
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

  @Test
  fun `listener notification`() {
    // Setup
    val latch = CountDownLatch(1)
    val modifications = mutableListOf<LiteralReference>()
    val liveLiteralsService = getTestLiveLiteralsService()
    liveLiteralsService.addOnLiteralsChangedListener(projectRule.fixture.testRootDisposable) {
      modifications.addAll(it)
      latch.countDown()
    }
    projectRule.fixture.configureFromExistingVirtualFile(file1.virtualFile)
    ApplicationManager.getApplication().invokeAndWait {
      // We run it and wait to ensure this has executed before the next assert
      liveLiteralsService.liveLiteralsMonitorStarted("TestDevice", LiveLiteralsMonitorHandler.DeviceType.PREVIEW)
    }
    assertFalse(liveLiteralsService.allConstants().isEmpty())

    // Run test
    projectRule.fixture.editor.executeAndSave {
      replaceText("ClassHello", "ClassBye")
      replaceText("999", "555")
    }

    // Wait for the modification to be notified
    latch.await(5, TimeUnit.SECONDS)
    assertEquals(2, modifications.size)
  }

  @Test
  fun `listener is only called when live literals are available`() {
    var changeListenerCalls = 0
    val liveLiteralsService = getTestLiveLiteralsService()
    liveLiteralsService.addOnLiteralsChangedListener(projectRule.fixture.testRootDisposable) {
      changeListenerCalls++
    }
    assertTrue(liveLiteralsService.allConstants().isEmpty())
    assertFalse(isAvailable)
    assertEquals(0, changeListenerCalls)
    ApplicationManager.getApplication().invokeAndWait {
      // We run it and wait to ensure this has executed before the next assert
      liveLiteralsService.liveLiteralsMonitorStarted("TestDevice", LiveLiteralsMonitorHandler.DeviceType.PREVIEW)
    }
    assertTrue(isAvailable)
    assertEquals(0, changeListenerCalls)
    assertTrue(liveLiteralsService.allConstants().isEmpty())
    projectRule.fixture.configureFromExistingVirtualFile(file1.virtualFile)

    ApplicationManager.getApplication().invokeAndWait {
      // We run it and wait to ensure this has executed before the next assert
      liveLiteralsService.liveLiteralsMonitorStopped("TestDevice")
    }
    assertFalse(isAvailable)
    assertEquals(0, changeListenerCalls)
  }
}