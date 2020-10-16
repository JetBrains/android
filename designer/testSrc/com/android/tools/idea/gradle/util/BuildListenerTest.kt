package com.android.tools.idea.gradle.util

import com.android.tools.idea.gradle.project.build.BuildContext
import com.android.tools.idea.gradle.project.build.BuildStatus
import com.android.tools.idea.gradle.project.build.GradleBuildState
import com.intellij.testFramework.PlatformTestCase
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

class BuildListenerTest : PlatformTestCase() {
  private lateinit var buildContext: BuildContext
  private lateinit var buildState: GradleBuildState

  @Mock
  private lateinit var buildListener: BuildListener

  override fun setUp() {
    super.setUp()
    MockitoAnnotations.initMocks(this)

    setupBuildListener(project, buildListener, project)
    buildState = GradleBuildState.getInstance(project)
  }

  override fun tearDown() {
    try {
      buildState.clear()
    }
    finally {
      super.tearDown()
    }
  }

  private fun createContext(buildMode: BuildMode): BuildContext {
    return BuildContext(project, listOf("task1", "task2"), buildMode)
  }

  fun testBuildSuccessful() {
    buildContext = createContext(BuildMode.ASSEMBLE)

    buildState.buildStarted(buildContext)

    Mockito.verify(buildListener).buildStarted()

    buildState.buildFinished(BuildStatus.SUCCESS)

    Mockito.verify(buildListener).buildSucceeded()
  }

  fun testBuildFailed() {
    buildContext = createContext(BuildMode.ASSEMBLE)

    buildState.buildStarted(buildContext)
    buildState.buildFinished(BuildStatus.FAILED)

    Mockito.verify(buildListener).buildFailed()
  }

  fun testBuildCancelled() {
    buildContext = createContext(BuildMode.ASSEMBLE)

    buildState.buildStarted(buildContext)

    buildState.buildFinished(BuildStatus.FAILED)

    Mockito.verify(buildListener).buildFailed()
  }

  fun testCleanBuild() {
    buildContext = createContext(BuildMode.CLEAN)

    buildState.buildStarted(buildContext)

    Mockito.verify(buildListener, Mockito.never()).buildStarted()

    buildState.buildFinished(BuildStatus.SUCCESS)

    Mockito.verify(buildListener, Mockito.never()).buildSucceeded()
    Mockito.verify(buildListener).buildFailed()
  }
}