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
import trebuchet.task.ImportTask;
import trebuchet.util.PrintlnImportFeedback;

import java.io.*;
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

  @Override
  public void parse(File file) throws IOException {
    AtraceDecompressor reader = new AtraceDecompressor(file);
    ImportTask task = new ImportTask(new PrintlnImportFeedback());
    myModel = task.importBuffer(reader);
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
    // TODO: Implement, for now we return a top level node that the UI expects.
    // Without this the UI crashes or is left in a bad state. Currently this feature is hidden behind a flag.
    CpuThreadInfo cti = new CpuThreadInfo(0, "main");
    CaptureNode cn = new CaptureNode();
    cn.setMethodModel(new MethodModel("main", "fake", "fake::main", "::"));
    HashMap<CpuThreadInfo, CaptureNode> captureTreeNodes = new HashMap<>();
    captureTreeNodes.put(cti, cn);
    return captureTreeNodes;
  }

  @Override
  public Range getRange() {
    double startTimestampUs = secondsToUs(myModel.getParentTimestamp());
    double endTimestampUs = secondsToUs((myModel.getEndTimestamp() - myModel.getBeginTimestamp()) + myModel.getParentTimestamp());
    return new Range(startTimestampUs, endTimestampUs);
  }
}
