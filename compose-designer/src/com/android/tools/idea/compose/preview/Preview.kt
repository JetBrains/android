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
import com.android.tools.adtui.common.ColoredIconGenerator
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.common.actions.IssueNotificationAction
import com.android.tools.idea.common.editor.ActionsToolbar
import com.android.tools.idea.common.editor.DesignFileEditor
import com.android.tools.idea.common.editor.SeamlessTextEditorWithPreview
import com.android.tools.idea.common.editor.SmartAutoRefresher
import com.android.tools.idea.common.editor.SmartRefreshable
import com.android.tools.idea.common.editor.ToolbarActionGroups
import com.android.tools.idea.common.error.IssuePanelSplitter
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.type.DesignerTypeRegistrar
import com.android.tools.idea.compose.preview.ComposePreviewToolbar.ForceCompileAndRefreshAction
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.build.BuildContext
import com.android.tools.idea.gradle.project.build.GradleBuildListener
import com.android.tools.idea.gradle.project.build.GradleBuildState
import com.android.tools.idea.gradle.project.build.PostProjectBuildTasksExecutor
import com.android.tools.idea.rendering.RefreshRenderAction.clearCacheAndRefreshSurface
import com.android.tools.idea.rendering.RenderSettings
import com.android.tools.idea.run.util.StopWatch
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.SceneMode
import com.android.tools.idea.uibuilder.type.LayoutEditorFileType
import com.google.wireless.android.sdk.stats.LayoutEditorState
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider.getInstance
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.EditorNotifications
import com.intellij.ui.JBColor
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.backend.common.pop
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtImportDirective
import java.awt.BorderLayout
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.function.Supplier
import javax.swing.JPanel

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

private val WHITE_REFRESH_BUTTON = ColoredIconGenerator.generateColoredIcon(AllIcons.Actions.ForceRefresh,
                                                                            JBColor(0x6E6E6E, 0xAFB1B3))
private val BLUE_REFRESH_BUTTON = ColoredIconGenerator.generateColoredIcon(AllIcons.Actions.ForceRefresh,
                                                                           JBColor(0x389FD6, 0x3592C4))
private val GREEN_REFRESH_BUTTON = ColoredIconGenerator.generateColoredIcon(AllIcons.Actions.ForceRefresh,
                                                                            JBColor(0x59A869, 0x499C54))
private val RED_REFRESH_BUTTON = ColoredIconGenerator.generateColoredIcon(AllIcons.Actions.ForceRefresh,
                                                                          JBColor(0xDB5860, 0xC75450))

/** Old FQN to lookup and throw a warning. This import should not be used anymore after migrating to using ui-tooling */
const val OLD_PREVIEW_ANNOTATION_FQN = "com.android.tools.preview.Preview"

/**
 * Transforms a dimension given on the [PreviewConfiguration] into the string value. If the dimension is [UNDEFINED_DIMENSION], the value
 * is converted to `wrap_content`. Otherwise, the value is returned concatenated with `dp`.
 */
private fun dimensionToString(dimension: Int) = if (dimension == UNDEFINED_DIMENSION) {
  "wrap_content"
}
else {
  "${dimension}dp"
}

const val BORDER_DEFINITION = """
<aapt:attr name="android:background">
  <shape
    android:shape="rectangle">
    <stroke
        android:width="1dp"
        android:color="#55AAAAAA" />
  </shape>
</aapt:attr>"""

/**
 * Generates the XML string wrapper for one [PreviewElement]
 */
private fun PreviewElement.toPreviewXmlString(withBorder: Boolean = true) =
  """
    <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:paddingTop="10dp"
        android:paddingBottom="10dp">
      <$COMPOSE_VIEW_ADAPTER
        xmlns:tools="http://schemas.android.com/tools"
        xmlns:aapt="http://schemas.android.com/aapt"
        android:layout_width="${dimensionToString(configuration.width)}"
        android:layout_height="${dimensionToString(configuration.height)}"
        android:padding="5dp"
        $COMPOSABLE_NAME_ATTR="$composableMethodFqn">
        ${if (withBorder) BORDER_DEFINITION else ""}
      </$COMPOSE_VIEW_ADAPTER>
    </LinearLayout>
  """.trimIndent()

