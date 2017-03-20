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
package com.android.tools.idea.explorer;

import com.android.tools.idea.explorer.adbimpl.AdbPathUtil;
import com.android.tools.idea.explorer.fs.*;
import com.android.tools.idea.explorer.ui.TreeUtil;
import com.android.utils.FileUtils;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.CommonBundle;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.*;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import com.intellij.ui.UIBundle;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.tree.*;
import java.awt.datatransfer.StringSelection;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;


/**
 * Implementation of the Device Explorer application logic
 */
public class DeviceExplorerController {
  @NotNull
  private static final Logger LOGGER = Logger.getInstance(DeviceExplorerController.class);
  @NotNull
  private static final Key<DeviceExplorerController> KEY = Key.create(DeviceExplorerController.class.getName());
  @NotNull
  private static final String DEVICE_EXPLORER_BUSY_MESSAGE = "Device Explorer is busy, please retry later or cancel current operation";
  private static final long FILE_ENTRY_CREATION_TIMEOUT_MILLIS = 10_000;
  private static final long FILE_ENTRY_DELETION_TIMEOUT_MILLIS = 10_000;

  private int myShowLoadingNodeDelayMillis = 200;
  private int myTransferringNodeRepaintMillis = 100;

  @NotNull private final Project myProject;
  @NotNull private final DeviceExplorerModel myModel;
  @NotNull private final DeviceExplorerView myView;
  @NotNull private final DeviceFileSystemService myService;
  @NotNull private final FutureCallbackExecutor myEdtExecutor;
  @NotNull private final DeviceExplorerFileManager myFileManager;
  @NotNull private final FileTransferWorkEstimator myWorkEstimator;
  @NotNull private final Set<DeviceFileEntryNode> myTransferringNodes = new HashSet<>();
  @NotNull private final Set<DeviceFileEntryNode> myLoadingChildren = new HashSet<>();
  @NotNull private final Alarm myLoadingNodesAlarms;
  @NotNull private final Alarm myTransferringNodesAlarms;
  @NotNull private final Alarm myLoadingChildrenAlarms;
  @Nullable private LongRunningOperationTracker myLongRunningOperationTracker;

  public DeviceExplorerController(@NotNull Project project,
                                  @NotNull DeviceExplorerModel model,
                                  @NotNull DeviceExplorerView view,
                                  @NotNull DeviceFileSystemService service,
                                  @NotNull DeviceExplorerFileManager fileManager,
                                  @NotNull Executor edtExecutor,
                                  @NotNull Executor taskExecutor) {
    myProject = project;
    myModel = model;
    myView = view;
    myService = service;
    myEdtExecutor = FutureCallbackExecutor.wrap(edtExecutor);
    myService.addListener(new ServiceListener());
    myView.addListener(new ViewListener());
    myFileManager = fileManager;
    myWorkEstimator = new FileTransferWorkEstimator(myEdtExecutor, taskExecutor);
    myLoadingNodesAlarms = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
    myTransferringNodesAlarms = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
    myLoadingChildrenAlarms = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

    project.putUserData(KEY, this);
  }

  @Nullable
  public static DeviceExplorerController getProjectController(@Nullable Project project) {
    if (project == null) {
      return null;
    }
    return project.getUserData(KEY);
  }

  @Nullable
  private DefaultTreeModel getTreeModel() {
    return myModel.getTreeModel();
  }

  @Nullable
  private DefaultTreeSelectionModel getTreeSelectionModel() {
    return myModel.getTreeSelectionModel();
  }

  public void setup() {
    myView.setup();
    myView.startRefresh("Initializing ADB");
    ListenableFuture<Void> future = myService.start();
    myEdtExecutor.addListener(future, myView::stopRefresh);
    myEdtExecutor.addCallback(future, new FutureCallback<Void>() {
      @Override
      public void onSuccess(@Nullable Void result) {
        refreshDeviceList();
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        myView.reportErrorRelatedToService(myService, "Error initializing ADB", t);
      }
    });
  }

  public void restartService() {
    myView.startRefresh("Restarting ADB");
    ListenableFuture<Void> future = myService.restart();
    myEdtExecutor.addListener(future, myView::stopRefresh);
    myEdtExecutor.addCallback(future, new FutureCallback<Void>() {
      @Override
      public void onSuccess(@Nullable Void result) {
        // A successful restart invokes {@link ServiceListener#serviceRestarted()} which
        // eventually refreshes the list of devices
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        myView.reportErrorRelatedToService(myService, "Error restarting ADB", t);
      }
    });
  }

  private void refreshDeviceList() {
    cancelPendingOperations();

    myView.startRefresh("Refreshing list of devices");
    ListenableFuture<List<DeviceFileSystem>> futureDevices = myService.getDevices();
    myEdtExecutor.addListener(futureDevices, myView::stopRefresh);
    myEdtExecutor.addCallback(futureDevices, new FutureCallback<List<DeviceFileSystem>>() {
      @Override
      public void onSuccess(@Nullable List<DeviceFileSystem> result) {
        assert result != null;
        myModel.removeAllDevices();
        result.forEach(myModel::addDevice);
        if (result.isEmpty()) {
          myView.showNoDeviceScreen();
        }
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        myModel.removeAllDevices();
        myView.reportErrorRelatedToService(myService, "Error refreshing list of devices", t);
      }
    });
  }

  private void setNoActiveDevice() {
    cancelPendingOperations();
    myModel.setActiveDevice(null);
    myModel.setActiveDeviceTreeModel(null, null, null);
    myView.showNoDeviceScreen();
  }

  private void setActiveDevice(@NotNull DeviceFileSystem device) {
    cancelPendingOperations();
    myModel.setActiveDevice(device);
    refreshActiveDevice(device);
  }

  private void deviceStateUpdated(@NotNull DeviceFileSystem device) {
    if (!Objects.equals(device, myModel.getActiveDevice())) {
      return;
    }

    // Refresh the active device view only if the device state has changed,
    // for example from offline -> online.
    DeviceState newState = device.getDeviceState();
    DeviceState lastKnownState = myModel.getActiveDeviceLastKnownState(device);
    if (Objects.equals(newState, lastKnownState)) {
      return;
    }
    myModel.setActiveDeviceLastKnownState(device);
    refreshActiveDevice(device);
  }

  private void refreshActiveDevice(@NotNull DeviceFileSystem device) {
    if (!Objects.equals(device, myModel.getActiveDevice())) {
      return;
    }

    if (device.getDeviceState() != DeviceState.ONLINE) {
      String message;
      if (device.getDeviceState() == DeviceState.UNAUTHORIZED ||
          device.getDeviceState() == DeviceState.OFFLINE) {
        message = "Device is pending authentication: please accept debugging session on the device";
      }
      else {
        message = String.format("Device is not online (%s)", device.getDeviceState());
      }
      myView.reportMessageRelatedToDevice(device, message);
      myModel.setActiveDeviceTreeModel(device, null, null);
      return;
    }

    ListenableFuture<DeviceFileEntry> futureRoot = device.getRootDirectory();
    myEdtExecutor.addCallback(futureRoot, new FutureCallback<DeviceFileEntry>() {
      @Override
      public void onSuccess(@Nullable DeviceFileEntry result) {
        assert result != null;
        DeviceFileEntryNode rootNode = new DeviceFileEntryNode(result);
        DefaultTreeModel model = new DefaultTreeModel(rootNode);
        myModel.setActiveDeviceTreeModel(device, model, new DefaultTreeSelectionModel());
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        myModel.setActiveDeviceTreeModel(device, null, null);
        myView.reportErrorRelatedToDevice(device, "Unable to access root directory of device", t);
      }
    });
  }

  private void cancelPendingOperations() {
    myLoadingNodesAlarms.cancelAllRequests();
    myLoadingChildrenAlarms.cancelAllRequests();
    myTransferringNodesAlarms.cancelAllRequests();
    myLoadingChildren.clear();
    myTransferringNodes.clear();
    if (myLongRunningOperationTracker != null) {
      myLongRunningOperationTracker.cancel();
    }
  }

