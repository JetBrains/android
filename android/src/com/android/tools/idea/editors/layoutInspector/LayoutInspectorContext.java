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

import com.android.annotations.NonNull;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.HandleViewDebug;
import com.android.ddmlib.IDevice;
import com.android.layoutinspector.model.ClientWindow;
import com.android.layoutinspector.model.ViewNode;
import com.android.layoutinspector.model.ViewProperty;
import com.android.tools.adtui.ptable.PTable;
import com.android.tools.adtui.ptable.PTableItem;
import com.android.tools.adtui.ptable.PTableModel;
import com.android.tools.adtui.workbench.ToolWindowDefinition;
import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.editors.layoutInspector.ptable.LITableGroupItem;
import com.android.tools.idea.editors.layoutInspector.ptable.LITableRendererProvider;
import com.android.tools.idea.editors.layoutInspector.ui.RollOverTree;
import com.android.tools.idea.editors.layoutInspector.ui.ViewNodeActiveDisplay;
import com.android.tools.idea.editors.layoutInspector.ui.ViewNodeTreeRenderer;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.LayoutInspectorEvent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.ui.JBCheckboxMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.util.Key;
import com.intellij.ui.SpeedSearchComparator;
import com.intellij.ui.TableSpeedSearch;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class LayoutInspectorContext implements Disposable, DataProvider, ViewNodeActiveDisplay.ViewNodeActiveDisplayListener,
                                               TreeSelectionListener, RollOverTree.TreeHoverListener,
                                               AndroidDebugBridge.IDeviceChangeListener {
  private static final Key<ViewNode> KEY_VIEW_NODE = Key.create(ViewNode.class.getName());

  // Hidden from public usage until we get UX/PM input on displaying display list output.
  private static final boolean DUMP_DISPLAYLIST_ENABLED = Boolean.getBoolean("dump.displaylist.enabled");

  private
  @Nullable Client myClient;
  private
  @Nullable ClientWindow myWindow;

  private ViewNode myRoot;
  private BufferedImage myBufferedImage;

  private ViewNodeActiveDisplay myPreview;

  // Left Node Tree
  private final RollOverTree myNodeTree;

  // Right Section: Properties Table
  private final PTableModel myTableModel;
  private final PTable myPropertiesTable;

  // Node popup menu
  private final JBPopupMenu myNodePopup;
  private final JBCheckboxMenuItem myNodeVisibleMenuItem;
  private final JMenuItem myDumpDisplayListMenuItem;

  public LayoutInspectorContext(@NotNull LayoutFileData layoutParser) {
    myRoot = layoutParser.myNode;
    myBufferedImage = layoutParser.myBufferedImage;

    myNodeTree = new RollOverTree(getRoot());
    myNodeTree.setCellRenderer(new ViewNodeTreeRenderer());
    myNodeTree.addTreeSelectionListener(this);
    myNodeTree.addTreeHoverListener(this);

    myTableModel = new PTableModel();
    myPropertiesTable = new PTable(myTableModel);
    myPropertiesTable.getColumnModel().getColumn(0).setMinWidth((int)(ToolWindowDefinition.DEFAULT_SIDE_WIDTH * 0.6));
    myPropertiesTable.setRendererProvider(LITableRendererProvider.getInstance());
    myPropertiesTable.setFillsViewportHeight(true);
    myPropertiesTable.getTableHeader().setReorderingAllowed(false);
    TableSpeedSearch propertiesSpeedSearch = new TableSpeedSearch(myPropertiesTable, (object, cell) -> {
      if (object == null) {
        return null;
      }

      assert object instanceof PTableItem : "Items in inspector properties table expected to be a PTableItem";
      return ((PTableItem)object).getName();
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

    if (isDumpDisplayListEnabled()) {
      myDumpDisplayListMenuItem = new JMenuItem("Dump DisplayList");
      myDumpDisplayListMenuItem.addActionListener(new LayoutInspectorContext.DumpDisplayListActionListener());
      myDumpDisplayListMenuItem.setEnabled(myClient != null && myWindow != null);
      myNodePopup.add(myDumpDisplayListMenuItem);

      AndroidDebugBridge.addDeviceChangeListener(this);
    }
    else {
      myDumpDisplayListMenuItem = null;
    }
    myNodeTree.addMouseListener(new LayoutInspectorContext.NodeRightClickAdapter());
  }

  public
  @NotNull
  RollOverTree getNodeTree() {
    return myNodeTree;
  }

  public
  @NotNull
  PTable getPropertiesTable() {
    return myPropertiesTable;
  }

  @NotNull
  public PTableModel getTableModel() {
    return myTableModel;
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
      myTableModel.setItems(convertToItems(selection.groupedProperties));
      if (myPreview != null) {
        myPreview.setSelectedNode(selection);
      }
    }
  }

  @NotNull
  public static List<PTableItem> convertToItems(@NotNull Map<String, List<ViewProperty>> properties) {
    List<PTableItem> items = new ArrayList<>();
    List<String> sortedKeys = new ArrayList<>(properties.keySet());
    Collections.sort(sortedKeys, String::compareToIgnoreCase);
    for (String key : sortedKeys) {
      items.add(new LITableGroupItem(key, properties.get(key)));
    }
    return items;
  }

  @Override
  public void onTreeCellHover(@Nullable TreePath path) {
    if (myPreview != null) {
      myPreview.setHoverNode(path == null ? null : (ViewNode)path.getLastPathComponent());
    }
  }

  @Override
  public void dispose() {
    AndroidDebugBridge.removeDeviceChangeListener(this);
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

  public void setSources(@Nullable Client client, @Nullable ClientWindow window) {
    myClient = client;
    myWindow = window;
    myDumpDisplayListMenuItem.setEnabled(myClient != null && myWindow != null);
  }

  public static boolean isDumpDisplayListEnabled() {
    return DUMP_DISPLAYLIST_ENABLED;
  }

  @Override
  public void deviceConnected(@NonNull IDevice device) {
  }

  @Override
  public void deviceDisconnected(@NonNull IDevice device) {
    if (myClient == null) return;

    IDevice currentDevice = myClient.getDevice();
    if (device.equals(currentDevice)) {
      setSources(null, null);
    }
  }

  @Override
  public void deviceChanged(@NonNull IDevice device, int changeMask) {
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

  private class DumpDisplayListActionListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      ViewNode node = (ViewNode)myNodePopup.getClientProperty(KEY_VIEW_NODE);
      if (node == null) {
        createNotification(AndroidBundle.message("android.ddms.actions.layoutinspector.dumpdisplay.notification.nonode"),
                           NotificationType.ERROR);
        return;
      }

      try {
        HandleViewDebug.dumpDisplayList(myClient, myWindow.title, node.toString());
      }
      catch (IOException e1) {
        createNotification(AndroidBundle.message("android.ddms.actions.layoutinspector.dumpdisplay.notification.failure", e1.getMessage()),
                           NotificationType.ERROR);
        return;
      }

      // TODO figure out better way to output, for now it outputs to logcat
      createNotification(AndroidBundle.message("android.ddms.actions.layoutinspector.dumpdisplay.notification.success"),
                         NotificationType.INFORMATION);

      UsageTracker.getInstance()
        .log(AndroidStudioEvent.newBuilder().setKind(AndroidStudioEvent.EventKind.LAYOUT_INSPECTOR_EVENT)
               .setLayoutInspectorEvent(LayoutInspectorEvent.newBuilder()
                                          .setType(LayoutInspectorEvent.LayoutInspectorEventType.DUMP_DISPLAYLIST)
               ));
    }
  }

  private void createNotification(@NotNull String message, @NotNull NotificationType type) {
    Notifications.Bus.notify(new Notification(AndroidBundle.message("android.ddms.actions.layoutinspector.notification.group"),
                                              AndroidBundle.message("android.ddms.actions.layoutinspector.notification.title"),
                                              message, type, null));
  }
}
