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
import com.android.tools.idea.common.type.DefaultDesignerFileType
import com.android.tools.idea.common.type.DesignerEditorFileType
import com.android.tools.idea.ui.designer.EditorDesignSurface
import com.google.common.collect.ImmutableCollection
import com.google.common.collect.ImmutableList
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorNotifications
import com.intellij.util.ui.UIUtil
import java.awt.LayoutManager
import java.awt.event.AdjustmentEvent
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
) : EditorDesignSurface(layout) {

  init {
    isOpaque = true
    isFocusable = false
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

  @Slow // Some implementations might be slow
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
}
