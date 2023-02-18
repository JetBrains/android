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
package com.android.tools.profilers.memory.adapters

import com.android.tools.adtui.model.Range
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.perflib.heap.ClassObj
import com.android.tools.perflib.heap.Instance
import com.android.tools.perflib.heap.Snapshot
import com.android.tools.perflib.heap.ext.NativeRegistryPostProcessor
import com.android.tools.perflib.heap.io.InMemoryBuffer
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Memory.HeapDumpInfo
import com.android.tools.profiler.proto.Transport
import com.android.tools.profilers.IdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.analytics.FeatureTracker
import com.android.tools.profilers.analytics.trackLoading
import com.android.tools.profilers.memory.MainMemoryProfilerStage
import com.android.tools.profilers.memory.MemoryProfiler.Companion.saveHeapDumpToFile
import com.android.tools.profilers.memory.adapters.CaptureObject.ClassifierAttribute.*
import com.android.tools.profilers.memory.adapters.CaptureObject.InstanceAttribute
import com.android.tools.profilers.memory.adapters.classifiers.AllHeapSet
import com.android.tools.profilers.memory.adapters.classifiers.HeapSet
import com.android.tools.profilers.memory.adapters.instancefilters.ActivityFragmentLeakInstanceFilter
import com.android.tools.profilers.memory.adapters.instancefilters.CaptureObjectInstanceFilter
import com.android.tools.profilers.memory.adapters.instancefilters.ProjectClassesInstanceFilter
import com.android.tools.proguard.ProguardMap
import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.google.wireless.android.sdk.stats.AndroidProfilerEvent.Loading
import gnu.trove.TObjectProcedure
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import java.io.OutputStream
import java.util.HashMap
import java.util.HashSet
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.stream.Collectors
import java.util.stream.Stream

