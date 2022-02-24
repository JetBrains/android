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
package com.android.tools.idea.run.tasks

import com.android.ddmlib.IDevice

/**
 * An interface used by [ClearLogcatTask] to find Logcat components and clear them.
 *
 * Note: If this interface is made inner to ClearLogcatTask, any Kotlin class that uses it will also depend on ClearLogcatTask and by its
 * base class LaunchTask which is in a different module. There is no need to make implementations of this class have to depend on
 * `intellij.android.deploy` so this is a top level interface.
 */
interface ClearableLogcatComponent {
  /**
   * Get the associated [IDevice] or null if not connected to a device.
   */
  fun getConnectedDevice(): IDevice?

  /**
   * Clear the logcat component.
   */
  fun clearLogcat()
}
