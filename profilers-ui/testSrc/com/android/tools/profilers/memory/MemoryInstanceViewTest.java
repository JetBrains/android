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
import com.android.tools.profilers.memory.adapters.InstanceObject;
import com.intellij.util.containers.ImmutableList;
import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MemoryInstanceViewTest {

  private static final List<InstanceObject> INSTANCE_OBJECT_LIST = Arrays.asList(
    MemoryProfilerTestBase.mockInstanceObject("MockInstance1"),
    MemoryProfilerTestBase.mockInstanceObject("MockInstance2"),
    MemoryProfilerTestBase.mockInstanceObject("MockInstance3"));

  @Rule public final FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("MemoryInstanceViewTestGrpc");

  @Test
  public void testSelectClassToShowInInstanceView() {
    MemoryProfilerStage stage = new MemoryProfilerStage(new StudioProfilers(myGrpcChannel.getClient(), new IdeProfilerServicesStub()));
    MemoryInstanceView view = new MemoryInstanceView(stage);
    stage.selectClass(MemoryProfilerTestBase.mockClassObject("MockClass", INSTANCE_OBJECT_LIST));
    assertTrue(view.getTree().getModel().getRoot() instanceof MemoryObjectTreeNode);
    MemoryObjectTreeNode root = (MemoryObjectTreeNode)view.getTree().getModel().getRoot();
    assertEquals(3, root.getChildCount());
    ImmutableList<MemoryObjectTreeNode<InstanceObject>> children = root.getChildren();
    assertEquals(INSTANCE_OBJECT_LIST.get(0).getName(), children.get(0).getAdapter().getName());
    assertEquals(INSTANCE_OBJECT_LIST.get(1).getName(), children.get(1).getAdapter().getName());
    assertEquals(INSTANCE_OBJECT_LIST.get(2).getName(), children.get(2).getAdapter().getName());
  }

  @Test
  public void testSelectInstanceToShowInInstanceView() {
    MemoryProfilerStage stage = new MemoryProfilerStage(new StudioProfilers(myGrpcChannel.getClient(), new IdeProfilerServicesStub()));
    MemoryInstanceView view = new MemoryInstanceView(stage);
    stage.selectClass(MemoryProfilerTestBase.mockClassObject("MockClass", INSTANCE_OBJECT_LIST));
    assertEquals(0, view.getTree().getSelectionCount());
    stage.selectInstance(INSTANCE_OBJECT_LIST.get(0));
    assertEquals(1, view.getTree().getSelectionCount());
  }

  @Test
  public void testResetInstanceView() {
    MemoryProfilerStage stage = new MemoryProfilerStage(new StudioProfilers(myGrpcChannel.getClient(), new IdeProfilerServicesStub()));
    MemoryInstanceView view = new MemoryInstanceView(stage);
    stage.selectClass(MemoryProfilerTestBase.mockClassObject("MockClass", INSTANCE_OBJECT_LIST));
    assertTrue(view.getTree().getModel().getRoot() instanceof MemoryObjectTreeNode);
    view.reset();
    assertNull(view.getTree());
  }
}