  private <T> ListenableFuture<Void> executeFuturesInSequence(@NotNull Iterator<T> iterator,
                                                              @NotNull Function<T, ListenableFuture<Void>> taskFactory) {
    return myEdtExecutor.executeFuturesInSequence(iterator, taskFactory);
  }

  private void startNodeDownload(@NotNull DeviceFileEntryNode node) {
    startNodeTransfer(node, true);
  }

  private void startNodeUpload(@NotNull DeviceFileEntryNode node) {
    startNodeTransfer(node, false);
  }

  private void startNodeTransfer(@NotNull DeviceFileEntryNode node, boolean download) {
    myView.startTreeBusyIndicator();
    if (download)
      node.setDownloading(true);
    else
      node.setUploading(true);
    if (myTransferringNodes.isEmpty()) {
      myTransferringNodesAlarms.addRequest(new MyTransferringNodesRepaint(), myTransferringNodeRepaintMillis);
    }
    myTransferringNodes.add(node);
  }

  private void stopNodeDownload(@NotNull DeviceFileEntryNode node) {
    stopNodeTransfer(node, true);
  }

  private void stopNodeUpload(@NotNull DeviceFileEntryNode node) {
    stopNodeTransfer(node, false);
  }

  private void stopNodeTransfer(@NotNull DeviceFileEntryNode node, boolean download) {
    myView.stopTreeBusyIndicator();
    if (download)
      node.setDownloading(false);
    else
      node.setUploading(false);
    if (getTreeModel() != null) {
      getTreeModel().nodeChanged(node);
    }
    myTransferringNodes.remove(node);
    if (myTransferringNodes.isEmpty()) {
      myTransferringNodesAlarms.cancelAllRequests();
    }
  }

  private void startLoadChildren(@NotNull DeviceFileEntryNode node) {
    myView.startTreeBusyIndicator();
    if (myLoadingChildren.isEmpty()) {
      myLoadingChildrenAlarms.addRequest(new MyLoadingChildrenRepaint(), myTransferringNodeRepaintMillis);
    }
    myLoadingChildren.add(node);
  }

  private void stopLoadChildren(@NotNull DeviceFileEntryNode node) {
    myView.stopTreeBusyIndicator();
    myLoadingChildren.remove(node);
    if (myLoadingChildren.isEmpty()) {
      myLoadingChildrenAlarms.cancelAllRequests();
    }
  }

  private boolean checkLongRunningOperationAllowed() {
    return myLongRunningOperationTracker == null;
  }

  private void registerLongRunningOperation(@NotNull LongRunningOperationTracker tracker) throws Exception {
    if (!checkLongRunningOperationAllowed()) {
      throw new Exception(DEVICE_EXPLORER_BUSY_MESSAGE);
    }

    myLongRunningOperationTracker = tracker;
    Disposer.register(myLongRunningOperationTracker, () -> {
      assert ApplicationManager.getApplication().isDispatchThread();
      myLongRunningOperationTracker = null;
    });
  }

  public boolean hasActiveDevice() {
    return myModel.getActiveDevice() != null;
  }

  @TestOnly
  @SuppressWarnings("SameParameterValue")
  public void setShowLoadingNodeDelayMillis(int showLoadingNodeDelayMillis) {
    myShowLoadingNodeDelayMillis = showLoadingNodeDelayMillis;
  }

  @TestOnly
  @SuppressWarnings("SameParameterValue")
  public void setTransferringNodeRepaintMillis(int transferringNodeRepaintMillis) {
    myTransferringNodeRepaintMillis = transferringNodeRepaintMillis;
  }

  private class ServiceListener implements DeviceFileSystemServiceListener {
    @Override
    public void serviceRestarted() {
      refreshDeviceList();
    }

    @Override
    public void deviceAdded(@NotNull DeviceFileSystem device) {
      myModel.addDevice(device);
    }

    @Override
    public void deviceRemoved(@NotNull DeviceFileSystem device) {
      myModel.removeDevice(device);
    }

    @Override
    public void deviceUpdated(@NotNull DeviceFileSystem device) {
      myModel.updateDevice(device);
      deviceStateUpdated(device);
    }
  }

  private class ViewListener implements DeviceExplorerViewListener {
    @Override
    public void noDeviceSelected() {
      setNoActiveDevice();
    }

    @Override
    public void deviceSelected(@NotNull DeviceFileSystem device) {
      setActiveDevice(device);
    }

    @Override
    public void openNodesInEditorInvoked(@NotNull List<DeviceFileEntryNode> treeNodes) {
      if (treeNodes.isEmpty()) {
        return;
      }

      if (!checkLongRunningOperationAllowed()) {
        myView.reportErrorRelatedToNode(getCommonParentNode(treeNodes), DEVICE_EXPLORER_BUSY_MESSAGE, new RuntimeException());
        return;
      }

      DeviceFileSystem device = myModel.getActiveDevice();

      executeFuturesInSequence(treeNodes.iterator(), treeNode -> {
        if (!Objects.equals(device, myModel.getActiveDevice())) {
          return Futures.immediateFuture(null);
        }

        if (treeNode.getEntry().isDirectory()) {
          return Futures.immediateFuture(null);
        }

        if (treeNode.isTransferring()) {
          myView.reportErrorRelatedToNode(treeNode, "Entry is already downloading or uploading", new RuntimeException());
          return Futures.immediateFuture(null);
        }

        ListenableFuture<Path> futurePath = downloadFileEntryToDefaultLocation(treeNode);
        myEdtExecutor.addCallback(futurePath, new FutureCallback<Path>() {
          @Override
          public void onSuccess(@Nullable Path localPath) {
            assert localPath != null;
            ListenableFuture<Void> futureOpen = myFileManager.openFileInEditor(localPath, true);
            myEdtExecutor.addCallback(futureOpen, new FutureCallback<Void>() {
              @Override
              public void onSuccess(@Nullable Void result) {
                // Nothing to do, file is opened in editor
              }

              @Override
              public void onFailure(@NotNull Throwable t) {
                String message = String.format("Unable to open file \"%s\" in editor", localPath);
                myView.reportErrorRelatedToNode(treeNode, message, t);
              }
            });
          }

          @Override
          public void onFailure(@NotNull Throwable t) {
            String message = String.format("Error downloading contents of device file %s", getUserFacingNodeName(treeNode));
            myView.reportErrorRelatedToNode(treeNode, message, t);
          }
        });
        return myEdtExecutor.transform(futurePath, path -> null);
      });
    }

    @Override
    public void saveNodesAsInvoked(@NotNull List<DeviceFileEntryNode> treeNodes) {
      if (treeNodes.isEmpty()) {
        return;
      }

      DeviceFileEntryNode commonParentNode = getCommonParentNode(treeNodes);
      if (!checkLongRunningOperationAllowed()) {
        myView.reportErrorRelatedToNode(commonParentNode, DEVICE_EXPLORER_BUSY_MESSAGE, new RuntimeException());
        return;
      }

      ListenableFuture<FileTransferSummary> futureSummary = (treeNodes.size() == 1) ?
                                                            saveSingleNodeAs(treeNodes.get(0)) :
                                                            saveMultiNodesAs(commonParentNode, treeNodes);

      myEdtExecutor.addCallback(futureSummary, new FutureCallback<FileTransferSummary>() {
        @Override
        public void onSuccess(@Nullable FileTransferSummary result) {
          assert result != null;
          reportSaveNodesAsSummary(commonParentNode, result);
        }

        @Override
        public void onFailure(@NotNull Throwable t) {
          myView.reportErrorRelatedToNode(commonParentNode, "Error saving file(s) to local file system", t);
        }
      });
    }

    private void reportSaveNodesAsSummary(@NotNull DeviceFileEntryNode node, @NotNull FileTransferSummary summary) {
      reportFileTransferSummary(node, summary, "downloaded", "downloading");
    }

