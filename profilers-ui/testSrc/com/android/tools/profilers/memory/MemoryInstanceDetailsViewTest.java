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

import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profiler.proto.MemoryServiceGrpc;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.TestGrpcChannel;
import com.android.tools.profilers.memory.adapters.ReferenceObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Rule;
import org.junit.Test;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class MemoryInstanceDetailsViewTest {
  @Rule
  public TestGrpcChannel<MemoryServiceMock>
    myGrpcChannel = new TestGrpcChannel<>("MEMORY_TEST_CHANNEL", new MemoryServiceMock());

  /**
   * Test that the component is visible based on whether the currently selected instance object has callstack/reference information.
   */
  @Test
  public void visibilityTest() throws Exception {
    StudioProfilers profilers = myGrpcChannel.getProfilers();
    MemoryProfilerStage stage = new MemoryProfilerStage(profilers);
    MemoryInstanceDetailsView detailsView = new MemoryInstanceDetailsView(stage);

    // Null selection
    Component component = detailsView.getComponent();
    assertNull(stage.getSelectedInstance());
    assertFalse(component.isVisible());

    // Selection with no callstack / reference information
    MockReferenceObject mockInstance = new MockReferenceObject("MockInstance");
    stage.selectInstance(mockInstance);
    assertFalse(component.isVisible());

    // Selection with reference information
    mockInstance = new MockReferenceObject("MockInstanceWithRef");
    mockInstance.addReference(new MockReferenceObject("MockReference"));
    stage.selectInstance(mockInstance);
    assertTrue(component.isVisible());

    // Selection with callstack information
    mockInstance = new MockReferenceObject("MockInstanceWithCallstack");
    mockInstance.addStack("MockClass", "MockMethod", 1);
    stage.selectInstance(mockInstance);
    assertTrue(component.isVisible());
  }

  /**
   * Test that the component generates the JTree model accurately based on the reference hierarchy
   * of an InstanceObject. We currently only populate up to two levels of children at a time to avoid
   * building a unnecessarily large tree at the beginning - descendants are further populated upon expansion.
   * This test ensures such behavior as well.
   */
  @Test
  public void buildTreeTest() throws Exception {
    StudioProfilers profilers = myGrpcChannel.getProfilers();
    MemoryProfilerStage stage = new MemoryProfilerStage(profilers);
    MemoryInstanceDetailsView detailsView = new MemoryInstanceDetailsView(stage);

    // Setup mock reference hierarchy:
    // MockRoot
    // -> Ref1
    // --> Ref2
    // --> Ref3
    // ---> Ref4
    // -> Ref5
    MockReferenceObject mockRootObject = new MockReferenceObject("MockRoot");
    MockReferenceObject mockRef1 = new MockReferenceObject("Ref1");
    MockReferenceObject mockRef2 = new MockReferenceObject("Ref2");
    MockReferenceObject mockRef3 = new MockReferenceObject("Ref3");
    MockReferenceObject mockRef4 = new MockReferenceObject("Ref4");
    MockReferenceObject mockRef5 = new MockReferenceObject("Ref5");
    mockRootObject.addReference(mockRef1);
    mockRootObject.addReference(mockRef5);
    mockRef1.addReference(mockRef2);
    mockRef1.addReference(mockRef3);
    mockRef3.addReference(mockRef4);

    JTree tree = detailsView.buildTree(mockRootObject);
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

  private static class MockReferenceObject implements ReferenceObject {
    @NotNull private final List<ReferenceObject> myReferrers = new ArrayList<>();
    @NotNull private final String myName;
    @NotNull private MemoryProfiler.AllocationStack myStack;

    public MockReferenceObject(@NotNull String name) {
      myName = name;
      myStack = MemoryProfiler.AllocationStack.newBuilder().build();
    }

    @NotNull
    @Override
    public String getName() {
      return myName;
    }

    @Nullable
    @Override
    public MemoryProfiler.AllocationStack getCallStack() {
      return myStack;
    }

    @NotNull
    @Override
    public List<ReferenceObject> getReferences() {
      return myReferrers;
    }

    @NotNull
    @Override
    public List<String> getReferenceFieldNames() {
      return Collections.EMPTY_LIST;
    }

    public void addReference(@NotNull ReferenceObject referrer) {
      myReferrers.add(referrer);
    }

    public void addStack(@NotNull String className, @NotNull String methodName, int lineNumber) {
      myStack = myStack.toBuilder().addStackFrames(MemoryProfiler.AllocationStack.StackFrame.newBuilder()
                                                     .setClassName(className).setMethodName(methodName).setLineNumber(lineNumber)).build();
    }
  }

  private static class MemoryServiceMock extends MemoryServiceGrpc.MemoryServiceImplBase {
  }
}