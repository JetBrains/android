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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.*;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
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
  private static final Logger LOGGER = Logger.getInstance(DeviceExplorerController.class);
  private static final Key<DeviceExplorerController> KEY = Key.create(DeviceExplorerController.class.getName());
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
  @NotNull private final Set<DeviceFileEntryNode> myTransferringNodes = new HashSet<>();
  @NotNull private final Set<DeviceFileEntryNode> myLoadingChildren = new HashSet<>();
  @NotNull private final Alarm myLoadingNodesAlarms;
  @NotNull private final Alarm myTransferringNodesAlarms;
  @NotNull private final Alarm myLoadingChildrenAlarms;

  public DeviceExplorerController(@NotNull Project project,
                                  @NotNull DeviceExplorerModel model,
                                  @NotNull DeviceExplorerView view,
                                  @NotNull DeviceFileSystemService service,
                                  @NotNull DeviceExplorerFileManager fileManager,
                                  @NotNull Executor edtExecutor) {
    myProject = project;
    myModel = model;
    myView = view;
    myService = service;
    myEdtExecutor = new FutureCallbackExecutor(edtExecutor);
    myService.addListener(new ServiceListener());
    myView.addListener(new ViewListener());
    myFileManager = fileManager;
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
    ListenableFuture<Void> future = myService.start();
    myEdtExecutor.addCallback(future, new FutureCallback<Void>() {
      @Override
      public void onSuccess(@Nullable Void result) {
        myView.serviceSetupSuccess();
        setupInitialView();
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        myView.reportErrorRelatedToService(myService, "Unable to start file system service", t);
      }
    });
  }

  public void restartService() {
    ListenableFuture<Void> futureResult = myService.restart();
    myEdtExecutor.addCallback(futureResult, new FutureCallback<Void>() {
      @Override
      public void onSuccess(@Nullable Void result) {
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        myView.reportErrorRelatedToService(myService, "Unable to restart file system service", t);
      }
    });
  }

  private void setupInitialView() {
    ListenableFuture<List<DeviceFileSystem>> future = myService.getDevices();
    myEdtExecutor.addCallback(future, new FutureCallback<List<DeviceFileSystem>>() {
      @Override
      public void onSuccess(List<DeviceFileSystem> result) {
        result.forEach(myModel::addDevice);
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        myView.reportErrorRelatedToService(myService, "Unable to get list of devices", t);
      }
    });
  }

  private void setActiveDevice(@Nullable DeviceFileSystem device) {
    myLoadingNodesAlarms.cancelAllRequests();
    myLoadingChildrenAlarms.cancelAllRequests();
    myTransferringNodesAlarms.cancelAllRequests();
    myLoadingChildren.clear();
    myTransferringNodes.clear();

    myModel.setActiveDevice(device);
    ListenableFuture<DefaultTreeModel> futureTreeModel = createTreeModel(device);
    myEdtExecutor.addCallback(futureTreeModel, new FutureCallback<DefaultTreeModel>() {
      @Override
      public void onSuccess(@Nullable DefaultTreeModel result) {
        myModel.setActiveDeviceTreeModel(device, result, new DefaultTreeSelectionModel());
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        assert device != null; // Never fails for "null" device
        myModel.setActiveDeviceTreeModel(device, null, null);
        myView.reportErrorRelatedToDevice(device, "Unable to get root directory of device", t);
      }
    });
  }

  @NotNull
  private ListenableFuture<DefaultTreeModel> createTreeModel(@Nullable DeviceFileSystem device) {
    SettableFuture<DefaultTreeModel> futureResult = SettableFuture.create();
    if (device == null) {
      futureResult.set(null);
      return futureResult;
    }

    ListenableFuture<DeviceFileEntry> futureRoot = device.getRootDirectory();
    myEdtExecutor.addCallback(futureRoot, new FutureCallback<DeviceFileEntry>() {
      @Override
      public void onSuccess(DeviceFileEntry result) {
        DeviceFileEntryNode rootNode = new DeviceFileEntryNode(result);
        DefaultTreeModel model = new DefaultTreeModel(rootNode);
        futureResult.set(model);
      }

      @Override
      public void onFailure(@NotNull Throwable t) {
        futureResult.setException(t);
      }
    });
    return futureResult;
  }

  /**
   * Execute a task from the {@code taskFactory} for each element of the {@code iterator},
   * waiting for the {@link ListenableFuture} returned by each task before executing the next one.
   * The goal is to ensure we don't overload a device with many parallel requests.
   * Cancellation can be implemented by the {@code taskFactory}, typically returning immediate
   * no-op futures when the cancellation condition is detected.
   *
   * @param iterator    The source of elements to process
   * @param taskFactory A factory {@link Function} that returns a {@link ListenableFuture} for a given element
   * @param <T>         The type of the elements to process
   */
  @NotNull
  private <T> ListenableFuture<Void> executeFuturesInSequence(@NotNull Iterator<T> iterator,
                                                              @NotNull Function<T, ListenableFuture<Void>> taskFactory) {
    SettableFuture<Void> finalResult = SettableFuture.create();
    executeFuturesInSequenceWorker(iterator, taskFactory, finalResult);
    return finalResult;
  }

  private <T> void executeFuturesInSequenceWorker(@NotNull Iterator<T> iterator,
                                                  @NotNull Function<T, ListenableFuture<Void>> taskFactory,
                                                  @NotNull SettableFuture<Void> finalResult) {
    if (iterator.hasNext()) {
      ListenableFuture<Void> future = taskFactory.apply(iterator.next());
      myEdtExecutor.addConsumer(future, (aVoid, throwable) -> executeFuturesInSequenceWorker(iterator, taskFactory, finalResult));
    }
    else {
      finalResult.set(null);
    }
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
    public void updated() {
      myLoadingNodesAlarms.cancelAllRequests();
      myModel.removeAllDevices();
      setupInitialView();
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
    }
  }

  private class ViewListener implements DeviceExplorerViewListener {
    @Override
    public void deviceSelected(@Nullable DeviceFileSystem device) {
      setActiveDevice(device);
    }

    @Override
    public void openNodesInEditorInvoked(@NotNull List<DeviceFileEntryNode> treeNodes) {
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
            try {
              myFileManager.openFileInEditor(localPath, true);
            }
            catch (Throwable t) {
              String message = String.format("Unable to open file \"%s\" in editor", localPath);
              myView.reportErrorRelatedToNode(treeNode, message, t);
            }
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
      if (treeNode.getEntry().isDirectory()) {
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

        return saveNodeAsWorker(summary -> downloadSingleDirectory(treeNode, localDirectory, summary));
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

        return saveNodeAsWorker(summary -> downloadSingleFile(treeNode, localFile, summary));
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

      return saveNodeAsWorker(summary -> {
        //noinspection CodeBlock2Expr
        return executeFuturesInSequence(treeNodes.iterator(), treeNode -> {
          Path nodePath = localDirectory.resolve(treeNode.getEntry().getName());
          return downloadSingleNode(treeNode, nodePath, summary);
        });
      });
    }

    @NotNull
    private ListenableFuture<FileTransferSummary> saveNodeAsWorker(
      @NotNull Function<FileTransferSummary, ListenableFuture<Void>> downloadWorker) {

      FileTransferSummary summary = new FileTransferSummary();

      myView.startTreeBusyIndicator();
      ListenableFuture<Void> futureDownload = downloadWorker.apply(summary);
      myEdtExecutor.addListener(futureDownload, myView::stopTreeBusyIndicator);

      return myEdtExecutor.transform(futureDownload, aVoid -> summary);
    }

    @NotNull
    private ListenableFuture<Void> downloadSingleNode(@NotNull DeviceFileEntryNode node,
                                                      @NotNull Path localPath,
                                                      @NotNull FileTransferSummary summary) {
      if (node.getEntry().isDirectory()) {
        return downloadSingleDirectory(node, localPath, summary);
      }
      else {
        return downloadSingleFile(node, localPath, summary);
      }
    }

    @NotNull
    private ListenableFuture<Void> downloadSingleFile(@NotNull DeviceFileEntryNode treeNode,
                                                      @NotNull Path localPath,
                                                      @NotNull FileTransferSummary summary) {
      assert !treeNode.getEntry().isDirectory();

      // Download single file
      if (treeNode.isTransferring()) {
        summary.problems.add(new Exception(String.format("File %s is already downloading or uploading", getUserFacingNodeName(treeNode))));
        return Futures.immediateFuture(null);
      }

      ListenableFuture<Long> futureEntrySize = downloadFileEntry(treeNode, localPath);
      SettableFuture<Void> futureResult = SettableFuture.create();
      myEdtExecutor.addConsumer(futureEntrySize, (byteCount, throwable) -> {
        if (throwable != null) {
          summary.problems.add(new Exception(String.format("Error saving contents of device file %s", getUserFacingNodeName(treeNode)),
                                             throwable));
        }
        else {
          summary.fileCount++;
          summary.byteCount += byteCount;
        }
        futureResult.set(null);
      });
      return futureResult;
    }

    @NotNull
    private ListenableFuture<Void> downloadSingleDirectory(@NotNull DeviceFileEntryNode treeNode,
                                                           @NotNull Path localDirectoryPath,
                                                           @NotNull FileTransferSummary summary) {
      assert treeNode.getEntry().isDirectory();

      // Ensure directory is created locally
      try {
        FileUtils.mkdirs(localDirectoryPath.toFile());
      }
      catch (Exception e) {
        return Futures.immediateFailedFuture(e);
      }
      summary.directoryCount++;

      SettableFuture<Void> futureResult = SettableFuture.create();

      ListenableFuture<Void> futureLoadChildren = loadNodeChildren(treeNode);
      myEdtExecutor.addCallback(futureLoadChildren, new FutureCallback<Void>() {
        @Override
        public void onSuccess(@Nullable Void result) {
          ListenableFuture<Void> futureDownloadChildren = executeFuturesInSequence(treeNode.getChildEntryNodes().iterator(), node -> {
            Path nodePath = localDirectoryPath.resolve(node.getEntry().getName());
            return downloadSingleNode(node, nodePath, summary);
          });
          myEdtExecutor.addConsumer(futureDownloadChildren, (aVoid, throwable) -> {
            if (throwable != null) {
              summary.problems.add(throwable);
            }
            futureResult.set(null);
          });
        }

        @Override
        public void onFailure(@NotNull Throwable t) {
          summary.problems.add(t);
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
                         x -> parentTreeNode.getEntry().getFileSystem().createNewFile(parentTreeNode.getEntry(), x));
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
                         x -> parentTreeNode.getEntry().getFileSystem().createNewDirectory(parentTreeNode.getEntry(), x));
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
          loadNodeChildren(parentTreeNode);
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
      // Add error message from execption if we have one
      if (error.getMessage() != null) {
        message += ":\n" + error.getMessage();
      }

      // Show error dialog
      Messages.showMessageDialog(message, UIBundle.message("error.dialog.title"), Messages.getErrorIcon());
    }

    @Override
    public void uploadFilesInvoked(@NotNull DeviceFileEntryNode treeNode) {
      FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createAllButJarContentsDescriptor();
      AtomicReference<List<VirtualFile>> filesRef = new AtomicReference<>();
      FileChooser.chooseFiles(descriptor, myProject, null, filesRef::set);
      if (filesRef.get() == null || filesRef.get().isEmpty()) {
        return;
      }
      FileTransferSummary summary = new FileTransferSummary();
      myView.startTreeBusyIndicator();
      ListenableFuture<Void> future = uploadVirtualFiles(treeNode, filesRef.get(), summary);
      myEdtExecutor.addListener(future, myView::stopTreeBusyIndicator);
      myEdtExecutor.addListener(future, () -> reportUploadFilesSummary(treeNode, summary));
    }

    private void reportUploadFilesSummary(@NotNull DeviceFileEntryNode treeNode, @NotNull FileTransferSummary summary) {
      reportFileTransferSummary(treeNode, summary, "uploaded", "uploading");
    }

    private ListenableFuture<Void> uploadVirtualFiles(@NotNull DeviceFileEntryNode parentNode,
                                                      @NotNull List<VirtualFile> files,
                                                      @NotNull FileTransferSummary problems) {
      return executeFuturesInSequence(files.iterator(), file -> uploadVirtualFile(parentNode, file, problems));
    }

    @NotNull
    private ListenableFuture<Void> uploadVirtualFile(@NotNull DeviceFileEntryNode treeNode,
                                                     @NotNull VirtualFile file,
                                                     @NotNull FileTransferSummary summary) {
      if (file.isDirectory()) {
        return uploadDirectory(treeNode, file, summary);
      }
      else {
        return uploadFile(treeNode, file, summary);
      }
    }

    @NotNull
    private ListenableFuture<Void> uploadDirectory(@NotNull DeviceFileEntryNode treeNode,
                                                   @NotNull VirtualFile file,
                                                   @NotNull FileTransferSummary summary) {
      summary.directoryCount++;
      SettableFuture<Void> futureResult = SettableFuture.create();
      logFuture(futureResult, String.format("Uploading local directory \"%s\" to remote path \"%s\"",
                                            file.getPath(),
                                            treeNode.getEntry().getFullPath()));

      // Create directory in destination device
      DeviceFileEntry entry = treeNode.getEntry();
      String directoryName = file.getName();
      ListenableFuture<Void> futureDirectory = entry.getFileSystem().createNewDirectory(entry, directoryName);
      myEdtExecutor.addCallback(futureDirectory, new FutureCallback<Void>() {
        @Override
        public void onSuccess(@Nullable Void result) {
          // Refresh node entries
          treeNode.setLoaded(false);
          ListenableFuture<Void> futureLoadChildren = loadNodeChildren(treeNode);
          myEdtExecutor.addCallback(futureLoadChildren, new FutureCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void result) {
              // Find node for newly created directory
              DeviceFileEntryNode childNode = treeNode.findChildEntry(directoryName);
              if (childNode == null) {
                // Note: This would happen if we didn't filter hidden files in the code below
                summary.problems.add(new RuntimeException(String.format("Error creating directory \"%s\"", directoryName)));
                futureResult.set(null);
                return;
              }
              myView.expandNode(childNode);

              // Upload all files into destination device
              // Note: We ignore hidden files ("." prefix) for now, as the listing service
              //       currently does not list hidden files/directories.
              ListenableFuture<Void> futureFileUploads = executeFuturesInSequence(
                Arrays.stream(file.getChildren()).filter(x -> !x.getName().startsWith(".")).iterator(),
                x -> uploadVirtualFile(childNode, x, summary));
              myEdtExecutor.addListener(futureFileUploads, () -> futureResult.set(null));
            }

            @Override
            public void onFailure(@NotNull Throwable t) {
              summary.problems.add(t);
              futureResult.set(null);
            }
          });
        }

        @Override
        public void onFailure(@NotNull Throwable t) {
          summary.problems.add(t);
          futureResult.set(null);
        }
      });
      return futureResult;
    }

    private class UploadFileState {
      @Nullable public ListenableFuture<Void> loadChildrenFuture;
      @Nullable public DeviceFileEntryNode childNode;
      public long byteCount;
    }

    @NotNull
    private ListenableFuture<Void> uploadFile(@NotNull DeviceFileEntryNode treeNode,
                                              @NotNull VirtualFile file,
                                              @NotNull FileTransferSummary summary) {
      SettableFuture<Void> futureResult = SettableFuture.create();
      logFuture(futureResult, String.format("Uploading local file \"%s\" to remote path \"%s\"",
                                            file.getPath(),
                                            treeNode.getEntry().getFullPath()));

      DeviceFileEntry entry = treeNode.getEntry();
      Path localPath = Paths.get(file.getPath());
      UploadFileState uploadState = new UploadFileState();
      ListenableFuture<Void> futureUpload = entry.getFileSystem().uploadFile(localPath, entry, new FileTransferProgress() {
        @Override
        public void progress(long currentBytes, long totalBytes) {
          uploadState.byteCount = totalBytes;
          // First check if child node already exists
          if (uploadState.childNode == null) {
            String fileName = localPath.getFileName().toString();
            uploadState.childNode = treeNode.findChildEntry(fileName);
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
              treeNode.setLoaded(false);
              uploadState.loadChildrenFuture = loadNodeChildren(treeNode);
            }
          }
        }

        @Override
        public boolean isCancelled() {
          return false;
        }
      });

      myEdtExecutor.addConsumer(futureUpload, (aVoid, throwable) -> {
        if (throwable != null) {
          summary.problems.add(throwable);
        }
        else {
          summary.fileCount++;
          summary.byteCount += uploadState.byteCount;
        }
      });

      // After the upload, refresh the list of children.
      myEdtExecutor.addListener(futureUpload, () -> {
        // Signal upload is done
        if (uploadState.childNode != null) {
          stopNodeUpload(uploadState.childNode);
        }
        // Refresh children
        treeNode.setLoaded(false);
        ListenableFuture<Void> futureLoadChildren = loadNodeChildren(treeNode);
        myEdtExecutor.addListener(futureLoadChildren, () -> futureResult.set(null));
      });
      return futureResult;
    }

    private class FileTransferSummary {
      public int fileCount;
      public int directoryCount;
      public long byteCount;
      @NotNull public List<Throwable> problems = new ArrayList<>();
    }

    private void reportFileTransferSummary(@NotNull DeviceFileEntryNode node,
                                           @NotNull FileTransferSummary summary,
                                           @NotNull String pastParticiple,
                                           @NotNull String presentParticiple) {
      String fileString = StringUtil.pluralize("file", summary.fileCount);
      String directoryString = StringUtil.pluralize("directory", summary.directoryCount);
      String byteCountString = StringUtil.pluralize("byte", Ints.saturatedCast(summary.byteCount));

      // Report success if no errors
      if (summary.problems.isEmpty()) {
        String successMessage;
        if (summary.directoryCount > 0) {
          successMessage = String.format("Successfully %s %,d %s and %,d %s for a total size of %,d %s.",
                                         pastParticiple,
                                         summary.fileCount,
                                         fileString,
                                         summary.directoryCount,
                                         directoryString,
                                         summary.byteCount,
                                         byteCountString);
        }
        else {
          successMessage = String.format("Successfully %s %,d %s for a total of size of %,d %s.",
                                         pastParticiple,
                                         summary.fileCount,
                                         fileString,
                                         summary.byteCount,
                                         byteCountString);
        }
        myView.reportMessageRelatedToNode(node, successMessage);
        return;
      }

      // Report error if there were any
      List<String> problems = summary.problems.stream()
        .map(x -> ExceptionUtil.getRootCause(x).getMessage())
        .filter(Objects::nonNull)
        .collect(Collectors.toList());

      boolean more = false;
      if (problems.size() > 10) {
        problems = problems.subList(0, 10);
        more = true;
      }

      String message = String.format("There were errors %s files and/or directories", presentParticiple);
      if (summary.fileCount > 0) {
        message += String.format(", although %,d %s %s successfully %s for a total of size of %,d %s",
                                 summary.fileCount,
                                 fileString,
                                 StringUtil.pluralize("was", summary.fileCount),
                                 pastParticiple,
                                 summary.byteCount,
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

    private <V> void logFuture(@NotNull ListenableFuture<V> future, @NotNull String message) {
      long startNano = System.nanoTime();
      LOGGER.trace(String.format(">>> %s", message));
      myEdtExecutor.addListener(future, () -> {
        long endNano = System.nanoTime();
        LOGGER.trace(String.format("<<< %s: %,d ms", message, (endNano - startNano) / 1_000_000));
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
      FileChooser.chooseFiles(descriptor, myProject, null, files -> {
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

      ListenableFuture<Long> futureSize = downloadFileEntry(treeNode, localPath);
      return myEdtExecutor.transform(futureSize, aLong -> localPath);
    }

    @NotNull
    private ListenableFuture<Long> downloadFileEntry(@NotNull DeviceFileEntryNode treeNode, @NotNull Path localPath) {
      // Download the entry to the local path
      DeviceFileEntry entry = treeNode.getEntry();
      startNodeDownload(treeNode);
      AtomicReference<Long> sizeRef = new AtomicReference<>();
      ListenableFuture<Void> futureDownload = myFileManager.downloadFileEntry(entry, localPath, new FileTransferProgress() {
        @Override
        public void progress(long currentBytes, long totalBytes) {
          treeNode.setTransferProgress(currentBytes, totalBytes);
          sizeRef.set(totalBytes);
        }

        @Override
        public boolean isCancelled() {
          return false;
        }
      });
      myEdtExecutor.addListener(futureDownload, () -> stopNodeDownload(treeNode));
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
          // Save selection
          TreePath[] oldSelections = getTreeSelectionModel().getSelectionPaths();

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
        .forEach(x -> restorePathSelection(parentPath, x, newSelections));

      TreePath[] newSelectionArray = ArrayUtil.toObjectArray(newSelections.stream().collect(Collectors.toList()), TreePath.class);
      treeSelectionModel.addSelectionPaths(newSelectionArray);
    }

    private void restorePathSelection(@NotNull TreePath parentPath,
                                      @NotNull TreePath oldPath,
                                      @NotNull Set<TreePath> selections) {

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
