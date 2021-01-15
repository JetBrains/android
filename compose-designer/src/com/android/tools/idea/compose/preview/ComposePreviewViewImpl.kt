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
import com.android.tools.adtui.stdui.UrlData
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.editor.PanZoomListener
import com.android.tools.idea.common.editor.ActionsToolbar
import com.android.tools.idea.common.error.IssuePanelSplitter
import com.android.tools.idea.common.surface.DelegateInteractionHandler
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.DesignSurfaceScrollPane
import com.android.tools.idea.common.surface.LayoutlibInteractionHandler
import com.android.tools.idea.common.surface.layout.MatchParentLayoutManager
import com.android.tools.idea.compose.preview.navigation.PreviewNavigationHandler
import com.android.tools.idea.compose.preview.scene.ComposeSceneComponentProvider
import com.android.tools.idea.compose.preview.scene.ComposeSceneUpdateListener
import com.android.tools.idea.editors.notifications.NotificationPanel
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.graphics.NlConstants
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.scene.RealTimeSessionClock
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.NlInteractionHandler
import com.android.tools.idea.uibuilder.surface.layout.GridSurfaceLayoutManager
import com.intellij.ide.plugins.newui.VerticalLayout
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.EmptyAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.ui.EditorNotifications
import com.intellij.ui.JBSplitter
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Insets
import java.awt.Point
import java.awt.event.AdjustmentEvent
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.OverlayLayout

private const val SURFACE_SPLITTER_DIVIDER_WIDTH_PX = 5
private const val ISSUE_SPLITTER_DIVIDER_WIDTH_PX = 3
private const val COMPOSE_PREVIEW_DOC_URL = "https://d.android.com/jetpack/compose/preview"

/**
 * Interface that isolates the view of the Compose view so it can be replaced for testing.
 */
interface ComposePreviewView {
  val pinnedSurface: NlDesignSurface
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
   * Sets whether the scene overlay is visible. If true, the outline for the components will be visible and selectable.
   */
  var hasComponentsOverlay: Boolean

  /**
   * Sets whether the panel is in "interactive" mode. If it's in interactive mode, the clicks will be forwarded to the
   * code being previewed using the [LayoutlibInteractionHandler].
   */
  var isInteractive: Boolean

  /**
   * Sets whether the panel is in animation preview mode. When this mode is active, the panel might need to show/hide different elements,
   * e.g. hiding the pinned previews panel.
   */
  var isAnimationPreview: Boolean

  /**
   * Sets whether the panel has content to display. If it does not, it will display an overlay with a message for the user.
   */
  var hasContent: Boolean

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
   * Hides the content if visible and displays the given [message].
   */
  fun showModalErrorMessage(message: String)

  /**
   * If the content is not already visible it shows the given message.
   */
  fun updateProgress(message: String)

  /**
   * If called the pinned previews will be shown/hidden at the top.
   */
  fun setPinnedSurfaceVisibility(visible: Boolean)
}

