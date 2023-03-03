/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.profilers.performance

import com.android.tools.adtui.model.FakeTimer
import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.Timeline
import com.android.tools.adtui.model.filter.Filter
import com.android.tools.idea.transport.faketransport.FakeGrpcServer
import com.android.tools.idea.transport.faketransport.FakeTransportService
import com.android.tools.perflib.vmtrace.ClockType
import com.android.tools.profilers.FakeIdeProfilerComponents
import com.android.tools.profilers.FakeIdeProfilerServices
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.cpu.CaptureNode
import com.android.tools.profilers.cpu.CpuCapture
import com.android.tools.profilers.cpu.CpuThreadInfo
import com.android.tools.profilers.cpu.capturedetails.CaptureDetails
import com.android.tools.profilers.cpu.capturedetails.CaptureDetailsView
import com.android.tools.profilers.cpu.capturedetails.ChartDetailsView.FlameChartDetailsView
import com.android.tools.profilers.cpu.capturedetails.TreeDetailsView.BottomUpDetailsView
import com.android.tools.profilers.cpu.capturedetails.TreeDetailsView.TopDownDetailsView
import com.android.tools.profilers.cpu.config.ProfilingConfiguration.TraceType
import com.android.tools.profilers.cpu.nodemodel.CaptureNodeModel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import org.junit.Rule
import org.junit.Test

class CaptureDetailsTest {
  @get:Rule
  val appRule = ApplicationRule()

  @get:Rule
  val disposableRule = DisposableRule()

  private val timer = FakeTimer()
  private val transportService = FakeTransportService(timer, false)

  @get:Rule
  var grpcServer = FakeGrpcServer.createFakeGrpcServer("CaptureDetailsTest", transportService)

  private fun benchmarkInit(prefix: String) =
    benchmarkMemoryAndTime("$prefix Initialization", "Load-Capture", memUnit = MemoryUnit.KB)
  private fun benchmarkRangeChange(prefix: String) =
    benchmarkMemoryAndTime("$prefix Range Change", "Range-Change", memUnit = MemoryUnit.KB)
  private fun benchmarkFilterChange(prefix: String) =
    benchmarkMemoryAndTime("$prefix Filter Change", "Filter-Change", memUnit = MemoryUnit.KB)
  private val benchmarkTopDownInit = benchmarkInit("Top-Down")
  private val benchmarkBottomUpInit = benchmarkInit("Bottom-Up")
  private val benchmarkFlameChartInit = benchmarkInit("Flame-Chart")
  private val benchmarkTopDownRangeChange = benchmarkRangeChange("Top-Down")
  private val benchmarkBottomUpRangeChange = benchmarkRangeChange("Bottom-Up")
  private val benchmarkFlameChartRangeChange = benchmarkRangeChange("Flame-Chart")
  private val benchmarkTopDownFilterChange = benchmarkFilterChange("Top-Down")
  private val benchmarkBottomUpFilterChange = benchmarkFilterChange("Bottom-Up")
  private val benchmarkFlameChartFilterChange = benchmarkFilterChange("Flame-Chart")

  @org.junit.Ignore("b/255883540, b/255883136")
  @Test
  fun benchmarkTopDown() = benchmarkInitAndUpdate(benchmarkTopDownInit,
                                                  benchmarkTopDownRangeChange,
                                                  benchmarkTopDownFilterChange,
                                                  CaptureDetails.Type.TOP_DOWN.build,
                                                  ::TopDownDetailsView)

  @Test
  fun benchmarkBottomUp() = benchmarkInitAndUpdate(benchmarkBottomUpInit,
                                                   benchmarkBottomUpRangeChange,
                                                   benchmarkBottomUpFilterChange,
                                                   CaptureDetails.Type.BOTTOM_UP.build,
                                                   ::BottomUpDetailsView)

  @Test
  fun benchmarkFlameChart() = benchmarkInitAndUpdate(benchmarkFlameChartInit,
                                                     benchmarkFlameChartRangeChange,
                                                     benchmarkFlameChartFilterChange,
                                                     CaptureDetails.Type.FLAME_CHART.build,
                                                     ::FlameChartDetailsView)

