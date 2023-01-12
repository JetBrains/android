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
package com.android.tools.idea.streaming

import com.android.adblib.DeviceSelector
import com.android.adblib.RemoteFileMode
import com.android.adblib.syncSend
import com.android.adblib.tools.InstallException
import com.android.adblib.tools.install
import com.android.tools.idea.adblib.AdbLibService
import com.intellij.ide.dnd.DnDDropHandler
import com.intellij.ide.dnd.DnDEvent
import com.intellij.ide.dnd.DnDSupport
import com.intellij.ide.dnd.FileCopyPasteUtil
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.nio.file.Path
import java.util.EnumSet
import javax.swing.JComponent

private const val DEVICE_DOWNLOAD_DIR = "/sdcard/Download"

/**
 * Installs a drop handler that installs .apk files and copies all other files to
 * the `/sdcard/Download` directory of the device. The lifetime of the drop handler
 * is determined by the lifetime of [displayView].
 *
 * @param dropTarget the drop target component
 * @param displayView the view associated with the device
 * @param project the project associated with [displayView]
 */
fun installFileDropHandler(dropTarget: JComponent, deviceSerialNumber: String, displayView: AbstractDisplayView, project: Project) {
  DnDSupport.createBuilder(dropTarget)
    .enableAsNativeTarget()
    .setTargetChecker { event ->
      if (FileCopyPasteUtil.isFileListFlavorAvailable(event)) {
        event.isDropPossible = true
        return@setTargetChecker false
      }
      return@setTargetChecker true
    }
    .setDisposableParent(displayView)
    .setDropHandler(DeviceFileDropHandler(deviceSerialNumber, displayView, project))
    .install()
}

/**
 * Drop handler that installs .apk files and pushes other files to the device.
 */
private class DeviceFileDropHandler(
  private val deviceSerialNumber: String,
  private val displayView: AbstractDisplayView,
  private val project: Project
) : DnDDropHandler {

  override fun drop(event: DnDEvent) {
    val files = FileCopyPasteUtil.getFileListFromAttachedObject(event.attachedObject).map(File::toPath)
    val fileTypes = getFileTypes(files)
    if (fileTypes.size > 1) {
      notifyOfError("Drag-and-drop can either install an APK, or copy one or more non-APK files to the device.")
      return
    }

    val adb = AdbLibService.getSession(project).deviceServices
    val deviceSelector = DeviceSelector.fromSerialNumber(deviceSerialNumber)

    if (fileTypes.contains(FileType.APK)) {
      displayView.showLongRunningOperationIndicator("Installing ${formatForDisplay("app consisting of ", files)}")

      CoroutineScope(Dispatchers.IO).launch {
        try {
          adb.install(deviceSelector, files, listOf("-t", "--user", "current", "--full"))
          notifyOfSuccess("${formatForDisplay("App consisting of ", files)} installed")
        }
        catch (e: Throwable) {
          val message = if (e is InstallException && e.isInvalidCompoundApk() && files.size > 1) {
            "The ${files.size} files don't belong to the same app"
          }
          else {
            e.message ?: "Installation failed - ${e.javaClass.simpleName}"
          }
          notifyOfError(message)
        }
        UIUtil.invokeLaterIfNeeded {
          displayView.hideLongRunningOperationIndicator()
        }
      }
    }
    else {
      val fileList = formatForDisplay("", files)
      displayView.showLongRunningOperationIndicator("Copying $fileList")

      CoroutineScope(Dispatchers.IO).launch {
        try {
          for (file in files) {
            adb.syncSend(deviceSelector, file, "${DEVICE_DOWNLOAD_DIR}/${file.fileName}", RemoteFileMode.DEFAULT)
          }
          notifyOfSuccess("$fileList copied")
        }
        catch (e: Throwable) {
          val message = e.message ?: "Copying failed"
          notifyOfError(message)
        }
        UIUtil.invokeLaterIfNeeded {
          displayView.hideLongRunningOperationIndicator()
        }
      }
    }
  }

  private fun formatForDisplay(prefixForPluralCase: String, files: List<Path>): String =
      if (files.size == 1) files.first().fileName.toString() else "$prefixForPluralCase${files.size} files"

  private fun getFileTypes(files: List<Path>): Set<FileType> {
    val types = EnumSet.noneOf(FileType::class.java)
    for (file in files) {
      types.add(if (file.toString().endsWith(".apk", ignoreCase = true)) FileType.APK else FileType.OTHER)
      if (types.contains(FileType.APK) && types.contains(FileType.OTHER)) {
        break
      }
    }
    return types
  }

  private fun notifyOfSuccess(message: String) {
    RUNNING_DEVICES_NOTIFICATION_GROUP.createNotification(message, NotificationType.INFORMATION).notify(project)
  }

  private fun notifyOfError(message: String) {
    RUNNING_DEVICES_NOTIFICATION_GROUP.createNotification(message, NotificationType.WARNING).notify(project)
  }

  private fun InstallException.isInvalidCompoundApk() =
      errorCode == "INSTALL_FAILED_INVALID_APK" && errorMessage.endsWith(" Split null was defined multiple times")

  private enum class FileType { APK, OTHER }
}
