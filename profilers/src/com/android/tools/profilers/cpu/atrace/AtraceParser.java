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
import com.android.tools.profilers.cpu.CaptureNode;
import com.android.tools.profilers.cpu.CpuThreadInfo;
import com.android.tools.profilers.cpu.MethodModel;
import com.android.tools.profilers.cpu.TraceParser;
import trebuchet.model.Model;
import trebuchet.model.ProcessModel;
import trebuchet.model.ThreadModel;
import trebuchet.model.base.SliceGroup;
import trebuchet.task.ImportTask;
import trebuchet.util.PrintlnImportFeedback;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * AtraceParser is a minimal implementation parsing the atrace file.
 * The class looks for the first and last lines in the file to get the total time, as well as
 * populates a minimal data structure to pass to the UI.
 */
public class AtraceParser implements TraceParser {

  // Trebuchet is our parser for atrace (systrace) raw data. trebuchet.Model is what Trebuchet uses to represent captured data."
  private Model myModel;
  private HashMap<CpuThreadInfo, CaptureNode> myCaptureTreeNodes = new HashMap<>();
  private int myProcessId;

  public AtraceParser(int processId) {
    myProcessId = processId;
  }

  @Override
  public void parse(File file) throws IOException {
    AtraceDecompressor reader = new AtraceDecompressor(file);
    ImportTask task = new ImportTask(new PrintlnImportFeedback());
    myModel = task.importBuffer(reader);
    myCaptureTreeNodes = buildCaptureTreeNodes();
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

  private HashMap<CpuThreadInfo, CaptureNode> buildCaptureTreeNodes() {
    ProcessModel selectedProcess = null;

    // TODO: Remove when getProcesses returns a Hashset.
    for (ProcessModel process : myModel.getProcesses()) {
      if (process.getId() == myProcessId) {
        selectedProcess = process;
        break;
      }
    }

    HashMap<CpuThreadInfo, CaptureNode> captureTreeNodes = new HashMap<>();
    if (selectedProcess != null) {
      Range range = getRange();
      for (ThreadModel thread : selectedProcess.getThreads()) {
        if (thread.getHasContent()) {
          CpuThreadInfo threadInfo = new CpuThreadInfo(thread.getId(), thread.getName());
          CaptureNode root = new CaptureNode();
          root.setMethodModel(new MethodModel.Builder("root").setNativeNamespaceAndClass("root").setParameters("").build());
          root.setStartGlobal((long)range.getMin());
          root.setEndGlobal((long)range.getMax());
          captureTreeNodes.put(threadInfo, root);
          for (SliceGroup slice : thread.getSlices()) {
            CaptureNode node = populateCaptureNode(slice, 0);
            root.addChild(node);
          }
        }
      }
    }
    return captureTreeNodes;
  }

  private CaptureNode populateCaptureNode(SliceGroup slice, int depth) {
    CaptureNode node = new CaptureNode();
    node.setMethodModel(new MethodModel.Builder(slice.getName()).setNativeNamespaceAndClass(slice.getName()).setParameters("").build());
    node.setStartGlobal(convertToUserTimeUs(slice.getStartTime()));
    node.setEndGlobal(convertToUserTimeUs(slice.getEndTime()));
    node.setDepth(depth);
    for (SliceGroup child : slice.getChildren()) {
      node.addChild(populateCaptureNode(child, depth + 1));
    }
    return node;
  }

  @Override
  public Range getRange() {
    double startTimestampUs = convertToUserTimeUs(myModel.getBeginTimestamp());
    double endTimestampUs = convertToUserTimeUs(myModel.getEndTimestamp());
    return new Range(startTimestampUs, endTimestampUs);
  }

  private long convertToUserTimeUs(double offsetTime) {
    return (long)secondsToUs( (offsetTime - myModel.getBeginTimestamp()) + myModel.getParentTimestamp());
  }
}
