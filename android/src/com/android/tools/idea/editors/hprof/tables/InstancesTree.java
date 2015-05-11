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
package com.android.tools.idea.editors.hprof.tables;

import com.android.tools.idea.editors.allocations.ColumnTreeBuilder;
import com.android.tools.idea.editors.hprof.descriptors.*;
import com.android.tools.perflib.heap.*;
import com.intellij.debugger.engine.DebugProcessEvents;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.SuspendManagerImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.ui.impl.DebuggerTreeRenderer;
import com.intellij.debugger.ui.impl.tree.TreeBuilder;
import com.intellij.debugger.ui.impl.tree.TreeBuilderNode;
import com.intellij.debugger.ui.impl.watch.DebuggerTree;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.DefaultNodeDescriptor;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.ui.tree.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.sun.jdi.request.EventRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreePath;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class InstancesTree {
  @NotNull private DebuggerTree myDebuggerTree;
  @NotNull private JComponent myColumnTree;
  @NotNull private DebugProcessImpl myDebugProcess;
  @NotNull private volatile SuspendContextImpl myDummySuspendContext;
  @NotNull private Heap myHeap;

  public InstancesTree(@NotNull Project project, @NotNull Heap heap, @NotNull MouseListener mouseListener) {
    myDebuggerTree = new DebuggerTree(project) {
      @Override
      protected void build(DebuggerContextImpl context) {
        DebuggerTreeNodeImpl root = (DebuggerTreeNodeImpl)getModel().getRoot();
        Instance instance = ((InstanceFieldDescriptorImpl)root.getDescriptor()).getInstance();
        addChildren(root, null, instance);
      }
    };
    myHeap = heap;
    setHeap(heap);
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
          addContainerChildren(debuggerTreeNode, 0);
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
    myDebuggerTree.setRootVisible(false);
    myDebuggerTree.addMouseListener(mouseListener);
    myDebuggerTree.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        super.mouseClicked(e);
        TreePath path = ((JTree)e.getComponent()).getSelectionPath();
        if (path == null || path.getPathCount() < 2) {
          return;
        }

        DebuggerTreeNodeImpl node = (DebuggerTreeNodeImpl)path.getPathComponent(1);
        if (node.getDescriptor() instanceof ExpansionDescriptorImpl) {
          DebuggerTreeNodeImpl containerNode = node.getParent();
          myDebuggerTree.getMutableModel().removeNodeFromParent(node);
          ExpansionDescriptorImpl expansionDescriptor = (ExpansionDescriptorImpl)node.getDescriptor();
          addContainerChildren(containerNode, expansionDescriptor.getStartIndex());
          myDebuggerTree.getMutableModel().nodeStructureChanged(containerNode);
        }
      }
    });

    ColumnTreeBuilder builder = new ColumnTreeBuilder(myDebuggerTree).addColumn(
      new ColumnTreeBuilder.ColumnBuilder()
        .setName("Instance").setPreferredWidth(600).setRenderer((DebuggerTreeRenderer)myDebuggerTree.getCellRenderer())
      ).addColumn(new ColumnTreeBuilder.ColumnBuilder().setName("Shallow Size").setPreferredWidth(80).setRenderer(
        new ColoredTreeCellRenderer() {
          @Override
          public void customizeCellRenderer(@NotNull JTree tree,
                                            Object value,
                                            boolean selected,
                                            boolean expanded,
                                            boolean leaf,
                                            int row,
                                            boolean hasFocus) {
            NodeDescriptorImpl nodeDescriptor = (NodeDescriptorImpl)((TreeBuilderNode)value).getUserObject();
            if (nodeDescriptor instanceof InstanceFieldDescriptorImpl) {
              InstanceFieldDescriptorImpl descriptor = (InstanceFieldDescriptorImpl)nodeDescriptor;
              assert !descriptor.isPrimitive();
              Instance instance = (Instance)descriptor.getValueData();
              if (instance != null) {
                append(String.valueOf(instance.getSize()), SimpleTextAttributes.REGULAR_ATTRIBUTES);
              }
            }
          }
        })
      ).addColumn(new ColumnTreeBuilder.ColumnBuilder().setName("Dominating Size").setPreferredWidth(80).setRenderer(
        new ColoredTreeCellRenderer() {
          @Override
          public void customizeCellRenderer(@NotNull JTree tree,
                                            Object value,
                                            boolean selected,
                                            boolean expanded,
                                            boolean leaf,
                                            int row,
                                            boolean hasFocus) {
            NodeDescriptorImpl nodeDescriptor = (NodeDescriptorImpl)((TreeBuilderNode)value).getUserObject();
            if (nodeDescriptor instanceof InstanceFieldDescriptorImpl) {
              InstanceFieldDescriptorImpl descriptor = (InstanceFieldDescriptorImpl)nodeDescriptor;
              assert !descriptor.isPrimitive();
              Instance instance = (Instance)descriptor.getValueData();
              if (instance != null && instance.getDistanceToGcRoot() != Integer.MAX_VALUE) {
                append(String.valueOf(instance.getTotalRetainedSize()), SimpleTextAttributes.REGULAR_ATTRIBUTES);
              }
            }
          }
        })
      );

    myColumnTree = builder.build();
  }

  @NotNull
  public JComponent getComponent() {
    return myColumnTree;
  }

  public void setHeap(@NotNull Heap heap) {
    myHeap = heap;
    if (myDebuggerTree.getMutableModel().getRoot() == null) {
      return;
    }

    NodeDescriptorImpl nodeDescriptor = ((DebuggerTreeNodeImpl)myDebuggerTree.getMutableModel().getRoot()).getDescriptor();
    if (nodeDescriptor instanceof ContainerDescriptorImpl) {
      ContainerDescriptorImpl originalDescriptor = (ContainerDescriptorImpl)nodeDescriptor;
      setRoot(new ContainerDescriptorImpl(originalDescriptor.getClassObj(), heap.getId()));
    }
  }

  public void setRoot(@NotNull NodeDescriptorImpl rootDescriptor) {
    DebuggerTreeNodeImpl root = DebuggerTreeNodeImpl.createNodeNoUpdate(myDebuggerTree, rootDescriptor);
    if (rootDescriptor instanceof HprofFieldDescriptorImpl) {
      ((HprofFieldDescriptorImpl)root.getDescriptor()).updateRepresentation(myDebugProcess.getManagerThread(), myDummySuspendContext);
    }
    myDebuggerTree.getMutableModel().setRoot(root);
    myDebuggerTree.treeChanged();
    myDebuggerTree.scrollRowToVisible(0);
  }

  private void addContainerChildren(@NotNull DebuggerTreeNodeImpl node, int startIndex) {
    ContainerDescriptorImpl containerDescriptor = (ContainerDescriptorImpl)node.getDescriptor();
    List<Instance> instances = containerDescriptor.getInstances();
    List<HprofFieldDescriptorImpl> descriptors = new ArrayList<HprofFieldDescriptorImpl>(100);
    int i = startIndex;
    int limit = startIndex + 100;
    for (; i < instances.size(); ++i) {
      if (i >= limit) {
        break;
      }
      Instance instance = instances.get(i);
      if (myHeap.getInstance(instance.getId()) != null) {
        descriptors.add(new InstanceFieldDescriptorImpl(
          myDebuggerTree.getProject(), new Field(
            Type.OBJECT, String.format("0x%x (%d)", instance.getId() & Type.getIdSizeMask(), i)), instance));
      }
    }
    HprofFieldDescriptorImpl.batchUpdateRepresentation(descriptors, myDebugProcess.getManagerThread(), myDummySuspendContext);
    for (HprofFieldDescriptorImpl descriptor : descriptors) {
      node.add(DebuggerTreeNodeImpl.createNodeNoUpdate(myDebuggerTree, descriptor));
    }
    if (i == limit) {
      node.add(DebuggerTreeNodeImpl.createNodeNoUpdate(myDebuggerTree, new ExpansionDescriptorImpl(limit, instances.size())));
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
