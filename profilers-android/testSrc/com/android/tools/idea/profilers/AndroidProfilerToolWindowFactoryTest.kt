package com.android.tools.idea.profilers

import com.android.testutils.waitForCondition
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.sdk.AndroidEnvironmentChecker
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Trace
import com.android.tools.profilers.cpu.CpuCaptureSessionArtifact
import com.android.tools.profilers.cpu.CpuProfilerStage
import com.android.tools.profilers.memory.HeapProfdSessionArtifact
import com.android.tools.profilers.memory.MainMemoryProfilerStage
import com.android.tools.profilers.taskbased.home.OpenHomeTabListener
import com.android.tools.profilers.taskbased.pastrecordings.OpenPastRecordingsTabListener
import com.android.tools.profilers.taskbased.task.OpenProfilerTaskTabListener
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.android.tools.profilers.tasks.args.singleartifact.cpu.CpuTaskArgs
import com.android.tools.profilers.tasks.args.singleartifact.memory.NativeAllocationsTaskArgs
import com.android.tools.profilers.tasks.taskhandlers.TaskModelTestUtils
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.wm.ext.LibraryDependentToolWindow
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit
import javax.swing.JPanel
import kotlin.test.fail

/**
 * Tests for [AndroidProfilerToolWindowFactory]
 */
@RunsInEdt
class AndroidProfilerToolWindowFactoryTest {
  @get:Rule
  val projectRule = ProjectRule()
  @get:Rule
  val edtRule = EdtRule()

  private val project get() = projectRule.project

  @Before
  fun setup() {
    // This test suite assumes the Task-Based UX is enabled unless otherwise specified.
    StudioFlags.PROFILER_TASK_BASED_UX.override(true)
  }

  @After
  fun cleanup() {
    StudioFlags.PROFILER_TASK_BASED_UX.clearOverride()
  }

  @Test
  fun isLibraryToolWindow() {
    val toolWindow =
      LibraryDependentToolWindow.EXTENSION_POINT_NAME.extensions.find { it.id == "Android Profiler" }
      ?: fail("Tool window not found")

    assertThat(toolWindow.librarySearchClass)
      .isEqualTo(AndroidEnvironmentChecker::class.qualifiedName)
  }

  @Test
  fun `createToolWindowContent implicitly opens the home and past recordings tab, with home tab selected`() {
    val toolWindow = ToolWindowHeadlessManagerImpl.MockToolWindow(project)
    val toolWindowFactory = AndroidProfilerToolWindowFactory()
    toolWindowFactory.init(toolWindow)
    // The following method call will call openHomeTab() and openPastRecordingsTab(), and then openHomeTab() again to reselect it.
    toolWindowFactory.createToolWindowContent(project, toolWindow)

    // Waiting for the home tab to be auto-opened via the createToolWindowContent.
    waitForCondition(5L, TimeUnit.SECONDS) {
      toolWindow.contentManager.contentCount == 2
    }

    val homeTabContent = toolWindow.contentManager.contents[0]
    assertThat(homeTabContent.tabName).isEqualTo("Home")
    assertThat(homeTabContent.displayName).isEqualTo("Home")
    assertThat(homeTabContent.toolwindowTitle).isEqualTo("Home")

    val pastRecordingsTabContent = toolWindow.contentManager.contents[1]
    assertThat(pastRecordingsTabContent.tabName).isEqualTo("Past Recordings")
    assertThat(pastRecordingsTabContent.displayName).isEqualTo("Past Recordings")
    assertThat(pastRecordingsTabContent.toolwindowTitle).isEqualTo("Past Recordings")

    // Assert that the home tab (first tab) was re-selected.
    assertThat(toolWindow.contentManager.selectedContent).isEqualTo(homeTabContent)
  }

  @Test
  fun `explicitly opening the home tab reselects existing home tab`() {
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
      toolWindow.contentManager.contentCount == 3 &&
      toolWindow.contentManager.selectedContent != null &&
      toolWindow.contentManager.selectedContent!!.displayName == "FakeTab"
    }

