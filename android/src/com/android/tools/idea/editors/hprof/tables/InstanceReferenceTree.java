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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.RowIcon;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.PlatformIcons;
import com.intellij.xdebugger.impl.ui.XDebuggerUIConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.TreePath;
import java.util.*;

public class InstanceReferenceTree {
  private static final int MAX_AUTO_EXPANSION_DEPTH = 5;

  @NotNull private Tree myTree;
  @NotNull private JComponent myColumnTree;
  private Instance myInstance;

  public InstanceReferenceTree(@NotNull SelectionModel selectionModel) {
    final TreeBuilder model = new TreeBuilder(null) {
      @Override
      public void buildChildren(TreeBuilderNode node) {
        if (node == getRoot()) {
          node.add(new InstanceNode(this, myInstance));
        }
        else {
          addReferences((InstanceNode)node);
        }
        nodeChanged(node);
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
    myTree.setLargeModel(true);
    myTree.addTreeExpansionListener(new TreeExpansionListener() {
      private boolean myIsCurrentlyExpanding = false;

      @Override
      public void treeExpanded(TreeExpansionEvent event) {
        if (myIsCurrentlyExpanding) {
          return;
        }

        myIsCurrentlyExpanding = true;
        InstanceNode node = (InstanceNode)event.getPath().getLastPathComponent();
        InstanceNode currentNode = node;
        int recursiveDepth = MAX_AUTO_EXPANSION_DEPTH;
        while (currentNode.getChildCount() == 1 && recursiveDepth > 0) {
          InstanceNode childNode = (InstanceNode)currentNode.getChildAt(0);
          if (childNode.isLeaf() || childNode.getInstance().getDistanceToGcRoot() == 0) {
            break;
          }
          currentNode = childNode;
          Instance currentInstance = currentNode.getInstance();
          --recursiveDepth;
          if (currentInstance.getDistanceToGcRoot() == 0) {
            break;
          }
        }

        if (node != currentNode) {
          myTree.expandPath(new TreePath(currentNode.getPath()));
        }
        myIsCurrentlyExpanding = false;
      }

      @Override
      public void treeCollapsed(TreeExpansionEvent event) {

      }
    });

    ColumnTreeBuilder builder = new ColumnTreeBuilder(myTree).addColumn(
      new ColumnTreeBuilder.ColumnBuilder()
        .setName("Reference Tree")
        .setPreferredWidth(1200)
        .setHeaderAlignment(SwingConstants.LEFT)
        .setRenderer(
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

            InstanceNode node = (InstanceNode)value;
            Instance instance = node.getInstance();

            String[] referenceVarNames = node.getVarNames();
            if (referenceVarNames.length > 0) {
              if (instance instanceof ArrayInstance) {
                append(StringUtil.pluralize("Index", referenceVarNames.length), SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES);
                append(" ", SimpleTextAttributes.REGULAR_ATTRIBUTES);
              }

              StringBuilder builder = new StringBuilder();
              builder.append(referenceVarNames[0]);
              for (int i = 1; i < referenceVarNames.length; ++i) {
                builder.append(", ");
                builder.append(referenceVarNames[i]);
              }
              append(builder.toString(), XDebuggerUIConstants.VALUE_NAME_ATTRIBUTES);
              append(" in ", SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES);
            }

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
            else if (instance instanceof ClassObj) {
              setIcon(PlatformIcons.CLASS_ICON);
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
      ).addColumn(
        new ColumnTreeBuilder.ColumnBuilder()
          .setName("Depth")
          .setPreferredWidth(40)
          .setHeaderAlignment(SwingConstants.RIGHT)
          .setRenderer(new ColoredTreeCellRenderer() {
            @Override
            public void customizeCellRenderer(@NotNull JTree tree,
                                              Object value,
                                              boolean selected,
                                              boolean expanded,
                                              boolean leaf,
                                              int row,
                                              boolean hasFocus) {
              Instance instance = ((InstanceNode)value).getInstance();
              if (instance != null && instance.getDistanceToGcRoot() != Integer.MAX_VALUE) {
                append(String.valueOf(instance.getDistanceToGcRoot()), SimpleTextAttributes.REGULAR_ATTRIBUTES);
              }
              setTextAlign(SwingConstants.RIGHT);
            }
          })
      ).addColumn(
        new ColumnTreeBuilder.ColumnBuilder()
          .setName("Shallow Size")
          .setPreferredWidth(80)
          .setHeaderAlignment(SwingConstants.RIGHT)
          .setRenderer(new ColoredTreeCellRenderer() {
            @Override
            public void customizeCellRenderer(@NotNull JTree tree,
                                              Object value,
                                              boolean selected,
                                              boolean expanded,
                                              boolean leaf,
                                              int row,
                                              boolean hasFocus) {
              Instance instance = ((InstanceNode)value).getInstance();
              if (instance != null) {
                append(String.valueOf(instance.getSize()), SimpleTextAttributes.REGULAR_ATTRIBUTES);
              }
              setTextAlign(SwingConstants.RIGHT);
            }
          })
      ).addColumn(
        new ColumnTreeBuilder.ColumnBuilder()
          .setName("Dominating Size")
          .setPreferredWidth(80)
          .setHeaderAlignment(SwingConstants.RIGHT)
          .setRenderer(new ColoredTreeCellRenderer() {
            @Override
            public void customizeCellRenderer(@NotNull JTree tree,
                                              Object value,
                                              boolean selected,
                                              boolean expanded,
                                              boolean leaf,
                                              int row,
                                              boolean hasFocus) {
              Instance instance = ((InstanceNode)value).getInstance();
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
          root.add(new InstanceNode(getMutableModel(), instance));
          model.nodeStructureChanged((TreeBuilderNode)model.getRoot());
          myTree.expandRow(0);
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

  private void addReferences(@NotNull InstanceNode node) {
    Instance instance = node.getInstance();
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
      List<String> scratchList = new ArrayList<String>(3);
      if (reference instanceof ClassInstance) {
        ClassInstance classInstance = (ClassInstance)reference;
        for (Map.Entry<Field, Object> entry : classInstance.getValues().entrySet()) {
          if (entry.getKey().getType() == Type.OBJECT && entry.getValue() == instance) {
            scratchList.add(entry.getKey().getName());
          }
        }
      }
      else if (reference instanceof ArrayInstance) {
        ArrayInstance arrayInstance = (ArrayInstance)reference;
        assert arrayInstance.getArrayType() == Type.OBJECT;
        Object[] values = arrayInstance.getValues();
        for (int i = 0; i < values.length; ++i) {
          if (values[i] == instance) {
            scratchList.add(String.valueOf(i));
          }
        }
      }
      else if (reference instanceof ClassObj) {
        ClassObj classObj = (ClassObj)reference;
        Map<Field, Object> staticValues = classObj.getStaticFieldValues();
        for (Map.Entry<Field, Object> entry : staticValues.entrySet()) {
          if (entry.getKey().getType() == Type.OBJECT && entry.getValue() == instance) {
            scratchList.add(entry.getKey().getName());
          }
        }
      }

      String[] scratchNameArray = new String[scratchList.size()];
      node.add(new InstanceNode(getMutableModel(), reference, scratchList.toArray(scratchNameArray)));
    }
  }

  private static class InstanceNode extends TreeBuilderNode {
    @NotNull private TreeBuilder myModel;
    @NotNull private String[] myVarNames;

    public InstanceNode(@NotNull TreeBuilder model, @NotNull Instance userObject, @NotNull String... varNames) {
      super(userObject);
      myModel = model;
      myVarNames = varNames;
    }

    @NotNull
    public String[] getVarNames() {
      return myVarNames;
    }

    public Instance getInstance() {
      return (Instance)getUserObject();
    }

    @Override
    protected TreeBuilder getTreeBuilder() {
      return myModel;
    }
  }
}
