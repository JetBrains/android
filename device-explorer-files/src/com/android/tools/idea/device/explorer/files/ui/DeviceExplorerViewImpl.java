/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.device.explorer.files.ui;

import static java.awt.event.InputEvent.CTRL_DOWN_MASK;
import static java.awt.event.InputEvent.SHIFT_DOWN_MASK;

import com.android.tools.idea.device.explorer.files.DeviceExplorerModel;
import com.android.tools.idea.device.explorer.files.DeviceExplorerModelListener;
import com.android.tools.idea.device.explorer.files.DeviceExplorerToolWindowFactory;
import com.android.tools.idea.device.explorer.files.DeviceExplorerView;
import com.android.tools.idea.device.explorer.files.DeviceExplorerViewListener;
import com.android.tools.idea.device.explorer.files.DeviceExplorerViewProgressListener;
import com.android.tools.idea.device.explorer.files.DeviceFileEntryNode;
import com.android.tools.idea.device.explorer.files.fs.DeviceFileSystem;
import com.android.tools.idea.device.explorer.files.fs.DeviceFileSystemRenderer;
import com.android.tools.idea.device.explorer.files.fs.DeviceFileSystemService;
import com.intellij.icons.AllIcons;
import com.intellij.ide.dnd.FileCopyPasteUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.treeStructure.Tree;
import icons.AndroidIcons;
import java.awt.BorderLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;
import javax.swing.Icon;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.TransferHandler;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultTreeSelectionModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreePath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public class DeviceExplorerViewImpl implements DeviceExplorerView {
  @NotNull private final List<DeviceExplorerViewListener> myListeners = new ArrayList<>();
  @NotNull private final List<DeviceExplorerViewProgressListener> myProgressListeners = new ArrayList<>();
  @NotNull private final DeviceFileSystemRenderer myDeviceRenderer;
  @NotNull private final DeviceExplorerPanel myPanel;
  @NotNull private final JBLoadingPanel myLoadingPanel;
  @Nullable private ComponentPopupMenu myTreePopupMenu;
  private int myTreeLoadingCount;

  public DeviceExplorerViewImpl(@NotNull Project project,
                                @NotNull DeviceFileSystemRenderer renderer,
                                @NotNull DeviceExplorerModel model) {
    model.addListener(new ModelListener());
    myDeviceRenderer = renderer;
    myPanel = new DeviceExplorerPanel();
    myPanel.setCancelActionListener(e -> myProgressListeners.forEach(DeviceExplorerViewProgressListener::cancellationRequested));
    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), project);
  }

  @NotNull
  public JComponent getComponent() {
    return myLoadingPanel;
  }

  @TestOnly
  @Nullable
  public JComboBox<DeviceFileSystem> getDeviceCombo() {
    return myPanel.getDeviceCombo();
  }

  @TestOnly
  @Nullable
  public JTree getFileTree() {
    return myPanel.getTree();
  }

  @TestOnly
  @Nullable
  public ActionGroup getFileTreeActionGroup() {
    return myTreePopupMenu == null ? null : myTreePopupMenu.getActionGroup();
  }

  @TestOnly
  @NotNull
  public JBLoadingPanel getLoadingPanel() {
    return myLoadingPanel;
  }

  @TestOnly
  @Nullable
  public DeviceExplorerPanel getDeviceExplorerPanel() {
    return myPanel;
  }

  @Override
  public void addListener(@NotNull DeviceExplorerViewListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void removeListener(@NotNull DeviceExplorerViewListener listener) {
    myListeners.remove(listener);
  }

  @Override
  public void addProgressListener(@NotNull DeviceExplorerViewProgressListener listener) {
    myProgressListeners.add(listener);
  }

  @Override
  public void removeProgressListener(@NotNull DeviceExplorerViewProgressListener listener) {
    myProgressListeners.remove(listener);
  }

  @Override
  public void setup() {
    setupPanel();
  }

  @Override
  public void reportErrorRelatedToService(@NotNull DeviceFileSystemService service, @NotNull String message, @NotNull Throwable t) {
    if (t.getMessage() != null) {
      message += ": " + t.getMessage();
    }

    // If the file system service (i.e. ADB under the hood) had an error, there are no devices
    // to show until the user takes an action, so we show the error "layer", hiding the other
    // controls.
    myPanel.showErrorMessageLayer(message, false);
  }

  @Override
  public void reportErrorRelatedToDevice(@NotNull DeviceFileSystem fileSystem, @NotNull String message, @NotNull Throwable t) {
    if (t.getMessage() != null) {
      message += ": " + t.getMessage();
    }

    // If there is an error related to a device, show the error "layer", hiding the other
    // controls, until the user takes some action to fix the issue.
    myPanel.showErrorMessageLayer(message, true);
  }

  @Override
  public void reportErrorRelatedToNode(@NotNull DeviceFileEntryNode node, @NotNull String message, @NotNull Throwable t) {
    reportError(message, t);
  }

  @Override
  public void reportErrorGeneric(@NotNull String message, @NotNull Throwable t) {
    reportError(message, t);
  }

  @Override
  public void reportMessageRelatedToDevice(@NotNull DeviceFileSystem fileSystem, @NotNull String message) {
    myPanel.showMessageLayer(message, true);
  }

  @Override
  public void reportMessageRelatedToNode(@NotNull DeviceFileEntryNode node, @NotNull String message) {
    reportMessage(message);
  }

  private static void reportMessage(@NotNull String message) {
    Notification notification = new Notification(DeviceExplorerToolWindowFactory.TOOL_WINDOW_ID,
                                                 DeviceExplorerToolWindowFactory.TOOL_WINDOW_ID,
                                                 message,
                                                 NotificationType.INFORMATION);

    ApplicationManager.getApplication().invokeLater(() -> Notifications.Bus.notify(notification));
  }

  private static void reportError(@NotNull String message, @NotNull Throwable t) {
    if (t instanceof CancellationException) {
      return;
    }

    if (t.getMessage() != null) {
      message += ": " + t.getMessage();
    }

    Notification notification = new Notification(DeviceExplorerToolWindowFactory.TOOL_WINDOW_ID,
                                                 DeviceExplorerToolWindowFactory.TOOL_WINDOW_ID,
                                                 message,
                                                 NotificationType.WARNING);

    ApplicationManager.getApplication().invokeLater(() -> Notifications.Bus.notify(notification));
  }

  private void setupPanel() {
    myLoadingPanel.add(myPanel.getComponent(), BorderLayout.CENTER);

    myPanel.getDeviceCombo().setRenderer(myDeviceRenderer.getDeviceNameListRenderer());

    myPanel.getDeviceCombo().addActionListener(actionEvent -> {
      Object sel = myPanel.getDeviceCombo().getSelectedItem();
      if (sel instanceof DeviceFileSystem) {
        DeviceFileSystem device = (DeviceFileSystem)sel;
        myListeners.forEach(x -> x.deviceSelected(device));
      }
      else {
        myListeners.forEach(DeviceExplorerViewListener::noDeviceSelected);
      }
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
          if (selRow != -1 && selPath != null) {
            openSelectedNodes(Collections.singletonList(selPath));
          }
        }
      }
    });
    tree.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (KeyEvent.VK_ENTER == e.getKeyCode()) {
          TreePath[] paths = tree.getSelectionPaths();
          if (paths != null) {
            openSelectedNodes(Arrays.asList(paths));
          }
        }
      }
    });


    tree.setTransferHandler(new TransferHandler() {
      @Override
      public boolean importData(TransferSupport support) {
        Transferable t = support.getTransferable();
        final List<Path> files = FileCopyPasteUtil.getFiles(t);
        if (files == null) {
          return false;
        }
        Point point = support.getDropLocation().getDropPoint();
        TreePath treePath = tree.getPathForLocation((int)point.getX(), (int)point.getY());
        if (treePath == null) {
          return false;
        }
        DeviceFileEntryNode node = DeviceFileEntryNode.fromNode(treePath.getLastPathComponent());
        if (node != null && node.getEntry().isDirectory()) {
          myListeners.forEach(l -> l.uploadFilesInvoked(node, files));
          return true;
        } else {
          return false;
        }
      }

      @Override
      public boolean canImport(JComponent comp, DataFlavor[] transferFlavors) {
        return FileCopyPasteUtil.isFileListFlavorAvailable(transferFlavors);
      }
    });
    tree.setDragEnabled(!GraphicsEnvironment.isHeadless());


    createTreePopupMenu();
    myLoadingPanel.setLoadingText("Initializing ADB");
    myLoadingPanel.startLoading();
  }

  private void createTreePopupMenu() {
    myTreePopupMenu = new ComponentPopupMenu(myPanel.getTree());
    ComponentPopupMenu fileMenu = myTreePopupMenu.addPopup("New");
    fileMenu.addItem(new NewFileMenuItem());
    fileMenu.addItem(new NewDirectoryMenuItem());
    myTreePopupMenu.addSeparator();
    myTreePopupMenu.addItem(new OpenMenuItem());
    myTreePopupMenu.addItem(new SaveAsMenuItem());
    myTreePopupMenu.addItem(new UploadFilesMenuItem());
    myTreePopupMenu.addItem(new DeleteNodesMenuItem());
    myTreePopupMenu.addSeparator();
    myTreePopupMenu.addItem(new SynchronizeNodesMenuItem());
    myTreePopupMenu.addItem(new CopyPathMenuItem());
    myTreePopupMenu.install();
  }

  private void openSelectedNodes(@NotNull List<TreePath> paths) {
    List<DeviceFileEntryNode> nodes =
      paths.stream()
        .map(x -> DeviceFileEntryNode.fromNode(x.getLastPathComponent()))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
    openNodes(nodes);
  }

  private void copyNodePaths(@NotNull List<DeviceFileEntryNode> treeNodes) {
    myListeners.forEach(x -> x.copyNodePathsInvoked(treeNodes));
  }

  private void openNodes(@NotNull List<DeviceFileEntryNode> treeNodes) {
    myListeners.forEach(x -> x.openNodesInEditorInvoked(treeNodes));
  }

  private void saveNodesAs(@NotNull List<DeviceFileEntryNode> treeNodes) {
    myListeners.forEach(x -> x.saveNodesAsInvoked(treeNodes));
  }

  private void newDirectory(@NotNull DeviceFileEntryNode treeNode) {
    myListeners.forEach(x -> x.newDirectoryInvoked(treeNode));
  }

  private void newFile(@NotNull DeviceFileEntryNode treeNode) {
    myListeners.forEach(x -> x.newFileInvoked(treeNode));
  }

  private void deleteNodes(@NotNull List<DeviceFileEntryNode> treeNodes) {
    myListeners.forEach(x -> x.deleteNodesInvoked(treeNodes));
  }

  private void synchronizeNodes(@NotNull List<DeviceFileEntryNode> treeNodes) {
    myListeners.forEach(x -> x.synchronizeNodesInvoked(treeNodes));
  }

  private void uploadFiles(@NotNull DeviceFileEntryNode treeNode) {
    myListeners.forEach(x -> x.uploadFilesInvoked(treeNode));
  }

  @Override
  public void startRefresh(@NotNull String text) {
    myPanel.showMessageLayer("", false);
    myLoadingPanel.setLoadingText(text);
    myLoadingPanel.startLoading();
  }

  @Override
  public void stopRefresh() {
    myLoadingPanel.stopLoading();
  }

  @Override
  public void showNoDeviceScreen() {
    myPanel.showMessageLayer("Connect a device via USB cable or run an Android Virtual Device",
                             AndroidIcons.DeviceExplorer.DevicesLineup,
                             false);
  }

  public void setRootFolder(@Nullable DefaultTreeModel model, @Nullable DefaultTreeSelectionModel treeSelectionModel) {
    Tree tree = myPanel.getTree();
    tree.setModel(model);
    tree.setSelectionModel(treeSelectionModel);

    if (model != null) {
      myPanel.showTree();
      DeviceFileEntryNode rootNode = DeviceFileEntryNode.fromNode(model.getRoot());
      if (rootNode != null) {
        tree.setRootVisible(false);
        expandTreeNode(rootNode);
      }
      else {
        // Show root, since it contains an error message (ErrorNode)
        tree.setRootVisible(true);
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

  @Override
  public void expandNode(@NotNull DeviceFileEntryNode treeNode) {
    myPanel.getTree().expandPath(new TreePath(treeNode.getPath()));
  }

  @Override
  public void startProgress() {
    myPanel.getProgressPanel().start();
  }

  @Override
  public void setProgressIndeterminate(boolean indeterminate) {
    myPanel.getProgressPanel().setIndeterminate(indeterminate);
  }

  @Override
  public void setProgressValue(double fraction) {
    myPanel.getProgressPanel().setProgress(fraction);
  }

  @Override
  public void setProgressOkColor() {
    myPanel.getProgressPanel().setOkStatusColor();
  }

  @Override
  public void setProgressWarningColor() {
    myPanel.getProgressPanel().setWarningStatusColor();
  }

  @Override
  public void setProgressErrorColor() {
    myPanel.getProgressPanel().setErrorStatusColor();
  }

  @Override
  public void setProgressText(@NotNull String text) {
    myPanel.getProgressPanel().setText(text);
  }

  @Override
  public void stopProgress() {
    myPanel.getProgressPanel().stop();
  }

  private void expandTreeNode(@NotNull DeviceFileEntryNode node) {
    myListeners.forEach(x -> x.treeNodeExpanding(node));
  }

  private void incrementTreeLoading() {
    if (myTreeLoadingCount == 0) {
      myPanel.getTree().setPaintBusy(true);
    }
    myTreeLoadingCount++;
  }

  private void decrementTreeLoading() {
    myTreeLoadingCount--;
    if (myTreeLoadingCount == 0) {
      myPanel.getTree().setPaintBusy(false);
    }
  }

  private class ModelListener implements DeviceExplorerModelListener {

    @Override
    public void deviceAdded(@NotNull DeviceFileSystem device) {
      myPanel.getDeviceCombo().addItem(device);
    }

    @Override
    public void deviceRemoved(@NotNull DeviceFileSystem device) {
      myPanel.getDeviceCombo().removeItem(device);
    }

    @Override
    public void deviceUpdated(@NotNull DeviceFileSystem device) {
      if (myPanel.getDeviceCombo().getSelectedItem() == device) {
        myPanel.getDeviceCombo().repaint();
      }
    }

    @Override
    public void activeDeviceChanged(@Nullable DeviceFileSystem newActiveDevice) {
      if (newActiveDevice != null && !newActiveDevice.equals(myPanel.getDeviceCombo().getSelectedItem())) {
        myPanel.getDeviceCombo().setSelectedItem(newActiveDevice);
      }
    }

    @Override
    public void treeModelChanged(@Nullable DefaultTreeModel newTreeModel, @Nullable DefaultTreeSelectionModel newTreeSelectionModel) {
      setRootFolder(newTreeModel, newTreeSelectionModel);
    }
  }

  /**
   * A popup menu item that works for both single and multi-element selections.
   */
  private abstract class TreeMenuItem implements PopupMenuItem {
    @NotNull
    @Override
    public String getText() {
      List<DeviceFileEntryNode> nodes = getSelectedNodes();
      if (nodes == null) {
        nodes = Collections.emptyList();
      }
      return getText(nodes);
    }

    @NotNull
    public abstract String getText(@NotNull List<@NotNull DeviceFileEntryNode> nodes);

    @Nullable
    @Override
    public Icon getIcon() {
      return null;
    }

    @Override
    public final boolean isEnabled() {
      List<DeviceFileEntryNode> nodes = getSelectedNodes();
      if (nodes == null) {
        return false;
      }
      return isEnabled(nodes);
    }

    public boolean isEnabled(@NotNull List<DeviceFileEntryNode> nodes) {
      return nodes.stream().anyMatch(this::isEnabled);
    }

    @Override
    public final boolean isVisible() {
      List<DeviceFileEntryNode> nodes = getSelectedNodes();
      if (nodes == null) {
        return false;
      }
      return isVisible(nodes);
    }

    public boolean isVisible(@NotNull List<DeviceFileEntryNode> nodes) {
      return nodes.stream().anyMatch(this::isVisible);
    }

    @Override
    public final void run() {
      List<DeviceFileEntryNode> nodes = getSelectedNodes();
      if (nodes == null) {
        return;
      }
      nodes = nodes.stream().filter(this::isEnabled).collect(Collectors.toList());
      if (!nodes.isEmpty()) {
        run(nodes);
      }
    }

    @Nullable
    private List<DeviceFileEntryNode> getSelectedNodes() {
      TreePath[] paths = myPanel.getTree().getSelectionPaths();
      if (paths == null) {
        return null;
      }
      List<DeviceFileEntryNode> nodes = Arrays.stream(paths)
        .map(path -> DeviceFileEntryNode.fromNode(path.getLastPathComponent()))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
      if (nodes.isEmpty()) {
        return null;
      }
      return nodes;
    }

    public boolean isVisible(@NotNull DeviceFileEntryNode node) {
      return true;
    }

    public boolean isEnabled(@NotNull DeviceFileEntryNode node) {
      return isVisible(node);
    }

    public abstract void run(@NotNull List<DeviceFileEntryNode> nodes);
  }

  /**
   * A {@link TreeMenuItem} that is active only for single element selections
   */
  private abstract class SingleSelectionTreeMenuItem extends TreeMenuItem {
    @Override
    public boolean isEnabled(@NotNull List<DeviceFileEntryNode> nodes) {
      return super.isEnabled(nodes) && nodes.size() == 1;
    }

    @Override
    public boolean isVisible(@NotNull List<DeviceFileEntryNode> nodes) {
      return super.isVisible(nodes) && nodes.size() == 1;
    }

    @Override
    public void run(@NotNull List<DeviceFileEntryNode> nodes) {
      if (nodes.size() == 1) {
        run(nodes.get(0));
      }
    }

    public abstract void run(@NotNull DeviceFileEntryNode node);
  }

  private class CopyPathMenuItem extends TreeMenuItem {
    @NotNull
    @Override
    public String getText(@NotNull List<@NotNull DeviceFileEntryNode> nodes) {
      if (nodes.size() > 1) {
        return "Copy Paths";
      }
      return "Copy Path";
    }

    @Override
    public String getShortcutId() {
      return "CopyPaths"; // Re-use shortcut from existing action
    }

    @Nullable
    @Override
    public Icon getIcon() {
      return AllIcons.Actions.Copy;
    }

    @Override
    public boolean isVisible(@NotNull DeviceFileEntryNode node) {
      return true;
    }

    @Override
    public void run(@NotNull List<DeviceFileEntryNode> nodes) {
      copyNodePaths(nodes);
    }
  }

  private class OpenMenuItem extends TreeMenuItem {
    @NotNull
    @Override
    public String getText(@NotNull List<@NotNull DeviceFileEntryNode> nodes) {
      return "Open";
    }

    @Nullable
    @Override
    public Icon getIcon() {
      return AllIcons.Actions.MenuOpen;
    }

    @Nullable
    @Override
    public String getShortcutId() {
      // Re-use existing shortcut, see platform/platform-resources/src/keymaps/$default.xml
      return "OpenFile";
    }

    @Override
    public boolean isVisible(@NotNull DeviceFileEntryNode node) {
      return node.getEntry().isFile();
    }

    @Override
    public void run(@NotNull List<DeviceFileEntryNode> nodes) {
      openNodes(nodes);
    }
  }

  private class SaveAsMenuItem extends TreeMenuItem {
    @NotNull
    @Override
    public String getText(@NotNull List<@NotNull DeviceFileEntryNode> nodes) {
      if (nodes.size() > 1) {
        return "Save To...";
      }
      return "Save As...";
    }

    @Nullable
    @Override
    public Icon getIcon() {
      return AllIcons.Actions.MenuSaveall;
    }

    @Nullable
    @Override
    public Shortcut[] getShortcuts() {
      return new Shortcut[]{
        new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_S, CTRL_DOWN_MASK | SHIFT_DOWN_MASK), null),
        new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_D, CTRL_DOWN_MASK | SHIFT_DOWN_MASK), null),
      };
    }

    @Override
    public boolean isVisible(@NotNull DeviceFileEntryNode node) {
      return true;
    }

    @Override
    public void run(@NotNull List<DeviceFileEntryNode> nodes) {
      saveNodesAs(nodes);
    }
  }

  private class NewFileMenuItem extends SingleSelectionTreeMenuItem {
    @NotNull
    @Override
    public String getText(@NotNull List<@NotNull DeviceFileEntryNode> nodes) {
      return "File";
    }

    @Nullable
    @Override
    public Icon getIcon() {
      return AllIcons.FileTypes.Text;
    }

    @Override
    public boolean isVisible(@NotNull DeviceFileEntryNode node) {
      return node.getEntry().isDirectory() || node.isSymbolicLinkToDirectory();
    }

    @Override
    public void run(@NotNull DeviceFileEntryNode node) {
      newFile(node);
    }
  }

  private class NewDirectoryMenuItem extends SingleSelectionTreeMenuItem {
    @NotNull
    @Override
    public String getText(@NotNull List<@NotNull DeviceFileEntryNode> nodes) {
      return "Directory";
    }

    @Nullable
    @Override
    public Icon getIcon() {
      return AllIcons.Nodes.Folder;
    }

    @Override
    public boolean isVisible(@NotNull DeviceFileEntryNode node) {
      return node.getEntry().isDirectory() || node.isSymbolicLinkToDirectory();
    }

    @Override
    public void run(@NotNull DeviceFileEntryNode node) {
      newDirectory(node);
    }
  }

  private class DeleteNodesMenuItem extends TreeMenuItem {
    @NotNull
    @Override
    public String getText(@NotNull List<@NotNull DeviceFileEntryNode> nodes) {
      return "Delete...";
    }

    @Nullable
    @Override
    public Icon getIcon() {
      return AllIcons.Actions.Cancel;
    }

    @Override
    public boolean isVisible(@NotNull DeviceFileEntryNode node) {
      return true;
    }

    @Nullable
    @Override
    public String getShortcutId() {
      // Re-use existing shortcut, see platform/platform-resources/src/keymaps/$default.xml
      return "$Delete";
    }

    @Override
    public void run(@NotNull List<DeviceFileEntryNode> nodes) {
      deleteNodes(nodes);
    }
  }

  private class SynchronizeNodesMenuItem extends TreeMenuItem {
    @NotNull
    @Override
    public String getText(@NotNull List<@NotNull DeviceFileEntryNode> nodes) {
      return "Synchronize";
    }

    @Nullable
    @Override
    public Icon getIcon() {
      return AllIcons.Actions.Refresh;
    }

    @NotNull
    @Override
    public String getShortcutId() {
      // Re-use existing shortcut, see platform/platform-resources/src/keymaps/$default.xml
      return "Refresh";
    }

    @Override
    public boolean isVisible(@NotNull DeviceFileEntryNode node) {
      return true;
    }

    @Override
    public void run(@NotNull List<DeviceFileEntryNode> nodes) {
      synchronizeNodes(nodes);
    }
  }

  private class UploadFilesMenuItem extends SingleSelectionTreeMenuItem {
    @NotNull
    @Override
    public String getText(@NotNull List<@NotNull DeviceFileEntryNode> nodes) {
      return "Upload...";
    }

    @Nullable
    @Override
    public Shortcut[] getShortcuts() {
      return new Shortcut[]{
        new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_O, CTRL_DOWN_MASK | SHIFT_DOWN_MASK), null),
        new KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_U, CTRL_DOWN_MASK | SHIFT_DOWN_MASK), null),
      };
    }

    @Override
    public boolean isVisible(@NotNull DeviceFileEntryNode node) {
      return node.getEntry().isDirectory() || node.isSymbolicLinkToDirectory();
    }

    @Override
    public void run(@NotNull DeviceFileEntryNode node) {
      uploadFiles(node);
    }
  }
}
