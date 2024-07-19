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

import com.android.annotations.concurrency.Slow
import com.android.flags.ifEnabled
import com.android.tools.adtui.PANNABLE_KEY
import com.android.tools.adtui.Pannable
import com.android.tools.adtui.stdui.ActionData
import com.android.tools.adtui.stdui.UrlData
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.common.editor.ActionsToolbar
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.model.NlModelUpdaterInterface
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.GuiInputHandler
import com.android.tools.idea.common.surface.handleLayoutlibNativeCrash
import com.android.tools.idea.compose.PsiComposePreviewElement
import com.android.tools.idea.compose.PsiComposePreviewElementInstance
import com.android.tools.idea.compose.preview.actions.ml.GenerateComposePreviewsForFileAction
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.editors.build.RenderingBuildStatus
import com.android.tools.idea.editors.build.RenderingBuildStatusManager
import com.android.tools.idea.editors.notifications.NotificationPanel
import com.android.tools.idea.editors.shortcuts.asString
import com.android.tools.idea.editors.shortcuts.getBuildAndRefreshShortcut
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.analytics.PreviewRefreshEventBuilder
import com.android.tools.idea.preview.gallery.GalleryModeProperty
import com.android.tools.idea.preview.mvvm.PreviewRepresentationView
import com.android.tools.idea.preview.navigation.PreviewNavigationHandler
import com.android.tools.idea.preview.refreshExistingPreviewElements
import com.android.tools.idea.preview.updatePreviewsAndRefresh
import com.android.tools.idea.rendering.tokens.requestBuildArtifactsForRendering
import com.android.tools.idea.studiobot.StudioBot
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlSurfaceBuilder
import com.android.tools.preview.ComposePreviewElement
import com.android.tools.preview.PreviewDisplaySettings
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.ui.EditorNotifications
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.LayoutFocusTraversalPolicy
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.idea.core.util.toPsiFile

private const val COMPOSE_PREVIEW_DOC_URL = "https://d.android.com/jetpack/compose/preview"

/** Interface that isolates the view of the Compose view so it can be replaced for testing. */
interface ComposePreviewView : PreviewRepresentationView {

  /**
   * Sets whether the panel has content to display. If it does not, it will display an overlay with
   * a message for the user.
   */
  var hasContent: Boolean

  /** True if the view is displaying an overlay with a message. */
  val isMessageBeingDisplayed: Boolean

  /** If true, the contents have been at least rendered once. */
  var hasRendered: Boolean

  /** Method called to force an update on the notifications for the given [FileEditor]. */
  fun updateNotifications(parentEditor: FileEditor)

  /**
   * Updates the surface visibility and displays the content or an error message depending on the
   * build state. This method is called after certain updates like a build or a preview refresh has
   * happened. Calling this method will also update the FileEditor notifications.
   */
  fun updateVisibilityAndNotifications()

  /** If the content is not already visible it shows the given message. */
  fun updateProgress(message: String)

  /** Called when a refresh in progress was cancelled by the user. */
  fun onRefreshCancelledByTheUser()

  /** Called when a refresh has completed. */
  fun onRefreshCompleted()

  /**
   * Called when a Layoutlib native crash is detected. It will show a notification to the user
   * allowing to re-enable the disabled Layoutlib. If the user chooses to re-enable layoutlib,
   * [onLayoutlibReEnable] will be called.
   */
  fun onLayoutlibNativeCrash(onLayoutlibReEnable: () -> Unit)

  /**
   * Updates the list of previews to be rendered in this [ComposePreviewView], requests a refresh
   * and returns the list of [ComposePreviewElement] being rendered.
   *
   * By default, refreshes all the previews on the [mainSurface]. Implementors of this interface can
   * override this method to add extra logic if rendering a different set of previews and/or if
   * rendering them in a different component.
   */
  suspend fun updatePreviewsAndRefresh(
    reinflate: Boolean,
    previewElements: Collection<PsiComposePreviewElementInstance>,
    psiFile: PsiFile,
    progressIndicator: ProgressIndicator,
    onRenderCompleted: (Int) -> Unit,
    previewElementModelAdapter: ComposePreviewElementModelAdapter,
    modelUpdater: NlModelUpdaterInterface,
    navigationHandler: PreviewNavigationHandler,
    configureLayoutlibSceneManager:
      (PreviewDisplaySettings, LayoutlibSceneManager) -> LayoutlibSceneManager,
    refreshEventBuilder: PreviewRefreshEventBuilder?,
  ): List<PsiComposePreviewElementInstance> {

    return mainSurface.updatePreviewsAndRefresh(
      // Don't reuse models when in gallery mode to avoid briefly showing an unexpected/mixed
      // state of the old and new preview.
      tryReusingModels = galleryMode == null,
      reinflate,
      previewElements,
      Logger.getInstance(ComposePreviewView::class.java),
      psiFile,
      mainSurface,
      progressIndicator,
      onRenderCompleted,
      previewElementModelAdapter,
      modelUpdater,
      navigationHandler,
      configureLayoutlibSceneManager,
      refreshEventBuilder,
    )
  }

