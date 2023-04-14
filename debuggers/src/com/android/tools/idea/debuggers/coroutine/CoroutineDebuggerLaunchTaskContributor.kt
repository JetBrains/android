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
import com.android.sdklib.AndroidVersion
import com.android.tools.deployer.Sites
import com.android.tools.idea.run.AndroidLaunchTaskContributor
import com.android.tools.idea.run.AndroidRunConfigurationBase
import com.intellij.execution.Executor
import com.intellij.execution.executors.DefaultDebugExecutor

/**
 * Responsible for setting the am start options to start the coroutine debugger agent.
 */
class CoroutineDebuggerLaunchTaskContributor : AndroidLaunchTaskContributor {

  override fun getAmStartOptions(applicationId: String,
                                 configuration: AndroidRunConfigurationBase,
                                 device: IDevice,
                                 executor: Executor): String {
    if (!FlagController.isCoroutineDebuggerEnabled) {
      return ""
    }

    if (DefaultDebugExecutor.EXECUTOR_ID != executor.id) {
      return ""
    }

    // On api 27 the agent .so is not found at startup time.
    // this is probably because there is no guarantee that the code_cache folder (where we put the .so) is created during app install.
    // On api 28 the whole debugger hangs when attaching the agent.
    // for now we're disabling it, but we could investigate this further and re-enable it later on.
    // Since this check doesn't prevent the coroutine debugger panel from showing up, we should consider moving it into the agent.
    if (!device.version.isGreaterOrEqualThan(AndroidVersion.VersionCodes.Q)) {
      return ""
    }

    val isCoroutineDebuggerEnabledInSettings = CoroutineDebuggerSettings.isCoroutineDebuggerEnabled()

    CoroutineDebuggerAnalyticsTracker.getInstance(configuration.project).trackLaunchEvent(!isCoroutineDebuggerEnabledInSettings)

    // coroutine debugger can be disabled from studio settings
    if (!isCoroutineDebuggerEnabledInSettings) {
      return ""
    }

    val appCodeCache = Sites.appCodeCache(applicationId)
    return "--attach-agent ${appCodeCache}coroutine_debugger_agent.so"
  }
}