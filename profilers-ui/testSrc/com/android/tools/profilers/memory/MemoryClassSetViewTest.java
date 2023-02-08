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
import com.android.tools.adtui.model.formatter.NumberFormatter;
import com.android.tools.idea.codenavigation.CodeLocation;
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel;
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.profiler.proto.Memory.AllocationStack;
import com.android.tools.profilers.*;
import com.android.tools.profilers.memory.adapters.*;
import com.android.tools.profilers.memory.adapters.FakeInstanceObject.Builder;
import com.android.tools.profilers.memory.adapters.classifiers.ClassSet;
import com.android.tools.profilers.memory.adapters.classifiers.ClassifierSet;
import com.android.tools.profilers.memory.adapters.classifiers.HeapSet;
import com.intellij.testFramework.ApplicationRule;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.util.*;
import java.util.function.Supplier;

import static com.android.tools.profilers.memory.ClassGrouping.*;
import static com.android.tools.profilers.memory.MemoryProfilerTestUtils.findChildClassSetWithName;
import static com.android.tools.profilers.memory.MemoryProfilerTestUtils.findChildWithPredicate;
import static com.android.tools.profilers.memory.adapters.MemoryObject.INVALID_VALUE;
import static com.android.tools.profilers.memory.adapters.ValueObject.ValueType.OBJECT;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNotNull;

public class MemoryClassSetViewTest {
  private static final long MOCK_CLASS_ID = 1;
  private static final String MOCK_CLASS_NAME = "MockClass";

  private final FakeTimer myTimer = new FakeTimer();
  @NotNull private final FakeMemoryService myMemoryService = new FakeMemoryService();
  @NotNull private final FakeIdeProfilerComponents myFakeIdeProfilerComponents = new FakeIdeProfilerComponents();
  @Rule public final FakeGrpcChannel myGrpcChannel =
    new FakeGrpcChannel("MemoryInstanceViewTestGrpc", new FakeTransportService(myTimer), new FakeProfilerService(myTimer), myMemoryService);
  @Rule public final ApplicationRule myApplicationRule = new ApplicationRule();

  private MainMemoryProfilerStage myStage;

  private MemoryClassSetView myClassSetView;
  private JTree myClassSetTree;

  private FakeCaptureObject myCaptureObject;
  private List<InstanceObject> myInstanceObjects;

  private MemoryObjectTreeNode<HeapSet> myClassifierSetHeapNode;
  private MemoryObjectTreeNode<MemoryObject> myClassSetRootNode;
  private MainMemoryProfilerStageView myStageView;
  private JTree myClassifierSetTree;

