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

import static javax.swing.SortOrder.ASCENDING;
import static javax.swing.SortOrder.DESCENDING;

import com.android.tools.adtui.model.Range;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Memory;
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import com.android.tools.profilers.memory.ClassGrouping;
import com.android.tools.profilers.memory.adapters.classifiers.ClassSet;
import com.android.tools.profilers.memory.adapters.classifiers.ClassifierSet;
import com.android.tools.profilers.memory.adapters.classifiers.HeapSet;
import com.android.tools.profilers.memory.adapters.instancefilters.ActivityFragmentLeakInstanceFilter;
import com.android.tools.profilers.memory.adapters.instancefilters.CaptureObjectInstanceFilter;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Stream;
import javax.swing.SortOrder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface CaptureObject extends MemoryObject {
  String DEFAULT_HEAP_NAME = "default";
  String IMAGE_HEAP_NAME = "image";
  String ZYGOTE_HEAP_NAME = "zygote";
  String APP_HEAP_NAME = "app";
  String JNI_HEAP_NAME = "JNI";
  String NATIVE_HEAP_NAME = "Native";

  int DEFAULT_HEAP_ID = 0;
  // ID for JNI pseudo-heap, it should not overlap with real Android heaps
  int JNI_HEAP_ID = 4;

  int DEFAULT_CLASSLOADER_ID = -1;
  String INVALID_HEAP_NAME = "INVALID";

  /**
   * Available attributes and sort preferences for {@link ClassifierSet}s. Implementations of {@link CaptureObject} should return a list
   * of the supported attributes.
   */
  enum ClassifierAttribute {
    LABEL(0, ASCENDING),
    ALLOCATIONS(2, DESCENDING),
    DEALLOCATIONS(1, DESCENDING),
    TOTAL_COUNT(3, DESCENDING),
    NATIVE_SIZE(4, DESCENDING),
    SHALLOW_SIZE(5, DESCENDING),
    RETAINED_SIZE(6, DESCENDING),
    ALLOCATIONS_SIZE(7, DESCENDING),
    DEALLOCATIONS_SIZE(8, DESCENDING),
    REMAINING_SIZE(9, DESCENDING),
    MODULE(10, ASCENDING),
    SHALLOW_DIFFERENCE(0, DESCENDING);

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
    ALLOCATION_TIME(3, DESCENDING),
    DEALLOCATION_TIME(2, DESCENDING),
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
  default String getInfoMessage() {
    return null;
  }

  @Nullable
  default Common.Session getSession() {
    return null;
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
  default Memory.AllocationStack.StackFrame getStackFrame(long methodId) {
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

  @NotNull
  ClassDb getClassDatabase();

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

  @NotNull
  default Set<CaptureObjectInstanceFilter> getSupportedInstanceFilters() {
    return Collections.EMPTY_SET;
  }

  /**
   * @return the filter of leaked activities and fragments if that is supported, or null otherwise
   * An implementation of this method should only return a filter in `getSupportedInstanceFilters`
   */
  @Nullable
  default ActivityFragmentLeakInstanceFilter getActivityFragmentLeakFilter() {
    return null;
  }

  @NotNull
  default Set<CaptureObjectInstanceFilter> getSelectedInstanceFilters() {
    return Collections.EMPTY_SET;
  }

  default ListenableFuture<Void> addInstanceFilter(@NotNull CaptureObjectInstanceFilter filter,
                                                   @NotNull Executor analyzeJoiner) {
    return Utils.makeEmptyTask();
  }

  default ListenableFuture<Void> removeInstanceFilter(@NotNull CaptureObjectInstanceFilter filter,
                                                      @NotNull Executor analyzeJoiner) {
    return Utils.makeEmptyTask();
  }

  default ListenableFuture<Void> setSingleFilter(@NotNull CaptureObjectInstanceFilter filter,
                                                 @NotNull Executor analyzeJoiner) {
    return Utils.makeEmptyTask();
  }

  default ListenableFuture<Void> removeAllFilters(@NotNull Executor analyzeJoiner) {
    return Utils.makeEmptyTask();
  }

  default boolean isGroupingSupported(ClassGrouping grouping) {
    switch (grouping) {
      case ARRANGE_BY_CLASS:
      case ARRANGE_BY_PACKAGE:
      case ARRANGE_BY_CALLSTACK:
        return true;
      case NATIVE_ARRANGE_BY_ALLOCATION_METHOD:
      case NATIVE_ARRANGE_BY_CALLSTACK:
      default:
        return false;
    }
  }

  default boolean canSafelyLoad() {
    return true;
  }

  final class Utils {
    static ListenableFutureTask<Void> makeEmptyTask() {
      return ListenableFutureTask.create(() -> null);
    }
  }
}