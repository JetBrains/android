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

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.DataFormatException;

/**
 * AtraceParser is a minimal implementation parsing the atrace file.
 * The class looks for the first and last lines in the file to get the total time, as well as
 * populates a minimal data structure to pass to the UI.
 * TODO: Use trebuchet to parse the atrace file and return actual objects to the UI. This
 * will require a change to the UI and how we use this object.
 */
public class AtraceParser implements TraceParser {

  private static final String PARENT_TS_MARKER = "parent_ts=";
  private double myStartCaptureMonoTimestamp;
  private double myEndCaptureMonoTimestamp;

  @Override
  public void parse(File file) throws IOException {
    AtraceDecompressor reader = new AtraceDecompressor(file);
    String line = "";
    String lastLine = line;
    double startCaptureTimestamp = 0.0;
    try {
      // Until we define our model the only thing we parse from the atrace file is the start timestamp,
      // and the end timestamp. This helps us build our range for the length of the capture.
      // Example format of a line.
      // # TASK-PID    TGID   CPU#  ||||    TIMESTAMP  FUNCTION
      // #    | |        |      |   ||||       |         |
      // atrace-1249  ( 1249) [002] ...1 66184.619718: tracing_mark_write: trace_event_clock_sync: parent_ts=16866.169922
      while ((line = reader.getNextLine()) != null) {
        lastLine = line;
        if (line.contains(PARENT_TS_MARKER)) {
          // parent_ts marker function has the format of
          // atrace-1249  ( 1249) [002] ...1 12345.619718: tracing_mark_write: trace_event_clock_sync: parent_ts=16866.169922
          // TODO: Abstract line parsing to a class, this will allow us to parse all components cleanly
          String parentTimestamp = line.substring(line.lastIndexOf("=") + 1);
          myStartCaptureMonoTimestamp = secondsToUs(Float.parseFloat(parentTimestamp));
          startCaptureTimestamp = getTimestampUs(line);
        }
      }
      assert myStartCaptureMonoTimestamp != 0;
      myEndCaptureMonoTimestamp = getTimestampUs(lastLine) - startCaptureTimestamp + myStartCaptureMonoTimestamp;
    }
    catch (DataFormatException ex) {
      ex.printStackTrace();
      throw new IOException("Invalid data format.", ex);
    }
  }

  /**
   * @param time as given to us from atrace file. Time in the atrace file is defined as
   *             systemTime(CLOCK_MONOTONIC) / 1000000000.0f returning the time in fractions of a second.
   * @return time converted to Us.
   */
  private double secondsToUs(float time) {
    return (time * 1000000.0f);
  }

  /**
   * @param line from atrace file.
   *             an example line is: atrace-1249  ( 1249) [002] ...1 66184.619718: tracing_mark_write: trace_event_clock_sync: parent_ts=16866.169922
   * @return time as double converted to Us.
   */
  private double getTimestampUs(String line) {
    String lineTimestamp = line.substring(0, line.indexOf(":"));
    lineTimestamp = lineTimestamp.substring(lineTimestamp.lastIndexOf(" "));
    return secondsToUs(Float.parseFloat(lineTimestamp));
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
    return new Range(myStartCaptureMonoTimestamp, myEndCaptureMonoTimestamp);
  }
}
