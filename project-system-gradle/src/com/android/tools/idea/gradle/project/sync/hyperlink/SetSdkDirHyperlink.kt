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
package com.android.tools.idea.gradle.project.sync.hyperlink

import com.google.common.annotations.VisibleForTesting
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.gradle.project.sync.issues.SyncIssueNotificationHyperlink
import com.android.tools.idea.gradle.util.LocalProperties
import com.android.tools.idea.sdk.AndroidSdks
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_QF_SDK_PATH_CHANGED
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.undo.GlobalUndoableAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.project.Project
import com.intellij.util.ModalityUiUtil
import org.jetbrains.android.sdk.AndroidSdkData
import java.io.File

class SetSdkDirHyperlink(
  val project: Project,
  @get:VisibleForTesting val localPropertiesPaths: List<String>
) : SyncIssueNotificationHyperlink(
  "set.sdkdir",
  "Set sdk.dir in local.properties and sync project",
  AndroidStudioEvent.GradleSyncQuickFix.SET_SDK_DIR_HYPERLINK
) {
  companion object {
    private const val SDK_DIR_UNDO_NAME = "Setup Sdk Location"
  }

  override fun execute(project: Project) {
    val localProperties = localPropertiesPaths.map { File(it) }.map { it.parentFile }.map { LocalProperties(it) }
    setSdkDirsAndRequestSync(localProperties)
  }

  private class SetSdkDirUndoableAction(
    val localProperties: List<LocalProperties>,
    val sdkData: AndroidSdkData
  ) : GlobalUndoableAction() {
    /**
     * Absence from this map means the LocalProperties file did not exist and was create by undo,
     * null means the file existed but had no sdk.dir property set.
     */
    private val changeHistory = mutableMapOf<LocalProperties, String?>()

    override fun undo() {
      localProperties.forEach {
        if (!changeHistory.containsKey(it)) it.propertiesFilePath.delete()
        val oldProperty = changeHistory[it] ?: "" // Setting to empty string will remove property
        it.setAndroidSdkPath(oldProperty)
      }
    }

    override fun redo() {
      localProperties.forEach {
        val sdkPath = it.androidSdkPath
        if (sdkPath != null && sdkPath.exists()) {
          changeHistory[it] = it.androidSdkPath?.absolutePath
        }
        it.setAndroidSdkPath(sdkData.location.toString())
        it.save()
      }
    }
  }

  private fun setSdkDirsAndRequestSync(localProperties: List<LocalProperties>) {
    val sdkData = AndroidSdks.getInstance().tryToChooseAndroidSdk()
    if (sdkData != null) {
      ModalityUiUtil.invokeLaterIfNeeded(
        ModalityState.defaultModalityState())
        {
          CommandProcessor.getInstance().executeCommand(project, {
            val undoableAction = SetSdkDirUndoableAction(localProperties, sdkData)
            undoableAction.redo()
            UndoManager.getInstance(project).undoableActionPerformed(undoableAction)
            GradleSyncInvoker.getInstance().requestProjectSync(project, GradleSyncInvoker.Request(TRIGGER_QF_SDK_PATH_CHANGED))
          }, SDK_DIR_UNDO_NAME, null)
        }
    }
  }
}