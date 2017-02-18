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

import com.android.ddmlib.IDevice;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executor;

public class AdbFileOperations {
  @NotNull private static final Logger LOGGER = Logger.getInstance(AdbFileOperations.class);

  @NotNull private final IDevice myDevice;
  @NotNull private final Executor myExecutor;

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
        String command = String.format("test -e '%s'", AdbFileListing.getEscapedPath(remotePath));
        AdbShellCommandResult commandResult = AdbShellCommandsUtil.executeCommand(myDevice, command);
        if (!commandResult.isError()) {
          futureResult.setException(AdbShellCommandException.create("File \"%s\" already exists on device", remotePath));
          return;
        }

        // Touch creates an empty file if the file does not exist.
        // Touch fails if there are permissions errors.
        command = String.format("touch '%s'", AdbFileListing.getEscapedPath(remotePath));
        commandResult = AdbShellCommandsUtil.executeCommand(myDevice, command);
        commandResult.ThrowIfError();

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
        String command = String.format("mkdir '%s'", AdbFileListing.getEscapedPath(remotePath));
        AdbShellCommandResult commandResult = AdbShellCommandsUtil.executeCommand(myDevice, command);
        commandResult.ThrowIfError();

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
        String command = String.format("rm -f %s", AdbFileListing.getEscapedPath(path));
        AdbShellCommandResult commandResult = AdbShellCommandsUtil.executeCommand(myDevice, command);
        commandResult.ThrowIfError();

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
        String command = String.format("rm -r -f %s", AdbFileListing.getEscapedPath(path));
        AdbShellCommandResult commandResult = AdbShellCommandsUtil.executeCommand(myDevice, command);
        commandResult.ThrowIfError();

        // All done
        futureResult.set(null);
      }
      catch (Throwable t) {
        futureResult.setException(t);
      }
    });

    return futureResult;
  }
}
