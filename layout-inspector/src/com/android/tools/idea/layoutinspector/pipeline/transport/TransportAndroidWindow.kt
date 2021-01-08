/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.annotations.concurrency.Slow
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.SkiaParserService
import com.android.tools.idea.layoutinspector.UnsupportedPictureVersionException
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.model.ComponentImageLoader
import com.android.tools.idea.layoutinspector.model.DrawViewChild
import com.android.tools.idea.layoutinspector.model.DrawViewImage
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.proto.SkiaParser
import com.android.tools.idea.layoutinspector.ui.InspectorBannerService
import com.android.tools.layoutinspector.LayoutInspectorUtils
import com.android.tools.layoutinspector.SkiaViewNode
import com.android.tools.layoutinspector.proto.LayoutInspectorProto
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.awt.Rectangle
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

private fun LayoutInspectorProto.ComponentTreeEvent.PayloadType.toImageType(): AndroidWindow.ImageType {
  return when (this) {
    LayoutInspectorProto.ComponentTreeEvent.PayloadType.SKP -> AndroidWindow.ImageType.SKP
    LayoutInspectorProto.ComponentTreeEvent.PayloadType.PNG_SKP_TOO_LARGE -> AndroidWindow.ImageType.PNG_SKP_TOO_LARGE
    LayoutInspectorProto.ComponentTreeEvent.PayloadType.PNG_AS_REQUESTED -> AndroidWindow.ImageType.PNG_AS_REQUESTED
    else -> AndroidWindow.ImageType.UNKNOWN
  }
}

/**
 * An [AndroidWindow] used by transport clients.
 *
 * @param isInterrupted A callback which will be called occasionally. If it ever returns true, we
 *    will abort our image processing at the earliest chance.
 */
class TransportAndroidWindow(
  private val project: Project,
  private val skiaParser: SkiaParserService,
  private val client: TransportInspectorClient,
  root: ViewNode,
  private val tree: LayoutInspectorProto.ComponentTreeEvent,
  private val isInterrupted: () -> Boolean)
  : AndroidWindow(root, root.drawId, tree.payloadType.toImageType()) {

  private var payloadId: Int = tree.payloadId

  override fun copyFrom(other: AndroidWindow) {
    super.copyFrom(other)
    if (other is TransportAndroidWindow) {
      payloadId = other.payloadId
    }
  }

  @Slow
  override fun doRefreshImages(scale: Double) {
    val bytes = client.getPayload(payloadId)
    if (bytes.isNotEmpty()) {
      try {
        when (imageType) {
          ImageType.PNG_AS_REQUESTED, ImageType.PNG_SKP_TOO_LARGE -> processPng(bytes, root, client)
          ImageType.SKP -> processSkp(bytes, skiaParser, project, client, root, scale)
          else -> client.logEvent(DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.INITIAL_RENDER_NO_PICTURE) // Shouldn't happen
        }
      }
      catch (ex: Exception) {
        // TODO: it seems like grpc can run out of memory landing us here. We should check for that.
        Logger.getInstance(LayoutInspector::class.java).warn(ex)
      }
    }
  }

  private fun processSkp(
    bytes: ByteArray,
    skiaParser: SkiaParserService,
    project: Project,
    client: TransportInspectorClient,
    rootView: ViewNode,
    scale: Double
  ) {
    val allNodes = rootView.flatten().asSequence().filter { it.drawId != 0L }
    val surfaceOriginX = rootView.x - tree.rootSurfaceOffsetX
    val surfaceOriginY = rootView.y - tree.rootSurfaceOffsetY
    val requestedNodeInfo = allNodes.mapNotNull {
      val bounds = it.transformedBounds.bounds.intersection(Rectangle(0, 0, Int.MAX_VALUE, Int.MAX_VALUE))
      if (bounds.isEmpty) null
      else LayoutInspectorUtils.makeRequestedNodeInfo(it.drawId, bounds.x - surfaceOriginX, bounds.y - surfaceOriginY, bounds.width,
                                                      bounds.height)
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
      client.logEvent(DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.INITIAL_RENDER)
      ViewNode.writeDrawChildren { drawChildren ->
        rootView.flatten().forEach { it.drawChildren().clear() }
        ComponentImageLoader(allNodes.associateBy { it.drawId }, rootViewFromSkiaImage).loadImages(drawChildren)
      }
    }
  }

  private fun processPng(bytes: ByteArray, rootView: ViewNode, client: TransportInspectorClient) {
    ImageIO.read(ByteArrayInputStream(bytes))?.let {
      ViewNode.writeDrawChildren { drawChildren ->
        rootView.flatten().forEach { it.drawChildren().clear() }
        rootView.drawChildren().add(DrawViewImage(it, rootView))
        rootView.flatten().forEach { it.children.mapTo(it.drawChildren()) { child -> DrawViewChild(child) } }
      }
    }
    client.logEvent(DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.INITIAL_RENDER_BITMAPS)
  }

  private fun getViewTree(
    bytes: ByteArray,
    requestedNodes: Iterable<SkiaParser.RequestedNodeInfo>,
    skiaParser: SkiaParserService,
    scale: Double
  ): Pair<SkiaViewNode?, String?> {
    var errorMessage: String? = null
    val inspectorView = try {
      val root = skiaParser.getViewTree(bytes, requestedNodes, scale, isInterrupted)

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
      Logger.getInstance(TransportAndroidWindow::class.java).warn(ex)
      null
    }
    return Pair(inspectorView, errorMessage)
  }
}