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

import com.android.ddmlib.AdbCommandRejectedException
import com.android.ddmlib.IDevice
import com.android.ddmlib.ShellCommandUnresponsiveException
import com.android.ddmlib.SyncException
import com.android.ddmlib.TimeoutException
import com.android.tools.idea.adb.AdbShellCommandException
import com.android.tools.idea.adb.AdbShellCommandsUtil
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.flags.StudioFlags
import com.google.common.base.Strings.emptyToNull
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.util.text.StringUtil
import java.io.IOException
import java.util.concurrent.Executor

class AdbFileOperations(
    private val myDevice: IDevice,
    private val deviceCapabilities: AdbDeviceCapabilities,
    taskExecutor: Executor) {
  private val myExecutor = FutureCallbackExecutor.wrap(taskExecutor)
  private val myShellCommandsUtil = AdbShellCommandsUtil(StudioFlags.ADBLIB_MIGRATION_DEVICE_EXPLORER.get())

  fun createNewFile(parentPath: String, fileName: String): ListenableFuture<Unit> {
    return createNewFileRunAs(parentPath, fileName, null)
  }

  fun createNewFileRunAs(parentPath: String, fileName: String, runAs: String?): ListenableFuture<Unit> {
    return myExecutor.executeAsync {
      if (fileName.contains(AdbPathUtil.FILE_SEPARATOR)) {
        throw AdbShellCommandException.create("File name $fileName contains invalid characters")
      }
      val remotePath = AdbPathUtil.resolve(parentPath, fileName)

      // Check remote file does not exists, so that we can give a relevant error message.
      // The check + create below is not an atomic operation, but this service does not
      // aim to guarantee strong atomicity for file system operations.
      val command = when {
        deviceCapabilities.supportsTestCommand() ->
          getCommand(runAs, "test -e ").withEscapedPath(remotePath).build()
        else ->
          getCommand(runAs, "ls -d -a ").withEscapedPath(remotePath).build()
      }
      val commandResult = myShellCommandsUtil.executeCommand(myDevice, command)
      if (!commandResult.isError) {
        throw AdbShellCommandException.create("File $remotePath already exists on device")
      }
      touchFileRunAs(remotePath, runAs)
    }
  }

  fun createNewDirectory(parentPath: String, directoryName: String): ListenableFuture<Unit> {
    return createNewDirectoryRunAs(parentPath, directoryName, null)
  }

  fun createNewDirectoryRunAs(parentPath: String, directoryName: String, runAs: String?): ListenableFuture<Unit> {
    return myExecutor.executeAsync {
      if (directoryName.contains(AdbPathUtil.FILE_SEPARATOR)) {
        throw AdbShellCommandException.create("Directory name \"$directoryName\" contains invalid characters")
      }

      // "mkdir" fails if the file/directory already exists
      val remotePath = AdbPathUtil.resolve(parentPath, directoryName)
      val command = getCommand(runAs, "mkdir ").withEscapedPath(remotePath).build()
      myShellCommandsUtil.executeCommand(myDevice, command).throwIfError()
    }
  }

  fun listPackages(): ListenableFuture<List<String>> {
    return myExecutor.executeAsync {
      val command = getCommand(null, "pm list packages").build()
      val commandResult = myShellCommandsUtil.executeCommand(myDevice, command)
      commandResult.throwIfError()
      commandResult.output.mapNotNull(::processPackageListLine)
    }
  }

  fun listPackageInfo(): ListenableFuture<List<PackageInfo>> {
    return myExecutor.executeAsync {
      val command = getCommand(null, "pm list packages -f").build()
      val commandResult = myShellCommandsUtil.executeCommand(myDevice, command)
      commandResult.throwIfError()
      commandResult.output.mapNotNull(::processPackageInfoLine)
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
      myShellCommandsUtil.executeCommand(myDevice, command).throwIfError()
    }
  }

  fun deleteRecursive(path: String): ListenableFuture<Unit> {
    return deleteRecursiveRunAs(path, null)
  }

  fun deleteRecursiveRunAs(path: String, runAs: String?): ListenableFuture<Unit> {
    return myExecutor.executeAsync {
      val command = getRmCommand(runAs, path, true)
      myShellCommandsUtil.executeCommand(myDevice, command).throwIfError()
    }
  }

  fun copyFile(source: String, destination: String): ListenableFuture<Unit> {
    return copyFileRunAs(source, destination, null)
  }

  fun copyFileRunAs(source: String, destination: String, runAs: String?): ListenableFuture<Unit> {
    return myExecutor.executeAsync {
      val command = when {
        deviceCapabilities.supportsCpCommand() ->
          getCommand(runAs, "cp ").withEscapedPath(source).withText(" ").withEscapedPath(destination).build()
        else ->
          getCommand(runAs, "cat ").withEscapedPath(source).withText(" >").withEscapedPath(destination).build()
      }
      myShellCommandsUtil.executeCommand(myDevice, command).throwIfError()
    }
  }

  fun createTempFile(tempPath: String): ListenableFuture<String> {
    return createTempFileRunAs(tempPath, null)
  }

  fun createTempFileRunAs(tempDirectory: String, runAs: String?): ListenableFuture<String> {
    return myExecutor.executeAsync {

      // Note: Instead of using "mktemp", we use our own unique filename generation + a call to "touch"
      //       for 2 reasons:
      //       * mktemp is not available on all API levels
      //       * mktemp creates a file with 600 permission, meaning the file is not
      //         accessible by processes running as "run-as"
      val tempFileName = UniqueFileNameGenerator.getInstance().getUniqueFileName("temp", "")
      val remotePath = AdbPathUtil.resolve(tempDirectory, tempFileName)
      touchFileRunAs(remotePath, runAs)
      remotePath
    }
  }

  fun touchFileAsDefaultUser(remotePath: String): ListenableFuture<Unit> {
    return myExecutor.executeAsync {
      val command = when {
        deviceCapabilities.supportsTouchCommand() ->
          // Touch creates an empty file if the file does not exist.
          // Touch fails if there are permissions errors.
          AdbShellCommandBuilder().withText("touch ").withEscapedPath(remotePath).build()
        else ->
          AdbShellCommandBuilder().withText("echo -n >").withEscapedPath(remotePath).build()
      }
      myShellCommandsUtil.executeCommand(myDevice, command).throwIfError()
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
    val command = when {
      deviceCapabilities.supportsTouchCommand() ->
        // Touch creates an empty file if the file does not exist.
        // Touch fails if there are permissions errors.
        getCommand(runAs, "touch ").withEscapedPath(remotePath).build()
      else ->
        getCommand(runAs, "echo -n >").withEscapedPath(remotePath).build()
    }
    myShellCommandsUtil.executeCommand(myDevice, command).throwIfError()
  }

  @Throws(
    TimeoutException::class,
    AdbCommandRejectedException::class,
    ShellCommandUnresponsiveException::class,
    IOException::class,
    SyncException::class
  )
  private fun getRmCommand(runAs: String?, path: String, recursive: Boolean): String {
    val recursiveArg = if (recursive) "-r " else ""
    val forceArg = if (deviceCapabilities.supportsRmForceFlag()) "-f " else ""
    return getCommand(runAs, "rm $recursiveArg$forceArg").withEscapedPath(path).build()
  }

  @Throws(TimeoutException::class, AdbCommandRejectedException::class, ShellCommandUnresponsiveException::class, IOException::class)
  private fun getCommand(runAs: String?, text: String): AdbShellCommandBuilder {
    val command = AdbShellCommandBuilder()
    if (deviceCapabilities.supportsSuRootCommand()) {
      command.withSuRootPrefix()
    } else if (runAs != null) {
      command.withRunAs(runAs)
    }
    return command.withText(text)
  }
}

private const val PACKAGE_PREFIX = "package:"

private fun processPackageListLine(line: String): String? =
  if (line.startsWith(PACKAGE_PREFIX)) emptyToNull(line.substring(PACKAGE_PREFIX.length)) else null

private fun processPackageInfoLine(line: String): AdbFileOperations.PackageInfo? {
  // Format is: package:<path>=<name>
  if (!line.startsWith(PACKAGE_PREFIX)) {
    return null
  }
  val separatorIndex = line.indexOf('=', PACKAGE_PREFIX.length)
  if (separatorIndex < 0) {
    return null
  }
  val path = line.substring(PACKAGE_PREFIX.length, separatorIndex).trim { it <= ' ' }
  if (StringUtil.isEmpty(path)) {
    return null
  }
  val packageName = line.substring(separatorIndex + 1).trim { it <= ' ' }
  return if (StringUtil.isEmpty(packageName)) {
    null
  } else AdbFileOperations.PackageInfo(packageName, path)
}
