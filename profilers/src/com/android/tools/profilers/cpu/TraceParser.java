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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.model.Range;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * Parses a trace file into a {@link Map<CpuThreadInfo, CaptureNode>}.
 * Also provides a method to get the trace capture range, and trace state data.
 */
public interface TraceParser {

  CpuCapture parse(File file) throws IOException;

  Map<CpuThreadInfo, CaptureNode> getCaptureTrees();

  Range getRange();

  /**
   * @return Whether the parser supports dual clock, i.e.
   * if the method trace data can be displayed in both thread and wall-clock time.
   */
  boolean supportsDualClock();
}
