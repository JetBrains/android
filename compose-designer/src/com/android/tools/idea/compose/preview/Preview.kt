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
import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.adtui.common.ColoredIconGenerator
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.common.actions.IssueNotificationAction
import com.android.tools.idea.common.editor.ActionsToolbar
import com.android.tools.idea.common.editor.DesignFileEditor
import com.android.tools.idea.common.editor.SeamlessTextEditorWithPreview
import com.android.tools.idea.common.editor.ToolbarActionGroups
import com.android.tools.idea.common.error.IssuePanelSplitter
import com.android.tools.idea.common.model.DefaultModelUpdater
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.type.DesignerTypeRegistrar
import com.android.tools.idea.common.util.BuildListener
import com.android.tools.idea.common.util.setupBuildListener
import com.android.tools.idea.common.util.setupChangeListener
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.build.GradleBuildState
import com.android.tools.idea.gradle.project.build.PostProjectBuildTasksExecutor
import com.android.tools.idea.rendering.RefreshRenderAction.clearCacheAndRefreshSurface
import com.android.tools.idea.rendering.RenderSettings
import com.android.tools.idea.run.util.StopWatch
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.SceneMode
import com.android.tools.idea.uibuilder.type.LayoutEditorFileType
import com.google.common.util.concurrent.Futures
import com.google.wireless.android.sdk.stats.LayoutEditorState
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ShortcutSet
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider.getInstance
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications
import com.intellij.ui.JBColor
import com.intellij.util.concurrency.AppExecutorUtil
import icons.StudioIcons
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.backend.common.pop
import java.awt.BorderLayout
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier
import javax.swing.Box
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.OverlayLayout
import kotlin.properties.Delegates
import kotlin.streams.asSequence

/** Preview element name */
const val PREVIEW_NAME = "Preview"

/** Package containing the preview definitions */
const val PREVIEW_PACKAGE = "androidx.ui.tooling.preview"

/** Only composables with this annotation will be rendered to the surface */
const val PREVIEW_ANNOTATION_FQN = "$PREVIEW_PACKAGE.$PREVIEW_NAME"

const val COMPOSABLE_ANNOTATION_FQN = "androidx.compose.Composable"

/** View included in the runtime library that will wrap the @Composable element so it gets rendered by layoutlib */
const val COMPOSE_VIEW_ADAPTER = "$PREVIEW_PACKAGE.ComposeViewAdapter"

/** [COMPOSE_VIEW_ADAPTER] view attribute containing the FQN of the @Composable name to call */
const val COMPOSABLE_NAME_ATTR = "tools:composableName"

private val GREEN_REFRESH_BUTTON = ColoredIconGenerator.generateColoredIcon(AllIcons.Actions.ForceRefresh,
                                                                            JBColor(0x59A869, 0x499C54))

/** Action ID of the IDE declared force refresh action (see PlatformActions.xml). This allows us to re-use the shortcut of the declared action. */
const val FORCE_REFRESH_ACTION_ID = "ForceRefresh"

private val NOTIFICATIONS_EP_NAME =
  ExtensionPointName.create<EditorNotifications.Provider<EditorNotificationPanel>>("com.android.tools.idea.compose.preview.composeEditorNotificationProvider");

/** [ShortcutSet] that triggers a build and refreshes the preview */
internal fun getBuildAndRefreshShortcut(): ShortcutSet = KeymapUtil.getActiveKeymapShortcuts(FORCE_REFRESH_ACTION_ID)

/**
 * [ComposePreviewManager.Status] result for when the preview is refreshing. Only [ComposePreviewManager.Status.isRefreshing] will be true.
 */
private val REFRESHING_STATUS = ComposePreviewManager.Status(hasRuntimeErrors = false,
                                                             hasSyntaxErrors = false,
                                                             isOutOfDate = false,
                                                             isRefreshing = true)

/**
 * Interface that provides access to the Compose Preview logic.
 */
interface ComposePreviewManager {
  /**
   * Status of the preview.
   *
   * @param hasRuntimeErrors true if the project has any runtime errors that prevent the preview being up to date.
   *  For example missing classes.
   * @param hasSyntaxErrors true if the preview is displaying content of a file that has syntax errors.
   * @param isOutOfDate true if the preview needs a refresh to be up to date.
   * @param isRefreshing true if the view is currently refreshing.
   */
  data class Status(val hasRuntimeErrors: Boolean, val hasSyntaxErrors: Boolean, val isOutOfDate: Boolean, val isRefreshing: Boolean) {
    /**
     * True if the preview has errors that will need a refresh
     */
    val hasErrors = hasRuntimeErrors || hasSyntaxErrors
  }

