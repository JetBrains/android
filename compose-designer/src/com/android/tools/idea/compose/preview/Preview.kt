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
import com.android.tools.compose.COMPOSE_VIEW_ADAPTER_FQN
import com.android.tools.idea.common.error.IssuePanelService
import com.android.tools.idea.common.model.AccessibilityModelUpdater
import com.android.tools.idea.common.model.DefaultModelUpdater
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.DelegateInteractionHandler
import com.android.tools.idea.common.surface.updateSceneViewVisibilities
import com.android.tools.idea.compose.ComposePreviewElementsModel
import com.android.tools.idea.compose.UiCheckModeFilter
import com.android.tools.idea.compose.buildlisteners.PreviewBuildListenersManager
import com.android.tools.idea.compose.preview.animation.ComposePreviewAnimationManager
import com.android.tools.idea.compose.preview.essentials.ComposePreviewEssentialsModeManager
import com.android.tools.idea.compose.preview.fast.FastPreviewSurface
import com.android.tools.idea.compose.preview.fast.requestFastPreviewRefreshAndTrack
import com.android.tools.idea.compose.preview.gallery.ComposeGalleryMode
import com.android.tools.idea.compose.preview.navigation.ComposePreviewNavigationHandler
import com.android.tools.idea.compose.preview.scene.ComposeSceneComponentProvider
import com.android.tools.idea.compose.preview.scene.ComposeScreenViewProvider
import com.android.tools.idea.compose.preview.util.containsOffset
import com.android.tools.idea.concurrency.AndroidCoroutinesAware
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.concurrency.SyntaxErrorUpdate
import com.android.tools.idea.concurrency.UniqueTaskCoroutineLauncher
import com.android.tools.idea.concurrency.launchWithProgress
import com.android.tools.idea.concurrency.psiFileChangeFlow
import com.android.tools.idea.concurrency.smartModeFlow
import com.android.tools.idea.concurrency.syntaxErrorFlow
import com.android.tools.idea.editors.build.ProjectBuildStatusManager
import com.android.tools.idea.editors.build.ProjectStatus
import com.android.tools.idea.editors.build.PsiCodeFileChangeDetectorService
import com.android.tools.idea.editors.build.outOfDateKtFiles
import com.android.tools.idea.editors.fast.CompilationResult
import com.android.tools.idea.editors.fast.FastPreviewManager
import com.android.tools.idea.editors.shortcuts.getBuildAndRefreshShortcut
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.flags.StudioFlags.COMPOSE_PREVIEW_RENDER_QUALITY_NOTIFY_REFRESH_TIME
import com.android.tools.idea.log.LoggerWithFixedInfo
import com.android.tools.idea.modes.essentials.EssentialsMode
import com.android.tools.idea.modes.essentials.EssentialsModeMessenger
import com.android.tools.idea.preview.Colors
import com.android.tools.idea.preview.DefaultRenderQualityManager
import com.android.tools.idea.preview.NavigatingInteractionHandler
import com.android.tools.idea.preview.RenderQualityManager
import com.android.tools.idea.preview.SimpleRenderQualityManager
import com.android.tools.idea.preview.actions.BuildAndRefresh
import com.android.tools.idea.preview.getDefaultPreviewQuality
import com.android.tools.idea.preview.interactive.InteractivePreviewManager
import com.android.tools.idea.preview.interactive.analytics.InteractivePreviewUsageTracker
import com.android.tools.idea.preview.lifecycle.PreviewLifecycleManager
import com.android.tools.idea.preview.modes.CommonPreviewModeManager
import com.android.tools.idea.preview.modes.PreviewMode
import com.android.tools.idea.preview.modes.PreviewModeManager
import com.android.tools.idea.preview.representation.PREVIEW_ELEMENT_INSTANCE
import com.android.tools.idea.preview.sortByDisplayAndSourcePosition
import com.android.tools.idea.projectsystem.needsBuild
import com.android.tools.idea.rendering.isErrorResult
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreferredVisibility
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentation
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentationState
import com.android.tools.idea.uibuilder.options.NlOptionsConfigurable
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.scene.accessibilityBasedHierarchyParser
import com.android.tools.idea.uibuilder.surface.LayoutManagerSwitcher
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.visual.analytics.VisualLintUsageTracker
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintMode
import com.android.tools.idea.util.toDisplayString
import com.android.tools.preview.ComposePreviewElementInstance
import com.android.tools.preview.PreviewDisplaySettings
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.ComposePreviewLiteModeEvent
import com.intellij.ide.ActivityTracker
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.rd.util.withUiContext
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.io.File
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.SwingConstants
import kotlin.properties.Delegates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.android.uipreview.ModuleClassLoaderOverlays
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.psi.KtFile

/** [Notification] group ID. Must match the `groupNotification` entry of `compose-designer.xml`. */
const val PREVIEW_NOTIFICATION_GROUP_ID = "Compose Preview Notification"

/**
 * [NlModel.NlModelUpdaterInterface] to be used for updating the Compose model from the Compose
 * render result, using the [View] hierarchy.
 */
private val defaultModelUpdater: NlModel.NlModelUpdaterInterface = DefaultModelUpdater()

/**
 * [NlModel.NlModelUpdaterInterface] to be used for updating the Compose model from the Compose
 * render result, using the [AccessibilityNodeInfo] hierarchy.
 */
private val accessibilityModelUpdater: NlModel.NlModelUpdaterInterface = AccessibilityModelUpdater()

/**
 * [NlModel] associated preview data
 *
 * @param project the [Project] used by the current view.
 * @param composePreviewManager [ComposePreviewManager] of the Preview.
 * @param previewElement the [ComposePreviewElement] associated to this model
 */
