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

import com.android.tools.idea.appinspection.api.process.ProcessesModel
import com.android.tools.idea.layoutinspector.memory.InspectorMemoryProbe
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient.Capability
import com.android.tools.idea.layoutinspector.properties.ViewNodeAndResourceLookup
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.android.tools.idea.layoutinspector.metrics.statistics.SessionStatistics
import com.android.tools.idea.layoutinspector.ui.InspectorBannerService
import com.intellij.openapi.project.Project
import org.jetbrains.android.util.AndroidBundle
import java.util.concurrent.Executors.newSingleThreadExecutor
import kotlin.properties.Delegates

const val REBOOT_FOR_LIVE_INSPECTOR_MESSAGE_KEY = "android.ddms.notification.layoutinspector.reboot.live.inspector"

enum class SelectionOrigin { INTERNAL, COMPONENT_TREE }

class InspectorModel(val project: Project) : ViewNodeAndResourceLookup {
  override val resourceLookup = ResourceLookup(project)
  val selectionListeners = mutableListOf<(ViewNode?, ViewNode?, SelectionOrigin) -> Unit>()
  /** Callback taking (oldWindow, newWindow, isStructuralChange */
  val modificationListeners = mutableListOf<(AndroidWindow?, AndroidWindow?, Boolean) -> Unit>()
  val connectionListeners = mutableListOf<(InspectorClient?) -> Unit>()
  override val stats = SessionStatistics()
  var lastGeneration = 0
  var updating = false

  @Suppress("unused")
  private val memoryProbe = InspectorMemoryProbe(this)
  private val idLookup = mutableMapOf<Long, ViewNode>()

  override var selection: ViewNode? = null
    private set

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

  val pictureType
    get() =
      when {
        windows.values.any { it.imageType == AndroidWindow.ImageType.BITMAP_AS_REQUESTED } -> {
          // If we find that we've requested and received a png, that's what we'll use first
          AndroidWindow.ImageType.BITMAP_AS_REQUESTED
        }
        windows.values.all { it.imageType == AndroidWindow.ImageType.SKP } -> {
          // If all windows are SKP, use that
          AndroidWindow.ImageType.SKP
        }
        else -> {
          AndroidWindow.ImageType.UNKNOWN
        }
      }

  private val hiddenNodes = mutableSetOf<ViewNode>()

  /**
   * Get a ViewNode by drawId
   */
  override operator fun get(id: Long): ViewNode? {
    if (idLookup.isEmpty()) {
      root.flatten().forEach { idLookup[it.drawId] = it }
    }
    return idLookup[id]
  }

  /**
   * Get a ViewNode by viewId name
   */
  operator fun get(id: String) = root.flatten().find { it.viewId?.name == id }

  /**
   * Get the root of the view tree that the view parameter lives in.
   *
   * This could be null if the view isn't in the model for any reason.
   */
   fun rootFor(view: ViewNode): ViewNode? {
    return view.parentSequence.firstOrNull { it.parent === root }
  }

  fun rootFor(viewId: Long): ViewNode? {
    return this[viewId]?.let { rootFor(it) }
  }

  /**
   * Update [root]'s bounds and children based on any updates to [windows]
   * Also adds a dark layer between windows if DIM_BEHIND is set.
   */
  private fun updateRoot(allIds: List<*>) {
    root.children.forEach { it.parent = null }
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

  fun setSelection(new: ViewNode?, origin: SelectionOrigin) {
    val old = selection
    selection = new
    selectionListeners.forEach { it(old, new, origin) }
  }

  fun updateConnection(client: InspectorClient) {
    connectionListeners.forEach { it(client) }
    updateConnectionNotification(client)
    updateStats(client)
  }

  private fun updateConnectionNotification(client: InspectorClient) {
    if (client.isConnected && !client.capabilities.contains(Capability.SUPPORTS_CONTINUOUS_MODE) && client.process.device.apiLevel >= 29) {
      InspectorBannerService.getInstance(project).notification = StatusNotificationImpl(
        AndroidBundle.message(REBOOT_FOR_LIVE_INSPECTOR_MESSAGE_KEY), emptyList())
    }
  }

  private fun updateStats(client: InspectorClient) {
    if (client.isConnected) {
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
        oldWindow.copyFrom(newWindow)
        val updater = Updater(oldWindow.root, newWindow.root)
        structuralChange = updater.update() || structuralChange
      }
    }

    updateRoot(allIds)
    if (selection?.parentSequence?.last() !== root) {
      selection = null
    }
    if (hoveredNode?.parentSequence?.last() !== root) {
      hoveredNode = null
    }
    lastGeneration = generation
    idLookup.clear()
    val allNodes = root.flatten().toSet()
    hiddenNodes.removeIf { !allNodes.contains(it) }
    updating = false
    modificationListeners.forEach { it(oldWindow, windows[newWindow?.id], structuralChange) }
  }

  fun notifyModified() =
    if (windows.isEmpty()) modificationListeners.forEach { it(null, null, false) }
    else windows.values.forEach { window -> modificationListeners.forEach { it(window, window, false) } }

  fun clear() {
    update(null, listOf<Nothing>(), 0)
  }

  fun setProcessModel(processes: ProcessesModel) {
    processes.addSelectedProcessListeners(newSingleThreadExecutor()) {
      clear()
    }
  }

  fun showAll() {
    hiddenNodes.clear()
    notifyModified()
  }

  fun hideSubtree(node: ViewNode) {
    hiddenNodes.addAll(node.flatten())
    notifyModified()
  }

  fun showOnlySubtree(subtreeRoot: ViewNode) {
    hiddenNodes.clear()
    lateinit var findNodes: (ViewNode) -> Sequence<ViewNode>
    findNodes = { node -> node.children.asSequence().filter { it != subtreeRoot }.flatMap { findNodes(it) }.plus(node) }
    hiddenNodes.addAll(findNodes(root).plus(root))
    notifyModified()
  }

  fun showOnlyParents(node: ViewNode) {
    hiddenNodes.clear()
    hiddenNodes.addAll(root.flatten().minus(node.parentSequence))
    notifyModified()
  }

  fun isVisible(node: ViewNode) = !hiddenNodes.contains(node)

  fun hasHiddenNodes() = hiddenNodes.isNotEmpty()

  private class Updater(private val oldRoot: ViewNode, private val newRoot: ViewNode) {
    private val oldNodes = oldRoot.flatten().asSequence().filter{ it.drawId != 0L }.associateByTo(mutableMapOf()) { it.drawId }

    fun update(): Boolean {
      val modified = update(oldRoot, oldRoot.parent, newRoot)
      oldNodes.values.forEach { it.parent = null }
      return modified
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
          oldNodes.remove(newChild.drawId)
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