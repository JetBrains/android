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
import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.filter.Filter
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.profiler.proto.Common
import com.android.tools.profiler.proto.Memory
import com.android.tools.profiler.proto.Memory.MemoryAllocSamplingData
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.ProfilersTestData
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.memory.BaseStreamingMemoryProfilerStage
import com.android.tools.profilers.memory.CaptureSelectionAspect
import com.android.tools.profilers.memory.ClassGrouping
import com.android.tools.profilers.memory.MainMemoryProfilerStage
import com.android.tools.profilers.memory.adapters.CaptureObject.DEFAULT_HEAP_ID
import com.android.tools.profilers.memory.adapters.CaptureObject.DEFAULT_HEAP_NAME
import com.android.tools.profilers.memory.adapters.CaptureObject.JNI_HEAP_ID
import com.android.tools.profilers.memory.adapters.CaptureObject.JNI_HEAP_NAME
import com.android.tools.profilers.memory.adapters.classifiers.ClassifierSet
import com.android.tools.profilers.stacktrace.NativeFrameSymbolizer
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import java.util.LinkedList
import java.util.Queue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameter
import org.junit.runners.Parameterized.Parameters

open class LiveAllocationCaptureObjectTest {

  protected val myTimer = FakeTimer()
  protected val myTransportService = FakeTransportService(myTimer)

  @get:Rule val myGrpcChannel = FakeGrpcChannel("LiveAllocationCaptureObjectTest", myTransportService)

  protected val loadService: ExecutorService = MoreExecutors.newDirectExecutorService()
  protected val loadJoiner: Executor = MoreExecutors.directExecutor()

  protected lateinit var myStage: MainMemoryProfilerStage
  protected val myAspectObserver = AspectObserver()
  protected lateinit var myIdeProfilerServices: FakeIdeProfilerServices

  open fun before() {
    myIdeProfilerServices = FakeIdeProfilerServices()
    myIdeProfilerServices.setNativeFrameSymbolizer(FAKE_SYMBOLIZER)
    myStage = MainMemoryProfilerStage(StudioProfilers(ProfilerClient(myGrpcChannel.channel), myIdeProfilerServices, myTimer))

    val dataStartTime = CAPTURE_START_TIME
    val dataEndTime = TimeUnit.SECONDS.toNanos(8)
    val contexts = ProfilersTestData.generateMemoryAllocContext(dataStartTime, dataEndTime)
    val allocEvents = ProfilersTestData.generateMemoryAllocEvents(dataStartTime, dataEndTime)
    val jniEvents = ProfilersTestData.generateMemoryJniRefEvents(dataStartTime, dataEndTime)

    contexts.forEach { context ->
      myTransportService.addEventToStream(
        ProfilersTestData.SESSION_DATA.streamId,
        Common.Event.newBuilder()
          .setPid(ProfilersTestData.SESSION_DATA.pid)
          .setKind(Common.Event.Kind.MEMORY_ALLOC_CONTEXTS)
          .setTimestamp(context.timestamp)
          .setMemoryAllocContexts(Memory.MemoryAllocContextsData.newBuilder().setContexts(context))
          .build(),
      )
    }

    allocEvents.forEach { events ->
      myTransportService.addEventToStream(
        ProfilersTestData.SESSION_DATA.streamId,
        Common.Event.newBuilder()
          .setPid(ProfilersTestData.SESSION_DATA.pid)
          .setKind(Common.Event.Kind.MEMORY_ALLOC_EVENTS)
          .setTimestamp(events.timestamp)
          .setMemoryAllocEvents(Memory.MemoryAllocEventsData.newBuilder().setEvents(events))
          .build(),
      )
    }

    jniEvents.forEach { jniRefs ->
      myTransportService.addEventToStream(
        ProfilersTestData.SESSION_DATA.streamId,
        Common.Event.newBuilder()
          .setPid(ProfilersTestData.SESSION_DATA.pid)
          .setKind(Common.Event.Kind.MEMORY_JNI_REF_EVENTS)
          .setTimestamp(jniRefs.timestamp)
          .setMemoryJniRefEvents(Memory.MemoryJniRefData.newBuilder().setEvents(jniRefs))
          .build(),
      )
    }
  }

  @RunWith(Parameterized::class)
  class AllHeapsTests : LiveAllocationCaptureObjectTest() {

    @Parameter(0) @JvmField var myHeapId: Int = 0

    @Parameter(1) @JvmField var myHeapName: String = ""

    private lateinit var myProfilerClient: ProfilerClient

    @Before
    override fun before() {
      super.before()
      myProfilerClient = ProfilerClient(myGrpcChannel.channel)
    }