  fun status(): Status

  /**
   * Requests a refresh of the preview surfaces. This will retrieve all the Preview annotations and render those elements.
   * The refresh will only happen if the Preview elements have changed from the last render.
   */
  fun refresh()

  /**
   * Invalidates the last cached [PreviewElement]s and forces a refresh
   */
  fun invalidateAndRefresh()
}

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
  val psiFileManager = PsiManager.getInstance(existingModel.project)
  // Reconfigure the model by setting the new display name and applying the configuration values
  existingModel.modelDisplayName = displayName
  val file = existingModel.virtualFile as ComposeAdapterLightVirtualFile
  ApplicationManager.getApplication().invokeAndWait {
    WriteAction.run<RuntimeException>  {
      // Update the contents of the VirtualFile associated to the NlModel. fireEvent value is currently ignored, just set to true in case
      // that changes in the future.
      file.setContent(null, fileContents, true)
      psiFileManager.reloadFromDisk(existingModel.file)
    }
  }
  configureLayoutlibSceneManager(surface.getSceneManager(existingModel) as LayoutlibSceneManager,
                                 RenderSettings.getProjectSettings(existingModel.project).showDecorations)

  return existingModel
}

/**
 * Refreshes the given [surface] with the given list of [CompletableFuture<NlModel>]. The method
 */
private fun updateSurfaceWithNewModels(surface: NlDesignSurface,
                                       futureModels: List<CompletableFuture<NlModel>>): CompletableFuture<Void> =
  CompletableFuture.allOf(*(futureModels.toTypedArray()))
    .thenCompose {
      val modelAddedFutures = futureModels.map {
        // We call addModel even though the model might not be new. If we try to add an existing model,
        // this will trigger a new render which is exactly what we want.
        surface.addModel(Futures.getDone(it));
      }

      CompletableFuture.allOf(*(modelAddedFutures.toTypedArray()))
    }

private fun findComposePreviewManagerForAction(e: AnActionEvent): ComposePreviewManager? {
  val project = e.project ?: return null
  val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return null
  return FileEditorManager.getInstance(project)?.getEditors(file)
    ?.filterIsInstance<ComposeTextEditorWithPreview>()
    ?.map { it.preview }
    ?.firstOrNull()
}

/**
 * A [FileEditor] that displays a preview of composable elements defined in the given [psiFile].
 *
 * The editor will display previews for all declared `@Composable` functions that also use the `@Preview` (see [PREVIEW_ANNOTATION_FQN])
 * annotation.
 * For every preview element a small XML is generated that allows Layoutlib to render a `@Composable` functions.
 *
 * @param psiFile [PsiFile] pointing to the Kotlin source containing the code to preview.
 * @param previewProvider call to obtain the [PreviewElement]s from the file.
 */
