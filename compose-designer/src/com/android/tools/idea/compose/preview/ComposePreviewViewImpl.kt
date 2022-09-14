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

import com.android.tools.adtui.PANNABLE_KEY
import com.android.tools.adtui.Pannable
import com.android.tools.adtui.stdui.ActionData
import com.android.tools.adtui.stdui.UrlData
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.editor.PanZoomListener
import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.common.editor.ActionsToolbar
import com.android.tools.idea.common.error.IssuePanelSplitter
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.DesignSurfaceScrollPane
import com.android.tools.idea.common.surface.InteractionManager
import com.android.tools.idea.common.surface.layout.MatchParentLayoutManager
import com.android.tools.idea.compose.preview.ComposePreviewBundle.message
import com.android.tools.idea.editors.build.ProjectBuildStatusManager
import com.android.tools.idea.editors.build.ProjectStatus
import com.android.tools.idea.editors.notifications.NotificationPanel
import com.android.tools.idea.editors.shortcuts.asString
import com.android.tools.idea.editors.shortcuts.getBuildAndRefreshShortcut
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.projectsystem.requestBuild
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.EmptyAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.ui.EditorNotifications
import com.intellij.ui.JBSplitter
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.event.AdjustmentEvent
import javax.swing.*

private const val SURFACE_SPLITTER_DIVIDER_WIDTH_PX = 5
private const val ISSUE_SPLITTER_DIVIDER_WIDTH_PX = 3
private const val COMPOSE_PREVIEW_DOC_URL = "https://d.android.com/jetpack/compose/preview"

/**
 * Interface that isolates the view of the Compose view so it can be replaced for testing.
 */
interface ComposePreviewView {
  /**
   * A list of additional (to the main) design surfaces in the preview view.
   */
  val surfaces: List<NlDesignSurface>

  val pinnedSurface: NlDesignSurface
    get() = surfaces[0]

  val mainSurface: NlDesignSurface

  /**
   * Returns the [JComponent] containing this [ComposePreviewView] that can be used to embed it other panels.
   */
  val component: JComponent

  /**
   * Allows replacing the bottom panel in the [ComposePreviewView]. Used to display the animations component.
   */
  var bottomPanel: JComponent?

  /**
   * Sets whether the panel is showed display pin toolbar
   */
  var showPinToolbar: Boolean

  /**
   * Sets whether the panel has content to display. If it does not, it will display an overlay with a message for the user.
   */
  var hasContent: Boolean

  /**
   * True if the view is displaying an overlay with a message.
   */
  val isMessageBeingDisplayed: Boolean

  /**
   * If true, the contents have been at least rendered once.
   */
  var hasRendered: Boolean

  /**
   * Method called to force an update on the notifications for the given [FileEditor].
   */
  fun updateNotifications(parentEditor: FileEditor)

  /**
   * Updates the surface visibility and displays the content or an error message depending on the build state. This method is called after
   * certain updates like a build or a preview refresh has happened.
   * Calling this method will also update the FileEditor notifications.
   */
  fun updateVisibilityAndNotifications()

  /**
   * If the content is not already visible it shows the given message.
   */
  fun updateProgress(message: String)

  /**
   * If called the pinned previews will be shown/hidden at the top.
   */
  fun setPinnedSurfaceVisibility(visible: Boolean)

  /**
   * Called when a refresh in progress was cancelled by the user.
   */
  fun onRefreshCancelledByTheUser()

  /**
   * Called when a refresh has completed.
   */
  fun onRefreshCompleted()
}

fun interface ComposePreviewViewProvider {
  fun invoke(project: Project,
             psiFilePointer: SmartPsiElementPointer<PsiFile>,
             projectBuildStatusManager: ProjectBuildStatusManager,
             dataProvider: DataProvider,
             mainDesignSurfaceBuilder: NlDesignSurface.Builder,
             designSurfaceBuilders: List<NlDesignSurface.Builder>,
             parentDisposable: Disposable,
             onPinFileAction: AnAction,
             onUnPinAction: AnAction): ComposePreviewView
}

/**
 * Creates a [JPanel] using an [OverlayLayout] containing all the given [JComponent]s.
 */
private fun createOverlayPanel(vararg components: JComponent): JPanel =
  object : JPanel() {
    // Since the overlay panel is transparent, we can not use optimized drawing or it will produce rendering artifacts.
    override fun isOptimizedDrawingEnabled(): Boolean = false
  }.apply<JPanel> {
    layout = OverlayLayout(this)
    components.forEach {
      it.alignmentX = Component.LEFT_ALIGNMENT
      it.alignmentY = Component.TOP_ALIGNMENT
      add(it)
    }
  }

