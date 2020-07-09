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

import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.common.editor.ActionsToolbar
import com.android.tools.idea.common.error.IssuePanelSplitter
import com.android.tools.idea.common.model.DefaultModelUpdater
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.model.NlModelBuilder
import com.android.tools.idea.common.model.NopSelectionModel
import com.android.tools.idea.common.model.updateFileContentBlocking
import com.android.tools.idea.common.surface.DelegateInteractionHandler
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.LayoutlibInteractionHandler
import com.android.tools.idea.common.util.BuildListener
import com.android.tools.idea.common.util.ControllableTicker
import com.android.tools.idea.common.util.asLogString
import com.android.tools.idea.common.util.setupBuildListener
import com.android.tools.idea.common.util.setupChangeListener
import com.android.tools.idea.compose.preview.PreviewGroup.Companion.ALL_PREVIEW_GROUP
import com.android.tools.idea.compose.preview.actions.ForceCompileAndRefreshAction
import com.android.tools.idea.compose.preview.actions.PreviewSurfaceActionManager
import com.android.tools.idea.compose.preview.actions.requestBuildForSurface
import com.android.tools.idea.compose.preview.animation.ComposePreviewAnimationManager
import com.android.tools.idea.compose.preview.navigation.PreviewNavigationHandler
import com.android.tools.idea.compose.preview.scene.ComposeSceneComponentProvider
import com.android.tools.idea.compose.preview.util.ComposeAdapterLightVirtualFile
import com.android.tools.idea.compose.preview.util.PreviewElement
import com.android.tools.idea.compose.preview.util.hasBeenBuiltSuccessfully
import com.android.tools.idea.compose.preview.util.isComposeErrorResult
import com.android.tools.idea.compose.preview.util.layoutlibSceneManagers
import com.android.tools.idea.compose.preview.util.modelAffinity
import com.android.tools.idea.compose.preview.util.requestComposeRender
import com.android.tools.idea.compose.preview.util.sortBySourcePosition
import com.android.tools.idea.concurrency.AndroidCoroutinesAware
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.concurrency.UniqueTaskCoroutineLauncher
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.editors.notifications.NotificationPanel
import com.android.tools.idea.editors.shortcuts.getBuildAndRefreshShortcut
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.flags.StudioFlags.COMPOSE_PREVIEW_AUTO_BUILD
import com.android.tools.idea.gradle.project.build.GradleBuildState
import com.android.tools.idea.gradle.project.build.PostProjectBuildTasksExecutor
import com.android.tools.idea.run.util.StopWatch
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentation
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.NlInteractionHandler
import com.android.tools.idea.uibuilder.surface.SceneMode
import com.android.tools.idea.util.runWhenSmartAndSyncedOnEdt
import com.intellij.application.subscribe
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.xml.XmlFile
import com.intellij.ui.EditorNotifications
import com.intellij.ui.JBColor
import com.intellij.ui.JBSplitter
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.android.facet.AndroidFacet
import java.awt.BorderLayout
import java.awt.Color
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.BiFunction
import java.util.function.Consumer
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.OverlayLayout
import kotlin.properties.Delegates

/**
 * Background color for the surface while "Interactive" is enabled.
 */
private val INTERACTIVE_BACKGROUND_COLOR = JBColor(Color(203, 210, 217),
                                                   Color(70, 69, 77))

/**
 * [NlModel] associated preview data
 *
 * @param previewElement the [PreviewElement] associated to this model
 */
private class ModelDataContext(private val composePreviewManager: ComposePreviewManager,
                               private val previewElement: PreviewElement) : DataContext {
  override fun getData(dataId: String): Any? = when (dataId) {
    COMPOSE_PREVIEW_MANAGER.name -> composePreviewManager
    COMPOSE_PREVIEW_ELEMENT.name -> previewElement
    else -> null
  }
}