private class PreviewEditor(private val psiFile: PsiFile,
                            private val previewProvider: () -> List<PreviewElement>) : ComposePreviewManager, Disposable, DesignFileEditor(
  psiFile.virtualFile!!) {
  private val LOG = Logger.getInstance(PreviewEditor::class.java)
  private val project = psiFile.project

  private val navigationHandler = PreviewNavigationHandler()
  private val surface = NlDesignSurface.builder(project, this)
    .setIsPreview(true)
    .showModelNames()
    .setNavigationHandler(navigationHandler)
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
    .setEditable(true)
    .build()
    .apply {
      setScreenMode(SceneMode.SCREEN_COMPOSE_ONLY, true)
      setMaxFitIntoScale(2f) // Set fit into limit to 200%
    }

  private val modelUpdater: NlModel.NlModelUpdaterInterface = DefaultModelUpdater()
  private var savedIsShowingDecorations: Boolean by Delegates.observable(
    RenderSettings.getProjectSettings(project).showDecorations) { _, oldValue, newValue ->
    if (oldValue != newValue) {
      // If the state changes, [DesignSurface.zoomToFit] will be called  to re-adjust the preview to the new content since the render sizes
      // will be significantly different. This way, the user will see all the content when this changes.
      surface.zoomToFit()
    }
  }

  /**
   * List of [PreviewElement] being rendered by this editor
   */
  var previewElements: List<PreviewElement> = emptyList()

  var isRefreshingPreview = false

  /**
   * This field will be false until the preview has rendered at least once. If the preview has not rendered once
   * we do not have enough information about errors and the rendering to show the preview. Once it has rendered,
   * even with errors, we can display additional information about the state of the preview.
   */
  var hasRenderedAtLeastOnce = false

  /**
   * Callback called after refresh has happened
   */
  var onRefresh: (() -> Unit)? = null

  private val notificationsPanel: Box = Box.createVerticalBox().apply {
    name = "NotificationsPanel"
  }

  // The notificationsWrapper helps pushing the notifications to the top of the layout. This whole panel will be hidden if no notifications
  // are available.
  private val notificationsWrapper = JPanel(BorderLayout()).apply {
    isOpaque = false
    isDoubleBuffered = false
    add(notificationsPanel, BorderLayout.NORTH)
  }

  /**
   * [WorkBench] used to contain all the preview elements.
   */
  val workbench = WorkBench<DesignSurface>(project, "Compose Preview", this, this).apply {

    val actionsToolbar = ActionsToolbar(this@PreviewEditor, surface)
    val contentPanel = JPanel(BorderLayout()).apply {
      add(actionsToolbar.toolbarComponent, BorderLayout.NORTH)

      val overlayPanel = object : JPanel() {
        // Since the overlay panel is transparent, we can not use optimized drawing or it will produce rendering artifacts.
        override fun isOptimizedDrawingEnabled(): Boolean = false
      }

      overlayPanel.apply {
        layout = OverlayLayout(this)

        add(notificationsWrapper)
        add(surface)
      }

      add(overlayPanel, BorderLayout.CENTER)
    }



    val issueErrorSplitter = IssuePanelSplitter(surface, contentPanel)

    init(issueErrorSplitter, surface, listOf(), false)
    showLoading(message("panel.building"))
  }

  init {
    component.add(workbench, BorderLayout.CENTER)

    setupBuildListener(project, object : BuildListener {
      override fun buildSucceeded() {
        EditorNotifications.getInstance(project).updateNotifications(psiFile.virtualFile!!)
        refresh()
      }

      override fun buildFailed() {
        LOG.debug("buildFailed")
        isRefreshingPreview = false
        updateSurfaceVisibilityAndNotifications()
      }

      override fun buildStarted() {
        isRefreshingPreview = true
        if (workbench.isMessageVisible) {
          workbench.showLoading(message("panel.building"))
          workbench.hideContent()
        }
        EditorNotifications.getInstance(project).updateNotifications(psiFile.virtualFile!!)
      }
    }, this)

    setupChangeListener(
      project,
      psiFile,
      { refresh() },
      this)
  }

  private fun hasErrorsAndNeedsBuild(): Boolean = !hasRenderedAtLeastOnce || surface.models.asSequence()
      .mapNotNull { surface.getSceneManager(it) }
      .filterIsInstance<LayoutlibSceneManager>()
      .mapNotNull { it.renderResult?.logger?.brokenClasses?.values }
      .flatten()
      .any {
        it is ReflectiveOperationException && it.stackTrace.any { ex -> COMPOSE_VIEW_ADAPTER == ex.className }
      }

  private fun hasSyntaxErrors(): Boolean = WolfTheProblemSolver.getInstance(project).isProblemFile(file)

  private fun isOutOfDate(): Boolean {
    val isModified = FileDocumentManager.getInstance().isFileModified(file)
    if (isModified) {
      return true
    }

    // The file was saved, check the compilation time
    val modificationStamp = file.timeStamp
    val lastBuildTimestamp = PostProjectBuildTasksExecutor.getInstance(project).lastBuildTimestamp ?: -1
    if (LOG.isDebugEnabled) {
      LOG.debug("modificationStamp=${modificationStamp}, lastBuildTimestamp=${lastBuildTimestamp}")
    }

    return lastBuildTimestamp in 1 until modificationStamp
  }

  override fun status(): ComposePreviewManager.Status = if (isRefreshingPreview || DumbService.isDumb(project))
    REFRESHING_STATUS
  else
    ComposePreviewManager.Status(hasErrorsAndNeedsBuild(), hasSyntaxErrors(), isOutOfDate(), false)

  /**
   * Returns true if the surface has at least one correctly rendered preview.
   */
  fun hasAtLeastOneValidPreview() = surface.models.asSequence()
    .mapNotNull { surface.getSceneManager(it) }
    .filterIsInstance<LayoutlibSceneManager>()
    .mapNotNull { it.renderResult }
    .any { it.renderResult.isSuccess && it.logger.brokenClasses.values.isEmpty() }

  /**
   * Hides the preview content and shows an error message on the surface.
   */
  private fun showModalErrorMessage(message: String) {
    LOG.debug("showModelErrorMessage: $message")
    workbench.loadingStopped(message)
  }

  /**
   * Method called when the notifications of the [PreviewEditor] need to be updated. This is called by the
   * [ComposePreviewEditorNotificationAdapter] when the editor needs to refresh the notifications.
   */
  internal fun updateNotifications() {
    notificationsPanel.removeAll()
    NOTIFICATIONS_EP_NAME.extensions()
      .asSequence()
      .mapNotNull { it.createNotificationPanel(file, this, project)  }
      .forEach {
        notificationsPanel.add(it)
      }

    // If no notification panels were added, we will hide the notifications panel
    if (notificationsPanel.componentCount > 0) {
      notificationsWrapper.isVisible = true
      notificationsPanel.revalidate()
    }
    else {
      notificationsWrapper.isVisible = false
    }
  }

  /**
   * Updates the surface visibility and displays the content or an error message depending on the build state. This method is called after
   * certain updates like a build or a preview refresh has happened.
   * Calling this method will also update the FileEditor notifications.
   */
  private fun updateSurfaceVisibilityAndNotifications() {
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
    EditorNotifications.getInstance(project).updateNotifications(file)
  }

  /**
   * Refresh the preview surfaces. This will retrieve all the Preview annotations and render those elements.
   */
  private fun doRefresh(filePreviewElements: List<PreviewElement>) {
    if (LOG.isDebugEnabled) LOG.debug("doRefresh of ${filePreviewElements.size} elements")
    val stopwatch = if (LOG.isDebugEnabled) StopWatch() else null

    val facet = AndroidFacet.getInstance(psiFile)!!
    val configurationManager = ConfigurationManager.getOrCreateInstance(facet)

    // Retrieve the models that were previously displayed so we can reuse them instead of creating new ones.
    val existingModels = surface.models.reverse().toMutableList()
    val showDecorations = RenderSettings.getProjectSettings(project).showDecorations

    // Now we generate all the models (or reuse) for the PreviewElements.
    val futureModels = filePreviewElements
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

        val modelFuture = if (existingModels.isNotEmpty()) {
          LOG.debug("Re-using model")
          CompletableFuture.completedFuture(configureExistingModel(existingModels.pop(), previewElement.displayName, fileContents, surface))
        }
        else {
          LOG.debug("No models to reuse were found. New model.")
          val file = ComposeAdapterLightVirtualFile("testFile.xml", fileContents)
          val configuration = Configuration.create(configurationManager, null, FolderConfiguration.createDefault())
          CompletableFuture.supplyAsync(Supplier<NlModel> {
            NlModel.create(this@PreviewEditor,
                           previewElement.displayName,
                           facet,
                           file,
                           configuration,
                           surface.componentRegistrar,
                           modelUpdater)
          }, AppExecutorUtil.getAppExecutorService())
        }

        modelFuture.whenComplete { model, ex ->
          ex?.let { LOG.warn(ex) }
          val navigable: Navigatable = PsiNavigationSupport.getInstance().createNavigatable(
            project, psiFile.virtualFile, previewElement.previewElementDefinitionPsi?.element?.textOffset ?: 0)
          navigationHandler.addDefaultLocation(model, navigable, psiFile.virtualFile)

          previewElement.configuration.applyTo(model.configuration)
        }
      }
      .toList()

    // Remove and dispose pre-existing models that were not used.
    // This will happen if the user removes one or more previews.
    if (LOG.isDebugEnabled) LOG.debug("Removing ${existingModels.size} model(s)")
    existingModels.forEach { surface.removeModel(it) }
    surface.zoomToFit()

    val rendersCompletedFuture = if (futureModels.isNotEmpty()) {
      updateSurfaceWithNewModels(surface, futureModels)
    }
    else {
      showModalErrorMessage(message("panel.no.previews.defined"))
      CompletableFuture.completedFuture(null)
    }

    rendersCompletedFuture
      .whenComplete { _, ex ->
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
        if (ex != null) {
          LOG.warn(ex)
        }

        isRefreshingPreview = false
        hasRenderedAtLeastOnce = true
        savedIsShowingDecorations = showDecorations
        updateSurfaceVisibilityAndNotifications()

        onRefresh?.invoke()
      }
  }

  /**
   * Requests a refresh the preview surfaces. This will retrieve all the Preview annotations and render those elements.
   * The refresh will only happen if the Preview elements have changed from the last render.
   */
  override fun refresh() {
    isRefreshingPreview = true
    val filePreviewElements = previewProvider()

    if (filePreviewElements == previewElements) {
      LOG.debug("No updates on the PreviewElements, just refreshing the existing ones")
      // In this case, there are no new previews. We need to make sure that the surface is still correctly
      // configured and that we are showing the right size for components. For example, if the user switches on/off
      // decorations, that will not generate/remove new PreviewElements but will change the surface settings.
      val showingDecorations = RenderSettings.getProjectSettings(project).showDecorations
      surface.models
        .mapNotNull { surface.getSceneManager(it) }
        .filterIsInstance<LayoutlibSceneManager>()
        .forEach {
          // When showing decorations, show the full device size
          configureLayoutlibSceneManager(it, fullDeviceSize = showingDecorations)
        }

      // There are not elements, skip model creation
      clearCacheAndRefreshSurface(surface).whenComplete { _, _ ->
        isRefreshingPreview = false
        updateSurfaceVisibilityAndNotifications()
      }
      return
    }

    doRefresh(filePreviewElements)
  }

  /**
   * Invalidates the last cached [PreviewElement]s and forces a fresh refresh
   */
  override fun invalidateAndRefresh() {
    doRefresh(previewProvider())
  }

  override fun getName(): String = "Compose Preview"

  fun registerShortcuts(applicableTo: JComponent) {
    ForceCompileAndRefreshAction(surface).registerCustomShortcutSet(getBuildAndRefreshShortcut(), applicableTo, this)
  }
}

