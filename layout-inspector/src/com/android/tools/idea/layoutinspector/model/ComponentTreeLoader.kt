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

import com.android.annotations.concurrency.Slow
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.RequestedNodeInfo
import com.android.tools.idea.layoutinspector.SkiaParser
import com.android.tools.idea.layoutinspector.SkiaParserService
import com.android.tools.idea.layoutinspector.UnsupportedPictureVersionException
import com.android.tools.idea.layoutinspector.common.StringTableImpl
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.android.tools.idea.layoutinspector.transport.DefaultInspectorClient
import com.android.tools.idea.layoutinspector.transport.InspectorClient
import com.android.tools.idea.layoutinspector.ui.InspectorBannerService
import com.android.tools.layoutinspector.proto.LayoutInspectorProto
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.ComponentTreeEvent.PayloadType.PNG_AS_REQUESTED
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.ComponentTreeEvent.PayloadType.PNG_SKP_TOO_LARGE
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.ComponentTreeEvent.PayloadType.SKP
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.LowMemoryWatcher
import java.awt.Polygon
import java.awt.Rectangle
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.imageio.ImageIO

private val LOAD_TIMEOUT = TimeUnit.SECONDS.toMillis(20)

/**
 * A [TreeLoader] that uses a [DefaultInspectorClient] to fetch a view tree from an API 29+ device, and parses it into [ViewNode]s
 */
object ComponentTreeLoader : TreeLoader {

  override fun loadComponentTree(
    data: Any?,
    resourceLookup: ResourceLookup,
    client: InspectorClient,
    project: Project
  ): Pair<AndroidWindow?, Int>? {
    return loadComponentTree(data, resourceLookup, client, SkiaParser, project)
  }

  @VisibleForTesting
  fun loadComponentTree(
    maybeEvent: Any?, resourceLookup: ResourceLookup,
    client: InspectorClient,
    skiaParser: SkiaParserService,
    project: Project
  ): Pair<AndroidWindow?, Int>? {
    val event = maybeEvent as? LayoutInspectorProto.LayoutInspectorEvent ?: return null
    val window: AndroidWindow? =
      if (event.tree.hasRoot()) {
        ComponentTreeLoaderImpl(event.tree, resourceLookup).loadComponentTree(client, skiaParser, project) ?: return null
      }
      else {
        null
      }
    return Pair(window, event.tree.generation)
  }

  override fun getAllWindowIds(data: Any?, client: InspectorClient): List<Long>? {
    val event = data as? LayoutInspectorProto.LayoutInspectorEvent ?: return null
    return event.tree.allWindowIdsList
  }
}