val FAKE_LAYOUT_RES_DIR = LightVirtualFile("layout")

/**
 * [ComposePreviewManager.Status] result for when the preview is refreshing. Only [isRefreshing] will be true.
 */
private val REFRESHING_STATUS = ComposePreviewManager.Status(hasRuntimeErrors = false,
                                                             hasSyntaxErrors = false,
                                                             isOutOfDate = false,
                                                             isRefreshing = true)

/**
 * A [LightVirtualFile] defined to allow quickly identifying the given file as an XML that is used as adapter
 * to be able to preview composable methods.
 * The contents of the file only reside in memory and contain some XML that will be passed to Layoutlib.
 */
private class ComposeAdapterLightVirtualFile(name: String, content: String) : LightVirtualFile(name, content) {
  override fun getParent() = FAKE_LAYOUT_RES_DIR
}

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

    /**
     * True if the Preview needs a refresh to display more up to date content.
     */
    val needsRefresh = hasErrors || isOutOfDate
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
  // Reconfigure the model by setting the new display name and applying the configuration values
  existingModel.modelDisplayName = displayName
  val file = existingModel.virtualFile as ComposeAdapterLightVirtualFile
  // Update the contents of the VirtualFile associated to the NlModel. fireEvent value is currently ignored, just set to true in case
  // that changes in the future.
  file.setContent(null, fileContents, true)

  configureLayoutlibSceneManager(surface.getSceneManager(existingModel) as LayoutlibSceneManager,
                                 RenderSettings.getProjectSettings(existingModel.project).showDecorations)

  return existingModel
}

/**
 * A [FileEditor] that displays a preview of composable elements defined in the given [psiFile].
 *
 * The editor will display previews for all declared `@Composable` methods that also use the `@Preview` (see [PREVIEW_ANNOTATION_FQN])
 * annotation.
 * For every preview element a small XML is generated that allows Layoutlib to render a `@Composable` method.
 *
 * @param psiFile [PsiFile] pointing to the Kotlin source containing the code to preview.
 * @param previewProvider call to obtain the [PreviewElement]s from the file.
 */
