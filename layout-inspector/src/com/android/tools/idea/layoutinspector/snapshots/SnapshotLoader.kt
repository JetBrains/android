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

import com.android.tools.idea.appinspection.inspector.api.process.DeviceDescriptor
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.layoutinspector.metrics.statistics.SessionStatistics
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.pipeline.InspectorClient
import com.android.tools.idea.layoutinspector.properties.PropertiesProvider
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.ObjectInputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * Mechanism for loading saved layout inspector snapshots. [SnapshotLoader.createSnapshotLoader] will create an appropriate concrete
 * [SnapshotLoader] given a snapshot file as input.
 */
interface SnapshotLoader {

  val propertiesProvider: PropertiesProvider
  val metadata: SnapshotMetadata?
  val processDescriptor: ProcessDescriptor
    get() = object : ProcessDescriptor {
      override val device: DeviceDescriptor
        get() = object : DeviceDescriptor {
          override val manufacturer: String get() = "" // TODO
          override val model: String get() = "" // TODO
          override val serial: String get() = "" // TODO
          override val isEmulator: Boolean get() = false // TODO
          override val apiLevel: Int get() = metadata?.apiLevel ?: 0
          override val version: String get() = "" // TODO
          override val codename: String get() = "" // TODO

        }
      override val abiCpuArch: String get() = "" // TODO
      override val name: String = metadata?.processName ?: "Unknown"
      override val isRunning: Boolean = false
      override val pid: Int get() = -1
      override val streamId: Long = -1
    }

  val capabilities: MutableCollection<InspectorClient.Capability>

  fun loadFile(file: Path, model: InspectorModel, stats: SessionStatistics): SnapshotMetadata?

  companion object {
    fun createSnapshotLoader(file: Path): SnapshotLoader? {
      val options = LayoutInspectorCaptureOptions()

      ObjectInputStream(Files.newInputStream(file)).use { input ->
        // Parse options
        options.parse(input.readUTF())
      }
      return when (options.version) {
        ProtocolVersion.Version1, ProtocolVersion.Version3 -> LegacySnapshotLoader()
        ProtocolVersion.Version2 -> null // Seems like version 2 was never implemented?
        ProtocolVersion.Version4 -> AppInspectionSnapshotLoader()
      }
    }
  }
}

class SnapshotLoaderException(reason: String) : Exception(reason)

enum class ProtocolVersion(val value: String) {
  Version1("1"), // Legacy layout inspector
  Version2("2"), // Legacy version that was never implemented
  Version3("3"), // new inspector for API <= 28
  Version4("4")  // Live layout inspector for API >= 29
}

private const val VERSION = "version"
private const val TITLE = "title"

class LayoutInspectorCaptureOptions(
  var version: ProtocolVersion = ProtocolVersion.Version1,
  var title: String = ""
) {

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
    try {
      version = ProtocolVersion.valueOf("Version${obj.get(VERSION).asString}")
    }
    catch (exception: IllegalArgumentException) {
      throw SnapshotLoaderException("This version of Studio doesn't support version ${obj.get(VERSION).asString} snapshots.")
    }
    title = obj.get(TITLE).asString
  }
}