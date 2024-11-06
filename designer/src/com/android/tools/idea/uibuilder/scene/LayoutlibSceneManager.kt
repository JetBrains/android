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

import com.android.SdkConstants
import com.android.ide.common.rendering.api.RenderSession.TouchEventType
import com.android.resources.Density
import com.android.sdklib.AndroidCoordinate
import com.android.tools.configurations.ConfigurationListener
import com.android.tools.idea.common.model.ModelListener
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.model.SelectionListener
import com.android.tools.idea.common.scene.SceneComponentHierarchyProvider
import com.android.tools.idea.common.scene.SceneManager
import com.android.tools.idea.common.scene.decorator.SceneDecoratorFactory
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.LayoutScannerConfiguration
import com.android.tools.idea.common.surface.LayoutScannerEnabled
import com.android.tools.idea.common.surface.SQUARE_SHAPE_POLICY
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.res.ResourceNotificationManager
import com.android.tools.idea.res.ResourceNotificationManager.Companion.getInstance
import com.android.tools.idea.uibuilder.analytics.NlAnalyticsManager
import com.android.tools.idea.uibuilder.api.ViewEditor
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl
import com.android.tools.idea.uibuilder.menu.NavigationViewSceneView
import com.android.tools.idea.uibuilder.scene.decorator.NlSceneDecoratorFactory
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.ScreenView
import com.android.tools.idea.uibuilder.surface.ScreenViewLayer
import com.android.tools.idea.uibuilder.type.MenuFileType
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintMode
import com.android.tools.rendering.RenderResult
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.ui.UIUtil
import java.awt.event.KeyEvent
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.jetbrains.annotations.TestOnly

private val DECORATOR_FACTORY: SceneDecoratorFactory = NlSceneDecoratorFactory()

/**
 * [SceneManager] that creates a Scene from an NlModel representing a layout using layoutlib.
 *
 * @param model the [NlModel] to be rendered by this [LayoutlibSceneManager].
 * @param designSurface the [DesignSurface] used to present the result of the renders.
 * @param renderTaskDisposerExecutor the [Executor] to be used for running the slow [dispose] calls.
 * @param sceneComponentProvider the [SceneComponentHierarchyProvider] providing the mapping from
 *   [NlComponent] to [SceneComponent]s.
 * @param layoutScannerConfig the [LayoutScannerConfiguration] for layout validation from
 *   Accessibility Testing Framework.
 */
