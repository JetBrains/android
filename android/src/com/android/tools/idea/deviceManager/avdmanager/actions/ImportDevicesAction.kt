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
import com.android.tools.idea.deviceManager.displayList.EmulatorDisplayList.Companion.deviceManager
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import java.awt.event.ActionEvent
import java.io.File

/**
 * Action to import devices from a given file
 */
class ImportDevicesAction(provider: DeviceProvider) : DeviceUiAction(provider, "Import Hardware Profiles") {
  override fun actionPerformed(e: ActionEvent) {
    val descriptor = FileChooserDescriptor(true, false, false, false, false, true)
    val homePath = System.getProperty("user.home")
    val parentPath = homePath?.let { File(it) } ?: File("/")
    val parent = LocalFileSystem.getInstance().findFileByIoFile(parentPath)
    val files = FileChooserFactory.getInstance().createFileChooser(descriptor, provider.project, null)
      .choose(null, parent)
    val importedDevices = files.flatMap {
      DeviceManagerConnection.getDevicesFromFile(VfsUtilCore.virtualToIoFile(it!!))
    }
    if (importedDevices.isNotEmpty()) {
      deviceManager.createDevices(importedDevices)
      provider.refreshDevices()
    }
  }

  override fun isEnabled(): Boolean = true
}