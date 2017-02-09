/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.profilers.memory;

import com.android.tools.profilers.FakeGrpcChannel;
import com.android.tools.profilers.FakeIdeProfilerComponents;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.StudioProfilersView;
import com.android.tools.profilers.memory.adapters.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Rule;
import org.junit.Test;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.android.tools.profilers.memory.MemoryProfilerConfiguration.ClassGrouping.ARRANGE_BY_PACKAGE;
import static com.android.tools.profilers.memory.MemoryProfilerConfiguration.ClassGrouping.ARRANGE_BY_CLASS;
import static org.junit.Assert.*;

public class MemoryProfilerStageViewTest extends MemoryProfilerTestBase {
  private StudioProfilersView myView;
  @NotNull private final FakeMemoryService myService = new FakeMemoryService();

  @Rule
  public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("MemoryProfilerStageViewTestChannel", myService);

  @Override
  protected FakeGrpcChannel getGrpcChannel() {
    return myGrpcChannel;
  }

  @Override
  protected void onProfilersCreated(StudioProfilers profilers) {
    myView = new StudioProfilersView(profilers, new FakeIdeProfilerComponents());
    myView.bind(MemoryProfilerStage.class, MemoryProfilerStageViewFake::new);
  }

  @Test
  public void testCaptureAndHeapView() throws Exception {
    final String dummyClassName1 = "DUMMY_CLASS1";
    final String dummyClassName2 = "DUMMY_CLASS2";
    InstanceObject mockInstance1 = mockInstanceObject(dummyClassName1, "DUMMY_INSTANCE", null, 0, 1, 2, 3);
    InstanceObject mockInstance2 = mockInstanceObject(dummyClassName2, "DUMMY_INSTANCE", null, 0, 4, 5, 6);
    ClassObject mockKlass1 = mockClassObject(dummyClassName1, 1, 2, 3, Collections.singletonList(mockInstance1));
    ClassObject mockKlass2 = mockClassObject(dummyClassName2, 4, 5, 6, Collections.singletonList(mockInstance2));
    HeapObject mockHeap1 = mockHeapObject("DUMMY_HEAP1", Arrays.asList(mockKlass1, mockKlass2));
    HeapObject mockHeap2 = mockHeapObject("DUMMY_HEAP2", Arrays.asList(mockKlass1, mockKlass2));
    CaptureObject mockCapture1 = mockCaptureObject("DUMMY_CAPTURE1", 5, 10, Arrays.asList(mockHeap1, mockHeap2), true);
    CaptureObject mockCapture2 = mockCaptureObject("DUMMY_CAPTURE2", 5, 10, Arrays.asList(mockHeap2, mockHeap1), true);

    MemoryProfilerStageView stageView = (MemoryProfilerStageView)myView.getStageView();
    JComponent captureComponent = stageView.getChartCaptureSplitter().getSecondComponent();
    assertTrue(captureComponent == null || !captureComponent.isVisible());

    JComponent instanceComponent = stageView.getMainSplitter().getSecondComponent();
    assertTrue(instanceComponent == null || !instanceComponent.isVisible());

    assertView(null, null, null, null, false);

    myStage.selectCapture(mockCapture1, null);
    assertView(mockCapture1, null, null, null, true);
    myAspectObserver.assertAndResetCounts(0, 1, 0, 0, 0, 0, 0);
    myMockLoader.runTask();
    assertView(mockCapture1, mockHeap1, null, null, false);
    myAspectObserver.assertAndResetCounts(0, 0, 1, 0, 1, 0, 0);

    // Tests selecting a capture which loads immediately.
    myMockLoader.setReturnImmediateFuture(true);
    myStage.selectCapture(mockCapture2, null);
    // 2 heap changes: 1 from changing the capture, the other from the auto-selection after the capture is loaded.
    assertView(mockCapture2, mockHeap2, null, null, false);
    myAspectObserver.assertAndResetCounts(0, 1, 1, 0, 2, 0, 0);

    stageView.getHeapView().getComponent().setSelectedItem(mockHeap1);
    assertSelection(mockCapture2, mockHeap1, null, null);
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 1, 0, 0);

