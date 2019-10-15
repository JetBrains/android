/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.cpu.capturedetails;

import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.filter.Filter;
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel;
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.perflib.vmtrace.ClockType;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profiler.proto.CpuProfiler;
import com.android.tools.profilers.*;
import com.android.tools.profilers.cpu.*;
import com.android.tools.profilers.cpu.nodemodel.JavaMethodModel;
import com.android.tools.profilers.event.FakeEventService;
import com.android.tools.profilers.memory.FakeMemoryService;
import com.android.tools.profilers.network.FakeNetworkService;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.*;

import static com.google.common.truth.Truth.assertThat;

public class CaptureModelTest {
  private final FakeTimer myTimer = new FakeTimer();
  private final FakeCpuService myCpuService = new FakeCpuService();

  @Rule
  public FakeGrpcChannel myGrpcChannel =
    new FakeGrpcChannel("CpuProfilerStageTestChannel", myCpuService, new FakeTransportService(myTimer), new FakeProfilerService(myTimer),
                        new FakeMemoryService(), new FakeEventService(), FakeNetworkService.newBuilder().build());

  private CaptureModel myModel;

  private CpuProfilerStage myStage;

  @Before
  public void setUp() {
    StudioProfilers profilers = new StudioProfilers(new ProfilerClient(myGrpcChannel.getName()), new FakeIdeProfilerServices(), myTimer);
    // One second must be enough for new devices (and processes) to be picked up
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myStage = new CpuProfilerStage(profilers);
    myStage.getStudioProfilers().setStage(myStage);
    myStage.enter();
    myModel = new CaptureModel(myStage);
  }

  /**
   * Creates a tree that is used in {@link #testFilter()}
   * The structure of the tree:
   * mainPackage.main [0..1000]
   *   -> otherPackage.method1 [0..500]
   *        -> myPackage.method2 [0..200]
   *             -> otherPackege.method4 [0..100]
   *             -> myPackage.method3 [101..20]
   *        -> otherPackage.method3 [300..500]
   *             -> otherPackage.method4 [300..400]
   *             -> otherPackage.method4 [401..500]
   *   -> myPackage.method1 [600..700]
   *        -> otherPackage.method3 [600..650]
   *        -> otherPackage.method4 [660..700]
   *   -> otherPackage.method2 [800..1000]
   *        -> otherPackage.method3 [800..850]
   *        -> otherPackage.method4 [860..900]
   */
  private static CaptureNode createFilterTestTree() {
    CaptureNode root = createNode("mainPackage.main", 0, 1000);
    root.addChild(createNode("otherPackage.method1", 0, 500));
    root.addChild(createNode("myPackage.method1", 600, 700));
    root.addChild(createNode("otherPackage.method2", 800, 1000));

    root.getChildAt(1).addChild(createNode("otherPackage.method3", 600, 650));
    root.getChildAt(1).addChild(createNode("otherPackage.method4", 660, 700));

    root.getChildAt(2).addChild(createNode("otherPackage.method3", 800, 850));
    root.getChildAt(2).addChild(createNode("otherPackage.method4", 860, 900));

    CaptureNode first = root.getFirstChild();
    first.addChild(createNode("myPackage.method2", 0, 200));
    first.addChild(createNode("otherPackage.method3", 300, 500));

    first.getChildAt(0).addChild(createNode("otherPackage.method4", 0, 100));
    first.getChildAt(0).addChild(createNode("myPackage.method3", 101, 200));

    first.getChildAt(1).addChild(createNode("otherPackage.method4", 300, 400));
    first.getChildAt(1).addChild(createNode("otherPackage.method4", 401, 500));

    return root;
  }

