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

import static com.android.tools.profilers.memory.MemoryProfilerTestUtils.findDescendantClassSetNodeWithInstance;
import static com.android.tools.profilers.memory.MemoryProfilerTestUtils.getRootClassifierSet;
import static com.android.tools.profilers.memory.adapters.FakeCaptureObject.DEFAULT_HEAP_ID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.stdui.ContextMenuItem;
import com.android.tools.idea.codenavigation.CodeLocation;
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel;
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.profilers.FakeIdeProfilerComponents;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.FakeProfilerService;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.StudioProfilersView;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.android.tools.profilers.memory.adapters.FakeCaptureObject;
import com.android.tools.profilers.memory.adapters.FakeInstanceObject;
import com.android.tools.profilers.memory.adapters.InstanceObject;
import com.android.tools.profilers.memory.adapters.ValueObject;
import com.android.tools.profilers.memory.adapters.classifiers.ClassSet;
import com.google.common.collect.ImmutableSet;
import com.intellij.testFramework.ApplicationRule;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.JTree;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class MemoryNavigationTest {
  private final FakeTimer myTimer = new FakeTimer();
  @Rule public final FakeGrpcChannel myGrpcChannel =
    new FakeGrpcChannel("MemoryNavigationTestGrpc", new FakeTransportService(myTimer), new FakeProfilerService(myTimer),
                        new FakeMemoryService());
  @Rule public final ApplicationRule myApplicationRule = new ApplicationRule();

  private MainMemoryProfilerStage myStage;
  private MainMemoryProfilerStageView myStageView;
  private FakeIdeProfilerComponents myFakeIdeProfilerComponents;

  @Before
  public void before() {
    FakeIdeProfilerServices profilerServices = new FakeIdeProfilerServices();
    StudioProfilers profilers = new StudioProfilers(new ProfilerClient(myGrpcChannel.getChannel()), profilerServices, myTimer);
    myFakeIdeProfilerComponents = new FakeIdeProfilerComponents();
    StudioProfilersView profilersView = new StudioProfilersView(profilers, myFakeIdeProfilerComponents);

    FakeCaptureObjectLoader loader = new FakeCaptureObjectLoader();
    loader.setReturnImmediateFuture(true);
    myStage = new MainMemoryProfilerStage(profilers, loader);
    myStageView = new MainMemoryProfilerStageView(profilersView, myStage);
  }

  @Test
  public void testGoToInstance() {
    FakeCaptureObject fakeCaptureObject = new FakeCaptureObject.Builder().build();
    FakeInstanceObject instance = new FakeInstanceObject.Builder(fakeCaptureObject, 1, "SAMPLE_CLASS").setName("instance")
      .setFields(Collections.singletonList("SAMPLE_FIELD")).setDepth(1).setShallowSize(3).setRetainedSize(9).build();
    assertEquals(1, instance.getFieldCount());
    assertEquals(1, instance.getFields().size());
    InstanceObject fieldInstance =
      new FakeInstanceObject.Builder(fakeCaptureObject, 2, "SAMPLE_FIELD_CLASS").setName("fieldInstance").setDepth(2).setShallowSize(6)
        .setRetainedSize(6).build();
    instance.setFieldValue("SAMPLE_FIELD", ValueObject.ValueType.OBJECT, fieldInstance);
    fakeCaptureObject.addInstanceObjects(ImmutableSet.of(instance, fieldInstance));
    myStage.selectCaptureDuration(
      new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> fakeCaptureObject)), null);

    ClassSet instanceClassSet =
      findDescendantClassSetNodeWithInstance(getRootClassifierSet(myStageView.getClassifierView().getTree()).getAdapter(), instance);
    myStage.getCaptureSelection().selectClassSet(instanceClassSet);

    myStage.getCaptureSelection().selectInstanceObject(instance);
    JTree classSetTree = myStageView.getInstanceDetailsView().getFieldTree();
    assertNotNull(classSetTree);

    // Check that the Go To Instance menu item exists but is disabled since no instance is selected
    List<ContextMenuItem> menus = myFakeIdeProfilerComponents.getComponentContextMenus(classSetTree);
    assertNotNull(menus);
    assertEquals(1, menus.size());
    assertEquals("Go to Instance", menus.get(0).getText());
    assertFalse(menus.get(0).isEnabled());

    // TODO renable this once field selection is implemented
    /*
    // Expands the instance in the classSetTree to select the field
    TreeNode instanceNode = ((MemoryObjectTreeNode)classSetTree.getMethodModel().getRoot()).getChildAt(0);
    classSetTree.expandPath(new TreePath(instanceNode));
    TreeNode fieldNode = instanceNode.getChildAt(0);
    classSetTree.setSelectionPath(new TreePath(fieldNode));
    assertEquals(instanceClassSet, myStage.getSelectedClassSet());
    assertEquals(null, myStage.getSelectedInstanceObject());

    // Trigger the context menu action to go to the field's class
    assertTrue(menus.get(0).isEnabled());
    menus.get(0).run();
    assertEquals(fakeCaptureObject.getHeapSet(DEFAULT_HEAP_ID), myStage.getCaptureSelection().getSelectedHeapSet());
    assertEquals(instanceClassSet, myStage.getSelectedClassSet());
    assertEquals(fieldInstance, myStage.getSelectedInstanceObject());
    */
  }

  @Test
  public void navigationTest() {
    final String testClassName = "com.Foo";

    FakeCaptureObject fakeCaptureObject = new FakeCaptureObject.Builder().build();
    InstanceObject fakeInstance =
      new FakeInstanceObject.Builder(fakeCaptureObject, 1, testClassName).setName("TestInstance").setDepth(1).setShallowSize(2)
        .setRetainedSize(3).build();
    fakeCaptureObject.addInstanceObjects(ImmutableSet.of(fakeInstance));

    myStage.selectCaptureDuration(
      new CaptureDurationData<>(1, false, false, new CaptureEntry<CaptureObject>(new Object(), () -> fakeCaptureObject)), null);
    assertEquals(fakeCaptureObject.getHeapSet(DEFAULT_HEAP_ID), myStage.getCaptureSelection().getSelectedHeapSet());

    ClassSet instanceClassSet =
      findDescendantClassSetNodeWithInstance(getRootClassifierSet(myStageView.getClassifierView().getTree()).getAdapter(), fakeInstance);
    myStage.getCaptureSelection().selectClassSet(instanceClassSet);
    myStage.getCaptureSelection().selectInstanceObject(fakeInstance);

    JTree classifierTree = myStageView.getClassifierView().getTree();
    assertNotNull(classifierTree);
    Supplier<CodeLocation> codeLocationSupplier = myFakeIdeProfilerComponents.getCodeLocationSupplier(classifierTree);

    assertNotNull(codeLocationSupplier);
    CodeLocation codeLocation = codeLocationSupplier.get();
    assertNotNull(codeLocation);
    String codeLocationClassName = codeLocation.getClassName();
    assertEquals(testClassName, codeLocationClassName);
  }
}
