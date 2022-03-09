/*
 * Copyright (C) 2019 The Android Open Source Project
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
import com.android.tools.idea.run.AndroidProcessHandler
import com.android.tools.idea.run.LaunchInfo
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent


/**
 * Creates [ReattachingConnectDebuggerTask] based on the given [baseConnector] bound with the given [masterAndroidProcessName].
 *
 * The returned reattaching connector keeps trying to reattach the debugger to the target processes even after those processes
 * are terminated as long as the master process is alive. This setup is useful for some cases such as AndroidTestOrchestrator
 * spawns a test process per test case sequentially.
 */
fun createReattachingConnectDebuggerTaskWithMasterAndroidProcessName(baseConnector: ConnectDebuggerTaskBase,
                                                                     masterAndroidProcessName: String): ReattachingConnectDebuggerTask {
  return ReattachingConnectDebuggerTask(
    baseConnector,
    object : ReattachingConnectDebuggerTaskListener {
      override fun onStart(launchInfo: LaunchInfo,
                           device: IDevice,
                           controller: ReattachingConnectDebuggerController) {
        val masterProcessHandler = AndroidProcessHandler(launchInfo.env.project, masterAndroidProcessName)
        masterProcessHandler.addTargetDevice(device)
        masterProcessHandler.addProcessListener(object : ProcessAdapter() {
          override fun processTerminated(event: ProcessEvent) {
            // Stop the reattaching debug connector task as soon as the master process is terminated.
            controller.stop()
          }
        })
        masterProcessHandler.startNotify()
      }
    })
}