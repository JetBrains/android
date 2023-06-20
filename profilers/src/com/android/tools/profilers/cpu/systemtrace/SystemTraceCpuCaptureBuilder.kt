/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.profilers.cpu.systemtrace

import com.android.tools.adtui.model.Range
import com.android.tools.adtui.model.SeriesData
import com.android.tools.perflib.vmtrace.ClockType
import com.android.tools.profilers.cpu.CaptureNode
import com.android.tools.profilers.cpu.CpuThreadInfo
import com.android.tools.profilers.cpu.ThreadState
import com.android.tools.profilers.cpu.nodemodel.SystemTraceNodeFactory
import java.util.concurrent.TimeUnit
import java.util.function.UnaryOperator
import kotlin.math.max

class SystemTraceCpuCaptureBuilder(private val model: SystemTraceModelAdapter) {

  companion object {
    val UTILIZATION_BUCKET_LENGTH_US = TimeUnit.MILLISECONDS.toMicros(50)
    val BLAST_BUFFER_QUEUE_COUNTER_REGEX = Regex("QueuedBuffer - .+BLAST#\\d")
  }

  fun build(traceId: Long, mainProcessId: Int, initialViewRange: Range): SystemTraceCpuCapture {

    val mainProcess = model.getProcessById(mainProcessId) ?: throw IllegalArgumentException(
      "A process with the id $mainProcessId was not found while parsing the capture.")

    val captureTreeNodes = buildCaptureTreeNodes(mainProcess)
    val threadState = buildThreadStateData(mainProcess)
    val cpuState = buildCpuStateData(mainProcess)
    val cpuCounters = buildCpuCountersData()
    val memoryCounters = buildMainProcessMemoryCountersData(mainProcess)
    val powerRailCounters = buildPowerRailCountersData()
    val batteryDrainCounters = buildBatteryDrainCountersData()
    val blastBufferQueueCounter = buildBlastBufferQueueCounterData(mainProcess)

    val frameManager = SystemTraceFrameManager(mainProcess)
    val sfManager = SystemTraceSurfaceflingerManager(model, mainProcess.name)

    return SystemTraceCpuCapture(traceId, model, captureTreeNodes, threadState, cpuState.schedulingData, cpuState.utilizationData,
                                 cpuCounters, memoryCounters, powerRailCounters, batteryDrainCounters, blastBufferQueueCounter,
                                 frameManager, sfManager, initialViewRange)
  }

  /**
   * Returns a map of [CpuThreadInfo] to [CaptureNode].
   * The capture nodes are built from [TraceEventModel] maintaining the order and hierarchy.
   */
  private fun buildCaptureTreeNodes(mainProcessModel: ProcessModel): Map<CpuThreadInfo, CaptureNode> {
    val threadToCaptureNodeMap = mutableMapOf<CpuThreadInfo, CaptureNode>()
    val nodeFactory = SystemTraceNodeFactory()

    for (thread in mainProcessModel.getThreads()) {
      val threadInfo = CpuThreadSliceInfo(thread.id, thread.name, mainProcessModel.id, mainProcessModel.name)
      val root = CaptureNode(nodeFactory.getNode(thread.name), ClockType.GLOBAL)
      root.startGlobal = model.getCaptureStartTimestampUs()
      root.endGlobal = model.getCaptureEndTimestampUs()
      threadToCaptureNodeMap[threadInfo] = root
      for (event in thread.traceEvents) {
        root.addChild(populateCaptureNode(event, 1, nodeFactory))
      }
    }
    return threadToCaptureNodeMap
  }

  /**
   * Recursive function that builds a tree of [CaptureNode] from a [TraceEventModel].
   *
   * @param traceEventModel to convert to a [CaptureNode]. This method will be recursively called on all children.
   * @param depth to current node. Depth starts at 0.
   *
   * @return The [CaptureNode] that mirrors the [TraceEventModel] passed in.
   */
  private fun populateCaptureNode(traceEventModel: TraceEventModel, depth: Int, nodeFactory: SystemTraceNodeFactory): CaptureNode {
    val node = CaptureNode(nodeFactory.getNode(traceEventModel.name), ClockType.GLOBAL)
    node.startGlobal = traceEventModel.startTimestampUs
    node.endGlobal = traceEventModel.endTimestampUs
    // Should we drop these thread times, as SystemTrace does not support dual clock?
    node.startThread = traceEventModel.startTimestampUs
    node.endThread = traceEventModel.startTimestampUs + traceEventModel.cpuTimeUs
    node.depth = depth
    for (event in traceEventModel.childrenEvents) {
      node.addChild(populateCaptureNode(event, depth + 1, nodeFactory))
    }
    return node
  }

