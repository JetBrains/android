/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.legacydevice

import com.android.ddmlib.ChunkHandler
import com.android.ddmlib.Client
import com.android.ddmlib.ClientData
import com.android.ddmlib.HandleViewDebug
import com.android.ddmlib.IDevice
import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.ui.buildDeviceName
import com.google.common.collect.Lists
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import org.jetbrains.android.sdk.AndroidSdkUtils
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.imageio.ImageIO

fun getLegacyConnectActions(
  project: Project, layoutInspector: LayoutInspector, deviceFilter: (IDevice) -> Boolean = { _ -> true }
): List<AnAction> {
  val debugBridge = AndroidSdkUtils.getDebugBridge(project) ?: return listOf()
  val result = mutableListOf<AnAction>()
  for (device in debugBridge.devices.filter(deviceFilter)) {
    val deviceName = buildDeviceName(
      device.serialNumber,
      device.getProperty(IDevice.PROP_DEVICE_MODEL) ?: "Unknown Model",
      device.getProperty(IDevice.PROP_DEVICE_MANUFACTURER) ?: "Unknown Manufacturer")
    val deviceAction = DropDownAction(deviceName, null, null)
    result.add(deviceAction)
    val sortedClients = device.clients
      .filter { it.clientData.hasFeature(ClientData.FEATURE_VIEW_HIERARCHY) }
      .sortedBy { it.clientData.clientDescription }
    for (client in sortedClients) {
      deviceAction.addAll(ListViewRootsHandler().getWindows(client, 5, TimeUnit.SECONDS)
                            .map {
                              LegacyConnectAction(client.clientData.clientDescription ?: "Unknown Process",
                                                  it, client, layoutInspector.layoutInspectorModel)
                            })
    }
  }
  return result
}

class LegacyConnectAction(name: String, val root: String, val client: Client, val model: InspectorModel) : AnAction(name) {
  override fun actionPerformed(e: AnActionEvent) {
    val hierarchyHandler = CaptureByteArrayHandler(HandleViewDebug.CHUNK_VURT)
    HandleViewDebug.dumpViewHierarchy(client, root, false, true, false, hierarchyHandler)
    val rootNode = ViewNodeParser.parse(hierarchyHandler.getData()!!)

    val imageHandler = CaptureByteArrayHandler(HandleViewDebug.CHUNK_VUOP)
    HandleViewDebug.captureView(client, root, rootNode?.hash ?: "", imageHandler)
    try {
      val data = imageHandler.getData()
      rootNode?.imageBottom = ImageIO.read(ByteArrayInputStream(data))
    }
    catch (e: IOException) {
      return
    }
    model.update(rootNode)
  }
}

private class CaptureByteArrayHandler(type: Int) : HandleViewDebug.ViewDumpHandler(type) {

  private val mData = AtomicReference<ByteArray>()

  override fun handleViewDebugResult(data: ByteBuffer) {
    val b = ByteArray(data.remaining())
    data.get(b)
    mData.set(b)
  }

  fun getData(): ByteArray? {
    waitForResult(15, TimeUnit.SECONDS)
    return mData.get()
  }
}

private class ListViewRootsHandler :
  HandleViewDebug.ViewDumpHandler(HandleViewDebug.CHUNK_VULW) {

  private val viewRoots = Lists.newCopyOnWriteArrayList<String>()

  override fun handleViewDebugResult(data: ByteBuffer) {
    val nWindows = data.int

    for (i in 0 until nWindows) {
      val len = data.int
      viewRoots.add(ChunkHandler.getString(data, len))
    }
  }

  @Throws(IOException::class)
  fun getWindows(c: Client, timeout: Long, unit: TimeUnit): List<String> {
    HandleViewDebug.listViewRoots(c, this)
    waitForResult(timeout, unit)
    return viewRoots
  }
}
