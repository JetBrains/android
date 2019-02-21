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
package com.android.tools.idea.layoutinspector.model

import kotlin.properties.Delegates

class InspectorModel(initialRoot: InspectorView) {
  val selectionListeners = mutableListOf<(InspectorView?, InspectorView?) -> Unit>()
  val modificationListeners = mutableListOf<(InspectorView?, InspectorView?) -> Unit>()

  var selection: InspectorView? by Delegates.observable(null as InspectorView?) { _, old, new ->
    selectionListeners.forEach { it(old, new) }
  }

  var root: InspectorView by Delegates.observable(initialRoot) { _, old, new ->
    modificationListeners.forEach { it(old, new) }
  }

  /**
   * Replaces all subtrees with differing root IDs. Existing views are updated.
   */
  fun update(newRoot: InspectorView) {
    val oldRoot = root
    val modified: Boolean
    if (newRoot.id != root.id || newRoot.type != root.type) {
      root = newRoot
      modified = true
    }
    else {
      modified = updateInternal(root, newRoot)
    }
    if (modified) {
      modificationListeners.forEach { it(oldRoot, newRoot) }
    }
  }

  private fun updateInternal(oldNode: InspectorView, newNode: InspectorView): Boolean {
    // TODO: should changes below cause modified to be set to true?
    // Maybe each view should have its own modification listener that can listen for such changes?
    oldNode.image = newNode.image
    oldNode.imageGenerationTime = newNode.imageGenerationTime
    oldNode.width = newNode.width
    oldNode.height = newNode.height
    oldNode.x = newNode.x
    oldNode.y = newNode.y

    var modified = false
    for ((id, child) in oldNode.children.toMap()) {
      val correspondingNew = newNode.children[id]
      if (correspondingNew == null || correspondingNew.type != child.type) {
        oldNode.children.remove(id)
        modified = true
      }
      else {
        newNode.children.remove(id)
        modified = updateInternal(child, correspondingNew) || modified
      }
    }
    if (newNode.children.isNotEmpty()) {
      oldNode.children.putAll(newNode.children)
      modified = true
    }
    return modified
  }
}