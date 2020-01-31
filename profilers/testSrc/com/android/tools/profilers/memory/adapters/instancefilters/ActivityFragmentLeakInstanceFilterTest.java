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
package com.android.tools.profilers.memory.adapters.instancefilters;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.profilers.memory.adapters.FakeCaptureObject;
import com.android.tools.profilers.memory.adapters.FakeInstanceObject;
import com.android.tools.profilers.memory.adapters.InstanceObject;
import com.android.tools.profilers.memory.adapters.ValueObject;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import org.junit.Test;

public class ActivityFragmentLeakInstanceFilterTest {

  @Test
  public void testActivityLeaks() {
    long baseClassActivityId = 1;
    long subClassActivityId = 2;
    long unrelatedClassId = 3;

    FakeCaptureObject capture = new FakeCaptureObject.Builder().build();

    // android.app.activity instance with mFinished field set to true and valid depth.
    FakeInstanceObject leakingBaseClassActivity =
      new FakeInstanceObject.Builder(capture, baseClassActivityId, ActivityFragmentLeakInstanceFilter.ACTIVTY_CLASS_NAME)
        .addField(ActivityFragmentLeakInstanceFilter.FINISHED_FIELD_NAME, ValueObject.ValueType.BOOLEAN, true)
        .setDepth(1)
        .build();
    // android.app.activity instance with no corresponding fields we can detect leaks from.
    FakeInstanceObject nonLeakingBaseClassActivity =
      new FakeInstanceObject.Builder(capture, baseClassActivityId, ActivityFragmentLeakInstanceFilter.ACTIVTY_CLASS_NAME)
        .addField("blah", ValueObject.ValueType.BOOLEAN, true)
        .build();
    // android.app.activity subclass instance with mDestroyed field set to true and valid depth.
    FakeInstanceObject leakingSubClassActivity =
      new FakeInstanceObject.Builder(capture, subClassActivityId, "my.activity.subclass")
        .setSuperClassId(baseClassActivityId)
        .addField(ActivityFragmentLeakInstanceFilter.DESTROYED_FIELD_NAME, ValueObject.ValueType.BOOLEAN, true)
        .setDepth(1)
        .build();
    // android.app.activity subclass instance with no corresponding fields we can detect leaks from.
    FakeInstanceObject nonLeakingSubClassActivity1 =
      new FakeInstanceObject.Builder(capture, subClassActivityId, "my.activity.subclass")
        .setSuperClassId(baseClassActivityId)
        .addField("blah", ValueObject.ValueType.BOOLEAN, true)
        .build();
    // android.app.activity subclass instance with mDestroyed field set to true but invalid depth (waiting to be gc'd instance)
    FakeInstanceObject nonLeakingSubClassActivity2 =
      new FakeInstanceObject.Builder(capture, subClassActivityId, "my.activity.subclass")
        .setSuperClassId(baseClassActivityId)
        .addField(ActivityFragmentLeakInstanceFilter.DESTROYED_FIELD_NAME, ValueObject.ValueType.BOOLEAN, true)
        .build();
    // unrelated class instance that does not belong to the android.app.activity hierarchy.
    FakeInstanceObject unrelatedClassInstance =
      new FakeInstanceObject.Builder(capture, unrelatedClassId, "my.other.class")
        .addField(ActivityFragmentLeakInstanceFilter.DESTROYED_FIELD_NAME, ValueObject.ValueType.BOOLEAN, true)
        .build();

    Set<InstanceObject> instances = ImmutableSet.of(leakingBaseClassActivity,
                                                    nonLeakingBaseClassActivity,
                                                    leakingSubClassActivity,
                                                    nonLeakingSubClassActivity1,
                                                    nonLeakingSubClassActivity2,
                                                    unrelatedClassInstance);

    ActivityFragmentLeakInstanceFilter filter = new ActivityFragmentLeakInstanceFilter();
    Set<InstanceObject> result = filter.filter(instances, capture.getClassDatabase());
    assertThat(result).containsExactly(leakingBaseClassActivity, leakingSubClassActivity);
  }

