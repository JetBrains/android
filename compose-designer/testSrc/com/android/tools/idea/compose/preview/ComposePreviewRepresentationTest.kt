/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.compose.preview

import com.android.testutils.delayUntilCondition
import com.android.testutils.waitForCondition
import com.android.tools.analytics.AnalyticsSettings
import com.android.tools.idea.common.error.DesignerCommonIssuePanel
import com.android.tools.idea.common.error.SharedIssuePanelProvider
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.DesignSurfaceListener
import com.android.tools.idea.common.surface.getDesignSurface
import com.android.tools.idea.compose.ComposeProjectRule
import com.android.tools.idea.compose.UiCheckModeFilter
import com.android.tools.idea.compose.preview.actions.ReRunUiCheckModeAction
import com.android.tools.idea.compose.preview.actions.UiCheckReopenTabAction
import com.android.tools.idea.compose.preview.gallery.ComposeGalleryMode
import com.android.tools.idea.compose.preview.util.previewElement
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.concurrency.asCollection
import com.android.tools.idea.concurrency.awaitStatus
import com.android.tools.idea.editors.build.ProjectStatus
import com.android.tools.idea.editors.fast.FastPreviewManager
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.actions.getPreviewManager
import com.android.tools.idea.preview.analytics.PreviewRefreshTracker
import com.android.tools.idea.preview.analytics.PreviewRefreshTrackerForTest
import com.android.tools.idea.preview.groups.PreviewGroupManager
import com.android.tools.idea.preview.modes.GRID_LAYOUT_MANAGER_OPTIONS
import com.android.tools.idea.preview.modes.LIST_LAYOUT_MANAGER_OPTION
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.projectsystem.TestProjectSystem
import com.android.tools.idea.testing.addFileToProjectAndInvalidate
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreferredVisibility
import com.android.tools.idea.uibuilder.editor.multirepresentation.TextEditorWithMultiRepresentationPreview
import com.android.tools.idea.uibuilder.editor.multirepresentation.sourcecode.SourceCodeEditorProvider
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.NlDesignSurfacePositionableContentLayoutManager
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintService
import com.android.tools.idea.util.TestToolWindowManager
import com.google.common.base.Preconditions.checkState
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.PreviewRefreshEvent
import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewToolWindowUtils
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.assertInstanceOf
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CountDownLatch
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.android.uipreview.ModuleClassLoaderOverlays
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito

internal class TestComposePreviewView(override val mainSurface: NlDesignSurface) :
  ComposePreviewView {
  override val component: JComponent
    get() = JPanel()

  override var bottomPanel: JComponent? = null
  override val isMessageBeingDisplayed: Boolean = false
  override var hasContent: Boolean = true
  override var hasRendered: Boolean = true
  override var galleryMode: ComposeGalleryMode? = null

  val refreshCompletedListeners: MutableList<() -> Unit> = mutableListOf()

  override fun updateNotifications(parentEditor: FileEditor) {}

  override fun updateVisibilityAndNotifications() {}

  override fun updateProgress(message: String) {}

  override fun onRefreshCancelledByTheUser() {}

  override fun onRefreshCompleted() {
    refreshCompletedListeners.forEach { it.invoke() }
  }

  override fun onLayoutlibNativeCrash(onLayoutlibReEnable: () -> Unit) {}
}

class ComposePreviewRepresentationTest {
  private val logger = Logger.getInstance(ComposePreviewRepresentationTest::class.java)

  @get:Rule val projectRule = ComposeProjectRule()
  private val project
    get() = projectRule.project

  private val fixture
    get() = projectRule.fixture

  @Before
  fun setup() {
    logger.setLevel(LogLevel.ALL)
    Logger.getInstance(ComposePreviewRepresentation::class.java).setLevel(LogLevel.ALL)
    Logger.getInstance(FastPreviewManager::class.java).setLevel(LogLevel.ALL)
    Logger.getInstance(ProjectStatus::class.java).setLevel(LogLevel.ALL)
    logger.info("setup")
    val testProjectSystem = TestProjectSystem(project).apply { usesCompose = true }
    runInEdtAndWait { testProjectSystem.useInTests() }
    logger.info("setup complete")
    project.replaceService(
      ToolWindowManager::class.java,
      TestToolWindowManager(project),
      fixture.testRootDisposable,
    )
    ToolWindowManager.getInstance(project)
      .registerToolWindow(RegisterToolWindowTask(ProblemsView.ID))

    // Create VisualLintService early to avoid it being created at the time of project disposal
    VisualLintService.getInstance(project)
  }

