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

import com.android.tools.profilers.StudioProfilersViewFake;
import com.android.tools.profilers.TestGrpcChannel;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.android.tools.profilers.memory.adapters.ClassObject;
import com.android.tools.profilers.memory.adapters.HeapObject;
import com.android.tools.profilers.memory.adapters.InstanceObject;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.*;

public class MemoryProfilerStageViewTest extends MemoryProfilerTestBase {
  private MemoryProfilerStageView myStageView;

  @Rule
  public TestGrpcChannel<MemoryServiceMock> myGrpcChannel = new TestGrpcChannel<>("MEMORY_TEST_CHANNEL", new MemoryServiceMock());

  @Override
  @Before
  public void setup() {
    myProfilers = myGrpcChannel.getProfilers();
    myMockService = myGrpcChannel.getService();
    myStage = new MemoryProfilerStage(myProfilers, DUMMY_LOADER);
    StudioProfilersViewFake view = new StudioProfilersViewFake(myProfilers);
    view.bind(MemoryProfilerStage.class, MemoryProfilerStageViewFake::new);
    myProfilers.setStage(myStage);
    myStageView = (MemoryProfilerStageView)view.getStageView();

    super.setup();
  }

  @Test
  public void testCaptureAndHeapView() throws Exception {
    JComponent captureComponent = myStageView.getChartCaptureSplitter().getSecondComponent();
    assertTrue(captureComponent == null || !captureComponent.isVisible());

    JComponent instanceComponent = myStageView.getMainSplitter().getSecondComponent();
    assertTrue(instanceComponent == null || !instanceComponent.isVisible());

    assertView(null, null, null, null);

    myStage.selectCapture(DUMMY_CAPTURE, null);
    assertView(DUMMY_CAPTURE, DUMMY_HEAP_1, null, null);
    assertAndResetCounts(0, 1, 1, 1, 0, 0);

    myStageView.getHeapView().getComponent().setSelectedItem(DUMMY_HEAP_2);
    assertSelection(DUMMY_CAPTURE, DUMMY_HEAP_2, null, null);
    assertAndResetCounts(0, 0, 0, 1, 0, 0);

    myStage.selectClass(DUMMY_CLASS_1);
    assertView(DUMMY_CAPTURE, DUMMY_HEAP_2, DUMMY_CLASS_1, null);
    assertAndResetCounts(0, 0, 0, 0, 1, 0);

    JTree classTree = myStageView.getClassView().getTree();
    assertNotNull(classTree);
    Object classRoot = classTree.getModel().getRoot();
    assertTrue(classRoot instanceof MemoryObjectTreeNode);
    assertTrue(((MemoryObjectTreeNode)classRoot).getAdapter() instanceof ClassObject);
    //noinspection unchecked
    MemoryObjectTreeNode<ClassObject> memoryClassRoot = (MemoryObjectTreeNode<ClassObject>)classRoot;
    myStageView.getClassView().getTree()
      .setSelectionPath(new TreePath(new Object[]{classTree.getModel().getRoot(), memoryClassRoot.getChildren().get(0)}));
    assertSelection(DUMMY_CAPTURE, DUMMY_HEAP_2, DUMMY_CLASS_2, null);

    myStage.selectInstance(DUMMY_INSTANCE);
    assertView(DUMMY_CAPTURE, DUMMY_HEAP_2, DUMMY_CLASS_2, DUMMY_INSTANCE);
    assertTrue(!myStageView.getInstanceView().getComponent().getSecondComponent().isVisible());

    myStage.selectCapture(null, null);
    assertView(null, null, null, null);
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
                          @Nullable InstanceObject expectedInstanceObject) {
    ComboBoxModel<HeapObject> heapObjectComboBoxModel = myStageView.getHeapView().getComponent().getModel();

    if (expectedCaptureObject == null) {
      assertFalse(myStageView.getChartCaptureSplitter().getSecondComponent().isVisible());
      assertEquals(MemoryCaptureView.ourLoadingText, myStageView.getCaptureView().getComponent().getText());
      assertEquals(heapObjectComboBoxModel.getSize(), 0);
      assertNull(myStageView.getClassView().getTree());
      assertFalse(myStageView.getInstanceView().getComponent().isVisible());
      return;
    }

    assertTrue(myStageView.getChartCaptureSplitter().getSecondComponent().isVisible());
    assertEquals(expectedCaptureObject.getLabel(), myStageView.getCaptureView().getComponent().getText());
    assertEquals(expectedCaptureObject.getHeaps(),
                 IntStream.range(0, heapObjectComboBoxModel.getSize()).mapToObj(heapObjectComboBoxModel::getElementAt)
                   .collect(Collectors.toList()));
    assertEquals(expectedHeapObject, heapObjectComboBoxModel.getSelectedItem());
    if (expectedHeapObject == null) {
      assertNull(myStageView.getClassView().getTree());
      return;
    }

    JTree classTree = myStageView.getClassView().getTree();
    assertNotNull(classTree);
    Object classTreeRoot = classTree.getModel().getRoot();
    assertTrue(classTreeRoot instanceof MemoryObjectTreeNode);
    assertTrue(((MemoryObjectTreeNode)classTreeRoot).getAdapter() instanceof ClassObject);
    //noinspection unchecked
    MemoryObjectTreeNode<ClassObject> memoryClassTreeRoot = (MemoryObjectTreeNode<ClassObject>)classTreeRoot;
    assertEquals(expectedHeapObject.getClasses(),
                 memoryClassTreeRoot.getChildren().stream().map(MemoryObjectTreeNode::getAdapter).collect(Collectors.toList()));

    if (expectedClassObject == null) {
      assertEquals(null, classTree.getLastSelectedPathComponent());
      assertTrue(!myStageView.getInstanceView().getComponent().isVisible());
      return;
    }

    Object selectedClassNode = classTree.getLastSelectedPathComponent();
    assertTrue(selectedClassNode instanceof MemoryObjectTreeNode);
    assertTrue(((MemoryObjectTreeNode)selectedClassNode).getAdapter() instanceof ClassObject);
    //noinspection unchecked
    MemoryObjectTreeNode<ClassObject> selectedClassObject = (MemoryObjectTreeNode<ClassObject>)selectedClassNode;
    assertEquals(expectedClassObject, selectedClassObject.getAdapter());

    assertTrue(myStageView.getInstanceView().getComponent().isVisible());
    JTree instanceTree = myStageView.getInstanceView().getTree();
    assertNotNull(instanceTree);

    if (expectedInstanceObject == null) {
      assertEquals(null, instanceTree.getLastSelectedPathComponent());
      return;
    }

    Object selectedInstanceNode = instanceTree.getLastSelectedPathComponent();
    assertTrue(selectedInstanceNode instanceof MemoryObjectTreeNode);
    assertTrue(((MemoryObjectTreeNode)selectedInstanceNode).getAdapter() instanceof InstanceObject);
    //noinspection unchecked
    MemoryObjectTreeNode<InstanceObject> selectedInstanceObject = (MemoryObjectTreeNode<InstanceObject>)selectedInstanceNode;
    assertEquals(expectedInstanceObject, selectedInstanceObject.getAdapter());
  }
}
