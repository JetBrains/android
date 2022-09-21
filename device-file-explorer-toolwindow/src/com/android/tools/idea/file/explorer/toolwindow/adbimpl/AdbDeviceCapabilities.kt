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
import com.android.adblib.RemoteFileMode
import com.android.adblib.syncSend
import com.android.tools.idea.adb.AdbShellCommandException
import com.android.tools.idea.adb.AdbShellCommandResult
import com.android.tools.idea.adb.AdbShellCommandsUtil
import com.android.tools.idea.concurrency.AndroidDispatchers.diskIoThread
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.io.FileUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

/**
 * Helper class used to detect various capabilities/features supported by a device
 * so callers can make decisions about which adb commands to use.
 */
class AdbDeviceCapabilities(coroutineScope: CoroutineScope, private val deviceName: String, private val device: ConnectedDevice) {
  private val logger = thisLogger()

  private val shellCommandsUtil = AdbShellCommandsUtil.create(device)

  suspend fun supportsTestCommand() = supportsTestCommand.await()
  private val supportsTestCommand = coroutineScope.async(start = CoroutineStart.LAZY) {
    assertNotDispatchThread()
    ScopedRemoteFile(AdbPathUtil.resolve(PROBE_FILES_TEMP_PATH, ".__temp_test_test__file__.tmp")).use { tempFile ->
      // Create the remote file used for testing capability
      tempFile.create()

      // Try the "test" command on it (it should succeed if the command is supported)
      val command = AdbShellCommandBuilder().withText("test -e ").withEscapedPath(tempFile.remotePath).build()
      val commandResult = shellCommandsUtil.executeCommand(command)
      try {
        commandResult.throwIfError()
        true
      }
      catch (e: AdbShellCommandException) {
        logger.info(
          """Device "$deviceName" does not seem to support the "test" command: ${
            commandResult.outputSummary()}""", e)
        false
      }
    }
  }

  suspend fun supportsRmForceFlag() = supportsRmForceFlag.await()
  private val supportsRmForceFlag = coroutineScope.async(start = CoroutineStart.LAZY) {
    assertNotDispatchThread()
    ScopedRemoteFile(AdbPathUtil.resolve(PROBE_FILES_TEMP_PATH, ".__temp_rm_test_file__.tmp")).use { tempFile ->
      // Create the remote file used for testing capability
      tempFile.create()

      // Try to delete it with "rm -f" (it should work if th command is supported)
      val command = AdbShellCommandBuilder().withText("rm -f ").withEscapedPath(tempFile.remotePath).build()
      val commandResult = shellCommandsUtil.executeCommand(command)
      try {
        commandResult.throwIfError()
        // If no error, "rm -f" is supported and test file has been deleted, so no need to delete it again.
        tempFile.deleteOnClose = false
        true
      }
      catch (e: AdbShellCommandException) {
        logger.info("""Device "$deviceName" does not seem to support "-f" flag for rm: ${
            commandResult.outputSummary()}""", e)
        false
      }
    }
  }

  suspend fun supportsTouchCommand() = supportsTouchCommand.await()
  private val supportsTouchCommand = coroutineScope.async(start = CoroutineStart.LAZY) {
    assertNotDispatchThread()
    ScopedRemoteFile(AdbPathUtil.resolve(PROBE_FILES_TEMP_PATH, ".__temp_touch_test_file__.tmp")).use { tempFile ->

      // Try to create the file with the "touch" command
      val command = AdbShellCommandBuilder().withText("touch ").withEscapedPath(tempFile.remotePath).build()
      val commandResult = shellCommandsUtil.executeCommand(command)
      try {
        commandResult.throwIfError()

        // If "touch" did not work, we want to delete the temporary file
        tempFile.deleteOnClose = true
        true
      }
      catch (e: AdbShellCommandException) {
        logger.info("""Device "$deviceName" does not seem to support "touch" command: ${
          commandResult.outputSummary()
        }""", e)
        false
      }
    }
  }