  /**
   * Builds a map of thread id to a list of [ThreadState] series.
   */
  private fun buildThreadStateData(mainProcessModel: ProcessModel): Map<Int, List<SeriesData<ThreadState>>> {
    val threadToStateSeries = mutableMapOf<Int, List<SeriesData<ThreadState>>>()

    for (thread in mainProcessModel.getThreads()) {
      val states: MutableList<SeriesData<ThreadState>> = ArrayList()
      threadToStateSeries[thread.id] = states

      // We use a (state, timestamp) tuple and assume the state is valid until the next state.
      // But Perfetto uses a (state, timestamp, duration) triplet to timebox each state.
      var (lastState, lastEndTimestampUs) = Pair(ThreadState.NO_ACTIVITY, 0L)
      for (sched in thread.schedulingEvents) {
        if (sched.state !== lastState) {
          states.add(SeriesData(sched.startTimestampUs, sched.state))
          lastState = sched.state
          lastEndTimestampUs = sched.endTimestampUs
        }
      }

      // To avoid the last thread state slice extending until
      // the end of user-dictated capture time, a fake NO_ACTIVITY
      // event is appended to terminate the last state slice.
      // Non-empty check makes sure we don't insert state data
      // when there is actually isn't any.
      if (lastState != ThreadState.NO_ACTIVITY && states.isNotEmpty()) {
        states.add(SeriesData(lastEndTimestampUs, ThreadState.NO_ACTIVITY))
      }
    }

    return threadToStateSeries
  }

  private data class CpuStateData(
    val schedulingData: Map<Int, List<SeriesData<CpuThreadSliceInfo>>>,
    val utilizationData: List<SeriesData<Long>>)

