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
@file:Suppress("UNUSED_PARAMETER")
package androidx.navigationevent.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner

public object LocalNavigationEventDispatcherOwner {
  private var currentNavigationEventDispatcherOwner: NavigationEventDispatcherOwner? = null

  /**
   * Returns the current [NavigationEventDispatcherOwner].
   * This method mimics the signature of a @Composable property getter.
   */
  @Composable
  public fun getCurrent(composer: Composer, changed: Int): NavigationEventDispatcherOwner? {
    return currentNavigationEventDispatcherOwner
  }

  /**
   * Sets the current [NavigationEventDispatcher] to be used by [getCurrent].
   */
  public fun setCurrentNavigationEventDispatcherOwner(navigationEventDispatcherOwner: NavigationEventDispatcherOwner?) {
    currentNavigationEventDispatcherOwner = navigationEventDispatcherOwner
  }
}
