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

import static com.android.tools.profilers.memory.adapters.CaptureObject.ClassifierAttribute.ALLOCATIONS;
import static com.android.tools.profilers.memory.adapters.CaptureObject.ClassifierAttribute.LABEL;
import static com.android.tools.profilers.memory.adapters.CaptureObject.ClassifierAttribute.NATIVE_SIZE;
import static com.android.tools.profilers.memory.adapters.CaptureObject.ClassifierAttribute.RETAINED_SIZE;
import static com.android.tools.profilers.memory.adapters.CaptureObject.ClassifierAttribute.SHALLOW_SIZE;
import static com.android.tools.profilers.memory.adapters.ClassDb.JAVA_LANG_CLASS;

import com.android.tools.adtui.model.Range;
import com.android.tools.idea.protobuf.ByteString;
import com.android.tools.perflib.heap.ClassObj;
import com.android.tools.perflib.heap.Heap;
import com.android.tools.perflib.heap.Instance;
import com.android.tools.perflib.heap.Snapshot;
import com.android.tools.perflib.heap.ext.NativeRegistryPostProcessor;
import com.android.tools.perflib.heap.io.InMemoryBuffer;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Memory.HeapDumpInfo;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.memory.MemoryProfiler;
import com.android.tools.profilers.memory.MemoryProfilerAspect;
import com.android.tools.profilers.memory.MemoryProfilerStage;
import com.android.tools.profilers.memory.adapters.instancefilters.ActivityFragmentLeakInstanceFilter;
import com.android.tools.profilers.memory.adapters.instancefilters.CaptureObjectInstanceFilter;
import com.android.tools.profilers.memory.adapters.instancefilters.ProjectClassesInstanceFilter;
import com.android.tools.proguard.ProguardMap;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import gnu.trove.TLongObjectHashMap;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class HeapDumpCaptureObject implements CaptureObject {

  @NotNull
  private final ProfilerClient myClient;

  @NotNull
  private final Common.Session mySession;

  @NotNull
  private final FeatureTracker myFeatureTracker;

  @NotNull
  private final Map<Integer, HeapSet> myHeapSets = new HashMap<>();

  @NotNull
  private final TLongObjectHashMap<InstanceObject> myInstanceIndex = new TLongObjectHashMap<>();

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

  @NotNull
  private final MemoryProfilerStage myStage;

  private final Set<CaptureObjectInstanceFilter> mySupportedInstanceFilters;

  private final Set<CaptureObjectInstanceFilter> myCurrentInstanceFilters = new HashSet<>();

  private final ExecutorService myExecutorService =
    Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("memory-heapdump-instancefilters").build());

  public HeapDumpCaptureObject(@NotNull ProfilerClient client,
                               @NotNull Common.Session session,
                               @NotNull HeapDumpInfo heapDumpInfo,
                               @Nullable ProguardMap proguardMap,
                               @NotNull FeatureTracker featureTracker,
                               @NotNull MemoryProfilerStage stage) {
    myClient = client;
    mySession = session;
    myHeapDumpInfo = heapDumpInfo;
    myProguardMap = proguardMap;
    myFeatureTracker = featureTracker;
    myStage = stage;

    mySupportedInstanceFilters = ImmutableSet.of(new ActivityFragmentLeakInstanceFilter(),
                                                 new ProjectClassesInstanceFilter(myStage.getStudioProfilers().getIdeServices()));
  }

  @NotNull
  @Override
  public String getName() {
    return "Heap Dump";
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
  public void saveToFile(@NotNull OutputStream outputStream) {
    MemoryProfiler.saveHeapDumpToFile(myClient, mySession, myHeapDumpInfo, outputStream, myFeatureTracker);
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
  public ClassDb getClassDatabase() {
    return myClassDb;
  }

  @Override
  public boolean load(@Nullable Range queryRange, @Nullable Executor queryJoiner) {
    Transport.BytesResponse response = myClient.getTransportClient().getBytes(Transport.BytesRequest.newBuilder()
                                                                                .setStreamId(mySession.getStreamId())
                                                                                .setId(Long.toString(myHeapDumpInfo.getStartTime()))
                                                                                .build());

    if (response.getContents() == ByteString.EMPTY) {
      myIsLoadingError = true;
      return false;
    }

    InMemoryBuffer buffer = new InMemoryBuffer(response.getContents().asReadOnlyByteBuffer());
    Snapshot snapshot;
    NativeRegistryPostProcessor nativeRegistryPostProcessor = new NativeRegistryPostProcessor();
    if (myProguardMap != null) {
      snapshot = Snapshot.createSnapshot(buffer, myProguardMap, Collections.singletonList(nativeRegistryPostProcessor));
    }
    else {
      snapshot = Snapshot.createSnapshot(buffer, new ProguardMap(), Collections.singletonList(nativeRegistryPostProcessor));
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
          heap.getClasses().stream().filter(classObj -> JAVA_LANG_CLASS.equals(classObj.getClassName())).findFirst().orElse(null);
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
        assert !myInstanceIndex.containsKey(classObj.getId());
        myInstanceIndex.put(classObj.getId(), classObject);
        heapSet.addDeltaInstanceObject(classObject);
      });
    }

    for (Heap heap : snapshot.getHeaps()) {
      HeapSet heapSet = heapSets.get(heap);
      heap.forEachInstance(instance -> {
        assert !JAVA_LANG_CLASS.equals(instance.getClassObj().getClassName());

        ClassObj classObj = instance.getClassObj();
        ClassDb.ClassEntry classEntry =
          classObj.getSuperClassObj() != null ?
          myClassDb.registerClass(classObj.getId(), classObj.getSuperClassObj().getId(), classObj.getClassName()) :
          myClassDb.registerClass(classObj.getId(), classObj.getClassName());
        InstanceObject instanceObject = new HeapDumpInstanceObject(this, instance, classEntry, null);
        assert !myInstanceIndex.containsKey(instance.getId());
        myInstanceIndex.put(instance.getId(), instanceObject);
        heapSet.addDeltaInstanceObject(instanceObject);
        return true;
      });
    }
    heapSets.forEach((key, value) -> {
      if ("default".equals(key.getName())) {
        if (heapSets.size() == 1 || key.getInstancesCount() > 0) {
          myHeapSets.put(key.getId(), value);
        }
      }
      else {
        myHeapSets.put(key.getId(), value);
      }
    });

    myStage.refreshSelectedHeap();

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
    myExecutorService.shutdownNow();
  }

  @NotNull
  @Override
  public List<ClassifierAttribute> getClassifierAttributes() {
    return myHasNativeAllocations ? Arrays.asList(LABEL, ALLOCATIONS, NATIVE_SIZE, SHALLOW_SIZE, RETAINED_SIZE)
                                  : Arrays.asList(LABEL, ALLOCATIONS, SHALLOW_SIZE, RETAINED_SIZE);
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

    return myInstanceIndex.get(instance.getId());
  }

  @NotNull
  InstanceObject createClassObjectInstance(@Nullable InstanceObject javaLangClass, @NotNull ClassObj classObj) {
    String className = javaLangClass == null ? JAVA_LANG_CLASS : classObj.getClassName();
    ClassDb.ClassEntry classEntry = classObj.getSuperClassObj() != null ?
                                    myClassDb.registerClass(classObj.getId(), classObj.getSuperClassObj().getId(), className) :
                                    myClassDb.registerClass(classObj.getId(), className);
    InstanceObject classObject;
    if (javaLangClass == null) {
      // Handle java.lang.Class which is a special case. All its instances are other classes, so wee need to create an InstanceObject for it
      // first for all classes to reference.
      classObject = new HeapDumpInstanceObject(this, classObj, classEntry, ValueObject.ValueType.CLASS);
    }
    else {
      classObject = new HeapDumpInstanceObject(this, classObj, javaLangClass.getClassEntry(), ValueObject.ValueType.CLASS);
    }
    return classObject;
  }

  @NotNull
  @Override
  public Set<CaptureObjectInstanceFilter> getSupportedInstanceFilters() {
    return mySupportedInstanceFilters;
  }

  @NotNull
  @Override
  public Set<CaptureObjectInstanceFilter> getSelectedInstanceFilters() {
    return myCurrentInstanceFilters;
  }

  @VisibleForTesting
  ExecutorService getInstanceFilterExecutor() {
    return myExecutorService;
  }

  @Override
  public void addInstanceFilter(@NotNull CaptureObjectInstanceFilter filterToAdd, @NotNull Executor analyzeJoiner) {
    assert mySupportedInstanceFilters.contains(filterToAdd);

    myCurrentInstanceFilters.add(filterToAdd);
    myStage.getAspect().changed(MemoryProfilerAspect.CURRENT_HEAP_UPDATING);
    myExecutorService.submit(() -> {
      // Run the analyzers on the currently existing InstanceObjects in the HeapSets.
      Set<InstanceObject> currentMatchedInstances = new HashSet<>();
      for (HeapSet heap : myHeapSets.values()) {
        currentMatchedInstances.addAll(heap.getInstancesStream().collect(Collectors.toSet()));
      }

      Set<InstanceObject> matchedInstancesFinal = filterToAdd.filter(currentMatchedInstances, myClassDb);
      analyzeJoiner.execute(() -> {
        for (HeapSet heap : myHeapSets.values()) {
          heap.clearClassifierSets();
        }

        matchedInstancesFinal.forEach(instance -> myHeapSets.get(instance.getHeapId()).addDeltaInstanceObject(instance));
        myStage.getAspect().changed(MemoryProfilerAspect.CURRENT_HEAP_UPDATED);
        myStage.refreshSelectedHeap();
      });
    });
  }

  @Override
  public void removeInstanceFilter(@NotNull CaptureObjectInstanceFilter filterToRemove, @NotNull Executor analyzeJoiner) {
    // Filter is not set in the first place so othing needs to be done.
    if (!myCurrentInstanceFilters.contains(filterToRemove)) {
      return;
    }

    myCurrentInstanceFilters.remove(filterToRemove);
    myStage.getAspect().changed(MemoryProfilerAspect.CURRENT_HEAP_UPDATING);
    myExecutorService.submit(() -> {
      // Run the remaining analyzers on the full instance set, since we don't know the the instances that have been removed from the
      // HeapSets using the filter that we are removing.
      Set<InstanceObject> allInstances = new HashSet<>(myInstanceIndex.size());
      myInstanceIndex.forEachValue(instance -> allInstances.add(instance));
      Set<InstanceObject> matchedInstances = allInstances;
      for (CaptureObjectInstanceFilter filter : myCurrentInstanceFilters) {
        matchedInstances = filter.filter(matchedInstances, myClassDb);
      }

      Set<InstanceObject> matchedInstancesFinal = matchedInstances;
      analyzeJoiner.execute(() -> {
        for (HeapSet heap : myHeapSets.values()) {
          heap.clearClassifierSets();
        }

        matchedInstancesFinal.forEach(instance -> myHeapSets.get(instance.getHeapId()).addDeltaInstanceObject(instance));
        myStage.getAspect().changed(MemoryProfilerAspect.CURRENT_HEAP_UPDATED);
        myStage.refreshSelectedHeap();
      });
    });
  }
}