  @After
  fun tearDown() {
    StudioFlags.NELE_ATF_FOR_COMPOSE.clearOverride()
    StudioFlags.NELE_COMPOSE_UI_CHECK_COLORBLIND_MODE.clearOverride()
  }

  @Test
  fun testPreviewInitialization() {
    val wrapper = ComposePreviewRepresentationTestContext(fixture, logger)
    runBlocking(workerThread) {
      wrapper.init()
      val preview = wrapper.createPreviewAndCompile()

      wrapper.mainSurface.models.forEach {
        assertTrue(preview.navigationHandler.defaultNavigationMap.contains(it))
      }

      assertThat(preview.composePreviewFlowManager.availableGroupsFlow.value.map { it.displayName })
        .containsExactly("groupA")

      val status = preview.status()
      val debugStatus = preview.debugStatusForTesting()
      assertFalse(debugStatus.toString(), status.isOutOfDate)
      // Ensure the only warning message is the missing Android SDK message
      assertTrue(
        debugStatus.renderResult
          .flatMap { it.logger.messages }
          .none { !it.html.contains("No Android SDK found.") }
      )
      preview.onDeactivate()
    }
  }

  @Test
  fun testPreviewRefreshMetricsAreTracked() {
    val wrapper = ComposePreviewRepresentationTestContext(fixture, logger)
    runBlocking(workerThread) {
      wrapper.init()
      try {
        AnalyticsSettings.optedIn = true
        var refreshTrackerFailed = false
        var successEventCount = 0
        val refreshTracker = PreviewRefreshTrackerForTest {
          if (
            it.result != PreviewRefreshEvent.RefreshResult.SUCCESS ||
              it.previewRendersList.isEmpty()
          ) {
            return@PreviewRefreshTrackerForTest
          }
          try {
            assertTrue(it.hasInQueueTimeMillis())
            assertTrue(it.hasRefreshTimeMillis())
            assertTrue(it.hasType())
            assertTrue(it.hasResult())
            assertTrue(it.hasPreviewsCount())
            assertTrue(it.hasPreviewsToRefresh())
            assertTrue(it.previewRendersList.isNotEmpty())
            assertTrue(
              it.previewRendersList.all { render ->
                render.hasResult()
                render.hasRenderTimeMillis()
                render.hasRenderQuality()
                render.hasInflate()
              }
            )
            successEventCount++
          } catch (t: Throwable) {
            refreshTrackerFailed = true
          }
        }
        PreviewRefreshTracker.setInstanceForTest(wrapper.mainSurface, refreshTracker)
        val preview = wrapper.createPreviewAndCompile()

        waitForCondition(5.seconds) { successEventCount > 0 }
        assertFalse(refreshTrackerFailed)
        preview.onDeactivate()
      } finally {
        PreviewRefreshTracker.cleanAfterTesting(wrapper.mainSurface)
        AnalyticsSettings.optedIn = false
      }
    }
  }

