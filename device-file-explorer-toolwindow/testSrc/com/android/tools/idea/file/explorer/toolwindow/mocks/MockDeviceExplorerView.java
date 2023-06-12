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
package com.android.tools.idea.file.explorer.toolwindow.mocks;

import com.android.tools.idea.FutureValuesTracker;
import com.android.tools.idea.file.explorer.toolwindow.DeviceExplorerModel;
import com.android.tools.idea.file.explorer.toolwindow.DeviceExplorerModelListener;
import com.android.tools.idea.file.explorer.toolwindow.DeviceExplorerView;
import com.android.tools.idea.file.explorer.toolwindow.DeviceExplorerViewListener;
import com.android.tools.idea.file.explorer.toolwindow.DeviceExplorerViewProgressListener;
import com.android.tools.idea.file.explorer.toolwindow.DeviceFileEntryNode;
import com.android.tools.idea.file.explorer.toolwindow.fs.DeviceFileSystem;
import com.android.tools.idea.file.explorer.toolwindow.fs.DeviceFileSystemRenderer;
import com.android.tools.idea.file.explorer.toolwindow.fs.DeviceFileSystemService;
import com.android.tools.idea.file.explorer.toolwindow.ui.DeviceExplorerViewImpl;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.project.Project;
import java.awt.Dimension;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JTree;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultTreeSelectionModel;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings({"NullableProblems", "unused"})
public class MockDeviceExplorerView implements DeviceExplorerView {
  @NotNull private final List<DeviceExplorerViewListener> myListeners = new ArrayList<>();
  @NotNull private final List<DeviceExplorerViewProgressListener> myProgressListeners = new ArrayList<>();
  @NotNull private final DeviceExplorerViewImpl myViewImpl;
  @NotNull private final FutureValuesTracker<String> myStartRefreshTracker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<Unit> myStopRefreshTracker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<Unit> myShowNoDeviceScreenTracker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<DeviceFileSystem> myDeviceSelectedTracker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<DeviceFileEntryNode> myTreeNodeExpandingTracker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<List<DeviceFileEntryNode>> myOpenNodesInEditorInvokedTracker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<List<DeviceFileEntryNode>> mySaveNodesAsTracker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<List<DeviceFileEntryNode>> myCopyNodePathsTracker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<List<DeviceFileEntryNode>> myDeleteNodesTracker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<List<DeviceFileEntryNode>> mySynchronizeNodesTracker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<DeviceFileEntryNode> myUploadFilesTracker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<DeviceFileEntryNode> myNewDirectoryTracker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<DeviceFileEntryNode> myNewFileTracker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<DeviceFileSystem> myDeviceAddedTracker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<DeviceFileSystem> myDeviceRemovedTracker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<DeviceFileSystem> myDeviceUpdatedTracker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<DefaultTreeModel> myTreeModelChangedTracker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<TreeModelEvent> myTreeNodesChangedTacker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<TreeModelEvent> myTreeNodesInsertedTacker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<TreeModelEvent> myTreeNodesRemovedTacker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<TreeModelEvent> myTreeStructureChangedTacker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<String> myReportErrorRelatedToServiceTracker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<String> myReportErrorRelatedToDeviceTracker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<String> myReportErrorRelatedToNodeTracker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<String> myReportErrorGenericTracker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<String> myReportMessageRelatedToNodeTracker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<Unit> myStartTreeBusyIndicatorTacker = new FutureValuesTracker<>();
  @NotNull private final FutureValuesTracker<Unit> myStopTreeBusyIndicatorTacker = new FutureValuesTracker<>();
  private int myBusyIndicatorCount;

  public MockDeviceExplorerView(@NotNull Project project,
                                @NotNull DeviceFileSystemRenderer deviceRenderer,
                                @NotNull DeviceExplorerModel model) {
    myViewImpl = new DeviceExplorerViewImpl(project, deviceRenderer, model);
    myViewImpl.addListener(new MyDeviceExplorerViewListener());
    myViewImpl.addProgressListener(new MyDeviceExplorerViewProgressListener());
    model.addListener(new MyDeviceExplorerModelListener());
  }

  public JComboBox<DeviceFileSystem> getDeviceCombo() {
    return myViewImpl.getDeviceCombo();
  }

  public JTree getTree() {
    return myViewImpl.getFileTree();
  }

