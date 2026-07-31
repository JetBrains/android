/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.profilers.memory.adapters.classifiers

import com.android.tools.profiler.perfetto.proto.TraceProcessor.QueryParameters
import com.android.tools.profilers.memory.adapters.CaptureObject
import com.android.tools.profilers.memory.adapters.ClassDb
import com.android.tools.profilers.memory.adapters.HeapDumpCaptureObject
import com.android.tools.profilers.memory.adapters.InstanceObject
import com.android.tools.profilers.memory.adapters.instancefilters.ActivityFragmentLeakInstanceFilter
import com.android.tools.profilers.memory.adapters.instancefilters.AllClassTypeFilter
import com.android.tools.profilers.memory.adapters.instancefilters.AllIssuesInstanceFilter
import com.android.tools.profilers.memory.adapters.instancefilters.BitmapDuplicationInstanceFilter
import com.android.tools.profilers.memory.adapters.instancefilters.CaptureObjectInstanceFilter
import com.android.tools.profilers.memory.adapters.instancefilters.NoneFilter
import com.android.tools.profilers.memory.adapters.instancefilters.ProjectClassesInstanceFilter
import com.android.tools.profilers.memory.adapters.instancefilters.SystemClassesInstanceFilter

/**
 * Specialization of [ClassSet] for Perfetto Trace Processor captures that offloads instance querying, pagination, and sorting directly to
 * the daemon.
 */
class TraceProcessorHeapDumpClassSet(classEntry: ClassDb.ClassEntry, val heapId: Int, val captureObject: HeapDumpCaptureObject) :
  ClassSet(classEntry) {

  private var sortAttributeEnum = QueryParameters.SortAttribute.SORT_RETAINED_SIZE
  private var sortDescending: Boolean = true

  override fun setSort(attribute: CaptureObject.InstanceAttribute, isDescending: Boolean) {
    sortDescending = isDescending
    sortAttributeEnum =
      when (attribute) {
        CaptureObject.InstanceAttribute.SHALLOW_SIZE -> QueryParameters.SortAttribute.SORT_SHALLOW_SIZE
        CaptureObject.InstanceAttribute.RETAINED_SIZE -> QueryParameters.SortAttribute.SORT_RETAINED_SIZE
        CaptureObject.InstanceAttribute.RETAINED_NATIVE_SIZE -> QueryParameters.SortAttribute.SORT_RETAINED_NATIVE_SIZE
        CaptureObject.InstanceAttribute.NATIVE_SIZE -> QueryParameters.SortAttribute.SORT_NATIVE_SIZE
        CaptureObject.InstanceAttribute.DEPTH -> QueryParameters.SortAttribute.SORT_DEPTH
        CaptureObject.InstanceAttribute.LABEL -> QueryParameters.SortAttribute.SORT_LABEL
        else -> QueryParameters.SortAttribute.SORT_RETAINED_SIZE
      }
  }

  override fun findContainingClassifierSet(target: InstanceObject): ClassifierSet? {
    return if (target.classEntry.classId == this.classEntry.classId) this else null
  }

  override val isClassFilterMatch: Boolean
    get() {
      val cFilter = captureObject.classTypeFilter
      val iFilter = captureObject.issueTypeFilter

      // 1. CLASS FILTERS (Project / System / All)
      when (cFilter) {
        is ProjectClassesInstanceFilter -> {
          val baseClassName = classEntry.className.substringBefore('$')
          if (!captureObject.projectClasses.contains(baseClassName)) {
            return false
          }
        }
        is SystemClassesInstanceFilter -> {
          val baseClassName = classEntry.className.substringBefore('$')
          if (captureObject.projectClasses.contains(baseClassName)) {
            return false
          }
        }
        is AllClassTypeFilter,
        is NoneFilter,
        null -> {
          // No class filter applied, or 'All classes' selected. Everything matches.
        }
      }

      // 2. ISSUE FILTERS (Leaks / Duplicates / All)
      when (iFilter) {
        is ActivityFragmentLeakInstanceFilter -> {
          if (!captureObject.classesWithLeaks.contains(classEntry.className)) return false
        }
        is BitmapDuplicationInstanceFilter -> {
          if (!captureObject.classesWithDuplicates.contains(classEntry.className)) return false
        }
        is AllIssuesInstanceFilter -> {
          if (
            !captureObject.classesWithLeaks.contains(classEntry.className) &&
              !captureObject.classesWithDuplicates.contains(classEntry.className)
          ) {
            return false
          }
        }
        is NoneFilter,
        null -> {
          // No issue filter applied. Everything matches.
        }
      }

      return true
    }

  override val instancesCount: Int
    get() = totalObjectCount

  override fun getInstances(offset: Int, limit: Int): List<InstanceObject> {
    val localInstances = captureObject.getClassObjectInstances(classEntry.classId).filter { heapId == 0 || it.heapId == heapId }
    val localCount = localInstances.size
    val result = mutableListOf<InstanceObject>()

    if (offset < localCount) {
      val localSlice = localInstances.drop(offset).take(limit)
      result.addAll(localSlice)
    }

    if (result.size < limit) {
      val remainingLimit = limit - result.size
      val daemonOffset = maxOf(0, offset - localCount)
      val heapName = captureObject.getHeapSet(heapId)?.name ?: ""
      val request =
        QueryParameters.HeapDumpInstancesParameters.newBuilder()
          .addClassNames(classEntry.className)
          .setOffset(daemonOffset)
          .setLimit(remainingLimit)
          .setSortAttribute(sortAttributeEnum)
          .setSortDescending(sortDescending)
          .setHeapName(heapName)
          .build()

      val response =
        captureObject.ideProfilerServices.traceProcessorService.getInstances(
          captureObject.heapDumpInfo.startTime,
          request,
          captureObject.ideProfilerServices,
        )

      val daemonInstances = response.instanceList.map { inst -> captureObject.getOrCreateTraceProcessorHeapDumpInstance(classEntry, inst) }
      result.addAll(daemonInstances)
    }
    return result
  }

  override fun countInstanceFilterMatch(filter: CaptureObjectInstanceFilter): Int {
    if (filter is ActivityFragmentLeakInstanceFilter || filter is BitmapDuplicationInstanceFilter) {
      return captureObject.filterInstances.count { it.classEntry.classId == classEntry.classId && filter.instanceTest(it) }
    }
    return super.countInstanceFilterMatch(filter)
  }
}
