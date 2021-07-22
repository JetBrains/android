/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.adtui.toolwindow.splittingtabs.state

interface SplittingTabsStateProvider {
  /**
   * Returns a String representing the state of the owning object.
   *
   * It doesn't seem possible to make the IntelliJ XMl Serialization code work properly with subclasses so we can't make the per-client
   * state object implement some interface or extend some base class and still be persisted by the PersistentStateComponent code.
   *
   * So, each client needs to encode and decode its own state.
   */
  fun getState(): String
}