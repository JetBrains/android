/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.ide.common.rendering.api.Bridge
import com.android.tools.analytics.UsageTracker
import com.android.tools.compose.COMPOSABLE_ANNOTATION_FQ_NAME
import com.android.tools.compose.COMPOSABLE_ANNOTATION_NAME
import com.android.tools.compose.COMPOSE_VIEW_ADAPTER_FQN
import com.android.tools.idea.common.error.DesignerCommonIssuePanel
import com.android.tools.idea.common.model.AccessibilityModelUpdater
import com.android.tools.idea.common.model.DefaultModelUpdater
import com.android.tools.idea.common.model.NlDataProvider
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.model.NlModelUpdaterInterface
import com.android.tools.idea.common.surface.DelegateInteractionHandler
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.updateSceneViewVisibilities
import com.android.tools.idea.compose.PsiComposePreviewElementInstance
import com.android.tools.idea.compose.preview.actions.RESIZE_PANEL_INSTANCE_KEY
import com.android.tools.idea.compose.preview.analytics.AnimationToolingUsageTracker
import com.android.tools.idea.compose.preview.animation.ComposeAnimationPreview
import com.android.tools.idea.compose.preview.animation.ComposeAnimationSubscriber
import com.android.tools.idea.compose.preview.animation.ComposeAnimationTracker
import com.android.tools.idea.compose.preview.flow.ComposePreviewFlowManager
import com.android.tools.idea.compose.preview.navigation.ComposePreviewNavigationHandler
import com.android.tools.idea.compose.preview.resize.ResizePanel
import com.android.tools.idea.compose.preview.scene.ComposeAnimationToolbarUpdater
import com.android.tools.idea.compose.preview.scene.ComposeSceneComponentProvider
import com.android.tools.idea.compose.preview.scene.ComposeScreenViewProvider
import com.android.tools.idea.compose.preview.uicheck.UiCheckPanelProvider
import com.android.tools.idea.compose.preview.util.containsOffset
import com.android.tools.idea.compose.preview.util.isFastPreviewAvailable
import com.android.tools.idea.concurrency.AndroidCoroutinesAware
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.concurrency.FlowableCollection
import com.android.tools.idea.concurrency.asCollection
import com.android.tools.idea.concurrency.launchWithProgress
import com.android.tools.idea.editors.build.PsiCodeFileOutOfDateStatusReporter
import com.android.tools.idea.editors.build.RenderingBuildStatus
import com.android.tools.idea.editors.build.RenderingBuildStatusManager
import com.android.tools.idea.editors.shortcuts.getBuildAndRefreshShortcut
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.flags.StudioFlags.COMPOSE_INTERACTIVE_FPS_LIMIT
import com.android.tools.idea.log.LoggerWithFixedInfo
import com.android.tools.idea.preview.Colors
import com.android.tools.idea.preview.DefaultRenderQualityManager
import com.android.tools.idea.preview.DefaultRenderQualityPolicy
import com.android.tools.idea.preview.NavigatingInteractionHandler
import com.android.tools.idea.preview.PreviewBuildListenersManager
import com.android.tools.idea.preview.PreviewInvalidationManager
import com.android.tools.idea.preview.PreviewPreloadClasses.INTERACTIVE_CLASSES_TO_PRELOAD
import com.android.tools.idea.preview.PreviewRefreshManager
import com.android.tools.idea.preview.RenderQualityManager
import com.android.tools.idea.preview.SimpleRenderQualityManager
import com.android.tools.idea.preview.actions.BuildAndRefresh
import com.android.tools.idea.preview.analytics.InteractivePreviewUsageTracker
import com.android.tools.idea.preview.analytics.PreviewRefreshEventBuilder
import com.android.tools.idea.preview.essentials.PreviewEssentialsModeManager
import com.android.tools.idea.preview.essentials.essentialsModeFlow
import com.android.tools.idea.preview.fast.CommonFastPreviewSurface
import com.android.tools.idea.preview.fast.FastPreviewSurface
import com.android.tools.idea.preview.find.findAnnotatedMethodsValues
import com.android.tools.idea.preview.flow.PreviewFlowManager
import com.android.tools.idea.preview.focus.CommonFocusEssentialsModeManager
import com.android.tools.idea.preview.focus.FocusMode
import com.android.tools.idea.preview.getDefaultPreviewQuality
import com.android.tools.idea.preview.groups.PreviewGroupManager
import com.android.tools.idea.preview.interactive.InteractivePreviewManager
import com.android.tools.idea.preview.interactive.fpsLimitFlow
import com.android.tools.idea.preview.lifecycle.PreviewLifecycleManager
import com.android.tools.idea.preview.modes.CommonPreviewModeManager
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.android.tools.idea.preview.mvvm.PREVIEW_VIEW_MODEL_STATUS
import com.android.tools.idea.preview.pagination.PreviewPaginationManager
import com.android.tools.idea.preview.representation.CommonPreviewStateManager
import com.android.tools.idea.preview.representation.PREVIEW_ELEMENT_INSTANCE
import com.android.tools.idea.preview.uicheck.UiCheckModeFilter
import com.android.tools.idea.projectsystem.needsBuild
import com.android.tools.idea.rendering.isErrorResult
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreferredVisibility
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentation
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentationState
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.scene.accessibilityBasedHierarchyParser
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.visual.analytics.VisualLintUsageTracker
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintMode
import com.android.tools.idea.util.runWhenSmartAndSynced
import com.android.tools.idea.util.toDisplayString
import com.android.tools.preview.ComposePreviewElementInstance
import com.android.tools.preview.PreviewDisplaySettings
import com.android.tools.rendering.RenderAsyncActionExecutor.RenderingTopic
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.ComposePreviewLiteModeEvent
import com.intellij.analysis.problemsView.toolWindow.ProblemsViewToolWindowUtils
import com.intellij.ide.ActivityTracker
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.ui.AncestorListenerAdapter
import com.intellij.util.messages.Topic
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.event.AncestorEvent
import kotlin.properties.Delegates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.psi.KtFile

/** [Notification] group ID. Must match the `groupNotification` entry of `compose-designer.xml`. */
const val PREVIEW_NOTIFICATION_GROUP_ID = "Compose Preview Notification"

/**
 * [NlModel.NlModelUpdaterInterface] to be used for updating the Compose model from the Compose
 * render result, using the [View] hierarchy.
 */
private val defaultModelUpdater: NlModelUpdaterInterface = DefaultModelUpdater()

/**
 * [NlModel.NlModelUpdaterInterface] to be used for updating the Compose model from the Compose
 * render result, using the [AccessibilityNodeInfo] hierarchy.
 */
private val accessibilityModelUpdater: NlModelUpdaterInterface = AccessibilityModelUpdater()

/**
 * [NlModel] associated preview data
 *
 * @param project the [Project] used by the current view.
 * @param composePreviewManager [ComposePreviewManager] of the Preview.
 * @param previewFlowManager the [PreviewFlowManager] that manages flows of
 *   [ComposePreviewElementInstance]
 * @param previewElement the [ComposePreviewElementInstance] associated to this model
 * @param fastPreviewSurface the [FastPreviewSurface] of the preview
 */
private fun createPreviewElementDataProvider(
  project: Project,
  composePreviewManager: ComposePreviewManager,
  previewFlowManager: PreviewFlowManager<out ComposePreviewElementInstance<*>>,
  previewElement: PsiComposePreviewElementInstance,
  fastPreviewSurface: FastPreviewSurface,
) =
  object :
    NlDataProvider(
      COMPOSE_PREVIEW_MANAGER,
      PreviewModeManager.KEY,
      PreviewGroupManager.KEY,
      PreviewPaginationManager.KEY,
      PreviewFlowManager.KEY,
      PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE,
      PREVIEW_ELEMENT_INSTANCE,
      CommonDataKeys.PROJECT,
      PREVIEW_VIEW_MODEL_STATUS,
      FastPreviewSurface.KEY,
      PreviewInvalidationManager.KEY,
    ) {
    override fun getData(dataId: String): Any? =
      when (dataId) {
        COMPOSE_PREVIEW_MANAGER.name,
        PreviewModeManager.KEY.name -> composePreviewManager
        PreviewPaginationManager.KEY.name -> previewFlowManager.previewFlowPaginator
        PreviewGroupManager.KEY.name,
        PreviewFlowManager.KEY.name -> previewFlowManager
        PSI_COMPOSE_PREVIEW_ELEMENT_INSTANCE.name,
        PREVIEW_ELEMENT_INSTANCE.name -> previewElement
        CommonDataKeys.PROJECT.name -> project
        PREVIEW_VIEW_MODEL_STATUS.name -> composePreviewManager.status()
        FastPreviewSurface.KEY.name -> fastPreviewSurface
        PreviewInvalidationManager.KEY.name -> composePreviewManager
        else -> null
      }
  }

