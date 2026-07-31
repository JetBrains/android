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
package com.android.tools.profilers.memory.adapters.instancefilters

import com.android.tools.profiler.perfetto.proto.TraceProcessor
import com.android.tools.profilers.memory.adapters.CaptureObject
import com.android.tools.profilers.memory.adapters.ClassDb
import com.android.tools.profilers.memory.adapters.HeapDumpCaptureObject
import com.android.tools.profilers.memory.adapters.InstanceObject
import com.android.tools.profilers.memory.adapters.TraceProcessorHeapDumpInstanceObject
import com.android.tools.profilers.memory.adapters.ValueObject
import com.google.common.annotations.VisibleForTesting

/** A filter to locate possible leaked activity/fragment instances for Trace Processor heap dumps. */
class TraceProcessorActivityFragmentLeakFilter(classDatabase: ClassDb, private val captureObject: CaptureObject? = null) :
  ActivityFragmentLeakInstanceFilter(classDatabase) {

  private var leakedInstances: Set<InstanceObject>? = null

  private val tpInstanceTest = makeTpLeakTest(classDatabase)

  override val instanceTest: (InstanceObject) -> Boolean = { inst -> leakedInstances?.contains(inst) ?: tpInstanceTest(inst) }

  private val allActivitySubclasses by lazy {
    classDatabase.getEntriesByName(ACTIVTY_CLASS_NAME).flatMapTo(HashSet()) { classEntry ->
      classDatabase.getDescendantClasses(classEntry.classId)
    }
  }

  private val allFragmentSubclasses by lazy {
    FRAGMENT_CLASS_NAMES.flatMap { className -> classDatabase.getEntriesByName(className) }
      .flatMapTo(HashSet()) { classEntry -> classDatabase.getDescendantClasses(classEntry.classId) }
  }

  // We override the instance test to use our pre-computed set if it exists, otherwise fallback to the dynamic test.
  // This allows the UI's ClassifierSet to perform O(1) checks during rendering.
  override fun filter(instances: Set<InstanceObject>): Set<InstanceObject> {
    if (leakedInstances == null) {
      preloadLeakTestFields(instances)
    }
    val leaks = leakedInstances ?: return emptySet()
    return instances.filterTo(HashSet()) { leaks.contains(it) }
  }

  fun preloadLeakTestFields(instances: Set<InstanceObject>) {
    try {
      if (captureObject is HeapDumpCaptureObject) {
        val targetInstances = instances.filter { it.classEntry in allActivitySubclasses || it.classEntry in allFragmentSubclasses }
        if (targetInstances.isNotEmpty()) {
          val targetMap =
            targetInstances
              .mapNotNull {
                val tpInst = it as? TraceProcessorHeapDumpInstanceObject
                if (tpInst != null) tpInst.instanceId to tpInst else null
              }
              .toMap()
          val ids = targetMap.keys.toList()

          ids.chunked(1000).forEach { chunk ->
            val result = captureObject.getPrimitiveFieldsBulk(chunk)
            // Mark all requested instances as preloaded (even if empty) to prevent fallback gRPC calls
            chunk.forEach { requestedId ->
              val instObj = targetMap[requestedId]
              if (instObj != null && instObj.preloadedPrimitiveFields == null) {
                instObj.preloadedPrimitiveFields = TraceProcessor.GetPrimitiveFieldsResult.InstancePrimitiveFields.getDefaultInstance()
              }
            }
            result.instancesList.forEach { protoInst ->
              val instObj = targetMap[protoInst.instanceId]
              if (instObj != null) {
                instObj.preloadedPrimitiveFields = protoInst
              }
            }
          }
        }
      }
      // After preloading all fields, run the leak evaluation ONCE and store the result.
      leakedInstances = instances.filter { tpInstanceTest(it) }.toSet()
    } catch (e: Exception) {
      // In case of a gRPC error or timeout, cache an empty set to prevent infinite retry loops on every UI frame.
      leakedInstances = emptySet()
    }
  }

  companion object {
    const val ACTIVTY_CLASS_NAME = "android.app.Activity"

    // native android Fragment, deprecated as of API 28.
    const val NATIVE_FRAGMENT_CLASS_NAME = "android.app.Fragment"

    // pre-androidx, support library version of the Fragment implementation.
    const val SUPPORT_FRAGMENT_CLASS_NAME = "android.support.v4.app.Fragment"

    // androidx version of the Fragment implementation
    const val ANDROIDX_FRAGMENT_CLASS_NAME = "androidx.fragment.app.Fragment"

    @VisibleForTesting const val FINISHED_FIELD_NAME = "mFinished"

    @VisibleForTesting const val DESTROYED_FIELD_NAME = "mDestroyed"

    @VisibleForTesting const val FRAGFMENT_MANAGER_FIELD_NAME = "mFragmentManager"
    private val FRAGMENT_CLASS_NAMES = arrayOf(NATIVE_FRAGMENT_CLASS_NAME, SUPPORT_FRAGMENT_CLASS_NAME, ANDROIDX_FRAGMENT_CLASS_NAME)

    private fun makeTpLeakTest(classDatabase: ClassDb): (InstanceObject) -> Boolean {
      val allActivitySubclasses by lazy {
        classDatabase.getEntriesByName(ACTIVTY_CLASS_NAME).flatMapTo(HashSet()) { classEntry ->
          classDatabase.getDescendantClasses(classEntry.classId)
        }
      }
      val allFragmentSubclasses by lazy {
        FRAGMENT_CLASS_NAMES.flatMap { className -> classDatabase.getEntriesByName(className) }
          .flatMapTo(HashSet()) { classEntry -> classDatabase.getDescendantClasses(classEntry.classId) }
      }
      return {
        it.valueType == ValueObject.ValueType.OBJECT &&
          (it.classEntry in allActivitySubclasses && isPotentialActivityLeak(it) ||
            it.classEntry in allFragmentSubclasses && isPotentialFragmentLeak(it))
      }
    }

    /**
     * An Activity instance is determined to be leaked if its mDestroyed/mFinished field has been set to true, and the instance still has a
     * valid depth (not waiting to be GC'd).
     */
    private fun isPotentialActivityLeak(instance: InstanceObject): Boolean {
      return isValidDepthWithAnyField(
        instance,
        { FINISHED_FIELD_NAME == it || DESTROYED_FIELD_NAME == it },
        { it == true || it == 1 || it == "true" || it == "1" },
      )
    }

    /**
     * A Fragment instance is determined to be potentially leaked if its mFragmentManager field is null. This indicates that the instance is
     * in its initial state. Note that this can mean that the instance has been destroyed, or just starting to be initialized but before
     * being attached to an activity. The latter gives us false positives, but it should not uncommon as long as users don't create
     * fragments way ahead of the time of adding them to a FragmentManager.
     */
    private fun isPotentialFragmentLeak(instance: InstanceObject): Boolean {
      val depth = instance.depth
      if (depth == 0 || depth == Int.MAX_VALUE) return false

      // In Perfetto TraceProcessor, null references are omitted from the graph entirely.
      // Therefore, if the mFragmentManager field is missing, it is effectively null.
      // For legacy Perflib, it might be present with a null value.
      val field = instance.fields.find { it.fieldName == FRAGFMENT_MANAGER_FIELD_NAME }
      return field == null || field.value == null
    }

    /** Check if the instance has a valid depth and any field satisfying predicates on its name and value */
    private fun isValidDepthWithAnyField(inst: InstanceObject, onName: (String) -> Boolean, onVal: (Any?) -> Boolean): Boolean {
      val depth = inst.depth
      return depth != 0 && depth != Int.MAX_VALUE && inst.fields.any { onName(it.fieldName) && onVal(it.value) }
    }
  }
}
