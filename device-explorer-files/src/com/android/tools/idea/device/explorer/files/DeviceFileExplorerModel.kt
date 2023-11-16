/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.device.explorer.files

import com.android.tools.idea.device.explorer.files.fs.DeviceFileSystem
import java.util.function.Consumer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.DefaultTreeSelectionModel

/**
 * The Device Explorer model class: encapsulates the list of devices,
 * their file system and also associated state changes to via the
 * [DeviceExplorerModelListener] listener class.
 */
open class DeviceFileExplorerModel {
  private val myListeners: MutableList<DeviceExplorerModelListener> = ArrayList()
  var treeModel: DefaultTreeModel? = null
    private set
  var treeSelectionModel: DefaultTreeSelectionModel? = null
    private set
  var activeDevice: DeviceFileSystem? = null
    private set

  open fun setDevice(device: DeviceFileSystem?, treeModel: DefaultTreeModel?, treeSelectionModel: DefaultTreeSelectionModel?) {
    activeDevice = device
    setActiveDeviceTreeModel(treeModel, treeSelectionModel)
  }

  fun addListener(listener: DeviceExplorerModelListener) {
    myListeners.add(listener)
  }

  fun removeListener(listener: DeviceExplorerModelListener) {
    myListeners.remove(listener)
  }

  private fun setActiveDeviceTreeModel(
    treeModel: DefaultTreeModel?,
    treeSelectionModel: DefaultTreeSelectionModel?
  ) {
    // Ignore if tree model is not changing
    if (this.treeModel == treeModel) {
      return
    }
    this.treeModel = treeModel
    this.treeSelectionModel = treeSelectionModel
    myListeners.forEach(Consumer { x: DeviceExplorerModelListener -> x.treeModelChanged(treeModel, treeSelectionModel) })
  }
}