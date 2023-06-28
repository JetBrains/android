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
package com.android.tools.profilers.cpu.simpleperf

import com.android.tools.adtui.model.Range
import com.android.tools.profiler.proto.SimpleperfReport
import com.android.tools.profilers.cpu.BaseCpuCapture
import com.android.tools.profilers.cpu.CpuProfilerTestUtils
import com.android.tools.profilers.cpu.nodemodel.CppFunctionModel
import com.google.common.collect.Lists
import com.google.common.truth.Truth
import com.intellij.openapi.util.io.FileUtil
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SimpleperfTraceParserTest {
  private lateinit var myParser: SimpleperfTraceParser
  private lateinit var myTraceFile: File

  @Before
  @Throws(IOException::class)
  fun setUp() {
    // Create the trace file that'll be used for this test by copying the test trace file to a temp file.

    myTraceFile = File("C:\\Users\\vaage\\Desktop\\cpu_trace.trace").also {
      if (it.exists()) { it.delete() }
      it.writeBytes(CpuProfilerTestUtils.traceFileToByteString("simpleperf.trace").toByteArray())
    }

    myParser = SimpleperfTraceParser()
  }

  @After
  fun tearDown() {
    myParser
  }

  @Test
  @Throws(IOException::class)
  fun samplesAndLostCountShouldMatchSimpleperfReport() {
    myParser.parseTraceFile(myTraceFile)

    Truth.assertThat(myParser.sampleCount).isEqualTo(23487)
    Truth.assertThat(myParser.lostSampleCount).isEqualTo(93)
  }

  @Test
  @Throws(IOException::class)
  fun allTreesShouldStartWithThreadName() {
    val capture = myParser.parse(myTraceFile, 0)

    capture.threads.forEach { thread ->
      capture.getCaptureNode(thread.id).also {
        // Use assertWithMessage with message here so that we know which thread failed to provide a capture node. Otherwise, we would just
        // know that a thread provided a null value.
        Truth.assertWithMessage("thread.id=${thread.id} thread.name=${thread.name}").that(it).isNotNull()
        Truth.assertThat(it!!.data.name).isEqualTo(thread.name)
      }
    }
  }

  @Test
  @Throws(IOException::class)
  fun checkKnownThreadsPresenceAndCount() {
    val capture = myParser.parse(myTraceFile, 0)
    Truth.assertThat(capture.captureNodes).isNotEmpty()

    // Using contains instead of equals because native thread names are limited to 15 characters and there is no way to predict where they
    // are going to be trimmed.

    val groups = capture.threads.groupBy { it.name }

    // Verity the number of important threads.
    Truth.assertThat(groups.filter { (key, _) -> "Studio:Heartbeat".contains(key) }.flatMap { it.value }).hasSize(1)
    Truth.assertThat(groups.filter { (key, _) -> "Studio:MemoryAgent".contains(key) }.flatMap { it.value }).hasSize(1)
    Truth.assertThat(groups.filter { (key, _) -> "Studio:Socket".contains(key) }.flatMap { it.value }).hasSize(1)
    Truth.assertThat(groups.filter { (key, _) -> "JVMTI Agent thread".contains(key) }.flatMap { it.value }).hasSize(2)

    // TODO: Update file name along with the trace files
    // libjvmtiagent should be the entry point
    groups.filter { (key, _) -> "Studio:Heartbeat".contains(key) }.flatMap { it.value }.map { capture.getCaptureNode(it.id) }.forEach {node ->
      Truth.assertThat(node).isNotNull()

      val startThread = node!!.getChildAt(0)

      Truth.assertThat(startThread.data.fullName).startsWith("__start_thread")

      startThread.getChildAt(0).also {child ->
        Truth.assertThat(child.data.fullName).startsWith("__pthread_start")
        Truth.assertThat(child.getChildAt(0).data.fullName).startsWith("libperfa_arm64.so")
      }
    }

    // TODO: Update file name along with the trace files
    // libjvmtiagent should be the entry point
    groups.filter { (key, _) -> "Studio:MemoryAgent".contains(key) }.flatMap { it.value }.map { capture.getCaptureNode(it.id) }.forEach {node ->
      Truth.assertThat(node).isNotNull()

      val startThread = node!!.getChildAt(0)

      Truth.assertThat(startThread.data.fullName).startsWith("__start_thread")

      startThread.getChildAt(0).also {child ->
        Truth.assertThat(child.data.fullName).startsWith("__pthread_start")
        Truth.assertThat(child.getChildAt(0).data.fullName).startsWith("libperfa_arm64.so")
      }
    }

    // TODO: Update file name along with the trace files
    // libjvmtiagent should be the entry point
    groups.filter { (key, _) -> "Studio:Studio:Socket".contains(key) }.flatMap { it.value }.map { capture.getCaptureNode(it.id) }.forEach {node ->
      Truth.assertThat(node).isNotNull()

      val startThread = node!!.getChildAt(0)

      Truth.assertThat(startThread.data.fullName).startsWith("__start_thread")

      startThread.getChildAt(0).also {child ->
        Truth.assertThat(child.data.fullName).startsWith("__pthread_start")
        Truth.assertThat(child.getChildAt(0).data.fullName).startsWith("libperfa_arm64.so")
      }
    }

    // openjdkjvmti should be the entry point
    groups.filter { (key, _) -> "JVMTI Agent thread".contains(key) }.flatMap { it.value }.map { capture.getCaptureNode(it.id) }.forEach {node ->
      Truth.assertThat(node).isNotNull()

      val startThread = node!!.getChildAt(0)

      Truth.assertThat(startThread.data.fullName).startsWith("__start_thread")

      startThread.getChildAt(0).also {child ->
        Truth.assertThat(child.data.fullName).startsWith("__pthread_start")
        Truth.assertThat(child.getChildAt(0).data.fullName).startsWith("openjdkjvmti::AgentCallback")
      }
    }
  }

  @Test
  @Throws(IOException::class)
  fun nodeDepthsShouldBeCoherent() {
    val anyTree = myParser.parse(myTraceFile, 0).captureNodes.iterator().next()

    Truth.assertThat(anyTree.depth).isEqualTo(0)

    // Just go as deep as possible in one branch per child and check the depths of each node in the branch
    anyTree.children.forEach { child ->
      val path = mutableListOf(child)

      while (path.last().firstChild != null) {
        path.add(path.last().firstChild!!)
      }

      // Create the expected depth values which should start at 1 and increase by one.
      val expectedDepths = (1..path.size).toList()

      Truth.assertThat(path.map { it.depth }).containsExactlyElementsIn(expectedDepths)
    }
  }

  @Test
  @Throws(IOException::class)
  fun cppModelVAddressComesFromParentInCallChain() {
    val mainThread = 7056
    val mainThreadName = "e.sample.tunnel"

    val trace = CpuProfilerTestUtils.traceFileToByteString("simpleperf_callchain.trace").let {
      val trace = FileUtil.createTempFile("native_trace", ".trace", true)
      FileOutputStream(trace).use { out -> out.write(it.toByteArray()) }
      return@let trace
    }

    val capture = myParser.parse(trace, 1)

    val mainFirstSample = myParser.mySamples.firstOrNull { sample: SimpleperfReport.Sample -> sample.threadId == mainThread }
    Truth.assertThat(mainFirstSample).isNotNull()

    val firstCallChain = Lists.reverse(mainFirstSample!!.callchainList)

    val leftMostMainTreeBranch = capture.getCaptureNode(mainFirstSample.threadId).let {
      Truth.assertThat(it).isNotNull()
      return@let mutableListOf(it!!)
    }

    while (leftMostMainTreeBranch.last().firstChild != null) {
      leftMostMainTreeBranch.add(leftMostMainTreeBranch.last().firstChild!!)
    }

    // tree branch = callchain + special node representing the thread name
    Truth.assertThat(leftMostMainTreeBranch.size).isEqualTo(firstCallChain.size + 1)
    Truth.assertThat(leftMostMainTreeBranch[0].data.name).isEqualTo(mainThreadName)

    var cppModelCount = 0
    for (i in 1 until firstCallChain.size) {
      (leftMostMainTreeBranch[i + 1].data as? CppFunctionModel)?.also {
        cppModelCount++
        val parentVAddress = firstCallChain[i - 1].vaddrInFile
        Truth.assertThat(it.vAddress).isEqualTo(parentVAddress)
      }
    }

    // Verify we indeed checked for some addresses
    Truth.assertThat(cppModelCount).isGreaterThan(0)
  }

  @Test
  @Throws(IOException::class)
  fun mainProcessShouldBePresent() {
    val capture = myParser.parse(myTraceFile, 0)
    val appPid = 8589
    val mainThread = capture.getCaptureNode(appPid)

    Truth.assertThat(mainThread).isNotNull()
    Truth.assertThat(capture.mainThreadId).isEqualTo(appPid)
  }

  @Test
  @Throws(IOException::class)
  fun invalidFileShouldFailDueToMagicNumberMismatch() {
    val traceBytes = CpuProfilerTestUtils.traceFileToByteString("simpleperf_malformed.trace")
    val trace = FileUtil.createTempFile("cpu_trace", ".trace")
    FileOutputStream(trace).use { out -> out.write(traceBytes.toByteArray()) }
    myParser = SimpleperfTraceParser()

    try {
      myParser.parse(trace, 0)
      Truth.assertWithMessage("IllegalStateException should have been thrown due to missing file.").fail()
    } catch (e: IllegalStateException) {
      // Do nothing. Expected exception.
      Truth.assertThat(e.message).contains("magic number mismatch")
    }
  }

  @Test
  fun verifyFileHasSimpleperfHeaderMismatchMagicNumber() {
    val traceBytes = CpuProfilerTestUtils.traceFileToByteString("simpleperf_malformed.trace")
    val trace = FileUtil.createTempFile("cpu_trace", ".trace")
    FileOutputStream(trace).use { out -> out.write(traceBytes.toByteArray()) }
    val result = SimpleperfTraceParser.verifyFileHasSimpleperfHeader(trace)
    assertFalse { result }
  }

  @Test
  fun verifyFileHasSimpleperfHeaderMismatchMagicNumberWithPerfettoTrace() {
    val traceBytes = CpuProfilerTestUtils.traceFileToByteString("perfetto_cpu_usage.trace")
    val trace = FileUtil.createTempFile("cpu_trace", ".trace")
    FileOutputStream(trace).use { out -> out.write(traceBytes.toByteArray()) }
    val result = SimpleperfTraceParser.verifyFileHasSimpleperfHeader(trace)
    assertFalse { result }
  }

  @Test
  fun verifyFileHasSimpleperfHeaderMismatchMagicNumberWithAtraceTrace() {
    val traceBytes = CpuProfilerTestUtils.traceFileToByteString("atrace.ctrace")
    val trace = FileUtil.createTempFile("cpu_trace", ".trace")
    FileOutputStream(trace).use { out -> out.write(traceBytes.toByteArray()) }
    val result = SimpleperfTraceParser.verifyFileHasSimpleperfHeader(trace)
    assertFalse { result }
  }

  @Test
  fun verifyFileHasSimpleperfHeaderMismatchMagicNumberWithArtTrace() {
    val traceBytes = CpuProfilerTestUtils.traceFileToByteString("art_non_streaming.trace")
    val trace = FileUtil.createTempFile("cpu_trace", ".trace")
    FileOutputStream(trace).use { out -> out.write(traceBytes.toByteArray()) }
    val result = SimpleperfTraceParser.verifyFileHasSimpleperfHeader(trace)
    assertFalse { result }
  }

  @Test
  fun verifyFileHasSimpleperfHeaderMagicNumberMatch() {
    val traceBytes = CpuProfilerTestUtils.traceFileToByteString("simpleperf.trace")
    val trace = FileUtil.createTempFile("cpu_trace", ".trace")
    FileOutputStream(trace).use { out -> out.write(traceBytes.toByteArray()) }
    val result = SimpleperfTraceParser.verifyFileHasSimpleperfHeader(trace)
    assertTrue { result }
  }

  @Test
  @Throws(IOException::class)
  fun rangeShouldBeFromFirstToLastTimestamp() {
    val capture = myParser.parse(myTraceFile, 0)

    val startTimeUs = TimeUnit.NANOSECONDS.toMicros(myParser.mySamples[0].time).toDouble()
    val endTimeUs = TimeUnit.NANOSECONDS.toMicros(myParser.mySamples[myParser.mySamples.size - 1].time).toDouble()
    val expected = Range(startTimeUs, endTimeUs)

    Truth.assertThat(capture.range.min).isWithin(0.0).of(expected.min)
    Truth.assertThat(capture.range.max).isWithin(0.0).of(expected.max)
  }

  @Test
  @Throws(IOException::class)
  fun emptyTraceCanBeParsed() {
    val traceBytes = CpuProfilerTestUtils.traceFileToByteString("simpleperf_empty.trace")
    val trace = FileUtil.createTempFile("cpu_trace", ".trace")
    FileOutputStream(trace).use { out -> out.write(traceBytes.toByteArray()) }
    val capture = myParser.parse(trace, 0)

    Truth.assertThat(capture.range.isEmpty).isTrue()
    Truth.assertThat(capture.captureNodes).isEmpty()
    Truth.assertThat(capture.mainThreadId).isEqualTo(BaseCpuCapture.NO_THREAD_ID)
  }

  @Test
  fun tagsSortedByExpectedOrder() {
    val shuffledTags = listOf("/a/b/c", "/c/d/e", "[java]", "/a/*").shuffled()
    val sortedTags = shuffledTags.sortedWith(SimpleperfTraceParser.TAG_COMPARATOR)

    Truth.assertThat(sortedTags).isEqualTo(listOf("/a/b/c", "/c/d/e", "[java]", "/a/*"))
  }
}