    // Simple test to check that we get the correct delta + total data.
    @Test
    fun testBasicDataLoad() {
      // Flag that gets set on the joiner thread to notify the main thread whether the contents in the ChangeNode are accurate.
      var loadSuccess = false
      val capture =
        LiveAllocationCaptureObject(
          myProfilerClient,
          ProfilersTestData.SESSION_DATA,
          CAPTURE_START_TIME,
          loadService,
          myStage,
        )

      // Heap set should start out empty.
      val heapSet = capture.getHeapSet(myHeapId)!!
      assertThat(heapSet.childrenClassifierSets).isEmpty()
      heapSet.classGrouping = ClassGrouping.ARRANGE_BY_PACKAGE

      // Listens to the aspect change when load is called, then check the content of the changedNode parameter
      myStage.captureSelection.aspect.addDependency(myAspectObserver).onChange(CaptureSelectionAspect.CURRENT_HEAP_CONTENTS) {
        loadSuccess = true
      }

      val expected0to4: Queue<ClassifierSetTestData> = LinkedList()
      expected0to4.add(ClassifierSetTestData(0, myHeapName, 4, 2, 2, 4, 2, true))
      expected0to4.add(ClassifierSetTestData(1, "This", 2, 1, 1, 2, 2, true))
      expected0to4.add(ClassifierSetTestData(2, "Is", 1, 1, 0, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(3, "Foo", 1, 1, 0, 1, 0, true))
      expected0to4.add(ClassifierSetTestData(2, "Also", 1, 0, 1, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(3, "Foo", 1, 0, 1, 1, 0, true))
      expected0to4.add(ClassifierSetTestData(1, "That", 2, 1, 1, 2, 2, true))
      expected0to4.add(ClassifierSetTestData(2, "Is", 1, 1, 0, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(3, "Bar", 1, 1, 0, 1, 0, true))
      expected0to4.add(ClassifierSetTestData(2, "Also", 1, 0, 1, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(3, "Bar", 1, 0, 1, 1, 0, true))

      val loadRange = Range(CAPTURE_START_TIME.toDouble(), (CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(4)).toDouble())
      loadSuccess = false
      capture.load(loadRange, loadJoiner)
      assertThat(loadSuccess).isTrue()
      verifyClassifierResult(heapSet, expected0to4, 0)
    }

    // This test checks that optimization by canceling outstanding queries works properly.
    @Test
    fun testUnstartedSelectionEventsCancelled() {
      // Flag that gets set on the joiner thread to notify the main thread whether the contents in the ChangeNode are accurate.
      var loadSuccess = false
      val capture =
        LiveAllocationCaptureObject(
          myProfilerClient,
          ProfilersTestData.SESSION_DATA,
          CAPTURE_START_TIME,
          null,
          myStage,
        )

      // Heap set should start out empty.
      val heapSet = capture.getHeapSet(myHeapId)!!
      assertThat(heapSet.childrenClassifierSets).isEmpty()
      heapSet.classGrouping = ClassGrouping.ARRANGE_BY_PACKAGE

      val expected0to4: Queue<ClassifierSetTestData> = LinkedList()
      expected0to4.add(ClassifierSetTestData(0, myHeapName, 4, 2, 2, 4, 2, true))
      expected0to4.add(ClassifierSetTestData(1, "This", 2, 1, 1, 2, 2, true))
      expected0to4.add(ClassifierSetTestData(2, "Is", 1, 1, 0, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(3, "Foo", 1, 1, 0, 1, 0, true))
      expected0to4.add(ClassifierSetTestData(2, "Also", 1, 0, 1, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(3, "Foo", 1, 0, 1, 1, 0, true))
      expected0to4.add(ClassifierSetTestData(1, "That", 2, 1, 1, 2, 2, true))
      expected0to4.add(ClassifierSetTestData(2, "Is", 1, 1, 0, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(3, "Bar", 1, 1, 0, 1, 0, true))
      expected0to4.add(ClassifierSetTestData(2, "Also", 1, 0, 1, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(3, "Bar", 1, 0, 1, 1, 0, true))

      val loadRange = Range(CAPTURE_START_TIME.toDouble(), (CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(4)).toDouble())
      capture.load(loadRange, loadJoiner)
      waitForLoadComplete(capture)
      verifyClassifierResult(heapSet, expected0to4, 0)

      val expected0to8: Queue<ClassifierSetTestData> = LinkedList()
      expected0to8.add(ClassifierSetTestData(0, myHeapName, 8, 6, 2, 8, 2, true))
      expected0to8.add(ClassifierSetTestData(1, "This", 4, 3, 1, 4, 2, true))
      expected0to8.add(ClassifierSetTestData(2, "Is", 2, 2, 0, 2, 1, true))
      expected0to8.add(ClassifierSetTestData(3, "Foo", 2, 2, 0, 2, 0, true))
      expected0to8.add(ClassifierSetTestData(2, "Also", 2, 1, 1, 2, 1, true))
      expected0to8.add(ClassifierSetTestData(3, "Foo", 2, 1, 1, 2, 0, true))
      expected0to8.add(ClassifierSetTestData(1, "That", 4, 3, 1, 4, 2, true))
      expected0to8.add(ClassifierSetTestData(2, "Is", 2, 2, 0, 2, 1, true))
      expected0to8.add(ClassifierSetTestData(3, "Bar", 2, 2, 0, 2, 0, true))
      expected0to8.add(ClassifierSetTestData(2, "Also", 2, 1, 1, 2, 1, true))
      expected0to8.add(ClassifierSetTestData(3, "Bar", 2, 1, 1, 2, 0, true))

      // Listens to the aspect change when load is called, then check the content of the changedNode parameter
      var myHeapChangedCount = 0
      myStage.captureSelection.aspect.addDependency(myAspectObserver).onChange(CaptureSelectionAspect.CURRENT_HEAP_CONTENTS) {
        // We should not receive more than one heapChanged event.
        assertThat(myHeapChangedCount++).isEqualTo(0)
        loadSuccess = true
      }

      // Adds a task that starts and blocks. This forces the subsequent selection change events to wait.
      val latch = CountDownLatch(1)
      checkNotNull(capture.executorService).execute {
        try {
          latch.await()
        } catch (ignored: Exception) {}
      }

      // Fake 4 selection range changes that would be cancelled.
      // We should only get the very last selection change event. e.g. {CAPTURE_START_TIME, CAPTURE_START_TIME + 8 secs}
      for (k in 0 until 4) {
        loadRange.set(CAPTURE_START_TIME.toDouble(), loadRange.max + TimeUnit.SECONDS.toMicros(1))
      }
      loadSuccess = false

      // unblocks our fake task, now only the last selection set should trigger the load.
      latch.countDown()
      waitForLoadComplete(capture)
      assertThat(loadSuccess).isTrue()
      verifyClassifierResult(heapSet, LinkedList(expected0to8), 0)
    }

    @Test
    fun testSelectionWithFilter() {
      // Flag that gets set on the joiner thread to notify the main thread whether the contents in the ChangeNode are accurate.
      var loadSuccess = false
      val capture =
        LiveAllocationCaptureObject(
          myProfilerClient,
          ProfilersTestData.SESSION_DATA,
          CAPTURE_START_TIME,
          loadService,
          myStage,
        )

      // Heap set should start out empty.
      val heapSet = capture.getHeapSet(myHeapId)!!
      assertThat(heapSet.childrenClassifierSets).isEmpty()
      heapSet.classGrouping = ClassGrouping.ARRANGE_BY_PACKAGE
      myStage.captureSelection.aspect.addDependency(myAspectObserver).onChange(CaptureSelectionAspect.CURRENT_HEAP_CONTENTS) {
        loadSuccess = true
      }

      val loadRange = Range(CAPTURE_START_TIME.toDouble(), (CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(4)).toDouble())
      loadSuccess = false
      capture.load(loadRange, loadJoiner)

      // Filter with "Foo"
      var expected0to4: Queue<ClassifierSetTestData> = LinkedList()
      expected0to4.add(ClassifierSetTestData(0, myHeapName, 2, 1, 1, 4, 1, true))
      expected0to4.add(ClassifierSetTestData(1, "This", 2, 1, 1, 2, 2, true))
      expected0to4.add(ClassifierSetTestData(2, "Is", 1, 1, 0, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(3, "Foo", 1, 1, 0, 1, 0, true))
      expected0to4.add(ClassifierSetTestData(2, "Also", 1, 0, 1, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(3, "Foo", 1, 0, 1, 1, 0, true))
      heapSet.selectFilter(Filter("Foo", true, false))
      assertThat(loadSuccess).isTrue()
      assertThat(heapSet.filterMatchCount).isEqualTo(2)
      verifyClassifierResult(heapSet, LinkedList(expected0to4), 0)

      // Filter with "Bar"
      heapSet.selectFilter(Filter("bar"))
      expected0to4 = LinkedList()
      expected0to4.add(ClassifierSetTestData(0, myHeapName, 2, 1, 1, 4, 1, true))
      expected0to4.add(ClassifierSetTestData(1, "That", 2, 1, 1, 2, 2, true))
      expected0to4.add(ClassifierSetTestData(2, "Is", 1, 1, 0, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(3, "Bar", 1, 1, 0, 1, 0, true))
      expected0to4.add(ClassifierSetTestData(2, "Also", 1, 0, 1, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(3, "Bar", 1, 0, 1, 1, 0, true))
      assertThat(heapSet.filterMatchCount).isEqualTo(2)
      verifyClassifierResult(heapSet, LinkedList(expected0to4), 0)

      // filter with package name and regex
      heapSet.selectFilter(Filter("T[a-z]is", false, true))
      expected0to4 = LinkedList()
      expected0to4.add(ClassifierSetTestData(0, myHeapName, 2, 1, 1, 4, 1, true))
      expected0to4.add(ClassifierSetTestData(1, "This", 2, 1, 1, 2, 2, true))
      expected0to4.add(ClassifierSetTestData(2, "Is", 1, 1, 0, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(3, "Foo", 1, 1, 0, 1, 0, true))
      expected0to4.add(ClassifierSetTestData(2, "Also", 1, 0, 1, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(3, "Foo", 1, 0, 1, 1, 0, true))
      assertThat(heapSet.filterMatchCount).isEqualTo(3)
      verifyClassifierResult(heapSet, LinkedList(expected0to4), 0)

      // Reset filter
      heapSet.selectFilter(Filter.EMPTY_FILTER)
      expected0to4 = LinkedList()
      expected0to4.add(ClassifierSetTestData(0, myHeapName, 4, 2, 2, 4, 2, true))
      expected0to4.add(ClassifierSetTestData(1, "This", 2, 1, 1, 2, 2, true))
      expected0to4.add(ClassifierSetTestData(2, "Is", 1, 1, 0, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(3, "Foo", 1, 1, 0, 1, 0, true))
      expected0to4.add(ClassifierSetTestData(2, "Also", 1, 0, 1, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(3, "Foo", 1, 0, 1, 1, 0, true))
      expected0to4.add(ClassifierSetTestData(1, "That", 2, 1, 1, 2, 2, true))
      expected0to4.add(ClassifierSetTestData(2, "Is", 1, 1, 0, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(3, "Bar", 1, 1, 0, 1, 0, true))
      expected0to4.add(ClassifierSetTestData(2, "Also", 1, 0, 1, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(3, "Bar", 1, 0, 1, 1, 0, true))
      verifyClassifierResult(heapSet, LinkedList(expected0to4), 0)
    }

    @Test
    fun testSelectionMinChanges() {
      // Flag that gets set on the joiner thread to notify the main thread whether the contents in the ChangeNode are accurate.
      var loadSuccess = false
      val capture =
        LiveAllocationCaptureObject(
          myProfilerClient,
          ProfilersTestData.SESSION_DATA,
          CAPTURE_START_TIME,
          loadService,
          myStage,
        )

      // Heap set should start out empty.
      val heapSet = capture.getHeapSet(myHeapId)!!
      assertThat(heapSet.childrenClassifierSets).isEmpty()
      heapSet.classGrouping = ClassGrouping.ARRANGE_BY_PACKAGE

      myStage.captureSelection.aspect.addDependency(myAspectObserver).onChange(CaptureSelectionAspect.CURRENT_HEAP_CONTENTS) {
        loadSuccess = true
      }

      val expected0to4: Queue<ClassifierSetTestData> = LinkedList()
      expected0to4.add(ClassifierSetTestData(0, myHeapName, 4, 2, 2, 4, 2, true))
      expected0to4.add(ClassifierSetTestData(1, "This", 2, 1, 1, 2, 2, true))
      expected0to4.add(ClassifierSetTestData(2, "Is", 1, 1, 0, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(3, "Foo", 1, 1, 0, 1, 0, true))
      expected0to4.add(ClassifierSetTestData(2, "Also", 1, 0, 1, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(3, "Foo", 1, 0, 1, 1, 0, true))
      expected0to4.add(ClassifierSetTestData(1, "That", 2, 1, 1, 2, 2, true))
      expected0to4.add(ClassifierSetTestData(2, "Is", 1, 1, 0, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(3, "Bar", 1, 1, 0, 1, 0, true))
      expected0to4.add(ClassifierSetTestData(2, "Also", 1, 0, 1, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(3, "Bar", 1, 0, 1, 1, 0, true))
      val loadRange = Range(CAPTURE_START_TIME.toDouble(), (CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(4)).toDouble())
      loadSuccess = false
      capture.load(loadRange, loadJoiner)
      assertThat(loadSuccess).isTrue()
      verifyClassifierResult(heapSet, expected0to4, 0)

      val expected2to4: Queue<ClassifierSetTestData> = LinkedList()
      expected2to4.add(ClassifierSetTestData(0, myHeapName, 2, 2, 2, 4, 2, true))
      expected2to4.add(ClassifierSetTestData(1, "This", 1, 1, 1, 2, 2, true))
      expected2to4.add(ClassifierSetTestData(2, "Is", 0, 1, 0, 1, 1, true))
      expected2to4.add(ClassifierSetTestData(3, "Foo", 0, 1, 0, 1, 0, true))
      expected2to4.add(ClassifierSetTestData(2, "Also", 1, 0, 1, 1, 1, true))
      expected2to4.add(ClassifierSetTestData(3, "Foo", 1, 0, 1, 1, 0, true))
      expected2to4.add(ClassifierSetTestData(1, "That", 1, 1, 1, 2, 2, true))
      expected2to4.add(ClassifierSetTestData(2, "Is", 0, 1, 0, 1, 1, true))
      expected2to4.add(ClassifierSetTestData(3, "Bar", 0, 1, 0, 1, 0, true))
      expected2to4.add(ClassifierSetTestData(2, "Also", 1, 0, 1, 1, 1, true))
      expected2to4.add(ClassifierSetTestData(3, "Bar", 1, 0, 1, 1, 0, true))

      // Shrink selection to {2,4}
      loadSuccess = false
      loadRange.min = (CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(2)).toDouble()
      assertThat(loadSuccess).isTrue()
      verifyClassifierResult(heapSet, LinkedList(expected2to4), 0)

      // Shrink selection to {4,4}
      val expected4to4: Queue<ClassifierSetTestData> = LinkedList()
      expected4to4.add(ClassifierSetTestData(0, myHeapName, 0, 0, 2, 2, 2, true))
      expected4to4.add(ClassifierSetTestData(1, "This", 0, 0, 1, 1, 1, true))
      expected4to4.add(ClassifierSetTestData(2, "Also", 0, 0, 1, 1, 1, true))
      expected4to4.add(ClassifierSetTestData(3, "Foo", 0, 0, 1, 1, 0, true))
      expected4to4.add(ClassifierSetTestData(1, "That", 0, 0, 1, 1, 1, true))
      expected4to4.add(ClassifierSetTestData(2, "Also", 0, 0, 1, 1, 1, true))
      expected4to4.add(ClassifierSetTestData(3, "Bar", 0, 0, 1, 1, 0, true))
      loadSuccess = false
      loadRange.min = (CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(4)).toDouble()
      assertThat(loadSuccess).isTrue()
      verifyClassifierResult(heapSet, expected4to4, 0)

      // Restore selection back to {2,4}
      loadSuccess = false
      loadRange.min = (CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(2)).toDouble()
      assertThat(loadSuccess).isTrue()
      verifyClassifierResult(heapSet, LinkedList(expected2to4), 0)
    }

    @Test
    fun testSelectionMaxChanges() {
      // Flag that gets set on the joiner thread to notify the main thread whether the contents in the ChangeNode are accurate.
      var loadSuccess = false
      val capture =
        LiveAllocationCaptureObject(
          myProfilerClient,
          ProfilersTestData.SESSION_DATA,
          CAPTURE_START_TIME,
          loadService,
          myStage,
        )

      // Heap set should start out empty.
      val heapSet = capture.getHeapSet(myHeapId)!!
      assertThat(heapSet.childrenClassifierSets).isEmpty()
      heapSet.classGrouping = ClassGrouping.ARRANGE_BY_PACKAGE

      myStage.captureSelection.aspect.addDependency(myAspectObserver).onChange(CaptureSelectionAspect.CURRENT_HEAP_CONTENTS) {
        loadSuccess = true
      }

      val expected0to4: Queue<ClassifierSetTestData> = LinkedList()
      expected0to4.add(ClassifierSetTestData(0, myHeapName, 4, 2, 2, 4, 2, true))
      expected0to4.add(ClassifierSetTestData(1, "This", 2, 1, 1, 2, 2, true))
      expected0to4.add(ClassifierSetTestData(2, "Is", 1, 1, 0, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(3, "Foo", 1, 1, 0, 1, 0, true))
      expected0to4.add(ClassifierSetTestData(2, "Also", 1, 0, 1, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(3, "Foo", 1, 0, 1, 1, 0, true))
      expected0to4.add(ClassifierSetTestData(1, "That", 2, 1, 1, 2, 2, true))
      expected0to4.add(ClassifierSetTestData(2, "Is", 1, 1, 0, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(3, "Bar", 1, 1, 0, 1, 0, true))
      expected0to4.add(ClassifierSetTestData(2, "Also", 1, 0, 1, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(3, "Bar", 1, 0, 1, 1, 0, true))
      val loadRange = Range(CAPTURE_START_TIME.toDouble(), (CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(4)).toDouble())
      loadSuccess = false
      capture.load(loadRange, loadJoiner)
      assertThat(loadSuccess).isTrue()
      verifyClassifierResult(heapSet, expected0to4, 0)

      val expected0to2: Queue<ClassifierSetTestData> = LinkedList()
      expected0to2.add(ClassifierSetTestData(0, myHeapName, 2, 0, 2, 2, 2, true))
      expected0to2.add(ClassifierSetTestData(1, "This", 1, 0, 1, 1, 1, true))
      expected0to2.add(ClassifierSetTestData(2, "Is", 1, 0, 1, 1, 1, true))
      expected0to2.add(ClassifierSetTestData(3, "Foo", 1, 0, 1, 1, 0, true))
      expected0to2.add(ClassifierSetTestData(1, "That", 1, 0, 1, 1, 1, true))
      expected0to2.add(ClassifierSetTestData(2, "Is", 1, 0, 1, 1, 1, true))
      expected0to2.add(ClassifierSetTestData(3, "Bar", 1, 0, 1, 1, 0, true))

      // Shrink selection to {0, 2}
      loadSuccess = false
      loadRange.max = (CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(2)).toDouble()
      assertThat(loadSuccess).isTrue()
      verifyClassifierResult(heapSet, LinkedList(expected0to2), 0)

      // Shrink selection to {0,0}
      val expected0to0: Queue<ClassifierSetTestData> = LinkedList()
      expected0to0.add(ClassifierSetTestData(0, myHeapName, 0, 0, 0, 0, 0, false))
      loadSuccess = false
      loadRange.max = CAPTURE_START_TIME.toDouble()
      assertThat(loadSuccess).isTrue()
      verifyClassifierResult(heapSet, expected0to0, 0)

      // Restore selection back to {0, 2}
      loadSuccess = false
      loadRange.max = (CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(2)).toDouble()
      assertThat(loadSuccess).isTrue()
      verifyClassifierResult(heapSet, LinkedList(expected0to2), 0)
    }

    @Test
    fun testSelectionShift() {
      // Flag that gets set on the joiner thread to notify the main thread whether the contents in the ChangeNode are accurate.
      var loadSuccess = false
      val capture =
        LiveAllocationCaptureObject(
          myProfilerClient,
          ProfilersTestData.SESSION_DATA,
          CAPTURE_START_TIME,
          loadService,
          myStage,
        )

      // Heap set should start out empty.
      val heapSet = capture.getHeapSet(myHeapId)!!
      assertThat(heapSet.childrenClassifierSets).isEmpty()
      heapSet.classGrouping = ClassGrouping.ARRANGE_BY_PACKAGE

      myStage.captureSelection.aspect.addDependency(myAspectObserver).onChange(CaptureSelectionAspect.CURRENT_HEAP_CONTENTS) {
        loadSuccess = true
      }

      val expected0to4: Queue<ClassifierSetTestData> = LinkedList()
      expected0to4.add(ClassifierSetTestData(0, myHeapName, 4, 2, 2, 4, 2, true))
      expected0to4.add(ClassifierSetTestData(1, "This", 2, 1, 1, 2, 2, true))
      expected0to4.add(ClassifierSetTestData(2, "Is", 1, 1, 0, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(3, "Foo", 1, 1, 0, 1, 0, true))
      expected0to4.add(ClassifierSetTestData(2, "Also", 1, 0, 1, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(3, "Foo", 1, 0, 1, 1, 0, true))
      expected0to4.add(ClassifierSetTestData(1, "That", 2, 1, 1, 2, 2, true))
      expected0to4.add(ClassifierSetTestData(2, "Is", 1, 1, 0, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(3, "Bar", 1, 1, 0, 1, 0, true))
      expected0to4.add(ClassifierSetTestData(2, "Also", 1, 0, 1, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(3, "Bar", 1, 0, 1, 1, 0, true))
      val loadRange = Range(CAPTURE_START_TIME.toDouble(), (CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(4)).toDouble())
      loadSuccess = false
      capture.load(loadRange, loadJoiner)
      assertThat(loadSuccess).isTrue()
      verifyClassifierResult(heapSet, LinkedList(expected0to4), 0)

      val expected4to8: Queue<ClassifierSetTestData> = LinkedList()
      expected4to8.add(ClassifierSetTestData(0, myHeapName, 4, 4, 2, 6, 2, true))
      expected4to8.add(ClassifierSetTestData(1, "This", 2, 2, 1, 3, 2, true))
      expected4to8.add(ClassifierSetTestData(2, "Also", 1, 1, 1, 2, 1, true))
      expected4to8.add(ClassifierSetTestData(3, "Foo", 1, 1, 1, 2, 0, true))
      expected4to8.add(ClassifierSetTestData(2, "Is", 1, 1, 0, 1, 1, true))
      expected4to8.add(ClassifierSetTestData(3, "Foo", 1, 1, 0, 1, 0, true))
      expected4to8.add(ClassifierSetTestData(1, "That", 2, 2, 1, 3, 2, true))
      expected4to8.add(ClassifierSetTestData(2, "Also", 1, 1, 1, 2, 1, true))
      expected4to8.add(ClassifierSetTestData(3, "Bar", 1, 1, 1, 2, 0, true))
      expected4to8.add(ClassifierSetTestData(2, "Is", 1, 1, 0, 1, 1, true))
      expected4to8.add(ClassifierSetTestData(3, "Bar", 1, 1, 0, 1, 0, true))

      // Shift selection to {4,8}
      loadSuccess = false
      loadRange.set(
        (CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(4)).toDouble(),
        (CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(8)).toDouble(),
      )
      assertThat(loadSuccess).isTrue()
      verifyClassifierResult(heapSet, LinkedList(expected4to8), 0)

      // Shift selection back to {0,4}
      loadSuccess = false
      loadRange.set(CAPTURE_START_TIME.toDouble(), (CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(4)).toDouble())
      assertThat(loadSuccess).isTrue()
      verifyClassifierResult(heapSet, LinkedList(expected0to4), 0)
    }

    @Test
    fun testInfoMessageBasedOnSelection() {
      val fullData =
        MemoryAllocSamplingData.newBuilder()
          .setSamplingNumInterval(BaseStreamingMemoryProfilerStage.LiveAllocationSamplingMode.FULL.value)
          .build()
      val sampledData =
        MemoryAllocSamplingData.newBuilder()
          .setSamplingNumInterval(BaseStreamingMemoryProfilerStage.LiveAllocationSamplingMode.SAMPLED.value)
          .build()
      val noneData =
        MemoryAllocSamplingData.newBuilder()
          .setSamplingNumInterval(BaseStreamingMemoryProfilerStage.LiveAllocationSamplingMode.NONE.value)
          .build()
      myTransportService.addEventToStream(
        ProfilersTestData.SESSION_DATA.streamId,
        Common.Event.newBuilder()
          .setPid(ProfilersTestData.SESSION_DATA.pid)
          .setKind(Common.Event.Kind.MEMORY_ALLOC_SAMPLING)
          .setTimestamp(TimeUnit.SECONDS.toNanos(CAPTURE_START_TIME))
          .setMemoryAllocSampling(fullData)
          .build(),
      )
      myTransportService.addEventToStream(
        ProfilersTestData.SESSION_DATA.streamId,
        Common.Event.newBuilder()
          .setPid(ProfilersTestData.SESSION_DATA.pid)
          .setKind(Common.Event.Kind.MEMORY_ALLOC_SAMPLING)
          .setTimestamp(TimeUnit.SECONDS.toNanos(CAPTURE_START_TIME + 1))
          .setMemoryAllocSampling(sampledData)
          .build(),
      )
      myTransportService.addEventToStream(
        ProfilersTestData.SESSION_DATA.streamId,
        Common.Event.newBuilder()
          .setPid(ProfilersTestData.SESSION_DATA.pid)
          .setKind(Common.Event.Kind.MEMORY_ALLOC_SAMPLING)
          .setTimestamp(TimeUnit.SECONDS.toNanos(CAPTURE_START_TIME + 2))
          .setMemoryAllocSampling(noneData)
          .build(),
      )
      myTransportService.addEventToStream(
        ProfilersTestData.SESSION_DATA.streamId,
        Common.Event.newBuilder()
          .setPid(ProfilersTestData.SESSION_DATA.pid)
          .setKind(Common.Event.Kind.MEMORY_ALLOC_SAMPLING)
          .setTimestamp(TimeUnit.SECONDS.toNanos(CAPTURE_START_TIME + 3))
          .setMemoryAllocSampling(fullData)
          .build(),
      )

      // Flag that gets set on the joiner thread to notify the main thread whether the contents in the ChangeNode are accurate.
      var loadSuccess = false
      val capture =
        LiveAllocationCaptureObject(
          myProfilerClient,
          ProfilersTestData.SESSION_DATA,
          CAPTURE_START_TIME,
          loadService,
          myStage,
        )
      myStage.captureSelection.aspect.addDependency(myAspectObserver).onChange(CaptureSelectionAspect.CURRENT_HEAP_CONTENTS) {
        loadSuccess = true
      }

      val loadRange = Range(CAPTURE_START_TIME.toDouble(), CAPTURE_START_TIME.toDouble())
      loadSuccess = false
      capture.load(loadRange, loadJoiner)
      assertThat(loadSuccess).isTrue()
      assertThat(capture.infoMessage).isNull()

      loadSuccess = false
      loadRange.set(CAPTURE_START_TIME.toDouble(), (CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(3)).toDouble())
      assertThat(loadSuccess).isTrue()
      assertThat(capture.infoMessage).isEqualTo(LiveAllocationCaptureObject.SAMPLING_INFO_MESSAGE)

      loadSuccess = false
      loadRange.set(
        (CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(1)).toDouble(),
        (CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(2)).toDouble(),
      )
      assertThat(loadSuccess).isTrue()
      assertThat(capture.infoMessage).isEqualTo(LiveAllocationCaptureObject.SAMPLING_INFO_MESSAGE)

      loadSuccess = false
      loadRange.set(
        (CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(2)).toDouble(),
        (CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(3)).toDouble(),
      )
      assertThat(loadSuccess).isTrue()
      assertThat(capture.infoMessage).isEqualTo(LiveAllocationCaptureObject.SAMPLING_INFO_MESSAGE)

      loadSuccess = false
      loadRange.set(
        (CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(3)).toDouble(),
        (CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(4)).toDouble(),
      )
      assertThat(loadSuccess).isTrue()
      assertThat(capture.infoMessage).isNull()
    }

    companion object {
      @Parameters(name = "{index}: HeapId:{0}, HeapName:{1}")
      @JvmStatic
      fun getHeapParameters(): Array<Array<Any>> {
        return arrayOf(
          arrayOf(DEFAULT_HEAP_ID, DEFAULT_HEAP_NAME),
          arrayOf(JNI_HEAP_ID, JNI_HEAP_NAME),
        )
      }
    }
  }

  class DefaultHeapTest : LiveAllocationCaptureObjectTest() {

    private lateinit var myProfilerClient: ProfilerClient

    @Before
    override fun before() {
      super.before()
      myProfilerClient = ProfilerClient(myGrpcChannel.channel)
    }

    // Class + method names in each StackFrame are lazy-loaded. Check that the method info are fetched correctly.
    @Test
    fun testLazyLoadedCallStack() {
      val capture =
        LiveAllocationCaptureObject(
          myProfilerClient,
          ProfilersTestData.SESSION_DATA,
          CAPTURE_START_TIME,
          loadService,
          myStage,
        )

      // Heap set should start out empty.
      val heapSet = capture.getHeapSet(DEFAULT_HEAP_ID)!!
      assertThat(heapSet.childrenClassifierSets).isEmpty()
      heapSet.classGrouping = ClassGrouping.ARRANGE_BY_CALLSTACK

      val expected0to4: Queue<ClassifierSetTestData> = LinkedList()
      expected0to4.add(ClassifierSetTestData(0, DEFAULT_HEAP_NAME, 4, 2, 2, 4, 4, true))
      expected0to4.add(ClassifierSetTestData(1, "BarMethodA() (LThat/Is/Bar;)", 1, 1, 0, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(2, "FooMethodA() (LThis/Is/Foo;)", 1, 1, 0, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(3, "Foo", 1, 1, 0, 1, 0, true))
      expected0to4.add(ClassifierSetTestData(1, "FooMethodB() (LThis/Also/Foo;)", 1, 1, 0, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(2, "BarMethodA() (LThat/Is/Bar;)", 1, 1, 0, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(3, "Bar", 1, 1, 0, 1, 0, true))
      expected0to4.add(ClassifierSetTestData(1, "BarMethodB() (LThat/Also/Bar;)", 1, 0, 1, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(2, "FooMethodB() (LThis/Also/Foo;)", 1, 0, 1, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(3, "Foo", 1, 0, 1, 1, 0, true))
      expected0to4.add(ClassifierSetTestData(1, "FooMethodA() (LThis/Is/Foo;)", 1, 0, 1, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(2, "BarMethodB() (LThat/Also/Bar;)", 1, 0, 1, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(3, "Bar", 1, 0, 1, 1, 0, true))

      val loadRange = Range(CAPTURE_START_TIME.toDouble(), (CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(4)).toDouble())
      capture.load(loadRange, loadJoiner)
      verifyClassifierResult(heapSet, expected0to4, 0)
    }

    @Test
    fun testSelectionWithJavaMethodFilter() {
      // Flag that gets set on the joiner thread to notify the main thread whether the contents in the ChangeNode are accurate.
      var loadSuccess = false
      val capture =
        LiveAllocationCaptureObject(
          myProfilerClient,
          ProfilersTestData.SESSION_DATA,
          CAPTURE_START_TIME,
          loadService,
          myStage,
        )

      // Heap set should start out empty.
      val heapSet = capture.getHeapSet(DEFAULT_HEAP_ID)!!
      assertThat(heapSet.childrenClassifierSets).isEmpty()
      heapSet.classGrouping = ClassGrouping.ARRANGE_BY_PACKAGE
      myStage.captureSelection.aspect.addDependency(myAspectObserver).onChange(CaptureSelectionAspect.CURRENT_HEAP_CONTENTS) {
        loadSuccess = true
      }

      val loadRange = Range(CAPTURE_START_TIME.toDouble(), (CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(4)).toDouble())
      loadSuccess = false
      capture.load(loadRange, loadJoiner)

      // Filter with Java method name
      heapSet.classGrouping = ClassGrouping.ARRANGE_BY_CALLSTACK
      heapSet.selectFilter(Filter("MethodA"))
      val expected0to4: Queue<ClassifierSetTestData> = LinkedList()
      expected0to4.add(ClassifierSetTestData(0, DEFAULT_HEAP_NAME, 3, 2, 1, 4, 3, true))
      expected0to4.add(ClassifierSetTestData(1, "BarMethodA() (LThat/Is/Bar;)", 1, 1, 0, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(2, "FooMethodA() (LThis/Is/Foo;)", 1, 1, 0, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(3, "Foo", 1, 1, 0, 1, 0, true))
      expected0to4.add(ClassifierSetTestData(1, "FooMethodB() (LThis/Also/Foo;)", 1, 1, 0, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(2, "BarMethodA() (LThat/Is/Bar;)", 1, 1, 0, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(3, "Bar", 1, 1, 0, 1, 0, true))
      expected0to4.add(ClassifierSetTestData(1, "FooMethodA() (LThis/Is/Foo;)", 1, 0, 1, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(2, "BarMethodB() (LThat/Also/Bar;)", 1, 0, 1, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(3, "Bar", 1, 0, 1, 1, 0, true))
      verifyClassifierResult(heapSet, LinkedList(expected0to4), 0)
    }
  }

  class JniHeapTest : LiveAllocationCaptureObjectTest() {

    private lateinit var myProfilerClient: ProfilerClient

    @Before
    override fun before() {
      super.before()
      myProfilerClient = ProfilerClient(myGrpcChannel.channel)
    }

    @Test
    fun testLazyLoadedCallStack() {
      val capture =
        LiveAllocationCaptureObject(
          myProfilerClient,
          ProfilersTestData.SESSION_DATA,
          CAPTURE_START_TIME,
          loadService,
          myStage,
        )

      // Heap set should start out empty.
      val heapSet = capture.getHeapSet(JNI_HEAP_ID)!!
      assertThat(heapSet.childrenClassifierSets).isEmpty()
      heapSet.classGrouping = ClassGrouping.ARRANGE_BY_CALLSTACK

      val expected0to4: Queue<ClassifierSetTestData> = LinkedList()
      expected0to4.add(ClassifierSetTestData(0, JNI_HEAP_NAME, 4, 2, 2, 4, 4, true))
      expected0to4.add(ClassifierSetTestData(1, "BarMethodA() (NativeNamespace::Bar)", 1, 1, 0, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(2, "FooMethodA() (NativeNamespace::Foo)", 1, 1, 0, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(3, "Foo", 1, 1, 0, 1, 0, true))
      expected0to4.add(ClassifierSetTestData(1, "FooMethodB() (NativeNamespace::Foo)", 1, 1, 0, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(2, "BarMethodA() (NativeNamespace::Bar)", 1, 1, 0, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(3, "Bar", 1, 1, 0, 1, 0, true))
      expected0to4.add(ClassifierSetTestData(1, "BarMethodB() (NativeNamespace::Bar)", 1, 0, 1, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(2, "FooMethodB() (NativeNamespace::Foo)", 1, 0, 1, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(3, "Foo", 1, 0, 1, 1, 0, true))
      expected0to4.add(ClassifierSetTestData(1, "FooMethodA() (NativeNamespace::Foo)", 1, 0, 1, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(2, "BarMethodB() (NativeNamespace::Bar)", 1, 0, 1, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(3, "Bar", 1, 0, 1, 1, 0, true))

      val loadRange = Range(CAPTURE_START_TIME.toDouble(), (CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(4)).toDouble())
      capture.load(loadRange, loadJoiner)
      verifyClassifierResult(heapSet, expected0to4, 0)
    }

    @Test
    fun testSelectionWithJavaMethodFilter() {
      // Flag that gets set on the joiner thread to notify the main thread whether the contents in the ChangeNode are accurate.
      var loadSuccess = false
      val capture =
        LiveAllocationCaptureObject(
          myProfilerClient,
          ProfilersTestData.SESSION_DATA,
          CAPTURE_START_TIME,
          loadService,
          myStage,
        )

      // Heap set should start out empty.
      val heapSet = capture.getHeapSet(JNI_HEAP_ID)!!
      assertThat(heapSet.childrenClassifierSets).isEmpty()
      heapSet.classGrouping = ClassGrouping.ARRANGE_BY_PACKAGE
      myStage.captureSelection.aspect.addDependency(myAspectObserver).onChange(CaptureSelectionAspect.CURRENT_HEAP_CONTENTS) {
        loadSuccess = true
      }

      val loadRange = Range(CAPTURE_START_TIME.toDouble(), (CAPTURE_START_TIME + TimeUnit.SECONDS.toMicros(4)).toDouble())
      loadSuccess = false
      capture.load(loadRange, loadJoiner)

      // Filter with Java method name
      heapSet.classGrouping = ClassGrouping.ARRANGE_BY_CALLSTACK
      heapSet.selectFilter(Filter("MethodA"))
      val expected0to4: Queue<ClassifierSetTestData> = LinkedList()
      expected0to4.add(ClassifierSetTestData(0, JNI_HEAP_NAME, 3, 2, 1, 4, 3, true))
      expected0to4.add(ClassifierSetTestData(1, "BarMethodA() (NativeNamespace::Bar)", 1, 1, 0, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(2, "FooMethodA() (NativeNamespace::Foo)", 1, 1, 0, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(3, "Foo", 1, 1, 0, 1, 0, true))
      expected0to4.add(ClassifierSetTestData(1, "FooMethodB() (NativeNamespace::Foo)", 1, 1, 0, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(2, "BarMethodA() (NativeNamespace::Bar)", 1, 1, 0, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(3, "Bar", 1, 1, 0, 1, 0, true))
      expected0to4.add(ClassifierSetTestData(1, "FooMethodA() (NativeNamespace::Foo)", 1, 0, 1, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(2, "BarMethodB() (NativeNamespace::Bar)", 1, 0, 1, 1, 1, true))
      expected0to4.add(ClassifierSetTestData(3, "Bar", 1, 0, 1, 1, 0, true))
      verifyClassifierResult(heapSet, LinkedList(expected0to4), 0)
    }
  }

  companion object {
    const val CAPTURE_START_TIME = 0L

    // A fake symbolizer so that JNI reference instance objects have proper app+system callstacks.
    private val FAKE_SYMBOLIZER =
      object : NativeFrameSymbolizer {
        override fun symbolize(abi: String, unsymbolizedFrame: Memory.NativeCallStack.NativeFrame): Memory.NativeCallStack.NativeFrame {
          val address = unsymbolizedFrame.address
          // System frame.
          return if (address == ProfilersTestData.SYSTEM_NATIVE_ADDRESSES_BASE) {
            unsymbolizedFrame.toBuilder().setModuleName(ProfilersTestData.FAKE_SYSTEM_NATIVE_MODULE).build()
          } else {
            val index = ((address - ProfilersTestData.NATIVE_ADDRESSES_BASE) % ProfilersTestData.FAKE_NATIVE_FUNCTION_NAMES.size).toInt()
            unsymbolizedFrame
              .toBuilder()
              .setModuleName(ProfilersTestData.FAKE_NATIVE_MODULE_NAMES[index])
              .setSymbolName(ProfilersTestData.FAKE_NATIVE_FUNCTION_NAMES[index])
              .setFileName(ProfilersTestData.FAKE_NATIVE_SOURCE_FILE[index])
              .build()
          }
        }

        override fun stop() {}
      }

    private fun verifyClassifierResult(
      node: ClassifierSet,
      expected: Queue<ClassifierSetTestData>,
      currentDepth: Int,
    ): Boolean {
      var done = false
      var currentNodeVisited = false
      var childrenVisited = false

      while (expected.isNotEmpty() && !done) {
        val testData = expected.peek() ?: break
        val depth = testData.depth

        if (depth < currentDepth) {
          // We are done with the current sub-tree.
          done = true
        } else if (depth > currentDepth) {
          // We need to go deeper...
          assertThat(node.childrenClassifierSets).isNotEmpty()
          assertThat(childrenVisited).isFalse()
          for (child in node.childrenClassifierSets) {
            val childResult = verifyClassifierResult(child, expected, currentDepth + 1)
            assertThat(childResult).isTrue()
          }
          childrenVisited = true
        } else {
          if (currentNodeVisited) {
            done = true
            continue
          }

          // We are at current node, consumes the current line.
          expected.poll()
          assertThat(node.name).isEqualTo(testData.name)
          assertThat(node.deltaAllocationCount).isEqualTo(testData.allocations)
          assertThat(node.deltaDeallocationCount).isEqualTo(testData.deallocations)
          assertThat(node.totalObjectCount).isEqualTo(testData.total)
          assertThat(node.instancesCount).isEqualTo(testData.instanceCount)
          assertThat(node.childrenClassifierSets).hasSize(testData.childrenSize)
          assertThat(node.hasStackInfo()).isEqualTo(testData.hasStack)
          currentNodeVisited = true
        }
      }

      assertThat(currentNodeVisited).isTrue()
      return currentNodeVisited
    }

    // Wait for the executor service to complete the task created in load(...)
    // NOTE - this works because myExecutorService is a single-threaded executor.
    private fun waitForLoadComplete(capture: LiveAllocationCaptureObject) {
      val latch = CountDownLatch(1)
      checkNotNull(capture.executorService).execute {
        try {
          latch.countDown()
        } catch (ignored: Exception) {}
      }
      latch.await()
    }
  }

  // Auxiliary class to verify ClassifierSet's internal data.
  protected class ClassifierSetTestData(
    val depth: Int,
    val name: String,
    val allocations: Int,
    val deallocations: Int,
    val total: Int,
    val instanceCount: Int,
    val childrenSize: Int,
    val hasStack: Boolean,
  )
}