  /**
   * Refreshes the [ComposePreviewElement]s corresponding to the [NlModel]s that should currently be
   * displayed in this [ComposePreviewView]. The [modelToPreview] argument is used to map [NlModel]s
   * to [ComposePreviewElement].
   *
   * By default, refreshes the elements corresponding to the [mainSurface] models. Implementors of
   * this interface can override this method if they want to render a different set of elements.
   */
  @Slow
  suspend fun refreshExistingPreviewElements(
    progressIndicator: ProgressIndicator,
    modelToPreview: NlModel.() -> PsiComposePreviewElement?,
    configureLayoutlibSceneManager:
      (PreviewDisplaySettings, LayoutlibSceneManager) -> LayoutlibSceneManager,
    refreshFilter: (LayoutlibSceneManager) -> Boolean,
    refreshOrder: (LayoutlibSceneManager) -> Int,
    refreshEventBuilder: PreviewRefreshEventBuilder?,
  ) {
    mainSurface.refreshExistingPreviewElements(
      progressIndicator,
      modelToPreview,
      configureLayoutlibSceneManager,
      refreshFilter,
      refreshOrder,
      refreshEventBuilder,
    )
  }
}

fun interface ComposePreviewViewProvider {
  fun invoke(
    project: Project,
    psiFilePointer: SmartPsiElementPointer<PsiFile>,
    renderingBuildStatusManager: RenderingBuildStatusManager,
    dataProvider: DataProvider,
    mainDesignSurfaceBuilder: NlSurfaceBuilder,
    parentDisposable: Disposable,
  ): ComposePreviewView
}

/**
 * [WorkBench] panel used to contain all the Compose Preview elements.
 *
 * This view contains all the different components that are part of the Compose Preview. If
 * expanded, in the content area the different areas look as follows:
 * ```
 * +------------------------------------------+
 * |                                          |     |
 * |       mainSurface                        |     |
 * |                                          |     |
 * |                                          |     |
 * +------------------------------------------+     | mainPanelSplitter
 * |                                          |     |
 * |   bottomPanel (for Animations Preview)   |     |
 * |                                          |     |
 * |                                          |     |
 * +------------------------------------------+
 * ```
 *
 * The [mainPanelSplitter] top panel contains the scrollable panel ([mainSurface]) and, also as
 * overlays, the zoom controls.
 *
 * @param project the current open project
 * @param psiFilePointer an [SmartPsiElementPointer] pointing to the file being rendered within this
 *   panel. Used to handle which notifications should be displayed.
 * @param renderingBuildStatusManager [RenderingBuildStatusManager] used to detect the current build
 *   status and show/hide the correct loading message.
 * @param dataProvider the [DataProvider] to be used by the [mainSurface] panel.
 * @param mainDesignSurfaceBuilder a builder to create main design surface
 * @param parentDisposable the [Disposable] to use as parent disposable for this panel.
 */
