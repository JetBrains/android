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
package com.android.tools.idea.common.surface

import com.android.annotations.concurrency.GuardedBy
import com.android.sdklib.AndroidDpCoordinate
import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.configurations.Configuration
import com.android.tools.idea.common.model.SelectionModel
import com.android.tools.idea.common.model.scaleBy
import com.android.tools.idea.common.scene.Scene
import com.android.tools.idea.common.scene.SceneContext
import com.android.tools.idea.common.scene.SceneManager
import com.android.tools.idea.common.scene.draw.ColorSet
import com.google.common.collect.ImmutableList
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.JBInsets
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.Shape
import java.util.function.Consumer

/** View of a [Scene] used in a [DesignSurface]. */
abstract class SceneView(
  open val surface: DesignSurface<*>,
  open val sceneManager: SceneManager,
  private val myShapePolicy: ShapePolicy,
) : Disposable {

  private val layersCacheLock = Any()

  @GuardedBy("myLayersCacheLock") private var layersCache: ImmutableList<Layer>? = null

  @SwingCoordinate
  var x: Int = 0
    private set

  @SwingCoordinate
  var y: Int = 0
    private set

  var isVisible: Boolean = true

  /** A [SceneContext] which offers the rendering and/or picking information for this [SceneView] */
  val context: SceneContext = SceneViewTransform(this)

  init {
    Disposer.register(sceneManager, this)
  }

  protected abstract fun createLayers(): ImmutableList<Layer>

  val scene: Scene
    get() = sceneManager.scene

  /**
   * If Layers are not exist, they will be created by [.createLayers]. This should happen only once.
   */
  private fun getLayers(): ImmutableList<Layer> {
    if (Disposer.isDisposed(surface)) {
      // Do not try to re-create the layers for a disposed surface
      return ImmutableList.of()
    }
    synchronized(layersCacheLock) {
      if (layersCache == null) {
        layersCache = createLayers()
        layersCache!!.forEach(Consumer { layer: Layer? -> Disposer.register(this, layer!!) })
      }
      return layersCache!!
    }
  }

  /**
   * Returns the current size of the view content, excluding margins. This is the same as
   * [.getContentSize] but accounts for the current zoom level
   *
   * @param dimension optional existing [Dimension] instance to be reused. If not null, the values
   *   will be set and this instance returned.
   */
  @SwingCoordinate
  fun getScaledContentSize(dimension: Dimension?): Dimension {
    val contentSize = getContentSize(dimension ?: Dimension())
    return contentSize.scaleBy(scale)
  }

  /**
   * Returns the current size of the view content, excluding margins. This is the same as
   * [.getContentSize] but accounts for the current zoom level
   */
  @get:SwingCoordinate
  val scaledContentSize: Dimension
    get() = getScaledContentSize(null)

  /** Returns the margin requested by this [SceneView] */
  val margin: Insets
    get() = JBInsets.emptyInsets()

  @AndroidDpCoordinate abstract fun getContentSize(dimension: Dimension?): Dimension

  val configuration: Configuration
    get() = sceneManager.model.configuration

  val selectionModel: SelectionModel
    get() = surface.selectionModel

  /**
   * Returns null if the screen is rectangular; if not, it returns a shape (round for AndroidWear
   * etc)
   */
  val screenShape: Shape?
    get() = myShapePolicy.getShape(this)

  open val scale: Double
    get() = surface.zoomController.scale

  val sceneScalingFactor: Float
    get() = sceneManager.sceneScalingFactor

  fun setLocation(@SwingCoordinate screenX: Int, @SwingCoordinate screenY: Int) {
    x = screenX
    y = screenY
  }

  /**
   * Custom translation to apply when converting between android coordinate space and swing
   * coordinate space.
   */
  @get:SwingCoordinate
  open val contentTranslationX: Int
    get() = 0

  /**
   * Custom translation to apply when converting between android coordinate space and swing
   * coordinate space.
   */
  @get:SwingCoordinate
  open val contentTranslationY: Int
    get() = 0

  abstract val colorSet: ColorSet

  /** Returns true if this [SceneView] can be dynamically resized. */
  open val isResizeable: Boolean
    get() = false

  /** Called when [DesignSurface.updateUI] is called. */
  fun updateUI() {}

  /** Called by the surface when the [SceneView] needs to be painted */
  fun paint(graphics: Graphics2D) {
    if (!isVisible) {
      return
    }
    getLayers().forEach { layer ->
      if (layer.isVisible) {
        layer.paint(graphics)
      }
    }
  }

  /** Called when a drag operation starts on the [DesignSurface] */
  fun onDragStart() {
    getLayers().filterIsInstance<SceneLayer>().forEach { layer ->
      if (layer.isShowOnHover) {
        layer.isShowOnHover = true
      }
    }
  }

  /** Called when a drag operation ends on the [DesignSurface] */
  fun onDragEnd() {
    getLayers().filterIsInstance<SceneLayer>().forEach { layer ->
      if (layer.isShowOnHover) {
        layer.isShowOnHover = false
      }
    }
  }

  /**
   * Returns whether this [SceneView] knows the content size. Some [SceneView] might not know its
   * content size while it's rendering or if the rendering is failure.
   */
  open fun hasContentSize(): Boolean {
    return isVisible
  }

  override fun dispose() {}

  /**
   * Called by the [DesignSurface] on mouse hover. The coordinates might be outside of the
   * boundaries of this [SceneView]
   */
  fun onHover(@SwingCoordinate mouseX: Int, @SwingCoordinate mouseY: Int) {
    getLayers().forEach { it.onHover(mouseX, mouseY) }
  }

  /**
   * Set the ConstraintsLayer and SceneLayer layers to paint, even if they are set to paint only on
   * mouse hover
   *
   * @param value if true, force painting
   */
  fun setForceLayersRepaint(value: Boolean) {
    getLayers().filterIsInstance<SceneLayer>().forEach { it.setTemporaryShow(value) }
  }
}