    myStage.selectClass(mockKlass1);
    assertView(mockCapture2, mockHeap1, mockKlass1, null, false);
    myAspectObserver.assertAndResetCounts(0, 0, 0, 0, 0, 1, 0);

    assertEquals(ARRANGE_BY_CLASS, myStage.getConfiguration().getClassGrouping());
    myStage.getConfiguration().setClassGrouping(ARRANGE_BY_PACKAGE);
    assertEquals(ARRANGE_BY_PACKAGE, ((MemoryProfilerStageView)myView.getStageView()).getClassGrouping().getComponent().getSelectedItem());
    myAspectObserver.assertAndResetCounts(0, 0, 0, 1, 0, 0, 0);

    JTree classTree = stageView.getClassView().getTree();
    assertNotNull(classTree);
    Object classRoot = classTree.getModel().getRoot();
    assertTrue(classRoot instanceof MemoryObjectTreeNode);
    assertTrue(((MemoryObjectTreeNode)classRoot).getAdapter() instanceof NamespaceObject);
    //noinspection unchecked
    MemoryObjectTreeNode<NamespaceObject> memoryClassRoot = (MemoryObjectTreeNode<NamespaceObject>)classRoot;
    stageView.getClassView().getTree()
      .setSelectionPath(new TreePath(new Object[]{classTree.getModel().getRoot(), memoryClassRoot.getChildren().get(1)}));
    assertSelection(mockCapture2, mockHeap1, mockKlass2, null);

    myStage.selectInstance(mockInstance1);
    assertSelection(mockCapture2, mockHeap1, mockKlass2, mockInstance1);

