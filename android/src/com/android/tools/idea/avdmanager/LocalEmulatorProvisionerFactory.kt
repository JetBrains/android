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
package com.android.tools.idea.avdmanager

import com.android.sdklib.deviceprovisioner.LocalEmulatorProvisionerPlugin
import com.android.sdklib.internal.avd.AvdInfo
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.avdmanager.AvdLaunchListener.RequestType
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerFactory
import com.intellij.openapi.project.Project
import kotlinx.coroutines.withContext

/** Builds a LocalEmulatorProvisionerPlugin with its dependencies provided by Studio. */
class LocalEmulatorProvisionerFactory : DeviceProvisionerFactory {
  override fun create(project: Project): LocalEmulatorProvisionerPlugin {
    val avdManagerConnection = AvdManagerConnection.getDefaultAvdManagerConnection()
    val avdManager =
      object : LocalEmulatorProvisionerPlugin.AvdManager {
        override suspend fun rescanAvds() =
          withContext(AndroidDispatchers.diskIoThread) { avdManagerConnection.getAvds(true) }

        override suspend fun createAvd(): Boolean =
          withContext(uiThread) {
            AvdWizardUtils.createAvdWizard(null, project, AvdOptionsModel(null)).showAndGet()
          }

        override suspend fun editAvd(avdInfo: AvdInfo): Boolean =
          withContext(uiThread) {
            AvdWizardUtils.createAvdWizard(null, project, avdInfo).showAndGet()
          }

        override suspend fun startAvd(avdInfo: AvdInfo) {
          withContext(workerThread) {
            avdManagerConnection.startAvd(project, avdInfo, RequestType.DIRECT)
          }
        }

        override suspend fun stopAvd(avdInfo: AvdInfo) {
          withContext(workerThread) { avdManagerConnection.stopAvd(avdInfo) }
        }
      }

    return LocalEmulatorProvisionerPlugin(AdbLibService.getSession(project), avdManager)
  }
}