/**
 * Sets up the given [sceneManager] with the right values to work on the Compose Preview. Currently,
 * this will configure if the preview elements will be displayed with "full device size" or simply
 * containing the previewed components (shrink mode).
 *
 * @param showDecorations when true, the rendered content will be shown with the full device size
 *   specified in the device configuration and with the frame decorations.
 * @param isInteractive whether the scene displays an interactive preview.
 * @param runVisualAnalysis whether to run Accessibility checks and Visual Lint analysis on the
 *   preview after it has been rendered. This will run the ATF scanner to detect issues affecting
 *   accessibility (e.g. low contrast, missing content description...), and the Visual Lint
 *   analyzers that are enabled to detect issues (e.g. components too wide, text too long...).
 */
@VisibleForTesting
fun configureLayoutlibSceneManager(
  sceneManager: LayoutlibSceneManager,
  showDecorations: Boolean,
  isInteractive: Boolean,
  requestPrivateClassLoader: Boolean,
  runVisualAnalysis: Boolean,
  quality: Float,
  disableAnimation: Boolean,
): LayoutlibSceneManager =
  sceneManager.apply {
    sceneRenderConfiguration.let { config ->
      config.useTransparentRendering = !showDecorations
      config.useShrinkRendering = !showDecorations
      // When the cache successful render image is enabled, the scene manager will retain the last
      // valid image even if subsequent renders fail. But do not cache in interactive mode as it
      // does not help, and it would make unnecessary copies of the bitmap.
      config.cacheSuccessfulRenderImage = !isInteractive
      config.classesToPreload = if (isInteractive) INTERACTIVE_CLASSES_TO_PRELOAD else emptyList()
      config.usePrivateClassLoader = requestPrivateClassLoader
      config.showDecorations = showDecorations
      // The Compose Preview has its own way to track out of date files so we ask the Layoutlib
      // Scene Manager to not report it via the regular log.
      config.reportOutOfDateUserClasses = false
      config.quality = quality
      config.customContentHierarchyParser =
        if (runVisualAnalysis) accessibilityBasedHierarchyParser else null
      config.layoutScannerConfig.isLayoutScannerEnabled = runVisualAnalysis
      // During configure of SceneManager, always clear the override render size in SceneManagers,
      // as they are reused and may have old resize data.
      config.clearOverrideRenderSize = true
      config.disableAnimation = disableAnimation
    }
    visualLintMode =
      if (runVisualAnalysis) {
        VisualLintMode.RUN_ON_PREVIEW_ONLY
      } else {
        VisualLintMode.DISABLED
      }
  }

/**
 * A [PreviewRepresentation] that provides a compose elements preview representation of the given
 * `psiFile`.
 *
 * A [component] is implied to display previews for all declared `@Composable` functions that also
 * use the `@Preview` (see [com.android.tools.compose.COMPOSE_PREVIEW_ANNOTATION_FQN]) annotation.
 * For every preview element a small XML is generated that allows Layoutlib to render a
 * `@Composable` functions.
 *
 * @param psiFile [PsiFile] pointing to the Kotlin source containing the code to preview.
 * @param preferredInitialVisibility preferred [PreferredVisibility] for this representation.
 * @param composePreviewViewProvider [ComposePreviewView] provider.
 */
