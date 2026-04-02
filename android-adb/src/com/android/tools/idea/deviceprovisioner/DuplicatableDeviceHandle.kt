/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.deviceprovisioner

import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.intellij.openapi.project.Project
import java.awt.Component

/** A DeviceHandle that supports a duplicate operation. */
interface DuplicatableDeviceHandle : DeviceHandle {
  /** Creates a new device based on this device, prompting the user for customization. */
  fun duplicate(project: Project?, parent: Component?)
}
