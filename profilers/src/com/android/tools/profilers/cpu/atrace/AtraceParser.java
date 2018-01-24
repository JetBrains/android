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
import trebuchet.model.Model;
import trebuchet.model.ProcessModel;
import trebuchet.model.SchedSlice;
import trebuchet.model.ThreadModel;
import trebuchet.model.base.SliceGroup;
import trebuchet.task.ImportTask;
import trebuchet.util.PrintlnImportFeedback;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AtraceParser is a minimal implementation parsing the atrace file.
 * The class looks for the first and last lines in the file to get the total time, as well as
 * populates a minimal data structure to pass to the UI.
 */
public class AtraceParser implements TraceParser {

  // Trebuchet is our parser for atrace (systrace) raw data. trebuchet.Model is what Trebuchet uses to represent captured data."
  private HashMap<CpuThreadInfo, CaptureNode> myCaptureTreeNodes = new HashMap<>();
  private Map<Integer, List<SeriesData<CpuProfilerStage.ThreadState>>> myThreadStateData;
  private int myProcessId;
  private ProcessModel myProcessModel;
  private Model myModel;
  private Range myRange;

  public AtraceParser(int processId) {
    myProcessId = processId;
  }

  @Override
  public CpuCapture parse(File file) throws IOException {
    AtraceDecompressor reader = new AtraceDecompressor(file);
    ImportTask task = new ImportTask(new PrintlnImportFeedback());
    myModel = task.importBuffer(reader);
    double startTimestampUs = convertToUserTimeUs(myModel.getBeginTimestamp());
    double endTimestampUs = convertToUserTimeUs(myModel.getEndTimestamp());
    myRange = new Range(startTimestampUs, endTimestampUs);
    myProcessModel = myModel.getProcesses().get(myProcessId);
    
    // TODO (b/69910215): Handle case capture does not contain process we are looking for.
    assert myProcessModel != null;
    myCaptureTreeNodes = buildCaptureTreeNodes();
    buildThreadStateData();
    return new AtraceCpuCapture(this);
  }

  @Override
  public boolean supportsDualClock() {
    return true;
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


  public Map<Integer, List<SeriesData<CpuProfilerStage.ThreadState>>> getThreadStateDataSeries() {
    return myThreadStateData;
  }

  /**
   * @return Returns a map of {@link CpuThreadInfo} to {@link CaptureNode}. The capture nodes are built from {@link SliceGroup} maintaining
   * the order and hierarchy.
   */
  private HashMap<CpuThreadInfo, CaptureNode> buildCaptureTreeNodes() {
    HashMap<CpuThreadInfo, CaptureNode> captureTreeNodes = new HashMap<>();
    Range range = getRange();
    for (ThreadModel thread : myProcessModel.getThreads()) {
      CpuThreadInfo threadInfo = new CpuThreadInfo(thread.getId(), thread.getName());
      CaptureNode root = new CaptureNode(new SingleNameModel(thread.getName()));
      root.setStartGlobal((long)range.getMin());
      root.setEndGlobal((long)range.getMax());
      captureTreeNodes.put(threadInfo, root);
      for (SliceGroup slice : thread.getSlices()) {
        CaptureNode node = populateCaptureNode(slice, 0);
        root.addChild(node);
      }
    }
    return captureTreeNodes;
  }

  /**
   * Recursive function that builds a tree of {@link CaptureNode} from a {@link SliceGroup}
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
    myThreadStateData = new HashMap<>();
    for (ThreadModel thread : myProcessModel.getThreads()) {
      List<SeriesData<CpuProfilerStage.ThreadState>> states = new ArrayList<>();
      myThreadStateData.put(thread.getId(), states);
      CpuProfilerStage.ThreadState lastState = CpuProfilerStage.ThreadState.UNKNOWN;
      for(SchedSlice slice : thread.getSchedSlices()) {
        long startTimeUs= convertToUserTimeUs(slice.getStartTime());
        CpuProfilerStage.ThreadState state = getState(slice);
        if ( state != lastState) {
          states.add(new SeriesData<>(startTimeUs, state));
          lastState = state;
        }
      }
    }
  }

  /**
   * @return converted state from the input slice.
   */
  private CpuProfilerStage.ThreadState getState(SchedSlice slice) {
    switch (slice.getState()) {
      case RUNNING:
      case RUNNABLE:
      case WAKING:
        return CpuProfilerStage.ThreadState.RUNNING_CAPTURED;
      case EXIT_DEAD:
        return CpuProfilerStage.ThreadState.DEAD_CAPTURED;
      case SLEEPING:
        return CpuProfilerStage.ThreadState.SLEEPING_CAPTURED;
      case UNINTR_SLEEP:
        // TODO (b/72498194) Add a new ThreadState for waiting on IO.
      case UNINTR_SLEEP_IO:
        return CpuProfilerStage.ThreadState.WAITING_CAPTURED;
      default:
        return CpuProfilerStage.ThreadState.UNKNOWN;
    }
  }

  @Override
  public Range getRange() {
    return myRange;
  }

  private long convertToUserTimeUs(double offsetTime) {
    return (long)secondsToUs( (offsetTime - myModel.getBeginTimestamp()) + myModel.getParentTimestamp());
  }
}
