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
package com.android.tools.idea.explorer.adbimpl

import com.android.ddmlib.IDevice
import com.android.tools.idea.explorer.adbimpl.AdbDeviceCapabilities
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.adb.AdbShellCommandsUtil
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.explorer.adbimpl.AdbPathUtil
import com.android.tools.idea.adb.AdbShellCommandException
import com.android.tools.idea.adb.AdbShellCommandResult
import com.android.tools.idea.explorer.adbimpl.AdbFileOperations
import com.android.tools.idea.explorer.adbimpl.UniqueFileNameGenerator
import com.android.tools.idea.explorer.adbimpl.AdbShellCommandBuilder
import kotlin.Throws
import com.android.ddmlib.AdbCommandRejectedException
import com.android.ddmlib.ShellCommandUnresponsiveException
import java.io.IOException
import com.android.ddmlib.SyncException
import com.android.ddmlib.TimeoutException
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.util.text.StringUtil
import java.util.Objects
import java.util.concurrent.Executor
import java.util.stream.Collectors

class AdbFileOperations(private val myDevice: IDevice, deviceCapabilities: AdbDeviceCapabilities, taskExecutor: Executor) {
  private val myExecutor: FutureCallbackExecutor
  private val myDeviceCapabilities: AdbDeviceCapabilities
  private val myShellCommandsUtil = AdbShellCommandsUtil(StudioFlags.ADBLIB_MIGRATION_DEVICE_EXPLORER.get())
  fun createNewFile(parentPath: String, fileName: String): ListenableFuture<Unit> {
    return createNewFileRunAs(parentPath, fileName, null)
  }

  fun createNewFileRunAs(parentPath: String, fileName: String, runAs: String?): ListenableFuture<Unit> {
    return myExecutor.executeAsync {
      if (fileName.contains(AdbPathUtil.FILE_SEPARATOR)) {
        throw AdbShellCommandException.create("File name \"%s\" contains invalid characters", fileName)
      }
      val remotePath = AdbPathUtil.resolve(parentPath, fileName)

      // Check remote file does not exists, so that we can give a relevant error message.
      // The check + create below is not an atomic operation, but this service does not
      // aim to guarantee strong atomicity for file system operations.
      val command: String
      command = if (myDeviceCapabilities.supportsTestCommand()) {
        getCommand(runAs, "test -e ").withEscapedPath(remotePath).build()
      } else {
        getCommand(runAs, "ls -d -a ").withEscapedPath(remotePath).build()
      }
      val commandResult = myShellCommandsUtil.executeCommand(myDevice, command)
      if (!commandResult.isError) {
        throw AdbShellCommandException.create("File \"%s\" already exists on device", remotePath)
      }
      touchFileRunAs(remotePath, runAs)
      Unit
    }
  }

  fun createNewDirectory(parentPath: String, directoryName: String): ListenableFuture<Unit> {
    return createNewDirectoryRunAs(parentPath, directoryName, null)
  }

  fun createNewDirectoryRunAs(parentPath: String, directoryName: String, runAs: String?): ListenableFuture<Unit> {
    return myExecutor.executeAsync {
      if (directoryName.contains(AdbPathUtil.FILE_SEPARATOR)) {
        throw AdbShellCommandException.create("Directory name \"%s\" contains invalid characters", directoryName)
      }

      // "mkdir" fails if the file/directory already exists
      val remotePath = AdbPathUtil.resolve(parentPath, directoryName)
      val command = getCommand(runAs, "mkdir ").withEscapedPath(remotePath).build()
      val commandResult = myShellCommandsUtil.executeCommand(myDevice, command)
      commandResult.throwIfError()
      Unit
    }
  }

  fun listPackages(): ListenableFuture<List<String?>> {
    return myExecutor.executeAsync {
      val command = getCommand(null, "pm list packages").build()
      val commandResult = myShellCommandsUtil.executeCommand(myDevice, command)
      commandResult.throwIfError()
      commandResult.output.stream()
        .map { line: String -> processPackageListLine(line) }
        .filter { x: String? -> !StringUtil.isEmpty(x) }
        .collect(Collectors.toList())
    }
  }

  fun listPackageInfo(): ListenableFuture<List<PackageInfo?>> {
    return myExecutor.executeAsync {
      val command = getCommand(null, "pm list packages -f").build()
      val commandResult = myShellCommandsUtil.executeCommand(myDevice, command)
      commandResult.throwIfError()
      commandResult.output.stream()
        .map { line: String -> processPackageInfoLine(line) }
        .filter { obj: PackageInfo? -> Objects.nonNull(obj) }
        .collect(Collectors.toList())
    }
  }

  class PackageInfo(val packageName: String, val path: String) {
    override fun toString(): String {
      return String.format("%s: path=%s", packageName, path)
    }
  }

  fun deleteFile(path: String): ListenableFuture<Unit> {
    return deleteFileRunAs(path, null)
  }

  fun deleteFileRunAs(path: String, runAs: String?): ListenableFuture<Unit> {
    return myExecutor.executeAsync {
      val command = getRmCommand(runAs, path, false)
      val commandResult = myShellCommandsUtil.executeCommand(myDevice, command)
      commandResult.throwIfError()
      Unit
    }
  }

  fun deleteRecursive(path: String): ListenableFuture<Unit> {
    return deleteRecursiveRunAs(path, null)
  }

