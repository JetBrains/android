/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.surface

import com.android.annotations.concurrency.UiThread
import com.android.sdklib.AndroidDpCoordinate
import com.android.tools.adtui.ZoomController
import com.android.tools.adtui.actions.ZoomType
import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.actions.LAYOUT_PREVIEW_HANDLER_KEY
import com.android.tools.idea.actions.LayoutPreviewHandler
import com.android.tools.idea.common.analytics.DesignerAnalyticsManager
import com.android.tools.idea.common.diagnostics.NlDiagnosticKey
import com.android.tools.idea.common.editor.ActionManager
import com.android.tools.idea.common.layout.LayoutManagerSwitcher
import com.android.tools.idea.common.layout.SceneViewAlignment
import com.android.tools.idea.common.layout.SurfaceLayoutOption
import com.android.tools.idea.common.layout.scroller.DesignSurfaceViewportScroller
import com.android.tools.idea.common.layout.scroller.ReferencePointScroller
import com.android.tools.idea.common.layout.scroller.TopLeftCornerScroller
import com.android.tools.idea.common.layout.scroller.ZoomCenterScroller
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.model.DnDTransferComponent
import com.android.tools.idea.common.model.DnDTransferItem
import com.android.tools.idea.common.model.ItemTransferable
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.model.SelectionModel
import com.android.tools.idea.common.scene.SceneManager
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.DesignSurfaceActionHandler
import com.android.tools.idea.common.surface.Interactable
import com.android.tools.idea.common.surface.InteractionHandler
import com.android.tools.idea.common.surface.LayoutScannerControl
import com.android.tools.idea.common.surface.ScaleChange
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.common.surface.SceneViewPanel
import com.android.tools.idea.common.surface.SurfaceScale
import com.android.tools.idea.common.surface.ZoomControlsPolicy
import com.android.tools.idea.common.surface.getFitContentIntoWindowScale
import com.android.tools.idea.common.surface.layout.DesignSurfaceViewport
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.analytics.NlAnalyticsManager
import com.android.tools.idea.uibuilder.graphics.NlConstants
import com.android.tools.idea.uibuilder.layout.option.GridLayoutManager
import com.android.tools.idea.uibuilder.layout.option.GridSurfaceLayoutManager
import com.android.tools.idea.uibuilder.layout.option.GroupedListSurfaceLayoutManager
import com.android.tools.idea.uibuilder.layout.option.ListLayoutManager
import com.android.tools.idea.uibuilder.model.getViewHandler
import com.android.tools.idea.uibuilder.model.h
import com.android.tools.idea.uibuilder.model.w
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlScreenViewProvider.Companion.loadPreferredMode
import com.android.tools.idea.uibuilder.surface.NlScreenViewProvider.Companion.savePreferredMode
import com.android.tools.idea.uibuilder.surface.layout.GroupedGridSurfaceLayoutManager
import com.android.tools.idea.uibuilder.visual.colorblindmode.ColorBlindMode
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintIssueProvider
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Iterables
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.scale.JBUIScale.sysScale
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import java.util.function.Supplier
import java.util.stream.Collectors
import javax.swing.JLayeredPane
import javax.swing.JPanel
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch

/**
 * The [DesignSurface] for the layout editor, which contains the full background, rulers, one or
 * more device renderings, etc
 *
 * @param sceneManagerProvider Allows customizing the generation of [SceneManager]s
 * @param delegateDataProvider See [NlSurfaceBuilder.setDelegateDataProvider]
 */
