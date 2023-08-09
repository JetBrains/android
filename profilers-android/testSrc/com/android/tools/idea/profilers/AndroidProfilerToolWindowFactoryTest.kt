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
import java.util.concurrent.TimeUnit
import javax.swing.JPanel
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
  fun `createToolWindowContent implicitly opens the home tab`() {
    StudioFlags.PROFILER_TASK_BASED_UX.override(true);

    val toolWindow = ToolWindowHeadlessManagerImpl.MockToolWindow(project)
    val toolWindowFactory = AndroidProfilerToolWindowFactory()
    toolWindowFactory.init(toolWindow)
    // The following method call will call openHomeTab().
    toolWindowFactory.createToolWindowContent(project, toolWindow)

    // Waiting for the home tab to be auto-opened via the createToolWindowContent.
    waitForCondition(5L, TimeUnit.SECONDS) {
      toolWindow.contentManager.contentCount == 1
    }

    val homeTabContent = toolWindow.contentManager.contents.first()!!
    assertThat(homeTabContent.tabName).isEqualTo("Home")
    assertThat(homeTabContent.displayName).isEqualTo("Home")
    assertThat(homeTabContent.toolwindowTitle).isEqualTo("Home")

    StudioFlags.PROFILER_TASK_BASED_UX.clearOverride()
  }

  @Test
  fun `explicitly opening the home tab reselects existing home tab`() {
    StudioFlags.PROFILER_TASK_BASED_UX.override(true);

    val toolWindow = ToolWindowHeadlessManagerImpl.MockToolWindow(project)
    val toolWindowFactory = AndroidProfilerToolWindowFactory()
    toolWindowFactory.init(toolWindow)
    toolWindowFactory.createToolWindowContent(project, toolWindow)

    // Wait for the home tab to be auto-selected via the call to openHomeTab() in AndroidProfilerToolWindow.createToolWindowContent.
    waitForCondition(5L, TimeUnit.SECONDS) {
      toolWindow.contentManager.selectedContent != null &&
      toolWindow.contentManager.selectedContent!!.displayName == "Home"
    }

    // Store home tab content to be used to make sure the re-opened home tab is the same as the original home tab.
    val homeTabContent = toolWindow.contentManager.contents[0]!!

    val profilerToolWindow = AndroidProfilerToolWindowFactory.PROJECT_PROFILER_MAP[project]
    assertThat(profilerToolWindow).isNotNull()

    // Open a fake tab.
    profilerToolWindow!!.createNewTab(JPanel(), "FakeTab", true)
    // Make sure new tab is open and is selected.
    waitForCondition(5L, TimeUnit.SECONDS) {
      toolWindow.contentManager.contentCount == 2 &&
      toolWindow.contentManager.selectedContent != null &&
      toolWindow.contentManager.selectedContent!!.displayName == "FakeTab"
    }

    // Re-open the home tab explicitly, should find already present home tab and re-select it.
    project.messageBus.syncPublisher(OpenHomeTabListener.TOPIC).openHomeTab()
    waitForCondition(5L, TimeUnit.SECONDS) {
      toolWindow.contentManager.contentCount == 2 && toolWindow.contentManager.selectedContent == homeTabContent
    }

    StudioFlags.PROFILER_TASK_BASED_UX.clearOverride()
  }
}