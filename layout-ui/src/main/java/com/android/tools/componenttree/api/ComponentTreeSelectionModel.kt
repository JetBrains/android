/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.componenttree.api

/**
 * The model returned by the [ComponentTreeBuilder].
 */
interface ComponentTreeSelectionModel {
  /**
   * The current selection.
   *
   * Any changes will cause the tree to be notified.
   */
  var selection: List<Any>

  /**
   * Add a listeners for selection changes.
   *
   * The callback will include the newly selected list of tree items.
   */
  fun addSelectionListener(listener: (List<Any>) -> Unit)

  /**
   * Remove a selection listener.
   */
  fun removeSelectionListener(listener: (List<Any>) -> Unit)
}