/**
 * [AnAction] that triggers a compilation of the current module. The build will automatically trigger a refresh
 * of the surface.
 */
private class ForceCompileAndRefreshAction(val surface: DesignSurface) :
  AnAction(message("notification.action.build.and.refresh"), null, GREEN_REFRESH_BUTTON) {
  override fun actionPerformed(e: AnActionEvent) {
    surface.models.map { it.module }.distinct().forEach {
      requestBuild(surface.project, it)
    }
  }

  override fun update(e: AnActionEvent) {
    val project = e.project ?: return
    val presentation = e.presentation
    val isRefreshing = findComposePreviewManagerForAction(e)?.status()?.isRefreshing == true
    presentation.isEnabled = !isRefreshing && !GradleBuildState.getInstance(project).isBuildInProgress
  }
}

/**
 * [ToolbarActionGroups] that includes the [ForceCompileAndRefreshAction]
 */
private class ComposePreviewToolbar(private val surface: DesignSurface) :
  ToolbarActionGroups(surface) {
  private inner class ViewOptionsAction : DropDownAction(message("action.view.options.title"), null, StudioIcons.Common.VISIBILITY_INLINE) {
    init {
      add(ToggleShowDecorationAction())
    }
  }

  private inner class ToggleShowDecorationAction :
    ToggleAction(message("action.show.decorations.title"), message("action.show.decorations.description"), null) {

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      val settings = RenderSettings.getProjectSettings(surface.project)

      if (settings.showDecorations != state) {
        // We also persist the settings to the RenderSettings
        settings.showDecorations = state
        findComposePreviewManagerForAction(e)?.invalidateAndRefresh()
      }
    }

    override fun isSelected(e: AnActionEvent): Boolean = RenderSettings.getProjectSettings(surface.project).showDecorations
  }

  override fun getNorthGroup(): ActionGroup = DefaultActionGroup(listOf(
    ViewOptionsAction(),
    ForceCompileAndRefreshAction(surface)
  ))

  override fun getNorthEastGroup(): ActionGroup = DefaultActionGroup().apply {
    addAll(getZoomActionsWithShortcuts(surface, this@ComposePreviewToolbar))
    add(IssueNotificationAction(surface))
  }
}