  @Test
  fun testUiCheckMode() {
    StudioFlags.NELE_ATF_FOR_COMPOSE.override(true)
    StudioFlags.NELE_COMPOSE_UI_CHECK_COLORBLIND_MODE.override(true)

    val wrapper = ComposePreviewRepresentationTestContext(fixture, logger)
    runBlocking(workerThread) {
      wrapper.init()
      val originalScale = 0.6
      wrapper.mainSurface.setScale(originalScale)
      val preview = wrapper.createPreviewAndCompile()
      assertInstanceOf<UiCheckModeFilter.Disabled>(preview.uiCheckFilterFlow.value)

      val previewElements =
        wrapper.mainSurface.models.mapNotNull { it.dataContext.previewElement() }
      val uiCheckElement = previewElements.single { it.methodFqn == "TestKt.Preview1" }

      val contentManager = ProblemsView.getToolWindow(project)!!.contentManager
      withContext(uiThread) {
        ProblemsViewToolWindowUtils.addTab(project, SharedIssuePanelProvider(project))
        assertEquals(1, contentManager.contents.size)
      }

      // Start UI Check mode
      wrapper.setModeAndWaitForRefresh(PreviewMode.UiCheck(uiCheckElement))

      assertInstanceOf<UiCheckModeFilter.Enabled>(preview.uiCheckFilterFlow.value)
      delayUntilCondition(250) {
        GRID_LAYOUT_MANAGER_OPTIONS.layoutManager ==
          (wrapper.mainSurface.sceneViewLayoutManager
              as? NlDesignSurfacePositionableContentLayoutManager)
            ?.layoutManager
      }

      assertTrue(preview.atfChecksEnabled)
      assertThat(preview.composePreviewFlowManager.availableGroupsFlow.value.map { it.displayName })
        .containsExactly("Screen sizes", "Font scales", "Light/Dark", "Colorblind filters")
        .inOrder()
      preview.filteredPreviewElementsInstancesFlowForTest().awaitStatus(
        "Failed set uiCheckMode",
        25.seconds,
      ) {
        it.asCollection().size > 2
      }
      assertEquals(
        """
          TestKt.Preview1
          spec:id=reference_phone,shape=Normal,width=411,height=891,unit=dp,dpi=420
          PreviewDisplaySettings(name=Medium Phone - Preview1, group=Screen sizes, showDecoration=true, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          spec:id=reference_foldable,shape=Normal,width=673,height=841,unit=dp,dpi=420
          PreviewDisplaySettings(name=Unfolded Foldable - Preview1, group=Screen sizes, showDecoration=true, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          spec:id=reference_tablet,shape=Normal,width=1280,height=800,unit=dp,dpi=240
          PreviewDisplaySettings(name=Medium Tablet - Preview1, group=Screen sizes, showDecoration=true, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          spec:id=reference_desktop,shape=Normal,width=1920,height=1080,unit=dp,dpi=160
          PreviewDisplaySettings(name=Desktop - Preview1, group=Screen sizes, showDecoration=true, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          spec:parent=_device_class_phone,orientation=landscape
          PreviewDisplaySettings(name=Medium Phone-Landscape - Preview1, group=Screen sizes, showDecoration=true, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          PreviewDisplaySettings(name=85% - Preview1, group=Font scales, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          PreviewDisplaySettings(name=100% - Preview1, group=Font scales, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          PreviewDisplaySettings(name=115% - Preview1, group=Font scales, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          PreviewDisplaySettings(name=130% - Preview1, group=Font scales, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          PreviewDisplaySettings(name=180% - Preview1, group=Font scales, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          PreviewDisplaySettings(name=200% - Preview1, group=Font scales, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          PreviewDisplaySettings(name=Light - Preview1, group=Light/Dark, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          PreviewDisplaySettings(name=Dark - Preview1, group=Light/Dark, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          PreviewDisplaySettings(name=Original - Preview1, group=Colorblind filters, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          PreviewDisplaySettings(name=Protanopes - Preview1, group=Colorblind filters, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          PreviewDisplaySettings(name=Protanomaly - Preview1, group=Colorblind filters, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          PreviewDisplaySettings(name=Deuteranopes - Preview1, group=Colorblind filters, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          PreviewDisplaySettings(name=Deuteranomaly - Preview1, group=Colorblind filters, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          PreviewDisplaySettings(name=Tritanopes - Preview1, group=Colorblind filters, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          PreviewDisplaySettings(name=Tritanomaly - Preview1, group=Colorblind filters, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

        """
          .trimIndent(),
        preview.filteredPreviewElementsInstancesFlowForTest().value.asCollection().joinToString(
          "\n"
        ) {
          val configurationDeviceSpecText =
            "${it.configuration.deviceSpec}\n".takeIf { str -> str.isNotBlank() } ?: ""
          "${it.methodFqn}\n$configurationDeviceSpecText${it.displaySettings}\n"
        },
      )

      // Change the scale of the surface
      wrapper.mainSurface.setScale(originalScale + 0.5)

      // Check that the UI Check tab has been created
      assertEquals(2, contentManager.contents.size)
      assertEquals(uiCheckElement.instanceId, contentManager.selectedContent?.tabName)

      // Stop UI Check mode
      wrapper.setModeAndWaitForRefresh(PreviewMode.Default())

      assertInstanceOf<UiCheckModeFilter.Disabled>(preview.uiCheckFilterFlow.value)
      delayUntilCondition(250) {
        LIST_LAYOUT_MANAGER_OPTION.layoutManager ==
          (wrapper.mainSurface.sceneViewLayoutManager
              as? NlDesignSurfacePositionableContentLayoutManager)
            ?.layoutManager
      }

      // Check that the surface zooms to fit when exiting UI check mode.
      assertEquals(1.0, wrapper.mainSurface.scale, 0.001)

      preview.filteredPreviewElementsInstancesFlowForTest().awaitStatus(
        "Failed stop uiCheckMode",
        25.seconds,
      ) {
        it.asCollection().size == 2
      }
      assertEquals(
        """
          TestKt.Preview1


          TestKt.Preview2


        """
          .trimIndent(),
        preview.filteredPreviewElementsInstancesFlowForTest().value.asCollection().joinToString(
          "\n"
        ) {
          "${it.methodFqn}\n${it.configuration.deviceSpec}\n"
        },
      )

      // Check that the UI Check tab is still present
      assertEquals(2, contentManager.contents.size)
      assertEquals(uiCheckElement.instanceId, contentManager.selectedContent?.tabName)

      // Restart UI Check mode on the same preview
      wrapper.setModeAndWaitForRefresh(PreviewMode.UiCheck(uiCheckElement)) {
        GRID_LAYOUT_MANAGER_OPTIONS.layoutManager ==
          (wrapper.mainSurface.sceneViewLayoutManager
              as? NlDesignSurfacePositionableContentLayoutManager)
            ?.layoutManager
      }

      // Check that the UI Check tab is being reused
      assertEquals(2, contentManager.contents.size)
      assertEquals(uiCheckElement.instanceId, contentManager.selectedContent?.tabName)

      ToolWindowManager.getInstance(project).getToolWindow(ProblemsView.ID)?.show()
      val reopenTabAction = UiCheckReopenTabAction(preview)
      // Check that UiCheckReopenTabAction is disabled when the UI Check tab is visible and selected
      run {
        val actionEvent = TestActionEvent.createTestEvent()
        reopenTabAction.update(actionEvent)
        assertFalse(actionEvent.presentation.isEnabled)
      }

      // Check that UiCheckReopenTabAction is enabled when the UI Check tab is not selected
      contentManager.setSelectedContent(contentManager.getContent(0)!!)
      assertNotEquals(uiCheckElement.instanceId, contentManager.selectedContent?.tabName)
      run {
        val actionEvent = TestActionEvent.createTestEvent()
        reopenTabAction.update(actionEvent)
        assertTrue(actionEvent.presentation.isEnabled)
      }

      // Check that performing UiCheckReopenTabAction selects the UI Check tab
      withContext(uiThread) {
        val actionEvent = TestActionEvent.createTestEvent()
        reopenTabAction.actionPerformed(actionEvent)
      }
      invokeAndWaitIfNeeded {
        assertEquals(uiCheckElement.instanceId, contentManager.selectedContent?.tabName)
      }

      // Check that UiCheckReopenTabAction is enabled when the UI Check tab has been closed
      withContext(uiThread) {
        ProblemsViewToolWindowUtils.removeTab(project, uiCheckElement.instanceId)
      }
      assertEquals(1, contentManager.contents.size)
      run {
        val actionEvent = TestActionEvent.createTestEvent()
        reopenTabAction.update(actionEvent)
        assertTrue(actionEvent.presentation.isEnabled)
      }

      // Check that performing UiCheckReopenTabAction recreates the UI Check tab
      withContext(uiThread) {
        val actionEvent = TestActionEvent.createTestEvent()
        reopenTabAction.actionPerformed(actionEvent)
      }
      invokeAndWaitIfNeeded {
        assertEquals(2, contentManager.contents.size)
        assertEquals(uiCheckElement.instanceId, contentManager.selectedContent?.tabName)
      }

      wrapper.setModeAndWaitForRefresh(PreviewMode.Default()) {
        LIST_LAYOUT_MANAGER_OPTION.layoutManager ==
          (wrapper.mainSurface.sceneViewLayoutManager
              as? NlDesignSurfacePositionableContentLayoutManager)
            ?.layoutManager
      }
      preview.onDeactivate()
    }
  }

