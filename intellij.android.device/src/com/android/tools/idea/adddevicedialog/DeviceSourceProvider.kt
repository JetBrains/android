/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.adddevicedialog

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

interface DeviceSourceProvider {
  /**
   * Creates a DeviceSource with the given Project.
   *
   * The project may be null if there is no project present. Implementations may return null if they
   * require a project, or if they do not support the given project.
   */
  fun createDeviceSource(project: Project?): DeviceSource?

  companion object {
    @JvmStatic
    val EP_NAME: ExtensionPointName<DeviceSourceProvider> =
      ExtensionPointName.create("com.android.tools.idea.adddevicedialog.deviceSourceProvider")

    @JvmStatic
    val deviceSourceProviders: List<DeviceSourceProvider>
      get() = EP_NAME.extensionList
  }
}
