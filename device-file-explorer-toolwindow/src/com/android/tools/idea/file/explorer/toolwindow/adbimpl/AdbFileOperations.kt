/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.file.explorer.toolwindow.adbimpl

import com.android.adblib.ConnectedDevice
import com.android.tools.idea.adb.AdbShellCommandException
import com.android.tools.idea.adb.AdbShellCommandsUtil
import com.google.common.base.Strings.emptyToNull
import com.intellij.openapi.util.text.StringUtil
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class AdbFileOperations(
  device: ConnectedDevice,
  private val deviceCapabilities: AdbDeviceCapabilities,
  private val dispatcher: CoroutineDispatcher) {
  private val shellCommandsUtil = AdbShellCommandsUtil.create(device)

  suspend fun createNewFile(parentPath: String, fileName: String) {
    return createNewFileRunAs(parentPath, fileName, null)
  }

  suspend fun createNewFileRunAs(parentPath: String, fileName: String, runAs: String?) {
    return withContext(dispatcher) {
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
      val commandResult = shellCommandsUtil.executeCommand(command)
      if (!commandResult.isError) {
        throw AdbShellCommandException.create("File $remotePath already exists on device")
      }
      touchFileRunAs(remotePath, runAs)
    }
  }

  suspend fun createNewDirectory(parentPath: String, directoryName: String) {
    return createNewDirectoryRunAs(parentPath, directoryName, null)
  }

  suspend fun createNewDirectoryRunAs(parentPath: String, directoryName: String, runAs: String?) {
    return withContext(dispatcher) {
      if (directoryName.contains(AdbPathUtil.FILE_SEPARATOR)) {
        throw AdbShellCommandException.create("Directory name \"$directoryName\" contains invalid characters")
      }

      // "mkdir" fails if the file/directory already exists
      val remotePath = AdbPathUtil.resolve(parentPath, directoryName)
      val command = getCommand(runAs, "mkdir ").withEscapedPath(remotePath).build()
      shellCommandsUtil.executeCommand(command).throwIfError()
    }
  }

  suspend fun listPackages(): List<String> {
    return withContext(dispatcher) {
      val command = getCommand(null, "pm list packages").build()
      val commandResult = shellCommandsUtil.executeCommand(command)
      commandResult.throwIfError()
      commandResult.output.mapNotNull(::processPackageListLine)
    }
  }

  suspend fun listPackageInfo(): List<PackageInfo> {
    return withContext(dispatcher) {
      val command = getCommand(null, "pm list packages -f").build()
      val commandResult = shellCommandsUtil.executeCommand(command)
      commandResult.throwIfError()
      commandResult.output.mapNotNull(::processPackageInfoLine)
    }
  }

  class PackageInfo(val packageName: String, val path: String) {
    override fun toString(): String {
      return String.format("%s: path=%s", packageName, path)
    }
  }

  suspend fun deleteFile(path: String) {
    return deleteFileRunAs(path, null)
  }

  suspend fun deleteFileRunAs(path: String, runAs: String?) {
    return withContext(dispatcher) {
      val command = getRmCommand(runAs, path, false)
      shellCommandsUtil.executeCommand(command).throwIfError()
    }
  }

  suspend fun deleteRecursive(path: String) {
    return deleteRecursiveRunAs(path, null)
  }

  suspend fun deleteRecursiveRunAs(path: String, runAs: String?) {
    return withContext(dispatcher) {
      val command = getRmCommand(runAs, path, true)
      shellCommandsUtil.executeCommand(command).throwIfError()
    }
  }

  suspend fun copyFile(source: String, destination: String) {
    return copyFileRunAs(source, destination, null)
  }

  suspend fun copyFileRunAs(source: String, destination: String, runAs: String?) {
    return withContext(dispatcher) {
      val command = when {
        deviceCapabilities.supportsCpCommand() ->
          getCommand(runAs, "cp ").withEscapedPath(source).withText(" ").withEscapedPath(destination).build()
        else ->
          getCommand(runAs, "cat ").withEscapedPath(source).withText(" >").withEscapedPath(destination).build()
      }
      shellCommandsUtil.executeCommand(command).throwIfError()
    }
  }

  suspend fun createTempFile(tempPath: String): String {
    return createTempFileRunAs(tempPath, null)
  }

  suspend fun createTempFileRunAs(tempDirectory: String, runAs: String?): String {
    return withContext(dispatcher) {

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

  suspend fun touchFileAsDefaultUser(remotePath: String) {
    return withContext(dispatcher) {
      val command = when {
        deviceCapabilities.supportsTouchCommand() ->
          // Touch creates an empty file if the file does not exist.
          // Touch fails if there are permissions errors.
          AdbShellCommandBuilder().withText("touch ").withEscapedPath(remotePath).build()
        else ->
          AdbShellCommandBuilder().withText("echo -n >").withEscapedPath(remotePath).build()
      }
      shellCommandsUtil.executeCommand(command).throwIfError()
    }
  }

  private suspend fun touchFileRunAs(remotePath: String, runAs: String?) {
    val command = when {
      deviceCapabilities.supportsTouchCommand() ->
        // Touch creates an empty file if the file does not exist.
        // Touch fails if there are permissions errors.
        getCommand(runAs, "touch ").withEscapedPath(remotePath).build()
      else ->
        getCommand(runAs, "echo -n >").withEscapedPath(remotePath).build()
    }
    shellCommandsUtil.executeCommand(command).throwIfError()
  }

  private suspend fun getRmCommand(runAs: String?, path: String, recursive: Boolean): String {
    val recursiveArg = if (recursive) "-r " else ""
    val forceArg = if (deviceCapabilities.supportsRmForceFlag()) "-f " else ""
    return getCommand(runAs, "rm $recursiveArg$forceArg").withEscapedPath(path).build()
  }

  private suspend fun getCommand(runAs: String?, text: String): AdbShellCommandBuilder {
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
