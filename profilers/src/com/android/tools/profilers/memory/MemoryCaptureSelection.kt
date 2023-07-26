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

import com.android.tools.adtui.model.AspectModel
import com.android.tools.adtui.model.ConditionalEnumComboBoxModel
import com.android.tools.adtui.model.filter.Filter
import com.android.tools.adtui.model.filter.FilterHandler
import com.android.tools.adtui.model.filter.FilterResult
import com.android.tools.inspectors.common.api.stacktrace.StackTraceModel
import com.android.tools.profilers.IdeProfilerServices
import com.android.tools.profilers.analytics.FilterMetadata
import com.android.tools.profilers.memory.adapters.CaptureObject
import com.android.tools.profilers.memory.adapters.FieldObject
import com.android.tools.profilers.memory.adapters.InstanceObject
import com.android.tools.profilers.memory.adapters.classifiers.ClassSet
import com.android.tools.profilers.memory.adapters.classifiers.HeapSet
import com.android.tools.profilers.memory.adapters.instancefilters.CaptureObjectInstanceFilter
import com.google.common.util.concurrent.ListenableFuture
import java.util.Objects
import java.util.concurrent.Executor

/**
 * This class manages the capture selection state and fires aspects when it is changed.
 */
class MemoryCaptureSelection(val ideServices: IdeProfilerServices) {
  val aspect = AspectModel<CaptureSelectionAspect>()

  val classGroupingModel = ConditionalEnumComboBoxModel(ClassGrouping::class.java) {grouping ->
    selectedCapture?.isGroupingSupported(grouping) ?: true
  }
  val filterHandler = object : FilterHandler() {
    override fun applyFilter(filter: Filter): FilterResult {
      selectCaptureFilter(filter)
      return when (val heapSet = this@MemoryCaptureSelection.selectedHeapSet) {
        null -> FilterResult.EMPTY_RESULT
        else -> FilterResult(heapSet.filterMatchCount, 0, true)
      }
    }
  }
  val allocationStackTraceModel = StackTraceModel(ideServices.codeNavigator)
  val deallocationStackTraceModel = StackTraceModel(ideServices.codeNavigator)

  private var lastFilter: Filter? = null

  private var myCaptureEntry: CaptureEntry<*>? = null
  var selectedCapture: CaptureObject? = null
    private set
  var selectedHeapSet: HeapSet? = null
    private set(heapSet) {
      if (field !== heapSet) {
        field = heapSet
        aspect.changed(CaptureSelectionAspect.CURRENT_HEAP)
      }
    }
  var selectedClassSet: ClassSet? = null
    private set(classSet) {
      if (field !== classSet) {
        field = classSet
        aspect.changed(CaptureSelectionAspect.CURRENT_CLASS)
      }
    }
  var selectedInstanceObject: InstanceObject? = null
    private set(instanceObject) {
      if (field !== instanceObject) {
        field = instanceObject
        aspect.changed(CaptureSelectionAspect.CURRENT_INSTANCE)
      }
    }
  var selectedFieldObjectPath: List<FieldObject> = emptyList()
    private set(fieldObjectPath) {
      if (field !== fieldObjectPath) {
        field = fieldObjectPath
        aspect.changed(CaptureSelectionAspect.CURRENT_FIELD_PATH)
      }
    }

  var classGrouping = ClassGrouping.ARRANGE_BY_CLASS
    set(newGrouping) {
      if (field != newGrouping) {
        field = newGrouping
        ideServices.featureTracker.trackChangeClassArrangment()
        aspect.changed(CaptureSelectionAspect.CLASS_GROUPING)
        filterHandler.refreshFilterContent()
      }
    }


