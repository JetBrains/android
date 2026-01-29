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

import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.resources.ScreenRound
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.model.DrawViewChild
import com.android.tools.idea.layoutinspector.model.DrawViewImage
import com.android.tools.idea.layoutinspector.model.NotificationModel
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.view.inspection.LayoutInspectorViewProtocol
import com.android.tools.layoutinspector.BITMAP_HEADER_SIZE
import com.android.tools.layoutinspector.BitmapType
import com.android.tools.layoutinspector.toInt
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType
import com.intellij.openapi.diagnostic.Logger
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.Inflater

/** An [AndroidWindow] used by the app inspection view inspector. */
class ViewAndroidWindow(
  private val notificationModel: NotificationModel,
  root: ViewNode,
  event: LayoutInspectorViewProtocol.LayoutEvent,
  folderConfiguration: FolderConfiguration,
  private val logEvent: (DynamicLayoutInspectorEventType) -> Unit,
) :
  AndroidWindow(
    root = root,
    displayId = if (event.rootView.hasDisplayId()) event.rootView.displayId else null,
    id = root.drawId,
    imageType = event.screenshot.type.toImageType(),
  ) {

  // capturing screenshots can be disabled, in which case the event will have no screenshot
  private var screenshotBytes = if (event.hasScreenshot()) event.screenshot.bytes.toByteArray() else null

  override var image: BufferedImage? = null

  val isXr: Boolean = event.isXr

  override val deviceClip =
    if (folderConfiguration.screenRoundQualifier?.value == ScreenRound.ROUND) {
      val width = folderConfiguration.screenWidthQualifier?.value
      val height = folderConfiguration.screenHeightQualifier?.value
      val dpi = folderConfiguration.densityQualifier?.value?.dpiValue
      if (width != null && height != null && dpi != null) {
        Ellipse2D.Float(0f, 0f, width * dpi / 160f, height * dpi / 160f)
      } else null
    } else null

  override fun copyFrom(other: AndroidWindow) {
    super.copyFrom(other)
    if (other is ViewAndroidWindow) {
      screenshotBytes = other.screenshotBytes
    }
  }

  override fun refreshImages(scale: Double) {
    try {
      val immutableScreenshotBytes = screenshotBytes
      if (immutableScreenshotBytes == null) {
        createDrawChildren(null)
        image = null
      } else {
        if (immutableScreenshotBytes.isNotEmpty()) {
          when (imageType) {
            ImageType.BITMAP_AS_REQUESTED -> {
              val bufferedImage = processBitmap(immutableScreenshotBytes)
              image = bufferedImage
              createDrawChildren(bufferedImage)
              logEvent(DynamicLayoutInspectorEventType.INITIAL_RENDER_BITMAPS)
            }
            else -> {
              image = null
              logEvent(DynamicLayoutInspectorEventType.INITIAL_RENDER_NO_PICTURE) // Shouldn't happen
            }
          }
        }
      }
    } catch (ex: Exception) {
      image = null
      // TODO: it seems like grpc can run out of memory landing us here. We should check for that.
      Logger.getInstance(LayoutInspector::class.java).warn(ex)
    }
  }

  /**
   * Creates the [DrawViewImage] and [DrawViewChild]ren, which will be used to render the image and borders. The image is optional, so the
   * [DrawViewImage] might not be created.
   */
  private fun createDrawChildren(image: BufferedImage?) {
    ViewNode.writeAccess {
      val views = root.flattenedList()
      views.forEach { it.drawChildren.clear() }
      if (image != null) {
        root.drawChildren.add(DrawViewImage(image, root, deviceClip))
      }
      views.forEach { it.children.mapTo(it.drawChildren) { child -> DrawViewChild(child) } }
    }
  }
}

/** Converts [bytes] into a [BufferedImage]. */
@VisibleForTesting
fun processBitmap(bytes: ByteArray): BufferedImage {
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
  return bitmapType.createImage(ByteBuffer.wrap(inflatedBytes, BITMAP_HEADER_SIZE, inflatedBytes.size - BITMAP_HEADER_SIZE), width, height)
}
