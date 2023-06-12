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
package com.android.tools.profilers.memory.adapters.classifiers

import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.model.filter.Filter
import com.android.tools.profilers.memory.adapters.ClassDb
import com.android.tools.profilers.memory.adapters.InstanceObject
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.Mockito.mock

class NativeAllocationMethodSetTest {
  @Test
  fun subClassifierIsDefault() {
    val callstackSet = NativeAllocationMethodSet("Test")
    assertThat(callstackSet.createSubClassifier()).isEqualTo(
      Classifier.Id)
  }

  @Test
  fun applyFilter() {
    val callstackSet = NativeAllocationMethodSet("Test")
    val filter = Filter("Test")
    callstackSet.applyFilter(filter, true)
    assertThat(filter.matches(callstackSet.stringForMatching)).isTrue()
  }

  @Test
  fun classifier() {
    val classifier = NativeAllocationMethodSet.createDefaultClassifier()
    val instanceObject = mock(InstanceObject::class.java)
    whenever(instanceObject.classEntry).thenReturn(
      ClassDb.ClassEntry(0, 0, "Test"))
    val callstackSet = classifier.getClassifierSet(instanceObject, true)
    assertThat(callstackSet).isInstanceOf(NativeAllocationMethodSet::class.java)
    assertThat((callstackSet as NativeAllocationMethodSet).name).isEqualTo("Test")
  }
}