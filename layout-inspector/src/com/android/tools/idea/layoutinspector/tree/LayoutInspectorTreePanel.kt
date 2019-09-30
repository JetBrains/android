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
package com.android.tools.idea.layoutinspector.tree

import com.android.tools.adtui.workbench.ToolContent
import com.android.tools.componenttree.api.ComponentTreeBuilder
import com.android.tools.componenttree.api.ComponentTreeModel
import com.android.tools.componenttree.api.ComponentTreeSelectionModel
import com.android.tools.componenttree.api.ViewNodeType
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.SkiaParser
import com.android.tools.idea.layoutinspector.common.StringTable
import com.android.tools.idea.layoutinspector.model.InspectorView
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.android.tools.idea.layoutinspector.transport.InspectorClient
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.ComponentTreeEvent
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.LayoutInspectorEvent
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.View
import com.android.tools.profiler.proto.Common
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.ui.UIUtil
import icons.StudioIcons
import org.jetbrains.android.dom.AndroidDomElementDescriptorProvider
import java.awt.Image
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.swing.Icon
import javax.swing.JComponent

private val LOAD_TIMEOUT = TimeUnit.SECONDS.toMillis(20)
const val GOTO_DEFINITION_ACTION_KEY = "gotoDefinition"

class LayoutInspectorTreePanel : ToolContent<LayoutInspector> {
  private var layoutInspector: LayoutInspector? = null
  private var client: InspectorClient? = null
  private val componentTree: JComponent
  private val componentTreeModel: ComponentTreeModel

  @VisibleForTesting
  val componentTreeSelectionModel: ComponentTreeSelectionModel

  private val loadStartTime = AtomicLong(-1)
  private val latestLoadTime = AtomicLong(-1)

  init {
    val builder = ComponentTreeBuilder()
      .withNodeType(InspectorViewNodeType())
      .withInvokeLaterOption { ApplicationManager.getApplication().invokeLater(it) }

    ActionManager.getInstance()?.getAction(IdeActions.ACTION_GOTO_DECLARATION)?.shortcutSet?.shortcuts
        ?.filterIsInstance<KeyboardShortcut>()
        ?.filter { it.secondKeyStroke == null }
        ?.forEach { builder.withKeyActionKey(GOTO_DEFINITION_ACTION_KEY, it.firstKeyStroke) { gotoDefinition() } }
    val (tree, model, selectionModel) = builder.build()
    componentTree = tree
    componentTreeModel = model
    componentTreeSelectionModel = selectionModel
    selectionModel.addSelectionListener { layoutInspector?.layoutInspectorModel?.selection = it.firstOrNull() as? ViewNode }
  }

  // TODO: There probably can only be 1 layout inspector per project. Do we need to handle changes?
  override fun setToolContext(toolContext: LayoutInspector?) {
    layoutInspector?.layoutInspectorModel?.modificationListeners?.remove(this::modelModified)
    layoutInspector = toolContext
    layoutInspector?.layoutInspectorModel?.modificationListeners?.add(this::modelModified)
    client = layoutInspector?.client
    client?.register(Common.Event.EventGroupIds.COMPONENT_TREE, ::loadComponentTree)
    client?.registerProcessChanged(::clearComponentTreeWhenProcessEnds)
    toolContext?.layoutInspectorModel?.selectionListeners?.add(this::selectionChanged)
  }

  override fun getComponent() = componentTree

  override fun dispose() {
  }

  private fun gotoDefinition() {
    val resourceLookup = layoutInspector?.layoutInspectorModel?.resourceLookup ?: return
    val node = componentTreeSelectionModel.selection.singleOrNull() as? ViewNode ?: return
    val location = resourceLookup.findFileLocation(node) ?: return
    location.navigatable?.navigate(true)
  }

  private fun clearComponentTreeWhenProcessEnds() {
    if (client?.isConnected == true) {
      return
    }
    val application = ApplicationManager.getApplication()
    application.invokeLater {
      val emptyRoot = ViewNode.EMPTY
      layoutInspector?.layoutInspectorModel?.update(emptyRoot)
    }
  }

