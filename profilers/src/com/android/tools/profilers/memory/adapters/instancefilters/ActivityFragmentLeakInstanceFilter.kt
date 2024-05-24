/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.profilers.memory.adapters.ClassDb
import com.android.tools.profilers.memory.adapters.FieldObject
import com.android.tools.profilers.memory.adapters.InstanceObject
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableSet
import java.util.function.Predicate
import java.util.stream.Collectors

/**
 * A filter to locate possible leaked activity/fragment instances.
 */
class ActivityFragmentLeakInstanceFilter(classDatabase: ClassDb)
  : CaptureObjectInstanceFilter(
  "activity/fragment leaks",
  "Show Activities and Fragments that are potentially causing memory leaks.",
  "Activity/Fragment leaks may include false positives.",
  "https://developer.android.com/r/studio-ui/profiler-memory-leak-detection",
  makeLeakTest(classDatabase)) {

  companion object {
    @VisibleForTesting
    const val ACTIVTY_CLASS_NAME = "android.app.Activity"

    // native android Fragment, deprecated as of API 28.
    @VisibleForTesting
    const val NATIVE_FRAGMENT_CLASS_NAME = "android.app.Fragment"

    // pre-androidx, support library version of the Fragment implementation.
    @VisibleForTesting
    const val SUPPORT_FRAGMENT_CLASS_NAME = "android.support.v4.app.Fragment"

    // androidx version of the Fragment implementation
    @VisibleForTesting
    const val ANDROIDX_FRAGMENT_CLASS_NAME = "androidx.fragment.app.Fragment"

    @VisibleForTesting
    const val FINISHED_FIELD_NAME = "mFinished"

    @VisibleForTesting
    const val DESTROYED_FIELD_NAME = "mDestroyed"

    @VisibleForTesting
    const val FRAGFMENT_MANAGER_FIELD_NAME = "mFragmentManager"
    private val FRAGMENT_CLASS_NAMES = arrayOf(NATIVE_FRAGMENT_CLASS_NAME,
                                               SUPPORT_FRAGMENT_CLASS_NAME,
                                               ANDROIDX_FRAGMENT_CLASS_NAME)

    private fun makeLeakTest(classDatabase: ClassDb): (InstanceObject) -> Boolean {
      val allActivitySubclasses by lazy {
        classDatabase.getEntriesByName(ACTIVTY_CLASS_NAME)
          .flatMapTo(HashSet()) { classEntry -> classDatabase.getDescendantClasses(classEntry.classId) }
      }
      val allFragmentSubclasses by lazy {
        FRAGMENT_CLASS_NAMES
          .flatMap { className -> classDatabase.getEntriesByName(className) }
          .flatMapTo(HashSet()) { classEntry -> classDatabase.getDescendantClasses(classEntry.classId) }
      }
      return { it.classEntry in allActivitySubclasses && isPotentialActivityLeak(it) ||
               it.classEntry in allFragmentSubclasses && isPotentialFragmentLeak(it) }
    }

    /**
     * An Activity instance is determined to be leaked if its mDestroyed/mFinished field has been set to true, and the instance still has a
     * valid depth (not waiting to be GC'd).
     */
    private fun isPotentialActivityLeak(instance: InstanceObject): Boolean {
      return isValidDepthWithAnyField(instance,
                                      { FINISHED_FIELD_NAME == it || DESTROYED_FIELD_NAME == it },
                                      { it as Boolean })
    }

    /**
     * A Fragment instance is determined to be potentially leaked if its mFragmentManager field is null. This indicates that the instance
     * is in its initial state. Note that this can mean that the instance has been destroyed, or just starting to be initialized but before
     * being attached to an activity. The latter gives us false positives, but it should not uncommon as long as users don't create fragments
     * way ahead of the time of adding them to a FragmentManager.
     */
    private fun isPotentialFragmentLeak(instance: InstanceObject): Boolean {
      return isValidDepthWithAnyField(instance, { FRAGFMENT_MANAGER_FIELD_NAME == it }, { it == null })
    }

    /**
     * Check if the instance has a valid depth and any field satisfying predicates on its name and value
     */
    private fun isValidDepthWithAnyField(inst: InstanceObject,
                                         onName: (String) -> Boolean,
                                         onVal: (Any?) -> Boolean): Boolean {
      val depth = inst.depth
      return depth != 0 && depth != Int.MAX_VALUE &&
             inst.fields.any { onName(it.fieldName) && onVal(it.value) }
    }
  }
}