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
package com.android.tools.profilers.memory

import com.android.tools.adtui.model.AspectObserver
import com.android.tools.profilers.ProfilerCombobox
import com.android.tools.profilers.memory.adapters.instancefilters.CaptureObjectInstanceFilter
import com.intellij.openapi.application.ApplicationManager
import javax.swing.DefaultComboBoxModel
import javax.swing.JLabel
import javax.swing.ListCellRenderer

internal class MemoryInstanceFilterMenu(stage: MemoryProfilerStage): AspectObserver() {
  val component = ProfilerCombobox<CaptureObjectInstanceFilter>()

  init {
    stage.aspect.addDependency(this)
      .onChange(MemoryProfilerAspect.CURRENT_LOADING_CAPTURE) {
        component.isVisible = false
      }
      .onChange(MemoryProfilerAspect.CURRENT_LOADED_CAPTURE) {
        val captureObject = stage.selectedCapture
        if (captureObject != null) {
          val filters = captureObject.supportedInstanceFilters
          if (filters.size > 0) {
            val allFilters = arrayOf<CaptureObjectInstanceFilter?>(null) + filters.toTypedArray()
            component.apply {
              isVisible = true
              model = DefaultComboBoxModel(allFilters)
              renderer = ListCellRenderer<CaptureObjectInstanceFilter> { _, value, _, _, _ ->
                JLabel("Show ${value?.displayName ?: "All"}")
              }
            }
          } else {
            component.isVisible = false
          }
        } else {
          component.isVisible = false
        }
      }

    component.addActionListener {
      val captureObject = stage.selectedCapture!!
      when (val filter = component.selectedItem as CaptureObjectInstanceFilter?) {
        null -> captureObject.removeAllFilters(ApplicationManager.getApplication()::invokeLater)
        else -> captureObject.setSingleFilter(filter, ApplicationManager.getApplication()::invokeLater)
      }
    }
  }
}
