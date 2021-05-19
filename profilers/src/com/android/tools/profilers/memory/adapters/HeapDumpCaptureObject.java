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
import com.android.tools.profilers.IdeProfilerServices;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.memory.MemoryProfiler;
import com.android.tools.profilers.memory.MemoryProfilerStage;
import com.android.tools.profilers.memory.adapters.classifiers.AllHeapSet;
import com.android.tools.profilers.memory.adapters.classifiers.ClassifierSet;
import com.android.tools.profilers.memory.adapters.classifiers.HeapSet;
import com.android.tools.profilers.memory.adapters.instancefilters.ActivityFragmentLeakInstanceFilter;
import com.android.tools.profilers.memory.adapters.instancefilters.CaptureObjectInstanceFilter;
import com.android.tools.profilers.memory.adapters.instancefilters.ProjectClassesInstanceFilter;
import com.android.tools.proguard.ProguardMap;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
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
import java.util.function.Consumer;
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
  private volatile boolean hasLoaded = false;

  private volatile boolean myIsLoadingError = false;

  private boolean myHasNativeAllocations;

  @NotNull private final IdeProfilerServices myIdeProfilerServices;

  private final ActivityFragmentLeakInstanceFilter myActivityFragmentLeakFilter;

  private final Set<CaptureObjectInstanceFilter> mySupportedInstanceFilters;

  private final Set<CaptureObjectInstanceFilter> myCurrentInstanceFilters = new HashSet<>();

  private final ListeningExecutorService myExecutorService =
    MoreExecutors.listeningDecorator(
      Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("memory-heapdump-instancefilters").build()));

  public HeapDumpCaptureObject(@NotNull ProfilerClient client,
                               @NotNull Common.Session session,
                               @NotNull HeapDumpInfo heapDumpInfo,
                               @Nullable ProguardMap proguardMap,
                               @NotNull FeatureTracker featureTracker,
                               @NotNull IdeProfilerServices ideProfilerServices) {
    myClient = client;
    mySession = session;
    myHeapDumpInfo = heapDumpInfo;
    myProguardMap = proguardMap;
    myFeatureTracker = featureTracker;
    myIdeProfilerServices = ideProfilerServices;

    myActivityFragmentLeakFilter = new ActivityFragmentLeakInstanceFilter(myClassDb);
    mySupportedInstanceFilters = ImmutableSet.of(myActivityFragmentLeakFilter,
                                                 new ProjectClassesInstanceFilter(ideProfilerServices));
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
    return hasLoaded ? myHeapSets.values() : Collections.emptyList();
  }

  @Override
  @Nullable
  public HeapSet getHeapSet(int heapId) {
    return myHeapSets.getOrDefault(heapId, null);
  }

  @NotNull
  @Override
  public Stream<InstanceObject> getInstances() {
    return hasLoaded ?
           getHeapSets().stream().map(ClassifierSet::getInstancesStream).flatMap(Function.identity()) :
           Stream.empty();
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

    load(new InMemoryBuffer(response.getContents().asReadOnlyByteBuffer()));
    return true;
  }

  @VisibleForTesting
  public final void load(InMemoryBuffer buffer) {
    NativeRegistryPostProcessor nativeRegistryPostProcessor = new NativeRegistryPostProcessor();
    Snapshot snapshot = Snapshot.createSnapshot(buffer,
                                                myProguardMap != null ? myProguardMap : new ProguardMap(),
                                                Collections.singletonList(nativeRegistryPostProcessor));
    snapshot.computeDominators();
    myHasNativeAllocations = nativeRegistryPostProcessor.getHasNativeAllocations();
    hasLoaded = true;

    InstanceObject javaLangClassObject = snapshot.getHeaps().stream()
      .flatMap(h -> h.getClasses().stream().filter(obj -> JAVA_LANG_CLASS.equals(obj.getClassName())))
      .map(cl -> createClassObjectInstance(null, cl))
      .findAny().orElse(null);

    if (myIdeProfilerServices.getFeatureConfig().isSeparateHeapDumpUiEnabled()) {
      Map<Heap, HeapSet> heapSets = snapshot.getHeaps().stream()
        .collect(Collectors.toMap(Function.identity(),
                                  heap -> new HeapSet(this, heap.getName(), heap.getId())));

      AllHeapSet superHeap = new AllHeapSet(this, heapSets.values().toArray(new HeapSet[0]));
      superHeap.clearClassifierSets(); // forces sub-classifier creation
      myHeapSets.put(superHeap.getId(), superHeap);
      heapSets.forEach((heap, heapSet) -> {
        heap.getClasses().forEach(cl ->
          addInstance(superHeap, cl.getId(), createClassObjectInstance(javaLangClassObject, cl)));

        heap.forEachInstance(instance -> {
          assert !JAVA_LANG_CLASS.equals(instance.getClassObj().getClassName());

          ClassObj classObj = instance.getClassObj();
          ClassDb.ClassEntry classEntry =
            classObj.getSuperClassObj() != null ?
            myClassDb.registerClass(classObj.getId(), classObj.getSuperClassObj().getId(), classObj.getClassName()) :
            myClassDb.registerClass(classObj.getId(), classObj.getClassName());
          addInstance(superHeap, instance.getId(), new HeapDumpInstanceObject(this, instance, classEntry, null));
          return true;
        });

        if (!"default".equals(heap.getName()) || snapshot.getHeaps().size() == 1 || heap.getInstancesCount() > 0) {
          myHeapSets.put(heap.getId(), heapSet);
        }
      });
    } else {
      snapshot.getHeaps().forEach(heap -> {
        HeapSet heapSet = new HeapSet(this, heap.getName(), heap.getId());

        heap.getClasses().forEach(cl ->
          addInstance(heapSet, cl.getId(), createClassObjectInstance(javaLangClassObject, cl)));

        heap.forEachInstance(instance -> {
          assert !JAVA_LANG_CLASS.equals(instance.getClassObj().getClassName());

          ClassObj classObj = instance.getClassObj();
          ClassDb.ClassEntry classEntry =
            classObj.getSuperClassObj() != null ?
            myClassDb.registerClass(classObj.getId(), classObj.getSuperClassObj().getId(), classObj.getClassName()) :
            myClassDb.registerClass(classObj.getId(), classObj.getClassName());
          addInstance(heapSet, instance.getId(), new HeapDumpInstanceObject(this, instance, classEntry, null));
          return true;
        });

        if (!"default".equals(heap.getName()) || snapshot.getHeaps().size() == 1 || heap.getInstancesCount() > 0) {
          myHeapSets.put(heap.getId(), heapSet);
        }
      });
    }
  }

  private void addInstance(HeapSet heapSet, long id, InstanceObject instObj) {
    assert !myInstanceIndex.containsKey(id);
    myInstanceIndex.put(id, instObj);
    heapSet.addDeltaInstanceObject(instObj);
  }

  @Override
  public boolean isDoneLoading() {
    return hasLoaded || myIsLoadingError;
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
    return hasLoaded ? myInstanceIndex.get(instance.getId()) : null;
  }

  @NotNull
  InstanceObject createClassObjectInstance(@Nullable InstanceObject javaLangClass, @NotNull ClassObj classObj) {
    String className = javaLangClass == null ? JAVA_LANG_CLASS : classObj.getClassName();
    ClassDb.ClassEntry classEntry = classObj.getSuperClassObj() != null ?
                                    myClassDb.registerClass(classObj.getId(), classObj.getSuperClassObj().getId(), className) :
                                    myClassDb.registerClass(classObj.getId(), className);
    // Handle java.lang.Class which is a special case. All its instances are other classes, so wee need to create an InstanceObject for it
    // first for all classes to reference.
    return new HeapDumpInstanceObject(this,
                                      classObj,
                                      javaLangClass == null ? classEntry : javaLangClass.getClassEntry(),
                                      ValueObject.ValueType.CLASS);
  }

  @Override
  public ActivityFragmentLeakInstanceFilter getActivityFragmentLeakFilter() {
    return myActivityFragmentLeakFilter;
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
  public ListenableFuture<Void> addInstanceFilter(@NotNull CaptureObjectInstanceFilter filterToAdd,
                                                  @NotNull Executor analyzeJoiner) {
    assert mySupportedInstanceFilters.contains(filterToAdd);

    myCurrentInstanceFilters.add(filterToAdd);
    return myExecutorService.submit(() -> {
      // Run the analyzers on the currently existing InstanceObjects in the HeapSets.
      Set<InstanceObject> currentInstances =
        myHeapSets.values().stream().flatMap(HeapSet::getInstancesStream).collect(Collectors.toSet());
      return refreshInstances(filterToAdd.filter(currentInstances), analyzeJoiner);
    });
  }

  @Override
  public ListenableFuture<Void> removeInstanceFilter(@NotNull CaptureObjectInstanceFilter filterToRemove,
                                                     @NotNull Executor analyzeJoiner) {
    // Filter is not set in the first place so nothing needs to be done.
    if (!myCurrentInstanceFilters.contains(filterToRemove)) {
      return CaptureObjectUtils.makeEmptyTask();
    }

    myCurrentInstanceFilters.remove(filterToRemove);
    return myExecutorService.submit(() -> {
      // Run the remaining analyzers on the full instance set, since we don't know that the instances that have been removed from the
      // HeapSets using the filter that we are removing.
      Set<InstanceObject> matchedInstances = getAllInstances();
      for (CaptureObjectInstanceFilter filter : myCurrentInstanceFilters) {
        matchedInstances = filter.filter(matchedInstances);
      }

      return refreshInstances(matchedInstances, analyzeJoiner);
    });
  }

  @Override
  public ListenableFuture<Void> setSingleFilter(@NotNull CaptureObjectInstanceFilter filter,
                                                @NotNull Executor analyzeJoiner) {
    myCurrentInstanceFilters.clear();
    myCurrentInstanceFilters.add(filter);
    return myExecutorService.submit(() -> refreshInstances(filter.filter(getAllInstances()), analyzeJoiner));
  }

  @Override
  public ListenableFuture<Void> removeAllFilters(@NotNull Executor analyzeJoiner) {
    myCurrentInstanceFilters.clear();
    return myExecutorService.submit(() -> refreshInstances(getAllInstances(), analyzeJoiner));
  }

  private Set<InstanceObject> getAllInstances() {
    Set<InstanceObject> allInstances = new HashSet<>(myInstanceIndex.size());
    myInstanceIndex.forEachValue(allInstances::add);
    return allInstances;
  }

  private Void refreshInstances(@NotNull Set<InstanceObject> instances,
                                @NotNull Executor executor) {
    executor.execute(() -> {
      myHeapSets.values().forEach(HeapSet::clearClassifierSets);
      Consumer<InstanceObject> onInst = myHeapSets.values().stream().filter(heap -> heap instanceof AllHeapSet)
        .map(h -> (Consumer<InstanceObject>)h::addDeltaInstanceObject).findAny()
        .orElse((InstanceObject inst) -> myHeapSets.get(inst.getHeapId()).addDeltaInstanceObject(inst));
      instances.forEach(onInst);
    });
    return null;
  }

  @Override
  public boolean canSafelyLoad() {
    Transport.BytesResponse response = myClient.getTransportClient().getBytes(Transport.BytesRequest.newBuilder()
                                                                                .setStreamId(mySession.getStreamId())
                                                                                .setId(Long.toString(myHeapDumpInfo.getStartTime()))
                                                                                .build());
    return MemoryProfilerStage.canSafelyLoadHprof(response.getSerializedSize());
  }
}