    @NotNull
    private ListenableFuture<FileTransferSummary> saveSingleNodeAs(@NotNull DeviceFileEntryNode treeNode) {
      // When saving a single entry, we should consider whether the entry is a symbolic link to
      // a directory, not just a plain directory.
      if (treeNode.getEntry().isDirectory() || treeNode.isSymbolicLinkToDirectory()) {
        // If single directory, choose the local directory path to download to, then download
        Path localDirectory;
        try {
          localDirectory = chooseSaveAsDirectoryPath(treeNode);
        }
        catch (Exception e) {
          return Futures.immediateFailedFuture(e);
        }
        if (localDirectory == null) {
          // User cancelled operation
          return Futures.immediateFailedFuture(new CancellationException());
        }

        return wrapFileTransfer(
          tracker -> addDownloadOperationWork(tracker, treeNode),
          tracker -> downloadSingleDirectory(treeNode, localDirectory, tracker));
      }
      else {
        // If single file, choose the local file path to download to, then download
        Path localFile;
        try {
          localFile = chooseSaveAsFilePath(treeNode);
        }
        catch (Exception e) {
          return Futures.immediateFailedFuture(e);
        }
        if (localFile == null) {
          // User cancelled operation
          return Futures.immediateFailedFuture(new CancellationException());
        }

        return wrapFileTransfer(
          tracker -> addDownloadOperationWork(tracker, treeNode),
          tracker -> downloadSingleFile(treeNode, localFile, tracker));
      }
    }

    @NotNull
    private ListenableFuture<FileTransferSummary> saveMultiNodesAs(@NotNull DeviceFileEntryNode commonParentNode,
                                                                   @NotNull List<DeviceFileEntryNode> treeNodes) {
      assert !treeNodes.isEmpty();

      // For downloading multiple entries, choose a local directory path to download to, then download
      // each entry relative to the chosen path
      Path localDirectory;
      try {
        localDirectory = chooseSaveAsDirectoryPath(commonParentNode);
      }
      catch (Exception e) {
        return Futures.immediateFailedFuture(e);
      }
      if (localDirectory == null) {
        // User cancelled operation
        return Futures.immediateFailedFuture(new CancellationException());
      }

      return wrapFileTransfer(
        tracker -> addDownloadOperationWork(tracker, treeNodes),
        tracker -> executeFuturesInSequence(treeNodes.iterator(), treeNode -> {
          Path nodePath = localDirectory.resolve(treeNode.getEntry().getName());
          return downloadSingleNode(treeNode, nodePath, tracker);
        }));
    }

    /**
     * Wrap a file transfer operation (either "SaveAs" or "Upload") so that the operation
     * shows various UI elements related to progress (and resets them when the operation
     * is over).
     *
     * @param prepareTransfer An operation to run before the transfer, typically
     *                        to estimate the amount of work, used for tracking progress
     *                        later on
     * @param performTransfer The transfer operation itself
     * @return A {@link ListenableFuture}&lt;{@link FileTransferSummary}&gt; that completes
     * when the whole transfer operation finishes. In case of cancellation, the future
     * completes with a {@link CancellationException}.
     */
    @NotNull
    private ListenableFuture<FileTransferSummary> wrapFileTransfer(
      @NotNull Function<FileTransferOperationTracker, ListenableFuture<Void>> prepareTransfer,
      @NotNull Function<FileTransferOperationTracker, ListenableFuture<Void>> performTransfer) {

      FileTransferOperationTracker tracker = new FileTransferOperationTracker(myView);
      try {
        registerLongRunningOperation(tracker);
      }
      catch (Exception e) {
        return Futures.immediateFailedFuture(e);
      }

      tracker.start();
      tracker.setCalculatingText(0, 0);
      tracker.setIndeterminate(true);
      Disposer.register(myProject, tracker);

      myView.startTreeBusyIndicator();
      ListenableFuture<Void> futurePrepare = prepareTransfer.apply(tracker);
      ListenableFuture<Void> futureTransfer = myEdtExecutor.transformAsync(futurePrepare, aVoid -> {
        tracker.setIndeterminate(false);
        return performTransfer.apply(tracker);
      });
      myEdtExecutor.addListener(futureTransfer, myView::stopTreeBusyIndicator);
      myEdtExecutor.addListener(futureTransfer, () -> Disposer.dispose(tracker));
      return myEdtExecutor.transform(futureTransfer, aVoid -> tracker.getSummary());
    }

    public ListenableFuture<Void> addUploadOperationWork(@NotNull FileTransferOperationTracker tracker,
                                                         @NotNull List<Path> files) {

      return executeFuturesInSequence(files.iterator(), x -> addUploadOperationWork(tracker, x));
    }

    public ListenableFuture<Void> addUploadOperationWork(@NotNull FileTransferOperationTracker tracker,
                                                         @NotNull Path path) {
      FileTransferWorkEstimatorProgress progress = createFileTransferEstimatorProgress(tracker);
      ListenableFuture<FileTransferWorkEstimate> futureWork = myWorkEstimator.estimateUploadWork(path, progress);
      return myEdtExecutor.transform(futureWork, work -> {
        assert work != null;
        tracker.addWorkEstimate(work);
        return null;
      });
    }

    public ListenableFuture<Void> addDownloadOperationWork(@NotNull FileTransferOperationTracker tracker,
                                                           @NotNull List<DeviceFileEntryNode> entryNodes) {
      ListenableFuture<Void> futureWork =
        executeFuturesInSequence(entryNodes.iterator(), node -> addDownloadOperationWork(tracker, node));
      return myEdtExecutor.transform(futureWork, aVoid -> null);
    }

    public ListenableFuture<Void> addDownloadOperationWork(@NotNull FileTransferOperationTracker tracker,
                                                           @NotNull DeviceFileEntryNode entryNode) {
      FileTransferWorkEstimatorProgress progress = createFileTransferEstimatorProgress(tracker);
      ListenableFuture<FileTransferWorkEstimate> futureEstimate =
        myWorkEstimator.estimateDownloadWork(entryNode.getEntry(), entryNode.isSymbolicLinkToDirectory(), progress);
      return myEdtExecutor.transform(futureEstimate, estimate -> {
        assert estimate != null;
        tracker.addWorkEstimate(estimate);
        return null;
      });
    }

    @NotNull
    private FileTransferWorkEstimatorProgress createFileTransferEstimatorProgress(@NotNull final FileTransferOperationTracker tracker) {
      return new FileTransferWorkEstimatorProgress() {
        @Override
        public void progress(int fileCount, int directoryCount) {
          tracker.setCalculatingText(fileCount, directoryCount);
        }

        @Override
        public boolean isCancelled() {
          return tracker.isCancelled();
        }
      };
    }

    @NotNull
    private ListenableFuture<Void> downloadSingleNode(@NotNull DeviceFileEntryNode node,
                                                      @NotNull Path localPath,
                                                      @NotNull FileTransferOperationTracker tracker) {
      if (node.getEntry().isDirectory()) {
        return downloadSingleDirectory(node, localPath, tracker);
      }
      else {
        return downloadSingleFile(node, localPath, tracker);
      }
    }

    @NotNull
    private ListenableFuture<Void> downloadSingleFile(@NotNull DeviceFileEntryNode treeNode,
                                                      @NotNull Path localPath,
                                                      @NotNull FileTransferOperationTracker tracker) {
      assert !treeNode.getEntry().isDirectory();

      // Download single file
      if (treeNode.isTransferring()) {
        tracker.addProblem(new Exception(String.format("File %s is already downloading or uploading", getUserFacingNodeName(treeNode))));
        return Futures.immediateFuture(null);
      }

      ListenableFuture<Long> futureEntrySize = downloadFileEntry(treeNode, localPath, tracker);
      SettableFuture<Void> futureResult = SettableFuture.create();
      myEdtExecutor.addConsumer(futureEntrySize, (byteCount, throwable) -> {
        if (throwable != null) {
          tracker.addProblem(new Exception(String.format("Error saving contents of device file %s", getUserFacingNodeName(treeNode)),
                                           throwable));
        }
        else {
          tracker.getSummary().addFileCount(1);
          tracker.getSummary().addByteCount(byteCount);
        }
        futureResult.set(null);
      });
      return futureResult;
    }

