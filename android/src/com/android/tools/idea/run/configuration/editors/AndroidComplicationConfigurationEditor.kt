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
import com.android.tools.idea.run.configuration.DefaultComplicationWatchFaceInfo
import com.android.tools.idea.run.configuration.getComplicationTypesFromManifest
import com.android.tools.idea.run.configuration.parseRawComplicationTypes
import com.android.tools.idea.welcome.wizard.invokeLater
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.Disposer
import com.intellij.ui.layout.panel
import javax.swing.BoxLayout


class AndroidComplicationConfigurationEditor(private val project: Project, configuration: AndroidComplicationConfiguration) :
  AndroidWearConfigurationEditor<AndroidComplicationConfiguration>(project, configuration) {

  private val slotsPanel = SlotsPanel()
  private var allAvailableSlots : List<ComplicationSlot> = emptyList()
  override fun createEditor() = panel {
    row {
      getModuleChooser()
      getComponentCompoBox()
      getInstallFlagsTextField()
    }
    row {
      component(slotsPanel.apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
      })
    }
  }

  init {
    Disposer.register(project, this)
  }

  override fun resetEditorFrom(runConfiguration: AndroidComplicationConfiguration) {
    super.resetEditorFrom(runConfiguration)

    allAvailableSlots = runConfiguration.componentLaunchOptions.watchFaceInfo.complicationSlots

    // getSupportedTypes will invoke the manifest merger and needs to be called in the background thread.
    ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Updating slots") {
      private var complicationModel: SlotsPanel.ComplicationsModel? = null

      override fun run(indicator: ProgressIndicator) {
        complicationModel = SlotsPanel.ComplicationsModel(
          runConfiguration.componentLaunchOptions.chosenSlots.map { it.copy() }.toMutableList(),
          allAvailableSlots,
          getSupportedTypes(runConfiguration.componentLaunchOptions.componentName)
        )
      }

      override fun onFinished() {
        complicationModel?.let { slotsPanel.setModel(it) }
      }
    })
  }

  override fun applyEditorTo(runConfiguration: AndroidComplicationConfiguration) {
    super.applyEditorTo(runConfiguration)

    runConfiguration.componentLaunchOptions.chosenSlots = slotsPanel.getModel().currentChosenSlots.map { it.copy() }
  }

  override fun onComponentNameChanged(newComponent: String?) {
    super.onComponentNameChanged(newComponent)

    if (newComponent == null) {
      slotsPanel.setModel(SlotsPanel.ComplicationsModel())
    }
    else {
      // getSupportedTypes will invoke the manifest merger and needs to be called in the background thread.
      ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Updating slots") {
        private var complicationModel: SlotsPanel.ComplicationsModel? = null

        override fun run(indicator: ProgressIndicator) {
          complicationModel = SlotsPanel.ComplicationsModel(arrayListOf(), allAvailableSlots, getSupportedTypes(newComponent))
        }

        override fun onFinished() {
          complicationModel?.let { slotsPanel.setModel(it) }
        }
      })
    }
  }

  @WorkerThread
  private fun getSupportedTypes(componentName: String?) : List<Complication.ComplicationType>{
    if (componentName == null) {
      return emptyList()
    }
    return parseRawComplicationTypes(getComplicationTypesFromManifest(moduleSelector.module!!, componentName) ?: emptyList())
  }
}