  /**
   * Builds a map of CPU ids to a list of [CpuThreadInfo] series. While building the CPU map it also builds a CPU utilization series.
   */
  private fun buildCpuStateData(mainProcessModel: ProcessModel): CpuStateData {

    // Initialize utilizationData with the buckets.
    val utilizationData = mutableListOf<SeriesData<Long>>()
    val startUserTimeUs: Long = model.getCaptureStartTimestampUs()
    val endUserTimeUs: Long = model.getCaptureEndTimestampUs()
    var i = startUserTimeUs
    while (i < endUserTimeUs + UTILIZATION_BUCKET_LENGTH_US) {
      utilizationData.add(SeriesData(i, 0L))
      i += UTILIZATION_BUCKET_LENGTH_US
    }

    val schedData = mutableMapOf<Int, List<SeriesData<CpuThreadSliceInfo>>>()

    // Create a lookup table for thread names, to be used when the full process info is missing
    val threadNames = model.getProcesses().flatMap { it.getThreads() }.associate { it.id to it.name }

    for (cpu in model.getCpuCores()) {
      val processList: MutableList<SeriesData<CpuThreadSliceInfo>> = ArrayList()
      var lastSliceEnd = cpu.schedulingEvents.firstOrNull()?.endTimestampUs ?: startUserTimeUs
      for (sched in cpu.schedulingEvents) {

        // If we have a gap, add a placeholder entry representing no threads using this cpu.
        if (sched.startTimestampUs > lastSliceEnd) {
          processList.add(SeriesData(lastSliceEnd, CpuThreadSliceInfo.NULL_THREAD))
        }

        // Some of PIDs and TIDs are not present on the process/thread lists, so we do our best to find their data here.
        val processName = model.getProcessById(sched.processId)?.getSafeProcessName() ?: ""
        // Start by checking threads in the known processes, fallback to dangling threads and again to an empty name.
        val threadName = model.getProcessById(sched.processId)?.threadById?.get(sched.threadId)?.name
                         ?: model.getDanglingThread(sched.threadId)?.name
                         ?: ""

        processList.add(
          SeriesData(sched.startTimestampUs,
                     CpuThreadSliceInfo(
                       sched.threadId, threadName,
                       sched.processId, processName,
                       sched.durationUs)))
        lastSliceEnd = sched.endTimestampUs
        if (sched.processId == mainProcessModel.id) {
          // Calculate our start time.
          val startBucket = (sched.startTimestampUs - startUserTimeUs) / UTILIZATION_BUCKET_LENGTH_US
          // The delta between this time and the end time is how much time we still need to account for in the loop.
          var sliceTimeInBucket = sched.startTimestampUs
          // Terminate on series bounds because the time given from the Model doesn't seem to be accurate.
          var i = max(0, startBucket).toInt()
          while (sched.endTimestampUs > sliceTimeInBucket && i < utilizationData.size) {
            // We want to know the time from the start of the event to the end of the bucket so we compute where our bucket ends.
            val bucketEndTime = startUserTimeUs + UTILIZATION_BUCKET_LENGTH_US * (i + 1)
            // Because the time to the end of the bucket may (and often is) longer than our total time we take the min of the two.
            val bucketTime = minOf(bucketEndTime, sched.endTimestampUs) - sliceTimeInBucket
            utilizationData[i].value += bucketTime
            sliceTimeInBucket += bucketTime
            i++
          }
        }
      }

      // We are done with this Cpu so we add a null process at the end to properly render this segment.
      processList.add(SeriesData(endUserTimeUs, CpuThreadSliceInfo.NULL_THREAD))
      schedData[cpu.id] = processList
    }

    // When we have finished processing all CPUs the utilization series contains the total time each CPU spent in each bucket.
    // Here we normalize this value across the max total wall clock time that could be spent in each bucket and end with our utilization.
    val utilizationTotalTime: Double = UTILIZATION_BUCKET_LENGTH_US * model.getCpuCores().size.toDouble()
    utilizationData.replaceAll(UnaryOperator { series: SeriesData<Long> ->
      // Normalize the utilization time as a percent form 0-1 then scale up to 0-100.
      series.value = (series.value / utilizationTotalTime * 100.0).toLong()
      series
    })

    return CpuStateData(schedData, utilizationData)
  }

  private fun buildMainProcessMemoryCountersData(mainProcessModel: ProcessModel): Map<String, List<SeriesData<Long>>> {
    return mainProcessModel.counterByName.entries
      .filter { it.key.startsWith("mem.") }
      .associate { it.key to convertCounterToSeriesData(it.value) }
      .toSortedMap()
  }

  private fun buildPowerRailCountersData(): Map<String, List<SeriesData<Long>>> {
    return model.getPowerRails().associate {
      it.name.replace("power.rails.", "") to convertCounterToSeriesData(it)
    }.toSortedMap()
  }

  private fun buildBatteryDrainCountersData(): Map<String, List<SeriesData<Long>>> {
    return model.getBatteryDrain().associate {
      it.name.replace("batt.", "") to convertCounterToSeriesData(it)
    }.toSortedMap()
  }

  private fun buildCpuCountersData(): List<Map<String, List<SeriesData<Long>>>> {
    return model.getCpuCores().map {
      cpuCoreModel -> cpuCoreModel.countersMap.asSequence().associate { it.key to convertCounterToSeriesData(it.value) }
    }
  }

  /**
   * In S+, the BLAST buffer queue replaces SurfaceFlinger buffer queue and thus we need to extract the BLAST buffer queue counter from the
   * app process.
   */
  private fun buildBlastBufferQueueCounterData(mainProcessModel: ProcessModel): List<SeriesData<Long>> {
    val counter = mainProcessModel.counterByName
                    .filterKeys { it.matches(BLAST_BUFFER_QUEUE_COUNTER_REGEX) }.values
                    .firstOrNull { it.valuesByTimestampUs.isNotEmpty() }
                  ?: return emptyList()
    return convertCounterToSeriesData(counter)
  }

  private fun convertCounterToSeriesData(counter: CounterModel): List<SeriesData<Long>> {
    return counter.valuesByTimestampUs.map { SeriesData(it.key, it.value.toLong()) }.toList()
  }
}