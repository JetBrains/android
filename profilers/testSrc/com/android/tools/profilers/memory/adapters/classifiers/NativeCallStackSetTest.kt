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
    assertThat(callstackSet.createSubClassifier().isTerminalClassifier).isFalse()
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
    whenever(instanceObject.callStackDepth).thenReturn(1)
    whenever(instanceObject.allocationCallStack).thenReturn(allocationStack)
    val callstackSet = classifier.getClassifierSet(instanceObject, true)
    assertThat(callstackSet).isInstanceOf(NativeCallStackSet::class.java)
    assertThat((callstackSet as NativeCallStackSet).name).isEqualTo("Test")
  }

  @Test
  fun classifierLeafNode() {
    val classifier = NativeCallStackSet.createDefaultClassifier()
    val instanceObject = Mockito.mock(InstanceObject::class.java)
    whenever(instanceObject.callStackDepth).thenReturn(0)
    whenever(instanceObject.allocationCallStack).thenReturn(null)
    whenever(instanceObject.classEntry).thenReturn(
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
    whenever(leafInstance.callStackDepth).thenReturn(0)
    whenever(leafInstance.allocationCallStack).thenReturn(null)
    whenever(leafInstance.instanceCount).thenReturn(1)
    whenever(leafInstance.classEntry).thenReturn(
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
    whenever(callStackInstance.callStackDepth).thenReturn(1)
    whenever(callStackInstance.allocationCallStack).thenReturn(allocationStack)
    val callStackSet = classifier.getClassifierSet(callStackInstance, true)!!
    assertThat(callStackSet.addDeltaInstanceObject(leafInstance)).isTrue()
    assertThat(classifier.allClassifierSets).containsExactly(leafSet, callStackSet)
    assertThat(classifier.filteredClassifierSets).containsExactlyElementsIn(classifier.allClassifierSets)
    val filter = Filter("Exclude Test")
    leafSet.applyFilter(filter, true)
    assertThat(classifier.filteredClassifierSets).containsExactly(callStackSet)
  }

  @Test
  fun applyFilter_doesntMatchTopLevel() {
    val classifier = NativeCallStackSet.createDefaultClassifier()
    // Leaf instance 1
    val leafInstance1 = Mockito.mock(InstanceObject::class.java)
    whenever(leafInstance1.callStackDepth).thenReturn(0)
    whenever(leafInstance1.allocationCallStack).thenReturn(null)
    whenever(leafInstance1.instanceCount).thenReturn(1)
    whenever(leafInstance1.classEntry).thenReturn(
      ClassDb.ClassEntry(0, 0, "Test Leaf 1"))
    val leafSet1 = classifier.getClassifierSet(leafInstance1, true)!!
    assertThat(leafSet1.addDeltaInstanceObject(leafInstance1)).isTrue()
    // Leaf instance 2
    val leafInstance2 = Mockito.mock(InstanceObject::class.java)
    whenever(leafInstance2.callStackDepth).thenReturn(0)
    whenever(leafInstance2.allocationCallStack).thenReturn(null)
    whenever(leafInstance2.instanceCount).thenReturn(1)
    whenever(leafInstance2.classEntry).thenReturn(
      ClassDb.ClassEntry(0, 0, "Test Leaf 2"))
    val leafSet2 = classifier.getClassifierSet(leafInstance2, true)!!
    assertThat(leafSet2.addDeltaInstanceObject(leafInstance2)).isTrue()
    // Callstack instance.
    val allocationStack = Memory.AllocationStack.newBuilder()
      .setFullStack(Memory.AllocationStack.StackFrameWrapper.newBuilder()
                      .addFrames(Memory.AllocationStack.StackFrame.newBuilder()
                                   .setMethodName("Test Root")))
      .build()
    val callStackInstance = Mockito.mock(InstanceObject::class.java)
    whenever(callStackInstance.callStackDepth).thenReturn(1)
    whenever(callStackInstance.allocationCallStack).thenReturn(allocationStack)
    val callStackSet = classifier.getClassifierSet(callStackInstance, true)!!
    assertThat(callStackSet.addDeltaInstanceObject(leafInstance1)).isTrue()
    assertThat(callStackSet.addDeltaInstanceObject(leafInstance2)).isTrue()

    callStackSet.applyFilter(Filter("Test"), true)

    assertThat(callStackSet.isMatched).isFalse()
    assertThat(callStackSet.childrenClassifierSets.size).isEqualTo(2)
    assertThat(callStackSet.childrenClassifierSets[0].isMatched).isTrue()
    assertThat(callStackSet.childrenClassifierSets[1].isMatched).isTrue()
  }
}