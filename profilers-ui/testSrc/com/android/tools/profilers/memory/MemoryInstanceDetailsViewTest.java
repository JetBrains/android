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

import static com.android.tools.profilers.memory.MemoryProfilerTestUtils.findChildWithPredicate;
import static com.android.tools.profilers.memory.adapters.ValueObject.ValueType.OBJECT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.adtui.common.ColumnTreeTestInfo;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.model.formatter.NumberFormatter;
import com.android.tools.adtui.stdui.ContextMenuItem;
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel;
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.profiler.proto.Memory;
import com.android.tools.profilers.FakeIdeProfilerComponents;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.android.tools.profilers.memory.adapters.FakeCaptureObject;
import com.android.tools.profilers.memory.adapters.FakeFieldObject;
import com.android.tools.profilers.memory.adapters.FakeInstanceObject;
import com.android.tools.profilers.memory.adapters.FieldObject;
import com.android.tools.profilers.memory.adapters.InstanceObject;
import com.android.tools.profilers.memory.adapters.MemoryObject;
import com.android.tools.profilers.memory.adapters.ReferenceObject;
import com.android.tools.profilers.memory.adapters.ValueObject;
import com.android.tools.profilers.memory.adapters.classifiers.ClassSet;
import com.google.common.collect.ImmutableSet;
import com.google.common.truth.Truth;
import com.intellij.util.containers.ContainerUtil;
import java.awt.Component;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function3;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class MemoryInstanceDetailsViewTest {
  private final FakeTimer myTimer = new FakeTimer();
  @Rule
  public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("MEMORY_TEST_CHANNEL", new FakeTransportService(myTimer));

  private MainMemoryProfilerStage myStage;
  private MemoryInstanceDetailsView myDetailsView;
  private FakeIdeProfilerComponents myFakeIdeProfilerComponents;
  private FakeCaptureObject myFakeCaptureObject;

  @Before
  public void setup() {
    myFakeIdeProfilerComponents = new FakeIdeProfilerComponents();
    FakeCaptureObjectLoader loader = new FakeCaptureObjectLoader();
    loader.setReturnImmediateFuture(true);
    myStage =
      new MainMemoryProfilerStage(new StudioProfilers(new ProfilerClient(myGrpcChannel.getChannel()), new FakeIdeProfilerServices(), myTimer),
                                  loader);
    myDetailsView = new MemoryInstanceDetailsView(myStage.getCaptureSelection(), myFakeIdeProfilerComponents, myStage.getTimeline());
    myFakeCaptureObject = new FakeCaptureObject.Builder().setCaptureName("SAMPLE_CAPTURE").build();
  }

  @Test
  public void NullSelectionVisibilityTest() throws Exception {
    // Null selection
    Component component = myDetailsView.getComponent();
    assertNull(myStage.getCaptureSelection().getSelectedInstanceObject());
    assertFalse(component.isVisible());
  }

  @Test
  public void NoCallstackOrReferenceVisibilityTest() throws Exception {
    // Selection with no callstack / reference information
    Component component = myDetailsView.getComponent();
    FakeInstanceObject fakeInstanceObject = new FakeInstanceObject.Builder(myFakeCaptureObject, 1, "SAMPLE_CLASS").build();
    myFakeCaptureObject.addInstanceObjects(ImmutableSet.of(fakeInstanceObject));
    myStage.selectCaptureDuration(
      new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> myFakeCaptureObject)),
      null);
    myStage.getCaptureSelection().selectInstanceObject(fakeInstanceObject);
    assertFalse(component.isVisible());
  }

  @Test
  public void SelectionWithReferenceVisibilityTest() throws Exception {
    // Selection with reference information
    Component component = myDetailsView.getComponent();
    FakeInstanceObject referee = new FakeInstanceObject.Builder(myFakeCaptureObject, 1, "REFEREE").setName("referee").build();
    FakeInstanceObject referer =
      new FakeInstanceObject.Builder(myFakeCaptureObject, 2, "REFERER").setName("referee").setFields(Collections.singletonList("mField"))
        .build();
    referer.setFieldValue("mField", OBJECT, referee);
    myFakeCaptureObject.addInstanceObjects(ImmutableSet.of(referee, referer));
    myStage.selectCaptureDuration(
      new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> myFakeCaptureObject)),
      null);
    myStage.getCaptureSelection().selectInstanceObject(referee);
    assertTrue(component.isVisible());
  }

  @Test
  public void SelectionWithCallstackVisibilityTest() throws Exception {
    // Selection with callstack information
    Component component = myDetailsView.getComponent();
    Memory.AllocationStack stack = Memory.AllocationStack.newBuilder().setFullStack(
      Memory.AllocationStack.StackFrameWrapper.newBuilder().addFrames(
        Memory.AllocationStack.StackFrame.newBuilder().setClassName("MockClass").setMethodName("MockMethod").setLineNumber(1)))
      .build();
    FakeInstanceObject instance = new FakeInstanceObject.Builder(myFakeCaptureObject, 1, "SAMPLE_CLASS").setAllocationStack(stack).build();
    myFakeCaptureObject.addInstanceObjects(ImmutableSet.of(instance));
    myStage.selectCaptureDuration(
      new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> myFakeCaptureObject)),
      null);
    myStage.getCaptureSelection().selectInstanceObject(instance);
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
      new FakeInstanceObject.Builder(myFakeCaptureObject, 1, "SAMPLE_CLASS").setName("fake1").setFields(Collections.singletonList("mField"))
        .build();
    FakeInstanceObject fakeInstance2 =
      new FakeInstanceObject.Builder(myFakeCaptureObject, 1, "SAMPLE_CLASS").setName("fake2").setFields(Collections.singletonList("mField"))
        .build();
    FakeInstanceObject fakeInstance3 =
      new FakeInstanceObject.Builder(myFakeCaptureObject, 1, "SAMPLE_CLASS").setName("fake3").setFields(Collections.singletonList("mField"))
        .build();
    FakeInstanceObject fakeInstance4 =
      new FakeInstanceObject.Builder(myFakeCaptureObject, 1, "SAMPLE_CLASS").setName("fake4").setFields(Collections.singletonList("mField"))
        .build();
    FakeInstanceObject fakeInstance5 =
      new FakeInstanceObject.Builder(myFakeCaptureObject, 1, "SAMPLE_CLASS").setName("fake5").setFields(Collections.singletonList("mField"))
        .build();
    FakeInstanceObject fakeRootObject =
      new FakeInstanceObject.Builder(myFakeCaptureObject, 2, "SAMPLE_ROOT").setName("FakeRoot").build();

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
    JTree tree = myDetailsView.buildReferenceTree(fakeRootObject);
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
      new FakeInstanceObject.Builder(myFakeCaptureObject, 1, "REFERER").setFields(Collections.singletonList("mField")).build();
    FakeInstanceObject referee = new FakeInstanceObject.Builder(myFakeCaptureObject, 2, "REFEREE").build();
    referer.setFieldValue("mField", OBJECT, referee);
    myFakeCaptureObject.addInstanceObjects(ImmutableSet.of(referer, referee));

    myStage.selectCaptureDuration(
      new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> myFakeCaptureObject)),
      null);
    assertNotNull(myStage.getCaptureSelection().getSelectedCapture());
    myStage.getCaptureSelection()
      .selectClassSet((ClassSet)myFakeCaptureObject.getHeapSet(FakeCaptureObject.DEFAULT_HEAP_ID).findContainingClassifierSet(referee));
    assertNotNull(myStage.getCaptureSelection().getSelectedClassSet());
    myStage.getCaptureSelection().selectInstanceObject(referee);
    JTree tree = myDetailsView.getReferenceTree();
    assertNotNull(tree);
    assertEquals(referee, myStage.getCaptureSelection().getSelectedInstanceObject());

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
    assertEquals(myFakeCaptureObject.getHeapSet(FakeCaptureObject.DEFAULT_HEAP_ID), myStage.getCaptureSelection().getSelectedHeapSet());
    assertNotNull(myStage.getCaptureSelection().getSelectedClassSet());
    assertEquals(myStage.getCaptureSelection().getSelectedClassSet(), myStage.getCaptureSelection().getSelectedClassSet().findContainingClassifierSet(referer));
    assertEquals(referer, myStage.getCaptureSelection().getSelectedInstanceObject());
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
      new FakeInstanceObject.Builder(myFakeCaptureObject, 1, "REFERER").setName("Ref1").setFields(Collections.singletonList("mField"))
        .setDepth(1).setNativeSize(1).setShallowSize(2).setRetainedSize(3).build();
    FakeInstanceObject fake2 =
      new FakeInstanceObject.Builder(myFakeCaptureObject, 1, "REFERER").setName("Ref2").setFields(Collections.singletonList("mField"))
        .setDepth(4).setNativeSize(2).setShallowSize(5).setRetainedSize(6).build();
    FakeInstanceObject fake3 =
      new FakeInstanceObject.Builder(myFakeCaptureObject, 1, "REFERER").setName("Ref3").setFields(Collections.singletonList("mField"))
        .setDepth(7).setNativeSize(3).setShallowSize(8).setRetainedSize(9).build();
    List<FakeInstanceObject> references = Arrays.asList(fake1, fake2, fake3);

    FakeInstanceObject fakeRootObject = new FakeInstanceObject.Builder(myFakeCaptureObject, 2, "REFEREE").setName("MockRoot").build();

    fake1.setFieldValue("mField", OBJECT, fakeRootObject);
    fake2.setFieldValue("mField", OBJECT, fakeRootObject);
    fake3.setFieldValue("mField", OBJECT, fakeRootObject);

    myFakeCaptureObject
      .addInstanceObjects(ImmutableSet.of(fake1, fake2, fake3, fakeRootObject));

    myStage.selectCaptureDuration(
      new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> myFakeCaptureObject)),
      null);
    myStage.getCaptureSelection().selectInstanceObject(fakeRootObject);

    JTree tree = myDetailsView.getReferenceTree();
    assertNotNull(tree);
    JScrollPane columnTreePane = (JScrollPane)myDetailsView.getReferenceColumnTree().getComponent(0);
    assertNotNull(columnTreePane);
    ColumnTreeTestInfo treeInfo = new ColumnTreeTestInfo(tree, columnTreePane);
    treeInfo.verifyColumnHeaders("Reference", "Alloc Time", "Dealloc Time", "Depth", "Native Size", "Shallow Size", "Retained Size");

    MemoryObjectTreeNode root = (MemoryObjectTreeNode)tree.getModel().getRoot();
    assertEquals(references.size(), root.getChildCount());
    for (int i = 0; i < root.getChildCount(); i++) {
      FakeInstanceObject ref = references.get(i);
      treeInfo.verifyRendererValues(root.getChildAt(i),
                                    new String[]{"mField in "},
                                    new String[]{"-"},
                                    new String[]{"-"},
                                    new String[]{NumberFormatter.formatInteger(ref.getDepth())},
                                    new String[]{NumberFormatter.formatInteger(ref.getNativeSize())},
                                    new String[]{NumberFormatter.formatInteger(ref.getShallowSize())},
                                    new String[]{NumberFormatter.formatInteger(ref.getRetainedSize())});
    }
  }

  @Test
  public void fieldsInitiallySortedByDefaultOrder() {
    // TODO test more sophisticated cases (e.g. multiple field names, icons, etc)
    // Setup mock field hierarchy:
    // fakeRootObject
    // -> fake1
    // -> fake2
    // -> fake3
    FakeInstanceObject fake1 =
      new FakeInstanceObject.Builder(myFakeCaptureObject, 1, "REFERER").setName("Obj1")
        .setDepth(1).setNativeSize(1).setShallowSize(2).setRetainedSize(3).build();
    FakeInstanceObject fake2 =
      new FakeInstanceObject.Builder(myFakeCaptureObject, 1, "REFERER").setName("Obj2")
        .setDepth(4).setNativeSize(2).setShallowSize(5).setRetainedSize(6).build();
    FakeInstanceObject fake3 =
      new FakeInstanceObject.Builder(myFakeCaptureObject, 1, "REFERER").setName("Obj3")
        .setDepth(7).setNativeSize(3).setShallowSize(8).setRetainedSize(9).build();

    FakeInstanceObject fakeRootObject = new FakeInstanceObject.Builder(myFakeCaptureObject, 2, "REFEREE")
      .setName("MockRoot")
      .setFields(Arrays.asList("field1", "field2", "field3"))
      .build();

    fakeRootObject.setFieldValue("field1", OBJECT, fake1);
    fakeRootObject.setFieldValue("field2", OBJECT, fake2);
    fakeRootObject.setFieldValue("field3", OBJECT, fake3);

    myFakeCaptureObject
      .addInstanceObjects(ImmutableSet.of(fake1, fake2, fake3, fakeRootObject));

    myStage.selectCaptureDuration(
      new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> myFakeCaptureObject)),
      null);
    myStage.getCaptureSelection().selectInstanceObject(fakeRootObject);

    JTree tree = myDetailsView.getFieldTree();
    assertNotNull(tree);

    MemoryObjectTreeNode<MemoryObject> root = (MemoryObjectTreeNode<MemoryObject>)tree.getModel().getRoot();
    List<InstanceObject> sortedChildren = Arrays.asList(fake1, fake2, fake3);
    // Default order is descending retained size as of when this test is written
    sortedChildren.sort(Comparator.comparingLong(InstanceObject::getRetainedSize).reversed());
    Truth.assertThat(ContainerUtil.map(root.getChildren(), v -> ((FakeFieldObject)v.getAdapter()).getValue()))
      .isEqualTo(sortedChildren);
  }

  @Test
  public void selectingNearestGCRootUpdatesReferenceTree() {
    FakeInstanceObject referer0 =
      new FakeInstanceObject.Builder(myFakeCaptureObject, 1, "REFERER")
        .setName("Ref0").setFields(Arrays.asList("r1", "r2"))
        .setDepth(0).setNativeSize(1).setShallowSize(2).setRetainedSize(3).build();

    FakeInstanceObject referer1 =
      new FakeInstanceObject.Builder(myFakeCaptureObject, 1, "REFERER")
        .setName("Ref1").setFields(Collections.singletonList("mField1"))
        .setDepth(1).setNativeSize(1).setShallowSize(2).setRetainedSize(3).build();
    FakeInstanceObject referer2 =
      new FakeInstanceObject.Builder(myFakeCaptureObject, 2, "REFERER")
        .setName("Ref2").setFields(Collections.singletonList("mField2"))
        .setDepth(1).setNativeSize(2).setShallowSize(5).setRetainedSize(6).build();
    FakeInstanceObject referer3 =
      new FakeInstanceObject.Builder(myFakeCaptureObject, 3, "REFERER")
        .setName("Ref3").setFields(Collections.singletonList("mField3"))
        .setDepth(7).setNativeSize(3).setShallowSize(8).setRetainedSize(9).build();

    FakeInstanceObject fakeRootObject = new FakeInstanceObject.Builder(myFakeCaptureObject, 2, "REFEREE")
      .setDepth(2)
      .setName("MockRoot").build();

    referer0.setFieldValue("r1", OBJECT, referer1);
    referer0.setFieldValue("r2", OBJECT, referer2);
    referer1.setFieldValue("mField1", OBJECT, fakeRootObject);
    referer2.setFieldValue("mField2", OBJECT, fakeRootObject);
    referer3.setFieldValue("mField3", OBJECT, fakeRootObject);

    myFakeCaptureObject
      .addInstanceObjects(ImmutableSet.of(referer1, referer2, referer3, fakeRootObject));

    myStage.selectCaptureDuration(
      new CaptureDurationData<>(1, false, false,
                                new CaptureEntry<CaptureObject>(new Object(), () -> myFakeCaptureObject)),
      null);
    myStage.getCaptureSelection().selectInstanceObject(fakeRootObject);

    // Before selection
    {
      Truth.assertThat(myDetailsView.getGCRootCheckBox().isSelected()).isFalse();
      ReferenceTreeNode root = (ReferenceTreeNode)myDetailsView.getReferenceTree().getModel().getRoot();
      Truth.assertThat(root.getAdapter()).isEqualTo(fakeRootObject);
      Truth.assertThat(root.myChildren.size()).isEqualTo(3);
    }


    // After selection
    {
      myDetailsView.getGCRootCheckBox().setSelected(true);

      NearestGCRootTreeNode root = (NearestGCRootTreeNode)myDetailsView.getReferenceTree().getModel().getRoot();
      Truth.assertThat(root.getAdapter()).isEqualTo(fakeRootObject);
      Truth.assertThat(root.myChildren.size()).isEqualTo(2);

      NearestGCRootTreeNode node1 = (NearestGCRootTreeNode)root.myChildren.get(0);
      Truth.assertThat(((ReferenceObject)node1.getAdapter()).getReferenceInstance()).isEqualTo(referer1);
      Truth.assertThat(node1.myChildren.size()).isEqualTo(1);

      NearestGCRootTreeNode node2 = (NearestGCRootTreeNode)root.myChildren.get(1);
      Truth.assertThat(((ReferenceObject)node2.getAdapter()).getReferenceInstance()).isEqualTo(referer2);
      Truth.assertThat(node2.myChildren).isEmpty(); // all but first child not automatically expanded
      node2.expandNode();
      Truth.assertThat(node2.myChildren.size()).isEqualTo(1);

      Arrays.asList(node1.myChildren.get(0), node2.myChildren.get(0)).forEach(node -> {
        NearestGCRootTreeNode gcRoot = (NearestGCRootTreeNode)node;
        Truth.assertThat(((ReferenceObject)gcRoot.getAdapter()).getReferenceInstance()).isEqualTo(referer0);
        Truth.assertThat(gcRoot.myChildren).isEmpty();
      });
    }
  }

  @Test
  public void testFieldSelection() {
    final long TEST_CLASS_ID = 1, TEST_FIELD_ID = 2;
    final String TEST_CLASS_NAME = "com.Foo";
    final String TEST_FIELD_NAME = "com.Field";

    FakeCaptureObject captureObject = new FakeCaptureObject.Builder().build();
    Function1<String, FakeInstanceObject> makeFieldInstance = name ->
      new FakeInstanceObject.Builder(captureObject, TEST_FIELD_ID, TEST_FIELD_NAME).setName(name).build();
    FakeInstanceObject instanceFooField = makeFieldInstance.invoke("instanceFooField");
    FakeInstanceObject instanceBarField = makeFieldInstance.invoke("instanceBarField");
    FakeFieldObject fieldFoo = new FakeFieldObject("fieldFoo", OBJECT, instanceFooField);
    FakeFieldObject fieldBar = new FakeFieldObject("fieldBar", OBJECT, instanceBarField);

    Function3<String, FieldObject, FakeInstanceObject, FakeInstanceObject> makeInstance = (name, field, fieldInstance) -> {
      FakeInstanceObject instance =
        new FakeInstanceObject.Builder(captureObject, TEST_CLASS_ID, TEST_CLASS_NAME).setName(name)
        .setFields(Collections.singletonList(field.getFieldName())).build();
      instance.setFieldValue(field.getFieldName(), field.getValueType(), fieldInstance);
      return instance;
    };
    FakeInstanceObject instanceFoo = makeInstance.invoke("instanceFoo", fieldFoo, instanceFooField);
    FakeInstanceObject instanceBar = makeInstance.invoke("instanceBar", fieldBar, instanceBarField);

    Set<InstanceObject> instanceObjects = new HashSet<>(Arrays.asList(instanceFoo, instanceBar, instanceFooField, instanceBarField));
    captureObject.addInstanceObjects(instanceObjects);
    myStage
      .selectCaptureDuration(new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> captureObject)),
                             null);

    myStage.getCaptureSelection().selectInstanceObject(instanceFoo);
    myStage.getCaptureSelection().selectFieldObjectPath(Collections.singletonList(fieldFoo));

    Truth.assertThat(myStage.getCaptureSelection().getSelectedInstanceObject()).isEqualTo(instanceFoo);
    Truth.assertThat(myStage.getCaptureSelection().getSelectedFieldObjectPath()).isEqualTo(Collections.singletonList(fieldFoo));

    JTree fieldTree = myDetailsView.getFieldTree();
    Truth.assertThat(fieldTree).isNotNull();
    findChildWithPredicate((MemoryObjectTreeNode<?>)fieldTree.getModel().getRoot(), field -> Objects.equals(field, fieldFoo));
  }
}