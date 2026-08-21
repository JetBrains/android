/*
 * Copyright (C) 2025 The Android Open Source Project
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

interface OnBackPressedDispatcherOwner {
  val onBackPressedDispatcher: OnBackPressedDispatcher
}

class OnBackPressedDispatcher

class NavigationEventDispatcher

interface NavigationEventDispatcherOwner {
  val navigationEventDispatcher: NavigationEventDispatcher
}

/**
 * Class that tries to mimic the state of ComposeViewAdapter's FakeOnBackPressedDispatcherOwner. It's used to test back press handling in
 * interactive previews.
 */
/**
 * Class that tries to mimic the state of ComposeViewAdapter's FakeOnBackPressedDispatcherOwner. It's used to test back press handling in
 * interactive previews.
 *
 * @param backStack The initial navigation back stack.
 */
class TestComposeViewAdapterViewObj(
  private val canBackPress: Boolean = false,
  private val onBackPressStartedCallback: (String) -> Unit = {},
  private val onBackPressProgressCallback: (Float, String) -> Unit = { _, _ -> },
  private val onBackPressCompletedCallback: () -> Unit = {},
  private val onBackPressCancelledCallback: () -> Unit = {},
  backStack: List<Any> = emptyList(),
  history: List<Any> = backStack,
  private val onBackToStateCallback: ((Any) -> Boolean)? = null,
) {
  /** The current back stack, mutable so back navigation operations can pop items. */
  val backStack: MutableList<Any> = history.toMutableList()

  @Suppress("unused", "PrivatePropertyName")
  private val FakeOnBackPressedDispatcherOwner =
    object : OnBackPressedDispatcherOwner, NavigationEventDispatcherOwner {

      override val onBackPressedDispatcher = OnBackPressedDispatcher()

      override val navigationEventDispatcher = NavigationEventDispatcher()

      fun canBackPress(): Boolean {
        return canBackPress
      }

      fun onBackPressStarted(edge: String) {
        onBackPressStartedCallback(edge)
      }

      fun onBackPressProgress(progress: Float, edge: String) {
        onBackPressProgressCallback(progress, edge)
      }

      fun onBackPressCompleted() {
        onBackPressCompletedCallback()
      }

      fun onBackPressCancelled() {
        onBackPressCancelledCallback()
      }

      /** Returns the current back navigation history from [backStack]. */
      fun getHistory(): List<Any> {
        return this@TestComposeViewAdapterViewObj.backStack
      }

      /**
       * Pops entries from [backStack] down to and including [navigationState] to simulate androidx Navigation3 / NavDisplay back
       * navigation.
       *
       * For example, if the back stack is `[3, 2, 1, 0]` and we navigate to `1`, items `3, 2, 1` are popped, leaving `[0]`.
       *
       * @return `true` if [navigationState] exists in [backStack] and was navigated back to, `false` otherwise.
       */
      fun backToState(navigationState: Any): Boolean {
        if (onBackToStateCallback != null) {
          return onBackToStateCallback.invoke(navigationState)
        }
        val currentBackStack = this@TestComposeViewAdapterViewObj.backStack
        val index = currentBackStack.indexOf(navigationState)
        if (index != -1) {
          // Remove all items from the top of the stack down to and including navigationState
          currentBackStack.subList(0, index + 1).clear()
          return true
        }
        return false
      }
    }
}

class TestNavigationEventDispatcherObj(
  private val canBackPress: Boolean = false,
  private val onBackPressStartedCallback: (String) -> Unit = {},
  private val onBackPressProgressCallback: (Float, String) -> Unit = { _, _ -> },
  private val onBackPressCompletedCallback: () -> Unit = {},
  private val onBackPressCancelledCallback: () -> Unit = {},
  backStack: List<Any> = emptyList(),
  history: List<Any> = backStack,
  private val onBackToStateCallback: ((Any) -> Boolean)? = null,
) : NavigationEventDispatcherOwner {

  /** The current back stack, mutable so back navigation operations can pop items. */
  val backStack: MutableList<Any> = history.toMutableList()

  override val navigationEventDispatcher = NavigationEventDispatcher()

  fun canBackPress(): Boolean {
    return canBackPress
  }

  fun onBackPressStarted(edge: String) {
    onBackPressStartedCallback(edge)
  }

  fun onBackPressProgress(progress: Float, edge: String) {
    onBackPressProgressCallback(progress, edge)
  }

  fun onBackPressCompleted() {
    onBackPressCompletedCallback()
  }

  fun onBackPressCancelled() {
    onBackPressCancelledCallback()
  }

  /** Returns the current back navigation history from [backStack]. */
  fun getHistory(): List<Any> {
    return backStack
  }

  /**
   * Pops entries from [backStack] down to and including [navigationState] to simulate androidx Navigation3 / NavDisplay back navigation.
   *
   * For example, if the back stack is `[3, 2, 1, 0]` and we navigate to `1`, items `3, 2, 1` are popped, leaving `[0]`.
   *
   * @return `true` if [navigationState] exists in [backStack] and was navigated back to, `false` otherwise.
   */
  fun backToState(navigationState: Any): Boolean {
    if (onBackToStateCallback != null) {
      return onBackToStateCallback.invoke(navigationState)
    }
    val index = backStack.indexOf(navigationState)
    if (index != -1) {
      // Remove all items from the top of the stack down to and including navigationState
      backStack.subList(0, index + 1).clear()
      return true
    }
    return false
  }
}
