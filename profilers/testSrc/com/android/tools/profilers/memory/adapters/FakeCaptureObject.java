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
package com.android.tools.profilers.memory.adapters;

import com.android.tools.adtui.model.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

public final class FakeCaptureObject implements CaptureObject {
  @NotNull private final String myCaptureName;
  @NotNull private final List<ClassifierAttribute> myClassifierAttributes;
  @NotNull private final List<InstanceAttribute> myInstanceAttributes;
  @NotNull private final Set<InstanceObject> myInstanceObjects = new HashSet<>();
  @NotNull private Map<Integer, HeapSet> myHeapSets = new HashMap<>();
  @NotNull private ClassDb myClassDb = new ClassDb();
  @NotNull private final Map<Integer, String> myHeapIdToName;
  private final long myStartTime;
  private final long myEndTime;
  private final boolean myIsLoadSuccessful;
  private final boolean myIsDoneLoading;
  private final boolean myIsError;

  private FakeCaptureObject(@NotNull String captureName,
                            @NotNull List<ClassifierAttribute> classifierAttributes,
                            @NotNull List<InstanceAttribute> instanceAttributes,
                            @NotNull Map<Integer, String> heapIdToName,
                            long startTime, long endTime,
                            boolean isLoadSuccessful, boolean isDoneLoading, boolean isError) {
    myCaptureName = captureName;
    myClassifierAttributes = classifierAttributes;
    myInstanceAttributes = instanceAttributes;
    myHeapIdToName = heapIdToName;
    myStartTime = startTime;
    myEndTime = endTime;
    myIsLoadSuccessful = isLoadSuccessful;
    myIsDoneLoading = isDoneLoading;
    myIsError = isError;
  }

  @NotNull
  @Override
  public String getName() {
    return myCaptureName;
  }

  @Nullable
  @Override
  public String getExportableExtension() {
    return null;
  }

  @Override
  public void saveToFile(@NotNull OutputStream outputStream) throws IOException {

  }

  @NotNull
  @Override
  public List<ClassifierAttribute> getClassifierAttributes() {
    return myClassifierAttributes;
  }

  @NotNull
  @Override
  public List<InstanceAttribute> getInstanceAttributes() {
    return myInstanceAttributes;
  }

  @NotNull
  public ClassDb.ClassEntry registerClass(long classLoaderId, @NotNull String className) {
    return myClassDb.registerClass(classLoaderId, className);
  }

  public boolean containsClass(long classLoaderId, @NotNull String className) {
    return myClassDb.containsClassEntry(classLoaderId, className);
  }

  public void addInstanceObjects(@NotNull Set<InstanceObject> instanceObjects) {
    myInstanceObjects.addAll(instanceObjects);
    myHeapSets.clear();
    for (InstanceObject instanceObject : myInstanceObjects) {
      HeapSet set = myHeapSets.computeIfAbsent(instanceObject.getHeapId(), id -> {
        String heapName = myHeapIdToName.containsKey(id) ? myHeapIdToName.get(id) : INVALID_HEAP_NAME;
        return new HeapSet(this, heapName, id);
      });
      set.addDeltaInstanceObject(instanceObject);
    }
  }

  @NotNull
  @Override
  public Collection<HeapSet> getHeapSets() {
    return myHeapSets.values();
  }

  @Nullable
  @Override
  public HeapSet getHeapSet(int heapId) {
    assert myHeapIdToName.containsKey(heapId);
    return myHeapSets.get(heapId);
  }

  @NotNull
  @Override
  public Stream<InstanceObject> getInstances() {
    return myInstanceObjects.stream();
  }

  @Override
  public long getStartTimeNs() {
    return myStartTime;
  }

  @Override
  public long getEndTimeNs() {
    return myEndTime;
  }

  @Override
  public boolean load(@Nullable Range queryRange, @Nullable Executor queryJoiner) {
    return myIsLoadSuccessful;
  }

  @Override
  public boolean isDoneLoading() {
    return myIsDoneLoading;
  }

  @Override
  public boolean isError() {
    return myIsError;
  }

  @Override
  public void unload() {

  }

  public static class Builder {
    public static final int DEFAULT_HEAP_ID = 0;
    public static final String DEFAULT_HEAP_NAME = "default";

    @NotNull private String myCaptureName = "DUMMY_CAPTURE";
    @NotNull private List<ClassifierAttribute> myClassifierAttributes = Arrays.asList(CaptureObject.ClassifierAttribute.values());
    @NotNull private List<InstanceAttribute> myInstanceAttributes = Arrays.asList(CaptureObject.InstanceAttribute.values());
    @NotNull private Map<Integer, String> myHeapIdToNameMap = Collections.singletonMap(DEFAULT_HEAP_ID, DEFAULT_HEAP_NAME);
    private long myStartTime = 0L;
    private long myEndTime = myStartTime + 1;
    private boolean myIsLoadSuccessful = true;
    private boolean myIsDoneLoading = true;
    private boolean myIsError = false;

    @NotNull
    public Builder setCaptureName(@NotNull String captureName) {
      myCaptureName = captureName;
      return this;
    }

    @NotNull
    public Builder setClassifierAttributes(@NotNull List<ClassifierAttribute> classifierAttributes) {
      myClassifierAttributes = classifierAttributes;
      return this;
    }

    @NotNull
    public Builder setInstanceAttributes(@NotNull List<InstanceAttribute> instanceAttributes) {
      myInstanceAttributes = instanceAttributes;
      return this;
    }

    @NotNull
    public Builder setHeapIdToNameMap(@NotNull Map<Integer, String> heapIdToNameMap) {
      myHeapIdToNameMap = heapIdToNameMap;
      return this;
    }

    @NotNull
    public Builder setStartTime(long startTime) {
      myStartTime = startTime;
      return this;
    }

    @NotNull
    public Builder setEndTime(long endTime) {
      myEndTime = endTime;
      return this;
    }

    @NotNull
    public Builder setLoadSuccessful(boolean loadSuccessful) {
      myIsLoadSuccessful = loadSuccessful;
      return this;
    }

    @NotNull
    public Builder setDoneLoading(boolean doneLoading) {
      myIsDoneLoading = doneLoading;
      return this;
    }

    @NotNull
    public Builder setError(boolean error) {
      myIsError = error;
      return this;
    }

    @NotNull
    public FakeCaptureObject build() {
      return new FakeCaptureObject(myCaptureName, myClassifierAttributes, myInstanceAttributes, myHeapIdToNameMap, myStartTime, myEndTime,
                                   myIsLoadSuccessful, myIsDoneLoading, myIsError);
    }
  }
}
