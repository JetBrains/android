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

import com.android.flags.junit.FlagRule
import com.android.testutils.delayUntilCondition
import com.android.testutils.waitForCondition
import com.android.tools.analytics.AnalyticsSettings
import com.android.tools.idea.common.TestPannable
import com.android.tools.idea.common.error.DesignerCommonIssuePanel
import com.android.tools.idea.common.error.SharedIssuePanelProvider
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.DesignSurfaceListener
import com.android.tools.idea.common.surface.getDesignSurface
import com.android.tools.idea.compose.ComposeProjectRule
import com.android.tools.idea.compose.PsiComposePreviewElementInstance
import com.android.tools.idea.compose.preview.actions.ReRunUiCheckModeAction
import com.android.tools.idea.compose.preview.actions.UiCheckReopenTabAction
import com.android.tools.idea.compose.preview.util.previewElement
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.concurrency.asCollection
import com.android.tools.idea.concurrency.awaitStatus
import com.android.tools.idea.concurrency.coroutineScope
import com.android.tools.idea.editors.build.RenderingBuildStatus
import com.android.tools.idea.editors.fast.FastPreviewManager
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.PreviewInvalidationManager
import com.android.tools.idea.preview.actions.getPreviewManager
import com.android.tools.idea.preview.analytics.PreviewRefreshTracker
import com.android.tools.idea.preview.analytics.PreviewRefreshTrackerForTest
import com.android.tools.idea.preview.fast.FastPreviewSurface
import com.android.tools.idea.preview.flow.PreviewFlowManager
import com.android.tools.idea.preview.gallery.GalleryMode
import com.android.tools.idea.preview.groups.PreviewGroupManager
import com.android.tools.idea.preview.modes.DEFAULT_LAYOUT_OPTION
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.android.tools.idea.preview.modes.UI_CHECK_LAYOUT_OPTION
import com.android.tools.idea.preview.modes.UiCheckInstance
import com.android.tools.idea.preview.mvvm.PREVIEW_VIEW_MODEL_STATUS
import com.android.tools.idea.preview.mvvm.PreviewViewModelStatus
import com.android.tools.idea.preview.uicheck.UiCheckModeFilter
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.projectsystem.TestProjectSystem
import com.android.tools.idea.rendering.tokens.FakeBuildSystemFilePreviewServices
import com.android.tools.idea.run.configuration.execution.findElementByText
import com.android.tools.idea.testing.addFileToProjectAndInvalidate
import com.android.tools.idea.uibuilder.analytics.NlAnalyticsManager
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreferredVisibility
import com.android.tools.idea.uibuilder.editor.multirepresentation.TextEditorWithMultiRepresentationPreview
import com.android.tools.idea.uibuilder.editor.multirepresentation.sourcecode.SourceCodeEditorProvider
import com.android.tools.idea.uibuilder.options.NlOptionsConfigurable
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.NlSurfaceBuilder
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintService
import com.android.tools.idea.util.TestToolWindowManager
import com.google.common.base.Preconditions.checkState
import com.google.common.collect.ImmutableList
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.PreviewRefreshEvent
import com.intellij.analysis.problemsView.toolWindow.ProblemsView
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewToolWindowUtils
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerKeys
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
import org.jetbrains.android.uipreview.AndroidEditorSettings
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class TestComposePreviewView(override val mainSurface: NlDesignSurface) :
  ComposePreviewView {
  override val component: JComponent
    get() = JPanel()

  override var bottomPanel: JComponent? = null
  override val isMessageBeingDisplayed: Boolean = false
  override var hasContent: Boolean = true
  override var hasRendered: Boolean = true
  override var galleryMode: GalleryMode? = null

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

  @get:Rule val flagRule = FlagRule(StudioFlags.COMPOSE_INTERACTIVE_FPS_LIMIT, 30)

  private val project
    get() = projectRule.project

  private val fixture
    get() = projectRule.fixture

  private var composePreviewEssentialsModeEnabled: Boolean = false
    set(value) {
      if (
        field == value &&
          AndroidEditorSettings.getInstance().globalState.isPreviewEssentialsModeEnabled == value
      )
        return
      runWriteActionAndWait {
        AndroidEditorSettings.getInstance().globalState.isPreviewEssentialsModeEnabled = value
        ApplicationManager.getApplication()
          .messageBus
          .syncPublisher(NlOptionsConfigurable.Listener.TOPIC)
          .onOptionsChanged()
      }
      field = value
    }

  @Before
  fun setup() {
    logger.setLevel(LogLevel.ALL)
    Logger.getInstance(ComposePreviewRepresentation::class.java).setLevel(LogLevel.ALL)
    Logger.getInstance(FastPreviewManager::class.java).setLevel(LogLevel.ALL)
    Logger.getInstance(RenderingBuildStatus::class.java).setLevel(LogLevel.ALL)
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
    StudioFlags.COMPOSE_UI_CHECK_FOR_WEAR.clearOverride()
    composePreviewEssentialsModeEnabled = false
  }

  @Test
  fun testPreviewInitialization() = runComposePreviewRepresentationTest {
    val preview = createPreviewAndCompile()
    mainSurface.models.forEach {
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
  }

  @Test
  fun testPreviewRefreshMetricsAreTracked() = runComposePreviewRepresentationTest {
    try {
      AnalyticsSettings.optedIn = true
      var refreshTrackerFailed = false
      var successEventCount = 0
      val refreshTracker = PreviewRefreshTrackerForTest {
        if (
          it.result != PreviewRefreshEvent.RefreshResult.SUCCESS || it.previewRendersList.isEmpty()
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
      PreviewRefreshTracker.setInstanceForTest(mainSurface, refreshTracker)
      createPreviewAndCompile()

      waitForCondition(5.seconds) { successEventCount > 0 }
      assertFalse(refreshTrackerFailed)
    } finally {
      PreviewRefreshTracker.cleanAfterTesting(mainSurface)
      AnalyticsSettings.optedIn = false
    }
  }

  @Test
  fun testUiCheckMode() = runComposePreviewRepresentationTest {
    val originalScale = 0.6
    mainSurface.zoomController.setScale(originalScale)
    val preview = createPreviewAndCompile()
    assertInstanceOf<UiCheckModeFilter.Disabled<PsiComposePreviewElementInstance>>(
      preview.uiCheckFilterFlow.value
    )

    val previewElements = mainSurface.models.mapNotNull { it.dataProvider?.previewElement() }
    val uiCheckElement = previewElements.single { it.methodFqn == "TestKt.Preview1" }
    val problemsView = ProblemsView.getToolWindow(project)!!

    val contentManager = runBlocking(uiThread) { problemsView.contentManager }
    withContext(uiThread) {
      ProblemsViewToolWindowUtils.addTab(project, SharedIssuePanelProvider(project))
      assertEquals(1, contentManager.contents.size)
    }

    // Start UI Check mode
    setModeAndWaitForRefresh(
      PreviewMode.UiCheck(UiCheckInstance(uiCheckElement, isWearPreview = false))
    )

    assertInstanceOf<UiCheckModeFilter.Enabled<PsiComposePreviewElementInstance>>(
      preview.uiCheckFilterFlow.value
    )
    delayUntilCondition(250) {
      UI_CHECK_LAYOUT_OPTION == mainSurface.layoutManagerSwitcher?.currentLayout?.value
    }

    assertThat(preview.composePreviewFlowManager.availableGroupsFlow.value.map { it.displayName })
      .containsExactly("Screen sizes", "Font scales", "Light/Dark", "Colorblind filters")
      .inOrder()
    preview.renderedPreviewElementsInstancesFlowForTest().awaitStatus(
      "Failed set uiCheckMode",
      25.seconds,
    ) {
      it.asCollection().size > 2
    }
    assertEquals(
      """
          TestKt.Preview1
          spec:width=411dp,height=891dp
          PreviewDisplaySettings(name=Medium Phone - Preview1, baseName=Preview1, parameterName=Medium Phone, group=Screen sizes, showDecoration=true, showBackground=false, backgroundColor=null, displayPositioning=NORMAL, organizationGroup=Screen sizes)

          TestKt.Preview1
          spec:width=673dp,height=841dp
          PreviewDisplaySettings(name=Unfolded Foldable - Preview1, baseName=Preview1, parameterName=Unfolded Foldable, group=Screen sizes, showDecoration=true, showBackground=false, backgroundColor=null, displayPositioning=NORMAL, organizationGroup=Screen sizes)

          TestKt.Preview1
          spec:width=1280dp,height=800dp,dpi=240
          PreviewDisplaySettings(name=Medium Tablet - Preview1, baseName=Preview1, parameterName=Medium Tablet, group=Screen sizes, showDecoration=true, showBackground=false, backgroundColor=null, displayPositioning=NORMAL, organizationGroup=Screen sizes)

          TestKt.Preview1
          spec:width=1920dp,height=1080dp,dpi=160
          PreviewDisplaySettings(name=Desktop - Preview1, baseName=Preview1, parameterName=Desktop, group=Screen sizes, showDecoration=true, showBackground=false, backgroundColor=null, displayPositioning=NORMAL, organizationGroup=Screen sizes)

          TestKt.Preview1
          spec:parent=_device_class_phone,orientation=landscape
          PreviewDisplaySettings(name=Medium Phone-Landscape - Preview1, baseName=Preview1, parameterName=Medium Phone-Landscape, group=Screen sizes, showDecoration=true, showBackground=false, backgroundColor=null, displayPositioning=NORMAL, organizationGroup=Screen sizes)

          TestKt.Preview1
          PreviewDisplaySettings(name=85% - Preview1, baseName=Preview1, parameterName=85%, group=Font scales, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL, organizationGroup=Font scales)

          TestKt.Preview1
          PreviewDisplaySettings(name=100% - Preview1, baseName=Preview1, parameterName=100%, group=Font scales, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL, organizationGroup=Font scales)

          TestKt.Preview1
          PreviewDisplaySettings(name=115% - Preview1, baseName=Preview1, parameterName=115%, group=Font scales, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL, organizationGroup=Font scales)

          TestKt.Preview1
          PreviewDisplaySettings(name=130% - Preview1, baseName=Preview1, parameterName=130%, group=Font scales, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL, organizationGroup=Font scales)

          TestKt.Preview1
          PreviewDisplaySettings(name=180% - Preview1, baseName=Preview1, parameterName=180%, group=Font scales, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL, organizationGroup=Font scales)

          TestKt.Preview1
          PreviewDisplaySettings(name=200% - Preview1, baseName=Preview1, parameterName=200%, group=Font scales, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL, organizationGroup=Font scales)

          TestKt.Preview1
          PreviewDisplaySettings(name=Light - Preview1, baseName=Preview1, parameterName=Light, group=Light/Dark, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL, organizationGroup=Light/Dark)

          TestKt.Preview1
          PreviewDisplaySettings(name=Dark - Preview1, baseName=Preview1, parameterName=Dark, group=Light/Dark, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL, organizationGroup=Light/Dark)

          TestKt.Preview1
          PreviewDisplaySettings(name=Original - Preview1, baseName=Preview1, parameterName=Original, group=Colorblind filters, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL, organizationGroup=Colorblind filters)

          TestKt.Preview1
          PreviewDisplaySettings(name=Protanopes - Preview1, baseName=Preview1, parameterName=Protanopes, group=Colorblind filters, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL, organizationGroup=Colorblind filters)

          TestKt.Preview1
          PreviewDisplaySettings(name=Protanomaly - Preview1, baseName=Preview1, parameterName=Protanomaly, group=Colorblind filters, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL, organizationGroup=Colorblind filters)

          TestKt.Preview1
          PreviewDisplaySettings(name=Deuteranopes - Preview1, baseName=Preview1, parameterName=Deuteranopes, group=Colorblind filters, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL, organizationGroup=Colorblind filters)

          TestKt.Preview1
          PreviewDisplaySettings(name=Deuteranomaly - Preview1, baseName=Preview1, parameterName=Deuteranomaly, group=Colorblind filters, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL, organizationGroup=Colorblind filters)

          TestKt.Preview1
          PreviewDisplaySettings(name=Tritanopes - Preview1, baseName=Preview1, parameterName=Tritanopes, group=Colorblind filters, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL, organizationGroup=Colorblind filters)

          TestKt.Preview1
          PreviewDisplaySettings(name=Tritanomaly - Preview1, baseName=Preview1, parameterName=Tritanomaly, group=Colorblind filters, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL, organizationGroup=Colorblind filters)

        """
        .trimIndent(),
      preview.renderedPreviewElementsInstancesFlowForTest().value.asCollection().joinToString(
        "\n"
      ) {
        val configurationDeviceSpecText =
          "${it.configuration.deviceSpec}\n".takeIf { str -> str.isNotBlank() } ?: ""
        "${it.methodFqn}\n$configurationDeviceSpecText${it.displaySettings}\n"
      },
    )

    // Change the scale of the surface
    val scaleUpdate = originalScale + 0.5
    mainSurface.zoomController.setScale(scaleUpdate)

    // Check that the UI Check tab has been created
    assertEquals(2, contentManager.contents.size)
    assertEquals(uiCheckElement.instanceId, contentManager.selectedContent?.tabName)

    // Stop UI Check mode
    setModeAndWaitForRefresh(PreviewMode.Default())

    assertInstanceOf<UiCheckModeFilter.Disabled<PsiComposePreviewElementInstance>>(
      preview.uiCheckFilterFlow.value
    )
    delayUntilCondition(250) {
      DEFAULT_LAYOUT_OPTION == mainSurface.layoutManagerSwitcher?.currentLayout?.value
    }

    // Check that the surface zoom stays unchanged when exiting UI check mode.
    assertEquals(scaleUpdate, mainSurface.zoomController.scale, 0.001)

    preview.renderedPreviewElementsInstancesFlowForTest().awaitStatus(
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
      preview.renderedPreviewElementsInstancesFlowForTest().value.asCollection().joinToString(
        "\n"
      ) {
        "${it.methodFqn}\n${it.configuration.deviceSpec}\n"
      },
    )

    // Check that the UI Check tab is still present
    assertEquals(2, contentManager.contents.size)
    assertEquals(uiCheckElement.instanceId, contentManager.selectedContent?.tabName)

    // Restart UI Check mode on the same preview
    setModeAndWaitForRefresh(
      PreviewMode.UiCheck(UiCheckInstance(uiCheckElement, isWearPreview = false))
    ) {
      UI_CHECK_LAYOUT_OPTION == mainSurface.layoutManagerSwitcher?.currentLayout?.value
    }

    // Check that the UI Check tab is being reused
    assertEquals(2, contentManager.contents.size)
    assertEquals(uiCheckElement.instanceId, contentManager.selectedContent?.tabName)

    problemsView.show()
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

    // We set the modality state here because we're removing and recreating the tab using the
    // APIs from ProblemsViewToolWindowUtils, which use invokeLater when creating the components.
    // By setting the modality state to the problems view component, we'll make sure the runnable
    // below will execute only after the component is ready.
    withContext(uiThread(ModalityState.stateForComponent(problemsView.component))) {
      assertEquals(2, contentManager.contents.size)
      assertEquals(uiCheckElement.instanceId, contentManager.selectedContent?.tabName)
    }

    setModeAndWaitForRefresh(PreviewMode.Default()) {
      DEFAULT_LAYOUT_OPTION == mainSurface.layoutManagerSwitcher?.currentLayout?.value
    }
  }

  @Test
  fun testPreviewManagersShouldBeRegisteredInDataProvider() = runComposePreviewRepresentationTest {
    createPreviewAndCompile()
    assertTrue(getData(PreviewModeManager.KEY.name) is PreviewModeManager)
    assertTrue(getData(PreviewGroupManager.KEY.name) is PreviewGroupManager)
    assertTrue(getData(PreviewFlowManager.KEY.name) is PreviewFlowManager<*>)
    assertTrue(getData(PREVIEW_VIEW_MODEL_STATUS.name) is PreviewViewModelStatus)
    assertTrue(getData(FastPreviewSurface.KEY.name) is FastPreviewSurface)
    assertTrue(getData(PreviewInvalidationManager.KEY.name) is PreviewInvalidationManager)
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
      whenever(surfaceMock.analyticsManager).thenReturn(mock<NlAnalyticsManager>())
      whenever(surfaceMock.sceneManagers).thenReturn(ImmutableList.of())
      whenever(surfaceMock.pannable).thenReturn(TestPannable())
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
    project.putUserData(FileEditorManagerKeys.ALLOW_IN_LIGHT_PROJECT, true)
    project.replaceService(
      FileEditorManager::class.java,
      FileEditorManagerImpl(project, project.coroutineScope),
      projectRule.fixture.testRootDisposable,
    )
    HeadlessDataManager.fallbackToProductionDataManager(projectRule.fixture.testRootDisposable)

    val testPsiFile = runWriteActionAndWait {
      fixture.addFileToProjectAndInvalidate(
        "Test.kt",
        // language=kotlin
        """
            import androidx.compose.ui.tooling.preview.Devices
            import androidx.compose.ui.tooling.preview.Preview
            import androidx.compose.runtime.Composable

            @Composable
            @Preview
            @Preview(name = "preview2", apiLevel = 12, group = "groupA", showBackground = true)
            fun Preview() {
            }
          """
          .trimIndent(),
      )
    }
    testPsiFile.putUserData(FileEditorProvider.KEY, SourceCodeEditorProvider())

    val editor =
      runBlocking(uiThread) {
        val editor =
          withContext(uiThread) {
            val editors =
              FileEditorManager.getInstance(project).openFile(testPsiFile.virtualFile, true, true)
            (editors[0] as TextEditorWithMultiRepresentationPreview<*>)
          }
        delayUntilCondition(250) { editor.getPreviewManager<ComposePreviewManager>() != null }
        editor
      }

    val mainSurface = runBlocking(uiThread) { editor.getDesignSurface() as NlDesignSurface }

    runComposePreviewRepresentationTest(testPsiFile, mainSurface) {
      val preview =
        editor.getPreviewManager<ComposePreviewManager>() as ComposePreviewRepresentation
      createPreviewAndCompile(preview)

      // Start UI Check mode
      val previewElements = mainSurface.models.mapNotNull { it.dataProvider?.previewElement() }
      val uiCheckElement = previewElements[1]

      run {
        waitForAllRefreshesToFinish(30.seconds)
        preview.setMode(PreviewMode.UiCheck(UiCheckInstance(uiCheckElement, isWearPreview = false)))
        delayUntilCondition(250) { preview.uiCheckFilterFlow.value is UiCheckModeFilter.Enabled }
      }

      val contentManager =
        withContext(uiThread) {
          ToolWindowManager.getInstance(project).getToolWindow(ProblemsView.ID)!!.contentManager
        }
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
        waitForAllRefreshesToFinish(30.seconds)
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
      withContext(uiThread) {
        rerunAction.actionPerformed(TestActionEvent.createTestEvent(dataContext))
      }
      delayUntilCondition(250) {
        (preview.uiCheckFilterFlow.value as? UiCheckModeFilter.Enabled)?.basePreviewInstance ==
          uiCheckElement
      }

      // Check that the rerun action is disabled
      run {
        val actionEvent = withContext(uiThread) { TestActionEvent.createTestEvent(dataContext) }
        rerunAction.update(actionEvent)
        assertTrue(actionEvent.presentation.isVisible)
        assertFalse(actionEvent.presentation.isEnabled)
      }

      // Stop UI Check mode
      run {
        waitForAllRefreshesToFinish(30.seconds)
        preview.setMode(PreviewMode.Default())
        delayUntilCondition(250) { preview.uiCheckFilterFlow.value is UiCheckModeFilter.Disabled }
      }

      // Check that the rerun action is enabled
      run {
        val actionEvent = withContext(uiThread) { TestActionEvent.createTestEvent(dataContext) }
        rerunAction.update(actionEvent)
        assertTrue(actionEvent.presentation.isEnabledAndVisible)
      }

      // Delete the preview annotation that is linked with the UI check
      runWriteCommandAction(project) {
        testPsiFile
          .findElementByText(
            "@Preview(name = \"preview2\", apiLevel = 12, group = \"groupA\", showBackground = true)"
          )
          .delete()
      }

      // Check that the rerun action is hidden
      run {
        val actionEvent = withContext(uiThread) { TestActionEvent.createTestEvent(dataContext) }
        rerunAction.update(actionEvent)
        assertFalse(actionEvent.presentation.isVisible)
      }

      waitForAllRefreshesToFinish(30.seconds)
      withContext(uiThread) { FileEditorManagerEx.getInstanceEx(project).closeAllFiles() }
    }
  }

  @Test
  fun testInteractivePreviewManagerFpsLimitIsInitializedWhenEssentialsModeIsDisabled() =
    runComposePreviewRepresentationTest {
      val preview = createPreviewAndCompile()

      assertEquals(30, preview.interactiveManager.fpsLimit)
    }

  @Test
  fun testInteractivePreviewManagerFpsLimitIsInitializedWhenEssentialsModeIsEnabled() =
    runComposePreviewRepresentationTest {
      composePreviewEssentialsModeEnabled = true

      // Only one preview/model is shown in gallery mode
      val preview = createPreviewAndCompile(expectedModelCount = 1)

      assertEquals(10, preview.interactiveManager.fpsLimit)
    }

  @Test
  fun testInteractivePreviewManagerFpsLimitIsUpdatedWhenEssentialsModeChanges() =
    runComposePreviewRepresentationTest {
      val preview = createPreviewAndCompile()

      assertEquals(30, preview.interactiveManager.fpsLimit)

      composePreviewEssentialsModeEnabled = true
      delayUntilCondition(delayPerIterationMs = 500) { preview.interactiveManager.fpsLimit == 10 }

      composePreviewEssentialsModeEnabled = false
      delayUntilCondition(delayPerIterationMs = 500) { preview.interactiveManager.fpsLimit == 30 }
    }

  @Test
  fun testWearUiCheckMode() {
    StudioFlags.COMPOSE_UI_CHECK_FOR_WEAR.override(true)

    val testPsiFile = runWriteActionAndWait {
      fixture.addFileToProjectAndInvalidate(
        "Test.kt",
        // language=kotlin
        """
            import androidx.compose.ui.tooling.preview.Devices
            import androidx.compose.ui.tooling.preview.Preview
            import androidx.compose.runtime.Composable

            @Composable
            @Preview(device = "id:wearos_square")
            fun Preview() {
            }
          """
          .trimIndent(),
      )
    }

    runComposePreviewRepresentationTest(testPsiFile) {
      // The file above contains only 1 preview/model
      val preview = createPreviewAndCompile(expectedModelCount = 1)
      assertInstanceOf<UiCheckModeFilter.Disabled<PsiComposePreviewElementInstance>>(
        preview.uiCheckFilterFlow.value
      )

      val uiCheckElement = mainSurface.models.mapNotNull { it.dataProvider?.previewElement() }[0]
      val problemsView = ProblemsView.getToolWindow(project)!!

      val contentManager = runBlocking(uiThread) { problemsView.contentManager }
      withContext(uiThread) {
        ProblemsViewToolWindowUtils.addTab(project, SharedIssuePanelProvider(project))
        assertEquals(1, contentManager.contents.size)
      }

      // Start UI Check mode
      setModeAndWaitForRefresh(
        PreviewMode.UiCheck(UiCheckInstance(uiCheckElement, isWearPreview = true))
      )

      assertInstanceOf<UiCheckModeFilter.Enabled<PsiComposePreviewElementInstance>>(
        preview.uiCheckFilterFlow.value
      )
      delayUntilCondition(250) {
        UI_CHECK_LAYOUT_OPTION == mainSurface.layoutManagerSwitcher?.currentLayout?.value
      }

      assertThat(preview.composePreviewFlowManager.availableGroupsFlow.value.map { it.displayName })
        .containsExactly("Wear OS Devices")
      preview.renderedPreviewElementsInstancesFlowForTest().awaitStatus(
        "Failed set uiCheckMode",
        25.seconds,
      ) {
        it.asCollection().size > 2
      }
      assertEquals(
        """
          TestKt.Preview
          id:wearos_large_round
          PreviewDisplaySettings(name=Wear OS Large Round - Preview, baseName=Preview, parameterName=Wear OS Large Round, group=Wear OS Devices, showDecoration=true, showBackground=false, backgroundColor=null, displayPositioning=NORMAL, organizationGroup=null)

          TestKt.Preview
          id:wearos_small_round
          PreviewDisplaySettings(name=Wear OS Small Round - Preview, baseName=Preview, parameterName=Wear OS Small Round, group=Wear OS Devices, showDecoration=true, showBackground=false, backgroundColor=null, displayPositioning=NORMAL, organizationGroup=null)

          TestKt.Preview
          id:wearos_square
          PreviewDisplaySettings(name=Wear OS Square - Preview, baseName=Preview, parameterName=Wear OS Square, group=Wear OS Devices, showDecoration=true, showBackground=false, backgroundColor=null, displayPositioning=NORMAL, organizationGroup=null)

          TestKt.Preview
          id:wearos_rect
          PreviewDisplaySettings(name=Wear OS Rectangular - Preview, baseName=Preview, parameterName=Wear OS Rectangular, group=Wear OS Devices, showDecoration=true, showBackground=false, backgroundColor=null, displayPositioning=NORMAL, organizationGroup=null)

        """
          .trimIndent(),
        preview.renderedPreviewElementsInstancesFlowForTest().value.asCollection().joinToString(
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
      setModeAndWaitForRefresh(PreviewMode.Default())

      assertInstanceOf<UiCheckModeFilter.Disabled<PsiComposePreviewElementInstance>>(
        preview.uiCheckFilterFlow.value
      )
    }
  }

  // Regression test for b/353458840
  @Test
  fun multiPreviewsAreOrderedByNameWhenNotInUICheckMode() {
    val testPsiFile =
      fixture.addFileToProjectAndInvalidate(
        "Test.kt",
        // language=kotlin
        """
            import androidx.compose.ui.tooling.preview.Devices
            import androidx.compose.ui.tooling.preview.Preview
            import androidx.compose.runtime.Composable

            @Preview(name = "1", group = "2")
            @Preview(name = "2", group = "2")
            @Preview(name = "3", group = "3")
            @Preview(name = "4", group = "3")
            @Preview(name = "5", group = "1")
            @Preview(name = "6", group = "1")
            annotation class MyMultiPreview

            @Composable
            @Preview
            fun Preview() {
            }

            @Composable
            @MyMultiPreview
            fun MultiPreview() {
            }
          """
          .trimIndent(),
      )

    runComposePreviewRepresentationTest(testPsiFile) {
      val preview = createPreviewAndCompile()

      assertEquals(
        """
          TestKt.Preview
          PreviewDisplaySettings(name=Preview, baseName=Preview, parameterName=null, group=null, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL, organizationGroup=null)

          TestKt.MultiPreview
          PreviewDisplaySettings(name=MultiPreview - 1, baseName=MultiPreview, parameterName=1, group=2, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL, organizationGroup=null)

          TestKt.MultiPreview
          PreviewDisplaySettings(name=MultiPreview - 2, baseName=MultiPreview, parameterName=2, group=2, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL, organizationGroup=null)

          TestKt.MultiPreview
          PreviewDisplaySettings(name=MultiPreview - 3, baseName=MultiPreview, parameterName=3, group=3, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL, organizationGroup=null)

          TestKt.MultiPreview
          PreviewDisplaySettings(name=MultiPreview - 4, baseName=MultiPreview, parameterName=4, group=3, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL, organizationGroup=null)

          TestKt.MultiPreview
          PreviewDisplaySettings(name=MultiPreview - 5, baseName=MultiPreview, parameterName=5, group=1, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL, organizationGroup=null)

          TestKt.MultiPreview
          PreviewDisplaySettings(name=MultiPreview - 6, baseName=MultiPreview, parameterName=6, group=1, showDecoration=false, showBackground=false, backgroundColor=null, displayPositioning=NORMAL, organizationGroup=null)

        """
          .trimIndent(),
        preview.renderedPreviewElementsInstancesFlowForTest().value.asCollection().joinToString(
          "\n"
        ) {
          "${it.methodFqn}\n${it.displaySettings}\n"
        },
      )
    }
  }

  private fun runComposePreviewRepresentationTest(
    previewPsiFile: PsiFile = createPreviewPsiFile(),
    mainSurface: NlDesignSurface =
      NlSurfaceBuilder.builder(fixture.project, fixture.testRootDisposable).build(),
    block: suspend ComposePreviewRepresentationTestContext.() -> Unit,
  ) {
    val context =
      ComposePreviewRepresentationTestContext(
        previewPsiFile,
        mainSurface,
        fixture,
        logger,
        projectRule.buildSystemServices,
      )
    runBlocking(workerThread) {
      try {
        context.block()
      } finally {
        context.cleanup()
      }
    }
  }

  private fun createPreviewPsiFile(): PsiFile {
    return runWriteActionAndWait {
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
  }

  /**
   * Wrapper class to perform operations and expose properties that are common to most tests in this
   * test class.
   */
  private class ComposePreviewRepresentationTestContext(
    private val previewPsiFile: PsiFile,
    val mainSurface: NlDesignSurface,
    private val fixture: CodeInsightTestFixture,
    private val logger: Logger,
    private val buildSystemServices: FakeBuildSystemFilePreviewServices,
  ) {

    private lateinit var preview: ComposePreviewRepresentation

    private lateinit var composeView: TestComposePreviewView

    private lateinit var dataProvider: DataProvider

    private lateinit var newModelAddedLatch: CountDownLatch

    init {
      mainSurface.addListener(
        object : DesignSurfaceListener {
          override fun modelsChanged(surface: DesignSurface<*>, models: List<NlModel?>) {
            val id = UUID.randomUUID().toString().substring(0, 5)
            logger.info("modelChanged ($id)")
            repeat(models.size) { newModelAddedLatch.countDown() }
          }
        }
      )
    }

    suspend fun createPreviewAndCompile(
      previewOverride: ComposePreviewRepresentation? = null,
      expectedModelCount: Int = 2,
    ): ComposePreviewRepresentation {
      newModelAddedLatch = CountDownLatch(expectedModelCount)
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
        buildSystemServices.simulateArtifactBuild(ProjectSystemBuildManager.BuildStatus.SUCCESS)
        logger.info("activate")
        preview.onActivate()

        newModelAddedLatch.await()
        delayWhileRefreshingOrDumb(preview)
      }
      return preview
    }

    suspend fun setModeAndWaitForRefresh(
      previewMode: PreviewMode,
      // In addition to refresh, we can wait for another condition before returning.
      additionalCondition: () -> Boolean = { true },
    ) {
      waitForAllRefreshesToFinish(30.seconds)
      var refresh = false
      composeView.refreshCompletedListeners.add { refresh = true }
      preview.setMode(previewMode)
      delayUntilCondition(250) { refresh && additionalCondition() }
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

    fun cleanup() {
      if (::preview.isInitialized) {
        preview.onDeactivate()
      }
    }
  }
}
