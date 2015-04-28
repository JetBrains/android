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
package com.android.tools.idea.uibuilder.palette;

import com.intellij.designer.LightToolWindowContent;
import com.intellij.icons.AllIcons;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.*;
import java.awt.*;

import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

public class NlPalettePanel extends JPanel implements LightToolWindowContent {
  public static final Insets INSETS = new Insets(0, 6, 0, 6);

  @NotNull private DnDAwareTree myTree;
  @NotNull private NlPaletteModel myModel;

  public NlPalettePanel() {
    myModel = new NlPaletteModel();
    myModel.loadPalette();
    myTree = createTree(myModel);
    JScrollPane pane = ScrollPaneFactory.createScrollPane(myTree, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
    setLayout(new BorderLayout());
    add(pane, BorderLayout.CENTER);
  }

  @NotNull
  private static DnDAwareTree createTree(@NotNull NlPaletteModel model) {
    DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(null);
    DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);
    DnDAwareTree tree = new DnDAwareTree(treeModel);
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    tree.setRootVisible(false);
    tree.setShowsRootHandles(false);
    tree.setBorder(new EmptyBorder(INSETS));
    tree.setToggleClickCount(1);
    ToolTipManager.sharedInstance().registerComponent(tree);
    TreeUtil.installActions(tree);
    enableDnD(tree);
    createCellRenderer(tree);
    addData(model, rootNode);
    expandAll(tree, rootNode);
    tree.setSelectionRow(0);
    return tree;
  }

  private static void expandAll(@NotNull JTree tree, @NotNull DefaultMutableTreeNode rootNode) {
    TreePath rootPath = new TreePath(rootNode);
    tree.expandPath(rootPath);
    TreeNode child = rootNode.getLastChild();
    while (child != null) {
      tree.expandPath(rootPath.pathByAddingChild(child));
      child = rootNode.getChildBefore(child);
    }
  }

  private static void createCellRenderer(@NotNull JTree tree) {
    tree.setCellRenderer(new ColoredTreeCellRenderer() {
      @Override
      public void customizeCellRenderer(@NotNull JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
        if (leaf) {
          NlPaletteItem item = (NlPaletteItem)node.getUserObject();
          append(item.getTitle());
          setIcon(item.getIcon());
          setToolTipText(item.getTooltip());
        }
        else {
          NlPaletteGroup group = (NlPaletteGroup)node.getUserObject();
          if (group != null) {
            append(group.getTitle());
          }
          setIcon(AllIcons.Nodes.Folder);
        }
      }
    });
  }

  private static void addData(@NotNull NlPaletteModel model, @NotNull DefaultMutableTreeNode rootNode) {
    for (NlPaletteGroup group : model.getGroups()) {
      DefaultMutableTreeNode groupNode = new DefaultMutableTreeNode(group);
      for (NlPaletteItem item : group.getItems()) {
        DefaultMutableTreeNode itemNode = new DefaultMutableTreeNode(item);
        groupNode.add(itemNode);
      }
      rootNode.add(groupNode);
    }
  }

  private static void enableDnD(@NotNull DnDAwareTree tree) {
    // todo...
  }

  @Override
  public void dispose() {
  }
}
