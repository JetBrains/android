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

import com.android.adblib.AdbSession
import com.android.sdklib.deviceprovisioner.DeviceProvisionerPlugin
import com.android.sdklib.deviceprovisioner.LocalEmulatorProvisionerPlugin
import com.android.sdklib.internal.avd.AvdInfo
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.avdmanager.AvdLaunchListener.RequestType
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.concurrency.AndroidDispatchers.diskIoThread
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerFactory
import com.android.tools.idea.deviceprovisioner.StudioDefaultDeviceActionPresentation
import com.intellij.ide.actions.RevealFileAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext

/** Builds a LocalEmulatorProvisionerPlugin with its dependencies provided by Studio. */
class LocalEmulatorProvisionerFactory : DeviceProvisionerFactory {
  override val isEnabled: Boolean
    get() = true

  override fun create(coroutineScope: CoroutineScope, project: Project) =
    create(coroutineScope, AdbLibService.getSession(project), project)

  fun create(
    coroutineScope: CoroutineScope,
    adbSession: AdbSession,
    project: Project?
  ): DeviceProvisionerPlugin {
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

        override suspend fun startAvd(avdInfo: AvdInfo, coldBoot: Boolean) {
          // Note: the original DeviceManager does this in UI thread, but this may call
          // @Slow methods so switch
          withContext(workerThread) {
            when {
              coldBoot ->
                avdManagerConnection.startAvdWithColdBoot(
                  project,
                  avdInfo,
                  RequestType.DIRECT_DEVICE_MANAGER
                )
              else ->
                avdManagerConnection.startAvd(project, avdInfo, RequestType.DIRECT_DEVICE_MANAGER)
            }
          }
        }

        override suspend fun stopAvd(avdInfo: AvdInfo) {
          withContext(workerThread) { avdManagerConnection.stopAvd(avdInfo) }
        }

        override suspend fun showOnDisk(avdInfo: AvdInfo) {
          RevealFileAction.openDirectory(avdInfo.dataFolderPath)
        }

        override suspend fun duplicateAvd(avdInfo: AvdInfo) {
          withContext(uiThread) {
            AvdWizardUtils.createAvdWizardForDuplication(null, project, AvdOptionsModel(avdInfo))
              .showAndGet()
          }
        }

        override suspend fun wipeData(avdInfo: AvdInfo) {
          withContext(diskIoThread) {
            if (!avdManagerConnection.wipeUserData(avdInfo)) {
              withContext(uiThread) {
                Messages.showErrorDialog(
                  project,
                  "Failed to wipe data. Please check that the emulator and its files are not in use and try again.",
                  "Wipe Data Error"
                )
              }
            }
          }
        }

        override suspend fun deleteAvd(avdInfo: AvdInfo) {
          withContext(diskIoThread) {
            if (!avdManagerConnection.deleteAvd(avdInfo)) {
              withContext(uiThread) {
                if (
                  MessageDialogBuilder.okCancel(
                      "Could Not Delete All AVD Files",
                      "There may be additional files remaining in the AVD directory. Open the directory, " +
                        "manually delete the files, and refresh AVD list."
                    )
                    .yesText("Open Directory")
                    .noText("OK")
                    .icon(Messages.getInformationIcon())
                    .ask(project)
                ) {
                  showOnDisk(avdInfo)
                }
              }
            }
          }
        }
      }

    return LocalEmulatorProvisionerPlugin(
      coroutineScope,
      adbSession,
      avdManager,
      defaultPresentation = StudioDefaultDeviceActionPresentation,
    )
  }
}