/**
 * Sets up the given [sceneManager] with the right values to work on the Compose Preview. Currently, this
 * will configure if the preview elements will be displayed with "full device size" or simply containing the
 * previewed components (shrink mode).
 * @param showDecorations when true, the rendered content will be shown with the full device size specified in
 * the device configuration and with the frame decorations.
 * @param isInteractive whether the scene displays an interactive preview.
 * @param usePrivateClassLoader whether the scene manager should use a private ClassLoader.
 */
private fun configureLayoutlibSceneManager(sceneManager: LayoutlibSceneManager,
                                           showDecorations: Boolean,
                                           isInteractive: Boolean,
                                           usePrivateClassLoader: Boolean): LayoutlibSceneManager =
  sceneManager.apply {
    setTransparentRendering(!showDecorations)
    setShrinkRendering(!showDecorations)
    setUseImagePool(false)
    setInteractive(isInteractive)
    setUsePrivateClassLoader(usePrivateClassLoader)
    setQuality(0.7f)
    setShowDecorations(showDecorations)
    forceReinflate()
  }

/**
 * Sets up the given [existingModel] with the right values to be used in the preview.
 */
private fun configureExistingModel(existingModel: NlModel,
                                   displayName: String,
                                   newDataContext: ModelDataContext,
                                   showDecorations: Boolean,
                                   isInteractive: Boolean,
                                   usePrivateClassLoader: Boolean,
                                   fileContents: String,
                                   surface: NlDesignSurface): NlModel {
  existingModel.updateFileContentBlocking(fileContents)
  // Reconfigure the model by setting the new display name and applying the configuration values
  existingModel.modelDisplayName = displayName
  existingModel.dataContext = newDataContext
  configureLayoutlibSceneManager(
    surface.getSceneManager(existingModel) as LayoutlibSceneManager, showDecorations, isInteractive, usePrivateClassLoader)

  return existingModel
}

/**
 * A [PreviewRepresentation] that provides a compose elements preview representation of the given `psiFile`.
 *
 * A [component] is implied to display previews for all declared `@Composable` functions that also use the `@Preview` (see
 * [com.android.tools.idea.compose.preview.util.PREVIEW_ANNOTATION_FQN]) annotation.
 * For every preview element a small XML is generated that allows Layoutlib to render a `@Composable` functions.
 *
 * @param psiFile [PsiFile] pointing to the Kotlin source containing the code to preview.
 * @param previewProvider [PreviewElementProvider] to obtain the [PreviewElement]s.
 */
