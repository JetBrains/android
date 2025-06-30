/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.profilers.memory;

import static com.android.tools.profilers.memory.MemoryProfilerTestUtils.findChildClassSetWithName;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.model.filter.Filter;
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel;
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.android.tools.profilers.memory.adapters.FakeCaptureObject;
import com.android.tools.profilers.memory.adapters.FakeInstanceObject;
import com.android.tools.profilers.memory.adapters.ValueObject;
import com.android.tools.profilers.memory.adapters.classifiers.ClassSet;
import com.android.tools.profilers.memory.adapters.classifiers.ClassifierSet;
import com.android.tools.profilers.memory.adapters.classifiers.HeapSet;
import com.intellij.testFramework.ApplicationRule;
import com.intellij.testFramework.DisposableRule;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class MemoryClassifierSetTest {
  private final FakeTimer myTimer = new FakeTimer();
  @Rule public final FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("MemoryClassifierSetTest", new FakeTransportService(myTimer));
  @Rule public final ApplicationRule myApplicationRule = new ApplicationRule();
  @Rule public final DisposableRule myDisposableRule = new DisposableRule();

  private MainMemoryProfilerStage myStage;
  private FakeCaptureObject myCaptureObject;

  @Before
  public void before() {
    StudioProfilers profilers = new StudioProfilers(new ProfilerClient(myGrpcChannel.getChannel()), new FakeIdeProfilerServices(), myTimer);
    FakeCaptureObjectLoader loader = new FakeCaptureObjectLoader();
    loader.setReturnImmediateFuture(true);
    myStage = new MainMemoryProfilerStage(profilers, loader);
    myStage.getTimeline().setStreaming(false);
  }

  @Test
  public void testFindContainingClassifierSetWithFilter() {
    // Setup capture with ClassA and ClassB, where ClassB references ClassA
    final String classAName = "ClassA";
    final String classBName = "ClassB";
    myCaptureObject = new FakeCaptureObject.Builder().build();
    FakeInstanceObject instanceA =
      new FakeInstanceObject.Builder(myCaptureObject, 1, classAName).setName("instanceA").build();
    FakeInstanceObject instanceB =
      new FakeInstanceObject.Builder(myCaptureObject, 2, classBName).setName("instanceB")
        .setFields(Collections.singletonList("fieldA")).build();
    instanceB.setFieldValue("fieldA", ValueObject.ValueType.OBJECT, instanceA);
    myCaptureObject.addInstanceObjects(new HashSet<>(Arrays.asList(instanceA, instanceB)));

    myStage.selectCaptureDuration(
      new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> myCaptureObject)), null);

    // Filter for ClassA
    myStage.getCaptureSelection().getFilterHandler().setFilter(new Filter(classAName));

    // Get the HeapSet
    HeapSet heapSet = myStage.getCaptureSelection().getSelectedHeapSet();
    assertThat(heapSet).isNotNull();

    // The HeapSet should contain a ClassSet for ClassA, but not for ClassB
    assertThat(findChildClassSetWithName(heapSet, classAName)).isNotNull();

    boolean classBFound = heapSet.getChildrenClassifierSets().stream()
      .filter(ClassSet.class::isInstance)
      .map(ClassSet.class::cast)
      .anyMatch(cs -> classBName.equals(cs.getClassEntry().getClassName()));
    assertThat(classBFound).isFalse();

    // Even though ClassB is filtered out, we should still be able to find its containing classifier set.
    ClassifierSet containingSet = heapSet.findContainingClassifierSet(instanceB);
    assertThat(containingSet).isNotNull();
    assertThat(containingSet).isInstanceOf(ClassSet.class);
    assertThat(((ClassSet)containingSet).getClassEntry().getClassName()).isEqualTo(classBName);
  }
}