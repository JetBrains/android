/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.editors.hierarchyview;

import com.android.tools.idea.editors.hierarchyview.model.ViewNode;
import com.android.tools.idea.editors.hierarchyview.ui.RollOverTree;
import com.android.tools.idea.editors.hierarchyview.ui.ViewNodeActiveDisplay;
import com.android.tools.idea.editors.hierarchyview.ui.ViewNodeTableModel;
import com.android.tools.idea.editors.hierarchyview.ui.ViewNodeTreeRenderer;
import com.google.common.collect.Lists;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.JBCheckboxMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.List;

public class HierarchyViewer
  implements TreeSelectionListener, RollOverTree.TreeHoverListener,
             ViewNodeActiveDisplay.ViewNodeActiveDisplayListener {
  private static final String FIRST_COMPONENT_WIDTH = "com.android.hv.first.comp.width";
  private static final String LAST_COMPONENT_WIDTH = "com.android.hv.last.comp.width";
  private static final int DEFAULT_WIDTH = 200;

  private static final Key<ViewNode> KEY_VIEWNODE = Key.create(ViewNode.class.getName());

  private final ViewNode myRoot;

  private final ThreeComponentsSplitter myContentSplitter;

  // Preview
  private final ViewNodeActiveDisplay myPreview;

  // Node tree
  private final RollOverTree myNodeTree;

  // Properties
  private final ViewNodeTableModel myTableModel;
  private final JBTable myPropertiesPanel;

  // Node popup menu
  private final JBPopupMenu myNodePopup;
  private final JBCheckboxMenuItem myNodeVisibleMenuItem;

  public HierarchyViewer(@NotNull ViewNode node,
                         @NotNull BufferedImage preview,
                         @NotNull final PropertiesComponent propertiesComponent,
                         @NotNull Disposable parent) {
    myRoot = node;

    // Create UI
    // Preview
    myPreview = new ViewNodeActiveDisplay(node, preview);

    // Node tree
    myNodeTree = new RollOverTree(node);
    myNodeTree.setCellRenderer(new ViewNodeTreeRenderer());

    // Properties table
    myTableModel = new ViewNodeTableModel();
    myPropertiesPanel = new JBTable(myTableModel);
    myPropertiesPanel.setFillsViewportHeight(true);
    myPropertiesPanel.getTableHeader().setReorderingAllowed(false);

    myContentSplitter = new ThreeComponentsSplitter(false, true);
    Disposer.register(parent, myContentSplitter);

    final JScrollPane firstComponent = ScrollPaneFactory.createScrollPane(myNodeTree);
    final JScrollPane lastComponent = ScrollPaneFactory.createScrollPane(myPropertiesPanel);

    myContentSplitter.setFirstComponent(firstComponent);
    myContentSplitter.setInnerComponent(myPreview);
    myContentSplitter.setLastComponent(lastComponent);

    myContentSplitter.setFirstSize(propertiesComponent.getInt(FIRST_COMPONENT_WIDTH, DEFAULT_WIDTH));
    myContentSplitter.setLastSize(propertiesComponent.getInt(LAST_COMPONENT_WIDTH, DEFAULT_WIDTH));

    // listen to size changes and update the saved sizes
    ComponentAdapter resizeListener = new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent componentEvent) {
        propertiesComponent.setValue(FIRST_COMPONENT_WIDTH, firstComponent.getSize().width, DEFAULT_WIDTH);
        propertiesComponent.setValue(LAST_COMPONENT_WIDTH, lastComponent.getSize().width, DEFAULT_WIDTH);
      }
    };
    firstComponent.addComponentListener(resizeListener);
    lastComponent.addComponentListener(resizeListener);

    myPreview.addViewNodeActiveDisplayListener(this);
    myNodeTree.addTreeSelectionListener(this);
    myNodeTree.addTreeHoverListener(this);

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
    myNodeVisibleMenuItem.addActionListener(new ShowHidePreviewActionListener());
    myNodePopup.add(myNodeVisibleMenuItem);
    myNodeTree.addMouseListener(new NodeRightClickAdapter());
  }

  public JComponent getRootComponent() {
    return myContentSplitter;
  }

  @Override
  public void valueChanged(TreeSelectionEvent e) {
    ViewNode selection = (ViewNode)myNodeTree.getLastSelectedPathComponent();
    myTableModel.setNode(selection);
    myPreview.setSelectedNode(selection);
  }

  @Override
  public void onTreeCellHover(@Nullable TreePath path) {
    myPreview.setHoverNode(path == null ? null : (ViewNode)path.getLastPathComponent());
  }

  @Override
  public void onViewNodeOver(ViewNode node) {
    if (node == null) {
      myNodeTree.updateHoverPath(null);
    }
    else {
      TreePath path = getPath(node);
      myNodeTree.scrollPathToVisible(path);
      myNodeTree.updateHoverPath(path);
    }
  }

  @Override
  public void onNodeSelected(ViewNode node) {
    if (node != null) {
      TreePath path = getPath(node);
      myNodeTree.scrollPathToVisible(path);
      myNodeTree.setSelectionPath(path);
    }
  }

  private TreePath getPath(ViewNode node) {
    List<Object> nodes = Lists.newArrayList();
    do {
      nodes.add(0, node);
      node = node.parent;
    }
    while (node != null);
    return new TreePath(nodes.toArray());
  }

  private class NodeRightClickAdapter extends MouseAdapter {

    @Override
    public void mousePressed(MouseEvent e) {
      if (e.isPopupTrigger()) {
        TreePath path = myNodeTree.getPathForEvent(e);
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

        myNodePopup.putClientProperty(KEY_VIEWNODE, node);

        // Show popup
        myNodePopup.show(myNodeTree, e.getX(), e.getY());
      }
    }
  }

  private class ShowHidePreviewActionListener implements ActionListener {

    @Override
    public void actionPerformed(ActionEvent e) {
      ViewNode node = (ViewNode)myNodePopup.getClientProperty(KEY_VIEWNODE);
      if (node == null) {
        return;
      }

      node.setForcedState(myNodeVisibleMenuItem.getState() ? ViewNode.ForcedState.VISIBLE : ViewNode.ForcedState.INVISIBLE);
      myRoot.updateNodeDrawn();
      myPreview.repaint();
      myNodeTree.repaint();
    }
  }
}
