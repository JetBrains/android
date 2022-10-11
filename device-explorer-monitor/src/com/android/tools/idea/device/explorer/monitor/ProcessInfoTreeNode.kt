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
package com.android.tools.idea.device.explorer.monitor

import com.android.tools.idea.device.monitor.processes.ProcessInfo
import com.android.tools.idea.device.monitor.processes.safeProcessName

/**
 * A [ProcessTreeNode] representing a [ProcessInfo].
 */
class ProcessInfoTreeNode(var processInfo: ProcessInfo) : ProcessTreeNode() {
  override fun toString(): String {
    return "${processInfo.safeProcessName} (${processInfo.pid})"
  }

  companion object {
    @JvmStatic
    fun fromNode(value: Any?): ProcessInfoTreeNode? {
      return if (value is ProcessInfoTreeNode) value else null
    }
  }
}