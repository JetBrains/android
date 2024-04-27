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
import static java.util.Collections.emptyMap;

import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.filter.Filter;
import com.android.tools.adtui.model.filter.FilterResult;
import com.android.tools.perflib.vmtrace.ClockType;
import com.android.tools.profilers.cpu.nodemodel.CaptureNodeModel;
import com.android.tools.profilers.cpu.nodemodel.JavaMethodModel;
import com.android.tools.profilers.cpu.nodemodel.SingleNameModel;
import com.intellij.util.containers.ContainerUtil;
import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.Test;

public class CaptureNodeTest {

  @Test
  public void captureNodeSpecificMethods() {
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
  public void hNodeApiMethods() {
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
    VisualNodeCaptureNode visualParent = new VisualNodeCaptureNode(new StubCaptureNodeModel(), ClockType.GLOBAL);

    realParent.addChild(childA);

    assertThat(childA.getParent()).isEqualTo(realParent);
    assertThat(realParent.getChildAt(0)).isEqualTo(childA);

    visualParent.addChild(childA);
    assertThat(childA.getParent()).isEqualTo(realParent);
    assertThat(realParent.getChildAt(0)).isEqualTo(childA);
    assertThat(visualParent.getChildAt(0)).isEqualTo(childA);
  }

  @Test
  public void testFilter() {
    CaptureNode node = createFilterTestTree();
    FilterResult filterResult = node.applyFilter(new Filter("myPackage"));

    // Verify filter result
    assertThat(filterResult.getMatchCount()).isEqualTo(3);
    assertThat(filterResult.getTotalCount()).isEqualTo(14);
    assertThat(filterResult.isFilterEnabled()).isTrue();

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
    FilterResult filterResult = root.applyFilter(Filter.EMPTY_FILTER);
    assertThat(filterResult.getMatchCount()).isEqualTo(14);
    assertThat(filterResult.getTotalCount()).isEqualTo(14);
    assertThat(filterResult.isFilterEnabled()).isFalse();
    root.getDescendantsStream().forEach(n -> assertThat(n.getFilterType()).isEqualTo(CaptureNode.FilterType.MATCH));
  }

  @Test
  public void testGetRootNode() {
    CaptureNode root = createFilterTestTree();
    assertThat(root.findRootNode()).isSameAs(root);
    assertThat(root.getChildAt(0).findRootNode()).isSameAs(root);
    assertThat(root.getChildAt(1).getChildAt(0).findRootNode()).isSameAs(root);
    assertThat(root.getChildAt(0).getChildAt(1).getChildAt(0).findRootNode()).isSameAs(root);
  }

  @Test
  public void testGetTopKNodesWithoutNameMapping() {
    CaptureNode root = createFilterTestTree();
    List<CaptureNode> longestNodes = root.getTopKNodes(
      4, "otherPackage.method4", Comparator.comparing(CaptureNode::getDuration), emptyMap());
    assertThat(ContainerUtil.map(longestNodes, CaptureNode::getDuration)).containsExactly(100L, 100L, 99L, 40L);
  }

  @Test
  public void testGetTopKNodesWithNameMapping() {
    CaptureNode root = createFilterTestTree();
    Map<String, List<CaptureNode>> nameToNodes = CpuThreadTrackModel.getNameToNodesMapping(root);
    List<CaptureNode> longestNodes = root.getTopKNodes(
      4, "otherPackage.method4", Comparator.comparing(CaptureNode::getDuration), nameToNodes);
    assertThat(ContainerUtil.map(longestNodes, CaptureNode::getDuration)).containsExactly(100L, 100L, 99L, 40L);
  }

  @Test
  public void testFilterAspect() throws Exception {
    CaptureNode node = new CaptureNode(new SingleNameModel("Foo"));
    CountDownLatch latch = new CountDownLatch(1);
    node.getAspectModel().addDependency(new AspectObserver()).onChange(CaptureNode.Aspect.FILTER_APPLIED, () -> latch.countDown());
    node.applyFilter(new Filter());
    assertThat(latch.await(100, TimeUnit.MILLISECONDS)).isTrue();
  }

  @Test
  public void abbreviationCollapseAdjacentUninterestingNodes() {
    Function<CaptureNodeModel, Function<List<CaptureNode>, CaptureNode>> make = data -> children -> {
      CaptureNode node = new CaptureNode(data);
      node.addChildren(children);
      return node;
    };
    CaptureNodeModel interestingData = new SingleNameModel("interesting");
    CaptureNodeModel uninterestingData = new SingleNameModel("uninteresting");

    Function<List<CaptureNode>, CaptureNode>
      transparent = make.apply(interestingData),
      opaque = make.apply(uninterestingData);

    Supplier<CaptureNode>
      transparentLeaf = () -> transparent.apply(Arrays.asList()),
      opaqueLeaf = () -> opaque.apply(Arrays.asList());

    CaptureNode tree =
      transparent.apply(Arrays.asList(transparent.apply(Arrays.asList(transparentLeaf.get(), transparentLeaf.get())),
                                      opaque.apply(Arrays.asList(transparentLeaf.get(), opaqueLeaf.get())),
                                      opaque.apply(Arrays.asList(transparentLeaf.get(),
                                                                 opaque.apply(Arrays.asList(transparentLeaf.get(),
                                                                                            transparentLeaf.get()))))));

    CaptureNode abbreviatedTree = tree.abbreviatedBy(node -> node.getData() == uninterestingData, uninterestingData);

    // All opaque nodes above are adjacent, so abbreviated tree should only have 1 opaque node
    assertThat(abbreviatedTree.fold(node -> node.getData() == uninterestingData ? 1 : 0, Integer::sum)).isEqualTo(1);
    // The only opaque node should have a total of 4 transparent children from collapsing
    assertThat(abbreviatedTree.fold(node -> node.getData() == uninterestingData ? node.getChildCount() : 0, Integer::sum)).isEqualTo(4);
  }

  /**
   * Creates a tree that is used in {@link #testFilter()}
   * The structure of the tree:
   * mainPackage.main [0..1000]
   * -> otherPackage.method1 [0..500]
   * -> myPackage.method2 [0..200]
   * -> otherPackege.method4 [0..100]
   * -> myPackage.method3 [101..20]
   * -> otherPackage.method3 [300..500]
   * -> otherPackage.method4 [300..400]
   * -> otherPackage.method4 [401..500]
   * -> myPackage.method1 [600..700]
   * -> otherPackage.method3 [600..650]
   * -> otherPackage.method4 [660..700]
   * -> otherPackage.method2 [800..1000]
   * -> otherPackage.method3 [800..850]
   * -> otherPackage.method4 [860..900]
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
}