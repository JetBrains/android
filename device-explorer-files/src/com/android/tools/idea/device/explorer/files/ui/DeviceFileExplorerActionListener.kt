/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.device.explorer.files.ui

import com.android.tools.idea.device.explorer.files.DeviceFileEntryNode

interface DeviceFileExplorerActionListener {
  val selectedNodes: List<DeviceFileEntryNode>?
  fun copyNodePaths(nodes: List<DeviceFileEntryNode>)
  fun openNodes(nodes: List<DeviceFileEntryNode>)
  fun saveNodesAs(nodes: List<DeviceFileEntryNode>)
  fun deleteNodes(nodes: List<DeviceFileEntryNode>)
  fun synchronizeNodes(nodes: List<DeviceFileEntryNode>)
  fun newFile(node: DeviceFileEntryNode)
  fun newDirectory(node: DeviceFileEntryNode)
  fun uploadFile(node: DeviceFileEntryNode)
}