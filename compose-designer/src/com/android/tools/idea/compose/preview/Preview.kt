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
import com.android.tools.idea.common.editor.DesignFileEditor
import com.android.tools.idea.common.error.IssuePanelSplitter
import com.android.tools.idea.common.model.DefaultModelUpdater
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.model.updateFileContentBlocking
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.InteractionHandler
import com.android.tools.idea.common.surface.LayoutlibInteractionHandler
import com.android.tools.idea.common.surface.SwitchingInteractionHandler
import com.android.tools.idea.common.util.BuildListener
import com.android.tools.idea.common.util.ControllableTicker
import com.android.tools.idea.common.util.setupBuildListener
import com.android.tools.idea.common.util.setupChangeListener
import com.android.tools.idea.compose.preview.actions.ForceCompileAndRefreshAction
import com.android.tools.idea.compose.preview.actions.PreviewSurfaceActionManager
import com.android.tools.idea.compose.preview.actions.requestBuildForSurface
import com.android.tools.idea.compose.preview.navigation.PreviewNavigationHandler
import com.android.tools.idea.concurrency.AndroidCoroutinesAware
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.UniqueTaskCoroutineLauncher
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.editors.notifications.NotificationPanel
import com.android.tools.idea.editors.shortcuts.getBuildAndRefreshShortcut
import com.android.tools.idea.flags.StudioFlags.COMPOSE_PREVIEW_AUTO_BUILD
import com.android.tools.idea.gradle.project.build.GradleBuildState
import com.android.tools.idea.gradle.project.build.PostProjectBuildTasksExecutor
import com.android.tools.idea.rendering.RenderSettings
import com.android.tools.idea.run.util.StopWatch
import com.android.tools.idea.uibuilder.editor.multirepresentation.PreviewRepresentation
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.NlInteractionHandler
import com.android.tools.idea.uibuilder.surface.SceneMode
import com.android.tools.idea.util.runWhenSmartAndSyncedOnEdt
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.pom.Navigatable
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.uipreview.ModuleClassLoaderManager
import org.jetbrains.kotlin.backend.common.pop
import java.awt.BorderLayout
import java.time.Duration
import java.util.EnumMap
import java.util.function.Consumer
import java.util.function.Supplier
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.OverlayLayout
import kotlin.properties.Delegates

/**
 * [ComposePreviewManager.Status] result for when the preview is refreshing. Only [ComposePreviewManager.Status.isRefreshing] will be true.
 */
private val REFRESHING_STATUS = ComposePreviewManager.Status(hasRuntimeErrors = false,
                                                             hasSyntaxErrors = false,
                                                             isOutOfDate = false,
                                                             isRefreshing = true)

/**
 * Sets up the given [sceneManager] with the right values to work on the Compose Preview. Currently, this
 * will configure if the preview elements will be displayed with "full device size" or simply containing the
 * previewed components (shrink mode).
 * @param fullDeviceSize when true, the rendered content will be shown with the full device size specified in
 * the device configuration.
 */
private fun configureLayoutlibSceneManager(sceneManager: LayoutlibSceneManager, fullDeviceSize: Boolean): LayoutlibSceneManager =
  sceneManager.apply {
    setTransparentRendering(!fullDeviceSize)
    setShrinkRendering(!fullDeviceSize)
    forceReinflate()
  }

/**
 * Sets up the given [existingModel] with the right values to be used in the preview.
 */
private fun configureExistingModel(existingModel: NlModel,
                                   displayName: String,
                                   fileContents: String,
                                   surface: NlDesignSurface): NlModel {
  existingModel.updateFileContentBlocking(fileContents)
  // Reconfigure the model by setting the new display name and applying the configuration values
  existingModel.modelDisplayName = displayName
  configureLayoutlibSceneManager(surface.getSceneManager(existingModel) as LayoutlibSceneManager,
                                 RenderSettings.getProjectSettings(existingModel.project).showDecorations)

  return existingModel
}

