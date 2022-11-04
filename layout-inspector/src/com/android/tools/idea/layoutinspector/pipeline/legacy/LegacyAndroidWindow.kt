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
package com.android.tools.idea.layoutinspector.pipeline.legacy

import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.model.DrawViewChild
import com.android.tools.idea.layoutinspector.model.DrawViewImage
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.google.wireless.android.sdk.stats.DynamicLayoutInspectorEvent
import java.awt.Image
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/**
 * An [AndroidWindow] used by legacy clients.
 */
class LegacyAndroidWindow(
  private val client: LegacyClient,
  root: ViewNode,
  private val windowName: String)
  : AndroidWindow(root, windowName, ImageType.BITMAP_AS_REQUESTED) {

  override fun refreshImages(scale: Double) {
    val image = client.latestScreenshots[windowName]?.let { pngBytes ->
      ImageIO.read(ByteArrayInputStream(pngBytes))?.let {
        val scaledWidth = (it.width * scale).toInt()
        val scaledHeight = (it.height * scale).toInt()
        if (scaledWidth > 0 && scaledHeight > 0) {
          it.getScaledInstance(scaledWidth, scaledHeight, Image.SCALE_DEFAULT)
        }
        else {
          null
        }
      }
    }
    ViewNode.writeAccess {
      root.flatten().forEach { it.drawChildren.clear() }
      if (image != null) {
        root.drawChildren.add(DrawViewImage(image, root))
      }
      root.flatten().forEach { it.children.mapTo(it.drawChildren) { child -> DrawViewChild(child) } }
      if (root.drawChildren.size != root.children.size) {
        client.logEvent(DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.COMPATIBILITY_RENDER)
      }
      else {
        client.logEvent(DynamicLayoutInspectorEvent.DynamicLayoutInspectorEventType.COMPATIBILITY_RENDER_NO_PICTURE)
      }
    }
  }
}