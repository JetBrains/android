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
package com.android.tools.idea.layoutinspector

import com.android.tools.idea.layoutinspector.common.StringTable
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.InspectorView
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.android.tools.idea.layoutinspector.transport.InspectorClient
import com.android.tools.layoutinspector.proto.LayoutInspectorProto
import com.android.tools.profiler.proto.Common
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.Messages
import com.intellij.util.ui.UIUtil
import java.awt.Image
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

// TODO: Set this to false before turning the dynamic layout inspector on by default
private const val DEBUG = true

private val LOAD_TIMEOUT = TimeUnit.SECONDS.toMillis(20)

class LayoutInspector(val layoutInspectorModel: InspectorModel) {
  val client = InspectorClient.createInstance(layoutInspectorModel.project)

  private val loadStartTime = AtomicLong(-1)
  private val latestLoadTime = AtomicLong(-1)

  init {
    client.register(Common.Event.EventGroupIds.LAYOUT_INSPECTOR_ERROR, ::showError)
    client.register(Common.Event.EventGroupIds.COMPONENT_TREE, ::loadComponentTree)
    client.registerProcessChanged(::clearComponentTreeWhenProcessEnds)
  }

  private fun showError(event: LayoutInspectorProto.LayoutInspectorEvent) {
    Logger.getInstance(LayoutInspector::class.java.canonicalName).warn(event.errorMessage)

    @Suppress("ConstantConditionIf")
    if (DEBUG) {
      ApplicationManager.getApplication().invokeLater {
        Messages.showErrorDialog(layoutInspectorModel.project, event.errorMessage, "Inspector Error")
      }
    }
  }
  
  private fun clearComponentTreeWhenProcessEnds() {
    if (client.isConnected) {
      return
    }
    val application = ApplicationManager.getApplication()
    application.invokeLater {
      layoutInspectorModel.update(null)
    }
  }

  private fun loadComponentTree(event: LayoutInspectorProto.LayoutInspectorEvent) {
    val time = System.currentTimeMillis()
    if (time - loadStartTime.get() < LOAD_TIMEOUT) {
      return
    }
    val root = try {
      val loader = ComponentTreeLoader(event.tree, layoutInspectorModel.resourceLookup)
      val rootView = loader.loadRootView()
      val bytes = client.getPayload(event.tree.payloadId)
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
          Logger.getInstance(LayoutInspector::class.java).warn(ex)
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
        layoutInspectorModel.update(root)
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

  private class ComponentTreeLoader(private val tree: LayoutInspectorProto.ComponentTreeEvent, private val resourceLookup: ResourceLookup?) {
    val stringTable = StringTable(tree.stringList)

    fun loadRootView(): ViewNode {
      resourceLookup?.updateConfiguration(tree.resources, stringTable)
      return loadView(tree.root, null)
    }

    fun loadView(view: LayoutInspectorProto.View, parent: ViewNode?): ViewNode {
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

}
