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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Helper class used to detect various capabilities/features supported by a {@link IDevice}
 * so callers can make decisions about which adb commands to use.
 */
public class AdbDeviceCapabilities {
  @NotNull private static final Logger LOGGER = Logger.getInstance(AdbDeviceCapabilities.class);
  @NotNull private static final String PROBE_FILES_TEMP_PATH = "/data/local/tmp/device-explorer";
  @NotNull private final IDevice myDevice;
  @Nullable private Boolean mySupportsTestCommand;
  @Nullable private Boolean mySupportsRmForceFlag;
  @Nullable private Boolean mySupportsTouchCommand;
  @Nullable private Boolean mySupportsSuRootCommand;

  public AdbDeviceCapabilities(@NotNull IDevice device) {
    myDevice = device;
  }

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

  public synchronized boolean supportsSuRootCommand()
    throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException {
    if (mySupportsSuRootCommand == null) {
      mySupportsSuRootCommand = supportsSuRootCommandWorker();
    }
    return mySupportsSuRootCommand;
  }

  @NotNull
  private static String getDeviceTraceInfo(@NotNull IDevice device) {
    return String.format("%s(%s)-%s", device.getName(), device.getSerialNumber(), device.getState());
  }

  @NotNull
  private static String getCommandOutputExtract(@NotNull AdbShellCommandResult commandResult) {
    List<String> output = commandResult.getOutput();
    if (output.isEmpty()) {
      return "[command output is empty]";
    }
    return output.stream().limit(5).collect(Collectors.joining("\n  ",  "\n  ", ""));
  }

  private boolean supportsTestCommandWorker()
    throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException, SyncException {

    try (ScopedRemoteFile tempFile = new ScopedRemoteFile(AdbPathUtil.resolve(PROBE_FILES_TEMP_PATH, ".__temp_test_test__file__.tmp"))) {
      // Create the remote file used for testing capability
      tempFile.create();

      // Try the "test" command on it (it should succeed if the command is supported)
      String command = new AdbShellCommandBuilder().withText("test -e ").withEscapedPath(tempFile.getRemotePath()).build();
      AdbShellCommandResult commandResult = AdbShellCommandsUtil.executeCommand(myDevice, command);
      try {
        commandResult.throwIfError();
        return true;
      }
      catch (AdbShellCommandException e) {
        LOGGER.info(String.format("Device \"%s\" does not seem to support the \"test\" command: %s",
                                  getDeviceTraceInfo(myDevice),
                                  getCommandOutputExtract(commandResult)),
                    e);
        return false;
      }
    }
  }

  public boolean supportsRmForceFlagWorker()
    throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException, SyncException {

    try (ScopedRemoteFile tempFile = new ScopedRemoteFile(AdbPathUtil.resolve(PROBE_FILES_TEMP_PATH, ".__temp_rm_test_file__.tmp"))) {
      // Create the remote file used for testing capability
      tempFile.create();

      // Try to delete it with "rm -f" (it should work if th command is supported)
      String command = new AdbShellCommandBuilder().withText("rm -f ").withEscapedPath(tempFile.getRemotePath()).build();
      AdbShellCommandResult commandResult = AdbShellCommandsUtil.executeCommand(myDevice, command);
      try {
        commandResult.throwIfError();
        // If no error, "rm -f" is supported and test file has been deleted, so no need to delete it again.
        tempFile.setDeleteOnClose(false);
        return true;
      }
      catch (AdbShellCommandException e) {
        LOGGER.info(String.format("Device \"%s\" does not seem to support \"-f\" flag for rm: %s",
                                  getDeviceTraceInfo(myDevice),
                                  getCommandOutputExtract(commandResult)),
                    e);
        return false;
      }
    }
  }

  private boolean supportsTouchCommandWorker()
    throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException {

    try (ScopedRemoteFile tempFile = new ScopedRemoteFile(AdbPathUtil.resolve(PROBE_FILES_TEMP_PATH, ".__temp_touch_test_file__.tmp"))) {

      // Try the create the file with the "touch" command
      String command = new AdbShellCommandBuilder().withText("touch ").withEscapedPath(tempFile.getRemotePath()).build();
      AdbShellCommandResult commandResult = AdbShellCommandsUtil.executeCommand(myDevice, command);
      try {
        commandResult.throwIfError();

        // If "touch" did not work, we want to delete the temporary file
        tempFile.setDeleteOnClose(true);
        return true;
      }
      catch (AdbShellCommandException e) {
        LOGGER.info(String.format("Device \"%s\" does not seem to support \"touch\" command: %s",
                                  getDeviceTraceInfo(myDevice),
                                  getCommandOutputExtract(commandResult)),
                    e);
        return false;
      }
    }
  }

  private boolean supportsSuRootCommandWorker()
    throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException {

    // Try a "su" command ("id") that should always succeed, unless "su" is not supported
    String command = new AdbShellCommandBuilder().withSuRootPrefix().withText("id").build();
    AdbShellCommandResult commandResult = AdbShellCommandsUtil.executeCommand(myDevice, command);
    try {
      commandResult.throwIfError();
      return true;
    }
    catch (AdbShellCommandException e) {
      LOGGER.info(String.format("Device \"%s\" does not seem to support the \"su 0\" command: %s",
                                getDeviceTraceInfo(myDevice),
                                getCommandOutputExtract(commandResult)),
                  e);
      return false;
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
        String command = new AdbShellCommandBuilder().withText("rm ").withEscapedPath(myRemotePath).build();
        AdbShellCommandResult commandResult = AdbShellCommandsUtil.executeCommand(myDevice, command);
        try {
          commandResult.throwIfError();
        }
        catch (AdbShellCommandException e) {
          // There is not much we can do if we can't delete the test file other than logging the error.
          LOGGER.warn(String.format("Device \"%s\": Error deleting temporary test file \"%s\": %s",
                                    getDeviceTraceInfo(myDevice),
                                    myRemotePath,
                                    getCommandOutputExtract(commandResult)),
                      e);
        }
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
