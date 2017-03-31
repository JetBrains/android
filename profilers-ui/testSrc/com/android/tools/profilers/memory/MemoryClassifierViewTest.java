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
import com.android.tools.profilers.stacktrace.CodeLocation;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.util.containers.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.util.*;
import java.util.function.Supplier;

import static com.android.tools.profiler.proto.MemoryProfiler.AllocationStack;
import static com.android.tools.profilers.memory.MemoryProfilerConfiguration.ClassGrouping.*;
import static com.android.tools.profilers.memory.MemoryProfilerTestUtils.*;
import static org.junit.Assert.*;

public class MemoryClassifierViewTest {
  @Rule
  public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("MEMORY_TEST_CHANNEL", new FakeMemoryService());

  private FakeIdeProfilerComponents myFakeIdeProfilerComponents;
  private MemoryProfilerStage myStage;
  private MemoryClassifierView myClassifierView;

  @Before
  public void before() {
    FakeIdeProfilerServices profilerServices = new FakeIdeProfilerServices();
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), profilerServices, new FakeTimer());

    myFakeIdeProfilerComponents = new FakeIdeProfilerComponents();
    StudioProfilersView profilersView = new StudioProfilersView(profilers, myFakeIdeProfilerComponents);

    FakeCaptureObjectLoader loader = new FakeCaptureObjectLoader();
    loader.setReturnImmediateFuture(true);
    myStage = new MemoryProfilerStage(new StudioProfilers(myGrpcChannel.getClient(), profilerServices), loader);
    MemoryProfilerStageView stageView = new MemoryProfilerStageView(profilersView, myStage);
    myClassifierView = stageView.getClassifierView();
  }

  /**
   * Tests that the component generates the classes JTree model accurately based on the package hierarchy
   * of a HeapSet.
   */
  @Test
  public void buildClassifierTreeTest() {
    final String CLASS_NAME_0 = "com.android.studio.Foo";
    final String CLASS_NAME_1 = "com.google.Bar";
    final String CLASS_NAME_2 = "com.android.studio.Baz";

    FakeCaptureObject captureObject = new FakeCaptureObject.Builder().build();
    InstanceObject instanceFoo0 =
      new FakeInstanceObject.Builder(captureObject, CLASS_NAME_0).setName("instanceFoo0").setDepth(1).setShallowSize(2).setRetainedSize(3)
        .build();
    InstanceObject instanceFoo1 =
      new FakeInstanceObject.Builder(captureObject, CLASS_NAME_0).setName("instanceFoo1").setDepth(2).setShallowSize(2).setRetainedSize(3)
        .build();
    InstanceObject instanceFoo2 =
      new FakeInstanceObject.Builder(captureObject, CLASS_NAME_0).setName("instanceFoo2").setDepth(3).setShallowSize(2).setRetainedSize(3)
        .build();
    InstanceObject instanceBar0 =
      new FakeInstanceObject.Builder(captureObject, CLASS_NAME_1).setName("instanceBar0").setDepth(1).setShallowSize(2).setRetainedSize(4)
        .build();
    InstanceObject instanceBaz0 =
      new FakeInstanceObject.Builder(captureObject, CLASS_NAME_2).setName("instanceBaz0").setDepth(1).setShallowSize(2).setRetainedSize(5)
        .build();
    InstanceObject instanceBaz1 =
      new FakeInstanceObject.Builder(captureObject, CLASS_NAME_2).setName("instanceBaz1").setDepth(1).setShallowSize(2).setRetainedSize(5)
        .build();
    InstanceObject instanceBaz2 =
      new FakeInstanceObject.Builder(captureObject, CLASS_NAME_2).setName("instanceBaz2").setDepth(1).setShallowSize(2).setRetainedSize(5)
        .build();
    InstanceObject instanceBaz3 =
      new FakeInstanceObject.Builder(captureObject, CLASS_NAME_2).setName("instanceBaz3").setDepth(1).setShallowSize(2).setRetainedSize(5)
        .build();
    Set<InstanceObject> instanceObjects = new HashSet<>(
      Arrays.asList(instanceFoo0, instanceFoo1, instanceFoo2, instanceBar0, instanceBaz0, instanceBaz1, instanceBaz2, instanceBaz3));
    captureObject.addInstanceObjects(instanceObjects);
    myStage.selectCaptureObject(captureObject, MoreExecutors.directExecutor());

    assertTrue(captureObject.containsClass(CaptureObject.DEFAULT_CLASSLOADER_ID, CLASS_NAME_0));
    assertTrue(captureObject.containsClass(CaptureObject.DEFAULT_CLASSLOADER_ID, CLASS_NAME_1));
    assertTrue(captureObject.containsClass(CaptureObject.DEFAULT_CLASSLOADER_ID, CLASS_NAME_2));

    HeapSet heapSet = captureObject.getHeapSet(instanceFoo0.getHeapId());
    assertNotNull(heapSet);
    myStage.selectHeapSet(heapSet);

    assertEquals(myStage.getConfiguration().getClassGrouping(), ARRANGE_BY_CLASS);
    assertNotNull(myClassifierView.getTree());
    JTree classifierTree = myClassifierView.getTree();

    Object root = classifierTree.getModel().getRoot();
    assertTrue(root instanceof MemoryObjectTreeNode);
    assertTrue(((MemoryObjectTreeNode)root).getAdapter() instanceof HeapSet);
    //noinspection unchecked
    MemoryObjectTreeNode<ClassifierSet> rootNode = (MemoryObjectTreeNode<ClassifierSet>)root;
    ImmutableList<MemoryObjectTreeNode<ClassifierSet>> childrenOfRoot = rootNode.getChildren();

    classifierTree.setSelectionPath(new TreePath(new Object[]{root, childrenOfRoot.get(0)}));
    MemoryObject selectedClassifier = ((MemoryObjectTreeNode)classifierTree.getSelectionPath().getLastPathComponent()).getAdapter();
    assertTrue(selectedClassifier instanceof ClassSet);

    // Check if group by package is grouping as expected.
    myStage.getConfiguration().setClassGrouping(ARRANGE_BY_PACKAGE);
    root = classifierTree.getModel().getRoot();
    assertTrue(root instanceof MemoryObjectTreeNode);
    assertTrue(((MemoryObjectTreeNode)root).getAdapter() instanceof HeapSet);
    //noinspection unchecked
    rootNode = (MemoryObjectTreeNode<ClassifierSet>)root;

    assertEquals(3, countClassSets(rootNode));
    TreePath selectionPath = classifierTree.getSelectionPath();
    assertNotNull(selectionPath);
    assertTrue(selectionPath.getLastPathComponent() instanceof MemoryObjectTreeNode);
    MemoryObject reselectedClassifier = ((MemoryObjectTreeNode)classifierTree.getSelectionPath().getLastPathComponent()).getAdapter();
    assertTrue(reselectedClassifier instanceof ClassSet);
    assertTrue(((ClassSet)selectedClassifier).isSupersetOf((ClassSet)reselectedClassifier));

    MemoryObjectTreeNode<? extends ClassifierSet> comNode = findChildWithName(rootNode, "com");
    verifyNode(comNode, 2, 8, 16, 33);
    MemoryObjectTreeNode<? extends ClassifierSet> googleNode = findChildWithName(comNode, "google");
    verifyNode(googleNode, 1, 1, 2, 4);
    MemoryObjectTreeNode<? extends ClassifierSet> androidNode = findChildWithName(comNode, "android");
    verifyNode(androidNode, 1, 7, 14, 29);
    MemoryObjectTreeNode<? extends ClassifierSet> studioNode = findChildWithName(androidNode, "studio");
    verifyNode(studioNode, 2, 7, 14, 29);

    MemoryObjectTreeNode<ClassSet> fooSet = findChildClassSetNodeWithClassName(studioNode, CLASS_NAME_0);
    assertEquals(fooSet.getAdapter(), fooSet.getAdapter().findContainingClassifierSet(instanceFoo0));
    assertEquals(fooSet.getAdapter(), fooSet.getAdapter().findContainingClassifierSet(instanceFoo1));
    assertEquals(fooSet.getAdapter(), fooSet.getAdapter().findContainingClassifierSet(instanceFoo2));

    MemoryObjectTreeNode<ClassSet> barSet = findChildClassSetNodeWithClassName(googleNode, CLASS_NAME_1);
    assertEquals(barSet.getAdapter(), barSet.getAdapter().findContainingClassifierSet(instanceBar0));

    MemoryObjectTreeNode<ClassSet> bazSet = findChildClassSetNodeWithClassName(studioNode, CLASS_NAME_2);
    assertEquals(bazSet.getAdapter(), bazSet.getAdapter().findContainingClassifierSet(instanceBaz0));
    assertEquals(bazSet.getAdapter(), bazSet.getAdapter().findContainingClassifierSet(instanceBaz1));
    assertEquals(bazSet.getAdapter(), bazSet.getAdapter().findContainingClassifierSet(instanceBaz2));
    assertEquals(bazSet.getAdapter(), bazSet.getAdapter().findContainingClassifierSet(instanceBaz3));

    // Check if flat list is correct.
    myStage.getConfiguration().setClassGrouping(ARRANGE_BY_CLASS);
    root = classifierTree.getModel().getRoot();
    assertTrue(root instanceof MemoryObjectTreeNode);
    assertTrue(((MemoryObjectTreeNode)root).getAdapter() instanceof HeapSet);
    //noinspection unchecked
    assertEquals(3, ((MemoryObjectTreeNode)root).getChildCount());
    //noinspection unchecked
    rootNode = (MemoryObjectTreeNode<ClassifierSet>)root;

    fooSet = findChildClassSetNodeWithClassName(rootNode, CLASS_NAME_0);
    assertEquals(fooSet.getAdapter(), fooSet.getAdapter().findContainingClassifierSet(instanceFoo0));
    assertEquals(fooSet.getAdapter(), fooSet.getAdapter().findContainingClassifierSet(instanceFoo1));
    assertEquals(fooSet.getAdapter(), fooSet.getAdapter().findContainingClassifierSet(instanceFoo2));

    barSet = findChildClassSetNodeWithClassName(rootNode, CLASS_NAME_1);
    assertEquals(barSet.getAdapter(), barSet.getAdapter().findContainingClassifierSet(instanceBar0));

    bazSet = findChildClassSetNodeWithClassName(rootNode, CLASS_NAME_2);
    assertEquals(bazSet.getAdapter(), bazSet.getAdapter().findContainingClassifierSet(instanceBaz0));
    assertEquals(bazSet.getAdapter(), bazSet.getAdapter().findContainingClassifierSet(instanceBaz1));
    assertEquals(bazSet.getAdapter(), bazSet.getAdapter().findContainingClassifierSet(instanceBaz2));
    assertEquals(bazSet.getAdapter(), bazSet.getAdapter().findContainingClassifierSet(instanceBaz3));
  }

  /**
   * Tests selection on the class tree. This makes sure that selecting class nodes results in an actual selection being registered, while
   * selecting package nodes should do nothing.
   */
  @Test
  public void classifierTreeSelectionTest() {
    final String CLASS_NAME_0 = "com.android.studio.Foo";
    final String CLASS_NAME_1 = "com.google.Bar";
    final String CLASS_NAME_2 = "com.android.studio.Baz";

    FakeCaptureObject captureObject = new FakeCaptureObject.Builder().build();
    InstanceObject instanceFoo0 =
      new FakeInstanceObject.Builder(captureObject, CLASS_NAME_0).setName("instanceFoo0").setDepth(1).setShallowSize(2).setRetainedSize(3)
        .build();
    InstanceObject instanceFoo1 =
      new FakeInstanceObject.Builder(captureObject, CLASS_NAME_0).setName("instanceFoo1").setDepth(2).setShallowSize(2).setRetainedSize(3)
        .build();
    InstanceObject instanceFoo2 =
      new FakeInstanceObject.Builder(captureObject, CLASS_NAME_0).setName("instanceFoo2").setDepth(3).setShallowSize(2).setRetainedSize(3)
        .build();
    InstanceObject instanceBar0 =
      new FakeInstanceObject.Builder(captureObject, CLASS_NAME_1).setName("instanceBar0").setDepth(1).setShallowSize(2).setRetainedSize(4)
        .build();
    InstanceObject instanceBaz0 =
      new FakeInstanceObject.Builder(captureObject, CLASS_NAME_2).setName("instanceBaz0").setDepth(1).setShallowSize(2).setRetainedSize(5)
        .build();
    InstanceObject instanceBaz1 =
      new FakeInstanceObject.Builder(captureObject, CLASS_NAME_2).setName("instanceBaz1").setDepth(1).setShallowSize(2).setRetainedSize(5)
        .build();
    InstanceObject instanceBaz2 =
      new FakeInstanceObject.Builder(captureObject, CLASS_NAME_2).setName("instanceBaz2").setDepth(1).setShallowSize(2).setRetainedSize(5)
        .build();
    InstanceObject instanceBaz3 =
      new FakeInstanceObject.Builder(captureObject, CLASS_NAME_2).setName("instanceBaz3").setDepth(1).setShallowSize(2).setRetainedSize(5)
        .build();
    Set<InstanceObject> instanceObjects = new HashSet<>(
      Arrays.asList(instanceFoo0, instanceFoo1, instanceFoo2, instanceBar0, instanceBaz0, instanceBaz1, instanceBaz2, instanceBaz3));
    captureObject.addInstanceObjects(instanceObjects);
    myStage.selectCaptureObject(captureObject, MoreExecutors.directExecutor());

    assertTrue(captureObject.containsClass(CaptureObject.DEFAULT_CLASSLOADER_ID, CLASS_NAME_0));
    assertTrue(captureObject.containsClass(CaptureObject.DEFAULT_CLASSLOADER_ID, CLASS_NAME_1));
    assertTrue(captureObject.containsClass(CaptureObject.DEFAULT_CLASSLOADER_ID, CLASS_NAME_2));

    HeapSet heapSet = captureObject.getHeapSet(instanceFoo0.getHeapId());
    assertNotNull(heapSet);
    myStage.selectHeapSet(heapSet);

    assertEquals(myStage.getConfiguration().getClassGrouping(), ARRANGE_BY_CLASS);
    assertNull(myStage.getSelectedClassSet());
    assertNotNull(myClassifierView.getTree());

    JTree classifierTree = myClassifierView.getTree();
    Object root = classifierTree.getModel().getRoot();
    assertTrue(root instanceof MemoryObjectTreeNode);
    assertTrue(((MemoryObjectTreeNode)root).getAdapter() instanceof HeapSet);
    //noinspection unchecked
    MemoryObjectTreeNode<ClassifierSet> rootNode = (MemoryObjectTreeNode<ClassifierSet>)root;

    //noinspection unchecked
    ImmutableList<MemoryObjectTreeNode<ClassifierSet>> childrenOfRoot = rootNode.getChildren();
    assertEquals(3, childrenOfRoot.size());
    classifierTree.setSelectionPath(new TreePath(new Object[]{root, childrenOfRoot.get(0)}));
    MemoryObjectTreeNode<ClassifierSet> selectedClassNode = childrenOfRoot.get(0);
    assertEquals(selectedClassNode.getAdapter(), myStage.getSelectedClassSet());
    assertEquals(selectedClassNode, classifierTree.getSelectionPath().getLastPathComponent());

    // Check that after changing to ARRANGE_BY_PACKAGE, the originally selected item is reselected.
    myStage.getConfiguration().setClassGrouping(ARRANGE_BY_PACKAGE);
    root = classifierTree.getModel().getRoot();
    assertTrue(root instanceof MemoryObjectTreeNode);
    assertTrue(((MemoryObjectTreeNode)root).getAdapter() instanceof HeapSet);
    //noinspection unchecked
    rootNode = (MemoryObjectTreeNode<ClassifierSet>)root;

    TreePath selectionPath = classifierTree.getSelectionPath();
    assertNotNull(selectionPath);
    Object reselected = selectionPath.getLastPathComponent();
    assertNotNull(reselected);
    assertTrue(reselected instanceof MemoryObjectTreeNode && ((MemoryObjectTreeNode)reselected).getAdapter() instanceof ClassSet);
    //noinspection unchecked
    assertTrue(selectedClassNode.getAdapter().isSupersetOf(((MemoryObjectTreeNode<ClassSet>)reselected).getAdapter()));

    // Try selecting a package -- this should not result in any changes to the state.
    MemoryObjectTreeNode<? extends ClassifierSet> comPackage = findChildWithName(rootNode, "com");
    ClassSet selectedClass = myStage.getSelectedClassSet();
    classifierTree.setSelectionPath(new TreePath(new Object[]{root, comPackage}));
    assertEquals(selectedClass, myStage.getSelectedClassSet());
  }

  @Test
  public void stackExistenceTest() {
    final String CLASS_NAME_0 = "com.android.studio.Foo";
    final String CLASS_NAME_1 = "com.google.Bar";
    final String CLASS_NAME_2 = "int[]";

    CodeLocation codeLocation1 = new CodeLocation.Builder(CLASS_NAME_0).setMethodName("fooMethod1").setLineNumber(5).build();
    CodeLocation codeLocation2 = new CodeLocation.Builder(CLASS_NAME_0).setMethodName("fooMethod2").setLineNumber(10).build();
    CodeLocation codeLocation3 = new CodeLocation.Builder(CLASS_NAME_0).setMethodName("fooMethod3").setLineNumber(15).build();
    CodeLocation codeLocation4 = new CodeLocation.Builder(CLASS_NAME_1).setMethodName("barMethod1").setLineNumber(20).build();

    //noinspection ConstantConditions
    AllocationStack callstack1 = AllocationStack.newBuilder()
      .addStackFrames(
        AllocationStack.StackFrame.newBuilder()
          .setClassName(codeLocation2.getClassName())
          .setMethodName(codeLocation2.getMethodName())
          .setLineNumber(codeLocation2.getLineNumber() + 1))
      .addStackFrames(
        AllocationStack.StackFrame.newBuilder()
          .setClassName(codeLocation1.getClassName())
          .setMethodName(codeLocation1.getMethodName())
          .setLineNumber(codeLocation1.getLineNumber() + 1))
      .build();
    //noinspection ConstantConditions
    AllocationStack callstack2 = AllocationStack.newBuilder()
      .addStackFrames(
        AllocationStack.StackFrame.newBuilder()
          .setClassName(codeLocation3.getClassName())
          .setMethodName(codeLocation3.getMethodName())
          .setLineNumber(codeLocation3.getLineNumber() + 1))
      .addStackFrames(
        AllocationStack.StackFrame.newBuilder()
          .setClassName(codeLocation1.getClassName())
          .setMethodName(codeLocation1.getMethodName())
          .setLineNumber(codeLocation1.getLineNumber() + 1))
      .build();
    //noinspection ConstantConditions
    AllocationStack callstack3 = AllocationStack.newBuilder()
      .addStackFrames(
        AllocationStack.StackFrame.newBuilder()
          .setClassName(codeLocation4.getClassName())
          .setMethodName(codeLocation4.getMethodName())
          .setLineNumber(codeLocation4.getLineNumber() + 1))
      .build();

    FakeCaptureObject captureObject = new FakeCaptureObject.Builder().build();
    InstanceObject instance1 =
      new FakeInstanceObject.Builder(captureObject, CLASS_NAME_0).setName("instanceFoo1").setAllocationStack(callstack1).setDepth(2)
        .setShallowSize(2).setRetainedSize(16).build();
    InstanceObject instance2 =
      new FakeInstanceObject.Builder(captureObject, CLASS_NAME_0).setName("instanceFoo2").setAllocationStack(callstack1).setDepth(2)
        .setShallowSize(2).setRetainedSize(24).build();
    InstanceObject instance3 =
      new FakeInstanceObject.Builder(captureObject, CLASS_NAME_0).setName("instanceFoo3").setAllocationStack(callstack1).setDepth(2)
        .setShallowSize(2).setRetainedSize(16).build();
    InstanceObject instance4 =
      new FakeInstanceObject.Builder(captureObject, CLASS_NAME_0).setName("instanceFoo4").setAllocationStack(callstack2).setDepth(2)
        .setShallowSize(2).setRetainedSize(16).build();
    InstanceObject instance5 =
      new FakeInstanceObject.Builder(captureObject, CLASS_NAME_0).setName("instanceFoo5").setAllocationStack(callstack2).setDepth(2)
        .setShallowSize(2).setRetainedSize(16).build();
    InstanceObject instance6 =
      new FakeInstanceObject.Builder(captureObject, CLASS_NAME_0).setName("instanceFoo6").setAllocationStack(callstack3).setDepth(2)
        .setShallowSize(2).setRetainedSize(16).build();
    InstanceObject instance7 =
      new FakeInstanceObject.Builder(captureObject, CLASS_NAME_1).setName("instanceBar7").setAllocationStack(callstack3).setDepth(2)
        .setShallowSize(2).setRetainedSize(16).build();
    InstanceObject instance8 =
      new FakeInstanceObject.Builder(captureObject, CLASS_NAME_2).setName("instanceBar8").setDepth(0).setShallowSize(2).setRetainedSize(8)
        .build();
    Set<InstanceObject> instanceObjects =
      new HashSet<>(Arrays.asList(instance1, instance2, instance3, instance4, instance5, instance6, instance7, instance8));
    captureObject.addInstanceObjects(instanceObjects);
    myStage.selectCaptureObject(captureObject, MoreExecutors.directExecutor());

    HeapSet heapSet = captureObject.getHeapSet(FakeCaptureObject.DEFAULT_HEAP_ID);
    assertNotNull(heapSet);
    myStage.selectHeapSet(heapSet);

    assertEquals(myStage.getConfiguration().getClassGrouping(), ARRANGE_BY_CLASS);
    assertNull(myStage.getSelectedClassSet());

    JTree classifierTree = myClassifierView.getTree();
    assertNotNull(classifierTree);
    Object root = classifierTree.getModel().getRoot();
    assertTrue(root instanceof MemoryObjectTreeNode);
    assertTrue(((MemoryObjectTreeNode)root).getAdapter() instanceof HeapSet);
    //noinspection unchecked
    MemoryObjectTreeNode<ClassifierSet> rootNode = (MemoryObjectTreeNode<ClassifierSet>)root;
    assertEquals(3, rootNode.getChildCount());

    MemoryObjectTreeNode<ClassSet> fooNode = findChildClassSetNodeWithClassName(rootNode, CLASS_NAME_0);
    assertTrue(fooNode.getAdapter().hasStackInfo());
    MemoryObjectTreeNode<ClassSet> barNode = findChildClassSetNodeWithClassName(rootNode, CLASS_NAME_1);
    assertTrue(barNode.getAdapter().hasStackInfo());
    MemoryObjectTreeNode<ClassSet> intNode = findChildClassSetNodeWithClassName(rootNode, CLASS_NAME_2);
    assertFalse(intNode.getAdapter().hasStackInfo());

    myStage.getConfiguration().setClassGrouping(ARRANGE_BY_PACKAGE);
    root = classifierTree.getModel().getRoot();
    assertTrue(root instanceof MemoryObjectTreeNode);
    assertTrue(((MemoryObjectTreeNode)root).getAdapter() instanceof HeapSet);
    //noinspection unchecked
    rootNode = (MemoryObjectTreeNode<ClassifierSet>)root;

    assertEquals(2, rootNode.getChildCount());
    intNode = findChildClassSetNodeWithClassName(rootNode, CLASS_NAME_2);
    assertFalse(intNode.getAdapter().hasStackInfo());
    MemoryObjectTreeNode<? extends ClassifierSet> comNode = findChildWithName(rootNode, "com");
    assertTrue(comNode.getAdapter().hasStackInfo());
  }

  @Test
  public void groupByStackTraceTest() {
    final String CLASS_NAME_0 = "com.android.studio.Foo";
    final String CLASS_NAME_1 = "com.google.Bar";
    final String CLASS_NAME_2 = "int[]";

    CodeLocation codeLocation1 = new CodeLocation.Builder("Foo").setMethodName("fooMethod1").setLineNumber(5).build();
    CodeLocation codeLocation2 = new CodeLocation.Builder("Foo").setMethodName("fooMethod2").setLineNumber(10).build();
    CodeLocation codeLocation3 = new CodeLocation.Builder("Foo").setMethodName("fooMethod3").setLineNumber(15).build();
    CodeLocation codeLocation4 = new CodeLocation.Builder("Bar").setMethodName("barMethod1").setLineNumber(20).build();

    //noinspection ConstantConditions
    AllocationStack callstack1 = AllocationStack.newBuilder()
      .addStackFrames(
        AllocationStack.StackFrame.newBuilder()
          .setClassName(codeLocation2.getClassName())
          .setMethodName(codeLocation2.getMethodName())
          .setLineNumber(codeLocation2.getLineNumber() + 1))
      .addStackFrames(
        AllocationStack.StackFrame.newBuilder()
          .setClassName(codeLocation1.getClassName())
          .setMethodName(codeLocation1.getMethodName())
          .setLineNumber(codeLocation1.getLineNumber() + 1))
      .build();
    //noinspection ConstantConditions
    AllocationStack callstack2 = AllocationStack.newBuilder()
      .addStackFrames(
        AllocationStack.StackFrame.newBuilder()
          .setClassName(codeLocation3.getClassName())
          .setMethodName(codeLocation3.getMethodName())
          .setLineNumber(codeLocation3.getLineNumber() + 1))
      .addStackFrames(
        AllocationStack.StackFrame.newBuilder()
          .setClassName(codeLocation1.getClassName())
          .setMethodName(codeLocation1.getMethodName())
          .setLineNumber(codeLocation1.getLineNumber() + 1))
      .build();
    //noinspection ConstantConditions
    AllocationStack callstack3 = AllocationStack.newBuilder()
      .addStackFrames(
        AllocationStack.StackFrame.newBuilder()
          .setClassName(codeLocation4.getClassName())
          .setMethodName(codeLocation4.getMethodName())
          .setLineNumber(codeLocation4.getLineNumber() + 1))
      .build();

    FakeCaptureObject captureObject = new FakeCaptureObject.Builder().build();
    InstanceObject instance1 =
      new FakeInstanceObject.Builder(captureObject, CLASS_NAME_0).setName("instanceFoo1").setAllocationStack(callstack1).setDepth(2)
        .setShallowSize(2).setRetainedSize(16).build();
    InstanceObject instance2 =
      new FakeInstanceObject.Builder(captureObject, CLASS_NAME_0).setName("instanceFoo2").setAllocationStack(callstack1).setDepth(2)
        .setShallowSize(2).setRetainedSize(24).build();
    InstanceObject instance3 =
      new FakeInstanceObject.Builder(captureObject, CLASS_NAME_0).setName("instanceFoo3").setAllocationStack(callstack1).setDepth(2)
        .setShallowSize(2).setRetainedSize(16).build();
    InstanceObject instance4 =
      new FakeInstanceObject.Builder(captureObject, CLASS_NAME_0).setName("instanceFoo4").setAllocationStack(callstack2).setDepth(2)
        .setShallowSize(2).setRetainedSize(16).build();
    InstanceObject instance5 =
      new FakeInstanceObject.Builder(captureObject, CLASS_NAME_0).setName("instanceFoo5").setAllocationStack(callstack2).setDepth(2)
        .setShallowSize(2).setRetainedSize(16).build();
    InstanceObject instance6 =
      new FakeInstanceObject.Builder(captureObject, CLASS_NAME_0).setName("instanceFoo6").setAllocationStack(callstack3).setDepth(2)
        .setShallowSize(2).setRetainedSize(16).build();
    InstanceObject instance7 =
      new FakeInstanceObject.Builder(captureObject, CLASS_NAME_1).setName("instanceBar7").setAllocationStack(callstack3).setDepth(2)
        .setShallowSize(2).setRetainedSize(16).build();
    InstanceObject instance8 =
      new FakeInstanceObject.Builder(captureObject, CLASS_NAME_2).setName("instanceBar8").setDepth(0).setShallowSize(2).setRetainedSize(8)
        .build();
    Set<InstanceObject> instanceObjects =
      new HashSet<>(Arrays.asList(instance1, instance2, instance3, instance4, instance5, instance6, instance7, instance8));
    captureObject.addInstanceObjects(instanceObjects);
    myStage.selectCaptureObject(captureObject, MoreExecutors.directExecutor());

    HeapSet heapSet = captureObject.getHeapSet(FakeCaptureObject.DEFAULT_HEAP_ID);
    assertNotNull(heapSet);
    myStage.selectHeapSet(heapSet);

    assertEquals(myStage.getConfiguration().getClassGrouping(), ARRANGE_BY_CLASS);
    assertNull(myStage.getSelectedClassSet());

    JTree classifierTree = myClassifierView.getTree();
    assertNotNull(classifierTree);
    Object root = classifierTree.getModel().getRoot();
    assertTrue(root instanceof MemoryObjectTreeNode);
    assertTrue(((MemoryObjectTreeNode)root).getAdapter() instanceof HeapSet);
    //noinspection unchecked
    MemoryObjectTreeNode<ClassifierSet> rootNode = (MemoryObjectTreeNode<ClassifierSet>)root;
    assertEquals(3, rootNode.getChildCount());

    myStage.getConfiguration().setClassGrouping(ARRANGE_BY_CALLSTACK);
    root = classifierTree.getModel().getRoot();
    assertTrue(root instanceof MemoryObjectTreeNode);
    assertTrue(((MemoryObjectTreeNode)root).getAdapter() instanceof HeapSet);
    //noinspection unchecked
    rootNode = (MemoryObjectTreeNode<ClassifierSet>)root;
    assertEquals(3, rootNode.getChildCount());

    MemoryObjectTreeNode<? extends ClassifierSet> codeLocation1Node = findChildWithPredicate(
      rootNode,
      classifierSet -> classifierSet instanceof MethodSet && codeLocation1.equals(((MethodSet)classifierSet).getCodeLocation()));
    assertEquals(2, codeLocation1Node.getChildCount());

    MemoryObjectTreeNode<? extends ClassifierSet> codeLocation2Node = findChildWithPredicate(
      codeLocation1Node,
      classifierSet -> classifierSet instanceof MethodSet && codeLocation2.equals(((MethodSet)classifierSet).getCodeLocation()));
    ClassSet callstack1FooClassSet = findChildClassSetWithName(codeLocation2Node.getAdapter(), CLASS_NAME_0);
    assertEquals(callstack1FooClassSet, callstack1FooClassSet.findContainingClassifierSet(instance1));
    assertEquals(callstack1FooClassSet, callstack1FooClassSet.findContainingClassifierSet(instance2));
    assertEquals(callstack1FooClassSet, callstack1FooClassSet.findContainingClassifierSet(instance3));

    MemoryObjectTreeNode<? extends ClassifierSet> codeLocation3Node = findChildWithPredicate(
      codeLocation1Node,
      classifierSet -> classifierSet instanceof MethodSet && codeLocation3.equals(((MethodSet)classifierSet).getCodeLocation()));
    ClassSet callstack2FooClassSet = findChildClassSetWithName(codeLocation3Node.getAdapter(), CLASS_NAME_0);
    assertEquals(callstack2FooClassSet, callstack2FooClassSet.findContainingClassifierSet(instance4));
    assertEquals(callstack2FooClassSet, callstack2FooClassSet.findContainingClassifierSet(instance5));

    MemoryObjectTreeNode<? extends ClassifierSet> codeLocation4Node = findChildWithPredicate(
      rootNode,
      classifierSet -> classifierSet instanceof MethodSet && codeLocation4.equals(((MethodSet)classifierSet).getCodeLocation()));
    assertEquals(2, codeLocation4Node.getChildCount());
    ClassSet callstack3FooClassSet = findChildClassSetWithName(codeLocation4Node.getAdapter(), CLASS_NAME_0);
    assertEquals(callstack3FooClassSet, callstack3FooClassSet.findContainingClassifierSet(instance6));
    ClassSet callstack3BarClassSet = findChildClassSetWithName(codeLocation4Node.getAdapter(), CLASS_NAME_1);
    assertEquals(callstack3BarClassSet, callstack3BarClassSet.findContainingClassifierSet(instance7));

    ClassSet noStackIntArrayClassSet = findChildClassSetWithName(rootNode.getAdapter(), CLASS_NAME_2);
    assertEquals(1, noStackIntArrayClassSet.getCount());

    MemoryObjectTreeNode<? extends ClassifierSet> nodeToSelect = findChildClassSetNodeWithClassName(codeLocation4Node, CLASS_NAME_0);
    classifierTree.setSelectionPath(new TreePath(new Object[]{root, codeLocation4Node, nodeToSelect}));
    myStage.getConfiguration().setClassGrouping(ARRANGE_BY_CLASS);
    assertEquals(3, rootNode.getChildCount());
    TreePath selectionPath = classifierTree.getSelectionPath();
    assertNotNull(selectionPath);
    Object selectedObject = selectionPath.getLastPathComponent();
    assertTrue(selectedObject instanceof MemoryObjectTreeNode &&
               ((MemoryObjectTreeNode)selectedObject).getAdapter() instanceof ClassSet);
    //noinspection unchecked
    assertTrue(((MemoryObjectTreeNode<ClassSet>)selectedObject).getAdapter().isSupersetOf(nodeToSelect.getAdapter()));
  }

  @Test
  public void navigationTest() {
    final String TEST_CLASS_NAME = "com.Foo";

    FakeCaptureObject captureObject = new FakeCaptureObject.Builder().build();
    InstanceObject instance1 =
      new FakeInstanceObject.Builder(captureObject, TEST_CLASS_NAME).setName("instanceFoo1").setDepth(0)
        .setShallowSize(0).setRetainedSize(0).build();
    Set<InstanceObject> instanceObjects = new HashSet<>(Collections.singleton(instance1));
    captureObject.addInstanceObjects(instanceObjects);
    myStage.selectCaptureObject(captureObject, MoreExecutors.directExecutor());

    HeapSet heapSet = captureObject.getHeapSet(FakeCaptureObject.DEFAULT_HEAP_ID);
    assertNotNull(heapSet);
    myStage.selectHeapSet(heapSet);

    assertEquals(myStage.getConfiguration().getClassGrouping(), ARRANGE_BY_CLASS);

    JTree classifierTree = myClassifierView.getTree();
    assertNotNull(classifierTree);

    Object root = classifierTree.getModel().getRoot();
    assertTrue(root instanceof MemoryObjectTreeNode);
    assertTrue(((MemoryObjectTreeNode)root).getAdapter() instanceof HeapSet);
    //noinspection unchecked
    MemoryObjectTreeNode<ClassifierSet> rootNode = (MemoryObjectTreeNode<ClassifierSet>)root;
    assertEquals(1, rootNode.getChildCount());

    assertNull(myStage.getSelectedClassSet());
    myStage.selectClassSet(findChildClassSetWithName(rootNode.getAdapter(), TEST_CLASS_NAME));
    myStage.selectInstanceObject(instance1);

    Supplier<CodeLocation> codeLocationSupplier = myFakeIdeProfilerComponents.getCodeLocationSupplier(classifierTree);

    assertNotNull(codeLocationSupplier);
    CodeLocation codeLocation = codeLocationSupplier.get();
    assertNotNull(codeLocation);
    String codeLocationClassName = codeLocation.getClassName();
    assertEquals(TEST_CLASS_NAME, codeLocationClassName);

    myStage.getStudioProfilers().getIdeServices().getCodeNavigator().addListener(myStage); // manually add, since we didn't enter stage
    myStage.getStudioProfilers().getIdeServices().getCodeNavigator().navigate(codeLocation);
    myStage.getStudioProfilers().getIdeServices().getCodeNavigator().removeListener(myStage);
    assertEquals(ProfilerMode.NORMAL, myStage.getProfilerMode());
  }

  @Test
  public void testCorrectColumnsAndRendererContents() {
    final String CLASS_NAME_0 = "bar.def";
    final String CLASS_NAME_1 = "foo.abc";
    final String CLASS_NAME_2 = "ghi";

    FakeCaptureObject captureObject = new FakeCaptureObject.Builder().build();
    InstanceObject instance1 =
      new FakeInstanceObject.Builder(captureObject, CLASS_NAME_0).setName("def").setDepth(7).setShallowSize(8).setRetainedSize(9).build();
    InstanceObject instance2 =
      new FakeInstanceObject.Builder(captureObject, CLASS_NAME_1).setName("abc").setDepth(4).setShallowSize(5).setRetainedSize(7).build();
    InstanceObject instance3 =
      new FakeInstanceObject.Builder(captureObject, CLASS_NAME_2).setName("ghi").setDepth(1).setShallowSize(2).setRetainedSize(3).build();
    captureObject.addInstanceObjects(new HashSet<>(Arrays.asList(instance1, instance2, instance3)));
    myStage.selectCaptureObject(captureObject, MoreExecutors.directExecutor());

    HeapSet heapSet = captureObject.getHeapSet(FakeCaptureObject.DEFAULT_HEAP_ID);
    assertNotNull(heapSet);
    myStage.selectHeapSet(heapSet);

    JTree tree = myClassifierView.getTree();
    assertNotNull(tree);
    JScrollPane columnTreePane = (JScrollPane)myClassifierView.getColumnTree();
    assertNotNull(columnTreePane);
    ColumnTreeTestInfo treeInfo = new ColumnTreeTestInfo(tree, columnTreePane);
    treeInfo.verifyColumnHeaders("Class Name", "Heap Count", "Shallow Size", "Retained Size");

    Object root = tree.getModel().getRoot();
    assertTrue(root instanceof MemoryObjectTreeNode);
    assertTrue(((MemoryObjectTreeNode)root).getAdapter() instanceof HeapSet);
    //noinspection unchecked
    MemoryObjectTreeNode<ClassifierSet> rootNode = (MemoryObjectTreeNode<ClassifierSet>)root;
    assertEquals(3, rootNode.getChildCount());

    List<InstanceObject> instanceObjects = Arrays.asList(instance1, instance2, instance3);
    List<String> classNames = Arrays.asList(CLASS_NAME_0, CLASS_NAME_1, CLASS_NAME_2);

    for (int i = 0; i < rootNode.getChildCount(); i++) {
      ClassSet classSet = findChildClassSetWithName(rootNode.getAdapter(), classNames.get(i));
      assertEquals(classSet, classSet.findContainingClassifierSet(instanceObjects.get(i)));
      MemoryObjectTreeNode<? extends ClassifierSet> node = findChildClassSetNodeWithClassName(rootNode, classNames.get(i));
      assertEquals(classSet, node.getAdapter());
      treeInfo.verifyRendererValues(rootNode.getChildAt(i),
                                    new String[]{classSet.getClassEntry().getSimpleClassName(),
                                      classSet.getClassEntry().getPackageName().isEmpty()
                                      ? null
                                      : " (" + classSet.getClassEntry().getPackageName() + ")"},
                                    new String[]{Integer.toString(classSet.getCount())},
                                    new String[]{Long.toString(classSet.getShallowSize())},
                                    new String[]{Long.toString(classSet.getRetainedSize())});
    }
  }

  private static int countClassSets(@NotNull MemoryObjectTreeNode<ClassifierSet> node) {
    int classSetCount = 0;
    for (MemoryObjectTreeNode<ClassifierSet> child : node.getChildren()) {
      if (child.getAdapter() instanceof ClassSet) {
        classSetCount++;
      }
      else {
        classSetCount += countClassSets(child);
      }
    }
    return classSetCount;
  }
}