  @Before
  public void before() {
    StudioProfilers profilers = new StudioProfilers(new ProfilerClient(myGrpcChannel.getChannel()), new FakeIdeProfilerServices(), myTimer);
    StudioProfilersView profilersView = new StudioProfilersView(profilers, myFakeIdeProfilerComponents);

    FakeCaptureObjectLoader loader = new FakeCaptureObjectLoader();
    loader.setReturnImmediateFuture(true);
    myStage = new MainMemoryProfilerStage(profilers, loader);
    myStageView = new MainMemoryProfilerStageView(profilersView, myStage);

    myCaptureObject = new FakeCaptureObject.Builder().build();
    myInstanceObjects = Arrays.asList(
      new Builder(myCaptureObject, MOCK_CLASS_ID, MOCK_CLASS_NAME).setName("MockInstance1").createFakeFields(0).setDepth(2)
        .setShallowSize(3).setRetainedSize(4).build(),
      new Builder(myCaptureObject, MOCK_CLASS_ID, MOCK_CLASS_NAME).setName("MockInstance2").createFakeFields(1).setDepth(5)
        .setShallowSize(6).setRetainedSize(7).build(),
      new Builder(myCaptureObject, MOCK_CLASS_ID, MOCK_CLASS_NAME).setName("MockInstance3").createFakeFields(5).setDepth(Integer.MAX_VALUE)
        .setShallowSize(9).setRetainedSize(10).build());
    myCaptureObject.addInstanceObjects(new HashSet<>(myInstanceObjects));

    myStage.selectCaptureDuration(
      new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> myCaptureObject)), null);
    myStage.getCaptureSelection().selectHeapSet(myCaptureObject.getHeapSet(FakeCaptureObject.DEFAULT_HEAP_ID));

    myClassifierSetTree = myStageView.getClassifierView().getTree();
    assertNotNull(myClassifierSetTree);
    Object classifierRoot = myClassifierSetTree.getModel().getRoot();
    assertThat(classifierRoot).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode<?>)classifierRoot).getAdapter()).isInstanceOf(HeapSet.class);
    //noinspection unchecked
    myClassifierSetHeapNode = (MemoryObjectTreeNode<HeapSet>)classifierRoot;

    myClassSetView = myStageView.getClassSetView();
    ClassifierSet classifierSet = myClassifierSetHeapNode.getAdapter().findContainingClassifierSet(myInstanceObjects.get(0));
    assertThat(classifierSet).isInstanceOf(ClassSet.class);
    myStage.getCaptureSelection().selectClassSet((ClassSet)classifierSet);

    myClassSetTree = myClassSetView.getTree();
    assertNotNull(myClassSetTree);
    Object classSetRoot = myClassSetTree.getModel().getRoot();
    assertThat(classSetRoot).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode<?>)classSetRoot).getAdapter()).isInstanceOf(ClassSet.class);
    //noinspection unchecked
    myClassSetRootNode = (MemoryObjectTreeNode<MemoryObject>)classSetRoot;
  }

  @Test
  public void testSelectClassSetToShowInClassSetView() {
    assertThat(myClassSetRootNode.getChildCount()).isEqualTo(3);
    List<MemoryObjectTreeNode<MemoryObject>> children = myClassSetRootNode.getChildren();
    // Verify the ordering is based on retain size.
    assertThat(children.get(0).getAdapter()).isEqualTo(myInstanceObjects.get(2));
    assertThat(children.get(1).getAdapter()).isEqualTo(myInstanceObjects.get(1));
    assertThat(children.get(2).getAdapter()).isEqualTo(myInstanceObjects.get(0));
  }

  @Test
  public void testSelectInstanceToShowInInstanceView() {
    assertThat(myClassSetTree.getSelectionCount()).isEqualTo(0);
    myStage.getCaptureSelection().selectInstanceObject(myInstanceObjects.get(0));
    assertThat(myClassSetTree.getSelectionCount()).isEqualTo(1);
    assertThat(myClassSetTree.getLastSelectedPathComponent()).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode<?>)myClassSetTree.getLastSelectedPathComponent()).getAdapter()).isEqualTo(myInstanceObjects.get(0));
  }

  @Test
  public void testTreeSelectionTriggersInstanceSelection() {
    MemoryAspectObserver observer = new MemoryAspectObserver(myStage.getAspect(),  myStage.getCaptureSelection().getAspect());

    // Selects the first instance object.
    MemoryObjectTreeNode firstNode = (MemoryObjectTreeNode)((MemoryObjectTreeNode<?>)myClassSetTree.getModel().getRoot()).getChildAt(0);
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
    JScrollPane columnTreePane = (JScrollPane)myClassSetView.getColumnTree().getComponent(0);
    assertThat(columnTreePane).isNotNull();
    ColumnTreeTestInfo treeInfo = new ColumnTreeTestInfo(myClassSetTree, columnTreePane);
    treeInfo.verifyColumnHeaders("Instance", "Alloc Time", "Dealloc Time", "Depth", "Native Size", "Shallow Size", "Retained Size");

    MemoryObjectTreeNode root = (MemoryObjectTreeNode)myClassSetTree.getModel().getRoot();
    assertThat(root.getChildCount()).isEqualTo(myInstanceObjects.size());
    for (int i = 0; i < root.getChildCount(); i++) {
      InstanceObject instance = myInstanceObjects.get(2 - i);
      treeInfo.verifyRendererValues(root.getChildAt(i),
                                    new String[]{instance.getName(), null, instance.getValueText(), null, instance.getToStringText()},
                                    new String[]{"-"},
                                    new String[]{"-"},
                                    new String[]{(instance.getDepth() >= 0 && instance.getDepth() < Integer.MAX_VALUE) ?
                                                 NumberFormatter.formatInteger(instance.getDepth()) : "-"},
                                    new String[]{formatSize(instance.getNativeSize())},
                                    new String[]{formatSize(instance.getShallowSize())},
                                    new String[]{formatSize(instance.getRetainedSize())});
    }
  }

  /**
   * Return text representation of the size, except nothing when it is the special "invalid" value
   */
  private static String formatSize(long size) {
    return size == INVALID_VALUE ? "-" : NumberFormatter.formatInteger(size);
  }

  @Test
  public void instanceSelectionTest() {
    final long TEST_CLASS_ID = 1, TEST_FIELD_ID = 2;
    final String TEST_CLASS_NAME = "com.Foo";
    final String TEST_FIELD_NAME = "com.Field";

    MemoryAspectObserver aspectObserver = new MemoryAspectObserver(myStage.getAspect(), myStage.getCaptureSelection().getAspect());

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
      new FakeInstanceObject.Builder(captureObject, TEST_FIELD_ID, TEST_FIELD_NAME).setName("instanceFooField").build();
    FakeInstanceObject instanceBarField =
      new FakeInstanceObject.Builder(captureObject, TEST_FIELD_ID, TEST_FIELD_NAME).setName("instanceBarField").build();
    FakeFieldObject fieldFoo = new FakeFieldObject("fieldFoo", OBJECT, instanceFooField);
    FakeFieldObject fieldBar = new FakeFieldObject("fieldBar", OBJECT, instanceBarField);

    FakeInstanceObject instanceFoo =
      new FakeInstanceObject.Builder(captureObject, TEST_CLASS_ID, TEST_CLASS_NAME).setName("instanceFoo").setAllocationStack(callstackFoo)
        .setFields(Collections.singletonList(fieldFoo.getFieldName())).build();
    instanceFoo.setFieldValue(fieldFoo.getFieldName(), fieldFoo.getValueType(), instanceFooField);
    FakeInstanceObject instanceBar =
      new FakeInstanceObject.Builder(captureObject, TEST_CLASS_ID, TEST_CLASS_NAME).setName("instanceBar").setAllocationStack(callstackBar)
        .setFields(Collections.singletonList(fieldBar.getFieldName())).build();
    instanceBar.setFieldValue(fieldBar.getFieldName(), fieldBar.getValueType(), instanceBarField);

    Set<InstanceObject> instanceObjects = new HashSet<>(Arrays.asList(instanceFoo, instanceBar, instanceFooField, instanceBarField));
    captureObject.addInstanceObjects(instanceObjects);
    myStage
      .selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> captureObject)),
                             null);

    assertThat(myStage.getCaptureSelection().getClassGrouping()).isEqualTo(ARRANGE_BY_CLASS);
    assertThat(myStage.getCaptureSelection().getSelectedHeapSet()).isNotNull();
    assertThat(myStage.getCaptureSelection().getSelectedHeapSet().getId()).isEqualTo(FakeCaptureObject.DEFAULT_HEAP_ID);
    myStage.getCaptureSelection().selectClassSet(findChildClassSetWithName(myStage.getCaptureSelection().getSelectedHeapSet(), TEST_CLASS_NAME));
    myStage.getCaptureSelection().selectInstanceObject(instanceFoo);
    myStage.getCaptureSelection().selectFieldObjectPath(Collections.singletonList(fieldFoo));
    aspectObserver.assertAndResetCounts(0, 1, 1, 0, 2, 2, 1, 1);

    myStage.getCaptureSelection().setClassGrouping(ARRANGE_BY_CALLSTACK);
    aspectObserver.assertAndResetCounts(0, 0, 0, 1, 0, 1, 2, 2);
    assertThat(myStage.getCaptureSelection().getSelectedInstanceObject()).isEqualTo(instanceFoo);
    assertThat(myStage.getCaptureSelection().getSelectedFieldObjectPath()).isEqualTo(Collections.singletonList(fieldFoo));

    myClassSetTree = myClassSetView.getTree();
    assertThat(myClassSetTree).isNotNull();
    Object classSetRoot = myClassSetTree.getModel().getRoot();
    assertThat(classSetRoot).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode<?>)classSetRoot).getAdapter()).isInstanceOf(ClassSet.class);
    //noinspection unchecked
    myClassSetRootNode = (MemoryObjectTreeNode<MemoryObject>)classSetRoot;
    findChildWithPredicate(myClassSetRootNode, instance -> instance == instanceFoo);

    myStage.getCaptureSelection().setClassGrouping(ARRANGE_BY_PACKAGE);
    aspectObserver.assertAndResetCounts(0, 0, 0, 1, 0, 1, 2, 2);
    assertThat(myStage.getCaptureSelection().getSelectedInstanceObject()).isEqualTo(instanceFoo);
    assertThat(myStage.getCaptureSelection().getSelectedFieldObjectPath()).isEqualTo(Collections.singletonList(fieldFoo));

    Supplier<CodeLocation> codeLocationSupplier = myFakeIdeProfilerComponents.getCodeLocationSupplier(myClassSetTree);

    assertThat(codeLocationSupplier).isNotNull();
    CodeLocation codeLocation = codeLocationSupplier.get();
    assertThat(codeLocation).isNotNull();
    String codeLocationClassName = codeLocation.getClassName();
    assertThat(codeLocationClassName).isEqualTo(TEST_CLASS_NAME);

    myStage.getStudioProfilers().getIdeServices().getCodeNavigator().addListener(myStage); // manually add, since we didn't enter stage
    myStage.getStudioProfilers().getIdeServices().getCodeNavigator().navigate(codeLocation);
    myStage.getStudioProfilers().getIdeServices().getCodeNavigator().removeListener(myStage);
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.NORMAL);
  }

  @Test
  public void testInstanceTreeNodeExpansion() {
    InstanceDetailsTreeNode instanceTreeNode0 = new InstanceDetailsTreeNode(myInstanceObjects.get(0));
    InstanceDetailsTreeNode instanceTreeNode1 = new InstanceDetailsTreeNode(myInstanceObjects.get(1));
    InstanceDetailsTreeNode instanceTreeNode2 = new InstanceDetailsTreeNode(myInstanceObjects.get(2));
    List<InstanceDetailsTreeNode> nodes = Arrays.asList(instanceTreeNode0, instanceTreeNode1, instanceTreeNode2);
    nodes.forEach(n -> assertChildCount(n, 0));
    nodes.forEach(LazyMemoryObjectTreeNode::expandNode);
    assertChildCount(instanceTreeNode0, 0);
    assertChildCount(instanceTreeNode1, 1);
    assertChildCount(instanceTreeNode2, 5);
    nodes.forEach(n -> assertThat(n.getBuiltChildren().stream().allMatch(c -> c.getAdapter() instanceof FieldObject)).isTrue());
  }

  @Test
  public void testLeafNodeExpansion() {
    myInstanceObjects.forEach(o -> {
      LeafNode<InstanceObject> node = new LeafNode<>(o);
      assertChildCount(node, 0);
      node.expandNode();
      assertChildCount(node, 0);
    });
  }

  private static void assertChildCount(LazyMemoryObjectTreeNode<?> node, int count) {
    assertThat(node.getBuiltChildren().size()).isEqualTo(count);
  }

  @Test
  public void testLazyPopulateSiblings() {
    myCaptureObject = new FakeCaptureObject.Builder().build();
    // create a mock class containing 209 instances
    List<InstanceObject> fakeInstances = new ArrayList<>();
    for (int i = 0; i < 209; i++) {
      String name = Integer.toString(i);
      fakeInstances.add(
        new FakeInstanceObject.Builder(myCaptureObject, MOCK_CLASS_ID, MOCK_CLASS_NAME).setName(name).setShallowSize(i).setDepth(i)
          .setRetainedSize(i).build());
    }
    myCaptureObject.addInstanceObjects(new HashSet<>(fakeInstances));
    myStage.selectCaptureDuration(
      new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> myCaptureObject)),
      null);
    myStage.getCaptureSelection().selectHeapSet(myCaptureObject.getHeapSet(FakeCaptureObject.DEFAULT_HEAP_ID));

    myClassifierSetTree = myStageView.getClassifierView().getTree();
    assertThat(myClassifierSetTree).isNotNull();
    Object classifierRoot = myClassifierSetTree.getModel().getRoot();
    assertThat(classifierRoot).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode<?>)classifierRoot).getAdapter()).isInstanceOf(HeapSet.class);
    //noinspection unchecked
    myClassifierSetHeapNode = (MemoryObjectTreeNode<HeapSet>)classifierRoot;

    ClassifierSet classifierSet = myClassifierSetHeapNode.getAdapter().findContainingClassifierSet(fakeInstances.get(0));
    assertThat(classifierSet).isInstanceOf(ClassSet.class);
    myStage.getCaptureSelection().selectClassSet((ClassSet)classifierSet);

    myClassSetTree = myClassSetView.getTree();
    assertThat(myClassSetTree).isNotNull();
    Object classSetRoot = myClassSetTree.getModel().getRoot();
    assertThat(classSetRoot).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode<?>)classSetRoot).getAdapter()).isInstanceOf(ClassSet.class);
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
    myStage.getCaptureSelection().selectInstanceObject(myInstanceObjects.get(0));
    assertThat(myStage.getCaptureSelection().getSelectedInstanceObject()).isEqualTo(myInstanceObjects.get(0));

    // Remove MockInstance2 and refresh selected heap, myStage should still select MockInstance1
    classSet.removeAddedDeltaInstanceObject(myInstanceObjects.get(1));
    myStage.getCaptureSelection().refreshSelectedHeap();
    assertThat(myStage.getCaptureSelection().getSelectedInstanceObject()).isEqualTo(myInstanceObjects.get(0));

    // Remove MockInstance1 and refresh selected heap, myStage should not select any InstanceObject
    classSet.removeAddedDeltaInstanceObject(myInstanceObjects.get(0));
    myStage.getCaptureSelection().refreshSelectedHeap();
    assertThat(myStage.getCaptureSelection().getSelectedInstanceObject()).isNull();
  }
}
