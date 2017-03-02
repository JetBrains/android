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
package com.android.tools.idea.explorer.adbimpl;

import com.android.ddmlib.*;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executor;

public class AdbFileOperations {
  @NotNull private static final Logger LOGGER = Logger.getInstance(AdbFileOperations.class);
  @NotNull private final IDevice myDevice;
  @NotNull private final Executor myExecutor;
  @NotNull private final DeviceCapabilities myDeviceCapabilities = new DeviceCapabilities();

  public AdbFileOperations(@NotNull IDevice device, @NotNull Executor taskExecutor) {
    myDevice = device;
    myExecutor = taskExecutor;
  }

  @NotNull
  public ListenableFuture<Void> createNewFile(@NotNull String parentPath, @NotNull String fileName) {
    SettableFuture<Void> futureResult = SettableFuture.create();

    if (fileName.contains(AdbPathUtil.FILE_SEPARATOR)) {
      futureResult.setException(AdbShellCommandException.create("File name \"%s\" contains invalid characters", fileName));
      return futureResult;
    }

    myExecutor.execute(() -> {
      try {
        String remotePath = AdbPathUtil.resolve(parentPath, fileName);

        // Check remote file does not exists, so that we can give a relevant error message.
        // The check + create below is not an atomic operation, but this service does not
        // aim to guarantee strong atomicity for file system operations.
        String command;
        if (myDeviceCapabilities.supportsTestCommand()) {
          command = String.format("test -e %s", AdbFileListing.getEscapedPath(remotePath));
        }
        else {
          command = String.format("ls -d -a %s", AdbFileListing.getEscapedPath(remotePath));
        }
        AdbShellCommandResult commandResult = AdbShellCommandsUtil.executeCommand(myDevice, command);
        if (!commandResult.isError()) {
          futureResult.setException(AdbShellCommandException.create("File \"%s\" already exists on device", remotePath));
          return;
        }

        if (myDeviceCapabilities.supportsTouchCommand()) {
          // Touch creates an empty file if the file does not exist.
          // Touch fails if there are permissions errors.
          command = String.format("touch %s", AdbFileListing.getEscapedPath(remotePath));
        }
        else {
          command = String.format("echo -n >%s", AdbFileListing.getEscapedPath(remotePath));
        }
        commandResult = AdbShellCommandsUtil.executeCommand(myDevice, command);
        commandResult.throwIfError();

        // All done
        futureResult.set(null);
      }
      catch (Throwable t) {
        futureResult.setException(t);
      }
    });

    return futureResult;
  }

  @NotNull
  public ListenableFuture<Void> createNewDirectory(@NotNull String parentPath, @NotNull String directoryName) {
    SettableFuture<Void> futureResult = SettableFuture.create();

    if (directoryName.contains(AdbPathUtil.FILE_SEPARATOR)) {
      futureResult.setException(AdbShellCommandException.create("Directory name \"%s\" contains invalid characters", directoryName));
      return futureResult;
    }

    myExecutor.execute(() -> {
      try {
        // mkdir fails if the file/directory already exists
        String remotePath = AdbPathUtil.resolve(parentPath, directoryName);
        String command = String.format("mkdir %s", AdbFileListing.getEscapedPath(remotePath));
        AdbShellCommandResult commandResult = AdbShellCommandsUtil.executeCommand(myDevice, command);
        commandResult.throwIfError();

        // All done
        futureResult.set(null);
      }
      catch (Throwable t) {
        futureResult.setException(t);
      }
    });

    return futureResult;
  }

  @NotNull
  public ListenableFuture<Void> deleteFile(@NotNull String path) {
    SettableFuture<Void> futureResult = SettableFuture.create();

    myExecutor.execute(() -> {
      try {
        String command = getRmCommand(path, false);
        AdbShellCommandResult commandResult = AdbShellCommandsUtil.executeCommand(myDevice, command);
        commandResult.throwIfError();

        // All done
        futureResult.set(null);
      }
      catch (Throwable t) {
        futureResult.setException(t);
      }
    });

    return futureResult;
  }

