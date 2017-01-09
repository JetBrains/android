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

import com.android.tools.profilers.FakeGrpcChannel;
import com.android.tools.profilers.IdeProfilerServicesStub;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.memory.adapters.ClassObject;
import com.android.tools.profilers.memory.adapters.HeapObject;
import com.android.tools.profilers.memory.adapters.NamespaceObject;
import com.intellij.util.containers.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;

import javax.swing.tree.TreePath;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.android.tools.profilers.memory.MemoryProfilerConfiguration.ClassGrouping.GROUP_BY_PACKAGE;
import static com.android.tools.profilers.memory.MemoryProfilerConfiguration.ClassGrouping.NO_GROUPING;
import static org.junit.Assert.*;

public class MemoryClassViewTest {
  @Rule
  public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("MEMORY_TEST_CHANNEL", new FakeMemoryService());

  /**
   * Tests that the component generates the classes JTree model accurately based on the package hierarchy
   * of a HeapObject.
   */
  @Test
  public void buildClassesTreeTest() {
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), new IdeProfilerServicesStub());
    MemoryProfilerStage stage = new MemoryProfilerStage(profilers);
    MemoryClassView classView = new MemoryClassView(stage);

    // Setup fake package hierarchy
    ClassObject mockClass1 = MemoryProfilerTestBase.mockClassObject("int", Collections.emptyList());
    ClassObject mockClass2 = MemoryProfilerTestBase.mockClassObject("com.android.Foo", Collections.emptyList());
    ClassObject mockClass3 = MemoryProfilerTestBase.mockClassObject("com.google.Bar", Collections.emptyList());
    ClassObject mockClass4 = MemoryProfilerTestBase.mockClassObject("com.android.Foo2", Collections.emptyList());
    ClassObject mockClass5 = MemoryProfilerTestBase.mockClassObject("java.lang.Object", Collections.emptyList());
    ClassObject mockClass6 = MemoryProfilerTestBase.mockClassObject("long", Collections.emptyList());
    List<ClassObject> fakeClassObjects = Arrays.asList(mockClass1, mockClass2, mockClass3, mockClass4, mockClass5, mockClass6);
    HeapObject mockHeap = MemoryProfilerTestBase.mockHeapObject("Test", fakeClassObjects);
    stage.selectHeap(mockHeap);

    assertEquals(stage.getConfiguration().getClassGrouping(), NO_GROUPING);
    // Check if group by package is grouping as expected.
    stage.getConfiguration().setClassGrouping(GROUP_BY_PACKAGE);
    assertNotNull(classView.getTree());
    Object root = classView.getTree().getModel().getRoot();
    assertTrue(root instanceof MemoryObjectTreeNode);
    assertTrue(((MemoryObjectTreeNode)root).getAdapter() instanceof NamespaceObject);
    //noinspection unchecked
    ImmutableList<MemoryObjectTreeNode<NamespaceObject>> children = ((MemoryObjectTreeNode<NamespaceObject>)root).getChildren();

    assertEquals(1, children.stream().filter((child) -> child.getAdapter() == mockClass1).count());
    assertEquals(1, children.stream().filter((child) -> child.getAdapter() == mockClass6).count());

    MemoryObjectTreeNode<NamespaceObject> javaPackage = getSingularInList(children, (child) -> child.getAdapter().getName().equals("java"));
    MemoryObjectTreeNode<NamespaceObject> langPackage = javaPackage.getChildren().get(0);
    assertEquals(1, langPackage.getChildCount());
    assertEquals("lang", langPackage.getAdapter().getName());
    assertEquals(1, langPackage.getChildCount());
    assertEquals(mockClass5, langPackage.getChildren().get(0).getAdapter());

    MemoryObjectTreeNode<NamespaceObject> comPackage = getSingularInList(children, (child) -> child.getAdapter().getName().equals("com"));
    assertEquals(2, comPackage.getChildCount());

    MemoryObjectTreeNode<NamespaceObject> googlePackage =
      getSingularInList(comPackage.getChildren(), (child) -> child.getAdapter().getName().equals("google"));
    assertEquals(1, googlePackage.getChildCount());
    assertEquals(mockClass3, googlePackage.getChildren().get(0).getAdapter());

    MemoryObjectTreeNode<NamespaceObject> androidPackage =
      getSingularInList(comPackage.getChildren(), (child) -> child.getAdapter().getName().equals("android"));
    assertEquals(2, androidPackage.getChildCount());
    assertEquals(mockClass2, androidPackage.getChildren().get(0).getAdapter());
    assertEquals(mockClass4, androidPackage.getChildren().get(1).getAdapter());

    // Check if flat list is correct.
    stage.getConfiguration().setClassGrouping(NO_GROUPING);
    assertNotNull(classView.getTree());
    root = classView.getTree().getModel().getRoot();
    assertTrue(root instanceof MemoryObjectTreeNode);
    assertTrue(((MemoryObjectTreeNode)root).getAdapter() instanceof NamespaceObject);
    //noinspection unchecked
    children = ((MemoryObjectTreeNode<NamespaceObject>)root).getChildren();
    for (int i = 0; i < children.size(); i++) {
      ClassObject fake = fakeClassObjects.get(i);
      List<MemoryObjectTreeNode<NamespaceObject>> filteredList =
        children.stream().filter((child) -> child.getAdapter() == fake).collect(Collectors.toList());
      assertEquals(1, filteredList.size());
    }
  }

  /**
   * Tests selection on the class tree. This makes sure that selecting class nodes results in an actual selection being registered, while
   * selecting package nodes should do nothing.
   */
  @Test
  public void classTreeSelectionTest() {
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), new IdeProfilerServicesStub());
    MemoryProfilerStage stage = new MemoryProfilerStage(profilers);
    MemoryClassView classView = new MemoryClassView(stage);

    // Setup fake package hierarchy
    ClassObject fake1 = MemoryProfilerTestBase.mockClassObject("int", Collections.emptyList());
    ClassObject fake2 = MemoryProfilerTestBase.mockClassObject("com.Foo", Collections.emptyList());
    List<ClassObject> fakeClassObjects = Arrays.asList(fake1, fake2);
    HeapObject mockHeap = MemoryProfilerTestBase.mockHeapObject("Test", fakeClassObjects);
    stage.selectHeap(mockHeap);

    assertEquals(stage.getConfiguration().getClassGrouping(), NO_GROUPING);
    assertNull(stage.getSelectedClass());
    assertNotNull(classView.getTree());
    Object root = classView.getTree().getModel().getRoot();
    assertTrue(root instanceof MemoryObjectTreeNode);
    assertTrue(((MemoryObjectTreeNode)root).getAdapter() instanceof NamespaceObject);
    //noinspection unchecked
    ImmutableList<MemoryObjectTreeNode<NamespaceObject>> children = ((MemoryObjectTreeNode<NamespaceObject>)root).getChildren();
    assertEquals(2, children.size());
    classView.getTree().setSelectionPath(new TreePath(new Object[]{root, children.get(0)}));
    MemoryObjectTreeNode<NamespaceObject> selectedClassNode = children.get(0);
    assertEquals(selectedClassNode.getAdapter(), stage.getSelectedClass());
    assertEquals(selectedClassNode, classView.getTree().getSelectionPath().getLastPathComponent());

    stage.getConfiguration().setClassGrouping(GROUP_BY_PACKAGE);
    // Check that after changing to GROUP_BY_PACKAGE, the originally selected item is reselected.
    Object reselected = classView.getTree().getSelectionPath().getLastPathComponent();
    assertNotNull(reselected);
    assertTrue(reselected instanceof MemoryObjectTreeNode && ((MemoryObjectTreeNode)reselected).getAdapter() instanceof ClassObject);
    assertEquals(selectedClassNode.getAdapter(), ((MemoryObjectTreeNode)reselected).getAdapter());

    // Try selecting a package -- this should not result in any changes to the state.
    MemoryObjectTreeNode<NamespaceObject> comPackage = getSingularInList(children, (child) -> child.getAdapter().getName().equals("com"));
    ClassObject selectedClass = stage.getSelectedClass();
    classView.getTree().setSelectionPath(new TreePath(new Object[]{root, comPackage}));
    assertEquals(selectedClass, stage.getSelectedClass());
  }

  @Test
  public void classTreeNodeComparatorTest() {
    MemoryObjectTreeNode<NamespaceObject> package1 = new MemoryObjectTreeNode<>(MemoryProfilerTestBase.mockPackageObject("bar"));
    MemoryObjectTreeNode<NamespaceObject> package2 = new MemoryObjectTreeNode<>(MemoryProfilerTestBase.mockPackageObject("foo"));
    MemoryObjectTreeNode<ClassObject> class1 = new MemoryObjectTreeNode<>(MemoryProfilerTestBase.mockClassObject("abar", Collections.emptyList()));
    MemoryObjectTreeNode<ClassObject> class2 = new MemoryObjectTreeNode<>(MemoryProfilerTestBase.mockClassObject("zoo", Collections.emptyList()));

    Comparator<MemoryObjectTreeNode> comparator =
      MemoryClassView.createTreeNodeComparator((o1, o2) -> o1.getClassName().compareTo(o2.getClassName()));
    assertTrue(comparator.compare(package1, package2) < 0);
    assertTrue(comparator.compare(class1, class2) < 0);
    assertTrue(comparator.compare(package1, class1) < 0);
    assertTrue(comparator.compare(class1, package1) > 0);
  }

  private static <T> T getSingularInList(@NotNull List<T> list, @NotNull Predicate<T> predicate) {
    List<T> reducedList = list.stream().filter(predicate).collect(Collectors.toList());
    assertEquals(1, reducedList.size());
    return reducedList.get(0);
  }
}
