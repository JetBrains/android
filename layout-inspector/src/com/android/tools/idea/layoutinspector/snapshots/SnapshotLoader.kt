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

import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.properties.PropertiesProvider
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.vfs.VirtualFile
import java.io.ObjectInputStream

/**
 * Mechanism for loading saved layout inspector snapshots. [SnapshotLoader.createSnapshotLoader] will create an appropriate concrete
 * [SnapshotLoader] given a snapshot file as input.
 */
interface SnapshotLoader {

  val propertiesProvider: PropertiesProvider
  fun loadFile(file: VirtualFile, model: InspectorModel)

  companion object {
    fun createSnapshotLoader(file: VirtualFile): SnapshotLoader? {
      val options = LayoutInspectorCaptureOptions()

      ObjectInputStream(file.inputStream).use { input ->
        // Parse options
        options.parse(input.readUTF())
      }
      return when (options.version) {
        ProtocolVersion.Version1 -> LegacySnapshotLoader()
        ProtocolVersion.Version2 -> null // Seems like version 2 was never implemented?
        ProtocolVersion.Version3 -> AppInspectionSnapshotLoader()
      }
    }
  }
}

enum class ProtocolVersion(val value: String) {
  Version1("1"), // Legacy layout inspector + new inspector for API <= 28
  Version2("2"), // Legacy version that was never implemented
  Version3("3")  // Live layout inspector for API >= 29
}

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
    version = ProtocolVersion.valueOf("Version${obj.get(VERSION).asString}")
    title = obj.get(TITLE).asString
  }

  companion object {
    private val VERSION = "version"
    private val TITLE = "title"
  }
}