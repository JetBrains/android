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
import com.android.tools.profiler.proto.MemoryProfiler.AllocationStack;
import com.android.tools.profilers.*;
import com.android.tools.profilers.memory.MemoryProfilerTestBase.FakeCaptureObjectLoader;
import com.android.tools.profilers.memory.adapters.*;
import com.android.tools.profilers.memory.adapters.FakeInstanceObject.Builder;
import com.android.tools.profilers.stacktrace.CodeLocation;
import com.intellij.util.containers.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.util.*;
import java.util.function.Supplier;

import static com.android.tools.profilers.memory.MemoryClassSetView.InstanceTreeNode;
import static com.android.tools.profilers.memory.MemoryProfilerConfiguration.ClassGrouping.*;
import static com.android.tools.profilers.memory.MemoryProfilerTestUtils.findChildClassSetWithName;
import static com.android.tools.profilers.memory.MemoryProfilerTestUtils.findChildWithPredicate;
import static com.android.tools.profilers.memory.adapters.ValueObject.ValueType.OBJECT;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNotNull;

public class MemoryClassSetViewTest {
  private static final String MOCK_CLASS_NAME = "MockClass";

  @NotNull private final FakeMemoryService myMemoryService = new FakeMemoryService();
  @NotNull private final FakeIdeProfilerComponents myFakeIdeProfilerComponents = new FakeIdeProfilerComponents();
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
    StudioProfilersView profilersView = new StudioProfilersView(profilers, myFakeIdeProfilerComponents);

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

