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
package com.android.tools.profilers.visualtests;

import com.android.testutils.TestUtils;
import com.android.tools.adtui.AnimatedComponent;
import com.android.tools.adtui.chart.hchart.HTreeChart;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.updater.Updatable;
import com.android.tools.adtui.visualtests.VisualTest;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profilers.cpu.*;
import com.android.tools.profilers.cpu.art.ArtTraceParser;
import com.android.tools.profilers.cpu.nodemodel.CaptureNodeModel;
import com.android.tools.profilers.cpu.simpleperf.SimpleperfTraceParser;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Displays an ART {@link HTreeChart} with Java methods and a simpleperf one with both java and native methods.
 */
public class CaptureNodeModelRendererVisualTest extends VisualTest {

  private static final String TEST_RESOURCE_DIR = "tools/adt/idea/profilers-ui/testData/visualtests/";

  private HTreeChart<CaptureNodeModel> myArtChart;
  private HTreeChart<CaptureNodeModel> mySimpleperfChart;
  private Range myArtRange = new Range();
  private Range mySimpleperfRange = new Range();

  @Override
  protected List<Updatable> createModelList() {
    return Collections.emptyList();
  }

  @Override
  protected List<AnimatedComponent> getDebugInfoComponents() {
    return Arrays.asList(myArtChart, mySimpleperfChart);
  }

  @Override
  protected void populateUi(@NotNull JPanel panel) {
    CaptureNode artNode = parseArtTraceAndGetHNode();
    myArtRange.set(artNode.getStart(), artNode.getEnd());
    myArtChart = new HTreeChart<>(null, myArtRange, HTreeChart.Orientation.TOP_DOWN);
    myArtChart.setHRenderer(new CaptureNodeModelHRenderer());
    myArtChart.setHTree(artNode);

    CaptureNode simpleperfNode = parseSimpleperfTraceAndGetHNode();
    mySimpleperfRange.set(simpleperfNode.getStart(), simpleperfNode.getEnd());
    mySimpleperfChart = new HTreeChart<>(null,mySimpleperfRange, HTreeChart.Orientation.TOP_DOWN);
    mySimpleperfChart.setHRenderer(new CaptureNodeModelHRenderer());
    mySimpleperfChart.setHTree(simpleperfNode);

    panel.setLayout(new GridLayout(2, 1));
    panel.add(myArtChart);
    panel.add(mySimpleperfChart);
  }

  private static CaptureNode parseArtTraceAndGetHNode() {
    return parseTraceAndGetHNode("cpu_trace.trace", "main", CpuProfiler.CpuProfilerType.ART);
  }

  private static CaptureNode parseSimpleperfTraceAndGetHNode() {
    return parseTraceAndGetHNode("simpleperf_trace.trace", "splayingbitmaps", CpuProfiler.CpuProfilerType.SIMPLEPERF);
  }

  private static CaptureNode parseTraceAndGetHNode(String traceFile, String nodeName,
                                                   CpuProfiler.CpuProfilerType profilerType) {
    File file = TestUtils.getWorkspaceFile(TEST_RESOURCE_DIR + traceFile);
    TraceParser parser;
    if (profilerType == CpuProfiler.CpuProfilerType.ART) {
      parser = new ArtTraceParser();
    }
    else if (profilerType == CpuProfiler.CpuProfilerType.SIMPLEPERF) {
      parser = new SimpleperfTraceParser();
    }
    else {
      throw new IllegalArgumentException("There is no parser available for profiler type " + profilerType);
    }
    try {
      parser.parse(file);
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    for (Map.Entry<CpuThreadInfo, CaptureNode> entry : parser.getCaptureTrees().entrySet()) {
      if (entry.getKey().getName().equals(nodeName)) {
        return entry.getValue();
      }
    }
    return null;
  }

  @Override
  public String getName() {
    return "MethodModelRenderer";
  }
}