  private fun loadComponentTree(event: LayoutInspectorEvent) {
    val time = System.currentTimeMillis()
    if (time - loadStartTime.get() < LOAD_TIMEOUT) {
      return
    }
    val root = try {
      val loader = ComponentTreeLoader(event.tree, layoutInspector?.layoutInspectorModel?.resourceLookup)
      val rootView = loader.loadRootView()
      val bytes = client?.getPayload(event.tree.payloadId) ?: return
      var viewRoot: InspectorView? = null
      if (bytes.isNotEmpty()) {
        try {
          viewRoot = SkiaParser.getViewTree(bytes)
          if (viewRoot != null && viewRoot.id.isEmpty()) {
            // We were unable to parse the skia image. Allow the user to interact with the component tree.
            viewRoot = null
          }
        }
        catch (ex: Exception) {
          Logger.getInstance(LayoutInspectorTreePanel::class.java).warn(ex)
        }
      }
      if (viewRoot != null) {
        val imageLoader = ComponentImageLoader(rootView, viewRoot)
        imageLoader.loadImages()
      }
      rootView
    }
    finally {
      loadStartTime.set(0)
    }

    ApplicationManager.getApplication().invokeLater {
      synchronized(latestLoadTime) {
        if (latestLoadTime.get() > time) {
          return@invokeLater
        }
        latestLoadTime.set(time)
        layoutInspector?.layoutInspectorModel?.update(root)
      }
    }
  }

  class ComponentImageLoader(root: ViewNode, viewRoot: InspectorView) {
    private val nodeMap = root.flatten().associateBy { it.drawId }
    private val viewMap = viewRoot.flatten().associateBy { it.id.toLong() }

    fun loadImages() {
      for ((drawId, node) in nodeMap) {
        val view = viewMap[drawId] ?: continue
        node.imageBottom = view.image
        addChildNodeImages(node, view)
      }
    }

    private fun addChildNodeImages(node: ViewNode, view: InspectorView) {
      var beforeChildren = true
      for (child in view.children.values) {
        val isChildNode = view.id != child.id && nodeMap.containsKey(child.id.toLong())
        when {
          isChildNode -> beforeChildren = false
          beforeChildren -> node.imageBottom = combine(node.imageBottom, child)
          else -> node.imageTop = combine(node.imageTop, child)
        }
        if (!isChildNode) {
          // Some Skia views are several levels deep:
          addChildNodeImages(node, child)
        }
      }
    }

    private fun combine(image: Image?, view: InspectorView): Image? =
      when {
        view.image == null -> image
        image == null -> view.image
        else -> {
          // Combine the images...
          val g = image.graphics
          UIUtil.drawImage(g, view.image!!, 0, 0, null)
          g.dispose()
          image
        }
      }
  }

  private class ComponentTreeLoader(private val tree: ComponentTreeEvent, private val resourceLookup: ResourceLookup?) {
    val stringTable = StringTable(tree.stringList)

    fun loadRootView(): ViewNode {
      resourceLookup?.updateConfiguration(tree.resources, stringTable)
      return loadView(tree.root, null)
    }

    fun loadView(view: View, parent: ViewNode?): ViewNode {
      val qualifiedName = "${stringTable[view.packageName]}.${stringTable[view.className]}"
      val viewId = stringTable[view.viewId]
      val textValue = stringTable[view.textValue]
      val layout = stringTable[view.layout]
      val x = view.x + (parent?.x ?: 0)
      val y = view.y + (parent?.y ?: 0)
      val node = ViewNode(view.drawId, qualifiedName, layout, x, y, view.width, view.height, viewId, textValue)
      view.subViewList.map { loadView(it, node) }.forEach {
        node.children.add(it)
        it.parent = node
      }
      return node
    }
  }

  @Suppress("UNUSED_PARAMETER")
  private fun modelModified(oldView: ViewNode?, newView: ViewNode?, structuralChange: Boolean) {
    if (structuralChange) {
      componentTreeModel.treeRoot = newView
    }
  }

  @Suppress("UNUSED_PARAMETER")
  private fun selectionChanged(oldView: ViewNode?, newView: ViewNode?) {
    if (newView == null) {
      componentTreeSelectionModel.selection = emptyList()
    }
    else {
      componentTreeSelectionModel.selection = Collections.singletonList(newView)
    }
  }

  private class InspectorViewNodeType : ViewNodeType<ViewNode>() {
    override val clazz = ViewNode::class.java

    override fun tagNameOf(node: ViewNode) = node.qualifiedName

    override fun idOf(node: ViewNode) = node.viewId?.name

    override fun textValueOf(node: ViewNode) = node.textValue

    override fun iconOf(node: ViewNode): Icon =
      AndroidDomElementDescriptorProvider.getIconForViewTag(node.unqualifiedName) ?: StudioIcons.LayoutEditor.Palette.UNKNOWN_VIEW

    override fun parentOf(node: ViewNode) = node.parent

    override fun childrenOf(node: ViewNode) = node.children
  }
}
