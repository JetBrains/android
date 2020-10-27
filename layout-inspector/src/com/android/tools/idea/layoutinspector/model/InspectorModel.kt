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
import com.android.tools.idea.layoutinspector.memory.InspectorMemoryProbe
import com.android.tools.idea.layoutinspector.properties.ViewNodeAndResourceLookup
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.android.tools.idea.layoutinspector.statistics.SessionStatistics
import com.android.tools.idea.layoutinspector.transport.DisconnectedClient
import com.android.tools.idea.layoutinspector.transport.InspectorClient
import com.android.tools.idea.layoutinspector.ui.InspectorBannerService
import com.intellij.openapi.project.Project
import org.jetbrains.android.util.AndroidBundle
import kotlin.properties.Delegates

const val REBOOT_FOR_LIVE_INSPECTOR_MESSAGE_KEY = "android.ddms.notification.layoutinspector.reboot.live.inspector"

class InspectorModel(val project: Project) : ViewNodeAndResourceLookup {
  override val resourceLookup = ResourceLookup(project)
  val selectionListeners = mutableListOf<(ViewNode?, ViewNode?) -> Unit>()
  /** Callback taking (oldWindow, newWindow, isStructuralChange */
  val modificationListeners = mutableListOf<(AndroidWindow?, AndroidWindow?, Boolean) -> Unit>()
  val connectionListeners = mutableListOf<(InspectorClient?) -> Unit>()
  val stats = SessionStatistics()
  var lastGeneration = 0
  var updating = false

  @Suppress("unused")
  private val memoryProbe = InspectorMemoryProbe(this)

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

  val windows = mutableMapOf<Any, AndroidWindow>()
  // synthetic node to hold the roots of the current windows.
  val root = ViewNode(-1, "root - hide", null, 0, 0, 0, 0, null, null, "", 0)

  val hasSubImages
    get() = windows.values.any { it.hasSubImages }

  /** Whether there are currently any views in this model */
  val isEmpty
    get() = windows.isEmpty()

  /**
   * Get a ViewNode by drawId
   */
  override operator fun get(id: Long) = root.flatten().find { it.drawId == id }

  /**
   * Get a ViewNode by viewId name
   */
  operator fun get(id: String) = root.flatten().find { it.viewId?.name == id }

  /**
   * Update [root]'s bounds and children based on any updates to [windows]
   * Also adds a dark layer between windows if DIM_BEHIND is set.
   */
  private fun updateRoot(allIds: List<*>) {
    root.children.clear()
    ViewNode.writeDrawChildren { drawChildren ->
      root.drawChildren().clear()
      val maxWidth = windows.values.map { it.width }.max() ?: 0
      val maxHeight = windows.values.map { it.height }.max() ?: 0
      root.width = maxWidth
      root.height = maxHeight
      for (id in allIds) {
        val window = windows[id] ?: continue
        if (window.isDimBehind) {
          root.drawChildren().add(Dimmer(root))
        }
        root.drawChildren().add(DrawViewChild(window.root))
        root.children.add(window.root)
        window.root.parent = root
      }
    }
  }

  fun updateConnection(client: InspectorClient) {
    connectionListeners.forEach { it(client) }
    updateConnectionNotification(client)
    updateStats(client)
  }

  private fun updateConnectionNotification(client: InspectorClient) {
    InspectorBannerService.getInstance(project).notification =
      if (client is LegacyClient && client.selectedStream.device.apiLevel >= 29)
        StatusNotificationImpl(AndroidBundle.message(REBOOT_FOR_LIVE_INSPECTOR_MESSAGE_KEY), emptyList())
      else null
  }

  private fun updateStats(client: InspectorClient) {
    if (client != DisconnectedClient) {
      stats.start(client.isCapturing)
    }
  }

  /**
   * Replaces all subtrees with differing root IDs. Existing views are updated.
   * This removes drawChildren from all existing [ViewNode]s. [AndroidWindow.refreshImages] must be called on newWindow after to regenerate
   * them.
   */
  fun update(newWindow: AndroidWindow?, allIds: List<*>, generation: Int) {
    updating = true
    var structuralChange: Boolean = windows.keys.retainAll(allIds)
    val oldWindow = windows[newWindow?.id]
    if (newWindow != null) {
      // changes in DIM_BEHIND will cause a structural change
      structuralChange = structuralChange || (newWindow.isDimBehind != oldWindow?.isDimBehind)
      if (newWindow == oldWindow && !structuralChange) {
        return
      }
      if (newWindow.root.drawId != oldWindow?.root?.drawId || newWindow.root.qualifiedName != oldWindow.root.qualifiedName) {
        windows[newWindow.id] = newWindow
        structuralChange = true
      }
      else {
        oldWindow.imageType = newWindow.imageType
        oldWindow.payloadId = newWindow.payloadId
        val updater = Updater(oldWindow.root, newWindow.root)
        structuralChange = updater.update() || structuralChange
      }
    }

    updateRoot(allIds)
    lastGeneration = generation
    updating = false
    modificationListeners.forEach { it(oldWindow, windows[newWindow?.id], structuralChange) }
  }

  fun notifyModified() = modificationListeners.forEach { it(null, null, false) }

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
      oldNode.setTransformedBounds(newNode.transformedBounds)
      oldNode.layoutFlags = newNode.layoutFlags
      oldNode.parent = parent
      if (oldNode is ComposeViewNode && newNode is ComposeViewNode) {
        oldNode.composeFilename = newNode.composeFilename
        oldNode.composePackageHash = newNode.composePackageHash
        oldNode.composeOffset = newNode.composeOffset
        oldNode.composeLineNumber = newNode.composeLineNumber
      }

      oldNode.children.clear()
      ViewNode.writeDrawChildren { drawChildren -> oldNode.drawChildren().clear() }
      for (newChild in newNode.children) {
        val oldChild = oldNodes[newChild.drawId]
        if (oldChild != null && oldChild.javaClass == newChild.javaClass) {
          modified = update(oldChild, oldNode, newChild) || modified
          oldNode.children.add(oldChild)
        } else {
          modified = true
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