  @Test
  fun testUiCheckModeWithColorBlindCheckEnabled() {
    StudioFlags.NELE_ATF_FOR_COMPOSE.override(true)
    StudioFlags.NELE_COMPOSE_UI_CHECK_COLORBLIND_MODE.override(true)
    val wrapper = ComposePreviewRepresentationTestContext(fixture, logger)

    runBlocking(workerThread) {
      wrapper.init()
      val preview = wrapper.createPreviewAndCompile()
      assertInstanceOf<UiCheckModeFilter.Disabled>(preview.uiCheckFilterFlow.value)

      val previewElements =
        wrapper.mainSurface.models.mapNotNull { it.dataContext.previewElement() }
      val uiCheckElement = previewElements.single { it.methodFqn == "TestKt.Preview1" }

      val contentManager = ProblemsView.getToolWindow(project)!!.contentManager
      ProblemsViewToolWindowUtils.addTab(project, SharedIssuePanelProvider(project))
      assertEquals(1, contentManager.contents.size)

      // Start UI Check mode
      wrapper.setModeAndWaitForRefresh(PreviewMode.UiCheck(uiCheckElement))
      assertInstanceOf<UiCheckModeFilter.Enabled>(preview.uiCheckFilterFlow.value)

      assertTrue(preview.atfChecksEnabled)
      assertThat(preview.composePreviewFlowManager.availableGroupsFlow.value.map { it.displayName })
        .containsExactly("Screen sizes", "Font scales", "Light/Dark", "Colorblind filters")
        .inOrder()
      preview.filteredPreviewElementsInstancesFlowForTest().awaitStatus(
        "Failed set uiCheckMode",
        25.seconds,
      ) {
        it.asCollection().size > 2
      }
      assertEquals(
        """
          TestKt.Preview1
          spec:id=reference_phone,shape=Normal,width=411,height=891,unit=dp,dpi=420
          PreviewDisplaySettings(name=Medium Phone - Preview1, group=Screen sizes, showDecoration=true, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          spec:id=reference_foldable,shape=Normal,width=673,height=841,unit=dp,dpi=420
          PreviewDisplaySettings(name=Unfolded Foldable - Preview1, group=Screen sizes, showDecoration=true, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          spec:id=reference_tablet,shape=Normal,width=1280,height=800,unit=dp,dpi=240
          PreviewDisplaySettings(name=Medium Tablet - Preview1, group=Screen sizes, showDecoration=true, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          spec:id=reference_desktop,shape=Normal,width=1920,height=1080,unit=dp,dpi=160
          PreviewDisplaySettings(name=Desktop - Preview1, group=Screen sizes, showDecoration=true, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          spec:parent=_device_class_phone,orientation=landscape
          PreviewDisplaySettings(name=Medium Phone-Landscape - Preview1, group=Screen sizes, showDecoration=true, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          PreviewDisplaySettings(name=85% - Preview1, group=Font scales, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          PreviewDisplaySettings(name=100% - Preview1, group=Font scales, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          PreviewDisplaySettings(name=115% - Preview1, group=Font scales, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          PreviewDisplaySettings(name=130% - Preview1, group=Font scales, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          PreviewDisplaySettings(name=180% - Preview1, group=Font scales, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          PreviewDisplaySettings(name=200% - Preview1, group=Font scales, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          PreviewDisplaySettings(name=Light - Preview1, group=Light/Dark, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          PreviewDisplaySettings(name=Dark - Preview1, group=Light/Dark, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          PreviewDisplaySettings(name=Original - Preview1, group=Colorblind filters, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          PreviewDisplaySettings(name=Protanopes - Preview1, group=Colorblind filters, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          PreviewDisplaySettings(name=Protanomaly - Preview1, group=Colorblind filters, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          PreviewDisplaySettings(name=Deuteranopes - Preview1, group=Colorblind filters, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          PreviewDisplaySettings(name=Deuteranomaly - Preview1, group=Colorblind filters, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          PreviewDisplaySettings(name=Tritanopes - Preview1, group=Colorblind filters, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          PreviewDisplaySettings(name=Tritanomaly - Preview1, group=Colorblind filters, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

        """
          .trimIndent(),
        preview.filteredPreviewElementsInstancesFlowForTest().value.asCollection().joinToString(
          "\n"
        ) {
          val configurationDeviceSpecText =
            "${it.configuration.deviceSpec}\n".takeIf { str -> str.isNotBlank() } ?: ""
          "${it.methodFqn}\n$configurationDeviceSpecText${it.displaySettings}\n"
        },
      )

      // Check that the UI Check tab has been created
      assertEquals(2, contentManager.contents.size)
      assertEquals(uiCheckElement.instanceId, contentManager.selectedContent?.tabName)

      // Stop UI Check mode
      wrapper.setModeAndWaitForRefresh(PreviewMode.Default())
      assertInstanceOf<UiCheckModeFilter.Disabled>(preview.uiCheckFilterFlow.value)

      preview.filteredPreviewElementsInstancesFlowForTest().awaitStatus(
        "Failed stop uiCheckMode",
        25.seconds,
      ) {
        it.asCollection().size == 2
      }
      assertEquals(
        """
          TestKt.Preview1


          TestKt.Preview2


        """
          .trimIndent(),
        preview.filteredPreviewElementsInstancesFlowForTest().value.asCollection().joinToString(
          "\n"
        ) {
          "${it.methodFqn}\n${it.configuration.deviceSpec}\n"
        },
      )

      // Check that the UI Check tab is still present
      assertEquals(2, contentManager.contents.size)
      assertEquals(uiCheckElement.instanceId, contentManager.selectedContent?.tabName)

      // Restart UI Check mode on the same preview
      wrapper.setModeAndWaitForRefresh(PreviewMode.UiCheck(uiCheckElement))

      // Check that the UI Check tab is being reused
      assertEquals(2, contentManager.contents.size)
      assertEquals(uiCheckElement.instanceId, contentManager.selectedContent?.tabName)

      wrapper.setModeAndWaitForRefresh(PreviewMode.Default())
      preview.onDeactivate()
    }
  }

