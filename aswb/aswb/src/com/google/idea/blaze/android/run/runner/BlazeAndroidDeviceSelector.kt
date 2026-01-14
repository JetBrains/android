/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.run.runner

import com.android.tools.idea.run.DeviceFutures
import com.android.tools.idea.run.editor.DeployTarget
import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project

/** Selects a device.  */
interface BlazeAndroidDeviceSelector {
  /** A device session  */
  class DeviceSession(val deployTarget: DeployTarget, val deviceFutures: DeviceFutures?)

  @Throws(ExecutionException::class)
  fun getDevice(
    project: Project,
    executor: Executor,
    env: ExecutionEnvironment,
    debug: Boolean,
    runConfigId: Int
  ): DeviceSession?

  /** Standard device selector  */
  class NormalDeviceSelector : BlazeAndroidDeviceSelector {
    override fun getDevice(
      project: Project,
      executor: Executor,
      env: ExecutionEnvironment,
      debug: Boolean,
      runConfigId: Int
    ): DeviceSession? {
      val deployTarget = BlazeDeployTargetService.getInstance(project).getDeployTarget() ?: return null
      var deviceFutures: DeviceFutures? = null
      if (!deployTarget.hasCustomRunProfileState(executor)) {
        deviceFutures = deployTarget.launchDevices(project)
      }
      return DeviceSession(deployTarget, deviceFutures)
    }
  }
}
