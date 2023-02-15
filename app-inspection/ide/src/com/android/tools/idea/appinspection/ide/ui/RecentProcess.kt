/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.appinspection.ide.ui

import com.android.ddmlib.IDevice
import com.android.tools.idea.appinspection.inspector.api.process.ProcessDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key

/**
 * Information about an Android process that was recently started from Studio.
 *
 * @param device the device the process was started on.
 * @param packageName the package name of the application.
 */
class RecentProcess(val device: IDevice, val packageName: String) {
  companion object {
    private val RECENT_PROCESS_KEY = Key.create<RecentProcess>("AppInspection.Recent.Process")

    fun get(project: Project): RecentProcess? = project.getUserData(RECENT_PROCESS_KEY)
    fun set(project: Project, process: RecentProcess?) =
      project.putUserData(RECENT_PROCESS_KEY, process)

    fun isRecentProcess(process: ProcessDescriptor, project: Project): Boolean =
      get(project)?.matches(process) ?: false
  }

  fun matches(process: ProcessDescriptor): Boolean =
    process.device.serial == device.serialNumber && process.name == packageName
}
