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

import com.android.tools.perflib.vmtrace.ClockType;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class CaptureNodeTest {

  @Test
  public void captureNodeSpecificMethods() throws IOException {
    CaptureNode node = new CaptureNode(new StubCaptureNodeModel());
    assertEquals(ClockType.GLOBAL, node.getClockType());

    node.setStartThread(3);
    node.setEndThread(5);
    node.setStartGlobal(3);
    node.setEndGlobal(13);

    assertEquals(3, node.getStartThread());
    assertEquals(5, node.getEndThread());
    assertEquals(3, node.getStartGlobal());
    assertEquals(13, node.getEndGlobal());

    assertEquals(0.2, node.threadGlobalRatio(), 0.0001);
  }

  @Test
  public void hNodeApiMethods() throws IOException {
    CaptureNode node = new CaptureNode(new StubCaptureNodeModel());

    node.setStartThread(0);
    node.setEndThread(10);
    node.setStartGlobal(20);
    node.setEndGlobal(50);

    assertEquals(ClockType.GLOBAL, node.getClockType());
    assertEquals(20, node.getStart());
    assertEquals(50, node.getEnd());
    assertEquals(30, node.getDuration());

    node.setClockType(ClockType.THREAD);
    assertEquals(ClockType.THREAD, node.getClockType());
    assertEquals(0, node.getStart());
    assertEquals(10, node.getEnd());
    assertEquals(10, node.getDuration());
  }
}