/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profilers.FakeGrpcChannel;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.common.ThreadId;
import com.android.tools.profilers.memory.adapters.*;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public abstract class MemoryProfilerTestBase {
  protected StudioProfilers myProfilers;
  protected MemoryProfilerStage myStage;
  protected MockCaptureObjectLoader myMockLoader;
  protected MemoryAspectObserver myAspectObserver;

  @Before
  public void setupBase() {
    myProfilers = new StudioProfilers(getGrpcChannel().getClient(), new FakeIdeProfilerServices());
    onProfilersCreated(myProfilers);
    myMockLoader = new MockCaptureObjectLoader();
    myStage = new MemoryProfilerStage(myProfilers, myMockLoader);
    myAspectObserver = new MemoryAspectObserver(myStage.getAspect());
    myProfilers.setStage(myStage);
  }

  /**
   * Child classes are responsible for providing their own fake grpc channel
   */
  protected abstract FakeGrpcChannel getGrpcChannel();

  protected void onProfilersCreated(StudioProfilers profilers) {
  }

  protected static class MockCaptureObjectLoader extends CaptureObjectLoader {
    @Nullable
    private ListenableFutureTask<CaptureObject> myTask;
    private boolean isReturnImmediateFuture;

    @NotNull
    @Override
    public ListenableFuture<CaptureObject> loadCapture(@NotNull CaptureObject captureObject) {
      if (isReturnImmediateFuture) {
        return Futures.immediateFuture(captureObject);
      }
      else {
        cancelTask();
        myTask = ListenableFutureTask.create(() -> captureObject.load() ? captureObject : null);
        return myTask;
      }
    }

    public void runTask() {
      if (myTask != null) {
        myTask.run();
        myTask = null;
      }
    }

    public void cancelTask() {
      if (myTask != null) {
        myTask.cancel(true);
        myTask = null;
      }
    }

    public void setReturnImmediateFuture(boolean val) {
      isReturnImmediateFuture = val;
      cancelTask();
    }
  }

  @NotNull
  static CaptureObject mockCaptureObject(@NotNull String name, long startTimeNs, long endTimeNs,
                                         @NotNull List<HeapObject> heaps, boolean loadSuccess) {
    CaptureObject object = mock(CaptureObject.class);
    when(object.getLabel()).thenReturn(name);
    when(object.getStartTimeNs()).thenReturn(startTimeNs);
    when(object.getEndTimeNs()).thenReturn(endTimeNs);
    when(object.getHeaps()).thenReturn(heaps);
    when(object.load()).thenReturn(loadSuccess);
    when(object.isDoneLoading()).thenReturn(true);
    when(object.isError()).thenReturn(!loadSuccess);
    return object;
  }

  @NotNull
  static NamespaceObject mockPackageObject(@NotNull String name) {
    NamespaceObject object = mock(NamespaceObject.class);
    when(object.getName()).thenReturn(name);
    return object;
  }

  @NotNull
  static HeapObject mockHeapObject(@NotNull String name, @NotNull List<ClassObject> klasses) {
    HeapObject object = mock(HeapObject.class);
    when(object.getHeapName()).thenReturn(name);
    when(object.getClasses()).thenReturn(klasses);
    when(object.getClassAttributes()).thenReturn(Arrays.asList(ClassObject.ClassAttribute.values()));
    return object;
  }

  @NotNull
  static ClassObject mockClassObject(@NotNull String name,
                                     int instanceSize,
                                     int shallowSize,
                                     long retainedSize,
                                     @NotNull List<InstanceObject> instances) {
    ClassObject object = mock(ClassObject.class);
    when(object.getName()).thenReturn(name);
    when(object.getInstanceSize()).thenReturn(instanceSize);
    when(object.getShallowSize()).thenReturn(shallowSize);
    when(object.getRetainedSize()).thenReturn(retainedSize);
    int lastDotIndex = name.lastIndexOf('.');
    when(object.getClassName()).thenReturn(name.substring(lastDotIndex + 1));
    when(object.getPackageName()).thenReturn(lastDotIndex > 0 ? name.substring(0, lastDotIndex) : "");
    when(object.getSplitPackageName()).thenReturn(lastDotIndex > 0 ? name.substring(0, lastDotIndex).split("\\.") : new String[0]);
    when(object.getTotalCount()).thenReturn(instances.size());
    when(object.getHeapCount()).thenReturn(instances.size());
    when(object.getInstances()).thenReturn(instances);
    when(object.getInstanceAttributes()).thenReturn(Arrays.asList(InstanceObject.InstanceAttribute.values()));
    return object;
  }

  @NotNull
  static InstanceObject mockInstanceObject(@NotNull String className,
                                           @NotNull String label,
                                           @Nullable String toString,
                                           int fieldCount,
                                           int depth,
                                           int shallowSize,
                                           long retainedSize) {
    List<FieldObject> mockedFields = new ArrayList<>(fieldCount);
    for (int i = 0; i < fieldCount; i++) {
      mockedFields.add(mockFieldObject(className, label, "field" + i));
    }
    InstanceObject object = mock(InstanceObject.class);
    when(object.getClassName()).thenReturn(className);
    when(object.getDisplayLabel()).thenReturn(label);
    when(object.getToStringText()).thenReturn(toString);
    when(object.getDepth()).thenReturn(depth);
    when(object.getShallowSize()).thenReturn(shallowSize);
    when(object.getRetainedSize()).thenReturn(retainedSize);
    when(object.getAllocationThreadId()).thenReturn(ThreadId.INVALID_THREAD_ID);
    when(object.getFields()).thenReturn(mockedFields);
    when(object.getFieldCount()).thenReturn(fieldCount);
    when(object.getCallStack()).thenReturn(MemoryProfiler.AllocationStack.newBuilder()
                                             .addStackFrames(MemoryProfiler.AllocationStack.StackFrame.newBuilder().build()).build());
    when(object.getReferenceAttributes()).thenReturn(Arrays.asList(InstanceObject.InstanceAttribute.values()));
    return object;
  }

  @NotNull
  static ReferenceObject mockReferenceObject(@NotNull String label,
                                             int depth,
                                             int shallowSize,
                                             long retainedSize,
                                             @NotNull List<ReferenceObject> referrers,
                                             @Nullable MemoryProfiler.AllocationStack stack) {
    ReferenceObject object = mock(ReferenceObject.class);
    when(object.getDisplayLabel()).thenReturn(label);
    when(object.getDepth()).thenReturn(depth);
    when(object.getShallowSize()).thenReturn(shallowSize);
    when(object.getRetainedSize()).thenReturn(retainedSize);
    when(object.getReferences()).thenReturn(referrers);
    when(object.getAllocationThreadId()).thenReturn(ThreadId.INVALID_THREAD_ID);
    when(object.getReferenceFieldNames()).thenReturn(Collections.emptyList());
    when(object.getReferenceAttributes()).thenReturn(Arrays.asList(InstanceObject.InstanceAttribute.values()));
    when(object.getCallStack()).thenReturn(stack == null ? MemoryProfiler.AllocationStack.newBuilder().build() : stack);
    return object;
  }

  @NotNull
  static FieldObject mockFieldObject(@NotNull String className, @NotNull String label, @NotNull String fieldName) {
    FieldObject object = mock(FieldObject.class);
    when(object.getClassName()).thenReturn(className);
    when(object.getDisplayLabel()).thenReturn(label);
    when(object.getFieldName()).thenReturn(fieldName);
    when(object.getAllocationThreadId()).thenReturn(ThreadId.INVALID_THREAD_ID);
    return object;
  }

  static ClassObject mockClassObjectAutogeneratedInstances(@NotNull String className,
                                                           int size,
                                                           int shallowSize,
                                                           long retainedSize,
                                                           int instancesCount) {
    List<InstanceObject> instances = IntStream.range(0, instancesCount)
      .mapToObj(i -> mockInstanceObject(className, "instance" + i, null, 0, 0, shallowSize, retainedSize / (long)instancesCount))
      .collect(toList());
    return mockClassObject(className, size, shallowSize, retainedSize, instances);
  }
}