    // Re-open the home tab explicitly, should find already present home tab and re-select it.
    project.messageBus.syncPublisher(OpenHomeTabListener.TOPIC).openHomeTab()
    waitForCondition(5L, TimeUnit.SECONDS) {
      toolWindow.contentManager.contentCount == 3 && toolWindow.contentManager.selectedContent == homeTabContent
    }
  }

  @Test
  fun `explicitly opening the past recordings tab reselects existing past recordings tab`() {
    val toolWindow = ToolWindowHeadlessManagerImpl.MockToolWindow(project)
    val toolWindowFactory = AndroidProfilerToolWindowFactory()
    toolWindowFactory.init(toolWindow)
    toolWindowFactory.createToolWindowContent(project, toolWindow)

    // Wait for the home and past recordings tab to be created, with the home tab being selected.
    waitForCondition(5L, TimeUnit.SECONDS) {
      toolWindow.contentManager.contents.size == 2 &&
      toolWindow.contentManager.selectedContent != null &&
      toolWindow.contentManager.selectedContent!!.displayName == "Home"
    }

    // Store past recordings tab content to be used to make sure the re-opened past recordings tab is the same as the original tab.
    val pastRecordingsTabContent = toolWindow.contentManager.contents[1]!!

    val profilerToolWindow = AndroidProfilerToolWindowFactory.PROJECT_PROFILER_MAP[project]
    assertThat(profilerToolWindow).isNotNull()

    // Open a fake tab.
    profilerToolWindow!!.createNewTab(JPanel(), "FakeTab", true)
    // Make sure new tab is open and is selected.
    waitForCondition(5L, TimeUnit.SECONDS) {
      toolWindow.contentManager.contentCount == 3 &&
      toolWindow.contentManager.selectedContent != null &&
      toolWindow.contentManager.selectedContent!!.displayName == "FakeTab"
    }

    // Re-open the home tab explicitly, should find already present home tab and re-select it.
    project.messageBus.syncPublisher(OpenPastRecordingsTabListener.TOPIC).openPastRecordingsTab()
    waitForCondition(5L, TimeUnit.SECONDS) {
      toolWindow.contentManager.contentCount == 3 && toolWindow.contentManager.selectedContent == pastRecordingsTabContent
    }
  }

  @Test
  fun `opening the task tab with CPU task creates second tab with CPU stage`() {
    val toolWindow = ToolWindowHeadlessManagerImpl.MockToolWindow(project)
    val toolWindowFactory = AndroidProfilerToolWindowFactory()
    toolWindowFactory.init(toolWindow)
    toolWindowFactory.createToolWindowContent(project, toolWindow)

    // Wait for the home tab to be auto-selected via the call to openHomeTab() in AndroidProfilerToolWindow.createToolWindowContent.
    waitForCondition(5L, TimeUnit.SECONDS) {
      toolWindow.contentManager.selectedContent != null &&
      toolWindow.contentManager.selectedContent!!.displayName == "Home"
    }

    // At this point, there is a home tab implicitly opened by the call to `createToolWindowContent`.
    val profilerToolWindow = AndroidProfilerToolWindowFactory.PROJECT_PROFILER_MAP[project]
    assertThat(profilerToolWindow).isNotNull()

    // Select a device to the cpu task can retrieve and set a non-null configuration.
    profilerToolWindow!!.profilers.taskHomeTabModel.processListModel.onDeviceSelection(
      TaskModelTestUtils.createDevice("FakeDevice", Common.Device.State.ONLINE, "13", 33))
    profilerToolWindow!!.createTaskTab(ProfilerTaskType.SYSTEM_TRACE, CpuTaskArgs(false,
      CpuCaptureSessionArtifact(profilerToolWindow.profilers, Common.Session.getDefaultInstance(),
                                Common.SessionMetaData.getDefaultInstance(), Trace.TraceInfo.getDefaultInstance())))

    // Creating the task tab with a SYSTEM_TRACE task (a CPU task) should open up a second tab with non-null content, a tab name
    // of "System Trace" and the current stage should be set to the CpuProfilerStage.
    waitForCondition(5L, TimeUnit.SECONDS) {
      toolWindow.contentManager.contentCount == 2 &&
      toolWindow.contentManager.selectedContent != null &&
      toolWindow.contentManager.selectedContent!!.displayName == "System Trace"
      profilerToolWindow.profilers.stage is CpuProfilerStage
    }
  }

  @Test
  fun `opening the task tab with memory task creates second tab with memory stage`() {
    val toolWindow = ToolWindowHeadlessManagerImpl.MockToolWindow(project)
    val toolWindowFactory = AndroidProfilerToolWindowFactory()
    toolWindowFactory.init(toolWindow)
    toolWindowFactory.createToolWindowContent(project, toolWindow)

    // Wait for the home tab to be auto-selected via the call to openHomeTab() in AndroidProfilerToolWindow.createToolWindowContent.
    waitForCondition(5L, TimeUnit.SECONDS) {
      toolWindow.contentManager.selectedContent != null &&
      toolWindow.contentManager.selectedContent!!.displayName == "Home"
    }

    // At this point, there is a home tab implicitly opened by the call to `createToolWindowContent`.

    val profilerToolWindow = AndroidProfilerToolWindowFactory.PROJECT_PROFILER_MAP[project]
    assertThat(profilerToolWindow).isNotNull()

    profilerToolWindow!!.createTaskTab(ProfilerTaskType.NATIVE_ALLOCATIONS, NativeAllocationsTaskArgs(false,
      HeapProfdSessionArtifact(profilerToolWindow.profilers, Common.Session.getDefaultInstance(),
                               Common.SessionMetaData.getDefaultInstance(), Trace.TraceInfo.getDefaultInstance())))

    // Creating the task tab with a NATIVE_ALLOCATIONS task (a memory task) should open up a second tab with non-null content, a tab name
    // of "Native Allocations" and the current stage should be set to the MainMemoryProfilerStage.
    waitForCondition(5L, TimeUnit.SECONDS) {
      toolWindow.contentManager.contentCount == 2 &&
      toolWindow.contentManager.selectedContent != null &&
      toolWindow.contentManager.selectedContent!!.displayName == "Native Allocations"
      profilerToolWindow.profilers.stage is MainMemoryProfilerStage
    }
  }

  @Test
  fun `call to selectTaskTab re-selects existing task tab`() {
    val toolWindow = ToolWindowHeadlessManagerImpl.MockToolWindow(project)
    val toolWindowFactory = AndroidProfilerToolWindowFactory()
    toolWindowFactory.init(toolWindow)
    toolWindowFactory.createToolWindowContent(project, toolWindow)

    // Wait for the home tab to be auto-selected via the call to openHomeTab() in AndroidProfilerToolWindow.createToolWindowContent.
    waitForCondition(5L, TimeUnit.SECONDS) {
      toolWindow.contentManager.selectedContent != null &&
      toolWindow.contentManager.selectedContent!!.displayName == "Home"
    }

    val profilerToolWindow = AndroidProfilerToolWindowFactory.PROJECT_PROFILER_MAP[project]
    assertThat(profilerToolWindow).isNotNull()

    // Select a device to the cpu task can retrieve and set a non-null configuration.
    profilerToolWindow!!.profilers.taskHomeTabModel.processListModel.onDeviceSelection(
      TaskModelTestUtils.createDevice("FakeDevice", Common.Device.State.ONLINE, "13", 33))
    // Create a task tab.
    profilerToolWindow!!.createTaskTab(ProfilerTaskType.SYSTEM_TRACE, CpuTaskArgs(false,
      CpuCaptureSessionArtifact(profilerToolWindow.profilers, Common.Session.getDefaultInstance(),
                                Common.SessionMetaData.getDefaultInstance(), Trace.TraceInfo.getDefaultInstance())))

    // Make sure new tab is open and is selected.
    waitForCondition(5L, TimeUnit.SECONDS) {
      toolWindow.contentManager.contentCount == 3 &&
      toolWindow.contentManager.selectedContent != null &&
      toolWindow.contentManager.selectedContent!!.displayName == "System Trace"
    }

    // Store task tab content to be used to make sure the re-opened task tab is the same as the original task tab.
    val taskTabContent = toolWindow.contentManager.contents[2]

    // Open the home tab again.
    profilerToolWindow.openHomeTab()
    waitForCondition(5L, TimeUnit.SECONDS) {
      toolWindow.contentManager.selectedContent != null &&
      toolWindow.contentManager.selectedContent!!.displayName == "Home"
    }

    // Re-open the task tab explicitly, should find already present task tab and re-select it.
    project.messageBus.syncPublisher(OpenProfilerTaskTabListener.TOPIC).openProfilerTaskTab()
    waitForCondition(5L, TimeUnit.SECONDS) {
      toolWindow.contentManager.contentCount == 3 && toolWindow.contentManager.selectedContent == taskTabContent
    }
  }
}