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
package com.android.tools.idea.editors.layoutInspector;

import com.android.tools.idea.editors.layoutInspector.model.ViewNode;
import com.android.tools.idea.editors.layoutInspector.ui.RollOverTree;
import com.android.tools.idea.editors.layoutInspector.ui.ViewNodeActiveDisplay;
import com.android.tools.idea.editors.layoutInspector.ui.ViewNodeTableModel;
import com.android.tools.idea.editors.layoutInspector.ui.ViewNodeTreeRenderer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.ui.JBCheckboxMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.util.Key;
import com.intellij.ui.SpeedSearchComparator;
import com.intellij.ui.TableSpeedSearch;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

public class LayoutInspectorContext implements Disposable, DataProvider, ViewNodeActiveDisplay.ViewNodeActiveDisplayListener,
                                               TreeSelectionListener, RollOverTree.TreeHoverListener {
  private static final Key<ViewNode> KEY_VIEW_NODE = Key.create(ViewNode.class.getName());

  private ViewNode myRoot;
  private BufferedImage myBufferedImage;

  private ViewNodeActiveDisplay myPreview;

  // Left Node Tree
  private final RollOverTree myNodeTree;

  // Right Section: Properties Table
  private final ViewNodeTableModel myTableModel;
  private final JBTable myPropertiesTable;

  // Node popup menu
  private final JBPopupMenu myNodePopup;
  private final JBCheckboxMenuItem myNodeVisibleMenuItem;

  public LayoutInspectorContext(@NotNull LayoutFileData layoutParser) {
    myRoot = layoutParser.myNode;
    myBufferedImage = layoutParser.myBufferedImage;

    myNodeTree = new RollOverTree(getRoot());
    myNodeTree.setCellRenderer(new ViewNodeTreeRenderer());
    myNodeTree.addTreeSelectionListener(this);
    myNodeTree.addTreeHoverListener(this);

    myTableModel = new ViewNodeTableModel();
    myPropertiesTable = new JBTable(myTableModel);
    myPropertiesTable.setFillsViewportHeight(true);
    myPropertiesTable.getTableHeader().setReorderingAllowed(false);
    TableSpeedSearch propertiesSpeedSearch = new TableSpeedSearch(myPropertiesTable, (object, cell) -> {
      if (object == null) {
        return null;
      }

      assert object instanceof String : "The model is expected to return String instances as values";
      return (String)object;
    });
    propertiesSpeedSearch.setComparator(new SpeedSearchComparator(false, false));

    // Expand visible nodes
    for (int i = 0; i < myNodeTree.getRowCount(); i++) {
      TreePath path = myNodeTree.getPathForRow(i);
      ViewNode n = (ViewNode)path.getLastPathComponent();
      if (n.isDrawn()) {
        myNodeTree.expandPath(path);
      }
    }

    // Select the root node
    myNodeTree.setSelectionRow(0);

    // Node popup
    myNodePopup = new JBPopupMenu();
    myNodeVisibleMenuItem = new JBCheckboxMenuItem("Show in preview");
    myNodeVisibleMenuItem.addActionListener(new LayoutInspectorContext.ShowHidePreviewActionListener());
    myNodePopup.add(myNodeVisibleMenuItem);
    myNodeTree.addMouseListener(new LayoutInspectorContext.NodeRightClickAdapter());
  }

  public
  @NotNull
  RollOverTree getNodeTree() {
    return myNodeTree;
  }

  public
  @NotNull
  JBTable getPropertiesTable() {
    return myPropertiesTable;
  }

  @Override
  public void onViewNodeOver(@Nullable ViewNode node) {
    if (node == null) {
      myNodeTree.updateHoverPath(null);
    }
    else {
      TreePath path = ViewNode.getPath(node);
      myNodeTree.updateHoverPath(path);
    }
  }

  @Override
  public void onNodeSelected(@NotNull ViewNode node) {
    TreePath path = ViewNode.getPath(node);
    myNodeTree.scrollPathToVisible(path);
    myNodeTree.setSelectionPath(path);
  }

  @Override
  public void valueChanged(@NotNull TreeSelectionEvent event) {
    ViewNode selection = (ViewNode)myNodeTree.getLastSelectedPathComponent();
    if (selection != null) {
      myTableModel.setNode(selection);
      if (myPreview != null) {
        myPreview.setSelectedNode(selection);
      }
    }
  }

  @Override
  public void onTreeCellHover(@Nullable TreePath path) {
    if (myPreview != null) {
      myPreview.setHoverNode(path == null ? null : (ViewNode)path.getLastPathComponent());
    }
  }

  @Override
  public void dispose() {

  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    return null;
  }

  public ViewNode getRoot() {
    return myRoot;
  }

  public BufferedImage getBufferedImage() {
    return myBufferedImage;
  }

  public void setPreview(ViewNodeActiveDisplay preview) {
    myPreview = preview;
  }

  private class NodeRightClickAdapter extends MouseAdapter {

    @Override
    public void mousePressed(@NotNull MouseEvent event) {
      if (event.isPopupTrigger()) {
        TreePath path = myNodeTree.getPathForEvent(event);
        if (path == null) {
          return;
        }

        ViewNode node = (ViewNode)path.getLastPathComponent();
        if (node.isParentVisible()) {
          myNodeVisibleMenuItem.setEnabled(true);
          if (node.getForcedState() == ViewNode.ForcedState.NONE) {
            myNodeVisibleMenuItem.setState(node.isDrawn());
          }
          else {
            myNodeVisibleMenuItem.setState(node.getForcedState() == ViewNode.ForcedState.VISIBLE);
          }
        }
        else {
          // The parent itself is invisible.
          myNodeVisibleMenuItem.setEnabled(false);
          myNodeVisibleMenuItem.setState(false);
        }

        myNodePopup.putClientProperty(KEY_VIEW_NODE, node);

        // Show popup
        myNodePopup.show(myNodeTree, event.getX(), event.getY());
      }
    }
  }

  private class ShowHidePreviewActionListener implements ActionListener {

    @Override
    public void actionPerformed(@NotNull ActionEvent event) {
      ViewNode node = (ViewNode)myNodePopup.getClientProperty(KEY_VIEW_NODE);
      if (node == null) {
        return;
      }

      node.setForcedState(myNodeVisibleMenuItem.getState() ? ViewNode.ForcedState.VISIBLE : ViewNode.ForcedState.INVISIBLE);
      getRoot().updateNodeDrawn();
      if (myPreview != null) {
        myPreview.repaint();
      }
      myNodeTree.repaint();
    }
  }
}
