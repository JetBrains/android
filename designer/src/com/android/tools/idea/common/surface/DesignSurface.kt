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
import com.android.annotations.concurrency.UiThread
import com.android.sdklib.AndroidCoordinate
import com.android.tools.adtui.PANNABLE_KEY
import com.android.tools.adtui.Pannable
import com.android.tools.adtui.ZOOMABLE_KEY
import com.android.tools.adtui.common.SwingCoordinate
import com.android.tools.configurations.Configuration
import com.android.tools.editor.PanZoomListener
import com.android.tools.idea.actions.CONFIGURATIONS
import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.common.analytics.DesignerAnalyticsManager
import com.android.tools.idea.common.editor.ActionManager
import com.android.tools.idea.common.error.Issue
import com.android.tools.idea.common.error.IssueListener
import com.android.tools.idea.common.error.IssueModel
import com.android.tools.idea.common.error.LintIssueProvider
import com.android.tools.idea.common.layout.LayoutManagerSwitcher
import com.android.tools.idea.common.layout.manager.MatchParentLayoutManager
import com.android.tools.idea.common.layout.manager.PositionableContentLayoutManager
import com.android.tools.idea.common.lint.LintAnnotationsModel
import com.android.tools.idea.common.model.Coordinates
import com.android.tools.idea.common.model.DefaultSelectionModel
import com.android.tools.idea.common.model.ItemTransferable
import com.android.tools.idea.common.model.ModelListener
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.model.SelectionListener
import com.android.tools.idea.common.model.SelectionModel
import com.android.tools.idea.common.scene.Scene
import com.android.tools.idea.common.scene.SceneManager
import com.android.tools.idea.common.surface.DesignSurfaceScrollPane.Companion.createDefaultScrollPane
import com.android.tools.idea.common.surface.DesignSurfaceSettings.Companion.getInstance
import com.android.tools.idea.common.surface.layout.DesignSurfaceViewport
import com.android.tools.idea.common.surface.layout.NonScrollableDesignSurfaceViewport
import com.android.tools.idea.common.surface.layout.ScrollableDesignSurfaceViewport
import com.android.tools.idea.common.type.DefaultDesignerFileType
import com.android.tools.idea.common.type.DesignerEditorFileType
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.ui.designer.EditorDesignSurface
import com.android.tools.idea.uibuilder.surface.ScreenView
import com.google.common.base.Predicate
import com.google.common.collect.Collections2
import com.google.common.collect.ImmutableCollection
import com.google.common.collect.ImmutableList
import com.google.common.collect.Sets
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.xml.XmlTag
import com.intellij.ui.EditorNotifications
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.EdtExecutorService
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.containers.toArray
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.AWTEvent
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.lang.ref.WeakReference
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.function.Consumer
import javax.swing.JComponent
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.OverlayLayout
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.concurrent.withLock
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly

private val LAYER_PROGRESS = JLayeredPane.POPUP_LAYER + 10
private val LAYER_MOUSE_CLICK = LAYER_PROGRESS + 10

/**
 * The expected bitwise Integer when both [DesignSurface] sizes and [Preview] renders are updated.
 */
private const val RESTORE_ZOOM_DONE_INT_MASK = 4

/**
 * Number used as part of the bitwise mask to notify [DesignSurface] to restore zoom.
 *
 * @see [DesignSurface.notifyRestoreZoom]
 */
private const val NOTIFY_RESTORE_ZOOM_INT_MASK = 1

/**
 * Number used as part of the bitwise mask to notify [DesignSurface] to restore zoom.
 *
 * @see also [DesignSurface.notifyRestoreZoom].
 */
private const val NOTIFY_COMPONENT_RESIZED_INT_MASK = 2

/** Filter got [DesignSurface.models] to avoid returning disposed elements */
val FILTER_DISPOSED_MODELS =
  Predicate<NlModel> { input: NlModel? -> input != null && !input.module.isDisposed }

/**
 * A generic design surface for use in a graphical editor.
 *
 * Setup the layers for the [DesignSurface] If the surface is scrollable, we use four layers:
 * 1. ScrollPane layer: Layer that contains the ScreenViews and does all the rendering, including
 *    the interaction layers.
 * 2. Progress layer: Displays the progress icon while a rendering is happening
 * 3. Mouse click display layer: It allows displaying clicks on the surface with a translucent
 *    bubble
 * 4. Zoom controls layer: Used to display the zoom controls of the surface
 *
 * (4) sits at the top of the stack so is the first one to receive events like clicks.
 *
 * If the surface is NOT scrollable, the zoom controls will not be added and the scroll pane will be
 * replaced by the actual content.
 */
