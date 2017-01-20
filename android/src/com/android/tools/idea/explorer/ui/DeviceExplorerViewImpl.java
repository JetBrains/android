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
package com.android.tools.idea.explorer.ui;

import com.android.tools.idea.explorer.*;
import com.android.tools.idea.explorer.fs.DeviceFileSystem;
import com.android.tools.idea.explorer.fs.DeviceFileSystemRenderer;
import com.android.tools.idea.explorer.fs.DeviceFileSystemService;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SideBorder;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.treeStructure.Tree;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

public class DeviceExplorerViewImpl implements DeviceExplorerView {
  @NotNull private final List<DeviceExplorerViewListener> myListeners = new ArrayList<>();
  @NotNull private final Project myProject;
  @NotNull private final ToolWindow myToolWindow;
  @NotNull private final DeviceFileSystemRenderer myDeviceRenderer;
  @Nullable private JBLoadingPanel myLoadingPanel;
  @Nullable private DeviceExplorerPanel myPanel;
  private int myTreeLoadingCount;

  public DeviceExplorerViewImpl(@NotNull Project project,
                                @NotNull ToolWindow toolWindow,
                                @NotNull DeviceFileSystemRenderer deviceRenderer,
                                @NotNull DeviceExplorerModel model) {
    myProject = project;
    myToolWindow = toolWindow;
    model.addListener(new ModelListener());
    myDeviceRenderer = deviceRenderer;
  }

  @TestOnly
  @Nullable
  public JComboBox<DeviceFileSystem> getDeviceCombo() {
    return myPanel != null ? myPanel.getDeviceCombo() : null;
  }

  @TestOnly
  @Nullable
  public JTree getFileTree() {
    return myPanel != null ? myPanel.getTree() : null;
  }

  @TestOnly
  @Nullable
  public JBLoadingPanel getLoadingPanel() { return myLoadingPanel;}

  @TestOnly
  @Nullable
  public DeviceExplorerPanel getDeviceExplorerPanel() { return myPanel;}

  @Override
  public void addListener(@NotNull DeviceExplorerViewListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void removeListener(@NotNull DeviceExplorerViewListener listener) {
    myListeners.remove(listener);
  }

  @Override
  public void setup() {
    myToolWindow.setIcon(AndroidIcons.AndroidToolWindow);
    myToolWindow.setAvailable(true, null);
    myToolWindow.setToHideOnEmptyContent(true);
    myToolWindow.setTitle(DeviceExplorerToolWindowFactory.TOOL_WINDOW_ID);

    setupPanel();
  }

  @Override
  public void reportErrorRelatedToService(@NotNull DeviceFileSystemService service, @NotNull String message, @NotNull Throwable t) {
    assert myLoadingPanel != null;

    reportError(message, t);

    //TODO: Show dedicated error panel
    myLoadingPanel.setLoadingText(String.format("Error initializing Android Debug Bridge: %s", t.getMessage()));
    myLoadingPanel.startLoading();
  }

  @Override
  public void reportErrorRelatedToDevice(@NotNull DeviceFileSystem fileSystem, @NotNull String message, @NotNull Throwable t) {
    reportError(message, t);
  }

  @Override
  public void reportErrorRelatedToNode(@NotNull DeviceFileEntryNode node, @NotNull String message, @NotNull Throwable t) {
    reportError(message, t);
  }

  private static void reportError(@NotNull String msg, @NotNull Throwable t) {
    if (t instanceof CancellationException) {
      return;
    }

    if (t.getMessage() != null) {
      msg += ": " + t.getMessage();
    }

    Notification notification = new Notification(DeviceExplorerToolWindowFactory.TOOL_WINDOW_ID,
                                                 DeviceExplorerToolWindowFactory.TOOL_WINDOW_ID,
                                                 msg,
                                                 NotificationType.WARNING);

    ApplicationManager.getApplication().invokeLater(() -> Notifications.Bus.notify(notification));
  }

  private void setupPanel() {
    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), myProject);
    final ContentManager contentManager = myToolWindow.getContentManager();
    Content c = contentManager.getFactory().createContent(myLoadingPanel, "", true);
    contentManager.addContent(c);

    myPanel = new DeviceExplorerPanel();
    myPanel.getComponent().setBorder(IdeBorderFactory.createBorder(SideBorder.BOTTOM));
    myLoadingPanel.add(myPanel.getComponent(), BorderLayout.CENTER);