  suspend fun supportsSuRootCommand() = supportsSuRootCommand.await()
  private val supportsSuRootCommand = coroutineScope.async(start = CoroutineStart.LAZY) {
    assertNotDispatchThread()
    // Try a "su" command ("id") that should always succeed, unless "su" is not supported
    val command = AdbShellCommandBuilder().withSuRootPrefix().withText("id").build()
    val commandResult = shellCommandsUtil.executeCommand(command)
    try {
      commandResult.throwIfError()
      true
    }
    catch (e: AdbShellCommandException) {
      logger.info(
          """Device "$deviceName" does not seem to support the "su 0" command: ${
              commandResult.outputSummary()}""", e)
      false
    }
  }

  suspend fun isRoot() = isRoot.await()
  private val isRoot = coroutineScope.async(start = CoroutineStart.LAZY) {
    assertNotDispatchThread()

    val result = shellCommandsUtil.executeCommandNoErrorCheck("echo \$USER_ID")
    result.output.joinToString().trim() == "0"
  }

  suspend fun supportsCpCommand() = supportsCpCommand.await()
  private val supportsCpCommand = coroutineScope.async(start = CoroutineStart.LAZY) {
    assertNotDispatchThread()
    ScopedRemoteFile(AdbPathUtil.resolve(PROBE_FILES_TEMP_PATH, ".__temp_cp_test_file__.tmp")).use { srcFile ->
      ScopedRemoteFile(AdbPathUtil.resolve(PROBE_FILES_TEMP_PATH, ".__temp_cp_test_file_dst__.tmp")).use { dstFile ->
        // Create the remote file used for testing capability
        srcFile.create()

        // Copy source file to destination file
        val command = AdbShellCommandBuilder()
          .withText("cp ")
          .withEscapedPath(srcFile.remotePath)
          .withText(" ")
          .withEscapedPath(dstFile.remotePath)
          .build()
        val commandResult = shellCommandsUtil.executeCommand(command)
        try {
          commandResult.throwIfError()

          // If "cp" succeeded, we need to delete the destination file
          dstFile.deleteOnClose = true
          true
        }
        catch (e: AdbShellCommandException) {
          logger.info(
              """Device "$deviceName" does not seem to support the "cp" command: ${
                  commandResult.outputSummary()}""", e)
          false
        }
      }
    }
  }

  suspend fun hasEscapingLs(): Boolean = hasEscapingLs.await()
  private val hasEscapingLs = coroutineScope.async(start = CoroutineStart.LAZY) {
    assertNotDispatchThread()
    try {
      touchEscapedPath()
    }
    catch (exception: AdbShellCommandException) {
      logger.info("""Device "$deviceName" does not seem to support the touch command""", exception)
      return@async false
    }
    try {
      ScopedRemoteFile(ESCAPING_LS_NOT_ESCAPED_PATH).use { file ->
        file.deleteOnClose = true
        lsEscapedPath()
      }
    }
    catch (exception: AdbShellCommandException) {
      logger.info("""Device "$deviceName" does not seem to support the ls command""", exception)
      false
    }
  }

  private suspend fun touchEscapedPath() {
    val command = AdbShellCommandBuilder().withText("touch $ESCAPING_LS_ESCAPED_PATH").build()
    val result = shellCommandsUtil.executeCommand(command)
    result.throwIfError()
    if (!result.isEmpty()) {
      throw AdbShellCommandException("Unexpected output from touch")
    }
  }

  private suspend fun lsEscapedPath(): Boolean {
    val command = AdbShellCommandBuilder().withText("ls $ESCAPING_LS_ESCAPED_PATH").build()
    val result = shellCommandsUtil.executeCommand(command)
    result.throwIfError()
    return when (result.output[0]) {
      ESCAPING_LS_ESCAPED_PATH -> true
      ESCAPING_LS_NOT_ESCAPED_PATH -> false
      else -> throw AdbShellCommandException("Unexpected output from ls")
    }
  }

