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
import com.android.tools.adtui.instructions.InstructionsPanel;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.filter.Filter;
import com.android.tools.adtui.model.formatter.NumberFormatter;
import com.android.tools.idea.codenavigation.CodeLocation;
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel;
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.profiler.proto.Memory;
import com.android.tools.profilers.*;
import com.android.tools.profilers.memory.adapters.*;
import com.android.tools.profilers.memory.adapters.classifiers.*;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.ui.ColoredTreeCellRenderer;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import javax.swing.*;
import javax.swing.table.TableColumnModel;
import javax.swing.tree.TreePath;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.android.tools.profiler.proto.Memory.AllocationStack;
import static com.android.tools.profilers.memory.ClassGrouping.*;
import static com.android.tools.profilers.memory.MemoryProfilerTestUtils.*;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

public class MemoryClassifierViewTest {
  private final FakeTimer myTimer = new FakeTimer();
  @Rule
  public FakeGrpcChannel myGrpcChannel =
    new FakeGrpcChannel("MEMORY_TEST_CHANNEL", new FakeTransportService(myTimer), new FakeProfilerService(myTimer),
                        new FakeMemoryService());

  private FakeIdeProfilerComponents myFakeIdeProfilerComponents;
  private MainMemoryProfilerStage myStage;
  private MemoryClassifierView myClassifierView;