  @Test
  public void testFragmentLeaks() {
    long baseClassNativeFragmentId = 1;
    long subClassNativeFragmentId = 2;
    long baseClassSupportFragmentId = 3;
    long baseClassAndroidxFragmentId = 5;

    FakeCaptureObject capture = new FakeCaptureObject.Builder().build();

    // null FragmentManager and not waiting to be GC'd
    FakeInstanceObject leakingNativeBaseNativeFragment =
      new FakeInstanceObject.Builder(capture, baseClassNativeFragmentId, ActivityFragmentLeakInstanceFilter.NATIVE_FRAGMENT_CLASS_NAME)
        .addField(ActivityFragmentLeakInstanceFilter.FRAGFMENT_MANAGER_FIELD_NAME, ValueObject.ValueType.OBJECT, null)
        .setDepth(1)
        .build();
    // null FragmentManager but waiting to be GC'd
    FakeInstanceObject nonLeakingBaseNativeFragment =
      new FakeInstanceObject.Builder(capture, baseClassNativeFragmentId, ActivityFragmentLeakInstanceFilter.NATIVE_FRAGMENT_CLASS_NAME)
        .addField(ActivityFragmentLeakInstanceFilter.FRAGFMENT_MANAGER_FIELD_NAME, ValueObject.ValueType.OBJECT, null)
        .build();
    // null FragmentManager and not waiting to be GC'd
    FakeInstanceObject leakingSubClassNativeFragment =
      new FakeInstanceObject.Builder(capture, subClassNativeFragmentId, "my.native.fragment.subclass")
        .setSuperClassId(baseClassNativeFragmentId)
        .addField(ActivityFragmentLeakInstanceFilter.FRAGFMENT_MANAGER_FIELD_NAME, ValueObject.ValueType.OBJECT, null)
        .setDepth(1)
        .build();
    // non-null FragmentManager
    FakeInstanceObject nonLeakingSubClassNativeFragment =
      new FakeInstanceObject.Builder(capture, subClassNativeFragmentId, "my.native.fragment.subclass")
        .setSuperClassId(baseClassNativeFragmentId)
        .addField(ActivityFragmentLeakInstanceFilter.FRAGFMENT_MANAGER_FIELD_NAME, ValueObject.ValueType.OBJECT, new Object())
        .setDepth(1)
        .build();
    // null FragmentManager and not waiting to be GC'd
    FakeInstanceObject leakingSupportBaseNativeFragment =
      new FakeInstanceObject.Builder(capture, baseClassSupportFragmentId, ActivityFragmentLeakInstanceFilter.SUPPORT_FRAGMENT_CLASS_NAME)
        .addField(ActivityFragmentLeakInstanceFilter.FRAGFMENT_MANAGER_FIELD_NAME, ValueObject.ValueType.OBJECT, null)
        .setDepth(1)
        .build();
    // no mFragmentManager field
    FakeInstanceObject nonleakingAndroidXBaseNativeFragment =
      new FakeInstanceObject.Builder(capture, baseClassAndroidxFragmentId, ActivityFragmentLeakInstanceFilter.ANDROIDX_FRAGMENT_CLASS_NAME)
        .build();

    Set<InstanceObject> instances = ImmutableSet.of(leakingNativeBaseNativeFragment,
                                                    nonLeakingBaseNativeFragment,
                                                    leakingSubClassNativeFragment,
                                                    nonLeakingSubClassNativeFragment,
                                                    leakingSupportBaseNativeFragment,
                                                    nonleakingAndroidXBaseNativeFragment);

    ActivityFragmentLeakInstanceFilter filter = new ActivityFragmentLeakInstanceFilter();
    Set<InstanceObject> result = filter.filter(instances, capture.getClassDatabase());
    assertThat(result).containsExactly(leakingNativeBaseNativeFragment, leakingSubClassNativeFragment, leakingSupportBaseNativeFragment);
  }
}