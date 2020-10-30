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

import com.android.tools.idea.avdmanager.ConfigureDeviceModel
import com.android.tools.idea.avdmanager.ConfigureDeviceOptionsStep
import com.android.tools.idea.deviceManager.displayList.NamedDevice
import com.android.tools.idea.ui.wizard.StudioWizardDialogBuilder
import com.android.tools.idea.wizard.model.ModelWizard
import com.intellij.openapi.project.Project
import java.beans.PropertyChangeListener
import javax.swing.Action
import javax.swing.Icon
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener

// TODO(qumeric): very similar to AvdUiAction and DeviceUiAction
abstract class PhysicalDeviceUiAction(
  protected val deviceProvider: PhysicalDeviceProvider,
  val text: String,
  val description: String,
  val icon: Icon
) : Action, HyperlinkListener {

  // merge with DeviceProvider?
  interface PhysicalDeviceProvider {
    val device: NamedDevice?
    val project: Project?
    /*
    // FIXME(qumeric): do we need similar?
    fun refreshAvds()
    fun refreshAvdsAndSelect(avdToSelect: AvdInfo?)
    val avdProviderComponent: JComponent
     */
  }

  override fun getValue(key: String): Any? = text.takeIf { Action.NAME == key }
  override fun putValue(key: String, value: Any) {}
  override fun setEnabled(b: Boolean) {}
  override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
  override fun removePropertyChangeListener(listener: PropertyChangeListener) {}

  override fun hyperlinkUpdate(e: HyperlinkEvent) {
    if (isEnabled) {
      actionPerformed(null)
    }
  }

  companion object {
    fun showHardwareProfileWizard(model: ConfigureDeviceModel) {
      val wizard = ModelWizard.Builder().addStep(ConfigureDeviceOptionsStep(model)).build()
      StudioWizardDialogBuilder(wizard, "Hardware Profile Configuration").setProject(model.project).build().show()
    }
  }
}