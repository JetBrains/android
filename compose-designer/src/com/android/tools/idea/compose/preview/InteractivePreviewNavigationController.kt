/*
 * Copyright (C) 2026 The Android Open Source Project
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

import com.android.annotations.concurrency.UiThread
import com.android.tools.adtui.compose.StudioComposePanel
import com.android.tools.environment.Logger
import com.android.tools.idea.compose.preview.interactive.NavigationControlsContent
import com.android.tools.idea.preview.analytics.InteractivePreviewUsageTracker
import com.android.tools.preview.ComposePreviewElementInstance
import com.intellij.openapi.actionSystem.DataKey
import java.lang.reflect.Method
import javax.swing.JComponent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Enum representing the edge from which a back navigation gesture can be initiated. */
enum class BackNavigationEdge(val visibleName: String) {
  /** Represents a back gesture initiated from the left edge of the screen. */
  EDGE_LEFT("Swipe Left"),

  /** Represents a back gesture initiated from the right edge of the screen. */
  EDGE_RIGHT("Swipe Right"),

  /** Represents no specific edge for the back gesture. */
  EDGE_NONE("None"),
}

/**
 * Controller for showing and hiding the navigation panel when [Interactive] mode is enabled.
 *
 * @param onAfterPanelUpdate A callback invoked immediately after the controller's visibility state changes (i.e., after a show/hide call).
 */