  @Test
  public void testFilter() {
    CaptureNode root = createFilterTestTree();

    CpuThreadInfo info = new CpuThreadInfo(101, "main");
    TraceParser parser = new FakeTraceParser(new Range(0, 30),
                                             new ImmutableMap.Builder<CpuThreadInfo, CaptureNode>()
                                               .put(info, root)
                                               .build(), false);
    CpuCapture capture = new CpuCapture(parser, 200, Cpu.CpuTraceType.UNSPECIFIED_TYPE);
    myModel.setCapture(capture);
    myModel.setThread(101);
    myModel.setDetails(CaptureDetails.Type.CALL_CHART);
    myModel.setFilter(new Filter("myPackage"));
    CaptureNode node = ((CaptureDetails.CallChart)myModel.getDetails()).getNode();
    // mainPackage.main
    assertThat(node.getFilterType()).isEqualTo(CaptureNode.FilterType.MATCH);

    // mainPackage.main
    checkChildrenFilterType(node, CaptureNode.FilterType.MATCH, CaptureNode.FilterType.EXACT_MATCH, CaptureNode.FilterType.UNMATCH);

    // mainPackage.main -> otherPackage.method1
    checkChildrenFilterType(node.getFirstChild(), CaptureNode.FilterType.EXACT_MATCH, CaptureNode.FilterType.UNMATCH);
    // mainPackage.main -> otherPackage.method1 -> myPackage.method2
    checkChildrenFilterType(node.getFirstChild().getFirstChild(), CaptureNode.FilterType.MATCH, CaptureNode.FilterType.EXACT_MATCH);
    // mainPackage.main -> otherPackage.method1 -> otherPackage.method3
    checkChildrenFilterType(node.getFirstChild().getChildAt(1), CaptureNode.FilterType.UNMATCH, CaptureNode.FilterType.UNMATCH);

    // mainPackage.main -> myPackage.method1
    checkChildrenFilterType(node.getChildAt(1), CaptureNode.FilterType.MATCH, CaptureNode.FilterType.MATCH);
    // mainPackage.main -> otherPackage.method2
    checkChildrenFilterType(node.getChildAt(2), CaptureNode.FilterType.UNMATCH, CaptureNode.FilterType.UNMATCH);
  }

  @Test
  public void testEmptyFilter() {
    CaptureNode root = createFilterTestTree();

    CpuThreadInfo info = new CpuThreadInfo(101, "main");
    TraceParser parser = new FakeTraceParser(new Range(0, 30),
                                             new ImmutableMap.Builder<CpuThreadInfo, CaptureNode>()
                                               .put(info, root)
                                               .build(), false);
    CpuCapture capture = new CpuCapture(parser, 200, Cpu.CpuTraceType.UNSPECIFIED_TYPE);
    myModel.setCapture(capture);
    myModel.setThread(101);
    myModel.setDetails(CaptureDetails.Type.CALL_CHART);
    myModel.setFilter(Filter.EMPTY_FILTER);
    getDescendants(((CaptureDetails.CallChart)myModel.getDetails()).getNode())
      .forEach(n -> assertThat(n.getFilterType()).isEqualTo(CaptureNode.FilterType.MATCH));
  }

  @Test
  public void testClockTypeGetsReset() {
    CaptureNode root = createFilterTestTree();

    CpuThreadInfo info = new CpuThreadInfo(101, "main");
    TraceParser globalOnlyClockSupported = new FakeTraceParser(new Range(0, 30),
                                                               new ImmutableMap.Builder<CpuThreadInfo, CaptureNode>()
                                                                 .put(info, root)
                                                                 .build(), false);
    TraceParser dualClockSupported = new FakeTraceParser(new Range(0, 30),
                                                         new ImmutableMap.Builder<CpuThreadInfo, CaptureNode>()
                                                           .put(info, root)
                                                           .build(), true);

    CpuCapture globalOnlyCapture = new CpuCapture(globalOnlyClockSupported, 200, Cpu.CpuTraceType.UNSPECIFIED_TYPE);
    CpuCapture dualCapture1 = new CpuCapture(dualClockSupported, 200, Cpu.CpuTraceType.UNSPECIFIED_TYPE);
    CpuCapture dualCapture2 = new CpuCapture(dualClockSupported, 200, Cpu.CpuTraceType.UNSPECIFIED_TYPE);
    myModel.setCapture(globalOnlyCapture);
    assertThat(myModel.getClockType()).isEqualTo(ClockType.GLOBAL);
    myModel.setClockType(ClockType.THREAD);
    assertThat(myModel.getClockType()).isEqualTo(ClockType.GLOBAL);

    myModel.setCapture(dualCapture1);
    assertThat(myModel.getClockType()).isEqualTo(ClockType.GLOBAL);
    myModel.setClockType(ClockType.THREAD);
    assertThat(myModel.getClockType()).isEqualTo(ClockType.THREAD);

    // If we set a capture that supports dual clock we don't change the clock.
    myModel.setCapture(dualCapture2);
    assertThat(myModel.getClockType()).isEqualTo(ClockType.THREAD);

    // If we set a capture that does not support dual clock we reset back to global.
    myModel.setCapture(globalOnlyCapture);
    assertThat(myModel.getClockType()).isEqualTo(ClockType.GLOBAL);

    // If we set a capture that does support dual clock after setting to global we stick to global.
    myModel.setCapture(dualCapture1);
    assertThat(myModel.getClockType()).isEqualTo(ClockType.GLOBAL);
  }