  suspend fun supportsMkTempCommand() = supportsMkTempCommand.await()
  private val supportsMkTempCommand = coroutineScope.async(start = CoroutineStart.LAZY)  {
    assertNotDispatchThread()
    // Copy source file to destination file
    val command = AdbShellCommandBuilder().withText("mktemp -p ").withEscapedPath(AdbPathUtil.DEVICE_TEMP_DIRECTORY).build()
    val commandResult = shellCommandsUtil.executeCommand(command)
    try {
      commandResult.throwIfError()
      if (commandResult.isEmpty()) {
        throw AdbShellCommandException("Unexpected output from mktemp, assuming not supported")
      }

      // If "mktemp" succeeded, we need to delete the destination file
      val remotePath = commandResult.output[0]
      ScopedRemoteFile(remotePath).use { tempFile -> tempFile.deleteOnClose = true }
      true
    }
    catch (e: AdbShellCommandException) {
      logger.info(
          """Device "$deviceName" does not seem to support the "cp" command: ${
              commandResult.outputSummary()}""", e)
      false
    }
  }

  /**
   * An [AutoCloseable] wrapper around a temporary file on a remote device.
   * The [close] method attempts to delete the file from the remote device
   * unless [deleteOnClose] is set to false.
   */
  private inner class ScopedRemoteFile(val remotePath: String) {
    var deleteOnClose = false

    suspend fun create() {
      assert(!deleteOnClose)
      assertNotDispatchThread()
      createRemoteTemporaryFile()
      deleteOnClose = true
    }

    /** Suspending version of [AutoCloseable.use()] */
    suspend fun <R> use(block: suspend (ScopedRemoteFile) -> R): R {
      var exception: Throwable? = null
      try {
        return block(this)
      } catch (e: Throwable) {
        exception = e
        throw e
      } finally {
        withContext(NonCancellable) {
          when (exception) {
            null -> close()
            else ->
              try {
                close()
              } catch (closeException: Throwable) {
                exception.addSuppressed(closeException)
              }
          }
        }
      }
    }

    suspend fun close() {
      if (!deleteOnClose) {
        return
      }
      try {
        val command = AdbShellCommandBuilder().withText("rm ").withEscapedPath(remotePath).build()
        val commandResult = shellCommandsUtil.executeCommand(command)
        try {
          commandResult.throwIfError()
        }
        catch (e: AdbShellCommandException) {
          // There is not much we can do if we can't delete the test file other than logging the error.
          logger.warn(
              """Device "$deviceName": Error deleting temporary test file "$remotePath": ${
                  commandResult.outputSummary()}""", e)
        }
      }
      catch (e: Exception) {
        // There is not much we can do if we can't delete the test file other than logging the error.
        logger.warn("""Device "$deviceName": Error deleting temporary test file "$remotePath"""", e)
      }
    }

    /**
     * Create an empty file on the remote device by first creating a local empty temporary file,
     * then pushing it to the remote device.
     */
    private suspend fun createRemoteTemporaryFile() = withContext(diskIoThread) {
      val tempFile = FileUtil.createTempFile(remotePath, "", true)
      try {
        val tempFileChannel = device.session.channelFactory.openFile(tempFile.toPath())
        logger.trace("""Device "$deviceName": Uploading temporary file "$tempFile" to remote file "$remotePath"""")
        device.session.deviceServices.syncSend(device.selector, tempFileChannel, remotePath, RemoteFileMode.DEFAULT)
      }
      finally {
        withContext(NonCancellable) {
          try {
            FileUtil.delete(tempFile)
          }
          catch (e: Exception) {
            logger.warn("""Device "$deviceName": Error deleting temporary file "$tempFile"""", e)
          }
        }
      }
    }
  }

  companion object {
    private val PROBE_FILES_TEMP_PATH = AdbPathUtil.resolve(AdbPathUtil.DEVICE_TEMP_DIRECTORY, "device-explorer")
    private val ESCAPING_LS_ESCAPED_PATH = AdbPathUtil.resolve(AdbPathUtil.DEVICE_TEMP_DIRECTORY, "oyX2HCKL\\ acuauQGJ")
    private val ESCAPING_LS_NOT_ESCAPED_PATH = AdbPathUtil.resolve(AdbPathUtil.DEVICE_TEMP_DIRECTORY, "oyX2HCKL acuauQGJ")
    private fun assertNotDispatchThread() {
      val application = ApplicationManager.getApplication()
      assert(application == null || !application.isDispatchThread)
    }

    private fun AdbShellCommandResult.outputSummary(): String =
      when {
        isEmpty() -> "[command output is empty]"
        else -> output.joinToString(prefix = "\n  ", postfix = "", separator = "\n  ", limit = 5)
      }
  }
}