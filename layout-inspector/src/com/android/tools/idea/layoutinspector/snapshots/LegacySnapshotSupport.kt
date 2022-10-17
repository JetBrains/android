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

import com.android.annotations.concurrency.Slow
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.layoutinspector.metrics.statistics.SessionStatistics
import com.android.tools.idea.layoutinspector.model.AndroidWindow
import com.android.tools.idea.layoutinspector.model.DrawViewChild
import com.android.tools.idea.layoutinspector.model.DrawViewImage
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.pipeline.legacy.LegacyPropertiesProvider
import com.android.tools.idea.layoutinspector.pipeline.legacy.LegacyTreeParser
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.io.write
import layoutinspector.snapshots.Metadata
import org.jetbrains.kotlin.idea.core.util.readString
import org.jetbrains.kotlin.idea.core.util.writeString
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

class LegacySnapshotLoader : SnapshotLoader {
  override val propertiesProvider = LegacyPropertiesProvider()
  override lateinit var metadata: SnapshotMetadata
    private set

  override val capabilities = mutableSetOf<InspectorClient.Capability>()

  override fun loadFile(file: Path, model: InspectorModel, stats: SessionStatistics): SnapshotMetadata {
    val options = LayoutInspectorCaptureOptions()

    ObjectInputStream(Files.newInputStream(file)).use { input ->
      // Parse options
      options.parse(input.readUTF())
      if (options.version != ProtocolVersion.Version1 && options.version != ProtocolVersion.Version3) {
        val message = "LegacySnapshotLoader only supports v1 and v3, got ${options.version}."
        Logger.getInstance(AppInspectionSnapshotLoader::class.java).error(message)
        throw Exception(message)
      }

      metadata = if (options.version == ProtocolVersion.Version3) {
        Metadata.parseDelimitedFrom(input).convert(ProtocolVersion.Version3)
      }
      else {
        SnapshotMetadata(ProtocolVersion.Version1)
      }


      val windows = mutableListOf<String>()
      while (input.available() > 0) {
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
        val windowName = if (options.version == ProtocolVersion.Version3) {
          input.readString()
        } else ""

        windows.add(windowName)
        val window = object : AndroidWindow(node, windowName, ImageType.BITMAP_AS_REQUESTED) {
          override fun refreshImages(scale: Double) {
            ViewNode.writeAccess {
              root.flatten().forEach { it.drawChildren.clear() }
              if (image != null) {
                root.drawChildren.add(DrawViewImage(image, root))
              }
              root.flatten().forEach { it.children.mapTo(it.drawChildren) { child -> DrawViewChild(child) } }
            }
          }
        }
        model.update(window, windows, 1)
      }
    }
    return metadata
  }
}

@Slow
fun saveLegacySnapshot(
  path: Path,
  data: Map<String, ByteArray>,
  images: Map<String, ByteArray>,
  process: ProcessDescriptor
): SnapshotMetadata {
  val baos = ByteArrayOutputStream(4096)
  val metadata: SnapshotMetadata
  ObjectOutputStream(baos).use { output ->
    output.writeUTF(LayoutInspectorCaptureOptions(ProtocolVersion.Version3).toString())

    metadata = SnapshotMetadata(
      snapshotVersion = ProtocolVersion.Version3,
      apiLevel = process.device.apiLevel,
      processName = process.name,
      source = Metadata.Source.STUDIO,
      sourceVersion = ApplicationInfo.getInstance().fullVersion,
      liveDuringCapture = false
    )

    metadata.toProto().writeDelimitedTo(output)

    for ((name, windowData) in data) {
      output.writeInt(windowData.size)
      output.write(windowData)

      // TODO: error handling?
      val imageData = images[name] ?: throw Exception()
      output.writeInt(imageData.size)
      output.write(imageData)
      output.writeString(name)
    }
  }
  path.write(baos.toByteArray())
  return metadata
}