private class ComposeTextEditorWithPreview constructor(
  composeTextEditor: TextEditor,
  preview: PreviewEditor) :
  SeamlessTextEditorWithPreview<PreviewEditor>(composeTextEditor, preview, "Compose Editor") {
  init {
    preview.registerShortcuts(component)
  }
}

/**
 * Returns the Compose [PreviewEditor] or null if this [FileEditor] is not a Compose preview.
 */
fun FileEditor.getComposePreviewManager(): ComposePreviewManager? =
  // If the FileEditor is the PrevieEditor, return it otherwise check if it's a ComposeTextEditorWithPreview and return the PreviewEditor
  // attached to it.
  if (this is PreviewEditor) this else (this as? ComposeTextEditorWithPreview)?.preview

/**
 * Provider for Compose Preview editors.
 */
class ComposeFileEditorProvider @JvmOverloads constructor(
  private val previewElementProvider: () -> PreviewElementFinder = ::defaultPreviewElementFinder) : FileEditorProvider, DumbAware {
  private val LOG = Logger.getInstance(ComposeFileEditorProvider::class.java)

  private object ComposeEditorFileType : LayoutEditorFileType() {
    override fun getLayoutEditorStateType() = LayoutEditorState.Type.COMPOSE

    override fun isResourceTypeOf(file: PsiFile): Boolean =
      file.virtualFile is ComposeAdapterLightVirtualFile

    override fun getToolbarActionGroups(surface: DesignSurface): ToolbarActionGroups =
      ComposePreviewToolbar(surface)

    override fun getSelectionContextToolbar(surface: DesignSurface, selection: List<NlComponent>): DefaultActionGroup =
      DefaultActionGroup()
  }

  init {
    if (StudioFlags.COMPOSE_PREVIEW.get()) {
      DesignerTypeRegistrar.register(ComposeEditorFileType)
    }
  }

  override fun accept(project: Project, file: VirtualFile): Boolean {
    if (!StudioFlags.COMPOSE_PREVIEW.get() || !file.isKotlinFileType()) {
      return false
    }

    val hasPreviewMethods = previewElementProvider().hasPreviewMethods(project, file)
    if (LOG.isDebugEnabled) {
      LOG.debug("${file.path} hasPreviewMethods=${hasPreviewMethods}")
    }

    return hasPreviewMethods
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    if (LOG.isDebugEnabled) {
      LOG.debug("createEditor file=${file.path}")
    }
    val psiFile = PsiManager.getInstance(project).findFile(file)!!
    val textEditor = getInstance().createEditor(project, file) as TextEditor
    val previewProvider = {
      if (DumbService.isDumb(project)) emptyList() else previewElementProvider().findPreviewMethods(project, file)
    }
    val previewEditor = PreviewEditor(psiFile = psiFile, previewProvider = previewProvider)
    val composeEditorWithPreview = ComposeTextEditorWithPreview(textEditor, previewEditor)

    previewEditor.onRefresh = {
      composeEditorWithPreview.isPureTextEditor = previewEditor.previewElements.isEmpty()
    }

    return composeEditorWithPreview
  }

  override fun getEditorTypeId() = "ComposeEditor"

  override fun getPolicy() = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}

/**
 * [EditorNotifications.Provider] that simply listens for notification update calls and forwards it to the right Preview editor.
 * This allows the [PreviewEditor] to have its own notification system that displays notifications that only cover the preview side
 * and not the whole editor.
 *
 * This also allows for Compose Preview notifications to use exactly the same interface as [EditorNotifications] so they can easily
 * be refactored.
 */
class ComposePreviewEditorNotificationAdapter : EditorNotifications.Provider<EditorNotificationPanel>() {

  override fun createNotificationPanel(file: VirtualFile, fileEditor: FileEditor, project: Project): EditorNotificationPanel? {
    // If the given FileEditor is part of the Compose Preview, forward the update to the PreviewEditor so it can handle its own
    // notifications.
    (fileEditor as? ComposeTextEditorWithPreview)?.preview?.updateNotifications()

    // We never create a full editor notification so return null. The PreviewEditor will handle its own notifications.
    return null
  }

  override fun getKey(): Key<EditorNotificationPanel> = COMPONENT_KEY

  private val COMPONENT_KEY = Key.create<EditorNotificationPanel>("android.tools.compose.preview.notification.forwarder")
}