private class PinnedLabelPanel(pinAction: AnAction) : JPanel() {
  private val button = ActionButtonWithText(pinAction,
                                            PresentationFactory().getPresentation(pinAction ?: EmptyAction()),
                                            "PinnedToolbar",
                                            ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE).apply {
    foreground = NamedColorUtil.getInactiveTextColor()
    font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
  }

  init {
    add(button)
  }

  fun update() {
    button.update()
  }

  override fun getMaximumSize(): Dimension = preferredSize
}

/**
 * [WorkBench] panel used to contain all the compose preview elements.
 *
 * This view contains all the different components that are part of the Compose Preview. If expanded, in the content area the different
 * areas look as follows:
 * ```
 * +------------------------------------------+
 * |                                          |     |             |
 * |       pinnedSurface                      |     |             |
 * |                                          |     |             |
 * |                                          |     |             |
 * +------------------------------------------+     | surfacesSplitter
 * |                                          |     |             |
 * |       mainSurface                        |     |             |  mainSplitter
 * |                                          |     |             |
 * |                                          |     |             |
 * +------------------------------------------+                   |
 * |                                          |                   |
 * |       bottomPanel (for animations Panel) |                   |
 * |                                          |                   |
 * |                                          |                   |
 * +------------------------------------------+
 * ```
 *
 * The mainSplitter top panel contains the scrollable panel and, also as overlays, the zoom controls and the label that indicates what is
 * currently pinned.
 *
 * @param project the current open project
 * @param psiFilePointer an [SmartPsiElementPointer] pointing to the file being rendered within this panel. Used to handle
 *   which notifications should be displayed.
 * @param projectBuildStatusManager [ProjectBuildStatusManager] used to detect the current build status and show/hide the correct loading
 *   message.
 * @param dataProvider the [DataProvider] to be used by the [pinnedSurface] and [mainSurface] panel.
 * @param mainDesignSurfaceBuilder a builder to create main design surface
 * @param designSurfaceBuilders a list of builders to create additional design surfaces
 * @param parentDisposable the [Disposable] to use as parent disposable for this panel.
 * @param onPinFileAction action to perform when pin file label is clicked
 * @param onUnPinAction action to perform when unpin file label is clicked
 */
