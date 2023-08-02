package com.android.tools.idea.profilers

import com.android.testutils.waitForCondition
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.sdk.AndroidOrApkFacetChecker
import com.android.tools.profilers.taskbased.home.OpenHomeTabListener
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.wm.ext.LibraryDependentToolWindow
import com.intellij.testFramework.ProjectRule
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import org.junit.Rule
import org.junit.Test
import kotlin.test.fail

/**
 * Tests for [AndroidProfilerToolWindowFactory]
 */
class AndroidProfilerToolWindowFactoryTest {
  @get:Rule
  val projectRule = ProjectRule()

  private val project get() = projectRule.project

  @Test
  fun isLibraryToolWindow() {
    val toolWindow =
      LibraryDependentToolWindow.EXTENSION_POINT_NAME.extensions.find { it.id == "Android Profiler" }
      ?: fail("Tool window not found")

    assertThat(toolWindow.librarySearchClass)
      .isEqualTo(AndroidOrApkFacetChecker::class.qualifiedName)
  }

  @Test
  fun testOpeningTheHomeTab() {
    StudioFlags.PROFILER_TASK_BASED_UX.override(true);

    val toolWindow = ToolWindowHeadlessManagerImpl.MockToolWindow(project)
    val toolWindowFactory = AndroidProfilerToolWindowFactory()
    toolWindowFactory.init(toolWindow)
    toolWindowFactory.createToolWindowContent(project, toolWindow)

    project.messageBus.syncPublisher(OpenHomeTabListener.TOPIC).openHomeTab()

    waitForCondition(5L, java.util.concurrent.TimeUnit.SECONDS) { toolWindow.contentManager.contentCount == 1 }

    val content = toolWindow.contentManager.contents.first()
    assertThat(content.tabName).isEqualTo("Home")
    assertThat(content.displayName).isEqualTo("Home")
    assertThat(content.toolwindowTitle).isEqualTo("Home")

    StudioFlags.PROFILER_TASK_BASED_UX.clearOverride()
  }
}