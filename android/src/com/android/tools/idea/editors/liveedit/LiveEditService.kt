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
package com.android.tools.idea.editors.liveedit

import com.android.ddmlib.IDevice
import com.android.tools.idea.editors.liveedit.ui.MANUAL_LIVE_EDIT_ACTION_ID
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.run.deployment.liveedit.LiveEditAdbEventsListener
import com.android.tools.idea.run.deployment.liveedit.LiveEditApp
import com.android.tools.idea.run.deployment.liveedit.LiveEditProjectMonitor
import com.android.tools.idea.run.deployment.liveedit.LiveEditStatus
import com.android.tools.idea.run.deployment.liveedit.SourceInlineCandidateCache
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfile
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.util.containers.stream
import org.jetbrains.annotations.VisibleForTesting

/**
 * Allows any component to listen to all method body edits of a project.
 */
interface LiveEditService : Disposable {

  val adbEventsListener: LiveEditAdbEventsListener

  companion object {
    @JvmStatic
    fun getInstance(project: Project): LiveEditService = project.getService(LiveEditServiceImpl::class.java)

    @JvmStatic
    fun usesCompose(project: Project) = project.modules.stream().anyMatch {
      ProjectSystemService.getInstance(project).projectSystem.getModuleSystem(it).usesCompose
    }

    // The action upon which we trigger LiveEdit to do a push in manual mode.
    // Right now this is set to "SaveAll" which is called via Ctrl/Cmd+S.
    @JvmStatic
    val PIGGYBACK_ACTION_ID: String = "SaveAll"

    enum class LiveEditTriggerMode {
      ON_SAVE,
      ON_HOTKEY,
      AUTOMATIC,

      // These two enums entries are deprecated in H. We keep them around so an user who updates from a version where these values were not
      // deprecated to a version where they were deprecated does not trigger a NullPointerException.
      LE_TRIGGER_MANUAL,
      LE_TRIGGER_AUTOMATIC,
    }

    fun isLeTriggerManual(mode: LiveEditTriggerMode): Boolean {
      return mode == LiveEditTriggerMode.ON_SAVE || mode == LiveEditTriggerMode.ON_HOTKEY
    }

    @JvmStatic
    fun isLeTriggerManual() = isLeTriggerManual(LiveEditApplicationConfiguration.getInstance().leTriggerMode)

    @JvmStatic
    fun isLeTriggerOnSave() = LiveEditApplicationConfiguration.getInstance().leTriggerMode == LiveEditTriggerMode.ON_SAVE

    @JvmStatic
    fun manualLiveEdit(project: Project) {
      if (!LiveEditApplicationConfiguration.getInstance().isLiveEdit) {
        return
      }

      if (!isLeTriggerManual()) {
        return
      }

      getInstance(project).triggerLiveEdit()
    }

    fun getLiveEditShortcut(): String =
      if (LiveEditApplicationConfiguration.getInstance().leTriggerMode === LiveEditTriggerMode.ON_SAVE)
        LiveEditAnActionListener.getLiveEditTriggerShortCutString()
      else
        ActionManager.getInstance().getAction(MANUAL_LIVE_EDIT_ACTION_ID)
          .shortcutSet.shortcuts.firstOrNull()?.let { KeymapUtil.getShortcutText(it) } ?: ""
  }

  fun inlineCandidateCache(): SourceInlineCandidateCache

  // TODO: Refactor this away when AndroidLiveEditDeployMonitor functionality is moved to LiveEditService/other classes.
  @VisibleForTesting
  fun getDeployMonitor(): LiveEditProjectMonitor
  fun devices(): Set<IDevice>
  fun editStatus(device: IDevice): LiveEditStatus

  /**
   * Called from Android Studio when an app is "Refreshed" (namely Apply Changes or Apply Code Changes) to a device
   */
  fun notifyAppRefresh(device: IDevice): Boolean

  /**
   * Called from Android Studio when an app is deployed (a.k.a Installed / IWIed / Delta-installed) to a device
   */
  fun notifyAppDeploy(runProfile: RunProfile, executor: Executor, packageName: String, device: IDevice, app: LiveEditApp): Boolean
  fun toggleLiveEdit(oldMode: LiveEditApplicationConfiguration.LiveEditMode, newMode: LiveEditApplicationConfiguration.LiveEditMode)
  fun toggleLiveEditMode(oldMode: LiveEditTriggerMode, newMode: LiveEditTriggerMode)
  fun triggerLiveEdit()
  fun notifyLiveEditAvailability(device: IDevice)
}
