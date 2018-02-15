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
package com.android.tools.profilers.cpu.atrace;

import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profilers.cpu.*;
import com.android.tools.profilers.cpu.nodemodel.SingleNameModel;
import org.jetbrains.annotations.NotNull;
import trebuchet.model.*;
import trebuchet.model.base.SliceGroup;
import trebuchet.task.ImportTask;
import trebuchet.util.PrintlnImportFeedback;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * AtraceParser is a minimal implementation parsing the atrace file.
 * The class looks for the first and last lines in the file to get the total time, as well as
 * populates a minimal data structure to pass to the UI.
 * Trebuchet is our parser for atrace (systrace) raw data.
 */
public class AtraceParser implements TraceParser {

  /**
   * Map of CpuThreadInfo to capture nodes. The thread info in this map does not contain process information.
   */
  private final Map<CpuThreadInfo, CaptureNode> myCaptureTreeNodes;

  /**
   * Map between processor ids and its set of thread changes.
   * Note: In kernel space all user space processes are treated at threads, where each thread has a TGID (Thread group ID).
   * The TGID is the main thread of a user space process. All references to processes outside this class refer to user space
   * processes while threads refer to threads within those processes.
   */
  private final Map<Integer, List<SeriesData<CpuThreadInfo>>> myCpuSchedulingToCpuData;

  /**
   * Map between thread id, and the thread state for each state transition on that thread.
   */
  private final Map<Integer, List<SeriesData<CpuProfilerStage.ThreadState>>> myThreadStateData;

  /**
   * List of cpu utilization values for a specific process. The values range from 0 -> 100 in increments of CPU count.
   * The value needs to be a long as that is what {@link com.android.tools.adtui.model.RangedContinuousSeries} expects.
   */
  private final List<SeriesData<Long>> myCpuUtilizationSeries;

  private int myProcessId;
  private ProcessModel myProcessModel;
  // Trebuchet.Model is what Trebuchet uses to represent all captured data.
  private Model myModel;
  private Range myRange;

  public AtraceParser(int processId) {
    myProcessId = processId;
    myCaptureTreeNodes = new HashMap<>();
    myThreadStateData = new HashMap<>();
    myCpuSchedulingToCpuData = new HashMap<>();
    myCpuUtilizationSeries = new LinkedList<>();
  }

  @Override
  public CpuCapture parse(File file, int traceId) throws IOException {
    AtraceDecompressor reader = new AtraceDecompressor(file);
    ImportTask task = new ImportTask(new PrintlnImportFeedback());
    myModel = task.importBuffer(reader);
    double startTimestampUs = convertToUserTimeUs(myModel.getBeginTimestamp());
    double endTimestampUs = convertToUserTimeUs(myModel.getEndTimestamp());
    myRange = new Range(startTimestampUs, endTimestampUs);
    myProcessModel = myModel.getProcesses().get(myProcessId);

    // TODO (b/69910215): Handle case capture does not contain process we are looking for.
    assert myProcessModel != null;
    buildCaptureTreeNodes();
    buildThreadStateData();
    buildCpuStateData();
    return new AtraceCpuCapture(this, traceId);
  }

  @Override
  public boolean supportsDualClock() {
    return false;
  }

  /**
   * @param time as given to us from atrace file. Time in the atrace file is defined as
   *             systemTime(CLOCK_MONOTONIC) / 1000000000.0f returning the time in fractions of a second.
   * @return time converted to Us.
   */
  private double secondsToUs(double time) {
    return (time * 1000000.0);
  }

  @Override
  public Map<CpuThreadInfo, CaptureNode> getCaptureTrees() {
    return myCaptureTreeNodes;
  }

  @NotNull
  public Map<Integer, List<SeriesData<CpuProfilerStage.ThreadState>>> getThreadStateDataSeries() {
    return myThreadStateData;
  }

  @NotNull
  public Map<Integer, List<SeriesData<CpuThreadInfo>>> getCpuThreadInfoStates() {
    return myCpuSchedulingToCpuData;
  }

  @NotNull
  public List<SeriesData<Long>> getCpuUtilizationSeries() {
    return myCpuUtilizationSeries;
  }