  /**
   * @return true if the internal state changed, otherwise false
   */
  fun selectCaptureEntry(captureEntry: CaptureEntry<out CaptureObject>?): Boolean {
    if (Objects.equals(myCaptureEntry, captureEntry)) {
      return false
    }
    selectedFieldObjectPath = emptyList()
    selectedInstanceObject = null
    selectedClassSet = null
    selectedHeapSet = null
    selectedCapture?.unload()
    myCaptureEntry = captureEntry
    selectedCapture = captureEntry?.captureObject
    classGroupingModel.update()
    classGrouping = classGroupingModel.getElementAt(0)
    aspect.changed(CaptureSelectionAspect.CURRENT_LOADING_CAPTURE)
    return true
  }

  fun selectHeapSet(heapSet: HeapSet?) {
    assert(heapSet == null || selectedCapture != null)
    if (selectedHeapSet !== heapSet) {
      selectedFieldObjectPath = emptyList()
      selectedInstanceObject = null
      selectedClassSet = null
      selectedHeapSet = heapSet
    }

    filterHandler.refreshFilterContent()
    heapSet?.let {
      ideServices.featureTracker.trackSelectMemoryHeap(it.name)
    }
  }

  /**
   * @return true if the internal state changed, otherwise false
   */
  fun selectClassSet(classSet: ClassSet?): Boolean {
    assert(classSet == null || selectedCapture != null)
    if (selectedClassSet === classSet) {
      return false
    }
    selectedFieldObjectPath = emptyList()
    selectedInstanceObject = null
    selectedClassSet = classSet
    return true
  }

  /**
   * @return true if the internal state changed, otherwise false
   */
  fun selectInstanceObject(instanceObject: InstanceObject?): Boolean {
    assert(instanceObject == null || selectedCapture != null)
    if (selectedInstanceObject === instanceObject) {
      return false
    }
    selectedFieldObjectPath = emptyList()
    selectedInstanceObject = instanceObject
    return true
  }

  /**
   * @return true if the internal state changed, otherwise false
   */
  fun selectFieldObjectPath(fieldObjectPath: List<FieldObject>): Boolean {
    assert(fieldObjectPath.isEmpty() || selectedCapture != null && selectedInstanceObject != null)
    if (Objects.equals(selectedFieldObjectPath, fieldObjectPath)) {
      return false
    }
    selectedFieldObjectPath = fieldObjectPath
    return true
  }

  /**
   * Re-apply the filter to selected heap's instances after they have changed
   */
  fun refreshSelectedHeap() {
    aspect.changed(CaptureSelectionAspect.CURRENT_HEAP_CONTENTS)
    filterHandler.refreshFilterContent()
    selectedHeapSet?.let { heap ->
      // Keep previously selected instance selected if it's still in refreshed heap
      val prevInst = selectedInstanceObject
      if (prevInst != null) {
        when (val newClass = heap.findContainingClassifierSet(prevInst)) {
          is ClassSet -> {
            val prevFields = selectedFieldObjectPath
            selectClassSet(newClass)
            selectInstanceObject(prevInst)
            selectFieldObjectPath(prevFields)
          }
          null -> selectInstanceObject(null)
        }
      }

      // If instance deselected, try to re-select the "same" class-set if it's still relevant
      if (selectedInstanceObject == null && selectedClassSet != null) {
        val className = selectedClassSet!!.name
        when (val newClass = heap.findClassifierSet { it.name == className }) {
          is ClassSet -> selectClassSet(newClass)
          else -> selectClassSet(ClassSet.EMPTY_SET)
        }
      }
    }
  }

  /**
   * @return true if selection was committed successfully
   */
  fun finishSelectingCaptureObject(captureObject: CaptureObject?): Boolean {
    if (captureObject != null && captureObject === selectedCapture && !captureObject.isError && captureObject.isDoneLoading) {
      aspect.changed(CaptureSelectionAspect.CURRENT_LOADED_CAPTURE)
      return true
    }
    return false
  }

  fun addInstanceFilter(filter: CaptureObjectInstanceFilter, joiner: Executor) =
    runCaptureInstanceFilter(joiner) { it.addInstanceFilter(filter, joiner) }

