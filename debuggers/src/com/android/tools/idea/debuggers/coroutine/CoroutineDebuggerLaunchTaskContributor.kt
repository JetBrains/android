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
package com.android.tools.idea.debuggers.coroutine

import com.android.ddmlib.IDevice
import com.android.tools.idea.run.AndroidLaunchTaskContributor
import com.android.tools.idea.run.LaunchOptions
import com.android.tools.idea.run.tasks.LaunchTask
import com.intellij.openapi.module.Module

/**
 * Responsible for setting the am start options to start the coroutine debugger agent.
 */
class CoroutineDebuggerLaunchTaskContributor : AndroidLaunchTaskContributor {
  override fun getTask(module: Module, applicationId: String, launchOptions: LaunchOptions): LaunchTask? {
    return null
  }

  override fun getAmStartOptions(module: Module, applicationId: String, launchOptions: LaunchOptions, device: IDevice): String {
    if (!FlagController.isCoroutineDebuggerEnabled) {
      return ""
    }

    if (!launchOptions.isDebug) {
      return ""
    }

    // TODO(b/182023182) make a public accessible method in Deployer to expose the sites values
    return "--attach-agent /data/data/${applicationId}/code_cache/coroutine_debugger_agent.so"
  }
}