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
package com.android.tools.idea.layoutinspector.runningdevices

import com.intellij.openapi.project.Project

interface LayoutInspectorManager {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): LayoutInspectorManager {
      return project.getService(LayoutInspectorManager::class.java)
    }
  }

  /**
   * True if Layout Inspector is enabled, false otherwise.
   */
  val isEnabled: Boolean

  /**
   * Turn Layout Inspector on or off.
   */
  fun toggleLayoutInspector(enable: Boolean)
}

private class LayoutInspectorManagerImpl : LayoutInspectorManager {
  // TODO(b/265150325): Layout Inspector will need to be enabled on a per-tab basis. This global variable is temporary.
  override var isEnabled: Boolean = false
    private set

  // TODO(b/265150325): implement logic to inject workbench in tab from running devices.
  override fun toggleLayoutInspector(enable: Boolean) {
    isEnabled = enable
  }
}