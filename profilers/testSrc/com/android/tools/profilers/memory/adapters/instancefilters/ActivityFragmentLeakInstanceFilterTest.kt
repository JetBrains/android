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

import com.android.tools.profilers.memory.adapters.FakeCaptureObject
import com.android.tools.profilers.memory.adapters.FakeInstanceObject
import com.android.tools.profilers.memory.adapters.ValueObject.ValueType.BOOLEAN
import com.android.tools.profilers.memory.adapters.ValueObject.ValueType.OBJECT
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ActivityFragmentLeakInstanceFilterTest {

  @Test
  fun testActivityLeaks() {
    val baseClassActivityId = 1L
    val subClassActivityId = 2L
    val unrelatedClassId = 3L

    val capture = FakeCaptureObject.Builder().build()

    // android.app.activity instance with mFinished field set to true and valid depth.
    val leakingBaseClassActivity =
      FakeInstanceObject.Builder(capture, baseClassActivityId, ActivityFragmentLeakInstanceFilter.ACTIVTY_CLASS_NAME)
        .addField(ActivityFragmentLeakInstanceFilter.FINISHED_FIELD_NAME, BOOLEAN, true)
        .setDepth(1)
        .build()
    // android.app.activity instance with no corresponding fields we can detect leaks from.
    val nonLeakingBaseClassActivity =
      FakeInstanceObject.Builder(capture, baseClassActivityId, ActivityFragmentLeakInstanceFilter.ACTIVTY_CLASS_NAME)
        .addField("blah", BOOLEAN, true)
        .build()
    // android.app.activity subclass instance with mDestroyed field set to true and valid depth.
    val leakingSubClassActivity =
      FakeInstanceObject.Builder(capture, subClassActivityId, "my.activity.subclass")
        .setSuperClassId(baseClassActivityId)
        .addField(ActivityFragmentLeakInstanceFilter.DESTROYED_FIELD_NAME, BOOLEAN, true)
        .setDepth(1)
        .build()
    // android.app.activity subclass instance with no corresponding fields we can detect leaks from.
    val nonLeakingSubClassActivity1 =
      FakeInstanceObject.Builder(capture, subClassActivityId, "my.activity.subclass")
        .setSuperClassId(baseClassActivityId)
        .addField("blah", BOOLEAN, true)
        .build()
    // android.app.activity subclass instance with mDestroyed field set to true but invalid depth (waiting to be gc'd instance)
    val nonLeakingSubClassActivity2 =
      FakeInstanceObject.Builder(capture, subClassActivityId, "my.activity.subclass")
        .setSuperClassId(baseClassActivityId)
        .addField(ActivityFragmentLeakInstanceFilter.DESTROYED_FIELD_NAME, BOOLEAN, true)
        .build()
    // unrelated class instance that does not belong to the android.app.activity hierarchy.
    val unrelatedClassInstance =
      FakeInstanceObject.Builder(capture, unrelatedClassId, "my.other.class")
        .addField(ActivityFragmentLeakInstanceFilter.DESTROYED_FIELD_NAME, BOOLEAN, true)
        .build()

    val instances =
      setOf(
        leakingBaseClassActivity,
        nonLeakingBaseClassActivity,
        leakingSubClassActivity,
        nonLeakingSubClassActivity1,
        nonLeakingSubClassActivity2,
        unrelatedClassInstance,
      )

    val filter = ActivityFragmentLeakInstanceFilter(capture.classDatabase)
    val result = filter.filter(instances)
    assertThat(result).containsExactly(leakingBaseClassActivity, leakingSubClassActivity)
  }

  @Test
  fun testFragmentLeaks() {
    val baseClassNativeFragmentId = 1L
    val subClassNativeFragmentId = 2L
    val baseClassSupportFragmentId = 3L
    val baseClassAndroidxFragmentId = 5L

    val capture = FakeCaptureObject.Builder().build()

    // null FragmentManager and not waiting to be GC'd
    val leakingNativeBaseNativeFragment =
      FakeInstanceObject.Builder(capture, baseClassNativeFragmentId, ActivityFragmentLeakInstanceFilter.NATIVE_FRAGMENT_CLASS_NAME)
        .addField(ActivityFragmentLeakInstanceFilter.FRAGFMENT_MANAGER_FIELD_NAME, OBJECT, null)
        .setDepth(1)
        .build()
    // null FragmentManager but waiting to be GC'd
    val nonLeakingBaseNativeFragment =
      FakeInstanceObject.Builder(capture, baseClassNativeFragmentId, ActivityFragmentLeakInstanceFilter.NATIVE_FRAGMENT_CLASS_NAME)
        .addField(ActivityFragmentLeakInstanceFilter.FRAGFMENT_MANAGER_FIELD_NAME, OBJECT, null)
        .build()
    // null FragmentManager and not waiting to be GC'd
    val leakingSubClassNativeFragment =
      FakeInstanceObject.Builder(capture, subClassNativeFragmentId, "my.native.fragment.subclass")
        .setSuperClassId(baseClassNativeFragmentId)
        .addField(ActivityFragmentLeakInstanceFilter.FRAGFMENT_MANAGER_FIELD_NAME, OBJECT, null)
        .setDepth(1)
        .build()
    // non-null FragmentManager
    val nonLeakingSubClassNativeFragment =
      FakeInstanceObject.Builder(capture, subClassNativeFragmentId, "my.native.fragment.subclass")
        .setSuperClassId(baseClassNativeFragmentId)
        .addField(ActivityFragmentLeakInstanceFilter.FRAGFMENT_MANAGER_FIELD_NAME, OBJECT, Any())
        .setDepth(1)
        .build()
    // null FragmentManager and not waiting to be GC'd
    val leakingSupportBaseNativeFragment =
      FakeInstanceObject.Builder(capture, baseClassSupportFragmentId, ActivityFragmentLeakInstanceFilter.SUPPORT_FRAGMENT_CLASS_NAME)
        .addField(ActivityFragmentLeakInstanceFilter.FRAGFMENT_MANAGER_FIELD_NAME, OBJECT, null)
        .setDepth(1)
        .build()
    // no mFragmentManager field
    val nonleakingAndroidXBaseNativeFragment =
      FakeInstanceObject.Builder(capture, baseClassAndroidxFragmentId, ActivityFragmentLeakInstanceFilter.ANDROIDX_FRAGMENT_CLASS_NAME)
        .build()

    val instances =
      setOf(
        leakingNativeBaseNativeFragment,
        nonLeakingBaseNativeFragment,
        leakingSubClassNativeFragment,
        nonLeakingSubClassNativeFragment,
        leakingSupportBaseNativeFragment,
        nonleakingAndroidXBaseNativeFragment,
      )

    val filter = ActivityFragmentLeakInstanceFilter(capture.classDatabase)
    val result = filter.filter(instances)
    assertThat(result)
      .containsExactly(
        leakingNativeBaseNativeFragment,
        leakingSubClassNativeFragment,
        leakingSupportBaseNativeFragment,
      )
  }
}