  private fun<T: CaptureDetails>
    benchmarkInitAndUpdate(benchmarkInit: BenchmarkRunner,
                           benchmarkRangeUpdate: BenchmarkRunner,
                           benchmarkFilterChange: BenchmarkRunner,
                           initModel: (ClockType, Range, List<CaptureNode>, CpuCapture, (Runnable) -> Unit) -> CaptureDetails,
                           initTree: (StudioProfilersView, T) -> CaptureDetailsView) {
    withTestData { range, captureNodes, cpuCapture ->
      val profilersView = fakeProfilersView()

      val treeView = benchmarkInit("synthetic") {
        initTree(profilersView, initModel(ClockType.GLOBAL, range, captureNodes, cpuCapture,
                                          ApplicationManager.getApplication()::executeOnPooledThread) as T)
      }

      benchmarkRangeUpdate("synthetic") {
        val lo = range.min
        val hi = range.max
        val updateIntervals = 100
        val d = (hi - lo) / (updateIntervals + 1)
        repeat(updateIntervals) { range.set(lo, range.max - d) }
        repeat(updateIntervals) { range.set(lo, range.max + d) }
        range.set(lo, hi)
        repeat(updateIntervals) { range.set(range.min + d, hi) }
        repeat(updateIntervals) { range.set(range.min - d, hi) }
      }

      benchmarkFilterChange("synthetic") {
        listOf("100", "42", "n/a", "").forEach { str ->
          captureNodes.forEach { node ->
            node.applyFilter(Filter(str))
          }
        }
      }

      treeView.hashCode() // keep it live to make sure it reacts to range change
    }
  }

  private fun fakeProfilersView(): StudioProfilersView {
    val profilers = object: StudioProfilers(ProfilerClient(grpcServer.channel), FakeIdeProfilerServices()) {
      override fun update(elapsedNs: Long) {}
    }
    return StudioProfilersView(profilers, FakeIdeProfilerComponents(), disposableRule.disposable)
  }

  private fun<A> withTestData(test: (Range, List<CaptureNode>, CpuCapture) -> A): A {
    val lo = 0
    val hi = Int.MAX_VALUE
    // for the purpose of benchmarking, we avoid mockito, because it can insert code that slows down
    // the program in an irrelevant way.
    val cpuCapture = object : CpuCapture {
      override fun getTraceId(): Long = TODO()
      override fun getType(): TraceType = TODO()
      override fun getTimeline(): Timeline = TODO()
      override fun isDualClock(): Boolean= TODO()
      override fun getDualClockDisabledMessage(): String = TODO()
      override fun updateClockType(clockType: ClockType)= TODO()
      override fun getMainThreadId(): Int = TODO()
      override fun getThreads(): MutableSet<CpuThreadInfo> = TODO()
      override fun containsThread(threadId: Int): Boolean = TODO()
      override fun getCaptureNode(threadId: Int): CaptureNode? = TODO()
      override fun getCaptureNodes(): MutableCollection<CaptureNode> = TODO()
      override fun collapseNodesWithTags(tagsToCollapse: MutableSet<String>) = TODO()
      override fun getCollapsedTags(): MutableSet<String> = TODO()
      override fun getTags(): MutableSet<String> = TODO()
    }
    return test(Range(lo.toDouble(), hi.toDouble()),
                testTrees(lo.toLong(), hi.toLong()),
                cpuCapture)
  }

  private fun testTrees(lo: Long, hi: Long): List<CaptureNode> {
    val numIds = 512
    var nextId = 0

    fun captureTree(branching: Int, depth: Int, lo: Long, hi: Long): CaptureNode =
      CaptureNode(model("${nextId++ % numIds}")).apply {
        startGlobal = lo
        endGlobal = hi
        startThread = lo
        endThread = hi
        if (depth > 0) {
          val l = (hi - lo) / branching
          (0 until branching).forEach { i ->
            addChild(captureTree(branching, depth - 1, lo + i * l, lo + (i + 1) * l))
          }
        }
      }

    val l = (hi - lo) / 4
    return listOf(captureTree(4, 8, lo, lo + l),
                  captureTree(16, 4, lo + l, lo + 2*l),
                  captureTree(2, 16, lo + 2*l,lo + 3*l),
                  captureTree(1, 1000, lo + 3*l, hi))
  }

  private fun model(name: String) = object : CaptureNodeModel {
    override fun getName() = name
    override fun getFullName() = name
    override fun getId() = name
  }
}