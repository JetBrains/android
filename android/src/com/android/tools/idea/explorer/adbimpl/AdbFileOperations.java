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
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

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
    return createNewFileRunAs(parentPath, fileName, null);
  }

  @NotNull
  public ListenableFuture<Void> createNewFileRunAs(@NotNull String parentPath, @NotNull String fileName, @Nullable String runAs) {
    return myExecutor.executeAsync(() -> {
      if (fileName.contains(AdbPathUtil.FILE_SEPARATOR)) {
        throw AdbShellCommandException.create("File name \"%s\" contains invalid characters", fileName);
      }

      String remotePath = AdbPathUtil.resolve(parentPath, fileName);

      // Check remote file does not exists, so that we can give a relevant error message.
      // The check + create below is not an atomic operation, but this service does not
      // aim to guarantee strong atomicity for file system operations.
      String command;
      if (myDeviceCapabilities.supportsTestCommand()) {
        command = getCommand(runAs, "test -e ").withEscapedPath(remotePath).build();
      }
      else {
        command = getCommand(runAs, "ls -d -a ").withEscapedPath(remotePath).build();
      }
      AdbShellCommandResult commandResult = AdbShellCommandsUtil.executeCommand(myDevice, command);
      if (!commandResult.isError()) {
        throw AdbShellCommandException.create("File \"%s\" already exists on device", remotePath);
      }

      touchFileRunAs(remotePath, runAs);

      // All done
      return null;
    });
  }

  @NotNull
  public ListenableFuture<Void> createNewDirectory(@NotNull String parentPath, @NotNull String directoryName) {
    return createNewDirectoryRunAs(parentPath, directoryName, null);
  }

  @NotNull
  public ListenableFuture<Void> createNewDirectoryRunAs(@NotNull String parentPath, @NotNull String directoryName, @Nullable String runAs) {
    return myExecutor.executeAsync(() -> {
      if (directoryName.contains(AdbPathUtil.FILE_SEPARATOR)) {
        throw AdbShellCommandException.create("Directory name \"%s\" contains invalid characters", directoryName);
      }

      // "mkdir" fails if the file/directory already exists
      String remotePath = AdbPathUtil.resolve(parentPath, directoryName);
      String command = getCommand(runAs, "mkdir ").withEscapedPath(remotePath).build();
      AdbShellCommandResult commandResult = AdbShellCommandsUtil.executeCommand(myDevice, command);
      commandResult.throwIfError();

      // All done
      return null;
    });
  }

  public ListenableFuture<List<String>> listPackages() {
    return myExecutor.executeAsync(() -> {
      String command = getCommand(null, "pm list packages").build();
      AdbShellCommandResult commandResult = AdbShellCommandsUtil.executeCommand(myDevice, command);
      commandResult.throwIfError();

      return commandResult.getOutput().stream()
        .map(AdbFileOperations::processPackageListLine)
        .filter(x -> !StringUtil.isEmpty(x))
        .collect(Collectors.toList());
    });
  }

  @Nullable
  private static String processPackageListLine(@NotNull String line) {
    String prefix = "package:";
    if (!line.startsWith(prefix)) {
      return null;
    }
    return line.substring(prefix.length());
  }

  public ListenableFuture<List<PackageInfo>> listPackageInfo() {
    return myExecutor.executeAsync(() -> {
      String command = getCommand(null, "pm list packages -f").build();
      AdbShellCommandResult commandResult = AdbShellCommandsUtil.executeCommand(myDevice, command);
      commandResult.throwIfError();

      return commandResult.getOutput().stream()
        .map(AdbFileOperations::processPackageInfoLine)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
    });
  }

  public static class PackageInfo {
    @NotNull private final String myName;
    @NotNull private final String myPath;

    public PackageInfo(@NotNull String name, @NotNull String path) {
      myName = name;
      myPath = path;
    }

    @NotNull
    public String getPackageName() {
      return myName;
    }

    @NotNull
    public String getPath() {
      return myPath;
    }

    @Override
    public String toString() {
      return String.format("%s: path=%s", myName, myPath);
    }
  }

  @Nullable
  private static PackageInfo processPackageInfoLine(@NotNull String line) {
    // Format is: package:<path>=<name>
    String prefix = "package:";
    if (!line.startsWith(prefix)) {
      return null;
    }
    int separatorIndex = line.indexOf('=', prefix.length());
    if (separatorIndex < 0) {
      return null;
    }
    String path = line.substring(prefix.length(), separatorIndex).trim();
    if (StringUtil.isEmpty(path)) {
      return null;
    }

    String packageName = line.substring(separatorIndex + 1).trim();
    if (StringUtil.isEmpty(packageName)) {
      return null;
    }

    return new PackageInfo(packageName, path);
  }

  @NotNull
  public ListenableFuture<Void> deleteFile(@NotNull String path) {
    return deleteFileRunAs(path, null);
  }

  @NotNull
  public ListenableFuture<Void> deleteFileRunAs(@NotNull String path, @Nullable String runAs) {
    return myExecutor.executeAsync(() -> {
      String command = getRmCommand(runAs, path, false);
      AdbShellCommandResult commandResult = AdbShellCommandsUtil.executeCommand(myDevice, command);
      commandResult.throwIfError();

      // All done
      return null;
    });
  }

  @NotNull
  public ListenableFuture<Void> deleteRecursive(@NotNull String path) {
    return deleteRecursiveRunAs(path, null);
  }

  @NotNull
  public ListenableFuture<Void> deleteRecursiveRunAs(@NotNull String path, @Nullable String runAs) {
    return myExecutor.executeAsync(() -> {
      String command = getRmCommand(runAs, path, true);
      AdbShellCommandResult commandResult = AdbShellCommandsUtil.executeCommand(myDevice, command);
      commandResult.throwIfError();

      // All done
      return null;
    });
  }

  @NotNull
  public ListenableFuture<Void> copyFile(@NotNull String source, @NotNull String destination) {
    return copyFileRunAs(source, destination, null);
  }

  @NotNull
  public ListenableFuture<Void> copyFileRunAs(@NotNull String source, @NotNull String destination, @Nullable String runAs) {
    return myExecutor.executeAsync(() -> {
      String command;
      if (myDeviceCapabilities.supportsCpCommand()) {
        command = getCommand(runAs, "cp ").withEscapedPath(source).withText(" ").withEscapedPath(destination).build();
      }
      else {
        command = getCommand(runAs, "cat ").withEscapedPath(source).withText(" >").withEscapedPath(destination).build();
      }
      AdbShellCommandResult commandResult = AdbShellCommandsUtil.executeCommand(myDevice, command);
      commandResult.throwIfError();
      return null;
    });
  }

  @NotNull
  public ListenableFuture<String> createTempFile(@NotNull String tempPath) {
    return createTempFileRunAs(tempPath, null);
  }

  @SuppressWarnings({"SameParameterValue", "WeakerAccess"})
  @NotNull
  public ListenableFuture<String> createTempFileRunAs(@NotNull String tempDirectoy, @Nullable String runAs) {
    return myExecutor.executeAsync(() -> {
      // Note: Instead of using "mktemp", we use our own unique filename generation + a call to "touch"
      //       for 2 reasons:
      //       * mktemp is not available on all API levels
      //       * mktemp creates a file with 600 permission, meaning the file is not
      //         accessible by processes running as "run-as"
      String tempFileName = UniqueFileNameGenerator.getInstance().getUniqueFileName("temp", "");
      String remotePath = AdbPathUtil.resolve(tempDirectoy, tempFileName);
      touchFileRunAs(remotePath, runAs);
      return remotePath;
    });
  }

  @NotNull
  public ListenableFuture<Void> touchFileAsDefaultUser(@NotNull String remotePath) {
    return myExecutor.executeAsync(() -> {
      String command;
      if (myDeviceCapabilities.supportsTouchCommand()) {
        // Touch creates an empty file if the file does not exist.
        // Touch fails if there are permissions errors.
        command = new AdbShellCommandBuilder().withText("touch ").withEscapedPath(remotePath).build();
      }
      else {
        command = new AdbShellCommandBuilder().withText("echo -n >").withEscapedPath(remotePath).build();
      }
      AdbShellCommandResult commandResult = AdbShellCommandsUtil.executeCommand(myDevice, command);
      commandResult.throwIfError();
      return null;
    });
  }

  private void touchFileRunAs(@NotNull String remotePath, @Nullable String runAs)
    throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException, AdbShellCommandException {
    String command;
    if (myDeviceCapabilities.supportsTouchCommand()) {
      // Touch creates an empty file if the file does not exist.
      // Touch fails if there are permissions errors.
      command = getCommand(runAs, "touch ").withEscapedPath(remotePath).build();
    }
    else {
      command = getCommand(runAs, "echo -n >").withEscapedPath(remotePath).build();
    }
    AdbShellCommandResult commandResult = AdbShellCommandsUtil.executeCommand(myDevice, command);
    commandResult.throwIfError();
  }

  @NotNull
  private String getRmCommand(@Nullable String runAs, @NotNull String path, boolean recursive)
    throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException, SyncException {
    if (myDeviceCapabilities.supportsRmForceFlag()) {
      return getCommand(runAs, String.format("rm %s-f ", (recursive ? "-r " : ""))).withEscapedPath(path).build();
    }
    else {
      return getCommand(runAs, String.format("rm %s", (recursive ? "-r " : ""))).withEscapedPath(path).build();
    }
  }

  @NotNull
  private AdbShellCommandBuilder getCommand(@Nullable String runAs, @NotNull String text)
    throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException {
    AdbShellCommandBuilder command = new AdbShellCommandBuilder();
    if (myDeviceCapabilities.supportsSuRootCommand()) {
      command.withSuRootPrefix();
    }
    else if (runAs != null) {
      command.withRunAs(runAs);
    }
    return command.withText(text);
  }
}