  @Test
  fun testPreviewModeManagerShouldBeRegisteredInDataProvider() {
    val wrapper = ComposePreviewRepresentationTestContext(fixture, logger)
    runBlocking(workerThread) {
      wrapper.init()
      wrapper.createPreviewAndCompile()
      assertTrue(wrapper.getData(PreviewModeManager.KEY.name) is PreviewModeManager)
    }
  }

  @Test
  fun testPreviewGroupManagerShouldBeRegisteredInDataProvider() {
    val wrapper = ComposePreviewRepresentationTestContext(fixture, logger)
    runBlocking(workerThread) {
      wrapper.init()
      wrapper.createPreviewAndCompile()
      assertTrue(wrapper.getData(PreviewGroupManager.KEY.name) is PreviewGroupManager)
    }
  }

  @Test
  fun testActivationDoesNotCleanOverlayClassLoader() =
    runBlocking(workerThread) {
      val composeTest = runWriteActionAndWait {
        fixture.addFileToProjectAndInvalidate(
          "Test.kt",
          // language=kotlin
          """
        import androidx.compose.ui.tooling.preview.Preview
        import androidx.compose.runtime.Composable

        @Composable
        @Preview
        fun Preview1() {
        }
      """
            .trimIndent(),
        )
      }
      val surfaceMock = Mockito.mock(NlDesignSurface::class.java)
      val composeView = TestComposePreviewView(surfaceMock)
      val previewRepresentation =
        ComposePreviewRepresentation(composeTest, PreferredVisibility.SPLIT) { _, _, _, _, _, _ ->
          composeView
        }
      Disposer.register(fixture.testRootDisposable, previewRepresentation)
      Disposer.register(fixture.testRootDisposable, surfaceMock)

      // Compile the project so that 'buildSucceeded' is called during build listener setup
      ProjectSystemService.getInstance(project).projectSystem.getBuildManager().compileProject()

      val job = launch {
        while (!previewRepresentation.hasBuildListenerSetupFinished()) {
          delay(500)
        }
      }

      val overlayClassLoader = ModuleClassLoaderOverlays.getInstance(fixture.module)
      assertTrue(overlayClassLoader.state.paths.isEmpty())
      overlayClassLoader.pushOverlayPath(Path.of("/tmp/test"))
      assertFalse(overlayClassLoader.state.paths.isEmpty())
      assertFalse(previewRepresentation.hasBuildListenerSetupFinished())
      previewRepresentation.onActivate()
      job.join()
      assertTrue(previewRepresentation.hasBuildListenerSetupFinished())
      assertFalse(overlayClassLoader.state.paths.isEmpty())
    }

