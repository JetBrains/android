package com.android.tools.idea.gradle.util

import com.android.tools.idea.gradle.project.build.BuildContext
import com.android.tools.idea.gradle.project.build.BuildStatus
import com.android.tools.idea.gradle.project.build.GradleBuildState
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.UIUtil
import junit.framework.TestCase.assertEquals
import org.junit.Rule
import org.junit.Test

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

  private fun setupBuildListener(buildMode: BuildMode): Triple<GradleBuildState, BuildContext, TestBuildListener> {
    // Make sure there are no pending call before setting up the listener
    processEvents()
    val listener = TestBuildListener()
    setupBuildListener(project, listener, projectRule.fixture.testRootDisposable)
    return Triple(GradleBuildState.getInstance(project),
                  BuildContext(project, listOf("task1", "task2"), buildMode),
                  listener)
  }

  private fun processEvents() = UIUtil.invokeAndWaitIfNeeded(Runnable {
    UIUtil.dispatchAllInvocationEvents()
  })

  @Test
  fun testBuildSuccessful() {
    val (buildState, buildContext, buildListener) = setupBuildListener(BuildMode.ASSEMBLE)

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
    val (buildState, buildContext, buildListener) = setupBuildListener(BuildMode.ASSEMBLE)

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
    val (buildState, buildContext, buildListener) = setupBuildListener(BuildMode.ASSEMBLE)

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
    val (buildState, buildContext, buildListener) = setupBuildListener(BuildMode.CLEAN)
    buildState.buildStarted(buildContext)
    processEvents()
    assertEquals("", buildListener.getLog())

    buildState.buildFinished(BuildStatus.SUCCESS)
    processEvents()
    assertEquals("Build Failed", buildListener.getLog())
  }

  /**
   * Regression test for b/177355531. Disposing the first build listener would remove all listeners.
   */
  @Test
  fun testRemoveSecondListener() {
    val secondListener = object : BuildListener {
      override fun buildSucceeded() {}
    }
    val secondDisposable = Disposer.newDisposable()
    setupBuildListener(project, secondListener, secondDisposable)

    Disposer.dispose(secondDisposable)

    val (buildState, buildContext, buildListener) = setupBuildListener(BuildMode.ASSEMBLE)
    buildState.buildStarted(buildContext)
    buildState.buildFinished(BuildStatus.SUCCESS)
    processEvents()
    assertEquals("""
      Build Started
      Build Succeeded
    """.trimIndent(), buildListener.getLog())
  }
}