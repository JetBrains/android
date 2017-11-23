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
package com.android.tools.profilers.memory.adapters;

import com.android.tools.adtui.model.Range;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

import static javax.swing.SortOrder.ASCENDING;
import static javax.swing.SortOrder.DESCENDING;

public interface CaptureObject extends MemoryObject {
  int DEFAULT_HEAP_ID = 0;
  int DEFAULT_CLASSLOADER_ID = -1;
  String INVALID_HEAP_NAME = "INVALID";

  /**
   * Available attributes and sort preferences for {@link ClassifierSet}s. Implementations of {@link CaptureObject} should return a list
   * of the supported attributes.
   */
  enum ClassifierAttribute {
    LABEL(0, ASCENDING),
    ALLOC_COUNT(2, DESCENDING),
    DEALLOC_COUNT(1, DESCENDING),
    NATIVE_SIZE(3, DESCENDING),
    SHALLOW_SIZE(4, DESCENDING),
    RETAINED_SIZE(5, DESCENDING);

    private final int myWeight;

    @NotNull private final SortOrder mySortOrder;

    ClassifierAttribute(int weight, @NotNull SortOrder sortOrder) {
      myWeight = weight;
      mySortOrder = sortOrder;
    }

    public int getWeight() {
      return myWeight;
    }

    @NotNull
    public SortOrder getSortOrder() {
      return mySortOrder;
    }
  }

  /**
   * Available attributes and sort preferences for instances in {@link ClassSet}s. Implementations of {@link CaptureObject} should return a
   * list of the supported attributes.
   */
  enum InstanceAttribute {
    LABEL(1, ASCENDING),
    ALLOCATION_TIME(2, DESCENDING),
    DEALLOCATION_TIME(3, DESCENDING),
    DEPTH(0, ASCENDING),
    NATIVE_SIZE(4, DESCENDING),
    SHALLOW_SIZE(5, DESCENDING),
    RETAINED_SIZE(6, DESCENDING);

    private final int myWeight;

    @NotNull private final SortOrder mySortOrder;

    InstanceAttribute(int weight, @NotNull SortOrder sortOrder) {
      myWeight = weight;
      mySortOrder = sortOrder;
    }

    public int getWeight() {
      return myWeight;
    }

    @NotNull
    public SortOrder getSortOrder() {
      return mySortOrder;
    }
  }

  @Nullable
  default Common.Session getSession() {
    return null;
  }

  default int getProcessId() {
    return -1;
  }

  @Nullable
  default MemoryServiceGrpc.MemoryServiceBlockingStub getClient() {
    return null;
  }

  default boolean isExportable() {
    return false;
  }

  @Nullable
  String getExportableExtension();

  void saveToFile(@NotNull OutputStream outputStream) throws IOException;

  @Nullable
  default MemoryProfiler.StackFrameInfoResponse getStackFrameInfoResponse(long methodId) {
    return null;
  }

  @NotNull
  List<ClassifierAttribute> getClassifierAttributes();

  @NotNull
  List<InstanceAttribute> getInstanceAttributes();

  @NotNull
  Collection<HeapSet> getHeapSets();

  @Nullable
  HeapSet getHeapSet(int heapId);

  @NotNull
  Stream<InstanceObject> getInstances();

  long getStartTimeNs();

  long getEndTimeNs();

  /**
   * Entry point for the {@link CaptureObject} to load its data. Note that it is up to the implementation to listen to changes
   * in the queryRange and make data changes accordingly. The optional queryJoiner allows the implementation to perform
   * operation back on the caller's thread (e.g. notifying UI updates) if bulk loading is done on a separate thread.
   * These parameters are only used by {@link LiveAllocationCaptureObject} instances at the moment, since partial selection/queries are
   * not supported otherwise.
   */
  boolean load(@Nullable Range queryRange, @Nullable Executor queryJoiner);

  boolean isDoneLoading();

  boolean isError();

  void unload();
}