private class PreviewEditor(private val psiFile: PsiFile,
                            private val previewProvider: () -> List<PreviewElement>) : ComposePreviewManager, SmartRefreshable, DesignFileEditor(
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
    .setEditable(false)
    .build()
    .apply {
      setScreenMode(SceneMode.SCREEN_COMPOSE_ONLY, true)
    }

  private val modelUpdater: NlModel.NlModelUpdaterInterface = PreviewModelUpdater(surface)

  /**
   * List of [PreviewElement] being rendered by this editor
   */
  var previewElements: List<PreviewElement> = emptyList()

  var isRefreshingPreview = false

  /**
   * Callback called after refresh has happened
   */
  var onRefresh: (() -> Unit)? = null

  /**
   * [WorkBench] used to contain all the preview elements.
   */
  override val workbench = WorkBench<DesignSurface>(project, "Compose Preview", this, this).apply {

    val actionsToolbar = ActionsToolbar(this@PreviewEditor, surface)
    val surfacePanel = JPanel(BorderLayout()).apply {
      add(actionsToolbar.toolbarComponent, BorderLayout.NORTH)
      add(surface, BorderLayout.CENTER)
    }
    val issueErrorSplitter = IssuePanelSplitter(surface, surfacePanel)

    init(issueErrorSplitter, surface, listOf(), false)
    showLoading(message("panel.building"))
  }

  /**
   * Calls refresh method on the the successful gradle build
   */
  private val refresher = SmartAutoRefresher(psiFile, this) { isRefreshingPreview = true }

  init {
    GradleBuildState.subscribe(project, object : GradleBuildListener.Adapter() {
      override fun buildStarted(context: BuildContext) {
        //  Show a loading message only if the content is not already displaying to avoid hiding it.
        if (workbench.isMessageVisible) {
          workbench.showLoading(message("panel.building"))
          workbench.hideContent()
        }
      }
    }, this)
  }

  private fun hasErrorsAndNeedsBuild(): Boolean = surface.models.asSequence()
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

    return lastBuildTimestamp in 1 until modificationStamp;
  }

  override fun status(): ComposePreviewManager.Status = if (isRefreshingPreview)
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
    .any { it.renderResult.isSuccess && it.logger?.brokenClasses?.values?.isEmpty() }

  /**
   * Hides the preview content and shows an error message on the surface.
   */
  private fun showModalErrorMessage(message: String) {
    LOG.debug("showModelErrorMessage: $message")
    workbench.loadingStopped(message)
  }

  /**
   * Updates the surface visibility and displays the content or an error message depending on the build state. This method is called after
   * certain updates like a build or a preview refresh has happened.
   */
  private fun updateSurfaceVisibility() {
    if (workbench.isMessageVisible && !hasAtLeastOneValidPreview()) {
      showModalErrorMessage(message("panel.needs.build"))
    }
    else {
      workbench.hideLoading()
      workbench.showContent()
    }
  }

  override fun buildFailed() {
    isRefreshingPreview = false
    updateSurfaceVisibility()
  }

  /**
   * Refresh the preview surfaces. This will retrieve all the Preview annotations and render those elements.
   */
  private fun doRefresh(filePreviewElements: List<PreviewElement>) {
    // Only display component border if decorations are not showed
    val showBorder = !RenderSettings.getProjectSettings(project).showDecorations
    val stopwatch = if (LOG.isDebugEnabled) StopWatch() else null

    val facet = AndroidFacet.getInstance(psiFile)!!
    val configurationManager = ConfigurationManager.getOrCreateInstance(facet)

    // Retrieve the models that were previously displayed so we can reuse them instead of creating new ones.
    val existingModels = surface.models.reverse().toMutableList()

    // Now we generate all the models (or reuse) for the PreviewElements.
    val newModels = filePreviewElements
      .asSequence()
      .onEach {
        if (LOG.isDebugEnabled) {
          LOG.debug("""Preview found at ${stopwatch?.duration?.toMillis()}ms

              ${it.toPreviewXmlString(withBorder = false)}
          """.trimIndent())
        }
      }
      .map { Pair(it, it.toPreviewXmlString(withBorder = showBorder)) }
      .map {
        val (previewElement, fileContents) = it

        val model = if (existingModels.isNotEmpty()) {
          LOG.debug("Re-using model")
          configureExistingModel(existingModels.pop(), previewElement.displayName, fileContents, surface)
        }
        else {
          LOG.debug("No models to reuse were found. New model.")
          val file = ComposeAdapterLightVirtualFile("testFile.xml", fileContents)
          val configuration = Configuration.create(configurationManager, null, FolderConfiguration.createDefault())
          NlModel.create(this@PreviewEditor,
                         previewElement.displayName,
                         facet,
                         file,
                         configuration,
                         surface.componentRegistrar,
                         modelUpdater)
        }

        val navigable: Navigatable = PsiNavigationSupport.getInstance().createNavigatable(
          project, psiFile.virtualFile, previewElement.previewElementDefinitionPsi?.element?.textOffset ?: 0)
        navigationHandler.addMap(model, navigable, psiFile.virtualFile.name)

        previewElement.configuration.applyTo(model.configuration)

        model
      }
      .toList()

    // Remove and dispose pre-existing models that were not used.
    // This will happen if the user removes one or more previews.
    existingModels.forEach { surface.removeModel(it) }

    val rendersCompletedFuture = if (newModels.isNotEmpty()) {
      val renders = newModels.map {
        // We call addModel even though the model might not be new. If we try to add an existing model,
        // this will trigger a new render which is exactly what we want.
        surface.addModel(it)
      }
      CompletableFuture.allOf(*(renders.toTypedArray()))
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
        updateSurfaceVisibility()
        if (ex != null) {
          LOG.warn(ex)
        }

        isRefreshingPreview = false
        // Make sure all notifications are cleared-up
        EditorNotifications.getInstance(project).updateNotifications(file)

        onRefresh?.invoke()
      }
  }

  /**
   * Requests a refresh the preview surfaces. This will retrieve all the Preview annotations and render those elements.
   * The refresh will only happen if the Preview elements have changed from the last render.
   */
  override fun refresh() {
    val filePreviewElements = previewProvider()

    if (filePreviewElements == previewElements) {
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
        // Make sure to clear refreshing notification
        EditorNotifications.getInstance(project).updateNotifications(file)
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

  fun isEditorForSurface(surface: DesignSurface) = surface == this.surface
}

/**
 * Extension method that returns if the file is a Kotlin file. This method first checks for the extension to fail fast without having to
 * actually trigger the potentially costly [VirtualFile#fileType] call.
 */
private fun VirtualFile.isKotlinFileType(): Boolean =
  extension == KotlinFileType.INSTANCE.defaultExtension && fileType == KotlinFileType.INSTANCE

/**
 * [ToolbarActionGroups] that includes the [ForceCompileAndRefreshAction]
 */
private class ComposePreviewToolbar(private val surface: DesignSurface) :
  ToolbarActionGroups(surface) {
  /**
   * [AnAction] that triggers a compilation of the current module. The build will automatically trigger a refresh
   * of the surface.
   */
  private inner class ForceCompileAndRefreshAction :
    AnAction(message("notification.action.build.and.refresh"), null, WHITE_REFRESH_BUTTON) {
    override fun actionPerformed(e: AnActionEvent) {
      val module = surface.model?.module ?: return
      requestBuild(surface.project, module)
    }

    override fun update(e: AnActionEvent) {
      val presentation = e.presentation
      presentation.isEnabled = true
      when {
        GradleBuildState.getInstance(surface.project).isBuildInProgress -> {
          presentation.icon = BLUE_REFRESH_BUTTON
          presentation.isEnabled = false
        }
        findComposePreviewManagerForSurface(surface)?.status()?.hasErrors == true -> presentation.icon = RED_REFRESH_BUTTON
        else -> presentation.icon = GREEN_REFRESH_BUTTON
      }
    }
  }

  private fun findComposePreviewManagerForSurface(surface: DesignSurface): ComposePreviewManager? =
    FileEditorManager.getInstance(surface.project).allEditors
      .filterIsInstance<ComposeTextEditorWithPreview>()
      .filter { it.preview.isEditorForSurface(surface) }
      .map { it.preview }
      .singleOrNull()

  // TODO(http://b/140948062): needs icon
  private inner class ToggleShowDecorationAction :
    ToggleAction(message("action.show.decorations.title"), message("action.show.decorations.description"), null) {

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      val settings = RenderSettings.getProjectSettings(surface.project)

      if (settings.showDecorations != state) {
        // We also persist the settings to the RenderSettings
        settings.showDecorations = state
        findComposePreviewManagerForSurface(surface)?.invalidateAndRefresh()
      }
    }

    override fun isSelected(e: AnActionEvent): Boolean = RenderSettings.getProjectSettings(surface.project).showDecorations
  }

  override fun getNorthGroup(): ActionGroup = DefaultActionGroup(listOf(
    ForceCompileAndRefreshAction(),
    ToggleShowDecorationAction()
  ))

  override fun getNorthEastGroup(): ActionGroup = DefaultActionGroup().apply {
    addAll(getZoomActionsWithShortcuts(surface, this@ComposePreviewToolbar))
    add(IssueNotificationAction(surface))
  }
}

