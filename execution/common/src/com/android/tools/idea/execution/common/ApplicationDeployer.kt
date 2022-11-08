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
import com.android.tools.deployer.Deployer
import com.android.tools.deployer.DeployerException
import com.android.tools.idea.run.ApkInfo

/**
 * Deploys one app, described as [ApkInfo], at a time.
 */
interface ApplicationDeployer {
  @Throws(DeployerException::class)
  fun fullDeploy(device: IDevice, app: ApkInfo, deployOptions: DeployOptions): Deployer.Result

  @Throws(DeployerException::class)
  fun applyChangesDeploy(device: IDevice, app: ApkInfo, deployOptions: DeployOptions): Deployer.Result

  @Throws(DeployerException::class)
  fun applyCodeChangesDeploy(device: IDevice, app: ApkInfo, deployOptions: DeployOptions): Deployer.Result
}

data class DeployOptions(
  val disabledDynamicFeatures: List<String>,
  var pmInstallFlags: String,
  val installOnAllUsers: Boolean,
  val alwaysInstallWithPm: Boolean
)