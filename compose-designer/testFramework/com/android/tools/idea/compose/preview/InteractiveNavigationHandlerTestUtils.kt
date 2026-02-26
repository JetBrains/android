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
class TestComposeViewAdapterViewObj(
  private val canBackPress: Boolean = false,
  private val onBackPressStartedCallback: (String) -> Unit = {},
  private val onBackPressProgressCallback: (Float, String) -> Unit = { _, _ -> },
  private val onBackPressCompletedCallback: () -> Unit = {},
  private val onBackPressCancelledCallback: () -> Unit = {},
) {
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
    }
}

class TestNavigationEventDispatcherObj(
  private val canBackPress: Boolean = false,
  private val onBackPressStartedCallback: (String) -> Unit = {},
  private val onBackPressProgressCallback: (Float, String) -> Unit = { _, _ -> },
  private val onBackPressCompletedCallback: () -> Unit = {},
  private val onBackPressCancelledCallback: () -> Unit = {},
) : NavigationEventDispatcherOwner {

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
}
