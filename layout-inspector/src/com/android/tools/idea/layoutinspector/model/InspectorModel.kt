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
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.properties.ViewNodeAndResourceLookup
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol.FoldEvent.SpecialAngles.NO_FOLD_ANGLE_VALUE
import com.android.tools.idea.util.ListenerCollection
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorErrorInfo
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors.newSingleThreadExecutor
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.pow
import kotlin.properties.Delegates

const val REBOOT_FOR_LIVE_INSPECTOR_MESSAGE_KEY = "android.ddms.notification.layoutinspector.reboot.live.inspector"

const val DECREASE_HALF_TIME = 2000
const val DECREASE_DELAY = 500L
val DECREASE_TIMEUNIT = TimeUnit.MILLISECONDS
val DECREASE_FACTOR = 2.0.pow(DECREASE_DELAY.toDouble() / DECREASE_HALF_TIME.toDouble()).toFloat()
const val DECREASE_BREAK_OFF = 0.75f

enum class SelectionOrigin { INTERNAL, COMPONENT_TREE }

/** Callback taking (oldWindow, newWindow, isStructuralChange */
typealias InspectorModelModificationListener = (AndroidWindow?, AndroidWindow?, Boolean) -> Unit

class InspectorModel(val project: Project, val scheduler: ScheduledExecutorService? = null) : ViewNodeAndResourceLookup {
  override val resourceLookup = ResourceLookup(project)
  val selectionListeners = mutableListOf<(ViewNode?, ViewNode?, SelectionOrigin) -> Unit>()

  val modificationListeners = ListenerCollection.createWithDirectExecutor<InspectorModelModificationListener>()

  val connectionListeners = mutableListOf<(InspectorClient?) -> Unit>()
  var lastGeneration = 0
  var updating = false

  /** After an [update] this will hold the max value for counts, skips, and highlightCount */
  val maxRecomposition = RecompositionData(0, 0)

  /** Holds the highest highlightCount seen since last time all highlight counts were zero */
  var maxHighlight = 0f

  private val idLookup = ConcurrentHashMap<Long, ViewNode>()

  override var selection: ViewNode? = null
    private set

  val hoverListeners = mutableListOf<(ViewNode?, ViewNode?) -> Unit>()
  var hoveredNode: ViewNode? by Delegates.observable(null as ViewNode?) { _, old, new ->
    if (new != old) {
      hoverListeners.forEach { it(old, new) }
    }
  }

  // TODO: update all listeners to use ListenerCollection
  val attachStageListeners = ListenerCollection.createWithDirectExecutor<(DynamicLayoutInspectorErrorInfo.AttachErrorState) -> Unit>()

  val windows = mutableMapOf<Any, AndroidWindow>()
  // synthetic node to hold the roots of the current windows.
  val root = ViewNode("android.root - hide")

  enum class Posture { HALF_OPEN, FLAT }
  enum class FoldOrientation { VERTICAL, HORIZONTAL }
  class FoldInfo(var angle: Int?, var posture: Posture?, var orientation: FoldOrientation) {
    fun toProto(): LayoutInspectorViewProtocol.FoldEvent = LayoutInspectorViewProtocol.FoldEvent.newBuilder().also { builder ->
      when (posture) {
        Posture.HALF_OPEN -> LayoutInspectorViewProtocol.FoldEvent.FoldState.HALF_OPEN
        Posture.FLAT -> LayoutInspectorViewProtocol.FoldEvent.FoldState.FLAT
        else -> null
      }?.let { builder.foldState = it }
      builder.angle = angle ?: NO_FOLD_ANGLE_VALUE
      builder.orientation = when (orientation) {
        FoldOrientation.VERTICAL -> LayoutInspectorViewProtocol.FoldEvent.FoldOrientation.VERTICAL
        FoldOrientation.HORIZONTAL -> LayoutInspectorViewProtocol.FoldEvent.FoldOrientation.HORIZONTAL
      }
    }.build()
  }

  var foldInfo: FoldInfo? = null
    set(value) {
      field = value
      notifyModified()
    }

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

  private val hiddenNodes = ConcurrentHashMap.newKeySet<ViewNode>()

  /**
   * Get a ViewNode by drawId
   */
  override operator fun get(id: Long): ViewNode? {
    if (idLookup.isEmpty()) {
      ViewNode.readAccess {
        root.flatten().forEach { idLookup[it.drawId] = it }
      }
    }
    return idLookup[id]
  }

  /**
   * Get a ViewNode by viewId name
   */
  operator fun get(id: String) = ViewNode.readAccess { root.flatten().find { it.viewId?.name == id } }

  fun fireAttachStateEvent(state: DynamicLayoutInspectorErrorInfo.AttachErrorState) {
    attachStageListeners.forEach { it.invoke(state) }
  }

