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
import com.android.tools.idea.editors.hprof.descriptors.InstanceFieldDescriptorImpl;
import com.android.tools.perflib.heap.ArrayInstance;
import com.android.tools.perflib.heap.Instance;
import com.android.tools.perflib.heap.RootObj;
import com.intellij.debugger.ui.impl.tree.TreeBuilder;
import com.intellij.debugger.ui.impl.tree.TreeBuilderNode;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.icons.AllIcons;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.RowIcon;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class InstanceReferenceTree {
  @NotNull private Tree myTree;
  @NotNull private JComponent myColumnTree;
  private Instance myInstance;

  public InstanceReferenceTree() {
    TreeBuilder model = new TreeBuilder(null) {
      @Override
      public void buildChildren(TreeBuilderNode node) {
        addReferences(node);
        this.nodeChanged(node);
      }

      @Override
      public boolean isExpandable(TreeBuilderNode node) {
        Instance instance = (Instance)node.getUserObject();
        return instance.getReferences().size() > 0;
      }
    };

    myTree = new Tree(model);
    myTree.setRootVisible(true);
    myTree.setShowsRootHandles(true);

    ColumnTreeBuilder builder = new ColumnTreeBuilder(myTree).addColumn(
      new ColumnTreeBuilder.ColumnBuilder().setName("Instance").setPreferredWidth(600).setRenderer(
        new ColoredTreeCellRenderer() {
          @Override
          public void customizeCellRenderer(@NotNull JTree tree,
                                            Object value,
                                            boolean selected,
                                            boolean expanded,
                                            boolean leaf,
                                            int row,
                                            boolean hasFocus) {
            Instance instance = (Instance)((TreeBuilderNode)value).getUserObject();
            SimpleTextAttributes attributes;
            if (myInstance.getImmediateDominator() == instance) {
              attributes = SimpleTextAttributes.SYNTHETIC_ATTRIBUTES;
            }
            else if (instance.getDistanceToGcRoot() == 0) {
              attributes = SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
            }
            else if (instance.getImmediateDominator() == null) {
              attributes = SimpleTextAttributes.ERROR_ATTRIBUTES;
            }
            else {
              attributes = SimpleTextAttributes.REGULAR_ATTRIBUTES;
            }

            if (instance instanceof ArrayInstance) {
              setIcon(AllIcons.Debugger.Db_array);
            }
            else {
              setIcon(AllIcons.Debugger.Value);
            }

            if (myInstance.getImmediateDominator() == instance || instance.getDistanceToGcRoot() == 0) {
              int totalIcons = 1 + (myInstance.getImmediateDominator() == instance ? 1 : 0) + (instance.getDistanceToGcRoot() == 0 ? 1 : 0);
              RowIcon icons = new RowIcon(totalIcons);
              icons.setIcon(getIcon(), 0);

              int currentIcon = 1;
              if (myInstance.getImmediateDominator() == instance) {
                icons.setIcon(AllIcons.Hierarchy.Class, currentIcon++);
              }
              if (instance.getDistanceToGcRoot() == 0) {
                icons.setIcon(AllIcons.Hierarchy.Subtypes, currentIcon);
              }
              setIcon(icons);
            }

            append(instance.toString(), attributes);
          }
        })
      ).addColumn(new ColumnTreeBuilder.ColumnBuilder().setName("Depth").setPreferredWidth(40).setRenderer(
        new ColoredTreeCellRenderer() {
          @Override
          public void customizeCellRenderer(@NotNull JTree tree,
                                            Object value,
                                            boolean selected,
                                            boolean expanded,
                                            boolean leaf,
                                            int row,
                                            boolean hasFocus) {
            Instance instance = (Instance)((TreeBuilderNode)value).getUserObject();
            if (instance.getDistanceToGcRoot() != Integer.MAX_VALUE) {
              append(String.valueOf(instance.getDistanceToGcRoot()), SimpleTextAttributes.REGULAR_ATTRIBUTES);
            }
          }
        })
      ).addColumn(
        new ColumnTreeBuilder.ColumnBuilder().setName("Shallow Size").setPreferredWidth(80).setRenderer(
          new ColoredTreeCellRenderer() {
            @Override
            public void customizeCellRenderer(@NotNull JTree tree,
                                              Object value,
                                              boolean selected,
                                              boolean expanded,
                                              boolean leaf,
                                              int row,
                                              boolean hasFocus) {
              Instance instance = (Instance)((TreeBuilderNode)value).getUserObject();
              append(String.valueOf(instance.getSize()), SimpleTextAttributes.REGULAR_ATTRIBUTES);
            }
          })
      ).addColumn(
        new ColumnTreeBuilder.ColumnBuilder().setName("Dominating Size").setPreferredWidth(80).setRenderer(
          new ColoredTreeCellRenderer() {
            @Override
            public void customizeCellRenderer(@NotNull JTree tree,
                                              Object value,
                                              boolean selected,
                                              boolean expanded,
                                              boolean leaf,
                                              int row,
                                              boolean hasFocus) {
              Instance instance = (Instance)((TreeBuilderNode)value).getUserObject();
              if (instance.getDistanceToGcRoot() != Integer.MAX_VALUE) {
                append(String.valueOf(instance.getTotalRetainedSize()), SimpleTextAttributes.REGULAR_ATTRIBUTES);
              }
          }
        })
      );

    myColumnTree = builder.build();
  }

  public JComponent getComponent() {
    return myColumnTree;
  }

  public MouseAdapter getMouseAdapter() {
    return new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent mouseEvent) {
        super.mousePressed(mouseEvent);
        TreePath path = ((JTree)mouseEvent.getComponent()).getSelectionPath();
        if (path == null || path.getPathCount() < 2) {
          return;
        }

        DebuggerTreeNodeImpl node = (DebuggerTreeNodeImpl)path.getPathComponent(1);
        if (node.getDescriptor() instanceof InstanceFieldDescriptorImpl) {
          InstanceFieldDescriptorImpl descriptor = (InstanceFieldDescriptorImpl)node.getDescriptor();
          myInstance = descriptor.getInstance();
          assert (myInstance != null);
          ((TreeBuilder)myTree.getModel()).setRoot(createInstanceBuilderNode(myInstance));
          ((TreeBuilder)myTree.getModel()).nodeStructureChanged((TreeBuilderNode)myTree.getModel().getRoot());
        }
      }
    };
  }

  private TreeBuilderNode createInstanceBuilderNode(@NotNull final Instance instance) {
    return new TreeBuilderNode(instance) {
      @Override
      protected TreeBuilder getTreeBuilder() {
        return (TreeBuilder)myTree.getModel();
      }
    };
  }

  private void addReferences(@NotNull TreeBuilderNode node) {
    Instance instance = (Instance)node.getUserObject();
    if (instance instanceof RootObj) {
      return;
    }

    List<Instance> sortedReferences = new ArrayList<Instance>(instance.getReferences());
    Collections.sort(sortedReferences, new Comparator<Instance>() {
      @Override
      public int compare(Instance o1, Instance o2) {
        return o1.getDistanceToGcRoot() - o2.getDistanceToGcRoot();
      }
    });

    for (Instance reference : sortedReferences) {
      node.add(createInstanceBuilderNode(reference));
    }
  }
}
