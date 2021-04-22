/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.pipeline.transport

import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.model.ComposeViewNode
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.ComponentTreeData
import com.android.tools.idea.layoutinspector.pipeline.TreeLoader
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.android.tools.idea.layoutinspector.skia.SkiaParser
import com.android.tools.layoutinspector.proto.LayoutInspectorProto
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.LowMemoryWatcher
import java.awt.Polygon
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

private val LOAD_TIMEOUT = TimeUnit.SECONDS.toMillis(20)

/**
 * A [TreeLoader] that uses a [TransportInspectorClient] to fetch a view tree from an API 29+ device, and parses it into [ViewNode]s
 */
class TransportTreeLoader(
  private val project: Project,
  private val client: TransportInspectorClient,
  private val skiaParser: SkiaParser
) : TreeLoader {

  override fun loadComponentTree(
    data: Any?,
    resourceLookup: ResourceLookup
  ): ComponentTreeData? {
    return loadComponentTree(data, resourceLookup, skiaParser)
  }

  @VisibleForTesting
  fun loadComponentTree(maybeEvent: Any?, resourceLookup: ResourceLookup, skiaParser: SkiaParser): ComponentTreeData? {
    val event = maybeEvent as? LayoutInspectorProto.LayoutInspectorEvent ?: return null
    val window: AndroidWindow? =
      if (event.tree.hasRoot()) {
        TransportTreeLoaderImpl(event.tree, resourceLookup).loadComponentTree(client, skiaParser, project) ?: return null
      }
      else {
        null
      }
    return ComponentTreeData(window, event.tree.generation, emptySet())
  }

  override fun getAllWindowIds(data: Any?): List<Long>? {
    val event = data as? LayoutInspectorProto.LayoutInspectorEvent ?: return null
    return event.tree.allWindowIdsList
  }
}

private class TransportTreeLoaderImpl(
  private val tree: LayoutInspectorProto.ComponentTreeEvent,
  private val resourceLookup: ResourceLookup?
) {
  private val loadStartTime = AtomicLong(-1)
  private val stringTable = StringTableImpl(tree.stringList)
  // if true, exit immediately and return null
  private var isInterrupted = false

  @Suppress("unused") // Need to keep a reference to receive notifications
  private val lowMemoryWatcher = LowMemoryWatcher.register(
    {
      isInterrupted = true
    }, LowMemoryWatcher.LowMemoryWatcherType.ONLY_AFTER_GC)

  fun loadComponentTree(client: TransportInspectorClient, skiaParser: SkiaParser, project: Project): AndroidWindow? {
    val time = System.currentTimeMillis()
    if (time - loadStartTime.get() < LOAD_TIMEOUT) {
      return null
    }
    try {
      val rootView = loadRootView() ?: return null
      return TransportAndroidWindow(project, skiaParser, client, rootView, tree) { isInterrupted }
    }
    finally {
      loadStartTime.set(0)
    }
  }

  private fun loadRootView(): ViewNode? {
    resourceLookup?.updateConfiguration(tree.resources.toAppContext(), stringTable)
    if (tree.hasRoot()) {
      return try {
        loadView(tree.root)
      }
      catch (interrupted: InterruptedException) {
        null
      }
    }
    return null
  }

  private fun loadView(view: LayoutInspectorProto.View, parent: ViewNode? = null): ViewNode {
    if (isInterrupted) {
      throw InterruptedException()
    }
    val qualifiedName = packagePrefix(stringTable[view.packageName]) + stringTable[view.className]
    val viewId = stringTable[view.viewId]
    val textValue = stringTable[view.textValue]
    val layout = stringTable[view.layout]
    val transformedBounds =
      if (view.hasTransformedBounds()) {
        view.transformedBounds?.let {
          Polygon(intArrayOf(it.topLeftX, it.topRightX, it.bottomRightX, it.bottomLeftX),
                  intArrayOf(it.topLeftY, it.topRightY, it.bottomRightY, it.bottomLeftY), 4)
        }
      }
      else null
    val node = if (view.packageName != 0) {
      ViewNode(view.drawId, qualifiedName, layout, view.x, view.y, view.width, view.height, transformedBounds, viewId, textValue,
               view.layoutFlags)
    }
    else {
      val composeFileName = stringTable[view.composeFilename]
      ComposeViewNode(view.drawId, qualifiedName, layout, view.x, view.y, view.width, view.height, transformedBounds, viewId, textValue,
                      view.layoutFlags, composeFileName, view.composePackageHash, view.composeOffset, view.composeLineNumber, 0)
    }
    parent?.children?.add(node)
    node.parent = parent
    view.subViewList.forEach { loadView(it, node) }
    return node
  }

  private fun packagePrefix(packageName: String): String {
    return if (packageName.isEmpty()) "" else "$packageName."
  }
}