open class HeapDumpCaptureObject(private val client: ProfilerClient,
                                 private val _session: Common.Session,
                                 private val heapDumpInfo: HeapDumpInfo,
                                 private val proguardMap: ProguardMap?,
                                 private val featureTracker: FeatureTracker,
                                 private val ideProfilerServices: IdeProfilerServices) : CaptureObject {
  private val _heapSets: MutableMap<Int, HeapSet> = HashMap()
  private val instanceIndex = Long2ObjectOpenHashMap<InstanceObject>()

  @get:VisibleForTesting
  val classDb = ClassDb()

  @Volatile
  private var hasLoaded = false

  @Volatile
  private var isLoadingError = false
  var hasNativeAllocations = false
    private set
  private val activityFragmentLeakFilter = ActivityFragmentLeakInstanceFilter(classDb)
  private val supportedInstanceFilters: Set<CaptureObjectInstanceFilter> = setOf(activityFragmentLeakFilter,
                                                                                 ProjectClassesInstanceFilter(ideProfilerServices))
  private val currentInstanceFilters = mutableSetOf<CaptureObjectInstanceFilter>()
  private val executorService = MoreExecutors.listeningDecorator(
    Executors.newSingleThreadExecutor(ThreadFactoryBuilder().setNameFormat("memory-heapdump-instancefilters").build())
  )

  private val allInstances: Set<InstanceObject>
    get() = HashSet<InstanceObject>(instanceIndex.size).also { instanceIndex.values.forEach(it::add) }

  @VisibleForTesting
  val instanceFilterExecutor get() = executorService

  override fun getName() = "Heap Dump"
  override fun isExportable() = true
  override fun getExportableExtension() = "hprof"
  override fun saveToFile(outputStream: OutputStream) = saveHeapDumpToFile(client, _session, heapDumpInfo, outputStream, featureTracker)
  override fun getHeapSets() = if (hasLoaded) _heapSets.values else emptyList()
  override fun getHeapSet(heapId: Int) = _heapSets.getOrDefault(heapId, null)
  override fun getInstances(): Stream<InstanceObject> =
    if (hasLoaded) heapSets.find { it is AllHeapSet }!!.instancesStream else Stream.empty()
  override fun getStartTimeNs() = heapDumpInfo.startTime
  override fun getEndTimeNs() = heapDumpInfo.endTime
  override fun getClassDatabase() = classDb
  override fun getSession() = _session

  override fun load(queryRange: Range?, queryJoiner: Executor?) = doGetBytesRequest().let { response ->
    if (response.contents === ByteString.EMPTY) false.also { isLoadingError = true }
    else true.also {
      ideProfilerServices.featureTracker.trackLoading(Loading.Type.HPROF,
                                                      sizeKb = countBytes() / 1024,
                                                      measure = { instanceIndex.size.toLong() }) {
        load(InMemoryBuffer(response.contents.asReadOnlyByteBuffer()))
      }
    }
  }

  @VisibleForTesting
  fun load(buffer: InMemoryBuffer) {
    val nativeRegistryPostProcessor = NativeRegistryPostProcessor()
    val snapshot = Snapshot.createSnapshot(buffer!!, proguardMap ?: ProguardMap(), listOf(nativeRegistryPostProcessor))
    snapshot.computeRetainedSizes()
    hasNativeAllocations = nativeRegistryPostProcessor.hasNativeAllocations
    hasLoaded = true
    val javaLangClassObject = snapshot.heaps.stream()
      .flatMap { h -> h.classes.stream().filter { ClassDb.JAVA_LANG_CLASS == it.className } }
      .map { createClassObjectInstance(null, it) }
      .findAny().orElse(null)
    val heapSetMappings = snapshot.heaps.associateWith { HeapSet(this, it.name, it.id) }
    val addInstanceToRightHeap: (HeapSet, Long, InstanceObject) -> Unit =
      AllHeapSet(this, heapSetMappings.values.toTypedArray()).let { superHeap ->
        superHeap.clearClassifierSets() // forces sub-classifier creation
        _heapSets[superHeap.id] = superHeap
        { _, id, classInst -> addInstance(superHeap, id, classInst) }
      }
    heapSetMappings.forEach { (heap, heapSet) ->
      heap.classes.forEach { addInstanceToRightHeap(heapSet, it.id, createClassObjectInstance(javaLangClassObject, it)) }
      heap.forEachInstance(object : TObjectProcedure<Instance> {
        override fun execute(instance: Instance): Boolean {
          assert(ClassDb.JAVA_LANG_CLASS != instance.classObj!!.className)
          val classEntry = instance.classObj!!.makeEntry()
          addInstanceToRightHeap(heapSet, instance.id, HeapDumpInstanceObject(this@HeapDumpCaptureObject, instance, classEntry, null))
          return true
        }
      })
      if ("default" != heap.name || snapshot.heaps.size == 1 || heap.instancesCount > 0) {
        _heapSets.put(heap.id, heapSet)
      }
    }
  }

  private fun addInstance(heapSet: HeapSet, id: Long, instObj: InstanceObject) {
    assert(!instanceIndex.containsKey(id))
    instanceIndex.put(id, instObj)
    heapSet.addDeltaInstanceObject(instObj)
  }

  override fun isDoneLoading() = hasLoaded || isLoadingError
  override fun isError() = isLoadingError
  override fun unload() {
    executorService.shutdownNow()
  }

  override fun getClassifierAttributes() =
    if (hasNativeAllocations) listOf(LABEL, ALLOCATIONS, NATIVE_SIZE, SHALLOW_SIZE, RETAINED_SIZE)
    else listOf(LABEL, ALLOCATIONS, SHALLOW_SIZE, RETAINED_SIZE)

  override fun getInstanceAttributes() =
    if (hasNativeAllocations) listOf(
        InstanceAttribute.LABEL, InstanceAttribute.DEPTH, InstanceAttribute.NATIVE_SIZE, InstanceAttribute.SHALLOW_SIZE,
        InstanceAttribute.RETAINED_SIZE)
    else listOf(InstanceAttribute.LABEL, InstanceAttribute.DEPTH, InstanceAttribute.SHALLOW_SIZE, InstanceAttribute.RETAINED_SIZE)
  open fun findInstanceObject(instance: Instance) = if (hasLoaded) instanceIndex.get(instance.id) else null

  fun createClassObjectInstance(javaLangClass: InstanceObject?, classObj: ClassObj): InstanceObject {
    val classEntry = classObj.makeEntry(if (javaLangClass == null) ClassDb.JAVA_LANG_CLASS else classObj.className)
    // Handle java.lang.Class which is a special case. All its instances are other classes, so wee need to create an InstanceObject for it
    // first for all classes to reference.
    return HeapDumpInstanceObject(this, classObj, javaLangClass?.classEntry ?: classEntry, ValueObject.ValueType.CLASS)
  }

  override fun getActivityFragmentLeakFilter() = activityFragmentLeakFilter
  override fun getSupportedInstanceFilters() = supportedInstanceFilters
  override fun getSelectedInstanceFilters() = currentInstanceFilters

  override fun addInstanceFilter(filterToAdd: CaptureObjectInstanceFilter, analyzeJoiner: Executor): ListenableFuture<Void> {
    assert(supportedInstanceFilters.contains(filterToAdd))
    currentInstanceFilters.add(filterToAdd)
    return executorService.submit<Void> {
      // Run the analyzers on the currently existing InstanceObjects in the HeapSets.
      val currentInstances = _heapSets.values.stream().flatMap { it.instancesStream }.collect(Collectors.toSet())
      refreshInstances(filterToAdd.filter(currentInstances), analyzeJoiner)
    }
  }

  override fun removeInstanceFilter(filterToRemove: CaptureObjectInstanceFilter, analyzeJoiner: Executor): ListenableFuture<Void> = when {
    // Filter is not set in the first place so nothing needs to be done.
    !currentInstanceFilters.contains(filterToRemove) -> CaptureObject.Utils.makeEmptyTask()
    else -> {
      currentInstanceFilters.remove(filterToRemove)
      executorService.submit<Void> {
        // Run the remaining analyzers on the full instance set, since we don't know that the instances that have been removed from the
        // HeapSets using the filter that we are removing.
        refreshInstances(currentInstanceFilters.fold(allInstances) { instances, filter -> filter.filter(instances) }, analyzeJoiner)
      }
    }
  }

  override fun setSingleFilter(filter: CaptureObjectInstanceFilter, analyzeJoiner: Executor): ListenableFuture<Void> {
    currentInstanceFilters.clear()
    currentInstanceFilters.add(filter)
    return executorService.submit<Void> { refreshInstances(filter.filter(allInstances), analyzeJoiner) }
  }

  override fun removeAllFilters(analyzeJoiner: Executor): ListenableFuture<Void> {
    currentInstanceFilters.clear()
    return executorService.submit<Void> { refreshInstances(allInstances, analyzeJoiner) }
  }

  private fun refreshInstances(instances: Set<InstanceObject>, executor: Executor): Void? {
    executor.execute {
      _heapSets.values.forEach { it.clearClassifierSets() }
      when (val h = _heapSets.values.find {it is AllHeapSet}) {
        null -> instances.forEach { _heapSets[it.heapId]!!.addDeltaInstanceObject(it) }
        else -> instances.forEach { h.addDeltaInstanceObject(it) }
      }
    }
    return null
  }

  override fun canSafelyLoad() = MainMemoryProfilerStage.canSafelyLoadHprof(countBytes().toLong())

  private fun doGetBytesRequest() = client.transportClient.getBytes(Transport.BytesRequest.newBuilder()
                                                                      .setStreamId(_session.streamId)
                                                                      .setId(heapDumpInfo.startTime.toString())
                                                                      .build())

  private fun countBytes() = doGetBytesRequest().serializedSize

  private fun ClassObj.makeEntry(name: String = this.className) =
    if (superClassObj != null) classDb.registerClass(id, superClassObj!!.id, name, totalRetainedSize)
    else classDb.registerClass(id, name, totalRetainedSize)
}