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
package com.android.tools.idea.run.configuration.execution

import com.android.ddmlib.IDevice
import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.run.editor.DeployTarget
import com.android.tools.idea.run.editor.DeployTargetState
import com.google.common.collect.ImmutableList
import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project

/** Deploy target  that provides the one device assigned during init.  */
class TestDeployTarget(val devices: List<IDevice>) : DeployTarget {
  constructor(device: IDevice) : this(listOf(device))

  override fun hasCustomRunProfileState(executor: Executor): Boolean {
    return false
  }

  @Throws(ExecutionException::class)
  override fun getRunProfileState(
    executor: Executor, env: ExecutionEnvironment, state: DeployTargetState): RunProfileState {
    throw UnsupportedOperationException()
  }

  override fun getDevices(project: Project): DeviceFutures {
    return DeviceFutures.forDevices(devices)
  }
}
