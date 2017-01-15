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

import com.android.tools.profilers.*;
import com.android.tools.profilers.memory.adapters.ClassObject;
import com.android.tools.profilers.memory.adapters.HeapObject;
import com.android.tools.profilers.memory.adapters.InstanceObject;
import com.android.tools.profilers.common.CodeLocation;
import com.intellij.util.containers.ImmutableList;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.Assert.*;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public class MemoryInstanceViewTest {
  private static final String MOCK_CLASS_NAME = "MockClass";

  private static final List<InstanceObject> INSTANCE_OBJECT_LIST = Arrays.asList(
    MemoryProfilerTestBase.mockInstanceObject(MOCK_CLASS_NAME, "MockInstance1"),
    MemoryProfilerTestBase.mockInstanceObject(MOCK_CLASS_NAME, "MockInstance2"),
    MemoryProfilerTestBase.mockInstanceObject(MOCK_CLASS_NAME, "MockInstance3"));

  @Rule public final FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("MemoryInstanceViewTestGrpc");

  private FakeIdeProfilerComponents myFakeIdeProfilerComponents;
  private MemoryProfilerStage myStage;
  private MemoryProfilerStageView myStageView;

  @Before
  public void before() {
    IdeProfilerServicesStub profilerServices = new IdeProfilerServicesStub();
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), profilerServices);

    myFakeIdeProfilerComponents = new FakeIdeProfilerComponents();
    StudioProfilersView profilersView = new StudioProfilersView(profilers, myFakeIdeProfilerComponents);

    myStage = spy(new MemoryProfilerStage(new StudioProfilers(myGrpcChannel.getClient(), profilerServices)));
    myStageView = new MemoryProfilerStageView(profilersView, myStage);
  }

  @Test
  public void testSelectClassToShowInInstanceView() {
    MemoryInstanceView view = new MemoryInstanceView(myStage, myFakeIdeProfilerComponents);

    myStage.selectClass(MemoryProfilerTestBase.mockClassObject("MockClass", INSTANCE_OBJECT_LIST));
    assertNotNull(view.getTree());
    assertTrue(view.getTree().getModel().getRoot() instanceof MemoryObjectTreeNode);
    MemoryObjectTreeNode root = (MemoryObjectTreeNode)view.getTree().getModel().getRoot();
    assertEquals(3, root.getChildCount());
    //noinspection unchecked
    ImmutableList<MemoryObjectTreeNode<InstanceObject>> children = root.getChildren();
    assertEquals(INSTANCE_OBJECT_LIST.get(0), children.get(0).getAdapter());
    assertEquals(INSTANCE_OBJECT_LIST.get(1), children.get(1).getAdapter());
    assertEquals(INSTANCE_OBJECT_LIST.get(2), children.get(2).getAdapter());
  }

  @Test
  public void testSelectInstanceToShowInInstanceView() {
    MemoryInstanceView view = new MemoryInstanceView(myStage, myFakeIdeProfilerComponents);
    myStage.selectClass(MemoryProfilerTestBase.mockClassObject("MockClass", INSTANCE_OBJECT_LIST));
    assertNotNull(view.getTree());
    assertEquals(0, view.getTree().getSelectionCount());
    myStage.selectInstance(INSTANCE_OBJECT_LIST.get(0));
    assertEquals(1, view.getTree().getSelectionCount());
  }

  @Test
  public void testResetInstanceView() {
    MemoryInstanceView view = new MemoryInstanceView(myStage, myFakeIdeProfilerComponents);
    myStage.selectClass(MemoryProfilerTestBase.mockClassObject("MockClass", INSTANCE_OBJECT_LIST));
    assertNotNull(view.getTree());
    assertTrue(view.getTree().getModel().getRoot() instanceof MemoryObjectTreeNode);
    view.reset();
    assertNull(view.getTree());
  }

  @Test
  public void navigationTest() {
    final String testClassName = "com.Foo";
    InstanceObject mockInstance = MemoryProfilerTestBase.mockInstanceObject(testClassName, "TestInstance");
    ClassObject mockClass = MemoryProfilerTestBase.mockClassObject(testClassName, Collections.singletonList(mockInstance));
    List<ClassObject> mockClassObjects = Collections.singletonList(mockClass);
    HeapObject mockHeap = MemoryProfilerTestBase.mockHeapObject("TestHeap", mockClassObjects);
    myStage.selectHeap(mockHeap);
    myStage.selectClass(mockClass);
    myStage.selectInstance(mockInstance);

    JTree instanceTree = myStageView.getClassView().getTree();
    assertNotNull(instanceTree);
    Supplier<CodeLocation> codeLocationSupplier = myFakeIdeProfilerComponents.getCodeLocationSupplier(instanceTree);

    assertNotNull(codeLocationSupplier);
    CodeLocation codeLocation = codeLocationSupplier.get();
    assertNotNull(codeLocation);
    String codeLocationClassName = codeLocation.getClassName();
    assertEquals(testClassName, codeLocationClassName);

    Runnable preNavigate = myFakeIdeProfilerComponents.getPreNavigate(instanceTree);
    assertNotNull(preNavigate);
    preNavigate.run();
    verify(myStage).setProfilerMode(ProfilerMode.NORMAL);
  }
}