    @NotNull
    private ListenableFuture<Void> downloadSingleDirectory(@NotNull DeviceFileEntryNode treeNode,
                                                           @NotNull Path localDirectoryPath,
                                                           @NotNull FileTransferOperationTracker tracker) {
      assert treeNode.getEntry().isDirectory() || treeNode.isSymbolicLinkToDirectory();
      if (tracker.isCancelled()) {
        return Futures.immediateCancelledFuture();
      }
      tracker.processDirectory();

      // Ensure directory is created locally
      try {
        FileUtils.mkdirs(localDirectoryPath.toFile());
      }
      catch (Exception e) {
        return Futures.immediateFailedFuture(e);
      }
      tracker.getSummary().addDirectoryCount(1);

      SettableFuture<Void> futureResult = SettableFuture.create();

      ListenableFuture<Void> futureLoadChildren = loadNodeChildren(treeNode);
      myEdtExecutor.addCallback(futureLoadChildren, new FutureCallback<Void>() {
        @Override
        public void onSuccess(@Nullable Void result) {
          ListenableFuture<Void> futureDownloadChildren = executeFuturesInSequence(treeNode.getChildEntryNodes().iterator(), node -> {
            Path nodePath = localDirectoryPath.resolve(node.getEntry().getName());
            return downloadSingleNode(node, nodePath, tracker);
          });
          myEdtExecutor.addConsumer(futureDownloadChildren, (aVoid, throwable) -> {
            if (throwable != null) {
              tracker.addProblem(throwable);
            }
            futureResult.set(null);
          });
        }

        @Override
        public void onFailure(@NotNull Throwable t) {
          tracker.addProblem(t);
          futureResult.set(null);
        }
      });

      return futureResult;
    }

    @Override
    public void copyNodePathsInvoked(@NotNull List<DeviceFileEntryNode> treeNodes) {
      String text = treeNodes.stream().map(x -> x.getEntry().getFullPath()).collect(Collectors.joining("\n"));
      CopyPasteManager.getInstance().setContents(new StringSelection(text));
    }

    @Override
    public void newFileInvoked(@NotNull DeviceFileEntryNode parentTreeNode) {
      newFileOrDirectory(parentTreeNode,
                         "NewTextFile.txt",
                         UIBundle.message("new.file.dialog.title"),
                         UIBundle.message("create.new.file.enter.new.file.name.prompt.text"),
                         UIBundle.message("create.new.file.file.name.cannot.be.empty.error.message"),
                         x -> UIBundle.message("create.new.file.could.not.create.file.error.message", x),
                         x -> parentTreeNode.getEntry().createNewFile(x));
    }

    @Override
    public void synchronizeNodesInvoked(@NotNull List<DeviceFileEntryNode> nodes) {
      if (nodes.isEmpty()) {
        return;
      }

      // Collect directories as well as parent directories of files
      Set<DeviceFileEntryNode> directoryNodes = nodes.stream()
        .map(x -> {
          if (x.isSymbolicLinkToDirectory() || x.getEntry().isDirectory()) {
            return x;
          }
          return DeviceFileEntryNode.fromNode(x.getParent());
        })
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());

      // Add descendant directories that have been expanded/loaded
      directoryNodes = directoryNodes.stream()
        .flatMap(node -> {
          List<DeviceFileEntryNode> nodesToSynchronize = new ArrayList<>();
          Stack<DeviceFileEntryNode> stack = new Stack<>(); // iterative DFS traversal
          stack.push(node);
          while (!stack.isEmpty()) {
            DeviceFileEntryNode currentNode = stack.pop();
            nodesToSynchronize.add(currentNode);
            currentNode.getChildEntryNodes().stream()
              .filter(x -> x.getEntry().isDirectory() || x.isSymbolicLinkToDirectory())
              .filter(DeviceFileEntryNode::isLoaded)
              .forEach(stack::push);
          }
          return nodesToSynchronize.stream();
        })
        .collect(Collectors.toSet());

      myView.startTreeBusyIndicator();
      ListenableFuture<Void> futuresRefresh = executeFuturesInSequence(directoryNodes.iterator(), treeNode -> {
        treeNode.setLoaded(false);
        return loadNodeChildren(treeNode);
      });
      myEdtExecutor.addListener(futuresRefresh, myView::stopTreeBusyIndicator);
    }

    @Override
    public void deleteNodesInvoked(@NotNull List<DeviceFileEntryNode> nodes) {
      if (nodes.isEmpty()) {
        return;
      }

      if (!checkLongRunningOperationAllowed()) {
        myView.reportErrorRelatedToNode(getCommonParentNode(nodes), DEVICE_EXPLORER_BUSY_MESSAGE, new RuntimeException());
        return;
      }

      List<DeviceFileEntry> fileEntries = nodes.stream().map(DeviceFileEntryNode::getEntry).collect(Collectors.toList());
      String message = createDeleteConfirmationMessage(fileEntries);
      int returnValue = Messages.showOkCancelDialog(message,
                                                    UIBundle.message("delete.dialog.title"),
                                                    ApplicationBundle.message("button.delete"),
                                                    CommonBundle.getCancelButtonText(),
                                                    Messages.getQuestionIcon());
      if (returnValue != Messages.OK) {
        return;
      }

      fileEntries.sort(Comparator.comparing(DeviceFileEntry::getFullPath));

      List<String> problems = ContainerUtil.newLinkedList();
      for (DeviceFileEntry fileEntry : fileEntries) {
        ListenableFuture<Void> futureDelete = fileEntry.delete();
        try {
          futureDelete.get(FILE_ENTRY_DELETION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        }
        catch (Throwable t) {
          LOGGER.info(String.format("Error deleting file \"%s\"", fileEntry.getFullPath()), t);
          String problemMessage = ExceptionUtil.getRootCause(t).getMessage();
          if (StringUtil.isEmpty(problemMessage)) {
            problemMessage = "Error deleting file";
          }
          problemMessage = String.format("%s: %s", fileEntry.getFullPath(), problemMessage);
          problems.add(problemMessage);
        }
      }

      if (!problems.isEmpty()) {
        reportDeletionProblem(problems);
      }

      // Refresh the parent node(s) to remove the deleted files
      Set<DeviceFileEntryNode> parentsToRefresh = nodes.stream()
        .map(x -> DeviceFileEntryNode.fromNode(x.getParent()))
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());

      executeFuturesInSequence(parentsToRefresh.iterator(), parentNode -> {
        parentNode.setLoaded(false);
        return loadNodeChildren(parentNode);
      });
    }

    private void reportDeletionProblem(@NotNull List<String> problems) {
      if (problems.size() == 1) {
        Messages.showMessageDialog("Could not erase file or folder:\n" + problems.get(0),
                                   UIBundle.message("error.dialog.title"), Messages.getErrorIcon());
        return;
      }
      boolean more = false;
      if (problems.size() > 10) {
        problems = problems.subList(0, 10);
        more = true;
      }
      Messages.showMessageDialog("Could not erase files or folders:\n  " + StringUtil.join(problems, ",\n  ") + (more ? "\n  ..." : ""),
                                 UIBundle.message("error.dialog.title"), Messages.getErrorIcon());
    }

    private String createDeleteConfirmationMessage(@NotNull List<DeviceFileEntry> filesToDelete) {
      if (filesToDelete.size() == 1) {
        if (filesToDelete.get(0).isDirectory()) {
          return UIBundle.message("are.you.sure.you.want.to.delete.selected.folder.confirmation.message", filesToDelete.get(0).getName());
        }
        else {
          return UIBundle.message("are.you.sure.you.want.to.delete.selected.file.confirmation.message", filesToDelete.get(0).getName());
        }
      }
      else {
        boolean hasFiles = false;
        boolean hasFolders = false;
        for (DeviceFileEntry file : filesToDelete) {
          boolean isDirectory = file.isDirectory();
          hasFiles |= !isDirectory;
          hasFolders |= isDirectory;
        }
        if (hasFiles && hasFolders) {
          return UIBundle
            .message("are.you.sure.you.want.to.delete.selected.files.and.directories.confirmation.message", filesToDelete.size());
        }
        else if (hasFolders) {
          return UIBundle.message("are.you.sure.you.want.to.delete.selected.folders.confirmation.message", filesToDelete.size());
        }
        else {
          return UIBundle.message("are.you.sure.you.want.to.delete.selected.files.and.files.confirmation.message", filesToDelete.size());
        }
      }
    }

