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
import com.android.tools.profilers.memory.adapters.classifiers.HeapSet;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  private final String myInfoMessage;
  private final boolean myCanSafelyLoad;

  private FakeCaptureObject(@NotNull String captureName,
                            @NotNull List<ClassifierAttribute> classifierAttributes,
                            @NotNull List<InstanceAttribute> instanceAttributes,
                            @NotNull Map<Integer, String> heapIdToName,
                            long startTime,
                            long endTime,
                            boolean isLoadSuccessful,
                            boolean isDoneLoading,
                            boolean isError,
                            String infoMessage,
                            boolean canSafelyLoad) {
    myCaptureName = captureName;
    myClassifierAttributes = classifierAttributes;
    myInstanceAttributes = instanceAttributes;
    myHeapIdToName = heapIdToName;
    myStartTime = startTime;
    myEndTime = endTime;
    myIsLoadSuccessful = isLoadSuccessful;
    myIsDoneLoading = isDoneLoading;
    myIsError = isError;
    myInfoMessage = infoMessage;
    myCanSafelyLoad = canSafelyLoad;
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
  public ClassDb.ClassEntry registerClass(long classId, long superClassId, @NotNull String className) {
    return myClassDb.registerClass(classId, superClassId, className);
  }

  public boolean containsClass(long classId) {
    return myClassDb.getEntry(classId) != null;
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

  @NotNull
  @Override
  public ClassDb getClassDatabase() {
    return myClassDb;
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
  public void unload() { }

  @Nullable
  @Override
  public String getInfoMessage() {
    return myInfoMessage;
  }

  @Override
  public boolean canSafelyLoad() {
    return myCanSafelyLoad;
  }

  public static class Builder {
    public static final int DEFAULT_HEAP_ID = 0;
    public static final String DEFAULT_HEAP_NAME = "default";

    @NotNull private String myCaptureName = "SAMPLE_CAPTURE";
    @NotNull private List<ClassifierAttribute> myClassifierAttributes = Arrays.asList(CaptureObject.ClassifierAttribute.values());
    @NotNull private List<InstanceAttribute> myInstanceAttributes = Arrays.asList(CaptureObject.InstanceAttribute.values());
    @NotNull private Map<Integer, String> myHeapIdToNameMap = Collections.singletonMap(DEFAULT_HEAP_ID, DEFAULT_HEAP_NAME);
    private long myStartTime = 0L;
    private long myEndTime = myStartTime + 1;
    private boolean myIsLoadSuccessful = true;
    private boolean myIsDoneLoading = true;
    private boolean myIsError = false;
    private String myInfoMessage = null;
    private boolean myCanSafelyLoad = true;

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
    public Builder setInfoMessage(String infoMessage) {
      myInfoMessage = infoMessage;
      return this;
    }

    @NotNull
    public Builder setCanSafelyLoad(boolean canSafelyLoad) {
      myCanSafelyLoad = canSafelyLoad;
      return this;
    }

    @NotNull
    public Builder removeClassifierAttribute(ClassifierAttribute attr) {
      myClassifierAttributes = myClassifierAttributes.stream().filter(a -> !a.equals(attr)).collect(Collectors.toList());
      return this;
    }

    @NotNull
    public FakeCaptureObject build() {
      return new FakeCaptureObject(myCaptureName, myClassifierAttributes, myInstanceAttributes, myHeapIdToNameMap, myStartTime, myEndTime,
                                   myIsLoadSuccessful, myIsDoneLoading, myIsError, myInfoMessage, myCanSafelyLoad);
    }
  }
}
