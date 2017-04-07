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
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.profilers.*;
import com.android.tools.profilers.memory.MemoryProfilerTestBase.FakeCaptureObjectLoader;
import com.android.tools.profilers.memory.adapters.*;
import com.android.tools.profilers.memory.adapters.FakeInstanceObject.Builder;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.util.containers.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static com.android.tools.profilers.memory.MemoryClassSetView.InstanceTreeNode;
import static org.junit.Assert.*;

public class MemoryClassSetViewTest {
  private static final String MOCK_CLASS_NAME = "MockClass";

  @NotNull private final FakeMemoryService myMemoryService = new FakeMemoryService();
  @Rule public final FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("MemoryInstanceViewTestGrpc", myMemoryService);

  private MemoryProfilerStage myStage;

  private MemoryClassSetView myClassSetView;
  private JTree myClassSetTree;

  private FakeCaptureObject myCaptureObject;
  private List<InstanceObject> myInstanceObjects;

  private MemoryObjectTreeNode<HeapSet> myClassifierSetHeapNode;
  private MemoryObjectTreeNode<MemoryObject> myClassSetRootNode;
  private MemoryProfilerStageView myStageView;
  private JTree myClassifierSetTree;

  @Before
  public void before() {
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices(), new FakeTimer());
    StudioProfilersView profilersView = new StudioProfilersView(profilers, new FakeIdeProfilerComponents());

    FakeCaptureObjectLoader loader = new FakeCaptureObjectLoader();
    loader.setReturnImmediateFuture(true);
    myStage = new MemoryProfilerStage(profilers, loader);
    myStageView = new MemoryProfilerStageView(profilersView, myStage);

    myCaptureObject = new FakeCaptureObject.Builder().build();
    myInstanceObjects = Arrays.asList(
      new Builder(myCaptureObject, MOCK_CLASS_NAME).setName("MockInstance1").createFakeFields(0).setDepth(2).setShallowSize(3)
        .setRetainedSize(4).build(),
      new Builder(myCaptureObject, MOCK_CLASS_NAME).setName("MockInstance2").createFakeFields(1).setDepth(5).setShallowSize(6)
        .setRetainedSize(7).build(),
      new Builder(myCaptureObject, MOCK_CLASS_NAME).setName("MockInstance3").createFakeFields(5).setDepth(Integer.MAX_VALUE)
        .setShallowSize(9).setRetainedSize(10).build());
    myCaptureObject.addInstanceObjects(new HashSet<>(myInstanceObjects));

    myStage.selectCaptureObject(myCaptureObject, MoreExecutors.directExecutor());
    myStage.selectHeapSet(myCaptureObject.getHeapSet(FakeCaptureObject.DEFAULT_HEAP_ID));

    myClassifierSetTree = myStageView.getClassifierView().getTree();
    assertNotNull(myClassifierSetTree);
    Object classifierRoot = myClassifierSetTree.getModel().getRoot();
    assertTrue(classifierRoot instanceof MemoryObjectTreeNode && ((MemoryObjectTreeNode)classifierRoot).getAdapter() instanceof HeapSet);
    //noinspection unchecked
    myClassifierSetHeapNode = (MemoryObjectTreeNode<HeapSet>)classifierRoot;

    myClassSetView = myStageView.getClassSetView();
    ClassifierSet classifierSet = myClassifierSetHeapNode.getAdapter().findContainingClassifierSet(myInstanceObjects.get(0));
    assertTrue(classifierSet instanceof ClassSet);
    myStage.selectClassSet((ClassSet)classifierSet);