    myStage.selectCaptureDuration(
      new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> myCaptureObject)), null);
    myStage.selectHeapSet(myCaptureObject.getHeapSet(FakeCaptureObject.DEFAULT_HEAP_ID));

    myClassifierSetTree = myStageView.getClassifierView().getTree();
    assertNotNull(myClassifierSetTree);
    Object classifierRoot = myClassifierSetTree.getModel().getRoot();
    assertThat(classifierRoot).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode)classifierRoot).getAdapter()).isInstanceOf(HeapSet.class);
    //noinspection unchecked
    myClassifierSetHeapNode = (MemoryObjectTreeNode<HeapSet>)classifierRoot;

    myClassSetView = myStageView.getClassSetView();
    ClassifierSet classifierSet = myClassifierSetHeapNode.getAdapter().findContainingClassifierSet(myInstanceObjects.get(0));
    assertThat(classifierSet).isInstanceOf(ClassSet.class);
    myStage.selectClassSet((ClassSet)classifierSet);

    myClassSetTree = myClassSetView.getTree();
    assertNotNull(myClassSetTree);
    Object classSetRoot = myClassSetTree.getModel().getRoot();
    assertThat(classSetRoot).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode)classSetRoot).getAdapter()).isInstanceOf(ClassSet.class);
    //noinspection unchecked
    myClassSetRootNode = (MemoryObjectTreeNode<MemoryObject>)classSetRoot;
  }

  @Test
  public void testSelectClassSetToShowInClassSetView() {
    assertThat(myClassSetRootNode.getChildCount()).isEqualTo(3);
    //noinspection unchecked
    ImmutableList<MemoryObjectTreeNode<MemoryObject>> children = myClassSetRootNode.getChildren();
    // Verify the ordering is based on retain size.
    assertThat(children.get(0).getAdapter()).isEqualTo(myInstanceObjects.get(2));
    assertThat(children.get(1).getAdapter()).isEqualTo(myInstanceObjects.get(1));
    assertThat(children.get(2).getAdapter()).isEqualTo(myInstanceObjects.get(0));
  }

  @Test
  public void testSelectInstanceToShowInInstanceView() {
    assertThat(myClassSetTree.getSelectionCount()).isEqualTo(0);
    myStage.selectInstanceObject(myInstanceObjects.get(0));
    assertThat(myClassSetTree.getSelectionCount()).isEqualTo(1);
    assertThat(myClassSetTree.getLastSelectedPathComponent()).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode)myClassSetTree.getLastSelectedPathComponent()).getAdapter()).isEqualTo(myInstanceObjects.get(0));
  }

  @Test
  public void testTreeSelectionTriggersInstanceSelection() {
    MemoryAspectObserver observer = new MemoryAspectObserver(myStage.getAspect());

    // Selects the first instance object.
    MemoryObjectTreeNode firstNode = (MemoryObjectTreeNode)((MemoryObjectTreeNode)myClassSetTree.getModel().getRoot()).getChildAt(0);
    // Verify the ordering is based on retain size.
    assertThat(firstNode.getAdapter()).isEqualTo(myInstanceObjects.get(2));
    myClassSetTree.setSelectionPath(new TreePath(firstNode));
    observer.assertAndResetCounts(0, 0, 0, 0, 0, 0, 1, 0);
    assertThat(myClassSetTree.getSelectionPath().getLastPathComponent()).isEqualTo(firstNode);
  }

  @Test
  public void testResetInstanceView() {
    myClassSetView.reset();
    assertThat(myClassSetView.getTree()).isNull();
  }

  @Test
  public void testCorrectColumnsAndRendererContents() {
    JScrollPane columnTreePane = (JScrollPane)myClassSetView.getColumnTree();
    assertThat(columnTreePane).isNotNull();
    ColumnTreeTestInfo treeInfo = new ColumnTreeTestInfo(myClassSetTree, columnTreePane);
    treeInfo.verifyColumnHeaders("Instance", "Alloc Time", "Dealloc Time", "Depth", "Native Size", "Shallow Size", "Retained Size");

    MemoryObjectTreeNode root = (MemoryObjectTreeNode)myClassSetTree.getModel().getRoot();
    assertThat(root.getChildCount()).isEqualTo(myInstanceObjects.size());
    for (int i = 0; i < root.getChildCount(); i++) {
      InstanceObject instance = myInstanceObjects.get(2 - i);
      treeInfo.verifyRendererValues(root.getChildAt(i),
                                    new String[]{instance.getName(), null, instance.getValueText(), null, instance.getToStringText()},
                                    new String[]{""},
                                    new String[]{""},
                                    new String[]{(instance.getDepth() >= 0 && instance.getDepth() < Integer.MAX_VALUE) ?
                                                 Integer.toString(instance.getDepth()) : ""},
                                    new String[]{Long.toString(instance.getNativeSize())},
                                    new String[]{Integer.toString(instance.getShallowSize())},
                                    new String[]{Long.toString(instance.getRetainedSize())});
    }
  }

  @Test
  public void fieldSelectionAndNavigationTest() {
    final String TEST_CLASS_NAME = "com.Foo";
    final String TEST_FIELD_NAME = "com.Field";

    MemoryAspectObserver aspectObserver = new MemoryAspectObserver(myStage.getAspect());

    CodeLocation codeLocationFoo = new CodeLocation.Builder("Foo").setMethodName("fooMethod1").setLineNumber(5).build();
    CodeLocation codeLocationBar = new CodeLocation.Builder("Bar").setMethodName("barMethod1").setLineNumber(20).build();

    //noinspection ConstantConditions
    AllocationStack callstackFoo = AllocationStack.newBuilder()
      .setFullStack(
        AllocationStack.StackFrameWrapper.newBuilder()
          .addFrames(
            AllocationStack.StackFrame.newBuilder()
              .setClassName(codeLocationFoo.getClassName())
              .setMethodName(codeLocationFoo.getMethodName())
              .setLineNumber(codeLocationFoo.getLineNumber() + 1)))
      .build();
    //noinspection ConstantConditions
    AllocationStack callstackBar = AllocationStack.newBuilder()
      .setFullStack(
        AllocationStack.StackFrameWrapper.newBuilder()
          .addFrames(
            AllocationStack.StackFrame.newBuilder()
              .setClassName(codeLocationBar.getClassName())
              .setMethodName(codeLocationBar.getMethodName())
              .setLineNumber(codeLocationBar.getLineNumber() + 1)))
      .build();

    FakeCaptureObject captureObject = new FakeCaptureObject.Builder().build();
    FakeInstanceObject instanceFooField =
      new FakeInstanceObject.Builder(captureObject, TEST_FIELD_NAME).setName("instanceFooField").build();
    FakeInstanceObject instanceBarField =
      new FakeInstanceObject.Builder(captureObject, TEST_FIELD_NAME).setName("instanceBarField").build();
    FakeFieldObject fieldFoo = new FakeFieldObject("fieldFoo", OBJECT, instanceFooField);
    FakeFieldObject fieldBar = new FakeFieldObject("fieldBar", OBJECT, instanceBarField);

    FakeInstanceObject instanceFoo =
      new FakeInstanceObject.Builder(captureObject, TEST_CLASS_NAME).setName("instanceFoo").setAllocationStack(callstackFoo)
        .setFields(Collections.singletonList(fieldFoo.getFieldName())).build();
    instanceFoo.setFieldValue(fieldFoo.getFieldName(), fieldFoo.getValueType(), instanceFooField);
    FakeInstanceObject instanceBar =
      new FakeInstanceObject.Builder(captureObject, TEST_CLASS_NAME).setName("instanceBar").setAllocationStack(callstackBar)
        .setFields(Collections.singletonList(fieldBar.getFieldName())).build();
    instanceBar.setFieldValue(fieldBar.getFieldName(), fieldBar.getValueType(), instanceBarField);

    Set<InstanceObject> instanceObjects = new HashSet<>(Arrays.asList(instanceFoo, instanceBar, instanceFooField, instanceBarField));
    captureObject.addInstanceObjects(instanceObjects);
    myStage
      .selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> captureObject)),
                             null);

    assertThat(myStage.getConfiguration().getClassGrouping()).isEqualTo(ARRANGE_BY_CLASS);
    assertThat(myStage.getSelectedHeapSet()).isNotNull();
    assertThat(myStage.getSelectedHeapSet().getId()).isEqualTo(FakeCaptureObject.DEFAULT_HEAP_ID);
    myStage.selectClassSet(findChildClassSetWithName(myStage.getSelectedHeapSet(), TEST_CLASS_NAME));
    myStage.selectInstanceObject(instanceFoo);
    myStage.selectFieldObjectPath(Collections.singletonList(fieldFoo));
    aspectObserver.assertAndResetCounts(0, 1, 1, 0, 2, 2, 1, 1);

    myStage.getConfiguration().setClassGrouping(ARRANGE_BY_CALLSTACK);
    aspectObserver.assertAndResetCounts(0, 0, 0, 1, 0, 1, 2, 2);
    assertThat(myStage.getSelectedInstanceObject()).isEqualTo(instanceFoo);
    assertThat(myStage.getSelectedFieldObjectPath()).isEqualTo(Collections.singletonList(fieldFoo));

    myClassSetTree = myClassSetView.getTree();
    assertThat(myClassSetTree).isNotNull();
    Object classSetRoot = myClassSetTree.getModel().getRoot();
    assertThat(classSetRoot).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode)classSetRoot).getAdapter()).isInstanceOf(ClassSet.class);
    //noinspection unchecked
    myClassSetRootNode = (MemoryObjectTreeNode<MemoryObject>)classSetRoot;
    findChildWithPredicate(findChildWithPredicate(myClassSetRootNode, instance -> instance == instanceFoo),
                           field -> Objects.equals(field, fieldFoo));

    myStage.getConfiguration().setClassGrouping(ARRANGE_BY_PACKAGE);
    aspectObserver.assertAndResetCounts(0, 0, 0, 1, 0, 1, 2, 2);
    assertThat(myStage.getSelectedInstanceObject()).isEqualTo(instanceFoo);
    assertThat(myStage.getSelectedFieldObjectPath()).isEqualTo(Collections.singletonList(fieldFoo));

    Supplier<CodeLocation> codeLocationSupplier = myFakeIdeProfilerComponents.getCodeLocationSupplier(myClassSetTree);

    assertThat(codeLocationSupplier).isNotNull();
    CodeLocation codeLocation = codeLocationSupplier.get();
    assertThat(codeLocation).isNotNull();
    String codeLocationClassName = codeLocation.getClassName();
    assertThat(codeLocationClassName).isEqualTo(TEST_FIELD_NAME);

    myStage.getStudioProfilers().getIdeServices().getCodeNavigator().addListener(myStage); // manually add, since we didn't enter stage
    myStage.getStudioProfilers().getIdeServices().getCodeNavigator().navigate(codeLocation);
    myStage.getStudioProfilers().getIdeServices().getCodeNavigator().removeListener(myStage);
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.NORMAL);
  }

  @Test
  public void testInstanceTreeNodeExpansion() {
    InstanceTreeNode instanceTreeNode0 = new InstanceTreeNode(myInstanceObjects.get(0));
    InstanceTreeNode instanceTreeNode1 = new InstanceTreeNode(myInstanceObjects.get(1));
    InstanceTreeNode instanceTreeNode2 = new InstanceTreeNode(myInstanceObjects.get(2));
    assertThat(instanceTreeNode0.getBuiltChildren().size()).isEqualTo(0);
    assertThat(instanceTreeNode1.getBuiltChildren().size()).isEqualTo(0);
    assertThat(instanceTreeNode2.getBuiltChildren().size()).isEqualTo(0);
    instanceTreeNode0.expandNode();
    instanceTreeNode1.expandNode();
    instanceTreeNode2.expandNode();
    assertThat(instanceTreeNode0.getBuiltChildren().size()).isEqualTo(0);
    assertThat(instanceTreeNode0.getBuiltChildren().stream().allMatch(node -> node.getAdapter() instanceof FieldObject)).isTrue();
    assertThat(instanceTreeNode1.getBuiltChildren().size()).isEqualTo(1);
    assertThat(instanceTreeNode1.getBuiltChildren().stream().allMatch(node -> node.getAdapter() instanceof FieldObject)).isTrue();
    assertThat(instanceTreeNode2.getBuiltChildren().size()).isEqualTo(5);
    assertThat(instanceTreeNode2.getBuiltChildren().stream().allMatch(node -> node.getAdapter() instanceof FieldObject)).isTrue();
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
    myStage.selectCaptureDuration(
      new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> myCaptureObject)),
      null);
    myStage.selectHeapSet(myCaptureObject.getHeapSet(FakeCaptureObject.DEFAULT_HEAP_ID));

    myClassifierSetTree = myStageView.getClassifierView().getTree();
    assertThat(myClassifierSetTree).isNotNull();
    Object classifierRoot = myClassifierSetTree.getModel().getRoot();
    assertThat(classifierRoot).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode)classifierRoot).getAdapter()).isInstanceOf(HeapSet.class);
    //noinspection unchecked
    myClassifierSetHeapNode = (MemoryObjectTreeNode<HeapSet>)classifierRoot;

    ClassifierSet classifierSet = myClassifierSetHeapNode.getAdapter().findContainingClassifierSet(fakeInstances.get(0));
    assertThat(classifierSet).isInstanceOf(ClassSet.class);
    myStage.selectClassSet((ClassSet)classifierSet);

    myClassSetTree = myClassSetView.getTree();
    assertThat(myClassSetTree).isNotNull();
    Object classSetRoot = myClassSetTree.getModel().getRoot();
    assertThat(classSetRoot).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode)classSetRoot).getAdapter()).isInstanceOf(ClassSet.class);
    //noinspection unchecked
    myClassSetRootNode = (MemoryObjectTreeNode<MemoryObject>)classSetRoot;

    // View would display only the first 100 object, plus an extra node for sibling expansion.
    assertThat(myClassSetRootNode.getChildCount()).isEqualTo(101);

    // Selecting a regular node would do nothing
    myClassSetTree.addSelectionPath(new TreePath(new Object[]{myClassSetRootNode, myClassSetRootNode.getChildAt(0)}));
    assertThat(myClassSetRootNode.getChildCount()).isEqualTo(101);

    // Selecting the last node would expand the next 100
    myClassSetTree.addSelectionPath(new TreePath(new Object[]{myClassSetRootNode, myClassSetRootNode.getChildAt(100)}));
    assertThat(myClassSetRootNode.getChildCount()).isEqualTo(201);

    // Selecting the last node again would expand the remaining 9
    myClassSetTree.addSelectionPath(new TreePath(new Object[]{myClassSetRootNode, myClassSetRootNode.getChildAt(200)}));
    assertThat(myClassSetRootNode.getChildCount()).isEqualTo(209);
  }


  @Test
  public void testSelectedInstanceAfterHeapChanged() {
    assertThat(myClassSetRootNode.getAdapter()).isInstanceOf(ClassSet.class);
    ClassSet classSet = (ClassSet)myClassSetRootNode.getAdapter();

    // Select MockInstance1
    myStage.selectInstanceObject(myInstanceObjects.get(0));
    assertThat(myStage.getSelectedInstanceObject()).isEqualTo(myInstanceObjects.get(0));

    // Remove MockInstance2 and refresh selected heap, myStage should still select MockInstance1
    classSet.removeAddingInstanceObject(myInstanceObjects.get(1));
    myStage.refreshSelectedHeap();
    assertThat(myStage.getSelectedInstanceObject()).isEqualTo(myInstanceObjects.get(0));

    // Remove MockInstance1 and refresh selected heap, myStage should not select any InstanceObject
    classSet.removeAddingInstanceObject(myInstanceObjects.get(0));
    myStage.refreshSelectedHeap();
    assertThat(myStage.getSelectedInstanceObject()).isNull();
  }
}
