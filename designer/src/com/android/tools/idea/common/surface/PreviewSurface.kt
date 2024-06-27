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
package com.android.tools.idea.common.surface

import com.android.annotations.concurrency.GuardedBy
import com.android.annotations.concurrency.Slow
import com.android.tools.configurations.Configuration
import com.android.tools.editor.PanZoomListener
import com.android.tools.idea.common.error.Issue
import com.android.tools.idea.common.error.IssueListener
import com.android.tools.idea.common.layout.LayoutManagerSwitcher
import com.android.tools.idea.common.model.ItemTransferable
import com.android.tools.idea.common.model.ModelListener
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.model.SelectionModel
import com.android.tools.idea.common.scene.SceneManager
import com.android.tools.idea.common.surface.DesignSurfaceSettings.Companion.getInstance
import com.android.tools.idea.common.type.DefaultDesignerFileType
import com.android.tools.idea.common.type.DesignerEditorFileType
import com.android.tools.idea.ui.designer.EditorDesignSurface
import com.google.common.collect.ImmutableCollection
import com.google.common.collect.ImmutableList
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorNotifications
import com.intellij.util.ui.UIUtil
import java.awt.LayoutManager
import java.awt.event.AdjustmentEvent
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.lang.ref.WeakReference
import java.util.concurrent.CompletableFuture
import java.util.concurrent.locks.ReentrantLock
import java.util.function.Consumer
import javax.swing.JPanel
import javax.swing.Timer
import kotlin.concurrent.withLock

/**
 * TODO Once [DesignSurface] is converted to kt, rename [PreviewSurface] back to [DesignSurface].
 */
