/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.legacyselector

import com.android.adblib.DeviceSelector
import com.android.ddmlib.IDevice
import com.android.sdklib.deviceprovisioner.DeviceProvisioner
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerService
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.guava.future
import java.util.Optional
import javax.swing.Icon


class ProvisionerHelper internal constructor(private val scope: CoroutineScope, private val provisioner: DeviceProvisioner) {
  fun getIcon(device: IDevice): ListenableFuture<Optional<Icon>> {
    return scope.future {
      val selector = DeviceSelector.fromSerialNumber(device.serialNumber)
      Optional.ofNullable(provisioner.findConnectedDeviceHandle(selector)?.state?.properties?.icon)
    }
  }
  companion object {
    fun newInstance(scope: CoroutineScope, project: Project): ProvisionerHelper {
      return ProvisionerHelper(scope, project.service<DeviceProvisionerService>().deviceProvisioner)
    }
  }
}
