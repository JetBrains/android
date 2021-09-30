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
package com.android.tools.idea.run.configuration.execution

import com.android.tools.deployer.model.component.AppComponent.Mode
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.runners.ExecutionEnvironment


class AndroidWatchFaceConfigurationExecutor(private val environment: ExecutionEnvironment) :
  AndroidWearConfigurationExecutorBase(environment) {

  override fun doOnDevice(deviceWearConfigurationExecutionSession: DeviceWearConfigurationExecutionSession) {
    val isDebug = environment.executor.id == DefaultDebugExecutor.EXECUTOR_ID

    val app = deviceWearConfigurationExecutionSession.installAppOnDevice()
    deviceWearConfigurationExecutionSession.activateComponent(app, if (isDebug) Mode.DEBUG else Mode.RUN)
    if (isDebug) {
      deviceWearConfigurationExecutionSession.attachDebuggerToClient()
    }
  }
}
