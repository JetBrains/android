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

import static com.android.tools.profilers.memory.MemoryProfiler.saveLegacyAllocationToFile;

import com.android.tools.adtui.model.Range;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Memory;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.memory.LegacyAllocationConverter;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LegacyAllocationCaptureObject implements CaptureObject {
  static final int DEFAULT_HEAP_ID = 0;
  static final String DEFAULT_HEAP_NAME = "default";

  @NotNull private final ProfilerClient myClient;
  @NotNull private final ClassDb myClassDb;
  @NotNull private final Common.Session mySession;
  @NotNull private final Memory.AllocationsInfo myInfo;
  @NotNull private final LegacyAllocationConverter myAllocationConverter;
  private long myStartTimeNs;
  private long myEndTimeNs;
  private final FeatureTracker myFeatureTracker;
  private volatile boolean myIsDoneLoading = false;
  private volatile boolean myIsLoadingError = false;
  // Allocation records do not have heap information, but we create a fake HeapSet container anyway so that we have a consistent MemoryObject model.
  private final HeapSet myFakeHeapSet;

  public LegacyAllocationCaptureObject(@NotNull ProfilerClient client,
                                       @NotNull Common.Session session,
                                       @NotNull Memory.AllocationsInfo info,
                                       @NotNull FeatureTracker featureTracker) {
    myClient = client;
    myClassDb = new ClassDb();
    mySession = session;
    myInfo = info;
    myAllocationConverter = new LegacyAllocationConverter();
    myStartTimeNs = info.getStartTime();
    myEndTimeNs = info.getEndTime();
    myFakeHeapSet = new HeapSet(this, DEFAULT_HEAP_NAME, DEFAULT_HEAP_ID);
    myFeatureTracker = featureTracker;
  }

  @NotNull
  @Override
  public String getName() {
    return "Recorded Allocations";
  }

  @Override
  public boolean isExportable() {
    return true;
  }

  @Nullable
  @Override
  public String getExportableExtension() {
    return "alloc";
  }

  @Override
  public void saveToFile(@NotNull OutputStream outputStream) {
    saveLegacyAllocationToFile(myClient, mySession, myInfo, outputStream, myFeatureTracker);
  }

  @NotNull
  @Override
  public Stream<InstanceObject> getInstances() {
    //noinspection ConstantConditions
    assert isDoneLoading() && !isError();
    return myFakeHeapSet.getInstancesStream();
  }

  @Override
  public long getStartTimeNs() {
    return myStartTimeNs;
  }

  @Override
  public long getEndTimeNs() {
    return myEndTimeNs;
  }

  @Override
  public boolean load(@Nullable Range queryRange, @Nullable Executor queryJoiner) {
    Transport.BytesResponse response;
    while (true) {
      response = myClient.getTransportClient().getBytes(Transport.BytesRequest.newBuilder()
                                                          .setStreamId(mySession.getStreamId())
                                                          .setId(Long.toString(myInfo.getStartTime()))
                                                          .build());
      if (!response.getContents().isEmpty()) {
        break;
      }
      else {
        try {
          Thread.sleep(50L);
        }
        catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          myIsLoadingError = true;
          return false;
        }
      }
    }

    // TODO remove this map, since we have built-in functionality in ClassDb now.
    Map<Integer, ClassDb.ClassEntry> classEntryMap = new HashMap<>();
    Map<Integer, Memory.AllocationStack> callStacks = new HashMap<>();
    myAllocationConverter.parseDump(response.getContents().toByteArray());
    myAllocationConverter.getAllocationStacks().forEach(stack -> callStacks.putIfAbsent(stack.getStackId(), stack));
    myAllocationConverter.getClassNames().forEach(
      // We don't have super class information so just assign invalid id as the super class id.
      klass -> classEntryMap.put(klass.getClassId(), myClassDb.registerClass(klass.getClassId(), klass.getClassName())));

    for (Memory.AllocationEvent.Allocation event : myAllocationConverter.getAllocationEvents()) {
      assert classEntryMap.containsKey(event.getClassTag());
      assert callStacks.containsKey(event.getStackId());
      myFakeHeapSet.addDeltaInstanceObject(
        new LegacyAllocationsInstanceObject(event, classEntryMap.get(event.getClassTag()), callStacks.get(event.getStackId())));
    }
    myIsDoneLoading = true;

    return true;
  }

  @Override
  public boolean isDoneLoading() {
    return myIsDoneLoading || myIsLoadingError;
  }

  @Override
  public boolean isError() {
    return myIsLoadingError;
  }

  @Override
  public void unload() {

  }

  @Override
  @NotNull
  public List<ClassifierAttribute> getClassifierAttributes() {
    return Arrays.asList(ClassifierAttribute.LABEL, ClassifierAttribute.ALLOCATIONS, ClassifierAttribute.SHALLOW_SIZE);
  }

  @NotNull
  @Override
  public List<CaptureObject.InstanceAttribute> getInstanceAttributes() {
    return Arrays.asList(CaptureObject.InstanceAttribute.LABEL, CaptureObject.InstanceAttribute.SHALLOW_SIZE);
  }

  @NotNull
  @Override
  public Collection<HeapSet> getHeapSets() {
    return Collections.singletonList(myFakeHeapSet);
  }

  @Override
  @Nullable
  public HeapSet getHeapSet(int heapId) {
    assert heapId == DEFAULT_HEAP_ID;
    return myFakeHeapSet;
  }
}
