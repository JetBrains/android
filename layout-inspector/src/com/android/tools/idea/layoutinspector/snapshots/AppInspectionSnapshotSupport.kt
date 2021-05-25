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
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.pipeline.appinspection.AppInspectionPropertiesProvider
import com.android.tools.idea.layoutinspector.pipeline.appinspection.AppInspectionTreeLoader
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.DisconnectedViewPropertiesCache
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.ViewLayoutInspectorClient
import com.android.tools.idea.layoutinspector.pipeline.appinspection.view.ViewPropertiesCache
import com.android.tools.idea.layoutinspector.skia.SkiaParserImpl
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.write
import layoutinspector.view.inspection.LayoutInspectorViewProtocol
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.nio.file.Path

/**
 * [SnapshotLoader] that can load snapshots saved by the app inspection-based version of the layout inspector.
 */
class AppInspectionSnapshotLoader : SnapshotLoader {
  private lateinit var viewPropertiesCache: ViewPropertiesCache
  override lateinit var propertiesProvider: AppInspectionPropertiesProvider

  override fun loadFile(file: VirtualFile, model: InspectorModel) {
    viewPropertiesCache = DisconnectedViewPropertiesCache(model)
    // TODO: compose
    propertiesProvider = AppInspectionPropertiesProvider(viewPropertiesCache, null, model)
    // TODO: error handling, metrics
    val treeLoader = AppInspectionTreeLoader(model.project, {}, SkiaParserImpl({}))
    ObjectInputStream(file.inputStream).use { input ->
      input.readUTF() // Options, unused
      val api = input.readInt()
      val name = input.readUTF()
      val response = LayoutInspectorViewProtocol.CaptureSnapshotResponse.parseFrom(input.readAllBytes())
      val allWindows = response.windowSnapshotsList.associateBy { it.layout.rootView.id }
      val rootIds = response.windowRoots.idsList
      rootIds.map { allWindows[it] }.forEach { windowInfo ->
        // should always be true
        if (windowInfo != null) {
          val data = ViewLayoutInspectorClient.Data(0, rootIds, windowInfo.layout, null /* TODO: compose support */)
          val treeData = treeLoader.loadComponentTree(data, model.resourceLookup, object : ProcessDescriptor {
            override val device: DeviceDescriptor
              get() = object : DeviceDescriptor {
                override val manufacturer: String get() = "" // TODO
                override val model: String get() = "" // TODO
                override val serial: String get() = "" // TODO
                override val isEmulator: Boolean get() = false // TODO
                override val apiLevel: Int get() = api
                override val version: String get() = "" // TODO
                override val codename: String get() = "" // TODO

              }
            override val abiCpuArch: String get() = "" // TODO
            override val name: String = name
            override val isRunning: Boolean = false
            override val pid: Int get() = -1
            override val streamId: Long = -1
          }) ?: throw Exception()
          model.update(treeData.window, rootIds, treeData.generation)
          viewPropertiesCache.setAllFrom(windowInfo.properties)
        }
      }
    }
  }
}

fun saveAppInspectorSnapshot(
  path: Path,
  data: Map<Long, ViewLayoutInspectorClient.Data>,
  properties: Map<Long, LayoutInspectorViewProtocol.PropertiesEvent>,
  processDescriptor: ProcessDescriptor
) {
  val response = LayoutInspectorViewProtocol.CaptureSnapshotResponse.newBuilder().apply {
    val allRootIds = data.values.firstOrNull()?.rootIds
    addAllWindowSnapshots(allRootIds?.map { rootId ->
      LayoutInspectorViewProtocol.CaptureSnapshotResponse.WindowSnapshot.newBuilder().apply {
        layout = data[rootId]?.viewEvent
        this.properties = properties[rootId]
      }.build()
    })
    windowRoots = LayoutInspectorViewProtocol.WindowRootsEvent.newBuilder().apply {
      addAllIds(allRootIds)
    }.build()
  }.build()
  saveAppInspectorSnapshot(path, response, processDescriptor)
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

