/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.deviceManager.avdmanager.actions

import com.android.sdklib.devices.Device
import com.android.tools.idea.deviceManager.avdmanager.ConfigureDeviceModel
import com.android.tools.idea.deviceManager.avdmanager.ConfigureDeviceOptionsStep
import com.android.tools.idea.ui.wizard.StudioWizardDialogBuilder
import com.android.tools.idea.wizard.model.ModelWizard
import com.intellij.openapi.project.Project
import java.beans.PropertyChangeListener
import javax.swing.Action

/**
 * A base class for actions that operate on [Device]s and can be bound to buttons
 */
abstract class DeviceUiAction(@JvmField protected val provider: DeviceProvider, val text: String) : Action {
  interface DeviceProvider {
    var device: Device?
    fun refreshDevices()
    fun selectDefaultDevice()
    val project: Project?
  }

  override fun getValue(key: String): Any? = text.takeIf { Action.NAME == key }
  override fun putValue(key: String, value: Any) {}
  override fun setEnabled(b: Boolean) {}
  override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
  override fun removePropertyChangeListener(listener: PropertyChangeListener) {}

  companion object {
    fun showHardwareProfileWizard(model: ConfigureDeviceModel) {
      val wizard = ModelWizard.Builder().addStep(ConfigureDeviceOptionsStep(model)).build()
      StudioWizardDialogBuilder(wizard, "Hardware Profile Configuration").setProject(model.project).build().show()
    }
  }
}