fun interface ComposePreviewViewProvider {
  fun invoke(project: Project,
             psiFilePointer: SmartPsiElementPointer<PsiFile>,
             projectBuildStatusManager: ProjectBuildStatusManager,
             navigationHandler: PreviewNavigationHandler,
             dataProvider: DataProvider,
             parentDisposable: Disposable,
             onPinFileAction: AnAction?,
             onUnPinAction: AnAction?): ComposePreviewView
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

private class PinnedLabelPanel(pinAction: AnAction? = null) : JPanel() {
  private val button = ActionButtonWithText(pinAction,
                                            PresentationFactory().getPresentation(pinAction ?: EmptyAction()),
                                            "PinnedToolbar",
                                            ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE).apply {
    foreground = UIUtil.getInactiveTextColor()
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
 * @param project the current open project
 * @param psiFilePointer an [SmartPsiElementPointer] pointing to the file being rendered within this panel. Used to handle
 *   which notifications should be displayed.
 * @param projectBuildStatusManager [ProjectBuildStatusManager] used to detect the current build status and show/hide the correct loading
 *   message.
 * @param navigationHandler the [PreviewNavigationHandler] used to handle the source code navigation when user clicks a component.
 * @param dataProvider the [DataProvider] to be used by this panel.
 * @param parentDisposable the [Disposable] to use as parent disposable for this panel.
 */
internal class ComposePreviewViewImpl(private val project: Project,
                                      private val psiFilePointer: SmartPsiElementPointer<PsiFile>,
                                      private val projectBuildStatusManager: ProjectBuildStatusManager,
                                      navigationHandler: PreviewNavigationHandler,
                                      dataProvider: DataProvider,
                                      parentDisposable: Disposable,
                                      onPinFileAction: AnAction?,
                                      onUnPinAction: AnAction?) :
  WorkBench<DesignSurface>(project, "Compose Preview", null, parentDisposable), ComposePreviewView, Pannable {
  private val log = Logger.getInstance(ComposePreviewViewImpl::class.java)

  private val sceneComponentProvider = ComposeSceneComponentProvider()
  private val delegateInteractionHandler = DelegateInteractionHandler()

  override val pinnedSurface by lazy {
    createPreviewDesignSurface(
      project, navigationHandler, delegateInteractionHandler, dataProvider, parentDisposable, DesignSurface.ZoomControlsPolicy.HIDDEN,
      GridSurfaceLayoutManager(NlConstants.DEFAULT_SCREEN_OFFSET_X, NlConstants.DEFAULT_SCREEN_OFFSET_Y, NlConstants.SCREEN_DELTA,
                               NlConstants.SCREEN_DELTA)) { surface, model ->
      LayoutlibSceneManager(model, surface, sceneComponentProvider, ComposeSceneUpdateListener(), { RealTimeSessionClock() })
    }
  }

  override val mainSurface = createPreviewDesignSurface(
    project, navigationHandler, delegateInteractionHandler, { key ->
    if (PANNABLE_KEY.`is`(key)) {
      this@ComposePreviewViewImpl
    } else dataProvider.getData(key)
  }, parentDisposable,
    DesignSurface.ZoomControlsPolicy.VISIBLE) { surface, model ->
    LayoutlibSceneManager(model, surface, sceneComponentProvider, ComposeSceneUpdateListener(), { RealTimeSessionClock() })
  }.also {
    it.addPanZoomListener(object: PanZoomListener {
      override fun zoomChanged(previousScale: Double, newScale: Double) {
        pinnedSurface.setScale(newScale)
      }

      override fun panningChanged(adjustmentEvent: AdjustmentEvent?) {
      }
    })
  }

  override val isPannable: Boolean
    get() = mainSurface.isPannable
  override var isPanning: Boolean
    get() = mainSurface.isPanning
    set(value) {
      pinnedSurface.isPanning = true
      mainSurface.isPanning = value
    }

  override val component: JComponent = this

  private val pinnedPanelLabel = PinnedLabelPanel(onUnPinAction)
  private val mainSurfacePinLabel = PinnedLabelPanel(onPinFileAction)

  /**
   * Vertical splitter where the top part is a surface containing the pinned elements and the bottom the main design surface.
   */
  private val surfaceSplitter = JBSplitter(true, 0.25f, 0f, 0.5f).apply {
    dividerWidth = SURFACE_SPLITTER_DIVIDER_WIDTH_PX
  }

  /**
   * Vertical splitter where the top component is the [surfaceSplitter] and the bottom component, when visible, is an auxiliary
   * panel associated with the preview. For example, it can be an animation inspector that lists all the animations the preview has.
   */
  private val mainPanelSplitter = JBSplitter(true, 0.7f).apply {
    dividerWidth = ISSUE_SPLITTER_DIVIDER_WIDTH_PX
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

  /**
   * Panel that contains the splitters with the surfaces.
   */
  private val surfacesPanel = JPanel(BorderLayout()).apply {
    // surfaceSplitter.firstComponent will contain the pinned surface elements
    surfaceSplitter.secondComponent = mainSurface
    mainPanelSplitter.firstComponent = surfaceSplitter
    add(mainPanelSplitter, BorderLayout.CENTER)
  }

  private val scrollPane = DesignSurfaceScrollPane.createDefaultScrollPane(surfacesPanel, mainSurface.background) {
  }

  override var scrollPosition: Point
    get() = scrollPane.viewport.viewPosition
    set(value) { scrollPane.viewport.viewPosition = value }

  /**
   * True if the pinned surface is visible in the preview.
   */
  private var isPinnedSurfaceVisible = false

  private val staticPreviewInteractionHandler = NlInteractionHandler(mainSurface)
  private val interactiveInteractionHandler by lazy { LayoutlibInteractionHandler(mainSurface) }

  init {
    // Start handling events for the static preview.
    delegateInteractionHandler.delegate = staticPreviewInteractionHandler

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
      val actionsToolbar = ActionsToolbar(parentDisposable, mainSurface)
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

    val issueErrorSplitter = IssuePanelSplitter(mainSurface, contentPanel)

    init(issueErrorSplitter, mainSurface, listOf(), false)
    showLoading(message("panel.building"))
  }

  override fun updateProgress(message: String) = UIUtil.invokeLaterIfNeeded {
    if (isMessageVisible) {
      showLoading(message)
      hideContent()
    }
  }

  override fun setPinnedSurfaceVisibility(visible: Boolean) = UIUtil.invokeLaterIfNeeded {
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

  override fun showModalErrorMessage(message: String) = UIUtil.invokeLaterIfNeeded {
    log.debug("showModelErrorMessage: $message")
    loadingStopped(message)
  }

  override fun updateNotifications(parentEditor: FileEditor) = UIUtil.invokeLaterIfNeeded {
    if (Disposer.isDisposed(this) || project.isDisposed || !parentEditor.isValid) return@invokeLaterIfNeeded

    notificationPanel.updateNotifications(psiFilePointer.virtualFile, parentEditor, project)
  }

  /**
   * Method called to ask all notifications to update.
   */
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
  override fun updateVisibilityAndNotifications() = UIUtil.invokeLaterIfNeeded {
    if (isMessageVisible && projectBuildStatusManager.status == NeedsBuild) {
      log.debug("Needs successful build")
      showModalErrorMessage(message("panel.needs.build"))
    }
    else {
      if (hasRendered) {
        log.debug("Show content")
        hideLoading()
        if (hasContent) {
          showContent()
        }
        else {
          hideContent()
          loadingStopped(message("panel.no.previews.defined"),
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
      pinToolbarContainer.isVisible = !isInteractive && !isPinnedSurfaceVisible && !isAnimationPreview
    }
    else {
      pinToolbarContainer.isVisible = false
    }

    updateNotifications()
  }

  override var hasComponentsOverlay: Boolean
    get() = sceneComponentProvider.enabled
    set(value) {
      sceneComponentProvider.enabled = value
    }

  override var isInteractive: Boolean = false
    set(value) {
      field = value
      if (value) {
        delegateInteractionHandler.delegate = interactiveInteractionHandler
      }
      else {
        delegateInteractionHandler.delegate = staticPreviewInteractionHandler
      }
      updateVisibilityAndNotifications()
    }

  override var isAnimationPreview: Boolean = false

  override var hasContent: Boolean = true

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
}