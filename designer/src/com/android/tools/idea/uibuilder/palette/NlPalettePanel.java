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
import com.intellij.ide.dnd.DnDAction;
import com.intellij.ide.dnd.DnDDragStartBean;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.dnd.DnDSource;
import com.intellij.ide.dnd.aware.DnDAwareTree;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.util.ui.TextTransferable;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.*;
import java.awt.*;

import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

public class NlPalettePanel extends JPanel implements LightToolWindowContent {
  public static final Insets INSETS = new Insets(0, 6, 0, 6);

  @NotNull private final DnDAwareTree myTree;
  @NotNull private final NlPaletteModel myModel;

  public NlPalettePanel() {
    myModel = new NlPaletteModel();
    myModel.loadPalette();
    myTree = createTree(myModel);
    JScrollPane pane = ScrollPaneFactory.createScrollPane(myTree, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
    setLayout(new BorderLayout());
    add(pane, BorderLayout.CENTER);
  }

  @NotNull
  public JComponent getFocusedComponent() {
    return myTree;
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
    new PaletteSpeedSearch(tree);
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
        Object content = node.getUserObject();
        if (content instanceof NlPaletteItem) {
          NlPaletteItem item = (NlPaletteItem)content;
          append(item.getTitle());
          setIcon(item.getIcon());
          setToolTipText(item.getTooltip());
        }
        else if (content instanceof NlPaletteGroup) {
          NlPaletteGroup group = (NlPaletteGroup)content;
          append(group.getTitle());
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
    final DnDManager dndManager = DnDManager.getInstance();
    dndManager.registerSource(new PaletteDnDSource(tree), tree);
  }

  @Override
  public void dispose() {
  }

  private static class PaletteDnDSource implements DnDSource {
    private final DnDAwareTree myTree;

    private PaletteDnDSource(@NotNull DnDAwareTree tree) {
      myTree = tree;
    }

    @Override
    public boolean canStartDragging(DnDAction action, Point dragOrigin) {
      TreePath path = myTree.getClosestPathForLocation(dragOrigin.x, dragOrigin.y);
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      Object content = node.getUserObject();
      return content instanceof NlPaletteItem;
    }

    @Override
    public DnDDragStartBean startDragging(DnDAction action, Point dragOrigin) {
      TreePath path = myTree.getClosestPathForLocation(dragOrigin.x, dragOrigin.y);
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      Object content = node.getUserObject();
      assert content instanceof NlPaletteItem;
      NlPaletteItem item = (NlPaletteItem)content;
      return new DnDDragStartBean(new TextTransferable(item.getRepresentation()));
    }

    @Nullable
    @Override
    public Pair<Image, Point> createDraggedImage(DnDAction action, Point dragOrigin) {
      TreePath path = myTree.getClosestPathForLocation(dragOrigin.x, dragOrigin.y);
      return DnDAwareTree.getDragImage(myTree, path, dragOrigin);
    }

    @Override
    public void dragDropEnd() {
      myTree.clearSelection();
    }

    @Override
    public void dropActionChanged(int gestureModifiers) {
    }
  }

  private static final class PaletteSpeedSearch extends TreeSpeedSearch {
    PaletteSpeedSearch(@NotNull JTree tree) {
      super(tree);
    }

    @Override
    protected boolean isMatchingElement(Object element, String pattern) {
      if (element == null || pattern == null) {
        return false;
      }
      TreePath path = (TreePath)element;
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      Object content = node.getUserObject();
      if (!(content instanceof NlPaletteItem)) {
        return false;
      }
      NlPaletteItem item = (NlPaletteItem)content;
      return compare(item.getTitle(), pattern);
    }
  }
}
