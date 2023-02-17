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
package com.android.tools.profilers.memory.adapters

import com.android.tools.adtui.model.AspectObserver
import com.android.tools.adtui.model.Range
import com.android.tools.inspectors.common.api.stacktrace.ThreadId
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Memory
import com.android.tools.profiler.proto.Memory.AllocationEvent
import com.android.tools.profiler.proto.Memory.AllocationStack
import com.android.tools.profiler.proto.Memory.JNIGlobalReferenceEvent
import com.android.tools.profiler.proto.Memory.NativeBacktrace
import com.android.tools.profiler.proto.Memory.NativeCallStack
import com.android.tools.profiler.proto.Memory.NativeCallStack.NativeFrame
import com.android.tools.profiler.proto.Transport
import com.android.tools.profiler.proto.Transport.GetEventGroupsRequest
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.memory.BaseMemoryProfilerStage
import com.android.tools.profilers.memory.CaptureSelectionAspect
import com.android.tools.profilers.memory.MemoryProfiler.Companion.hasOnlyFullAllocationTrackingWithinRegion
import com.android.tools.profilers.memory.adapters.CaptureObject.ClassifierAttribute
import com.android.tools.profilers.memory.adapters.CaptureObject.InstanceAttribute.*
import com.android.tools.profilers.memory.adapters.classifiers.HeapSet
import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.intellij.openapi.diagnostic.Logger
import gnu.trove.TIntObjectHashMap
import gnu.trove.TLongObjectHashMap
import org.objectweb.asm.Type
import java.io.OutputStream
import java.util.TreeMap
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.stream.Stream
import kotlin.math.max
import kotlin.math.min

