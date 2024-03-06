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
import com.android.sdklib.deviceprovisioner.DeviceIcons
import com.android.sdklib.deviceprovisioner.DeviceProvisionerPlugin
import com.android.sdklib.deviceprovisioner.LocalEmulatorProvisionerPlugin
import com.android.sdklib.deviceprovisioner.LocalEmulatorSnapshot
import com.android.sdklib.internal.avd.AvdInfo
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.avdmanager.AvdLaunchListener.RequestType.DIRECT_DEVICE_MANAGER
import com.android.tools.idea.avdmanager.AvdLaunchListener.RequestType.INDIRECT
import com.android.tools.idea.avdmanager.ui.AvdOptionsModel
import com.android.tools.idea.avdmanager.ui.AvdWizardUtils
import com.android.tools.idea.concurrency.AndroidDispatchers.diskIoThread
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.deviceprovisioner.DeviceProvisionerFactory
import com.android.tools.idea.deviceprovisioner.StudioDefaultDeviceActionPresentation
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils
import com.intellij.ide.actions.RevealFileAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import icons.StudioIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.guava.await
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
    project: Project?,
    avdManager: LocalEmulatorProvisionerPlugin.AvdManager = AvdManagerImpl(project)
  ): DeviceProvisionerPlugin {
    return LocalEmulatorProvisionerPlugin(
      coroutineScope,
      adbSession,
      avdManager,
      deviceIcons =
        DeviceIcons(
          handheld = StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_PHONE,
          wear = StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_WEAR,
          tv = StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_TV,
          automotive = StudioIcons.DeviceExplorer.VIRTUAL_DEVICE_CAR
        ),
      defaultPresentation = StudioDefaultDeviceActionPresentation,
      diskIoThread,
    )
  }
}

private class AvdManagerImpl(val project: Project?) : LocalEmulatorProvisionerPlugin.AvdManager {
  private val avdManagerConnection = AvdManagerConnection.getDefaultAvdManagerConnection()

  override suspend fun rescanAvds() =
    withContext(diskIoThread) { avdManagerConnection.getAvds(true) }

  override suspend fun createAvd(): AvdInfo? {
    val avdOptionsModel = AvdOptionsModel(null)
    withContext(uiThread) {
      AvdWizardUtils.createAvdWizard(null, project, avdOptionsModel).showAndGet()
    }
    return avdOptionsModel.createdAvd.orElse(null)
  }

  override suspend fun editAvd(avdInfo: AvdInfo): AvdInfo? {
    val avdOptionsModel = AvdOptionsModel(avdInfo)
    withContext(uiThread) {
      AvdWizardUtils.createAvdWizard(null, project, avdOptionsModel).showAndGet()
    }
    return avdOptionsModel.createdAvd.orElse(null)
  }

  override suspend fun startAvd(avdInfo: AvdInfo): Unit =
    // Note: the original DeviceManager does this in UI thread, but this may call
    // @Slow methods so switch
    withContext(workerThread) {
      avdManagerConnection.quickBoot(project, avdInfo, DIRECT_DEVICE_MANAGER).await()
    }

  override suspend fun coldBootAvd(avdInfo: AvdInfo): Unit =
    withContext(workerThread) {
      avdManagerConnection.coldBoot(project, avdInfo, DIRECT_DEVICE_MANAGER).await()
    }

  override suspend fun bootAvdFromSnapshot(
    avdInfo: AvdInfo,
    snapshot: LocalEmulatorSnapshot
  ): Unit =
    withContext(workerThread) {
      val snapshotPath = snapshot.path.fileName.toString()
      avdManagerConnection.bootWithSnapshot(project, avdInfo, snapshotPath, INDIRECT).await()
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
                "There may be additional files remaining in the AVD directory. To fully delete " +
                  "the AVD, open the directory and manually delete the files."
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

  override suspend fun downloadAvdSystemImage(avdInfo: AvdInfo) {
    val path = AvdManagerConnection.getRequiredSystemImagePath(avdInfo) ?: return
    withContext(uiThread) {
      SdkQuickfixUtils.createDialogForPaths(project, listOf(path))?.showAndGet()
    }
  }
}