    @Override
    public void newDirectoryInvoked(@NotNull DeviceFileEntryNode parentTreeNode) {
      newFileOrDirectory(parentTreeNode,
                         "NewFolder",
                         UIBundle.message("new.folder.dialog.title"),
                         UIBundle.message("create.new.folder.enter.new.folder.name.prompt.text"),
                         UIBundle.message("create.new.folder.folder.name.cannot.be.empty.error.message"),
                         x -> UIBundle.message("create.new.folder.could.not.create.folder.error.message", x),
                         x -> parentTreeNode.getEntry().createNewDirectory(x));
    }

    private void newFileOrDirectory(@NotNull DeviceFileEntryNode parentTreeNode,
                                    @NotNull String initialName,
                                    @NotNull String title,
                                    @NotNull String prompt,
                                    @NotNull String emptyErrorMessage,
                                    @NotNull Function<String, String> errorMessage,
                                    @NotNull Function<String, ListenableFuture<Void>> createFunction) {
      DefaultTreeModel treeModel = getTreeModel();
      if (treeModel == null) {
        return;
      }

      while (true) {
        String newFileName = Messages.showInputDialog(prompt, title, Messages.getQuestionIcon(), initialName, new InputValidatorEx() {
          @Nullable
          @Override
          public String getErrorText(String inputString) {
            if (StringUtil.isEmpty(inputString.trim())) {
              return emptyErrorMessage;
            }
            else if (inputString.contains(AdbPathUtil.FILE_SEPARATOR)) {
              return "Path cannot contain \"/\" characters";
            }
            return null;
          }

          @Override
          public boolean checkInput(String inputString) {
            return canClose(inputString);
          }

          @Override
          public boolean canClose(String inputString) {
            return !StringUtil.isEmpty(inputString.trim());
          }
        });
        if (newFileName == null) {
          return;
        }

        ListenableFuture<Void> futureResult = createFunction.apply(newFileName);
        try {
          futureResult.get(FILE_ENTRY_CREATION_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

          // Refresh the parent node to show the newly created file
          parentTreeNode.setLoaded(false);
          ListenableFuture<Void> futureLoad = loadNodeChildren(parentTreeNode);
          myEdtExecutor.addListener(futureLoad, () -> myView.expandNode(parentTreeNode));
        }
        catch (ExecutionException | InterruptedException | TimeoutException e) {
          showErrorMessage(errorMessage.apply(newFileName), e);
          initialName = newFileName;
          continue;  // Try again
        }
        return;
      }
    }

    private void showErrorMessage(@NotNull String message, @NotNull Throwable error) {
      // Execution exceptions contain the actual cause of the error
      if (error instanceof ExecutionException) {
        if (error.getCause() != null) {
          error = error.getCause();
        }
      }
      // Add error message from exception if we have one
      if (error.getMessage() != null) {
        message += ":\n" + error.getMessage();
      }

      // Show error dialog
      Messages.showMessageDialog(message, UIBundle.message("error.dialog.title"), Messages.getErrorIcon());
    }

    @Override
    public void uploadFilesInvoked(@NotNull DeviceFileEntryNode treeNode) {
      if (!checkLongRunningOperationAllowed()) {
        myView.reportErrorRelatedToNode(treeNode, DEVICE_EXPLORER_BUSY_MESSAGE, new RuntimeException());
        return;
      }

      FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createAllButJarContentsDescriptor();
      AtomicReference<List<VirtualFile>> filesRef = new AtomicReference<>();
      FileChooser.chooseFiles(descriptor, myProject, null, filesRef::set);
      if (filesRef.get() == null || filesRef.get().isEmpty()) {
        return;
      }

      ListenableFuture<FileTransferSummary> futureSummary =
        wrapFileTransfer(tracker -> {
                           List<Path> paths = filesRef.get().stream()
                             .map(x -> Paths.get(x.getPath()))
                             .collect(Collectors.toList());
                           return addUploadOperationWork(tracker, paths);
                         },
                         tracker -> uploadVirtualFiles(treeNode, filesRef.get(), tracker));
      myEdtExecutor.addCallback(futureSummary, new FutureCallback<FileTransferSummary>() {
        @Override
        public void onSuccess(@Nullable FileTransferSummary result) {
          assert result != null;
          reportUploadFilesSummary(treeNode, result);
        }

        @Override
        public void onFailure(@NotNull Throwable t) {
          myView.reportErrorRelatedToNode(treeNode, "Error uploading files(s) to device", t);
        }
      });
    }

    private void reportUploadFilesSummary(@NotNull DeviceFileEntryNode treeNode, @NotNull FileTransferSummary summary) {
      reportFileTransferSummary(treeNode, summary, "uploaded", "uploading");
    }

    private ListenableFuture<Void> uploadVirtualFiles(@NotNull DeviceFileEntryNode parentNode,
                                                      @NotNull List<VirtualFile> files,
                                                      @NotNull FileTransferOperationTracker tracker) {
      // Upload each file
      ListenableFuture<Void> futureUploadFiles =
        executeFuturesInSequence(files.iterator(), file -> uploadVirtualFile(parentNode, file, tracker));

      // Refresh children nodes
      return myEdtExecutor.transformAsync(futureUploadFiles, aVoid -> {
        parentNode.setLoaded(false);
        return loadNodeChildren(parentNode);
      });
    }

    @NotNull
    private ListenableFuture<Void> uploadVirtualFile(@NotNull DeviceFileEntryNode treeNode,
                                                     @NotNull VirtualFile file,
                                                     @NotNull FileTransferOperationTracker tracker) {
      if (file.isDirectory()) {
        return uploadDirectory(treeNode, file, tracker);
      }
      else {
        return uploadFile(treeNode, file, tracker);
      }
    }

    @NotNull
    private ListenableFuture<Void> uploadDirectory(@NotNull DeviceFileEntryNode parentNode,
                                                   @NotNull VirtualFile file,
                                                   @NotNull FileTransferOperationTracker tracker) {
      if (tracker.isCancelled()) {
        return Futures.immediateCancelledFuture();
      }
      tracker.processDirectory();
      tracker.getSummary().addDirectoryCount(1);

      SettableFuture<Void> futureResult = SettableFuture.create();

      // Create directory in destination device
      DeviceFileEntry parentEntry = parentNode.getEntry();
      String directoryName = file.getName();
      ListenableFuture<Void> futureDirectory = parentEntry.createNewDirectory(directoryName);
      myEdtExecutor.addConsumer(futureDirectory, (aVoid, createDirectoryError) -> {
        // Refresh node entries
        parentNode.setLoaded(false);
        ListenableFuture<Void> futureLoadChildren = loadNodeChildren(parentNode);
        myEdtExecutor.addCallback(futureLoadChildren, new FutureCallback<Void>() {
          @Override
          public void onSuccess(@Nullable Void result) {
            // Find node for newly created directory
            DeviceFileEntryNode childNode = parentNode.findChildEntry(directoryName);
            if (childNode == null) {
              // Note: This would happen if we didn't filter hidden files in the code below
              //       or if we failed to create the child directory
              if (createDirectoryError != null) {
                tracker.addProblem(createDirectoryError);
              }
              else {
                tracker.addProblem(new Exception(String.format("Error creating directory \"%s\"", directoryName)));
              }
              futureResult.set(null);
              return;
            }

            // Upload all files into destination device
            // Note: We ignore hidden files ("." prefix) for now, as the listing service
            //       currently does not list hidden files/directories.
            List<VirtualFile> childFiles = Arrays.stream(file.getChildren())
              .filter(x -> !x.getName().startsWith("."))
              .collect(Collectors.toList());
            ListenableFuture<Void> futureFileUploads = uploadVirtualFiles(childNode, childFiles, tracker);
            myEdtExecutor.addListener(futureFileUploads, () -> futureResult.set(null));
          }

          @Override
          public void onFailure(@NotNull Throwable t) {
            tracker.addProblem(t);
            futureResult.set(null);
          }
        });
      });
      return futureResult;
    }

    @NotNull
    private ListenableFuture<Void> uploadFile(@NotNull DeviceFileEntryNode parentNode,
                                              @NotNull VirtualFile file,
                                              @NotNull FileTransferOperationTracker tracker) {
      if (tracker.isCancelled()) {
        return Futures.immediateCancelledFuture();
      }
      tracker.processFile();
      tracker.setUploadFileText(file, 0, 0);

      SettableFuture<Void> futureResult = SettableFuture.create();
      logFuture(futureResult, millis -> String.format("Uploaded file in %,d msec: %s",
                                                      millis,
                                                      AdbPathUtil.resolve(parentNode.getEntry().getFullPath(),
                                                                          file.getName())));

      DeviceFileEntry parentEntry = parentNode.getEntry();
      Path localPath = Paths.get(file.getPath());
      UploadFileState uploadState = new UploadFileState();
      ListenableFuture<Void> futureUpload = parentEntry.uploadFile(localPath, new FileTransferProgress() {
        private long previousBytes;

        @Override
        public void progress(long currentBytes, long totalBytes) {
          // Update progress UI
          tracker.processFileBytes(currentBytes - previousBytes);
          tracker.setUploadFileText(file, currentBytes, totalBytes);
          previousBytes = currentBytes;

          // Update Tree UI
          uploadState.byteCount = totalBytes;
          // First check if child node already exists
          if (uploadState.childNode == null) {
            String fileName = localPath.getFileName().toString();
            uploadState.childNode = parentNode.findChildEntry(fileName);
            if (uploadState.childNode != null) {
              startNodeUpload(uploadState.childNode);
            }
          }

          // If the child node entry is present, simply update its upload status
          if (uploadState.childNode != null) {
            uploadState.childNode.setTransferProgress(currentBytes, totalBytes);
            return;
          }

          // If we already tried to load the children, reset so we try again
          if (uploadState.loadChildrenFuture != null && uploadState.loadChildrenFuture.isDone()) {
            uploadState.loadChildrenFuture = null;
          }

          // Start loading children
          if (currentBytes > 0) {
            if (uploadState.loadChildrenFuture == null) {
              parentNode.setLoaded(false);
              uploadState.loadChildrenFuture = loadNodeChildren(parentNode);
            }
          }
        }

        @Override
        public boolean isCancelled() {
          return tracker.isCancelled();
        }
      });

      myEdtExecutor.addConsumer(futureUpload, (aVoid, throwable) -> {
        // Complete this method
        futureResult.set(null);

        // Update summary
        if (throwable != null) {
          tracker.addProblem(throwable);
        }
        else {
          tracker.getSummary().addFileCount(1);
          tracker.getSummary().addByteCount(uploadState.byteCount);
        }

        // Signal upload is done
        if (uploadState.childNode != null) {
          stopNodeUpload(uploadState.childNode);
        }
      });

      return futureResult;
    }

    private class UploadFileState {
      @Nullable public ListenableFuture<Void> loadChildrenFuture;
      @Nullable public DeviceFileEntryNode childNode;
      public long byteCount;
    }

    private void reportFileTransferSummary(@NotNull DeviceFileEntryNode node,
                                           @NotNull FileTransferSummary summary,
                                           @NotNull String pastParticiple,
                                           @NotNull String presentParticiple) {
      String fileString = StringUtil.pluralize("file", summary.getFileCount());
      String directoryString = StringUtil.pluralize("directory", summary.getDirectoryCount());
      String byteCountString = StringUtil.pluralize("byte", Ints.saturatedCast(summary.getByteCount()));

      // Report success if no errors
      if (summary.getProblems().isEmpty()) {
        String successMessage;
        if (summary.getDirectoryCount() > 0) {
          successMessage = String.format("Successfully %s %,d %s and %,d %s for a total size of %,d %s in %s.",
                                         pastParticiple,
                                         summary.getFileCount(),
                                         fileString,
                                         summary.getDirectoryCount(),
                                         directoryString,
                                         summary.getByteCount(),
                                         byteCountString,
                                         StringUtil.formatDuration(summary.getDurationMillis()));
        }
        else {
          successMessage = String.format("Successfully %s %,d %s for a total of size of %,d %s in %s.",
                                         pastParticiple,
                                         summary.getFileCount(),
                                         fileString,
                                         summary.getByteCount(),
                                         byteCountString,
                                         StringUtil.formatDuration(summary.getDurationMillis()));
        }
        myView.reportMessageRelatedToNode(node, successMessage);
        return;
      }

      // Report error if there were any
      List<String> problems = summary.getProblems().stream()
        .map(x -> ExceptionUtil.getRootCause(x).getMessage())
        .filter(Objects::nonNull)
        .collect(Collectors.toList());

      boolean more = false;
      if (problems.size() > 10) {
        problems = problems.subList(0, 10);
        more = true;
      }

      String message = String.format("There were errors %s files and/or directories", presentParticiple);
      if (summary.getFileCount() > 0) {
        message += String.format(", although %,d %s %s successfully %s in %s for a total of size of %,d %s",
                                 summary.getFileCount(),
                                 fileString,
                                 (summary.getFileCount() <= 1 ? "was" : "were"),
                                 pastParticiple,
                                 StringUtil.formatDuration(summary.getDurationMillis()),
                                 summary.getByteCount(),
                                 byteCountString);
      }
      myView.reportErrorRelatedToNode(node,
                                      message,
                                      new Exception("\n  " + StringUtil.join(problems, ",\n  ") + (more ? "\n  ..." : "")));
    }

    @NotNull
    private DeviceFileEntryNode getCommonParentNode(@NotNull List<DeviceFileEntryNode> treeNodes) {
      TreePath commonPath = TreeUtil.getCommonPath(treeNodes);
      LOGGER.assertTrue(commonPath != null);
      DeviceFileEntryNode result = DeviceFileEntryNode.fromNode(commonPath.getLastPathComponent());
      LOGGER.assertTrue(result != null);
      return result;
    }

    private <V> void logFuture(@NotNull ListenableFuture<V> future, @NotNull Function<Long, String> message) {
      long startNano = System.nanoTime();
      myEdtExecutor.addListener(future, () -> {
        long endNano = System.nanoTime();
        LOGGER.trace(message.apply((endNano - startNano) / 1_000_000));
      });
    }

    @Nullable
    private Path chooseSaveAsFilePath(@NotNull DeviceFileEntryNode treeNode) throws Exception {
      DeviceFileEntry entry = treeNode.getEntry();
      Path localPath = myFileManager.getDefaultLocalPathForEntry(entry);

      FileUtils.mkdirs(localPath.getParent().toFile());
      VirtualFile baseDir = VfsUtil.findFileByIoFile(localPath.getParent().toFile(), true);
      if (baseDir == null) {
        throw new Exception(String.format("Unable to locate file \"%s\"", localPath.getParent()));
      }

      FileSaverDescriptor descriptor = new FileSaverDescriptor("Save As", "");
      FileSaverDialog saveFileDialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, myProject);
      VirtualFileWrapper fileWrapper = saveFileDialog.save(baseDir, localPath.getFileName().toString());
      if (fileWrapper == null) {
        throw new CancellationException();
      }
      return fileWrapper.getFile().toPath();
    }