abstract class DesignSurface<T : SceneManager>(
  val project: Project,
  actionManagerProvider: (DesignSurface<T>) -> ActionManager<out DesignSurface<T>>,
  interactableProvider: (DesignSurface<T>) -> Interactable = { SurfaceInteractable(it) },
  interactionProviderCreator: (DesignSurface<T>) -> InteractionHandler,
  positionableLayoutManager: PositionableContentLayoutManager,
  // We do not need "open" here, but unfortunately we use mocks, and they fail if this is not
  // defined as open. "open" can be removed if we remove the mocks.
  open val actionHandlerProvider: (DesignSurface<T>) -> DesignSurfaceActionHandler,
  // We do not need "open" here, but unfortunately we use mocks, and they fail if this is not
  // defined as open. "open" can be removed if we remove the mocks.
  open val selectionModel: SelectionModel = DefaultSelectionModel(),
  private val zoomControlsPolicy: ZoomControlsPolicy,
  waitForRenderBeforeRestoringZoom: Boolean = false,
) :
  EditorDesignSurface(BorderLayout()),
  Disposable,
  InteractableScenesSurface,
  ScaleListener,
  UiDataProvider {

  /** [CoroutineScope] to be used by any operations constrained to this DesignSurface */
  protected val scope = AndroidCoroutineScope(this)

  /** The expected bitwise mask value for when we want to restore the zoom. */
  private val expectedRestoreZoomMask: Int =
    if (waitForRenderBeforeRestoringZoom) {
      // We should wait for rendering and to DesignSurface to resize.
      NOTIFY_RESTORE_ZOOM_INT_MASK or NOTIFY_COMPONENT_RESIZED_INT_MASK
    } else {
      // There is no need to wait for rendering we can restore zoom whenever DesignSurface resizes.
      NOTIFY_COMPONENT_RESIZED_INT_MASK
    }

  init {
    // TODO: handle the case when selection are from different NlModels.
    // Manager can be null if the selected component is not part of NlModel. For example, a
    // temporarily NlModel. In that case we don't change focused SceneView.
    val selectionListener = SelectionListener { _, selection ->
      if (focusedSceneView != null) {
        notifySelectionChanged(selection)
      } else {
        notifySelectionChanged(emptyList())
      }
    }
    selectionModel.addListener(selectionListener)
  }

  /**
   * Responsible for converting this surface state and send it for tracking (if logging is enabled).
   */
  open val analyticsManager: DesignerAnalyticsManager = DesignerAnalyticsManager(this)

  private val hasZoomControls: Boolean = zoomControlsPolicy != ZoomControlsPolicy.HIDDEN

  val layeredPane: JLayeredPane = JLayeredPane().apply { setFocusable(true) }

  val guiInputHandler =
    GuiInputHandler(this, interactableProvider(this), interactionProviderCreator(this))

  private val mouseClickDisplayPanel = MouseClickDisplayPanel(parentDisposable = this)

  private val progressPanel =
    SurfaceProgressPanel(parentDisposable = this, ::useSmallProgressIcon).apply {
      name = "Layout Editor Progress Panel"
    }

  private val zoomControlsLayerPane: JPanel? =
    if (zoomControlsPolicy != ZoomControlsPolicy.HIDDEN)
      JPanel().apply {
        border = JBUI.Borders.empty(UIUtil.getScrollBarWidth())
        isOpaque = false
        layout = BorderLayout()
        setFocusable(false)
        if (zoomControlsPolicy == ZoomControlsPolicy.AUTO_HIDE) isVisible = false
      }
    else null

  private val progressIndicators: MutableSet<ProgressIndicator> = HashSet()

  protected val sceneViewPanel =
    SceneViewPanel(
        sceneViewProvider = ::sceneViews,
        interactionLayersProvider = ::getLayers,
        actionManagerProvider = ::actionManager,
        shouldRenderErrorsPanel = ::shouldRenderErrorsPanel,
        layoutManager = positionableLayoutManager,
      )
      .apply {
        Disposer.register(this@DesignSurface, this)
        background = this@DesignSurface.background
        if (hasZoomControls) alignmentX = CENTER_ALIGNMENT
      }

  /** [JScrollPane] contained in this [DesignSurface] when zooming is enabled. */
  val scrollPane: JScrollPane? =
    if (hasZoomControls) {
      createDefaultScrollPane(sceneViewPanel, background) { notifyPanningChanged() }
        .apply {
          addComponentListener(
            object : ComponentAdapter() {
              override fun componentResized(e: ComponentEvent) {
                // Relayout the PositionableContents when visible size (e.g. window size) of
                // ScrollPane is changed.
                revalidateScrollArea()
              }
            }
          )
        }
    } else null

  /** Current scrollable [Rectangle] if available or null. */
  val currentScrollRectangle: Rectangle?
    get() = scrollPane?.viewport?.let { Rectangle(it.viewPosition, it.size) }

  private fun getLayers() = guiInputHandler.layers

  val actionManager: ActionManager<out DesignSurface<T>> =
    actionManagerProvider(this).apply { registerActionsShortcuts(layeredPane) }

  val viewport: DesignSurfaceViewport =
    if (scrollPane != null) ScrollableDesignSurfaceViewport(scrollPane.viewport)
    else NonScrollableDesignSurfaceViewport(viewport = this)

  /**
   * Component that wraps the displayed content. If this is a scrollable surface, that will be the
   * Scroll Pane. Otherwise, it will be the ScreenViewPanel container.
   */
  private val contentContainerPane: JComponent = scrollPane ?: sceneViewPanel

  /** Returns the size of the surface scroll viewport. */
  @get:SwingCoordinate
  val extentSize: Dimension
    get() = viewport.extentSize

  /** Returns the size of the surface containing the ScreenViews. */
  @get:SwingCoordinate
  val viewSize: Dimension
    get() = viewport.viewSize

  val interactionPane: JComponent
    get() = sceneViewPanel

  val preferredFocusedComponent: JComponent
    get() = interactionPane

  private val onHoverListener: AWTEventListener =
    if (zoomControlsLayerPane != null && zoomControlsPolicy == ZoomControlsPolicy.AUTO_HIDE) {
      createZoomControlAutoHiddenListener(
          zoomControlPaneOwner = this,
          zoomControlComponent = zoomControlsLayerPane,
        )
        .apply {
          Toolkit.getDefaultToolkit().addAWTEventListener(this@apply, AWTEvent.MOUSE_EVENT_MASK)
        }
    } else AWTEventListener {}

  /**
   * Enables the mouse click display. If enabled, the clicks of the user are displayed in the
   * surface.
   */
  fun enableMouseClickDisplay() {
    mouseClickDisplayPanel.isEnabled = true
  }

  /** Disables the mouse click display. */
  fun disableMouseClickDisplay() {
    mouseClickDisplayPanel.isEnabled = false
  }

  /** Sets the tooltip for the design surface */
  fun setDesignToolTip(text: String?) {
    sceneViewPanel.setToolTipText(text)
  }

  /**
   * Asks the [ScreenView]s contained in this [DesignSurface] for a re-layouts. The re-layout will
   * not happen immediately in this call.
   */
  @UiThread
  fun revalidateScrollArea() {
    // Mark the scene view panel as invalid to force a revalidation when the scroll pane is
    // revalidated.
    sceneViewPanel.invalidate()
    // Schedule a layout for later.
    contentContainerPane.revalidate()
    // Also schedule a repaint.
    sceneViewPanel.repaint()
  }

  /** Re-layouts the ScreenViews contained in this design surface immediately. */
  @UiThread
  fun validateScrollArea() {
    // Mark both the sceneview panel and the scroll pane as invalid to force a relayout.
    sceneViewPanel.invalidate()
    contentContainerPane.invalidate()
    // Validate the scroll pane immediately and layout components.
    contentContainerPane.validate()
    sceneViewPanel.repaint()
  }

  /** Converts a given point that is in view coordinates to viewport coordinates. */
  @TestOnly
  fun getCoordinatesOnViewportForTest(viewCoordinates: Point): Point {
    return SwingUtilities.convertPoint(
      sceneViewPanel,
      viewCoordinates.x,
      viewCoordinates.y,
      viewport.viewportComponent,
    )
  }

  fun registerIndicator(indicator: ProgressIndicator) {
    if (project.isDisposed || Disposer.isDisposed(this)) {
      return
    }
    synchronized(progressIndicators) {
      if (progressIndicators.add(indicator)) {
        progressPanel.showProgressIcon()
      }
    }
  }

  fun unregisterIndicator(indicator: ProgressIndicator) {
    synchronized(progressIndicators) {
      progressIndicators.remove(indicator)
      if (progressIndicators.isEmpty()) {
        progressPanel.hideProgressIcon()
      }
    }
  }

  var isActive: Boolean = false
    private set

  /** The editor has been activated */
  open fun activate() {
    if (Disposer.isDisposed(this)) {
      // Prevent activating a disposed surface.
      return
    }

    if (!isActive) {
      for (manager in sceneManagers) {
        manager.activate(this)
      }
      if (zoomControlsPolicy == ZoomControlsPolicy.AUTO_HIDE) {
        Toolkit.getDefaultToolkit().addAWTEventListener(onHoverListener, AWTEvent.MOUSE_EVENT_MASK)
      }
    }
    isActive = true
    issueModel.activate()
  }

  open fun deactivate() {
    if (isActive) {
      Toolkit.getDefaultToolkit().removeAWTEventListener(onHoverListener)
      for (manager in sceneManagers) {
        manager.deactivate(this)
      }
    }
    isActive = false
    issueModel.deactivate()

    guiInputHandler.cancelInteraction()
  }

  init {
    isOpaque = true
    isFocusable = false

    // TODO: Do this as part of the layout/validate operation instead
    addComponentListener(
      object : ComponentAdapter() {
        override fun componentResized(componentEvent: ComponentEvent) {
          if (componentEvent.id == ComponentEvent.COMPONENT_RESIZED) {
            if (
              readyToRestoreZoomMask.get() != RESTORE_ZOOM_DONE_INT_MASK &&
                isShowing &&
                width > 0 &&
                height > 0
            ) {
              val hasModelAttached = checkIfReadyToRestoreZoom(NOTIFY_COMPONENT_RESIZED_INT_MASK)
              if (!hasModelAttached) {
                // No model is attached, ignore the setup of initial zoom level.
                return
              }
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

  /** Calls [repaint] with delay. */
  private val repaintTimer = Timer(15) { repaint() }

  /** Call this to generate repaints */
  fun needsRepaint() {
    if (!repaintTimer.isRunning) {
      repaintTimer.isRepeats = false
      repaintTimer.start()
    }
  }

  private val modelListener: ModelListener =
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

  @GuardedBy("listenersLock") private val listeners = mutableListOf<DesignSurfaceListener>()

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

  private fun clearListeners() {
    listenersLock.withLock {
      listeners.clear()
      zoomListeners.clear()
    }
  }

  /**
   * Gets a copy of [zoomListeners] under a lock. Use this method instead of accessing the listeners
   * directly.
   */
  private fun getZoomListeners(): ImmutableList<PanZoomListener> {
    listenersLock.withLock {
      return ImmutableList.copyOf(zoomListeners)
    }
  }

  /**
   * Gets a copy of [listeners] under a lock. Use this method instead of accessing the listeners
   * directly.
   */
  private fun getSurfaceListeners(): ImmutableList<DesignSurfaceListener> {
    listenersLock.withLock {
      return ImmutableList.copyOf(listeners)
    }
  }

  private fun notifyModelsChanged(models: List<NlModel?>) {
    val listeners = getSurfaceListeners()
    for (listener in listeners) {
      runInEdt { listener.modelsChanged(this, models) }
    }
  }

  private fun notifySelectionChanged(newSelection: List<NlComponent>) {
    val listeners = getSurfaceListeners()
    for (listener in listeners) {
      listener.componentSelectionChanged(this, newSelection)
    }
  }

  /**
   * A bitwise mask used by [notifyRestoreZoom]. If the "or" operator applied to this mask gets a
   * bitwise values of [NOTIFY_RESTORE_ZOOM_INT_MASK], [NOTIFY_COMPONENT_RESIZED_INT_MASK] we can
   * restore the zoom.
   */
  private val readyToRestoreZoomMask = AtomicInteger(0)

  /**
   * Notify to [DesignSurface] that we can now try to restore the zoom or apply zoom to fit if no
   * zoom has been stored before.
   *
   * This is used for when we need to wait for events external to [DesignSurface] (such as Rendering
   * of the content) before trying to restore the zoom.
   *
   * Note: if [waitForRenderBeforeRestoringZoom] flag is enabled, it waits [DesignSurface] to be
   * resized before restoring the zoom.
   */
  @UiThread
  fun notifyRestoreZoom() {
    checkIfReadyToRestoreZoom(NOTIFY_RESTORE_ZOOM_INT_MASK)
  }

  /**
   * Resets the bitwise mask responsible to check weather restoring zoom or waiting for
   * [notifyRestoreZoom]. Resetting will allow DesignSurface to call
   * [waitForRenderBeforeRestoringZoom] as if it happens for the first time.
   *
   * This is useful when we switch modes or layouts.
   *
   * Note: if [waitForRenderBeforeRestoringZoom] is enabledit will wait [notifyRestoreZoom] to be
   * performed at least once before trying to restore the zoom.
   */
  fun resetRestoreZoomNotifier() {
    if (readyToRestoreZoomMask.get() == RESTORE_ZOOM_DONE_INT_MASK && height > 0 && width > 0) {
      // If we have performed already the first [DesignSurface.waitForRenderBeforeRestoringZoom]
      // we can just set the bitwise map with the NOTIFY_RESTORE_ZOOM_INT_MASK flag.
      readyToRestoreZoomMask.set(NOTIFY_RESTORE_ZOOM_INT_MASK)
    } else {
      readyToRestoreZoomMask.set(0)
    }
  }

  @TestOnly
  fun notifyComponentResizedForTest() {
    checkIfReadyToRestoreZoom(NOTIFY_COMPONENT_RESIZED_INT_MASK)
  }

  /**
   * Synchronized function that checks if we can call [restoreZoomOrZoomToFit]. We can call it only
   * when [bitwiseNumber] has received both "1" and "2". This function solves a race condition of
   * when the sizes of the content to show and the sizes of [DesignSurface] aren't yet synchronized
   * causing a wrong fitScale value.
   *
   * Note: if [waitForRenderBeforeRestoringZoom] is enabled it will wait
   * DesignSurface.notifyRestoreZoom() to be performed at least once. if
   * [waitForRenderBeforeRestoringZoom] is disabled it will directly perform
   * restoreZoomOrZoomToFit()
   */
  @UiThread
  private fun checkIfReadyToRestoreZoom(bitwiseNumber: Int): Boolean {
    val newMask =
      readyToRestoreZoomMask.updateAndGet {
        if (it == expectedRestoreZoomMask || it == RESTORE_ZOOM_DONE_INT_MASK) {
          RESTORE_ZOOM_DONE_INT_MASK
        } else {
          it or bitwiseNumber
        }
      }
    if (newMask == expectedRestoreZoomMask) {
      return restoreZoomOrZoomToFit()
    }

    return false
  }

  override fun onScaleChange(update: ScaleChange) {
    if (update.isAnimating) {
      revalidateScrollArea()
      return
    }
    if (update.shouldStoreScale) {
      models.firstOrNull()?.let { storeCurrentScale(it) }
    } else {
      // If we shouldn't store the scale we remove the existing one.
      models.firstOrNull()?.let {
        val state = getInstance(project).surfaceState
        state.saveFileScale(project, it.virtualFile, null)
      }
    }
    revalidateScrollArea()
    notifyScaleChanged(update.previousScale, update.newScale)
  }

  /** Save the current zoom level from the file of the given [NlModel]. */
  private fun storeCurrentScale(model: NlModel) {
    if (!isKeepingScaleWhenReopen()) {
      return
    }
    val state = getInstance(project).surfaceState
    // TODO Maybe have a reference to virtualFile directly instead of from NlModel
    state.saveFileScale(project, model.virtualFile, zoomController)
  }

  private fun notifyScaleChanged(previousScale: Double, newScale: Double) {
    for (listener in getZoomListeners()) {
      listener.zoomChanged(previousScale, newScale)
    }
  }

  private fun notifyPanningChanged() {
    for (listener in getZoomListeners()) {
      listener.panningChanged()
    }
  }

  /**
   * @param x the x coordinate of the double click converted to pixels in the Android coordinate
   *   system
   * @param y the y coordinate of the double click converted to pixels in the Android coordinate
   *   system
   */
  open fun notifyComponentActivate(
    component: NlComponent,
    @AndroidCoordinate x: Int,
    @AndroidCoordinate y: Int,
  ) {
    notifyComponentActivate(component)
  }

  open fun notifyComponentActivate(component: NlComponent) {}

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

  /**
   * Scroll to the center of a list of given components. Usually the center of the area containing
   * these elements.
   */
  abstract fun scrollToCenter(list: List<NlComponent>)

  /**
   * The offsets to the left and top edges when scrolling to a component by calling
   * [scrollToVisible].
   */
  @get:SwingCoordinate protected abstract val scrollToVisibleOffset: Dimension

  /**
   * Ensures that the given model is visible in the surface by scrolling to it if needed. If the
   * [SceneView] is partially visible and [forceScroll] is set to `false`, no scroll will happen.
   */
  fun scrollToVisible(sceneView: SceneView, forceScroll: Boolean) {
    sceneViewPanel.findSceneViewRectangle(sceneView)?.let { sceneViewRectangle ->
      if (forceScroll || !viewport.viewRect.intersects(sceneViewRectangle)) {
        val offset = scrollToVisibleOffset
        setScrollPosition(sceneViewRectangle.x - offset.width, sceneViewRectangle.y - offset.height)
      }
    }
  }

  /**
   * Ensures that the given model is visible in the surface by scrolling to it if needed. If the
   * [NlModel] is partially visible and [forceScroll] is set to false, no scroll will happen.
   */
  fun scrollToVisible(model: NlModel, forceScroll: Boolean) {
    sceneViews
      .firstOrNull { it.sceneManager.model == model }
      ?.let { view -> scrollToVisible(view, forceScroll) }
  }

  /**
   * Given a rectangle relative to a sceneView, find its absolute coordinates and then scroll to
   * center such rectangle. See [scrollToCenter]
   *
   * @param sceneView the [SceneView] that contains the given rectangle.
   * @param rectangle the rectangle that should be visible, with its coordinates relative to the
   *   sceneView.
   */
  protected fun scrollToCenter(sceneView: SceneView, @SwingCoordinate rectangle: Rectangle) {
    val availableSpace = viewport.extentSize
    sceneViewPanel.findMeasuredSceneViewRectangle(sceneView, availableSpace)?.let {
      sceneViewRectangle ->
      val topLeftCorner =
        Point(sceneViewRectangle.x + rectangle.x, sceneViewRectangle.y + rectangle.y)
      scrollToCenter(Rectangle(topLeftCorner, rectangle.size))
    }
  }

  /**
   * Move the scroll position to make the given rectangle visible and centered. If the given
   * rectangle is too big for the available space, it will be centered anyway and some of its
   * borders will probably not be visible at the new scroll position.
   *
   * @param rectangle the rectangle that should be centered.
   */
  private fun scrollToCenter(@SwingCoordinate rectangle: Rectangle) {
    val availableSpace = viewport.extentSize
    val extraW = availableSpace.width - rectangle.width
    val extraH = availableSpace.height - rectangle.height
    setScrollPosition(rectangle.x - (extraW + 1) / 2, rectangle.y - (extraH + 1) / 2)
  }

  @TestOnly
  fun setScrollViewSizeAndValidateForTest(
    @SwingCoordinate width: Int,
    @SwingCoordinate height: Int,
  ) {
    scrollPane?.let {
      it.setSize(width, height)
      it.doLayout()
      UIUtil.invokeAndWaitIfNeeded { validateScrollArea() }
    }
  }

  protected open fun isKeepingScaleWhenReopen(): Boolean {
    return true
  }

  /**
   * Restore the zoom level if it can be loaded from persistent settings, otherwise zoom-to-fit.
   *
   * @return whether zoom-to-fit or zoom restore has happened, which won't happen if there is no
   *   model.
   */
  @UiThread
  private fun restoreZoomOrZoomToFit(): Boolean {
    val model = model ?: return false
    if (!restorePreviousScale(model)) {
      zoomController.zoomToFit()
    }
    return true
  }

  /**
   * Apply zoom to fit if there is a stored zoom in the persistent settings, it does nothing
   * otherwise.
   *
   * Because zoom to fit scale gets calculated whenever [DesignSurface] changes its space or number
   * of items, this function clear-up the stored zoom and replace it with the newly calculated zoom
   * to fit value.
   */
  fun zoomToFitIfStorageNotEmpty() {
    if (isZoomStored()) {
      zoomController.zoomToFit()
    }
  }

  /**
   * Checks if there is zoom level stored from persistent settings.
   *
   * @return true if persistent settings contains a stored zoom, false otherwise.
   */
  private fun isZoomStored(): Boolean {
    val model = model ?: return false
    return getInstance(model.project)
      .surfaceState
      .loadFileScale(project, model.virtualFile, zoomController) != null
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

  private val modelToSceneManagersLock = ReentrantReadWriteLock()

  @GuardedBy("modelToSceneManagersLock")
  private val modelToSceneManagers = LinkedHashMap<NlModel, T>()

  /** Filter got [sceneManagers] to avoid returning disposed elements */
  private val filterDisposedSceneManagers =
    Predicate<T> { input: T? -> input != null && FILTER_DISPOSED_MODELS.apply(input.model) }

  @Slow
  /** Some implementations might be slow */
  protected abstract fun createSceneManager(model: NlModel): T

  /**
   * @return the primary (first) [NlModel] if exist. null otherwise.
   * @see [models]
   */
  @Deprecated("b/352512443 The surface can contain multiple models. Use models instead.")
  val model: NlModel?
    get() = models.firstOrNull()

  /** @return the list of added non-disposed [NlModel]s. */
  val models: ImmutableList<NlModel>
    get() {
      modelToSceneManagersLock.readLock().withLock {
        return ImmutableList.copyOf(Sets.filter(modelToSceneManagers.keys, FILTER_DISPOSED_MODELS))
      }
    }

  /** @return the list of all non-disposed [SceneManager]s */
  val sceneManagers: ImmutableList<T>
    get() {
      modelToSceneManagersLock.readLock().withLock {
        return ImmutableList.copyOf(
          Collections2.filter(modelToSceneManagers.values, filterDisposedSceneManagers)
        )
      }
    }

  /** @return The [SceneManager] associated to the given [NlModel]. */
  fun getSceneManager(model: NlModel): T? {
    if (model.module.isDisposed) {
      return null
    }
    modelToSceneManagersLock.readLock().withLock {
      return modelToSceneManagers.get(model)
    }
  }

  /**
   * Add an [NlModel] to DesignSurface and return the created [SceneManager]. If it is added before
   * then it just returns the associated [SceneManager] which was created before. The [NlModel] will
   * be moved to the last position which might affect rendering.
   *
   * @param model the added [NlModel]
   */
  @Slow
  private fun addModel(model: NlModel): T {
    var manager = getSceneManager(model)
    manager?.let {
      modelToSceneManagersLock.writeLock().withLock {
        // No need to add same model twice. We just move it to the bottom of the model list since
        // order is important.
        val managerToMove: T? = modelToSceneManagers.remove(model)
        if (managerToMove != null) {
          modelToSceneManagers[model] = managerToMove
        }
        return it
      }
    }

    model.addListener(modelListener)
    // SceneManager creation is a slow operation. Multiple can happen in parallel.
    // We optimistically create a new scene manager for the given model and then, with the mapping
    // locked we checked if a different one has been added.
    val newManager = createSceneManager(model)

    modelToSceneManagersLock.writeLock().withLock {
      manager = modelToSceneManagers.putIfAbsent(model, newManager)
      if (manager == null) {
        // The new SceneManager was correctly added
        manager = newManager
      }
    }

    if (manager !== newManager) {
      // There was already a manager assigned to the model so discard this one.
      Disposer.dispose(newManager)
    }
    if (isActive) {
      manager?.activate(this)
    }
    return manager!!
  }

  /**
   * Remove an [NlModel] from DesignSurface. If it isn't added before then nothing happens.
   *
   * @param model the [NlModel] to remove
   */
  fun removeModel(model: NlModel) {
    if (!removeModelImpl(model)) {
      return
    }

    reactivateGuiInputHandler()
  }

  /**
   * Remove an [NlModel] from DesignSurface. If it had not been added before then nothing happens.
   *
   * @param model the [NlModel] to remove
   * @return true if the model existed and was removed
   */
  private fun removeModelImpl(model: NlModel): Boolean {
    val manager: SceneManager?
    modelToSceneManagersLock.writeLock().withLock { manager = modelToSceneManagers.remove(model) }
    // Mark the scene view panel as invalid to force the scene views to be updated
    sceneViewPanel.removeSceneViewForModel(model)

    if (manager == null) {
      return false
    }

    model.deactivate(this)
    model.removeListener(modelListener)
    Disposer.dispose(manager)
    UIUtil.invokeLaterIfNeeded { this.revalidateScrollArea() }
    return true
  }

  override val focusedSceneView: SceneView?
    get() {
      val managers = sceneManagers
      if (managers.size == 1) {
        // Always return primary SceneView In single-model mode,
        val manager: T = model?.let { getSceneManager(it) } ?: return null
        return manager.sceneViews.firstOrNull()
      }
      val selection = selectionModel.selection
      if (selection.isNotEmpty()) {
        val primary = selection[0]
        val manager: T? = getSceneManager(primary.model)
        return manager?.sceneViews?.firstOrNull()
      }
      return null
    }

  /** Returns the list of [SceneView]s attached to this [DesignSurface]. */
  val sceneViews: ImmutableCollection<SceneView>
    get() {
      return sceneManagers
        .stream()
        .flatMap { sceneManager: T -> sceneManager.sceneViews.stream() }
        .collect(ImmutableList.toImmutableList())
    }

  @Deprecated("b/352512443 Owner can have multiple scenes")
  override val scene: Scene?
    get() = model?.let { getSceneManager(it) }?.scene

  override fun getSceneViewAt(@SwingCoordinate x: Int, @SwingCoordinate y: Int): SceneView? {
    val sceneViews = sceneViews
    val scaledSize = Dimension()
    for (view in sceneViews) {
      view.getScaledContentSize(scaledSize)
      if (
        (view.x <= x && x <= view.x + scaledSize.width && view.y <= y) &&
          y <= (view.y + scaledSize.height)
      ) {
        return view
      }
    }
    return null
  }

  @Deprecated("b/352512443")
  override fun getSceneViewAtOrPrimary(
    @SwingCoordinate x: Int,
    @SwingCoordinate y: Int,
  ): SceneView? {
    // TODO: For keeping the behaviour as before in multi-model case, we return primary SceneView
    // when there is no hovered SceneView.
    return getSceneViewAt(x, y) ?: model?.let { getSceneManager(it) }?.sceneViews?.firstOrNull()
  }

  /**
   * Returns the [SceneView] under the mouse cursor if the mouse is within the coordinates of this
   * surface or null otherwise.
   */
  val sceneViewAtMousePosition: SceneView?
    get() {
      val mouseLocation =
        if (!GraphicsEnvironment.isHeadless()) MouseInfo.getPointerInfo().location else null
      if (mouseLocation == null || contains(mouseLocation) || !isVisible || !isEnabled) {
        return null
      }

      SwingUtilities.convertPointFromScreen(mouseLocation, sceneViewPanel)
      return getSceneViewAt(mouseLocation.x, mouseLocation.y)
    }

  override fun onHover(@SwingCoordinate x: Int, @SwingCoordinate y: Int) {
    sceneViews.forEach { it.onHover(x, y) }
  }

  val layoutType: DesignerEditorFileType
    get() = model?.type ?: DefaultDesignerFileType

  /**
   * @return true if the content is editable (e.g. move position or drag-and-drop), false otherwise.
   */
  val isEditable: Boolean
    get() = layoutType.isEditable()

  override val configurations: ImmutableCollection<Configuration>
    get() = models.stream().map(NlModel::configuration).collect(ImmutableList.toImmutableList())

  /**
   * Update the status of [GuiInputHandler]. It will start or stop listening depending on the
   * current layout type.
   */
  private fun reactivateGuiInputHandler() {
    if (isEditable) {
      guiInputHandler.startListening()
    } else {
      guiInputHandler.stopListening()
    }
  }

  /** Support for panning actions. */
  val pannable =
    object : Pannable {
      override var isPanning: Boolean
        get() = guiInputHandler.isPanning
        set(value) {
          guiInputHandler.isPanning = value
        }

      override val isPannable: Boolean
        get() = true

      /**
       * Sets the offset for the scroll viewer to the specified x and y values The offset will never
       * be less than zero, and never greater that the maximum value allowed by the sizes of the
       * underlying view and the extent. If the zoom factor is large enough that a scroll bars isn't
       * visible, the position will be set to zero.
       */
      @set:SwingCoordinate
      @get:SwingCoordinate
      override var scrollPosition: Point
        get() = viewport.viewPosition
        set(value) {
          value.setLocation(max(0.0, value.x.toDouble()), max(0.0, value.y.toDouble()))

          val extent: Dimension = viewport.extentSize
          val view: Dimension = viewport.viewSize

          val minX = min(value.x.toDouble(), (view.width - extent.width).toDouble()).toInt()
          val minY = min(value.y.toDouble(), (view.height - extent.height).toDouble()).toInt()

          value.setLocation(minX, minY)

          viewport.viewPosition = value
        }
    }

  fun setScrollPosition(@SwingCoordinate x: Int, @SwingCoordinate y: Int) {
    pannable.scrollPosition = Point(x, y)
  }

  /**
   * This is called before [setModel]. After the returned future completes, we'll wait for smart
   * mode and then invoke [setModel]. If a [DesignSurface] needs to do any extra work before the
   * model is set it should be done here.
   */
  open fun goingToSetModel(model: NlModel?): CompletableFuture<*> {
    return CompletableFuture.completedFuture<Any?>(null)
  }

  /**
   * Sets the current [NlModel] to [DesignSurface].
   *
   * @see [removeModel]
   */
  open fun setModel(newModel: NlModel?) {
    val oldModel = model
    if (newModel === oldModel) {
      return
    }

    if (oldModel != null) {
      removeModelImpl(oldModel)
    }

    if (newModel == null) {
      return
    }

    scope.launch {
      addModel(newModel)
      sceneManagers.forEach { it.requestRenderAndWait() }
      // Mark the scene view panel as invalid to force the scene views to be updated
      sceneViewPanel.invalidate()

      reactivateGuiInputHandler()
      withContext(uiThread) {
        restoreZoomOrZoomToFit()
        revalidateScrollArea()
      }

      notifyModelsChanged(listOf(newModel))
    }
  }

  /**
   * Add an [NlModel] to DesignSurface and return the created [SceneManager]. If it is added before
   * then it just returns the associated [SceneManager] which created before. In this function, the
   * scene views are not updated and [DesignSurfaceListener.modelsChanged] callback is triggered
   * immediately.
   *
   * Note that the order of the addition might be important for the rendering order.
   * [PositionableContentLayoutManager] will receive the models in the order they are added.
   *
   * @param model the added [NlModel]
   * @see [addModel]
   */
  fun addModelWithoutRender(modelToAdd: NlModel): CompletableFuture<T> {
    return CompletableFuture.supplyAsync(
        { addModel(modelToAdd) },
        AppExecutorUtil.getAppExecutorService(),
      )
      .whenCompleteAsync(
        { _, _ ->
          if (project.isDisposed || modelToAdd.isDisposed) return@whenCompleteAsync
          notifyModelsChanged(listOf(modelToAdd))
          reactivateGuiInputHandler()
        },
        EdtExecutorService.getInstance(),
      )
  }

  /**
   * Bulk version of [addModelWithoutRender].
   *
   * This method is expected to be called in the background thread, and it will schedule the
   * corresponding call to [DesignSurfaceListener.modelsChanged] in EDT for later.
   */
  @RequiresBackgroundThread
  fun addModelsWithoutRender(models: List<NlModel>): List<T> {
    val sceneManagers = models.map { addModel(it) }
    notifyModelsChanged(models)
    reactivateGuiInputHandler()
    return sceneManagers
  }

  private var lintIssueProvider: LintIssueProvider? = null
  val issueModel: IssueModel = IssueModel(this, project)

  fun deactivateIssueModel() {
    issueModel.deactivate()
  }

  fun setLintAnnotationsModel(model: LintAnnotationsModel) {
    lintIssueProvider?.let { it.lintAnnotationsModel = model }

    if (lintIssueProvider == null) {
      lintIssueProvider = LintIssueProvider(model).also { issueModel.addIssueProvider(it) }
    }
  }

  override fun updateUI() {
    super.updateUI()
    if (modelToSceneManagers != null) {
      // updateUI() is called in the parent constructor, at that time all class member in this class
      // has not initialized.
      for (manager in sceneManagers) {
        manager.sceneViews.forEach(Consumer { obj: SceneView -> obj.updateUI() })
      }
    }
  }

  override fun setBackground(bg: Color?) {
    super.setBackground(bg)
    // setBackground is called before the class initialization is complete so we do the null
    // checking to prevent calling mySceneViewPanel
    // before the constructor has completed. At that point mySceneViewPanel might still be null.
    if (sceneViewPanel != null) {
      sceneViewPanel.setBackground(bg)
    }
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink[DESIGN_SURFACE] = this
    sink[GuiInputHandler.CURSOR_RECEIVER] = this

    sink[PANNABLE_KEY] = pannable
    sink[ZOOMABLE_KEY] = zoomController
    sink[CONFIGURATIONS] = configurations

    val handler: DesignSurfaceActionHandler = actionHandlerProvider(this)
    sink[PlatformDataKeys.DELETE_ELEMENT_PROVIDER] = handler
    sink[PlatformDataKeys.CUT_PROVIDER] = handler
    sink[PlatformDataKeys.COPY_PROVIDER] = handler
    sink[PlatformDataKeys.PASTE_PROVIDER] = handler

    sink[PlatformCoreDataKeys.FILE_EDITOR] = fileEditorDelegate

    fun getMenuPoint(): Point? {
      val view = focusedSceneView ?: return null
      val selection = selectionModel.primary ?: return null
      val sceneComponent = scene?.getSceneComponent(selection) ?: return null
      return Point(
        Coordinates.getSwingXDip(view, sceneComponent.centerX),
        Coordinates.getSwingYDip(view, sceneComponent.centerY),
      )
    }
    sink[PlatformDataKeys.CONTEXT_MENU_POINT] = getMenuPoint()
    sink[PlatformDataKeys.MODULE] = model?.module

    sink.lazy(CommonDataKeys.PSI_ELEMENT) {
      focusedSceneView?.selectionModel?.primary?.tagDeprecated
    }
    sink.lazy(LangDataKeys.PSI_ELEMENT_ARRAY) {
      focusedSceneView
        ?.selectionModel
        ?.selection
        ?.map { it.tagDeprecated }
        ?.toArray<XmlTag>(XmlTag.EMPTY)
    }
  }

  override fun dispose() {
    clearListeners()
    guiInputHandler.stopListening()
    Toolkit.getDefaultToolkit().removeAWTEventListener(onHoverListener)
    if (repaintTimer.isRunning) {
      repaintTimer.stop()
    }
    models.forEach { removeModelImpl(it) }
  }

  init {
    guiInputHandler.startListening()
    add(layeredPane)
    zoomControlsLayerPane?.add(actionManager.designSurfaceToolbar, BorderLayout.EAST)
    layeredPane.apply {
      add(progressPanel, LAYER_PROGRESS)
      add(mouseClickDisplayPanel, LAYER_MOUSE_CLICK)
      zoomControlsLayerPane?.let { add(it) }
      setLayer(zoomControlsLayerPane, JLayeredPane.DRAG_LAYER)
      if (scrollPane != null) {
        layout = MatchParentLayoutManager()
        add(scrollPane)
        setLayer(zoomControlsLayerPane, JLayeredPane.POPUP_LAYER)
      } else {
        layout = OverlayLayout(this@apply)
        add(sceneViewPanel)
        setLayer(zoomControlsLayerPane, JLayeredPane.POPUP_LAYER)
      }
    }
  }

  final override fun add(comp: Component?): Component {
    return super.add(comp)
  }
}
