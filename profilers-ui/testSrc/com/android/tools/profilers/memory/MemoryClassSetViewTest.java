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
package com.android.tools.profilers.memory;

import com.android.tools.adtui.common.ColumnTreeTestInfo;
import com.android.tools.profilers.*;
import com.android.tools.profilers.stacktrace.CodeLocation;
import com.android.tools.profilers.stacktrace.ContextMenuItem;
import com.android.tools.profilers.memory.adapters.ClassObject;
import com.android.tools.profilers.memory.adapters.FieldObject;
import com.android.tools.profilers.memory.adapters.HeapObject;
import com.android.tools.profilers.memory.adapters.InstanceObject;
import com.intellij.util.containers.ImmutableList;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.swing.*;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static com.android.tools.profilers.memory.MemoryInstanceView.InstanceTreeNode;
import static org.junit.Assert.*;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class MemoryInstanceViewTest {
  private static final String MOCK_CLASS_NAME = "MockClass";

  private static final List<InstanceObject> INSTANCE_OBJECT_LIST = Arrays.asList(
    MemoryProfilerTestBase.mockInstanceObject(MOCK_CLASS_NAME, "MockInstance1", "string1", null, null, 0, 2, 3, 4),
    MemoryProfilerTestBase.mockInstanceObject(MOCK_CLASS_NAME, "MockInstance2", "string2", null, null, 1, 5, 6, 7),
    MemoryProfilerTestBase.mockInstanceObject(MOCK_CLASS_NAME, "MockInstance3", "string3", null, null, 5, Integer.MAX_VALUE, 9, 10));

  private static final ClassObject MOCK_CLASS = MemoryProfilerTestBase.mockClassObject(MOCK_CLASS_NAME, 1, 2, 3, INSTANCE_OBJECT_LIST);

  @Rule public final FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("MemoryInstanceViewTestGrpc", new FakeMemoryService());

  private FakeIdeProfilerComponents myFakeIdeProfilerComponents;
  private MemoryProfilerStage myStage;
  private MemoryProfilerStageView myStageView;

  @Before
  public void before() {
    FakeIdeProfilerServices profilerServices = new FakeIdeProfilerServices();
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), profilerServices);

    myFakeIdeProfilerComponents = new FakeIdeProfilerComponents();
    StudioProfilersView profilersView = new StudioProfilersView(profilers, myFakeIdeProfilerComponents);

    myStage = spy(new MemoryProfilerStage(new StudioProfilers(myGrpcChannel.getClient(), profilerServices)));
    myStageView = new MemoryProfilerStageView(profilersView, myStage);
  }

  @Test
  public void testSelectClassToShowInInstanceView() {
    MemoryInstanceView view = new MemoryInstanceView(myStage, myFakeIdeProfilerComponents);

    myStage.selectClass(MOCK_CLASS);
    assertNotNull(view.getTree());
    assertTrue(view.getTree().getModel().getRoot() instanceof MemoryObjectTreeNode);
    MemoryObjectTreeNode root = (MemoryObjectTreeNode)view.getTree().getModel().getRoot();
    assertEquals(3, root.getChildCount());
    //noinspection unchecked
    ImmutableList<MemoryObjectTreeNode<InstanceObject>> children = root.getChildren();
    // Verify the ordering is based on retain size.
    assertEquals(INSTANCE_OBJECT_LIST.get(2), children.get(0).getAdapter());
    assertEquals(INSTANCE_OBJECT_LIST.get(1), children.get(1).getAdapter());
    assertEquals(INSTANCE_OBJECT_LIST.get(0), children.get(2).getAdapter());
  }

  @Test
  public void testSelectInstanceToShowInInstanceView() {
    MemoryInstanceView view = new MemoryInstanceView(myStage, myFakeIdeProfilerComponents);
    myStage.selectClass(MOCK_CLASS);
    assertNotNull(view.getTree());
    assertEquals(0, view.getTree().getSelectionCount());
    myStage.selectInstance(INSTANCE_OBJECT_LIST.get(0));
    assertEquals(1, view.getTree().getSelectionCount());
  }

  @Test
  public void testTreeSelectionTriggersInstanceSelection() {
    MemoryInstanceView view = new MemoryInstanceView(myStage, myFakeIdeProfilerComponents);
    myStage.selectClass(MOCK_CLASS);
    MemoryAspectObserver observer = new MemoryAspectObserver(myStage.getAspect());

    // Selects the first instance object.
    JTree tree = view.getTree();
    MemoryObjectTreeNode firstNode = (MemoryObjectTreeNode)((MemoryObjectTreeNode)tree.getModel().getRoot()).getChildAt(0);
    // Verify the ordering is based on retain size.
    assertEquals(INSTANCE_OBJECT_LIST.get(2), firstNode.getAdapter());
    tree.setSelectionPath(new TreePath(firstNode));
    observer.assertAndResetCounts(0, 0, 0, 0, 0, 0, 1);
    assertEquals(firstNode, tree.getSelectionPath().getLastPathComponent());
  }

  @Test
  public void testResetInstanceView() {
    MemoryInstanceView view = new MemoryInstanceView(myStage, myFakeIdeProfilerComponents);
    myStage.selectClass(MOCK_CLASS);
    assertNotNull(view.getTree());
    assertTrue(view.getTree().getModel().getRoot() instanceof MemoryObjectTreeNode);
    view.reset();
    assertNull(view.getTree());
  }

  @Test
  public void testGoToInstance() {
    InstanceObject instance = MemoryProfilerTestBase.mockInstanceObject("instanceClass", "instance", null, null, null, 1, 1, 2, 3);
    assertEquals(1, instance.getFieldCount());
    assertEquals(1, instance.getFields().size());

    // Setup a Class-Instance-Fields hierarchy so that the instance contains a field of a different class
    FieldObject field = instance.getFields().get(0);
    ClassObject fieldClass = MemoryProfilerTestBase.mockClassObject("fieldClass", 1, 2, 3, Collections.singletonList(field));
    HeapObject fieldHeap = MemoryProfilerTestBase.mockHeapObject("fieldHeap", Collections.singletonList(fieldClass));

    when(field.getClassObject()).thenReturn(fieldClass);
    when(fieldClass.getHeapObject()).thenReturn(fieldHeap);

    ClassObject instanceClass = MemoryProfilerTestBase.mockClassObject("instanceClass", 1, 2, 3, Collections.singletonList(instance));

    MemoryInstanceView view = new MemoryInstanceView(myStage, myFakeIdeProfilerComponents);
    myStage.selectClass(instanceClass);
    JTree tree = view.getTree();

    // Check that the Go To Instance menu item exists but is disabled since no instance is selected
    List<ContextMenuItem> menus = myFakeIdeProfilerComponents.getComponentContextMenus(tree);
    assertEquals(1, menus.size());
    assertEquals("Go to Instance", menus.get(0).getText());
    assertFalse(menus.get(0).isEnabled());

    // Expands the instance in the tree to select the field
    TreeNode instanceNode = ((MemoryObjectTreeNode)tree.getModel().getRoot()).getChildAt(0);
    tree.expandPath(new TreePath(instanceNode));
    TreeNode fieldNode = instanceNode.getChildAt(0);
    tree.setSelectionPath(new TreePath(fieldNode));
    assertEquals(instanceClass, myStage.getSelectedClass());
    assertEquals(field, myStage.getSelectedInstance());

    // Trigger the context menu action to go to the field's class
    assertTrue(menus.get(0).isEnabled());
    menus.get(0).run();
    assertEquals(fieldHeap, myStage.getSelectedHeap());
    assertEquals(fieldClass, myStage.getSelectedClass());
    assertEquals(field, myStage.getSelectedInstance());
  }

  @Test
  public void navigationTest() {
    final String testClassName = "com.Foo";
    InstanceObject mockInstance = MemoryProfilerTestBase.mockInstanceObject(testClassName, "TestInstance", null, null, null, 0, 1, 2, 3);
    ClassObject mockClass = MemoryProfilerTestBase.mockClassObject(testClassName, 4, 5, 6, Collections.singletonList(mockInstance));
    List<ClassObject> mockClassObjects = Collections.singletonList(mockClass);
    HeapObject mockHeap = MemoryProfilerTestBase.mockHeapObject("TestHeap", mockClassObjects);
    myStage.selectHeap(mockHeap);
    myStage.selectClass(mockClass);
    myStage.selectInstance(mockInstance);

    JTree instanceTree = myStageView.getClassView().getTree();
    assertNotNull(instanceTree);
    Supplier<CodeLocation> codeLocationSupplier = myFakeIdeProfilerComponents.getCodeLocationSupplier(instanceTree);

    assertNotNull(codeLocationSupplier);
    CodeLocation codeLocation = codeLocationSupplier.get();
    assertNotNull(codeLocation);
    String codeLocationClassName = codeLocation.getClassName();
    assertEquals(testClassName, codeLocationClassName);

    myStage.getStudioProfilers().getIdeServices().getCodeNavigator().navigate(codeLocation);
    assertEquals(ProfilerMode.NORMAL, myStage.getProfilerMode());
  }

  @Test
  public void testCorrectColumnsAndRendererContents() {
    MemoryInstanceView view = new MemoryInstanceView(myStage, myFakeIdeProfilerComponents);
    myStage.selectClass(MOCK_CLASS);

    JTree tree = view.getTree();
    assertNotNull(tree);
    JScrollPane columnTreePane = (JScrollPane)view.getColumnTree();
    assertNotNull(columnTreePane);
    ColumnTreeTestInfo treeInfo = new ColumnTreeTestInfo(tree, columnTreePane);
    treeInfo.verifyColumnHeaders("Instance", "Depth", "Shallow Size", "Retained Size");

    MemoryObjectTreeNode root = (MemoryObjectTreeNode)tree.getModel().getRoot();
    assertEquals(INSTANCE_OBJECT_LIST.size(), root.getChildCount());
    for (int i = 0; i < root.getChildCount(); i++) {
      InstanceObject instance = INSTANCE_OBJECT_LIST.get(2 - i);
      treeInfo.verifyRendererValues(root.getChildAt(i),
                                    new String[]{instance.getName(), instance.getToStringText()},
                                    new String[]{(instance.getDepth() >= 0 && instance.getDepth() < Integer.MAX_VALUE) ?
                                                 Integer.toString(instance.getDepth()) : ""},
                                    new String[]{Integer.toString(instance.getShallowSize())},
                                    new String[]{Long.toString(instance.getRetainedSize())});
    }
  }

  @Test
  public void testInstanceTreeNodeExpansion() {
    InstanceTreeNode instanceTreeNode0 = new InstanceTreeNode(INSTANCE_OBJECT_LIST.get(0));
    InstanceTreeNode instanceTreeNode1 = new InstanceTreeNode(INSTANCE_OBJECT_LIST.get(1));
    InstanceTreeNode instanceTreeNode2 = new InstanceTreeNode(INSTANCE_OBJECT_LIST.get(2));
    assertEquals(0, instanceTreeNode0.getBuiltChildren().size());
    assertEquals(0, instanceTreeNode1.getBuiltChildren().size());
    assertEquals(0, instanceTreeNode2.getBuiltChildren().size());
    instanceTreeNode0.expandNode();
    instanceTreeNode1.expandNode();
    instanceTreeNode2.expandNode();
    assertEquals(0, instanceTreeNode0.getBuiltChildren().size());
    assertTrue(instanceTreeNode0.getBuiltChildren().stream().allMatch(node -> node instanceof InstanceTreeNode));
    assertEquals(1, instanceTreeNode1.getBuiltChildren().size());
    assertTrue(instanceTreeNode1.getBuiltChildren().stream().allMatch(node -> node instanceof InstanceTreeNode));
    assertEquals(5, instanceTreeNode2.getBuiltChildren().size());
    assertTrue(instanceTreeNode2.getBuiltChildren().stream().allMatch(node -> node instanceof InstanceTreeNode));
  }

  @Test
  public void testLazyPopulateSiblings() {
    MemoryInstanceView view = new MemoryInstanceView(myStage, myFakeIdeProfilerComponents);

    // create a mock class containing 209 instances
    List<InstanceObject> fakeInstances = new ArrayList<>();
    for (int i = 0; i < 209; i++) {
      String name = Integer.toString(i);
      fakeInstances.add(MemoryProfilerTestBase.mockInstanceObject(
        MOCK_CLASS_NAME, name, name, null, null, i, i, i, i));
    }

    ClassObject fakeClass = MemoryProfilerTestBase.mockClassObject(
      MOCK_CLASS_NAME, 1, 1, 1, fakeInstances);
    myStage.selectClass(fakeClass);
    JTree tree = view.getTree();
    MemoryObjectTreeNode root = (MemoryObjectTreeNode)tree.getModel().getRoot();

    // View would display only the first 100 object, plus an extra node for sibling expansion.
    assertEquals(101, root.getChildCount());

    // Selecting a regular node would do nothing
    tree.addSelectionPath(new TreePath(new Object[]{root, root.getChildAt(0)}));
    assertEquals(101, root.getChildCount());

    // Selecting the last node would expand the next 100
    tree.addSelectionPath(new TreePath(new Object[]{root, root.getChildAt(100)}));
    assertEquals(201, root.getChildCount());

    // Selecting the last node again would expand the remaining 9
    tree.addSelectionPath(new TreePath(new Object[]{root, root.getChildAt(200)}));
    assertEquals(209, root.getChildCount());
  }
}