    @Nullable
    private Path chooseSaveAsDirectoryPath(@NotNull DeviceFileEntryNode treeNode) throws Exception {
      DeviceFileEntry entry = treeNode.getEntry();
      Path localPath = myFileManager.getDefaultLocalPathForEntry(entry);

      FileUtils.mkdirs(localPath.toFile());
      VirtualFile localDir = VfsUtil.findFileByIoFile(localPath.toFile(), true);
      if (localDir == null) {
        throw new Exception(String.format("Unable to locate directory \"%s\"", localPath.getParent()));
      }

      FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
      AtomicReference<Path> result = new AtomicReference<>();
      FileChooser.chooseFiles(descriptor, myProject, localDir, files -> {
        if (files.size() == 1) {
          Path path = Paths.get(files.get(0).getPath());
          result.set(path);
        }
      });

      return result.get();
    }

    @NotNull
    private ListenableFuture<Path> downloadFileEntryToDefaultLocation(@NotNull DeviceFileEntryNode treeNode) {
      // Figure out local path, ask user in "Save As" dialog if required
      Path localPath;
      try {
        localPath = myFileManager.getDefaultLocalPathForEntry(treeNode.getEntry());
      }
      catch (Throwable t) {
        return Futures.immediateFailedFuture(t);
      }

      ListenableFuture<FileTransferSummary> futureSave = wrapFileTransfer(
        tracker -> addDownloadOperationWork(tracker, treeNode),
        tracker -> {
          ListenableFuture<Long> futureSize = downloadFileEntry(treeNode, localPath, tracker);
          return myEdtExecutor.transform(futureSize, aLong -> null);
        });
      return myEdtExecutor.transform(futureSave, summary -> localPath);
    }

