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
import com.intellij.util.ui.UIUtil
import java.awt.Image
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
    data: Any?, resourceLookup: ResourceLookup, client: InspectorClient, project: Project
  ): Pair<ViewNode, Long>? {
    return loadComponentTree(data, resourceLookup, client, SkiaParser, project)?.let { Pair(it, it.drawId) }
  }

  @VisibleForTesting
  fun loadComponentTree(
    maybeEvent: Any?, resourceLookup: ResourceLookup, client: InspectorClient, skiaParser: SkiaParserService, project: Project
  ): ViewNode? {
    val event = maybeEvent as? LayoutInspectorProto.LayoutInspectorEvent ?: return null
    return ComponentTreeLoaderImpl(event.tree, resourceLookup).loadComponentTree(client, skiaParser, project)
  }

  override fun getAllWindowIds(data: Any?, client: InspectorClient): List<Long>? {
    val event = data as? LayoutInspectorProto.LayoutInspectorEvent ?: return null
    return event.tree.allWindowIdsList
  }
}

private class ComponentTreeLoaderImpl(
  private val tree: LayoutInspectorProto.ComponentTreeEvent, private val resourceLookup: ResourceLookup?
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

  @Slow
  fun loadComponentTree(client: InspectorClient, skiaParser: SkiaParserService, project: Project): ViewNode? {
    val defaultClient = client as? DefaultInspectorClient ?: throw UnsupportedOperationException(
      "ComponentTreeLoaderImpl requires a DefaultClient")
    val time = System.currentTimeMillis()
    if (time - loadStartTime.get() < LOAD_TIMEOUT) {
      return null
    }
    return try {
      val rootView = loadRootView() ?: return null
      rootView.imageType = tree.payloadType
      val bytes = defaultClient.getPayload(tree.payloadId)
      if (bytes.isNotEmpty()) {
        try {
          when (tree.payloadType) {
            PNG_AS_REQUESTED, PNG_SKP_TOO_LARGE -> processPng(bytes, rootView, client)
            SKP -> processSkp(bytes, skiaParser, project, client, rootView)
            else -> client.logEvent(DynamicLayoutInspectorEventType.INITIAL_RENDER_NO_PICTURE) // Shouldn't happen
          }
        }
        catch (ex: Exception) {
          Logger.getInstance(LayoutInspector::class.java).warn(ex)
        }
      }
      rootView
    }
    finally {
      loadStartTime.set(0)
    }
  }

  private fun processSkp(bytes: ByteArray,
                         skiaParser: SkiaParserService,
                         project: Project,
                         client: DefaultInspectorClient,
                         rootView: ViewNode) {
    val (rootViewFromSkiaImage, errorMessage) = getViewTree(bytes, skiaParser)

    if (errorMessage != null) {
      InspectorBannerService.getInstance(project).setNotification(errorMessage)
    }
    if (rootViewFromSkiaImage == null || rootViewFromSkiaImage.id.isEmpty()) {
      // We were unable to parse the skia image. Turn on screenshot mode on the device.
      client.requestScreenshotMode()
      // metrics will be logged when we come back with a bitmap
    }
    else {
      client.logEvent(DynamicLayoutInspectorEventType.INITIAL_RENDER)
      ComponentImageLoader(rootView, rootViewFromSkiaImage).loadImages()
    }
  }

  private fun processPng(bytes: ByteArray,
                         rootView: ViewNode,
                         client: InspectorClient) {
    ImageIO.read(ByteArrayInputStream(bytes))?.let {
      rootView.imageBottom = it
    }
    client.logEvent(DynamicLayoutInspectorEventType.INITIAL_RENDER_BITMAPS)
  }

  private fun getViewTree(bytes: ByteArray, skiaParser: SkiaParserService): Pair<InspectorView?, String?> {
    var errorMessage: String? = null
    val inspectorView = try {
      val root = skiaParser.getViewTree(bytes) { isInterrupted }
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
      null
    }
    return Pair(inspectorView, errorMessage)
  }

  private fun loadRootView(): ViewNode? {
    resourceLookup?.updateConfiguration(tree.resources, stringTable)
    if (tree.hasRoot()) {
      try {
        return loadView(tree.root)
      }
      catch (interrupted: InterruptedException) {
        return null
      }
    }
    return null
  }

  private fun loadView(view: LayoutInspectorProto.View): ViewNode {
    if (isInterrupted) {
      throw InterruptedException()
    }
    val qualifiedName = packagePrefix(stringTable[view.packageName]) + stringTable[view.className]
    val methodName = packagePrefix(stringTable[view.composePackage]) + stringTable[view.composeInvocation]
    val composeFileName = stringTable[view.composeFilename]
    val viewId = stringTable[view.viewId]
    val textValue = stringTable[view.textValue]
    val layout = stringTable[view.layout]
    val node = if (composeFileName.isEmpty()) {
      ViewNode(view.drawId, qualifiedName, layout, view.x, view.y, view.width, view.height, viewId, textValue, view.layoutFlags)
    }
    else {
      ComposeViewNode(view.drawId, qualifiedName, layout, view.x, view.y, view.width, view.height, viewId, textValue, view.layoutFlags,
                      composeFileName, methodName, view.composeLineNumber)
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

  private class ComponentImageLoader(root: ViewNode, viewRoot: InspectorView) {
    private val nodeMap = root.flatten().associateBy { it.drawId }
    private val viewMap = viewRoot.flatten().associateBy { it.id.toLong() }
    private val offset = root.bounds.location

    init {
      val rootView = viewMap[root.drawId]
      offset.translate(-1 * (rootView?.x ?: 0), -1 * (rootView?.y ?: 0))
    }

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
          beforeChildren -> node.imageBottom = combine(node.imageBottom, child, node.bounds)
          else -> node.imageTop = combine(node.imageTop, child, node.bounds)
        }
        if (!isChildNode) {
          // Some Skia views are several levels deep:
          addChildNodeImages(node, child)
        }
      }
    }

    private fun combine(image: Image?,
                        view: InspectorView,
                        bounds: Rectangle): Image? {
      if (view.image == null) {
        return image
      }
      if (image == null) {
        return view.image
      }
      @Suppress("UndesirableClassUsage")
      // Combine the images...
      val g = image.graphics
      UIUtil.drawImage(g, view.image!!, offset.x + view.x - bounds.x, offset.y + view.y - bounds.y, null)
      g.dispose()
      return image
    }
  }
}