  fun deleteRecursiveRunAs(path: String, runAs: String?): ListenableFuture<Unit> {
    return myExecutor.executeAsync {
      val command = getRmCommand(runAs, path, true)
      val commandResult = myShellCommandsUtil.executeCommand(myDevice, command)
      commandResult.throwIfError()
      Unit
    }
  }

  fun copyFile(source: String, destination: String): ListenableFuture<Unit> {
    return copyFileRunAs(source, destination, null)
  }

  fun copyFileRunAs(source: String, destination: String, runAs: String?): ListenableFuture<Unit> {
    return myExecutor.executeAsync {
      val command: String
      command = if (myDeviceCapabilities.supportsCpCommand()) {
        getCommand(runAs, "cp ").withEscapedPath(source).withText(" ").withEscapedPath(destination).build()
      } else {
        getCommand(runAs, "cat ").withEscapedPath(source).withText(" >").withEscapedPath(destination).build()
      }
      val commandResult = myShellCommandsUtil.executeCommand(myDevice, command)
      commandResult.throwIfError()
      Unit
    }
  }

  fun createTempFile(tempPath: String): ListenableFuture<String> {
    return createTempFileRunAs(tempPath, null)
  }

  fun createTempFileRunAs(tempDirectoy: String, runAs: String?): ListenableFuture<String> {
    return myExecutor.executeAsync {

      // Note: Instead of using "mktemp", we use our own unique filename generation + a call to "touch"
      //       for 2 reasons:
      //       * mktemp is not available on all API levels
      //       * mktemp creates a file with 600 permission, meaning the file is not
      //         accessible by processes running as "run-as"
      val tempFileName = UniqueFileNameGenerator.getInstance().getUniqueFileName("temp", "")
      val remotePath = AdbPathUtil.resolve(tempDirectoy, tempFileName)
      touchFileRunAs(remotePath, runAs)
      remotePath
    }
  }

  fun touchFileAsDefaultUser(remotePath: String): ListenableFuture<Unit> {
    return myExecutor.executeAsync {
      val command: String
      command = if (myDeviceCapabilities.supportsTouchCommand()) {
        // Touch creates an empty file if the file does not exist.
        // Touch fails if there are permissions errors.
        AdbShellCommandBuilder().withText("touch ").withEscapedPath(remotePath).build()
      } else {
        AdbShellCommandBuilder().withText("echo -n >").withEscapedPath(remotePath).build()
      }
      val commandResult = myShellCommandsUtil.executeCommand(myDevice, command)
      commandResult.throwIfError()
      Unit
    }
  }

  @Throws(
    TimeoutException::class,
    AdbCommandRejectedException::class,
    ShellCommandUnresponsiveException::class,
    IOException::class,
    AdbShellCommandException::class
  )
  private fun touchFileRunAs(remotePath: String, runAs: String?) {
    val command: String
    command = if (myDeviceCapabilities.supportsTouchCommand()) {
      // Touch creates an empty file if the file does not exist.
      // Touch fails if there are permissions errors.
      getCommand(runAs, "touch ").withEscapedPath(remotePath).build()
    } else {
      getCommand(runAs, "echo -n >").withEscapedPath(remotePath).build()
    }
    val commandResult = myShellCommandsUtil.executeCommand(myDevice, command)
    commandResult.throwIfError()
  }

  @Throws(
    TimeoutException::class,
    AdbCommandRejectedException::class,
    ShellCommandUnresponsiveException::class,
    IOException::class,
    SyncException::class
  )
  private fun getRmCommand(runAs: String?, path: String, recursive: Boolean): String {
    return if (myDeviceCapabilities.supportsRmForceFlag()) {
      getCommand(runAs, String.format("rm %s-f ", if (recursive) "-r " else "")).withEscapedPath(path).build()
    } else {
      getCommand(runAs, String.format("rm %s", if (recursive) "-r " else "")).withEscapedPath(path).build()
    }
  }

  @Throws(TimeoutException::class, AdbCommandRejectedException::class, ShellCommandUnresponsiveException::class, IOException::class)
  private fun getCommand(runAs: String?, text: String): AdbShellCommandBuilder {
    val command = AdbShellCommandBuilder()
    if (myDeviceCapabilities.supportsSuRootCommand()) {
      command.withSuRootPrefix()
    } else if (runAs != null) {
      command.withRunAs(runAs)
    }
    return command.withText(text)
  }

  companion object {
    private fun processPackageListLine(line: String): String? {
      val prefix = "package:"
      return if (!line.startsWith(prefix)) {
        null
      } else line.substring(prefix.length)
    }

    private fun processPackageInfoLine(line: String): PackageInfo? {
      // Format is: package:<path>=<name>
      val prefix = "package:"
      if (!line.startsWith(prefix)) {
        return null
      }
      val separatorIndex = line.indexOf('=', prefix.length)
      if (separatorIndex < 0) {
        return null
      }
      val path = line.substring(prefix.length, separatorIndex).trim { it <= ' ' }
      if (StringUtil.isEmpty(path)) {
        return null
      }
      val packageName = line.substring(separatorIndex + 1).trim { it <= ' ' }
      return if (StringUtil.isEmpty(packageName)) {
        null
      } else PackageInfo(packageName, path)
    }
  }

  init {
    myExecutor = FutureCallbackExecutor.wrap(taskExecutor)
    myDeviceCapabilities = deviceCapabilities
  }
}