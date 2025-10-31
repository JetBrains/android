/*
 * Copyright (C) 2025 The Android Open Source Project
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

import android.databinding.tool.util.StringUtils
import com.android.tools.adtui.model.AspectObserver
import com.android.tools.profilers.ProfilerDropDownComponent
import com.android.tools.profilers.ProfilerFlows
import com.android.tools.profilers.Selection
import com.android.tools.profilers.memory.adapters.CaptureObject
import com.android.tools.profilers.memory.adapters.instancefilters.CaptureObjectInstanceFilter
import com.intellij.openapi.application.ApplicationManager
import java.util.Collections
import java.util.concurrent.Executor
import javax.swing.JPanel
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Base class for a dropdown menu that filters memory captures.
 */
internal abstract class BaseFilterMenu(
  protected val selection: MemoryCaptureSelection,
  private val defaultFilterDisplayName: String,
  private val filterDescription: String,
  private val onFilterChange: (CaptureObjectInstanceFilter?, Executor) -> Unit,
  private val currentFilterAspect: CaptureSelectionAspect
) : AspectObserver() {

  protected val executor: Executor = Executor(ApplicationManager.getApplication()::invokeLater)
  private val filterFlow: MutableStateFlow<Selection<CaptureObjectInstanceFilter?>> =
    ProfilerFlows.createMutableStateFlow(Selection.emptySelection())

  private var captureObject: CaptureObject? = null
  private var filters: List<CaptureObjectInstanceFilter?> = emptyList()

  val component: JPanel

  init {
    val dropDown = ProfilerDropDownComponent<CaptureObjectInstanceFilter?>(
      defaultFilterDisplayName,
      filterDescription,
      null,
      filterFlow,
      null,
      { filter -> onFilterChange(filter, executor) },
      this::getFilterDisplayName
    )

    component = createComponent(dropDown)

    selection.aspect.addDependency(this)
      .onChange(CaptureSelectionAspect.CURRENT_LOADING_CAPTURE, ::setNewCapture)
      .onChange(CaptureSelectionAspect.CURRENT_LOADED_CAPTURE, ::updateCaptureState)
      .onChange(currentFilterAspect, ::refreshFilter)

    setNewCapture()
    refreshFilter()
  }

  protected abstract fun createComponent(dropDown: ProfilerDropDownComponent<CaptureObjectInstanceFilter?>): JPanel
  protected abstract fun getAvailableFilters(capture: CaptureObject?): List<CaptureObjectInstanceFilter?>
  protected abstract fun getCurrentFilter(): CaptureObjectInstanceFilter?

  private fun setNewCapture() {
    captureObject = selection.selectedCapture
    if (captureObject == null) {
      filters = emptyList()
      return // Loading probably failed.
    }
    // Clear filters and wait for updateCaptureState, as the capture is not fully loaded yet.
    filters = emptyList()
    refreshFilter()
  }

  private fun updateCaptureState() {
    // Capture is fully loaded, so it's now safe to access its filters.
    captureObject = selection.selectedCapture // Re-get captureObject in case it changed during loading
    filters = getAvailableFilters(captureObject)
    refreshFilter()
  }

  private fun refreshFilter() {
    val currentFilter = getCurrentFilter()
    val availableFilters = if (captureObject == null) Collections.emptyList() else filters
    filterFlow.value = Selection(currentFilter, availableFilters)
  }

  private fun getFilterDisplayName(value: CaptureObjectInstanceFilter?): String {
    return value?.displayName?.let { StringUtils.capitalize(it) } ?: defaultFilterDisplayName
  }
}