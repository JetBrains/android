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
import com.android.tools.perflib.heap.ClassObj
import com.android.tools.perflib.heap.Instance
import com.android.tools.perflib.heap.Snapshot
import com.android.tools.perflib.heap.ext.NativeRegistryPostProcessor
import com.android.tools.perflib.heap.io.InMemoryBuffer
import com.android.tools.profiler.perfetto.proto.TraceProcessor
import com.android.tools.profiler.perfetto.proto.TraceProcessor.QueryParameters
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Memory.HeapDumpInfo
import com.android.tools.profilers.IdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.analytics.FeatureTracker
import com.android.tools.profilers.analytics.trackLoading
import com.android.tools.profilers.memory.BitmapDuplicationAnalyzer
import com.android.tools.profilers.memory.ClassGrouping
import com.android.tools.profilers.memory.MainMemoryProfilerStage
import com.android.tools.profilers.memory.MemoryProfiler.Companion.saveHeapDumpToFile
import com.android.tools.profilers.memory.TraceProcessorBitmapDuplicationAnalyzer
import com.android.tools.profilers.memory.adapters.CaptureObject.ClassifierAttribute.LABEL
import com.android.tools.profilers.memory.adapters.CaptureObject.ClassifierAttribute.NATIVE_SIZE
import com.android.tools.profilers.memory.adapters.CaptureObject.ClassifierAttribute.RETAINED_NATIVE_SIZE
import com.android.tools.profilers.memory.adapters.CaptureObject.ClassifierAttribute.RETAINED_SIZE
import com.android.tools.profilers.memory.adapters.CaptureObject.ClassifierAttribute.SHALLOW_SIZE
import com.android.tools.profilers.memory.adapters.CaptureObject.ClassifierAttribute.TOTAL_COUNT
import com.android.tools.profilers.memory.adapters.CaptureObject.InstanceAttribute
import com.android.tools.profilers.memory.adapters.classifiers.AllHeapSet
import com.android.tools.profilers.memory.adapters.classifiers.HeapSet
import com.android.tools.profilers.memory.adapters.instancefilters.ActivityFragmentLeakInstanceFilter
import com.android.tools.profilers.memory.adapters.instancefilters.AllClassTypeFilter
import com.android.tools.profilers.memory.adapters.instancefilters.AllIssuesInstanceFilter
import com.android.tools.profilers.memory.adapters.instancefilters.BitmapDuplicationInstanceFilter
import com.android.tools.profilers.memory.adapters.instancefilters.CaptureObjectInstanceFilter
import com.android.tools.profilers.memory.adapters.instancefilters.NoneFilter
import com.android.tools.profilers.memory.adapters.instancefilters.ProjectClassesInstanceFilter
import com.android.tools.profilers.memory.adapters.instancefilters.SystemClassesInstanceFilter
import com.android.tools.profilers.memory.adapters.instancefilters.TraceProcessorActivityFragmentLeakFilter
import com.android.tools.proguard.ProguardMap
import com.google.common.annotations.VisibleForTesting
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.google.wireless.android.sdk.stats.AndroidProfilerEvent.Loading
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.text.StringUtil
import it.unimi.dsi.fastutil.Hash
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import java.io.File
import java.io.OutputStream
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.stream.Stream

