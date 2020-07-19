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

import com.android.tools.idea.layoutinspector.legacydevice.LegacyClient
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.android.tools.idea.layoutinspector.transport.InspectorClient
import com.android.tools.idea.layoutinspector.ui.InspectorBannerService
import com.intellij.openapi.project.Project
import org.jetbrains.android.util.AndroidBundle
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import kotlin.properties.Delegates

const val REBOOT_FOR_LIVE_INSPECTOR_MESSAGE_KEY = "android.ddms.notification.layoutinspector.reboot.live.inspector"

class InspectorModel(val project: Project) {
  val selectionListeners = mutableListOf<(ViewNode?, ViewNode?) -> Unit>()
  val modificationListeners = mutableListOf<(ViewNode?, ViewNode?, Boolean) -> Unit>()
  val connectionListeners = mutableListOf<(InspectorClient?) -> Unit>()
  val resourceLookup = ResourceLookup(project)

  // TODO: store this at the window root
  private fun findSubimages(root: ViewNode?) =
    root?.flatten()?.minus(root)?.any { it.drawChildren.firstIsInstanceOrNull<DrawViewImage>() != null } == true

  var selection: ViewNode? by Delegates.observable(null as ViewNode?) { _, old, new ->
    if (new != old) {
      selectionListeners.forEach { it(old, new) }
    }
  }

  val hoverListeners = mutableListOf<(ViewNode?, ViewNode?) -> Unit>()
  var hoveredNode: ViewNode? by Delegates.observable(null as ViewNode?) { _, old, new ->
    if (new != old) {
      hoverListeners.forEach { it(old, new) }
    }
  }

  private val roots = mutableMapOf<Any, ViewNode>()
  // dummy node to hold the roots of the current windows.
  val root = ViewNode(-1, "root - hide", null, 0, 0, 0, 0, null, "", 0)

  var hasSubImages = false
    private set

  /** Whether there are currently any views in this model */
  val isEmpty
    get() = root.children.isEmpty()

  /**
   * Get a ViewNode by drawId
   */
  operator fun get(id: Long) = root.flatten().find { it.drawId == id }

  /**
   * Get a ViewNode by viewId name
   */
  operator fun get(id: String) = root.flatten().find { it.viewId?.name == id }

  /**
   * Update [root]'s bounds and children based on any updates to [roots]
   * Also adds a dark layer between windows if DIM_BEHIND is set.
   */
  private fun updateRoot(allIds: List<*>) {
    root.children.clear()
    root.drawChildren.clear()
    val maxWidth = roots.values.map { it.width }.max() ?: 0
    val maxHeight = roots.values.map { it.height }.max() ?: 0
    root.width = maxWidth
    root.height = maxHeight
    for (id in allIds) {
      val viewNode = roots[id] ?: continue
      if (viewNode.isDimBehind) {
        root.drawChildren.add(Dimmer(root))
      }
      root.children.add(viewNode)
      root.drawChildren.add(DrawViewChild(viewNode))
      viewNode.parent = root
    }
  }

  fun updateConnection(client: InspectorClient?) {
    connectionListeners.forEach { it(client) }
    updateConnectionNotification(client)
  }

  private fun updateConnectionNotification(client: InspectorClient?) {
    InspectorBannerService.getInstance(project).notification =
      if (client is LegacyClient && client.selectedStream.device.apiLevel >= 29)
        StatusNotificationImpl(AndroidBundle.message(REBOOT_FOR_LIVE_INSPECTOR_MESSAGE_KEY), emptyList())
      else null
  }

  /**
   * Replaces all subtrees with differing root IDs. Existing views are updated.
   */
  fun update(newRoot: ViewNode?, id: Any, allIds: List<*>) {
    var structuralChange: Boolean = roots.keys.retainAll(allIds)
    val oldRoot = roots[id]
    // changes in DIM_BEHIND will cause a structural change
    structuralChange = structuralChange || (newRoot?.isDimBehind != oldRoot?.isDimBehind)
    if (newRoot == oldRoot && !structuralChange) {
      return
    }
    if (newRoot?.drawId != oldRoot?.drawId || newRoot?.qualifiedName != oldRoot?.qualifiedName) {
      if (newRoot != null) {
        roots[id] = newRoot
      }
      else {
        roots.remove(id)
      }
      structuralChange = true
    }
    else {
      if (oldRoot == null || newRoot == null) {
        structuralChange = true
      }
      else {
        val updater = Updater(oldRoot, newRoot)
        structuralChange = updater.update() || structuralChange
      }
    }

    updateRoot(allIds)
    hasSubImages = root.children.any { findSubimages(it) }
    modificationListeners.forEach { it(oldRoot, roots[id], structuralChange) }
  }

  fun notifyModified() = modificationListeners.forEach { it(root, root, false) }

  private class Updater(private val oldRoot: ViewNode, private val newRoot: ViewNode) {
    private val oldNodes = oldRoot.flatten().asSequence().filter{ it.drawId != 0L }.associateBy { it.drawId }

    fun update(): Boolean {
      return update(oldRoot, oldRoot.parent, newRoot)
    }

    private fun update(oldNode: ViewNode, parent: ViewNode?, newNode: ViewNode): Boolean {
      var modified = (parent != oldNode.parent) || !sameChildren(oldNode, newNode)
      // TODO: should changes below cause modified to be set to true?
      // Maybe each view should have its own modification listener that can listen for such changes?
      oldNode.width = newNode.width
      oldNode.height = newNode.height
      oldNode.qualifiedName = newNode.qualifiedName
      oldNode.layout = newNode.layout
      oldNode.x = newNode.x
      oldNode.y = newNode.y
      oldNode.layoutFlags = newNode.layoutFlags
      oldNode.imageType = newNode.imageType
      oldNode.parent = parent
      if (oldNode is ComposeViewNode && newNode is ComposeViewNode) {
        oldNode.composeFilename = newNode.composeFilename
        oldNode.composePackageHash = newNode.composePackageHash
        oldNode.composeOffset = newNode.composeOffset
        oldNode.composeLineNumber = newNode.composeLineNumber
      }

      oldNode.children.clear()
      oldNode.drawChildren.clear()
      for (newChild in newNode.drawChildren) {
        val newChildView = newChild.owner
        if (newChildView != newNode) {
          val oldChild = oldNodes[newChildView.drawId]
          if (oldChild != null && oldChild.javaClass == newChildView.javaClass) {
            modified = update(oldChild, oldNode, newChildView) || modified
            oldNode.children.add(oldChild)
            oldNode.drawChildren.add(newChild)
            newChild.owner = oldChild
          }
          else {
            modified = true
            oldNode.children.add(newChildView)
            oldNode.drawChildren.add(newChild)
            newChildView.parent = oldNode
          }
        }
        else {
          oldNode.drawChildren.add(newChild)
          newChild.owner = oldNode
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