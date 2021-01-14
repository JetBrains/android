package com.android.tools.idea.gradle.util

import com.android.tools.idea.gradle.project.build.BuildContext
import com.android.tools.idea.gradle.project.build.BuildStatus
import com.android.tools.idea.gradle.project.build.GradleBuildState
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestCase
import com.intellij.util.ui.UIUtil
import junit.framework.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import java.lang.StringBuilder

private class TestBuildListener: BuildListener {
  private val log = StringBuilder()

  override fun buildStarted() {
    log.append("Build Started\n")
  }

  override fun buildSucceeded() {
    log.append("Build Succeeded\n")
  }

  override fun buildFailed() {
    log.append("Build Failed\n")
  }

  fun getLog(): String = log.toString().trimEnd()
}

class BuildListenerTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()
  private val project: Project
    get() = projectRule.project
  private lateinit var buildContext: BuildContext
  private lateinit var buildState: GradleBuildState

  private val buildListener: TestBuildListener = TestBuildListener()

  @Before
  fun setUp() {
    setupBuildListener(project, buildListener, projectRule.fixture.testRootDisposable)
    buildState = GradleBuildState.getInstance(project)
  }

  @After
  fun tearDown() {
    buildState.clear()
  }

  private fun createContext(buildMode: BuildMode): BuildContext {
    return BuildContext(project, listOf("task1", "task2"), buildMode)
  }

  private fun processEvents() = UIUtil.invokeAndWaitIfNeeded(Runnable {
    UIUtil.dispatchAllInvocationEvents()
  })

  @Test
  fun testBuildSuccessful() {
    buildContext = createContext(BuildMode.ASSEMBLE)

    buildState.buildStarted(buildContext)
    processEvents()
    assertEquals("Build Started", buildListener.getLog())

    buildState.buildFinished(BuildStatus.SUCCESS)
    processEvents()
    assertEquals("""
      Build Started
      Build Succeeded
    """.trimIndent(), buildListener.getLog())
  }

  @Test
  fun testBuildFailed() {
    buildContext = createContext(BuildMode.ASSEMBLE)

    buildState.buildStarted(buildContext)
    buildState.buildFinished(BuildStatus.FAILED)
    processEvents()
    assertEquals("""
      Build Started
      Build Failed
    """.trimIndent(), buildListener.getLog())
  }

  @Test
  fun testBuildCancelled() {
    buildContext = createContext(BuildMode.ASSEMBLE)

    buildState.buildStarted(buildContext)
    buildState.buildFinished(BuildStatus.FAILED)
    processEvents()
    assertEquals("""
      Build Started
      Build Failed
    """.trimIndent(), buildListener.getLog())
  }

  @Test
  fun testCleanBuild() {
    buildContext = createContext(BuildMode.CLEAN)
    buildState.buildStarted(buildContext)
    processEvents()
    assertEquals("", buildListener.getLog())

    buildState.buildFinished(BuildStatus.SUCCESS)
    processEvents()
    assertEquals("Build Failed", buildListener.getLog())
  }
}