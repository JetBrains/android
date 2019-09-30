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

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.perflib.vmtrace.ClockType;
import org.junit.Test;

import java.io.IOException;

public class CaptureNodeTest {

  @Test
  public void captureNodeSpecificMethods() throws IOException {
    CaptureNode node = new CaptureNode(new StubCaptureNodeModel());
    assertThat(node.getClockType()).isEqualTo(ClockType.GLOBAL);

    node.setStartThread(3);
    node.setEndThread(5);
    node.setStartGlobal(3);
    node.setEndGlobal(13);

    assertThat(node.getStartThread()).isEqualTo(3);
    assertThat(node.getEndThread()).isEqualTo(5);
    assertThat(node.getStartGlobal()).isEqualTo(3);
    assertThat(node.getEndGlobal()).isEqualTo(13);

    assertThat(node.threadGlobalRatio()).isWithin(0.0001).of(0.2);
  }

  @Test
  public void hNodeApiMethods() throws IOException {
    CaptureNode node = new CaptureNode(new StubCaptureNodeModel());

    node.setStartThread(0);
    node.setEndThread(10);
    node.setStartGlobal(20);
    node.setEndGlobal(50);

    assertThat(node.getClockType()).isEqualTo(ClockType.GLOBAL);
    assertThat(node.getStart()).isEqualTo(20);
    assertThat(node.getEnd()).isEqualTo(50);
    assertThat(node.getDuration()).isEqualTo(30);

    node.setClockType(ClockType.THREAD);
    assertThat(node.getClockType()).isEqualTo(ClockType.THREAD);
    assertThat(node.getStart()).isEqualTo(0);
    assertThat(node.getEnd()).isEqualTo(10);
    assertThat(node.getDuration()).isEqualTo(10);
  }

  @Test
  public void addChild() {
    CaptureNode realParent = new CaptureNode(new StubCaptureNodeModel());
    CaptureNode childA = new CaptureNode(new StubCaptureNodeModel());
    VisualNodeCaptureNode visualParent = new VisualNodeCaptureNode(new StubCaptureNodeModel());

    realParent.addChild(childA);

    assertThat(childA.getParent()).isEqualTo(realParent);
    assertThat(realParent.getChildAt(0)).isEqualTo(childA);

    visualParent.addChild(childA);
    assertThat(childA.getParent()).isEqualTo(realParent);
    assertThat(realParent.getChildAt(0)).isEqualTo(childA);
    assertThat(visualParent.getChildAt(0)).isEqualTo(childA);
  }
}