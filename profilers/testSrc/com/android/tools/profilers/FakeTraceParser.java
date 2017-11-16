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
package com.android.tools.profilers;

import com.android.tools.adtui.model.Range;
import com.android.tools.profilers.cpu.CaptureNode;
import com.android.tools.profilers.cpu.CpuCapture;
import com.android.tools.profilers.cpu.CpuThreadInfo;
import com.android.tools.profilers.cpu.TraceParser;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * This class is used in test when the test wants to return specific data from a capture.
 * This class is primarily passed to {@link CpuCapture}
 */
public class FakeTraceParser implements TraceParser {
  private Range myCaptureRange;
  private Map<CpuThreadInfo, CaptureNode> myCaptureTrees;
  private boolean myDualClock;

  public FakeTraceParser(Range captureRange,
                         Map<CpuThreadInfo, CaptureNode> captureTrees,
                         boolean isDualClock) {
    myCaptureRange = captureRange;
    myCaptureTrees = captureTrees;
    myDualClock = isDualClock;
  }

  @Override
  public CpuCapture parse(File file) {
    return new CpuCapture(this);
  }

  @Override
  public Map<CpuThreadInfo, CaptureNode> getCaptureTrees() {
    return myCaptureTrees;
  }

  @Override
  public Range getRange() {
    return myCaptureRange;
  }

  @Override
  public boolean supportsDualClock() {
    return myDualClock;
  }
}
