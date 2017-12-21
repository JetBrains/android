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
import com.android.tools.adtui.model.Range;
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
import javax.swing.table.TableColumnModel;
import javax.swing.tree.TreePath;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.android.tools.profiler.proto.MemoryProfiler.AllocationStack;
import static com.android.tools.profilers.memory.MemoryProfilerConfiguration.ClassGrouping.*;
import static com.android.tools.profilers.memory.MemoryProfilerTestUtils.*;
import static com.google.common.truth.Truth.assertThat;

public class MemoryClassifierViewTest {
  @Rule
  public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("MEMORY_TEST_CHANNEL", new FakeMemoryService());

  private FakeIdeProfilerServices myFakeIdeProfilerServices;
  private FakeIdeProfilerComponents myFakeIdeProfilerComponents;
  private MemoryProfilerStage myStage;
  private MemoryClassifierView myClassifierView;

  @Before
  public void before() {
    FakeCaptureObjectLoader loader = new FakeCaptureObjectLoader();
    loader.setReturnImmediateFuture(true);
    myFakeIdeProfilerServices = new FakeIdeProfilerServices();
    myFakeIdeProfilerComponents = new FakeIdeProfilerComponents();
    myStage = new MemoryProfilerStage(new StudioProfilers(myGrpcChannel.getClient(), myFakeIdeProfilerServices), loader);
    myClassifierView = new MemoryClassifierView(myStage, myFakeIdeProfilerComponents);
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
    myStage
      .selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> captureObject)),
                             null);

    assertThat(captureObject.containsClass(CaptureObject.DEFAULT_CLASSLOADER_ID, CLASS_NAME_0)).isTrue();
    assertThat(captureObject.containsClass(CaptureObject.DEFAULT_CLASSLOADER_ID, CLASS_NAME_1)).isTrue();
    assertThat(captureObject.containsClass(CaptureObject.DEFAULT_CLASSLOADER_ID, CLASS_NAME_2)).isTrue();

    HeapSet heapSet = captureObject.getHeapSet(instanceFoo0.getHeapId());
    assertThat(heapSet).isNotNull();
    myStage.selectHeapSet(heapSet);

    assertThat(myStage.getConfiguration().getClassGrouping()).isEqualTo(ARRANGE_BY_CLASS);
    assertThat(myClassifierView.getTree()).isNotNull();
    JTree classifierTree = myClassifierView.getTree();

    Object root = classifierTree.getModel().getRoot();
    assertThat(root).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode)root).getAdapter()).isInstanceOf(HeapSet.class);
    //noinspection unchecked
    MemoryObjectTreeNode<ClassifierSet> rootNode = (MemoryObjectTreeNode<ClassifierSet>)root;
    ImmutableList<MemoryObjectTreeNode<ClassifierSet>> childrenOfRoot = rootNode.getChildren();

    classifierTree.setSelectionPath(new TreePath(new Object[]{root, childrenOfRoot.get(0)}));
    MemoryObject selectedClassifier = ((MemoryObjectTreeNode)classifierTree.getSelectionPath().getLastPathComponent()).getAdapter();
    assertThat(selectedClassifier).isInstanceOf(ClassSet.class);

    // Check if group by package is grouping as expected.
    myStage.getConfiguration().setClassGrouping(ARRANGE_BY_PACKAGE);

    TableColumnModel tableColumnModel = myClassifierView.getTableColumnModel();
    assertThat(tableColumnModel.getColumn(0).getHeaderValue()).isEqualTo("Package Name");

    root = classifierTree.getModel().getRoot();
    assertThat(root).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode)root).getAdapter()).isInstanceOf(HeapSet.class);
    //noinspection unchecked
    rootNode = (MemoryObjectTreeNode<ClassifierSet>)root;

    assertThat(countClassSets(rootNode)).isEqualTo(3);
    TreePath selectionPath = classifierTree.getSelectionPath();
    assertThat(selectionPath).isNotNull();
    assertThat(selectionPath.getLastPathComponent()).isInstanceOf(MemoryObjectTreeNode.class);
    MemoryObject reselectedClassifier = ((MemoryObjectTreeNode)classifierTree.getSelectionPath().getLastPathComponent()).getAdapter();
    assertThat(reselectedClassifier).isInstanceOf(ClassSet.class);
    assertThat(((ClassSet)selectedClassifier).isSupersetOf((ClassSet)reselectedClassifier)).isTrue();

    verifyNode((MemoryObjectTreeNode)root, 1, 8, 16, 33);
    MemoryObjectTreeNode<? extends ClassifierSet> comNode = findChildWithName(rootNode, "com");
    verifyNode(comNode, 2, 8, 16, 33);
    MemoryObjectTreeNode<? extends ClassifierSet> googleNode = findChildWithName(comNode, "google");
    verifyNode(googleNode, 1, 1, 2, 4);
    MemoryObjectTreeNode<? extends ClassifierSet> androidNode = findChildWithName(comNode, "android");
    verifyNode(androidNode, 1, 7, 14, 29);
    MemoryObjectTreeNode<? extends ClassifierSet> studioNode = findChildWithName(androidNode, "studio");
    verifyNode(studioNode, 2, 7, 14, 29);

    MemoryObjectTreeNode<ClassSet> fooSet = findChildClassSetNodeWithClassName(studioNode, CLASS_NAME_0);
    assertThat(fooSet.getAdapter().findContainingClassifierSet(instanceFoo0)).isEqualTo(fooSet.getAdapter());
    assertThat(fooSet.getAdapter().findContainingClassifierSet(instanceFoo1)).isEqualTo(fooSet.getAdapter());
    assertThat(fooSet.getAdapter().findContainingClassifierSet(instanceFoo2)).isEqualTo(fooSet.getAdapter());

    MemoryObjectTreeNode<ClassSet> barSet = findChildClassSetNodeWithClassName(googleNode, CLASS_NAME_1);
    assertThat(barSet.getAdapter().findContainingClassifierSet(instanceBar0)).isEqualTo(barSet.getAdapter());

    MemoryObjectTreeNode<ClassSet> bazSet = findChildClassSetNodeWithClassName(studioNode, CLASS_NAME_2);
    assertThat(bazSet.getAdapter().findContainingClassifierSet(instanceBaz0)).isEqualTo(bazSet.getAdapter());
    assertThat(bazSet.getAdapter().findContainingClassifierSet(instanceBaz1)).isEqualTo(bazSet.getAdapter());
    assertThat(bazSet.getAdapter().findContainingClassifierSet(instanceBaz2)).isEqualTo(bazSet.getAdapter());
    assertThat(bazSet.getAdapter().findContainingClassifierSet(instanceBaz3)).isEqualTo(bazSet.getAdapter());

    // Check if flat list is correct.
    myStage.getConfiguration().setClassGrouping(ARRANGE_BY_CLASS);

    tableColumnModel = myClassifierView.getTableColumnModel();
    assertThat(tableColumnModel.getColumn(0).getHeaderValue()).isEqualTo("Class Name");

    root = classifierTree.getModel().getRoot();
    assertThat(root).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode)root).getAdapter()).isInstanceOf(HeapSet.class);
    //noinspection unchecked
    assertThat(((MemoryObjectTreeNode)root).getChildCount()).isEqualTo(3);
    //noinspection unchecked
    rootNode = (MemoryObjectTreeNode<ClassifierSet>)root;

    // Heap node should now have 3 children, all other stats should not have changed.
    verifyNode((MemoryObjectTreeNode)root, 3, 8, 16, 33);

    fooSet = findChildClassSetNodeWithClassName(rootNode, CLASS_NAME_0);
    assertThat(fooSet.getAdapter().findContainingClassifierSet(instanceFoo0)).isEqualTo(fooSet.getAdapter());
    assertThat(fooSet.getAdapter().findContainingClassifierSet(instanceFoo1)).isEqualTo(fooSet.getAdapter());
    assertThat(fooSet.getAdapter().findContainingClassifierSet(instanceFoo2)).isEqualTo(fooSet.getAdapter());

    barSet = findChildClassSetNodeWithClassName(rootNode, CLASS_NAME_1);
    assertThat(barSet.getAdapter().findContainingClassifierSet(instanceBar0)).isEqualTo(barSet.getAdapter());

    bazSet = findChildClassSetNodeWithClassName(rootNode, CLASS_NAME_2);
    assertThat(bazSet.getAdapter().findContainingClassifierSet(instanceBaz0)).isEqualTo(bazSet.getAdapter());
    assertThat(bazSet.getAdapter().findContainingClassifierSet(instanceBaz1)).isEqualTo(bazSet.getAdapter());
    assertThat(bazSet.getAdapter().findContainingClassifierSet(instanceBaz2)).isEqualTo(bazSet.getAdapter());
    assertThat(bazSet.getAdapter().findContainingClassifierSet(instanceBaz3)).isEqualTo(bazSet.getAdapter());
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
    myStage
      .selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> captureObject)),
                             null);

    assertThat(captureObject.containsClass(CaptureObject.DEFAULT_CLASSLOADER_ID, CLASS_NAME_0)).isTrue();
    assertThat(captureObject.containsClass(CaptureObject.DEFAULT_CLASSLOADER_ID, CLASS_NAME_1)).isTrue();
    assertThat(captureObject.containsClass(CaptureObject.DEFAULT_CLASSLOADER_ID, CLASS_NAME_2)).isTrue();

    HeapSet heapSet = captureObject.getHeapSet(instanceFoo0.getHeapId());
    assertThat(heapSet).isNotNull();
    myStage.selectHeapSet(heapSet);

    assertThat(myStage.getConfiguration().getClassGrouping()).isEqualTo(ARRANGE_BY_CLASS);
    assertThat(myStage.getSelectedClassSet()).isNull();
    assertThat(myClassifierView.getTree()).isNotNull();

    JTree classifierTree = myClassifierView.getTree();
    Object root = classifierTree.getModel().getRoot();
    assertThat(root).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode)root).getAdapter()).isInstanceOf(HeapSet.class);
    //noinspection unchecked
    MemoryObjectTreeNode<ClassifierSet> rootNode = (MemoryObjectTreeNode<ClassifierSet>)root;

    //noinspection unchecked
    ImmutableList<MemoryObjectTreeNode<ClassifierSet>> childrenOfRoot = rootNode.getChildren();
    assertThat(childrenOfRoot.size()).isEqualTo(3);
    classifierTree.setSelectionPath(new TreePath(new Object[]{root, childrenOfRoot.get(0)}));
    MemoryObjectTreeNode<ClassifierSet> selectedClassNode = childrenOfRoot.get(0);
    assertThat(myStage.getSelectedClassSet()).isEqualTo(selectedClassNode.getAdapter());
    assertThat(classifierTree.getSelectionPath().getLastPathComponent()).isEqualTo(selectedClassNode);

    // Check that after changing to ARRANGE_BY_PACKAGE, the originally selected item is reselected.
    myStage.getConfiguration().setClassGrouping(ARRANGE_BY_PACKAGE);

    TableColumnModel tableColumnModel = myClassifierView.getTableColumnModel();
    assertThat(tableColumnModel.getColumn(0).getHeaderValue()).isEqualTo("Package Name");

    root = classifierTree.getModel().getRoot();
    assertThat(root).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode)root).getAdapter()).isInstanceOf(HeapSet.class);
    //noinspection unchecked
    rootNode = (MemoryObjectTreeNode<ClassifierSet>)root;

    TreePath selectionPath = classifierTree.getSelectionPath();
    assertThat(selectionPath).isNotNull();
    Object reselected = selectionPath.getLastPathComponent();
    assertThat(reselected).isNotNull();
    assertThat(reselected).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode)reselected).getAdapter()).isInstanceOf(ClassSet.class);
    //noinspection unchecked
    assertThat(selectedClassNode.getAdapter().isSupersetOf(((MemoryObjectTreeNode<ClassSet>)reselected).getAdapter())).isTrue();

    // Clear the selection from the model. The Tree's selection should get cleared as well.
    myStage.selectClassSet(null);
    assertThat(classifierTree.getSelectionPath()).isNull();

    // Try selecting a package -- this should not result in any changes to the state.
    MemoryObjectTreeNode<? extends ClassifierSet> comPackage = findChildWithName(rootNode, "com");
    ClassSet selectedClass = myStage.getSelectedClassSet();
    classifierTree.setSelectionPath(new TreePath(new Object[]{root, comPackage}));
    assertThat(myStage.getSelectedClassSet()).isEqualTo(selectedClass);
  }

  @Test
  public void stackExistenceTest() {
    final String CLASS_NAME_0 = "com.android.studio.Foo";
    final String CLASS_NAME_1 = "com.google.Bar";
    final String CLASS_NAME_2 = "int[]";
    final String METHOD_NAME_0 = "fooMethod0";
    final String METHOD_NAME_1 = "fooMethod1";
    final String METHOD_NAME_2 = "fooMethod2";
    final String METHOD_NAME_3 = "barMethod0";
    final int LINE_NUMBER_0 = 5;
    final int LINE_NUMBER_1 = 10;
    final int LINE_NUMBER_2 = 15;
    final int LINE_NUMBER_3 = 20;

    //noinspection ConstantConditions
    AllocationStack callstack1 = AllocationStack.newBuilder()
      .setFullStack(
        AllocationStack.StackFrameWrapper.newBuilder()
          .addFrames(
            AllocationStack.StackFrame.newBuilder()
              .setClassName(CLASS_NAME_0)
              .setMethodName(METHOD_NAME_1)
              .setLineNumber(LINE_NUMBER_1 + 1))
          .addFrames(
            AllocationStack.StackFrame.newBuilder()
              .setClassName(CLASS_NAME_0)
              .setMethodName(METHOD_NAME_0)
              .setLineNumber(LINE_NUMBER_0 + 1)))
      .build();
    //noinspection ConstantConditions
    AllocationStack callstack2 = AllocationStack.newBuilder()
      .setFullStack(
        AllocationStack.StackFrameWrapper.newBuilder()
          .addFrames(
            AllocationStack.StackFrame.newBuilder()
              .setClassName(CLASS_NAME_0)
              .setMethodName(METHOD_NAME_2)
              .setLineNumber(LINE_NUMBER_2 + 1))
          .addFrames(
            AllocationStack.StackFrame.newBuilder()
              .setClassName(CLASS_NAME_0)
              .setMethodName(METHOD_NAME_0)
              .setLineNumber(LINE_NUMBER_0 + 1)))
      .build();
    //noinspection ConstantConditions
    AllocationStack callstack3 = AllocationStack.newBuilder()
      .setFullStack(
        AllocationStack.StackFrameWrapper.newBuilder()
          .addFrames(
            AllocationStack.StackFrame.newBuilder()
              .setClassName(CLASS_NAME_1)
              .setMethodName(METHOD_NAME_3)
              .setLineNumber(LINE_NUMBER_3 + 1)))
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
    myStage
      .selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> captureObject)),
                             null);

    HeapSet heapSet = captureObject.getHeapSet(CaptureObject.DEFAULT_HEAP_ID);
    assertThat(heapSet).isNotNull();
    myStage.selectHeapSet(heapSet);

    assertThat(myStage.getConfiguration().getClassGrouping()).isEqualTo(ARRANGE_BY_CLASS);
    assertThat(myStage.getSelectedClassSet()).isNull();

    JTree classifierTree = myClassifierView.getTree();
    assertThat(classifierTree).isNotNull();
    Object root = classifierTree.getModel().getRoot();
    assertThat(root).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode)root).getAdapter()).isInstanceOf(HeapSet.class);
    //noinspection unchecked
    MemoryObjectTreeNode<ClassifierSet> rootNode = (MemoryObjectTreeNode<ClassifierSet>)root;
    assertThat(rootNode.getChildCount()).isEqualTo(3);

    MemoryObjectTreeNode<ClassSet> fooNode = findChildClassSetNodeWithClassName(rootNode, CLASS_NAME_0);
    assertThat(fooNode.getAdapter().hasStackInfo()).isTrue();
    MemoryObjectTreeNode<ClassSet> barNode = findChildClassSetNodeWithClassName(rootNode, CLASS_NAME_1);
    assertThat(barNode.getAdapter().hasStackInfo()).isTrue();
    MemoryObjectTreeNode<ClassSet> intNode = findChildClassSetNodeWithClassName(rootNode, CLASS_NAME_2);
    assertThat(intNode.getAdapter().hasStackInfo()).isFalse();

    myStage.getConfiguration().setClassGrouping(ARRANGE_BY_PACKAGE);

    TableColumnModel tableColumnModel = myClassifierView.getTableColumnModel();
    assertThat(tableColumnModel.getColumn(0).getHeaderValue()).isEqualTo("Package Name");

    root = classifierTree.getModel().getRoot();
    assertThat(root).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode)root).getAdapter()).isInstanceOf(HeapSet.class);
    //noinspection unchecked
    rootNode = (MemoryObjectTreeNode<ClassifierSet>)root;

    assertThat(rootNode.getChildCount()).isEqualTo(2);
    intNode = findChildClassSetNodeWithClassName(rootNode, CLASS_NAME_2);
    assertThat(intNode.getAdapter().hasStackInfo()).isFalse();
    MemoryObjectTreeNode<? extends ClassifierSet> comNode = findChildWithName(rootNode, "com");
    assertThat(comNode.getAdapter().hasStackInfo()).isTrue();
  }

  @Test
  public void groupByStackTraceTest() {
    final String CLASS_NAME_0 = "com.android.studio.Foo";
    final String CLASS_NAME_1 = "com.google.Bar";
    final String CLASS_NAME_2 = "int[]";
    final String METHOD_NAME_0 = "fooMethod0";
    final String METHOD_NAME_1 = "fooMethod1";
    final String METHOD_NAME_2 = "fooMethod2";
    final String METHOD_NAME_3 = "barMethod0";
    final int LINE_NUMBER_0 = 5;
    final int LINE_NUMBER_1 = 10;
    final int LINE_NUMBER_2 = 15;
    final int LINE_NUMBER_3 = 20;
    final int LINE_NUMBER_4 = 25;

    //noinspection ConstantConditions
    AllocationStack callstack1 = AllocationStack.newBuilder()
      .setFullStack(
        AllocationStack.StackFrameWrapper.newBuilder()
          .addFrames(
            AllocationStack.StackFrame.newBuilder()
              .setClassName(CLASS_NAME_0)
              .setMethodName(METHOD_NAME_1)
              .setLineNumber(LINE_NUMBER_1 + 1))
          .addFrames(
            AllocationStack.StackFrame.newBuilder()
              .setClassName(CLASS_NAME_0)
              .setMethodName(METHOD_NAME_0)
              .setLineNumber(LINE_NUMBER_0 + 1)))
      .build();
    //noinspection ConstantConditions
    AllocationStack callstack2 = AllocationStack.newBuilder()
      .setFullStack(
        AllocationStack.StackFrameWrapper.newBuilder()
          .addFrames(
            AllocationStack.StackFrame.newBuilder()
              .setClassName(CLASS_NAME_0)
              .setMethodName(METHOD_NAME_2)
              .setLineNumber(LINE_NUMBER_2 + 1))
          .addFrames(
            AllocationStack.StackFrame.newBuilder()
              .setClassName(CLASS_NAME_0)
              .setMethodName(METHOD_NAME_0)
              .setLineNumber(LINE_NUMBER_0 + 1)))
      .build();
    //noinspection ConstantConditions
    AllocationStack callstack3 = AllocationStack.newBuilder()
      .setFullStack(
        AllocationStack.StackFrameWrapper.newBuilder()
          .addFrames(
            AllocationStack.StackFrame.newBuilder()
              .setClassName(CLASS_NAME_1)
              .setMethodName(METHOD_NAME_3)
              .setLineNumber(LINE_NUMBER_3 + 1)))
      .build();
    // Check that callstacks only differs by line numbers are still grouped together in the same node.
    //noinspection ConstantConditions
    AllocationStack callstack4 = AllocationStack.newBuilder()
      .setFullStack(
        AllocationStack.StackFrameWrapper.newBuilder()
          .addFrames(
            AllocationStack.StackFrame.newBuilder()
              .setClassName(CLASS_NAME_1)
              .setMethodName(METHOD_NAME_3)
              .setLineNumber(LINE_NUMBER_4 + 1)))
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
    InstanceObject instance9 =
      new FakeInstanceObject.Builder(captureObject, CLASS_NAME_1).setName("instanceBar9").setAllocationStack(callstack4).setDepth(2)
        .setShallowSize(2).setRetainedSize(16).build();
    Set<InstanceObject> instanceObjects =
      new HashSet<>(Arrays.asList(instance1, instance2, instance3, instance4, instance5, instance6, instance7, instance8, instance9));
    captureObject.addInstanceObjects(instanceObjects);
    myStage
      .selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> captureObject)),
                             null);

    HeapSet heapSet = captureObject.getHeapSet(CaptureObject.DEFAULT_HEAP_ID);
    assertThat(heapSet).isNotNull();
    myStage.selectHeapSet(heapSet);

    assertThat(myStage.getConfiguration().getClassGrouping()).isEqualTo(ARRANGE_BY_CLASS);
    assertThat(myStage.getSelectedClassSet()).isNull();

    TableColumnModel tableColumnModel = myClassifierView.getTableColumnModel();
    assertThat(tableColumnModel.getColumn(0).getHeaderValue()).isEqualTo("Class Name");

    JTree classifierTree = myClassifierView.getTree();
    assertThat(classifierTree).isNotNull();
    Object root = classifierTree.getModel().getRoot();
    assertThat(root).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode)root).getAdapter()).isInstanceOf(HeapSet.class);
    //noinspection unchecked
    MemoryObjectTreeNode<ClassifierSet> rootNode = (MemoryObjectTreeNode<ClassifierSet>)root;
    assertThat(rootNode.getChildCount()).isEqualTo(3);

    myStage.getConfiguration().setClassGrouping(ARRANGE_BY_CALLSTACK);

    tableColumnModel = myClassifierView.getTableColumnModel();
    assertThat(tableColumnModel.getColumn(0).getHeaderValue()).isEqualTo("Callstack Name");

    root = classifierTree.getModel().getRoot();
    assertThat(root).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode)root).getAdapter()).isInstanceOf(HeapSet.class);
    //noinspection unchecked
    rootNode = (MemoryObjectTreeNode<ClassifierSet>)root;
    assertThat(rootNode.getChildCount()).isEqualTo(3);

    MemoryObjectTreeNode<? extends MemoryObject> methodSet1Node = findChildWithPredicate(
      rootNode,
      classifierSet -> classifierSet instanceof MethodSet && verifyMethodSet((MethodSet)classifierSet, CLASS_NAME_0, METHOD_NAME_0));
    assertThat(methodSet1Node.getChildCount()).isEqualTo(2);

    MemoryObjectTreeNode<? extends MemoryObject> methodSet2Node = findChildWithPredicate(
      methodSet1Node,
      classifierSet -> classifierSet instanceof MethodSet && verifyMethodSet((MethodSet)classifierSet, CLASS_NAME_0, METHOD_NAME_1));
    ClassSet callstack1FooClassSet = findChildClassSetWithName((ClassifierSet)methodSet2Node.getAdapter(), CLASS_NAME_0);
    assertThat(callstack1FooClassSet.findContainingClassifierSet(instance1)).isEqualTo(callstack1FooClassSet);
    assertThat(callstack1FooClassSet.findContainingClassifierSet(instance2)).isEqualTo(callstack1FooClassSet);
    assertThat(callstack1FooClassSet.findContainingClassifierSet(instance3)).isEqualTo(callstack1FooClassSet);

    MemoryObjectTreeNode<? extends MemoryObject> methodSet3Node = findChildWithPredicate(
      methodSet1Node,
      classifierSet -> classifierSet instanceof MethodSet && verifyMethodSet((MethodSet)classifierSet, CLASS_NAME_0, METHOD_NAME_2));
    ClassSet callstack2FooClassSet = findChildClassSetWithName((ClassifierSet)methodSet3Node.getAdapter(), CLASS_NAME_0);
    assertThat(callstack2FooClassSet.findContainingClassifierSet(instance4)).isEqualTo(callstack2FooClassSet);
    assertThat(callstack2FooClassSet.findContainingClassifierSet(instance5)).isEqualTo(callstack2FooClassSet);

    MemoryObjectTreeNode<? extends MemoryObject> methodSet4Node = findChildWithPredicate(
      rootNode,
      classifierSet -> classifierSet instanceof MethodSet && verifyMethodSet((MethodSet)classifierSet, CLASS_NAME_1, METHOD_NAME_3));
    assertThat(methodSet4Node.getChildCount()).isEqualTo(2);
    ClassSet callstack3FooClassSet = findChildClassSetWithName((ClassifierSet)methodSet4Node.getAdapter(), CLASS_NAME_0);
    assertThat(callstack3FooClassSet.findContainingClassifierSet(instance6)).isEqualTo(callstack3FooClassSet);
    ClassSet callstack3BarClassSet = findChildClassSetWithName((ClassifierSet)methodSet4Node.getAdapter(), CLASS_NAME_1);
    assertThat(callstack3BarClassSet.findContainingClassifierSet(instance7)).isEqualTo(callstack3BarClassSet);
    assertThat(callstack3BarClassSet.findContainingClassifierSet(instance9)).isEqualTo(callstack3BarClassSet);

    ClassSet noStackIntArrayClassSet = findChildClassSetWithName(rootNode.getAdapter(), CLASS_NAME_2);
    assertThat(noStackIntArrayClassSet.getAllocatedCount()).isEqualTo(1);

    //noinspection unchecked
    MemoryObjectTreeNode<? extends ClassifierSet> nodeToSelect =
      findChildClassSetNodeWithClassName((MemoryObjectTreeNode<ClassifierSet>)methodSet4Node, CLASS_NAME_0);
    classifierTree.setSelectionPath(new TreePath(new Object[]{root, methodSet4Node, nodeToSelect}));
    myStage.getConfiguration().setClassGrouping(ARRANGE_BY_CLASS);

    tableColumnModel = myClassifierView.getTableColumnModel();
    assertThat(tableColumnModel.getColumn(0).getHeaderValue()).isEqualTo("Class Name");

    assertThat(rootNode.getChildCount()).isEqualTo(3);
    TreePath selectionPath = classifierTree.getSelectionPath();
    assertThat(selectionPath).isNotNull();
    Object selectedObject = selectionPath.getLastPathComponent();
    assertThat(selectedObject).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode)selectedObject).getAdapter()).isInstanceOf(ClassSet.class);
    //noinspection unchecked
    assertThat(((MemoryObjectTreeNode<ClassSet>)selectedObject).getAdapter().isSupersetOf(nodeToSelect.getAdapter())).isTrue();
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
    myStage
      .selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> captureObject)),
                             null);

    HeapSet heapSet = captureObject.getHeapSet(CaptureObject.DEFAULT_HEAP_ID);
    assertThat(heapSet).isNotNull();
    myStage.selectHeapSet(heapSet);

    assertThat(myStage.getConfiguration().getClassGrouping()).isEqualTo(ARRANGE_BY_CLASS);

    JTree classifierTree = myClassifierView.getTree();
    assertThat(classifierTree).isNotNull();

    Object root = classifierTree.getModel().getRoot();
    assertThat(root).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode)root).getAdapter()).isInstanceOf(HeapSet.class);
    //noinspection unchecked
    MemoryObjectTreeNode<ClassifierSet> rootNode = (MemoryObjectTreeNode<ClassifierSet>)root;
    assertThat(rootNode.getChildCount()).isEqualTo(1);

    assertThat(myStage.getSelectedClassSet()).isNull();
    myStage.selectClassSet(findChildClassSetWithName(rootNode.getAdapter(), TEST_CLASS_NAME));
    myStage.selectInstanceObject(instance1);

    Supplier<CodeLocation> codeLocationSupplier = myFakeIdeProfilerComponents.getCodeLocationSupplier(classifierTree);

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
    myStage
      .selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> captureObject)),
                             null);

    HeapSet heapSet = captureObject.getHeapSet(CaptureObject.DEFAULT_HEAP_ID);
    assertThat(heapSet).isNotNull();
    myStage.selectHeapSet(heapSet);

    JTree tree = myClassifierView.getTree();
    assertThat(tree).isNotNull();
    JScrollPane columnTreePane = (JScrollPane)myClassifierView.getColumnTree();
    assertThat(columnTreePane).isNotNull();
    ColumnTreeTestInfo treeInfo = new ColumnTreeTestInfo(tree, columnTreePane);
    treeInfo.verifyColumnHeaders("Class Name", "Alloc Count", "Dealloc Count", "Native Size", "Shallow Size", "Retained Size");

    Object root = tree.getModel().getRoot();
    assertThat(root).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode)root).getAdapter()).isInstanceOf(HeapSet.class);
    //noinspection unchecked
    MemoryObjectTreeNode<ClassifierSet> rootNode = (MemoryObjectTreeNode<ClassifierSet>)root;
    assertThat(rootNode.getChildCount()).isEqualTo(3);

    List<InstanceObject> instanceObjects = Arrays.asList(instance1, instance2, instance3);
    List<String> classNames = Arrays.asList(CLASS_NAME_0, CLASS_NAME_1, CLASS_NAME_2);

    for (int i = 0; i < rootNode.getChildCount(); i++) {
      ClassSet classSet = findChildClassSetWithName(rootNode.getAdapter(), classNames.get(i));
      assertThat(classSet.findContainingClassifierSet(instanceObjects.get(i))).isEqualTo(classSet);
      MemoryObjectTreeNode<? extends ClassifierSet> node = findChildClassSetNodeWithClassName(rootNode, classNames.get(i));
      assertThat(node.getAdapter()).isEqualTo(classSet);
      treeInfo.verifyRendererValues(rootNode.getChildAt(i),
                                    new String[]{classSet.getClassEntry().getSimpleClassName(),
                                      classSet.getClassEntry().getPackageName().isEmpty()
                                      ? null
                                      : " (" + classSet.getClassEntry().getPackageName() + ")"},
                                    new String[]{Integer.toString(classSet.getAllocatedCount())},
                                    new String[]{Integer.toString(classSet.getDeallocatedCount())},
                                    new String[]{Long.toString(classSet.getTotalNativeSize())},
                                    new String[]{Long.toString(classSet.getTotalShallowSize())},
                                    new String[]{Long.toString(classSet.getTotalRetainedSize())});
    }
  }

  @Test
  public void testCaptureChangedListener() {
    final int captureStartTime = 0;
    // String containing data used for validating against each TreeNode.
    // Format: Name, Alloc Count, Dealloc Count, Instance Count, Shallow Size, Children Size
    final String nodeFormat = "%s,%d,%d,%d,%d";

    LiveAllocationCaptureObject capture = new LiveAllocationCaptureObject(myGrpcChannel.getClient().getMemoryClient(),
                                                                          ProfilersTestData.SESSION_DATA,
                                                                          captureStartTime,
                                                                          MoreExecutors.newDirectExecutorService(),
                                                                          myStage);
    // Bypass the stage's selection logic and manually alter the range selection ourselves.
    // This avoids the interaction of the SelectionModel triggering the stage to select CaptureData based on AllocationInfosDataSeries
    myStage.getSelectionModel().clearListeners();
    myStage.selectCaptureDuration(new CaptureDurationData<>(Long.MAX_VALUE, true, true, new CaptureEntry<>(capture, () -> capture)),
                                  MoreExecutors.directExecutor());
    myStage.selectHeapSet(capture.getHeapSet(CaptureObject.DEFAULT_HEAP_ID));
    // Changed to group by package so we can test nested node cases.
    myStage.getConfiguration().setClassGrouping(ARRANGE_BY_PACKAGE);

    JTree tree = myClassifierView.getTree();
    assertThat(tree).isNotNull();
    JScrollPane columnTreePane = (JScrollPane)myClassifierView.getColumnTree();
    assertThat(columnTreePane).isNotNull();
    ColumnTreeTestInfo treeInfo = new ColumnTreeTestInfo(tree, columnTreePane);

    Object root = tree.getModel().getRoot();
    assertThat(root).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode)root).getAdapter()).isInstanceOf(HeapSet.class);
    //noinspection unchecked
    MemoryObjectTreeNode<ClassifierSet> rootNode = (MemoryObjectTreeNode<ClassifierSet>)root;
    assertThat(rootNode.getChildCount()).isEqualTo(0);

    // Initial selection from 0 to 3
    Range selectionRange = myStage.getStudioProfilers().getTimeline().getSelectionRange();
    selectionRange.set(captureStartTime, captureStartTime + 3);
    Queue<String> expected_0_to_3 = new LinkedList<>();
    expected_0_to_3.add(String.format(nodeFormat, "default heap", 3, 1, 2, 2));
    expected_0_to_3.add(" " + String.format(nodeFormat, "This", 2, 1, 1, 2));
    expected_0_to_3.add("  " + String.format(nodeFormat, "Is", 1, 1, 0, 1));
    expected_0_to_3.add("   " + String.format(nodeFormat, "Foo", 1, 1, 0, 0));
    expected_0_to_3.add("  " + String.format(nodeFormat, "Also", 1, 0, 1, 1));
    expected_0_to_3.add("   " + String.format(nodeFormat, "Foo", 1, 0, 1, 0));
    expected_0_to_3.add(" " + String.format(nodeFormat, "That", 1, 0, 1, 1));
    expected_0_to_3.add("  " + String.format(nodeFormat, "Is", 1, 0, 1, 1));
    expected_0_to_3.add("   " + String.format(nodeFormat, "Bar", 1, 0, 1, 0));
    verifyLiveAllocRenderResult(treeInfo, rootNode, expected_0_to_3, 0);
    assertThat(myClassifierView.getClassifierPanel().getComponentCount()).isEqualTo(1);
    assertThat(myClassifierView.getClassifierPanel().getComponent(0)).isNotInstanceOf(InfoMessagePanel.class);

    // Expand right to 6
    selectionRange.set(captureStartTime, captureStartTime + 6);
    Queue<String> expected_0_to_6 = new LinkedList<>();
    expected_0_to_6.add(String.format(nodeFormat, "default heap", 6, 4, 2, 2));
    expected_0_to_6.add(" " + String.format(nodeFormat, "This", 3, 2, 1, 2));
    expected_0_to_6.add("  " + String.format(nodeFormat, "Is", 2, 1, 1, 1));
    expected_0_to_6.add("   " + String.format(nodeFormat, "Foo", 2, 1, 1, 0));
    expected_0_to_6.add("  " + String.format(nodeFormat, "Also", 1, 1, 0, 1));
    expected_0_to_6.add("   " + String.format(nodeFormat, "Foo", 1, 1, 0, 0));
    expected_0_to_6.add(" " + String.format(nodeFormat, "That", 3, 2, 1, 2));
    expected_0_to_6.add("  " + String.format(nodeFormat, "Is", 2, 1, 1, 1));
    expected_0_to_6.add("   " + String.format(nodeFormat, "Bar", 2, 1, 1, 0));
    expected_0_to_6.add("  " + String.format(nodeFormat, "Also", 1, 1, 0, 1));
    expected_0_to_6.add("   " + String.format(nodeFormat, "Bar", 1, 1, 0, 0));
    verifyLiveAllocRenderResult(treeInfo, rootNode, expected_0_to_6, 0);
    assertThat(myClassifierView.getClassifierPanel().getComponentCount()).isEqualTo(1);
    assertThat(myClassifierView.getClassifierPanel().getComponent(0)).isNotInstanceOf(InfoMessagePanel.class);

    // Shrink left to 3
    selectionRange.set(captureStartTime + 3, captureStartTime + 6);
    Queue<String> expected_3_to_6 = new LinkedList<>();
    expected_3_to_6.add(String.format(nodeFormat, "default heap", 3, 3, 0, 2));
    expected_3_to_6.add(" " + String.format(nodeFormat, "This", 1, 1, 0, 2));
    expected_3_to_6.add("  " + String.format(nodeFormat, "Is", 1, 0, 1, 1));
    expected_3_to_6.add("   " + String.format(nodeFormat, "Foo", 1, 0, 1, 0));
    expected_3_to_6.add("  " + String.format(nodeFormat, "Also", 0, 1, -1, 1));
    expected_3_to_6.add("   " + String.format(nodeFormat, "Foo", 0, 1, -1, 0));
    expected_3_to_6.add(" " + String.format(nodeFormat, "That", 2, 2, 0, 2));
    expected_3_to_6.add("  " + String.format(nodeFormat, "Is", 1, 1, 0, 1));
    expected_3_to_6.add("   " + String.format(nodeFormat, "Bar", 1, 1, 0, 0));
    expected_3_to_6.add("  " + String.format(nodeFormat, "Also", 1, 1, 0, 1));
    expected_3_to_6.add("   " + String.format(nodeFormat, "Bar", 1, 1, 0, 0));
    verifyLiveAllocRenderResult(treeInfo, rootNode, expected_3_to_6, 0);
    assertThat(myClassifierView.getClassifierPanel().getComponentCount()).isEqualTo(1);
    assertThat(myClassifierView.getClassifierPanel().getComponent(0)).isNotInstanceOf(InfoMessagePanel.class);

    // Shrink right to 3
    selectionRange.set(captureStartTime + 3, captureStartTime + 3);
    Queue<String> expected_3_to_3 = new LinkedList<>();
    expected_3_to_3.add(String.format(nodeFormat, "default heap", 0, 0, 0, 0));
    verifyLiveAllocRenderResult(treeInfo, rootNode, expected_3_to_3, 0);
    assertThat(myClassifierView.getClassifierPanel().getComponentCount()).isEqualTo(1);
    assertThat(myClassifierView.getClassifierPanel().getComponent(0)).isInstanceOf(InfoMessagePanel.class);

    // Shifts to {4,10}
    selectionRange.set(captureStartTime + 4, captureStartTime + 9);
    Queue<String> expected_4_to_9 = new LinkedList<>();
    expected_4_to_9.add(String.format(nodeFormat, "default heap", 5, 5, 0, 2));
    expected_4_to_9.add(" " + String.format(nodeFormat, "This", 3, 3, 0, 2));
    expected_4_to_9.add("  " + String.format(nodeFormat, "Is", 2, 1, 1, 1));
    expected_4_to_9.add("   " + String.format(nodeFormat, "Foo", 2, 1, 1, 0));
    expected_4_to_9.add("  " + String.format(nodeFormat, "Also", 1, 2, -1, 1));
    expected_4_to_9.add("   " + String.format(nodeFormat, "Foo", 1, 2, -1, 0));
    expected_4_to_9.add(" " + String.format(nodeFormat, "That", 2, 2, 0, 2));
    expected_4_to_9.add("  " + String.format(nodeFormat, "Is", 1, 1, 0, 1));
    expected_4_to_9.add("   " + String.format(nodeFormat, "Bar", 1, 1, 0, 0));
    expected_4_to_9.add("  " + String.format(nodeFormat, "Also", 1, 1, 0, 1));
    expected_4_to_9.add("   " + String.format(nodeFormat, "Bar", 1, 1, 0, 0));
    verifyLiveAllocRenderResult(treeInfo, rootNode, expected_4_to_9, 0);
    assertThat(myClassifierView.getClassifierPanel().getComponentCount()).isEqualTo(1);
    assertThat(myClassifierView.getClassifierPanel().getComponent(0)).isNotInstanceOf(InfoMessagePanel.class);

    // Displays snapshot at {10,10}
    myFakeIdeProfilerServices.enableMemorySnapshot(true);
    selectionRange.set(captureStartTime + 9, captureStartTime + 9);
    Queue<String> expected_snapshot_3 = new LinkedList<>();
    expected_snapshot_3.add(String.format(nodeFormat, "default heap", 2, 0, 2, 2));
    expected_snapshot_3.add(" " + String.format(nodeFormat, "This", 1, 0, 1, 1));
    expected_snapshot_3.add("  " + String.format(nodeFormat, "Is", 1, 0, 1, 1));
    expected_snapshot_3.add("   " + String.format(nodeFormat, "Foo", 1, 0, 1, 0));
    expected_snapshot_3.add(" " + String.format(nodeFormat, "That", 1, 0, 1, 1));
    expected_snapshot_3.add("  " + String.format(nodeFormat, "Also", 1, 0, 1, 1));
    expected_snapshot_3.add("   " + String.format(nodeFormat, "Bar", 1, 0, 1, 0));
    verifyLiveAllocRenderResult(treeInfo, rootNode, expected_snapshot_3, 0);
    assertThat(myClassifierView.getClassifierPanel().getComponentCount()).isEqualTo(1);
    assertThat(myClassifierView.getClassifierPanel().getComponent(0)).isNotInstanceOf(InfoMessagePanel.class);
  }

  @Test
  public void testCaptureFilter() {
    final int captureStartTime = 0;
    // String containing data used for validating against each TreeNode.
    // Format: Name, Alloc Count, Dealloc Count, Instance Count, Shallow Size, Children Size
    final String nodeFormat = "%s,%d,%d,%d,%d";

    LiveAllocationCaptureObject capture = new LiveAllocationCaptureObject(myGrpcChannel.getClient().getMemoryClient(),
                                                                          ProfilersTestData.SESSION_DATA,
                                                                          captureStartTime,
                                                                          MoreExecutors.newDirectExecutorService(),
                                                                          myStage);
    // Bypass the stage's selection logic and manually alter the range selection ourselves.
    // This avoids the interaction of the SelectionModel triggering the stage to select CaptureData based on AllocationInfosDataSeries
    myStage.getSelectionModel().clearListeners();
    myStage.selectCaptureDuration(new CaptureDurationData<>(Long.MAX_VALUE, true, true, new CaptureEntry<>(capture, () -> capture)),
                                  MoreExecutors.directExecutor());
    myStage.selectHeapSet(capture.getHeapSet(CaptureObject.DEFAULT_HEAP_ID));
    // Changed to group by package so we can test nested node cases.
    myStage.getConfiguration().setClassGrouping(ARRANGE_BY_PACKAGE);

    JTree tree = myClassifierView.getTree();
    assertThat(tree).isNotNull();
    JScrollPane columnTreePane = (JScrollPane)myClassifierView.getColumnTree();
    assertThat(columnTreePane).isNotNull();
    ColumnTreeTestInfo treeInfo = new ColumnTreeTestInfo(tree, columnTreePane);

    Object root = tree.getModel().getRoot();
    assertThat(root).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode)root).getAdapter()).isInstanceOf(HeapSet.class);
    //noinspection unchecked
    MemoryObjectTreeNode<ClassifierSet> rootNode = (MemoryObjectTreeNode<ClassifierSet>)root;
    assertThat(rootNode.getChildCount()).isEqualTo(0);

    // Initial selection from 0 to 3
    Range selectionRange = myStage.getStudioProfilers().getTimeline().getSelectionRange();
    selectionRange.set(captureStartTime, captureStartTime + 3);
    Queue<String> expected_0_to_3 = new LinkedList<>();
    expected_0_to_3.add(String.format(nodeFormat, "default heap", 3, 1, 2, 2));
    expected_0_to_3.add(" " + String.format(nodeFormat, "This", 2, 1, 1, 2));
    expected_0_to_3.add("  " + String.format(nodeFormat, "Is", 1, 1, 0, 1));
    expected_0_to_3.add("   " + String.format(nodeFormat, "Foo", 1, 1, 0, 0));
    expected_0_to_3.add("  " + String.format(nodeFormat, "Also", 1, 0, 1, 1));
    expected_0_to_3.add("   " + String.format(nodeFormat, "Foo", 1, 0, 1, 0));
    expected_0_to_3.add(" " + String.format(nodeFormat, "That", 1, 0, 1, 1));
    expected_0_to_3.add("  " + String.format(nodeFormat, "Is", 1, 0, 1, 1));
    expected_0_to_3.add("   " + String.format(nodeFormat, "Bar", 1, 0, 1, 0));
    verifyLiveAllocRenderResult(treeInfo, rootNode, expected_0_to_3, 0);
    assertThat(myClassifierView.getClassifierPanel().getComponentCount()).isEqualTo(1);
    assertThat(myClassifierView.getClassifierPanel().getComponent(0)).isNotInstanceOf(InfoMessagePanel.class);

    // Add a filter to remove That.is.Bar
    myStage.selectCaptureFilter(Pattern.compile("^.*Foo.*$"));

    expected_0_to_3 = new LinkedList<>();
    expected_0_to_3.add(String.format(nodeFormat, "default heap", 2, 1, 1, 1));
    expected_0_to_3.add(" " + String.format(nodeFormat, "This", 2, 1, 1, 2));
    expected_0_to_3.add("  " + String.format(nodeFormat, "Is", 1, 1, 0, 1));
    expected_0_to_3.add("   " + String.format(nodeFormat, "Foo", 1, 1, 0, 0));
    expected_0_to_3.add("  " + String.format(nodeFormat, "Also", 1, 0, 1, 1));
    expected_0_to_3.add("   " + String.format(nodeFormat, "Foo", 1, 0, 1, 0));
    verifyLiveAllocRenderResult(treeInfo, rootNode, expected_0_to_3, 0);
    assertThat(myClassifierView.getClassifierPanel().getComponentCount()).isEqualTo(1);
    assertThat(myClassifierView.getClassifierPanel().getComponent(0)).isNotInstanceOf(InfoMessagePanel.class);

    // Expand right to 6
    selectionRange.set(captureStartTime, captureStartTime + 6);
    Queue<String> expected_0_to_6 = new LinkedList<>();
    expected_0_to_6.add(String.format(nodeFormat, "default heap", 3, 2, 1, 1));
    expected_0_to_6.add(" " + String.format(nodeFormat, "This", 3, 2, 1, 2));
    expected_0_to_6.add("  " + String.format(nodeFormat, "Is", 2, 1, 1, 1));
    expected_0_to_6.add("   " + String.format(nodeFormat, "Foo", 2, 1, 1, 0));
    expected_0_to_6.add("  " + String.format(nodeFormat, "Also", 1, 1, 0, 1));
    expected_0_to_6.add("   " + String.format(nodeFormat, "Foo", 1, 1, 0, 0));
    verifyLiveAllocRenderResult(treeInfo, rootNode, expected_0_to_6, 0);
    assertThat(myClassifierView.getClassifierPanel().getComponentCount()).isEqualTo(1);
    assertThat(myClassifierView.getClassifierPanel().getComponent(0)).isNotInstanceOf(InfoMessagePanel.class);

    // Shrink left to 3
    selectionRange.set(captureStartTime + 3, captureStartTime + 6);
    Queue<String> expected_3_to_6 = new LinkedList<>();
    expected_3_to_6.add(String.format(nodeFormat, "default heap", 1, 1, 0, 1));
    expected_3_to_6.add(" " + String.format(nodeFormat, "This", 1, 1, 0, 2));
    expected_3_to_6.add("  " + String.format(nodeFormat, "Is", 1, 0, 1, 1));
    expected_3_to_6.add("   " + String.format(nodeFormat, "Foo", 1, 0, 1, 0));
    expected_3_to_6.add("  " + String.format(nodeFormat, "Also", 0, 1, -1, 1));
    expected_3_to_6.add("   " + String.format(nodeFormat, "Foo", 0, 1, -1, 0));
    verifyLiveAllocRenderResult(treeInfo, rootNode, expected_3_to_6, 0);
    assertThat(myClassifierView.getClassifierPanel().getComponentCount()).isEqualTo(1);
    assertThat(myClassifierView.getClassifierPanel().getComponent(0)).isNotInstanceOf(InfoMessagePanel.class);

    // Displays snapshot at {10,10}
    myFakeIdeProfilerServices.enableMemorySnapshot(true);
    selectionRange.set(captureStartTime + 9, captureStartTime + 9);
    Queue<String> expected_snapshot_3 = new LinkedList<>();
    expected_snapshot_3.add(String.format(nodeFormat, "default heap", 1, 0, 1, 1));
    expected_snapshot_3.add(" " + String.format(nodeFormat, "This", 1, 0, 1, 1));
    expected_snapshot_3.add("  " + String.format(nodeFormat, "Is", 1, 0, 1, 1));
    expected_snapshot_3.add("   " + String.format(nodeFormat, "Foo", 1, 0, 1, 0));
    verifyLiveAllocRenderResult(treeInfo, rootNode, expected_snapshot_3, 0);
    assertThat(myClassifierView.getClassifierPanel().getComponentCount()).isEqualTo(1);
    assertThat(myClassifierView.getClassifierPanel().getComponent(0)).isNotInstanceOf(InfoMessagePanel.class);

    // Changed to group by callstack and update the rootNode
    myStage.getConfiguration().setClassGrouping(ARRANGE_BY_CALLSTACK);
    root = tree.getModel().getRoot();
    assertThat(root).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode)root).getAdapter()).isInstanceOf(HeapSet.class);
    rootNode = (MemoryObjectTreeNode<ClassifierSet>)root;

    // The "Foo" filter should apply to the new grouping automatically
    myFakeIdeProfilerServices.enableMemorySnapshot(true);
    expected_snapshot_3 = new LinkedList<>();
    expected_snapshot_3.add(String.format(nodeFormat, "default heap", 2, 0, 2, 2));
    expected_snapshot_3.add(" " + String.format(nodeFormat, "FooMethodA()", 1, 0, 1, 1));
    expected_snapshot_3.add("  " + String.format(nodeFormat, "BarMethodB()", 1, 0, 1, 1));
    expected_snapshot_3.add("   " + String.format(nodeFormat, "Bar", 1, 0, 1, 0));
    expected_snapshot_3.add(" " + String.format(nodeFormat, "BarMethodA()", 1, 0, 1, 1));
    expected_snapshot_3.add("  " + String.format(nodeFormat, "FooMethodA()", 1, 0, 1, 1));
    expected_snapshot_3.add("   " + String.format(nodeFormat, "Foo", 1, 0, 1, 0));
    verifyLiveAllocRenderResult(treeInfo, rootNode, expected_snapshot_3, 0);
    assertThat(myClassifierView.getClassifierPanel().getComponentCount()).isEqualTo(1);
    assertThat(myClassifierView.getClassifierPanel().getComponent(0)).isNotInstanceOf(InfoMessagePanel.class);
  }


  @Test
  public void testSelectedClassSetAndInstance() {
    final int captureStartTime = 0;
    // String containing data used for validating against each TreeNode.
    // Format: Name, Alloc Count, Dealloc Count, Instance Count, Shallow Size, Children Size
    final String nodeFormat = "%s,%d,%d,%d,%d";

    // classSetView includes necessary listeners to refresh class set instanceObjects
    MemoryClassSetView classSetView = new MemoryClassSetView(myStage, myFakeIdeProfilerComponents);

    LiveAllocationCaptureObject capture = new LiveAllocationCaptureObject(myGrpcChannel.getClient().getMemoryClient(),
                                                                          ProfilersTestData.SESSION_DATA,
                                                                          captureStartTime,
                                                                          MoreExecutors.newDirectExecutorService(),
                                                                          myStage);
    // Bypass the stage's selection logic and manually alter the range selection ourselves.
    // This avoids the interaction of the SelectionModel triggering the stage to select CaptureData based on AllocationInfosDataSeries
    myStage.getSelectionModel().clearListeners();
    myStage.selectCaptureDuration(new CaptureDurationData<>(Long.MAX_VALUE, true, true, new CaptureEntry<>(capture, () -> capture)),
                                  MoreExecutors.directExecutor());
    myStage.selectHeapSet(capture.getHeapSet(CaptureObject.DEFAULT_HEAP_ID));
    // Changed to group by package so we can test nested node cases.
    myStage.getConfiguration().setClassGrouping(ARRANGE_BY_PACKAGE);

    JTree tree = myClassifierView.getTree();
    assertThat(tree).isNotNull();
    JScrollPane columnTreePane = (JScrollPane)myClassifierView.getColumnTree();
    assertThat(columnTreePane).isNotNull();
    ColumnTreeTestInfo treeInfo = new ColumnTreeTestInfo(tree, columnTreePane);

    Object root = tree.getModel().getRoot();
    assertThat(root).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode)root).getAdapter()).isInstanceOf(HeapSet.class);
    //noinspection unchecked
    MemoryObjectTreeNode<ClassifierSet> rootNode = (MemoryObjectTreeNode<ClassifierSet>)root;
    assertThat(rootNode.getChildCount()).isEqualTo(0);

    // Initial selection from 0 to 3
    Range selectionRange = myStage.getStudioProfilers().getTimeline().getSelectionRange();
    selectionRange.set(captureStartTime, captureStartTime + 3);
    Queue<String> expected_0_to_3 = new LinkedList<>();
    expected_0_to_3.add(String.format(nodeFormat, "default heap", 3, 1, 2, 2));
    expected_0_to_3.add(" " + String.format(nodeFormat, "This", 2, 1, 1, 2));
    expected_0_to_3.add("  " + String.format(nodeFormat, "Is", 1, 1, 0, 1));
    expected_0_to_3.add("   " + String.format(nodeFormat, "Foo", 1, 1, 0, 0));
    expected_0_to_3.add("  " + String.format(nodeFormat, "Also", 1, 0, 1, 1));
    expected_0_to_3.add("   " + String.format(nodeFormat, "Foo", 1, 0, 1, 0));
    expected_0_to_3.add(" " + String.format(nodeFormat, "That", 1, 0, 1, 1));
    expected_0_to_3.add("  " + String.format(nodeFormat, "Is", 1, 0, 1, 1));
    expected_0_to_3.add("   " + String.format(nodeFormat, "Bar", 1, 0, 1, 0));
    verifyLiveAllocRenderResult(treeInfo, rootNode, expected_0_to_3, 0);

    MemoryObjectTreeNode<ClassifierSet> childThat = rootNode.getChildren().get(1);
    MemoryObjectTreeNode<ClassifierSet> childThatIs = childThat.getChildren().get(0);
    MemoryObjectTreeNode<ClassifierSet> childThatIsBar = childThatIs.getChildren().get(0);
    tree.setSelectionPath(new TreePath(new Object[]{root, childThat, childThatIs, childThatIsBar}));

    InstanceObject selectedInstance = childThatIsBar.getAdapter().getInstancesStream().collect(Collectors.toList()).get(0);
    myStage.selectInstanceObject(selectedInstance);
    assertThat(myStage.getSelectedInstanceObject()).isEqualTo(selectedInstance);

    assertThat(childThatIsBar.getAdapter()).isInstanceOf(ClassSet.class);
    assertThat(myStage.getSelectedClassSet()).isEqualTo(childThatIsBar.getAdapter());
    assertThat(tree.getSelectionPath().getLastPathComponent()).isInstanceOf(MemoryObjectTreeNode.class);
    MemoryObjectTreeNode<ClassifierSet> selectedNode = (MemoryObjectTreeNode<ClassifierSet>)tree.getSelectionPath().getLastPathComponent();
    assertThat(selectedNode).isEqualTo(childThatIsBar);

    // Expand right to 6
    selectionRange.set(captureStartTime, captureStartTime + 6);
    Queue<String> expected_0_to_6 = new LinkedList<>();
    expected_0_to_6.add(String.format(nodeFormat, "default heap", 6, 4, 2, 2));
    expected_0_to_6.add(" " + String.format(nodeFormat, "This", 3, 2, 1, 2));
    expected_0_to_6.add("  " + String.format(nodeFormat, "Is", 2, 1, 1, 1));
    expected_0_to_6.add("   " + String.format(nodeFormat, "Foo", 2, 1, 1, 0));
    expected_0_to_6.add("  " + String.format(nodeFormat, "Also", 1, 1, 0, 1));
    expected_0_to_6.add("   " + String.format(nodeFormat, "Foo", 1, 1, 0, 0));
    expected_0_to_6.add(" " + String.format(nodeFormat, "That", 3, 2, 1, 2));
    expected_0_to_6.add("  " + String.format(nodeFormat, "Is", 2, 1, 1, 1));
    expected_0_to_6.add("   " + String.format(nodeFormat, "Bar", 2, 1, 1, 0));
    expected_0_to_6.add("  " + String.format(nodeFormat, "Also", 1, 1, 0, 1));
    expected_0_to_6.add("   " + String.format(nodeFormat, "Bar", 1, 1, 0, 0));
    verifyLiveAllocRenderResult(treeInfo, rootNode, expected_0_to_6, 0);

    // Selected ClassSet should not change
    assertThat(tree.getSelectionPath().getLastPathComponent()).isInstanceOf(MemoryObjectTreeNode.class);
    selectedNode = (MemoryObjectTreeNode<ClassifierSet>)tree.getSelectionPath().getLastPathComponent();
    assertThat(selectedNode.getAdapter()).isEqualTo(childThatIsBar.getAdapter());
    assertThat(myStage.getSelectedClassSet()).isEqualTo(childThatIsBar.getAdapter());
    assertThat(myStage.getSelectedInstanceObject()).isEqualTo(selectedInstance);

    // Shifts to {4,10}
    selectionRange.set(captureStartTime + 4, captureStartTime + 9);
    Queue<String> expected_4_to_9 = new LinkedList<>();
    expected_4_to_9.add(String.format(nodeFormat, "default heap", 5, 5, 0, 2));
    expected_4_to_9.add(" " + String.format(nodeFormat, "This", 3, 3, 0, 2));
    expected_4_to_9.add("  " + String.format(nodeFormat, "Is", 2, 1, 1, 1));
    expected_4_to_9.add("   " + String.format(nodeFormat, "Foo", 2, 1, 1, 0));
    expected_4_to_9.add("  " + String.format(nodeFormat, "Also", 1, 2, -1, 1));
    expected_4_to_9.add("   " + String.format(nodeFormat, "Foo", 1, 2, -1, 0));
    expected_4_to_9.add(" " + String.format(nodeFormat, "That", 2, 2, 0, 2));
    expected_4_to_9.add("  " + String.format(nodeFormat, "Is", 1, 1, 0, 1));
    expected_4_to_9.add("   " + String.format(nodeFormat, "Bar", 1, 1, 0, 0));
    expected_4_to_9.add("  " + String.format(nodeFormat, "Also", 1, 1, 0, 1));
    expected_4_to_9.add("   " + String.format(nodeFormat, "Bar", 1, 1, 0, 0));
    verifyLiveAllocRenderResult(treeInfo, rootNode, expected_4_to_9, 0);
    assertThat(myStage.getSelectedInstanceObject()).isNull();

    // Shrink to empty range
    myFakeIdeProfilerServices.enableMemorySnapshot(true);
    selectionRange.set(captureStartTime + 3, captureStartTime + 3);
    Queue<String> expected_snapshot_3 = new LinkedList<>();
    expected_snapshot_3.add(String.format(nodeFormat, "default heap", 2, 0, 2, 2));
    expected_snapshot_3.add(" " + String.format(nodeFormat, "This", 1, 0, 1, 1));
    expected_snapshot_3.add("  " + String.format(nodeFormat, "Also", 1, 0, 1, 1));
    expected_snapshot_3.add("   " + String.format(nodeFormat, "Foo", 1, 0, 1, 0));
    expected_snapshot_3.add(" " + String.format(nodeFormat, "That", 1, 0, 1, 1));
    expected_snapshot_3.add("  " + String.format(nodeFormat, "Is", 1, 0, 1, 1));
    expected_snapshot_3.add("   " + String.format(nodeFormat, "Bar", 1, 0, 1, 0));
    verifyLiveAllocRenderResult(treeInfo, rootNode, expected_snapshot_3, 0);

    // No path is selected and selected ClassSet is set to EMPTY_CLASS_SET
    assertThat(tree.getSelectionPath()).isNull();
    assertThat(myStage.getSelectedClassSet()).isEqualTo(ClassSet.EMPTY_SET);
    assertThat(myStage.getSelectedInstanceObject()).isNull();
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

  private static boolean verifyMethodSet(@NotNull MethodSet methodSet, @NotNull String className, @NotNull String methodName) {
    return className.equals(methodSet.getClassName()) && methodName.equals(methodSet.getMethodName());
  }

  /**
   * Helper method to walk through the entire tree hierarchy and validate each node against the data stored in the expected queue.
   */
  private static boolean verifyLiveAllocRenderResult(@NotNull ColumnTreeTestInfo testInfo,
                                                     @NotNull MemoryObjectTreeNode node,
                                                     @NotNull Queue<String> expected,
                                                     int currentDepth) {
    boolean done = false;
    boolean currentNodeVisited = false;
    boolean childrenVisited = false;
    String line;

    while ((line = expected.peek()) != null && !done) {
      String trimmedLine = line.trim();
      int depth = line.indexOf(trimmedLine);

      if (depth < currentDepth) {
        // We are done with the current sub-tree.
        done = true;
      }
      else if (depth > currentDepth) {
        // We need to go deeper...
        ImmutableList<MemoryObjectTreeNode> children = node.getChildren();
        assertThat(children.size()).isGreaterThan(0);
        assertThat(childrenVisited).isFalse();
        for (MemoryObjectTreeNode childNode : children) {
          boolean childResult = verifyLiveAllocRenderResult(testInfo, childNode, expected, currentDepth + 1);
          assertThat(childResult).isTrue();
        }
        childrenVisited = true;
      }
      else {
        if (currentNodeVisited) {
          done = true;
          continue;
        }

        // We are at current node, consumes the current line.
        expected.poll();
        String[] split = trimmedLine.split(",");
        testInfo.verifyRendererValues(node, new String[]{split[0]}, new String[]{split[1]}, new String[]{split[2]}, new String[]{split[3]});

        // Children's size
        assertThat(node.getChildCount()).isEqualTo(Integer.parseInt(split[4]));
        currentNodeVisited = true;
      }
    }

    assertThat(currentNodeVisited).isTrue();
    return currentNodeVisited;
  }
}
