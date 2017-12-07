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

import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.model.Range;
import com.android.tools.perflib.vmtrace.ClockType;
import com.android.tools.profilers.FakeGrpcChannel;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.FakeProfilerService;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.cpu.nodemodel.JavaMethodModel;
import com.android.tools.profilers.event.FakeEventService;
import com.android.tools.profilers.memory.FakeMemoryService;
import com.android.tools.profilers.network.FakeNetworkService;
import com.google.common.collect.ImmutableMap;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.regex.Pattern;

import static com.google.common.truth.Truth.assertThat;


public class CaptureModelTest {
  private final FakeProfilerService myProfilerService = new FakeProfilerService();

  private final FakeCpuService myCpuService = new FakeCpuService();

  @Rule
  public FakeGrpcChannel myGrpcChannel =
    new FakeGrpcChannel("CpuProfilerStageTestChannel", myCpuService, myProfilerService,
                        new FakeMemoryService(), new FakeEventService(), FakeNetworkService.newBuilder().build());

  private CpuProfilerStage myStage;

  private CaptureModel myModel;
  @Before
  public void setUp() {
    FakeTimer timer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices(), timer);
    // One second must be enough for new devices (and processes) to be picked up
    timer.tick(FakeTimer.ONE_SECOND_IN_NS);
    myStage = new CpuProfilerStage(profilers);
    myStage.getStudioProfilers().setStage(myStage);
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
  private CaptureNode createFilterTestTree() {
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
    CpuCapture capture = new CpuCapture(new Range(0, 30),
                                        new ImmutableMap.Builder<CpuThreadInfo, CaptureNode>()
                                          .put(info, root)
                                          .build(),
                                        true);
    myModel.setCapture(capture);
    myModel.setThread(101);
    myModel.setDetails(CaptureModel.Details.Type.CALL_CHART);
    myModel.setFilter(Pattern.compile("^.*" + Pattern.quote("myPackage") + ".*$"));
    CaptureNode node = (CaptureNode)((CaptureModel.CallChart)myModel.getDetails()).getNode();
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

  private static void checkChildren(CaptureNode node, String... childrenId) {
    assertThat(node.getChildCount()).isEqualTo(childrenId.length);
    for (int i = 0; i < node.getChildCount(); ++i) {
      assertThat(node.getChildAt(i).getData().getId()).isEqualTo(childrenId[i]);
    }
  }

  private static void checkChildrenFilterType(CaptureNode node, CaptureNode.FilterType... filterTypes) {
    assertThat(node.getChildren().size()).isEqualTo(filterTypes.length);
    for (int i = 0; i < filterTypes.length; ++i) {
      assertThat(node.getChildren().get(i).getFilterType()).isEqualTo(filterTypes[i]);
    }
  }

  private CaptureNode createNode(String fullMethodName, long start, long end) {
    int index = fullMethodName.lastIndexOf('.');
    assert index != -1;
    String className = fullMethodName.substring(0, index);
    String methodName = fullMethodName.substring(index + 1);

    CaptureNode node = new CaptureNode();
    node.setCaptureNodeModel(new JavaMethodModel(methodName, className));
    node.setClockType(ClockType.GLOBAL);
    node.setStartGlobal(start);
    node.setEndGlobal(end);
    node.setStartThread(start);
    node.setEndThread(end);

    return node;
  }
}