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
package com.android.tools.idea.editors.hprof.views;

import com.android.tools.adtui.common.ColumnTreeBuilder;
import com.android.tools.idea.actions.EditMultipleSourcesAction;
import com.android.tools.idea.actions.PsiFileAndLineNavigation;
import com.android.tools.idea.editors.hprof.descriptors.*;
import com.android.tools.perflib.heap.*;
import com.android.tools.perflib.heap.memoryanalyzer.HprofBitmapProvider;
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
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.sun.jdi.request.EventRequest;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.*;
import java.util.List;

public final class InstancesTreeView implements DataProvider, Disposable {
  public static final String TREE_NAME = "HprofInstancesTree";
  public static final DataKey<ClassInstance> SELECTED_CLASS_INSTANCE = DataKey.create("HprofInstanceTreeView.SelectedClassInstance");

  private static final int NODES_PER_EXPANSION = 100;

  @NotNull private Project myProject;
  @NotNull private DebuggerTree myDebuggerTree;
  @NotNull private JComponent myColumnTree;
  @NotNull private SelectionModel mySelectionModel;

  @NotNull private DebugProcessImpl myDebugProcess;
  @SuppressWarnings("NullableProblems") @NotNull private volatile SuspendContextImpl myDummySuspendContext;

  @NotNull private Heap myHeap;
  @Nullable private ClassObj myClassObj;
  @Nullable private Comparator<DebuggerTreeNodeImpl> myComparator;
  @NotNull private SortOrder mySortOrder = SortOrder.UNSORTED;
  @NotNull private GoToInstanceAction myGoToInstanceAction;

  public InstancesTreeView(@NotNull Project project, @NotNull SelectionModel selectionModel) {
    myProject = project;
    mySelectionModel = selectionModel;

    myDebuggerTree = new DebuggerTree(project) {
      @Override
      protected void build(DebuggerContextImpl context) {
        DebuggerTreeNodeImpl root = (DebuggerTreeNodeImpl)getModel().getRoot();
        Instance instance = ((InstanceFieldDescriptorImpl)root.getDescriptor()).getInstance();
        addChildren(root, null, instance);
      }

      @Override
      public Object getData(@NonNls String dataId) {
        return InstancesTreeView.this.getData(dataId);
      }
    };
    myDebuggerTree.getComponent().setName(TREE_NAME);

    Disposer.register(myProject, this);
    Disposer.register(this, myDebuggerTree);

    myHeap = mySelectionModel.getHeap();
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
          addContainerChildren(debuggerTreeNode, 0, true);
        }
        else {
          InstanceFieldDescriptorImpl instanceDescriptor = (InstanceFieldDescriptorImpl)descriptor;
          addChildren(debuggerTreeNode, instanceDescriptor.getHprofField(), instanceDescriptor.getInstance());
        }

        sortTree(debuggerTreeNode);
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

    myDebuggerTree.putClientProperty(DataManager.CLIENT_PROPERTY_DATA_PROVIDER, this);
    JBList contextActionList = new JBList(new EditMultipleSourcesAction());
    JBPopupFactory.getInstance().createListPopupBuilder(contextActionList);
    myGoToInstanceAction = new GoToInstanceAction(myDebuggerTree);
    myDebuggerTree.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        DefaultActionGroup popupGroup = new DefaultActionGroup(new EditMultipleSourcesAction());
        Instance selectedInstance = mySelectionModel.getInstance();
        if (selectedInstance instanceof ClassInstance && HprofBitmapProvider.canGetBitmapFromInstance(selectedInstance)) {
          popupGroup.add(new ViewBitmapAction());
        }
        popupGroup.add(myGoToInstanceAction);