  /**
   * Get the root of the view tree that the [view] parameter lives in.
   *
   * This could be null if the view isn't in the model for any reason.
   */
  fun rootFor(view: ViewNode): ViewNode? =
    ViewNode.readAccess { view.parentSequence.firstOrNull { it.parent === root } }

  /**
   * Get the root of the view tree that the view with [viewId] lives in.
   *
   * This could be null if the view isn't in the model or [viewId] doesn't exist for any reason.
   */
  fun rootFor(viewId: Long): ViewNode? {
    return this[viewId]?.let { rootFor(it) }
  }

  /**
   * In-place update of all nodes (no structural changes should be made).
   */
  fun updateAll(operation: (ViewNode) -> Unit) {
    ViewNode.readAccess { root.flatten().forEach { operation(it) } }
  }

  /**
   * Update [root]'s bounds and children based on any updates to [windows]
   * Also adds a dark layer between windows if DIM_BEHIND is set.
   */
  private fun updateRoot(allIds: List<*>) {
    ViewNode.writeAccess {
      root.children.forEach { it.parent = null }
      root.children.clear()
      root.drawChildren.clear()
      val maxWidth = windows.values.map { it.width }.maxOrNull() ?: 0
      val maxHeight = windows.values.map { it.height }.maxOrNull() ?: 0
      root.layoutBounds.width = maxWidth
      root.layoutBounds.height = maxHeight
      for (id in allIds) {
        val window = windows[id] ?: continue
        if (window.isDimBehind) {
          root.drawChildren.add(Dimmer(root))
        }
        root.drawChildren.add(DrawViewChild(window.root))
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

  fun resetRecompositionCounts() {
    resetRecompositionCounters()
    updatePropertiesPanel()
  }

  private fun resetRecompositionCounters() {
    maxRecomposition.reset()
    maxHighlight = 0f
    updateAll { node -> (node as? ComposeViewNode)?.resetRecomposeCounts() }
  }

  fun updatePropertiesPanel() {
    setSelection(selection, SelectionOrigin.INTERNAL)
  }

  fun updateConnection(client: InspectorClient) {
    connectionListeners.forEach { it(client) }
  }

  /**
   * Replaces all subtrees with differing root IDs. Existing views are updated.
   * This removes drawChildren from all existing [ViewNode]s. [AndroidWindow.refreshImages] must be called on newWindow after to regenerate
   * them.
   */
  fun update(newWindow: AndroidWindow?, allIds: List<*>, generation: Int, notifyUpdateCompleted: () -> Unit = {}) {
    if (windows.isEmpty()) {
      // Reset the recomposition counters if this is a new connection:
      resetRecompositionCounters()
    }
    var structuralChange: Boolean = windows.keys.retainAll(allIds)
    val oldWindow = windows[newWindow?.id]
    updating = true
    try {
      ViewNode.writeAccess {
        if (newWindow != null) {
          // changes in DIM_BEHIND will cause a structural change
          structuralChange = structuralChange || (newWindow.isDimBehind != oldWindow?.isDimBehind)
          if (newWindow == oldWindow && !structuralChange) {
            return@writeAccess
          }
          else if (newWindow.root.drawId != oldWindow?.root?.drawId || newWindow.root.qualifiedName != oldWindow.root.qualifiedName) {
            windows[newWindow.id] = newWindow
            structuralChange = true
            if (oldWindow == null) {
              // build draw tree on initial load of the window, so we can scale and scroll correctly.
              // We only want to do this on initial load since otherwise there'll be flickering between when the tree is updated and when
              // the images are loaded.
              buildDrawTree(newWindow.root)
            }
          }
          else {
            oldWindow.copyFrom(newWindow)
            val updater = Updater(oldWindow.root, newWindow.root, this)
            structuralChange = updater.update() || structuralChange
          }
        }

        updateRoot(allIds)
        if (selection?.parentSequence?.lastOrNull() !== root) {
          selection = null
        }
        if (hoveredNode?.parentSequence?.lastOrNull() !== root) {
          hoveredNode = null
        }
        lastGeneration = generation
        idLookup.clear()
        val allNodes = root.flatten().toSet()
        hiddenNodes.removeIf { !allNodes.contains(it) }
        maxRecomposition.reset()
        root.flatten().forEach { maxRecomposition.maxOf(it) }
        if (StudioFlags.DYNAMIC_LAYOUT_INSPECTOR_ENABLE_RECOMPOSITION_HIGHLIGHTS.get() &&
            scheduler != null && maxHighlight < maxRecomposition.highlightCount) {
          if (maxHighlight == 0f) {
            scheduler.schedule(::decreaseHighlights, DECREASE_DELAY, DECREASE_TIMEUNIT)
          }
          maxHighlight = maxRecomposition.highlightCount
        }
      }
      root.calculateTransitiveBounds()
    }
    finally {
      updating = false
    }

    notifyUpdateCompleted()
    modificationListeners.forEach { it(oldWindow, windows[newWindow?.id], structuralChange) }
  }

  private fun decreaseHighlights() {
    ViewNode.writeAccess {
      val max = root.flatten().filterIsInstance<ComposeViewNode>().maxOfOrNull { it.recompositions.decreaseHighlights() } ?: 0f
      if (max != 0f) {
        scheduler?.schedule(::decreaseHighlights, DECREASE_DELAY, DECREASE_TIMEUNIT)
      } else {
        maxHighlight = 0f
      }
    }
    windows.values.forEach { window ->
      modificationListeners.forEach { it(window, window, false) }
    }
  }

  /**
   * Build draw nodes
   */
  private fun ViewNode.WriteAccess.buildDrawTree(node: ViewNode) {
    if (node.drawChildren.isEmpty()) {
      node.children.forEach {
        node.drawChildren.add(DrawViewChild(it))
        buildDrawTree(it)
      }
    }
  }

  fun notifyModified(structuralChange: Boolean = false) {
    if (windows.isEmpty()) modificationListeners.forEach { it(null, null, structuralChange) }
    else windows.values.forEach { window -> modificationListeners.forEach { it(window, window, structuralChange) } }
  }

  fun clear() {
    foldInfo = null
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
    ViewNode.readAccess { hiddenNodes.addAll(node.flatten()) }
    notifyModified()
  }

  fun showOnlySubtree(subtreeRoot: ViewNode) {
    hiddenNodes.clear()
    ViewNode.readAccess {
      @Suppress("JoinDeclarationAndAssignment")
      lateinit var findNodes: (ViewNode) -> Sequence<ViewNode>
      findNodes = { node -> node.children.asSequence().filter { it != subtreeRoot }.flatMap { findNodes(it) }.plus(node) }
      hiddenNodes.addAll(findNodes(root).plus(root))
    }
    notifyModified()
  }

  fun showOnlyParents(node: ViewNode) {
    hiddenNodes.clear()
    ViewNode.readAccess {
      hiddenNodes.addAll(root.flatten().minus(node.parentSequence))
    }
    notifyModified()
  }

  fun isVisible(node: ViewNode) = !hiddenNodes.contains(node)

  fun hasHiddenNodes() = hiddenNodes.isNotEmpty()

  /**
   * Used to update the model when the view changes on the device.
   */
  private class Updater(
    private val oldRoot: ViewNode,
    private val newRoot: ViewNode,
    private val access: ViewNode.WriteAccess
  ) {
    private val oldNodes = access.run {
      oldRoot.flatten().filter { it.drawId != 0L }.associateByTo(mutableMapOf()) { it.drawId }
    }

    fun update(): Boolean {
      return access.run {
        val modified = update(oldRoot, oldRoot.parent, newRoot)
        oldNodes.values.forEach { it.parent = null }
        modified
      }
    }

    /**
     * Called when the view has changed on the device.
     * All the information from the [newNode] is copied into the [oldNode].
     */
    private fun ViewNode.WriteAccess.update(oldNode: ViewNode, parent: ViewNode?, newNode: ViewNode): Boolean {
      var modified = (parent != oldNode.parent) || !sameChildren(oldNode, newNode)
      // TODO: should changes below cause modified to be set to true?
      // Maybe each view should have its own modification listener that can listen for such changes?
      oldNode.layoutBounds = newNode.layoutBounds
      oldNode.qualifiedName = newNode.qualifiedName
      oldNode.layout = newNode.layout
      oldNode.renderBounds = newNode.renderBounds
      oldNode.layoutFlags = newNode.layoutFlags
      oldNode.parent = parent
      if (oldNode is ComposeViewNode && newNode is ComposeViewNode) {
        oldNode.composeFilename = newNode.composeFilename
        oldNode.composePackageHash = newNode.composePackageHash
        oldNode.composeOffset = newNode.composeOffset
        oldNode.composeLineNumber = newNode.composeLineNumber
        oldNode.composeFlags = newNode.composeFlags
        oldNode.recompositions.update(newNode.recompositions)
      }

      oldNode.children.clear()
      // Don't update or clear the drawChildren at this point. They will be refreshed by a listener after the update is complete,
      // and we can continue using the old ones for view sizing calculations until that happens.

      for (newChild in newNode.children) {
        val oldChild = oldNodes[newChild.drawId]
        if (oldChild != null && oldChild.javaClass == newChild.javaClass) {
          modified = update(oldChild, oldNode, newChild) || modified
          oldNode.children.add(oldChild)
          oldNodes.remove(newChild.drawId)
        }
        else {
          modified = true
          oldNode.children.add(newChild)
          newChild.parent = oldNode
        }
      }
      return modified
    }

    private fun ViewNode.WriteAccess.sameChildren(oldNode: ViewNode?, newNode: ViewNode?): Boolean {
      if (oldNode?.children?.size != newNode?.children?.size) {
        return false
      }
      return oldNode?.children?.indices?.all { oldNode.children[it].drawId == newNode?.children?.get(it)?.drawId } ?: true
    }
  }
}