    @NotNull
    private ListenableFuture<Long> downloadFileEntry(@NotNull DeviceFileEntryNode treeNode,
                                                     @NotNull Path localPath,
                                                     @NotNull FileTransferOperationTracker tracker) {
      if (tracker.isCancelled()) {
        return Futures.immediateCancelledFuture();
      }
      tracker.processFile();
      tracker.setDownloadFileText(treeNode.getEntry(), 0, 0);

      // Download the entry to the local path
      DeviceFileEntry entry = treeNode.getEntry();
      startNodeDownload(treeNode);
      AtomicReference<Long> sizeRef = new AtomicReference<>();
      ListenableFuture<Void> futureDownload = myFileManager.downloadFileEntry(entry, localPath, new FileTransferProgress() {
        private long previousBytes;

        @Override
        public void progress(long currentBytes, long totalBytes) {
          // Update progress UI
          tracker.processFileBytes(currentBytes - previousBytes);
          previousBytes = currentBytes;
          tracker.setDownloadFileText(treeNode.getEntry(), currentBytes, totalBytes);

          // Update Tree UI
          treeNode.setTransferProgress(currentBytes, totalBytes);
          sizeRef.set(totalBytes);
        }

        @Override
        public boolean isCancelled() {
          return tracker.isCancelled();
        }
      });
      myEdtExecutor.addListener(futureDownload, () -> stopNodeDownload(treeNode));
      logFuture(futureDownload, millis -> String.format("Downloaded file in %,d msec: %s", millis, entry.getFullPath()));
      return myEdtExecutor.transform(futureDownload, aVoid -> sizeRef.get());
    }

    @Override
    public void treeNodeExpanding(@NotNull DeviceFileEntryNode node) {
      loadNodeChildren(node);
    }

    private ListenableFuture<Void> loadNodeChildren(@NotNull final DeviceFileEntryNode node) {
      // Ensure node is expanded only once
      if (node.isLoaded()) {
        return Futures.immediateFuture(null);
      }
      node.setLoaded(true);

      // Leaf nodes are not expandable
      if (node.isLeaf()) {
        return Futures.immediateFuture(null);
      }

      DefaultTreeModel treeModel = getTreeModel();
      DefaultTreeSelectionModel treeSelectionModel = getTreeSelectionModel();
      if (treeModel == null || treeSelectionModel == null) {
        return Futures.immediateFuture(null);
      }

      DeviceFileSystem fileSystem = myModel.getActiveDevice();
      if (!Objects.equals(fileSystem, node.getEntry().getFileSystem())) {
        return Futures.immediateFuture(null);
      }

      ShowLoadingNodeRequest showLoadingNode = new ShowLoadingNodeRequest(treeModel, node);
      myLoadingNodesAlarms.addRequest(showLoadingNode, myShowLoadingNodeDelayMillis);

      startLoadChildren(node);
      ListenableFuture<List<DeviceFileEntry>> futureEntries = node.getEntry().getEntries();
      myEdtExecutor.addCallback(futureEntries, new FutureCallback<List<DeviceFileEntry>>() {
        @Override
        public void onSuccess(List<DeviceFileEntry> result) {
          if (!Objects.equals(treeModel, getTreeModel())) {
            // We switched to another device, ignore this callback
            return;
          }

          // Save selection
          TreePath[] oldSelections = treeSelectionModel.getSelectionPaths();

          // Collect existing entries that have the "isLinkToDirectory" property set
          Set<String> isLinkToDirectory = node.getChildEntryNodes().stream()
            .filter(DeviceFileEntryNode::isSymbolicLinkToDirectory)
            .map(x -> x.getEntry().getName())
            .collect(Collectors.toSet());

          // Sort new entries according to presentation sort order
          Comparator<DeviceFileEntry> comparator = NodeSorting.getCustomComparator(
            DeviceFileEntry::getName,
            x -> x.isDirectory() || isLinkToDirectory.contains(x.getName()));
          result.sort(comparator);

          List<DeviceFileEntryNode> addedNodes = updateChildrenNodes(treeModel, node, result);

          // Restore selection
          restoreTreeSelection(treeSelectionModel, oldSelections, node);

          List<DeviceFileEntryNode> symlinkNodes = addedNodes
            .stream()
            .filter(x -> x.getEntry().isSymbolicLink())
            .collect(Collectors.toList());
          querySymbolicLinks(symlinkNodes, treeModel);
        }

        @Override
        public void onFailure(@NotNull Throwable t) {
          String message = ExceptionUtil.getRootCause(t).getMessage();
          if (StringUtil.isEmpty(message)) {
            message = String.format("Unable to list entries of directory %s", getUserFacingNodeName(node));
          }

          node.removeAllChildren();
          node.add(new ErrorNode(message));
          node.setAllowsChildren(true);
          treeModel.nodeStructureChanged(node);
        }
      });
      myEdtExecutor.addListener(futureEntries, () -> {
        stopLoadChildren(node);
        myLoadingNodesAlarms.cancelRequest(showLoadingNode);
      });
      return myEdtExecutor.transform(futureEntries, entries -> null);
    }

    @NotNull
    private List<DeviceFileEntryNode> updateChildrenNodes(@NotNull DefaultTreeModel treeModel,
                                                          @NotNull DeviceFileEntryNode parentNode,
                                                          @NotNull List<DeviceFileEntry> newEntries) {

      TreeUtil.UpdateChildrenOps<DeviceFileEntryNode, DeviceFileEntry> updateChildrenOps =
        new TreeUtil.UpdateChildrenOps<DeviceFileEntryNode, DeviceFileEntry>() {
          @Nullable
          @Override
          public DeviceFileEntryNode getChildNode(@NotNull DeviceFileEntryNode parentNode, int index) {
            // Some nodes (e.g. "error" or "loading" nodes) are not of the same type,
            // we return null in those cases to that the update algorithm will remove them from
            // the parent node.
            return DeviceFileEntryNode.fromNode(parentNode.getChildAt(index));
          }

          @NotNull
          @Override
          public DeviceFileEntryNode mapEntry(@NotNull DeviceFileEntry entry) {
            return new DeviceFileEntryNode(entry);
          }

          @Override
          public int compareNodeWithEntry(@NotNull DeviceFileEntryNode node,
                                          @NotNull DeviceFileEntry entry) {
            return node.getEntry().getName().compareTo(entry.getName());
          }

          @Override
          public void updateNode(@NotNull DeviceFileEntryNode node,
                                 @NotNull DeviceFileEntry entry) {
            node.setEntry(entry);
          }
        };

      List<DeviceFileEntryNode> addedNodes = TreeUtil.updateChildrenNodes(treeModel, parentNode, newEntries, updateChildrenOps);
      parentNode.setAllowsChildren(parentNode.getChildCount() > 0);
      return addedNodes;
    }

