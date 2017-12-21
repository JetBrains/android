// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.profilers.memory.adapters;

import com.android.tools.profiler.proto.MemoryProfiler.AllocationStack;
import com.android.tools.profilers.stacktrace.ThreadId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class JniReferenceInstanceObject implements InstanceObject {
  @NotNull private final LiveAllocationInstanceObject myReferencedObject;
  private long myAllocTime = Long.MIN_VALUE;
  private long myDeallocTime = Long.MAX_VALUE;
  private final long myRefValue;
  private final long myObjectTag;
  @Nullable private AllocationStack myAllocationCallstack;
  @Nullable private AllocationStack myDeallocationCallstack;
  @NotNull private final ThreadId myAllocThreadId;
  @NotNull private final ThreadId myDeallocThreadId;
  @Nullable private List<FieldObject> myFields;

  private static final String REF_NAME_FORMATTER = "JNI Global Reference (0x%x)";
  private static final String OBJECT_NAME_FORMATTER = "%s@%d";

  /**
   * Creates a new instance object for tracking global JNI references.
   *
   * @param  referencedObject  instance object for a java object this JNI reference points to
   * @param  allocThreadId     thread where this JNI reference was allocated
   * @param  objectTag         JVM TI tag for the java object this JNI reference points to
   * @param  refValue          value of the global JNI references (jobject, jstring, jclass)
   *
   */
  public JniReferenceInstanceObject(@NotNull LiveAllocationInstanceObject referencedObject,
                                    @Nullable ThreadId allocThreadId,
                                    long objectTag,
                                    long refValue) {
    myReferencedObject = referencedObject;
    myRefValue = refValue;
    myObjectTag = objectTag;
    myAllocThreadId = allocThreadId == null ? ThreadId.INVALID_THREAD_ID : allocThreadId;
    myDeallocThreadId = ThreadId.INVALID_THREAD_ID;
  }

  // Set allocTime as Long.MIN_VALUE when no allocation event can be found
  public void setAllocationTime(long allocTime) {
    myAllocTime = allocTime;
  }

  @Override
  public long getAllocTime() {
    return myAllocTime;
  }

  // Set deallocTime as Long.MAX_VALUE when no deallocation event can be found
  public void setDeallocTime(long deallocTime) {
    myDeallocTime = deallocTime;
  }

  @Override
  public long getDeallocTime() {
    return myDeallocTime;
  }

  public void setAllocationStack(@NotNull AllocationStack callstack) {
    myAllocationCallstack = callstack;
  }

  @Nullable
  @Override
  public AllocationStack getAllocationCallStack() {
    return myAllocationCallstack;
  }

  public void setDeallocationStack(@NotNull AllocationStack callstack) {
    myDeallocationCallstack = callstack;
  }

  @Nullable
  public AllocationStack getDeallocationStack() {
    return myAllocationCallstack;
  }

  @Override
  public boolean hasTimeData() {
    return hasAllocTime() || hasDeallocTime();
  }

  @Override
  public boolean hasAllocTime() {
    return myAllocTime != Long.MIN_VALUE;
  }

  @Override
  public boolean hasDeallocTime() {
    return myDeallocTime != Long.MAX_VALUE;
  }

  @NotNull
  @Override
  public String getName() {
    return "";
  }

  @Override
  public int getHeapId() {
    return LiveAllocationCaptureObject.JNI_HEAP_ID;
  }

  @Override
  public int getShallowSize() {
    return myReferencedObject.getShallowSize();
  }

  @Override
  public boolean getIsRoot() {
    return true;
  }

  @Override
  public int getFieldCount() {
    return 1;
  }

  @NotNull
  @Override
  public List<FieldObject> getFields() {
    if (myFields == null) {
      myFields = Collections.singletonList(new JniRefField());
    }
    return myFields;
  }

  @NotNull
  @Override
  public ThreadId getAllocationThreadId() {
    return myAllocThreadId;
  }

  @NotNull
  public ThreadId getDeallocationThreadId() {
    return myDeallocThreadId;
  }

  @NotNull
  @Override
  public ClassDb.ClassEntry getClassEntry() {
    return myReferencedObject.getClassEntry();
  }

  @Nullable
  @Override
  public InstanceObject getClassObject() {
    return myReferencedObject.getClassObject();
  }

  @NotNull
  @Override
  public ValueType getValueType() {
    return myReferencedObject.getValueType();
  }

  @NotNull
  @Override
  public String getValueText() {
    return String.format(REF_NAME_FORMATTER, myRefValue);
  }

  public long getRefValue() {
    return myRefValue;
  }

  private class JniRefField implements FieldObject {
    @NotNull
    @Override
    public String getFieldName() {
      return "";
    }

    @Nullable
    @Override
    public InstanceObject getAsInstance() {
      return myReferencedObject;
    }

    @Nullable
    @Override
    public Object getValue() {
      return "";
    }

    @NotNull
    @Override
    public ValueType getValueType() {
      return ValueType.OBJECT;
    }

    @NotNull
    @Override
    public String getValueText() {
      return String.format(OBJECT_NAME_FORMATTER, myReferencedObject.getValueText(), myObjectTag);
    }

    @NotNull
    @Override
    public String getName() {
      return "";
    }
  }
}
