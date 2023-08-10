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
package com.android.tools.idea.execution.common

import com.android.ddmlib.IDevice
import com.android.sdklib.AndroidVersion
import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key

class AndroidSessionInfo private constructor(val devices: List<IDevice>, val applicationId: String) {

  companion object {
    val KEY = Key<AndroidSessionInfo>("KEY")
    val ANDROID_DEVICE_API_LEVEL = Key<AndroidVersion>("ANDROID_DEVICE_API_LEVEL")

    @JvmStatic
    fun create(
      processHandler: ProcessHandler,
      devices: List<IDevice>,
      applicationId: String
    ): AndroidSessionInfo {
      val result = AndroidSessionInfo(devices, applicationId)
      processHandler.putUserData(KEY, result)
      return result
    }

    fun from(processHandler: ProcessHandler): AndroidSessionInfo? = processHandler.getUserData(KEY)

    /**
     * Find all the actively running session in the given project.
     */
    @JvmStatic
    fun findActiveSession(project: Project): List<AndroidSessionInfo> {
      return ExecutionManager.getInstance(project).getRunningProcesses().mapNotNull { handler -> handler.getUserData(KEY) }
    }
  }
}