  @NotNull
  public ListenableFuture<Void> deleteRecursive(@NotNull String path) {
    SettableFuture<Void> futureResult = SettableFuture.create();

    myExecutor.execute(() -> {
      try {
        String command = getRmCommand(path, true);
        AdbShellCommandResult commandResult = AdbShellCommandsUtil.executeCommand(myDevice, command);
        commandResult.throwIfError();

        // All done
        futureResult.set(null);
      }
      catch (Throwable t) {
        futureResult.setException(t);
      }
    });

    return futureResult;
  }

  @NotNull
  private String getRmCommand(@NotNull String path, boolean recursive)
    throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException, SyncException {
    if (myDeviceCapabilities.supportsRmForceFlag()) {
      return String.format("rm %s-f %s", (recursive ? "-r " : ""), AdbFileListing.getEscapedPath(path));
    }
    else {
      return String.format("rm %s%s", (recursive ? "-r " : ""), AdbFileListing.getEscapedPath(path));
    }
  }

  @NotNull
  private static String getDeviceTraceInfo(@NotNull IDevice device) {
    return String.format("%s(%s)-%s", device.getName(), device.getSerialNumber(), device.getState());
  }

  private class DeviceCapabilities {
    @NotNull private static final String PROBE_FILES_TEMP_PATH = "/data/local/tmp/device-explorer";
    @Nullable private Boolean mySupportsTestCommand;
    @Nullable private Boolean mySupportsRmForceFlag;
    @Nullable private Boolean mySupportsTouchCommand;

    public synchronized boolean supportsTestCommand()
      throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException, SyncException {
      if (mySupportsTestCommand == null) {
        mySupportsTestCommand = supportsTestCommandWorker();
      }
      return mySupportsTestCommand;
    }

    public synchronized boolean supportsRmForceFlag()
      throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException, SyncException {
      if (mySupportsRmForceFlag == null) {
        mySupportsRmForceFlag = supportsRmForceFlagWorker();
      }
      return mySupportsRmForceFlag;
    }

    public synchronized boolean supportsTouchCommand()
      throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException {
      if (mySupportsTouchCommand == null) {
        mySupportsTouchCommand = supportsTouchCommandWorker();
      }
      return mySupportsTouchCommand;
    }

    private boolean supportsTestCommandWorker()
      throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException, SyncException {

      try (ScopedRemoteFile tempFile = new ScopedRemoteFile(AdbPathUtil.resolve(PROBE_FILES_TEMP_PATH, ".__temp_test_test__file__.tmp"))) {
        // Create the remote file used for testing capability
        tempFile.create();

        // Try the "test" command on it (it should succeed if the command is supported)
        String command = String.format("test -e %s", AdbFileListing.getEscapedPath(tempFile.getRemotePath()));
        AdbShellCommandResult commandResult = AdbShellCommandsUtil.executeCommand(myDevice, command);
        try {
          commandResult.throwIfError();
          return true;
        }
        catch (AdbShellCommandException e) {
          LOGGER.info(String.format("Device \"%s\" does not seem to support the \"test\" command",
                                    getDeviceTraceInfo(myDevice)),
                      e);
          return false;
        }
      }
    }

    public synchronized boolean supportsRmForceFlagWorker()
      throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException, SyncException {

      try (ScopedRemoteFile tempFile = new ScopedRemoteFile(AdbPathUtil.resolve(PROBE_FILES_TEMP_PATH, ".__temp_rm_test_file__.tmp"))) {
        // Create the remote file used for testing capability
        tempFile.create();

        // Try to delete it with "rm -f" (it should work if th command is supported)
        String command = String.format("rm -f %s", AdbFileListing.getEscapedPath(tempFile.getRemotePath()));
        AdbShellCommandResult commandResult = AdbShellCommandsUtil.executeCommand(myDevice, command);
        try {
          commandResult.throwIfError();
          // If no error, "rm -f" is supported and test file has been deleted, so no need to delete it again.
          tempFile.setDeleteOnClose(false);
          return true;
        }
        catch (AdbShellCommandException e) {
          LOGGER.info(String.format("Device \"%s\" does not seem to support \"-f\" flag for rm", getDeviceTraceInfo(myDevice)), e);
          return false;
        }
      }
    }