  /**
   * @return Returns a map of {@link CpuThreadInfo} to {@link CaptureNode}. The capture nodes are built from {@link SliceGroup} maintaining
   * the order and hierarchy.
   */
  private void buildCaptureTreeNodes() {
    Range range = getRange();
    for (ThreadModel thread : myProcessModel.getThreads()) {
      CpuThreadInfo threadInfo = new CpuThreadInfo(thread.getId(), thread.getName());
      CaptureNode root = new CaptureNode(new SingleNameModel(thread.getName()));
      root.setStartGlobal((long)range.getMin());
      root.setEndGlobal((long)range.getMax());
      myCaptureTreeNodes.put(threadInfo, root);
      for (SliceGroup slice : thread.getSlices()) {
        CaptureNode node = populateCaptureNode(slice, 0);
        root.addChild(node);
      }
    }
  }

  /**
   * Recursive function that builds a tree of {@link CaptureNode} from a {@link SliceGroup}
   *
   * @param slice to convert to a {@link CaptureNode}. This method will be recursively called on all children.
   * @param depth to current node. Depth starts at 0
   * @return The {@link CaptureNode} that mirrors the {@link SliceGroup} passed in.
   */
  private CaptureNode populateCaptureNode(SliceGroup slice, int depth) {
    CaptureNode node = new CaptureNode(new SingleNameModel(slice.getName()));
    node.setStartGlobal(convertToUserTimeUs(slice.getStartTime()));
    node.setEndGlobal(convertToUserTimeUs(slice.getEndTime()));
    node.setDepth(depth);
    for (SliceGroup child : slice.getChildren()) {
      node.addChild(populateCaptureNode(child, depth + 1));
    }
    return node;
  }

  /**
   * Builds a map of thread id to a list of {@link CpuProfilerStage.ThreadState} series.
   */
  private void buildThreadStateData() {
    for (ThreadModel thread : myProcessModel.getThreads()) {
      List<SeriesData<CpuProfilerStage.ThreadState>> states = new ArrayList<>();
      myThreadStateData.put(thread.getId(), states);
      CpuProfilerStage.ThreadState lastState = CpuProfilerStage.ThreadState.UNKNOWN;
      for (SchedSlice slice : thread.getSchedSlices()) {
        long startTimeUs = convertToUserTimeUs(slice.getStartTime());
        CpuProfilerStage.ThreadState state = getState(slice);
        if (state != lastState) {
          states.add(new SeriesData<>(startTimeUs, state));
          lastState = state;
        }
      }
    }
  }

  /**
   * Builds a map of CPU ids to a list of {@link CpuThreadInfo} series. While building the CPU map it also builds a CPU utilization series.
   */
  private void buildCpuStateData() {
    // Add initial value to start of series for proper visualization.
    myCpuUtilizationSeries.add(new SeriesData<>(convertToUserTimeUs(myModel.getBeginTimestamp()), 0L));
    for (CpuModel cpu : myModel.getCpus()) {
      ListIterator<SeriesData<Long>> cpuSeriesIt = myCpuUtilizationSeries.listIterator();
      List<SeriesData<CpuThreadInfo>> processList = new ArrayList<>();
      CpuProcessSlice lastSlice = cpu.getSlices().get(0);
      for (CpuProcessSlice slice : cpu.getSlices()) {
        Long sliceStartTimeUs = convertToUserTimeUs(slice.getStartTime());
        Long sliceEndTimeUs = convertToUserTimeUs(slice.getEndTime());
        if (slice.getStartTime() > lastSlice.getEndTime()) {
          processList.add(new SeriesData<>(convertToUserTimeUs(lastSlice.getEndTime()), CpuThreadInfo.NULL_THREAD));
        }
        processList.add(new SeriesData<>(sliceStartTimeUs,
                                         new CpuThreadInfo(slice.getThreadId(), slice.getThreadName(), slice.getId(),
                                                           slice.getName())));
        lastSlice = slice;

        // While looping the process slices we build our CPU utilization graph so we don't need to loop the same data twice.
        if (slice.getId() == myProcessId) {
          buildCpuUtilizationData(cpuSeriesIt, sliceStartTimeUs, sliceEndTimeUs);
        }
      }

      // We are done with this Cpu so we add a null process at the end to properly render this segment.
      processList.add(new SeriesData<>(convertToUserTimeUs(myModel.getEndTimestamp()), CpuThreadInfo.NULL_THREAD));
      myCpuSchedulingToCpuData.put(cpu.getId(), processList);
    }

    // When we are done we have a count of how many processes are running at any time. Here we convert that count to % of CPU used.
    myCpuUtilizationSeries.replaceAll((series) -> {
      series.value *= (long)(100 / (myModel.getCpus().size() * 1.0));
      return series;
    });
  }

