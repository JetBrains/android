/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.deviceManager.avdmanager.actions

import com.android.tools.idea.deviceManager.avdmanager.DeviceManagerConnection
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.vfs.LocalFileSystem
import java.awt.event.ActionEvent
import java.io.File

/**
 * Action to export a given device to a file
 */
class ExportDeviceAction(provider: DeviceProvider) : DeviceUiAction(provider, "Export") {
  override fun actionPerformed(e: ActionEvent) {
    val descriptor = FileSaverDescriptor("Export Location", "Select a location for the exported device", "xml")
    val homePath = System.getProperty("user.home")
    val parentPath = homePath?.let { File(it) } ?: File("/")
    val parent = LocalFileSystem.getInstance().findFileByIoFile(parentPath)
    val fileWrapper = FileChooserFactory.getInstance()
      .createSaveFileDialog(descriptor, provider.project)
      .save(parent, "device.xml")
    val device = provider.device
    if (device != null && fileWrapper != null) {
      DeviceManagerConnection.writeDevicesToFile(listOf(device), fileWrapper.file)
    }
  }

  override fun isEnabled(): Boolean = provider.device != null
}