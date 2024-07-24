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

import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.idea.actions.LayoutPreviewHandler
import com.android.tools.idea.common.diagnostics.NlDiagnosticKey
import com.android.tools.idea.common.editor.ActionManager
import com.android.tools.idea.common.layout.LayoutManagerSwitcher
import com.android.tools.idea.common.layout.SceneViewAlignment
import com.android.tools.idea.common.layout.SurfaceLayoutOption
import com.android.tools.idea.common.layout.scroller.DesignSurfaceViewportScroller
import com.android.tools.idea.common.layout.scroller.ReferencePointScroller
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.model.SelectionModel
import com.android.tools.idea.common.scene.SceneManager
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.DesignSurfaceActionHandler
import com.android.tools.idea.common.surface.Interactable
import com.android.tools.idea.common.surface.InteractionHandler
import com.android.tools.idea.common.surface.ScaleChange
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.common.surface.SceneViewPanel
import com.android.tools.idea.common.surface.SurfaceScale
import com.android.tools.idea.common.surface.ZoomControlsPolicy
import com.android.tools.idea.common.surface.layout.DesignSurfaceViewport
import com.android.tools.idea.uibuilder.graphics.NlConstants
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.scene.RenderListener
import com.android.tools.idea.uibuilder.surface.NlScreenViewProvider.Companion.loadPreferredMode
import com.android.tools.idea.uibuilder.visual.colorblindmode.ColorBlindMode
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintIssueProvider
import com.google.common.collect.ImmutableSet
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.project.Project
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier

/**
 * The [DesignSurface] for the layout editor, which contains the full background, rulers, one or
 * more device renderings, etc
 *
 * @param sceneManagerProvider Allows customizing the generation of [SceneManager]s
 * @param delegateDataProvider See [NlSurfaceBuilder.setDelegateDataProvider]
 */
