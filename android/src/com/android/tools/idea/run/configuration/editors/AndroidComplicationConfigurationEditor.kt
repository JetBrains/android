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
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import kotlinx.coroutines.Dispatchers
import java.awt.event.ActionListener
import javax.swing.BoxLayout
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.android.util.AndroidBundle

class AndroidComplicationConfigurationEditor(
  project: Project,
  configuration: AndroidComplicationConfiguration,
) : AndroidWearConfigurationEditor<AndroidComplicationConfiguration>(project, configuration) {

  private var updatingJob: Job? = null
  private val slotsPanel = SlotsPanel()
  private var allAvailableSlots: List<ComplicationSlot> = emptyList()

  override fun createEditor() = panel {
    getModuleChooser()
    group(AndroidBundle.message("android.run.configuration.complication.launch.options")) {
      getComponentComboBox()
      getInstallFlagsTextField()
    }
    row { cell(slotsPanel.apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }).align(AlignX.FILL) }
  }

  override fun resetEditorFrom(runConfiguration: AndroidComplicationConfiguration) {
    allAvailableSlots = runConfiguration.componentLaunchOptions.watchFaceInfo.complicationSlots
    wearComponentFqNameComboBox.removeActionListener(componentItemListener)
    scope.launch {
      reset(runConfiguration)
      updateComplicationModel(runConfiguration.componentLaunchOptions.chosenSlots.toMutableList())
      wearComponentFqNameComboBox.addActionListener(componentItemListener)
    }
  }

  private val componentItemListener = ActionListener {
    scope.launch { updateComplicationModel(arrayListOf()) }
  }

  override fun applyEditorTo(runConfiguration: AndroidComplicationConfiguration) {
    super.applyEditorTo(runConfiguration)
    runConfiguration.componentLaunchOptions.chosenSlots =
      slotsPanel.getModel().currentChosenSlots.map { it.copy() }
  }

  private suspend fun updateComplicationModel(
    chosenSlots: MutableList<AndroidComplicationConfiguration.ChosenSlot>
  ) {
    updatingJob?.cancelAndJoin()
    updatingJob = coroutineContext.job
    val componentName = wearComponentFqNameComboBox.item
    val model =
      if (componentName == null) {
        SlotsPanel.ComplicationsModel(allAvailableSlots = allAvailableSlots)
      } else {
        val supportedTypes = getSupportedTypes(componentName)
        SlotsPanel.ComplicationsModel(
          allAvailableSlots = allAvailableSlots,
          supportedTypes = supportedTypes,
          currentChosenSlots = chosenSlots,
        )
      }
    withContext(Dispatchers.EDT + ModalityState.any ().asContextElement()) {
      slotsPanel.setModel(model)
    }
  }

  @WorkerThread
  private fun getSupportedTypes(componentName: String?): List<Complication.ComplicationType> {
    val module = moduleSelector.module
    if (componentName == null || module == null) {
      return emptyList()
    }
    return parseRawComplicationTypes(
      getComplicationTypesFromManifest(module, componentName) ?: emptyList()
    )
  }
}