private class ComposeTextEditorWithPreview constructor(
  val composeTextEditor: TextEditor,
  val preview: PreviewEditor) :
  SeamlessTextEditorWithPreview(composeTextEditor, preview, "Compose Editor"), TextEditor {
  override fun canNavigateTo(navigatable: Navigatable): Boolean {
    return composeTextEditor.canNavigateTo(navigatable)
  }

  override fun navigateTo(navigatable: Navigatable) {
    composeTextEditor.navigateTo(navigatable)
  }

  override fun getEditor(): Editor {
    return composeTextEditor.editor
  }
}

/**
 * Returns the Compose [PreviewEditor] or null if this [FileEditor] is not a Compose preview.
 */
fun FileEditor.getComposePreviewManager(): ComposePreviewManager? = (this as? ComposeTextEditorWithPreview)?.preview

/**
 * Provider for Compose Preview editors.
 *
 * @param projectContainsOldPackageImportsHandler Handler method called when the provider finds imports of the old preview packages. This is
 *  a temporary mechanism until all our internal users have migrated out of the old name.
 */
class ComposeFileEditorProvider(
  private val projectContainsOldPackageImportsHandler: (Project) -> Unit = ::defaultOldPackageNotificationsHandler) : FileEditorProvider, DumbAware {
  private val LOG = Logger.getInstance(ComposeFileEditorProvider::class.java)
  private val previewElementProvider = AnnotationPreviewElementFinder

  init {
    if (StudioFlags.COMPOSE_PREVIEW.get()) {
      DesignerTypeRegistrar.register(object : LayoutEditorFileType() {
        override fun getLayoutEditorStateType() = LayoutEditorState.Type.COMPOSE

        override fun isResourceTypeOf(file: PsiFile): Boolean =
          file.virtualFile is ComposeAdapterLightVirtualFile

        override fun getToolbarActionGroups(surface: DesignSurface): ToolbarActionGroups =
          ComposePreviewToolbar(surface)

        override fun getSelectionContextToolbar(surface: DesignSurface, selection: List<NlComponent>): DefaultActionGroup =
          DefaultActionGroup()
      })
    }
  }

  override fun accept(project: Project, file: VirtualFile): Boolean {
    if (!StudioFlags.COMPOSE_PREVIEW.get() || !file.isKotlinFileType()) {
      return false
    }

    val hasPreviewMethods = previewElementProvider.hasPreviewMethods(project, file)
    if (LOG.isDebugEnabled) {
      LOG.debug("${file.path} hasPreviewMethods=${hasPreviewMethods}")
    }

    if (!hasPreviewMethods) {
      val hasOldImports = PsiTreeUtil.findChildrenOfType(PsiManager.getInstance(project).findFile(file), KtImportDirective::class.java)
        .any { OLD_PREVIEW_ANNOTATION_FQN == it.importedFqName?.asString() }

      if (hasOldImports) {
        projectContainsOldPackageImportsHandler(project)
      }
    }

    return hasPreviewMethods
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    if (LOG.isDebugEnabled) {
      LOG.debug("createEditor file=${file.path}")
    }
    val psiFile = PsiManager.getInstance(project).findFile(file)!!
    val textEditor = getInstance().createEditor(project, file) as TextEditor
    val previewEditor = PreviewEditor(psiFile = psiFile, previewProvider = { previewElementProvider.findPreviewMethods(project, file) })
    val composeEditorWithPreview = ComposeTextEditorWithPreview(textEditor, previewEditor)

    // Queue to avoid refreshing notifications on every key stroke
    val modificationQueue = MergingUpdateQueue("Notifications Update queue",
                                               TimeUnit.SECONDS.toMillis(1).toInt(),
                                               true,
                                               null,
                                               composeEditorWithPreview)
      .apply {
        setRestartTimerOnAdd(true)
      }

    // Update that triggers a preview refresh. It does not trigger a recompile.
    val refreshPreview = object : Update("refreshPreview") {
      override fun run() {
        LOG.debug("refreshPreview requested")
        previewEditor.refresh()
      }
    }

    val updateNotifications = object : Update("updateNotifications") {
      override fun run() {
        LOG.debug("updateNotifications requested")
        if (composeEditorWithPreview.isModified) {
          EditorNotifications.getInstance(project).updateNotifications(file)
        }
      }
    }

    previewEditor.onRefresh = {
      composeEditorWithPreview.isPureTextEditor = previewEditor.previewElements.isEmpty()
    }

    setupChangeListener(
      project,
      psiFile,
      { previewEditor.previewElements },
      { modificationQueue.queue(refreshPreview) },
      { modificationQueue.queue(updateNotifications) },
      composeEditorWithPreview)

    return composeEditorWithPreview
  }

  override fun getEditorTypeId() = "ComposeEditor"

  override fun getPolicy() = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}