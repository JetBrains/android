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

import com.android.tools.adtui.common.ColumnTreeTestInfo;
import com.android.tools.profiler.proto.MemoryProfiler;
import com.android.tools.profilers.FakeGrpcChannel;
import com.android.tools.profilers.FakeIdeProfilerComponents;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.memory.MemoryProfilerTestBase.FakeCaptureObjectLoader;
import com.android.tools.profilers.memory.adapters.*;
import com.android.tools.profilers.stacktrace.ContextMenuItem;
import com.google.common.collect.ImmutableSet;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.android.tools.profilers.memory.adapters.ValueObject.ValueType.OBJECT;
import static org.junit.Assert.*;

public class MemoryInstanceDetailsViewTest {
  @Rule
  public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("MEMORY_TEST_CHANNEL", new FakeMemoryService());

  private MemoryProfilerStage myStage;
  private MemoryInstanceDetailsView myDetailsView;
  private FakeIdeProfilerComponents myFakeIdeProfilerComponents;
  private FakeCaptureObject myFakeCaptureObject;
  private StudioProfilers myProfilers;

  @Before
  public void setup() {
    myFakeIdeProfilerComponents = new FakeIdeProfilerComponents();
    FakeCaptureObjectLoader loader = new FakeCaptureObjectLoader();
    loader.setReturnImmediateFuture(true);
    myProfilers = new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices());
    myStage = new MemoryProfilerStage(myProfilers, loader);
    myDetailsView = new MemoryInstanceDetailsView(myStage, myFakeIdeProfilerComponents);
    myFakeCaptureObject = new FakeCaptureObject.Builder().setCaptureName("DUMMY_CAPTURE").build();
  }

  @After
  public void tearDown() throws Exception {
    myProfilers.stop();
  }

  @Test
  public void NullSelectionVisibilityTest() throws Exception {
    // Null selection
    Component component = myDetailsView.getComponent();
    assertNull(myStage.getSelectedInstanceObject());
    assertFalse(component.isVisible());
  }

  @Test
  public void NoCallstackOrReferenceVisibilityTest() throws Exception {
    // Selection with no callstack / reference information
    Component component = myDetailsView.getComponent();
    FakeInstanceObject fakeInstanceObject = new FakeInstanceObject.Builder(myFakeCaptureObject, "DUMMY_CLASS").build();
    myFakeCaptureObject.addInstanceObjects(ImmutableSet.of(fakeInstanceObject));
    myStage.selectCaptureDuration(
      new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> myFakeCaptureObject)),
      null);
    myStage.selectInstanceObject(fakeInstanceObject);
    assertFalse(component.isVisible());
  }

  @Test
  public void SelectionWithReferenceVisibilityTest() throws Exception {
    // Selection with reference information
    Component component = myDetailsView.getComponent();
    FakeInstanceObject referee = new FakeInstanceObject.Builder(myFakeCaptureObject, "REFEREE").setName("referee").build();
    FakeInstanceObject referer =
      new FakeInstanceObject.Builder(myFakeCaptureObject, "REFERER").setName("referee").setFields(Collections.singletonList("mField"))
        .build();
    referer.setFieldValue("mField", OBJECT, referee);
    myFakeCaptureObject.addInstanceObjects(ImmutableSet.of(referee, referer));
    myStage.selectCaptureDuration(
      new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> myFakeCaptureObject)),
      null);
    myStage.selectInstanceObject(referee);
    assertTrue(component.isVisible());
  }

  @Test
  public void SelectionWithCallstackVisibilityTest() throws Exception {
    // Selection with callstack information
    Component component = myDetailsView.getComponent();
    MemoryProfiler.AllocationStack stack = MemoryProfiler.AllocationStack.newBuilder().setFullStack(
      MemoryProfiler.AllocationStack.StackFrameWrapper.newBuilder().addFrames(
        MemoryProfiler.AllocationStack.StackFrame.newBuilder().setClassName("MockClass").setMethodName("MockMethod").setLineNumber(1)))
      .build();
    FakeInstanceObject instance = new FakeInstanceObject.Builder(myFakeCaptureObject, "DUMMY_CLASS").setAllocationStack(stack).build();
    myFakeCaptureObject.addInstanceObjects(ImmutableSet.of(instance));
    myStage.selectCaptureDuration(
      new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> myFakeCaptureObject)),
      null);
    myStage.selectInstanceObject(instance);
    assertTrue(component.isVisible());
  }

  /**
   * Test that the component accurately generates the instances JTree model based on the reference hierarchy
   * of an InstanceObject. We currently only populate up to one level of children at a time to avoid
   * building a unnecessarily large tree at the beginning - descendants are further populated upon expansion.
   * This test ensures such behavior as well.
   */
  @SuppressWarnings("unchecked")
  @Test
  public void buildTreeTest() throws Exception {
    // Setup mock reference hierarchy:
    // MockRoot
    // -> Ref1
    // --> Ref2
    // --> Ref3
    // ---> Ref4
    // -> Ref5
    FakeInstanceObject fakeInstance1 =
      new FakeInstanceObject.Builder(myFakeCaptureObject, "DUMMY_CLASS").setName("fake1").setFields(Collections.singletonList("mField"))
        .build();
    FakeInstanceObject fakeInstance2 =
      new FakeInstanceObject.Builder(myFakeCaptureObject, "DUMMY_CLASS").setName("fake2").setFields(Collections.singletonList("mField"))
        .build();
    FakeInstanceObject fakeInstance3 =
      new FakeInstanceObject.Builder(myFakeCaptureObject, "DUMMY_CLASS").setName("fake3").setFields(Collections.singletonList("mField"))
        .build();
    FakeInstanceObject fakeInstance4 =
      new FakeInstanceObject.Builder(myFakeCaptureObject, "DUMMY_CLASS").setName("fake4").setFields(Collections.singletonList("mField"))
        .build();
    FakeInstanceObject fakeInstance5 =
      new FakeInstanceObject.Builder(myFakeCaptureObject, "DUMMY_CLASS").setName("fake5").setFields(Collections.singletonList("mField"))
        .build();
    FakeInstanceObject fakeRootObject =
      new FakeInstanceObject.Builder(myFakeCaptureObject, "DUMMY_ROOT").setName("FakeRoot").build();

    fakeInstance1.setFieldValue("mField", OBJECT, fakeRootObject);
    fakeInstance2.setFieldValue("mField", OBJECT, fakeInstance1);
    fakeInstance3.setFieldValue("mField", OBJECT, fakeInstance1);
    fakeInstance4.setFieldValue("mField", OBJECT, fakeInstance3);
    fakeInstance5.setFieldValue("mField", OBJECT, fakeRootObject);

    myFakeCaptureObject
      .addInstanceObjects(ImmutableSet.of(fakeInstance1, fakeInstance2, fakeInstance3, fakeInstance4, fakeInstance5, fakeRootObject));

    myStage.selectCaptureDuration(
      new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> myFakeCaptureObject)),
      null);
    JTree tree = myDetailsView.buildTree(fakeRootObject);
    DefaultTreeModel treeModel = (DefaultTreeModel)tree.getModel();
    assertNotNull(treeModel);
    MemoryObjectTreeNode<ValueObject> treeRoot = (MemoryObjectTreeNode<ValueObject>)treeModel.getRoot();
    assertNotNull(treeRoot);

    // Check the initialize tree structure is correctly populated
    assertEquals(fakeRootObject, treeRoot.getAdapter());
    assertEquals(2, treeRoot.getChildCount());
    MemoryObjectTreeNode ref1 = treeRoot.getChildren().stream().filter(child -> child.getAdapter() instanceof ReferenceObject &&
                                                                                "fake1".equals(((ReferenceObject)child.getAdapter())
                                                                                                 .getReferenceInstance().getName()))
      .findFirst().orElse(null);
    assertNotNull(ref1);
    assertEquals(fakeInstance1, ((ReferenceObject)ref1.getAdapter()).getReferenceInstance());
    MemoryObjectTreeNode ref5 = treeRoot.getChildren().stream().filter(child -> child.getAdapter() instanceof ReferenceObject &&
                                                                                "fake5".equals(((ReferenceObject)child.getAdapter())
                                                                                                 .getReferenceInstance().getName()))
      .findFirst().orElse(null);
    assertNotNull(ref5);
    assertEquals(fakeInstance5, ((ReferenceObject)ref5.getAdapter()).getReferenceInstance());

    assertEquals(2, ref1.getChildCount());
    assertEquals(0, ref5.getChildCount());

    MemoryObjectTreeNode<ReferenceObject> ref2 = (MemoryObjectTreeNode<ReferenceObject>)ref1.getChildAt(0);
    MemoryObjectTreeNode<ReferenceObject> ref3 = (MemoryObjectTreeNode<ReferenceObject>)ref1.getChildAt(1);
    assertEquals(fakeInstance2, ref2.getAdapter().getReferenceInstance());
    assertEquals(fakeInstance3, ref3.getAdapter().getReferenceInstance());

    TreePath path = new TreePath(new Object[]{treeRoot, ref1});
    tree.expandPath(path);
    assertEquals(0, ref2.getChildCount());
    assertEquals(1, ref3.getChildCount());
    assertEquals(fakeInstance4, ((MemoryObjectTreeNode<ReferenceObject>)ref3.getChildAt(0)).getAdapter().getReferenceInstance());
  }

  @Test
  public void testGoToInstance() {
    FakeInstanceObject referer =
      new FakeInstanceObject.Builder(myFakeCaptureObject, "REFERER").setFields(Collections.singletonList("mField")).build();
    FakeInstanceObject referee = new FakeInstanceObject.Builder(myFakeCaptureObject, "REFEREE").build();
    referer.setFieldValue("mField", OBJECT, referee);
    myFakeCaptureObject.addInstanceObjects(ImmutableSet.of(referer, referee));

    myStage.selectCaptureDuration(
      new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> myFakeCaptureObject)),
      null);
    assertNotNull(myStage.getSelectedCapture());
    myStage
      .selectClassSet((ClassSet)myFakeCaptureObject.getHeapSet(FakeCaptureObject.DEFAULT_HEAP_ID).findContainingClassifierSet(referee));
    assertNotNull(myStage.getSelectedClassSet());
    myStage.selectInstanceObject(referee);
    JTree tree = myDetailsView.getReferenceTree();
    assertNotNull(tree);
    assertEquals(referee, myStage.getSelectedInstanceObject());

    // Check that the Go To Instance menu item exists but is disabled since no instance is selected
    List<ContextMenuItem> menus = myFakeIdeProfilerComponents.getComponentContextMenus(tree);
    assertNotNull(menus);
    assertEquals(1, menus.size());
    assertEquals("Go to Instance", menus.get(0).getText());
    assertFalse(menus.get(0).isEnabled());

    // Selects the referer node and triggers the context menu action to select the ref instance.
    TreeNode refNode = ((MemoryObjectTreeNode)tree.getModel().getRoot()).getChildAt(0);
    tree.setSelectionPath(new TreePath(refNode));
    assertTrue(menus.get(0).isEnabled());
    menus.get(0).run();
    assertEquals(myFakeCaptureObject.getHeapSet(FakeCaptureObject.DEFAULT_HEAP_ID), myStage.getSelectedHeapSet());
    assertNotNull(myStage.getSelectedClassSet());
    assertEquals(myStage.getSelectedClassSet(), myStage.getSelectedClassSet().findContainingClassifierSet(referer));
    assertEquals(referer, myStage.getSelectedInstanceObject());
  }

  @Test
  public void testCorrectColumnsAndRendererContents() {
    // TODO test more sophisticated cases (e.g. multiple field names, icons, etc)
    // Setup mock reference hierarchy:
    // fakeRootObject
    // -> fake1
    // -> fake2
    // -> fake3
    FakeInstanceObject fake1 =
      new FakeInstanceObject.Builder(myFakeCaptureObject, "REFERER").setName("Ref1").setFields(Collections.singletonList("mField"))
        .setDepth(1).setNativeSize(1).setShallowSize(2).setRetainedSize(3).build();
    FakeInstanceObject fake2 =
      new FakeInstanceObject.Builder(myFakeCaptureObject, "REFERER").setName("Ref2").setFields(Collections.singletonList("mField"))
        .setDepth(4).setNativeSize(2).setShallowSize(5).setRetainedSize(6).build();
    FakeInstanceObject fake3 =
      new FakeInstanceObject.Builder(myFakeCaptureObject, "REFERER").setName("Ref3").setFields(Collections.singletonList("mField"))
        .setDepth(7).setNativeSize(3).setShallowSize(8).setRetainedSize(9).build();
    List<FakeInstanceObject> references = Arrays.asList(fake1, fake2, fake3);

    FakeInstanceObject fakeRootObject = new FakeInstanceObject.Builder(myFakeCaptureObject, "REFEREE").setName("MockRoot").build();

    fake1.setFieldValue("mField", OBJECT, fakeRootObject);
    fake2.setFieldValue("mField", OBJECT, fakeRootObject);
    fake3.setFieldValue("mField", OBJECT, fakeRootObject);

    myFakeCaptureObject
      .addInstanceObjects(ImmutableSet.of(fake1, fake2, fake3, fakeRootObject));

    myStage.selectCaptureDuration(
      new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> myFakeCaptureObject)),
      null);
    myStage.selectInstanceObject(fakeRootObject);

    JTree tree = myDetailsView.getReferenceTree();
    assertNotNull(tree);
    JScrollPane columnTreePane = (JScrollPane)myDetailsView.getReferenceColumnTree();
    assertNotNull(columnTreePane);
    ColumnTreeTestInfo treeInfo = new ColumnTreeTestInfo(tree, columnTreePane);
    treeInfo.verifyColumnHeaders("Reference", "Alloc Time", "Dealloc Time", "Depth", "Native Size", "Shallow Size", "Retained Size");

    MemoryObjectTreeNode root = (MemoryObjectTreeNode)tree.getModel().getRoot();
    assertEquals(references.size(), root.getChildCount());
    for (int i = 0; i < root.getChildCount(); i++) {
      FakeInstanceObject ref = references.get(i);
      treeInfo.verifyRendererValues(root.getChildAt(i),
                                    new String[]{"mField in "},
                                    new String[]{""},
                                    new String[]{""},
                                    new String[]{Integer.toString(ref.getDepth())},
                                    new String[]{Long.toString(ref.getNativeSize())},
                                    new String[]{Integer.toString(ref.getShallowSize())},
                                    new String[]{Long.toString(ref.getRetainedSize())});
    }
  }
}