    //noinspection GtkPreferredJComboBoxRenderer
    myPanel.getDeviceCombo().setRenderer(myDeviceRenderer.getDeviceNameListRenderer());

    myPanel.getDeviceCombo().addActionListener(actionEvent -> {
      Object sel = myPanel.getDeviceCombo().getSelectedItem();
      DeviceFileSystem device = (sel instanceof DeviceFileSystem) ? (DeviceFileSystem)sel : null;
      myListeners.forEach(x -> x.deviceSelected(device));
    });

    Tree tree = myPanel.getTree();
    tree.addTreeWillExpandListener(new TreeWillExpandListener() {
      @Override
      public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {

        DeviceFileEntryNode node = DeviceFileEntryNode.fromNode(event.getPath().getLastPathComponent());
        if (node != null) {
          expandTreeNode(node);
        }
      }

      @Override
      public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException {
      }
    });
    tree.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        // Double click on a file should result in a file open
        if (e.getClickCount() == 2) {
          int selRow = tree.getRowForLocation(e.getX(), e.getY());
          TreePath selPath = tree.getPathForLocation(e.getX(), e.getY());
          if (selRow != -1) {
            openSelectedNode(selPath);
          }
        }
      }
    });
    tree.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (KeyEvent.VK_ENTER == e.getKeyCode()) {
          openSelectedNode(tree.getSelectionPath());
        }
      }
    });

    myLoadingPanel.setLoadingText("Initializing ADB");
    myLoadingPanel.startLoading();
  }

  private void openSelectedNode(@Nullable TreePath selPath) {
    if (selPath == null) {
      return;
    }
    DeviceFileEntryNode node = DeviceFileEntryNode.fromNode(selPath.getLastPathComponent());
    if (node == null) {
      return;
    }
    if (node.getEntry().isFile()) {
      myListeners.forEach(x -> x.treeNodeActionPerformed(node));
    }
  }

  @Override
  public void serviceSetupSuccess() {
    assert myLoadingPanel != null;

    myLoadingPanel.stopLoading();
  }

  public void setRootFolder(@Nullable DefaultTreeModel model) {
    assert myPanel != null;

    Tree tree = myPanel.getTree();
    tree.setModel(model);

    if (model != null) {
      DeviceFileEntryNode rootNode = DeviceFileEntryNode.fromNode(model.getRoot());
      if (rootNode != null) {
        tree.setRootVisible(false);
        expandTreeNode(rootNode);
      }
    }
  }

  @Override
  public void startTreeBusyIndicator() {
    incrementTreeLoading();
  }

  @Override
  public void stopTreeBusyIndicator() {
    decrementTreeLoading();
  }

  private void expandTreeNode(@NotNull DeviceFileEntryNode node) {
    myListeners.forEach(x -> x.treeNodeExpanding(node));
  }

  private void incrementTreeLoading() {
    assert myPanel != null;

    if (myTreeLoadingCount == 0) {
      myPanel.getTree().setPaintBusy(true);
    }
    myTreeLoadingCount++;
  }

  private void decrementTreeLoading() {
    assert myPanel != null;

    myTreeLoadingCount--;
    if (myTreeLoadingCount == 0) {
      myPanel.getTree().setPaintBusy(false);
    }
  }

  private class ModelListener implements DeviceExplorerModelListener {
    @Override
    public void allDevicesRemoved() {
      if (myPanel != null) {
        myPanel.getDeviceCombo().removeAllItems();
      }
    }

    @Override
    public void deviceAdded(@NotNull DeviceFileSystem device) {
      if (myPanel != null) {
        myPanel.getDeviceCombo().addItem(device);
      }
    }

    @Override
    public void deviceRemoved(@NotNull DeviceFileSystem device) {
      if (myPanel != null) {
        myPanel.getDeviceCombo().removeItem(device);
      }
    }

    @Override
    public void deviceUpdated(@NotNull DeviceFileSystem device) {
      if (myPanel != null) {
        if (myPanel.getDeviceCombo().getSelectedItem() == device) {
          myPanel.getDeviceCombo().repaint();
        }
      }
    }

    @Override
    public void activeDeviceChanged(@Nullable DeviceFileSystem newActiveDevice) {
    }

    @Override
    public void treeModelChanged(@Nullable DefaultTreeModel newTreeModel) {
      setRootFolder(newTreeModel);
    }
  }
}
