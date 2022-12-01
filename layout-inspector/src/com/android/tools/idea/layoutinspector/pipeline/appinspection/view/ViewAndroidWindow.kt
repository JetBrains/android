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
package com.android.tools.idea.layoutinspector.pipeline.appinspection.view

import com.android.annotations.concurrency.Slow
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.resources.ScreenRound
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.model.ComponentImageLoader
import com.android.tools.idea.layoutinspector.model.DrawViewChild
import com.android.tools.idea.layoutinspector.model.DrawViewImage
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.proto.SkiaParser.RequestedNodeInfo
import com.android.tools.idea.layoutinspector.skia.ParsingFailedException
import com.android.tools.idea.layoutinspector.skia.SkiaParser
import com.android.tools.idea.layoutinspector.skia.UnsupportedPictureVersionException
import com.android.tools.idea.layoutinspector.ui.InspectorBannerService
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol
import com.android.tools.layoutinspector.BITMAP_HEADER_SIZE
import com.android.tools.layoutinspector.BitmapType
import com.android.tools.layoutinspector.InvalidPictureException
import com.android.tools.layoutinspector.LayoutInspectorUtils
import com.android.tools.layoutinspector.SkiaViewNode
import com.android.tools.layoutinspector.toInt
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import java.awt.Rectangle
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.Inflater

/**
 * An [AndroidWindow] used by the app inspection view inspector.
 *
 * @param isInterrupted A callback which will be called occasionally. If it ever returns true, we
 *    will abort our image processing at the earliest chance.
 */
class ViewAndroidWindow(
  private val project: Project,
  private val skiaParser: SkiaParser,
  root: ViewNode,
  private val event: LayoutInspectorViewProtocol.LayoutEvent,
  folderConfiguration: FolderConfiguration,
  private val isInterrupted: () -> Boolean,
  private val logEvent: (DynamicLayoutInspectorEventType) -> Unit)
  : AndroidWindow(root, root.drawId, event.screenshot.type.toImageType()) {

  private var bytes = event.screenshot.bytes.toByteArray()

  override val deviceClip =
    if (folderConfiguration.screenRoundQualifier?.value == ScreenRound.ROUND) {
      val width = folderConfiguration.screenWidthQualifier?.value
      val height = folderConfiguration.screenHeightQualifier?.value
      val dpi = folderConfiguration.densityQualifier?.value?.dpiValue
      if (width != null && height != null && dpi != null) {
        Ellipse2D.Float(0f, 0f, width * dpi / 160f, height * dpi / 160f)
      }
      else null
    }
    else null

  override fun copyFrom(other: AndroidWindow) {
    super.copyFrom(other)
    if (other is ViewAndroidWindow) {
      bytes = other.bytes
    }
  }

  @Slow
  override fun refreshImages(scale: Double) {
    if (bytes.isNotEmpty()) {
      try {
        when (imageType) {
          ImageType.BITMAP_AS_REQUESTED -> {
            val bufferedImage = processBitmap(bytes)
            createDrawChildren(bufferedImage)
            logEvent(DynamicLayoutInspectorEventType.INITIAL_RENDER_BITMAPS)
          }
          ImageType.SKP, ImageType.SKP_PENDING -> processSkp(bytes, skiaParser, project, scale)
          else -> logEvent(DynamicLayoutInspectorEventType.INITIAL_RENDER_NO_PICTURE) // Shouldn't happen
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
    skiaParser: SkiaParser,
    project: Project,
    scale: Double
  ) {
    val (nodeMap, requestedNodeInfo) = ViewNode.readAccess {
      val allNodes = root.flatten().filter { it.drawId != 0L }
      val nodeMap = allNodes.associateBy { it.drawId }
      val surfaceOriginX = root.layoutBounds.x - event.rootOffset.x
      val surfaceOriginY = root.layoutBounds.y - event.rootOffset.y
      val requests = allNodes.mapNotNull {
        val bounds = it.renderBounds.bounds.intersection(Rectangle(0, 0, Int.MAX_VALUE, Int.MAX_VALUE))
        if (bounds.isEmpty) null
        else LayoutInspectorUtils.makeRequestedNodeInfo(it.drawId, bounds.x - surfaceOriginX, bounds.y - surfaceOriginY, bounds.width,
                                                        bounds.height)
      }.toList()
      Pair(nodeMap, requests)
    }
    if (requestedNodeInfo.isEmpty()) {
      return
    }
    val (rootViewFromSkiaImage, errorMessage) = getViewTree(bytes, requestedNodeInfo, skiaParser, scale)

    if (errorMessage != null) {
      InspectorBannerService.getInstance(project)?.addNotification(errorMessage)
    }
    if (rootViewFromSkiaImage != null && rootViewFromSkiaImage.id != 0L) {
      logEvent(DynamicLayoutInspectorEventType.INITIAL_RENDER)
      ComponentImageLoader(nodeMap, rootViewFromSkiaImage).loadImages(this)
    }
  }

  /**
   * Converts [bytes] into a [BufferedImage].
   */
  private fun processBitmap(bytes: ByteArray): BufferedImage {
    val inf = Inflater().also { it.setInput(bytes) }
    val baos = ByteArrayOutputStream()
    val buffer = ByteArray(4096)
    while (!inf.finished()) {
      val count = inf.inflate(buffer)
      if (count <= 0) {
        break
      }
      baos.write(buffer, 0, count)
    }

    val inflatedBytes = baos.toByteArray()
    val width = inflatedBytes.toInt()
    val height = inflatedBytes.sliceArray(4..7).toInt()
    val bitmapType = BitmapType.fromByteVal(inflatedBytes[8])
    return bitmapType.createImage(ByteBuffer.wrap(inflatedBytes, BITMAP_HEADER_SIZE, inflatedBytes.size - BITMAP_HEADER_SIZE), width,
                                       height)
  }

  /**
   * Creates the [DrawViewImage] and [DrawViewChild]ren, which will be used to render the image and borders.
   */
  private fun createDrawChildren(image: BufferedImage) {
    ViewNode.writeAccess {
      root.flatten().forEach { it.drawChildren.clear() }
      root.drawChildren.add(DrawViewImage(image, root, deviceClip))
      root.flatten().forEach { it.children.mapTo(it.drawChildren) { child -> DrawViewChild(child) } }
    }
  }

  private fun getViewTree(
    bytes: ByteArray,
    requestedNodes: Iterable<RequestedNodeInfo>,
    skiaParser: SkiaParser,
    scale: Double
  ): Pair<SkiaViewNode?, String?> {
    var errorMessage: String? = null
    val inspectorView = try {
      skiaParser.getViewTree(bytes, requestedNodes, scale, isInterrupted)
    }
    catch (ex: InvalidPictureException) {
      // It looks like what we got wasn't an SKP at all.
      errorMessage = "Invalid picture data received from device. Rotation disabled."
      null
    }
    catch (ex: ParsingFailedException) {
      // It looked like a valid picture, but we were unable to parse it.
      errorMessage = "Invalid picture data received from device. Rotation disabled."
      null
    }
    catch (ex: UnsupportedPictureVersionException) {
      errorMessage = "No renderer supporting SKP version ${ex.version} found. Rotation disabled."
      null
    }
    catch (ex: Exception) {
      errorMessage = "Problem launching renderer. Rotation disabled."
      Logger.getInstance(ViewAndroidWindow::class.java).warn(ex)
      null
    }
    return Pair(inspectorView, errorMessage)
  }
}