abstract class NlSurface
internal constructor(
  project: Project,
  parentDisposable: Disposable,
  protected val sceneManagerProvider: (NlDesignSurface, NlModel) -> LayoutlibSceneManager,
  defaultLayoutOption: SurfaceLayoutOption,
  actionManagerProvider:
    (DesignSurface<LayoutlibSceneManager>) -> ActionManager<
        out DesignSurface<LayoutlibSceneManager>
      >,
  interactableProvider: (DesignSurface<LayoutlibSceneManager>) -> Interactable,
  interactionHandlerProvider: (DesignSurface<LayoutlibSceneManager>) -> InteractionHandler,
  @SurfaceScale minScale: Double,
  @SurfaceScale maxScale: Double,
  actionHandlerProvider: (DesignSurface<LayoutlibSceneManager>) -> DesignSurfaceActionHandler,
  protected val delegateDataProvider: DataProvider?,
  selectionModel: SelectionModel,
  zoomControlsPolicy: ZoomControlsPolicy?,
  protected val supportedActionsProvider: Supplier<ImmutableSet<NlSupportedActions>>,
  private val shouldRenderErrorsPanel: Boolean,
  maxZoomToFitLevel: Double,
  issueProviderFactory: (DesignSurface<LayoutlibSceneManager>) -> VisualLintIssueProvider,
) :
  DesignSurface<LayoutlibSceneManager>(
    project,
    parentDisposable,
    actionManagerProvider,
    interactableProvider,
    interactionHandlerProvider,
    { surface ->
      NlDesignSurfacePositionableContentLayoutManager(
        surface as NlDesignSurface,
        parentDisposable,
        defaultLayoutOption,
      )
    },
    actionHandlerProvider,
    selectionModel,
    zoomControlsPolicy!!,
  ),
  LayoutPreviewHandler,
  NlDiagnosticKey {

  var screenViewProvider: ScreenViewProvider = loadPreferredMode()

  /** Returns whether this surface is currently in resize mode or not. See [setResizeMode] */
  var isCanvasResizing: Boolean = false

  val renderListener = RenderListener { modelRendered() }

  var isRenderingSynchronously: Boolean = false

  var isInAnimationScrubbing: Boolean = false

  /** The rotation degree of the surface to simulate the phone rotation. */
  var rotateSurfaceDegree: Float = Float.NaN

  protected val sceneViewLayoutManager: NlDesignSurfacePositionableContentLayoutManager
    get() = sceneViewPanel.layout as NlDesignSurfacePositionableContentLayoutManager

  abstract fun onLayoutUpdated(layoutOption: SurfaceLayoutOption)

  /**
   * Tells this surface to resize mode. While on resizing mode, the views won't be auto positioned.
   * This can be disabled to avoid moving the screens around when the user is resizing the canvas.
   * See [CanvasResizeInteraction]
   *
   * @param isResizing true to enable the resize mode
   */
  abstract fun setResizeMode(isResizing: Boolean)

  /**
   * When true, the surface will autoscroll when the mouse gets near the edges. See
   * [JScrollPane.setAutoscrolls]
   */
  abstract fun setSurfaceAutoscrolls(enabled: Boolean)

  abstract fun setScreenViewProvider(screenViewProvider: ScreenViewProvider, setAsDefault: Boolean)

  /**
   * Update the color-blind mode in the [ScreenViewProvider] for this surface and make sure to
   * update all the SceneViews in this surface to reflect the change.
   */
  abstract fun setColorBlindMode(mode: ColorBlindMode)

  override fun shouldRenderErrorsPanel(): Boolean {
    return shouldRenderErrorsPanel
  }

  /**
   * Set the ConstraintsLayer and SceneLayer layers to paint, even if they are set to paint only on
   * mouse hover
   *
   * @param value if true, force painting
   */
  abstract fun forceLayersPaint(value: Boolean)

  /**
   * The offsets to the left and top edges when scrolling to a component by calling
   * [scrollToVisible]
   */
  @get:SwingCoordinate
  override val scrollToVisibleOffset: Dimension
    get() =
      Dimension(2 * NlConstants.DEFAULT_SCREEN_OFFSET_X, 2 * NlConstants.DEFAULT_SCREEN_OFFSET_Y)

  override fun setModel(newModel: NlModel?): CompletableFuture<Void> {
    return super.setModel(newModel)
  }

  override fun dispose() {
    super.dispose()
  }

  override fun notifyComponentActivate(component: NlComponent, x: Int, y: Int) {
    super.notifyComponentActivate(component, x, y)
  }

  /**
   * Notifies the design surface that the given screen view (which must be showing in this design
   * surface) has been rendered (possibly with errors)
   */
  abstract fun updateErrorDisplay()

  abstract fun modelRendered()

  override fun deactivate() {
    super.deactivate()
  }

  override fun activate() {
    super.activate()
  }

  abstract fun findSceneViewRectangles(): Map<SceneView, Rectangle?>

  override val layoutManagerSwitcher: LayoutManagerSwitcher?
    get() = sceneViewPanel.layout as? LayoutManagerSwitcher

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
  abstract fun createScrollerForGroupedSurfaces(
    port: DesignSurfaceViewport,
    update: ScaleChange,
    oldScrollPosition: Point,
    newScrollPosition: Point,
  ): DesignSurfaceViewportScroller

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
  abstract fun zoomAndCenter(sceneView: SceneView, @SwingCoordinate rectangle: Rectangle)

  /**
   * When the surface is in "Animation Mode", the error display is not updated. This allows for the
   * surface to render results faster without triggering updates of the issue panel per frame.
   */
  abstract fun setRenderSynchronously(enabled: Boolean)

  abstract fun setAnimationScrubbing(value: Boolean)

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

  /**
   * Sets the [SceneViewAlignment] for the [SceneView]s. This only applies to [SceneView]s when the
   * content size is less than the minimum size allowed. See [SceneViewPanel].
   */
  abstract fun setSceneViewAlignment(sceneViewAlignment: SceneViewAlignment)
}