private class ComponentTreeLoaderImpl(
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

  fun loadComponentTree(client: InspectorClient, skiaParser: SkiaParserService, project: Project): AndroidWindow? {
    val defaultClient = client as? DefaultInspectorClient ?: throw UnsupportedOperationException(
      "ComponentTreeLoaderImpl requires a DefaultClient")
    val time = System.currentTimeMillis()
    if (time - loadStartTime.get() < LOAD_TIMEOUT) {
      return null
    }
    return try {
      val rootView = loadRootView() ?: return null
      val window = AndroidWindow(rootView, rootView.drawId, tree.payloadType, tree.payloadId) @Slow { scale, window ->
        val bytes = defaultClient.getPayload(window.payloadId)
        if (bytes.isNotEmpty()) {
          val root = window.root
          try {
            when (window.imageType) {
              PNG_AS_REQUESTED, PNG_SKP_TOO_LARGE -> processPng(bytes, root, client)
              SKP -> processSkp(bytes, skiaParser, project, client, root, scale)
              else -> client.logEvent(DynamicLayoutInspectorEventType.INITIAL_RENDER_NO_PICTURE) // Shouldn't happen
            }
          }
          catch (ex: Exception) {
            // TODO: it seems like grpc can run out of memory landing us here. We should check for that.
            Logger.getInstance(LayoutInspector::class.java).warn(ex)
          }
        }
      }
      window
    }
    finally {
      loadStartTime.set(0)
    }
  }

  private fun processSkp(
    bytes: ByteArray,
    skiaParser: SkiaParserService,
    project: Project,
    client: DefaultInspectorClient,
    rootView: ViewNode,
    scale: Double
  ) {
    val allNodes = rootView.flatten().asSequence().filter { it.drawId != 0L }
    val surfaceOriginX = rootView.x - tree.rootSurfaceOffsetX
    val surfaceOriginY = rootView.y - tree.rootSurfaceOffsetY
    val requestedNodeInfo = allNodes.mapNotNull {
      val bounds = it.transformedBounds.bounds.intersection(Rectangle(0, 0, Int.MAX_VALUE, Int.MAX_VALUE))
      if (bounds.isEmpty) null
      else RequestedNodeInfo(it.drawId, bounds.width, bounds.height,
                             bounds.x - surfaceOriginX, bounds.y - surfaceOriginY)
    }.toList()
    if (requestedNodeInfo.isEmpty()) {
      return
    }
    val (rootViewFromSkiaImage, errorMessage) = getViewTree(bytes, requestedNodeInfo, skiaParser, scale)

    if (errorMessage != null) {
      InspectorBannerService.getInstance(project).setNotification(errorMessage)
    }
    if (rootViewFromSkiaImage == null || rootViewFromSkiaImage.id == 0L) {
      // We were unable to parse the skia image. Turn on screenshot mode on the device.
      client.requestScreenshotMode()
      // metrics will be logged when we come back with a bitmap
    }
    else {
      client.logEvent(DynamicLayoutInspectorEventType.INITIAL_RENDER)
      ViewNode.writeDrawChildren { drawChildren ->
        rootView.flatten().forEach { it.drawChildren().clear() }
        ComponentImageLoader(allNodes.associateBy { it.drawId }, rootViewFromSkiaImage).loadImages(drawChildren)
      }
    }
  }

  private fun processPng(bytes: ByteArray, rootView: ViewNode, client: InspectorClient) {
    ImageIO.read(ByteArrayInputStream(bytes))?.let {
      ViewNode.writeDrawChildren { drawChildren ->
        rootView.flatten().forEach { it.drawChildren().clear() }
        rootView.drawChildren().add(DrawViewImage(it, rootView))
        rootView.flatten().forEach { it.children.mapTo(it.drawChildren()) { child -> DrawViewChild(child) } }
      }
    }
    client.logEvent(DynamicLayoutInspectorEventType.INITIAL_RENDER_BITMAPS)
  }

  private fun getViewTree(
    bytes: ByteArray,
    requestedNodes: Iterable<RequestedNodeInfo>,
    skiaParser: SkiaParserService,
    scale: Double
  ): Pair<SkiaViewNode?, String?> {
    var errorMessage: String? = null
    val inspectorView = try {
      val root = skiaParser.getViewTree(bytes, requestedNodes, scale) { isInterrupted }

      if (root == null) {
        // We were unable to parse the skia image. Allow the user to interact with the component tree.
        errorMessage = "Invalid picture data received from device. Rotation disabled."
      }
      root
    }
    catch (ex: UnsupportedPictureVersionException) {
      errorMessage = "No renderer supporting SKP version ${ex.version} found. Rotation disabled."
      null
    }
    catch (ex: Exception) {
      errorMessage = "Problem launching renderer. Rotation disabled."
      Logger.getInstance(ComponentTreeLoaderImpl::class.java).warn(ex)
      null
    }
    return Pair(inspectorView, errorMessage)
  }

  private fun loadRootView(): ViewNode? {
    resourceLookup?.updateConfiguration(tree.resources, stringTable)
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

  private fun loadView(view: LayoutInspectorProto.View): ViewNode {
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
      ComposeViewNode(view.drawId, qualifiedName, layout, view.x, view.y, view.width, view.height, viewId, textValue, view.layoutFlags,
                      composeFileName, view.composePackageHash, view.composeOffset, view.composeLineNumber)
    }
    view.subViewList.map { loadView(it) }.forEach {
      node.children.add(it)
      it.parent = node
    }
    return node
  }

  private fun packagePrefix(packageName: String): String {
    return if (packageName.isEmpty()) "" else "$packageName."
  }
}

@VisibleForTesting
class ComponentImageLoader(private val nodeMap: Map<Long, ViewNode>, skiaRoot: SkiaViewNode) {
  private val nonImageSkiaNodes = skiaRoot.flatten().filter { it.image == null }.associateBy {  it.id }

  fun loadImages(drawChildren: ViewNode.() -> MutableList<DrawViewNode>) {
      for ((drawId, node) in nodeMap) {
        val remainingChildren = LinkedHashSet(node.children)
        val skiaNode = nonImageSkiaNodes[drawId]
        if (skiaNode != null) {
          for (childSkiaNode in skiaNode.children) {
            val image = childSkiaNode.image
            if (image != null) {
              node.drawChildren().add(DrawViewImage(image, node))
            }
            else {
              val viewForSkiaChild = nodeMap[childSkiaNode.id] ?: continue
              val actualChild = viewForSkiaChild.parentSequence.find { remainingChildren.contains(it) } ?: continue
              remainingChildren.remove(actualChild)
              node.drawChildren().add(DrawViewChild(actualChild))
            }
          }
        }
        remainingChildren.mapTo(node.drawChildren()) { DrawViewChild(it) }
      }
  }
}