open class LayoutlibSceneManager(
  model: NlModel,
  designSurface: DesignSurface<*>,
  renderTaskDisposerExecutor: Executor = AppExecutorUtil.getAppExecutorService(),
  sceneComponentProvider: SceneComponentHierarchyProvider =
    LayoutlibSceneManagerHierarchyProvider(),
  layoutScannerConfig: LayoutScannerConfiguration = LayoutScannerEnabled(),
) : SceneManager(model, designSurface, sceneComponentProvider), InteractiveSceneManager {

  private val isDisposed = AtomicBoolean(false)

  private var areListenersRegistered = false

  override val designSurface: NlDesignSurface
    get() = super.designSurface as NlDesignSurface

  val viewEditor: ViewEditor = ViewEditorImpl(model, scene)

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
  private val layoutlibSceneRenderer: LayoutlibSceneRenderer =
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
  open val renderResult: RenderResult?
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

  override fun updateSceneViews() {
    if (model.type === MenuFileType) {
      this.sceneView = createSceneViewsForMenu()
      this.secondarySceneView = null
      return
    }
    designSurface.screenViewProvider.let {
      this.sceneView = it.createPrimarySceneView(designSurface, this)
      this.secondarySceneView = it.createSecondarySceneView(designSurface, this)
    }
    designSurface.updateErrorDisplay()
  }

  private fun createSceneViewsForMenu(): SceneView {
    // TODO See if there's a better way to trigger the NavigationViewSceneView. Perhaps examine the
    // view objects?
    val newSceneView: SceneView =
      model.file.rootTag
        ?.takeIf {
          it.getAttributeValue(SdkConstants.ATTR_SHOW_IN, SdkConstants.TOOLS_URI) ==
            NavigationViewSceneView.SHOW_IN_ATTRIBUTE_VALUE
        }
        ?.let {
          ScreenView.newBuilder(designSurface, this)
            .withLayersProvider { sv: ScreenView? ->
              val colorBlindMode = designSurface.screenViewProvider.colorBlindFilter
              ImmutableList.of(
                ScreenViewLayer(
                  sv!!,
                  colorBlindMode,
                  designSurface,
                  designSurface::rotateSurfaceDegree,
                )
              )
            }
            .withContentSizePolicy(NavigationViewSceneView.CONTENT_SIZE_POLICY)
            .withShapePolicy(SQUARE_SHAPE_POLICY)
            .build()
        } ?: ScreenView.newBuilder(designSurface, this).build()
    designSurface.updateErrorDisplay()
    return newSceneView
  }

  private val selectionChangeListener = SelectionListener { _, _ ->
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
        this@LayoutlibSceneManager.update()
      }
    }

  private val configurationChangeListener = ConfigurationListener { flags ->
    configurationUpdatedFlags.getAndUpdate { it or flags }
    if ((flags and ConfigurationListener.CFG_DEVICE) != 0) {
      currentDpi = model.configuration.getDensity().dpiValue
    }
    true
  }

  private val modelChangeListener =
    object : ModelListener {
      override fun modelDerivedDataChanged(model: NlModel) {
        // After the model derived data is changed, we need to update the selection in Edt thread.
        // Changing selection should run in UI thread to avoid race condition.
        val surface: NlDesignSurface = this@LayoutlibSceneManager.designSurface
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
        // The structure might have changed, force a re-inflate
        sceneRenderConfiguration.needsInflation.set(true)
        // If the update is reversed (namely, we update the View hierarchy from the component
        // hierarchy because information about scrolling is located in the component hierarchy and
        // is lost in the view hierarchy) we need to run render again to propagate the change
        // (re-layout) in the scrolling values to the View hierarchy (position, children etc.) and
        // render the updated result.
        layoutlibSceneRenderer.sceneRenderConfiguration.doubleRenderIfNeeded.set(true)
        requestRender()
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
        requestRender()
      }
    }

  init {
    updateSceneViews()
    designSurface.selectionModel.addListener(selectionChangeListener)
    model.configuration.addListener(configurationChangeListener)
    val components: List<NlComponent> = model.treeReader.components
    if (components.isNotEmpty()) {
      val rootComponent = components.first().root
      val previous = scene.isAnimated
      scene.isAnimated = false
      val hierarchy = sceneComponentProvider.createHierarchy(this, rootComponent)
      hierarchy.firstOrNull()?.let {
        updateFromComponent(it, HashSet())
        scene.root = it
        updateTargets()
        scene.isAnimated = previous
      } ?: Logger.getInstance(LayoutlibSceneManager::class.java).warn("No root component")
    }
    model.addListener(modelChangeListener)
    areListenersRegistered = true
    // let's make sure the selection is correct
    scene.selectionChanged(designSurface.selectionModel, designSurface.selectionModel.selection)
  }

  private fun getRenderTrigger() =
    getTriggerFromChangeType(model.lastChangeType).also { model.resetLastChange() }

  private fun onBeforeRender(): Boolean {
    if (isDisposed.get()) {
      Logger.getInstance(LayoutlibSceneManager::class.java)
        .warn("tried to render after LayoutlibSceneManager has been disposed")
      return false
    }
    logConfigurationChange(designSurface)
    return true
  }

  /** Adds a new render request to the queue. */
  override fun requestRender() {
    layoutlibSceneRenderer.takeIf { onBeforeRender() }?.requestRender(getRenderTrigger())
  }

  /** Adds a new render request to the queue and wait for it to finish. */
  @RequiresBackgroundThread
  override suspend fun requestRenderAndWait() {
    layoutlibSceneRenderer.takeIf { onBeforeRender() }?.requestRenderAndWait(getRenderTrigger())
  }

  override fun requestLayoutAsync(animate: Boolean): CompletableFuture<Void> {
    if (isDisposed.get()) {
      Logger.getInstance(LayoutlibSceneManager::class.java)
        .warn("requestLayout after LayoutlibSceneManager has been disposed")
    }
    val currentTask =
      layoutlibSceneRenderer.renderTask ?: return CompletableFuture.completedFuture(null)
    return currentTask.layout().thenAccept { result: RenderResult? ->
      result
        ?.takeUnless { isDisposed.get() }
        ?.let {
          layoutlibSceneRenderer.updateHierarchy(it)
          model.notifyListenersModelChangedOnLayout(animate)
        }
    }
  }

  /**
   * Executes the given block under a [RenderSession]. This allows the given block to access
   * resources since they are set up before executing it.
   *
   * @param block the [Runnable] to be executed in the Render thread.
   * @param timeout maximum time to wait for the action to execute. If <= 0, the default timeout
   *   will be used (see [RenderAsyncActionExecutor])
   * @param timeUnit the [TimeUnit] of the given timeout.
   */
  open fun executeInRenderSessionAsync(
    block: Runnable,
    timeout: Long,
    timeUnit: TimeUnit,
  ): CompletableFuture<Void> {
    val currentTask =
      layoutlibSceneRenderer.renderTask ?: return CompletableFuture.completedFuture(null)
    return currentTask.runAsyncRenderActionWithSession(block, timeout, timeUnit)
  }

  private fun updateTargets() {
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
      requestRender()
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
  ) {
    if (isDisposed.get()) {
      Logger.getInstance(LayoutlibSceneManager::class.java)
        .warn("triggerTouchEventAsync after LayoutlibSceneManager has been disposed")
      return
    }
    layoutlibSceneRenderer.renderTask?.let {
      interactiveEventsCount++
      it.triggerTouchEvent(type, x, y, currentTimeNanos())
    }
  }

  /**
   * Passes an AWT KeyEvent from the surface to layoutlib.
   *
   * @return a future that is completed when layoutlib handled the key event
   */
  fun triggerKeyEventAsync(event: KeyEvent) {
    if (isDisposed.get()) {
      Logger.getInstance(LayoutlibSceneManager::class.java)
        .warn("triggerKeyEventAsync after LayoutlibSceneManager has been disposed")
      return
    }
    layoutlibSceneRenderer.renderTask?.let {
      interactiveEventsCount++
      it.triggerKeyEvent(event, currentTimeNanos())
    }
  }

  /** Executes the given [Runnable] callback synchronously with a 30ms timeout. */
  override fun executeCallbacksAndRequestRender() {
    sceneRenderConfiguration.layoutlibCallbacksConfig.set(
      LayoutlibCallbacksConfig.EXECUTE_BEFORE_RENDERING
    )
    requestRender()
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
