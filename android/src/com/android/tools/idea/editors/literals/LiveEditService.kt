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

package com.android.tools.idea.editors.literals

import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.IDevice
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration
import com.android.tools.idea.editors.liveedit.ui.EmulatorLiveEditAdapter
import com.android.tools.idea.editors.liveedit.ui.LiveEditIssueNotificationAction
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.run.deployment.liveedit.AdbConnection
import com.android.tools.idea.run.deployment.liveedit.AndroidLiveEditDeployMonitor
import com.android.tools.idea.run.deployment.liveedit.DeviceConnection
import com.android.tools.idea.run.deployment.liveedit.EditEvent
import com.android.tools.idea.run.deployment.liveedit.LiveEditStatus
import com.android.tools.idea.run.deployment.liveedit.LiveEditStatus.Companion.createRerunnableErrorStatus
import com.android.tools.idea.run.deployment.liveedit.PsiListener
import com.android.tools.idea.run.deployment.liveedit.SourceInlineCandidateCache
import com.android.tools.idea.streaming.RUNNING_DEVICES_TOOL_WINDOW_ID
import com.android.tools.idea.streaming.SERIAL_NUMBER_KEY
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiManager
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.stream
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.Callable
import java.util.concurrent.Executor

/**
 * Allows any component to listen to all method body edits of a project.
 */
@Service
class LiveEditService constructor(val project: Project,
                                  val deviceConnection: DeviceConnection,
                                  var executor: Executor) : Disposable {

  // We quickly hand off the processing of PSI events to our own executor, since PSI events are likely
  // dispatched from the UI thread, and we do not want to block it.
  constructor(project: Project) : this(project,
                                       AdbConnection,
                                       AppExecutorUtil.createBoundedApplicationPoolExecutor(
                                         "Document changed listeners executor", 1))

  init {
    val adapter = EmulatorLiveEditAdapter(project)
    LiveEditIssueNotificationAction.registerProject(project, adapter)
    Disposer.register(this) { LiveEditIssueNotificationAction.unregisterProject(project) }
    ApplicationManager.getApplication().invokeLater {
      val contentManager = project.getServiceIfCreated(ToolWindowManager::class.java)
        ?.getToolWindow(RUNNING_DEVICES_TOOL_WINDOW_ID)
        ?.contentManager
      contentManager?.addContentManagerListener( object : ContentManagerListener {
          override fun contentAdded(event: ContentManagerEvent) {
            if (event.content.component !is DataProvider) {
              return
            }
            val serial = (event.content.component as DataProvider).getData(SERIAL_NUMBER_KEY.name) as String?
            serial?.let { adapter.register(it) }
          }

          override fun contentRemoveQuery(event: ContentManagerEvent) {
            if (event.content.component !is DataProvider) {
              return
            }
            val serial = (event.content.component as DataProvider).getData(SERIAL_NUMBER_KEY.name) as String?
            serial?.let { adapter.unregister(it) }
          }
        })
      contentManager?.contents?.forEach {
        if (it.component is DataProvider) {
          val serial = (it.component as DataProvider).getData(SERIAL_NUMBER_KEY.name) as String?
          serial?.let { s -> adapter.register(s) }
        }
      }
    }
  }

  fun inlineCandidateCache() : SourceInlineCandidateCache {
    return deployMonitor.compiler.inlineCandidateCache
  }

  private val deployMonitor: AndroidLiveEditDeployMonitor

  init {
    // TODO: Deactivate this when not needed.
    val listener = PsiListener(this::onPsiChanged)
    PsiManager.getInstance(project).addPsiTreeChangeListener(listener, this)
    deployMonitor = AndroidLiveEditDeployMonitor(this, project)
    // TODO: Delete if it turns our we don't need Hard-refresh trigger.
    //bindKeyMapShortcut(LiveEditApplicationConfiguration.getInstance().leTriggerMode)
  }

  companion object {

    // The action upon which we trigger LiveEdit to do a push in manual mode.
    // Right now this is set to "SaveAll" which is called via Ctrl/Cmd+S.
    @JvmStatic
    val PIGGYBACK_ACTION_ID: String = "SaveAll"

    enum class LiveEditTriggerMode {
      LE_TRIGGER_MANUAL,
      LE_TRIGGER_AUTOMATIC,
    }

    fun isLeTriggerManual(mode : LiveEditTriggerMode) : Boolean {
      return mode == LiveEditTriggerMode.LE_TRIGGER_MANUAL
    }

    @JvmStatic
    fun isLeTriggerManual() = isLeTriggerManual(LiveEditApplicationConfiguration.getInstance().leTriggerMode)

    @JvmStatic
    fun getInstance(project: Project): LiveEditService = project.getService(LiveEditService::class.java)

    @JvmStatic
    fun usesCompose(project: Project) = project.modules.stream().anyMatch {
      ProjectSystemService.getInstance(project).projectSystem.getModuleSystem(it).usesCompose
    }

    fun hasLiveEditSupportedDeviceConnected() = AndroidDebugBridge.getBridge()!!.devices.any { device ->
      AndroidLiveEditDeployMonitor.supportLiveEdits(device)
    }
  }

  // TODO: Refactor this away when AndroidLiveEditDeployMonitor functionality is moved to LiveEditService/other classes.
  @VisibleForTesting
  fun getDeployMonitor(): AndroidLiveEditDeployMonitor {
    return deployMonitor
  }

  fun devices(): Set<IDevice> {
    return deployMonitor.devices();
  }

  fun editStatus(device: IDevice): LiveEditStatus {
    return deployMonitor.status(device)
  }

  fun notifyAppRefresh(device: IDevice): Boolean {
    return deployMonitor.notifyAppRefresh(device)
  }

  fun getCallback(packageName: String, device: IDevice) : Callable<*>? {
    return deployMonitor.getCallback(packageName, device)
  }

  fun toggleLiveEdit(oldMode: LiveEditApplicationConfiguration.LiveEditMode, newMode: LiveEditApplicationConfiguration.LiveEditMode) {
    if (oldMode == newMode) {
      return
    } else if (newMode == LiveEditApplicationConfiguration.LiveEditMode.LIVE_EDIT) {
      if (usesCompose(project) && hasLiveEditSupportedDeviceConnected()) {
        deployMonitor.requestRerun()
      }
    } else {
      deployMonitor.clearDevices();
    }
  }

  fun toggleLiveEditMode(oldMode: LiveEditTriggerMode, newMode: LiveEditTriggerMode) {
    if (oldMode == newMode) {
      return
    } else if (newMode == LiveEditTriggerMode.LE_TRIGGER_AUTOMATIC) {
      deployMonitor.onManualLETrigger(project)
    }
  }

  @com.android.annotations.Trace
  private fun onPsiChanged(event: EditEvent) {
    executor.execute { deployMonitor.onPsiChanged(event) }
  }

  override fun dispose() {
    //TODO: "Not yet implemented"
  }

  fun triggerLiveEdit() {
    deployMonitor.onManualLETrigger(project)
  }
}
