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
package com.android.tools.idea.layoutinspector.snapshots

import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.model.DrawViewChild
import com.android.tools.idea.layoutinspector.model.DrawViewImage
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.legacy.LegacyPropertiesProvider
import com.android.tools.idea.layoutinspector.pipeline.legacy.LegacyTreeParser
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.vfs.VirtualFile
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.ObjectInputStream
import javax.imageio.ImageIO

class LegacySnapshotLoader {
  val propertiesProvider = LegacyPropertiesProvider()

  fun loadFile(file: VirtualFile, model: InspectorModel) {
    val options = LayoutInspectorCaptureOptions()

    ObjectInputStream(file.inputStream).use { input ->
      // Parse options
      options.parse(input.readUTF())

      // Parse view node
      val nodeBytes = ByteArray(input.readInt())
      input.readFully(nodeBytes)
      val propertyUpdater = LegacyPropertiesProvider.Updater(model)
      val (node, _) = LegacyTreeParser.parseLiveViewNode(
        nodeBytes, propertyUpdater
      ) ?: throw IOException("Error parsing view node")
      propertyUpdater.apply(propertiesProvider)

      // Preview image
      val previewBytes = ByteArray(input.readInt())
      input.readFully(previewBytes)
      val image = ImageIO.read(ByteArrayInputStream(previewBytes))

      val window = object: AndroidWindow(node, "window", ImageType.BITMAP_AS_REQUESTED) {
        override fun refreshImages(scale: Double) {
          ViewNode.writeDrawChildren { drawChildren ->
            root.flatten().forEach { it.drawChildren().clear() }
            if (image != null) {
              root.drawChildren().add(DrawViewImage(image, root))
            }
            root.flatten().forEach { it.children.mapTo(it.drawChildren()) { child -> DrawViewChild(child) } }
          }
        }
      }
      model.update(window, listOf("window"), 1)
    }
  }
}

enum class ProtocolVersion(val value: String) {
  Version1("1"),
  Version2("2")
}

class LayoutInspectorCaptureOptions {

  var version = ProtocolVersion.Version1
  var title = ""

  override fun toString(): String {
    return serialize()
  }

  private fun serialize(): String {
    val obj = JsonObject()
    obj.addProperty(VERSION, version.value)
    obj.addProperty(TITLE, title)
    return obj.toString()
  }

  fun parse(json: String) {
    val obj = JsonParser().parse(json).asJsonObject
    version = ProtocolVersion.valueOf("Version${obj.get(VERSION).asString}")
    title = obj.get(TITLE).asString
  }

  companion object {
    private val VERSION = "version"
    private val TITLE = "title"
  }
}