  @Test
  fun testRerunUiCheckAction() {
    // Use the real FileEditorManager
    project.putUserData(FileEditorManagerImpl.ALLOW_IN_LIGHT_PROJECT, true)
    project.replaceService(
      FileEditorManager::class.java,
      FileEditorManagerImpl(project, project.coroutineScope),
      projectRule.fixture.testRootDisposable,
    )
    HeadlessDataManager.fallbackToProductionDataManager(projectRule.fixture.testRootDisposable)

    StudioFlags.NELE_ATF_FOR_COMPOSE.override(true)
    val wrapper = ComposePreviewRepresentationTestContext(fixture, logger)
    runBlocking(workerThread) {
      val testPsiFile = wrapper.createPreviewPsiFile()
      testPsiFile.putUserData(FileEditorProvider.KEY, SourceCodeEditorProvider())
      val editor =
        withContext(uiThread) {
          val editors =
            FileEditorManager.getInstance(project).openFile(testPsiFile.virtualFile, true, true)
          (editors[0] as TextEditorWithMultiRepresentationPreview<*>)
        }
      delayUntilCondition(250) { editor.getPreviewManager<ComposePreviewManager>() != null }
      wrapper.init(withContext(uiThread) { editor.getDesignSurface() as NlDesignSurface })

      val preview =
        editor.getPreviewManager<ComposePreviewManager>() as ComposePreviewRepresentation
      wrapper.createPreviewAndCompile(preview)

      // Start UI Check mode
      val previewElements =
        wrapper.mainSurface.models.mapNotNull { it.dataContext.previewElement() }
      val uiCheckElement = previewElements.single { it.methodFqn == "TestKt.Preview1" }

      run {
        preview.waitForAnyPendingRefresh()
        preview.setMode(PreviewMode.UiCheck(uiCheckElement))
        delayUntilCondition(250) { preview.uiCheckFilterFlow.value is UiCheckModeFilter.Enabled }
      }

      val contentManager =
        ToolWindowManager.getInstance(project).getToolWindow(ProblemsView.ID)!!.contentManager
      delayUntilCondition(250) {
        contentManager.selectedContent?.tabName == uiCheckElement.instanceId
      }
      val tab = contentManager.selectedContent!!
      val dataContext =
        withContext(uiThread) {
          ((tab.component as DesignerCommonIssuePanel).toolbar as ActionToolbarImpl)
            .toolbarDataContext
        }

      // Check that the rerun action is disabled
      val rerunAction = ReRunUiCheckModeAction()
      run {
        val actionEvent = withContext(uiThread) { TestActionEvent.createTestEvent(dataContext) }
        rerunAction.update(actionEvent)
        assertTrue(actionEvent.presentation.isVisible)
        assertFalse(actionEvent.presentation.isEnabled)
      }

      // Stop UI Check mode
      run {
        preview.waitForAnyPendingRefresh()
        preview.setMode(PreviewMode.Default())
        delayUntilCondition(250) { preview.uiCheckFilterFlow.value is UiCheckModeFilter.Disabled }
      }

      // Check that the rerun action is enabled
      run {
        val actionEvent = withContext(uiThread) { TestActionEvent.createTestEvent(dataContext) }
        rerunAction.update(actionEvent)
        assertTrue(actionEvent.presentation.isEnabledAndVisible)
      }

      // Rerun UI check with the problems panel action
      launch(uiThread) { rerunAction.actionPerformed(TestActionEvent.createTestEvent(dataContext)) }
      delayUntilCondition(250) { preview.uiCheckFilterFlow.value is UiCheckModeFilter.Enabled }

      // Check that the rerun action is disabled
      run {
        val actionEvent = withContext(uiThread) { TestActionEvent.createTestEvent(dataContext) }
        rerunAction.update(actionEvent)
        assertTrue(actionEvent.presentation.isVisible)
        assertFalse(actionEvent.presentation.isEnabled)
      }

      preview.waitForAnyPendingRefresh()
      withContext(uiThread) { FileEditorManagerEx.getInstanceEx(project).closeAllFiles() }
    }
  }