    private void restoreTreeSelection(@NotNull DefaultTreeSelectionModel treeSelectionModel,
                                      @NotNull TreePath[] oldSelections,
                                      @NotNull DefaultMutableTreeNode parentNode) {
      Set<TreePath> newSelections = new HashSet<>();
      TreePath parentPath = new TreePath(parentNode.getPath());
      Arrays.stream(oldSelections)
        .forEach(x -> restorePathSelection(treeSelectionModel, parentPath, x, newSelections));

      TreePath[] newSelectionArray = ArrayUtil.toObjectArray(newSelections.stream().collect(Collectors.toList()), TreePath.class);
      treeSelectionModel.addSelectionPaths(newSelectionArray);
    }

    private void restorePathSelection(@NotNull DefaultTreeSelectionModel treeSelectionModel,
                                      @NotNull TreePath parentPath,
                                      @NotNull TreePath oldPath,
                                      @NotNull Set<TreePath> selections) {
      if (treeSelectionModel.isPathSelected(oldPath)) {
        return;
      }
      if (Objects.equals(parentPath, oldPath)) {
        return;
      }
      if (!parentPath.isDescendant(oldPath)) {
        return;
      }
      TreeNode node = (TreeNode)parentPath.getLastPathComponent();

      TreeNode existingChild = TreeUtil.getChildren(node)
        .filter(x -> Objects.equals(x, oldPath.getLastPathComponent()))
        .findFirst()
        .orElse(null);
      if (existingChild == null) {
        selections.add(parentPath);
      }
    }

    /**
     * Asynchronously update the tree node UI of the {@code symlinkNodes} entries if they target
     * a directory, i.e. update tree nodes with a "Folder" and "Expandable arrow" icon.
     */
    private void querySymbolicLinks(@NotNull List<DeviceFileEntryNode> symlinkNodes, @NotNull DefaultTreeModel treeModel) {
      // Note: We process (asynchronously) one entry at a time, instead of all of them in parallel,
      //       to avoid flooding the device with too many requests, which would eventually lead
      //       to the device to reject additional requests.
      executeFuturesInSequence(symlinkNodes.iterator(), treeNode -> {
        ListenableFuture<Boolean> futureIsLinkToDirectory = treeNode.getEntry().isSymbolicLinkToDirectory();
        myEdtExecutor.addConsumer(futureIsLinkToDirectory, (@Nullable Boolean result, @Nullable Throwable throwable) -> {
          // Log error, but keep going as we may have more symlinkNodes to examine
          if (throwable != null) {
            LOGGER.info(String.format("Error determining if file entry \"%s\" is a link to a directory",
                                      treeNode.getEntry().getName()),
                        throwable);
          }

          // Stop all processing if tree model has changed, i.e. UI has been switched to another device
          if (!Objects.equals(myModel.getTreeModel(), treeModel)) {
            return;
          }

          // Update tree node appearance (in case of "null"" result, we assume the entry
          // does not target a directory).
          boolean isDirectory = result != null && result;

          if (treeNode.isSymbolicLinkToDirectory() != isDirectory) {
            MutableTreeNode parent = (MutableTreeNode)treeNode.getParent();

            // Remove element from tree at current position (assume tree is sorted)
            int previousIndex = TreeUtil.binarySearch(parent, treeNode, NodeSorting.getTreeNodeComparator());
            if (previousIndex >= 0) {
              treeModel.removeNodeFromParent(treeNode);
            }

            // Update node state (is-link-to-directory)
            treeNode.setSymbolicLinkToDirectory(isDirectory);

            // Insert node in its new position
            int newIndex = TreeUtil.binarySearch(parent, treeNode, NodeSorting.getTreeNodeComparator());
            if (newIndex < 0) {
              treeModel.insertNodeInto(treeNode, parent, -(newIndex + 1));
            }
          }
        });
        return myEdtExecutor.transform(futureIsLinkToDirectory, aBoolean -> null);
      });
    }


    @NotNull
    private String getUserFacingNodeName(@NotNull DeviceFileEntryNode node) {
      return StringUtil.isEmpty(node.getEntry().getName()) ?
             "[root]" :
             "\"" + node.getEntry().getName() + "\"";
    }
  }

  private static class ShowLoadingNodeRequest implements Runnable {
    @NotNull private DefaultTreeModel myTreeModel;
    @NotNull private DeviceFileEntryNode myNode;

    public ShowLoadingNodeRequest(@NotNull DefaultTreeModel treeModel, @NotNull DeviceFileEntryNode node) {
      myTreeModel = treeModel;
      myNode = node;
    }

    @Override
    public void run() {
      myNode.setAllowsChildren(true);
      myNode.add(new MyLoadingNode(myNode.getEntry()));
      myTreeModel.nodeStructureChanged(myNode);
    }
  }

  private class MyTransferringNodesRepaint implements Runnable {
    @Override
    public void run() {
      myTransferringNodes.forEach(x -> {
        x.incTransferringTick();
        if (getTreeModel() != null) {
          getTreeModel().nodeChanged(x);
        }
      });
      myTransferringNodesAlarms.addRequest(new MyTransferringNodesRepaint(), myTransferringNodeRepaintMillis);
    }
  }

  private class MyLoadingChildrenRepaint implements Runnable {
    @Override
    public void run() {
      myLoadingChildren.forEach(x -> {
        if (x.getChildCount() == 0)
          return;

        TreeNode node = x.getFirstChild();
        if (node instanceof MyLoadingNode) {
          MyLoadingNode loadingNode = (MyLoadingNode)node;
          loadingNode.incTick();
          if (getTreeModel() != null) {
            getTreeModel().nodeChanged(loadingNode);
          }
        }
      });
      myLoadingChildrenAlarms.addRequest(new MyLoadingChildrenRepaint(), myTransferringNodeRepaintMillis);
    }
  }

  private static class NodeSorting {
    /**
     * Compare {@link DeviceFileEntryNode} by directory first, by name second.
     */
    @NotNull
    public static Comparator<DeviceFileEntryNode> getEntryNodeComparator() {
      return getCustomComparator(x -> x.getEntry().getName(), o1 -> o1.getEntry().isDirectory() || o1.isSymbolicLinkToDirectory());
    }

    /**
     * Compare {@link TreeNode} as {@link DeviceFileEntryNode}. Any other type of tree node
     * is considered "less than".
     */
    @NotNull
    public static Comparator<TreeNode> getTreeNodeComparator() {
      return (o1, o2) -> {
        if (o1 instanceof DeviceFileEntryNode && o2 instanceof DeviceFileEntryNode) {
          return getEntryNodeComparator().compare((DeviceFileEntryNode)o1, (DeviceFileEntryNode)o2);
        }
        else if (o1 instanceof DeviceFileEntryNode) {
          return 1;
        }
        else if (o2 instanceof DeviceFileEntryNode) {
          return -1;
        }
        else {
          return 0;
        }
      };
    }

    /**
     * Apply the same ordering as {@link NodeSorting#getEntryNodeComparator()} on any
     * object type given a custom {@code isDirectory} predicate and @{link getName}
     * function.
     */
    @NotNull
    public static <V> Comparator<V> getCustomComparator(@NotNull Function<V, String> nameProvider,
                                                        @NotNull Predicate<V> isDirectory) {
      // Compare so that directory are always before files
      return (o1, o2) -> {
        if (o1 == null && o2 == null) {
          return 0;
        }
        else if (o1 == null) {
          return -1;
        }
        else if (o2 == null) {
          return 1;
        }
        else {
          boolean isDir1 = isDirectory.test(o1);
          boolean isDir2 = isDirectory.test(o2);
          if (isDir1 == isDir2) {
            return StringUtil.compare(nameProvider.apply(o1), nameProvider.apply(o2), true);
          }
          else if (isDir1) {
            return -1;
          }
          else {
            return 1;
          }
        }
      };
    }
  }
}