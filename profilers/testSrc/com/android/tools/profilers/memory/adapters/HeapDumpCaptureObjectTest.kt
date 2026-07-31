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

import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.transport.TransportServiceUtils
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.perflib.heap.SnapshotBuilder
import com.android.tools.profiler.perfetto.proto.TraceProcessor
import com.android.tools.profiler.proto.Memory.HeapDumpInfo
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.FakeTraceProcessorService
import com.android.tools.profilers.IdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilersTestData
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.memory.FakeCaptureObjectLoader
import com.android.tools.profilers.memory.MainMemoryProfilerStage
import com.android.tools.profilers.memory.MemoryProfilerTestUtils.findChildClassSetWithName
import com.android.tools.profilers.memory.adapters.classifiers.ClassSet
import com.android.tools.profilers.memory.adapters.instancefilters.ActivityFragmentLeakInstanceFilter
import com.android.tools.profilers.memory.adapters.instancefilters.BitmapDuplicationInstanceFilter
import com.google.common.truth.Truth
import java.io.File
import java.util.HashSet
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.stream.Collectors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class HeapDumpCaptureObjectTest {

  private val myTimer = FakeTimer()
  private val myTransportService = FakeTransportService(myTimer)
  private val myIdeProfilerServices = FakeIdeProfilerServices()

  @get:Rule val myGrpcChannel = FakeGrpcChannel("HeapDumpCaptureObjectTest", myTransportService)

  private lateinit var myStage: MainMemoryProfilerStage

  @Before
  fun setUp() {
    myIdeProfilerServices.setHeapDumpTraceInEditorEnabled(false)
    myStage =
      MainMemoryProfilerStage(
        StudioProfilers(ProfilerClient(myGrpcChannel.channel), myIdeProfilerServices, myTimer),
        FakeCaptureObjectLoader(),
      )
  }

  private fun createCaptureObject(
    dumpInfo: HeapDumpInfo,
    file: File,
    ideServices: IdeProfilerServices = myStage.studioProfilers.ideServices,
    useTraceProcessor: Boolean = false,
  ): HeapDumpCaptureObject {
    (ideServices as? FakeIdeProfilerServices)?.setUseTraceProcessorForHprofEnabled(useTraceProcessor)
    return HeapDumpCaptureObject(
      ProfilerClient(myGrpcChannel.channel),
      ProfilersTestData.SESSION_DATA,
      dumpInfo,
      null,
      ideServices.featureTracker,
      ideServices,
      { file },
    )
  }

  private fun createTraceProcessorCaptureObject(testService: FakeTraceProcessorService): HeapDumpCaptureObject {
    val file = TransportServiceUtils.createTempFile("temp_tp_heap", ".hprof", ByteString.copyFrom(byteArrayOf(1, 2, 3, 4)))
    val services = FakeIdeProfilerServices()
    services.setHeapDumpTraceInEditorEnabled(true)
    testService.forceSucceedLoadTrace = true
    services.setTraceProcessorService(testService)
    val dumpInfo = HeapDumpInfo.newBuilder().setStartTime(1000L).setEndTime(2000L).build()
    return createCaptureObject(dumpInfo, file, ideServices = services, useTraceProcessor = true)
  }

  /**
   * This is a high-level test that validates the generation of the hprof MemoryObject hierarchy based on a Snapshot buffer. We want to
   * ensure not only the HeapDumpCaptureObject holds the correct HeapSet(s) representing the Snapshot, but children MemoryObject nodes (e.g.
   * ClassSet, InstanceObject) hold correct information as well.
   */
  @Test
  @Throws(Exception::class)
  fun testHeapDumpObjectsGeneration() {
    val startTimeNs: Long = 3
    val endTimeNs: Long = 8
    val dumpInfo = HeapDumpInfo.newBuilder().setStartTime(startTimeNs).setEndTime(endTimeNs).build()
    val snapshotBuilder = SnapshotBuilder(2, 0, 0).addReferences(1, 2).addRoot(1)
    val buffer = snapshotBuilder.byteBuffer
    val tempFile = TransportServiceUtils.createTempFile("temp_heap", ".hprof", ByteString.copyFrom(buffer))

    val capture = createCaptureObject(dumpInfo, tempFile)

    // Verify values associated with the HeapDumpInfo object.
    assertEquals(startTimeNs, capture.startTimeNs)
    assertEquals(endTimeNs, capture.endTimeNs)
    assertFalse(capture.isDoneLoading)
    assertFalse(capture.isError)

    // Load in a simple Snapshot and verify the MemoryObject hierarchy:
    // - 1 holds reference to 2
    // - single root object in default heap

    myTransportService.addFile(startTimeNs.toString(), tempFile.absolutePath)
    capture.load(null, null)
    assertTrue(capture.isDoneLoading)
    assertFalse(capture.isError)

    val heaps = capture.heapSets
    assertEquals(2, heaps.size.toLong()) // default heap should not show up if it doesn't contain anything

    // "default" heap only contains roots, no ClassObjects
    val defaultHeap = heaps.stream().filter { heap -> "default" == heap.name }.findFirst().orElse(null)
    assertNull(defaultHeap)

    // "testHeap" contains the reference, softreference classes, plus a unique class for each instance we created (2).
    val testHeap = heaps.stream().filter { heap -> "testHeap" == heap.name }.findFirst().orElse(null)
    assertEquals(testHeap!!.name, "testHeap")
    assertEquals(6, testHeap.instancesCount.toLong())

    val classClassifier = ClassSet.createDefaultClassifier()
    classClassifier.partition(emptyList(), testHeap.instancesStream.collect(Collectors.toCollection { HashSet<InstanceObject>() }))
    val classSets = classClassifier.filteredClassifierSets
    assertEquals(4, classSets.size.toLong())
    assertTrue(classSets.stream().allMatch { classifier -> classifier is ClassSet })
    // With the change, each ClassObj is an instance of its own class, so we expect a ClassSet for each class in the snapshot.
    assertTrue(classSets.stream().anyMatch { classifier -> "java.lang.ref.Reference" == (classifier as ClassSet).classEntry.className })
    assertTrue(classSets.stream().anyMatch { classifier -> "SoftAndHardReference" == (classifier as ClassSet).classEntry.className })
    assertTrue(classSets.stream().anyMatch { classifier -> "Class0" == (classifier as ClassSet).classEntry.className })
    assertTrue(classSets.stream().anyMatch { classifier -> "Class1" == (classifier as ClassSet).classEntry.className })

    // The ClassSet for a class now contains both regular instances and the class object instance.
    assertEquals(2, findChildClassSetWithName(classClassifier, "Class0").instancesCount.toLong())
    assertEquals(1, findChildClassSetWithName(classClassifier, "java.lang.ref.Reference").instancesCount.toLong())
    // We need to filter by value type to get the regular object instance we want to test.
    val instance0 =
      findChildClassSetWithName(classClassifier, "Class0")
        .instancesStream
        .filter { i -> i.valueType == ValueObject.ValueType.OBJECT }
        .findFirst()
        .orElse(null)
    val instance1 =
      findChildClassSetWithName(classClassifier, "Class1")
        .instancesStream
        .filter { i -> i.valueType == ValueObject.ValueType.OBJECT }
        .findFirst()
        .orElse(null)
    verifyInstance(instance0!!, "Class0@1 (0x1)", 0, 1, 0)
    verifyInstance(instance1!!, "Class1@2 (0x2)", 1, 0, 1)

    // Also verify the Class object instances, which are now visible due to the change.
    val class0ClassObject =
      findChildClassSetWithName(classClassifier, "Class0")
        .instancesStream
        .filter { i -> i.valueType == ValueObject.ValueType.CLASS }
        .findFirst()
        .orElse(null)
    assertNotNull(class0ClassObject)
    // Class objects are not part of the GC root path in this test, so their depth is MAX_VALUE.
    // They also have no static fields (in this test) and no incoming references.
    verifyInstance(class0ClassObject!!, "Class0.class@101 (0x65)", Integer.MAX_VALUE, 0, 0)

    val class1ClassObject =
      findChildClassSetWithName(classClassifier, "Class1")
        .instancesStream
        .filter { i -> i.valueType == ValueObject.ValueType.CLASS }
        .findFirst()
        .orElse(null)
    assertNotNull(class1ClassObject)
    verifyInstance(class1ClassObject!!, "Class1.class@102 (0x66)", Integer.MAX_VALUE, 0, 0)

    val field0 = instance0.fields[0]
    assertEquals(field0.asInstance, instance1)
    val reference1 = instance1.references[0]
    assertEquals(reference1.referenceInstance, instance0)
  }

  @Test
  @Throws(Exception::class)
  fun testDefaultHeapShowsUpWhenItIsNonEmpty() {
    val startTimeNs: Long = 3
    val endTimeNs: Long = 8
    val dumpInfo = HeapDumpInfo.newBuilder().setStartTime(startTimeNs).setEndTime(endTimeNs).build()
    val snapshotBuilder = SnapshotBuilder(2, 0, 0).addReferences(1, 2).setDefaultHeapInstanceCount(1).addRoot(1)
    val buffer = snapshotBuilder.byteBuffer
    val tempFile = TransportServiceUtils.createTempFile("temp_heap", ".hprof", ByteString.copyFrom(buffer))

    val capture = createCaptureObject(dumpInfo, tempFile)

    // Verify values associated with the HeapDumpInfo object.
    assertEquals(startTimeNs, capture.startTimeNs)
    assertEquals(endTimeNs, capture.endTimeNs)
    assertFalse(capture.isDoneLoading)
    assertFalse(capture.isError)

    // Load in a simple Snapshot and verify the MemoryObject hierarchy:
    // - 1 holds reference to 2
    // - single root object in default heap

    myTransportService.addFile(startTimeNs.toString(), tempFile.absolutePath)
    capture.load(null, null)

    assertTrue(capture.isDoneLoading)
    assertFalse(capture.isError)

    val heaps = capture.heapSets
    assertEquals(3, heaps.size.toLong())

    val defaultHeap = heaps.stream().filter { heap -> "default" == heap.name }.findFirst().orElse(null)
    assertNotNull(defaultHeap)
  }

  @Test
  @Throws(Exception::class)
  fun testLoadingFailure() {
    val dumpInfo = HeapDumpInfo.newBuilder().setStartTime(3).setEndTime(8).build()
    val dummyFile = File("/does/not/exist.hprof")
    val capture = createCaptureObject(dumpInfo, dummyFile)

    assertFalse(capture.isDoneLoading)
    assertFalse(capture.isError)
    capture.load(null, null)

    assertTrue(capture.isDoneLoading)
    assertTrue(capture.isError)
    assertEquals(0, capture.heapSets.size.toLong())
  }

  @Test
  @Throws(Exception::class)
  fun testHeapDumpActivityLeak() {
    val dumpInfo = HeapDumpInfo.newBuilder().setStartTime(0).setEndTime(1).build()

    val hprof = resolveWorkspacePath("tools/adt/idea/profilers/testData/hprofs/displayingbitmaps_leakedActivity.hprof")
    val realFile = hprof.toFile()

    val capture = createCaptureObject(dumpInfo, realFile)

    myTransportService.addFile("0", realFile.absolutePath)
    capture.load(null, null)
    assertTrue(capture.isDoneLoading)
    assertFalse(capture.isError)

    val allInstanceCount = capture.instances.count()
    Truth.assertThat(allInstanceCount).isGreaterThan(7L)
    val filters = capture.supportedIssueTypeFilters
    val leakFilter = filters.stream().filter { filter -> filter is ActivityFragmentLeakInstanceFilter }.findAny()
    Truth.assertThat(leakFilter.isPresent).isTrue()

    val addFilterLatch = CountDownLatch(1)
    capture.setIssueTypeFilter(leakFilter.get(), Executor { it.run() })
    // Wait for the filter to finish running on the off-main-thread executor.
    capture.instanceFilterExecutor.execute { addFilterLatch.countDown() }
    addFilterLatch.await()
    val filtredInstances = capture.instances.collect(Collectors.toList())
    Truth.assertThat(filtredInstances).hasSize(7)
    Truth.assertThat(filtredInstances.stream().filter { instance -> instance.classEntry.simpleClassName == "ImageDetailActivity" }.count())
      .isEqualTo(1)
    Truth.assertThat(
        filtredInstances.stream().filter { instance -> instance.classEntry.simpleClassName == "ImageCache\$RetainFragment" }.count()
      )
      .isEqualTo(1)
    Truth.assertThat(filtredInstances.stream().filter { instance -> instance.classEntry.simpleClassName == "ImageDetailFragment" }.count())
      .isEqualTo(5)

    val removeFilterLatch = CountDownLatch(1)
    capture.setIssueTypeFilter(null, Executor { it.run() })
    // Wait for the filter to finish running on the off-main-thread executor.
    capture.instanceFilterExecutor.execute { removeFilterLatch.countDown() }
    removeFilterLatch.await()
    Truth.assertThat(capture.instances.count()).isEqualTo(allInstanceCount)
  }

  /**
   * Validates that loading a heap dump via TraceProcessor correctly builds standard HeapSets ("All", "app", "image", "zygote"), maps
   * synthetic Class object overview entries to their represented classes, and computes ClassObject instances.
   */
  @Test
  fun testTraceProcessorLoadingAndHierarchyGeneration() {
    val testService = FakeTraceProcessorService()
    val capture = createTraceProcessorCaptureObject(testService)

    // Configure fake TraceProcessor response with a standard class and a synthetic Class<T> overview.
    val bitmapOverview =
      TraceProcessor.HeapDumpResult.ClassOverview.newBuilder()
        .setClassId(10L)
        .setClassName("android.graphics.Bitmap")
        .setHeapName("app")
        .setInstanceCount(5)
        .setShallowSize(250)
        .setRetainedSize(1000)
        .setNativeSize(500)
        .setRetainedNativeSize(2500)
        .build()

    val classObjectOverview =
      TraceProcessor.HeapDumpResult.ClassOverview.newBuilder()
        .setClassId(11L)
        .setClassName("java.lang.Class<android.graphics.Bitmap>")
        .setHeapName("app")
        .setInstanceCount(1)
        .setShallowSize(64)
        .setRetainedSize(64)
        .build()

    testService.javaHeapDumpResult =
      TraceProcessor.HeapDumpResult.newBuilder().addClassOverview(bitmapOverview).addClassOverview(classObjectOverview).build()

    val classObjectInstData =
      TraceProcessor.HeapDumpInstancesResult.InstanceData.newBuilder().setId(500L).setTypeId(11L).setHeapName("app").build()
    testService.instancesResult = TraceProcessor.HeapDumpInstancesResult.newBuilder().addInstance(classObjectInstData).build()

    // Load capture object and verify standard heap sets and native allocation flags.
    capture.load(null, null)

    Truth.assertThat(capture.isDoneLoading).isTrue()
    Truth.assertThat(capture.isError).isFalse()
    Truth.assertThat(capture.isTraceProcessor).isTrue()
    Truth.assertThat(capture.heapSets.map { it.name }).containsAllOf("All", "app", "image", "zygote")
    Truth.assertThat(capture.hasNativeAllocations).isTrue()
    Truth.assertThat(capture.hasRetainedNativeAllocations).isTrue()

    // Verify synthetic Class<T> overview maps back to the represented class in syntheticToRepresentedClassMap.
    Truth.assertThat(capture.syntheticToRepresentedClassMap[11L]).isEqualTo(10L)

    // Verify ClassObject instance generation and its properties.
    val classObjectInstances = capture.getClassObjectInstances(10L)
    Truth.assertThat(classObjectInstances).hasSize(1)
    Truth.assertThat(capture.getRepresentedClassId(500L)).isEqualTo(10L)
    val classObj = classObjectInstances[0]
    Truth.assertThat(classObj.valueType).isEqualTo(ValueObject.ValueType.CLASS)
    Truth.assertThat(classObj.shallowSize).isEqualTo(0)
    Truth.assertThat(classObj.retainedSize).isEqualTo(0L)
  }

  /**
   * Verifies that batch-loading instances by ID caches objects in the instance index, and that primitive field bulk requests correctly map
   * back to the requested instance IDs.
   */
  @Test
  fun testBatchInstanceAndPrimitiveFieldResolution() {
    val testService = FakeTraceProcessorService()
    val capture = createTraceProcessorCaptureObject(testService)

    // Populate fake service with class overview and batch instance data.
    val bitmapOverview =
      TraceProcessor.HeapDumpResult.ClassOverview.newBuilder()
        .setClassId(10L)
        .setClassName("android.graphics.Bitmap")
        .setHeapName("app")
        .setInstanceCount(2)
        .build()

    testService.javaHeapDumpResult = TraceProcessor.HeapDumpResult.newBuilder().addClassOverview(bitmapOverview).build()

    capture.load(null, null)

    val inst100 = TraceProcessor.HeapDumpInstancesResult.InstanceData.newBuilder().setId(100L).setTypeId(10L).setHeapName("app").build()
    val inst101 = TraceProcessor.HeapDumpInstancesResult.InstanceData.newBuilder().setId(101L).setTypeId(10L).setHeapName("app").build()

    testService.instancesResult = TraceProcessor.HeapDumpInstancesResult.newBuilder().addInstance(inst100).addInstance(inst101).build()

    // Verify batch ID lookup and instance caching in instanceIndex.
    val instances = capture.getInstancesByIds(listOf(100L, 101L))
    Truth.assertThat(instances).hasSize(2)
    Truth.assertThat(instances.map { it.id }).containsExactly(100L, 101L)

    val first = capture.findInstanceObjectById(100L)
    Truth.assertThat(first).isNotNull()
    val second = capture.findInstanceObjectByIdCached(100L)
    Truth.assertThat(second === first).isTrue()

    // Verify bulk primitive field retrieval matches requested instance IDs.
    val field100 = TraceProcessor.GetPrimitiveFieldsResult.InstancePrimitiveFields.newBuilder().setInstanceId(100L).build()
    val field101 = TraceProcessor.GetPrimitiveFieldsResult.InstancePrimitiveFields.newBuilder().setInstanceId(101L).build()
    testService.primitiveFieldsResult =
      TraceProcessor.GetPrimitiveFieldsResult.newBuilder().addInstances(field100).addInstances(field101).build()

    val bulkFields = capture.getPrimitiveFieldsBulk(listOf(100L, 101L))
    Truth.assertThat(bulkFields.instancesList).hasSize(2)
    Truth.assertThat(bulkFields.instancesList.map { it.instanceId }).containsExactly(100L, 101L)
  }

  /**
   * Tests forward and reverse reference prefetching via TraceProcessor, verifying that reference relationships between owner and owned
   * objects are properly established and cached.
   */
  @Test
  fun testReferencePrefetchingAndReverseChainResolution() {
    val testService = FakeTraceProcessorService()
    val capture = createTraceProcessorCaptureObject(testService)

    // Populate fake service with forward/reverse reference relationships.
    val bitmapOverview =
      TraceProcessor.HeapDumpResult.ClassOverview.newBuilder()
        .setClassId(10L)
        .setClassName("android.graphics.Bitmap")
        .setHeapName("app")
        .setInstanceCount(2)
        .build()

    testService.javaHeapDumpResult = TraceProcessor.HeapDumpResult.newBuilder().addClassOverview(bitmapOverview).build()

    capture.load(null, null)

    val ownerData =
      TraceProcessor.HeapDumpInstancesResult.InstanceData.newBuilder()
        .setId(200L)
        .setTypeId(10L)
        .setHeapName("app")
        .setHasReferences(true)
        .build()
    val ownedData =
      TraceProcessor.HeapDumpInstancesResult.InstanceData.newBuilder()
        .setId(300L)
        .setTypeId(10L)
        .setHeapName("app")
        .setHasReferences(true)
        .build()

    testService.instancesResult = TraceProcessor.HeapDumpInstancesResult.newBuilder().addInstance(ownerData).addInstance(ownedData).build()

    // Retrieve instances by index to ensure distinct owner (200L) and owned (300L) objects.
    val instances = capture.getInstancesByIds(listOf(200L, 300L))
    val cls = capture.classDb.getEntry(10L)
    val owner = capture.getOrCreateTraceProcessorHeapDumpInstance(cls, instances[0])
    val owned = capture.getOrCreateTraceProcessorHeapDumpInstance(cls, instances[1])

    val refData =
      TraceProcessor.GetReferencesResult.ReferenceData.newBuilder().setOwnerId(200L).setOwnedId(300L).setFieldName("mChild").build()

    testService.referencesResult = TraceProcessor.GetReferencesResult.newBuilder().addReference(refData).build()

    // Prefetch reference chains and verify forward/reverse link establishment.
    capture.prefetchReferences(listOf(owner))
    capture.prefetchReverseReferences(listOf(owned))

    Truth.assertThat(owned.references).hasSize(1)
    Truth.assertThat(owned.references[0].referenceInstance).isEqualTo(owner)
    Truth.assertThat(owned.references[0].referenceFieldNames).containsExactly("mChild")

    val bulkRefs = capture.getReferencesBulk(listOf(200L, 300L), fetchForward = true, fetchReverse = true)
    Truth.assertThat(bulkRefs.referenceList).hasSize(1)
  }

  /**
   * Validates issue type filtering in TraceProcessor mode, verifying that duplicate bitmap instances are identified and correctly filtered
   * when an issue type filter is applied.
   */
  @Test
  fun testIssueTypeFilteringWithTraceProcessor() {
    val testService = FakeTraceProcessorService()
    val capture = createTraceProcessorCaptureObject(testService)

    // Configure fake service with instances for classes.
    val bitmapOverview =
      TraceProcessor.HeapDumpResult.ClassOverview.newBuilder()
        .setClassId(10L)
        .setClassName("android.graphics.Bitmap")
        .setHeapName("app")
        .setInstanceCount(1)
        .build()

    testService.javaHeapDumpResult = TraceProcessor.HeapDumpResult.newBuilder().addClassOverview(bitmapOverview).build()

    val instData = TraceProcessor.HeapDumpInstancesResult.InstanceData.newBuilder().setId(400L).setTypeId(10L).setHeapName("app").build()

    testService.instancesForClassesResult = TraceProcessor.HeapDumpInstancesResult.newBuilder().addInstance(instData).build()

    capture.load(null, null)

    // Mark Bitmap class as having duplicates and verify supported filters.
    capture.classesWithDuplicates.add("android.graphics.Bitmap")

    val supportedFilters = capture.supportedIssueTypeFilters
    Truth.assertThat(supportedFilters.any { it is BitmapDuplicationInstanceFilter }).isTrue()

    // Apply BitmapDuplicationInstanceFilter and verify duplicate issue detection.
    val filter = BitmapDuplicationInstanceFilter(capture.filterInstances)
    capture.issueTypeFilter = filter

    val issueInstances = capture.getIssueInstances(filter).toList()
    Truth.assertThat(issueInstances).hasSize(1)
    Truth.assertThat((issueInstances[0] as TraceProcessorHeapDumpInstanceObject).instanceId).isEqualTo(400L)
  }

  /**
   * Verifies that Perfetto Java heap dump files (".perfetto-java-heap-dump") are loaded via TraceProcessor and properly initialize issue
   * filters (such as activityFragmentLeakFilter).
   */
  @Test
  fun testTraceProcessorLoadingWithPerfettoJavaHeapDumpExtension() {
    val testService = FakeTraceProcessorService()

    // Create temporary file with .perfetto-java-heap-dump extension.
    val file =
      TransportServiceUtils.createTempFile("temp_tp_heap", ".perfetto-java-heap-dump", ByteString.copyFrom(byteArrayOf(1, 2, 3, 4)))
    val services = FakeIdeProfilerServices()
    services.setHeapDumpTraceInEditorEnabled(true)
    testService.forceSucceedLoadTrace = true
    services.setTraceProcessorService(testService)
    val dumpInfo = HeapDumpInfo.newBuilder().setStartTime(1000L).setEndTime(2000L).build()
    val capture = createCaptureObject(dumpInfo, file, ideServices = services, useTraceProcessor = true)

    // Verify TraceProcessor loading and ensure activityFragmentLeakFilter is initialized.
    capture.load(null, null)

    Truth.assertThat(capture.isDoneLoading).isTrue()
    Truth.assertThat(capture.isError).isFalse()
    Truth.assertThat(capture.isTraceProcessor).isTrue()
    Truth.assertThat(capture.supportedIssueTypeFilters).isNotEmpty()
    Truth.assertThat(capture.activityFragmentLeakFilter).isNotNull()
  }

  companion object {
    private fun verifyInstance(instance: InstanceObject, valueText: String, depth: Int, fieldsCount: Int, referencesCount: Int) {
      assertEquals(valueText, instance.valueText)
      assertEquals(depth.toLong(), instance.depth.toLong())
      assertEquals(fieldsCount.toLong(), instance.fields.size.toLong())
      assertEquals(referencesCount.toLong(), instance.references.size.toLong())
    }
  }
}