class LiveAllocationCaptureObject(private val client: ProfilerClient,
                                  private val session: Common.Session,
                                  private val captureStartTime: Long,
                                  loadService: ExecutorService?,
                                  private val stage: BaseMemoryProfilerStage) : CaptureObject {
  @JvmField
  @VisibleForTesting
  val executorService: ExecutorService? = loadService
                                          ?: Executors.newSingleThreadExecutor(
                                            ThreadFactoryBuilder().setNameFormat("profiler-live-allocation").build()
                                          )
  private val classDb = ClassDb()
  private val instanceMap = TIntObjectHashMap<LiveAllocationInstanceObject>()
  private val callstackMap = TIntObjectHashMap<AllocationStack>()

  // Mapping from unsymbolized addresses to symbolized native frames
  private val nativeFrameMap = TLongObjectHashMap<NativeFrame>()
  private val methodIdMap = TLongObjectHashMap<AllocationStack.StackFrame>()
  private val threadIdMap = TIntObjectHashMap<ThreadId>()
  private val jniMemoryRegionMap = TreeMap<Long, Memory.MemoryMap.MemoryRegion>()
  private val heapSets = mutableListOf(HeapSet(this, CaptureObject.DEFAULT_HEAP_NAME, 0),  // default
                                       HeapSet(this, CaptureObject.IMAGE_HEAP_NAME, 1),  // image
                                       HeapSet(this, CaptureObject.ZYGOTE_HEAP_NAME, 2),  // zygote
                                       HeapSet(this, CaptureObject.APP_HEAP_NAME, 3)) // app
    .also {
      it.add(HeapSet(this, CaptureObject.JNI_HEAP_NAME, CaptureObject.JNI_HEAP_ID))
    }
  private val aspectObserver = AspectObserver()
  private var contextEndTimeNs = Long.MIN_VALUE
  private var previousQueryStartTimeNs = Long.MIN_VALUE
  private var previousQueryEndTimeNs = Long.MIN_VALUE

  // Keeps track of the latest sample's timestamp we have queried thus far.
  private var lastSeenTimestampNs = Long.MIN_VALUE
  private var queryRange: Range? = null
  private var currentTask: Future<*>? = null
  private var infoMessage: String? = null

  private val allocationEventAdapter = object: EventAdapter<Memory.BatchAllocationEvents, AllocationEvent> {
    override fun getTimestamp(event: AllocationEvent) = event.timestamp
    override fun getEventList(batch: Memory.BatchAllocationEvents) = batch.eventsList
    override fun getBatchEvents(startTimeNs: Long, endTimeNs: Long) =
      getEvents(startTimeNs, endTimeNs, Common.Event.Kind.MEMORY_ALLOC_EVENTS) { it.memoryAllocEvents.events }.apply {
        updateSeenTimestamp(Memory.BatchAllocationEvents::getTimestamp)
    }
  }

  private val jniReferenceEventAdapter = object: EventAdapter<Memory.BatchJNIGlobalRefEvent, JNIGlobalReferenceEvent> {
    override fun getTimestamp(event: JNIGlobalReferenceEvent) = event.timestamp
    override fun getEventList(batch: Memory.BatchJNIGlobalRefEvent) = batch.eventsList
    override fun getBatchEvents(startTimeNs: Long, endTimeNs: Long) =
      getEvents(startTimeNs, endTimeNs, Common.Event.Kind.MEMORY_JNI_REF_EVENTS) {it.memoryJniRefEvents.events}
        .apply { updateSeenTimestamp(Memory.BatchJNIGlobalRefEvent::getTimestamp) }
  }

  override fun getSession() = session
  override fun getName() = "Live Allocation"
  override fun getExportableExtension() = null
  override fun saveToFile(outputStream: OutputStream) = throw UnsupportedOperationException()

  override fun getClassifierAttributes() =
    listOf(ClassifierAttribute.LABEL,
           ClassifierAttribute.ALLOCATIONS,
           ClassifierAttribute.DEALLOCATIONS,
           ClassifierAttribute.TOTAL_COUNT,
           ClassifierAttribute.SHALLOW_SIZE,
           ClassifierAttribute.SHALLOW_DIFFERENCE)

  override fun getInstanceAttributes() = listOf(LABEL, ALLOCATION_TIME, DEALLOCATION_TIME, SHALLOW_SIZE)
  override fun getInfoMessage() = infoMessage

  override fun getHeapSets() =
    // Exclude DEFAULT_HEAP since it shouldn't show up in use in devices that support live allocation tracking.
    // But handle the unexpected, just in case....
    if (heapSets[0].instancesCount > 0) heapSets else heapSets.subList(1, heapSets.size)

  override fun getHeapSet(heapId: Int) = heapSets[heapId]
  override fun getInstances() = heapSets.stream().flatMap { it.instancesStream }
  override fun getStartTimeNs() = captureStartTime
  override fun getEndTimeNs() = Long.MAX_VALUE
  override fun getClassDatabase() = classDb

  override fun load(queryRange: Range?, queryJoiner: Executor?) = true.also {
    assert(queryRange != null)
    assert(queryJoiner != null)
    this.queryRange = queryRange
    // TODO There's a problem with this, as the datastore is effectively a real-time system.
    // TODO In other words, when we query for some range, we may not get back entries that are still being inserted, and we don't re-query.
    fun loadTimeRange() = loadTimeRange(this.queryRange!!, queryJoiner!!)
    this.queryRange!!.addDependency(aspectObserver).onChange(Range.Aspect.RANGE, ::loadTimeRange)

    // Load the initial data within queryRange.
    loadTimeRange()
  }

  override fun getStackFrame(methodId: Long) = methodIdMap[methodId]
  override fun isDoneLoading() = true
  override fun isError() = false

  override fun unload() {
    queryRange?.removeDependencies(aspectObserver)
    executorService!!.shutdownNow()
  }

  // Update myContextEndTimeNs and Callstack information
  private fun updateAllocationContexts(endTimeNs: Long) {
    if (contextEndTimeNs < endTimeNs) {
      fun<T> TIntObjectHashMap<T>.putIfAbsent(k: Int, v: T) { if (!containsKey(k)) { put(k, v) } }
      fun<T> TLongObjectHashMap<T>.putIfAbsent(k: Long, v: T) { if (!containsKey(k)) { put(k, v) } }
      for (contexts in getAllocationContexts(contextEndTimeNs, endTimeNs)) {
        // We don't have super class information at the moment so just assign invalid id as the super class id.
        contexts.classesList.forEach { classDb.registerClass(it.classId.toLong(), Type.getType(it.className).className) }
        contexts.methodsList.forEach { methodIdMap.putIfAbsent(it.methodId, it) }
        contexts.encodedStacksList.forEach { callstackMap.putIfAbsent(it.stackId, it) }
        contexts.threadInfosList.forEach {
          threadIdMap.putIfAbsent(it.threadId,
                                  ThreadId(
                                    it.threadName
                                  )
          )
        }
        contexts.memoryMap.regionsList.forEach { jniMemoryRegionMap[it.startAddress] = it }
        contextEndTimeNs = max(contextEndTimeNs, contexts.timestamp)
      }
    }
  }

  /**
   * Load allocation data corresponding to the input time range. Note that load operation is expensive and happens on a different thread
   * (via myExecutorService). When loading is done, it informs the listener (e.g. UI) to update via the input joiner.
   */
  private fun loadTimeRange(queryRange: Range, joiner: Executor) {
    try {
      // Ignore invalid range. This can happen when a selection range is cleared during the process of a new range being selected.
      if (queryRange.isEmpty) {
        return
      }
      currentTask?.cancel(false)
      currentTask = executorService!!.submit<Any?> {
        val newStartTimeNs = TimeUnit.MICROSECONDS.toNanos(queryRange.min.toLong())
        val newEndTimeNs = TimeUnit.MICROSECONDS.toNanos(queryRange.max.toLong())
        if (newStartTimeNs == previousQueryStartTimeNs && newEndTimeNs == previousQueryEndTimeNs) {
          return@submit null
        }
        val hasNonFullTrackingRegion = !hasOnlyFullAllocationTrackingWithinRegion(
          stage.studioProfilers, session,
          TimeUnit.NANOSECONDS.toMicros(newStartTimeNs), TimeUnit.NANOSECONDS.toMicros(newEndTimeNs))
        joiner.execute { stage.captureSelection.aspect.changed(CaptureSelectionAspect.CURRENT_HEAP_UPDATING) }
        updateAllocationContexts(newEndTimeNs)

        // Snapshots data
        val snapshotList = mutableListOf<InstanceObject>()
        val resetSnapshotList = mutableListOf<InstanceObject>()
        // Delta data
        val deltaAllocationList = mutableListOf<InstanceObject>()
        val resetDeltaAllocationList = mutableListOf<InstanceObject>()
        val deltaFreeList = mutableListOf<InstanceObject>()
        val resetDeltaFreeList = mutableListOf<InstanceObject>()

        fun queryDelta(start: Long, end: Long, allocs: MutableList<InstanceObject>, deallocs: MutableList<InstanceObject>, reset: Boolean) {
          queryJavaInstanceDelta(start, end, allocs, deallocs, reset)
          queryJniReferencesDelta(start, end, allocs, deallocs, reset)
        }

        fun<T> List<T>.addAllToEach(vararg outs: MutableList<T>) = outs.forEach { it.addAll(this) }

        // Clear and recreate the instance/heap sets if previous range does not intersect with the new one
        val clear = previousQueryEndTimeNs <= newStartTimeNs || newEndTimeNs <= previousQueryStartTimeNs
        if (clear) {
          instanceMap.clear()
          // If we are resetting, then first establish the object snapshot at the query range's start point.
          queryJavaInstanceSnapshot(newStartTimeNs, snapshotList)
          queryJniReferencesSnapshot(newStartTimeNs, snapshotList)

          // Update the delta allocations and deallocations within the selection range on the snapshot.
          queryDelta(newStartTimeNs, newEndTimeNs, deltaAllocationList, deltaFreeList, false)
        }
        else {
          // Compute selection left differences.
          val leftAllocations = mutableListOf<InstanceObject>()
          val leftDeallocations = mutableListOf<InstanceObject>()
          if (newStartTimeNs < previousQueryStartTimeNs) {
            // Selection's min shifts left
            queryDelta(newStartTimeNs, previousQueryStartTimeNs, leftAllocations, leftDeallocations, false)
            // add data within this range to the deltas
            // Allocations happen after selection min: remove instance from snapshot
            // Deallocations happen after selection min: add instance to snapshot
            leftAllocations.addAllToEach(deltaAllocationList, resetSnapshotList)
            leftDeallocations.addAllToEach(deltaFreeList, snapshotList)
          }
          else if (newStartTimeNs > previousQueryStartTimeNs) {
            // Selection's min shifts right
            queryDelta(previousQueryStartTimeNs, newStartTimeNs, leftAllocations, leftDeallocations, true)
            // Remove data within this range from the deltas
            // Allocations happen before the selection's min: add instance to snapshot
            // Deallocations before the selection's min: remove instance from snapshot
            leftAllocations.addAllToEach(resetDeltaAllocationList, snapshotList)
            leftDeallocations.addAllToEach(resetDeltaFreeList, resetSnapshotList)
          }

          // Compute selection right differences.
          val rightAllocations = mutableListOf<InstanceObject>()
          val rightDeallocations = mutableListOf<InstanceObject>()
          if (newEndTimeNs < previousQueryEndTimeNs) {
            // Selection's max shifts left: remove data within this range from the deltas
            queryDelta(newEndTimeNs, previousQueryEndTimeNs, rightAllocations, rightDeallocations, true)
            resetDeltaAllocationList.addAll(rightAllocations)
            resetDeltaFreeList.addAll(rightDeallocations)
          }
          else if (newEndTimeNs > previousQueryEndTimeNs) {
            // Selection's max shifts right: add data within this range to the deltas
            queryDelta(previousQueryEndTimeNs, newEndTimeNs, rightAllocations, rightDeallocations, false)
            deltaAllocationList.addAll(rightAllocations)
            deltaFreeList.addAll(rightDeallocations)
          }
        }
        previousQueryStartTimeNs = newStartTimeNs
        // Samples that are within the query range may not have arrived from the daemon yet. If the query range is greater than the
        // last sample we have seen. Set the last query timestamp to the last sample's timestmap, so that next time we will requery
        // the range between (last-seen sample, newEndTimeNs).
        previousQueryEndTimeNs = min(newEndTimeNs, lastSeenTimestampNs)
        val selection = stage.captureSelection
        joiner.execute {
          selection.aspect.changed(CaptureSelectionAspect.CURRENT_HEAP_UPDATED)
          if (clear ||
              deltaAllocationList.size + deltaFreeList.size + resetDeltaAllocationList.size + resetDeltaFreeList.size > 0) {
            if (clear) {
              heapSets.forEach { it.clearClassifierSets() }
            }
            snapshotList.forEach { heapSets[it.heapId].addSnapshotInstanceObject(it) }
            resetSnapshotList.forEach { heapSets[it.heapId].removeSnapshotInstanceObject(it) }
            deltaAllocationList.forEach { heapSets[it.heapId].addDeltaInstanceObject(it) }
            deltaFreeList.forEach { heapSets[it.heapId].freeDeltaInstanceObject(it) }
            resetDeltaAllocationList.forEach { heapSets[it.heapId].removeAddedDeltaInstanceObject(it) }
            resetDeltaFreeList.forEach { heapSets[it.heapId].removeFreedDeltaInstanceObject(it) }
            infoMessage = if (hasNonFullTrackingRegion) SAMPLING_INFO_MESSAGE else null
            selection.refreshSelectedHeap()
          }
        }
        null
      }
    }
    catch (e: RejectedExecutionException) {
      logger.debug(e)
    }
  }

  private fun AllocationEvent.Allocation.getOrCreateInstanceObject() =
    instanceMap[tag] ?:
    classDb.getEntry(classTag.toLong()).let { entry ->
      val callstack = if (stackId != 0) callstackMap[stackId]!! else null
      val thread = if (threadId != 0) threadIdMap[threadId]!! else ThreadId.INVALID_THREAD_ID
      LiveAllocationInstanceObject(this@LiveAllocationCaptureObject, entry, thread, callstack, size, heapId).also { instanceMap.put(tag, it) }
    }

  private fun JNIGlobalReferenceEvent.getOrCreateJniRefObject() = instanceMap[objectTag]?.let { referencedObject ->
    referencedObject.getJniRefByValue(refValue) ?:
    JniReferenceInstanceObject(this@LiveAllocationCaptureObject, referencedObject, objectTag.toLong(), refValue)
      .also(referencedObject::addJniRef)
  } // If a Java object can't be found by a given tag, nothing is known about the JNI reference and we can't track it.

  /**
   * Populates the input list with all instance objects that are alive at |snapshotTimeNs|.
   */
  private fun queryJavaInstanceSnapshot(snapshotTimeNs: Long, snapshotList: MutableList<InstanceObject>) =
    querySnapshot(snapshotTimeNs, snapshotList, allocationEventAdapter) { event, liveInstanceMap ->
      when (event.eventCase) {
        AllocationEvent.EventCase.ALLOC_DATA -> {
          // Allocation - create an InstanceObject. This might be removed later if there is a corresponding FREE_DATA event.
          val allocation = event.allocData
          val instance = allocation.getOrCreateInstanceObject()
          instance.setAllocationTime(event.timestamp)
          liveInstanceMap[allocation.tag] = instance
        }
        AllocationEvent.EventCase.FREE_DATA -> {
          // Deallocation - there should be a matching InstanceObject.
          val deallocation = event.freeData
          liveInstanceMap.remove(deallocation.tag)
          // Don't keep deallocated objects around in the cache to avoid bloating memory.
          instanceMap.remove(deallocation.tag)
        }
        // ignore CLASS_DATA as they are handled via context updates.
        AllocationEvent.EventCase.CLASS_DATA -> { }
      }
    }

  private fun queryJniReferencesSnapshot(snapshotTimeNs: Long, snapshotList: MutableList<InstanceObject>) {
    querySnapshot(snapshotTimeNs, snapshotList, jniReferenceEventAdapter) { event, instanceMap ->
      when (event.eventType) {
        JNIGlobalReferenceEvent.Type.CREATE_GLOBAL_REF -> {
          // New global ref - create an InstanceObject. This might be removed later if there is a corresponding DELETE_GLOBAL_REF event.
          // If JNI reference object can't be constructed, it is most likely because allocation for underlying java object was not
          // reported. We don't have anything to show and ignore this reference.
          event.getOrCreateJniRefObject()?.let { refObject ->
            refObject.setAllocEvent(event)
            instanceMap[refObject.refValue] = refObject
          }
        }
        JNIGlobalReferenceEvent.Type.DELETE_GLOBAL_REF -> instanceMap.remove(event.refValue)?.let { refObject ->
          // If the referencing instance object is still around, remove the added JNI ref.
          if (this.instanceMap.containsKey(event.objectTag)) {
            this.instanceMap[event.objectTag].removeJniRef(refObject as JniReferenceInstanceObject)
          }
        }
      }
    }
  }

  private fun<E> querySnapshot(snapshotTimeNs: Long, snapshotList: MutableList<InstanceObject>,
                               eventAdapter: EventAdapter<*, E>, handleEvent: (E, MutableMap<Any, InstanceObject>) -> Unit) {
    val instanceMap = LinkedHashMap<Any, InstanceObject>()
    // Retrieve all the event samples from the start of the session until the snapshot time.
    eventAdapter.forEachEventStream(session.startTimestamp, snapshotTimeNs) { eventStream ->
      // Only consider events up to but excluding the snapshot time.
      eventStream
        .filter { eventAdapter.getTimestamp(it) < snapshotTimeNs }
        .sorted(Comparator.comparingLong(eventAdapter::getTimestamp))
        .forEach { handleEvent(it, instanceMap) }
    }
    snapshotList.addAll(instanceMap.values)
  }

  /**
   * @param startTimeNs      start time to query data for.
   * @param endTimeNs        end time to query data for.
   * @param allocationList   Instances that were allocated within the query range will be added here.
   * @param deallocationList Instances that were deallocated within the query range will be added here.
   * @param resetInstance    Whether the InstanceObject's alloc/dealloc time information should reset if a corresponding allocation or
   * deallocation event has occurred. The [ClassifierSet] rely on the presence (or absence) of these time data
   * to determine whether the InstanceObject should be added (or removed) from the ClassifierSet. Also see [                         ][ClassifierSet.removeDeltaInstanceInformation].
   */
  private fun queryJavaInstanceDelta(startTimeNs: Long,
                                     endTimeNs: Long,
                                     allocationList: MutableList<InstanceObject>,
                                     deallocationList: MutableList<InstanceObject>,
                                     resetInstance: Boolean) =
    queryDelta(startTimeNs, endTimeNs, allocationEventAdapter) { event ->
      when (event.eventCase) {
        AllocationEvent.EventCase.ALLOC_DATA -> {
          // New allocation - create an InstanceObject.
          val instance = event.allocData.getOrCreateInstanceObject()
          instance.setAllocationTime(if (resetInstance) Long.MIN_VALUE else event.timestamp)
          allocationList.add(instance)
        }
        AllocationEvent.EventCase.FREE_DATA -> {
          // New deallocation - there should be a matching InstanceObject.
          val deallocation = event.freeData
          // FIXME(b/180630877) The tag is supposed to always be in the instance map
          instanceMap[deallocation.tag]?.let { instance ->
            instance.deallocTime = if (resetInstance) Long.MAX_VALUE else event.timestamp
            deallocationList.add(instance)
          }
        }
        // ignore CLASS_DATA as they are handled via context updates.
        AllocationEvent.EventCase.CLASS_DATA -> { }
      }
    }

  private fun queryJniReferencesDelta(startTimeNs: Long,
                                      endTimeNs: Long,
                                      allocationList: MutableList<InstanceObject>,
                                      deallocationList: MutableList<InstanceObject>,
                                      resetInstance: Boolean) {
    queryDelta(startTimeNs, endTimeNs, jniReferenceEventAdapter) { event ->
      // If JNI reference object can't be constructed, it is most likely because allocation for underlying java object was not
      // reported. We don't have anything to show and ignore this reference.
      event.getOrCreateJniRefObject()?.let { refObject ->
        when (event.eventType) {
          JNIGlobalReferenceEvent.Type.CREATE_GLOBAL_REF -> {
            if (resetInstance) {
              refObject.setAllocationTime(Long.MIN_VALUE)
            }
            else {
              refObject.setAllocEvent(event)
            }
            allocationList.add(refObject)
          }
          JNIGlobalReferenceEvent.Type.DELETE_GLOBAL_REF -> {
            if (resetInstance) {
              refObject.setAllocationTime(Long.MAX_VALUE)
            }
            else {
              refObject.deallocTime = event.timestamp
              if (event.hasBacktrace()) {
                refObject.setDeallocationBacktrace(event.backtrace)
              }
              refObject.setDeallocThreadId(lookupThreadId(event.threadId))
            }
            deallocationList.add(refObject)
          }
          else -> assert(false)
        }
      }
    }
  }

  private fun<E> queryDelta(startTimeNs: Long, endTimeNs: Long, eventAdapter: EventAdapter<*, E>, handleEvent: (E) -> Unit) {
    // Case for point-snapshot - we don't need to further query deltas.
    if (startTimeNs != endTimeNs) {
      eventAdapter.forEachEventStream(startTimeNs, endTimeNs) { eventStream ->
        eventStream
          .filter { eventAdapter.getTimestamp(it) in startTimeNs until endTimeNs } // Only consider events between the delta range [start time, end time)
          .sorted(Comparator.comparingLong(eventAdapter::getTimestamp))
          .forEach(handleEvent)
      }
    }
  }

  private fun JniReferenceInstanceObject.setAllocEvent(event: JNIGlobalReferenceEvent) {
    setAllocThreadId(lookupThreadId(event.threadId))
    setAllocationTime(event.timestamp)
    if (event.hasBacktrace())
      setAllocationBacktrace(event.backtrace)
  }

  private fun lookupThreadId(threadId: Int): ThreadId = if (threadId != 0) threadIdMap[threadId]!! else ThreadId.INVALID_THREAD_ID

  fun resolveNativeBacktrace(backtrace: NativeBacktrace?): NativeCallStack = when {
    backtrace == null || backtrace.addressesCount == 0 -> NativeCallStack.getDefaultInstance()
    else -> NativeCallStack.newBuilder().let { builder ->
      for (address in backtrace.addressesList) {
        if (!nativeFrameMap.containsKey(address)) {
          val (module, offset) = getRegionByAddress(address)?.let {
            Pair(it.name, it.fileOffset + (address - it.startAddress)) // Adjust address to represent module offset.
          } ?: Pair("", 0L)
          val unsymbolizedFrame = NativeFrame.newBuilder().setAddress(address).setModuleName(module).setModuleOffset(offset).build()
          val symbolizedFrame = stage.studioProfilers.ideServices.nativeFrameSymbolizer
            .symbolize(stage.studioProfilers.sessionsManager.selectedSessionMetaData.processAbi, unsymbolizedFrame)
          nativeFrameMap.put(address, symbolizedFrame)
        }
        builder.addFrames(nativeFrameMap[address])
      }
      builder.build()
    }
  }

  private fun getRegionByAddress(address: Long) = jniMemoryRegionMap.floorEntry(address)?.let { entry ->
    val region = entry.value
    if (address in region.startAddress until region.endAddress) region else null
  }

  private fun getAllocationContexts(startTimeNs: Long, endTimeNs: Long) =
    client.transportClient.getEventGroups(
      buildEventGroupRequest(Common.Event.Kind.MEMORY_ALLOC_CONTEXTS, startTimeNs, endTimeNs + QUERY_BUFFER_NS))
      .getResultList { it.memoryAllocContexts.contexts }

  private fun<T> getEvents(startTimeNs: Long, endTimeNs: Long, event: Common.Event.Kind, extract: (Common.Event) -> T): List<T> =
    client.transportClient.getEventGroups(
      buildEventGroupRequest(event, startTimeNs - QUERY_BUFFER_NS, endTimeNs + QUERY_BUFFER_NS))
      .getResultList(extract)

  private fun<T> Transport.GetEventGroupsResponse.getResultList(extract: (Common.Event) -> T): List<T> = when (groupsCount) {
    1 -> getGroups(0).eventsList.map(extract)
    else -> emptyList<T>().also { assert(groupsCount == 0) }
  }

  private fun buildEventGroupRequest(kind: Common.Event.Kind, startTimeNs: Long, endTimeNs: Long) = GetEventGroupsRequest.newBuilder()
      .setStreamId(session.streamId)
      .setPid(session.pid)
      .setKind(kind)
      .setFromTimestamp(startTimeNs)
      .setToTimestamp(endTimeNs)
      .build()

  private fun<T> List<T>.updateSeenTimestamp(timestamp: (T) -> Long) = stream().mapToLong(timestamp).max().ifPresent {
    lastSeenTimestampNs = max(lastSeenTimestampNs, it)
  }

  companion object {
    private val logger: Logger
      get() = Logger.getInstance(LiveAllocationCaptureObject::class.java)

    // Buffer to ensure the queries include the before/after batched sample(s) that fall just outside of the query range, as those samples
    // can contain events with timestamps that satisfy the query.
    // In perfa, the batched samples are sent in 500ms but can take time to arrive. 5 seconds should be more than enough as a buffer.
    private val QUERY_BUFFER_NS = TimeUnit.SECONDS.toNanos(5)

    @VisibleForTesting
    const val SAMPLING_INFO_MESSAGE = "Selected region does not have full tracking. Data may be inaccurate."
  }

  private interface EventAdapter<B,E> {
    fun getTimestamp(event: E): Long
    fun getEventList(batch: B): List<E>
    fun getBatchEvents(startTimeNs: Long, endTimeNs: Long): List<B>
    fun forEachEventStream(startTimeNs: Long, endTimeNs: Long, handle: (Stream<E>) -> Unit) =
      getBatchEvents(startTimeNs, endTimeNs).forEach { handle(getEventList(it).stream()) }
  }
}