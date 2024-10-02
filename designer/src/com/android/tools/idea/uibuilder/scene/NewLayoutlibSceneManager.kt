/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.scene

import com.android.resources.Density
import com.android.tools.configurations.ConfigurationListener
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.model.SelectionListener
import com.android.tools.idea.common.scene.SceneComponentHierarchyProvider
import com.android.tools.idea.common.scene.SceneManager
import com.android.tools.idea.common.scene.decorator.SceneDecoratorFactory
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.LayoutScannerConfiguration
import com.android.tools.idea.res.ResourceNotificationManager
import com.android.tools.idea.uibuilder.analytics.NlAnalyticsManager
import com.android.tools.idea.uibuilder.scene.decorator.NlSceneDecoratorFactory
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintMode
import com.android.tools.rendering.RenderResult
import com.google.common.collect.ImmutableSet
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import org.jetbrains.annotations.TestOnly

private val DECORATOR_FACTORY: SceneDecoratorFactory = NlSceneDecoratorFactory()

/**
 * [SceneManager] that creates a Scene from an NlModel representing a layout using layoutlib.
 *
 * @param model the [NlModel] to be rendered by this [NewLayoutlibSceneManager].
 * @param designSurface the [DesignSurface] used to present the result of the renders.
 * @param renderTaskDisposerExecutor the [Executor] to be used for running the slow [dispose] calls.
 * @param sceneComponentProvider the [SceneComponentHierarchyProvider] providing the mapping from
 *   [NlComponent] to [SceneComponent]s.
 * @param layoutScannerConfig the [LayoutScannerConfiguration] for layout validation from
 *   Accessibility Testing Framework.
 */
abstract class NewLayoutlibSceneManager(
  model: NlModel,
  designSurface: DesignSurface<*>,
  renderTaskDisposerExecutor: Executor,
  sceneComponentProvider: SceneComponentHierarchyProvider,
  layoutScannerConfig: LayoutScannerConfiguration,
) : SceneManager(model, designSurface, sceneComponentProvider) {
  override val designSurface: NlDesignSurface
    get() = super.designSurface as NlDesignSurface

  override val sceneDecoratorFactory: SceneDecoratorFactory = DECORATOR_FACTORY

  /**
   * In the layout editor, Scene uses [AndroidDpCoordinate]s whereas rendering is done in (zoomed
   * and offset) [AndroidCoordinate]s. The scaling factor between them is the ratio of the screen
   * density to the standard density (160).
   */
  override val sceneScalingFactor: Float
    get() = model.configuration.density.dpiValue / Density.DEFAULT_DENSITY.toFloat()

  /** Helper class in charge of some render related responsibilities */
  // TODO(b/335424569): add a better explanation after moving more responsibilities to
  // LayoutlibSceneRenderer
  // TODO(b/369573219): make this field private and remove JvmField annotation
  @JvmField
  protected val layoutlibSceneRenderer: LayoutlibSceneRenderer =
    LayoutlibSceneRenderer(
      this,
      renderTaskDisposerExecutor,
      model,
      designSurface as NlDesignSurface,
      layoutScannerConfig,
    )

  /** The configuration to use when inflating and rendering. */
  val sceneRenderConfiguration: LayoutlibSceneRenderConfiguration
    get() = layoutlibSceneRenderer.sceneRenderConfiguration

  /** Returns if there are any pending render requests. */
  @get:TestOnly
  val isRendering: Boolean
    get() = layoutlibSceneRenderer.isRendering()

  /** The [RenderResult] of the latest render. */
  val renderResult: RenderResult?
    get() = layoutlibSceneRenderer.renderResult

  /** The quality used in the latest render. */
  val lastRenderQuality: Float
    get() = layoutlibSceneRenderer.lastRenderQuality

  /** Reset the last [RenderResult] to avoid using it as part of the cache in the next render. */
  fun invalidateCachedResponse() {
    layoutlibSceneRenderer.renderResult = null
  }

  /** The [VisualLintMode] currently set for the model associated with this scene manager. */
  var visualLintMode = VisualLintMode.DISABLED

  /** If true, listen to resource changes by processing calls to [resourcesChanged]. */
  var listenResourceChange = true

  /**
   * If true, automatically update (if needed) and re-render when being activated. Which happens
   * after [activate] is called. Note that if it is activated already, then it will not re-render.
   */
  // TODO(b/369573219): remove JvmField annotation
  @JvmField var updateAndRenderWhenActivated = true

  override fun resourcesChanged(reasons: ImmutableSet<ResourceNotificationManager.Reason>) {
    if (listenResourceChange) {
      super.resourcesChanged(reasons)
    }
  }

  // TODO(b/369573219): make this field private and remove JvmField annotation
  @JvmField
  protected val selectionChangeListener = SelectionListener { _, _ ->
    updateTargets()
    scene.needsRebuildList()
  }

  /** Variables to track previous values of the configuration bar for tracking purposes. */
  private val configurationUpdatedFlags = AtomicInteger(0)
  private var currentDpi = 0
    set(value) {
      if (field != value) {
        field = value
        // Update from the model to update the dpi
        this@NewLayoutlibSceneManager.update()
      }
    }

  // TODO(b/369573219): make this field private and remove JvmField annotation
  @JvmField
  protected val configurationChangeListener = ConfigurationListener { flags ->
    configurationUpdatedFlags.getAndUpdate { it or flags }
    if ((flags and ConfigurationListener.CFG_DEVICE) != 0) {
      currentDpi = model.configuration.getDensity().dpiValue
    }
    true
  }

  // TODO(b/369573219): make this method private
  protected fun updateTargets() {
    val updateAgain = Runnable { this.updateTargets() }
    scene.root?.let {
      updateTargetProviders(it, updateAgain)
      it.updateTargets()
    }
  }

  // TODO(b/369573219): make this method private
  protected fun logConfigurationChange(surface: DesignSurface<*>) {
    val flags: Int = configurationUpdatedFlags.getAndSet(0) // Get and reset the saved flags
    if (flags != 0) {
      // usage tracking (we only pay attention to individual changes where only one item is affected
      // since those are likely to be triggered by the user
      val analyticsManager = (surface.analyticsManager) as NlAnalyticsManager

      if ((flags and ConfigurationListener.CFG_THEME) != 0) {
        analyticsManager.trackThemeChange()
      }
      if ((flags and ConfigurationListener.CFG_TARGET) != 0) {
        analyticsManager.trackApiLevelChange()
      }
      if ((flags and ConfigurationListener.CFG_LOCALE) != 0) {
        analyticsManager.trackLanguageChange()
      }
      if ((flags and ConfigurationListener.CFG_DEVICE) != 0) {
        analyticsManager.trackDeviceChange()
      }
    }
  }
}