  @Test
  public void testDetailsFeatureTracking() {
    FakeFeatureTracker tracker = (FakeFeatureTracker)myStage.getStudioProfilers().getIdeServices().getFeatureTracker();

    assertThat(tracker.getLastCaptureDetailsType()).isNull();

    myModel.setDetails(CaptureDetails.Type.CALL_CHART);
    assertThat(tracker.getLastCaptureDetailsType()).isEqualTo(CaptureDetails.Type.CALL_CHART);

    myModel.setDetails(CaptureDetails.Type.FLAME_CHART);
    assertThat(tracker.getLastCaptureDetailsType()).isEqualTo(CaptureDetails.Type.FLAME_CHART);

    myModel.setDetails(CaptureDetails.Type.TOP_DOWN);
    assertThat(tracker.getLastCaptureDetailsType()).isEqualTo(CaptureDetails.Type.TOP_DOWN);

    myModel.setDetails(CaptureDetails.Type.BOTTOM_UP);
    assertThat(tracker.getLastCaptureDetailsType()).isEqualTo(CaptureDetails.Type.BOTTOM_UP);
  }

  @Test
  public void detailsFeatureTrackingIgnoresEventWithTheSameType() throws IOException, ExecutionException, InterruptedException {
    FakeFeatureTracker tracker = (FakeFeatureTracker)myStage.getStudioProfilers().getIdeServices().getFeatureTracker();
    myModel.setCapture(CpuProfilerTestUtils.getValidCapture());
    // Using BOTTOM_UP because CALL_CHART is the default
    myModel.setDetails(CaptureDetails.Type.BOTTOM_UP);
    assertThat(tracker.getLastCaptureDetailsType()).isEqualTo(CaptureDetails.Type.BOTTOM_UP);
    tracker.resetLastCaptureDetailsType();
    myModel.setDetails(CaptureDetails.Type.BOTTOM_UP);
    assertThat(tracker.getLastCaptureDetailsType()).isNull();
  }

  @Test
  public void detailsSetWithoutACaptureReturnsNullDetails() {
    myModel.setCapture(null);
    myModel.setDetails(CaptureDetails.Type.CALL_CHART);
    assertThat(myModel.getDetails()).isNull();
  }

  private static void checkChildrenFilterType(CaptureNode node, CaptureNode.FilterType... filterTypes) {
    assertThat(node.getChildren().size()).isEqualTo(filterTypes.length);
    for (int i = 0; i < filterTypes.length; ++i) {
      assertThat(node.getChildren().get(i).getFilterType()).isEqualTo(filterTypes[i]);
    }
  }

  private static CaptureNode createNode(String fullMethodName, long start, long end) {
    int index = fullMethodName.lastIndexOf('.');
    assert index != -1;
    String className = fullMethodName.substring(0, index);
    String methodName = fullMethodName.substring(index + 1);

    CaptureNode node = new CaptureNode(new JavaMethodModel(methodName, className));
    node.setClockType(ClockType.GLOBAL);
    node.setStartGlobal(start);
    node.setEndGlobal(end);
    node.setStartThread(start);
    node.setEndThread(end);

    return node;
  }

  @NotNull
  private static List<CaptureNode> getDescendants(@NotNull CaptureNode node) {
    List<CaptureNode> descendants = new ArrayList<>();
    descendants.add(node);

    int head = 0;
    while (head < descendants.size()) {
      CaptureNode curNode = descendants.get(head++);
      descendants.addAll(curNode.getChildren());
    }
    return  descendants;
  }
}