private class PreviewElementDataContext(
  private val project: Project,
  private val composePreviewManager: ComposePreviewManager,
  private val previewElement: ComposePreviewElementInstance
) : DataContext {
  override fun getData(dataId: String): Any? =
    when (dataId) {
      COMPOSE_PREVIEW_MANAGER.name,
      PreviewModeManager.KEY.name -> composePreviewManager
      COMPOSE_PREVIEW_ELEMENT_INSTANCE.name,
      PREVIEW_ELEMENT_INSTANCE.name -> previewElement
      CommonDataKeys.PROJECT.name -> project
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
 * @param runAtfChecks whether to run Accessibility checks on the preview after it has been
 *   rendered. This will run the ATF scanner to detect issues affecting accessibility (e.g. low
 *   contrast, missing content description...)
 * @param runVisualLinting whether to run the Visual Lint analysis on the preview after it has been
 *   rendered. This will run all the Visual Lint analyzers that are enabled and will detect design
 *   issues (e.g. components too wide, text too long...)
 */
@VisibleForTesting
fun configureLayoutlibSceneManager(
  sceneManager: LayoutlibSceneManager,
  showDecorations: Boolean,
  isInteractive: Boolean,
  requestPrivateClassLoader: Boolean,
  runAtfChecks: Boolean,
  runVisualLinting: Boolean,
  quality: Float
): LayoutlibSceneManager =
  sceneManager.apply {
    setTransparentRendering(!showDecorations)
    setShrinkRendering(!showDecorations)
    interactive = isInteractive
    isUsePrivateClassLoader = requestPrivateClassLoader
    setShowDecorations(showDecorations)
    // The Compose Preview has its own way to track out of date files so we ask the Layoutlib
    // Scene Manager to not report it via the regular log.
    doNotReportOutOfDateUserClasses()
    setQuality(quality)
    if (runAtfChecks || runVisualLinting) {
      setCustomContentHierarchyParser(accessibilityBasedHierarchyParser)
    } else {
      setCustomContentHierarchyParser(null)
    }
    layoutScannerConfig.isLayoutScannerEnabled = runAtfChecks
    visualLintMode =
      if (runVisualLinting) {
        VisualLintMode.RUN_ON_PREVIEW_ONLY
      } else {
        VisualLintMode.DISABLED
      }
  }

/** Key for the persistent group state for the Compose Preview. */
private const val SELECTED_GROUP_KEY = "selectedGroup"

/** Key for persisting the selected layout manager. */
private const val LAYOUT_KEY = "previewLayout"

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
  composePreviewViewProvider: ComposePreviewViewProvider
) :
  PreviewRepresentation,
  ComposePreviewManagerEx,
  UserDataHolderEx by UserDataHolderBase(),
  AndroidCoroutinesAware,
  FastPreviewSurface {

  /**
   * Only for requests to refresh UI and notifications (without refreshing the preview contents).
   * This allows to bundle notifications and respects the activation/deactivation lifecycle.
   *
   * Each instance subscribes itself to the flow when it is activated, and it is automatically
   * unsubscribed when the [lifecycleManager] detects a deactivation (see [onActivate],
   * [initializeFlows] and [onDeactivate])
   */
  private val refreshNotificationsAndVisibilityFlow: MutableSharedFlow<Unit> =
    MutableSharedFlow(replay = 1)

  private val log = Logger.getInstance(ComposePreviewRepresentation::class.java)
  private val isDisposed = AtomicBoolean(false)

  private val psiFilePointer = runReadAction { SmartPointerManager.createPointer(psiFile) }
  private val project
    get() = psiFilePointer.project

  private val previewBuildListenersManager: PreviewBuildListenersManager

  private val isInteractiveMode: Boolean
    get() = mode is PreviewMode.Interactive

  private val refreshManager = ComposePreviewRefreshManager.getInstance(project)

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
        if (isInteractiveMode) {
          interactiveManager.pause()
        }
        // The editor is scheduled to be deactivated, deactivate its issue model to avoid
        // updating publish the issue update event.
        surface.deactivateIssueModel()
      },
      onDelayedDeactivate = {
        // If currently selected mode is not Normal mode, switch for Default normal mode.
        if (!mode.isNormal) mode = PreviewMode.Default
        log.debug("Delayed surface deactivation")
        surface.deactivate()
      },
    )

  /**
   * Flow containing all the [ComposePreviewElement]s available in the current file. This flow is
   * only updated when this Compose Preview representation is active.
   */
  override val allPreviewElementsInFileFlow:
    MutableStateFlow<Collection<ComposePreviewElementInstance>> =
    MutableStateFlow(emptyList())

  /**
   * Flow containing all the [ComposePreviewElementInstance]s available in the current file to be
   * rendered. These are all the previews in [allPreviewElementsInFileFlow] filtered using
   * [filterFlow]. This flow is only updated when this Compose Preview representation is active.
   */
  private val filteredPreviewElementsInstancesFlow:
    MutableStateFlow<Collection<ComposePreviewElementInstance>> =
    MutableStateFlow(emptyList())

  /**
   * Flow containing all the [ComposePreviewElementInstance]s that have completed rendering. These
   * are all the [filteredPreviewElementsInstancesFlow] that have rendered.
   */
  private val renderedPreviewElementsInstancesFlow:
    MutableStateFlow<Collection<ComposePreviewElementInstance>> =
    MutableStateFlow(emptyList())

  /**
   * Gives access to the filtered preview elements. For testing only. Users of this class should not
   * use this method.
   */
  @TestOnly
  fun filteredPreviewElementsInstancesFlowForTest():
    StateFlow<Collection<ComposePreviewElementInstance>> = filteredPreviewElementsInstancesFlow

  private val projectBuildStatusManager =
    ProjectBuildStatusManager.create(
      this,
      psiFile,
      onReady = {
        // When the preview is opened we must trigger an initial refresh. We wait for the
        // project to be smart and synced to do it.
        when (it) {
          // Do not refresh if we still need to build the project. Instead, only update the
          // empty panel and editor notifications if needed.
          ProjectStatus.NeedsBuild -> requestVisibilityAndNotificationsUpdate()
          else -> requestRefresh()
        }
      }
    )

  /**
   * [UniqueTaskCoroutineLauncher] for ensuring that only one fast preview request is launched at a
   * time.
   */
  private val fastPreviewCompilationLauncher: UniqueTaskCoroutineLauncher by
    lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
      UniqueTaskCoroutineLauncher(this, "Compilation Launcher")
    }

  /**
   * This field will be false until the preview has rendered at least once. If the preview has not
   * rendered once we do not have enough information about errors and the rendering to show the
   * preview. Once it has rendered, even with errors, we can display additional information about
   * the state of the preview.
   */
  private val hasRenderedAtLeastOnce = AtomicBoolean(false)

  private val isAnimationPreviewEnabled: Boolean
    get() = mode is PreviewMode.AnimationInspection

  init {
    val project = psiFile.project

    val essentialsModeMessagingService = service<EssentialsModeMessenger>()
    project.messageBus
      .connect(this as Disposable)
      .subscribe(
        essentialsModeMessagingService.TOPIC,
        EssentialsModeMessenger.Listener {
          updateFpsForCurrentMode()
          updateGalleryMode(
            ComposePreviewLiteModeEvent.ComposePreviewLiteModeEventType
              .STUDIO_ESSENTIALS_MODE_SWITCH
          )
          // When getting out of Essentials Mode, request a refresh
          if (!EssentialsMode.isEnabled()) requestRefresh()
        }
      )

    project.messageBus
      .connect(this as Disposable)
      .subscribe(
        NlOptionsConfigurable.Listener.TOPIC,
        NlOptionsConfigurable.Listener {
          updateGalleryMode(
            ComposePreviewLiteModeEvent.ComposePreviewLiteModeEventType.PREVIEW_LITE_MODE_SWITCH
          )
        }
      )

    previewBuildListenersManager =
      PreviewBuildListenersManager(
        { psiFilePointer },
        ::invalidate,
        ::requestRefresh,
        ::requestVisibilityAndNotificationsUpdate
      )
  }

  /**
   * Updates the [composeWorkBench]'s [ComposeGalleryMode] according to the state of Android Studio
   * (and/or Compose Preview) Essentials Mode.
   *
   * @param sourceEventType type of the event that triggered the update
   */
  private fun updateGalleryMode(
    sourceEventType: ComposePreviewLiteModeEvent.ComposePreviewLiteModeEventType? = null
  ) {
    // If Preview is inactive - don't update Gallery.
    if (!lifecycleManager.isActive()) return
    val essentialsModeIsEnabled = ComposePreviewEssentialsModeManager.isEssentialsModeEnabled
    val galleryModeIsSet = composeWorkBench.galleryMode != null
    // Only update gallery mode if needed
    if (essentialsModeIsEnabled == galleryModeIsSet) return

    if (galleryModeIsSet) {
      // There is no need to switch back to Default mode as toolbar is available.
      // When exiting Essentials mode - preview will stay in Gallery mode.
    } else {
      currentLayoutMode = LayoutMode.Gallery
    }
    logComposePreviewLiteModeEvent(sourceEventType)
    requestRefresh()
  }

  @TestOnly fun updateGalleryModeForTest() = updateGalleryMode()

  private fun updateFpsForCurrentMode() {
    interactiveManager.fpsLimit =
      if (EssentialsMode.isEnabled()) {
        StudioFlags.COMPOSE_INTERACTIVE_FPS_LIMIT.get() / 3
      } else {
        StudioFlags.COMPOSE_INTERACTIVE_FPS_LIMIT.get()
      }
  }

  /** Whether the preview needs a full refresh or not. */
  private val invalidated = AtomicBoolean(true)

  /**
   * Preview element provider corresponding to the current state of the Preview. Different modes
   * might require a different provider to be set, e.g. UI check mode needs a provider that produces
   * previews with reference devices. When exiting the mode and returning to static preview, the
   * element provider should be reset to [defaultPreviewElementProvider].
   */
  @VisibleForTesting
  val uiCheckFilterFlow = MutableStateFlow<UiCheckModeFilter>(UiCheckModeFilter.Disabled)

  /**
   * Current filter being applied to the preview. The filter allows to select one element or a group
   * of them.
   */
  private val filterFlow: MutableStateFlow<ComposePreviewElementsModel.Filter> =
    MutableStateFlow(ComposePreviewElementsModel.Filter.Disabled)

  override var groupFilter: PreviewGroup
    get() =
      (filterFlow.value as? ComposePreviewElementsModel.Filter.Group)?.filterGroup
        ?: PreviewGroup.All
    set(value) {
      // We can only apply a group filter if no filter existed before or if the current one is
      // already a group filter.
      val canApplyGroupFilter =
        filterFlow.value == ComposePreviewElementsModel.Filter.Disabled ||
          filterFlow.value is ComposePreviewElementsModel.Filter.Group
      filterFlow.value =
        if (value is PreviewGroup.Named && canApplyGroupFilter) {
          ComposePreviewElementsModel.Filter.Group(value)
        } else {
          ComposePreviewElementsModel.Filter.Disabled
        }
    }

  /**
   * Filter that can be applied to select a single instance. Setting this filter will trigger a
   * refresh.
   */
  private fun setSingleFilterFlowValue(newValue: ComposePreviewElementInstance?) {
    filterFlow.value =
      if (newValue != null) {
        ComposePreviewElementsModel.Filter.Single(newValue)
      } else {
        ComposePreviewElementsModel.Filter.Disabled
      }
  }

  override val availableGroupsFlow: MutableStateFlow<Set<PreviewGroup.Named>> =
    MutableStateFlow(setOf())

  @VisibleForTesting
  val navigationHandler =
    ComposePreviewNavigationHandler().apply {
      Disposer.register(this@ComposePreviewRepresentation, this)
    }

  private val emptyUiCheckPanel =
    object : JPanel() {
      init {
        layout = BorderLayout()
        isOpaque = false
        isVisible = false
        add(
          JLabel(message("ui.check.mode.empty.message"), SwingConstants.CENTER),
          BorderLayout.CENTER
        )
      }
    }

  override var isUiCheckFilterEnabled: Boolean by
    Delegates.observable(true) { _, oldValue, newValue ->
      if (oldValue == newValue) return@observable
      launch(uiThread) {
        var hasVisiblePreviews = false
        if (newValue) {
          surface.updateSceneViewVisibilities {
            (it.sceneManager.model in uiCheckFilterFlow.value.modelsWithErrors).also { visible ->
              hasVisiblePreviews = hasVisiblePreviews || visible
            }
          }
        } else {
          hasVisiblePreviews = true
          surface.updateSceneViewVisibilities { true }
        }
        emptyUiCheckPanel.isVisible = !hasVisiblePreviews
      }
    }

  private val postIssueUpdateListenerForUiCheck = {
    val models = mutableSetOf<NlModel>()
    val facet = surface.models.firstOrNull()?.facet
    surface.visualLintIssueProvider
      .getUnsuppressedIssues()
      .map { it.source }
      .forEach { models.addAll(it.models) }
    uiCheckFilterFlow.value.modelsWithErrors = models
    if (isUiCheckFilterEnabled) {
      ApplicationManager.getApplication().invokeLater {
        var count = 0
        surface.updateSceneViewVisibilities {
          (it.sceneManager.model in models).also { visible -> if (visible) count++ }
        }
        emptyUiCheckPanel.isVisible = count == 0
        VisualLintUsageTracker.getInstance().trackVisiblePreviews(count, facet)
        surface.zoomToFit()
        surface.repaint()
      }
    }
    uiCheckFilterFlow.value.trackTimeOfFirstRun(System.currentTimeMillis(), facet)
  }

  private val previewElementModelAdapter =
    object : ComposePreviewElementModelAdapter() {
      override fun createDataContext(previewElement: ComposePreviewElementInstance) =
        PreviewElementDataContext(project, this@ComposePreviewRepresentation, previewElement)

      override fun toXml(previewElement: ComposePreviewElementInstance) =
        previewElement
          .toPreviewXml()
          // Whether to paint the debug boundaries or not
          .toolsAttribute("paintBounds", showDebugBoundaries.toString())
          .apply {
            if (isAnimationPreviewEnabled) {
              // If the animation inspection is active, start the PreviewAnimationClock with
              // the current epoch time.
              toolsAttribute("animationClockStartTime", System.currentTimeMillis().toString())
            }
          }
          .buildString()
    }

  private suspend fun startInteractivePreview(instance: ComposePreviewElementInstance) {
    log.debug("New single preview element focus: $instance")
    requestVisibilityAndNotificationsUpdate()
    // We should call this before assigning the instance to singlePreviewElementInstance
    val quickRefresh = shouldQuickRefresh()
    val peerPreviews = filteredPreviewElementsInstancesFlow.value.size
    setSingleFilterFlowValue(instance)
    sceneComponentProvider.enabled = false
    val startUpStart = System.currentTimeMillis()
    forceRefresh(
        if (quickRefresh) ComposePreviewRefreshType.QUICK else ComposePreviewRefreshType.NORMAL
      )
      .join()
    // Currently it will re-create classloader and will be slower than switch from static
    InteractivePreviewUsageTracker.getInstance(surface)
      .logStartupTime((System.currentTimeMillis() - startUpStart).toInt(), peerPreviews)
    interactiveManager.start()
    requestVisibilityAndNotificationsUpdate()
    ActivityTracker.getInstance().inc()
  }

  private suspend fun startUiCheckPreview(instance: ComposePreviewElementInstance) {
    log.debug(
      "Starting UI check. ATF checks enabled: $atfChecksEnabled, Visual Linting enabled: $visualLintingEnabled"
    )
    qualityManager.pause()
    uiCheckFilterFlow.value = UiCheckModeFilter.Enabled(instance, surface.scale)
    withContext(uiThread) {
      emptyUiCheckPanel.apply {
        isVisible = false
        surface.layeredPane.add(this, JLayeredPane.POPUP_LAYER, 0)
      }
      if (
        (surface.sceneViewLayoutManager as LayoutManagerSwitcher).isLayoutManagerSelected(
          PREVIEW_LAYOUT_GALLERY_OPTION.layoutManager
        )
      ) {
        // Gallery layout does not make sense for UI Check mode. So if coming from Gallery mode,
        // switch to the default layout.
        (surface.sceneViewLayoutManager as LayoutManagerSwitcher).setLayoutManager(
          DEFAULT_PREVIEW_LAYOUT_MANAGER
        )
      }
      createUiCheckTab(instance)
    }
    forceRefresh().join()
  }

  fun createUiCheckTab(instance: ComposePreviewElementInstance) {
    IssuePanelService.getInstance(project).startUiCheck(
      this,
      instance.instanceId,
      instance.displaySettings.name,
      surface,
      postIssueUpdateListenerForUiCheck
    ) {
      // Pass preview manager and instance to the tab created for this UI Check preview.
      // This enables restarting the UI Check mode from an action inside the tab.
      when (it) {
        COMPOSE_PREVIEW_MANAGER.name -> this
        COMPOSE_PREVIEW_ELEMENT_INSTANCE.name -> instance
        else -> null
      }
    }
  }

  private suspend fun onUiCheckPreviewStop() {
    qualityManager.resume()
    uiCheckFilterFlow.value.basePreviewInstance?.let {
      IssuePanelService.getInstance(project)
        .stopUiCheck(it.instanceId, surface, postIssueUpdateListenerForUiCheck)
    }
    withContext(uiThread) {
      surface.layeredPane.remove(emptyUiCheckPanel)
      surface.updateSceneViewVisibilities { true }
      (uiCheckFilterFlow.value as? UiCheckModeFilter.Enabled)?.let {
        surface.setScale(it.surfaceScale)
      }
    }
    uiCheckFilterFlow.value = UiCheckModeFilter.Disabled
    forceRefresh().join()
  }

  private suspend fun onInteractivePreviewStop() {
    requestVisibilityAndNotificationsUpdate()
    interactiveManager.stop()
    filterFlow.value = ComposePreviewElementsModel.Filter.Disabled
    forceRefresh().join()
  }

  private fun updateAnimationPanelVisibility() {
    if (!hasRenderedAtLeastOnce.get()) return
    composeWorkBench.bottomPanel =
      when {
        status().hasErrors || project.needsBuild -> null
        isAnimationPreviewEnabled -> ComposePreviewAnimationManager.currentInspector?.component
        else -> null
      }
  }

  override var showDebugBoundaries: Boolean = false
    set(value) {
      field = value
      invalidate()
      requestRefresh()
    }

  override val previewedFile: PsiFile?
    get() = psiFilePointer.element

  override var isInspectionTooltipEnabled: Boolean = false

  override var isFilterEnabled: Boolean = false

  private val dataProvider = DataProvider {
    when (it) {
      COMPOSE_PREVIEW_MANAGER.name,
      PreviewModeManager.KEY.name -> this@ComposePreviewRepresentation
      PlatformCoreDataKeys.BGT_DATA_PROVIDER.name -> DataProvider { slowId -> getSlowData(slowId) }
      CommonDataKeys.PROJECT.name -> project
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

  private val composeWorkBench: ComposePreviewView =
    UIUtil.invokeAndWaitIfNeeded(
        Computable {
          composePreviewViewProvider.invoke(
            project,
            psiFilePointer,
            projectBuildStatusManager,
            dataProvider,
            createMainDesignSurfaceBuilder(
              project,
              navigationHandler,
              delegateInteractionHandler,
              dataProvider, // Will be overridden by the preview provider
              this,
              sceneComponentProvider,
              ComposeScreenViewProvider(this)
            ),
            this
          )
        }
      )
      .apply { mainSurface.background = Colors.DEFAULT_BACKGROUND_COLOR }

  @VisibleForTesting
  val staticPreviewInteractionHandler =
    ComposeNavigationInteractionHandler(
        composeWorkBench.mainSurface,
        NavigatingInteractionHandler(
          composeWorkBench.mainSurface,
          navigationHandler,
          isSelectionEnabled = { StudioFlags.COMPOSE_PREVIEW_SELECTION.get() }
        )
      )
      .also { delegateInteractionHandler.delegate = it }

  private val interactiveManager =
    InteractivePreviewManager(
        composeWorkBench.mainSurface,
        StudioFlags.COMPOSE_INTERACTIVE_FPS_LIMIT.get(),
        { surface.sceneManagers },
        { InteractivePreviewUsageTracker.getInstance(surface) },
        delegateInteractionHandler
      )
      .also { Disposer.register(this@ComposePreviewRepresentation, it) }

  @get:VisibleForTesting
  val surface: NlDesignSurface
    get() = composeWorkBench.mainSurface

  private val allowQualityChangeIfInactive = AtomicBoolean(false)
  private val qualityPolicy = ComposePreviewRenderQualityPolicy { surface.screenScalingFactor }
  private val qualityManager: RenderQualityManager =
    if (StudioFlags.COMPOSE_PREVIEW_RENDER_QUALITY.get())
      DefaultRenderQualityManager(surface, qualityPolicy) {
        requestRefresh(type = ComposePreviewRefreshType.QUALITY)
      }
    else SimpleRenderQualityManager { getDefaultPreviewQuality() }

  /**
   * Callback first time after the preview has loaded the initial state and it's ready to restore
   * any saved state.
   */
  private var onRestoreState: (() -> Unit)? = null

  private val psiCodeFileChangeDetectorService =
    PsiCodeFileChangeDetectorService.getInstance(project)

  /**
   * Currently selected [LayoutMode]. If [LayoutMode] has changed - hierarchy of the components will
   * be rearranged and for [LayoutMode.Gallery] tab component will be added.
   */
  private var currentLayoutMode: LayoutMode = LayoutMode.Default
    set(value) {
      // Switching layout from toolbar.
      if (field == value) return
      field = value

      when (value) {
        LayoutMode.Gallery -> {
          composeWorkBench.galleryMode = ComposeGalleryMode(composeWorkBench.mainSurface)
        }
        LayoutMode.Default -> {
          composeWorkBench.galleryMode = null
        }
      }
    }

  private val previewModeManager: PreviewModeManager =
    CommonPreviewModeManager(scope = this, onEnter = ::onEnter, onExit = ::onExit)

  init {
    updateGalleryMode()
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
    val shouldRefreshAfterBuildFailed = { isAnimationPreviewEnabled }
    previewBuildListenersManager.setupPreviewBuildListeners(this, shouldRefreshAfterBuildFailed) {
      composeWorkBench.updateProgress(message("panel.building"))
    }
  }

  /** Initializes the flows that will listen to different events and will call [requestRefresh]. */
  @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
  private fun CoroutineScope.initializeFlows() {
    with(this@initializeFlows) {
      launch(workerThread) {
        // Launch all the listeners that are bound to the current activation.
        ComposePreviewElementsModel.instantiatedPreviewElementsFlow(
            previewElementFlowForFile(psiFilePointer).map { it.sortByDisplayAndSourcePosition() },
          )
          .collectLatest { allPreviewElementsInFileFlow.value = it }
      }

      launch(workerThread) {
        val filteredPreviewsFlow =
          ComposePreviewElementsModel.filteredPreviewElementsFlow(
            allPreviewElementsInFileFlow,
            filterFlow,
          )

        // Flow for Preview changes
        combine(
            allPreviewElementsInFileFlow,
            filteredPreviewsFlow,
            uiCheckFilterFlow,
          ) { allAvailablePreviews, filteredPreviews, uiCheckFilter ->
            // Calculate groups
            val allGroups =
              allAvailablePreviews
                .mapNotNull {
                  it.displaySettings.group?.let { group -> PreviewGroup.namedGroup(group) }
                }
                .toSet()

            // UI Check works in the output of one particular instance (similar to interactive
            // preview).
            // When enabled, UI Check will generate here a number of previews in different reference
            // devices so, when filterPreviewInstances is called and uiCheck filter is Enabled, for
            // 1 preview, multiple will be returned.
            availableGroupsFlow.value = uiCheckFilter.filterGroups(allGroups)
            uiCheckFilter.filterPreviewInstances(filteredPreviews)
          }
          .collectLatest { filteredPreviewElementsInstancesFlow.value = it }
      }

      // Trigger refreshes on available previews changes
      launch(workerThread) {
        filteredPreviewElementsInstancesFlow.collectLatest {
          if (it.isEmpty() && isUiCheckPreview) {
            // If there are no previews for UI Check mode, then the original composable
            // was renamed or removed. We should quit UI Check mode.
            restorePrevious()
          } else {
            invalidate()
            requestRefresh()
          }
        }
      }

      // Flow to collate and process refreshNotificationsAndVisibilityFlow requests.
      launch(workerThread) {
        refreshNotificationsAndVisibilityFlow.conflate().collect {
          refreshNotificationsAndVisibilityFlow
            .resetReplayCache() // Do not keep re-playing after we have received the element.
          log.debug("refreshNotificationsAndVisibilityFlow, request=$it")
          composeWorkBench.updateVisibilityAndNotifications()
        }
      }

      launch(workerThread) {
        log.debug(
          "smartModeFlow setup status=${projectBuildStatusManager.status}, dumbMode=${DumbService.isDumb(project)}"
        )
        // Flow handling switch to smart mode.
        smartModeFlow(project, this@ComposePreviewRepresentation, log).collectLatest {
          val projectBuildStatus = projectBuildStatusManager.status
          log.debug(
            "smartModeFlow, status change status=${projectBuildStatus}, dumbMode=${DumbService.isDumb(project)}"
          )
          when (projectBuildStatus) {
            // Do not refresh if we still need to build the project. Instead, only update the
            // empty panel and editor notifications if needed.
            ProjectStatus.NotReady,
            ProjectStatus.NeedsBuild,
            ProjectStatus.Building -> requestVisibilityAndNotificationsUpdate()
            else -> requestRefresh()
          }
        }
      }

      // Flow handling file changes and syntax error changes.
      launch(workerThread) {
        merge(
            psiFileChangeFlow(psiFilePointer.project, this@launch)
              // filter only for the file we care about
              .filter { it.language == KotlinLanguage.INSTANCE }
              .onEach {
                // Invalidate the preview to detect for changes in any annotation even in
                // other files as long as they are Kotlin.
                // We do not refresh at this point. If the change is in the preview file
                // currently
                // opened, the change flow below will
                // detect the modification and trigger a refresh if needed.
                invalidate()
              }
              .debounce {
                // The debounce timer is smaller when running with Fast Preview so the changes
                // are more responsive to typing.
                if (isFastPreviewAvailable()) 250L else 1000L
              },
            syntaxErrorFlow(project, this@ComposePreviewRepresentation, log, null)
              // Detect when problems disappear
              .filter { it is SyntaxErrorUpdate.Disappeared }
              .map { it.file }
              // We listen for problems disappearing so we know when we need to re-trigger a
              // Fast Preview compile.
              // We can safely ignore this events if:
              //  - No files are out of date or it's not a relevant file
              //  - Fast Preview is not active, we do not need to detect files having
              // problems removed.
              .filter {
                isFastPreviewAvailable() &&
                  psiCodeFileChangeDetectorService.outOfDateFiles.isNotEmpty()
              }
              .filter { file ->
                // We only care about this in Kotlin files when they are out of date.
                psiCodeFileChangeDetectorService.outOfDateKtFiles
                  .map { it.virtualFile }
                  .any { it == file }
              }
          )
          .conflate()
          .collect {
            // If Fast Preview is enabled and there are Kotlin files out of date,
            // trigger a compilation. Otherwise, we will just refresh normally.
            if (
              isFastPreviewAvailable() &&
                psiCodeFileChangeDetectorService.outOfDateKtFiles.isNotEmpty()
            ) {
              try {
                requestFastPreviewRefreshAndTrack()
                return@collect
              } catch (_: Throwable) {
                // Ignore any cancellation exceptions
              }
            }

            if (
              !EssentialsMode.isEnabled() &&
                mode !is PreviewMode.Interactive &&
                !isAnimationPreviewEnabled &&
                !ComposePreviewEssentialsModeManager.isEssentialsModeEnabled
            )
              requestRefresh()
          }
      }
    }
  }

  @TestOnly
  fun hasBuildListenerSetupFinished() = previewBuildListenersManager.buildListenerSetupFinished

  /**
   * Whether fast preview is available. In addition to checking its normal availability from
   * [FastPreviewManager], we also verify that essentials mode is not enabled, because fast preview
   * should not be available in this case.
   */
  private fun isFastPreviewAvailable() =
    FastPreviewManager.getInstance(project).isAvailable &&
      !ComposePreviewEssentialsModeManager.isEssentialsModeEnabled

  override fun onActivate() {
    lifecycleManager.activate()
    // Gallery mode should be updated only if Preview is active / in foreground.
    // It will help to avoid enabling gallery mode while Preview is inactive, as it will also save
    // this state for later to restore.
    updateGalleryMode()
  }

  private fun CoroutineScope.activate(resume: Boolean) {
    log.debug("onActivate")

    qualityPolicy.activate()

    initializeFlows()

    if (!resume) {
      onInit()
    }

    surface.activate()

    if (isInteractiveMode) {
      interactiveManager.resume()
    }

    val anyKtFilesOutOfDate = psiCodeFileChangeDetectorService.outOfDateFiles.any { it is KtFile }
    if (isFastPreviewAvailable() && anyKtFilesOutOfDate) {
      // If any files are out of date, we force a refresh when re-activating. This allows us to
      // compile the changes if Fast Preview is enabled OR to refresh the preview elements in case
      // the annotations have changed.
      launch { requestFastPreviewRefreshAndTrack() }
    } else if (invalidated.get()) requestRefresh()
  }

  override fun onDeactivate() {
    lifecycleManager.deactivate()
  }
  // endregion

  override fun onCaretPositionChanged(event: CaretEvent, isModificationTriggered: Boolean) {
    if (EssentialsMode.isEnabled()) return
    if (isModificationTriggered) return // We do not move the preview while the user is typing
    if (!StudioFlags.COMPOSE_PREVIEW_SCROLL_ON_CARET_MOVE.get()) return
    if (isInteractiveMode) return
    // If we have not changed line, ignore
    if (event.newPosition.line == event.oldPosition.line) return
    val offset = event.editor.logicalPositionToOffset(event.newPosition)

    lifecycleManager.executeIfActive {
      launch(uiThread) {
        val filePreviewElements = withContext(workerThread) { allPreviewElementsInFileFlow.value }
        // Workaround for b/238735830: The following withContext(uiThread) should not be needed but
        // the code below ends up being executed
        // in a worker thread under some circumstances so we need to prevent that from happening by
        // forcing the context switch.
        withContext(uiThread) {
          filePreviewElements
            .find { element ->
              element.previewBodyPsi?.psiRange.containsOffset(offset) ||
                element.previewElementDefinitionPsi?.psiRange.containsOffset(offset)
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

  override fun dispose() {
    isDisposed.set(true)
    if (mode is PreviewMode.Interactive) {
      interactiveManager.stop()
    }
  }

  private fun hasErrorsAndNeedsBuild(): Boolean =
    renderedPreviewElementsInstancesFlow.value.isNotEmpty() &&
      (!hasRenderedAtLeastOnce.get() ||
        surface.sceneManagers.any { it.renderResult.isErrorResult(COMPOSE_VIEW_ADAPTER_FQN) })

  private fun hasSyntaxErrors(): Boolean =
    WolfTheProblemSolver.getInstance(project).isProblemFile(psiFilePointer.virtualFile)

  /**
   * Cached previous [ComposePreviewManager.Status] used to trigger notifications if there's been a
   * change.
   */
  private val previousStatusRef: AtomicReference<ComposePreviewManager.Status?> =
    AtomicReference(null)

  override fun status(): ComposePreviewManager.Status {
    val projectBuildStatus = projectBuildStatusManager.status
    val isRefreshing =
      (refreshManager.refreshingTypeFlow.value != null ||
        DumbService.isDumb(project) ||
        projectBuildStatus == ProjectStatus.Building)

    // If we are refreshing, we avoid spending time checking other conditions like errors or if the
    // preview
    // is out of date.
    val newStatus =
      ComposePreviewManager.Status(
        !isRefreshing && hasErrorsAndNeedsBuild(),
        !isRefreshing && hasSyntaxErrors(),
        !isRefreshing &&
          (projectBuildStatus is ProjectStatus.OutOfDate ||
            projectBuildStatus is ProjectStatus.NeedsBuild),
        !isRefreshing &&
          (projectBuildStatus as? ProjectStatus.OutOfDate)?.areResourcesOutOfDate ?: false,
        isRefreshing,
      )

    // This allows us to display notifications synchronized with any other change detection. The
    // moment we detect a difference,
    // we immediately ask the editor to refresh the notifications.
    // For example, IntelliJ will periodically update the toolbar. If one of the actions checks the
    // state and changes its UI, this will
    // allow for notifications to be refreshed at the same time.
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
    layoutlibSceneManager: LayoutlibSceneManager
  ) =
    configureLayoutlibSceneManager(
      layoutlibSceneManager,
      showDecorations = displaySettings.showDecoration,
      isInteractive = isInteractiveMode,
      requestPrivateClassLoader = usePrivateClassLoader(),
      runAtfChecks = atfChecksEnabled,
      runVisualLinting = visualLintingEnabled,
      quality = qualityManager.getTargetQuality(layoutlibSceneManager)
    )

  private fun onAfterRender() {
    composeWorkBench.hasRendered = true
    if (!hasRenderedAtLeastOnce.getAndSet(true)) {
      logComposePreviewLiteModeEvent(
        ComposePreviewLiteModeEvent.ComposePreviewLiteModeEventType.OPEN_AND_RENDER
      )
    }
    // Some Composables (e.g. Popup) delay their content placement and wrap them into a coroutine
    // controlled by the Compose clock. For that reason, we need to call
    // executeCallbacksAndRequestRender() once, to make sure the queued behaviors are triggered
    // and displayed in static preview.
    surface.sceneManagers.forEach { it.executeCallbacksAndRequestRender(null) }
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
              .setIsComposePreviewLiteMode(
                ComposePreviewEssentialsModeManager.isEssentialsModeEnabled
              )
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
    filteredPreviews: List<ComposePreviewElementInstance>,
    quickRefresh: Boolean,
    progressIndicator: ProgressIndicator
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
    onRestoreState?.invoke()
    onRestoreState = null

    if (progressIndicator.isCanceled) return // Return early if user has cancelled the refresh

    val showingPreviewElements =
      composeWorkBench.updatePreviewsAndRefresh(
        !quickRefresh,
        filteredPreviews,
        psiFile,
        progressIndicator,
        this::onAfterRender,
        previewElementModelAdapter,
        if (atfChecksEnabled || visualLintingEnabled) accessibilityModelUpdater
        else defaultModelUpdater,
        navigationHandler,
        this::configureLayoutlibSceneManagerForPreviewElement
      )
    if (progressIndicator.isCanceled) return // Return early if user has cancelled the refresh

    renderedPreviewElementsInstancesFlow.value = showingPreviewElements
    if (showingPreviewElements.size < numberOfPreviewsToRender) {
      // Some preview elements did not result in model creations. This could be because of failed
      // PreviewElements instantiation.
      // TODO(b/160300892): Add better error handling for failed instantiations.
      log.warn("Some preview elements have failed")
    }
  }

  private fun requestRefresh(
    type: ComposePreviewRefreshType = ComposePreviewRefreshType.NORMAL,
    completableDeferred: CompletableDeferred<Unit>? = null
  ) {
    if (isDisposed.get()) {
      completableDeferred?.completeExceptionally(IllegalStateException("Already disposed"))
      return
    }

    refreshManager.requestRefresh(
      ComposePreviewRefreshRequest(this.hashCode().toString(), ::refresh, completableDeferred, type)
    )
  }

  @TestOnly
  fun requestRefreshForTest(
    type: ComposePreviewRefreshType = ComposePreviewRefreshType.NORMAL,
    completableDeferred: CompletableDeferred<Unit>? = null
  ) = requestRefresh(type, completableDeferred)

  private fun requestVisibilityAndNotificationsUpdate() {
    launch(workerThread) { refreshNotificationsAndVisibilityFlow.emit(Unit) }
    launch(uiThread) { updateAnimationPanelVisibility() }
  }

  /**
   * Requests a refresh the preview surfaces. This will retrieve all the Preview annotations and
   * render those elements. The refresh will only happen if the Preview elements have changed from
   * the last render.
   */
  private fun refresh(refreshRequest: ComposePreviewRefreshRequest): Job {
    val requestLogger = LoggerWithFixedInfo(log, mapOf("requestId" to refreshRequest.requestId))
    requestLogger.debug(
      "Refresh triggered editor=${psiFilePointer.containingFile?.name}. Refresh type: ${refreshRequest.refreshType}"
    )
    val refreshTriggers: List<Throwable> = refreshRequest.requestSources

    if (refreshRequest.refreshType == ComposePreviewRefreshType.TRACE) {
      refreshTriggers.forEach { requestLogger.debug("Refresh trace, no work being done", it) }
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
          psiFilePointer.containingFile?.let { " (${it.name})" } ?: ""
        ),
        "",
        "",
        true
      )
    if (!Disposer.tryRegister(this, refreshProgressIndicator)) {
      refreshProgressIndicator.processFinish()
      return CompletableDeferred<Unit>().also {
        it.completeExceptionally(IllegalStateException("Already disposed"))
      }
    }

    // Make sure not to start refreshes when deactivated, unless it is the first quality refresh
    // that happens since deactivation. This is expected to happen to decrease the quality of its
    // previews when deactivating. Also, don't launch refreshes in the activation scope to avoid
    // cancelling the refresh mid-way when a simple tab change happens.
    if (
      !lifecycleManager.isActive() &&
        !(refreshRequest.refreshType == ComposePreviewRefreshType.QUALITY &&
          allowQualityChangeIfInactive.getAndSet(false))
    ) {
      refreshProgressIndicator.processFinish()
      requestLogger.debug(
        "Inactive representation (${psiFilePointer.containingFile?.name}), no work being done"
      )
      return CompletableDeferred(Unit)
    }

    var invalidateIfCancelled = false

    val refreshJob =
      launchWithProgress(refreshProgressIndicator, uiThread) {
        refreshTriggers.forEach {
          requestLogger.debug("Refresh triggered (inside launchWithProgress scope)", it)
        }

        if (DumbService.isDumb(project)) {
          requestLogger.debug("Project is in dumb mode, not able to refresh")
          return@launchWithProgress
        }

        if (projectBuildStatusManager.status == ProjectStatus.NeedsBuild) {
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
              filteredPreviewElementsInstancesFlow.value.sortByDisplayAndSourcePosition()
            }
          composeWorkBench.hasContent = previewsToRender.isNotEmpty() || isUiCheckPreview
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
              }
            )
          } else {
            refreshProgressIndicator.text =
              message("refresh.progress.indicator.refreshing.all.previews")
            composeWorkBench.updateProgress(message("panel.initializing"))
            doRefreshSync(
              previewsToRender,
              refreshRequest.refreshType == ComposePreviewRefreshType.QUICK,
              refreshProgressIndicator
            )
          }
        } catch (t: Throwable) {
          // It's normal for refreshes to get cancelled by the refreshManager, so log the
          // CancellationExceptions as 'debug' to avoid being too noisy.
          if (t is CancellationException) requestLogger.debug("Request cancelled", t)
          else requestLogger.warn("Request failed", t)
        } finally {
          // Force updating toolbar icons after refresh
          ActivityTracker.getInstance().inc()
        }
      }

    refreshJob.invokeOnCompletion {
      requestLogger.debug("Completed")
      launch(uiThread) { Disposer.dispose(refreshProgressIndicator) }
      if (it is CancellationException) {
        if (invalidateIfCancelled) invalidate()
        composeWorkBench.onRefreshCancelledByTheUser()
      } else {
        if (it != null) invalidate()
        composeWorkBench.onRefreshCompleted()
      }

      launch(uiThread) {
        if (
          !composeWorkBench.isMessageBeingDisplayed &&
            (refreshRequest.refreshType != ComposePreviewRefreshType.QUALITY ||
              COMPOSE_PREVIEW_RENDER_QUALITY_NOTIFY_REFRESH_TIME.get())
        ) {
          // Only notify the preview refresh time if there are previews to show.
          val durationString =
            Duration.ofMillis((System.nanoTime() - startTime) / 1_000_000).toDisplayString()
          val notification =
            Notification(
              PREVIEW_NOTIFICATION_GROUP_ID,
              message("event.log.refresh.title"),
              message("event.log.refresh.total.elapsed.time", durationString),
              NotificationType.INFORMATION
            )
          Notifications.Bus.notify(notification, project)
        }
      }
    }
    return refreshJob
  }

  override fun getState(): PreviewRepresentationState {
    val selectedGroupName =
      (filterFlow.value as? ComposePreviewElementsModel.Filter.Group)?.filterGroup?.name ?: ""
    val selectedLayoutName =
      PREVIEW_LAYOUT_MANAGER_OPTIONS.find {
          (surface.sceneViewLayoutManager as LayoutManagerSwitcher).isLayoutManagerSelected(
            it.layoutManager
          )
        }
        ?.displayName ?: ""
    return mapOf(SELECTED_GROUP_KEY to selectedGroupName, LAYOUT_KEY to selectedLayoutName)
  }

  override fun setState(state: PreviewRepresentationState) {
    val selectedGroupName = state[SELECTED_GROUP_KEY]
    val previewLayoutName = state[LAYOUT_KEY]
    onRestoreState = {
      if (!selectedGroupName.isNullOrEmpty()) {
        availableGroupsFlow.value
          .find { it.name == selectedGroupName }
          ?.let { filterFlow.value = ComposePreviewElementsModel.Filter.Group(it) }
      }

      PREVIEW_LAYOUT_MANAGER_OPTIONS.find { it.displayName == previewLayoutName }
        ?.let {
          (surface.sceneViewLayoutManager as LayoutManagerSwitcher).setLayoutManager(
            it.layoutManager
          )
          // If gallery mode was selected before - need to restore this type of layout.
          if (it == PREVIEW_LAYOUT_GALLERY_OPTION) {
            allPreviewElementsInFileFlow.value.firstOrNull()?.let { previewElement ->
              mode = PreviewMode.Gallery(previewElement)
            }
          }
        }
    }
  }

  /**
   * Whether the scene manager should use a private ClassLoader. Currently, that's done for
   * interactive preview and animation inspector, where it's crucial not to share the state (which
   * includes the compose framework).
   */
  private fun usePrivateClassLoader() =
    isInteractiveMode || isAnimationPreviewEnabled || shouldQuickRefresh()

  override fun invalidate() {
    invalidated.set(true)
  }

  /** Returns if this representation has been invalidated. Only for use in tests. */
  @TestOnly internal fun isInvalid(): Boolean = invalidated.get()

  /**
   * Same as [requestRefresh] but does a previous [invalidate] to ensure the preview definitions are
   * re-loaded from the files.
   *
   * The return [Deferred] will complete when the refresh finalizes.
   */
  private fun forceRefresh(
    type: ComposePreviewRefreshType = ComposePreviewRefreshType.NORMAL
  ): Deferred<Unit> {
    val completableDeferred = CompletableDeferred<Unit>()
    invalidate()
    requestRefresh(type, completableDeferred)

    return completableDeferred
  }

  override fun registerShortcuts(applicableTo: JComponent) {
    psiFilePointer.element?.let {
      BuildAndRefresh { it }
        .registerCustomShortcutSet(getBuildAndRefreshShortcut(), applicableTo, this)
    }
  }

  /** We will only do quick refresh if there is a single preview. */
  private fun shouldQuickRefresh() = filteredPreviewElementsInstancesFlow.value.size == 1

  /**
   * Waits for any on-going or pending refreshes to complete. It optionally accepts a runnable that
   * can be executed before the next render is executed.
   */
  suspend fun waitForAnyPendingRefresh(runnable: () -> Unit = {}) {
    if (isDisposed.get()) {
      return
    }

    val completableDeferred = CompletableDeferred<Unit>()
    completableDeferred.invokeOnCompletion { if (it == null) runnable() }
    requestRefresh(ComposePreviewRefreshType.TRACE, completableDeferred)
    completableDeferred.join()
  }

  private suspend fun requestFastPreviewRefreshAndTrack(): CompilationResult {
    val previewFile =
      psiFilePointer.element
        ?: return CompilationResult.RequestException(
          IllegalStateException("Preview File is no valid")
        )
    val previewFileModule =
      readAction { previewFile.module }
        ?: return CompilationResult.RequestException(
          IllegalStateException("Preview File does not have a valid module")
        )
    val outOfDateFiles =
      psiCodeFileChangeDetectorService.outOfDateFiles
        .filterIsInstance<KtFile>()
        .filter { modifiedFile ->
          if (modifiedFile.isEquivalentTo(previewFile)) return@filter true
          val modifiedFileModule = readAction { modifiedFile.module } ?: return@filter false

          // Keep the file if the file is from this module or from a module we depend on
          modifiedFileModule == previewFileModule ||
            ModuleManager.getInstance(project)
              .isModuleDependent(previewFileModule, modifiedFileModule)
        }
        .toSet()

    // Nothing to compile
    if (outOfDateFiles.isEmpty()) return CompilationResult.Success

    return requestFastPreviewRefreshAndTrack(
      this@ComposePreviewRepresentation,
      previewFileModule,
      outOfDateFiles,
      status(),
      fastPreviewCompilationLauncher
    ) { outputAbsolutePath ->
      ModuleClassLoaderOverlays.getInstance(previewFileModule)
        .pushOverlayPath(File(outputAbsolutePath).toPath())
      forceRefresh().join()
    }
  }

  override fun requestFastPreviewRefreshAsync(): Deferred<CompilationResult> =
    lifecycleManager.executeIfActive { async { requestFastPreviewRefreshAndTrack() } }
      ?: CompletableDeferred(CompilationResult.CompilationAborted())

  /** Waits for any preview to be populated. */
  @TestOnly
  suspend fun waitForAnyPreviewToBeAvailable() {
    allPreviewElementsInFileFlow.filter { it.isNotEmpty() }.take(1).collect()
  }

  override var mode by previewModeManager::mode

  override fun restorePrevious() = previewModeManager.restorePrevious()

  private suspend fun onEnter(mode: PreviewMode) {
    when (mode) {
      is PreviewMode.Default -> {
        sceneComponentProvider.enabled = true
        setSingleFilterFlowValue(null)
        forceRefresh().join()
        surface.repaint()
      }
      is PreviewMode.Interactive -> {
        startInteractivePreview(mode.selected as ComposePreviewElementInstance)
      }
      is PreviewMode.UiCheck -> {
        startUiCheckPreview(mode.selected as ComposePreviewElementInstance)
      }
      is PreviewMode.AnimationInspection -> {
        ComposePreviewAnimationManager.onAnimationInspectorOpened()
        setSingleFilterFlowValue(mode.selected as ComposePreviewElementInstance)
        sceneComponentProvider.enabled = false

        withContext(uiThread) {
          // Open the animation inspection panel
          ComposePreviewAnimationManager.createAnimationInspectorPanel(
            surface,
            this@ComposePreviewRepresentation,
            psiFilePointer
          ) {
            // Close this inspection panel, making all the necessary UI changes (e.g. changing
            // background and refreshing the preview) before
            // opening a new one.
            updateAnimationPanelVisibility()
          }
          updateAnimationPanelVisibility()
        }
        forceRefresh().join()
      }
      is PreviewMode.Gallery -> {
        setSingleFilterFlowValue(mode.selected as ComposePreviewElementInstance)
        withContext(uiThread) {
          val layoutManager = surface.sceneViewLayoutManager as LayoutManagerSwitcher
          if (!layoutManager.isLayoutManagerSelected(PREVIEW_LAYOUT_GALLERY_OPTION.layoutManager)) {
            // The only allowed layout manager for Gallery mode is the one from
            // PREVIEW_LAYOUT_GALLERY_OPTION
            layoutManager.setLayoutManager(PREVIEW_LAYOUT_GALLERY_OPTION.layoutManager)
          }
        }
      }
    }
    surface.background = mode.backgroundColor
    withUiContext { currentLayoutMode = mode.layoutMode }
  }

  private suspend fun onExit(mode: PreviewMode) {
    when (mode) {
      is PreviewMode.Default -> {}
      is PreviewMode.Interactive -> {
        log.debug("Stopping interactive")
        onInteractivePreviewStop()
        requestVisibilityAndNotificationsUpdate()
      }
      is PreviewMode.UiCheck -> {
        log.debug("Stopping UI check")
        onUiCheckPreviewStop()
      }
      is PreviewMode.AnimationInspection -> {
        onInteractivePreviewStop()
        withContext(uiThread) {
          // Close the animation inspection panel
          ComposePreviewAnimationManager.closeCurrentInspector()
        }
        // Swap the components back
        updateAnimationPanelVisibility()
      }
      is PreviewMode.Gallery -> {}
    }
  }
}
