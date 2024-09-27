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

import com.android.ide.common.rendering.api.RenderSession.TouchEventType
import com.android.resources.Density
import com.android.sdklib.AndroidCoordinate
import com.android.tools.configurations.ConfigurationListener
import com.android.tools.idea.common.model.ModelListener
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.model.SelectionListener
import com.android.tools.idea.common.scene.SceneComponentHierarchyProvider
import com.android.tools.idea.common.scene.SceneManager
import com.android.tools.idea.common.scene.decorator.SceneDecoratorFactory
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.LayoutScannerConfiguration
import com.android.tools.idea.res.ResourceNotificationManager
import com.android.tools.idea.res.ResourceNotificationManager.Companion.getInstance
import com.android.tools.idea.uibuilder.analytics.NlAnalyticsManager
import com.android.tools.idea.uibuilder.scene.decorator.NlSceneDecoratorFactory
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintMode
import com.android.tools.rendering.ExecuteCallbacksResult
import com.android.tools.rendering.InteractionEventResult
import com.android.tools.rendering.RenderResult
import com.google.common.collect.ImmutableSet
import com.google.wireless.android.sdk.stats.LayoutEditorRenderResult
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.ui.UIUtil
import java.awt.event.KeyEvent
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
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
) : SceneManager(model, designSurface, sceneComponentProvider), InteractiveSceneManager {

  // TODO(b/369573219): make this field private and remove JvmField annotation
  @JvmField protected val isDisposed = AtomicBoolean(false)

  // TODO(b/369573219): make this field private and remove JvmField annotation
  @JvmField protected var areListenersRegistered = false

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
  var updateAndRenderWhenActivated = true

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

  // TODO(b/369573219): make this field private and remove JvmField annotation
  @JvmField
  protected val modelChangeListener =
    object : ModelListener {
      override fun modelDerivedDataChanged(model: NlModel) {
        // After the model derived data is changed, we need to update the selection in Edt thread.
        // Changing selection should run in UI thread to avoid race condition.
        val surface: NlDesignSurface = this@NewLayoutlibSceneManager.designSurface
        CompletableFuture.runAsync(
          {
            // Ensure the new derived that is passed to the Scene components hierarchy
            if (!isDisposed.get()) update()

            // Selection change listener should run in UI thread not in the layoublib rendering
            // thread. This avoids race condition.
            selectionChangeListener.selectionChanged(
              surface.selectionModel,
              surface.selectionModel.selection,
            )
          },
          EdtExecutorService.getInstance(),
        )
      }

      override fun modelChanged(model: NlModel) {
        val surface: NlDesignSurface = this@NewLayoutlibSceneManager.designSurface
        // The structure might have changed, force a re-inflate
        sceneRenderConfiguration.needsInflation.set(true)
        // If the update is reversed (namely, we update the View hierarchy from the component
        // hierarchy because information about scrolling is located in the component hierarchy and
        // is lost in the view hierarchy) we need to run render again to propagate the change
        // (re-layout) in the scrolling values to the View hierarchy (position, children etc.) and
        // render the updated result.
        layoutlibSceneRenderer.sceneRenderConfiguration.doubleRenderIfNeeded.set(true)
        requestRenderAsync(getTriggerFromChangeType(model.lastChangeType))
          .thenRunAsync(
            {
              selectionChangeListener.selectionChanged(
                surface.selectionModel,
                surface.selectionModel.selection,
              )
            },
            EdtExecutorService.getInstance(),
          )
      }

      override fun modelChangedOnLayout(model: NlModel, animate: Boolean) {
        UIUtil.invokeLaterIfNeeded {
          if (!isDisposed.get()) {
            val previous: Boolean = scene.isAnimated
            scene.isAnimated = animate
            update()
            scene.isAnimated = previous
          }
        }
      }

      override fun modelLiveUpdate(model: NlModel) {
        requestRenderAsync()
      }
    }

  /**
   * Adds a new render request to the queue.
   *
   * @param trigger render trigger for reporting purposes
   * @return [CompletableFuture] that will be completed once the render has been done.
   */
  protected open fun requestRenderAsync(
    trigger: LayoutEditorRenderResult.Trigger?
  ): CompletableFuture<Void> {
    if (isDisposed.get()) {
      Logger.getInstance(LayoutlibSceneManager::class.java)
        .warn("requestRender after LayoutlibSceneManager has been disposed")
      return CompletableFuture.completedFuture(null)
    }

    logConfigurationChange(designSurface)
    model.resetLastChange()
    return layoutlibSceneRenderer.renderAsync(trigger).thenCompose {
      CompletableFuture.completedFuture(null)
    }
  }

  override fun requestRenderAsync() =
    requestRenderAsync(getTriggerFromChangeType(model.lastChangeType))

  // TODO(b/369573219): make this method private
  protected fun updateTargets() {
    val updateAgain = Runnable { this.updateTargets() }
    scene.root?.let {
      updateTargetProviders(it, updateAgain)
      it.updateTargets()
    }
  }

  private fun logConfigurationChange(surface: DesignSurface<*>) {
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

  override fun activate(source: Any): Boolean {
    val active = super.activate(source)
    layoutlibSceneRenderer.activate()
    if (active && updateAndRenderWhenActivated) {
      val manager = getInstance(model.project)
      val version = manager.getCurrentVersion(model.facet, model.file, model.configuration)
      if (version != layoutlibSceneRenderer.renderedVersion) {
        sceneRenderConfiguration.needsInflation.set(true)
      }
      requestRenderAsync()
    }
    return active
  }

  override fun deactivate(source: Any): Boolean =
    super.deactivate(source).also { if (it) layoutlibSceneRenderer.deactivate() }

  override fun dispose() {
    if (isDisposed.getAndSet(true)) return
    try {
      if (areListenersRegistered) {
        val model = model
        designSurface.selectionModel.removeListener(selectionChangeListener)
        model.configuration.removeListener(configurationChangeListener)
        model.removeListener(modelChangeListener)
      }
    } finally {
      super.dispose()
    }
  }

  /** Count of user events received by this scene since last reset. */
  final override var interactiveEventsCount = 0
    private set

  private fun currentTimeNanos(): Long = layoutlibSceneRenderer.sessionClock.timeNanos

  /**
   * Triggers execution of the Handler and frame callbacks in the layoutlib.
   *
   * @return a future that is completed when callbacks are executed.
   */
  private fun executeCallbacksAsync(): CompletableFuture<ExecuteCallbacksResult> {
    if (isDisposed.get()) {
      Logger.getInstance(LayoutlibSceneManager::class.java)
        .warn("executeCallbacks after LayoutlibSceneManager has been disposed")
    }
    val currentTask =
      layoutlibSceneRenderer.renderTask
        ?: return CompletableFuture.completedFuture(ExecuteCallbacksResult.EMPTY)
    return currentTask.executeCallbacks(currentTimeNanos())
  }

  /**
   * Informs layoutlib that there was a (mouse) touch event detected of a particular type at a
   * particular point
   *
   * @param type type of touch event
   * @param x horizontal android coordinate of the detected touch event
   * @param y vertical android coordinate of the detected touch event
   * @return a future that is completed when layoutlib handled the touch event
   */
  fun triggerTouchEventAsync(
    type: TouchEventType,
    @AndroidCoordinate x: Int,
    @AndroidCoordinate y: Int,
  ): CompletableFuture<InteractionEventResult?> {
    if (isDisposed.get()) {
      Logger.getInstance(LayoutlibSceneManager::class.java)
        .warn("triggerTouchEventAsync after LayoutlibSceneManager has been disposed")
    }

    val currentTask =
      layoutlibSceneRenderer.renderTask ?: return CompletableFuture.completedFuture(null)
    interactiveEventsCount++
    return currentTask.triggerTouchEvent(type, x, y, currentTimeNanos())
  }

  /**
   * Passes an AWT KeyEvent from the surface to layoutlib.
   *
   * @return a future that is completed when layoutlib handled the key event
   */
  fun triggerKeyEventAsync(event: KeyEvent): CompletableFuture<InteractionEventResult?> {
    if (isDisposed.get()) {
      Logger.getInstance(LayoutlibSceneManager::class.java)
        .warn("triggerKeyEventAsync after LayoutlibSceneManager has been disposed")
    }

    val currentTask =
      layoutlibSceneRenderer.renderTask ?: return CompletableFuture.completedFuture(null)
    interactiveEventsCount++
    return currentTask.triggerKeyEvent(event, currentTimeNanos())
  }

  /** Executes the given [Runnable] callback synchronously with a 30ms timeout. */
  override fun executeCallbacksAndRequestRender(): CompletableFuture<Void> {
    return executeCallbacksAsync().thenCompose { requestRenderAsync() }
  }

  /** Pauses session clock, so that session time stops advancing. */
  override fun pauseSessionClock(): Unit = layoutlibSceneRenderer.sessionClock.pause()

  /** Resumes session clock, so that session time keeps advancing. */
  override fun resumeSessionClock(): Unit = layoutlibSceneRenderer.sessionClock.resume()

  /** Resets the counter of user events received by this scene to 0. */
  override fun resetInteractiveEventsCounter() {
    interactiveEventsCount = 0
  }
}
