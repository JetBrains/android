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
package com.android.tools.idea.device.monitor.ui.menu.item

import com.android.tools.idea.device.monitor.ui.DeviceMonitorActionsListener
import com.android.tools.idea.device.monitor.ProcessTreeNode

/**
 * A [TreeMenuItem] that is active only for single element selections
 */
abstract class SingleSelectionTreeMenuItem(listener: DeviceMonitorActionsListener) : TreeMenuItem(listener) {
  override fun isEnabled(nodes: List<ProcessTreeNode>): Boolean {
    return super.isEnabled(nodes) && nodes.size == 1
  }

  override fun isVisible(nodes: List<ProcessTreeNode>): Boolean {
    return super.isVisible(nodes) && nodes.size == 1
  }

  override fun run(nodes: List<ProcessTreeNode>) {
    if (nodes.size == 1) {
      run(nodes[0])
    }
  }

  abstract fun run(node: ProcessTreeNode)
}