        ActionManager.getInstance().createActionPopupMenu(ActionPlaces.UNKNOWN, popupGroup).getComponent().show(comp, x, y);
      }
    });

    mySelectionModel.addListener(new SelectionModel.SelectionListener() {
      @Override
      public void onHeapChanged(@NotNull Heap heap) {
        if (heap != myHeap) {
          myHeap = heap;
          if (myDebuggerTree.getMutableModel().getRoot() != null) {
            onSelectionChanged();
          }
        }
      }

      @Override
      public void onClassObjChanged(@Nullable ClassObj classObj) {
        if (classObj != myClassObj) {
          myClassObj = classObj;
          onSelectionChanged();
        }
      }

      @Override
      public void onInstanceChanged(@Nullable Instance instance) {
        if (instance == null) {
          return;
        }

        TreePath path = myDebuggerTree.getSelectionPath();
        if (path != null) {
          DebuggerTreeNodeImpl treeNode = (DebuggerTreeNodeImpl)path.getPathComponent(1);
          if (treeNode.getDescriptor() instanceof InstanceFieldDescriptorImpl &&
              ((InstanceFieldDescriptorImpl)treeNode.getDescriptor()).getInstance() == instance) {
            return;
          }
        }

        DebuggerTreeNodeImpl root = (DebuggerTreeNodeImpl)myDebuggerTree.getMutableModel().getRoot();
        DebuggerTreeNodeImpl targetNode = expandMoreNodesUntilFound(root, instance);
        if (targetNode != null) {
          final TreePath targetPath = new TreePath(targetNode.getPath());
          myDebuggerTree.treeChanged();
          myDebuggerTree.setSelectionPath(targetPath);
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              myDebuggerTree.scrollPathToVisible(targetPath);
            }
          });
        }
      }

      private void onSelectionChanged() {
        DebuggerTreeNodeImpl newRoot;
        Instance singleChild = null;
        if (myClassObj != null) {
          ContainerDescriptorImpl containerDescriptor = new ContainerDescriptorImpl(myClassObj, myHeap.getId());
          newRoot = DebuggerTreeNodeImpl.createNodeNoUpdate(myDebuggerTree, containerDescriptor);
          if (containerDescriptor.getInstances().size() == 1) {
            singleChild = containerDescriptor.getInstances().get(0);
          }
        }
        else {
          newRoot = myDebuggerTree.getNodeFactory().getDefaultNode();
        }

        myDebuggerTree.getMutableModel().setRoot(newRoot);
        myDebuggerTree.treeChanged();
        if (myDebuggerTree.getRowCount() > 0) {
          myDebuggerTree.scrollRowToVisible(0);
        }

        if (singleChild != null) {
          myDebuggerTree.setSelectionInterval(0 , 0);
          mySelectionModel.setInstance(singleChild);
        }
      }
    });

    myDebuggerTree.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        TreePath path = e.getPath();
        if (path == null || path.getPathCount() < 2 || !e.isAddedPath()) {
          mySelectionModel.setInstance(null);
          return;
        }

        DebuggerTreeNodeImpl instanceNode = (DebuggerTreeNodeImpl)path.getPathComponent(1);
        if (instanceNode.getDescriptor() instanceof InstanceFieldDescriptorImpl) {
          InstanceFieldDescriptorImpl descriptor = (InstanceFieldDescriptorImpl)instanceNode.getDescriptor();
          if (descriptor.getInstance() != mySelectionModel.getInstance()) {
            mySelectionModel.setInstance(descriptor.getInstance());
          }
        }

        // Handle node expansions (this is present when the list is large).
        DebuggerTreeNodeImpl lastPathNode = (DebuggerTreeNodeImpl)path.getLastPathComponent();
        if (lastPathNode.getDescriptor() instanceof ExpansionDescriptorImpl) {
          ExpansionDescriptorImpl expansionDescriptor = (ExpansionDescriptorImpl)lastPathNode.getDescriptor();
          DebuggerTreeNodeImpl parentNode = lastPathNode.getParent();
          myDebuggerTree.getMutableModel().removeNodeFromParent(lastPathNode);

          if (parentNode.getDescriptor() instanceof ContainerDescriptorImpl) {
            addContainerChildren(parentNode, expansionDescriptor.getStartIndex(), true);
          }
          else if (parentNode.getDescriptor() instanceof InstanceFieldDescriptorImpl) {
            InstanceFieldDescriptorImpl instanceFieldDescriptor = (InstanceFieldDescriptorImpl)parentNode.getDescriptor();
            addChildren(parentNode, instanceFieldDescriptor.getHprofField(), instanceFieldDescriptor.getInstance(),
                        expansionDescriptor.getStartIndex());
          }

          sortTree(parentNode);
          myDebuggerTree.getMutableModel().nodeStructureChanged(parentNode);

          if (myComparator != null) {
            myDebuggerTree.scrollPathToVisible(new TreePath(((DebuggerTreeNodeImpl)parentNode.getLastChild()).getPath()));
          }
        }
      }
    });

    ColumnTreeBuilder builder = new ColumnTreeBuilder(myDebuggerTree)
      .addColumn(
        new ColumnTreeBuilder.ColumnBuilder()
          .setName("Instance")
          .setPreferredWidth(600)
          .setHeaderAlignment(SwingConstants.LEFT)
          .setComparator(new Comparator<DebuggerTreeNodeImpl>() {
            @Override
            public int compare(@NotNull DebuggerTreeNodeImpl a, @NotNull DebuggerTreeNodeImpl b) {
              return getDefaultOrdering(a, b);
            }
          })
          .setRenderer((DebuggerTreeRenderer)myDebuggerTree.getCellRenderer()))
      .addColumn(
        new ColumnTreeBuilder.ColumnBuilder()
          .setName("Depth")
          .setPreferredWidth(60)
          .setHeaderAlignment(SwingConstants.RIGHT)
          .setComparator(new Comparator<DebuggerTreeNodeImpl>() {
            @Override
            public int compare(DebuggerTreeNodeImpl a, DebuggerTreeNodeImpl b) {
              int depthA = 0;
              int depthB = 0;
              if (a.getDescriptor() instanceof InstanceFieldDescriptorImpl) {
                Instance instanceA = (Instance)((InstanceFieldDescriptorImpl)a.getDescriptor()).getValueData();
                if (instanceA != null) {
                  depthA = instanceA.getDistanceToGcRoot();
                }
              }
              if (b.getDescriptor() instanceof InstanceFieldDescriptorImpl) {
                Instance instanceB = (Instance)((InstanceFieldDescriptorImpl)b.getDescriptor()).getValueData();
                if (instanceB != null) {
                  depthB = instanceB.getDistanceToGcRoot();
                }
              }
              if (depthA != depthB) {
                return depthA - depthB;
              }
              else {
                return getDefaultOrdering(a, b);
              }
            }
          })
          .setRenderer(new ColoredTreeCellRenderer() {
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
                  append(String.valueOf(instance.getDistanceToGcRoot()), SimpleTextAttributes.REGULAR_ATTRIBUTES);
                }
              }
              setTextAlign(SwingConstants.RIGHT);
            }
          }))
      .addColumn(
        new ColumnTreeBuilder.ColumnBuilder()
          .setName("Shallow Size")
          .setPreferredWidth(80)
          .setHeaderAlignment(SwingConstants.RIGHT)
          .setComparator(new Comparator<DebuggerTreeNodeImpl>() {
            @Override
            public int compare(@NotNull DebuggerTreeNodeImpl a, @NotNull DebuggerTreeNodeImpl b) {
              int sizeA = 0;
              int sizeB = 0;
              if (a.getDescriptor() instanceof InstanceFieldDescriptorImpl) {
                Instance instanceA = (Instance)((InstanceFieldDescriptorImpl)a.getDescriptor()).getValueData();
                if (instanceA != null) {
                  sizeA = instanceA.getSize();
                }
              }
              if (b.getDescriptor() instanceof InstanceFieldDescriptorImpl) {
                Instance instanceB = (Instance)((InstanceFieldDescriptorImpl)b.getDescriptor()).getValueData();
                if (instanceB != null) {
                  sizeB = instanceB.getSize();
                }
              }
              if (sizeA != sizeB) {
                return sizeA - sizeB;
              }
              else {
                return getDefaultOrdering(a, b);
              }
            }
          })
          .setRenderer(new ColoredTreeCellRenderer() {
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
              setTextAlign(SwingConstants.RIGHT);
            }
          }))
      .addColumn(
        new ColumnTreeBuilder.ColumnBuilder()
          .setName("Dominating Size")
          .setPreferredWidth(80)
          .setHeaderAlignment(SwingConstants.RIGHT)
          .setComparator(new Comparator<DebuggerTreeNodeImpl>() {
            @Override
            public int compare(@NotNull DebuggerTreeNodeImpl a, @NotNull DebuggerTreeNodeImpl b) {
              long sizeA = 0;
              long sizeB = 0;
              if (a.getDescriptor() instanceof InstanceFieldDescriptorImpl) {
                Instance instanceA = (Instance)((InstanceFieldDescriptorImpl)a.getDescriptor()).getValueData();
                if (instanceA != null && instanceA.getDistanceToGcRoot() != Integer.MAX_VALUE) {
                  sizeA = instanceA.getTotalRetainedSize();
                }
              }
              if (b.getDescriptor() instanceof InstanceFieldDescriptorImpl) {
                Instance instanceB = (Instance)((InstanceFieldDescriptorImpl)b.getDescriptor()).getValueData();
                if (instanceB != null && instanceB.getDistanceToGcRoot() != Integer.MAX_VALUE) {
                  sizeB = instanceB.getTotalRetainedSize();
                }
              }
              if (sizeA != sizeB) {
                return (int)(sizeA - sizeB);
              }
              else {
                return getDefaultOrdering(a, b);
              }
            }
          })
          .setRenderer(new ColoredTreeCellRenderer() {
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
              setTextAlign(SwingConstants.RIGHT);
            }
          })
      );

    //noinspection NullableProblems
    builder.setTreeSorter(new ColumnTreeBuilder.TreeSorter<DebuggerTreeNodeImpl>() {
      @Override
      public void sort(@NotNull Comparator<DebuggerTreeNodeImpl> comparator, @NotNull SortOrder sortOrder) {
        if (myComparator != comparator && mySortOrder != sortOrder) {
          myComparator = comparator;
          mySortOrder = sortOrder;
          TreeBuilder mutableModel = myDebuggerTree.getMutableModel();
          DebuggerTreeNodeImpl root = (DebuggerTreeNodeImpl)mutableModel.getRoot();

          sortTree(root);

          mySelectionModel.setSelectionLocked(true);
          TreePath selectionPath = myDebuggerTree.getSelectionPath();
          mutableModel.nodeStructureChanged(root);
          myDebuggerTree.setSelectionPath(selectionPath);
          myDebuggerTree.scrollPathToVisible(selectionPath);
          mySelectionModel.setSelectionLocked(false);
        }
      }
    });

    myColumnTree = builder.build();
  }

  public void addGoToInstanceListener(@NotNull GoToInstanceListener listener) {
    myGoToInstanceAction.addListener(listener);
  }

  @NotNull
  public JComponent getComponent() {
    return myColumnTree;
  }

  public void requestFocus() {
    myDebuggerTree.requestFocus();
  }

  private void sortTree(@NotNull DebuggerTreeNodeImpl node) {
    if (myComparator == null) {
      return;
    }

    // We don't want to accidentally build children, so we have to get the raw children instead.
    Enumeration e = node.rawChildren();
    if (e.hasMoreElements()) {
      //noinspection unchecked
      ArrayList<DebuggerTreeNodeImpl> builtChildren = Collections.list(e);

      // First check if there's an expansion node. Remove if there is, and add it back at the end.
      DebuggerTreeNodeImpl expansionNode = builtChildren.get(builtChildren.size() - 1);
      if (expansionNode.getDescriptor() instanceof ExpansionDescriptorImpl) {
        builtChildren.remove(builtChildren.size() - 1);
      }
      else {
        expansionNode = null;
      }

      Collections.sort(builtChildren, myComparator);
      node.removeAllChildren(); // Remove children after sorting, since the sort may depend on the parent information.
      for (DebuggerTreeNodeImpl childNode : builtChildren) {
        node.add(childNode);
        sortTree(childNode);
      }

      if (expansionNode != null) {
        node.add(expansionNode);
      }
    }
  }

  private int getDefaultOrdering(@NotNull DebuggerTreeNodeImpl a, @NotNull DebuggerTreeNodeImpl b) {
    NodeDescriptorImpl parentDescriptor = a.getParent().getDescriptor();
    if (parentDescriptor instanceof InstanceFieldDescriptorImpl) {
      Instance parentInstance = ((InstanceFieldDescriptorImpl)parentDescriptor).getInstance();
      if (parentInstance instanceof ArrayInstance) {
        return getMemoryOrderingSortResult(a, b);
      }
    }
    else if (parentDescriptor instanceof ContainerDescriptorImpl) {
      return getMemoryOrderingSortResult(a, b);
    }
    return a.getDescriptor().getLabel().compareTo(b.getDescriptor().getLabel());
  }

  private int getMemoryOrderingSortResult(@NotNull DebuggerTreeNodeImpl a, @NotNull DebuggerTreeNodeImpl b) {
    return (((HprofFieldDescriptorImpl)a.getDescriptor()).getMemoryOrdering() -
            ((HprofFieldDescriptorImpl)b.getDescriptor()).getMemoryOrdering()) ^
           (mySortOrder == SortOrder.ASCENDING ? 1 : -1);
  }

  @Nullable
  private DebuggerTreeNodeImpl expandMoreNodesUntilFound(@NotNull DebuggerTreeNodeImpl node, @NotNull Instance targetInstance) {
    if (node.getDescriptor() instanceof ContainerDescriptorImpl) {
      int startIndex = 0;
      if (node.getChildCount() > 0) {
        DebuggerTreeNodeImpl lastNode = (DebuggerTreeNodeImpl)node.getChildAt(node.getChildCount() - 1);
        if (lastNode.getDescriptor() instanceof ExpansionDescriptorImpl) {
          lastNode.removeFromParent();
          startIndex = node.getChildCount() - 1;
        }
        else {
          for (int scanIndex = 0; scanIndex < node.getChildCount(); ++scanIndex) {
            DebuggerTreeNodeImpl scanNode = (DebuggerTreeNodeImpl)node.getChildAt(scanIndex);
            NodeDescriptor nodeDescriptor = scanNode.getDescriptor();
            if (nodeDescriptor instanceof InstanceFieldDescriptorImpl &&
                ((InstanceFieldDescriptorImpl)nodeDescriptor).getInstance() == targetInstance) {
              return scanNode;
            }
          }
          return null;
        }
      }

      int maxSize = ((ContainerDescriptorImpl)node.getDescriptor()).getInstances().size();
      while (startIndex < maxSize) {
        addContainerChildren(node, startIndex, false);
        for (int scanIndex = startIndex; scanIndex < node.getChildCount(); ++scanIndex) {
          DebuggerTreeNodeImpl scanNode = (DebuggerTreeNodeImpl)node.getChildAt(scanIndex);
          NodeDescriptor nodeDescriptor = scanNode.getDescriptor();
          if (nodeDescriptor instanceof InstanceFieldDescriptorImpl &&
              ((InstanceFieldDescriptorImpl)nodeDescriptor).getInstance() == targetInstance) {
            addExpansionNode(node, node.getChildCount(), maxSize);
            return scanNode;
          }
        }
        startIndex = node.getChildCount();
      }
    }

    return null;
  }

  private void addContainerChildren(@NotNull DebuggerTreeNodeImpl node, int startIndex, boolean addExpansionNode) {
    ContainerDescriptorImpl containerDescriptor = (ContainerDescriptorImpl)node.getDescriptor();
    List<Instance> instances = containerDescriptor.getInstances();
    List<HprofFieldDescriptorImpl> descriptors = new ArrayList<HprofFieldDescriptorImpl>(NODES_PER_EXPANSION);
    int currentIndex = startIndex;
    int limit = currentIndex + NODES_PER_EXPANSION;
    for (int loopCounter = currentIndex; loopCounter < instances.size() && currentIndex < limit; ++loopCounter) {
      Instance instance = instances.get(loopCounter);
      if (myHeap.getInstance(instance.getId()) != null) {
        descriptors.add(new InstanceFieldDescriptorImpl(
          myDebuggerTree.getProject(),
          new Field(Type.OBJECT, Integer.toString(currentIndex)),
          instance,
          currentIndex));
        ++currentIndex;
      }
    }
    HprofFieldDescriptorImpl.batchUpdateRepresentation(descriptors, myDebugProcess.getManagerThread(), myDummySuspendContext);
    for (HprofFieldDescriptorImpl descriptor : descriptors) {
      node.add(DebuggerTreeNodeImpl.createNodeNoUpdate(myDebuggerTree, descriptor));
    }
    if (currentIndex == limit && addExpansionNode) {
      addExpansionNode(node, limit, instances.size());
    }
  }

  private void addExpansionNode(@NotNull DebuggerTreeNodeImpl node, int currentIndex, int maxSize) {
    node.add(DebuggerTreeNodeImpl.createNodeNoUpdate(myDebuggerTree, new ExpansionDescriptorImpl("instances", currentIndex, maxSize)));
  }

  private void addChildren(@NotNull DebuggerTreeNodeImpl node, @Nullable Field field, @Nullable Instance instance) {
    addChildren(node, field, instance, 0);
  }

  private void addChildren(@NotNull DebuggerTreeNodeImpl node, @Nullable Field field, @Nullable Instance instance, int arrayStartIndex) {
    if (instance == null) {
      return;
    }

    // These local variables are used for adding an expansion node for array tree node expansion.
    int currentArrayIndex = arrayStartIndex;
    int limit = currentArrayIndex + NODES_PER_EXPANSION;
    int arrayLength = 0;

    List<HprofFieldDescriptorImpl> descriptors;
    if (instance instanceof ClassInstance) {
      ClassInstance classInstance = (ClassInstance)instance;
      descriptors = new ArrayList<HprofFieldDescriptorImpl>(classInstance.getValues().size());
      int i = 0;
      for (ClassInstance.FieldValue entry : classInstance.getValues()) {
        if (entry.getField().getType() == Type.OBJECT) {
          descriptors.add(new InstanceFieldDescriptorImpl(myDebuggerTree.getProject(), entry.getField(), (Instance)entry.getValue(), i));
        }
        else {
          descriptors.add(new PrimitiveFieldDescriptorImpl(myDebuggerTree.getProject(), entry.getField(), entry.getValue(), i));
        }
        ++i;
      }
    }
    else if (instance instanceof ArrayInstance) {
      assert (field != null);
      ArrayInstance arrayInstance = (ArrayInstance)instance;
      Object[] values = arrayInstance.getValues();
      descriptors = new ArrayList<HprofFieldDescriptorImpl>(values.length);
      arrayLength = values.length;

      if (arrayInstance.getArrayType() == Type.OBJECT) {
        while (currentArrayIndex < arrayLength && currentArrayIndex < limit) {
          descriptors.add(
            new InstanceFieldDescriptorImpl(
              myDebuggerTree.getProject(),
              new Field(arrayInstance.getArrayType(), String.valueOf(currentArrayIndex)),
              (Instance)values[currentArrayIndex], currentArrayIndex));
          ++currentArrayIndex;
        }
      }
      else {
        while (currentArrayIndex < arrayLength && currentArrayIndex < limit) {
          descriptors.add(
            new PrimitiveFieldDescriptorImpl(myDebuggerTree.getProject(),
              new Field(arrayInstance.getArrayType(), String.valueOf(currentArrayIndex)), values[currentArrayIndex], currentArrayIndex));
          ++currentArrayIndex;
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

    if (currentArrayIndex == limit) {
      node.add(DebuggerTreeNodeImpl.createNodeNoUpdate(myDebuggerTree, new ExpansionDescriptorImpl("array elements", limit, arrayLength)));
    }
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (CommonDataKeys.NAVIGATABLE_ARRAY.is(dataId)) {
      return getTargetFiles();
    }
    else if (CommonDataKeys.PROJECT.is(dataId)) {
      return myProject;
    }
    else if (SELECTED_CLASS_INSTANCE.is(dataId)){
      Instance instance = mySelectionModel.getInstance();
      return instance instanceof ClassInstance ? (ClassInstance)instance : null;
    }
    else if (InstanceReferenceTreeView.NAVIGATABLE_INSTANCE.is(dataId)) {
      Object node = myDebuggerTree.getSelectionPath().getLastPathComponent();
      NodeDescriptorImpl nodeDescriptor = node instanceof DebuggerTreeNodeImpl ? ((DebuggerTreeNodeImpl) node).getDescriptor() : null;
      return nodeDescriptor instanceof InstanceFieldDescriptorImpl ? ((InstanceFieldDescriptorImpl) nodeDescriptor).getInstance() : null;
    }
    return null;
  }

  @Nullable
  private PsiFileAndLineNavigation[] getTargetFiles() {
    Object node = myDebuggerTree.getSelectionPath().getLastPathComponent();

    String className = null;
    if (node instanceof DebuggerTreeNodeImpl) {
      NodeDescriptorImpl nodeDescriptor = ((DebuggerTreeNodeImpl)node).getDescriptor();
      if (nodeDescriptor instanceof InstanceFieldDescriptorImpl) {
        Instance instance = ((InstanceFieldDescriptorImpl)nodeDescriptor).getInstance();
        if (instance != null) {
          if (instance instanceof ClassObj) {
            className = ((ClassObj)instance).getClassName();
          }
          else {
            className = instance.getClassObj().getClassName();
            if (instance instanceof ArrayInstance) {
              className = className.replace("[]", "");
            }
          }
        }
      }
    }

    return PsiFileAndLineNavigation.wrappersForClassName(myProject, className, 0);
  }

  @Override
  public void dispose() {
    myDebugProcess.stop(true);
    myDebugProcess.dispose();
  }
}
