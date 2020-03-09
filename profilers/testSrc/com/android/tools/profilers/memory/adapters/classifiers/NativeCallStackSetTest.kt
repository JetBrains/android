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

import com.android.tools.adtui.model.filter.Filter
import com.android.tools.profiler.proto.Memory
import com.android.tools.profilers.memory.adapters.ClassDb
import com.android.tools.profilers.memory.adapters.InstanceObject
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.mockito.Mockito

class NativeCallStackSetTest {
  @Test
  fun subClassifierIsDefault() {
    val callstackSet = NativeCallStackSet(Memory.AllocationStack.StackFrame.getDefaultInstance(), 0)
    assertThat(callstackSet.createSubClassifier()).isInstanceOf(
      NativeFunctionClassifier::class.java)
  }

  @Test
  fun classifierChildNode() {
    val classifier = NativeCallStackSet.createDefaultClassifier()
    val allocationStack = Memory.AllocationStack.newBuilder()
      .setFullStack(Memory.AllocationStack.StackFrameWrapper.newBuilder()
                      .addFrames(Memory.AllocationStack.StackFrame.newBuilder()
                                   .setMethodName("Test")))
      .build()
    val instanceObject = Mockito.mock(InstanceObject::class.java)
    Mockito.`when`(instanceObject.callStackDepth).thenReturn(1)
    Mockito.`when`(instanceObject.allocationCallStack).thenReturn(allocationStack)
    val callstackSet = classifier.getClassifierSet(instanceObject, true)
    assertThat(callstackSet).isInstanceOf(NativeCallStackSet::class.java)
    assertThat((callstackSet as NativeCallStackSet).name).isEqualTo("Test")
  }

  @Test
  fun classifierLeafNode() {
    val classifier = NativeCallStackSet.createDefaultClassifier()
    val instanceObject = Mockito.mock(InstanceObject::class.java)
    Mockito.`when`(instanceObject.callStackDepth).thenReturn(0)
    Mockito.`when`(instanceObject.allocationCallStack).thenReturn(null)
    Mockito.`when`(instanceObject.classEntry).thenReturn(
      ClassDb.ClassEntry(0, 0, "Test"))
    val callstackSet = classifier.getClassifierSet(instanceObject, true)
    assertThat(callstackSet).isInstanceOf(NativeAllocationMethodSet::class.java)
    assertThat((callstackSet as NativeAllocationMethodSet).name).isEqualTo("Test")
  }

  @Test
  fun nodesCachedAndFiltered() {
    val classifier = NativeCallStackSet.createDefaultClassifier()
    // Leaf instance
    val leafInstance = Mockito.mock(InstanceObject::class.java)
    Mockito.`when`(leafInstance.callStackDepth).thenReturn(0)
    Mockito.`when`(leafInstance.allocationCallStack).thenReturn(null)
    Mockito.`when`(leafInstance.instanceCount).thenReturn(1)
    Mockito.`when`(leafInstance.classEntry).thenReturn(
      ClassDb.ClassEntry(0, 0, "Test"))
    val leafSet = classifier.getClassifierSet(leafInstance, true)!!
    assertThat(leafSet.addDeltaInstanceObject(leafInstance)).isTrue()
    // Callstack instance.
    val allocationStack = Memory.AllocationStack.newBuilder()
      .setFullStack(Memory.AllocationStack.StackFrameWrapper.newBuilder()
                      .addFrames(Memory.AllocationStack.StackFrame.newBuilder()
                                   .setMethodName("Test Method")))
      .build()
    val callStackInstance = Mockito.mock(InstanceObject::class.java)
    Mockito.`when`(callStackInstance.callStackDepth).thenReturn(1)
    Mockito.`when`(callStackInstance.allocationCallStack).thenReturn(allocationStack)
    val callStackSet = classifier.getClassifierSet(callStackInstance, true)!!
    assertThat(callStackSet.addDeltaInstanceObject(leafInstance)).isTrue()
    assertThat(classifier.allClassifierSets).containsExactly(leafSet, callStackSet)
    assertThat(classifier.filteredClassifierSets).containsExactlyElementsIn(classifier.allClassifierSets)
    val filter = Filter("Exclude Test")
    leafSet.applyFilter(filter, false, true)
    assertThat(classifier.filteredClassifierSets).containsExactly(callStackSet)
  }
}