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

import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.intellij.openapi.project.Project
import kotlin.properties.Delegates

class InspectorModel(val project: Project, initialRoot: ViewNode? = null) {
  val selectionListeners = mutableListOf<(ViewNode?, ViewNode?) -> Unit>()
  val modificationListeners = mutableListOf<(ViewNode?, ViewNode?, Boolean) -> Unit>()
  val resourceLookup = ResourceLookup(project)

  var selection: ViewNode? by Delegates.observable(null as ViewNode?) { _, old, new ->
    if (new != old) {
      selectionListeners.forEach { it(old, new) }
    }
  }

  var root: ViewNode? by Delegates.observable(initialRoot) { _, old, new ->
    modificationListeners.forEach { it(old, new, true) }
  }

  /**
   * Replaces all subtrees with differing root IDs. Existing views are updated.
   */
  fun update(newRoot: ViewNode?) {
    if (newRoot == root) {
      return
    }
    val oldRoot = root
    val structuralChange: Boolean
    if (newRoot?.drawId != root?.drawId || newRoot?.qualifiedName != root?.qualifiedName) {
      root = newRoot
      structuralChange = true
    }
    else {
      if (oldRoot == null || newRoot == null) {
        structuralChange = true
      }
      else {
        val updater = Updater(oldRoot, newRoot)
        structuralChange = updater.update()
      }
    }
    modificationListeners.forEach { it(oldRoot, newRoot, structuralChange) }
  }

  private class Updater(private val oldRoot: ViewNode, private val newRoot: ViewNode) {
    private val oldNodes = oldRoot.flatten().associateBy { it.drawId }

    fun update(): Boolean {
      return update(oldRoot, null, newRoot)
    }

    private fun update(oldNode: ViewNode, parent: ViewNode?, newNode: ViewNode): Boolean {
      var modified = (parent != oldNode.parent) || !sameChildren(oldNode, newNode)
      // TODO: should changes below cause modified to be set to true?
      // Maybe each view should have its own modification listener that can listen for such changes?
      oldNode.imageBottom = newNode.imageBottom
      oldNode.imageTop = newNode.imageTop
      oldNode.width = newNode.width
      oldNode.height = newNode.height
      oldNode.x = newNode.x
      oldNode.y = newNode.y
      oldNode.parent = parent

      oldNode.children.clear()
      for (newChild in newNode.children) {
        val oldChild = oldNodes[newChild.drawId]
        if (oldChild != null) {
          modified = update(oldChild, oldNode, newChild) || modified
          oldNode.children.add(oldChild)
        } else {
          oldNode.children.add(newChild)
          newChild.parent = oldNode
        }
      }
      return modified
    }

    private fun sameChildren(oldNode: ViewNode?, newNode: ViewNode?): Boolean {
      if (oldNode?.children?.size != newNode?.children?.size) {
        return false
      }
      return oldNode?.children?.indices?.all { oldNode.children[it].drawId == newNode?.children?.get(it)?.drawId } ?: true
    }
  }
}