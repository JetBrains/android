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
import com.android.annotations.VisibleForTesting;
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
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.observable.collections.ObservableList;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.LayoutInspectorEvent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.JBCheckboxMenuItem;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.ui.SpeedSearchComparator;
import com.intellij.ui.TableSpeedSearch;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.TreePath;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LayoutInspectorContext implements Disposable, DataProvider, ViewNodeActiveDisplay.ViewNodeActiveDisplayListener,
                                               TreeSelectionListener, RollOverTree.TreeHoverListener,
                                               AndroidDebugBridge.IDeviceChangeListener {
  private static final Key<ViewNode> KEY_VIEW_NODE = Key.create(ViewNode.class.getName());

  // Hidden from public usage until we get UX/PM input on displaying display list output.
  private static final boolean DUMP_DISPLAYLIST_ENABLED = Boolean.getBoolean("dump.displaylist.enabled");

  @Nullable
  private Client myClient;
  @Nullable
  private ClientWindow myWindow;
  @Nullable
  private ViewNode myRoot;
  @Nullable
  private BufferedImage myBufferedImage;
  @NotNull
  private ViewNodeActiveDisplay myPreview;

  // Left Node Tree
  @NotNull
  private RollOverTree myNodeTree;
  // list of ViewNodes for the subview path
  @NotNull
  private final ObservableList<ViewNode> mySubviewList;

  // Right Section: Properties Table
  @NotNull
  private final PTableModel myTableModel;
  @NotNull
  private final PTable myPropertiesTable;

  // Node popup menu
  @NotNull
  private final JBPopupMenu myNodePopup;
  @NotNull
  private final JBCheckboxMenuItem myNodeVisibleMenuItem;
  @NotNull
  private final JMenuItem myDumpDisplayListMenuItem;
  @NotNull
  private final JMenuItem mySubtreePreviewMenuItem;

  @NotNull
  private static Logger getLogger() {
    return Logger.getInstance(LayoutInspectorContext.class);
  }

  public LayoutInspectorContext(@NotNull LayoutFileData layoutParser,@NotNull Disposable parentDisposable) {
    myRoot = layoutParser.getNode();
    myBufferedImage = layoutParser.getBufferedImage();

    myNodeTree = createNodeTree(getRoot());

    mySubviewList = new ObservableList<>();

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

    // Node popup
    myNodePopup = new JBPopupMenu();
    myNodeVisibleMenuItem = new JBCheckboxMenuItem(AndroidBundle.message("android.ddms.actions.layoutinspector.menu.show.bound"));
    myNodeVisibleMenuItem.addActionListener(new LayoutInspectorContext.ShowHidePreviewActionListener());
    myNodePopup.add(myNodeVisibleMenuItem);

    AndroidDebugBridge.addDeviceChangeListener(this);

    myDumpDisplayListMenuItem = new JMenuItem(AndroidBundle.message("android.ddms.actions.layoutinspector.menu.dump.display"));
    myDumpDisplayListMenuItem.setVisible(false);
    if (isDumpDisplayListEnabled()) {
      myDumpDisplayListMenuItem.setVisible(true);
      myDumpDisplayListMenuItem.addActionListener(new LayoutInspectorContext.DumpDisplayListActionListener());
      myDumpDisplayListMenuItem.setEnabled(isDeviceConnected());
      myNodePopup.add(myDumpDisplayListMenuItem);
    }

    // sub tree preview
    mySubtreePreviewMenuItem = new JMenuItem("Render subtree preview");
    mySubtreePreviewMenuItem.setVisible(false);
    if (StudioFlags.LAYOUT_INSPECTOR_SUB_VIEW_ENABLED.get()) {
      mySubtreePreviewMenuItem.setVisible(true);
      mySubtreePreviewMenuItem.addActionListener(new LayoutInspectorContext.RenderSubtreePreviewActionListener());
      mySubtreePreviewMenuItem.setEnabled(isDeviceConnected());
      myNodePopup.add(mySubtreePreviewMenuItem);
    }

    Disposer.register(parentDisposable, this);
  }

  @NotNull
  private RollOverTree createNodeTree(@Nullable ViewNode root) {
    RollOverTree tree = new RollOverTree(root);
    // Select the root node
    tree.setSelectionRow(0);
    tree.setCellRenderer(new ViewNodeTreeRenderer());
    tree.addTreeSelectionListener(this);
    tree.addTreeHoverListener(this);
    // Expand visible nodes
    for (int i = 0; i < tree.getRowCount(); i++) {
      TreePath path = tree.getPathForRow(i);
      ViewNode n = (ViewNode)path.getLastPathComponent();
      if (n.isDrawn()) {
        tree.expandPath(path);
      }
    }

    tree.addMouseListener(new LayoutInspectorContext.NodeRightClickAdapter());
    return tree;
  }

  @NotNull
  public RollOverTree getNodeTree() {
    return myNodeTree;
  }

  @NotNull
  public PTable getPropertiesTable() {
    return myPropertiesTable;
  }

  @NotNull
  public PTableModel getTableModel() {
    return myTableModel;
  }

  @Override
  public void onViewNodeOver(@Nullable ViewNode node) {
    if (myNodeTree == null) return;
    if (node == null) {
      myNodeTree.updateHoverPath(null);
    }
    else {
      TreePath path = ViewNode.getPathFromParent(node, myRoot);
      myNodeTree.updateHoverPath(path);
    }
  }

  @Override
  public void onNodeSelected(@NotNull ViewNode node) {
    if (myNodeTree == null) return;
    TreePath path = ViewNode.getPathFromParent(node, myRoot);
    myNodeTree.scrollPathToVisible(path);
    myNodeTree.setSelectionPath(path);
  }

  @Override
  public void onNodeDoubleClicked(@NotNull ViewNode node) {
    if (isDeviceConnected() && StudioFlags.LAYOUT_INSPECTOR_SUB_VIEW_ENABLED.get()) {
      showSubView(node);
    }
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

  @Nullable
  public ViewNode getRoot() {
    return myRoot;
  }

  @Nullable
  public BufferedImage getBufferedImage() {
    return myBufferedImage;
  }

  public void setPreview(@NotNull ViewNodeActiveDisplay preview) {
    myPreview = preview;
  }

  public void setSources(@Nullable Client client, @Nullable ClientWindow window) {
    myClient = client;
    myWindow = window;
    myDumpDisplayListMenuItem.setEnabled(isDeviceConnected());
    mySubtreePreviewMenuItem.setEnabled(isDeviceConnected());
  }

  private boolean isDeviceConnected() {
    return myClient != null && myWindow != null;
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
      if (myNodeTree == null) return;
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

        // hide sub view menu from the root
        mySubtreePreviewMenuItem.setVisible(!node.equals(myRoot));

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
      if (myNodeTree == null) return;
      myNodeTree.repaint();
    }
  }

  private class DumpDisplayListActionListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      if (!isDeviceConnected()) return;

      ViewNode node = (ViewNode)myNodePopup.getClientProperty(KEY_VIEW_NODE);
      if (node == null) {
        createNotification(AndroidBundle.message("android.ddms.actions.layoutinspector.dumpdisplay.notification.nonode"),
                           NotificationType.ERROR);
        return;
      }

      if (myClient == null || myWindow == null) return;
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

  private class RenderSubtreePreviewActionListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      if (isDeviceConnected()) {
        showSubView((ViewNode)myNodePopup.getClientProperty(KEY_VIEW_NODE));
      }
    }
  }

  public void goBackSubView() {
    assert(!mySubviewList.isEmpty());
    ViewNode lastNode = mySubviewList.get(mySubviewList.size() - 1);
    if (lastNode == null) return;
    updatePreview(lastNode);
    mySubviewList.remove(lastNode);
  }

  @NotNull
  public ObservableList<ViewNode> getSubviewList() {
    return mySubviewList;
  }

  @VisibleForTesting
  public void showSubView(@NotNull ViewNode node) {
    ViewNode root = getRoot();
    updatePreview(node);
    mySubviewList.add(root);
  }

  private void updatePreview(@NotNull ViewNode node) {
    if (myWindow == null) return;

    byte[] bytes = myWindow.loadViewImage(node, 10, TimeUnit.SECONDS);

    if (bytes == null) return;

    try {
      myBufferedImage = ImageIO.read(new ByteArrayInputStream(bytes));
    }
    catch (IOException e) {
      getLogger().warn(e);
    }

    myRoot = node;
    myPreview.setPreview(myBufferedImage, node);
    myNodeTree = createNodeTree(node);
    myPreview.repaint();
  }
}