class ComposePreviewRepresentation(psiFile: PsiFile,
                                   previewProvider: PreviewElementProvider) :
  PreviewRepresentation, ComposePreviewManagerEx, UserDataHolderEx by UserDataHolderBase(), AndroidCoroutinesAware {
  private val LOG = Logger.getInstance(ComposePreviewRepresentation::class.java)
  private val project = psiFile.project
  private val psiFilePointer = SmartPointerManager.createPointer<PsiFile>(psiFile)

  /**
   * [PreviewElementProvider] used to save the result of a call to `previewProvider`. Calls to `previewProvider` can potentially
   * be slow. This saves the last result and it is refreshed on demand when we know is not running on the UI thread.
   */
  private val memoizedElementsProvider = MemoizedPreviewElementProvider(previewProvider,
                                                                        ModificationTracker {
                                                                          ReadAction.compute<Long, Throwable> {
                                                                            psiFilePointer.element?.modificationStamp ?: -1
                                                                          }
                                                                        })
  private val previewElementProvider = PreviewFilters(memoizedElementsProvider)

  /**
   * A [UniqueTaskCoroutineLauncher] used to run the image rendering. This ensures that only one image rendering is running at time.
   */
  private val uniqueRefreshLauncher = UniqueTaskCoroutineLauncher(this, "Compose Preview refresh")

  override var groupFilter: PreviewGroup by Delegates.observable(ALL_PREVIEW_GROUP) { _, oldValue, newValue ->
    if (oldValue != newValue) {
      LOG.debug("New group preview element selection: $newValue")
      previewElementProvider.groupNameFilter = newValue
      // Force refresh to ensure the new preview elements are picked up
      forceRefresh()
    }
  }

  @Volatile
  override var availableGroups: Set<PreviewGroup> = emptySet()

  /**
   * Enum that determines the current status of the interactive preview.
   *
   * The transitions are are like:
   * DISABLED -> STARTED -> READY -> STOPPING
   *    ^                               +
   *    |                               |
   *    +-------------------------------+
   */
  private enum class InteractiveMode {
    DISABLED,
    /** Status when interactive has been started but the first render has not happened yet. */
    STARTING,
    /** Interactive is ready and running. */
    READY,
    /** The interactive preview is stopping but it has not been fully disposed yet. */
    STOPPING;

    fun isStartingOrReady() = this == STARTING || this == READY
  }

  @Volatile
  private var interactiveMode = InteractiveMode.DISABLED
  private val navigationHandler = PreviewNavigationHandler()

  override var interactivePreviewElementInstanceId: String? by Delegates.observable(null as String?) { _, oldValue, newValue ->
    if (oldValue != newValue) {
      LOG.debug("New single preview element focus: $newValue")
      val isInteractive = newValue != null
      // The order matters because we first want to change the composable being previewed and then start interactive loop when enabled
      // but we want to stop the loop first and then change the composable when disabled
      if (isInteractive) { // Enable interactive
        interactiveMode = InteractiveMode.STARTING
        previewElementProvider.instanceIdFilter = newValue
        sceneComponentProvider.enabled = false
        forceRefresh().invokeOnCompletion {
          ticker.start()
          delegateInteractionHandler.delegate = interactiveInteractionHandler

          if (StudioFlags.COMPOSE_ANIMATED_PREVIEW_SHOW_CLICK.get()) {
            // While in interactive mode, display a small ripple when clicking
            surface.enableMouseClickDisplay()
          }
          surface.background = INTERACTIVE_BACKGROUND_COLOR
          interactiveMode = InteractiveMode.READY
        }
      }
      else { // Disable interactive
        interactiveMode = InteractiveMode.STOPPING
        surface.background = defaultSurfaceBackground
        surface.disableMouseClickDisplay()
        delegateInteractionHandler.delegate = staticPreviewInteractionHandler
        ticker.stop()
        sceneComponentProvider.enabled = true
        previewElementProvider.clearInstanceIdFilter()
        forceRefresh().invokeOnCompletion {
          interactiveMode = InteractiveMode.DISABLED
        }
      }
    }
  }

  private val animationInspection = AtomicBoolean(false)

  override var animationInspectionPreviewElementInstanceId: String? by Delegates.observable(null as String?) { _, oldValue, newValue ->
    if (oldValue != newValue) {
      animationInspection.set(newValue != null)
      if (animationInspection.get()) {
        LOG.debug("Animation Inspector open for preview: $newValue")
        previewElementProvider.instanceIdFilter = newValue
        sceneComponentProvider.enabled = false
        // Open the animation inspection panel
        mainPanelSplitter.secondComponent = ComposePreviewAnimationManager.createAnimationInspectorPanel(surface, this)
      }
      else {
        // Close the animation inspection panel
        ComposePreviewAnimationManager.closeCurrentInspector()
        mainPanelSplitter.secondComponent = null
        sceneComponentProvider.enabled = true
        previewElementProvider.instanceIdFilter = null
      }
      forceRefresh()
    }
  }

  override var showDebugBoundaries: Boolean = false
    set(value) {
      field = value
      forceRefresh()
    }

  private val sceneComponentProvider = ComposeSceneComponentProvider()
  private val delegateInteractionHandler = DelegateInteractionHandler()
  private val surface = NlDesignSurface.builder(project, this)
    .setIsPreview(true)
    .showModelNames()
    .setNavigationHandler(navigationHandler)
    .setLayoutManager(DEFAULT_PREVIEW_LAYOUT_MANAGER)
    .setActionManagerProvider { surface -> PreviewSurfaceActionManager(surface) }
    .setInteractionHandlerProvider { delegateInteractionHandler }
    .setActionHandler { surface -> PreviewSurfaceActionHandler(surface) }
    .setSceneManagerProvider { surface, model -> LayoutlibSceneManager(model, surface, sceneComponentProvider)}
    .setEditable(true)
    .setDelegateDataProvider {
      return@setDelegateDataProvider when (it) {
        COMPOSE_PREVIEW_MANAGER.name -> this
        // The Compose preview NlModels do not point to the actual file but to a synthetic file
        // generated for Layoutlib. This ensures we return the right file.
        CommonDataKeys.VIRTUAL_FILE.name -> psiFilePointer.virtualFile
        else -> null
      }
    }
    .setSelectionModel(NopSelectionModel)
    .build()
    .apply {
      setScreenMode(SceneMode.COMPOSE, false)
      setMaxFitIntoZoomLevel(2.0) // Set fit into limit to 200%
    }
  private val staticPreviewInteractionHandler = NlInteractionHandler(surface)
  private val interactiveInteractionHandler by lazy { LayoutlibInteractionHandler(surface) }

  /**
   * Default background used by the surface. This is used to restore the state after disabling the interactive preview.
   */
  private val defaultSurfaceBackground: Color = surface.background

  private val modelUpdater: NlModel.NlModelUpdaterInterface = DefaultModelUpdater()

  /**
   * List of [PreviewElement] being rendered by this editor
   */
  var previewElements: List<PreviewElement> = emptyList()

  private val isContentBeingRendered = AtomicBoolean(false)

  /**
   * This field will be false until the preview has rendered at least once. If the preview has not rendered once
   * we do not have enough information about errors and the rendering to show the preview. Once it has rendered,
   * even with errors, we can display additional information about the state of the preview.
   */
  private val hasRenderedAtLeastOnce = AtomicBoolean(false)

  /**
   * Callback called after refresh has happened
   */
  var onRefresh: (() -> Unit)? = null

  private val notificationsPanel = NotificationPanel(
    ExtensionPointName.create("com.android.tools.idea.compose.preview.composeEditorNotificationProvider"))

  private val actionsToolbar = ActionsToolbar(this@ComposePreviewRepresentation, surface)

  /**
   * Vertical splitter where the top component is the main Compose Preview panel and the bottom component, when visible, is an auxiliary
   * panel associated with the preview. For example, it can be an animation inspector that lists all the animations the preview has.
   */
  private val mainPanelSplitter = JBSplitter(true, 0.7f)

  /**
   * [WorkBench] used to contain all the preview elements.
   */
  private val workbench = WorkBench<DesignSurface>(project, "Compose Preview", null, this).apply {
    val contentPanel = JPanel(BorderLayout()).apply {
      add(actionsToolbar.toolbarComponent, BorderLayout.NORTH)

      val overlayPanel = object : JPanel() {
        // Since the overlay panel is transparent, we can not use optimized drawing or it will produce rendering artifacts.
        override fun isOptimizedDrawingEnabled(): Boolean = false
      }

      overlayPanel.apply {
        layout = OverlayLayout(this)

        add(notificationsPanel)
        add(surface)
      }

      mainPanelSplitter.firstComponent = overlayPanel
      add(mainPanelSplitter, BorderLayout.CENTER)
    }

    val issueErrorSplitter = IssuePanelSplitter(surface, contentPanel)

    init(issueErrorSplitter, surface, listOf(), false)
    showLoading(message("panel.building"))
  }

  private val ticker = ControllableTicker({
                                            surface.layoutlibSceneManagers.forEach {
                                              it.executeCallbacksAndRequestRender(null)
                                            }
                                          }, Duration.ofMillis(30))

  private val hasSuccessfulBuild = AtomicBoolean(false)

  /**
   * Tracks whether the preview has received an [onActivate] call before or not. This is used to decide whether
   * [onInit] must be called.
   */
  private val isFirstActivation = AtomicBoolean(true)

  init {
    Disposer.register(this, ticker)

    // Start handling events for the static preview.
    delegateInteractionHandler.delegate = staticPreviewInteractionHandler
  }

  override val component = workbench

  /**
   * Completes the initialization of the preview. This method is only called once after the first [onActivate]
   * happens.
   */
  private fun onInit() {
    LOG.debug("onInit")
    if (Disposer.isDisposed(this)) {
      LOG.info("Preview was closed before the initialization completed.")
    }
    val psiFile = psiFilePointer.element
    requireNotNull(psiFile) { "PsiFile was disposed before the preview initialization completed." }
    setupBuildListener(project, object : BuildListener {
      override fun buildSucceeded() {
        val file = psiFilePointer.element
        if (file == null) {
          LOG.debug("invalid PsiFile")
          return
        }

        hasSuccessfulBuild.set(true)
        EditorNotifications.getInstance(project).updateNotifications(file.virtualFile!!)
        forceRefresh()
      }

      override fun buildFailed() {
        LOG.debug("buildFailed")
        hasSuccessfulBuild.set(false)
        updateSurfaceVisibilityAndNotifications()
      }

      override fun buildStarted() {
        if (workbench.isMessageVisible) {
          workbench.showLoading(message("panel.building"))
          workbench.hideContent()
        }
        // When building, invalidate the Animation Inspector, since the animations are now obsolete and new ones will be subscribed once
        // build is complete and refresh is triggered.
        ComposePreviewAnimationManager.invalidate()
        EditorNotifications.getInstance(project).updateNotifications(psiFilePointer.virtualFile!!)
      }
    }, this)

    setupChangeListener(
      project,
      psiFile,
      {
        if (isAutoBuildEnabled && !hasSyntaxErrors()) requestBuildForSurface(surface)
        else ApplicationManager.getApplication().invokeLater {
          // When changes are made to the file, the animations become obsolete, so we invalidate the Animation Inspector and only display
          // the new ones after a successful build.
          ComposePreviewAnimationManager.invalidate()
          refresh()
        }
      },
      this)

    // When the preview is opened we must trigger an initial refresh. We wait for the project to be smart and synched to do it.
    project.runWhenSmartAndSyncedOnEdt(this, Consumer {
      launch {
        // Update the current build status in the background
        hasSuccessfulBuild.set(hasBeenBuiltSuccessfully(psiFilePointer))
      }
      refresh()
    })

    DumbService.DUMB_MODE.subscribe(this, object : DumbService.DumbModeListener {
      override fun exitDumbMode() {
        refresh()
      }
    })
  }

  override fun onActivate() {
    LOG.debug("onActivate")
    if (isFirstActivation.getAndSet(false)) onInit()
  }

  override fun dispose() {
    animationInspectionPreviewElementInstanceId = null
  }

  override var isAutoBuildEnabled: Boolean = COMPOSE_PREVIEW_AUTO_BUILD.get()
    get() = COMPOSE_PREVIEW_AUTO_BUILD.get() && field

  private fun hasErrorsAndNeedsBuild(): Boolean = !hasRenderedAtLeastOnce.get() || surface.layoutlibSceneManagers
    .any { it.renderResult.isComposeErrorResult() }

  private fun hasSyntaxErrors(): Boolean = WolfTheProblemSolver.getInstance(project).isProblemFile(psiFilePointer.virtualFile)

  private fun isOutOfDate(): Boolean {
    val isModified = FileDocumentManager.getInstance().isFileModified(psiFilePointer.virtualFile)
    if (isModified) {
      return true
    }

    // The file was saved, check the compilation time
    val modificationStamp = psiFilePointer.virtualFile.timeStamp
    val lastBuildTimestamp = PostProjectBuildTasksExecutor.getInstance(project).lastBuildTimestamp ?: -1
    if (LOG.isDebugEnabled) {
      LOG.debug("modificationStamp=${modificationStamp}, lastBuildTimestamp=${lastBuildTimestamp}")
    }

    return lastBuildTimestamp in 1 until modificationStamp
  }

  override fun status(): ComposePreviewManager.Status {
    val isRefreshing = (isContentBeingRendered.get() ||
                        DumbService.isDumb(project) ||
                        GradleBuildState.getInstance(project).isBuildInProgress)

    // If we are refreshing, we avoid spending time checking other conditions like errors or if the preview
    // is out of date.
    return ComposePreviewManager.Status(
      !isRefreshing && hasErrorsAndNeedsBuild(),
      !isRefreshing && hasSyntaxErrors(),
      !isRefreshing && isOutOfDate(),
      isRefreshing,
      interactiveMode == InteractiveMode.READY)
  }

  /**
   * Hides the preview content and shows an error message on the surface.
   */
  private fun showModalErrorMessage(message: String) = UIUtil.invokeLaterIfNeeded {
    LOG.debug("showModelErrorMessage: $message")
    workbench.loadingStopped(message)
  }

  /**
   * Method called when the notifications of the [PreviewRepresentation] need to be updated. This is called by the
   * [ComposePreviewNotificationProvider] when the editor needs to refresh the notifications.
   */
  override fun updateNotifications(parentEditor: FileEditor) = UIUtil.invokeLaterIfNeeded {
    if (!parentEditor.isValid) return@invokeLaterIfNeeded

    notificationsPanel.updateNotifications(psiFilePointer.virtualFile, parentEditor, project)
  }

  private fun updateNotifications() = UIUtil.invokeLaterIfNeeded {
    // Make sure all notifications are cleared-up
    if (!project.isDisposed) {
      EditorNotifications.getInstance(project).updateNotifications(psiFilePointer.virtualFile)
    }
  }

  /**
   * Updates the surface visibility and displays the content or an error message depending on the build state. This method is called after
   * certain updates like a build or a preview refresh has happened.
   * Calling this method will also update the FileEditor notifications.
   */
  private fun updateSurfaceVisibilityAndNotifications() = UIUtil.invokeLaterIfNeeded {
      if (workbench.isMessageVisible && !hasSuccessfulBuild.get()) {
        LOG.debug("Needs successful build")
        showModalErrorMessage(message("panel.needs.build"))
      }
      else {
        LOG.debug("Show content")
        workbench.hideLoading()
        workbench.showContent()
      }

      updateNotifications()
    }

  /**
   * Refresh the preview surfaces. This will retrieve all the Preview annotations and render those elements.
   * The call will block until all the given [PreviewElement]s have completed rendering.
   */
  private suspend fun doRefreshSync(filePreviewElements: List<PreviewElement>) {
    if (LOG.isDebugEnabled) LOG.debug("doRefresh of ${filePreviewElements.count()} elements.")
    val stopwatch = if (LOG.isDebugEnabled) StopWatch() else null
    val psiFile = ReadAction.compute<PsiFile?, Throwable> {
      val element = psiFilePointer.element

      return@compute if (element == null || !element.isValid) {
        LOG.warn("doRefresh with invalid PsiFile")
        null
      }
      else {
        element
      }
    } ?: return

    // Cache available groups
    availableGroups = previewElementProvider.allAvailableGroups

    val facet = AndroidFacet.getInstance(psiFile)!!
    val configurationManager = ConfigurationManager.getOrCreateInstance(facet)

    // Retrieve the models that were previously displayed so we can reuse them instead of creating new ones.
    val existingModels = surface.models.toMutableList()

    // Now we generate all the models (or reuse) for the PreviewElements.
    val models = previewElementProvider
      .previewElements
      .map {
        val xmlOutput = it.toPreviewXml()
          // Whether to paint the debug boundaries or not
          .toolsAttribute("paintBounds", showDebugBoundaries.toString())
          .apply {
            if (animationInspection.get()) {
              // If the animation inspection is active, start the PreviewAnimationClock with the current epoch time.
              toolsAttribute("animationClockStartTime", System.currentTimeMillis().toString())
            }
          }
          .buildString()

        Pair(it, xmlOutput)
      }
      .map {
        val (previewElement, fileContents) = it

        if (LOG.isDebugEnabled) {
          LOG.debug("""Preview found at ${stopwatch?.duration?.toMillis()}ms
              displayName=${previewElement.displaySettings.name}
              methodName=${previewElement.composableMethodFqn}

              ${fileContents}
          """.trimIndent())
        }

        val model = if (existingModels.isNotEmpty()) {
          // Find the same model we were using before, if possible. See modelAffinity for more details.
          val reusedModel = existingModels.minBy { aModel -> modelAffinity(aModel.dataContext, previewElement) }!!
          existingModels.remove(reusedModel)

          LOG.debug("Re-using model ${reusedModel.virtualFile.name}")
          configureExistingModel(reusedModel,
                                 previewElement.displaySettings.name,
                                 ModelDataContext(this, previewElement),
                                 previewElement.displaySettings.showDecoration,
                                 interactiveMode.isStartingOrReady(),
                                 usePrivateClassLoader(),
                                 fileContents,
                                 surface)
        }
        else {
          val now = System.currentTimeMillis()
          LOG.debug("No models to reuse were found. New model $now.")
          val file = ComposeAdapterLightVirtualFile("compose-model-$now.xml", fileContents) { psiFilePointer.virtualFile }
          val configuration = Configuration.create(configurationManager, null, FolderConfiguration.createDefault())
          NlModel.builder(facet, file, configuration)
            .withParentDisposable(this@ComposePreviewRepresentation)
            .withModelDisplayName(previewElement.displaySettings.name)
            .withModelUpdater(modelUpdater)
            .withComponentRegistrar(surface.componentRegistrar)
            .withDataContext(ModelDataContext(this, previewElement))
            .withXmlProvider(BiFunction<Project, VirtualFile, XmlFile> { project, virtualFile ->
              NlModelBuilder.getDefaultFile(project, virtualFile).also {
                it.putUserData(ModuleUtilCore.KEY_MODULE, facet.module)
              }
            })
            .build()
        }

        val offset = ReadAction.compute<Int, Throwable> {
          previewElement.previewElementDefinitionPsi?.element?.textOffset ?: 0
        }

        navigationHandler.setDefaultLocation(model, psiFile, offset)

        previewElement.configuration.applyTo(model.configuration)

        model to previewElement
      }
      .toList()

    // Remove and dispose pre-existing models that were not used.
    // This will happen if the user removes one or more previews.
    if (LOG.isDebugEnabled) LOG.debug("Removing ${existingModels.size} model(s)")
    existingModels.forEach {
      surface.removeModel(it)
      Disposer.dispose(it)
    }
    val newSceneManagers = models
      .map {
        val (model, previewElement) = it
        // We call addModel even though the model might not be new. If we try to add an existing model,
        // this will trigger a new render which is exactly what we want.
        configureLayoutlibSceneManager(surface.addModelWithoutRender(model) as LayoutlibSceneManager,
                                       showDecorations = previewElement.displaySettings.showDecoration,
                                       isInteractive = interactiveMode.isStartingOrReady(),
                                       usePrivateClassLoader = usePrivateClassLoader())
      }

    surface.repaint()
    if (newSceneManagers.isNotEmpty()) {
      CompletableFuture.allOf(*newSceneManagers
        .map { it.requestComposeRender() }
        .toTypedArray())
        .await()
      hasRenderedAtLeastOnce.set(true)
    }
    else {
      showModalErrorMessage(message("panel.no.previews.defined"))
    }

    if (LOG.isDebugEnabled) {
      LOG.debug("Render completed in ${stopwatch?.duration?.toMillis()}ms")

      // Log any rendering errors
      surface.layoutlibSceneManagers
        .forEach {
          val modelName = it.model.modelDisplayName
          it.renderResult?.let { result ->
            val logger = result.logger
            LOG.debug("""modelName="$modelName" result
                  | ${result}
                  | hasErrors=${logger.hasErrors()}
                  | missingClasses=${logger.missingClasses}
                  | messages=${logger.messages.asLogString()}
                  | exceptions=${logger.brokenClasses.values + logger.classesWithIncorrectFormat.values}
                """.trimMargin())
          }
        }
    }

    if (models.size >= filePreviewElements.size) {
      previewElements = filePreviewElements
    }
    else {
      // Some preview elements did not result in model creations. This could be because of failed PreviewElements instantiation.
      // TODO(b/160300892): Add better error handling for failed instantiations.
      LOG.warn("Some preview elements have failed")
    }

    withContext(uiThread) {
      surface.zoomToFit()
    }

    onRefresh?.invoke()
  }

  /**
   * Requests a refresh the preview surfaces. This will retrieve all the Preview annotations and render those elements.
   * The refresh will only happen if the Preview elements have changed from the last render.
   */
  fun refresh(): Job {
    var refreshTrigger: Throwable? = if (LOG.isDebugEnabled) Throwable() else null
    return launch(uiThread) {
      LOG.debug("Refresh triggered", refreshTrigger)
      if (DumbService.isDumb(project)) {
        LOG.debug("Project is in dumb mode, not able to refresh")
        return@launch
      }

      isContentBeingRendered.set(true)
      updateNotifications()
      try {
        val filePreviewElements = withContext(workerThread) {
          memoizedElementsProvider.previewElements
            .toList()
            .sortBySourcePosition()
        }

        if (filePreviewElements == previewElements) {
          LOG.debug("No updates on the PreviewElements, just refreshing the existing ones")
          // In this case, there are no new previews. We need to make sure that the surface is still correctly
          // configured and that we are showing the right size for components. For example, if the user switches on/off
          // decorations, that will not generate/remove new PreviewElements but will change the surface settings.
          uniqueRefreshLauncher.launch {
            surface.models
              .mapNotNull {
                val sceneManager = surface.getSceneManager(it) as? LayoutlibSceneManager ?: return@mapNotNull null
                val previewElement = it.dataContext.getData(COMPOSE_PREVIEW_ELEMENT) ?: return@mapNotNull null
                previewElement to sceneManager
              }
              .forEach {
                val (previewElement, sceneManager) = it
                // When showing decorations, show the full device size
                configureLayoutlibSceneManager(sceneManager,
                                               showDecorations = previewElement.displaySettings.showDecoration,
                                               isInteractive = interactiveMode.isStartingOrReady(),
                                               usePrivateClassLoader = usePrivateClassLoader())
                  .requestComposeRender()
                  .await()
              }
          }?.join()
        }
        else {
          uniqueRefreshLauncher.launch {
            doRefreshSync(filePreviewElements)
          }?.join()
        }
      }
      catch (t: Throwable) {
        LOG.warn("Refresh request failed", t)
      }
      finally {
        isContentBeingRendered.set(false)
        updateSurfaceVisibilityAndNotifications()
      }
    }
  }

  /**
   * Whether the scene manager should use a private ClassLoader. Currently, that's done for interactive preview and animation inspector,
   * where it's crucial not to share the state (which includes the compose framework).
   */
  private fun usePrivateClassLoader() = interactiveMode.isStartingOrReady() || animationInspection.get()

  private fun forceRefresh(): Job {
    previewElements = emptyList() // This will just force a refresh
    return refresh()
  }

  override fun registerShortcuts(applicableTo: JComponent) {
    ForceCompileAndRefreshAction(surface).registerCustomShortcutSet(getBuildAndRefreshShortcut(), applicableTo, this)
  }
}
