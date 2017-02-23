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
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.CommonBundle;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.fileChooser.FileSaverDialog;
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
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
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
  private int myDownloadingNodeRepaintMillis = 100;

  @NotNull private final Project myProject;
  @NotNull private final DeviceExplorerModel myModel;
  @NotNull private final DeviceExplorerView myView;
  @NotNull private final DeviceFileSystemService myService;
  @NotNull private final FutureCallbackExecutor myEdtExecutor;
  @NotNull private final DeviceExplorerFileManager myFileManager;
  @NotNull private final Set<DeviceFileEntryNode> myDownloadingNodes = new HashSet<>();
  @NotNull private final Set<DeviceFileEntryNode> myLoadingChildren = new HashSet<>();
  @NotNull private final Alarm myLoadingNodesAlarms;
  @NotNull private final Alarm myDownloadingNodesAlarms;
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
    myDownloadingNodesAlarms = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
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
    myDownloadingNodesAlarms.cancelAllRequests();
    myLoadingChildren.clear();
    myDownloadingNodes.clear();

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
  private <T> void executeFuturesInSequence(@NotNull Iterator<T> iterator,
                                            @NotNull Function<T, ListenableFuture<Void>> taskFactory) {
    if (iterator.hasNext()) {
      ListenableFuture<Void> future = taskFactory.apply(iterator.next());
      myEdtExecutor.addConsumer(future, (aVoid, throwable) -> executeFuturesInSequence(iterator, taskFactory));
    }
  }

  private void startDownloadNode(@NotNull DeviceFileEntryNode node) {
    myView.startTreeBusyIndicator();
    node.setDownloading(true);
    if (myDownloadingNodes.size() == 0) {
      myDownloadingNodesAlarms.addRequest(new MyDownloadingNodesRepaint(), myDownloadingNodeRepaintMillis);
    }
    myDownloadingNodes.add(node);
  }

  private void stopDownloadNode(@NotNull DeviceFileEntryNode node) {
    myView.stopTreeBusyIndicator();
    node.setDownloading(false);
    if (getTreeModel() != null) {
      getTreeModel().nodeChanged(node);
    }
    myDownloadingNodes.remove(node);
    if (myDownloadingNodes.size() == 0) {
      myDownloadingNodesAlarms.cancelAllRequests();
    }
  }

  private void startLoadChildren(@NotNull DeviceFileEntryNode node) {
    myView.startTreeBusyIndicator();
    if (myLoadingChildren.size() == 0) {
      myLoadingChildrenAlarms.addRequest(new MyLoadingChildrenRepaint(), myDownloadingNodeRepaintMillis);
    }
    myLoadingChildren.add(node);
  }

  private void stopLoadChildren(@NotNull DeviceFileEntryNode node) {
    myView.stopTreeBusyIndicator();
    myLoadingChildren.remove(node);
    if (myLoadingChildren.size() == 0) {
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
  public void setDownloadingNodeRepaintMillis(int downloadingNodeRepaintMillis) {
    myDownloadingNodeRepaintMillis = downloadingNodeRepaintMillis;
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

        if (treeNode.isDownloading()) {
          myView.reportErrorRelatedToNode(treeNode, "Entry is already downloading", new RuntimeException());
          return Futures.immediateFuture(null);
        }

        ListenableFuture<Path> futurePath = downloadFileEntry(treeNode, false);
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
        SettableFuture<Void> futureResult = SettableFuture.create();
        myEdtExecutor.addConsumer(futurePath, (path, throwable) -> futureResult.set(null));
        return futureResult;
      });
    }

    @Override
    public void saveNodeAsInvoked(@NotNull DeviceFileEntryNode treeNode) {
      if (treeNode.getEntry().isDirectory()) {
        return;
      }

      if (treeNode.isDownloading()) {
        myView.reportErrorRelatedToNode(treeNode, "Entry is already downloading", new RuntimeException());
        return;
      }

      ListenableFuture<Path> futurePath = downloadFileEntry(treeNode, true);
      myEdtExecutor.addCallback(futurePath, new FutureCallback<Path>() {
        @Override
        public void onSuccess(@Nullable Path localPath) {
          assert localPath != null;
          String message = String.format("Device file %s successfully downloaded to local file \"%s\"",
                                         getUserFacingNodeName(treeNode),
                                         localPath);
          myView.reportMessageRelatedToNode(treeNode, message);
        }

        @Override
        public void onFailure(@NotNull Throwable t) {
          String message = String.format("Error saving contents of device file %s", getUserFacingNodeName(treeNode));
          myView.reportErrorRelatedToNode(treeNode, message, t);
        }
      });
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

    @NotNull
    private ListenableFuture<Path> downloadFileEntry(@NotNull DeviceFileEntryNode treeNode, boolean askForLocation) {
      SettableFuture<Path> futureResult = SettableFuture.create();

      // Figure out local path, ask user in "Save As" dialog if required
      Path localPath;
      try {
        if (askForLocation) {
          localPath = chooseSaveAsLocalPath(treeNode.getEntry());
        }
        else {
          localPath = myFileManager.getDefaultLocalPathForEntry(treeNode.getEntry());
        }
      }
      catch (Throwable t) {
        futureResult.setException(t);
        return futureResult;
      }

      // Download the entry to the local path
      DeviceFileEntry entry = treeNode.getEntry();
      startDownloadNode(treeNode);
      ListenableFuture<Void> future = myFileManager.downloadFileEntry(entry, localPath, new FileTransferProgress() {
        @Override
        public void progress(long currentBytes, long totalBytes) {
          treeNode.setDownloadProgress(currentBytes, totalBytes);
        }

        @Override
        public boolean isCancelled() {
          return false;
        }
      });

      myEdtExecutor.addCallback(future, new FutureCallback<Void>() {
        @Override
        public void onSuccess(@Nullable Void result) {
          stopDownloadNode(treeNode);
          futureResult.set(localPath);
        }

        @Override
        public void onFailure(@NotNull Throwable t) {
          stopDownloadNode(treeNode);
          futureResult.setException(t);
        }
      });

      return futureResult;
    }

    @NotNull
    private Path chooseSaveAsLocalPath(@NotNull DeviceFileEntry entry) {
      Path localPath = myFileManager.getDefaultLocalPathForEntry(entry);

      FileUtils.mkdirs(localPath.getParent().toFile());
      VirtualFile baseDir = VfsUtil.findFileByIoFile(localPath.getParent().toFile(), true);
      if (baseDir == null) {
        throw new RuntimeException(String.format("Unable to locate file \"%s\"", localPath.getParent()));
      }

      FileSaverDescriptor descriptor = new FileSaverDescriptor("Save As", "");
      FileSaverDialog saveFileDialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, myProject);
      VirtualFileWrapper fileWrapper = saveFileDialog.save(baseDir, localPath.getFileName().toString());
      if (fileWrapper == null) {
        throw new CancellationException();
      }
      return fileWrapper.getFile().toPath();
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

      SettableFuture<Void> futureResult = SettableFuture.create();

      startLoadChildren(node);
      ListenableFuture<List<DeviceFileEntry>> futureEntries = node.getEntry().getEntries();
      myEdtExecutor.addCallback(futureEntries, new FutureCallback<List<DeviceFileEntry>>() {
        @Override
        public void onSuccess(List<DeviceFileEntry> result) {
          // Save selection
          TreePath[] oldSelections = getTreeSelectionModel().getSelectionPaths();
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
          String message = String.format("Unable to list entries of directory %s", getUserFacingNodeName(node));
          myView.reportErrorRelatedToNode(node, message, t);

          node.removeAllChildren();
          node.add(new ErrorNode(message));
          node.setAllowsChildren(true);
          treeModel.nodeStructureChanged(node);
        }
      });

      myEdtExecutor.addConsumer(futureEntries, (entries, throwable) -> {
        stopLoadChildren(node);
        myLoadingNodesAlarms.cancelRequest(showLoadingNode);
        futureResult.set(null);
      });

      return futureResult;
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
      for (int i = 0; i < node.getChildCount(); i++) {
        if (Objects.equals(node.getChildAt(i), oldPath.getLastPathComponent())) {
          return;
        }
      }
      selections.add(parentPath);
    }

    /**
     * Asynchronously update the tree node UI of the {@code symlinkNodes} entries if they target
     * a directory, i.e. update tree nodes with a "Folder" and "Expandable arrow" icon.
     */
    private void querySymbolicLinks(@NotNull List<DeviceFileEntryNode> symlinkNodes, @NotNull DefaultTreeModel treeModel) {
      querySymbolicLinksWorker(symlinkNodes, 0, treeModel);
    }

    private void querySymbolicLinksWorker(@NotNull List<DeviceFileEntryNode> symlinkNodes,
                                          int nodeIndex,
                                          @NotNull DefaultTreeModel treeModel) {
      if (nodeIndex >= symlinkNodes.size()) {
        return;
      }

      // Note: We process (asynchronously) one entry at a time, instead of all of them in parallel,
      //       to avoid flooding the device with too many requests, which would eventually lead
      //       to the device to reject additional requests.
      DeviceFileEntryNode treeNode = symlinkNodes.get(nodeIndex);
      ListenableFuture<Boolean> future = treeNode.getEntry().isSymbolicLinkToDirectory();
      myEdtExecutor.addConsumer(future, (@Nullable Boolean result, @Nullable Throwable throwable) -> {
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
        treeNode.setSymbolicLinkToDirectory(isDirectory);
        treeModel.nodeStructureChanged(treeNode);

        // Asynchronously process the next symlink
        querySymbolicLinksWorker(symlinkNodes, nodeIndex + 1, treeModel);
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

  private class MyDownloadingNodesRepaint implements Runnable {
    @Override
    public void run() {
      myDownloadingNodes.forEach(x -> {
        x.incDownloadingTick();
        if (getTreeModel() != null) {
          getTreeModel().nodeChanged(x);
        }
      });
      myDownloadingNodesAlarms.addRequest(new MyDownloadingNodesRepaint(), myDownloadingNodeRepaintMillis);
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
          MyLoadingNode ladingNode = (MyLoadingNode)node;
          ladingNode.incDownloadingTick();
          if (getTreeModel() != null) {
            getTreeModel().nodeChanged(ladingNode);
          }
        }
      });
      myLoadingChildrenAlarms.addRequest(new MyLoadingChildrenRepaint(), myDownloadingNodeRepaintMillis);
    }
  }
}