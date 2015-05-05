/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.hprof.tables.instancestable;

import com.android.tools.idea.editors.hprof.descriptors.ContainerDescriptorImpl;
import com.android.tools.idea.editors.hprof.descriptors.HprofFieldDescriptorImpl;
import com.android.tools.idea.editors.hprof.descriptors.InstanceFieldDescriptorImpl;
import com.android.tools.idea.editors.hprof.descriptors.PrimitiveFieldDescriptorImpl;
import com.android.tools.perflib.heap.*;
import com.intellij.debugger.engine.DebugProcessEvents;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.SuspendManagerImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.ui.impl.tree.TreeBuilder;
import com.intellij.debugger.ui.impl.tree.TreeBuilderNode;
import com.intellij.debugger.ui.impl.watch.DebuggerTree;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.DefaultNodeDescriptor;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.sun.jdi.request.EventRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class InstancesDebuggerTree {
  @NotNull private DebuggerTree myDebuggerTree;
  @NotNull private DebugProcessImpl myDebugProcess;
  @NotNull private volatile SuspendContextImpl myDummySuspendContext;

  public InstancesDebuggerTree(@NotNull Project project) {
    myDebuggerTree = new DebuggerTree(project) {
      @Override
      protected void build(DebuggerContextImpl context) {
        DebuggerTreeNodeImpl root = (DebuggerTreeNodeImpl)getModel().getRoot();
        Instance instance = ((InstanceFieldDescriptorImpl)root.getDescriptor()).getInstance();
        addChildren(root, null, instance);
      }
    };
    myDebugProcess = new DebugProcessEvents(project);
    final SuspendManagerImpl suspendManager = new SuspendManagerImpl(myDebugProcess);
    myDebugProcess.getManagerThread().invokeAndWait(new DebuggerCommandImpl() {
      @Override
      protected void action() throws Exception {
        myDummySuspendContext = suspendManager.pushSuspendContext(EventRequest.SUSPEND_NONE, 1);
      }
    });

    final TreeBuilder model = new TreeBuilder(myDebuggerTree) {
      @Override
      public void buildChildren(TreeBuilderNode node) {
        final DebuggerTreeNodeImpl debuggerTreeNode = (DebuggerTreeNodeImpl)node;
        NodeDescriptor descriptor = debuggerTreeNode.getDescriptor();
        if (descriptor instanceof DefaultNodeDescriptor) {
          return;
        }
        else if (descriptor instanceof ContainerDescriptorImpl) {
          ContainerDescriptorImpl containerDescriptor = (ContainerDescriptorImpl)descriptor;
          addChildren(debuggerTreeNode, containerDescriptor.getInstances());
        }
        else {
          InstanceFieldDescriptorImpl instanceDescriptor = (InstanceFieldDescriptorImpl)descriptor;
          addChildren(debuggerTreeNode, instanceDescriptor.getHprofField(), instanceDescriptor.getInstance());
        }
        myDebuggerTree.treeDidChange();
      }

      @Override
      public boolean isExpandable(TreeBuilderNode builderNode) {
        return ((DebuggerTreeNodeImpl)builderNode).getDescriptor().isExpandable();
      }
    };
    model.setRoot(myDebuggerTree.getNodeFactory().getDefaultNode());
    model.addTreeModelListener(new TreeModelListener() {
      @Override
      public void treeNodesChanged(TreeModelEvent event) {
        myDebuggerTree.hideTooltip();
      }

      @Override
      public void treeNodesInserted(TreeModelEvent event) {
        myDebuggerTree.hideTooltip();
      }

      @Override
      public void treeNodesRemoved(TreeModelEvent event) {
        myDebuggerTree.hideTooltip();
      }

      @Override
      public void treeStructureChanged(TreeModelEvent event) {
        myDebuggerTree.hideTooltip();
      }
    });
    myDebuggerTree.setModel(model);
  }

  @NotNull
  public DebuggerTree getComponent() {
    return myDebuggerTree;
  }

  public void setRoot(@NotNull DebuggerTreeNodeImpl root) {
    if (root.getDescriptor() instanceof HprofFieldDescriptorImpl) {
      ((HprofFieldDescriptorImpl)root.getDescriptor()).updateRepresentation(myDebugProcess.getManagerThread(), myDummySuspendContext);
    }
    myDebuggerTree.getMutableModel().setRoot(root);
    myDebuggerTree.treeChanged();
  }

  private void addChildren(@NotNull DebuggerTreeNodeImpl node, @NotNull Collection<Instance> instances) {
    List<HprofFieldDescriptorImpl> descriptors = new ArrayList<HprofFieldDescriptorImpl>(instances.size());
    for (Instance instance : instances) {
      descriptors.add(
        new InstanceFieldDescriptorImpl(myDebuggerTree.getProject(), new Field(Type.OBJECT, String.format("0x%x", instance.getId())), instance));
    }
    HprofFieldDescriptorImpl.batchUpdateRepresentation(descriptors, myDebugProcess.getManagerThread(), myDummySuspendContext);
    for (HprofFieldDescriptorImpl descriptor : descriptors) {
      node.add(DebuggerTreeNodeImpl.createNodeNoUpdate(myDebuggerTree, descriptor));
    }
  }

  private void addChildren(@NotNull DebuggerTreeNodeImpl node, @Nullable Field field, @Nullable Instance instance) {
    if (instance == null) {
      return;
    }

    // TODO: Limit number of children built.

    List<HprofFieldDescriptorImpl> descriptors;
    if (instance instanceof ClassInstance) {
      ClassInstance classInstance = (ClassInstance)instance;
      descriptors = new ArrayList<HprofFieldDescriptorImpl>(classInstance.getValues().size());
      for (Map.Entry<Field, Object> entry : classInstance.getValues().entrySet()) {
        if (entry.getKey().getType() == Type.OBJECT) {
          descriptors.add(new InstanceFieldDescriptorImpl(myDebuggerTree.getProject(), entry.getKey(), (Instance)entry.getValue()));
        }
        else {
          descriptors.add(new PrimitiveFieldDescriptorImpl(myDebuggerTree.getProject(), entry.getKey(), entry.getValue()));
        }
      }
    }
    else if (instance instanceof ArrayInstance) {
      assert (field != null);
      ArrayInstance arrayInstance = (ArrayInstance)instance;
      if (arrayInstance.getArrayType() == Type.OBJECT) {
        descriptors = new ArrayList<HprofFieldDescriptorImpl>(arrayInstance.getValues().length);
        for (int i = 0; i < arrayInstance.getValues().length; ++i) {
          descriptors.add(
            new InstanceFieldDescriptorImpl(myDebuggerTree.getProject(), new Field(arrayInstance.getArrayType(), String.valueOf(i)),
                                       (Instance)arrayInstance.getValues()[i]));
        }
      }
      else {
        descriptors = new ArrayList<HprofFieldDescriptorImpl>(arrayInstance.getValues().length);
        for (int i = 0; i < arrayInstance.getValues().length; ++i) {
          descriptors.add(
            new PrimitiveFieldDescriptorImpl(myDebuggerTree.getProject(), new Field(arrayInstance.getArrayType(), String.valueOf(i)),
                                        arrayInstance.getValues()[i]));
        }
      }
    }
    else {
      throw new RuntimeException("Unimplemented Instance type in addChildren.");
    }

    HprofFieldDescriptorImpl.batchUpdateRepresentation(descriptors, myDebugProcess.getManagerThread(), myDummySuspendContext);
    for (HprofFieldDescriptorImpl descriptor : descriptors) {
      node.add(DebuggerTreeNodeImpl.createNodeNoUpdate(myDebuggerTree, descriptor));
    }
  }
}
