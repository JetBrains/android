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
package com.android.tools.idea.run.configuration.editors

import com.android.annotations.concurrency.WorkerThread
import com.android.tools.deployer.model.component.Complication
import com.android.tools.idea.run.configuration.AndroidComplicationConfiguration
import com.android.tools.idea.run.configuration.ComplicationSlot
import com.android.tools.idea.run.configuration.getComplicationTypesFromManifest
import com.android.tools.idea.run.configuration.parseRawComplicationTypes
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.android.util.AndroidBundle

class AndroidComplicationConfigurationEditor(private val project: Project, configuration: AndroidComplicationConfiguration) :
  AndroidWearConfigurationEditor<AndroidComplicationConfiguration>(project, configuration) {

  private val slotsPanel = SlotsPanel()
  private var allAvailableSlots: List<ComplicationSlot> = emptyList()

  override fun createEditor() = panel {
    getModuleChooser()
    group(AndroidBundle.message("wearos.complication.slot.launch.options"), indent = false) {
      getComponentComboBox()
      getInstallFlagsTextField()
    }
    row {
      cell(slotsPanel).align(AlignX.FILL)
    }
  }

  init {
    Disposer.register(project, this)
  }

  override fun resetEditorFrom(runConfiguration: AndroidComplicationConfiguration) {
    super.resetEditorFrom(runConfiguration)

    allAvailableSlots = runConfiguration.componentLaunchOptions.watchFaceInfo.complicationSlots
    val chosenSlots = runConfiguration.componentLaunchOptions.chosenSlots.map { it.copy() }.toMutableList()
    updateComplicationModel(chosenSlots, runConfiguration.componentLaunchOptions.componentName)
  }

  override fun applyEditorTo(runConfiguration: AndroidComplicationConfiguration) {
    super.applyEditorTo(runConfiguration)

    runConfiguration.componentLaunchOptions.chosenSlots = slotsPanel.getModel().currentChosenSlots.map { it.copy() }
  }

  override fun onComponentNameChanged(newComponent: String?) {
    super.onComponentNameChanged(newComponent)

    if (newComponent == null) {
      slotsPanel.setModel(SlotsPanel.ComplicationsModel(allAvailableSlots = allAvailableSlots))
    }
    else {
      updateComplicationModel(arrayListOf(), newComponent)
    }
  }

  private fun updateComplicationModel(chosenSlots: MutableList<AndroidComplicationConfiguration.ChosenSlot>, componentName: String?) {
    // The following backgroundable task can be run before the configuration editor dialog is shown, for example when run from
    // the gutter. When this happens, the ModalityState used by the backgroundable task will be registered with ModalityState.NON_MODAL.
    // Once a dialog is showing, any EDT events run with ModalityState.NON_MODAL will be enqueued and only executed once the dialog is
    // closed. This is because we enter a secondary loop (cf https://docs.oracle.com/javase/7/docs/api/java/awt/SecondaryLoop.html) when
    // showing a dialog. In our case, we want to update the UI on the EDT thread when the dialog is open.
    // When using ModalityState.any(), we ensure the event is pushed to the secondary queue and executed while the dialog is open.
    val modalityState = ModalityState.any()
    runBackgroundableTask(AndroidBundle.message("wearos.complication.progress.updating.slots"), project) {
      val supportedTypes = getSupportedTypes(componentName)
      runInEdt(modalityState) {
        if (project.isDisposed) return@runInEdt
        slotsPanel.setModel(SlotsPanel.ComplicationsModel(
          chosenSlots, allAvailableSlots, supportedTypes
        ))
      }
    }
  }

  @WorkerThread
  private fun getSupportedTypes(componentName: String?): List<Complication.ComplicationType> {
    val module = moduleSelector.module
    if (componentName == null || module == null) {
      return emptyList()
    }
    return parseRawComplicationTypes(getComplicationTypesFromManifest(module, componentName) ?: emptyList())
  }
}