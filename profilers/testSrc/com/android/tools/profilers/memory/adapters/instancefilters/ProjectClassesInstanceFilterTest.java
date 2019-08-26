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

import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.memory.adapters.FakeCaptureObject;
import com.android.tools.profilers.memory.adapters.FakeInstanceObject;
import com.android.tools.profilers.memory.adapters.InstanceObject;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import org.junit.Test;

public class ProjectClassesInstanceFilterTest {

  @Test
  public void testFilter() {
    FakeIdeProfilerServices ideServices = new FakeIdeProfilerServices();
    ideServices.addProjectClasses("my.foo.bar", "your.bar.foo");

    String matchedClass = "my.foo.bar";
    String matchedInnerClass = "your.bar.foo$1";
    String mismatchedClass = "my.bar";
    String mismatchedInnerClass = "your.foo$1";
    FakeCaptureObject capture = new FakeCaptureObject.Builder().build();

    FakeInstanceObject matchedClassInstance = new FakeInstanceObject.Builder(capture, 1, matchedClass).build();
    FakeInstanceObject matchedInnerClassInstance = new FakeInstanceObject.Builder(capture, 2, matchedInnerClass).build();
    FakeInstanceObject mismatchedClassInstance = new FakeInstanceObject.Builder(capture, 3, mismatchedClass).build();
    FakeInstanceObject mismatchedInnerClassInstance = new FakeInstanceObject.Builder(capture, 4, mismatchedInnerClass).build();
    Set<InstanceObject> instances = ImmutableSet.of(matchedClassInstance,
                                                    matchedInnerClassInstance,
                                                    mismatchedClassInstance,
                                                    mismatchedInnerClassInstance);

    ProjectClassesInstanceFilter filter = new ProjectClassesInstanceFilter(ideServices);
    Set<InstanceObject> result = filter.filter(instances, capture.getClassDatabase());
    assertThat(result).containsExactly(matchedClassInstance, matchedInnerClassInstance);
  }
}