internal class ComposePreviewViewImpl(private val project: Project,
                                      private val psiFilePointer: SmartPsiElementPointer<PsiFile>,
                                      private val projectBuildStatusManager: ProjectBuildStatusManager,
                                      dataProvider: DataProvider,
                                      mainDesignSurfaceBuilder: NlDesignSurface.Builder,
                                      designSurfaceBuilders: List<NlDesignSurface.Builder>,
                                      parentDisposable: Disposable,
                                      onPinFileAction: AnAction,
                                      onUnPinAction: AnAction) :
  ComposePreviewView, Pannable, DataProvider {

  private val workbench = WorkBench<DesignSurface<*>>(project, "Compose Preview", null, parentDisposable, 0)

  private val log = Logger.getInstance(ComposePreviewViewImpl::class.java)

  override val surfaces by lazy { designSurfaceBuilders.map { it.build() } }

  override val mainSurface = mainDesignSurfaceBuilder.setDelegateDataProvider { key ->
    if (PANNABLE_KEY.`is`(key)) {
      this@ComposePreviewViewImpl
    } else if (InteractionManager.CURSOR_RECEIVER.`is`(key)) {
      // TODO(b/229842640): We should actually pass the [scrollPane] here, but it does not work
      workbench
    } else dataProvider.getData(key)
  }.build().also {
    it.addPanZoomListener(object : PanZoomListener {
      override fun zoomChanged(previousScale: Double, newScale: Double) =
        this@ComposePreviewViewImpl.surfaces.stream().forEach { s -> s.setScale(newScale) }

      override fun panningChanged(adjustmentEvent: AdjustmentEvent?) {
      }
    })
  }

  override val isPannable: Boolean
    get() = mainSurface.isPannable
  override var isPanning: Boolean
    get() = mainSurface.isPanning
    set(value) {
      mainSurface.isPanning = value
      surfaces.forEach { it.isPanning = value }
    }

  override val component: JComponent = workbench

  private val pinnedPanelLabel = PinnedLabelPanel(onUnPinAction)
  private val mainSurfacePinLabel = PinnedLabelPanel(onPinFileAction)

  /**
   * Vertical splitter where the top part is a surface containing the pinned elements and the bottom the main design surface.
   */
  private val surfaceSplitter = JBSplitter(true, 0.25f, 0f, 0.5f).apply {
    dividerWidth = SURFACE_SPLITTER_DIVIDER_WIDTH_PX
    // surfaceSplitter.firstComponent will contain the pinned surface elements
    secondComponent = mainSurface
  }

  private val notificationPanel = NotificationPanel(
    ExtensionPointName.create("com.android.tools.idea.compose.preview.composeEditorNotificationProvider"))

  /**
   * Panel containing pinning button.
   */
  private val pinToolbarContainer = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
    border = JBUI.Borders.empty()
    isVisible = false
    isOpaque = false
    alignmentX = Component.LEFT_ALIGNMENT
    alignmentY = Component.TOP_ALIGNMENT
  }

  private val scrollPane = DesignSurfaceScrollPane.createDefaultScrollPane(surfaceSplitter, mainSurface.background) {
  }

  override var scrollPosition: Point
    get() = scrollPane.viewport.viewPosition
    set(value) {
      val extentSize = scrollPane.viewport.extentSize
      val viewSize = scrollPane.viewport.viewSize
      val maxAvailableWidth = viewSize.width - extentSize.width
      val maxAvailableHeight = viewSize.height - extentSize.height

      value.setLocation(value.x.coerceIn(0, maxAvailableWidth), value.y.coerceIn(0, maxAvailableHeight))
      scrollPane.viewport.viewPosition = value
    }

  /**
   * True if the pinned surface is visible in the preview.
   */
  private var isPinnedSurfaceVisible = false

  /**
   * Vertical splitter where the top component is the [surfaceSplitter] and the bottom component, when visible, is an auxiliary
   * panel associated with the preview. For example, it can be an animation inspector that lists all the animations the preview has.
   */
  private val mainPanelSplitter = JBSplitter(true, 0.7f).apply {
    dividerWidth = ISSUE_SPLITTER_DIVIDER_WIDTH_PX
  }

  /**
   * [ActionData] that triggers Build and Refresh of the preview.
   */
  private val buildAndRefreshAction: ActionData
    get() {
      val actionDataText = "${message("panel.needs.build.action.text")}${getBuildAndRefreshShortcut().asString()}"
      return ActionData(actionDataText) {
        psiFilePointer.element?.virtualFile?.let { project.requestBuild(it) }
        workbench.repaint() // Repaint the workbench, otherwise the text and link will keep displaying if the mouse is hovering the link
      }
    }
  private lateinit var actionsToolbar: ActionsToolbar

  init {
    mainSurface.name = "Compose"

    val layeredPane = JLayeredPane().apply {
      isFocusable = true
      isOpaque = true
      layout = MatchParentLayoutManager()

      val zoomControlsLayerPane = object : JPanel(BorderLayout()) {
        override fun isOptimizedDrawingEnabled(): Boolean = false
      }.apply {
        border = JBUI.Borders.empty(UIUtil.getScrollBarWidth())
        isOpaque = false
        isFocusable = false
        add(mainSurface.actionManager.createDesignSurfaceToolbar(), BorderLayout.EAST)
      }
      this.add(zoomControlsLayerPane, JLayeredPane.DRAG_LAYER as Integer)
      this.add(scrollPane, JLayeredPane.POPUP_LAYER as Integer)
    }

    val contentPanel = JPanel(BorderLayout()).apply {
      actionsToolbar = ActionsToolbar(parentDisposable, mainSurface)
      add(actionsToolbar.toolbarComponent, BorderLayout.NORTH)

      // Panel containing notifications and the pin label
      val topPanel = JPanel(VerticalLayout(0)).apply {
        isOpaque = false
        isFocusable = false
        add(notificationPanel, VerticalLayout.FILL_HORIZONTAL)
        add(pinToolbarContainer)
      }

      add(createOverlayPanel(topPanel, layeredPane), BorderLayout.CENTER)
    }

    mainPanelSplitter.firstComponent = contentPanel

    val issueErrorSplitter = IssuePanelSplitter(psiFilePointer.virtualFile, mainSurface, mainPanelSplitter)

    workbench.init(issueErrorSplitter, mainSurface, listOf(), false)
    workbench.hideContent()
    if (projectBuildStatusManager.status == ProjectStatus.NeedsBuild) {
      log.debug("Project needs build")
      showNeedsToBuildErrorPanel()
    }
    else {
      val message = when {
        projectBuildStatusManager.isBuilding -> message("panel.building")
        DumbService.getInstance(project).isDumb -> message("panel.indexing")
        else -> message("panel.initializing")
      }
      log.debug("Show loading: $message")
      workbench.showLoading(message)
    }
    workbench.focusTraversalPolicy = LayoutFocusTraversalPolicy()
    workbench.isFocusCycleRoot = true
  }

  override fun updateProgress(message: String) = UIUtil.invokeLaterIfNeeded {
    log.debug("updateProgress: $message")
    if (workbench.isMessageVisible) {
      workbench.showLoading(message)
      workbench.hideContent()
    }
  }

  override fun setPinnedSurfaceVisibility(visible: Boolean) {
    UIUtil.invokeLaterIfNeeded {
      if (StudioFlags.COMPOSE_PIN_PREVIEW.get() && visible) {
        isPinnedSurfaceVisible = true
        surfaceSplitter.firstComponent = pinnedSurface
      }
      else {
        isPinnedSurfaceVisible = false
        surfaceSplitter.firstComponent = null
      }

      if (StudioFlags.COMPOSE_PIN_PREVIEW.get()) {
        pinToolbarContainer.removeAll()
        pinToolbarContainer.add(if (isPinnedSurfaceVisible) pinnedPanelLabel else mainSurfacePinLabel)
      }

      // The main surface label is only displayed if there is a text and the pinned surface is not already visible.
      updateVisibilityAndNotifications()
    }
  }

  private fun showModalErrorMessage(message: String, actionData: ActionData?) = UIUtil.invokeLaterIfNeeded {
    log.debug("showModelErrorMessage: $message")
    workbench.loadingStopped(message, actionData)
  }

  override fun updateNotifications(parentEditor: FileEditor) = UIUtil.invokeLaterIfNeeded {
    if (Disposer.isDisposed(workbench) || project.isDisposed || !parentEditor.isValid) return@invokeLaterIfNeeded

    notificationPanel.updateNotifications(psiFilePointer.virtualFile, parentEditor, project)
  }

  /**
   * Method called to ask all notifications to update.
   */
  private fun updateNotifications() = UIUtil.invokeLaterIfNeeded {
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
    else
      onRefreshCompleted()
  }

  override fun onRefreshCompleted() {
    updateVisibilityAndNotifications()
  }

  /**
   * Updates the surface visibility and displays the content or an error message depending on the build state. This method is called after
   * certain updates like a build or a preview refresh has happened.
   * Calling this method will also update the FileEditor notifications.
   */
  override fun updateVisibilityAndNotifications() = UIUtil.invokeLaterIfNeeded {
    if (workbench.isMessageVisible && projectBuildStatusManager.status == ProjectStatus.NeedsBuild) {
      log.debug("Needs successful build")
      showNeedsToBuildErrorPanel()
    }
    else {
      if (hasRendered) {
        log.debug("Show content")
        workbench.hideLoading()
        if (hasContent) {
          if (workbench.showContent()) {
            // We invoke later to allow the panel to layout itself before calling zoomToFit.
            ApplicationManager.getApplication().invokeLater {
              // We zoom to fit the pinned surface to have better initial zoom level when first build is completed.
              if (isPinnedSurfaceVisible) pinnedSurface.zoomToFit()
              // We don't zoom to fit the mainSurface because it restores to the last zoom level when it opened.
              // If there is no last zoom level (e.g. it is a new file), it zooms to fit to have the initial zoom level. See
              // addComponentListener(...) in the constructor of DesignSurface.
            }
          }
        }
        else {
          workbench.hideContent()
          workbench.loadingStopped(message("panel.no.previews.defined"),
                         null,
                         UrlData(message("panel.no.previews.action"), COMPOSE_PREVIEW_DOC_URL),
                         null)
        }

        if (StudioFlags.COMPOSE_PIN_PREVIEW.get()) {
          mainSurfacePinLabel.isVisible = !isPinnedSurfaceVisible
          mainSurfacePinLabel.update()
          pinnedPanelLabel.update()
        }
      }
    }

    if (StudioFlags.COMPOSE_PIN_PREVIEW.get()) {
      // The "pin this file" action is only visible if the pin surface is not visible and if we are not in interactive nor animation preview.
      pinToolbarContainer.isVisible = showPinToolbar
    }
    else {
      pinToolbarContainer.isVisible = false
    }

    updateNotifications()
  }

  override var showPinToolbar: Boolean = true

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