open class HeapDumpCaptureObject(
  private val client: ProfilerClient,
  private val _session: Common.Session,
  val heapDumpInfo: HeapDumpInfo,
  private val proguardMap: ProguardMap?,
  private val featureTracker: FeatureTracker,
  val ideProfilerServices: IdeProfilerServices,
  private val fileSupplier: (() -> File?)? = null,
) : CaptureObject {
  var isTraceProcessor = true
    private set

  private val logger = Logger.getInstance(HeapDumpCaptureObject::class.java)

  private val _heapSets: MutableMap<Int, HeapSet> = HashMap()

  private val classObjectInstances = mutableMapOf<Long, InstanceObject>()
  private val classObjectToRepresentedClassMap = mutableMapOf<Long, Long>()

  fun getRepresentedClassId(classObjectId: Long): Long? = classObjectToRepresentedClassMap[classObjectId]

  val syntheticToRepresentedClassMap = mutableMapOf<Long, Long>()
  val representedToSyntheticClassMap = mutableMapOf<Long, Long>()

  private val classObjectInstancesForClasses = mutableMapOf<Long, MutableList<InstanceObject>>()

  fun getClassObjectInstances(classId: Long): List<InstanceObject> {
    classObjectInstancesForClasses[classId]?.let {
      return it
    }
    if (classId !in representedToSyntheticClassMap) return emptyList()

    val representedClassEntry = classDb.getEntry(classId) ?: return emptyList()
    val request =
      QueryParameters.HeapDumpInstancesParameters.newBuilder()
        .addClassNames("java.lang.Class<${representedClassEntry.className}>")
        .setOffset(0)
        .setLimit(1)
        .setSortAttribute(QueryParameters.SortAttribute.SORT_DEPTH)
        .setSortDescending(false)
        .build()

    val response = ideProfilerServices.traceProcessorService.getInstances(heapDumpInfo.startTime, request, ideProfilerServices)
    val bestClassObject =
      response.instanceList.firstOrNull()?.let { inst -> getOrCreateTraceProcessorHeapDumpInstance(representedClassEntry, inst) }
        ?: return emptyList()

    val resultList: MutableList<InstanceObject> = mutableListOf(bestClassObject)
    classObjectInstancesForClasses[classId] = resultList
    classObjectInstances[classId] = bestClassObject
    classObjectToRepresentedClassMap[bestClassObject.instanceId] = classId
    return resultList
  }

  // A load factor of 0.5 is used for performance reasons due to the interaction of two hash tables. See b/372321482 for details.
  private val instanceIndex = Long2ObjectOpenHashMap<InstanceObject>(16, Hash.FAST_LOAD_FACTOR)

  @get:VisibleForTesting val classDb = ClassDb()

  @Volatile private var hasInstancesLoaded = false
  @Volatile private var isFullyLoaded = false

  @Volatile private var isLoadingError = false
  var hasNativeAllocations = false
  var hasRetainedNativeAllocations = false
    private set

  var fileExtension: String? = null
    internal set

  val isARTHeapDump: Boolean
    get() = fileExtension.equals("hprof", ignoreCase = true) || fileExtension.equals("prof", ignoreCase = true)

  private lateinit var activityFragmentLeakFilter: ActivityFragmentLeakInstanceFilter
  private val bitmapDuplicationAnalyzer = BitmapDuplicationAnalyzer()
  private lateinit var bitmapDuplicationFilter: BitmapDuplicationInstanceFilter

  val classesWithLeaks = mutableSetOf<String>()
  val classesWithDuplicates = mutableSetOf<String>()

  // In headless unit test environments, ProjectScopeBuilder.getInstance(myProject) can return null,
  // causing allProjectClasses / AllClassesSearch.search to throw an exception when evaluated on a background thread.
  // We catch Throwable and return emptySet() to prevent unit test crashes while preserving production behavior.
  val projectClasses: Set<String> by lazy {
    try {
      val app = ApplicationManager.getApplication()
      if (app != null) {
        app.runReadAction(Computable { ideProfilerServices.allProjectClasses })
      } else {
        ideProfilerServices.allProjectClasses
      }
    } catch (e: ProcessCanceledException) {
      throw e
    } catch (e: Throwable) {
      emptySet()
    }
  }

  init {
    ApplicationManager.getApplication()?.executeOnPooledThread {
      // Pre-warm lazy projectClasses set asynchronously on a background thread so UI filtering doesn't block EDT.
      @Suppress("UNUSED_VARIABLE") val projectClassesLoaded = projectClasses
    }
  }

  private var supportedClassTypeFilters =
    setOf(AllClassTypeFilter, ProjectClassesInstanceFilter(ideProfilerServices), SystemClassesInstanceFilter(ideProfilerServices))
  private lateinit var supportedIssueTypeFilters: Set<CaptureObjectInstanceFilter> // To be initialized after activity and bitmap filters
  var classTypeFilter: CaptureObjectInstanceFilter? = null
  var issueTypeFilter: CaptureObjectInstanceFilter? = null

  private val executorService =
    MoreExecutors.listeningDecorator(
      Executors.newSingleThreadExecutor(ThreadFactoryBuilder().setNameFormat("memory-heapdump-instancefilters").build())
    )

  private val allInstances: Set<InstanceObject>
    get() = HashSet<InstanceObject>(instanceIndex.size).also { instanceIndex.values.forEach(it::add) }

  @VisibleForTesting
  val instanceFilterExecutor
    get() = executorService

  override fun getName() = "Heap Dump"

  override fun isExportable() = true

  override fun getExportableExtension() = fileExtension ?: "hprof"

  override fun saveToFile(outputStream: OutputStream) = saveHeapDumpToFile(client, _session, heapDumpInfo, outputStream, featureTracker)

  override fun getHeapSets() = if (hasInstancesLoaded) _heapSets.values else emptyList()

  override fun getHeapSet(heapId: Int) = _heapSets.getOrDefault(heapId, null)

  override fun getInstances(): Stream<InstanceObject> =
    if (hasInstancesLoaded) heapSets.find { it is AllHeapSet }!!.instancesStream else Stream.empty()

  override fun getStartTimeNs() = heapDumpInfo.startTime

  override fun getEndTimeNs() = heapDumpInfo.endTime

  val filterInstances = mutableSetOf<InstanceObject>()

  fun getIssueInstances(filter: CaptureObjectInstanceFilter): Sequence<InstanceObject> {
    val targetInstances = if (isTraceProcessor) filterInstances else allInstances
    return targetInstances.filter { filter.instanceTest(it) }.asSequence()
  }

  override fun getClassDatabase() = classDb

  override fun getSession() = _session

  override fun load(queryRange: Range?, queryJoiner: Executor?): Boolean {
    val file = fileSupplier?.invoke()
    if (file == null || !file.exists() || file.length() == 0L) {
      logger.warn("Heap dump file is missing or empty.")
      isLoadingError = true
      return false
    }

    fileExtension = file.extension

    return true.also {
      ideProfilerServices.featureTracker.trackLoading(
        Loading.Type.HPROF,
        sizeKb = (file.length() / 1024).toInt(),
        measure = { instanceIndex.size.toLong() },
      ) {
        val useTraceProcessor = if (isARTHeapDump) ideProfilerServices.featureConfig.isUseTraceProcessorForHprofEnabled else true
        this.isTraceProcessor = useTraceProcessor
        if (useTraceProcessor) {
          val loadSuccess = ideProfilerServices.traceProcessorService.loadTrace(heapDumpInfo.startTime, file, ideProfilerServices)
          if (loadSuccess) {
            logger.info("TraceProcessor successfully loaded heap dump file: ${file.absolutePath}")
            loadFromTraceProcessor(heapDumpInfo.startTime)
          } else {
            logger.warn("TraceProcessor failed to load heap dump file: ${file.absolutePath}")
            isLoadingError = true
          }
        } else {
          val buffer =
            try {
              InMemoryBuffer(file)
            } catch (e: Exception) {
              logger.warn("Heap dump file failed to parse into buffer.", e)
              isLoadingError = true
              null
            }
          if (buffer != null) {
            load(buffer)
          }
        }
      }
    }
  }

  @VisibleForTesting
  fun load(buffer: InMemoryBuffer) {
    val nativeRegistryPostProcessor = NativeRegistryPostProcessor()
    val snapshot = Snapshot.createSnapshot(buffer!!, proguardMap ?: ProguardMap(), listOf(nativeRegistryPostProcessor))
    snapshot.computeRetainedSizes()
    hasNativeAllocations = nativeRegistryPostProcessor.hasNativeAllocations
    val heapSetMappings = snapshot.heaps.associateWith { HeapSet(this, StringUtil.escapeXmlEntities(it.name), it.id) }
    val addInstanceToRightHeap: (HeapSet, Long, InstanceObject) -> Unit =
      AllHeapSet(this, heapSetMappings.values.toTypedArray()).let { superHeap ->
        superHeap.clearClassifierSets() // forces sub-classifier creation
        _heapSets[superHeap.id] = superHeap
        { _, id, classInst -> addInstance(superHeap, id, classInst) }
      }
    // Iterate creating UI objects for some inspectable in the heap dump:
    // 1. If a class is in the image heap, include it only if it has instances.
    // 2. If a class is not in the image heap, include it always.
    snapshot.heaps
      .asSequence()
      .flatMap { it.classes.asSequence() }
      .forEach { classObj ->
        classObj.makeEntry()
        val isInImageHeap = classObj.heap?.name == CaptureObject.IMAGE_HEAP_NAME
        if ((isInImageHeap && classObj.instanceCount > 0) || !isInImageHeap) {
          val heapSet = heapSetMappings[classObj.heap]!!
          // Create an InstanceObject for the ClassObj. The ID must be the real classObj.id
          // so that findInstanceObject and other lookups work correctly.
          addInstanceToRightHeap(heapSet, classObj.id, createClassObjectInstance(classObj))
        }
      }

    // Process all other instances (ClassInstance and ArrayInstance).
    heapSetMappings.forEach { (heap, heapSet) ->
      heap.forEachInstance { instance ->
        // The class for this instance would have already been registered.
        val classEntry = instance.classObj!!.makeEntry()
        addInstanceToRightHeap(heapSet, instance.id, HeapDumpInstanceObject(this@HeapDumpCaptureObject, instance, classEntry, null))
        true
      }
      if ("default" != heap.name || snapshot.heaps.size == 1 || heap.instancesCount > 0) {
        _heapSets.put(heap.id, heapSet)
      }
    }
    hasRetainedNativeAllocations = hasNativeAllocations && _heapSets.values.any { it.totalRetainedNativeSize > 0L }
    hasInstancesLoaded = true
    activityFragmentLeakFilter = ActivityFragmentLeakInstanceFilter(classDb)
    bitmapDuplicationAnalyzer.apply {
      analyze(allInstances)
      bitmapDuplicationFilter = BitmapDuplicationInstanceFilter(getDuplicateInstances())
    }
    classesWithLeaks.addAll(allInstances.filter { activityFragmentLeakFilter.instanceTest(it) }.map { it.classEntry.className })
    classesWithDuplicates.addAll(allInstances.filter { bitmapDuplicationFilter.instanceTest(it) }.map { it.classEntry.className })
    // Initialize supportedIssueTypeFilters now that activity and bitmap filters are ready
    supportedIssueTypeFilters =
      setOf(
        NoneFilter,
        AllIssuesInstanceFilter(activityFragmentLeakFilter, bitmapDuplicationFilter),
        activityFragmentLeakFilter,
        bitmapDuplicationFilter,
      )
    isFullyLoaded = true
  }

  private fun loadFromTraceProcessor(traceId: Long) {
    val result = ideProfilerServices.traceProcessorService.loadHeapDumpData(traceId, ideProfilerServices)
    hasNativeAllocations = result.classOverviewList.any { it.nativeSize > 0L }
    hasRetainedNativeAllocations = result.classOverviewList.any { it.retainedNativeSize > 0L }

    val heapSetMappings = mutableMapOf<String, HeapSet>()
    // Ensure standard Android heaps exist, since we no longer iterate all instances at load time
    val standardHeaps = setOf("default", "app", "image", "zygote")
    val allHeaps = (result.classOverviewList.map { it.heapName.standardHeapName() } + standardHeaps).distinct()
    allHeaps.forEach { name -> heapSetMappings[name] = HeapSet(this, name, if (name == "default") 0 else name.hashCode()) }
    // Also ensure "default" heap exists if there are no instances or if we need a fallback
    if (!heapSetMappings.containsKey("default")) {
      heapSetMappings["default"] = HeapSet(this, "default", 0)
    }

    val superHeap = AllHeapSet(this, heapSetMappings.values.toTypedArray())
    superHeap.clearClassifierSets()
    _heapSets[superHeap.id] = superHeap

    val classObjectOverviews = mutableListOf<TraceProcessor.HeapDumpResult.ClassOverview>()
    result.classOverviewList.forEach { cls ->
      if (cls.className.startsWith("java.lang.Class<") && cls.className.endsWith(">")) {
        classObjectOverviews.add(cls)
      } else {
        classDb.registerClass(cls.classId, cls.superClassId, cls.className, -1L)
      }
    }

    val classNameToEntries = classDb.classEntries.groupBy { it.className }

    if (classDb.getEntriesByName(ClassDb.JAVA_LANG_CLASS).isEmpty()) {
      classDb.registerClass(ClassDb.INVALID_CLASS_ID.toLong(), ClassDb.JAVA_LANG_CLASS)
    }

    syntheticToRepresentedClassMap.clear()
    representedToSyntheticClassMap.clear()

    val representedInstanceCounts = mutableMapOf<Long, Int>()

    result.classOverviewList.forEach { cls ->
      if (!classDb.hasEntry(cls.classId)) return@forEach

      val classEntry = classDb.getEntry(cls.classId)
      val count = cls.instanceCount.toInt()
      representedInstanceCounts[cls.classId] = count

      if (count == 0) return@forEach

      val heapSet = heapSetMappings[cls.heapName.standardHeapName()]
      if (heapSet != null) {
        val overviewInst =
          TraceProcessorHeapDumpInstance(
            this,
            classEntry,
            false,
            heapSet.id,
            count,
            cls.shallowSize.toInt(),
            cls.nativeSize,
            cls.retainedNativeSize,
            cls.retainedSize,
          )
        superHeap.addLazyClassOverview(overviewInst, count, cls.shallowSize, cls.nativeSize, cls.retainedNativeSize, cls.retainedSize)
      }
    }

    // Now process the class objects we saved, grouping by represented name to avoid double-counting
    processClassObjectOverviews(classObjectOverviews, classNameToEntries, representedInstanceCounts, superHeap, heapSetMappings)

    heapSetMappings.forEach { (name, heapSet) ->
      if ("default" != name || heapSetMappings.size == 1 || heapSet.totalObjectSetCount > 0) {
        _heapSets[heapSet.id] = heapSet
      }
    }

    if (isARTHeapDump) {
      preloadFilterInstances()
    } else {
      bitmapDuplicationFilter = BitmapDuplicationInstanceFilter(emptySet())
      activityFragmentLeakFilter = TraceProcessorActivityFragmentLeakFilter(classDb, this)
    }

    supportedIssueTypeFilters =
      setOf(
        NoneFilter,
        AllIssuesInstanceFilter(activityFragmentLeakFilter, bitmapDuplicationFilter),
        activityFragmentLeakFilter,
        bitmapDuplicationFilter,
      )

    hasInstancesLoaded = true
    isFullyLoaded = true
  }

  private fun processClassObjectOverviews(
    classObjectOverviews: List<TraceProcessor.HeapDumpResult.ClassOverview>,
    classNameToEntries: Map<String, List<ClassDb.ClassEntry>>,
    representedInstanceCounts: Map<Long, Int>,
    superHeap: AllHeapSet,
    heapSetMappings: Map<String, HeapSet>,
  ) {
    classObjectOverviews
      .groupBy { it.className.substring("java.lang.Class<".length, it.className.length - 1) }
      .forEach { (representedName, clsList) ->
        val representedEntry = classNameToEntries[representedName]?.firstOrNull()
        if (representedEntry != null) {
          val bestCls = clsList.maxByOrNull { it.retainedSize } ?: clsList.first()
          syntheticToRepresentedClassMap[bestCls.classId] = representedEntry.classId
          representedToSyntheticClassMap[representedEntry.classId] = bestCls.classId

          val instanceCount = representedInstanceCounts[representedEntry.classId] ?: 0
          val isInImageHeap = bestCls.heapName.standardHeapName() == CaptureObject.IMAGE_HEAP_NAME

          if ((isInImageHeap && instanceCount > 0) || !isInImageHeap) {
            val heapSet = heapSetMappings[bestCls.heapName.standardHeapName()]
            if (heapSet != null) {
              // A class object acts as 1 instance representing the class itself
              val overviewInst =
                TraceProcessorHeapDumpInstance(
                  this,
                  representedEntry,
                  true,
                  heapSet.id,
                  1,
                  bestCls.shallowSize.toInt(),
                  bestCls.nativeSize,
                  bestCls.retainedNativeSize,
                  bestCls.retainedSize,
                )
              // Pass 0 for sizes to avoid inflating the overall class size with the class object's size
              superHeap.addLazyClassOverview(overviewInst, 1, 0L, 0L, 0L, 0L)
            }
          }
        }
      }
  }

  private fun preloadFilterInstances() {
    // Eagerly fetch specific instances to allow filters to work without full memory load
    filterInstances.clear()

    val activitySubclasses =
      classDb.getEntriesByName(TraceProcessorActivityFragmentLeakFilter.ACTIVTY_CLASS_NAME).flatMapTo(HashSet()) { classEntry ->
        classDb.getDescendantClasses(classEntry.classId)
      }
    val fragmentSubclasses =
      listOf(
          TraceProcessorActivityFragmentLeakFilter.NATIVE_FRAGMENT_CLASS_NAME,
          TraceProcessorActivityFragmentLeakFilter.SUPPORT_FRAGMENT_CLASS_NAME,
          TraceProcessorActivityFragmentLeakFilter.ANDROIDX_FRAGMENT_CLASS_NAME,
        )
        .flatMap { className -> classDb.getEntriesByName(className) }
        .flatMapTo(HashSet()) { classEntry -> classDb.getDescendantClasses(classEntry.classId) }

    val filterClasses =
      activitySubclasses +
        fragmentSubclasses +
        classDb.getEntriesByName(TraceProcessorBitmapDuplicationAnalyzer.BITMAP_CLASS_NAME) +
        classDb.getEntriesByName(TraceProcessorBitmapDuplicationAnalyzer.BITMAP_DUMP_DATA_CLASS_NAME)

    val classNames = filterClasses.map { it.className }
    val instancesResult =
      ideProfilerServices.traceProcessorService.getInstancesForClasses(heapDumpInfo.startTime, classNames, ideProfilerServices)
    val classEntryMap = filterClasses.associateBy { it.classId }

    instancesResult.instanceList.forEach { inst ->
      classEntryMap[inst.typeId]?.let { cls ->
        val tpInst = getOrCreateTraceProcessorHeapDumpInstance(cls, inst)
        filterInstances.add(tpInst)
      }
    }
    val tpLeakFilter = TraceProcessorActivityFragmentLeakFilter(classDb, this)
    tpLeakFilter.preloadLeakTestFields(filterInstances)
    activityFragmentLeakFilter = tpLeakFilter
    val tpBitmapAnalyzer = TraceProcessorBitmapDuplicationAnalyzer()
    tpBitmapAnalyzer.analyze(filterInstances, this)
    bitmapDuplicationFilter = BitmapDuplicationInstanceFilter(tpBitmapAnalyzer.getDuplicateInstances())
    classesWithLeaks.addAll(filterInstances.filter { tpLeakFilter.instanceTest(it) }.map { it.classEntry.className })
    classesWithDuplicates.addAll(filterInstances.filter { bitmapDuplicationFilter.instanceTest(it) }.map { it.classEntry.className })
  }

  fun getInstancesByIds(instanceIds: List<Long>): List<TraceProcessor.HeapDumpInstancesResult.InstanceData> {
    val request = TraceProcessor.QueryParameters.HeapDumpInstancesParameters.newBuilder().addAllInstanceIds(instanceIds).build()
    val response = ideProfilerServices.traceProcessorService.getInstances(heapDumpInfo.startTime, request, ideProfilerServices)
    return response.instanceList
  }

  fun getPrimitiveFields(instanceId: Long): TraceProcessor.GetPrimitiveFieldsResult {
    return ideProfilerServices.traceProcessorService.getPrimitiveFields(heapDumpInfo.startTime, listOf(instanceId), ideProfilerServices)
  }

  fun getPrimitiveFieldsBulk(instanceIds: List<Long>): TraceProcessor.GetPrimitiveFieldsResult {
    return ideProfilerServices.traceProcessorService.getPrimitiveFields(heapDumpInfo.startTime, instanceIds, ideProfilerServices)
  }

  fun getReferencesBulk(
    instanceIds: List<Long>,
    fetchForward: Boolean = true,
    fetchReverse: Boolean = true,
  ): TraceProcessor.GetReferencesResult {
    return ideProfilerServices.traceProcessorService.getReferences(
      heapDumpInfo.startTime,
      instanceIds,
      fetchForward,
      fetchReverse,
      ideProfilerServices,
    )
  }

  private fun addInstance(heapSet: HeapSet, id: Long, instObj: InstanceObject) {
    assert(!instanceIndex.containsKey(id))
    instanceIndex.put(id, instObj)
    heapSet.addDeltaInstanceObject(instObj)
  }

  override fun isDoneLoading() = isFullyLoaded || isLoadingError

  override fun isError() = isLoadingError

  override fun unload() {
    executorService.shutdownNow()
    ideProfilerServices.traceProcessorService.unloadTrace(heapDumpInfo.startTime)
  }

  override fun getClassifierAttributes(): List<CaptureObject.ClassifierAttribute> {
    val attributes = mutableListOf(LABEL, TOTAL_COUNT)
    if (hasNativeAllocations) attributes.add(NATIVE_SIZE)
    attributes.add(SHALLOW_SIZE)
    if (hasRetainedNativeAllocations) attributes.add(RETAINED_NATIVE_SIZE)
    attributes.add(RETAINED_SIZE)
    return attributes
  }

  override fun getInstanceAttributes(): List<CaptureObject.InstanceAttribute> {
    val attributes = mutableListOf(InstanceAttribute.LABEL, InstanceAttribute.DEPTH)
    if (hasNativeAllocations) attributes.add(InstanceAttribute.NATIVE_SIZE)
    attributes.add(InstanceAttribute.SHALLOW_SIZE)
    if (hasRetainedNativeAllocations) attributes.add(InstanceAttribute.RETAINED_NATIVE_SIZE)
    attributes.add(InstanceAttribute.RETAINED_SIZE)
    return attributes
  }

  open fun findInstanceObject(instance: Instance) = if (hasInstancesLoaded) instanceIndex.get(instance.id) else null

  fun getOrCreateTraceProcessorHeapDumpInstance(
    cls: ClassDb.ClassEntry,
    inst: TraceProcessor.HeapDumpInstancesResult.InstanceData,
  ): TraceProcessorHeapDumpInstanceObject {
    val representedClassId = syntheticToRepresentedClassMap[cls.classId]
    val finalCls =
      if (representedClassId != null && classDb.hasEntry(representedClassId)) {
        classDb.getEntry(representedClassId)
      } else {
        cls
      }
    return (instanceIndex.get(inst.id) as? TraceProcessorHeapDumpInstanceObject)
      ?: run {
        val valueType =
          when {
            inst.id == finalCls.classId -> ValueObject.ValueType.CLASS
            finalCls.className == "java.lang.String" -> ValueObject.ValueType.STRING
            finalCls.className.endsWith("[]") -> ValueObject.ValueType.ARRAY
            else -> ValueObject.ValueType.OBJECT
          }
        val tpInst = TraceProcessorHeapDumpInstanceObject(finalCls, inst, valueType, this, emptyList(), emptyList())
        instanceIndex.put(inst.id, tpInst)
        tpInst
      }
  }

  open fun findInstanceObjectById(id: Long): InstanceObject? {
    instanceIndex.get(id)?.let {
      return it
    }
    val instances = getInstancesByIds(listOf(id))
    if (instances.isNotEmpty()) {
      val inst = instances.first()
      if (classDb.hasEntry(inst.typeId)) {
        val cls = classDb.getEntry(inst.typeId)
        return getOrCreateTraceProcessorHeapDumpInstance(cls, inst)
      } else {
        val representedId = syntheticToRepresentedClassMap[inst.typeId]
        if (representedId != null) {
          val representedEntry = classDb.getEntry(representedId)
          return getOrCreateTraceProcessorHeapDumpInstance(representedEntry, inst)
        }
      }
    }
    return null
  }

  fun findInstanceObjectByIdCached(id: Long): InstanceObject? = instanceIndex.get(id)

  fun prefetchReferences(instances: List<TraceProcessorHeapDumpInstanceObject>) {
    val unrequestedInstances = instances.filter { !it.fetchedReferences }
    if (unrequestedInstances.isEmpty()) return

    val result = getReferencesBulk(unrequestedInstances.map { it.instanceId })
    val refsByOwner = result.referenceList.groupBy { it.ownerId }
    val refsByOwned = result.referenceList.groupBy { it.ownedId }

    unrequestedInstances.forEach { inst ->
      val forward = refsByOwner[inst.instanceId] ?: emptyList()
      val reverse = refsByOwned[inst.instanceId] ?: emptyList()
      inst.setReferences(forward, reverse)
    }
  }

  fun prefetchReverseReferences(instances: List<TraceProcessorHeapDumpInstanceObject>) {
    val unrequestedInstances = instances.filter { !it.fetchedReverseReferences }
    if (unrequestedInstances.isEmpty()) return

    val result = getReferencesBulk(unrequestedInstances.map { it.instanceId }, fetchForward = false, fetchReverse = true)
    val refsByOwned = result.referenceList.groupBy { it.ownedId }

    unrequestedInstances.forEach { inst ->
      val reverse = refsByOwned[inst.instanceId] ?: emptyList()
      inst.setReverseReferences(reverse)
    }
  }

  fun createClassObjectInstance(classObj: ClassObj, isTransient: Boolean = false): InstanceObject {
    // The ClassEntry associated with this InstanceObject should be for the class it represents
    // (e.g. MyClass), not "java.lang.Class".
    // This makes the object appear under its own class grouping in the UI, where it can act as a
    // placeholder to access its static fields.
    val classEntry = classObj.makeEntry()
    return HeapDumpInstanceObject(this, classObj, classEntry, ValueObject.ValueType.CLASS, isTransient)
  }

  /**
   * Finds an existing [InstanceObject] or creates one on-demand if the reference is a [ClassObj] that was filtered out during initial
   * loading (e.g. a class on the image heap with no instances). The new instance is registered in the instance index and added to the
   * correct heap.
   */
  fun getOrCreateInstanceObject(instance: Instance): InstanceObject? {
    return findInstanceObject(instance)
      ?: when (instance) {
        is ClassObj -> createClassObjectInstance(instance, true)
        else -> null
      }
  }

  override fun getActivityFragmentLeakFilter() = activityFragmentLeakFilter

  override fun getBitmapDuplicationFilter() = bitmapDuplicationFilter

  override fun getSupportedClassTypeFilters() = supportedClassTypeFilters

  override fun getSupportedIssueTypeFilters() = supportedIssueTypeFilters

  override fun getSelectedInstanceFilters(): Set<CaptureObjectInstanceFilter> = setOfNotNull(classTypeFilter, issueTypeFilter)

  override fun setClassTypeFilter(filter: CaptureObjectInstanceFilter?, analyzeJoiner: Executor): ListenableFuture<Void?> {
    assert(filter == null || filter in supportedClassTypeFilters)
    classTypeFilter = filter
    return applyFilters(analyzeJoiner)
  }

  override fun setIssueTypeFilter(filter: CaptureObjectInstanceFilter?, analyzeJoiner: Executor): ListenableFuture<Void?> {
    assert(filter == null || filter in supportedIssueTypeFilters)
    issueTypeFilter = filter
    return applyFilters(analyzeJoiner)
  }

  private fun applyFilters(analyzeJoiner: Executor): ListenableFuture<Void?> {
    val filtersToApply = selectedInstanceFilters
    return executorService.submit<Void?> {
      if (!isTraceProcessor) {
        val instancesToShow = filtersToApply.fold(allInstances) { instances, filter -> filter.filter(instances) }
        // The refreshInstances call needs to block until the UI work is complete, so we wait for the result of the future it returns.
        refreshInstances(instancesToShow, analyzeJoiner)
      }
      null
    }
  }

  private fun refreshInstances(instances: Set<InstanceObject>, executor: Executor): Void? {
    executor.execute {
      _heapSets.values.forEach { it.clearClassifierSets() }
      when (val h = _heapSets.values.find { it is AllHeapSet }) {
        null -> instances.forEach { _heapSets[it.heapId]!!.addDeltaInstanceObject(it) }
        else -> instances.forEach { h.addDeltaInstanceObject(it) }
      }
    }
    return null
  }

  override fun canSafelyLoad(): Boolean {
    val file = fileSupplier?.invoke()
    return if (file != null) {
      MainMemoryProfilerStage.canSafelyLoadHprof(file.length())
    } else {
      false
    }
  }

  override fun isGroupingSupported(grouping: ClassGrouping?): Boolean {
    return when (grouping) {
      ClassGrouping.ARRANGE_BY_CLASS,
      ClassGrouping.ARRANGE_BY_PACKAGE -> true
      else -> false
    }
  }

  private fun ClassObj.makeEntry(name: String = this.className) =
    if (superClassObj != null) classDb.registerClass(id, superClassObj!!.id, name, totalRetainedSize)
    else classDb.registerClass(id, name, totalRetainedSize)
}