  /**
   * Wrapper class to perform operations and expose properties that are common to most tests in this
   * test class.
   */
  private class ComposePreviewRepresentationTestContext(
    private val fixture: CodeInsightTestFixture,
    private val logger: Logger,
  ) {

    private lateinit var _mainSurface: NlDesignSurface
    val mainSurface
      get() = _mainSurface

    private lateinit var previewPsiFile: PsiFile

    private lateinit var preview: ComposePreviewRepresentation

    private lateinit var composeView: TestComposePreviewView

    private lateinit var dataProvider: DataProvider

    private lateinit var modelRenderedLatch: CountDownLatch

    fun init(surfaceOverride: NlDesignSurface? = null) {
      if (!::previewPsiFile.isInitialized) {
        createPreviewPsiFile()
      }
      _mainSurface =
        surfaceOverride
          ?: NlDesignSurface.builder(fixture.project, fixture.testRootDisposable).build()
      modelRenderedLatch = CountDownLatch(2)

      _mainSurface.addListener(
        object : DesignSurfaceListener {
          override fun modelChanged(surface: DesignSurface<*>, model: NlModel?) {
            val id = UUID.randomUUID().toString().substring(0, 5)
            logger.info("modelChanged ($id)")
            (surface.getSceneManager(model!!) as? LayoutlibSceneManager)?.addRenderListener {
              logger.info("renderListener ($id)")
              modelRenderedLatch.countDown()
            }
          }
        }
      )
    }

    suspend fun createPreviewAndCompile(
      previewOverride: ComposePreviewRepresentation? = null
    ): ComposePreviewRepresentation {
      composeView = TestComposePreviewView(mainSurface)
      preview =
        previewOverride
          ?: ComposePreviewRepresentation(previewPsiFile, PreferredVisibility.SPLIT) {
            _,
            _,
            _,
            provider,
            _,
            _ ->
            dataProvider = provider
            composeView
          }
      Disposer.register(fixture.testRootDisposable, preview)
      withContext(workerThread) {
        logger.info("compile")
        ProjectSystemService.getInstance(fixture.project)
          .projectSystem
          .getBuildManager()
          .compileProject()
        logger.info("activate")
        preview.onActivate()

        modelRenderedLatch.await()
        delayWhileRefreshingOrDumb(preview)
      }
      return preview
    }

    suspend fun setModeAndWaitForRefresh(
      previewMode: PreviewMode,
      // In addition to refresh, we can wait for another condition before returning.
      additionalCondition: () -> Boolean = { true },
    ) {
      preview.waitForAnyPendingRefresh()
      var refresh = false
      composeView.refreshCompletedListeners.add { refresh = true }
      preview.setMode(previewMode)
      delayUntilCondition(250) { refresh && additionalCondition() }
    }

    fun createPreviewPsiFile(): PsiFile {
      previewPsiFile = runWriteActionAndWait {
        fixture.addFileToProjectAndInvalidate(
          "Test.kt",
          // language=kotlin
          """
            import androidx.compose.ui.tooling.preview.Devices
            import androidx.compose.ui.tooling.preview.Preview
            import androidx.compose.runtime.Composable

            @Composable
            @Preview
            fun Preview1() {
            }

            @Composable
            @Preview(name = "preview2", apiLevel = 12, group = "groupA", showBackground = true)
            fun Preview2() {
            }
          """
            .trimIndent(),
        )
      }
      return previewPsiFile
    }

    private suspend fun delayWhileRefreshingOrDumb(preview: ComposePreviewRepresentation) {
      delayUntilCondition(250) {
        !(preview.status().isRefreshing || DumbService.getInstance(fixture.project).isDumb)
      }
    }

    fun getData(dataId: String): Any? {
      checkState(
        ::dataProvider.isInitialized,
        "createPreviewAndCompile() must be called before getData() to make sure the DataProvider " +
          "is initialized.",
      )
      return dataProvider.getData(dataId)
    }
  }
}