  public ActionGroup getFileTreeActionGroup() {
    return myViewImpl.getFileTreeActionGroup();
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

  public void cancelTransfer() {
    for (DeviceExplorerViewProgressListener listener : myProgressListeners) {
      listener.cancellationRequested();
    }
  }

  @Override
  public void setup() {
    myViewImpl.setup();

    // Force a layout so that the panel, tree view, combo, etc. have a non-empty size
    assert myViewImpl.getDeviceExplorerPanel() != null;

    myViewImpl.getLoadingPanel().setSize(new Dimension(100, 300));
    myViewImpl.getDeviceExplorerPanel().getComponent().setSize(new Dimension(100, 300));
    myViewImpl.getDeviceExplorerPanel().getDeviceCombo().setSize(new Dimension(100, 30));
    myViewImpl.getDeviceExplorerPanel().getColumnTreePane().setSize(new Dimension(100, 300));

    myViewImpl.getLoadingPanel().doLayout();
    myViewImpl.getDeviceExplorerPanel().getComponent().doLayout();
    myViewImpl.getDeviceExplorerPanel().getDeviceCombo().doLayout();
    myViewImpl.getDeviceExplorerPanel().getColumnTreePane().doLayout();
    myViewImpl.getDeviceExplorerPanel().getColumnTreePane().getViewport().doLayout();
    myViewImpl.getDeviceExplorerPanel().getColumnTreePane().getViewport().getView().doLayout();
  }

  @Override
  public void startRefresh(@NotNull String text) {
    myViewImpl.startRefresh(text);
    myStartRefreshTracker.produce(text);
  }

  @Override
  public void stopRefresh() {
    myViewImpl.stopRefresh();
    myStopRefreshTracker.produce(null);
  }

  @Override
  public void showNoDeviceScreen() {
    myViewImpl.showNoDeviceScreen();
    myShowNoDeviceScreenTracker.produce(null);
  }

  @Override
  public void reportErrorRelatedToService(@NotNull DeviceFileSystemService service, @NotNull String message, @NotNull Throwable t) {
    myReportErrorRelatedToServiceTracker.produce(message + getThrowableMessage(t));
    myViewImpl.reportErrorRelatedToService(service, message, t);
  }

  @Override
  public void reportErrorRelatedToDevice(@NotNull DeviceFileSystem fileSystem, @NotNull String message, @NotNull Throwable t) {
    myReportErrorRelatedToDeviceTracker.produce(message + getThrowableMessage(t));
    myViewImpl.reportErrorRelatedToDevice(fileSystem, message, t);
  }

  @Override
  public void reportErrorRelatedToNode(@NotNull DeviceFileEntryNode node, @NotNull String message, @NotNull Throwable t) {
    myReportErrorRelatedToNodeTracker.produce(message + getThrowableMessage(t));
    myViewImpl.reportErrorRelatedToNode(node, message, t);
  }

  @Override
  public void reportErrorGeneric(@NotNull String message, @NotNull Throwable t) {
    myReportErrorGenericTracker.produce(message + getThrowableMessage(t));
    myViewImpl.reportErrorGeneric(message, t);
  }

  @Override
  public void reportMessageRelatedToDevice(@NotNull DeviceFileSystem fileSystem, @NotNull String message) {
    myViewImpl.reportMessageRelatedToDevice(fileSystem, message);
  }

  @Override
  public void reportMessageRelatedToNode(@NotNull DeviceFileEntryNode node, @NotNull String message) {
    myReportMessageRelatedToNodeTracker.produce(message);
    myViewImpl.reportMessageRelatedToNode(node, message);
  }

  @NotNull
  private static String getThrowableMessage(@NotNull Throwable t) {
    return t.getMessage() == null ? "" : ": " + t.getMessage();
  }

  @Override
  public void startTreeBusyIndicator() {
    if (myBusyIndicatorCount == 0) {
      myStartTreeBusyIndicatorTacker.produce(null);
    }
    myBusyIndicatorCount++;

    myViewImpl.startTreeBusyIndicator();
  }

  @Override
  public void stopTreeBusyIndicator() {
    myBusyIndicatorCount--;
    if (myBusyIndicatorCount == 0) {
      myStopTreeBusyIndicatorTacker.produce(null);
    }
    myViewImpl.stopTreeBusyIndicator();
  }

  @Override
  public void expandNode(@NotNull DeviceFileEntryNode treeNode) {
    myViewImpl.expandNode(treeNode);
  }

  @Override
  public void startProgress() {
    myViewImpl.startProgress();
  }

  @Override
  public void setProgressIndeterminate(boolean indeterminate) {
    myViewImpl.setProgressIndeterminate(indeterminate);
  }

  @Override
  public void setProgressValue(double fraction) {
    myViewImpl.setProgressValue(fraction);
  }

  @Override
  public void setProgressOkColor() {
    myViewImpl.setProgressOkColor();
  }

  @Override
  public void setProgressWarningColor() {
    myViewImpl.setProgressWarningColor();
  }

  @Override
  public void setProgressErrorColor() {
    myViewImpl.setProgressErrorColor();
  }

  @Override
  public void setProgressText(@NotNull String text) {
    myViewImpl.setProgressText(text);
  }

  @Override
  public void stopProgress() {
    myViewImpl.stopProgress();
  }

  public List<DeviceExplorerViewListener> getListeners() {
    return myListeners;
  }

  public FutureValuesTracker<DeviceFileSystem> getDeviceAddedTracker() {
    return myDeviceAddedTracker;
  }

  public FutureValuesTracker<DeviceFileSystem> getDeviceRemovedTracker() {
    return myDeviceRemovedTracker;
  }

  public FutureValuesTracker<DeviceFileSystem> getDeviceUpdatedTracker() {
    return myDeviceUpdatedTracker;
  }

  @NotNull
  public FutureValuesTracker<String> getStartRefreshTracker() {
    return myStartRefreshTracker;
  }

  @NotNull
  public FutureValuesTracker<Unit> getStopRefreshTracker() {
    return myStopRefreshTracker;
  }

  @NotNull
  public FutureValuesTracker<Unit> getShowNoDeviceScreenTracker() {
    return myShowNoDeviceScreenTracker;
  }

  @NotNull
  public FutureValuesTracker<DeviceFileSystem> getDeviceSelectedTracker() {
    return myDeviceSelectedTracker;
  }

  @NotNull
  public FutureValuesTracker<List<DeviceFileEntryNode>> getOpenNodesInEditorInvokedTracker() {
    return myOpenNodesInEditorInvokedTracker;
  }

  @NotNull
  public FutureValuesTracker<DeviceFileEntryNode> getTreeNodeExpandingTracker() {
    return myTreeNodeExpandingTracker;
  }

  @NotNull
  public FutureValuesTracker<TreeModelEvent> getTreeNodesChangedTacker() {
    return myTreeNodesChangedTacker;
  }

  @NotNull
  public FutureValuesTracker<TreeModelEvent> getTreeNodesInsertedTacker() {
    return myTreeNodesInsertedTacker;
  }

  @NotNull
  public FutureValuesTracker<TreeModelEvent> getTreeNodesRemovedTacker() {
    return myTreeNodesRemovedTacker;
  }

  @NotNull
  public FutureValuesTracker<TreeModelEvent> getTreeStructureChangedTacker() {
    return myTreeStructureChangedTacker;
  }

  @NotNull
  public FutureValuesTracker<DefaultTreeModel> getTreeModelChangedTracker() {
    return myTreeModelChangedTracker;
  }

  @NotNull
  public FutureValuesTracker<String> getReportErrorRelatedToServiceTracker() {
    return myReportErrorRelatedToServiceTracker;
  }

  @NotNull
  public FutureValuesTracker<String> getReportErrorRelatedToDeviceTracker() {
    return myReportErrorRelatedToDeviceTracker;
  }

  @NotNull
  public FutureValuesTracker<String> getReportErrorRelatedToNodeTracker() {
    return myReportErrorRelatedToNodeTracker;
  }

  @NotNull
  public FutureValuesTracker<String> getReportErrorGenericTracker() {
    return myReportErrorGenericTracker;
  }

  @NotNull
  public FutureValuesTracker<String> getReportMessageRelatedToNodeTracker() {
    return myReportMessageRelatedToNodeTracker;
  }

  @NotNull
  public FutureValuesTracker<Unit> getStartTreeBusyIndicatorTacker() {
    return myStartTreeBusyIndicatorTacker;
  }

  @NotNull
  public FutureValuesTracker<Unit> getStopTreeBusyIndicatorTacker() {
    return myStopTreeBusyIndicatorTacker;
  }

  @NotNull
  public FutureValuesTracker<List<DeviceFileEntryNode>> getSaveNodesAsTracker() {
    return mySaveNodesAsTracker;
  }

  @NotNull
  public FutureValuesTracker<List<DeviceFileEntryNode>> getCopyNodePathsTracker() {
    return myCopyNodePathsTracker;
  }

  @NotNull
  public FutureValuesTracker<List<DeviceFileEntryNode>> getDeleteNodesTracker() {
    return myDeleteNodesTracker;
  }

  @NotNull
  public FutureValuesTracker<DeviceFileEntryNode> getUploadFilesTracker() {
    return myUploadFilesTracker;
  }

  public void deviceTreeModelUpdated(@Nullable DeviceFileSystem device,
                                     @Nullable DefaultTreeModel model,
                                     @Nullable DefaultTreeSelectionModel selectionModel) {
    if (model != null) {
      model.addTreeModelListener(new MyTreeModelListener());
    }
  }

  @NotNull
  public FutureValuesTracker<List<DeviceFileEntryNode>> getSynchronizeNodesTracker() {
    return mySynchronizeNodesTracker;
  }

  private class MyDeviceExplorerViewListener implements DeviceExplorerViewListener {

    @Override
    public void noDeviceSelected() {
      myListeners.forEach(DeviceExplorerViewListener::noDeviceSelected);
    }

    @Override
    public void deviceSelected(@NotNull DeviceFileSystem device) {
      myDeviceSelectedTracker.produce(device);
      myListeners.forEach(l -> l.deviceSelected(device));
    }

    @Override
    public void openNodesInEditorInvoked(@NotNull List<DeviceFileEntryNode> treeNodes) {
      myOpenNodesInEditorInvokedTracker.produce(treeNodes);
      myListeners.forEach(l -> l.openNodesInEditorInvoked(treeNodes));
    }

    @Override
    public void treeNodeExpanding(@NotNull DeviceFileEntryNode treeNode) {
      myTreeNodeExpandingTracker.produce(treeNode);
      myListeners.forEach(l -> l.treeNodeExpanding(treeNode));
    }

    @Override
    public void saveNodesAsInvoked(@NotNull List<DeviceFileEntryNode> treeNodes) {
      mySaveNodesAsTracker.produce(treeNodes);
      myListeners.forEach(l -> l.saveNodesAsInvoked(treeNodes));
    }

    @Override
    public void copyNodePathsInvoked(@NotNull List<DeviceFileEntryNode> treeNodes) {
      myCopyNodePathsTracker.produce(treeNodes);
      myListeners.forEach(l -> l.copyNodePathsInvoked(treeNodes));
    }

    @Override
    public void newDirectoryInvoked(@NotNull DeviceFileEntryNode parentTreeNode) {
      myNewDirectoryTracker.produce(parentTreeNode);
      myListeners.forEach(l -> l.newDirectoryInvoked(parentTreeNode));
    }

    @Override
    public void newFileInvoked(@NotNull DeviceFileEntryNode parentTreeNode) {
      myNewFileTracker.produce(parentTreeNode);
      myListeners.forEach(l -> l.newFileInvoked(parentTreeNode));
    }

    @Override
    public void deleteNodesInvoked(@NotNull List<DeviceFileEntryNode> treeNodes) {
      myDeleteNodesTracker.produce(treeNodes);
      myListeners.forEach(l -> l.deleteNodesInvoked(treeNodes));
    }

    @Override
    public void uploadFilesInvoked(@NotNull DeviceFileEntryNode treeNode) {
      myUploadFilesTracker.produce(treeNode);
      myListeners.forEach(l -> l.uploadFilesInvoked(treeNode));
    }

    @Override
    public void uploadFilesInvoked(@NotNull DeviceFileEntryNode treeNode, List<Path> files) {
      myUploadFilesTracker.produce(treeNode);
      myListeners.forEach(l -> l.uploadFilesInvoked(treeNode, files));
    }

    @Override
    public void synchronizeNodesInvoked(@NotNull List<DeviceFileEntryNode> treeNodes) {
      mySynchronizeNodesTracker.produce(treeNodes);
      myListeners.forEach(l -> l.synchronizeNodesInvoked(treeNodes));
    }
  }

  private class MyDeviceExplorerViewProgressListener implements DeviceExplorerViewProgressListener {
    @Override
    public void cancellationRequested() {
      myProgressListeners.forEach(DeviceExplorerViewProgressListener::cancellationRequested);
    }
  }

  private class MyDeviceExplorerModelListener implements DeviceExplorerModelListener {

    @Override
    public void deviceAdded(@NotNull DeviceFileSystem device) {
      myDeviceAddedTracker.produce(device);
    }

    @Override
    public void deviceRemoved(@NotNull DeviceFileSystem device) {
      myDeviceRemovedTracker.produce(device);
    }

    @Override
    public void deviceUpdated(@NotNull DeviceFileSystem device) {
      myDeviceUpdatedTracker.produce(device);
    }

    @Override
    public void activeDeviceChanged(@Nullable DeviceFileSystem newActiveDevice) {
    }

    @Override
    public void treeModelChanged(@Nullable DefaultTreeModel newTreeModel, @Nullable DefaultTreeSelectionModel newTreeSelectionModel) {
      myTreeModelChangedTracker.produce(newTreeModel);
    }
  }

  private class MyTreeModelListener implements TreeModelListener {
    @Override
    public void treeNodesChanged(TreeModelEvent e) {
      myTreeNodesChangedTacker.produce(e);
    }

    @Override
    public void treeNodesInserted(TreeModelEvent e) {
      myTreeNodesInsertedTacker.produce(e);
    }

    @Override
    public void treeNodesRemoved(TreeModelEvent e) {
      myTreeNodesRemovedTacker.produce(e);
    }

    @Override
    public void treeStructureChanged(TreeModelEvent e) {
      myTreeStructureChangedTacker.produce(e);
    }
  }
}
