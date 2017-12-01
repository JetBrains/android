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
import com.android.tools.perflib.vmtrace.VmTraceParser;
import com.android.tools.profilers.cpu.CaptureNode;
import com.android.tools.profilers.cpu.MethodModelHRenderer;
import com.android.tools.profilers.cpu.nodemodel.CaptureNodeModel;
import com.android.tools.profilers.cpu.CpuThreadInfo;
import com.android.tools.profilers.cpu.art.ArtTraceHandler;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class CpuHTreeChartReducerVisualTest extends VisualTest {
  private static final String TEST_RESOURCE_DIR = "tools/adt/idea/profilers-ui/testData/visualtests/";

  private HTreeChart<CaptureNodeModel> myChart;
  private HTreeChart<CaptureNodeModel> myNotOptimizedChart;
  private Range myRange = new Range();

  @Override
  protected List<Updatable> createModelList() {
    return Collections.emptyList();
  }

  @Override
  protected List<AnimatedComponent> getDebugInfoComponents() {
    return Arrays.asList(myChart, myNotOptimizedChart);
  }

  @Override
  protected void populateUi(@NotNull JPanel panel) {
    CaptureNode node = parseAndGetHNode();
    myRange.set(node.getStart(), node.getEnd());

    myChart = new HTreeChart<>(null, myRange, HTreeChart.Orientation.TOP_DOWN);
    myChart.setHRenderer(new MethodModelHRenderer());
    myChart.setHTree(node);

    myNotOptimizedChart = new HTreeChart<>(null, myRange, HTreeChart.Orientation.TOP_DOWN, (rectangles, nodes) -> {
    });
    myNotOptimizedChart.setHRenderer(new MethodModelHRenderer());
    myNotOptimizedChart.setHTree(node);

    panel.setLayout(new GridLayout(2, 1));
    panel.add(myChart);
    panel.add(myNotOptimizedChart);
  }

  private static CaptureNode parseAndGetHNode() {
    File file = TestUtils.getWorkspaceFile(TEST_RESOURCE_DIR + "cpu_trace.trace");
    ArtTraceHandler traceHandler = new ArtTraceHandler();
    VmTraceParser parser = new VmTraceParser(file, traceHandler);
    try {
      parser.parse();
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    for (Map.Entry<CpuThreadInfo, CaptureNode> entry : traceHandler.getThreadsGraph().entrySet()) {
      if (entry.getKey().getName().equals("main")) {
        return entry.getValue();
      }
    }
    return null;
  }

  @Override
  public String getName() {
    return "CpuHTreeChartReducer";
  }
}