    myClassSetTree = myClassSetView.getTree();
    assertNotNull(myClassSetTree);
    Object classSetRoot = myClassSetTree.getModel().getRoot();
    assertTrue(classSetRoot instanceof MemoryObjectTreeNode && ((MemoryObjectTreeNode)classSetRoot).getAdapter() instanceof ClassSet);
    //noinspection unchecked
    myClassSetRootNode = (MemoryObjectTreeNode<MemoryObject>)classSetRoot;
  }

  @Test
  public void testSelectClassSetToShowInClassSetView() {
    assertEquals(3, myClassSetRootNode.getChildCount());
    //noinspection unchecked
    ImmutableList<MemoryObjectTreeNode<MemoryObject>> children = myClassSetRootNode.getChildren();
    // Verify the ordering is based on retain size.
    assertEquals(myInstanceObjects.get(2), children.get(0).getAdapter());
    assertEquals(myInstanceObjects.get(1), children.get(1).getAdapter());
    assertEquals(myInstanceObjects.get(0), children.get(2).getAdapter());
  }

  @Test
  public void testSelectInstanceToShowInInstanceView() {
    assertEquals(0, myClassSetTree.getSelectionCount());
    myStage.selectInstanceObject(myInstanceObjects.get(0));
    assertEquals(1, myClassSetTree.getSelectionCount());
    assertTrue(myClassSetTree.getLastSelectedPathComponent() instanceof MemoryObjectTreeNode);
    assertEquals(myInstanceObjects.get(0), ((MemoryObjectTreeNode)myClassSetTree.getLastSelectedPathComponent()).getAdapter());
  }

  @Test
  public void testTreeSelectionTriggersInstanceSelection() {
    MemoryAspectObserver observer = new MemoryAspectObserver(myStage.getAspect());

    // Selects the first instance object.
    MemoryObjectTreeNode firstNode = (MemoryObjectTreeNode)((MemoryObjectTreeNode)myClassSetTree.getModel().getRoot()).getChildAt(0);
    // Verify the ordering is based on retain size.
    assertEquals(myInstanceObjects.get(2), firstNode.getAdapter());
    myClassSetTree.setSelectionPath(new TreePath(firstNode));
    observer.assertAndResetCounts(0, 0, 0, 0, 0, 0, 1);
    assertEquals(firstNode, myClassSetTree.getSelectionPath().getLastPathComponent());
  }

  @Test
  public void testResetInstanceView() {
    myClassSetView.reset();
    assertNull(myClassSetView.getTree());
  }

  @Test
  public void testCorrectColumnsAndRendererContents() {
    JScrollPane columnTreePane = (JScrollPane)myClassSetView.getColumnTree();
    assertNotNull(columnTreePane);
    ColumnTreeTestInfo treeInfo = new ColumnTreeTestInfo(myClassSetTree, columnTreePane);
    treeInfo.verifyColumnHeaders("Instance", "Depth", "Shallow Size", "Retained Size");

    MemoryObjectTreeNode root = (MemoryObjectTreeNode)myClassSetTree.getModel().getRoot();
    assertEquals(myInstanceObjects.size(), root.getChildCount());
    for (int i = 0; i < root.getChildCount(); i++) {
      InstanceObject instance = myInstanceObjects.get(2 - i);
      treeInfo.verifyRendererValues(root.getChildAt(i),
                                    new String[]{instance.getName(), null, instance.getValueText(), null, instance.getToStringText()},
                                    new String[]{(instance.getDepth() >= 0 && instance.getDepth() < Integer.MAX_VALUE) ?
                                                 Integer.toString(instance.getDepth()) : ""},
                                    new String[]{Integer.toString(instance.getShallowSize())},
                                    new String[]{Long.toString(instance.getRetainedSize())});
    }
  }

  @Test
  public void testInstanceTreeNodeExpansion() {
    InstanceTreeNode instanceTreeNode0 = new InstanceTreeNode(myInstanceObjects.get(0));
    InstanceTreeNode instanceTreeNode1 = new InstanceTreeNode(myInstanceObjects.get(1));
    InstanceTreeNode instanceTreeNode2 = new InstanceTreeNode(myInstanceObjects.get(2));
    assertEquals(0, instanceTreeNode0.getBuiltChildren().size());
    assertEquals(0, instanceTreeNode1.getBuiltChildren().size());
    assertEquals(0, instanceTreeNode2.getBuiltChildren().size());
    instanceTreeNode0.expandNode();
    instanceTreeNode1.expandNode();
    instanceTreeNode2.expandNode();
    assertEquals(0, instanceTreeNode0.getBuiltChildren().size());
    assertTrue(instanceTreeNode0.getBuiltChildren().stream().allMatch(node -> node.getAdapter() instanceof FieldObject));
    assertEquals(1, instanceTreeNode1.getBuiltChildren().size());
    assertTrue(instanceTreeNode1.getBuiltChildren().stream().allMatch(node -> node.getAdapter() instanceof FieldObject));
    assertEquals(5, instanceTreeNode2.getBuiltChildren().size());
    assertTrue(instanceTreeNode2.getBuiltChildren().stream().allMatch(node -> node.getAdapter() instanceof FieldObject));
  }

  @Test
  public void testLazyPopulateSiblings() {
    myCaptureObject = new FakeCaptureObject.Builder().build();
    // create a mock class containing 209 instances
    List<InstanceObject> fakeInstances = new ArrayList<>();
    for (int i = 0; i < 209; i++) {
      String name = Integer.toString(i);
      fakeInstances.add(
        new FakeInstanceObject.Builder(myCaptureObject, MOCK_CLASS_NAME).setName(name).setShallowSize(i).setDepth(i).setRetainedSize(i)
          .build());
    }
    myCaptureObject.addInstanceObjects(new HashSet<>(fakeInstances));
    myStage.selectCaptureObject(myCaptureObject, MoreExecutors.directExecutor());
    myStage.selectHeapSet(myCaptureObject.getHeapSet(FakeCaptureObject.DEFAULT_HEAP_ID));

    myClassifierSetTree = myStageView.getClassifierView().getTree();
    assertNotNull(myClassifierSetTree);
    Object classifierRoot = myClassifierSetTree.getModel().getRoot();
    assertTrue(classifierRoot instanceof MemoryObjectTreeNode && ((MemoryObjectTreeNode)classifierRoot).getAdapter() instanceof HeapSet);
    //noinspection unchecked
    myClassifierSetHeapNode = (MemoryObjectTreeNode<HeapSet>)classifierRoot;

    ClassifierSet classifierSet = myClassifierSetHeapNode.getAdapter().findContainingClassifierSet(fakeInstances.get(0));
    assertTrue(classifierSet instanceof ClassSet);
    myStage.selectClassSet((ClassSet)classifierSet);

    myClassSetTree = myClassSetView.getTree();
    assertNotNull(myClassSetTree);
    Object classSetRoot = myClassSetTree.getModel().getRoot();
    assertTrue(classSetRoot instanceof MemoryObjectTreeNode && ((MemoryObjectTreeNode)classSetRoot).getAdapter() instanceof ClassSet);
    //noinspection unchecked
    myClassSetRootNode = (MemoryObjectTreeNode<MemoryObject>)classSetRoot;

    // View would display only the first 100 object, plus an extra node for sibling expansion.
    assertEquals(101, myClassSetRootNode.getChildCount());

    // Selecting a regular node would do nothing
    myClassSetTree.addSelectionPath(new TreePath(new Object[]{myClassSetRootNode, myClassSetRootNode.getChildAt(0)}));
    assertEquals(101, myClassSetRootNode.getChildCount());

    // Selecting the last node would expand the next 100
    myClassSetTree.addSelectionPath(new TreePath(new Object[]{myClassSetRootNode, myClassSetRootNode.getChildAt(100)}));
    assertEquals(201, myClassSetRootNode.getChildCount());

    // Selecting the last node again would expand the remaining 9
    myClassSetTree.addSelectionPath(new TreePath(new Object[]{myClassSetRootNode, myClassSetRootNode.getChildAt(200)}));
    assertEquals(209, myClassSetRootNode.getChildCount());
  }
}
