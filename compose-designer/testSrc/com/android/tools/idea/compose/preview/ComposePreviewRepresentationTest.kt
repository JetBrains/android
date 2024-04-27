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
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.DesignSurfaceListener
import com.android.tools.idea.compose.ComposeProjectRule
import com.android.tools.idea.compose.UiCheckModeFilter
import com.android.tools.idea.compose.preview.actions.ReRunUiCheckModeAction
import com.android.tools.idea.compose.preview.actions.UiCheckReopenTabAction
import com.android.tools.idea.compose.preview.gallery.ComposeGalleryMode
import com.android.tools.idea.compose.preview.util.previewElement
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.concurrency.awaitStatus
import com.android.tools.idea.editors.build.ProjectStatus
import com.android.tools.idea.editors.fast.FastPreviewManager
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.modes.GRID_LAYOUT_MANAGER_OPTIONS
import com.android.tools.idea.preview.modes.LIST_LAYOUT_MANAGER_OPTION
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.projectsystem.TestProjectSystem
import com.android.tools.idea.testing.addFileToProjectAndInvalidate
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreferredVisibility
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.NlDesignSurfacePositionableContentLayoutManager
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintService
import com.android.tools.idea.util.TestToolWindowManager
import com.google.common.truth.Truth.assertThat
import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.assertInstanceOf
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.android.uipreview.ModuleClassLoaderOverlays
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CountDownLatch
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.time.Duration.Companion.seconds

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
      fixture.testRootDisposable
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
  fun testPreviewInitialization() =
    runBlocking(workerThread) {
      val composeTest = createComposeTest()

      val mainSurface = NlDesignSurface.builder(project, fixture.testRootDisposable).build()
      val modelRenderedLatch = CountDownLatch(2)

      mainSurface.addListener(
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

      val composeView = TestComposePreviewView(mainSurface)
      val preview =
        ComposePreviewRepresentation(composeTest, PreferredVisibility.SPLIT) { _, _, _, _, _, _ ->
          composeView
        }
      Disposer.register(fixture.testRootDisposable, preview)
      withContext(Dispatchers.IO) {
        logger.info("compile")
        ProjectSystemService.getInstance(project).projectSystem.getBuildManager().compileProject()
        logger.info("activate")
        preview.onActivate()

        modelRenderedLatch.await()
        delayWhileRefreshingOrDumb(preview)
      }

      mainSurface.models.forEach {
        assertTrue(preview.navigationHandler.defaultNavigationMap.contains(it))
      }

      assertThat(preview.availableGroupsFlow.value.map { it.displayName }).containsExactly("groupA")

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

  @Test
  fun testUiCheckMode() {
    StudioFlags.NELE_ATF_FOR_COMPOSE.override(true)
    runBlocking(workerThread) {
      val composeTest = createComposeTest()

      val mainSurface = NlDesignSurface.builder(project, fixture.testRootDisposable).build()
      val modelRenderedLatch = CountDownLatch(2)

      mainSurface.addListener(
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
      val originalScale = 0.6
      mainSurface.setScale(originalScale)

      val composeView = TestComposePreviewView(mainSurface)
      val preview =
        ComposePreviewRepresentation(composeTest, PreferredVisibility.SPLIT) { _, _, _, _, _, _ ->
          composeView
        }
      Disposer.register(fixture.testRootDisposable, preview)
      withContext(Dispatchers.IO) {
        logger.info("compile")
        ProjectSystemService.getInstance(project).projectSystem.getBuildManager().compileProject()
        logger.info("activate")
        preview.onActivate()

        modelRenderedLatch.await()

        delayWhileRefreshingOrDumb(preview)
      }
      assertInstanceOf<UiCheckModeFilter.Disabled>(preview.uiCheckFilterFlow.value)

      val previewElements = mainSurface.models.mapNotNull { it.dataContext.previewElement() }
      val uiCheckElement = previewElements.single { it.methodFqn == "TestKt.Preview1" }

      val contentManager =
        ToolWindowManager.getInstance(project).getToolWindow(ProblemsView.ID)!!.contentManager
      assertEquals(0, contentManager.contents.size)

      // Start UI Check mode
      run {
        preview.waitForAnyPendingRefresh()
        var refresh = false
        composeView.refreshCompletedListeners.add { refresh = true }
        preview.setMode(PreviewMode.UiCheck(uiCheckElement))
        delayUntilCondition(250) { refresh }
      }

      assertInstanceOf<UiCheckModeFilter.Enabled>(preview.uiCheckFilterFlow.value)
      delayUntilCondition(250) {
        GRID_LAYOUT_MANAGER_OPTIONS.layoutManager ==
          (mainSurface.sceneViewLayoutManager as? NlDesignSurfacePositionableContentLayoutManager)
            ?.layoutManager
      }

      assertTrue(preview.atfChecksEnabled)
      assertThat(preview.availableGroupsFlow.value.map { it.displayName })
        .containsExactly("Screen sizes", "Font scales", "Light/Dark")
        .inOrder()
      preview.filteredPreviewElementsInstancesFlowForTest().awaitStatus(
        "Failed set uiCheckMode",
        5.seconds
      ) {
        it.size > 2
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

        """
          .trimIndent(),
        preview.filteredPreviewElementsInstancesFlowForTest().value.joinToString("\n") {
          val configurationDeviceSpecText =
            "${it.configuration.deviceSpec}\n".takeIf { str -> str.isNotBlank() } ?: ""
          "${it.methodFqn}\n$configurationDeviceSpecText${it.displaySettings}\n"
        }
      )

      // Change the scale of the surface
      mainSurface.setScale(originalScale + 0.5)

      // Check that the UI Check tab has been created
      assertEquals(2, contentManager.contents.size)
      assertNotNull(contentManager.findContent(uiCheckElement.displaySettings.name))
      val rerunAction = ReRunUiCheckModeAction()
      run {
        val actionEvent = TestActionEvent.createTestEvent()
        rerunAction.update(actionEvent)
        assertTrue(actionEvent.presentation.isVisible)
        assertFalse(actionEvent.presentation.isEnabled)
      }

      run {
        preview.waitForAnyPendingRefresh()
        var refresh = false
        composeView.refreshCompletedListeners.add { refresh = true }
        // Stop UI Check mode
        preview.setMode(PreviewMode.Default())
        delayUntilCondition(250) { refresh }
      }

      assertInstanceOf<UiCheckModeFilter.Disabled>(preview.uiCheckFilterFlow.value)
      delayUntilCondition(250) {
        LIST_LAYOUT_MANAGER_OPTION.layoutManager ==
          (mainSurface.sceneViewLayoutManager as? NlDesignSurfacePositionableContentLayoutManager)
            ?.layoutManager
      }

      // Check that the surface zooms to fit when exiting UI check mode.
      assertEquals(1.0, mainSurface.scale, 0.001)

      preview.filteredPreviewElementsInstancesFlowForTest().awaitStatus(
        "Failed stop uiCheckMode",
        5.seconds
      ) {
        it.size == 2
      }
      assertEquals(
        """
          TestKt.Preview1


          TestKt.Preview2


        """
          .trimIndent(),
        preview.filteredPreviewElementsInstancesFlowForTest().value.joinToString("\n") {
          "${it.methodFqn}\n${it.configuration.deviceSpec}\n"
        }
      )

      // Check that the UI Check tab is still present
      assertEquals(2, contentManager.contents.size)
      assertNotNull(contentManager.findContent(uiCheckElement.displaySettings.name))
      run {
        val actionEvent = TestActionEvent.createTestEvent()
        rerunAction.update(actionEvent)
        assertTrue(actionEvent.presentation.isEnabledAndVisible)
      }

      // Re-run UI check with the problems panel action
      launch(uiThread) { rerunAction.actionPerformed(TestActionEvent.createTestEvent()) }
      delayUntilCondition(250) { preview.uiCheckFilterFlow.value is UiCheckModeFilter.Enabled }
      preview.filteredPreviewElementsInstancesFlowForTest().awaitStatus(
        "Failed set uiCheckMode",
        5.seconds
      ) {
        it.size > 2
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

        """
          .trimIndent(),
        preview.filteredPreviewElementsInstancesFlowForTest().value.joinToString("\n") {
          val configurationDeviceSpecText =
            "${it.configuration.deviceSpec}\n".takeIf { str -> str.isNotBlank() } ?: ""
          "${it.methodFqn}\n$configurationDeviceSpecText${it.displaySettings}\n"
        }
      )

      run {
        preview.waitForAnyPendingRefresh()
        var refresh = false
        composeView.refreshCompletedListeners.add { refresh = true }
        // Stop UI Check mode
        preview.setMode(PreviewMode.Default(GRID_LAYOUT_MANAGER_OPTIONS))
        delayUntilCondition(250) {
          refresh &&
            GRID_LAYOUT_MANAGER_OPTIONS.layoutManager ==
              (mainSurface.sceneViewLayoutManager
                  as? NlDesignSurfacePositionableContentLayoutManager)
                ?.layoutManager
        }
      }

      // Restart UI Check mode on the same preview
      run {
        preview.waitForAnyPendingRefresh()
        var refresh = false
        composeView.refreshCompletedListeners.add { refresh = true }
        preview.setMode(PreviewMode.UiCheck(uiCheckElement))
        delayUntilCondition(250) {
          refresh &&
            GRID_LAYOUT_MANAGER_OPTIONS.layoutManager ==
              (mainSurface.sceneViewLayoutManager
                  as? NlDesignSurfacePositionableContentLayoutManager)
                ?.layoutManager
        }
      }

      // Check that the UI Check tab is being reused
      assertEquals(2, contentManager.contents.size)
      val tab = contentManager.findContent(uiCheckElement.displaySettings.name)
      assertNotNull(tab)
      assertTrue(contentManager.selectedContent == tab)

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
      assertFalse(contentManager.selectedContent == tab)
      run {
        val actionEvent = TestActionEvent.createTestEvent()
        reopenTabAction.update(actionEvent)
        assertTrue(actionEvent.presentation.isEnabled)
      }

      // Check that performing UiCheckReopenTabAction selects the UI Check tab
      run {
        val actionEvent = TestActionEvent.createTestEvent()
        reopenTabAction.actionPerformed(actionEvent)
        assertTrue(contentManager.selectedContent == tab)
      }

      // Check that UiCheckReopenTabAction is enabled when the UI Check tab has been closed
      contentManager.removeContent(tab, true)
      assertNull(contentManager.findContent(uiCheckElement.displaySettings.name))
      run {
        val actionEvent = TestActionEvent.createTestEvent()
        reopenTabAction.update(actionEvent)
        assertTrue(actionEvent.presentation.isEnabled)
      }

      // Check that performing UiCheckReopenTabAction recreates the UI Check tab
      run {
        val actionEvent = TestActionEvent.createTestEvent()
        reopenTabAction.actionPerformed(actionEvent)
        assertEquals(2, contentManager.contents.size)
        val newTab = contentManager.findContent(uiCheckElement.displaySettings.name)
        assertNotNull(newTab)
        assertTrue(contentManager.selectedContent == newTab)
      }

      run {
        preview.waitForAnyPendingRefresh()
        var refresh = false
        composeView.refreshCompletedListeners.add { refresh = true }
        // Stop UI Check mode
        preview.setMode(PreviewMode.Default())
        delayUntilCondition(250) {
          refresh &&
            LIST_LAYOUT_MANAGER_OPTION.layoutManager ==
              (mainSurface.sceneViewLayoutManager
                  as? NlDesignSurfacePositionableContentLayoutManager)
                ?.layoutManager
        }
      }

      preview.onDeactivate()
    }
  }

  @Test
  fun testUiCheckModeWithColorBlindCheckEnabled() {
    StudioFlags.NELE_ATF_FOR_COMPOSE.override(true)
    StudioFlags.NELE_COMPOSE_UI_CHECK_COLORBLIND_MODE.override(true)
    runBlocking(workerThread) {
      val composeTest = createComposeTest()

      val mainSurface = NlDesignSurface.builder(project, fixture.testRootDisposable).build()
      val modelRenderedLatch = CountDownLatch(2)

      mainSurface.addListener(
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

      val composeView = TestComposePreviewView(mainSurface)
      val preview =
        ComposePreviewRepresentation(composeTest, PreferredVisibility.SPLIT) { _, _, _, _, _, _ ->
          composeView
        }
      Disposer.register(fixture.testRootDisposable, preview)
      withContext(Dispatchers.IO) {
        logger.info("compile")
        ProjectSystemService.getInstance(project).projectSystem.getBuildManager().compileProject()
        logger.info("activate")
        preview.onActivate()

        modelRenderedLatch.await()

        delayWhileRefreshingOrDumb(preview)
      }
      assertInstanceOf<UiCheckModeFilter.Disabled>(preview.uiCheckFilterFlow.value)

      val previewElements = mainSurface.models.mapNotNull { it.dataContext.previewElement() }
      val uiCheckElement = previewElements.single { it.methodFqn == "TestKt.Preview1" }

      val contentManager =
        ToolWindowManager.getInstance(project).getToolWindow(ProblemsView.ID)!!.contentManager
      assertEquals(0, contentManager.contents.size)

      // Start UI Check mode
      run {
        preview.waitForAnyPendingRefresh()
        var refresh = false
        composeView.refreshCompletedListeners.add { refresh = true }
        preview.setMode(PreviewMode.UiCheck(uiCheckElement))
        delayUntilCondition(250) { refresh }
      }

      assertInstanceOf<UiCheckModeFilter.Enabled>(preview.uiCheckFilterFlow.value)

      assertTrue(preview.atfChecksEnabled)
      assertThat(preview.availableGroupsFlow.value.map { it.displayName })
        .containsExactly("Screen sizes", "Font scales", "Light/Dark", "Colorblind filters")
        .inOrder()
      preview.filteredPreviewElementsInstancesFlowForTest().awaitStatus(
        "Failed set uiCheckMode",
        5.seconds
      ) {
        it.size > 2
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
          PreviewDisplaySettings(name=Original, group=Colorblind filters, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          PreviewDisplaySettings(name=Protanopes, group=Colorblind filters, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          PreviewDisplaySettings(name=Protanomaly, group=Colorblind filters, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          PreviewDisplaySettings(name=Deuteranopes, group=Colorblind filters, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          PreviewDisplaySettings(name=Deuteranomaly, group=Colorblind filters, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          PreviewDisplaySettings(name=Tritanopes, group=Colorblind filters, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          PreviewDisplaySettings(name=Tritanomaly, group=Colorblind filters, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

        """
          .trimIndent(),
        preview.filteredPreviewElementsInstancesFlowForTest().value.joinToString("\n") {
          val configurationDeviceSpecText =
            "${it.configuration.deviceSpec}\n".takeIf { str -> str.isNotBlank() } ?: ""
          "${it.methodFqn}\n$configurationDeviceSpecText${it.displaySettings}\n"
        }
      )

      // Check that the UI Check tab has been created
      assertEquals(2, contentManager.contents.size)
      assertNotNull(contentManager.findContent(uiCheckElement.displaySettings.name))
      val rerunAction = ReRunUiCheckModeAction()
      run {
        val actionEvent = TestActionEvent.createTestEvent()
        rerunAction.update(actionEvent)
        assertTrue(actionEvent.presentation.isVisible)
        assertFalse(actionEvent.presentation.isEnabled)
      }

      // Stop UI Check mode
      run {
        preview.waitForAnyPendingRefresh()
        var refresh = false
        composeView.refreshCompletedListeners.add { refresh = true }
        preview.setMode(PreviewMode.Default())
        delayUntilCondition(250) { refresh }
      }
      assertInstanceOf<UiCheckModeFilter.Disabled>(preview.uiCheckFilterFlow.value)

      preview.filteredPreviewElementsInstancesFlowForTest().awaitStatus(
        "Failed stop uiCheckMode",
        5.seconds
      ) {
        it.size == 2
      }
      assertEquals(
        """
          TestKt.Preview1


          TestKt.Preview2


        """
          .trimIndent(),
        preview.filteredPreviewElementsInstancesFlowForTest().value.joinToString("\n") {
          "${it.methodFqn}\n${it.configuration.deviceSpec}\n"
        }
      )

      // Check that the UI Check tab is still present
      assertEquals(2, contentManager.contents.size)
      assertNotNull(contentManager.findContent(uiCheckElement.displaySettings.name))
      run {
        val actionEvent = TestActionEvent.createTestEvent()
        rerunAction.update(actionEvent)
        assertTrue(actionEvent.presentation.isEnabledAndVisible)
      }

      // Re-run UI check with the problems panel action
      launch(uiThread) { rerunAction.actionPerformed(TestActionEvent.createTestEvent()) }
      delayUntilCondition(250) { preview.mode.value is PreviewMode.UiCheck }
      preview.filteredPreviewElementsInstancesFlowForTest().awaitStatus(
        "Failed set uiCheckMode",
        5.seconds
      ) {
        it.size > 2
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
          PreviewDisplaySettings(name=Original, group=Colorblind filters, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          PreviewDisplaySettings(name=Protanopes, group=Colorblind filters, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          PreviewDisplaySettings(name=Protanomaly, group=Colorblind filters, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          PreviewDisplaySettings(name=Deuteranopes, group=Colorblind filters, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          PreviewDisplaySettings(name=Deuteranomaly, group=Colorblind filters, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          PreviewDisplaySettings(name=Tritanopes, group=Colorblind filters, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

          TestKt.Preview1
          PreviewDisplaySettings(name=Tritanomaly, group=Colorblind filters, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL)

        """
          .trimIndent(),
        preview.filteredPreviewElementsInstancesFlowForTest().value.joinToString("\n") {
          val configurationDeviceSpecText =
            "${it.configuration.deviceSpec}\n".takeIf { str -> str.isNotBlank() } ?: ""
          "${it.methodFqn}\n$configurationDeviceSpecText${it.displaySettings}\n"
        }
      )

      // Stop UI Check mode
      run {
        preview.waitForAnyPendingRefresh()
        var refresh = false
        composeView.refreshCompletedListeners.add { refresh = true }
        preview.setMode(PreviewMode.Default())
        delayUntilCondition(250) { refresh }
      }

      // Restart UI Check mode on the same preview
      run {
        preview.waitForAnyPendingRefresh()
        var refresh = false
        composeView.refreshCompletedListeners.add { refresh = true }
        preview.setMode(PreviewMode.UiCheck(uiCheckElement))
        delayUntilCondition(250) { refresh }
      }

      // Check that the UI Check tab is being reused
      assertEquals(2, contentManager.contents.size)
      val tab = contentManager.findContent(uiCheckElement.displaySettings.name)
      assertNotNull(tab)

      run {
        preview.waitForAnyPendingRefresh()
        var refresh = false
        composeView.refreshCompletedListeners.add { refresh = true }
        preview.setMode(PreviewMode.Default())
        delayUntilCondition(250) { refresh }
      }
      preview.onDeactivate()
    }
  }

  private fun createComposeTest() = runWriteActionAndWait {
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

  @Test
  fun testPreviewModeManagerShouldBeRegisteredInDataProvider() =
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
        fun Preview() {
        }
      """
            .trimIndent()
        )
      }

      val mainSurface = NlDesignSurface.builder(project, fixture.testRootDisposable).build()
      val modelRenderedLatch = CountDownLatch(2)

      mainSurface.addListener(
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

      val composeView = TestComposePreviewView(mainSurface)
      lateinit var dataProvider: DataProvider
      val preview =
        ComposePreviewRepresentation(composeTest, PreferredVisibility.SPLIT) {
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
      withContext(Dispatchers.IO) {
        logger.info("compile")
        ProjectSystemService.getInstance(project).projectSystem.getBuildManager().compileProject()
        logger.info("activate")
        preview.onActivate()

        modelRenderedLatch.await()
        delayWhileRefreshingOrDumb(preview)
      }

      assertEquals(preview, dataProvider.getData(PreviewModeManager.KEY.name))
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
            .trimIndent()
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

  private suspend fun delayWhileRefreshingOrDumb(preview: ComposePreviewRepresentation) {
    delayUntilCondition(250) {
      !(preview.status().isRefreshing || DumbService.getInstance(project).isDumb)
    }
  }
}
