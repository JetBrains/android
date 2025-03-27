/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.memory.adapters

import com.android.tools.idea.codenavigation.CodeLocation
import com.android.tools.inspectors.common.api.stacktrace.ThreadId
import com.android.tools.profiler.proto.Memory.AllocationStack
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap

class LiveAllocationInstanceObject(private val captureObject: LiveAllocationCaptureObject,
                                   private val classEntry: ClassDb.ClassEntry,
                                   private val threadId: ThreadId,
                                   private val callstack: AllocationStack?,
                                   private val size: Long,
                                   private val heapId: Int) : InstanceObject {
  private val valueType = when {
    classEntry.className == "java.lang.String" -> ValueObject.ValueType.STRING
    classEntry.className.endsWith("[]") -> ValueObject.ValueType.ARRAY
    else -> ValueObject.ValueType.OBJECT
  }
  private var allocTime = Long.MIN_VALUE
  private var deallocTime = Long.MAX_VALUE
  private var jniRefs: Long2ObjectOpenHashMap<JniReferenceInstanceObject>? = null

  override fun getAllocTime() = allocTime

  // Set deallocTime as Long.MAX_VALUE when no deallocation event can be found
  fun setDeallocTime(deallocTime: Long) {
    this.deallocTime = deallocTime
  }

  // Set allocTime as Long.MIN_VALUE when no allocation event can be found
  fun setAllocationTime(allocTime: Long) {
    this.allocTime = allocTime
  }

  override fun getDeallocTime() = deallocTime
  override fun hasTimeData() = hasAllocTime() || hasDeallocTime()
  override fun hasAllocTime() = allocTime != Long.MIN_VALUE
  override fun hasDeallocTime() = deallocTime != Long.MAX_VALUE
  override fun getName() = ""
  override fun getHeapId() = heapId
  override fun getShallowSize() = size.toInt()
  override fun getAllocationCallStack() = callstack

  override fun getAllocationCodeLocations() = when (callstack?.frameCase) {
    AllocationStack.FrameCase.ENCODED_STACK -> callstack.encodedStack.framesList.map { frame ->
      val resolvedFrame = captureObject.getStackFrame(frame.methodId)
      CodeLocation.Builder(resolvedFrame.className)
        .setMethodName(resolvedFrame.methodName)
        .setLineNumber(frame.lineNumber - 1)
        .build()
    }
    else -> listOf()
  }

  override fun getAllocationThreadId() = threadId
  override fun getClassEntry() = classEntry
  override fun getValueType() = valueType
  override fun getValueText() = classEntry.simpleClassName

  fun getJniRefByValue(refValue: Long) = jniRefs?.get(refValue)

  fun addJniRef(ref: JniReferenceInstanceObject) {
    if (jniRefs == null) jniRefs = Long2ObjectOpenHashMap()
    jniRefs!!.put(ref.refValue, ref)
  }

  fun removeJniRef(ref: JniReferenceInstanceObject) {
    jniRefs?.remove(ref.refValue)
  }
}