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

import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.memory.adapters.FakeCaptureObject
import com.android.tools.profilers.memory.adapters.FakeInstanceObject
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProjectClassesInstanceFilterTest {

  @Test
  fun testFilter() {
    val ideServices =
      FakeIdeProfilerServices().apply {
        addProjectClasses("my.foo.bar", "your.bar.foo")
      }

    val matchedClass = "my.foo.bar"
    val matchedInnerClass = "your.bar.foo$1"
    val mismatchedClass = "my.bar"
    val mismatchedInnerClass = "your.foo$1"
    val capture = FakeCaptureObject.Builder().build()

    val matchedClassInstance = FakeInstanceObject.Builder(capture, 1, matchedClass).build()
    val matchedInnerClassInstance = FakeInstanceObject.Builder(capture, 2, matchedInnerClass).build()
    val mismatchedClassInstance = FakeInstanceObject.Builder(capture, 3, mismatchedClass).build()
    val mismatchedInnerClassInstance = FakeInstanceObject.Builder(capture, 4, mismatchedInnerClass).build()
    val instances =
      setOf(
        matchedClassInstance,
        matchedInnerClassInstance,
        mismatchedClassInstance,
        mismatchedInnerClassInstance,
      )

    val filter = ProjectClassesInstanceFilter(ideServices)
    val result = filter.filter(instances)
    assertThat(result).containsExactly(matchedClassInstance, matchedInnerClassInstance)
  }
}