  @Before
  public void before() {
    FakeCaptureObjectLoader loader = new FakeCaptureObjectLoader();
    loader.setReturnImmediateFuture(true);
    FakeIdeProfilerServices fakeIdeProfilerServices = new FakeIdeProfilerServices();
    myFakeIdeProfilerComponents = new FakeIdeProfilerComponents();
    myStage =
      new MainMemoryProfilerStage(
        new StudioProfilers(new ProfilerClient(myGrpcChannel.getChannel()), fakeIdeProfilerServices, new FakeTimer()),
        loader);
    myClassifierView = new MemoryClassifierView(myStage.getCaptureSelection(), myFakeIdeProfilerComponents);
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
      new FakeInstanceObject.Builder(captureObject, 0, CLASS_NAME_0).setName("instanceFoo0").setDepth(1).setShallowSize(2)
        .setRetainedSize(3).build();
    InstanceObject instanceFoo1 =
      new FakeInstanceObject.Builder(captureObject, 0, CLASS_NAME_0).setName("instanceFoo1").setDepth(2).setShallowSize(2)
        .setRetainedSize(3).build();
    InstanceObject instanceFoo2 =
      new FakeInstanceObject.Builder(captureObject, 0, CLASS_NAME_0).setName("instanceFoo2").setDepth(3).setShallowSize(2)
        .setRetainedSize(3).build();
    InstanceObject instanceBar0 =
      new FakeInstanceObject.Builder(captureObject, 1, CLASS_NAME_1).setName("instanceBar0").setDepth(1).setShallowSize(2)
        .setRetainedSize(4).build();
    InstanceObject instanceBaz0 =
      new FakeInstanceObject.Builder(captureObject, 2, CLASS_NAME_2).setName("instanceBaz0").setDepth(1).setShallowSize(2)
        .setRetainedSize(5).build();
    InstanceObject instanceBaz1 =
      new FakeInstanceObject.Builder(captureObject, 2, CLASS_NAME_2).setName("instanceBaz1").setDepth(1).setShallowSize(2)
        .setRetainedSize(5).build();
    InstanceObject instanceBaz2 =
      new FakeInstanceObject.Builder(captureObject, 2, CLASS_NAME_2).setName("instanceBaz2").setDepth(1).setShallowSize(2)
        .setRetainedSize(5).build();
    InstanceObject instanceBaz3 =
      new FakeInstanceObject.Builder(captureObject, 2, CLASS_NAME_2).setName("instanceBaz3").setDepth(1).setShallowSize(2)
        .setRetainedSize(5).build();
    Set<InstanceObject> instanceObjects = new HashSet<>(
      Arrays.asList(instanceFoo0, instanceFoo1, instanceFoo2, instanceBar0, instanceBaz0, instanceBaz1, instanceBaz2, instanceBaz3));
    captureObject.addInstanceObjects(instanceObjects);
    myStage
      .selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> captureObject)),
                             null);

    assertThat(captureObject.containsClass(0)).isTrue();
    assertThat(captureObject.containsClass(1)).isTrue();
    assertThat(captureObject.containsClass(2)).isTrue();

    HeapSet heapSet = captureObject.getHeapSet(instanceFoo0.getHeapId());
    assertThat(heapSet).isNotNull();
    myStage.getCaptureSelection().selectHeapSet(heapSet);

    assertThat(myStage.getCaptureSelection().getClassGrouping()).isEqualTo(ARRANGE_BY_CLASS);
    assertThat(myClassifierView.getTree()).isNotNull();
    JTree classifierTree = myClassifierView.getTree();

    Object root = classifierTree.getModel().getRoot();
    assertThat(root).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode<?>)root).getAdapter()).isInstanceOf(HeapSet.class);
    //noinspection unchecked
    MemoryObjectTreeNode<ClassifierSet> rootNode = (MemoryObjectTreeNode<ClassifierSet>)root;
    List<MemoryObjectTreeNode<ClassifierSet>> childrenOfRoot = rootNode.getChildren();

    classifierTree.setSelectionPath(new TreePath(new Object[]{root, childrenOfRoot.get(0)}));
    MemoryObject selectedClassifier = ((MemoryObjectTreeNode<?>)classifierTree.getSelectionPath().getLastPathComponent()).getAdapter();
    assertThat(selectedClassifier).isInstanceOf(ClassSet.class);

    // Check if group by package is grouping as expected.
    myStage.getCaptureSelection().setClassGrouping(ARRANGE_BY_PACKAGE);

    TableColumnModel tableColumnModel = myClassifierView.getTableColumnModel();
    assertThat(tableColumnModel.getColumn(0).getHeaderValue()).isEqualTo("Package Name");

    root = classifierTree.getModel().getRoot();
    assertThat(root).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode<?>)root).getAdapter()).isInstanceOf(HeapSet.class);
    //noinspection unchecked
    rootNode = (MemoryObjectTreeNode<ClassifierSet>)root;

    assertThat(countClassSets(rootNode)).isEqualTo(3);
    TreePath selectionPath = classifierTree.getSelectionPath();
    assertThat(selectionPath).isNotNull();
    assertThat(selectionPath.getLastPathComponent()).isInstanceOf(MemoryObjectTreeNode.class);
    MemoryObject reselectedClassifier = ((MemoryObjectTreeNode<?>)classifierTree.getSelectionPath().getLastPathComponent()).getAdapter();
    assertThat(reselectedClassifier).isInstanceOf(ClassSet.class);
    Set<InstanceObject> instances = ((ClassSet)reselectedClassifier).getInstancesStream().collect(Collectors.toSet());
    assertThat(((ClassSet)selectedClassifier).isSupersetOf(instances)).isTrue();

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
    myStage.getCaptureSelection().setClassGrouping(ARRANGE_BY_CLASS);

    tableColumnModel = myClassifierView.getTableColumnModel();
    assertThat(tableColumnModel.getColumn(0).getHeaderValue()).isEqualTo("Class Name");

    root = classifierTree.getModel().getRoot();
    assertThat(root).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode<?>)root).getAdapter()).isInstanceOf(HeapSet.class);
    //noinspection unchecked
    assertThat(((MemoryObjectTreeNode<?>)root).getChildCount()).isEqualTo(3);
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
      new FakeInstanceObject.Builder(captureObject, 0, CLASS_NAME_0).setName("instanceFoo0").setDepth(1).setShallowSize(2)
        .setRetainedSize(3)
        .build();
    InstanceObject instanceFoo1 =
      new FakeInstanceObject.Builder(captureObject, 0, CLASS_NAME_0).setName("instanceFoo1").setDepth(2).setShallowSize(2)
        .setRetainedSize(3)
        .build();
    InstanceObject instanceFoo2 =
      new FakeInstanceObject.Builder(captureObject, 0, CLASS_NAME_0).setName("instanceFoo2").setDepth(3).setShallowSize(2)
        .setRetainedSize(3)
        .build();
    InstanceObject instanceBar0 =
      new FakeInstanceObject.Builder(captureObject, 1, CLASS_NAME_1).setName("instanceBar0").setDepth(1).setShallowSize(2)
        .setRetainedSize(4)
        .build();
    InstanceObject instanceBaz0 =
      new FakeInstanceObject.Builder(captureObject, 2, CLASS_NAME_2).setName("instanceBaz0").setDepth(1).setShallowSize(2)
        .setRetainedSize(5)
        .build();
    InstanceObject instanceBaz1 =
      new FakeInstanceObject.Builder(captureObject, 2, CLASS_NAME_2).setName("instanceBaz1").setDepth(1).setShallowSize(2)
        .setRetainedSize(5)
        .build();
    InstanceObject instanceBaz2 =
      new FakeInstanceObject.Builder(captureObject, 2, CLASS_NAME_2).setName("instanceBaz2").setDepth(1).setShallowSize(2)
        .setRetainedSize(5)
        .build();
    InstanceObject instanceBaz3 =
      new FakeInstanceObject.Builder(captureObject, 2, CLASS_NAME_2).setName("instanceBaz3").setDepth(1).setShallowSize(2)
        .setRetainedSize(5)
        .build();
    Set<InstanceObject> instanceObjects = new HashSet<>(
      Arrays.asList(instanceFoo0, instanceFoo1, instanceFoo2, instanceBar0, instanceBaz0, instanceBaz1, instanceBaz2, instanceBaz3));
    captureObject.addInstanceObjects(instanceObjects);
    myStage
      .selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> captureObject)),
                             null);

    assertThat(captureObject.containsClass(0)).isTrue();
    assertThat(captureObject.containsClass(1)).isTrue();
    assertThat(captureObject.containsClass(2)).isTrue();

    HeapSet heapSet = captureObject.getHeapSet(instanceFoo0.getHeapId());
    assertThat(heapSet).isNotNull();
    myStage.getCaptureSelection().selectHeapSet(heapSet);

    assertThat(myStage.getCaptureSelection().getClassGrouping()).isEqualTo(ARRANGE_BY_CLASS);
    assertThat(myStage.getCaptureSelection().getSelectedClassSet()).isNull();
    assertThat(myClassifierView.getTree()).isNotNull();

    JTree classifierTree = myClassifierView.getTree();
    Object root = classifierTree.getModel().getRoot();
    assertThat(root).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode<?>)root).getAdapter()).isInstanceOf(HeapSet.class);
    //noinspection unchecked
    MemoryObjectTreeNode<ClassifierSet> rootNode = (MemoryObjectTreeNode<ClassifierSet>)root;

    //noinspection unchecked
    List<MemoryObjectTreeNode<ClassifierSet>> childrenOfRoot = rootNode.getChildren();
    assertThat(childrenOfRoot.size()).isEqualTo(3);
    classifierTree.setSelectionPath(new TreePath(new Object[]{root, childrenOfRoot.get(0)}));
    MemoryObjectTreeNode<ClassifierSet> selectedClassNode = childrenOfRoot.get(0);
    assertThat(myStage.getCaptureSelection().getSelectedClassSet()).isEqualTo(selectedClassNode.getAdapter());
    assertThat(classifierTree.getSelectionPath().getLastPathComponent()).isEqualTo(selectedClassNode);

    // Check that after changing to ARRANGE_BY_PACKAGE, the originally selected item is reselected.
    myStage.getCaptureSelection().setClassGrouping(ARRANGE_BY_PACKAGE);

    TableColumnModel tableColumnModel = myClassifierView.getTableColumnModel();
    assertThat(tableColumnModel.getColumn(0).getHeaderValue()).isEqualTo("Package Name");

    root = classifierTree.getModel().getRoot();
    assertThat(root).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode<?>)root).getAdapter()).isInstanceOf(HeapSet.class);
    //noinspection unchecked
    rootNode = (MemoryObjectTreeNode<ClassifierSet>)root;

    TreePath selectionPath = classifierTree.getSelectionPath();
    assertThat(selectionPath).isNotNull();
    Object reselected = selectionPath.getLastPathComponent();
    assertThat(reselected).isNotNull();
    assertThat(reselected).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode<?>)reselected).getAdapter()).isInstanceOf(ClassSet.class);
    //noinspection unchecked
    Set<InstanceObject> instances = (((MemoryObjectTreeNode<ClassSet>)reselected).getAdapter()).getInstancesStream().collect(Collectors.toSet());
    assertThat(selectedClassNode.getAdapter().isSupersetOf(instances)).isTrue();

    // Clear the selection from the model. The Tree's selection should get cleared as well.
    myStage.getCaptureSelection().selectClassSet(null);
    assertThat(classifierTree.getSelectionPath()).isNull();

    // Try selecting a package -- this should not result in any changes to the state.
    MemoryObjectTreeNode<? extends ClassifierSet> comPackage = findChildWithName(rootNode, "com");
    ClassSet selectedClass = myStage.getCaptureSelection().getSelectedClassSet();
    classifierTree.setSelectionPath(new TreePath(new Object[]{root, comPackage}));
    assertThat(myStage.getCaptureSelection().getSelectedClassSet()).isEqualTo(selectedClass);
  }

  @Test
  public void testCsvExporting() {
    final String CLASS_NAME_0 = "com.android.studio.Foo";
    final String CLASS_NAME_1 = "com.google.Bar";
    final String CLASS_NAME_2 = "com.android.studio.Baz";

    FakeCaptureObject captureObject = new FakeCaptureObject.Builder()
      .removeClassifierAttribute(CaptureObject.ClassifierAttribute.MODULE)
      .build();
    InstanceObject instanceFoo0 =
      new FakeInstanceObject.Builder(captureObject, 0, CLASS_NAME_0).setName("instanceFoo0").setDepth(1).setShallowSize(2)
        .setRetainedSize(3)
        .build();
    InstanceObject instanceFoo1 =
      new FakeInstanceObject.Builder(captureObject, 0, CLASS_NAME_0).setName("instanceFoo1").setDepth(2).setShallowSize(2)
        .setRetainedSize(3)
        .build();
    InstanceObject instanceFoo2 =
      new FakeInstanceObject.Builder(captureObject, 0, CLASS_NAME_0).setName("instanceFoo2").setDepth(3).setShallowSize(2)
        .setRetainedSize(3)
        .build();
    InstanceObject instanceBar0 =
      new FakeInstanceObject.Builder(captureObject, 1, CLASS_NAME_1).setName("instanceBar0").setDepth(1).setShallowSize(2)
        .setRetainedSize(4)
        .build();
    InstanceObject instanceBaz0 =
      new FakeInstanceObject.Builder(captureObject, 2, CLASS_NAME_2).setName("instanceBaz0").setDepth(1).setShallowSize(2)
        .setRetainedSize(5)
        .build();
    InstanceObject instanceBaz1 =
      new FakeInstanceObject.Builder(captureObject, 2, CLASS_NAME_2).setName("instanceBaz1").setDepth(1).setShallowSize(2)
        .setRetainedSize(5)
        .build();
    InstanceObject instanceBaz2 =
      new FakeInstanceObject.Builder(captureObject, 2, CLASS_NAME_2).setName("instanceBaz2").setDepth(1).setShallowSize(2)
        .setRetainedSize(5)
        .build();
    InstanceObject instanceBaz3 =
      new FakeInstanceObject.Builder(captureObject, 2, CLASS_NAME_2).setName("instanceBaz3").setDepth(1).setShallowSize(2)
        .setRetainedSize(5)
        .build();
    Set<InstanceObject> instanceObjects = new HashSet<>(
      Arrays.asList(instanceFoo0, instanceFoo1, instanceFoo2, instanceBar0, instanceBaz0, instanceBaz1, instanceBaz2, instanceBaz3));
    captureObject.addInstanceObjects(instanceObjects);
    myStage
      .selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> captureObject)),
                             null);

    HeapSet heapSet = captureObject.getHeapSet(instanceFoo0.getHeapId());
    myStage.getCaptureSelection().selectHeapSet(heapSet);


    JTree classifierTree = myClassifierView.getTree();
    Object root = classifierTree.getModel().getRoot();
    //noinspection unchecked
    MemoryObjectTreeNode<ClassifierSet> rootNode = (MemoryObjectTreeNode<ClassifierSet>)root;

    //noinspection unchecked
    List<MemoryObjectTreeNode<ClassifierSet>> childrenOfRoot = rootNode.getChildren();
    assertThat(childrenOfRoot.size()).isEqualTo(3);
    classifierTree.setSelectionPath(new TreePath(new Object[]{root, childrenOfRoot.get(0)}));

    CsvExporter exporter = new CsvExporter(() -> classifierTree, () -> captureObject,
                                           myFakeIdeProfilerComponents, myStage.getStudioProfilers().getIdeServices());
    checkCsvOutput((CsvExporter.RunnableContextMenuItem)exporter.makeClassExportItem(), captureObject::getClassifierAttributes);
    checkCsvOutput((CsvExporter.RunnableContextMenuItem)exporter.makeInstanceExportItem(), captureObject::getInstanceAttributes);
  }

  private static void checkCsvOutput(CsvExporter.RunnableContextMenuItem exporter, Supplier<List<?>> attributes) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    exporter.exportEntries(out);
    String[] lines = out.toString().split("\n");
    assertThat(lines.length).isGreaterThan(0);
    for (String line: lines) {
      assertThat(line.split(",").length).isEqualTo(attributes.get().size());
    }
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
      new FakeInstanceObject.Builder(captureObject, 0, CLASS_NAME_0).setName("instanceFoo1").setAllocationStack(callstack1)
        .setDepth(2).setShallowSize(2).setRetainedSize(16).build();
    InstanceObject instance2 =
      new FakeInstanceObject.Builder(captureObject, 0, CLASS_NAME_0).setName("instanceFoo2").setAllocationStack(callstack1)
        .setDepth(2).setShallowSize(2).setRetainedSize(24).build();
    InstanceObject instance3 =
      new FakeInstanceObject.Builder(captureObject, 0, CLASS_NAME_0).setName("instanceFoo3").setAllocationStack(callstack1)
        .setDepth(2).setShallowSize(2).setRetainedSize(16).build();
    InstanceObject instance4 =
      new FakeInstanceObject.Builder(captureObject, 0, CLASS_NAME_0).setName("instanceFoo4").setAllocationStack(callstack2)
        .setDepth(2).setShallowSize(2).setRetainedSize(16).build();
    InstanceObject instance5 =
      new FakeInstanceObject.Builder(captureObject, 0, CLASS_NAME_0).setName("instanceFoo5").setAllocationStack(callstack2)
        .setDepth(2).setShallowSize(2).setRetainedSize(16).build();
    InstanceObject instance6 =
      new FakeInstanceObject.Builder(captureObject, 0, CLASS_NAME_0).setName("instanceFoo6").setAllocationStack(callstack3)
        .setDepth(2).setShallowSize(2).setRetainedSize(16).build();
    InstanceObject instance7 =
      new FakeInstanceObject.Builder(captureObject, 1, CLASS_NAME_1).setName("instanceBar7").setAllocationStack(callstack3)
        .setDepth(2).setShallowSize(2).setRetainedSize(16).build();
    InstanceObject instance8 =
      new FakeInstanceObject.Builder(captureObject, 2, CLASS_NAME_2).setName("instanceBar8").setDepth(0).setShallowSize(2)
        .setRetainedSize(8).build();
    Set<InstanceObject> instanceObjects =
      new HashSet<>(Arrays.asList(instance1, instance2, instance3, instance4, instance5, instance6, instance7, instance8));
    captureObject.addInstanceObjects(instanceObjects);
    myStage
      .selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> captureObject)),
                             null);

    HeapSet heapSet = captureObject.getHeapSet(CaptureObject.DEFAULT_HEAP_ID);
    assertThat(heapSet).isNotNull();
    myStage.getCaptureSelection().selectHeapSet(heapSet);

    assertThat(myStage.getCaptureSelection().getClassGrouping()).isEqualTo(ARRANGE_BY_CLASS);
    assertThat(myStage.getCaptureSelection().getSelectedClassSet()).isNull();

    JTree classifierTree = myClassifierView.getTree();
    assertThat(classifierTree).isNotNull();
    Object root = classifierTree.getModel().getRoot();
    assertThat(root).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode<?>)root).getAdapter()).isInstanceOf(HeapSet.class);
    //noinspection unchecked
    MemoryObjectTreeNode<ClassifierSet> rootNode = (MemoryObjectTreeNode<ClassifierSet>)root;
    assertThat(rootNode.getChildCount()).isEqualTo(3);

    MemoryObjectTreeNode<ClassSet> fooNode = findChildClassSetNodeWithClassName(rootNode, CLASS_NAME_0);
    assertThat(fooNode.getAdapter().hasStackInfo()).isTrue();
    MemoryObjectTreeNode<ClassSet> barNode = findChildClassSetNodeWithClassName(rootNode, CLASS_NAME_1);
    assertThat(barNode.getAdapter().hasStackInfo()).isTrue();
    MemoryObjectTreeNode<ClassSet> intNode = findChildClassSetNodeWithClassName(rootNode, CLASS_NAME_2);
    assertThat(intNode.getAdapter().hasStackInfo()).isFalse();

    myStage.getCaptureSelection().setClassGrouping(ARRANGE_BY_PACKAGE);

    TableColumnModel tableColumnModel = myClassifierView.getTableColumnModel();
    assertThat(tableColumnModel.getColumn(0).getHeaderValue()).isEqualTo("Package Name");

    root = classifierTree.getModel().getRoot();
    assertThat(root).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode<?>)root).getAdapter()).isInstanceOf(HeapSet.class);
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
      new FakeInstanceObject.Builder(captureObject, 0, CLASS_NAME_0).setName("instanceFoo1").setAllocationStack(callstack1)
        .setDepth(2).setShallowSize(2).setRetainedSize(16).build();
    InstanceObject instance2 =
      new FakeInstanceObject.Builder(captureObject, 0, CLASS_NAME_0).setName("instanceFoo2").setAllocationStack(callstack1)
        .setDepth(2).setShallowSize(2).setRetainedSize(24).build();
    InstanceObject instance3 =
      new FakeInstanceObject.Builder(captureObject, 0, CLASS_NAME_0).setName("instanceFoo3").setAllocationStack(callstack1)
        .setDepth(2).setShallowSize(2).setRetainedSize(16).build();
    InstanceObject instance4 =
      new FakeInstanceObject.Builder(captureObject, 0, CLASS_NAME_0).setName("instanceFoo4").setAllocationStack(callstack2)
        .setDepth(2).setShallowSize(2).setRetainedSize(16).build();
    InstanceObject instance5 =
      new FakeInstanceObject.Builder(captureObject, 0, CLASS_NAME_0).setName("instanceFoo5").setAllocationStack(callstack2)
        .setDepth(2).setShallowSize(2).setRetainedSize(16).build();
    InstanceObject instance6 =
      new FakeInstanceObject.Builder(captureObject, 0, CLASS_NAME_0).setName("instanceFoo6").setAllocationStack(callstack3)
        .setDepth(2).setShallowSize(2).setRetainedSize(16).build();
    InstanceObject instance7 =
      new FakeInstanceObject.Builder(captureObject, 1, CLASS_NAME_1).setName("instanceBar7").setAllocationStack(callstack3)
        .setDepth(2).setShallowSize(2).setRetainedSize(16).build();
    InstanceObject instance8 =
      new FakeInstanceObject.Builder(captureObject, 2, CLASS_NAME_2).setName("instanceBar8").setDepth(0).setShallowSize(2)
        .setRetainedSize(8).build();
    InstanceObject instance9 =
      new FakeInstanceObject.Builder(captureObject, 1, CLASS_NAME_1).setName("instanceBar9").setAllocationStack(callstack4)
        .setDepth(2).setShallowSize(2).setRetainedSize(16).build();
    Set<InstanceObject> instanceObjects =
      new HashSet<>(Arrays.asList(instance1, instance2, instance3, instance4, instance5, instance6, instance7, instance8, instance9));
    captureObject.addInstanceObjects(instanceObjects);
    myStage
      .selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> captureObject)),
                             null);

    HeapSet heapSet = captureObject.getHeapSet(CaptureObject.DEFAULT_HEAP_ID);
    assertThat(heapSet).isNotNull();
    myStage.getCaptureSelection().selectHeapSet(heapSet);

    assertThat(myStage.getCaptureSelection().getClassGrouping()).isEqualTo(ARRANGE_BY_CLASS);
    assertThat(myStage.getCaptureSelection().getSelectedClassSet()).isNull();

    TableColumnModel tableColumnModel = myClassifierView.getTableColumnModel();
    assertThat(tableColumnModel.getColumn(0).getHeaderValue()).isEqualTo("Class Name");

    JTree classifierTree = myClassifierView.getTree();
    assertThat(classifierTree).isNotNull();
    Object root = classifierTree.getModel().getRoot();
    assertThat(root).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode<?>)root).getAdapter()).isInstanceOf(HeapSet.class);
    //noinspection unchecked
    MemoryObjectTreeNode<ClassifierSet> rootNode = (MemoryObjectTreeNode<ClassifierSet>)root;
    assertThat(rootNode.getChildCount()).isEqualTo(3);

    myStage.getCaptureSelection().setClassGrouping(ARRANGE_BY_CALLSTACK);

    tableColumnModel = myClassifierView.getTableColumnModel();
    assertThat(tableColumnModel.getColumn(0).getHeaderValue()).isEqualTo("Callstack Name");

    root = classifierTree.getModel().getRoot();
    assertThat(root).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode<?>)root).getAdapter()).isInstanceOf(HeapSet.class);
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
    assertThat(noStackIntArrayClassSet.getDeltaAllocationCount()).isEqualTo(1);

    //noinspection unchecked
    MemoryObjectTreeNode<? extends ClassifierSet> nodeToSelect =
      findChildClassSetNodeWithClassName((MemoryObjectTreeNode<ClassifierSet>)methodSet4Node, CLASS_NAME_0);
    classifierTree.setSelectionPath(new TreePath(new Object[]{root, methodSet4Node, nodeToSelect}));
    myStage.getCaptureSelection().setClassGrouping(ARRANGE_BY_CLASS);

    tableColumnModel = myClassifierView.getTableColumnModel();
    assertThat(tableColumnModel.getColumn(0).getHeaderValue()).isEqualTo("Class Name");

    assertThat(rootNode.getChildCount()).isEqualTo(3);
    TreePath selectionPath = classifierTree.getSelectionPath();
    assertThat(selectionPath).isNotNull();
    Object selectedObject = selectionPath.getLastPathComponent();
    assertThat(selectedObject).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode<?>)selectedObject).getAdapter()).isInstanceOf(ClassSet.class);
    //noinspection unchecked
    Set<InstanceObject> instances = (nodeToSelect.getAdapter()).getInstancesStream().collect(Collectors.toSet());;
    assertThat(((MemoryObjectTreeNode<ClassSet>)selectedObject).getAdapter().isSupersetOf(instances)).isTrue();
  }

  @Test
  public void testNamingNodeForNativeCallStackSet() {
    final String ALLOCATION_FUNCTION = "Test Function";
    ColoredTreeCellRenderer renderer = myClassifierView.getNameColumnRenderer();
    MemoryObjectTreeNode<NativeCallStackSet> node = mock(MemoryObjectTreeNode.class);
    Mockito.when(node.getAdapter())
      .thenReturn(new NativeCallStackSet(AllocationStack.StackFrame.newBuilder().setMethodName(ALLOCATION_FUNCTION).build(), 0));
    CaptureObject captureObject = new FakeCaptureObject.Builder().build();
    myStage
      .selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> captureObject)),
                             null);
    myStage.getCaptureSelection().selectHeapSet(new FakeHeapSet());
    renderer.customizeCellRenderer(myClassifierView.getTree(), node, false, false, false, 0, false);
    assertThat(renderer.getFragmentTag(0)).isEqualTo(ALLOCATION_FUNCTION);
    assertThat(renderer.getIcon()).isEqualTo(StudioIcons.Profiler.Overlays.METHOD_STACK);
  }

  @Test
  public void testNamingNodeForNativeAllocationMethodSet() {
    final String ALLOCATION_FUNCTION = "Test Function";
    ColoredTreeCellRenderer renderer = myClassifierView.getNameColumnRenderer();
    MemoryObjectTreeNode<NativeAllocationMethodSet> node = mock(MemoryObjectTreeNode.class);
    Mockito.when(node.getAdapter()).thenReturn(new NativeAllocationMethodSet(ALLOCATION_FUNCTION));
    CaptureObject captureObject = new FakeCaptureObject.Builder().build();
    myStage
      .selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> captureObject)),
                             null);
    myStage.getCaptureSelection().selectHeapSet(new FakeHeapSet());
    renderer.customizeCellRenderer(myClassifierView.getTree(), node, false, false, false, 0, false);
    assertThat(renderer.getFragmentTag(0)).isEqualTo(ALLOCATION_FUNCTION);
    assertThat(renderer.getIcon()).isEqualTo(StudioIcons.Profiler.Overlays.ARRAY_STACK);
  }

  @Test
  public void navigationTest() {
    final String TEST_CLASS_NAME = "com.Foo";

    FakeCaptureObject captureObject = new FakeCaptureObject.Builder().build();
    InstanceObject instance1 =
      new FakeInstanceObject.Builder(captureObject, 1, TEST_CLASS_NAME).setName("instanceFoo1").setDepth(0)
        .setShallowSize(0).setRetainedSize(0).build();
    Set<InstanceObject> instanceObjects = new HashSet<>(Collections.singleton(instance1));
    captureObject.addInstanceObjects(instanceObjects);
    myStage
      .selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> captureObject)),
                             null);

    HeapSet heapSet = captureObject.getHeapSet(CaptureObject.DEFAULT_HEAP_ID);
    assertThat(heapSet).isNotNull();
    myStage.getCaptureSelection().selectHeapSet(heapSet);

    assertThat(myStage.getCaptureSelection().getClassGrouping()).isEqualTo(ARRANGE_BY_CLASS);

    JTree classifierTree = myClassifierView.getTree();
    assertThat(classifierTree).isNotNull();

    Object root = classifierTree.getModel().getRoot();
    assertThat(root).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode<?>)root).getAdapter()).isInstanceOf(HeapSet.class);
    //noinspection unchecked
    MemoryObjectTreeNode<ClassifierSet> rootNode = (MemoryObjectTreeNode<ClassifierSet>)root;
    assertThat(rootNode.getChildCount()).isEqualTo(1);

    assertThat(myStage.getCaptureSelection().getSelectedClassSet()).isNull();
    myStage.getCaptureSelection().selectClassSet(findChildClassSetWithName(rootNode.getAdapter(), TEST_CLASS_NAME));
    myStage.getCaptureSelection().selectInstanceObject(instance1);

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
  public void nativeNavigationTest() {
    final String ALLOCATION_FUNCTION = "Test Function";
    final String FILE_NAME = "/some/file/path";
    final int LINE_NUMBER = 1234;
    AllocationStack.StackFrame stack = AllocationStack.StackFrame.newBuilder().setMethodName(ALLOCATION_FUNCTION)
      .setFileName(FILE_NAME)
      .setLineNumber(LINE_NUMBER).build();
    MemoryObjectTreeNode<NativeCallStackSet> node = mock(MemoryObjectTreeNode.class);
    Mockito.when(node.getAdapter()).thenReturn(new NativeCallStackSet(stack, 1));
    CaptureObject captureObject = new FakeCaptureObject.Builder().build();
    myStage.selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<>(new Object(), () -> captureObject)), null);
    myStage.getCaptureSelection().setClassGrouping(NATIVE_ARRANGE_BY_CALLSTACK);
    NativeMemoryHeapSet heapSet = new NativeMemoryHeapSet(captureObject);
    heapSet.addDeltaInstanceObject(new NativeAllocationInstanceObject(Memory.AllocationEvent.Allocation.getDefaultInstance(),
                                                                      new ClassDb.ClassEntry(0, 0, ""),
                                                                      AllocationStack.newBuilder().setFullStack(
                                                                        AllocationStack.StackFrameWrapper.newBuilder().addFrames(stack))
                                                                        .build(),
                                                                      1));
    myStage.getCaptureSelection().selectHeapSet(heapSet);

    JTree classifierTree = myClassifierView.getTree();
    assertThat(classifierTree).isNotNull();

    Object root = classifierTree.getModel().getRoot();
    assertThat(root).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode<?>)root).getAdapter()).isInstanceOf(HeapSet.class);
    //noinspection unchecked
    MemoryObjectTreeNode<ClassifierSet> rootNode = (MemoryObjectTreeNode<ClassifierSet>)root;
    assertThat(rootNode.getChildCount()).isEqualTo(1);

    assertThat(myStage.getCaptureSelection().getSelectedClassSet()).isNull();
    classifierTree.setSelectionRow(1);

    Supplier<CodeLocation> codeLocationSupplier = myFakeIdeProfilerComponents.getCodeLocationSupplier(classifierTree);

    assertThat(codeLocationSupplier).isNotNull();
    CodeLocation codeLocation = codeLocationSupplier.get();
    assertThat(codeLocation).isNotNull();
    assertThat(codeLocation.getMethodName()).isEqualTo(ALLOCATION_FUNCTION);
    assertThat(codeLocation.getFileName()).isEqualTo(FILE_NAME);
    assertThat(codeLocation.getLineNumber()).isEqualTo(LINE_NUMBER - 1); // Correct for the base 0
  }

  @Test
  public void testCorrectColumnsAndRendererContents() {
    final String CLASS_NAME_0 = "bar.def";
    final String CLASS_NAME_1 = "foo.abc";
    final String CLASS_NAME_2 = "ghi";

    FakeCaptureObject captureObject = new FakeCaptureObject.Builder().build();
    InstanceObject instance1 =
      new FakeInstanceObject.Builder(captureObject, 1, CLASS_NAME_0).setName("def").setDepth(7).setShallowSize(8).setRetainedSize(9)
        .build();
    InstanceObject instance2 =
      new FakeInstanceObject.Builder(captureObject, 2, CLASS_NAME_1).setName("abc").setDepth(4).setShallowSize(5).setRetainedSize(7)
        .build();
    InstanceObject instance3 =
      new FakeInstanceObject.Builder(captureObject, 3, CLASS_NAME_2).setName("ghi").setDepth(1).setShallowSize(2).setRetainedSize(3)
        .build();
    captureObject.addInstanceObjects(new HashSet<>(Arrays.asList(instance1, instance2, instance3)));
    myStage
      .selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> captureObject)),
                             null);

    HeapSet heapSet = captureObject.getHeapSet(CaptureObject.DEFAULT_HEAP_ID);
    assertThat(heapSet).isNotNull();
    myStage.getCaptureSelection().selectHeapSet(heapSet);

    JTree tree = myClassifierView.getTree();
    assertThat(tree).isNotNull();
    JScrollPane columnTreePane = (JScrollPane)myClassifierView.getColumnTree().getComponent(0);
    assertThat(columnTreePane).isNotNull();
    ColumnTreeTestInfo treeInfo = new ColumnTreeTestInfo(tree, columnTreePane);
    treeInfo
      .verifyColumnHeaders("Class Name", "Allocations", "Deallocations", "Total Count", "Native Size", "Shallow Size", "Retained Size",
                           "Allocations Size", "Deallocations Size", "Remaining Size", "Module Name", "Shallow Size Change");

    Object root = tree.getModel().getRoot();
    assertThat(root).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode<?>)root).getAdapter()).isInstanceOf(HeapSet.class);
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
      treeInfo.verifyRendererValues(node,
                                    new String[]{classSet.getClassEntry().getSimpleClassName(),
                                      classSet.getClassEntry().getPackageName().isEmpty()
                                      ? null
                                      : " (" + classSet.getClassEntry().getPackageName() + ")"},
                                    new String[]{Integer.toString(classSet.getDeltaAllocationCount())},
                                    new String[]{Integer.toString(classSet.getDeltaDeallocationCount())},
                                    new String[]{Integer.toString(classSet.getTotalObjectCount())},
                                    new String[]{Long.toString(classSet.getTotalNativeSize())},
                                    new String[]{Long.toString(classSet.getTotalShallowSize())},
                                    new String[]{Long.toString(classSet.getTotalRetainedSize())},
                                    new String[]{Long.toString(classSet.getAllocationSize())},
                                    new String[]{Long.toString(classSet.getDeallocationSize())},
                                    new String[]{Long.toString(classSet.getTotalRemainingSize())},
                                    new String[]{null},
                                    new String[]{Long.toString(classSet.getDeltaShallowSize())});
    }
  }

  @Test
  public void testCaptureChangedListener() {
    final int captureStartTime = 0;
    Range selectionRange = myStage.getTimeline().getSelectionRange();
    // LiveAllocationCaptureObject assumes a valid, non-empty range.
    selectionRange.set(0, 0);

    LiveAllocationCaptureObject capture = new LiveAllocationCaptureObject(new ProfilerClient(myGrpcChannel.getChannel()),
                                                                          ProfilersTestData.SESSION_DATA,
                                                                          captureStartTime,
                                                                          MoreExecutors.newDirectExecutorService(),
                                                                          myStage);
    // Bypass the stage's selection logic and manually alter the range selection ourselves.
    // This avoids the interaction of the RangeSelectionModel triggering the stage to select CaptureData based on AllocationInfosDataSeries
    myStage.getRangeSelectionModel().clearListeners();
    myStage.selectCaptureDuration(new CaptureDurationData<>(Long.MAX_VALUE, true, true, new CaptureEntry<>(capture, () -> capture)),
                                  MoreExecutors.directExecutor());
    myStage.getCaptureSelection().selectHeapSet(capture.getHeapSet(CaptureObject.DEFAULT_HEAP_ID));
    // Changed to group by package so we can test nested node cases.
    myStage.getCaptureSelection().setClassGrouping(ARRANGE_BY_PACKAGE);

    JTree tree = myClassifierView.getTree();
    assertThat(tree).isNotNull();
    JScrollPane columnTreePane = (JScrollPane)myClassifierView.getColumnTree().getComponent(0);
    assertThat(columnTreePane).isNotNull();
    ColumnTreeTestInfo treeInfo = new ColumnTreeTestInfo(tree, columnTreePane);

    Object root = tree.getModel().getRoot();
    assertThat(root).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode<?>)root).getAdapter()).isInstanceOf(HeapSet.class);
    //noinspection unchecked
    MemoryObjectTreeNode<ClassifierSet> rootNode = (MemoryObjectTreeNode<ClassifierSet>)root;
    assertThat(rootNode.getChildCount()).isEqualTo(0);

    // Initial selection from 0 to 4
    selectionRange.set(captureStartTime, captureStartTime + TimeUnit.SECONDS.toMicros(4));
    Queue<MemoryObjectTreeNodeTestData> expected_0_to_4 = new LinkedList<>();
    expected_0_to_4.add(new MemoryObjectTreeNodeTestData(0, "default heap", 4, 2, 2, 2, 2, 2));
    expected_0_to_4.add(new MemoryObjectTreeNodeTestData(1, "This", 2, 1, 1, 1, 2, 1));
    expected_0_to_4.add(new MemoryObjectTreeNodeTestData(2, "Is", 1, 1, 0, 0, 1));
    expected_0_to_4.add(new MemoryObjectTreeNodeTestData(3, "Foo", 1, 1, 0, 0, 0));
    expected_0_to_4.add(new MemoryObjectTreeNodeTestData(2, "Also", 1, 0, 1, 1, 1, 1));
    expected_0_to_4.add(new MemoryObjectTreeNodeTestData(3, "Foo", 1, 0, 1, 1, 0, 1));
    expected_0_to_4.add(new MemoryObjectTreeNodeTestData(1, "That", 2, 1, 1, 1, 2, 1));
    expected_0_to_4.add(new MemoryObjectTreeNodeTestData(2, "Is", 1, 1, 0, 0, 1));
    expected_0_to_4.add(new MemoryObjectTreeNodeTestData(3, "Bar", 1, 1, 0, 0, 0));
    expected_0_to_4.add(new MemoryObjectTreeNodeTestData(2, "Also", 1, 0, 1, 1, 1, 1));
    expected_0_to_4.add(new MemoryObjectTreeNodeTestData(3, "Bar", 1, 0, 1, 1, 0, 1));
    verifyLiveAllocRenderResult(treeInfo, rootNode, expected_0_to_4, 0);

    // Expand right to 8
    selectionRange.set(captureStartTime, captureStartTime + TimeUnit.SECONDS.toMicros(8));
    Queue<MemoryObjectTreeNodeTestData> expected_0_to_8 = new LinkedList<>();
    expected_0_to_8.add(new MemoryObjectTreeNodeTestData(0, "default heap", 8, 6, 2, 2, 2, 2));
    expected_0_to_8.add(new MemoryObjectTreeNodeTestData(1, "This", 4, 3, 1, 1, 2, 1));
    expected_0_to_8.add(new MemoryObjectTreeNodeTestData(2, "Is", 2, 2, 0, 0, 1));
    expected_0_to_8.add(new MemoryObjectTreeNodeTestData(3, "Foo", 2, 2, 0, 0, 0));
    expected_0_to_8.add(new MemoryObjectTreeNodeTestData(2, "Also", 2, 1, 1, 1, 1, 1));
    expected_0_to_8.add(new MemoryObjectTreeNodeTestData(3, "Foo", 2, 1, 1, 1, 0, 1));
    expected_0_to_8.add(new MemoryObjectTreeNodeTestData(1, "That", 4, 3, 1, 1, 2, 1));
    expected_0_to_8.add(new MemoryObjectTreeNodeTestData(2, "Is", 2, 2, 0, 0, 1));
    expected_0_to_8.add(new MemoryObjectTreeNodeTestData(3, "Bar", 2, 2, 0, 0, 0));
    expected_0_to_8.add(new MemoryObjectTreeNodeTestData(2, "Also", 2, 1, 1, 1, 1, 1));
    expected_0_to_8.add(new MemoryObjectTreeNodeTestData(3, "Bar", 2, 1, 1, 1, 0, 1));
    verifyLiveAllocRenderResult(treeInfo, rootNode, expected_0_to_8, 0);

    // Shrink left to 4
    selectionRange.setMin(captureStartTime + TimeUnit.SECONDS.toMicros(4));
    Queue<MemoryObjectTreeNodeTestData> expected_4_to_8 = new LinkedList<>();
    expected_4_to_8.add(new MemoryObjectTreeNodeTestData(0, "default heap", 4, 4, 2, 2, 2));
    expected_4_to_8.add(new MemoryObjectTreeNodeTestData(1, "This", 2, 2, 1, 1, 2));
    expected_4_to_8.add(new MemoryObjectTreeNodeTestData(2, "Is", 1, 1, 0, 0, 1));
    expected_4_to_8.add(new MemoryObjectTreeNodeTestData(3, "Foo", 1, 1, 0, 0, 0));
    expected_4_to_8.add(new MemoryObjectTreeNodeTestData(2, "Also", 1, 1, 1, 1, 1));
    expected_4_to_8.add(new MemoryObjectTreeNodeTestData(3, "Foo", 1, 1, 1, 1, 0));
    expected_4_to_8.add(new MemoryObjectTreeNodeTestData(1, "That", 2, 2, 1, 1, 2));
    expected_4_to_8.add(new MemoryObjectTreeNodeTestData(2, "Is", 1, 1, 0, 0, 1));
    expected_4_to_8.add(new MemoryObjectTreeNodeTestData(3, "Bar", 1, 1, 0, 0, 0));
    expected_4_to_8.add(new MemoryObjectTreeNodeTestData(2, "Also", 1, 1, 1, 1, 1));
    expected_4_to_8.add(new MemoryObjectTreeNodeTestData(3, "Bar", 1, 1, 1, 1, 0));
    verifyLiveAllocRenderResult(treeInfo, rootNode, expected_4_to_8, 0);

    // Shrink right to 4
    selectionRange.setMax(captureStartTime + TimeUnit.SECONDS.toMicros(4));
    Queue<MemoryObjectTreeNodeTestData> expected_4_to_4 = new LinkedList<>();
    expected_4_to_4.add(new MemoryObjectTreeNodeTestData(0, "default heap", 0, 0, 2, 2, 2));
    expected_4_to_4.add(new MemoryObjectTreeNodeTestData(1, "This", 0, 0, 1, 1, 1));
    expected_4_to_4.add(new MemoryObjectTreeNodeTestData(2, "Also", 0, 0, 1, 1, 1));
    expected_4_to_4.add(new MemoryObjectTreeNodeTestData(3, "Foo", 0, 0, 1, 1, 0));
    expected_4_to_4.add(new MemoryObjectTreeNodeTestData(1, "That", 0, 0, 1, 1, 1));
    expected_4_to_4.add(new MemoryObjectTreeNodeTestData(2, "Also", 0, 0, 1, 1, 1));
    expected_4_to_4.add(new MemoryObjectTreeNodeTestData(3, "Bar", 0, 0, 1, 1, 0));
    verifyLiveAllocRenderResult(treeInfo, rootNode, expected_4_to_4, 0);
  }

  @Test
  public void testCaptureFilter() {
    final int captureStartTime = 0;
    Range selectionRange = myStage.getTimeline().getSelectionRange();
    // LiveAllocationCaptureObject assumes a valid, non-empty range.
    selectionRange.set(0, 0);

    LiveAllocationCaptureObject capture = new LiveAllocationCaptureObject(new ProfilerClient(myGrpcChannel.getChannel()),
                                                                          ProfilersTestData.SESSION_DATA,
                                                                          captureStartTime,
                                                                          MoreExecutors.newDirectExecutorService(),
                                                                          myStage);
    // Bypass the stage's selection logic and manually alter the range selection ourselves.
    // This avoids the interaction of the RangeSelectionModel triggering the stage to select CaptureData based on AllocationInfosDataSeries
    myStage.getRangeSelectionModel().clearListeners();
    myStage.selectCaptureDuration(new CaptureDurationData<>(Long.MAX_VALUE, true, true, new CaptureEntry<>(capture, () -> capture)),
                                  MoreExecutors.directExecutor());
    myStage.getCaptureSelection().selectHeapSet(capture.getHeapSet(CaptureObject.DEFAULT_HEAP_ID));
    // Changed to group by package so we can test nested node cases.
    myStage.getCaptureSelection().setClassGrouping(ARRANGE_BY_PACKAGE);

    JTree tree = myClassifierView.getTree();
    assertThat(tree).isNotNull();
    JScrollPane columnTreePane = (JScrollPane)myClassifierView.getColumnTree().getComponent(0);
    assertThat(columnTreePane).isNotNull();
    ColumnTreeTestInfo treeInfo = new ColumnTreeTestInfo(tree, columnTreePane);

    Object root = tree.getModel().getRoot();
    assertThat(root).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode<?>)root).getAdapter()).isInstanceOf(HeapSet.class);
    //noinspection unchecked
    MemoryObjectTreeNode<ClassifierSet> rootNode = (MemoryObjectTreeNode<ClassifierSet>)root;
    assertThat(rootNode.getChildCount()).isEqualTo(0);

    // Initial selection from 0 to 4
    selectionRange.set(captureStartTime, captureStartTime + TimeUnit.SECONDS.toMicros(4));
    Queue<MemoryObjectTreeNodeTestData> expected_0_to_4 = new LinkedList<>();
    expected_0_to_4.add(new MemoryObjectTreeNodeTestData(0, "default heap", 4, 2, 2, 2, 2, 2));
    expected_0_to_4.add(new MemoryObjectTreeNodeTestData(1, "This", 2, 1, 1, 1, 2, 1));
    expected_0_to_4.add(new MemoryObjectTreeNodeTestData(2, "Is", 1, 1, 0, 0, 1));
    expected_0_to_4.add(new MemoryObjectTreeNodeTestData(3, "Foo", 1, 1, 0, 0, 0));
    expected_0_to_4.add(new MemoryObjectTreeNodeTestData(2, "Also", 1, 0, 1, 1, 1, 1));
    expected_0_to_4.add(new MemoryObjectTreeNodeTestData(3, "Foo", 1, 0, 1, 1, 0, 1));
    expected_0_to_4.add(new MemoryObjectTreeNodeTestData(1, "That", 2, 1, 1, 1, 2, 1));
    expected_0_to_4.add(new MemoryObjectTreeNodeTestData(2, "Is", 1, 1, 0, 0, 1));
    expected_0_to_4.add(new MemoryObjectTreeNodeTestData(3, "Bar", 1, 1, 0, 0, 0));
    expected_0_to_4.add(new MemoryObjectTreeNodeTestData(2, "Also", 1, 0, 1, 1, 1, 1));
    expected_0_to_4.add(new MemoryObjectTreeNodeTestData(3, "Bar", 1, 0, 1, 1, 0, 1));
    verifyLiveAllocRenderResult(treeInfo, rootNode, expected_0_to_4, 0);
    assertThat(myClassifierView.getClassifierPanel().getComponentCount()).isEqualTo(1);
    assertThat(myClassifierView.getClassifierPanel().getComponent(0)).isNotInstanceOf(InstructionsPanel.class);

    // Add a filter to remove That.is.Bar
    myStage.getCaptureSelection().getFilterHandler().setFilter(new Filter("Foo"));

    expected_0_to_4 = new LinkedList<>();
    expected_0_to_4.add(new MemoryObjectTreeNodeTestData(0, "default heap", 2, 1, 1, 1, 1, 5));
    expected_0_to_4.add(new MemoryObjectTreeNodeTestData(1, "This", 2, 1, 1, 1,2, 3));
    expected_0_to_4.add(new MemoryObjectTreeNodeTestData(2, "Is", 1, 1, 0, 0, 1));
    expected_0_to_4.add(new MemoryObjectTreeNodeTestData(3, "Foo", 1, 1, 0, 0, 0));
    expected_0_to_4.add(new MemoryObjectTreeNodeTestData(2, "Also", 1, 0, 1, 1, 1, 2));
    expected_0_to_4.add(new MemoryObjectTreeNodeTestData(3, "Foo", 1, 0, 1, 1, 0, 1));
    verifyLiveAllocRenderResult(treeInfo, rootNode, expected_0_to_4, 0);
    assertThat(myClassifierView.getClassifierPanel().getComponentCount()).isEqualTo(1);
    assertThat(myClassifierView.getClassifierPanel().getComponent(0)).isNotInstanceOf(InstructionsPanel.class);

    // Expand right to 8
    selectionRange.setMax(captureStartTime + TimeUnit.SECONDS.toMicros(8));
    Queue<MemoryObjectTreeNodeTestData> expected_0_to_8 = new LinkedList<>();
    expected_0_to_8.add(new MemoryObjectTreeNodeTestData(0, "default heap", 4, 3, 1, 1, 1, 11));
    expected_0_to_8.add(new MemoryObjectTreeNodeTestData(1, "This", 4, 3, 1, 1, 2, 6));
    expected_0_to_8.add(new MemoryObjectTreeNodeTestData(2, "Is", 2, 2, 0, 0, 1));
    expected_0_to_8.add(new MemoryObjectTreeNodeTestData(3, "Foo", 2, 2, 0, 0, 0));
    expected_0_to_8.add(new MemoryObjectTreeNodeTestData(2, "Also", 2, 1, 1, 1, 1, 3));
    expected_0_to_8.add(new MemoryObjectTreeNodeTestData(3, "Foo", 2, 1, 1, 1, 0, 1));
    verifyLiveAllocRenderResult(treeInfo, rootNode, expected_0_to_8, 0);
    assertThat(myClassifierView.getClassifierPanel().getComponentCount()).isEqualTo(1);
    assertThat(myClassifierView.getClassifierPanel().getComponent(0)).isNotInstanceOf(InstructionsPanel.class);

    // Shrink left to 4
    selectionRange.setMin(captureStartTime + TimeUnit.SECONDS.toMicros(4));
    Queue<MemoryObjectTreeNodeTestData> expected_4_to_8 = new LinkedList<>();
    expected_4_to_8.add(new MemoryObjectTreeNodeTestData(0, "default heap", 2, 2, 1, 1, 1, 16));
    expected_4_to_8.add(new MemoryObjectTreeNodeTestData(1, "This", 2, 2, 1, 1, 2, 7));
    expected_4_to_8.add(new MemoryObjectTreeNodeTestData(2, "Is", 1, 1, 0, 0, 1));
    expected_4_to_8.add(new MemoryObjectTreeNodeTestData(3, "Foo", 1, 1, 0, 0, 0));
    expected_4_to_8.add(new MemoryObjectTreeNodeTestData(2, "Also", 1, 1, 1, 1, 1, 2));
    expected_4_to_8.add(new MemoryObjectTreeNodeTestData(3, "Foo", 1, 1, 1, 1, 0));
    verifyLiveAllocRenderResult(treeInfo, rootNode, expected_4_to_8, 0);
    assertThat(myClassifierView.getClassifierPanel().getComponentCount()).isEqualTo(1);
    assertThat(myClassifierView.getClassifierPanel().getComponent(0)).isNotInstanceOf(InstructionsPanel.class);

    // Changed to group by callstack and update the rootNode
    myStage.getCaptureSelection().setClassGrouping(ARRANGE_BY_CALLSTACK);
    root = tree.getModel().getRoot();
    assertThat(root).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode<?>)root).getAdapter()).isInstanceOf(HeapSet.class);
    rootNode = (MemoryObjectTreeNode<ClassifierSet>)root;

    //// The 3, "Foo" filter should apply to the new grouping automatically
    expected_4_to_8 = new LinkedList<>();
    expected_4_to_8.add(new MemoryObjectTreeNodeTestData(0, "default heap", 4, 4, 2, 2, 4, 16));
    expected_4_to_8.add(new MemoryObjectTreeNodeTestData(1, "FooMethodB()", 1, 1, 0, 0, 1));
    expected_4_to_8.add(new MemoryObjectTreeNodeTestData(2, "BarMethodA()", 1, 1, 0, 0, 1));
    expected_4_to_8.add(new MemoryObjectTreeNodeTestData(3, "Bar", 1, 1, 0, 0, 0));
    expected_4_to_8.add(new MemoryObjectTreeNodeTestData(1, "FooMethodA()", 1, 1, 1, 1, 1));
    expected_4_to_8.add(new MemoryObjectTreeNodeTestData(2, "BarMethodB()", 1, 1, 1, 1, 1));
    expected_4_to_8.add(new MemoryObjectTreeNodeTestData(3, "Bar", 1, 1, 1, 1, 0));
    expected_4_to_8.add(new MemoryObjectTreeNodeTestData(1, "BarMethodB()", 1, 1, 1, 1, 1));
    expected_4_to_8.add(new MemoryObjectTreeNodeTestData(2, "FooMethodB()", 1, 1, 1, 1, 1));
    expected_4_to_8.add(new MemoryObjectTreeNodeTestData(3, "Foo", 1, 1, 1, 1, 0));
    expected_4_to_8.add(new MemoryObjectTreeNodeTestData(1, "BarMethodA()", 1, 1, 0, 0, 1));
    expected_4_to_8.add(new MemoryObjectTreeNodeTestData(2, "FooMethodA()", 1, 1, 0, 0, 1));
    expected_4_to_8.add(new MemoryObjectTreeNodeTestData(3, "Foo", 1, 1, 0, 0, 0));
    verifyLiveAllocRenderResult(treeInfo, rootNode, expected_4_to_8, 0);
    assertThat(myClassifierView.getClassifierPanel().getComponentCount()).isEqualTo(1);
    assertThat(myClassifierView.getClassifierPanel().getComponent(0)).isNotInstanceOf(InstructionsPanel.class);

    // Apply an invalid filter
    myStage.getCaptureSelection().getFilterHandler().setFilter(new Filter("BLAH"));
    Queue<MemoryObjectTreeNodeTestData> expect_none = new LinkedList<>();
    expect_none.add(new MemoryObjectTreeNodeTestData(0, "default heap", 0, 0, 0, 0, 0, 16));
    verifyLiveAllocRenderResult(treeInfo, rootNode, expect_none, 0);
    assertThat(myClassifierView.getClassifierPanel().getComponentCount()).isEqualTo(1);
    assertThat(myClassifierView.getClassifierPanel().getComponent(0)).isInstanceOf(InstructionsPanel.class);
  }

  @Test
  public void testSelectedClassSetAndInstance() {
    final int captureStartTime = 0;
    Range selectionRange = myStage.getTimeline().getSelectionRange();
    // LiveAllocationCaptureObject assumes a valid, non-empty range.
    selectionRange.set(0, 0);

    LiveAllocationCaptureObject capture = new LiveAllocationCaptureObject(new ProfilerClient(myGrpcChannel.getChannel()),
                                                                          ProfilersTestData.SESSION_DATA,
                                                                          captureStartTime,
                                                                          MoreExecutors.newDirectExecutorService(),
                                                                          myStage);
    // Bypass the stage's selection logic and manually alter the range selection ourselves.
    // This avoids the interaction of the RangeSelectionModel triggering the stage to select CaptureData based on AllocationInfosDataSeries
    myStage.getRangeSelectionModel().clearListeners();
    myStage.selectCaptureDuration(new CaptureDurationData<>(Long.MAX_VALUE, true, true, new CaptureEntry<>(capture, () -> capture)),
                                  MoreExecutors.directExecutor());
    myStage.getCaptureSelection().selectHeapSet(capture.getHeapSet(CaptureObject.DEFAULT_HEAP_ID));
    // Changed to group by package so we can test nested node cases.
    myStage.getCaptureSelection().setClassGrouping(ARRANGE_BY_PACKAGE);

    JTree tree = myClassifierView.getTree();
    assertThat(tree).isNotNull();
    JScrollPane columnTreePane = (JScrollPane)myClassifierView.getColumnTree().getComponent(0);
    assertThat(columnTreePane).isNotNull();
    ColumnTreeTestInfo treeInfo = new ColumnTreeTestInfo(tree, columnTreePane);

    Object root = tree.getModel().getRoot();
    assertThat(root).isInstanceOf(MemoryObjectTreeNode.class);
    assertThat(((MemoryObjectTreeNode<?>)root).getAdapter()).isInstanceOf(HeapSet.class);
    //noinspection unchecked
    MemoryObjectTreeNode<ClassifierSet> rootNode = (MemoryObjectTreeNode<ClassifierSet>)root;
    assertThat(rootNode.getChildCount()).isEqualTo(0);

    // Initial selection from 0 to 4
    selectionRange.set(captureStartTime, captureStartTime + TimeUnit.SECONDS.toMicros(4));
    Queue<MemoryObjectTreeNodeTestData> expected_0_to_4 = new LinkedList<>();
    expected_0_to_4.add(new MemoryObjectTreeNodeTestData(0, "default heap", 4, 2, 2, 2, 2, 2));
    expected_0_to_4.add(new MemoryObjectTreeNodeTestData(1, "This", 2, 1, 1, 1, 2, 1));
    expected_0_to_4.add(new MemoryObjectTreeNodeTestData(2, "Is", 1, 1, 0, 0, 1));
    expected_0_to_4.add(new MemoryObjectTreeNodeTestData(3, "Foo", 1, 1, 0, 0, 0));
    expected_0_to_4.add(new MemoryObjectTreeNodeTestData(2, "Also", 1, 0, 1, 1, 1, 1));
    expected_0_to_4.add(new MemoryObjectTreeNodeTestData(3, "Foo", 1, 0, 1, 1, 0, 1));
    expected_0_to_4.add(new MemoryObjectTreeNodeTestData(1, "That", 2, 1, 1, 1, 2, 1));
    expected_0_to_4.add(new MemoryObjectTreeNodeTestData(2, "Is", 1, 1, 0, 0, 1));
    expected_0_to_4.add(new MemoryObjectTreeNodeTestData(3, "Bar", 1, 1, 0, 0, 0));
    expected_0_to_4.add(new MemoryObjectTreeNodeTestData(2, "Also", 1, 0, 1, 1, 1, 1));
    expected_0_to_4.add(new MemoryObjectTreeNodeTestData(3, "Bar", 1, 0, 1, 1, 0, 1));
    verifyLiveAllocRenderResult(treeInfo, rootNode, expected_0_to_4, 0);

    MemoryObjectTreeNode<ClassifierSet> childThat = rootNode.getChildren().get(1);
    MemoryObjectTreeNode<ClassifierSet> childThatIs = childThat.getChildren().get(0);
    MemoryObjectTreeNode<ClassifierSet> childThatIsBar = childThatIs.getChildren().get(0);
    tree.setSelectionPath(new TreePath(new Object[]{root, childThat, childThatIs, childThatIsBar}));

    InstanceObject selectedInstance = childThatIsBar.getAdapter().getInstancesStream().collect(Collectors.toList()).get(0);
    myStage.getCaptureSelection().selectInstanceObject(selectedInstance);
    assertThat(myStage.getCaptureSelection().getSelectedInstanceObject()).isEqualTo(selectedInstance);

    assertThat(childThatIsBar.getAdapter()).isInstanceOf(ClassSet.class);
    assertThat(myStage.getCaptureSelection().getSelectedClassSet()).isEqualTo(childThatIsBar.getAdapter());
    assertThat(tree.getSelectionPath().getLastPathComponent()).isInstanceOf(MemoryObjectTreeNode.class);
    MemoryObjectTreeNode<ClassifierSet> selectedNode = (MemoryObjectTreeNode<ClassifierSet>)tree.getSelectionPath().getLastPathComponent();
    assertThat(selectedNode).isEqualTo(childThatIsBar);

    // Expand right to 8
    selectionRange.set(captureStartTime, captureStartTime + TimeUnit.SECONDS.toMicros(8));
    Queue<MemoryObjectTreeNodeTestData> expected_0_to_8 = new LinkedList<>();
    expected_0_to_8.add(new MemoryObjectTreeNodeTestData(0, "default heap", 8, 6, 2, 2, 2, 2));
    expected_0_to_8.add(new MemoryObjectTreeNodeTestData(1, "This", 4, 3, 1, 1, 2, 1));
    expected_0_to_8.add(new MemoryObjectTreeNodeTestData(2, "Is", 2, 2, 0, 0, 1));
    expected_0_to_8.add(new MemoryObjectTreeNodeTestData(3, "Foo", 2, 2, 0, 0, 0));
    expected_0_to_8.add(new MemoryObjectTreeNodeTestData(2, "Also", 2, 1, 1, 1, 1, 1));
    expected_0_to_8.add(new MemoryObjectTreeNodeTestData(3, "Foo", 2, 1, 1, 1, 0, 1));
    expected_0_to_8.add(new MemoryObjectTreeNodeTestData(1, "That", 4, 3, 1, 1, 2, 1));
    expected_0_to_8.add(new MemoryObjectTreeNodeTestData(2, "Is", 2, 2, 0, 0, 1));
    expected_0_to_8.add(new MemoryObjectTreeNodeTestData(3, "Bar", 2, 2, 0, 0, 0));
    expected_0_to_8.add(new MemoryObjectTreeNodeTestData(2, "Also", 2, 1, 1, 1, 1, 1));
    expected_0_to_8.add(new MemoryObjectTreeNodeTestData(3, "Bar", 2, 1, 1, 1, 0, 1));
    verifyLiveAllocRenderResult(treeInfo, rootNode, expected_0_to_8, 0);

    // Selected ClassSet should not change
    assertThat(tree.getSelectionPath().getLastPathComponent()).isInstanceOf(MemoryObjectTreeNode.class);
    selectedNode = (MemoryObjectTreeNode<ClassifierSet>)tree.getSelectionPath().getLastPathComponent();
    assertThat(selectedNode.getAdapter()).isEqualTo(childThatIsBar.getAdapter());
    assertThat(myStage.getCaptureSelection().getSelectedClassSet()).isEqualTo(childThatIsBar.getAdapter());
    assertThat(myStage.getCaptureSelection().getSelectedInstanceObject()).isEqualTo(selectedInstance);

    // Shrink left to 4
    selectionRange.setMin(captureStartTime + TimeUnit.SECONDS.toMicros(4));
    Queue<MemoryObjectTreeNodeTestData> expected_4_to_8 = new LinkedList<>();
    expected_4_to_8.add(new MemoryObjectTreeNodeTestData(0, "default heap", 4, 4, 2, 2, 2));
    expected_4_to_8.add(new MemoryObjectTreeNodeTestData(1, "This", 2, 2, 1, 1, 2));
    expected_4_to_8.add(new MemoryObjectTreeNodeTestData(2, "Is", 1, 1, 0, 0, 1));
    expected_4_to_8.add(new MemoryObjectTreeNodeTestData(3, "Foo", 1, 1, 0, 0, 0));
    expected_4_to_8.add(new MemoryObjectTreeNodeTestData(2, "Also", 1, 1, 1, 1, 1));
    expected_4_to_8.add(new MemoryObjectTreeNodeTestData(3, "Foo", 1, 1, 1, 1, 0));
    expected_4_to_8.add(new MemoryObjectTreeNodeTestData(1, "That", 2, 2, 1, 1, 2));
    expected_4_to_8.add(new MemoryObjectTreeNodeTestData(2, "Is", 1, 1, 0, 0, 1));
    expected_4_to_8.add(new MemoryObjectTreeNodeTestData(3, "Bar", 1, 1, 0, 0, 0));
    expected_4_to_8.add(new MemoryObjectTreeNodeTestData(2, "Also", 1, 1, 1, 1, 1));
    expected_4_to_8.add(new MemoryObjectTreeNodeTestData(3, "Bar", 1, 1, 1, 1, 0));
    verifyLiveAllocRenderResult(treeInfo, rootNode, expected_4_to_8, 0);

    // Selected ClassSet should not change
    assertThat(tree.getSelectionPath().getLastPathComponent()).isInstanceOf(MemoryObjectTreeNode.class);
    selectedNode = (MemoryObjectTreeNode<ClassifierSet>)tree.getSelectionPath().getLastPathComponent();
    assertThat(selectedNode.getAdapter()).isEqualTo(childThatIsBar.getAdapter());
    assertThat(myStage.getCaptureSelection().getSelectedClassSet()).isEqualTo(childThatIsBar.getAdapter());
    // If the old instance isn't part of the instances anymore, it shouldn't be selected!!
    InstanceObject reselectedInstance = capture.getInstances().filter(selectedInstance::equals).findAny().orElse(null);
    assertThat(myStage.getCaptureSelection().getSelectedInstanceObject()).isEqualTo(reselectedInstance);

    // Shrink to empty range and make sure class-set and instance are unselected
    double oldMin = selectionRange.getMin();
    double oldMax = selectionRange.getMax();
    selectionRange.setMin(0);
    selectionRange.setMax(0);
    assertThat(capture.getInstances().count()).isEqualTo(0);
    assertThat(myStage.getCaptureSelection().getSelectedClassSet()).isEqualTo(ClassSet.EMPTY_SET);
    assertThat(myStage.getCaptureSelection().getSelectedInstanceObject()).isNull();
    selectionRange.setMin(oldMin);
    selectionRange.setMax(oldMax);

    // Apply an invalid filter
    myStage.getCaptureSelection().getFilterHandler().setFilter(new Filter("BLAH"));
    // No path is selected and selected ClassSet is set to EMPTY_CLASS_SET
    assertThat(tree.getSelectionPath()).isNull();
    assertThat(myStage.getCaptureSelection().getSelectedClassSet()).isEqualTo(ClassSet.EMPTY_SET);
    assertThat(myStage.getCaptureSelection().getSelectedInstanceObject()).isNull();
  }

  @Test
  public void testClassifierHeaders() {
    CaptureObject captureObject = new FakeCaptureObject.Builder().build();
    myStage
      .selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> captureObject)),
                             null);
    myStage.getCaptureSelection().selectHeapSet(new FakeHeapSet());
    TableColumnModel tableColumnModel = myClassifierView.getTableColumnModel();
    myStage.getCaptureSelection().setClassGrouping(ARRANGE_BY_CLASS);
    assertThat(tableColumnModel.getColumn(0).getHeaderValue()).isEqualTo("Class Name");
    myStage.getCaptureSelection().setClassGrouping(ARRANGE_BY_CALLSTACK);
    assertThat(tableColumnModel.getColumn(0).getHeaderValue()).isEqualTo("Callstack Name");
    myStage.getCaptureSelection().setClassGrouping(ARRANGE_BY_PACKAGE);
    assertThat(tableColumnModel.getColumn(0).getHeaderValue()).isEqualTo("Package Name");
    myStage.getCaptureSelection().setClassGrouping(NATIVE_ARRANGE_BY_ALLOCATION_METHOD);
    assertThat(tableColumnModel.getColumn(0).getHeaderValue()).isEqualTo("Allocation function");
    myStage.getCaptureSelection().setClassGrouping(NATIVE_ARRANGE_BY_CALLSTACK);
    assertThat(tableColumnModel.getColumn(0).getHeaderValue()).isEqualTo("Callstack Name");
  }

  @Test
  public void smallestSuperSetNodeOfEmptyInstances() {
    FakeCaptureObject capture = new FakeCaptureObject.Builder().build();
    capture.addInstanceObjects(new HashSet<>(Collections.singletonList(new FakeInstanceObject.Builder(capture, 0, "obj").build())));
    myStage.selectCaptureDuration(new CaptureDurationData<>(1, false, false,
                                                            new CaptureEntry<>(new Object(), () -> capture)),
                                  null);
    myStage.getCaptureSelection().selectHeapSet(capture.getHeapSet(FakeCaptureObject.DEFAULT_HEAP_ID));
    myStage.getCaptureSelection().setClassGrouping(ARRANGE_BY_PACKAGE);
    MemoryObjectTreeNode<ClassifierSet> root = (MemoryObjectTreeNode<ClassifierSet>)myClassifierView.getTree().getModel().getRoot();
    assertThat(MemoryClassifierView.findSmallestSuperSetNode(root, ClassSet.EMPTY_SET)).isSameAs(root);
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

  private class FakeHeapSet extends HeapSet {
    public FakeHeapSet() {
      super(new FakeCaptureObject.Builder().build(), "Fake Heap", 0);
    }

    @NotNull
    @Override
    public Classifier createSubClassifier() {
      // Support all classifier sets returning a default.
      return ClassSet.createDefaultClassifier();
    }
  }

  private static boolean verifyMethodSet(@NotNull MethodSet methodSet, @NotNull String className, @NotNull String methodName) {
    return className.equals(methodSet.getClassName()) && methodName.equals(methodSet.getMethodName());
  }

  /**
   * Helper method to walk through the entire tree hierarchy and validate each node against the data stored in the expected queue.
   */
  private static boolean verifyLiveAllocRenderResult(@NotNull ColumnTreeTestInfo testInfo,
                                                     @NotNull MemoryObjectTreeNode node,
                                                     @NotNull Queue<MemoryObjectTreeNodeTestData> expected,
                                                     int currentDepth) {
    boolean done = false;
    boolean currentNodeVisited = false;
    boolean childrenVisited = false;
    MemoryObjectTreeNodeTestData testData;

    while ((testData = expected.peek()) != null && !done) {
      int depth = testData.depth;

      if (depth < currentDepth) {
        // We are done with the current sub-tree.
        done = true;
      }
      else if (depth > currentDepth) {
        // We need to go deeper...
        List<MemoryObjectTreeNode> children = node.getChildren();
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
        testInfo.verifyRendererValues(node,
                                      new String[]{testData.name},
                                      new String[]{NumberFormatter.formatInteger(testData.allocations)},
                                      new String[]{NumberFormatter.formatInteger(testData.deallocations)},
                                      new String[]{NumberFormatter.formatInteger(testData.total)},
                                      new String[]{NumberFormatter.formatInteger(testData.shallowSize)},
                                      new String[]{NumberFormatter.formatInteger(testData.shallowDiff)});

        // Children's size
        assertThat(node.getChildCount()).isEqualTo(testData.childrenSize);
        currentNodeVisited = true;
      }
    }

    assertThat(currentNodeVisited).isTrue();
    return currentNodeVisited;
  }

  // Auxiliary class to verify MemoryObjectTreeNodeTestData's internal data.
  private static class MemoryObjectTreeNodeTestData {
    int depth;
    String name;
    int allocations;
    int deallocations;
    int total;
    long shallowSize;
    int childrenSize;
    long shallowDiff;

    MemoryObjectTreeNodeTestData(int depth,
                                 String name,
                                 int allocations,
                                 int deallocations,
                                 int total,
                                 long shallowSize,
                                 int childrenSize) {
      this(depth, name, allocations, deallocations, total, shallowSize, childrenSize, 0);
    }

    MemoryObjectTreeNodeTestData(int depth,
                                 String name,
                                 int allocations,
                                 int deallocations,
                                 int total,
                                 long shallowSize,
                                 int childrenSize,
                                 long shallowDiff) {
      this.depth = depth;
      this.name = name;
      this.allocations = allocations;
      this.deallocations = deallocations;
      this.total = total;
      this.shallowSize = shallowSize;
      this.childrenSize = childrenSize;
      this.shallowDiff = shallowDiff;
    }
  }
}