abstract class PreviewSurface<T : SceneManager>(
  val project: Project,
  val selectionModel: SelectionModel,
  val zoomControlsPolicy: ZoomControlsPolicy,
  layout: LayoutManager,
) : EditorDesignSurface(layout), Disposable, InteractableScenesSurface, ScaleListener {

  init {
    isOpaque = true
    isFocusable = false

    // TODO: Do this as part of the layout/validate operation instead
    addComponentListener(
      object : ComponentAdapter() {
        /**
         * When surface is opened at first time, it zoom-to-fit the content to make the previews fit
         * the initial window size. After that it leave user to control the zoom. This flag
         * indicates if the initial zoom-to-fit is done or not.
         */
        private var isInitialZoomLevelDetermined = false

        override fun componentResized(componentEvent: ComponentEvent) {
          if (componentEvent.id == ComponentEvent.COMPONENT_RESIZED) {
            if (!isInitialZoomLevelDetermined && isShowing && width > 0 && height > 0) {
              // Set previous scale when DesignSurface becomes visible at first time.
              val hasModelAttached = restoreZoomOrZoomToFit()
              if (!hasModelAttached) {
                // No model is attached, ignore the setup of initial zoom level.
                return
              }
              // The default size is defined, enable the flag.
              isInitialZoomLevelDetermined = true
            }
            // We rebuilt the scene to make sure all SceneComponents are placed at right positions.
            sceneManagers.forEach { manager: T -> manager.scene.needsRebuildList() }
            repaint()
          }
        }
      }
    )
  }

  protected open fun useSmallProgressIcon(): Boolean {
    return true
  }

  /** All the selectable components in the design surface */
  abstract val selectableComponents: List<NlComponent>

  abstract val layoutManagerSwitcher: LayoutManagerSwitcher?

  private val myIssueListeners: MutableList<IssueListener> = ArrayList()

  val issueListener: IssueListener = IssueListener { issue: Issue? ->
    myIssueListeners.forEach(
      Consumer { listener: IssueListener -> listener.onIssueSelected(issue) }
    )
  }

  fun addIssueListener(listener: IssueListener) {
    myIssueListeners.add(listener)
  }

  fun removeIssueListener(listener: IssueListener) {
    myIssueListeners.remove(listener)
  }

  private var _fileEditorDelegate = WeakReference<FileEditor?>(null)

  /**
   * Sets the file editor to which actions like undo/redo will be delegated. This is only needed if
   * this DesignSurface is not a child of a [FileEditor].
   *
   * The surface will only keep a [WeakReference] to the editor.
   */
  var fileEditorDelegate: FileEditor?
    get() = _fileEditorDelegate.get()
    set(value) {
      _fileEditorDelegate = WeakReference(value)
    }

  /** Updates the notifications panel associated to this [DesignSurface]. */
  protected fun updateNotifications() {
    val fileEditor: FileEditor? = _fileEditorDelegate.get()
    val file = fileEditor?.file ?: return
    UIUtil.invokeLaterIfNeeded {
      EditorNotifications.getInstance(project).updateNotifications(file)
    }
  }

  /** Calls [repaint] with delay. TODO Make it private */
  protected val repaintTimer = Timer(15) { repaint() }

  /** Call this to generate repaints */
  fun needsRepaint() {
    if (!repaintTimer.isRunning) {
      repaintTimer.isRepeats = false
      repaintTimer.start()
    }
  }

  // TODO Make it private
  protected val modelListener: ModelListener =
    object : ModelListener {
      override fun modelDerivedDataChanged(model: NlModel) {
        updateNotifications()
      }

      override fun modelChanged(model: NlModel) {
        updateNotifications()
      }

      override fun modelChangedOnLayout(model: NlModel, animate: Boolean) {
        repaint()
      }
    }

  private val listenersLock = ReentrantLock()

  // TODO Make it private
  // TODO listeners are called directly in number of places. Shouldn't getSurfaceListeners be called
  // instead?
  @GuardedBy("listenersLock") protected val listeners = mutableListOf<DesignSurfaceListener>()

  @GuardedBy("listenersLock") private val zoomListeners = mutableListOf<PanZoomListener>()

  fun addListener(listener: DesignSurfaceListener) {
    listenersLock.withLock {
      // Ensure single registration
      listeners.remove(listener)
      listeners.add(listener)
    }
  }

  fun removeListener(listener: DesignSurfaceListener) {
    listenersLock.withLock { listeners.remove(listener) }
  }

  fun addPanZoomListener(listener: PanZoomListener) {
    listenersLock.withLock {
      // Ensure single registration
      zoomListeners.remove(listener)
      zoomListeners.add(listener)
    }
  }

  fun removePanZoomListener(listener: PanZoomListener) {
    listenersLock.withLock { zoomListeners.remove(listener) }
  }

  // TODO Make it private
  protected fun clearListeners() {
    listenersLock.withLock {
      listeners.clear()
      zoomListeners.clear()
    }
  }

  /**
   * Gets a copy of [zoomListeners] under a lock. Use this method instead of accessing the listeners
   * directly. TODO Make it private
   */
  protected fun getZoomListeners(): ImmutableList<PanZoomListener> {
    listenersLock.withLock {
      return ImmutableList.copyOf(zoomListeners)
    }
  }

  /**
   * Gets a copy of [listeners] under a lock. Use this method instead of accessing the listeners
   * directly. TODO Make it private
   */
  protected fun getSurfaceListeners(): ImmutableList<DesignSurfaceListener> {
    listenersLock.withLock {
      return ImmutableList.copyOf(listeners)
    }
  }

  protected fun notifyScaleChanged(previousScale: Double, newScale: Double) {
    for (listener in getZoomListeners()) {
      listener.zoomChanged(previousScale, newScale)
    }
  }

  protected fun notifyPanningChanged(adjustmentEvent: AdjustmentEvent) {
    for (listener in getZoomListeners()) {
      listener.panningChanged(adjustmentEvent)
    }
  }

  abstract val selectionAsTransferable: ItemTransferable

  /**
   * Returns whether render error panels should be rendered when [SceneView]s in this surface have
   * render errors.
   */
  open fun shouldRenderErrorsPanel(): Boolean {
    return false
  }

  /** When not null, returns a [JPanel] to be rendered next to the primary panel of the editor. */
  open val accessoryPanel: JPanel? = null

  open fun getLayoutScannerControl(): LayoutScannerControl? {
    return null
  }

  /**
   * Scroll to the center of a list of given components. Usually the center of the area containing
   * these elements.
   */
  abstract fun scrollToCenter(list: List<NlComponent>)

  protected open fun isKeepingScaleWhenReopen(): Boolean {
    return true
  }

  /**
   * Restore the zoom level if it can be loaded from persistent settings, otherwise zoom-to-fit.
   *
   * @return whether zoom-to-fit or zoom restore has happened, which won't happen if there is no
   *   model.
   */
  fun restoreZoomOrZoomToFit(): Boolean {
    val model = model ?: return false
    if (!restorePreviousScale(model)) {
      zoomController.zoomToFit()
    }
    return true
  }

  /**
   * Load the saved zoom level from the file of the given [NlModel]. Return true if the previous
   * zoom level is restored, false otherwise.
   */
  private fun restorePreviousScale(model: NlModel): Boolean {
    if (!isKeepingScaleWhenReopen()) {
      return false
    }
    val state = getInstance(model.project).surfaceState
    val previousScale =
      state.loadFileScale(project, model.virtualFile, zoomController) ?: return false
    zoomController.setScale(previousScale)
    return true
  }

  @Slow
  /** Some implementations might be slow */
  protected abstract fun createSceneManager(model: NlModel): T

  /**
   * @return the primary (first) [NlModel] if exist. null otherwise.
   * @see [models]
   */
  @Deprecated("The surface can contain multiple models. Use {@link #getModels()} instead.")
  val model: NlModel?
    get() = models.firstOrNull()

  abstract val models: ImmutableList<NlModel>
  abstract val sceneManagers: ImmutableList<T>

  val layoutType: DesignerEditorFileType
    get() = model?.type ?: DefaultDesignerFileType

  override fun getConfigurations(): ImmutableCollection<Configuration> {
    return models.stream().map(NlModel::configuration).collect(ImmutableList.toImmutableList())
  }

  abstract fun setModel(model: NlModel?): CompletableFuture<Void>

  // TODO Make private
  protected val renderFutures = mutableListOf<CompletableFuture<Void>>()

  /** Returns true if this surface is currently refreshing. */
  fun isRefreshing(): Boolean {
    synchronized(renderFutures) {
      return renderFutures.isNotEmpty()
    }
  }

  /**
   * Schedule the render requests sequentially for all [SceneManager]s in this [DesignSurface].
   *
   * @param renderRequest The requested rendering to be scheduled. This gives the caller a chance to
   *   choose the preferred rendering request.
   * @return A callback which is triggered when the scheduled rendering are completed.
   */
  protected fun requestSequentialRender(
    renderRequest: (T) -> CompletableFuture<Void?>?
  ): CompletableFuture<Void> {
    val callback = CompletableFuture<Void>()
    synchronized(renderFutures) {
      if (renderFutures.isNotEmpty()) {
        // TODO: This may make the rendered previews not match the last status of NlModel if the
        // modifications happen during rendering.
        // Similar case happens in LayoutlibSceneManager#requestRender function, both need to be
        // fixed.
        renderFutures.add(callback)
        return callback
      } else {
        renderFutures.add(callback)
      }
    }

    // Cascading the CompletableFuture to make them executing sequentially.
    var renderFuture = CompletableFuture.completedFuture<Void?>(null)
    for (manager in sceneManagers) {
      renderFuture =
        renderFuture.thenCompose {
          val future = renderRequest(manager)
          invalidate()
          future
        }
    }
    renderFuture.thenRun {
      synchronized(renderFutures) {
        renderFutures.forEach { it.complete(null) }
        renderFutures.clear()
      }
      updateNotifications()
    }

    return callback
  }
}
