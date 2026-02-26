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

import com.android.tools.environment.Logger
import com.intellij.openapi.actionSystem.DataKey
import java.lang.reflect.Method

/** Enum representing the edge from which a back navigation gesture can be initiated. */
enum class BackNavigationEdge(val visibleName: String) {
  /** Represents a back gesture initiated from the left edge of the screen. */
  LEFT_EDGE("Left Edge"),

  /** Represents a back gesture initiated from the right edge of the screen. */
  RIGHT_EDGE("Right Edge"),

  /** Represents no specific edge for the back gesture. */
  NONE("None"),
}

/**
 * Handles back gestures and predictive back navigation during [PreviewMode.Interactive].
 *
 * This handler works by interacting with the underlying navigation dispatcher object. It primarily attempts to use the
 * [LocalNavigationEventDispatcherOwner] instance, which may be provided directly from a bytecode transform from
 * [LocalNavigationEventTransform]. If that is not available, it attempts to find the dispatcher owner by reflecting on the
 * `androidx.compose.ui.tooling.ComposeViewAdapter` to locate the [FakeOnBackPressedDispatcherOwner] field.
 */
class InteractiveNavigationHandler {

  private var backPressDispatcherOwner: Any? = null

  private var canBackPressMethod: Method? = null
  private var onBackPressStartedMethod: Method? = null
  private var onBackPressProgressMethod: Method? = null
  private var onBackPressCompletedMethod: Method? = null
  private var onBackPressCancelledMethod: Method? = null

  private val logger = Logger.getInstance(InteractiveNavigationHandler::class.java)

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
  fun backPressStart(edge: BackNavigationEdge = BackNavigationEdge.LEFT_EDGE) {
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
  fun backPressProgress(progress: Float, edge: BackNavigationEdge = BackNavigationEdge.LEFT_EDGE) {
    val resolvedMethod =
      onBackPressProgressMethod ?: backPressDispatcherOwner.findMethod(ON_BACK_PRESS_PROGRESS).also { onBackPressProgressMethod = it }
    resolvedMethod?.invoke(backPressDispatcherOwner, progress.coerceIn(0.0f, 1.0f), edge.name)
  }

  /**
   * Completes the back press, triggering the navigation action.
   *
   * It uses the interactive back press completion method identified during [updateObjects].
   */
  fun backPressCompleted() {
    val resolvedMethod =
      onBackPressCompletedMethod ?: backPressDispatcherOwner.findMethod(ON_BACK_PRESS_COMPLETED).also { onBackPressCompletedMethod = it }
    resolvedMethod?.invoke(backPressDispatcherOwner)
      ?: logger.debug("Can't perform back press,reflected method invocation should not be null")
  }

  /** Cancels the back press, If a back press is in progress stops the interactive back gesture simulation. */
  fun backPressCancelled() {
    val resolvedMethod =
      onBackPressCancelledMethod ?: backPressDispatcherOwner.findMethod(ON_BACK_PRESS_CANCELLED).also { onBackPressCancelledMethod = it }
    resolvedMethod?.invoke(backPressDispatcherOwner)
      ?: logger.debug("Can't call back press cancelled, reflected method invocation should not be null")
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

  companion object {
    private const val CAN_BACK_PRESS = "canBackPress"
    private const val ON_BACK_PRESS_STARTED = "onBackPressStarted"
    private const val ON_BACK_PRESS_PROGRESS = "onBackPressProgress"
    private const val ON_BACK_PRESS_COMPLETED = "onBackPressCompleted"
    private const val ON_BACK_PRESS_CANCELLED = "onBackPressCancelled"

    val KEY = DataKey.create<InteractiveNavigationHandler>(InteractiveNavigationHandler::class.java.name)
  }
}
