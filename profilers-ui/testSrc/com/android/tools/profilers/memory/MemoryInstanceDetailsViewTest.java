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

import com.android.tools.adtui.common.ColumnTreeTestInfo;
import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profilers.FakeGrpcChannel;
import com.android.tools.profilers.FakeIdeProfilerComponents;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.common.ContextMenuItem;
import com.android.tools.profilers.memory.adapters.ClassObject;
import com.android.tools.profilers.memory.adapters.HeapObject;
import com.android.tools.profilers.memory.adapters.ReferenceObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.Arrays;
import java.util.Collections;

import static com.android.tools.profilers.memory.MemoryProfilerTestBase.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

public class MemoryInstanceDetailsViewTest {
  @Rule
  public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("MEMORY_TEST_CHANNEL", new FakeMemoryService());

  private MemoryProfilerStage myStage;
  private MemoryInstanceDetailsView myDetailsView;
  private FakeIdeProfilerComponents myFakeIdeProfilerComponents;

  @Before
  public void setup() {
    myFakeIdeProfilerComponents = new FakeIdeProfilerComponents();
    myStage = new MemoryProfilerStage(new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices()));
    myDetailsView = new MemoryInstanceDetailsView(myStage, myFakeIdeProfilerComponents);
  }

  @Test
  public void NullSelectionVisibilityTest() throws Exception {
    // Null selection
    Component component = myDetailsView.getComponent();
    assertNull(myStage.getSelectedInstance());
    assertFalse(component.isVisible());
  }

  @Test
  public void NoCallstackOrReferenceVisibilityTest() throws Exception {
    // Selection with no callstack / reference information
    Component component = myDetailsView.getComponent();
    ReferenceObject mockInstance = mockReferenceObject("MockInstance", 1, 2, 3, Collections.emptyList(), null);
    myStage.selectInstance(mockInstance);
    assertFalse(component.isVisible());
  }


  @Test
  public void SelectionWithReferenceVisibilityTest() throws Exception {
    // Selection with reference information
    Component component = myDetailsView.getComponent();
    ReferenceObject mockRef = mockReferenceObject("MockReference", 1, 2, 3, Collections.emptyList(), null);
    ReferenceObject mockInstance =
      mockReferenceObject("MockInstanceWithRef", 1, 2, 3, Collections.singletonList(mockRef), null);
    myStage.selectInstance(mockInstance);
    assertTrue(component.isVisible());
  }


  @Test
  public void SelectionWithCallstackVisibilityTest() throws Exception {
    // Selection with callstack information
    Component component = myDetailsView.getComponent();
    MemoryProfiler.AllocationStack stack = MemoryProfiler.AllocationStack.newBuilder().addStackFrames(
      MemoryProfiler.AllocationStack.StackFrame.newBuilder().setClassName("MockClass").setMethodName("MockMethod").setLineNumber(1))
      .build();
    ReferenceObject mockInstance = mockReferenceObject("MockInstanceWithCallstack", 1, 2, 3, Collections.emptyList(), stack);
    myStage.selectInstance(mockInstance);
    assertTrue(component.isVisible());
  }

  /**
   * Test that the component generates the instances JTree model accurately based on the reference hierarchy
   * of an InstanceObject. We currently only populate up to two levels of children at a time to avoid
   * building a unnecessarily large tree at the beginning - descendants are further populated upon expansion.
   * This test ensures such behavior as well.
   */
  @Test
  public void buildTreeTest() throws Exception {
    // Setup mock reference hierarchy:
    // MockRoot
    // -> Ref1
    // --> Ref2
    // --> Ref3
    // ---> Ref4
    // -> Ref5
    ReferenceObject mockRef2 = mockReferenceObject("Ref2", 1, 2, 3, Collections.emptyList(), null);
    ReferenceObject mockRef4 = mockReferenceObject("Ref4", 1, 2, 3, Collections.emptyList(), null);
    ReferenceObject mockRef5 = mockReferenceObject("Ref5", 1, 2, 3, Collections.emptyList(), null);
    ReferenceObject mockRef3 = mockReferenceObject("Ref3", 1, 2, 3, Collections.singletonList(mockRef4), null);
    ReferenceObject mockRef1 = mockReferenceObject("Ref1", 1, 2, 3, Arrays.asList(mockRef2, mockRef3), null);
    ReferenceObject mockRootObject = mockReferenceObject("MockRoot", 1, 2, 3, Arrays.asList(mockRef1, mockRef5), null);

    JTree tree = myDetailsView.buildTree(mockRootObject);
    DefaultTreeModel treeModel = (DefaultTreeModel)tree.getModel();
    assertNotNull(treeModel);
    MemoryObjectTreeNode treeRoot = (MemoryObjectTreeNode)treeModel.getRoot();
    assertNotNull(treeRoot);

    // Check the initialize tree structure is correctly populated
    assertEquals(mockRootObject, treeRoot.getAdapter());
    assertEquals(2, treeRoot.getChildCount());
    MemoryObjectTreeNode ref1Child = (MemoryObjectTreeNode)treeRoot.getChildAt(0);
    MemoryObjectTreeNode ref5Child = (MemoryObjectTreeNode)treeRoot.getChildAt(1);
    assertEquals(mockRef1, ref1Child.getAdapter());
    assertEquals(mockRef5, ref5Child.getAdapter());
    assertEquals(2, ref1Child.getChildCount());
    assertEquals(0, ref5Child.getChildCount());
    MemoryObjectTreeNode ref2Child = (MemoryObjectTreeNode)ref1Child.getChildAt(0);
    MemoryObjectTreeNode ref3Child = (MemoryObjectTreeNode)ref1Child.getChildAt(1);
    assertEquals(mockRef2, ref2Child.getAdapter());
    assertEquals(mockRef3, ref3Child.getAdapter());

    // Check that nodes are not populated beyond the first two levels
    assertEquals(0, ref2Child.getChildCount());
    assertEquals(0, ref3Child.getChildCount());

    // Check that nodes are populated once expansion happens
    TreePath path = new TreePath(new Object[]{treeRoot, ref1Child});
    tree.expandPath(path);
    assertEquals(0, ref2Child.getChildCount());
    assertEquals(1, ref3Child.getChildCount());
    assertEquals(mockRef4, ((MemoryObjectTreeNode)ref3Child.getChildAt(0)).getAdapter());
  }

  @Test
  public void testGoToInstance() {
    ReferenceObject mockRef1 = mockReferenceObject("Ref1", 1, 2, 3, Collections.emptyList(), null);
    ClassObject ref1Class = mockClassObject("ref1Class", 1, 2, 3, Collections.singletonList(mockRef1));
    HeapObject ref1Heap = mockHeapObject("ref1Heap", Collections.singletonList(ref1Class));
    when(mockRef1.getClassObject()).thenReturn(ref1Class);
    when(ref1Class.getHeapObject()).thenReturn(ref1Heap);
    ReferenceObject mockRoot = mockReferenceObject("MockRoot", 1, 2, 3, Arrays.asList(mockRef1), null);

    myStage.selectInstance(mockRoot);
    JTree tree = myDetailsView.getReferenceTree();
    assertNotNull(tree);
    assertEquals(mockRoot, myStage.getSelectedInstance());
    assertNull(myStage.getSelectedClass());

    // Check that the Go To Instance menu item exists but is disabled since no instance is selected
    java.util.List<ContextMenuItem> menus = myFakeIdeProfilerComponents.getComponentContextMenus(tree);
    assertEquals(1, menus.size());
    assertEquals("Go to Instance", menus.get(0).getText());
    assertFalse(menus.get(0).isEnabled());

    // Selects the mockRef1 node and triggers the context menu action to select the ref instance.
    TreeNode refNode = ((MemoryObjectTreeNode)tree.getModel().getRoot()).getChildAt(0);
    tree.setSelectionPath(new TreePath(refNode));
    assertTrue(menus.get(0).isEnabled());
    menus.get(0).run();
    assertEquals(ref1Heap, myStage.getSelectedHeap());
    assertEquals(ref1Class, myStage.getSelectedClass());
    assertEquals(mockRef1, myStage.getSelectedInstance());
  }

  @Test
  public void testCorrectColumnsAndRendererContents() {
    // TODO test more sophisticated cases (e.g. multiple field names, icons, etc)
    // Setup mock reference hierarchy:
    // MockRoot
    // -> Ref1
    // -> Ref2
    // -> Ref3
    ReferenceObject mockRef1 = mockReferenceObject("Ref1", 1, 2, 3, Collections.emptyList(), null);
    ReferenceObject mockRef2 = mockReferenceObject("Ref2", 4, 5, 6, Collections.emptyList(), null);
    ReferenceObject mockRef3 = mockReferenceObject("Ref3", 7, 8, 9, Collections.emptyList(), null);
    java.util.List<ReferenceObject> references = Arrays.asList(mockRef1, mockRef2, mockRef3);
    ReferenceObject mockRootObject = mockReferenceObject("MockRoot", 1, 2, 3, references, null);
    myStage.selectInstance(mockRootObject);

    JTree tree = myDetailsView.getReferenceTree();
    assertNotNull(tree);
    JScrollPane columnTreePane = (JScrollPane)myDetailsView.getReferenceColumnTree();
    assertNotNull(columnTreePane);
    ColumnTreeTestInfo treeInfo = new ColumnTreeTestInfo(tree, columnTreePane);
    treeInfo.verifyColumnHeaders("Reference", "Depth", "Shallow Size", "Retained Size");

    MemoryObjectTreeNode root = (MemoryObjectTreeNode)tree.getModel().getRoot();
    assertEquals(references.size(), root.getChildCount());
    for (int i = 0; i < root.getChildCount(); i++) {
      ReferenceObject ref = references.get(i);
      treeInfo.verifyRendererValues(root.getChildAt(i),
                                    new String[]{ref.getDisplayLabel()},
                                    new String[]{Integer.toString(ref.getDepth())},
                                    new String[]{Integer.toString(ref.getShallowSize())},
                                    new String[]{Long.toString(ref.getRetainedSize())});
    }
  }
}