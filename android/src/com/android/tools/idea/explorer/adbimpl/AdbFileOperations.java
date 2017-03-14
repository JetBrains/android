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
import com.android.tools.idea.explorer.FutureCallbackExecutor;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.concurrent.Executor;

public class AdbFileOperations {
  @NotNull private final IDevice myDevice;
  @NotNull private final FutureCallbackExecutor myExecutor;
  @NotNull private final AdbDeviceCapabilities myDeviceCapabilities;

  public AdbFileOperations(@NotNull IDevice device, @NotNull AdbDeviceCapabilities deviceCapabilities, @NotNull Executor taskExecutor) {
    myDevice = device;
    myExecutor = FutureCallbackExecutor.wrap(taskExecutor);
    myDeviceCapabilities = deviceCapabilities;
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
          command = getCommand("test -e ").withEscapedPath(remotePath).build();
        }
        else {
          command = getCommand("ls -d -a ").withEscapedPath(remotePath).build();
        }
        AdbShellCommandResult commandResult = AdbShellCommandsUtil.executeCommand(myDevice, command);
        if (!commandResult.isError()) {
          futureResult.setException(AdbShellCommandException.create("File \"%s\" already exists on device", remotePath));
          return;
        }

        if (myDeviceCapabilities.supportsTouchCommand()) {
          // Touch creates an empty file if the file does not exist.
          // Touch fails if there are permissions errors.
          command = getCommand("touch ").withEscapedPath(remotePath).build();
        }
        else {
          command = getCommand("cat </dev/null >").withEscapedPath(remotePath).build();
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
        // "mkdir" fails if the file/directory already exists
        String remotePath = AdbPathUtil.resolve(parentPath, directoryName);
        String command = getCommand("mkdir ").withEscapedPath(remotePath).build();
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
      return getCommand(String.format("rm %s-f ", (recursive ? "-r " : ""))).withEscapedPath(path).build();
    }
    else {
      return getCommand(String.format("rm %s", (recursive ? "-r " : ""))).withEscapedPath(path).build();
    }
  }

  @NotNull
  private AdbShellCommandBuilder getCommand(@NotNull String text)
    throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException {
    AdbShellCommandBuilder command = new AdbShellCommandBuilder();
    if (myDeviceCapabilities.supportsSuRootCommand()) {
      command.withSuRootPrefix();
    }
    return command.withText(text);
  }
}