/**
 * A [PreviewRepresentation] that provides a compose elements preview representation of the given [psiFile].
 *
 * A [component] is implied to display previews for all declared `@Composable` functions that also use the `@Preview` (see
 * [PREVIEW_ANNOTATION_FQN]) annotation.
 * For every preview element a small XML is generated that allows Layoutlib to render a `@Composable` functions.
 *
 * @param psiFile [PsiFile] pointing to the Kotlin source containing the code to preview.
 * @param previewProvider [PreviewElementProvider] to obtain the [PreviewElement]s.
 */
class ComposePreviewRepresentation(psiFile: PsiFile,
                                   previewProvider: PreviewElementProvider) :
  PreviewRepresentation, ComposePreviewManager, UserDataHolderEx by UserDataHolderBase(), AndroidCoroutinesAware {
  private val LOG = Logger.getInstance(ComposePreviewRepresentation::class.java)
  private val project = psiFile.project
  private val psiFilePointer = SmartPointerManager.createPointer(psiFile)

  private val previewProvider = GroupNameFilteredPreviewProvider(previewProvider)

  /**
   * A [UniqueTaskCoroutineLauncher] used to run the image rendering. This ensures that only one image rendering is running at time.
   */
  private val uniqueRefreshLauncher = UniqueTaskCoroutineLauncher(this, "Compose Preview refresh")

  override var groupNameFilter: String? by Delegates.observable(null as String?) { _, oldValue, newValue ->
    if (oldValue != newValue) {
      this.previewProvider.groupName = newValue
      refresh()
    }
  }
  override val availableGroups: Set<String> get() = previewProvider.availableGroups

  private enum class InteractionMode {
    DEFAULT,
    INTERACTIVE,
  }

  private val navigationHandler = PreviewNavigationHandler()
  private var interactionHandler: SwitchingInteractionHandler<InteractionMode>? = null

  private val surface = NlDesignSurface.builder(project, this)
    .setIsPreview(true)
    .showModelNames()
    .setNavigationHandler(navigationHandler)
    .setDefaultSurfaceState(DesignSurface.State.SPLIT)
    .setSceneManagerProvider { surface, model ->
      val currentRenderSettings = RenderSettings.getProjectSettings(project)

      val settingsProvider = Supplier {
        // For the compose preview we always use live rendering enabled to make use of the image pool. Also
        // we customize the quality setting and set it to the minimum for now to optimize for rendering speed.
        // For now we just render at 70% quality to get a good balance of speed/memory vs rendering quality.
        currentRenderSettings
          .copy(quality = 0.7f, useLiveRendering = true)
      }
      // When showing decorations, show the full device size
      configureLayoutlibSceneManager(LayoutlibSceneManager(model, surface, settingsProvider),
                                     fullDeviceSize = currentRenderSettings.showDecorations)
    }
    .setActionManagerProvider { surface -> PreviewSurfaceActionManager(surface) }
    .setInteractionHandlerProvider { surface ->
      val interactionHandlers = EnumMap<InteractionMode, InteractionHandler>(InteractionMode::class.java)
      interactionHandlers[InteractionMode.DEFAULT] = NlInteractionHandler(surface)
      interactionHandlers[InteractionMode.INTERACTIVE] = LayoutlibInteractionHandler(surface)
      interactionHandler = SwitchingInteractionHandler(interactionHandlers, InteractionMode.DEFAULT)
      interactionHandler
    }
    .setEditable(true)
    .build()
    .apply {
      setScreenMode(SceneMode.COMPOSE, false)
      setMaxFitIntoScale(2f) // Set fit into limit to 200%
    }

  override var isInteractive: Boolean
    set(value) {
      interactionHandler?.let {
        it.selected = if (value) InteractionMode.INTERACTIVE else InteractionMode.DEFAULT
      }
      if (value) {
        ticker.start()
      } else {
        ticker.stop()
      }
    }
    get() {
      return interactionHandler?.selected == InteractionMode.INTERACTIVE
    }

  private val modelUpdater: NlModel.NlModelUpdaterInterface = DefaultModelUpdater()
  private var savedIsShowingDecorations: Boolean by Delegates.observable(
    RenderSettings.getProjectSettings(project).showDecorations) { _, oldValue, newValue ->
    if (oldValue != newValue) {
      // If the state changes, [DesignSurface.zoomToFit] will be called  to re-adjust the preview to the new content since the render sizes
      // will be significantly different. This way, the user will see all the content when this changes.
      launch(uiThread) {
        surface.zoomToFit()
      }
    }
  }

  /**
   * List of [PreviewElement] being rendered by this editor
   */
  var previewElements: List<PreviewElement> = emptyList()

  private var isContentBeingRendered = false

  /**
   * This field will be false until the preview has rendered at least once. If the preview has not rendered once
   * we do not have enough information about errors and the rendering to show the preview. Once it has rendered,
   * even with errors, we can display additional information about the state of the preview.
   */
  private var hasRenderedAtLeastOnce = false

  /**
   * Callback called after refresh has happened
   */
  var onRefresh: (() -> Unit)? = null

  private val notificationsPanel = NotificationPanel(
    ExtensionPointName.create<EditorNotifications.Provider<EditorNotificationPanel>>(
      "com.android.tools.idea.compose.preview.composeEditorNotificationProvider"))

  /**
   * [WorkBench] used to contain all the preview elements.
   */
  private val workbench = WorkBench<DesignSurface>(project, "Compose Preview", null, this).apply {

    val actionsToolbar = ActionsToolbar(this@ComposePreviewRepresentation, surface)
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

      add(overlayPanel, BorderLayout.CENTER)
    }

    val issueErrorSplitter = IssuePanelSplitter(surface, contentPanel)

    init(issueErrorSplitter, surface, listOf(), false)
    showLoading(message("panel.building"))
  }

  private val ticker = ControllableTicker({
    surface.models.map { surface.getSceneManager(it) }.filterIsInstance<LayoutlibSceneManager>().forEach {
      it.executeCallbacks().thenRun(
        Runnable { it.requestRender() })
    }
  }, Duration.ofMillis(30))

  init {
    Disposer.register(this, ticker)
    setupBuildListener(project, object : BuildListener {
      override fun buildSucceeded() {
        val file = psiFilePointer.element
        if (file == null) {
          LOG.debug("invalid PsiFile")
          return
        }

        EditorNotifications.getInstance(project).updateNotifications(file.virtualFile!!)
        refresh()
      }

      override fun buildFailed() {
        LOG.debug("buildFailed")
        updateSurfaceVisibilityAndNotifications()
      }

      override fun buildStarted() {
        if (workbench.isMessageVisible) {
          workbench.showLoading(message("panel.building"))
          workbench.hideContent()
        }
        EditorNotifications.getInstance(project).updateNotifications(psiFilePointer.virtualFile!!)
      }
    }, this)

    setupChangeListener(
      project,
      psiFile,
      { if (isAutoBuildEnabled) requestBuildForSurface(surface) else ApplicationManager.getApplication().invokeLater { refresh() } },
      this)

    // When the preview is opened we must trigger an initial refresh. We wait for the project to be smart and synched to do it.
    project.runWhenSmartAndSyncedOnEdt(this, Consumer {
      refresh()
    })
  }

  override val component = workbench

  override fun dispose() { }

  override var isAutoBuildEnabled: Boolean = COMPOSE_PREVIEW_AUTO_BUILD.get()
    get() = COMPOSE_PREVIEW_AUTO_BUILD.get() && field

  private fun hasErrorsAndNeedsBuild(): Boolean = !hasRenderedAtLeastOnce || surface.models.asSequence()
      .mapNotNull { surface.getSceneManager(it) }
      .filterIsInstance<LayoutlibSceneManager>()
      .mapNotNull { it.renderResult?.logger?.brokenClasses?.values }
      .flatten()
      .any {
        it is ReflectiveOperationException && it.stackTrace.any { ex -> COMPOSE_VIEW_ADAPTER == ex.className }
      }

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

  override fun status(): ComposePreviewManager.Status = if (isContentBeingRendered ||
                                                            DumbService.isDumb(project) ||
                                                            GradleBuildState.getInstance(project).isBuildInProgress)
    REFRESHING_STATUS
  else
    ComposePreviewManager.Status(hasErrorsAndNeedsBuild(), hasSyntaxErrors(), isOutOfDate(), false)

  /**
   * Returns true if the surface has at least one correctly rendered preview.
   */
  private fun hasAtLeastOneValidPreview() = surface.models.asSequence()
    .mapNotNull { surface.getSceneManager(it) }
    .filterIsInstance<LayoutlibSceneManager>()
    .mapNotNull { it.renderResult }
    .any { it.renderResult.isSuccess && it.logger.brokenClasses.values.isEmpty() }

  /**
   * Hides the preview content and shows an error message on the surface.
   */
  private fun showModalErrorMessage(message: String) = UIUtil.invokeLaterIfNeeded {
    LOG.debug("showModelErrorMessage: $message")
    workbench.loadingStopped(message)
  }

  /**
   * Method called when the notifications of the [PreviewEditor] need to be updated. This is called by the
   * [ComposePreviewEditorNotificationAdapter] when the editor needs to refresh the notifications.
   */
  override fun updateNotifications(parentEditor: FileEditor) = UIUtil.invokeLaterIfNeeded {
    if (!parentEditor.isValid) return@invokeLaterIfNeeded

    notificationsPanel.updateNotifications(psiFilePointer.virtualFile, parentEditor, project)
  }

  /**
   * Updates the surface visibility and displays the content or an error message depending on the build state. This method is called after
   * certain updates like a build or a preview refresh has happened.
   * Calling this method will also update the FileEditor notifications.
   */
  private fun updateSurfaceVisibilityAndNotifications() = UIUtil.invokeLaterIfNeeded {
    if (workbench.isMessageVisible && !hasAtLeastOneValidPreview()) {
      LOG.debug("No valid previews available")
      showModalErrorMessage(message("panel.needs.build"))
    }
    else {
      LOG.debug("Show content")
      workbench.hideLoading()
      workbench.showContent()
    }

    // Make sure all notifications are cleared-up
    EditorNotifications.getInstance(project).updateNotifications(psiFilePointer.virtualFile)
  }

  /**
   * Refresh the preview surfaces. This will retrieve all the Preview annotations and render those elements.
   * The call will block until all the given [PreviewElement]s have completed rendering.
   */
  private suspend fun doRefreshSync(filePreviewElements: List<PreviewElement>) {
    if (LOG.isDebugEnabled) LOG.debug("doRefresh of ${filePreviewElements.size} elements.")
    val stopwatch = if (LOG.isDebugEnabled) StopWatch() else null
    val psiFile = psiFilePointer.element
    if (psiFile == null || !psiFile.isValid) {
      LOG.warn("doRefresh with invalid PsiFile")
      return
    }
    val facet = AndroidFacet.getInstance(psiFile)!!
    val configurationManager = ConfigurationManager.getOrCreateInstance(facet)

    // Retrieve the models that were previously displayed so we can reuse them instead of creating new ones.
    val existingModels = surface.models.reverse().toMutableList()
    val showDecorations = RenderSettings.getProjectSettings(project).showDecorations

    // Now we generate all the models (or reuse) for the PreviewElements.
    val models = filePreviewElements
      .asSequence()
      .map { Pair(it, it.toPreviewXmlString(matchParent = showDecorations)) }
      .map {
        val (previewElement, fileContents) = it

        if (LOG.isDebugEnabled) {
          LOG.debug("""Preview found at ${stopwatch?.duration?.toMillis()}ms
              displayName=${previewElement.displayName}
              methodName=${previewElement.composableMethodFqn}

              ${fileContents}
          """.trimIndent())
        }

        val model = if (existingModels.isNotEmpty()) {
          LOG.debug("Re-using model")
          configureExistingModel(existingModels.pop(), previewElement.displayName, fileContents, surface)
        }
        else {
          LOG.debug("No models to reuse were found. New model.")
          val file = ComposeAdapterLightVirtualFile("testFile.xml", fileContents)
          val configuration = Configuration.create(configurationManager, null, FolderConfiguration.createDefault())
          NlModel.create(this@ComposePreviewRepresentation,
                         previewElement.displayName,
                         facet,
                         file,
                         configuration,
                         surface.componentRegistrar,
                         modelUpdater)
        }

        val navigable: Navigatable = PsiNavigationSupport.getInstance().createNavigatable(
          project, psiFile.virtualFile, previewElement.previewElementDefinitionPsi?.element?.textOffset ?: 0)
        navigationHandler.addDefaultLocation(model, navigable, psiFile.virtualFile)

        previewElement.configuration.applyTo(model.configuration)

        model
      }
      .toList()

    // Remove and dispose pre-existing models that were not used.
    // This will happen if the user removes one or more previews.
    if (LOG.isDebugEnabled) LOG.debug("Removing ${existingModels.size} model(s)")
    existingModels.forEach { surface.removeModel(it) }
    models
      .onEach {
        // We call addModel even though the model might not be new. If we try to add an existing model,
        // this will trigger a new render which is exactly what we want.
        surface.addAndRenderModel(it).await()
      }.ifEmpty {
        showModalErrorMessage(message("panel.no.previews.defined"))
      }

    if (LOG.isDebugEnabled) {
      LOG.debug("Render completed in ${stopwatch?.duration?.toMillis()}ms")

      // Log any rendering errors
      surface.models.asSequence()
        .mapNotNull { surface.getSceneManager(it) }
        .filterIsInstance<LayoutlibSceneManager>()
        .forEach {
          val modelName = it.model.modelDisplayName
          it.renderResult?.let { result ->
            val logger = result.logger
            LOG.debug("""modelName="$modelName" result
                  | ${result}
                  | hasErrors=${logger.hasErrors()}
                  | missingClasses=${logger.missingClasses}
                  | messages=${logger.messages}
                  | exceptions=${logger.brokenClasses.values + logger.classesWithIncorrectFormat.values}
                """.trimMargin())
          }
        }
    }

    previewElements = filePreviewElements
    hasRenderedAtLeastOnce = true
    savedIsShowingDecorations = showDecorations

    withContext(uiThread) {
      surface.zoomToFit()
    }

    onRefresh?.invoke()
  }

  /**
   * Requests a refresh the preview surfaces. This will retrieve all the Preview annotations and render those elements.
   * The refresh will only happen if the Preview elements have changed from the last render.
   */
  override fun refresh() {
    launch(uiThread) {
      isContentBeingRendered = true
      val filePreviewElements = previewProvider.previewElements

      if (filePreviewElements == previewElements && savedIsShowingDecorations == RenderSettings.getProjectSettings(
          project).showDecorations) {
        LOG.debug("No updates on the PreviewElements, just refreshing the existing ones")
        // In this case, there are no new previews. We need to make sure that the surface is still correctly
        // configured and that we are showing the right size for components. For example, if the user switches on/off
        // decorations, that will not generate/remove new PreviewElements but will change the surface settings.
        val showingDecorations = RenderSettings.getProjectSettings(project).showDecorations
        uniqueRefreshLauncher.launch {
          surface.models
            .mapNotNull { surface.getSceneManager(it) }
            .filterIsInstance<LayoutlibSceneManager>()
            .forEach {
              // When showing decorations, show the full device size
              configureLayoutlibSceneManager(it, fullDeviceSize = showingDecorations)
            }
          surface.requestRender().await()
        }
      }
      else {
        uniqueRefreshLauncher.launch {
          doRefreshSync(filePreviewElements)
        }
      }

      isContentBeingRendered = false
      updateSurfaceVisibilityAndNotifications()
    }
  }

  override fun registerShortcuts(applicableTo: JComponent) {
    ForceCompileAndRefreshAction(surface).registerCustomShortcutSet(getBuildAndRefreshShortcut(), applicableTo, this)
  }
}

/**
 * A thin [FileEditor] wrapper around [ComposePreviewRepresentation].
 *
 * @param psiFile [PsiFile] pointing to the Kotlin source containing the code to preview.
 * @param representation a compose PreviewRepresentation of the [psiFile].
 */
internal class PreviewEditor(psiFile: PsiFile, val representation: ComposePreviewRepresentation) :
  ComposePreviewManager by representation, DesignFileEditor(
  psiFile.virtualFile!!) {

  var onRefresh: (() -> Unit)? = null
    set(value) {
      field = value
      representation.onRefresh = value
    }

  init {
    Disposer.register(this, representation)
    component.add(representation.component, BorderLayout.CENTER)
  }

  override fun getName(): String = "Compose Preview"

  fun registerShortcuts(applicableTo: JComponent) { representation.registerShortcuts(applicableTo) }

  fun updateNotifications() { representation.updateNotifications(this@PreviewEditor) }
}
