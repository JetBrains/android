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
import com.android.tools.profilers.cpu.CaptureNode;
import com.android.tools.profilers.cpu.CpuThreadInfo;
import com.android.tools.profilers.cpu.MethodModel;
import com.google.protobuf3jarjar.ByteString;
import com.intellij.openapi.util.io.FileUtil;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;

import static com.android.tools.profilers.cpu.CpuProfilerTestUtils.traceFileToByteString;
import static org.junit.Assert.*;

public class SimplePerfTraceParserTest {

  private SimplePerfTraceParser myParser;

  @Before
  public void setUp() throws IOException {
    ByteString traceBytes = traceFileToByteString("simpleperf.trace");
    File trace = FileUtil.createTempFile("cpu_trace", ".trace");
    try (FileOutputStream out = new FileOutputStream(trace)) {
      out.write(traceBytes.toByteArray());
    }
    myParser = new SimplePerfTraceParser(trace);
  }

  @Test
  public void samplesAndLostCountShouldMatchSimpleperfReport() throws IOException {
    myParser.parseTraceFile();
    assertEquals(32844, myParser.getSampleCount());
    assertEquals(10396, myParser.getLostSampleCount());
  }

  @Test
  public void allTreesShouldStartWithMain() throws IOException {
    myParser.parse();
    Map<CpuThreadInfo, CaptureNode> callTrees = myParser.getCaptureTrees();
    for (CaptureNode tree : callTrees.values()) {
      assertNotNull(tree.getData());
      assertEquals("main", tree.getData().getName());
    }
  }

  @Test
  public void nodeDepthsShouldBeCoherent() throws IOException {
    myParser.parse();
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
    myParser.parse();
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
    myParser = new SimplePerfTraceParser(trace);

    try {
      myParser.parse();
      fail("IllegalStateException should have been thrown due to malformed file.");
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("Malformed trace file"));
      // Do nothing. Expected exception.
    }
  }
}