    myStage.selectCapture(null, null);
    assertView(null, null, null, null, false);
  }

  @Test
  public void testLoadingNewCaptureWithExistingLoad() throws Exception {
    HeapObject mockHeap1 = mockHeapObject("DUMMY_HEAP1", Collections.emptyList());
    HeapObject mockHeap2 = mockHeapObject("DUMMY_HEAP2", Collections.emptyList());
    CaptureObject mockCapture1 = mockCaptureObject("DUMMY_CAPTURE1", 5, 10, Collections.singletonList(mockHeap1), true);
    CaptureObject mockCapture2 = mockCaptureObject("DUMMY_CAPTURE2", 10, 15, Collections.singletonList(mockHeap2), true);

    myStage.selectCapture(mockCapture1, null);
    assertView(mockCapture1, null, null, null, true);
    myAspectObserver.assertAndResetCounts(0, 1, 0, 0, 0, 0, 0);

    // Select a new capture before the first is loaded.
    myStage.selectCapture(mockCapture2, null);
    assertView(mockCapture2, null, null, null, true);
    myAspectObserver.assertAndResetCounts(0, 1, 0, 0, 0, 0, 0);
    myMockLoader.runTask();
    assertView(mockCapture2, mockHeap2, null, null, false);
    myAspectObserver.assertAndResetCounts(0, 0, 1, 0, 1, 0, 0);
  }

  private void assertSelection(@Nullable CaptureObject expectedCaptureObject,
                               @Nullable HeapObject expectedHeapObject,
                               @Nullable ClassObject expectedClassObject,
                               @Nullable InstanceObject expectedInstanceObject) {
    assertEquals(expectedCaptureObject, myStage.getSelectedCapture());
    assertEquals(expectedHeapObject, myStage.getSelectedHeap());
    assertEquals(expectedClassObject, myStage.getSelectedClass());
    assertEquals(expectedInstanceObject, myStage.getSelectedInstance());
  }

  private void assertView(@Nullable CaptureObject expectedCaptureObject,
                          @Nullable HeapObject expectedHeapObject,
                          @Nullable ClassObject expectedClassObject,
                          @Nullable InstanceObject expectedInstanceObject,
                          boolean isCaptureLoading) {
    MemoryProfilerStageView stageView = (MemoryProfilerStageView)myView.getStageView();

    ComboBoxModel<HeapObject> heapObjectComboBoxModel = stageView.getHeapView().getComponent().getModel();

    if (expectedCaptureObject == null) {
      assertNull(stageView.getChartCaptureSplitter().getSecondComponent());
      assertEquals("", stageView.getCaptureView().getComponent().getText());
      assertEquals(heapObjectComboBoxModel.getSize(), 0);
      assertNull(stageView.getClassView().getTree());
      assertFalse(stageView.getInstanceView().getComponent().isVisible());
      assertFalse(stageView.getInstanceDetailsView().getComponent().isVisible());
      return;
    }

    assertNotNull(stageView.getChartCaptureSplitter().getSecondComponent());
    if (isCaptureLoading) {
      assertEquals("", stageView.getCaptureView().getComponent().getText());
      assertEquals(heapObjectComboBoxModel.getSize(), 0);
    }
    else {
      assertEquals(stageView.getCapturePanel(), stageView.getChartCaptureSplitter().getSecondComponent());
      assertEquals(expectedCaptureObject.getLabel(), stageView.getCaptureView().getComponent().getText());
      assertEquals(expectedCaptureObject.getHeaps(),
                   IntStream.range(0, heapObjectComboBoxModel.getSize()).mapToObj(heapObjectComboBoxModel::getElementAt)
                     .collect(Collectors.toList()));
      assertEquals(expectedHeapObject, heapObjectComboBoxModel.getSelectedItem());
    }

    if (expectedHeapObject == null) {
      assertNull(stageView.getClassView().getTree());
      return;
    }

    JTree classTree = stageView.getClassView().getTree();
    assertNotNull(classTree);
    Object classTreeRoot = classTree.getModel().getRoot();
    assertTrue(classTreeRoot instanceof MemoryObjectTreeNode);
    assertTrue(((MemoryObjectTreeNode)classTreeRoot).getAdapter() instanceof NamespaceObject);
    //noinspection unchecked
    MemoryObjectTreeNode<NamespaceObject> memoryClassTreeRoot = (MemoryObjectTreeNode<NamespaceObject>)classTreeRoot;
    assertEquals(expectedHeapObject.getClasses(),
                 memoryClassTreeRoot.getChildren().stream().map(MemoryObjectTreeNode::getAdapter).collect(Collectors.toList()));

    if (expectedClassObject == null) {
      assertEquals(null, classTree.getLastSelectedPathComponent());
      assertFalse(stageView.getInstanceView().getComponent().isVisible());
      assertFalse(stageView.getInstanceDetailsView().getComponent().isVisible());
      return;
    }

    Object selectedClassNode = classTree.getLastSelectedPathComponent();
    assertTrue(selectedClassNode instanceof MemoryObjectTreeNode);
    assertTrue(((MemoryObjectTreeNode)selectedClassNode).getAdapter() instanceof ClassObject);
    //noinspection unchecked
    MemoryObjectTreeNode<ClassObject> selectedClassObject = (MemoryObjectTreeNode<ClassObject>)selectedClassNode;
    assertEquals(expectedClassObject, selectedClassObject.getAdapter());

    assertTrue(stageView.getInstanceView().getComponent().isVisible());
    JTree instanceTree = stageView.getInstanceView().getTree();
    assertNotNull(instanceTree);

    if (expectedInstanceObject == null) {
      assertEquals(null, instanceTree.getLastSelectedPathComponent());
      assertFalse(stageView.getInstanceDetailsView().getComponent().isVisible());
      return;
    }

    Object selectedInstanceNode = instanceTree.getLastSelectedPathComponent();
    assertTrue(selectedInstanceNode instanceof MemoryObjectTreeNode);
    assertTrue(((MemoryObjectTreeNode)selectedInstanceNode).getAdapter() instanceof InstanceObject);
    //noinspection unchecked
    MemoryObjectTreeNode<InstanceObject> selectedInstanceObject = (MemoryObjectTreeNode<InstanceObject>)selectedInstanceNode;
    assertEquals(expectedInstanceObject, selectedInstanceObject.getAdapter());

    boolean detailsViewVisible = (expectedInstanceObject.getCallStack() != null &&
                                  expectedInstanceObject.getCallStack().getStackFramesCount() > 0) ||
                                 !expectedInstanceObject.getReferences().isEmpty();
    assertEquals(detailsViewVisible, stageView.getInstanceDetailsView().getComponent().isVisible());
  }
}