class InteractivePreviewNavigationController(
  private val usageTrackerProvider: () -> InteractivePreviewUsageTracker,
  private val onAfterPanelUpdate: () -> Unit = {},
  fpsUpdater: SharedFlow<Unit>,
) {

  private val _backPressCompletedFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  val backPressCompletedFlow = _backPressCompletedFlow.asSharedFlow()

  var isBackGestureInProgress: Boolean = false
    private set

  private val showNavigationControlsProvider = {
    StudioComposePanel { NavigationControlsContent(interactivePreviewNavigationController = this, fpsUpdater = fpsUpdater) }
  }

  /** The currently active [JComponent] for back navigation controls, or null if controls are hidden. */
  private var activeBackNavigationPanelInInteractiveMode: JComponent? = null
  private var backPressDispatcherOwner: Any? = null

  private var canBackPressMethod: Method? = null
  private var onBackPressStartedMethod: Method? = null
  private var onBackPressProgressMethod: Method? = null
  private var onBackPressCompletedMethod: Method? = null
  private var onBackPressCancelledMethod: Method? = null

  private val logger = Logger.getInstance(InteractivePreviewNavigationController::class.java)

  /**
   * Updates the objects needed to resolve the back press dispatcher and resets cached reflection methods.
   *
   * NOTE: This method should be called whenever the underlying navigation dispatcher owner might have changed. To optimize efficiency, this
   * method only resolves the [backPressDispatcherOwner] and resets the cached [Method] properties to null. Reflection is then used lazily
   * to resolve each method only when it is first needed.
   *
   * @param currentNavigationEventDispatcherOwnerObj The local object [LocalNavigationEventDispatcherOwner] loaded from
   *   [LocalNavigationEventTransform].
   * @param currentComposeViewAdapterObj The object of the actual `androidx.compose.ui.tooling.ComposeViewAdapter`.
   */
  fun updateObjects(currentNavigationEventDispatcherOwnerObj: Any?, currentComposeViewAdapterObj: Any) {
    backPressDispatcherOwner = getBackPressDispatcherOwner(currentNavigationEventDispatcherOwnerObj, currentComposeViewAdapterObj)

    isBackGestureInProgress = false

    // Reset the cached values
    canBackPressMethod = null
    onBackPressStartedMethod = null
    onBackPressProgressMethod = null
    onBackPressCompletedMethod = null
    onBackPressCancelledMethod = null
  }

  /** Loads the dispatcher owner field used to perform back navigation when using androidx.navigation3. */
  private fun getBackPressDispatcherOwner(navigationEventDispatcherOwnerObj: Any?, composeViewAdapterObj: Any?): Any? {
    // When [navigationEventDispatcherOwnerObj] is null it means we haven't loaded any object from
    // [FakeNavigationEventDispatcherOwnerLoader].
    // This means that we should find a FakeOnBackPressedDispatcherOwner within the ComposeViewAdapter object.
    return navigationEventDispatcherOwnerObj
      ?: composeViewAdapterObj?.let { obj ->
        obj::class
          .java
          .declaredFields
          .singleOrNull { it.name == "FakeOnBackPressedDispatcherOwner" }
          .also { it?.isAccessible = true }
          ?.get(obj)
      }
  }

  private fun Any?.findMethod(methodName: String): Method? =
    this?.let {
      it::class
        .java
        .declaredMethods
        .singleOrNull { method -> method.name == methodName }
        .also { method ->
          if (method == null) {
            logger.debug("Could not find method $methodName via reflection.")
          }
        }
    }

  /**
   * Checks if there are views in the navigation stacks where we can navigate back.
   *
   * @return true if there are views in the back navigation stack and a [backPressCompleted] can be performed, false otherwise.
   */
  fun canBackPress(): Boolean {
    val resolvedMethod = canBackPressMethod ?: backPressDispatcherOwner.findMethod(CAN_BACK_PRESS).also { canBackPressMethod = it }
    return resolvedMethod?.invoke(backPressDispatcherOwner) as? Boolean ?: false
  }

  /**
   * Simulates the start of an interactive back gesture.
   *
   * @param edge The [BackNavigationEdge] of the device on which the progress is performed.
   */
  fun backPressStart(edge: BackNavigationEdge = BackNavigationEdge.EDGE_LEFT) {
    isBackGestureInProgress = true
    val resolvedMethod =
      onBackPressStartedMethod ?: backPressDispatcherOwner.findMethod(ON_BACK_PRESS_STARTED).also { onBackPressStartedMethod = it }
    resolvedMethod?.invoke(backPressDispatcherOwner, edge.name)
  }

  /**
   * Simulates the progress of an interactive back gesture.
   *
   * @param progress The progress of the gesture, from 0.0 to 1.0.
   * @param edge The [BackNavigationEdge] of the device on which the progress is performed.
   */
  fun backPressProgress(progress: Float, edge: BackNavigationEdge = BackNavigationEdge.EDGE_LEFT) {
    // If we move progress back, to 0f we cancel the back press
    if (progress <= 0.0f) {
      backPressCancelled()
    } else {
      val resolvedMethod =
        onBackPressProgressMethod ?: backPressDispatcherOwner.findMethod(ON_BACK_PRESS_PROGRESS).also { onBackPressProgressMethod = it }
      resolvedMethod?.invoke(backPressDispatcherOwner, progress.coerceIn(0.0f, 1.0f), edge.name)
    }
  }

  /**
   * Completes the back press, triggering the navigation action.
   *
   * It uses the interactive back press completion method identified during [updateObjects].
   */
  fun backPressCompleted() {
    isBackGestureInProgress = false
    val resolvedMethod =
      onBackPressCompletedMethod ?: backPressDispatcherOwner.findMethod(ON_BACK_PRESS_COMPLETED).also { onBackPressCompletedMethod = it }
    resolvedMethod?.invoke(backPressDispatcherOwner)
      ?: logger.debug("Can't perform back press, reflected method invocation should not be null")
    _backPressCompletedFlow.tryEmit(Unit)
  }

  /** Cancels the back press. If a back press is in progress, stops the interactive back gesture simulation. */
  fun backPressCancelled() {
    isBackGestureInProgress = false
    val resolvedMethod =
      onBackPressCancelledMethod ?: backPressDispatcherOwner.findMethod(ON_BACK_PRESS_CANCELLED).also { onBackPressCancelledMethod = it }
    resolvedMethod?.invoke(backPressDispatcherOwner)
      ?: logger.debug("Can't call back press cancelled, reflected method invocation should not be null")
    _backPressCompletedFlow.tryEmit(Unit)
  }

  /**
   * Checks, via reflection, if back navigation can be performed. This method supports the new (navigation3) back press APIs.
   *
   * @return true if [backPressCompleted] can be called, false otherwise.
   */
  fun canPerformBackNavigation(): Boolean =
    (onBackPressCompletedMethod ?: backPressDispatcherOwner.findMethod(ON_BACK_PRESS_COMPLETED).also { onBackPressCompletedMethod = it }) !=
      null

  /**
   * Checks, via reflection, if predictive back navigation can be performed.
   *
   * This method only supports the new (navigation3) back press API.
   *
   * @return true if [backPressProgress] and [backPressCompleted] can be called, false otherwise.
   */
  fun isPredictiveBackReady(): Boolean =
    (onBackPressProgressMethod ?: backPressDispatcherOwner.findMethod(ON_BACK_PRESS_PROGRESS).also { onBackPressProgressMethod = it }) !=
      null

  /**
   * Shows the navigation controls for the given [instance].
   *
   * If the controller is not already enabled, it retrieves the navigation component from the [showNavigationControlsProvider] and triggers
   * [onAfterPanelUpdate].
   *
   * @param instance The [ComposePreviewElementInstance] for which to show the navigation controls.
   */
  @UiThread
  fun showNavigationControls(instance: ComposePreviewElementInstance<*>) {
    if (activeBackNavigationPanelInInteractiveMode == null) {
      activeBackNavigationPanelInInteractiveMode = showNavigationControlsProvider()
      onAfterPanelUpdate()
      usageTrackerProvider().trackNavigationPanelVisibilityChange(isShown = true)
    }
  }

  /**
   * Hides the navigation controls.
   *
   * If the controller is currently enabled, it clears the active navigation panel and triggers [onAfterPanelUpdate].
   */
  @UiThread
  fun hideNavigationControls() {
    backPressCancelled()
    if (activeBackNavigationPanelInInteractiveMode != null) {
      activeBackNavigationPanelInInteractiveMode = null
      onAfterPanelUpdate()
      usageTrackerProvider().trackNavigationPanelVisibilityChange(isShown = false)
    }
  }

  /**
   * Returns the currently active navigation panel [JComponent] or null if the navigation controls are not visible.
   *
   * @return The active navigation component or null.
   */
  fun getBottomPanelComponent() = activeBackNavigationPanelInInteractiveMode

  /**
   * Returns `true` if the navigation controls are currently enabled and visible, `false` otherwise.
   *
   * @return True if navigation controls are enabled.
   */
  fun isNavigationControlsShown(): Boolean = activeBackNavigationPanelInInteractiveMode != null

  fun trackNavigationProgressPress() = usageTrackerProvider().trackNavigationPanelProgressPress()

  fun trackNavigationBackPress() = usageTrackerProvider().trackNavigationPanelBackPress()

  fun trackEdgeDropdownPress() = usageTrackerProvider().trackNavigationPanelEdgeDropdownPress()

  companion object {
    private const val CAN_BACK_PRESS = "canBackPress"
    private const val ON_BACK_PRESS_STARTED = "onBackPressStarted"
    private const val ON_BACK_PRESS_PROGRESS = "onBackPressProgress"
    private const val ON_BACK_PRESS_COMPLETED = "onBackPressCompleted"
    private const val ON_BACK_PRESS_CANCELLED = "onBackPressCancelled"

    /**
     * The [DataKey] used to access the [InteractivePreviewNavigationController] from the [com.intellij.openapi.actionSystem.DataContext].
     */
    val KEY = DataKey.create<InteractivePreviewNavigationController>(InteractivePreviewNavigationController::class.java.name)
  }
}
