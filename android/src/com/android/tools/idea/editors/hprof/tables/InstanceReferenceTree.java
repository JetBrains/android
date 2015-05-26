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
import com.android.tools.perflib.heap.*;
import com.intellij.debugger.ui.impl.tree.TreeBuilder;
import com.intellij.debugger.ui.impl.tree.TreeBuilderNode;
import com.intellij.icons.AllIcons;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.RowIcon;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class InstanceReferenceTree {
  @NotNull private Tree myTree;
  @NotNull private JComponent myColumnTree;
  private Instance myInstance;

  public InstanceReferenceTree(@NotNull SelectionModel selectionModel) {
    final TreeBuilder model = new TreeBuilder(null) {
      @Override
      public void buildChildren(TreeBuilderNode node) {
        if (node == getRoot()) {
          node.add(createInstanceBuilderNode(myInstance));
          nodeChanged(node);
        }
        else {
          addReferences(node);
          nodeChanged(node);
        }
      }

      @Override
      public boolean isExpandable(TreeBuilderNode node) {
        if (node == getRoot()) {
          return node.getChildCount() > 0;
        }
        else {
          Instance instance = (Instance)node.getUserObject();
          return instance.getReferences().size() > 0;
        }
      }
    };

    // Set the root to a dummy object since the TreeBuilder implementation is very buggy.
    model.setRoot(new TreeBuilderNode(null) {
      @Override
      protected TreeBuilder getTreeBuilder() {
        return model;
      }
    });

    myTree = new Tree(model);
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);

    ColumnTreeBuilder builder = new ColumnTreeBuilder(myTree).addColumn(
      new ColumnTreeBuilder.ColumnBuilder().setName("Reference Tree").setPreferredWidth(600).setRenderer(
        new ColoredTreeCellRenderer() {
          @Override
          public void customizeCellRenderer(@NotNull JTree tree,
                                            Object value,
                                            boolean selected,
                                            boolean expanded,
                                            boolean leaf,
                                            int row,
                                            boolean hasFocus) {
            if (tree.getModel().getRoot() == value) {
              append("Dummy root node. This should not show up in view.");
              return;
            }

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
            if (instance != null && instance.getDistanceToGcRoot() != Integer.MAX_VALUE) {
              append(String.valueOf(instance.getDistanceToGcRoot()), SimpleTextAttributes.REGULAR_ATTRIBUTES);
            }
            setTextAlign(SwingConstants.RIGHT);
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
              if (instance != null) {
                append(String.valueOf(instance.getSize()), SimpleTextAttributes.REGULAR_ATTRIBUTES);
              }
              setTextAlign(SwingConstants.RIGHT);
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
              if (instance != null && instance.getDistanceToGcRoot() != Integer.MAX_VALUE) {
                append(String.valueOf(instance.getTotalRetainedSize()), SimpleTextAttributes.REGULAR_ATTRIBUTES);
              }
              setTextAlign(SwingConstants.RIGHT);
          }
        })
      );

    myColumnTree = builder.build();

    selectionModel.addListener(new SelectionModel.SelectionListener() {
      @Override
      public void onHeapChanged(@NotNull Heap heap) {
        clearInstance();
      }

      @Override
      public void onClassObjChanged(@Nullable ClassObj classObj) {
        clearInstance();
      }

      @Override
      public void onInstanceChanged(@Nullable Instance instance) {
        if (instance == null) {
          clearInstance();
        }
        else {
          myInstance = instance;
          TreeBuilder model = getMutableModel();
          TreeBuilderNode root = (TreeBuilderNode)model.getRoot();
          root.removeAllChildren();
          root.add(createInstanceBuilderNode(instance));
          model.nodeStructureChanged((TreeBuilderNode)model.getRoot());
        }
      }
    });
  }

  public JComponent getComponent() {
    return myColumnTree;
  }

  private void clearInstance() {
    TreeBuilderNode root = (TreeBuilderNode)getMutableModel().getRoot();
    root.removeAllChildren();
    getMutableModel().nodeStructureChanged(root);
  }

  @NotNull
  private TreeBuilder getMutableModel() {
    return (TreeBuilder)myTree.getModel();
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