class NlDesignSurface
internal constructor(
  project: Project,
  private val sceneManagerProvider: (NlDesignSurface, NlModel) -> LayoutlibSceneManager,
  actionManagerProvider:
    (DesignSurface<LayoutlibSceneManager>) -> ActionManager<
        out DesignSurface<LayoutlibSceneManager>
      >,
  interactableProvider: (DesignSurface<LayoutlibSceneManager>) -> Interactable,
  interactionHandlerProvider: (DesignSurface<LayoutlibSceneManager>) -> InteractionHandler,
  actionHandlerProvider: (DesignSurface<LayoutlibSceneManager>) -> DesignSurfaceActionHandler,
  private val delegateDataProvider: DataProvider?,
  selectionModel: SelectionModel,
  zoomControlsPolicy: ZoomControlsPolicy,
  private val supportedActionsProvider: Supplier<ImmutableSet<NlSupportedActions>>,
  private val shouldRenderErrorsPanel: Boolean,
  shouldZoomOnFirstComponentResize: Boolean,
  issueProviderFactory: (DesignSurface<LayoutlibSceneManager>) -> VisualLintIssueProvider,
  nlDesignSurfacePositionableContentLayoutManager: NlDesignSurfacePositionableContentLayoutManager,
) :
  DesignSurface<LayoutlibSceneManager>(
    project,
    actionManagerProvider,
    interactableProvider,
    interactionHandlerProvider,
    nlDesignSurfacePositionableContentLayoutManager,
    actionHandlerProvider,
    selectionModel,
    zoomControlsPolicy,
    shouldZoomOnFirstComponentResize,
  ),
  NlDiagnosticKey {

  /**
   * [EditorNotificationPanel] to indicate List mode is deprecated. It should only be visible if
   * List is selected.
   *
   * TODO(b/369564706): remove this banner when List mode is removed
   */
  private val listDeprecationBanner =
    object : EditorNotificationPanel(Status.Warning) {
      init {
        text = "List mode will be deprecated in the next release. Please use Grid mode if possible."
        isVisible = false
      }
    }

  init {
    val deprecationBannerPanel =
      JPanel(BorderLayout()).apply {
        add(listDeprecationBanner, BorderLayout.NORTH)
        isOpaque = false
      }
    layeredPane.add(deprecationBannerPanel, JLayeredPane.DEFAULT_LAYER)
    viewport.addChangeListener {
      val scroller = viewportScroller
      viewportScroller = null
      scroller?.scroll(viewport)
    }
  }

  var screenViewProvider: ScreenViewProvider = loadPreferredMode()
    private set(value) {
      if (field != value) {
        field.onViewProviderReplaced()
        field = value

        for (manager in sceneManagers) {
          manager.updateSceneViews()
          manager.requestRenderAsync()
        }
        revalidateScrollArea()
      }
    }

  /** Returns whether this surface is currently in resize mode or not. See [setResizeMode] */
  var isCanvasResizing: Boolean = false

  var isRenderingSynchronously: Boolean = false

  var isAnimationScrubbing: Boolean = false

  /** The rotation degree of the surface to simulate the phone rotation. */
  var rotateSurfaceDegree: Float = Float.NaN

  private val sceneViewLayoutManager: NlDesignSurfacePositionableContentLayoutManager
    get() = sceneViewPanel.layout as NlDesignSurfacePositionableContentLayoutManager

  val layoutPreviewHandler =
    object : LayoutPreviewHandler {
      override var previewWithToolsVisibilityAndPosition = true
        set(value) {
          if (field != value) {
            field = value
            forceRefresh()
          }
        }
    }

  private val layoutScannerControl: LayoutScannerControl = NlLayoutScanner(this)

  override val analyticsManager: DesignerAnalyticsManager = NlAnalyticsManager(this)

  override val zoomController: ZoomController =
    NlDesignSurfaceZoomController(
        {
          sceneViewLayoutManager.getFitIntoScale(
            sceneViewPanel.positionableContent,
            viewport.extentSize,
          )
        },
        analyticsManager,
        selectionModel,
        this,
      )
      .apply {
        scope.launch {
          beforeZoomChange.collect { zoomType ->
            if (zoomType == ZoomType.FIT) {
              sceneViewLayoutManager.clearCachedGroups()
            }
          }
        }
        // TODO(b/330155137): Move setOnScaleListener to Kotlin flow
        setOnScaleListener(this@NlDesignSurface)
        screenScalingFactor = sysScale(this@NlDesignSurface).toDouble()
      }

  val visualLintIssueProvider = issueProviderFactory(this)

  private val errorQueue = ErrorQueue(this, project)

  override val accessoryPanel: AccessoryPanel =
    AccessoryPanel(AccessoryPanel.Type.SOUTH_PANEL, true).apply { setSurface(this@NlDesignSurface) }

  /** To scroll to correct viewport position when its size is changed. */
  private var viewportScroller: DesignSurfaceViewportScroller? = null

  @UiThread
  fun onLayoutUpdated(layoutOption: SurfaceLayoutOption) {
    setSceneViewAlignment(layoutOption.sceneViewAlignment)
    setScrollPosition(0, 0)
    revalidateScrollArea()
  }

  @UiThread
  fun updateLayoutDeprecationBannerVisibility(visible: Boolean) {
    listDeprecationBanner.isVisible = visible
  }

  /** Triggers a re-inflation and re-render, but it doesn't wait for it to finish. */
  override fun forceRefresh() {
    scope.launch {
      sceneManagers.forEach {
        it.sceneRenderConfiguration.needsInflation.set(true)
        it.requestRenderAsync()
      }
    }
  }

  override fun createSceneManager(model: NlModel): LayoutlibSceneManager {
    val manager = sceneManagerProvider(this, model)
    return manager
  }

  /**
   * Tells this surface to resize mode. While on resizing mode, the views won't be auto positioned.
   * This can be disabled to avoid moving the screens around when the user is resizing the canvas.
   * See [CanvasResizeInteraction]
   *
   * @param isResizing true to enable the resize mode
   */
  fun setResizeMode(isResizing: Boolean) {
    isCanvasResizing = isResizing
    // When in resize mode, allow the scrollable surface autoscroll so it follow the mouse.
    setSurfaceAutoscrolls(isResizing)
  }

  /**
   * When true, the surface will autoscroll when the mouse gets near the edges. See
   * [JScrollPane.setAutoscrolls]
   */
  private fun setSurfaceAutoscrolls(enabled: Boolean) {
    scrollPane?.autoscrolls = enabled
  }

  fun setScreenViewProvider(newScreenViewProvider: ScreenViewProvider, setAsDefault: Boolean) {
    (newScreenViewProvider as? NlScreenViewProvider)?.let {
      if (setAsDefault) savePreferredMode(it)
    }
    screenViewProvider = newScreenViewProvider
  }

  /**
   * Update the color-blind mode in the [ScreenViewProvider] for this surface and make sure to
   * update all the SceneViews in this surface to reflect the change.
   */
  fun setColorBlindMode(mode: ColorBlindMode) {
    screenViewProvider.colorBlindFilter = mode
    for (manager in sceneManagers) {
      manager.updateSceneViews()
      manager.requestRenderAsync()
    }
    revalidateScrollArea()
  }

  override fun shouldRenderErrorsPanel(): Boolean {
    return shouldRenderErrorsPanel
  }

  /**
   * Set the ConstraintsLayer and SceneLayer layers to paint, even if they are set to paint only on
   * mouse hover
   *
   * @param value if true, force painting
   */
  fun forceLayersPaint(value: Boolean) {
    for (view in sceneViews) {
      view.setForceLayersRepaint(value)
    }
    repaint()
  }

  /**
   * The offsets to the left and top edges when scrolling to a component by calling
   * [scrollToVisible]
   */
  @get:SwingCoordinate
  override val scrollToVisibleOffset =
    Dimension(2 * NlConstants.DEFAULT_SCREEN_OFFSET_X, 2 * NlConstants.DEFAULT_SCREEN_OFFSET_Y)

  override fun setModel(newModel: NlModel?) {
    accessoryPanel.setModel(model)
    super.setModel(newModel)
  }

  override fun dispose() {
    accessoryPanel.setSurface(null)
    super.dispose()
  }

  override fun notifyComponentActivate(component: NlComponent, x: Int, y: Int) {
    val handler = component.getViewHandler {}

    handler?.onActivateInDesignSurface(component, x, y)
    super.notifyComponentActivate(component, x, y)
  }

  /**
   * Notifies the design surface that the given screen view (which must be showing in this design
   * surface) has been rendered (possibly with errors)
   */
  fun updateErrorDisplay() {
    if (isRenderingSynchronously) {
      // No errors update while we are in the middle of playing an animation
      return
    }

    errorQueue.updateErrorDisplay(
      layoutScannerControl,
      visualLintIssueProvider,
      this,
      issueModel,
      this::sceneManagers,
    )
  }

  /**
   * Notifies the design surface that a model being shown in this surface has been rendered (or
   * re-rendered).
   */
  fun modelRendered() {
    updateErrorDisplay()

    // modelRendered might be called in the Layoutlib Render thread and revalidateScrollArea needs
    // to be called on the UI thread.
    UIUtil.invokeLaterIfNeeded { this.revalidateScrollArea() }
  }

  override fun deactivate() {
    errorQueue.deactivate(issueModel)
    visualLintIssueProvider.clear()
    super.deactivate()
  }

  override fun activate() {
    super.activate()
    updateErrorDisplay()
  }

  override fun useSmallProgressIcon(): Boolean {
    if (focusedSceneView == null) {
      return false
    }

    return sceneManagers.any { it.renderResult != null }
  }

  /**
   * Triggers a re-inflation and re-render. This method doesn't wait for the refresh to finish, but
   * it sets up a progress indicator to inform the user about the refresh progress.
   */
  override fun forceUserRequestedRefresh() {
    // When the user initiates the refresh, give some feedback via progress indicator.
    val refreshProgressIndicator =
      BackgroundableProcessIndicator(project, "Refreshing...", "", "", false)
    scope
      .launch {
        sceneManagers.forEach {
          it.sceneRenderConfiguration.needsInflation.set(true)
          it.requestRenderAsync().await()
        }
      }
      .invokeOnCompletion { refreshProgressIndicator.processFinish() }
  }

  fun findSceneViewRectangles(): Map<SceneView, Rectangle?> {
    return sceneViewPanel.findSceneViewRectangles()
  }

  override val layoutManagerSwitcher: LayoutManagerSwitcher?
    get() = sceneViewPanel.layout as? LayoutManagerSwitcher

  override fun scrollToCenter(list: List<NlComponent>) {
    val view = focusedSceneView ?: return
    if (list.isEmpty()) {
      return
    }
    val scene = view.scene
    @AndroidDpCoordinate val componentsArea = Rectangle(0, 0, -1, -1)
    @AndroidDpCoordinate val componentRect = Rectangle()
    list
      .filter { !it.isRoot }
      .forEach {
        val component = scene.getSceneComponent(it) ?: return@forEach
        component.fillRect(componentRect)
        if (componentsArea.width < 0) {
          componentsArea.bounds = componentRect
        } else {
          componentsArea.add(componentRect)
        }
      }

    @SwingCoordinate val areaToCenter = Coordinates.getSwingRectDip(view, componentsArea)
    if (areaToCenter.isEmpty || layeredPane.visibleRect.contains(areaToCenter)) {
      // No need to scroll to components if they are all fully visible on the surface.
      return
    }

    @SwingCoordinate val swingViewportSize = extentSize
    @SwingCoordinate val targetSwingX = areaToCenter.centerX.toInt()
    @SwingCoordinate val targetSwingY = areaToCenter.centerY.toInt()
    // Center to position.
    setScrollPosition(
      targetSwingX - swingViewportSize.width / 2,
      targetSwingY - swingViewportSize.height / 2,
    )
    @SurfaceScale val fitScale = this.getFitContentIntoWindowScale(areaToCenter.size)

    if (zoomController.scale > fitScale) {
      // Scale down to fit selection.
      zoomController.setScale(fitScale, targetSwingX, targetSwingY)
    }
  }

  /**
   * Creates a [ReferencePointScroller] that scrolls a given focus point of [NlDesignSurface]. The
   * focus point could be either the coordinates of a focused scene view or the position of the
   * mouse.
   *
   * @param port The view port were to apply the [ReferencePointScroller]
   * @param newScrollPosition The scroll position of the next scrolling action.
   * @param update The [ScaleChange] applied to this [NlDesignSurface].
   * @param oldScrollPosition the previous scroll position
   * @return A [ReferencePointScroller] to apply to this [NlDesignSurface].
   */
  private fun createScrollerForGroupedSurfaces(
    port: DesignSurfaceViewport,
    update: ScaleChange,
    oldScrollPosition: Point,
    newScrollPosition: Point,
  ): DesignSurfaceViewportScroller {
    val focusPoint = focusedSceneView?.let { Point(it.x, it.y) } ?: update.focusPoint
    return if (focusPoint.x < 0 || focusPoint.y < 0) {
      // zoom with top-left of the visible area as anchor
      TopLeftCornerScroller(
        Dimension(port.viewSize),
        newScrollPosition,
        update.previousScale,
        update.newScale,
      )
    } else {
      // zoom with mouse position as anchor, and considering its relative position to the existing
      // scene views
      ReferencePointScroller(
        Dimension(port.viewSize),
        Point(oldScrollPosition),
        focusPoint,
        update.previousScale,
        update.newScale,
        findSceneViewRectangles(),
      ) {
        sceneViewPanel.findMeasuredSceneViewRectangle(it, extentSize)
      }
    }
  }

  /**
   * Zoom (in or out) and move the scroll position to ensure that the given rectangle is fully
   * visible and centered. When zooming, the sceneViews may move around, and so the rectangle's
   * coordinates should be relative to the sceneView. The given rectangle should be a subsection of
   * the given sceneView.
   *
   * @param sceneView the [SceneView] that contains the given rectangle
   * @param rectangle the rectangle that should be visible, with its coordinates relative to the
   *   sceneView, and with its currentsize (before zooming).
   */
  fun zoomAndCenter(sceneView: SceneView, @SwingCoordinate rectangle: Rectangle) {
    if (scrollPane == null) {
      Logger.getInstance(NlDesignSurface::class.java)
        .warn("The scroll pane is null, cannot zoom and center.")
      return
    }

    // Calculate the scaleChangeNeeded so that after zooming,
    // the given rectangle with a given offset fits tight in the scroll panel.
    val offset = scrollToVisibleOffset
    val availableSize = extentSize
    val curSize = Dimension(rectangle.width, rectangle.height)

    // Make sure both dimensions fit, and at least one of them is as tight
    // as possible (respecting the offset).
    var scaleChangeNeeded =
      min(
        (availableSize.getWidth() - 2 * offset.width) / curSize.getWidth(),
        (availableSize.getHeight() - 2 * offset.height) / curSize.getHeight(),
      )

    // Adjust the scale change to keep the new scale between the lower and upper bounds.
    val curScale: Double = zoomController.scale
    val boundedNewScale: Double = zoomController.getBoundedScale(curScale * scaleChangeNeeded)
    scaleChangeNeeded = boundedNewScale / curScale

    // The rectangle size and its coordinates relative to the sceneView have
    // changed due to the scale change.
    rectangle.setRect(
      rectangle.x * scaleChangeNeeded,
      rectangle.y * scaleChangeNeeded,
      rectangle.width * scaleChangeNeeded,
      rectangle.height * scaleChangeNeeded,
    )

    if (zoomController.setScale(boundedNewScale)) {
      viewportScroller = DesignSurfaceViewportScroller { scrollToCenter(sceneView, rectangle) }
    } else {
      // If scale hasn't changed, then just scroll to center
      scrollToCenter(sceneView, rectangle)
    }
  }

  override fun onScaleChange(update: ScaleChange) {
    super.onScaleChange(update)

    val port = viewport
    val scrollPosition = pannable.scrollPosition
    var focusPoint = update.focusPoint

    val layoutManager = sceneViewLayoutManager.currentLayout.value.layoutManager

    // If layout is a vertical list layout
    val isGroupedListLayout =
      layoutManager is GroupedListSurfaceLayoutManager || layoutManager is ListLayoutManager
    // If layout is grouped grid layout.
    val isGroupedGridLayout =
      layoutManager is GroupedGridSurfaceLayoutManager || layoutManager is GridLayoutManager

    if (isGroupedListLayout) {
      viewportScroller =
        createScrollerForGroupedSurfaces(
          port,
          update,
          scrollPosition,
          Point(scrollPosition.x, max(0.0, focusPoint.y.toDouble()).toInt()),
        )
    } else if (isGroupedGridLayout && StudioFlags.SCROLLABLE_ZOOM_ON_GRID.get()) {
      viewportScroller =
        createScrollerForGroupedSurfaces(port, update, scrollPosition, scrollPosition)
    } else if (layoutManager !is GridSurfaceLayoutManager) {
      if (focusPoint.x < 0 || focusPoint.y < 0) {
        focusPoint = Point(port.viewportComponent.width / 2, port.viewportComponent.height / 2)
      }
      val zoomCenterInView = Point(scrollPosition.x + focusPoint.x, scrollPosition.y + focusPoint.y)

      viewportScroller =
        ZoomCenterScroller(Dimension(port.viewSize), Point(scrollPosition), zoomCenterInView)
    }
  }

  /**
   * When the surface is in "Animation Mode", the error display is not updated. This allows for the
   * surface to render results faster without triggering updates of the issue panel per frame.
   */
  fun setRenderSynchronously(enabled: Boolean) {
    isRenderingSynchronously = enabled

    // If animation is enabled, scanner must be paused.
    if (enabled) layoutScannerControl.pause() else layoutScannerControl.resume()
  }

  /** Return whenever surface is rotating. */
  val isRotating: Boolean
    get() = !java.lang.Float.isNaN(rotateSurfaceDegree)

  val supportedActions: ImmutableSet<NlSupportedActions>
    get() = supportedActionsProvider.get()

  /**
   * Sets the min size allowed for the scrollable surface. This is useful in cases where we have an
   * interaction that needs to extend the available space.
   */
  var scrollableViewMinSize: Dimension = Dimension()
    set(value) {
      field.size = value
    }

  override val selectableComponents: List<NlComponent>
    get() {
      val root = models.flatMap { it.treeReader.components }.firstOrNull() ?: return emptyList()
      return root.flatten().collect(Collectors.toList())
    }

  override val selectionAsTransferable: ItemTransferable
    get() {
      val components =
        selectionModel.selection
          .stream()
          .filter { it.tag != null }
          .map { DnDTransferComponent(it.tagName, it.tag!!.text, it.w, it.h) }
          .collect(ImmutableList.toImmutableList())

      val selectedModels =
        selectionModel.selection.stream().map { it.model }.collect(ImmutableSet.toImmutableSet())

      if (selectedModels.size != 1) {
        Logger.getInstance(NlDesignSurface::class.java)
          .warn("Elements from multiple models were selected.")
      }

      val selectedModel = Iterables.getFirst(selectedModels, null)
      return ItemTransferable(DnDTransferItem(selectedModel?.treeWriter?.id ?: 0, components))
    }

  /**
   * Sets the [SceneViewAlignment] for the [SceneView]s. This only applies to [SceneView]s when the
   * content size is less than the minimum size allowed. See [SceneViewPanel].
   */
  fun setSceneViewAlignment(sceneViewAlignment: SceneViewAlignment) {
    sceneViewPanel.sceneViewAlignment = sceneViewAlignment.alignmentX
  }

  override fun uiDataSnapshot(sink: DataSink) {
    super.uiDataSnapshot(sink)
    sink[LAYOUT_PREVIEW_HANDLER_KEY] = layoutPreviewHandler
    DataSink.uiDataSnapshot(sink, delegateDataProvider)
  }
}