/** Lightweight synthetic proxy node representing a class overview or synthetic class object in classifier sets. */
class TraceProcessorHeapDumpInstance(
  val captureObject: HeapDumpCaptureObject,
  classEntry: ClassDb.ClassEntry,
  val isSyntheticClass: Boolean,
  val overviewHeapId: Int,
  val overviewCount: Int,
  val overviewShallowSize: Int = 0,
  val overviewNativeSize: Long = 0L,
  val overviewRetainedNativeSize: Long = 0L,
  val overviewRetainedSize: Long = 0L,
) : InstanceObject {
  private val _classEntry = classEntry

  override fun getClassEntry(): ClassDb.ClassEntry = _classEntry

  override fun getCallStackDepth(): Int = 0

  override fun getHeapId(): Int = overviewHeapId

  override fun getValueType(): ValueObject.ValueType = ValueObject.ValueType.OBJECT

  override fun getNativeSize(): Long = if (isSyntheticClass) 0L else overviewNativeSize

  override fun getShallowSize(): Int = if (isSyntheticClass) 0 else overviewShallowSize

  override fun getRetainedNativeSize(): Long = if (isSyntheticClass) 0L else overviewRetainedNativeSize

  override fun getRetainedSize(): Long = if (isSyntheticClass) 0L else overviewRetainedSize

  override fun getInstanceCount(): Int = overviewCount

  override fun getName(): String = ""

  override fun getValueText(): String {
    if (isSyntheticClass) {
      val id = captureObject.representedToSyntheticClassMap[_classEntry.classId] ?: 0
      return "${_classEntry.simpleClassName}.class@$id"
    }
    return ""
  }
}
