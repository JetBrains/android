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
interface ComponentTreeModel {
  /**
   * The current tree root (if any).
   * Setting this will fire [hierarchyChanged] on the root.
   */
  var treeRoot: Any?

  /**
   * Notify any listeners that [changedNode] or some child node beneath it has changed. If there is a structural change in the tree,
   * this method should be called so that the underlying JTree can be updated appropriately.
   */
  fun hierarchyChanged(changedNode: Any?)
}
