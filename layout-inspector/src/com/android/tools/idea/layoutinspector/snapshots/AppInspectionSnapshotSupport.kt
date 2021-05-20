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

import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.ViewLayoutInspectorClient
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.ViewPropertiesCache
import com.android.tools.idea.layoutinspector.properties.PropertiesProvider
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.write
import layoutinspector.view.inspection.LayoutInspectorViewProtocol
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream
import java.nio.file.Path

/**
 * [SnapshotLoader] that can load snapshots saved by the app inspection-based version of the layout inspector.
 */
class AppInspectionSnapshotLoader : SnapshotLoader {
  override val propertiesProvider: PropertiesProvider
    get() = throw NotImplementedError() // TODO

  override fun loadFile(file: VirtualFile, model: InspectorModel) {
    // TODO
  }
}

fun saveAppInspectorSnapshot(path: Path, data: MutableMap<Long, ViewLayoutInspectorClient.Data>, propertiesCache: ViewPropertiesCache) {
  // TODO
}

fun saveAppInspectorSnapshot(path: Path, data: LayoutInspectorViewProtocol.CaptureSnapshotResponse, processDescriptor: ProcessDescriptor) {
  val serializedProto = data.toByteArray()
  val output = ByteArrayOutputStream()
  ObjectOutputStream(output).use { objectOutput ->
    objectOutput.writeUTF(LayoutInspectorCaptureOptions(ProtocolVersion.Version3, "TODO").toString())
    objectOutput.writeInt(processDescriptor.device.apiLevel)
    objectOutput.writeUTF(processDescriptor.name)
    objectOutput.write(serializedProto)
  }
  path.write(output.toByteArray())
}

