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
package com.android.tools.idea.device.explorer

import com.android.adblib.serialNumber
import com.android.annotations.concurrency.UiThread
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.tools.analytics.UsageTracker.log
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.device.explorer.common.DeviceExplorerControllerListener
import com.android.tools.idea.device.explorer.common.DeviceExplorerSettings
import com.android.tools.idea.device.explorer.common.DeviceExplorerTabController
import com.android.tools.idea.device.explorer.ui.DeviceExplorerView
import com.android.tools.idea.device.explorer.ui.DeviceExplorerViewListener
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DeviceExplorerEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import kotlinx.coroutines.launch

@UiThread
class DeviceExplorerController(
  project: Project,
  private val model: DeviceExplorerModel,
  private val view: DeviceExplorerView,
  private val tabControllers: List<DeviceExplorerTabController>) : Disposable, DeviceExplorerControllerListener {
  private val uiThreadScope = AndroidCoroutineScope(this, AndroidDispatchers.uiThread)
  private val viewListener: DeviceExplorerViewListener = ViewListener()

  init {
    Disposer.register(project, this)
    view.addListener(viewListener)
    project.putUserData(KEY, this)
  }

  override fun dispose() {
    view.removeListener(viewListener)
  }

  override fun packageFilterToggled(isActive: Boolean) {
    DeviceExplorerSettings.getInstance().isPackageFilterActive = isActive
    trackAction(DeviceExplorerEvent.Action.APPLICATION_ID_FILTER_TOGGLED)
    trackAction(
      if (isActive) DeviceExplorerEvent.Action.APPLICATION_ID_FILTER_TOGGLED_ON
      else DeviceExplorerEvent.Action.APPLICATION_ID_FILTER_TOGGLED_OFF
    )
    tabControllers.forEach {
      it.setPackageFilter(isActive)
    }
  }

  fun setup() {
    uiThreadScope.launch {
      view.setup()
      tabControllers.forEach {
        it.setup()
        it.controllerListener = this@DeviceExplorerController
        view.addTab(it.getViewComponent(), it.getTabName())
      }
      launch { view.trackDeviceListChanges() }
      launch { view.trackActiveDeviceChanges() }
    }
  }

  fun reportErrorFindingDevice(message: String) {
    view.reportErrorGeneric(message, IllegalStateException())
  }

  fun selectActiveDevice(serialNumber: String) {
    uiThreadScope.launch {
      when (val device = model.devices.value.find { it.state.connectedDevice?.serialNumber == serialNumber }) {
        null -> reportErrorFindingDevice("Unable to find device with serial number $serialNumber. Please retry.")
        else -> setActiveDevice(device)
      }
    }
  }

  private fun setActiveDevice(deviceHandle: DeviceHandle?) {
    model.setActiveDevice(deviceHandle)
    trackAction(DeviceExplorerEvent.Action.DEVICE_CHANGE)
    tabControllers.forEach {
      it.setActiveConnectedDevice(deviceHandle)
    }
  }

  private fun trackAction(action: DeviceExplorerEvent.Action) {
    log(
      AndroidStudioEvent.newBuilder()
        .setKind(AndroidStudioEvent.EventKind.DEVICE_EXPLORER)
        .setDeviceExplorerEvent(
          DeviceExplorerEvent.newBuilder()
            .setAction(action)
        )
    )
  }

  private inner class ViewListener : DeviceExplorerViewListener {
    override fun noDeviceSelected() {
      setActiveDevice(null)
    }

    override fun deviceSelected(deviceHandle: DeviceHandle) {
      setActiveDevice(deviceHandle)
    }
  }

  companion object {
    private val KEY = Key.create<DeviceExplorerController>(
      DeviceExplorerController::class.java.name
    )
    @JvmStatic
    fun getProjectController(project: Project?): DeviceExplorerController? {
      return project?.getUserData(KEY)
    }
  }
}