  fun removeInstanceFilter(filter: CaptureObjectInstanceFilter, joiner: Executor) =
    runCaptureInstanceFilter(joiner) { it.removeInstanceFilter(filter, joiner) }

  fun setFilter(filter: CaptureObjectInstanceFilter, joiner: Executor) =
    runCaptureInstanceFilter(joiner) { it.setSingleFilter(filter, joiner) }

  fun removeAllFilters(joiner: Executor) =
    runCaptureInstanceFilter(joiner) { it.removeAllFilters(joiner) }

  private fun runCaptureInstanceFilter(joiner: Executor, update: (CaptureObject) -> ListenableFuture<Void>) {
    aspect.changed(CaptureSelectionAspect.CURRENT_HEAP_UPDATING)
    update(selectedCapture!!).addListener(Runnable {
      aspect.changed(CaptureSelectionAspect.CURRENT_HEAP_UPDATED)
      refreshSelectedHeap()
    }, joiner)
  }


  private fun selectCaptureFilter(filter: Filter) {
    // Only track filter usage when filter has been updated.
    if (!Objects.equals(lastFilter, filter)) {
      lastFilter = filter
      trackFilterUsage(filter)
    }
    selectedHeapSet?.selectFilter(filter)

    // Clears the selected ClassSet if it's been filtered.
    if (selectedClassSet != null && selectedClassSet!!.isFiltered) {
      selectClassSet(ClassSet.EMPTY_SET)
    }
    aspect.changed(CaptureSelectionAspect.CURRENT_FILTER)
  }

  private fun trackFilterUsage(filter: Filter) {
    val filterMetadata = FilterMetadata()
    val featureTracker = ideServices.featureTracker
    when (classGrouping) {
      ClassGrouping.ARRANGE_BY_CLASS -> filterMetadata.view = FilterMetadata.View.MEMORY_CLASS
      ClassGrouping.ARRANGE_BY_PACKAGE -> filterMetadata.view = FilterMetadata.View.MEMORY_PACKAGE
      ClassGrouping.ARRANGE_BY_CALLSTACK -> filterMetadata.view = FilterMetadata.View.MEMORY_CALLSTACK
      ClassGrouping.NATIVE_ARRANGE_BY_ALLOCATION_METHOD,
      ClassGrouping.NATIVE_ARRANGE_BY_CALLSTACK -> {}
    }
    filterMetadata.setFeaturesUsed(filter.isMatchCase, filter.isRegex)
    selectedHeapSet?.let {
      filterMetadata.matchedElementCount = it.filteredObjectSetCount
      filterMetadata.totalElementCount = it.totalObjectSetCount
    }
    filterMetadata.filterTextLength = if (filter.isEmpty) 0 else filter.filterString.length
    featureTracker.trackFilterMetadata(filterMetadata)
  }
}

enum class CaptureSelectionAspect {
  CLASS_GROUPING,
  CURRENT_LOADING_CAPTURE,
  CURRENT_LOADED_CAPTURE,
  CURRENT_CAPTURE_ELAPSED_TIME,
  CURRENT_HEAP,
  CURRENT_HEAP_UPDATING,
  CURRENT_HEAP_UPDATED,
  CURRENT_HEAP_CONTENTS,
  CURRENT_CLASS,
  CURRENT_INSTANCE,
  CURRENT_FIELD_PATH,
  CURRENT_FILTER
}

enum class ClassGrouping(val label: String) {
  ARRANGE_BY_CLASS("Arrange by class"),
  ARRANGE_BY_PACKAGE("Arrange by package"),
  ARRANGE_BY_CALLSTACK("Arrange by callstack"),
  NATIVE_ARRANGE_BY_ALLOCATION_METHOD("Arrange by allocation method"),
  NATIVE_ARRANGE_BY_CALLSTACK("Arrange by callstack");
  override fun toString() = label
}