class ComposePreviewRepresentation(
  psiFile: PsiFile,
  override val preferredInitialVisibility: PreferredVisibility,
  composePreviewViewProvider: ComposePreviewViewProvider,
) :
  PreviewRepresentation,
  ComposePreviewManagerEx,
  UserDataHolderEx by UserDataHolderBase(),
  AndroidCoroutinesAware,
  FastPreviewSurface {

  private val log = Logger.getInstance(ComposePreviewRepresentation::class.java)
  private val isDisposed = AtomicBoolean(false)

  private val psiFilePointer = runReadAction { SmartPointerManager.createPointer(psiFile) }
  private val project
    get() = psiFilePointer.project

  override val caretNavigationHandler = CaretNavigationHandlerImpl()

  private val previewBuildListenersManager =
    PreviewBuildListenersManager(
      isFastPreviewSupported = true,
      ::invalidate,
      ::requestRefresh,
      ::requestVisibilityAndNotificationsUpdate,
    )

  private val refreshManager = PreviewRefreshManager.getInstance(RenderingTopic.COMPOSE_PREVIEW)

  private val lifecycleManager =
    PreviewLifecycleManager(
      project,
      parentScope = this,
      onInitActivate = { activate(false) },
      onResumeActivate = { activate(true) },
      onDeactivate = {
        qualityPolicy.deactivate()
        allowQualityChangeIfInactive.set(true)
        requestRefresh(type = ComposePreviewRefreshType.QUALITY)
        log.debug("onDeactivate")
        if (mode.value is PreviewMode.Interactive) {
          interactiveManager.pause()
        }
        // The editor is scheduled to be deactivated, deactivate its issue model to avoid
        // updating publish the issue update event.
        surface.deactivateIssueModel()
      },
      onDelayedDeactivate = {
        // Deactivating the surface below will deactivate all SceneManager instances, and that will
        // dispose all their associated RenderTask instances, meaning that a full refresh will be
        // needed when reactivating
        invalidated.set(true)
        // If currently selected mode is not Normal mode, switch for Default normal mode.
        if (!mode.value.isNormal) previewModeManager.setMode(PreviewMode.Default())
        log.debug("Delayed surface deactivation")
        surface.deactivate()
      },
    )

  /**
   * Gives access to the rendered preview elements. For testing only. Users of this class should not
   * use this method.
   */
  @TestOnly
  fun renderedPreviewElementsInstancesFlowForTest() =
    composePreviewFlowManager.renderedPreviewElementsFlow

  private val renderingBuildStatusManager = RenderingBuildStatusManager.create(this, psiFile)

  /**
   * This field will be false until the preview has rendered at least once. If the preview has not
   * rendered once we do not have enough information about errors and the rendering to show the
   * preview. Once it has rendered, even with errors, we can display additional information about
   * the state of the preview.
   */
  private val hasRenderedAtLeastOnce = AtomicBoolean(false)

  /**
   * Indicates if a preview mode change is in progress. This field is set to true from the time
   * [PreviewModeManager.setMode] is called until the preview has finished rendering with the new
   * [PreviewMode].
   */
  private val isPreviewModeChanging = AtomicBoolean(true)

  @VisibleForTesting internal val composePreviewFlowManager = ComposePreviewFlowManager()

  /** Whether the preview needs a full refresh or not. */
  private val invalidated = AtomicBoolean(true)

  /**
   * Preview element provider corresponding to the current state of the Preview. Different modes
   * might require a different provider to be set, e.g. UI check mode needs a provider that produces
   * previews with reference devices. When exiting the mode and returning to static preview, the
   * element provider should be reset to [defaultPreviewElementProvider].
   *
   * TODO(b/305011776): remove it to only use the flow from ComposePreviewFlowManager
   */
  val uiCheckFilterFlow
    @VisibleForTesting get() = composePreviewFlowManager.uiCheckFilterFlow

  @VisibleForTesting
  val navigationHandler =
    ComposePreviewNavigationHandler().apply {
      Disposer.register(this@ComposePreviewRepresentation, this)
    }

  /** Blank panel to display on top of the surface when transitioning between two [PreviewMode]s */
  private val blankTransitionPanel = JPanel()

  private val emptyUiCheckPanel =
    object : JPanel() {
      val label =
        JLabel(message("ui.check.mode.empty.message"), SwingConstants.CENTER).apply {
          isVisible = false
        }

      init {
        layout = BorderLayout()
        isOpaque = true
        isVisible = true
        add(label, BorderLayout.CENTER)
        addAncestorListener(
          object : AncestorListenerAdapter() {
            override fun ancestorAdded(event: AncestorEvent?) {
              background = mode.value.backgroundColor
            }

            override fun ancestorRemoved(event: AncestorEvent?) {
              if (parent == event?.ancestorParent) {
                label.isVisible = false
              }
            }
          }
        )
      }

      fun setHasErrors(hasErrors: Boolean) {
        label.isVisible = !hasErrors
        isVisible = !hasErrors
      }
    }

  override var isUiCheckFilterEnabled: Boolean by
    Delegates.observable(true) { _, oldValue, newValue ->
      if (oldValue == newValue) return@observable
      launch(uiThread) {
        var hasVisiblePreviews = false
        if (newValue) {
          surface.updateSceneViewVisibilities {
            (uiCheckFilterFlow.value.modelsWithErrors?.contains(it.sceneManager.model) == true)
              .also { visible -> hasVisiblePreviews = hasVisiblePreviews || visible }
          }
        } else {
          hasVisiblePreviews = true
          surface.updateSceneViewVisibilities { true }
        }
        emptyUiCheckPanel.setHasErrors(hasVisiblePreviews)
      }
    }

  private val postIssueUpdateListenerForUiCheck =
    object : Runnable {
      private var activated = false

      fun activate() {
        if (!activated) {
          activated = true
          uiCheckFilterFlow.value.modelsWithErrors = null
        }
      }

      fun deactivate() {
        activated = false
      }

      override fun run() {
        if (!activated) {
          return
        }
        val models = mutableSetOf<NlModel>()
        val facet = surface.models.firstOrNull()?.facet
        surface.visualLintIssueProvider
          .getUnsuppressedIssues()
          .map { it.source }
          .forEach { models.addAll(it.models) }
        if (models == uiCheckFilterFlow.value.modelsWithErrors) {
          // No changes in which models have error, so no need to recompute preview visibilities
          return
        }
        uiCheckFilterFlow.value.modelsWithErrors = models
        if (isUiCheckFilterEnabled) {
          ApplicationManager.getApplication().invokeLater {
            var count = 0
            surface.updateSceneViewVisibilities {
              (it.sceneManager.model in models).also { visible -> if (visible) count++ }
            }
            emptyUiCheckPanel.setHasErrors(count > 0)
            VisualLintUsageTracker.getInstance().trackVisiblePreviews(count, facet)
            val totalContentToShow = surface.sceneManagers.flatMap { it.sceneViews }.size
            // If after applying the filter the number of Previews to show is different we need to
            // re-apply zoom-to-fit.
            if (count != totalContentToShow) {
              surface.zoomController.zoomToFit()
            }
            surface.repaint()
          }
        } else {
          emptyUiCheckPanel.isVisible = false
        }
      }
    }

  private val previewElementModelAdapter =
    object : ComposePreviewElementModelAdapter() {
      override fun createDataProvider(previewElement: PsiComposePreviewElementInstance) =
        createPreviewElementDataProvider(
          project,
          this@ComposePreviewRepresentation,
          composePreviewFlowManager,
          previewElement,
          this@ComposePreviewRepresentation,
        )

      override fun toXml(previewElement: PsiComposePreviewElementInstance) =
        previewElement
          .toPreviewXml()
          // Whether to paint the debug boundaries or not
          .toolsAttribute("paintBounds", showDebugBoundaries.toString())
          .apply {
            if (mode.value is PreviewMode.AnimationInspection) {
              // If the animation inspection is active, start the PreviewAnimationClock with
              // the current epoch time.
              toolsAttribute("animationClockStartTime", System.currentTimeMillis().toString())
            }
          }
          .buildString()
    }

  private suspend fun startInteractivePreview(instance: ComposePreviewElementInstance<*>) {
    log.debug("New single preview element focus: $instance")
    requestVisibilityAndNotificationsUpdate()
    // We should call this before assigning the instance to singlePreviewElementInstance
    val peerPreviews = composePreviewFlowManager.previewsCount()
    val quickRefresh = peerPreviews == 1
    sceneComponentProvider.enabled = false
    val startUpStart = System.currentTimeMillis()
    invalidateAndRefresh(
      if (quickRefresh) ComposePreviewRefreshType.QUICK else ComposePreviewRefreshType.NORMAL
    )
    // Currently it will re-create classloader and will be slower than switch from static
    InteractivePreviewUsageTracker.getInstance(surface)
      .logStartupTime((System.currentTimeMillis() - startUpStart).toInt(), peerPreviews)
    interactiveManager.start()
    requestVisibilityAndNotificationsUpdate()
    ActivityTracker.getInstance().inc()
  }

  private suspend fun startUiCheckPreview(
    instance: PsiComposePreviewElementInstance,
    isWearPreview: Boolean,
  ) {
    log.debug("Starting UI check.")
    val startTime = System.currentTimeMillis()
    surface.resetColorBlindMode()
    uiCheckFilterFlow.value = UiCheckModeFilter.Enabled(instance, isWearPreview)
    withContext(uiThread) {
      emptyUiCheckPanel.apply {
        isVisible = true
        // Put this panel in the DRAG_LAYER so that it hides everything in the surface,
        // including the zoom toolbar.
        surface.layeredPane.add(this, JLayeredPane.DRAG_LAYER, 0)
      }
      createUiCheckTab(instance, isWearPreview)
      ProblemsViewToolWindowUtils.selectTab(project, instance.instanceId)
    }
    val completableDeferred =
      CompletableDeferred<Unit>().apply {
        invokeOnCompletion {
          postIssueUpdateListenerForUiCheck.activate()
          VisualLintUsageTracker.getInstance()
            .trackFirstRunTime(
              System.currentTimeMillis() - startTime,
              surface.models.firstOrNull()?.facet,
            )
        }
      }
    invalidate()
    requestRefresh(completableDeferred = completableDeferred)
    completableDeferred.join()
  }

  fun createUiCheckTab(instance: ComposePreviewElementInstance<*>, isWearPreview: Boolean) {
    val uiCheckIssuePanel = UiCheckPanelProvider(instance, isWearPreview, project).getPanel()
    uiCheckIssuePanel.issueProvider.registerUpdateListener(postIssueUpdateListenerForUiCheck)
    uiCheckIssuePanel.issueProvider.activate()
    uiCheckIssuePanel.addIssueSelectionListener(surface.issueListener, surface)
    (surface.visualLintIssueProvider as? ComposeVisualLintIssueProvider)?.onUiCheckStart(
      instance.instanceId
    )
  }

  private suspend fun onUiCheckPreviewStop() {
    postIssueUpdateListenerForUiCheck.deactivate()
    uiCheckFilterFlow.value.basePreviewInstance?.let { uiCheckPanelCleanup(it.instanceId) }
    (surface.visualLintIssueProvider as? ComposeVisualLintIssueProvider)?.onUiCheckStop()
    uiCheckFilterFlow.value = UiCheckModeFilter.Disabled()
    surface.resetColorBlindMode()
    withContext(uiThread) {
      surface.layeredPane.remove(emptyUiCheckPanel)
      surface.updateSceneViewVisibilities { true }
    }
  }

  private fun uiCheckPanelCleanup(instanceId: String) {
    val panel =
      ProblemsViewToolWindowUtils.getTabById(project, instanceId) as? DesignerCommonIssuePanel
    panel?.removeIssueSelectionListener(surface.issueListener)
    panel?.issueProvider?.removeUpdateListener(postIssueUpdateListenerForUiCheck)
    panel?.issueProvider?.deactivate()
  }

  private fun onInteractivePreviewStop() {
    requestVisibilityAndNotificationsUpdate()
    interactiveManager.stop()
    invalidate()
  }

  private fun updateAnimationPanelVisibility() {
    if (!hasRenderedAtLeastOnce.get()) return

    // Always hide currentAnimationPreview if it's not PreviewMode.AnimationInspection even if
    // preview is not rendered yet.
    if (mode.value !is PreviewMode.AnimationInspection) {
      composeWorkBench.bottomPanel = null
    }
    composeWorkBench.bottomPanel =
      when {
        status().hasErrors || project.needsBuild -> null
        mode.value is PreviewMode.AnimationInspection -> currentAnimationPreview?.component
        else -> null
      }
  }

  override var showDebugBoundaries: Boolean = false
    set(value) {
      field = value
      invalidate()
      requestRefresh()
    }

  private val dataProvider = DataProvider {
    when (it) {
      COMPOSE_PREVIEW_MANAGER.name,
      PreviewModeManager.KEY.name -> this@ComposePreviewRepresentation
      PreviewPaginationManager.KEY.name -> composePreviewFlowManager.previewFlowPaginator
      PreviewGroupManager.KEY.name,
      PreviewFlowManager.KEY.name -> composePreviewFlowManager
      PlatformCoreDataKeys.BGT_DATA_PROVIDER.name -> DataProvider { slowId -> getSlowData(slowId) }
      CommonDataKeys.PROJECT.name -> project
      PREVIEW_VIEW_MODEL_STATUS.name -> status()
      FastPreviewSurface.KEY.name -> this@ComposePreviewRepresentation
      PreviewInvalidationManager.KEY.name -> this@ComposePreviewRepresentation
      RESIZE_PANEL_INSTANCE_KEY.name -> activeResizePanelInFocusMode
      else -> null
    }
  }

  private fun getSlowData(dataId: String): Any? {
    return when {
      // The Compose preview NlModels do not point to the actual file but to a synthetic file
      // generated for Layoutlib. This ensures we return the right file.
      CommonDataKeys.VIRTUAL_FILE.`is`(dataId) -> psiFilePointer.virtualFile
      else -> null
    }
  }

  private val delegateInteractionHandler = DelegateInteractionHandler()
  private val sceneComponentProvider = ComposeSceneComponentProvider()

  /**
   * Cached previous [ComposePreviewManager.Status] used to trigger notifications if there's been a
   * change.
   */
  private val previousStatusRef: AtomicReference<ComposePreviewManager.Status?> =
    AtomicReference(null)

  private val composeWorkBench: ComposePreviewView =
    UIUtil.invokeAndWaitIfNeeded(
        Computable {
          composePreviewViewProvider.invoke(
            project,
            psiFilePointer,
            renderingBuildStatusManager,
            dataProvider,
            createMainDesignSurfaceBuilder(
              project,
              navigationHandler,
              delegateInteractionHandler,
              dataProvider, // Will be overridden by the preview provider
              this,
              sceneComponentProvider,
              ComposeScreenViewProvider(this),
            ) {
              mode.value is PreviewMode.Interactive
            },
            this,
          )
        }
      )
      .apply { mainSurface.background = Colors.DEFAULT_BACKGROUND_COLOR }
      .also {
        it.mainSurface.analyticsManager.setEditorFileTypeWithoutTracking(
          psiFilePointer.virtualFile,
          project,
        )
      }

  val staticNavHandler =
    NavigatingInteractionHandler(
      composeWorkBench.mainSurface,
      navigationHandler,
      isSelectionEnabled = true,
    )

  @VisibleForTesting
  val staticPreviewInteractionHandler =
    ComposeNavigationInteractionHandler(staticNavHandler).also {
      delegateInteractionHandler.delegate = it
    }

  private val fpsLimitFlow =
    essentialsModeFlow(project, this).fpsLimitFlow(this, COMPOSE_INTERACTIVE_FPS_LIMIT.get())

  @VisibleForTesting
  val interactiveManager =
    InteractivePreviewManager(
        composeWorkBench.mainSurface,
        fpsLimitFlow.value,
        { surface.sceneManagers },
        { InteractivePreviewUsageTracker.getInstance(surface) },
        delegateInteractionHandler,
      )
      .also { Disposer.register(this@ComposePreviewRepresentation, it) }

  @get:VisibleForTesting
  val surface: NlDesignSurface
    get() = composeWorkBench.mainSurface

  private val allowQualityChangeIfInactive = AtomicBoolean(false)
  private val qualityPolicy = DefaultRenderQualityPolicy {
    surface.zoomController.screenScalingFactor
  }
  private val qualityManager: RenderQualityManager =
    if (StudioFlags.PREVIEW_RENDER_QUALITY.get())
      DefaultRenderQualityManager(surface, qualityPolicy) {
        requestRefresh(type = ComposePreviewRefreshType.QUALITY)
      }
    else SimpleRenderQualityManager { getDefaultPreviewQuality() }

  private val myPsiCodeFileOutOfDateStatusReporter =
    PsiCodeFileOutOfDateStatusReporter.getInstance(project)

  private val previewModeManager: PreviewModeManager = CommonPreviewModeManager()

  private val focusEssentialsModeManager =
    CommonFocusEssentialsModeManager(
        project = psiFile.project,
        lifecycleManager = lifecycleManager,
        previewFlowManager = composePreviewFlowManager,
        previewModeManager = previewModeManager,
        onUpdatedFromPreviewEssentialsMode = {
          logComposePreviewLiteModeEvent(
            ComposePreviewLiteModeEvent.ComposePreviewLiteModeEventType.PREVIEW_LITE_MODE_SWITCH
          )
        },
        requestRefresh = ::requestRefresh,
      )
      .also { Disposer.register(this@ComposePreviewRepresentation, it) }

  private val hasPreviewsCachedValue = AtomicBoolean(false)

  private var _refreshIndicatorCallback = {}

  private val stateManager =
    CommonPreviewStateManager(
      surfaceProvider = { surface },
      currentGroupFilterProvider = { composePreviewFlowManager.getCurrentFilterAsGroup() },
      previewFlowManager = composePreviewFlowManager,
      previewModeManager = previewModeManager,
    )

  /**
   * The [ResizePanel] instance used when [PreviewMode.Focus] is active.
   *
   * This panel allows modifying the device configuration for the focused preview element. It is
   * `null` when not in Focus mode and its lifecycle (creation/disposal) is tied to entering and
   * exiting [PreviewMode.Focus].
   */
  private var activeResizePanelInFocusMode: ResizePanel? = null

  init {
    launch {
      // Keep track of the last mode that was set to ensure it is correctly disposed
      var lastMode: PreviewMode? = null

      previewModeManager.mode.collect {
        (it.selected as? PsiComposePreviewElementInstance).let { element ->
          composePreviewFlowManager.setSingleFilter(element)
        }

        if (PreviewModeManager.areModesOfDifferentType(lastMode, it)) {
          lastMode?.let { last -> onExit(last) }
          // The layout update needs to happen before onEnter, so that any zooming performed
          // in onEnter uses the correct preview layout when measuring scale.
          updateLayoutManager(it)
          // Sets that the mode is changing.
          isPreviewModeChanging.set(true)
          // A mode change requires recalculating zoom-to-fit, so the zoom notifier is reset.
          // However, a resize of the surface is not expected for all mode changes.
          surface.resetZoomToFitNotifier(
            shouldWaitForResize = it.expectResizeOnEnter(lastMode, project)
          )
          onEnter(it)
        } else {
          updateLayoutManager(it)
          if ((lastMode as? PreviewMode.Focus)?.isFocusModeWithDifferentTabs(it) == true) {
            // If both the previous and the current previewModes are of type Focus, but they
            // are showing two different previewElements (two different tabs), then we should
            // apply zoom-to-fit.
            // We don't need to wait for surface to resize because Focus mode has already run
            // onEnter() and the surface is already with the correct updated sizes.
            isPreviewModeChanging.set(true)
            surface.resetZoomToFitNotifier(false)
          }
        }
        lastMode = it
      }
    }

    launch {
      fpsLimitFlow.collect {
        interactiveManager.fpsLimit = it
        // When getting out of Essentials Mode, request a refresh
        if (!PreviewEssentialsModeManager.isEssentialsModeEnabled) requestRefresh()
      }
    }

    // When the preview is opened we must trigger an initial refresh. We wait for the
    // project to be smart and synced to do it.
    project.runWhenSmartAndSynced(
      this,
      {
        when (renderingBuildStatusManager.status) {
          // Do not refresh if we still need to build the project. Instead, only update the
          // empty panel and editor notifications if needed.
          RenderingBuildStatus.NeedsBuild -> requestVisibilityAndNotificationsUpdate()
          else -> requestRefresh()
        }
      },
    )
  }

  private suspend fun updateLayoutManager(mode: PreviewMode) {
    withContext(uiThread) {
      surface.layoutManagerSwitcher?.currentLayoutOption?.value = mode.layoutOption
    }
  }

  override val component: JComponent
    get() = composeWorkBench.component

  /**
   * Completes the initialization of the preview. This method is only called once after the first
   * [onActivate] happens.
   */
  private fun onInit() {
    log.debug("onInit")
    if (isDisposed.get()) {
      log.info("Preview was closed before the initialization completed.")
    }

    // This callback is passed to setupPreviewBuildListeners, which will check for its value to
    // decide if refresh should be called when the build fails (by default, we don't refresh). We
    // want that to happen if the animation inspection was open at the beginning of the build. This
    // ensures the animations panel is showed again after the build completes
    val shouldRefreshAfterBuildFailed = { mode.value is PreviewMode.AnimationInspection }
    previewBuildListenersManager.setupPreviewBuildListeners(
      disposable = this,
      psiFilePointer,
      shouldRefreshAfterBuildFailed,
    ) {
      composeWorkBench.updateProgress(message("panel.building"))
      // When building, invalidate the Animation Preview, since the animations are now obsolete and
      // new ones will be subscribed once build is complete and refresh is triggered.
      currentAnimationPreview?.invalidatePanel()
      requestVisibilityAndNotificationsUpdate()
    }
  }

  @TestOnly
  fun hasBuildListenerSetupFinished() = previewBuildListenersManager.buildListenerSetupFinished

  override fun onActivate() {
    lifecycleManager.activate()
  }

  private fun CoroutineScope.activate(resume: Boolean) {
    log.debug("onActivate")

    qualityPolicy.activate()
    requestRefresh(type = ComposePreviewRefreshType.QUALITY)

    composePreviewFlowManager.run {
      this@activate.initializeFlows(
        this@ComposePreviewRepresentation,
        previewModeManager,
        myPsiCodeFileOutOfDateStatusReporter,
        psiFilePointer,
        ::invalidate,
        ::requestRefresh,
        delegateFastPreviewSurface::requestFastPreviewRefreshSync,
        ::restorePrevious,
        { renderingBuildStatusManager.status },
        { composeWorkBench.updateVisibilityAndNotifications() },
      )
    }

    if (!resume) {
      onInit()
    }

    surface.activate()

    if (mode.value is PreviewMode.Interactive) {
      interactiveManager.resume()
    }

    // At this point everything have been initialized or re-activated. Now we need to check whether
    // a full refresh is needed or could be avoided, and all considered scenarios are listed below:
    // - First activation: initial state is invalidated=true, so a full refresh will happen
    // - Re-activation and build or fast compile happened while deactivated: build listeners should
    //   have invalidated this, and then a full refresh will happen
    // - Re-activation and any kotlin file out of date: fast compile will happen if fast preview is
    //   enabled, and then a full refresh will happen.
    // - Re-activation and any non-kotlin file out of date: manual invalidation done here and then
    //   a full refresh will happen
    if (myPsiCodeFileOutOfDateStatusReporter.outOfDateFiles.isNotEmpty()) invalidate()
    val anyKtFilesOutOfDate =
      myPsiCodeFileOutOfDateStatusReporter.outOfDateFiles.any { it is KtFile }
    if (isFastPreviewAvailable(project) && anyKtFilesOutOfDate) {
      // If any files are out of date, we force a refresh when re-activating. This allows us to
      // compile the changes if Fast Preview is enabled OR to refresh the preview elements in case
      // the annotations have changed.
      launch { delegateFastPreviewSurface.requestFastPreviewRefreshSync() }
    } else if (invalidated.get()) requestRefresh()

    // Focus mode should be updated only if Preview is active / in foreground.
    // It will help to avoid enabling Focus mode while Preview is inactive, as it will also save
    // this state for later to restore.
    focusEssentialsModeManager.activate()
  }

  override fun onDeactivate() {
    lifecycleManager.deactivate()
  }

  // endregion

  override fun dispose() {
    isDisposed.set(true)
    if (mode.value is PreviewMode.Interactive) {
      interactiveManager.stop()
    } else if (mode.value is PreviewMode.UiCheck) {
      uiCheckFilterFlow.value.basePreviewInstance?.let { uiCheckPanelCleanup(it.instanceId) }
    }
  }

  override val mode: StateFlow<PreviewMode>
    get() = previewModeManager.mode

  private fun hasErrorsAndNeedsBuild(): Boolean =
    composePreviewFlowManager.hasRenderedPreviewElements() &&
      (!hasRenderedAtLeastOnce.get() ||
        surface.sceneManagers.any { it.renderResult.isErrorResult(COMPOSE_VIEW_ADAPTER_FQN) })

  private fun hasSyntaxErrors(): Boolean =
    WolfTheProblemSolver.getInstance(project).isProblemFile(psiFilePointer.virtualFile)

  override fun status(): ComposePreviewManager.Status {
    val projectBuildStatus = renderingBuildStatusManager.status
    val isRefreshing =
      (refreshManager.refreshingTypeFlow.value != null ||
        DumbService.isDumb(project) ||
        projectBuildStatus == RenderingBuildStatus.Building)

    // If we are refreshing, we avoid spending time checking other conditions like errors or if the
    // preview is out of date.
    val newStatus =
      ComposePreviewManager.Status(
        hasRenderErrors = !isRefreshing && hasErrorsAndNeedsBuild(),
        hasSyntaxErrors = !isRefreshing && hasSyntaxErrors(),
        isOutOfDate =
          !isRefreshing &&
            (projectBuildStatus is RenderingBuildStatus.OutOfDate ||
              projectBuildStatus is RenderingBuildStatus.NeedsBuild),
        areResourcesOutOfDate =
          !isRefreshing &&
            (projectBuildStatus as? RenderingBuildStatus.OutOfDate)?.areResourcesOutOfDate ?: false,
        isRefreshing = isRefreshing,
        psiFilePointer = psiFilePointer,
      )

    // This allows us to display notifications synchronized with any other change detection. The
    // moment we detect a difference, we immediately ask the editor to refresh the notifications.
    // For example, IntelliJ will periodically update the toolbar. If one of the actions checks the
    // state and changes its UI, this will allow for notifications to be refreshed at the same time.
    val previousStatus = previousStatusRef.getAndSet(newStatus)
    if (newStatus != previousStatus) {
      requestVisibilityAndNotificationsUpdate()
    }

    return newStatus
  }

  /**
   * Method called when the notifications of the [PreviewRepresentation] need to be updated. This is
   * called by the [ComposeNewPreviewNotificationProvider] when the editor needs to refresh the
   * notifications.
   */
  override fun updateNotifications(parentEditor: FileEditor) =
    composeWorkBench.updateNotifications(parentEditor)

  private fun configureLayoutlibSceneManagerForPreviewElement(
    displaySettings: PreviewDisplaySettings,
    layoutlibSceneManager: LayoutlibSceneManager,
  ) =
    configureLayoutlibSceneManager(
      layoutlibSceneManager,
      showDecorations = displaySettings.showDecoration,
      isInteractive = mode.value is PreviewMode.Interactive,
      requestPrivateClassLoader = usePrivateClassLoader(),
      runVisualAnalysis = mode.value is PreviewMode.UiCheck,
      quality = qualityManager.getTargetQuality(layoutlibSceneManager),
      disableAnimation = mode.value.isNormal,
    )

  private fun onAfterRender(previewsCount: Int) {
    composeWorkBench.hasRendered = true
    surface.sceneManagers.forEach {
      ComposeAnimationToolbarUpdater.update(this, it) {
        AnimationToolingUsageTracker.getInstance(surface)
      }
    }

    // Only update the hasRenderedAtLeastOnce field if we rendered at least one preview. Otherwise,
    // we might end up triggering unwanted behaviors (e.g. zooming incorrectly) when refresh happens
    // with 0 previews, e.g. when the panel is initializing. hasRenderedAtLeastOnce is also checked
    // when updating the animation panel visibility and when looking for render errors, which can
    // only happen if at least one preview is (attempted to be) rendered.
    if (previewsCount > 0) {
      if (!hasRenderedAtLeastOnce.getAndSet(true)) {
        logComposePreviewLiteModeEvent(
          ComposePreviewLiteModeEvent.ComposePreviewLiteModeEventType.OPEN_AND_RENDER
        )
      }
      // If this render was triggered by a mode change, check if we have stored scale, if we do we
      // try to restore it.
      // Otherwise, we notify to surface to apply zoom-to-fit.
      if (isPreviewModeChanging.getAndSet(false)) {
        launch(uiThread) {
          if (!surface.restorePreviousScale()) {
            surface.notifyZoomToFit()
          }
        }
      }
      updateResizePanel()
    }
  }

  /**
   * Updates the [activeResizePanelInFocusMode] with the currently focused [LayoutlibSceneManager].
   * This is called after a render completes in Focus mode.
   */
  private fun updateResizePanel() {
    activeResizePanelInFocusMode?.let { panel ->
      val focusedSceneManager = surface.sceneManagers.singleOrNull()
      if (focusedSceneManager != null) {
        panel.setSceneManager(focusedSceneManager)
      } else {
        log.error("activeResizePanelInFocusMode is not null, but there are no single scene manager")
      }
    }
  }

  /**
   * Logs a [ComposePreviewLiteModeEvent], which should happen after the first render and when the
   * user enables or disables Compose Preview Essentials Mode.
   */
  private fun logComposePreviewLiteModeEvent(
    eventType: ComposePreviewLiteModeEvent.ComposePreviewLiteModeEventType?
  ) {
    if (eventType == null) return
    ApplicationManager.getApplication().executeOnPooledThread {
      UsageTracker.log(
        AndroidStudioEvent.newBuilder()
          .setKind(AndroidStudioEvent.EventKind.COMPOSE_PREVIEW_LITE_MODE)
          .setComposePreviewLiteModeEvent(
            ComposePreviewLiteModeEvent.newBuilder()
              .setType(eventType)
              .setIsComposePreviewLiteMode(PreviewEssentialsModeManager.isEssentialsModeEnabled)
          )
      )
    }
  }

  /**
   * Refresh the preview surfaces. This will retrieve all the Preview annotations and render those
   * elements. The call will block until all the given [ComposePreviewElementInstance]s have
   * completed rendering. If [quickRefresh] is true the preview surfaces for the same
   * [ComposePreviewElementInstance]s do not get reinflated, this allows to save time for e.g.
   * static to animated preview transition. A [ProgressIndicator] that runs while refresh is in
   * progress is given, and this method should return early if the indicator is cancelled.
   */
  private suspend fun doRefreshSync(
    filteredPreviews: List<PsiComposePreviewElementInstance>,
    quickRefresh: Boolean,
    progressIndicator: ProgressIndicator,
    refreshEventBuilder: PreviewRefreshEventBuilder?,
  ) {
    val numberOfPreviewsToRender = filteredPreviews.size
    if (log.isDebugEnabled) log.debug("doRefresh of $numberOfPreviewsToRender elements.")
    val psiFile =
      readAction {
        val element = psiFilePointer.element

        return@readAction if (element == null || !element.isValid) {
          log.warn("doRefresh with invalid PsiFile")
          null
        } else {
          element
        }
      } ?: return

    // Restore
    stateManager.restoreState()

    hidePanelsBeforeRender()

    val showingPreviewElements =
      composeWorkBench.updatePreviewsAndRefresh(
        !quickRefresh,
        filteredPreviews,
        psiFile,
        progressIndicator,
        previewElementModelAdapter,
        if (mode.value is PreviewMode.UiCheck) accessibilityModelUpdater else defaultModelUpdater,
        navigationHandler,
        this::configureLayoutlibSceneManagerForPreviewElement,
        refreshEventBuilder,
      )

    onAfterRender(showingPreviewElements.size)

    if (previewModeManager.mode.value.isFocus) {
      // We need to get rid of the flickering when switching tabs in focus tabs b/287484743
      withContext(uiThread) { surface.notifyZoomToFit() }
    }

    composePreviewFlowManager.updateRenderedPreviews(showingPreviewElements)
    if (showingPreviewElements.size < numberOfPreviewsToRender) {
      // Some preview elements did not result in model creations. This could be because of failed
      // PreviewElements instantiation.
      // TODO(b/160300892): Add better error handling for failed instantiations.
      log.warn("Some preview elements have failed")
    }
    // Restoring the surface visibility after render as it may have been hidden in focus mode.
    surface.interactionPane.isVisible = true
  }

  /**
   * Hides the panels before rendering.
   *
   * We need to hide the interaction panel if in focus mode to avoid the flickering b/287484743.
   * Also, we need to clear and hide the resize panel before rendering otherwise it will be shown
   * during rendering as configuration can be updated.
   */
  private fun hidePanelsBeforeRender() {
    if (previewModeManager.mode.value.isFocus) {
      surface.interactionPane.isVisible = false
    }
    activeResizePanelInFocusMode?.clearPanelAndHidePanel()
  }

  private fun requestRefresh(
    type: ComposePreviewRefreshType = ComposePreviewRefreshType.NORMAL,
    completableDeferred: CompletableDeferred<Unit>? = null,
  ) {
    if (isDisposed.get()) {
      completableDeferred?.completeAlreadyDisposed()
      return
    }
    // Make sure not to allow quality change refreshes when the flag is disabled
    if (type == ComposePreviewRefreshType.QUALITY && !StudioFlags.PREVIEW_RENDER_QUALITY.get()) {
      completableDeferred?.completeExceptionally(IllegalStateException("Not enabled"))
      return
    }
    // Make sure not to request refreshes when deactivated, unless it is an allowed quality refresh,
    // which is expected to happen to decrease the quality of the previews when deactivating.
    if (
      !lifecycleManager.isActive() &&
        !(type == ComposePreviewRefreshType.QUALITY && allowQualityChangeIfInactive.get())
    ) {
      completableDeferred?.completeExceptionally(IllegalStateException("Not active"))
      return
    }

    refreshManager.requestRefresh(
      ComposePreviewRefreshRequest(
        surface,
        this.hashCode().toString(),
        ::refresh,
        completableDeferred,
        type,
      )
    )
  }

  @TestOnly
  fun requestRefreshForTest(
    type: ComposePreviewRefreshType = ComposePreviewRefreshType.NORMAL,
    completableDeferred: CompletableDeferred<Unit>? = null,
  ) = requestRefresh(type, completableDeferred)

  private fun requestVisibilityAndNotificationsUpdate() {
    if (!hasRenderedAtLeastOnce.get()) return

    composePreviewFlowManager.run {
      this@ComposePreviewRepresentation.updateVisibilityAndNotifications(
        ::updateAnimationPanelVisibility
      )
    }
  }

  /**
   * Completes the [CompletableDeferred] exceptionally with an [IllegalStateException] that says
   * this [ComposePreviewRepresentation] is already disposed.
   */
  private fun CompletableDeferred<Unit>.completeAlreadyDisposed() {
    this.completeExceptionally(IllegalStateException("Already disposed"))
  }

  @TestOnly
  fun updateRefreshIndicatorCallbackForTests(refreshIndicatorCallback: () -> Unit) {
    _refreshIndicatorCallback = refreshIndicatorCallback
  }

  /**
   * Requests a refresh the preview surfaces. This will retrieve all the Preview annotations and
   * render those elements. The refresh will only happen if the Preview elements have changed from
   * the last render.
   */
  private fun refresh(refreshRequest: ComposePreviewRefreshRequest): Job {
    if (isDisposed.get()) return CompletableDeferred<Unit>().also { it.completeAlreadyDisposed() }

    val requestLogger = LoggerWithFixedInfo(log, mapOf("requestId" to refreshRequest.requestId))
    requestLogger.debug(
      "Refresh triggered editor=${psiFilePointer.containingFile?.name}. Refresh type: ${refreshRequest.refreshType}"
    )
    val refreshTriggers: List<Throwable> = refreshRequest.requestSources

    // Make sure not to start refreshes when deactivated, unless it is the first quality refresh
    // that happens since deactivation. This is expected to happen to decrease the quality of its
    // previews when deactivating. Also, don't launch refreshes in the activation scope to avoid
    // cancelling the refresh mid-way when a simple tab change happens.
    if (
      !lifecycleManager.isActive() &&
        !(refreshRequest.refreshType == ComposePreviewRefreshType.QUALITY &&
          allowQualityChangeIfInactive.getAndSet(false))
    ) {
      requestLogger.debug(
        "Inactive representation (${psiFilePointer.containingFile?.name}), no work being done"
      )
      return CompletableDeferred(Unit)
    }

    // Return early when quality refresh won't actually refresh anything
    if (
      refreshRequest.refreshType == ComposePreviewRefreshType.QUALITY &&
        !qualityManager.needsQualityChange(surface)
    ) {
      return CompletableDeferred(Unit)
    }

    val startTime = System.nanoTime()
    // Start a progress indicator so users are aware that a long task is running. Stop it by calling
    // processFinish() if returning early.
    val refreshProgressIndicator =
      BackgroundableProcessIndicator(
        project,
        message(
          "refresh.progress.indicator.title",
          psiFilePointer.containingFile?.let { " (${it.name})" } ?: "",
        ),
        "",
        "",
        true,
      )
    _refreshIndicatorCallback()
    if (!Disposer.tryRegister(this, refreshProgressIndicator)) {
      refreshProgressIndicator.processFinish()
      return CompletableDeferred<Unit>().also { it.completeAlreadyDisposed() }
    }

    var invalidateIfCancelled = false

    val refreshJob =
      launchWithProgress(refreshProgressIndicator, workerThread) {
        refreshTriggers.forEach {
          requestLogger.debug("Refresh triggered (inside launchWithProgress scope)", it)
        }

        if (DumbService.isDumb(project)) {
          requestLogger.debug("Project is in dumb mode, not able to refresh")
          return@launchWithProgress
        }

        if (renderingBuildStatusManager.status == RenderingBuildStatus.NeedsBuild) {
          // Project needs to be built before being able to refresh.
          requestLogger.debug("Project has not build, not able to refresh")
          return@launchWithProgress
        }

        if (Bridge.hasNativeCrash()) {
          composeWorkBench.onLayoutlibNativeCrash { requestRefresh() }
          return@launchWithProgress
        }

        requestVisibilityAndNotificationsUpdate()

        try {
          refreshProgressIndicator.text = message("refresh.progress.indicator.finding.previews")

          val needsFullRefresh =
            refreshRequest.refreshType != ComposePreviewRefreshType.QUALITY &&
              invalidated.getAndSet(false)
          invalidateIfCancelled = needsFullRefresh

          val previewsToRender =
            withContext(workerThread) {
              composePreviewFlowManager.toRenderPreviewElementsFlow.value.asCollection().toList()
            }
          composeWorkBench.hasContent =
            previewsToRender.isNotEmpty() || mode.value is PreviewMode.UiCheck
          if (!needsFullRefresh) {
            requestLogger.debug(
              "No updates on the PreviewElements, just refreshing the existing ones"
            )
            // In this case, there are no new previews. We need to make sure that the surface is
            // still correctly configured and that we are showing the right size for components.
            // For example, if the user switches on/off decorations, that will not generate/remove
            // new PreviewElements but will change the surface settings.
            refreshProgressIndicator.text =
              message("refresh.progress.indicator.reusing.existing.previews")
            composeWorkBench.refreshExistingPreviewElements(
              refreshProgressIndicator,
              previewElementModelAdapter::modelToElement,
              this@ComposePreviewRepresentation::configureLayoutlibSceneManagerForPreviewElement,
              refreshFilter = { sceneManager ->
                refreshRequest.refreshType != ComposePreviewRefreshType.QUALITY ||
                  qualityManager.needsQualityChange(sceneManager)
              },
              refreshOrder = { sceneManager ->
                // decreasing quality before increasing
                qualityManager
                  .getTargetQuality(sceneManager)
                  .compareTo(sceneManager.lastRenderQuality)
              },
              refreshRequest.refreshEventBuilder,
            )
          } else {
            refreshProgressIndicator.text =
              message("refresh.progress.indicator.refreshing.all.previews")
            composeWorkBench.updateProgress(message("panel.initializing"))
            postIssueUpdateListenerForUiCheck.deactivate()
            emptyUiCheckPanel.isVisible = previewModeManager.mode.value is PreviewMode.UiCheck
            doRefreshSync(
              previewsToRender,
              refreshRequest.refreshType == ComposePreviewRefreshType.QUICK,
              refreshProgressIndicator,
              refreshRequest.refreshEventBuilder,
            )
          }
        } catch (t: Throwable) {
          if (t is CancellationException) {
            // We want to make sure the next refresh invalidates if this invalidation didn't happen
            // Careful though, this needs to be performed here and not in the invokeOnCompletion of
            // the job that is returned as the invokeOnComplete is run concurrently with the next
            // refresh request and there can be race conditions.
            if (invalidateIfCancelled) invalidate()
            // Make sure to propagate cancellations
            throw t
          } else requestLogger.warn("Request failed", t)
        } finally {
          // Force updating toolbar icons after refresh
          ActivityTracker.getInstance().inc()
        }
      }

    refreshJob.invokeOnCompletion {
      requestLogger.debug("Completed")
      launch(uiThread) { Disposer.dispose(refreshProgressIndicator) }
      if (it is CancellationException) {
        composeWorkBench.onRefreshCancelledByTheUser()
      } else {
        if (it != null) invalidate()
        composeWorkBench.onRefreshCompleted()
      }

      if (it == null && previewModeManager.mode.value is PreviewMode.UiCheck) {
        postIssueUpdateListenerForUiCheck.activate()
      }

      launch(uiThread) {
        if (
          !composeWorkBench.isMessageBeingDisplayed &&
            refreshRequest.refreshType != ComposePreviewRefreshType.QUALITY
        ) {
          // Only notify the preview refresh time if there are previews to show.
          val durationString =
            Duration.ofMillis((System.nanoTime() - startTime) / 1_000_000).toDisplayString()
          val notification =
            Notification(
              PREVIEW_NOTIFICATION_GROUP_ID,
              message("event.log.refresh.title"),
              message("event.log.refresh.total.elapsed.time", durationString),
              NotificationType.INFORMATION,
            )
          Notifications.Bus.notify(notification, project)
        }
      }
    }
    return refreshJob
  }

  override fun getState() = stateManager.getState()

  override fun setState(state: PreviewRepresentationState) = stateManager.setState(state)

  override fun hasPreviewsCached() = hasPreviewsCachedValue.get()

  /**
   * Iterate over the Composables of this file and returns true as soon as we find one with a
   * `@Preview` or MultiPreview annotations. This function also updates the value of
   * [hasPreviewsCachedValue] accordingly.
   */
  override suspend fun hasPreviews(): Boolean {
    val vFile = readAction { psiFilePointer.element?.virtualFile } ?: return false
    findAnnotatedMethodsValues(
        project,
        vFile,
        COMPOSABLE_ANNOTATION_FQ_NAME,
        COMPOSABLE_ANNOTATION_NAME,
      ) { methods ->
        methods.asFlow()
      }
      .forEach { composableMethod ->
        if (composableMethod.hasPreviewElements()) {
          hasPreviewsCachedValue.set(true)
          return@hasPreviews true
        }
      }
    hasPreviewsCachedValue.set(false)
    return false
  }

  /**
   * Whether the scene manager should use a private ClassLoader. Currently, that's done for
   * interactive preview and animation inspector, where it's crucial not to share the state (which
   * includes the compose framework).
   */
  private fun usePrivateClassLoader() =
    mode.value is PreviewMode.Interactive ||
      mode.value is PreviewMode.AnimationInspection ||
      composePreviewFlowManager.previewsCount() == 1

  override fun invalidate() {
    invalidated.set(true)
  }

  /** Returns if this representation has been invalidated. Only for use in tests. */
  @TestOnly internal fun isInvalid(): Boolean = invalidated.get()

  /**
   * Same as [requestRefresh] but does a previous [invalidate] to ensure the preview definitions are
   * re-loaded from the files. This function will suspend until the refresh job completes normally
   * or exceptionally. A successful completion doesn't mean the refresh was successful, as it might
   * have failed or been cancelled.
   */
  private suspend fun invalidateAndRefresh(
    type: ComposePreviewRefreshType = ComposePreviewRefreshType.NORMAL
  ) {
    CompletableDeferred<Unit>().let {
      invalidate()
      requestRefresh(type, it)
      it.join()
    }
  }

  override fun registerShortcuts(applicableTo: JComponent) {
    BuildAndRefresh { psiFilePointer.element }
      .registerCustomShortcutSet(getBuildAndRefreshShortcut(), applicableTo, this)
  }
  private val delegateFastPreviewSurface =
    CommonFastPreviewSurface(
      parentDisposable = this,
      lifecycleManager = lifecycleManager,
      psiFilePointer = psiFilePointer,
      previewStatusProvider = ::status,
      delegateRefresh = ::invalidateAndRefresh,
    )

  override fun requestFastPreviewRefreshAsync() =
    delegateFastPreviewSurface.requestFastPreviewRefreshAsync()

  /** Waits for any preview to be populated. */
  @TestOnly
  suspend fun waitForAnyPreviewToBeAvailable() {
    composePreviewFlowManager.allPreviewElementsFlow
      .filter { it is FlowableCollection.Present && it.collection.isNotEmpty() }
      .take(1)
      .collect()
  }

  override fun restorePrevious() = previewModeManager.restorePrevious()

  override fun setMode(mode: PreviewMode) {
    previewModeManager.setMode(mode)
  }

  /**
   * Performs setup for [mode] when this mode is started from a previous mode of a different class.
   */
  private suspend fun onEnter(mode: PreviewMode) {
    when (mode) {
      is PreviewMode.Default -> {
        sceneComponentProvider.enabled = true
        invalidateAndRefresh()
        withContext(uiThread) { surface.repaint() }
      }
      is PreviewMode.Interactive -> {
        startInteractivePreview(mode.selected as ComposePreviewElementInstance)
      }
      is PreviewMode.UiCheck -> {
        val uiCheckInstance = mode.baseInstance
        startUiCheckPreview(
          uiCheckInstance.baseElement as PsiComposePreviewElementInstance,
          uiCheckInstance.isWearPreview,
        )
      }
      is PreviewMode.AnimationInspection -> {
        ComposeAnimationListener.messageBus
          .syncPublisher(ComposeAnimationListener.TOPIC)
          .newAnimationPreviewIsOpening()
        sceneComponentProvider.enabled = false

        withContext(uiThread) {
          val animationPreview = createAnimationPreviewPanel(surface, psiFilePointer)
          currentAnimationPreview = animationPreview

          // Subscribe to bus message for closing the inspector from other files
          ComposeAnimationListener.messageBus
            .connect(animationPreview)
            .subscribe(
              ComposeAnimationListener.TOPIC,
              object : ComposeAnimationListener {
                override fun newAnimationPreviewIsOpening() {
                  restorePrevious()
                }
              },
            )

          ComposeAnimationSubscriber.setHandler(animationPreview)
          updateAnimationPanelVisibility()
        }
        invalidateAndRefresh()
      }
      is PreviewMode.Focus -> {
        withContext(uiThread) {
          if (StudioFlags.COMPOSE_PREVIEW_RESIZING.get()) {
            activeResizePanelInFocusMode = ResizePanel(composeWorkBench.mainSurface)
          }
          composeWorkBench.focusMode =
            activeResizePanelInFocusMode?.let { resizePanel ->
              FocusMode(composeWorkBench.mainSurface, resizePanel).apply {
                addSelectionListener { resizePanel.clearPanelAndHidePanel() }
              }
            } ?: FocusMode(composeWorkBench.mainSurface)
        }
        // If file had one Preview, on Entering Focus mode render will not be invoked,
        // so [onAfterRender] and [updateResizePanel] will not be invoked either, we need to do it
        // manually
        surface.sceneManagers.singleOrNull()?.let { updateResizePanel() }
      }
    }
    surface.background = mode.backgroundColor
    surface.layeredPane.remove(blankTransitionPanel)
  }

  /** Performs cleanup for [mode] when leaving this mode to go to a mode of a different class. */
  private suspend fun onExit(mode: PreviewMode) {
    // Show blank surface while performing exit cleanup
    surface.layeredPane.add(blankTransitionPanel, JLayeredPane.DRAG_LAYER, 0)
    when (mode) {
      is PreviewMode.Default -> {}
      is PreviewMode.Interactive -> {
        log.debug("Stopping interactive")
        onInteractivePreviewStop()
      }
      is PreviewMode.UiCheck -> {
        log.debug("Stopping UI check")
        onUiCheckPreviewStop()
      }
      is PreviewMode.AnimationInspection -> {
        currentAnimationPreview?.let {
          withContext(uiThread) { Disposer.dispose(it) }
          it.tracker.closeAnimationInspector()
        }
        currentAnimationPreview = null
        ComposeAnimationSubscriber.setHandler(null)
        requestVisibilityAndNotificationsUpdate()
      }
      is PreviewMode.Focus -> {
        withContext(uiThread) {
          composeWorkBench.focusMode = null
          Disposer.dispose(activeResizePanelInFocusMode!!)
          activeResizePanelInFocusMode = null
        }
      }
    }
  }

  private fun createAnimationPreviewPanel(
    surface: DesignSurface<LayoutlibSceneManager>,
    psiFilePointer: SmartPsiElementPointer<PsiFile>,
  ): ComposeAnimationPreview {
    return ComposeAnimationPreview(
        surface.project,
        ComposeAnimationTracker(AnimationToolingUsageTracker.getInstance(surface)),
        { surface.model?.let { surface.getSceneManager(it) } },
        surface,
        psiFilePointer,
      )
      .also {
        Disposer.register(this@ComposePreviewRepresentation, it)
        it.tracker.openAnimationInspector()
      }
  }

  private var currentAnimationPreview: ComposeAnimationPreview? = null

  /**
   * Manages the preview's response to caret movements, including highlighting components at the
   * caret's position and scrolling components into view.
   */
  inner class CaretNavigationHandlerImpl() : PreviewRepresentation.CaretNavigationHandler {
    override var isNavigatingToCode: Boolean = false

    override fun onCaretPositionChanged(event: CaretEvent, isModificationTriggered: Boolean) {
      if (StudioFlags.COMPOSE_PREVIEW_CODE_TO_PREVIEW_NAVIGATION.get() && !isNavigatingToCode) {
        staticNavHandler.onCaretMoved(event.newPosition.line + 1)
      }

      // If isNavigatingToCode was true it was correctly handled above so we should reset it
      isNavigatingToCode = false
      if (PreviewEssentialsModeManager.isEssentialsModeEnabled) return
      if (isModificationTriggered) return // We do not move the preview while the user is typing
      if (!StudioFlags.COMPOSE_PREVIEW_SCROLL_ON_CARET_MOVE.get()) return
      if (mode.value is PreviewMode.Interactive) return
      // If we have not changed line, ignore
      if (event.newPosition.line == event.oldPosition.line) return
      val offset = event.editor.logicalPositionToOffset(event.newPosition)

      lifecycleManager.executeIfActive {
        launch(uiThread) {
          val filePreviewElements =
            withContext(workerThread) { composePreviewFlowManager.allPreviewElementsFlow.value }
          // Workaround for b/238735830: The following withContext(uiThread) should not be needed
          // but the code below ends up being executed in a worker thread under some circumstances
          // so we need to prevent that from happening by forcing the context switch.
          withContext(uiThread) {
            when (filePreviewElements) {
              is FlowableCollection.Uninitialized -> {}
              is FlowableCollection.Present -> {
                filePreviewElements.collection
                  .find { element ->
                    element.previewBody?.psiRange.containsOffset(offset) ||
                      element.previewElementDefinition?.psiRange.containsOffset(offset)
                  }
                  ?.let { selectedPreviewElement ->
                    surface.models.find {
                      previewElementModelAdapter.modelToElement(it) == selectedPreviewElement
                    }
                  }
                  ?.let { surface.scrollToVisible(it, true) }
              }
            }
          }
        }
      }
    }
  }

  private interface ComposeAnimationListener {
    companion object {
      val TOPIC = Topic.create("Compose Animation Preview", ComposeAnimationListener::class.java)
      val messageBus = ApplicationManager.getApplication().messageBus
    }

    fun newAnimationPreviewIsOpening()
  }
}
