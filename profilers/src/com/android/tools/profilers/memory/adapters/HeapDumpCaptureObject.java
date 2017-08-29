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
import com.android.tools.adtui.model.formatter.TimeAxisFormatter;
import com.android.tools.perflib.heap.ClassObj;
import com.android.tools.perflib.heap.Heap;
import com.android.tools.perflib.heap.Instance;
import com.android.tools.perflib.heap.Snapshot;
import com.android.tools.perflib.heap.ext.NativeRegistryPostProcessor;
import com.android.tools.perflib.heap.io.InMemoryBuffer;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.MemoryProfiler.DumpDataRequest;
import com.android.tools.profiler.proto.MemoryProfiler.DumpDataResponse;
import com.android.tools.profiler.proto.MemoryProfiler.HeapDumpInfo;
import com.android.tools.profiler.proto.MemoryServiceGrpc.MemoryServiceBlockingStub;
import com.android.tools.profilers.RelativeTimeConverter;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.proguard.ProguardMap;
import com.google.common.annotations.VisibleForTesting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.android.tools.profilers.memory.adapters.CaptureObject.ClassifierAttribute.*;

public class HeapDumpCaptureObject implements CaptureObject {

  @NotNull
  private final MemoryServiceBlockingStub myClient;

  private final int myProcessId;

  @Nullable
  private final Common.Session mySession;

  @NotNull
  private final String myLabel;

  @NotNull
  private final FeatureTracker myFeatureTracker;

  @NotNull
  private final Map<Integer, HeapSet> myHeapSets = new HashMap<>();

  @NotNull
  private final Map<ClassObj, InstanceObject> myClassObjectIndex = new HashMap<>();

  @NotNull
  private final Map<Instance, InstanceObject> myInstanceIndex = new HashMap<>();

  @NotNull
  private final ClassDb myClassDb = new ClassDb();

  @NotNull
  private final HeapDumpInfo myHeapDumpInfo;

  @Nullable
  private final ProguardMap myProguardMap;

  @Nullable
  private volatile Snapshot mySnapshot;

  private volatile boolean myIsLoadingError = false;

  private boolean myHasNativeAllocations;

  public HeapDumpCaptureObject(@NotNull MemoryServiceBlockingStub client,
                               @Nullable Common.Session session,
                               int appId,
                               @NotNull HeapDumpInfo heapDumpInfo,
                               @Nullable ProguardMap proguardMap,
                               @NotNull RelativeTimeConverter converter,
                               @NotNull FeatureTracker featureTracker) {
    myClient = client;
    myProcessId = appId;
    mySession = session;
    myHeapDumpInfo = heapDumpInfo;
    myProguardMap = proguardMap;
    myLabel =
      "Heap Dump @ " +
      TimeAxisFormatter.DEFAULT
        .getFixedPointFormattedString(TimeUnit.MILLISECONDS.toMicros(1),
                                      TimeUnit.NANOSECONDS.toMicros(converter.convertToRelativeTime(myHeapDumpInfo.getStartTime())));
    myFeatureTracker = featureTracker;
  }

  @NotNull
  @Override
  public String getName() {
    return myLabel;
  }

  @Override
  public boolean isExportable() {
    return true;
  }

  @Nullable
  @Override
  public String getExportableExtension() {
    return "hprof";
  }

  @Override
  public void saveToFile(@NotNull OutputStream outputStream) throws IOException {
    DumpDataResponse response = myClient.getHeapDump(
      DumpDataRequest.newBuilder().setProcessId(myProcessId).setSession(mySession).setDumpTime(myHeapDumpInfo.getStartTime()).build());
    if (response.getStatus() == DumpDataResponse.Status.SUCCESS) {
      response.getData().writeTo(outputStream);
      myFeatureTracker.trackExportHeap();
    }
    else {
      throw new IOException("Could not retrieve hprof dump.");
    }
  }

  @VisibleForTesting
  @NotNull
  ClassDb getClassDb() {
    return myClassDb;
  }

  @NotNull
  @Override
  public Collection<HeapSet> getHeapSets() {
    Snapshot snapshot = mySnapshot;
    if (snapshot == null) {
      return Collections.emptyList();
    }
    return myHeapSets.values();
  }

  @Override
  @Nullable
  public HeapSet getHeapSet(int heapId) {
    return myHeapSets.getOrDefault(heapId, null);
  }

  @NotNull
  @Override
  public Stream<InstanceObject> getInstances() {
    Snapshot snapshot = mySnapshot;
    if (snapshot == null) {
      return Stream.empty();
    }
    return getHeapSets().stream().map(ClassifierSet::getInstancesStream).flatMap(Function.identity());
  }

  @Override
  public long getStartTimeNs() {
    return myHeapDumpInfo.getStartTime();
  }

  @Override
  public long getEndTimeNs() {
    return myHeapDumpInfo.getEndTime();
  }

  public boolean getHasNativeAllocations() {
    return myHasNativeAllocations;
  }

  @Override
  public boolean load(@Nullable Range queryRange, @Nullable Executor queryJoiner) {
    DumpDataResponse response;
    while (true) {
      // TODO move this to another thread and complete before we notify
      response = myClient.getHeapDump(DumpDataRequest.newBuilder()
                                        .setProcessId(myProcessId)
                                        .setSession(mySession)
                                        .setDumpTime(myHeapDumpInfo.getStartTime()).build());
      if (response.getStatus() == DumpDataResponse.Status.SUCCESS) {
        break;
      }
      else if (response.getStatus() == DumpDataResponse.Status.NOT_READY) {
        try {
          Thread.sleep(50L);
        }
        catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          myIsLoadingError = true;
          return false;
        }
        continue;
      }
      myIsLoadingError = true;
      return false;
    }

