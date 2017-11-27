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
package com.android.tools.profilers.cpu.simpleperf;

import com.android.tools.adtui.model.HNode;
import com.android.tools.adtui.model.Range;
import com.android.tools.profilers.cpu.CaptureNode;
import com.android.tools.profilers.cpu.CpuThreadInfo;
import com.android.tools.profilers.cpu.nodemodel.MethodModel;
import com.google.protobuf3jarjar.ByteString;
import com.intellij.openapi.util.io.FileUtil;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.android.tools.profilers.cpu.CpuProfilerTestUtils.traceFileToByteString;
import static org.junit.Assert.*;

public class SimpleperfTraceParserTest {

  private SimpleperfTraceParser myParser;

  private File myTraceFile;

  @Before
  public void setUp() throws IOException {
    ByteString traceBytes = traceFileToByteString("simpleperf.trace");
    File trace = FileUtil.createTempFile("cpu_trace", ".trace");
    try (FileOutputStream out = new FileOutputStream(trace)) {
      out.write(traceBytes.toByteArray());
    }
    myTraceFile = trace;
    myParser = new SimpleperfTraceParser();
  }

  @Test
  public void samplesAndLostCountShouldMatchSimpleperfReport() throws IOException {
    myParser.parseTraceFile(myTraceFile);
    assertEquals(32844, myParser.getSampleCount());
    assertEquals(10396, myParser.getLostSampleCount());
  }

  @Test
  public void allTreesShouldStartWithThreadName() throws IOException {
    myParser.parse(myTraceFile);
    Map<CpuThreadInfo, CaptureNode> callTrees = myParser.getCaptureTrees();

    for (Map.Entry<CpuThreadInfo, CaptureNode> entry : callTrees.entrySet()) {
      CaptureNode tree = entry.getValue();
      assertNotNull(tree.getData());
      assertEquals(entry.getKey().getName(), tree.getData().getName());
    }
  }

  @Test
  public void checkKnownThreadsPresenceAndCount() throws IOException {
    myParser.parse(myTraceFile);
    Map<CpuThreadInfo, CaptureNode> callTrees = myParser.getCaptureTrees();

    assertFalse(callTrees.values().isEmpty());

    // Studio:Heartbeat
    int studioHeartbeatCount = 0;
    // displayingbitmaps
    int displayingBitmapsCount = 0;
    // Studio:Agent
    int studioAgentCount = 0;
    // JVMTI Agent thread
    int jvmtiAgentCount = 0;

    for (Map.Entry<CpuThreadInfo, CaptureNode> tree : callTrees.entrySet()) {
      String thread = tree.getKey().getName();
      // Using contains instead of equals because native thread names are limited to 15 characters
      // and there is no way to predict where they are going to be trimmed.
      if ("Studio:Heartbeat".contains(thread)) {
        studioHeartbeatCount++;
        // libperfa should be the entry point
        assertTrue(tree.getValue().getChildAt(0).getData().getName().startsWith("libperfa.so"));
      }
      else if ("displayingbitmaps".contains(thread)) {
        displayingBitmapsCount++;
        // libperfa should be the entry point
        assertTrue(tree.getValue().getChildAt(0).getData().getName().startsWith("libperfa.so"));
      }
      else if ("Studio:Agent".contains(thread)) {
        studioAgentCount++;
        // libperfa should be the entry point
        assertTrue(tree.getValue().getChildAt(0).getData().getName().startsWith("libperfa.so"));
      }
      else if ("JVMTI Agent thread".contains(thread)) {
        jvmtiAgentCount++;
        // libperfa should be the entry point
        assertTrue(tree.getValue().getChildAt(0).getData().getName().startsWith("libperfa.so"));
      }
    }

    assertEquals(1, studioHeartbeatCount);
    assertEquals(1, displayingBitmapsCount);
    assertEquals(1, studioAgentCount);
    assertEquals(1, jvmtiAgentCount);
  }

  @Test
  public void nodeDepthsShouldBeCoherent() throws IOException {
    myParser.parse(myTraceFile);
    CaptureNode anyTree = myParser.getCaptureTrees().values().iterator().next();
    assertEquals(0, anyTree.getDepth());

    // Just go as deep as possible in one branch per child and check the depths of each node in the branch
    for (HNode<MethodModel> child : anyTree.getChildren()) {
      int depth = 1;
      HNode<MethodModel> node = child;
      while (node != null) {
        assertEquals(depth++, node.getDepth());
        node = node.getFirstChild();
      }
    }
  }

  @Test
  public void mainProcessShouldBePresent() throws IOException {
    myParser.parse(myTraceFile);
    CaptureNode mainThread = myParser.getCaptureTrees().entrySet().stream()
      .filter(entry -> entry.getKey().getId() == 24358 /* App pid */)
      .map(Map.Entry::getValue)
      .findAny()
      .orElse(null);
    assertNotNull(mainThread);
  }

  @Test
  public void fileIdsShouldBeMappedToAnExistingFile() throws IOException {
    ByteString traceBytes = traceFileToByteString("simpleperf_malformed.trace");
    File trace = FileUtil.createTempFile("cpu_trace", ".trace");
    try (FileOutputStream out = new FileOutputStream(trace)) {
      out.write(traceBytes.toByteArray());
    }
    myParser = new SimpleperfTraceParser();

    try {
      myParser.parse(trace);
      fail("IllegalStateException should have been thrown due to missing file.");
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("Malformed trace file"));
      // Do nothing. Expected exception.
    }
  }

  @Test
  public void rangeShouldBeFromFirstToLastTimestamp() throws IOException {
    myParser.parse(myTraceFile);
    long startTimeUs = TimeUnit.NANOSECONDS.toMicros(myParser.mySamples.get(0).getTime());
    long endTimeUs = TimeUnit.NANOSECONDS.toMicros( myParser.mySamples.get(myParser.mySamples.size() - 1).getTime());
    Range expected = new Range(startTimeUs, endTimeUs);
    assertEquals(expected.getMin(), myParser.getRange().getMin(), 0);
    assertEquals(expected.getMax(), myParser.getRange().getMax(), 0);
  }
}