  /**
   * Helper function to build CPU utilization data series. This function loops the current cpu utilization series looking for
   * the first element that is greater than our process start time and adds a new entry before that point. It then increments the value
   * of each series element following. The iteration continues until it finds the first element greater than our process end time.
   * A new series object is inserted just before this point. The result of this is the initial list is modified to have 2 elements added
   * with each element in the middle incremented by one.
   *
   * The start and end times represent a slice when the core is used continuously, and the core's utilization data before the start
   * timestamp has been captured by cpuSeriesIt.
   *
   * @param cpuSeriesIt        An iterator to the list of utilization series data. This function will use the iterator in the current state and
   *                           not reset it.
   * @param sliceStartTimeUs The converted time of the slice start.
   * @param sliceEndTimeUs   The converted time of the slice end.
   * @return
   */
  @NotNull
  private SeriesData<Long> buildCpuUtilizationData(ListIterator<SeriesData<Long>> cpuSeriesIt,
                                                   Long sliceStartTimeUs,
                                                   Long sliceEndTimeUs) {
    // Assume our CPU usage is 0, and we are changing it to 1.
    Long usedCpuCount = 1L;
    SeriesData<Long> last = null;
    if (cpuSeriesIt.hasPrevious()) {
      last = cpuSeriesIt.previous();
    }
    // Move a pointer through our current series looking for where we start beyond our current process.
    // Process =     [xxxxxx]
    // Series = |-------| |------|
    // Pointer  ^  ->   ^
    while (cpuSeriesIt.hasNext()) {
      SeriesData<Long> current = cpuSeriesIt.next();
      if (current.x > sliceStartTimeUs) {
        // Note: previous returns the current node but it sets the index to be the previous index.
        // If we wanted the previous element without storing last we need to call previous twice.
        cpuSeriesIt.previous();
        // Last can be null happen if the first element is greater than our process start time.
        if (last != null) {
          usedCpuCount = last.value + 1;
        }
        break;
      }
      last = current;
    }
    // Add a new entry into our series for the current process start time.
    // Using the example above, we can see that we add an entry for the new process.
    // Process =     [xxxxxx]
    // Series = |----|--| |------|
    // Pointer       ^
    SeriesData<Long> startProcessPoint = new SeriesData<>(sliceStartTimeUs, usedCpuCount);
    cpuSeriesIt.add(startProcessPoint);
    last = startProcessPoint;
    // Now we search for a time greater than our end time, and update the process count of each element along the way.
    // Process =     [xxxxxx]
    // Series = |----|--| |------|
    // Pointer                   ^
    while (cpuSeriesIt.hasNext()) {
      SeriesData<Long> current = cpuSeriesIt.next();
      if (current.x > sliceEndTimeUs) {
        cpuSeriesIt.previous();
        usedCpuCount = last.value;
        break;
      }
      current.value++;
      usedCpuCount = current.value;
      cpuSeriesIt.set(current);
      last = current;
    }

    //Decrement current CPU count back to last CPU count and add element to our series.
    usedCpuCount--;
    SeriesData<Long> endProcessPoint = new SeriesData<>(sliceEndTimeUs, usedCpuCount);
    cpuSeriesIt.add(endProcessPoint);
    last = endProcessPoint;
    return last;
  }

  /**
   * @return converted state from the input slice.
   */
  private CpuProfilerStage.ThreadState getState(SchedSlice slice) {
    switch (slice.getState()) {
      case RUNNING:
        return CpuProfilerStage.ThreadState.RUNNING_CAPTURED;
      case WAKING:
      case RUNNABLE:
        return CpuProfilerStage.ThreadState.RUNNABLE_CAPTURED;
      case EXIT_DEAD:
        return CpuProfilerStage.ThreadState.DEAD_CAPTURED;
      case SLEEPING:
        return CpuProfilerStage.ThreadState.SLEEPING_CAPTURED;
      case UNINTR_SLEEP:
        return CpuProfilerStage.ThreadState.WAITING_CAPTURED;
      case UNINTR_SLEEP_IO:
        return CpuProfilerStage.ThreadState.WAITING_IO_CAPTURED;
      default:
        return CpuProfilerStage.ThreadState.UNKNOWN;
    }
  }

  @Override
  public Range getRange() {
    return myRange;
  }

  private long convertToUserTimeUs(double offsetTime) {
    return (long)secondsToUs((offsetTime - myModel.getBeginTimestamp()) + myModel.getParentTimestamp());
  }
}