    private boolean supportsTouchCommandWorker()
      throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException {

      try (ScopedRemoteFile tempFile = new ScopedRemoteFile(AdbPathUtil.resolve(PROBE_FILES_TEMP_PATH, ".__temp_touch_test_file__.tmp"))) {

        // Try the create the file with the "touch" command
        String command = String.format("touch %s", tempFile.getRemotePath());
        AdbShellCommandResult commandResult = AdbShellCommandsUtil.executeCommand(myDevice, command);
        try {
          commandResult.throwIfError();

          // If "touch" did not work, we want to delete the temporary file
          tempFile.setDeleteOnClose(true);
          return true;
        }
        catch (AdbShellCommandException e) {
          LOGGER.info(String.format("Device \"%s\" does not seem to support \"touch\" command",
                                    getDeviceTraceInfo(myDevice)),
                      e);
          return false;
        }
      }
    }

    /**
     * An {@link AutoCloseable} wrapper around a temporary file on a remote device.
     * The {@link #close()} method attempts to delete the file from the remote device
     * unless the {@link #setDeleteOnClose(boolean) setDeletedOnClose(false)} is called.
     */
    private class ScopedRemoteFile implements AutoCloseable {
      @NotNull private final String myRemotePath;
      private boolean myDeleteOnClose;

      public ScopedRemoteFile(@NotNull String remotePath) {
        myRemotePath = remotePath;
      }

      public void setDeleteOnClose(boolean value) {
        myDeleteOnClose = value;
      }

      @NotNull
      public String getRemotePath() {
        return myRemotePath;
      }

      public void create() throws TimeoutException, AdbCommandRejectedException, SyncException, IOException {
        assert !myDeleteOnClose;
        myDeleteOnClose = createRemoteTemporaryFile();
      }

      @Override
      public void close() {
        if (!myDeleteOnClose) {
          return;
        }

        try {
          String command = String.format("rm %s", AdbFileListing.getEscapedPath(myRemotePath));
          AdbShellCommandResult commandResult = AdbShellCommandsUtil.executeCommand(myDevice, command);
          commandResult.throwIfError();
        }
        catch (Exception e) {
          // There is not much we can do if we can't delete the test file other than logging the error.
          LOGGER.warn(String.format("Device \"%s\": Error deleting temporary test file \"%s\"",
                                    getDeviceTraceInfo(myDevice),
                                    myRemotePath),
                      e);
        }
      }

      /**
       * Create an empty file on the remote device by first creating a local empty temporary file,
       * then pushing it to the remote device.
       */
      private boolean createRemoteTemporaryFile()
        throws IOException, TimeoutException, AdbCommandRejectedException, SyncException {

        File file = FileUtil.createTempFile(myRemotePath, "", true);
        try {
          SyncService sync = myDevice.getSyncService();
          if (sync == null) {
            throw new IOException(String.format("Device \"%s\": Unable to open sync connection",
                                                getDeviceTraceInfo(myDevice)));
          }

          try {
            LOGGER.trace(String.format("Device \"%s\": Uploading temporary file \"%s\" to remote file \"%s\"",
                                       getDeviceTraceInfo(myDevice),
                                       file,
                                       myRemotePath));
            sync.pushFile(file.getPath(), myRemotePath, SyncService.getNullProgressMonitor());
            return true;
          }
          finally {
            sync.close();
          }
        }
        finally {
          try {
            FileUtil.delete(file);
          }
          catch (Exception e) {
            LOGGER.warn(String.format("Device \"%s\": Error deleting temporary file \"%s\"",
                                      getDeviceTraceInfo(myDevice),
                                      file),
                        e);
          }
        }
      }
    }
  }
}