    InMemoryBuffer buffer = new InMemoryBuffer(response.getData().asReadOnlyByteBuffer());
    Snapshot snapshot;
    NativeRegistryPostProcessor nativeRegistryPostProcessor = new NativeRegistryPostProcessor();
    if (myProguardMap != null) {
      snapshot = Snapshot.createSnapshot(buffer, myProguardMap, Arrays.asList(nativeRegistryPostProcessor));
    }
    else {
      snapshot = Snapshot.createSnapshot(buffer, new ProguardMap(), Arrays.asList(nativeRegistryPostProcessor));
    }
    snapshot.computeDominators();
    myHasNativeAllocations = nativeRegistryPostProcessor.getHasNativeAllocations();
    mySnapshot = snapshot;

    Map<Heap, HeapSet> heapSets = new HashMap<>(snapshot.getHeaps().size());
    InstanceObject javaLangClassObject = null;
    for (Heap heap : snapshot.getHeaps()) {
      HeapSet heapSet = new HeapSet(this, heap.getName(), heap.getId());
      heapSets.put(heap, heapSet);
      if (javaLangClassObject == null) {
        ClassObj javaLangClass =
          heap.getClasses().stream().filter(classObj -> ClassDb.JAVA_LANG_CLASS.equals(classObj.getClassName())).findFirst().orElse(null);
        if (javaLangClass != null) {
          javaLangClassObject = createClassObjectInstance(null, javaLangClass);
        }
      }
    }

    InstanceObject finalJavaLangClassObject = javaLangClassObject;
    for (Heap heap : snapshot.getHeaps()) {
      HeapSet heapSet = heapSets.get(heap);
      heap.getClasses().forEach(classObj -> {
        InstanceObject classObject = createClassObjectInstance(finalJavaLangClassObject, classObj);
        myInstanceIndex.put(classObj, classObject);
        heapSet.addInstanceObject(classObject);
      });
    }

    for (Heap heap : snapshot.getHeaps()) {
      HeapSet heapSet = heapSets.get(heap);
      heap.forEachInstance(instance -> {
        assert !ClassDb.JAVA_LANG_CLASS.equals(getName());
        ClassObj classObj = instance.getClassObj();
        InstanceObject instanceObject =
          new HeapDumpInstanceObject(this, getClassObjectInstance(instance), instance,
                                     myClassDb.registerClass(classObj.getClassLoaderId(), classObj.getClassName()), null);
        myInstanceIndex.put(instance, instanceObject);
        heapSet.addInstanceObject(instanceObject);
        return true;
      });
    }
    heapSets.entrySet().forEach(entry -> myHeapSets.put(entry.getKey().getId(), entry.getValue()));

    return true;
  }

  @Override
  public boolean isDoneLoading() {
    return mySnapshot != null || myIsLoadingError;
  }

  @Override
  public boolean isError() {
    return myIsLoadingError;
  }

  @Override
  public void unload() {

  }

  @NotNull
  @Override
  public List<ClassifierAttribute> getClassifierAttributes() {
    return myHasNativeAllocations ? Arrays.asList(LABEL, ALLOC_COUNT, NATIVE_SIZE, SHALLOW_SIZE, RETAINED_SIZE)
                                  : Arrays.asList(LABEL, ALLOC_COUNT, SHALLOW_SIZE, RETAINED_SIZE);
  }

  @Override
  @NotNull
  public List<InstanceAttribute> getInstanceAttributes() {
    return myHasNativeAllocations ?
           Arrays
             .asList(InstanceAttribute.LABEL, InstanceAttribute.DEPTH, InstanceAttribute.NATIVE_SIZE, InstanceAttribute.SHALLOW_SIZE,
                     InstanceAttribute.RETAINED_SIZE) :
           Arrays
             .asList(InstanceAttribute.LABEL, InstanceAttribute.DEPTH, InstanceAttribute.SHALLOW_SIZE, InstanceAttribute.RETAINED_SIZE);
  }

  @Nullable
  public InstanceObject findInstanceObject(@NotNull Instance instance) {
    if (mySnapshot == null) {
      return null;
    }

    return myInstanceIndex.get(instance);
  }

  @NotNull
  InstanceObject createClassObjectInstance(@Nullable InstanceObject javaLangClass, @NotNull ClassObj classObj) {
    if (javaLangClass == null) {
      // Deal with the root java.lang.Class object.
      assert !myClassObjectIndex.containsKey(classObj);
      InstanceObject rootInstanceObject =
        new HeapDumpInstanceObject(this, null, classObj,
                                   myClassDb.registerClass(classObj.getClassLoaderId(), "java.lang.Class"),
                                   ValueObject.ValueType.CLASS);
      myClassObjectIndex.put(classObj, rootInstanceObject);
      return rootInstanceObject;
    }
    else {
      HeapDumpInstanceObject classObject = new HeapDumpInstanceObject(this, javaLangClass, classObj, myClassDb
        .registerClass(classObj.getClassLoaderId(), javaLangClass.getClassEntry().getClassName()), ValueObject.ValueType.CLASS);
      myClassObjectIndex.put(classObj, classObject);
      return classObject;
    }
  }

  @Nullable
  InstanceObject getClassObjectInstance(@NotNull Instance instance) {
    ClassObj classObj = instance.getClassObj();
    return myClassObjectIndex.get(classObj);
  }
}