internal class ComposePreviewViewImpl(
  private val project: Project,
  private val psiFilePointer: SmartPsiElementPointer<PsiFile>,
  private val renderingBuildStatusManager: RenderingBuildStatusManager,
  dataProvider: DataProvider,
  mainDesignSurfaceBuilder: NlSurfaceBuilder,
  parentDisposable: Disposable,
) : ComposePreviewView, DataProvider {

  private val workbench =
    WorkBench<DesignSurface<*>>(project, "Compose Preview", null, parentDisposable, 0)

  private val log = Logger.getInstance(ComposePreviewViewImpl::class.java)

  private val scope = AndroidCoroutineScope(parentDisposable)

  override val mainSurface =
    mainDesignSurfaceBuilder
      .setDelegateDataProvider { key ->
        when {
          PANNABLE_KEY.`is`(key) -> pannable
          // TODO(b/229842640): We should actually pass the [scrollPane] here, but it does not work
          GuiInputHandler.CURSOR_RECEIVER.`is`(key) -> workbench
          PlatformCoreDataKeys.BGT_DATA_PROVIDER.`is`(key) -> {
            DataProvider { getDataInBackground(it) }
          }
          else -> dataProvider.getData(key)
        }
      }
      .build()
      .apply {
        // Set the initial scale value to 0.25, so the preview is not large before first
        // zoom-to-fit is triggered.
        zoomController.setScale(0.25)
      }

  private val pannable: Pannable = mainSurface.pannable

  private fun getDataInBackground(dataId: String): Any? {
    if (CommonDataKeys.PSI_ELEMENT.`is`(dataId)) {
      // DesignSurface's data provider will return the root XML element, which for Compose
      // Previews is a ComposeViewAdapter. This is used, for example, to populate the editor's
      // navigation bar. If we return ComposeViewAdapter, the navigation bar will show the file
      // that contains it, i.e. the ComposeAdapterLightVirtualFile XML file. Instead, we should
      // return its origin file, so the navigation bar will show the kotlin file containing the
      // composable.
      return (mainSurface.models.firstOrNull()?.virtualFile as? ComposeAdapterLightVirtualFile)
        ?.originFile
        ?.toPsiFile(project)
    }
    return null
  }

  override val component: JComponent = workbench

  private val notificationPanel =
    NotificationPanel(
      ExtensionPointName.create(
        "com.android.tools.idea.compose.preview.composeEditorNotificationProvider"
      )
    )

  /**
   * Vertical splitter where the top component is the [mainSurface] and the bottom component, when
   * visible, is an auxiliary panel associated with the preview. For example, it can be an animation
   * inspector that lists all the animations the preview has.
   */
  private val mainPanelSplitter =
    OnePixelSplitter(true, 0.7f).apply { this.setBlindZone { Insets(1, 0, 1, 0) } }

  /** [ActionData] that triggers Build and Refresh of the preview. */
  private val buildAndRefreshAction: ActionData
    get() {
      val actionDataText =
        "${message("panel.needs.build.action.text")}${getBuildAndRefreshShortcut().asString()}"
      return ActionData(actionDataText) {
        val virtualFile = psiFilePointer.element?.virtualFile
        scope.launch(workerThread) {
          if (virtualFile != null) project.requestBuildArtifactsForRendering(virtualFile)
          withContext(uiThread) {
            // Repaint the workbench, otherwise the text and link will keep displaying if the mouse
            // is hovering the link
            workbench.repaint()
          }
        }
      }
    }

  private val actionsToolbar: ActionsToolbar

  val content = JPanel(BorderLayout())

  init {
    mainSurface.name = "Compose"

    val contentPanel =
      JPanel(BorderLayout()).apply {
        actionsToolbar = ActionsToolbar(parentDisposable, mainSurface)
        add(actionsToolbar.toolbarComponent, BorderLayout.NORTH)

        // Panel containing notifications label
        val topPanel =
          JPanel(VerticalLayout(0)).apply {
            isOpaque = false
            isFocusable = false
            add(notificationPanel, VerticalLayout.FILL)
          }

        content.add(topPanel, BorderLayout.NORTH)
        content.add(mainSurface, BorderLayout.CENTER)

        add(content, BorderLayout.CENTER)
      }

    mainPanelSplitter.firstComponent = contentPanel

    workbench.init(mainPanelSplitter, mainSurface, listOf(), false)
    workbench.hideContent()
    val projectStatus = renderingBuildStatusManager.statusFlow.value
    log.debug("ProjectStatus: $projectStatus")
    when (projectStatus) {
      RenderingBuildStatus.NeedsBuild -> {
        if (psiFilePointer.virtualFile.fileSystem.isReadOnly) {
          log.debug("Preview not supported in read-only files")
          showModalErrorMessage(message("panel.read.only.file"))
        } else {
          log.debug("Project needs build")
          showNeedsToBuildErrorPanel()
        }
      }
      RenderingBuildStatus.Building -> workbench.showLoading(message("panel.building"))
      RenderingBuildStatus.NotReady -> workbench.showLoading(message("panel.initializing"))
      else -> {
        if (DumbService.getInstance(project).isDumb)
          workbench.showLoading(message("panel.indexing"))
      }
    }
    workbench.focusTraversalPolicy = LayoutFocusTraversalPolicy()
    workbench.isFocusCycleRoot = true

    DataManager.registerDataProvider(workbench) { getData(it) }
    Disposer.register(parentDisposable) { DataManager.removeDataProvider(workbench) }
  }

  override var galleryMode by GalleryModeProperty(content, mainSurface)

  override fun updateProgress(message: String) =
    UIUtil.invokeLaterIfNeeded {
      log.debug("updateProgress: $message")
      if (workbench.isMessageVisible) {
        workbench.showLoading(message)
        workbench.hideContent()
      }
    }

  private fun showModalErrorMessage(message: String, actionData: ActionData? = null) =
    UIUtil.invokeLaterIfNeeded {
      log.debug("showModelErrorMessage: $message")
      workbench.loadingStopped(message, actionData)
    }

  override fun updateNotifications(parentEditor: FileEditor) =
    UIUtil.invokeLaterIfNeeded {
      if (Disposer.isDisposed(workbench) || project.isDisposed || !parentEditor.isValid)
        return@invokeLaterIfNeeded

      notificationPanel.updateNotifications(psiFilePointer.virtualFile, parentEditor, project)
    }

  /** Method called to ask all notifications to update. */
  private fun updateNotifications() =
    UIUtil.invokeLaterIfNeeded {
      actionsToolbar.updateActions()
      // Make sure all notifications are cleared-up
      if (!project.isDisposed) {
        EditorNotifications.getInstance(project).updateNotifications(psiFilePointer.virtualFile)
      }
    }

  /**
   * Shows an error message saying a successful build is needed and a link to trigger a new build.
   */
  private fun showNeedsToBuildErrorPanel() {
    showModalErrorMessage(message("panel.needs.build"), buildAndRefreshAction)
  }

  override fun onRefreshCancelledByTheUser() {
    if (!hasRendered)
      showModalErrorMessage(message("panel.refresh.cancelled"), buildAndRefreshAction)
    else onRefreshCompleted()
  }

  override fun onRefreshCompleted() {
    updateVisibilityAndNotifications()
  }

  override fun onLayoutlibNativeCrash(onLayoutlibReEnable: () -> Unit) {
    workbench.handleLayoutlibNativeCrash(onLayoutlibReEnable)
  }

  /**
   * Updates the surface visibility and displays the content or an error message depending on the
   * build state. This method is called after certain updates like a build or a preview refresh has
   * happened. Calling this method will also update the FileEditor notifications.
   */
  override fun updateVisibilityAndNotifications() =
    UIUtil.invokeLaterIfNeeded {
      if (
        workbench.isMessageVisible &&
          renderingBuildStatusManager.status == RenderingBuildStatus.NeedsBuild
      ) {
        if (psiFilePointer.virtualFile.fileSystem.isReadOnly) {
          showModalErrorMessage(message("panel.read.only.file"))
        } else {
          log.debug("Needs successful build")
          showNeedsToBuildErrorPanel()
        }
      } else {
        if (hasRendered) {
          log.debug("Show content")
          workbench.hideLoading()
          if (hasContent) {
            workbench.showContent()
          } else {
            workbench.hideContent()
            workbench.loadingStopped(
              message("panel.no.previews.defined"),
              null,
              UrlData(message("panel.no.previews.action"), COMPOSE_PREVIEW_DOC_URL),
              StudioFlags.COMPOSE_PREVIEW_GENERATE_ALL_PREVIEWS_FILE.ifEnabled {
                if (!StudioBot.getInstance().isContextAllowed(project)) {
                  return@ifEnabled null
                }

                ActionData(message("action.generate.previews.for.file")) {
                  val psiFile = psiFilePointer.element ?: return@ActionData
                  val selectedEditor =
                    (FileEditorManager.getInstance(psiFile.project).selectedEditor
                        as? TextEditorWithPreview)
                      ?.editor ?: return@ActionData
                  val simpleContext =
                    SimpleDataContext.builder()
                      .add(CommonDataKeys.PSI_FILE, psiFile)
                      .add(CommonDataKeys.EDITOR, selectedEditor)
                      .build()
                  ActionUtil.invokeAction(
                    GenerateComposePreviewsForFileAction(),
                    simpleContext,
                    ActionPlaces.UNKNOWN,
                    null,
                    null,
                  )
                }
              },
            )
          }
        }
      }

      updateNotifications()
    }

  override var hasContent: Boolean = false

  override val isMessageBeingDisplayed: Boolean
    get() = this.workbench.isMessageVisible

  @get:Synchronized
  override var hasRendered: Boolean = false
    @Synchronized
    set(value) {
      field = value
      updateVisibilityAndNotifications()
    }

  override var bottomPanel: JComponent?
    set(value) {
      mainPanelSplitter.secondComponent = value
    }
    get() = mainPanelSplitter.secondComponent

  override fun getData(dataId: String): Any? {
    return if (DESIGN_SURFACE.`is`(dataId)) mainSurface else null
  }
}
