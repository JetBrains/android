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

import static com.android.tools.profiler.proto.MemoryProfiler.AllocationStack;
import static com.android.tools.profilers.memory.MemoryProfilerConfiguration.ClassGrouping.*;
import static com.android.tools.profilers.memory.MemoryProfilerTestUtils.*;

import static com.google.common.truth.Truth.*;

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
    myStage.selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> captureObject)),
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
    myStage.selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> captureObject)),
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
    myStage.selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> captureObject)),
                                  null);

    HeapSet heapSet = captureObject.getHeapSet(FakeCaptureObject.DEFAULT_HEAP_ID);
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
    myStage.selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> captureObject)),
                                  null);

    HeapSet heapSet = captureObject.getHeapSet(FakeCaptureObject.DEFAULT_HEAP_ID);
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

    MemoryObjectTreeNode<? extends MemoryObject> codeLocation1Node = findChildWithPredicate(
      rootNode,
      classifierSet -> classifierSet instanceof MethodSet && codeLocation1.equals(((MethodSet)classifierSet).getCodeLocation()));
    assertThat(codeLocation1Node.getChildCount()).isEqualTo(2);

    MemoryObjectTreeNode<? extends MemoryObject> codeLocation2Node = findChildWithPredicate(
      codeLocation1Node,
      classifierSet -> classifierSet instanceof MethodSet && codeLocation2.equals(((MethodSet)classifierSet).getCodeLocation()));
    ClassSet callstack1FooClassSet = findChildClassSetWithName((ClassifierSet)codeLocation2Node.getAdapter(), CLASS_NAME_0);
    assertThat(callstack1FooClassSet.findContainingClassifierSet(instance1)).isEqualTo(callstack1FooClassSet);
    assertThat(callstack1FooClassSet.findContainingClassifierSet(instance2)).isEqualTo(callstack1FooClassSet);
    assertThat(callstack1FooClassSet.findContainingClassifierSet(instance3)).isEqualTo(callstack1FooClassSet);

    MemoryObjectTreeNode<? extends MemoryObject> codeLocation3Node = findChildWithPredicate(
      codeLocation1Node,
      classifierSet -> classifierSet instanceof MethodSet && codeLocation3.equals(((MethodSet)classifierSet).getCodeLocation()));
    ClassSet callstack2FooClassSet = findChildClassSetWithName((ClassifierSet)codeLocation3Node.getAdapter(), CLASS_NAME_0);
    assertThat(callstack2FooClassSet.findContainingClassifierSet(instance4)).isEqualTo(callstack2FooClassSet);
    assertThat(callstack2FooClassSet.findContainingClassifierSet(instance5)).isEqualTo(callstack2FooClassSet);

    MemoryObjectTreeNode<? extends MemoryObject> codeLocation4Node = findChildWithPredicate(
      rootNode,
      classifierSet -> classifierSet instanceof MethodSet && codeLocation4.equals(((MethodSet)classifierSet).getCodeLocation()));
    assertThat(codeLocation4Node.getChildCount()).isEqualTo(2);
    ClassSet callstack3FooClassSet = findChildClassSetWithName((ClassifierSet)codeLocation4Node.getAdapter(), CLASS_NAME_0);
    assertThat(callstack3FooClassSet.findContainingClassifierSet(instance6)).isEqualTo(callstack3FooClassSet);
    ClassSet callstack3BarClassSet = findChildClassSetWithName((ClassifierSet)codeLocation4Node.getAdapter(), CLASS_NAME_1);
    assertThat(callstack3BarClassSet.findContainingClassifierSet(instance7)).isEqualTo(callstack3BarClassSet);

    ClassSet noStackIntArrayClassSet = findChildClassSetWithName(rootNode.getAdapter(), CLASS_NAME_2);
    assertThat(noStackIntArrayClassSet.getAllocatedCount()).isEqualTo(1);

    //noinspection unchecked
    MemoryObjectTreeNode<? extends ClassifierSet> nodeToSelect = findChildClassSetNodeWithClassName((MemoryObjectTreeNode<ClassifierSet>)codeLocation4Node, CLASS_NAME_0);
    classifierTree.setSelectionPath(new TreePath(new Object[]{root, codeLocation4Node, nodeToSelect}));
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
    myStage.selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> captureObject)),
                                  null);

    HeapSet heapSet = captureObject.getHeapSet(FakeCaptureObject.DEFAULT_HEAP_ID);
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
    myStage.selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> captureObject)),
                                  null);

    HeapSet heapSet = captureObject.getHeapSet(FakeCaptureObject.DEFAULT_HEAP_ID);
    assertThat(heapSet).isNotNull();
    myStage.selectHeapSet(heapSet);

    JTree tree = myClassifierView.getTree();
    assertThat(tree).isNotNull();
    JScrollPane columnTreePane = (JScrollPane)myClassifierView.getColumnTree();
    assertThat(columnTreePane).isNotNull();
    ColumnTreeTestInfo treeInfo = new ColumnTreeTestInfo(tree, columnTreePane);
    treeInfo.verifyColumnHeaders("Class Name", "Alloc Count", "Dealloc Count", "Shallow Size", "Retained Size");

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
                                    new String[]{Long.toString(classSet.getTotalShallowSize())},
                                    new String[]{Long.toString(classSet.getTotalRetainedSize())});
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
