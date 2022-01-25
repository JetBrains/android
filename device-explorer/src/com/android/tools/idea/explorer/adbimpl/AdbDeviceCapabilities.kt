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

import com.android.tools.idea.adb.AdbShellCommandsUtil.executeCommand
import com.android.ddmlib.IDevice
import com.android.tools.idea.adb.AdbShellCommandsUtil
import com.android.tools.idea.flags.StudioFlags
import kotlin.Throws
import com.android.ddmlib.AdbCommandRejectedException
import com.android.ddmlib.ShellCommandUnresponsiveException
import java.io.IOException
import com.android.ddmlib.SyncException
import com.android.tools.idea.explorer.adbimpl.AdbDeviceCapabilities
import com.android.tools.idea.explorer.adbimpl.AdbDeviceCapabilities.ScopedRemoteFile
import com.android.tools.idea.explorer.adbimpl.AdbPathUtil
import com.android.tools.idea.explorer.adbimpl.AdbShellCommandBuilder
import com.android.tools.idea.adb.AdbShellCommandResult
import com.android.tools.idea.adb.AdbShellCommandException
import com.android.tools.idea.explorer.adbimpl.DeviceUtil
import com.android.ddmlib.SyncService
import com.android.ddmlib.TimeoutException
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import java.lang.Exception
import java.util.stream.Collectors

/**
 * Helper class used to detect various capabilities/features supported by a [IDevice]
 * so callers can make decisions about which adb commands to use.
 */
class AdbDeviceCapabilities(private val myDevice: IDevice) {
  private val myShellCommandsUtil = AdbShellCommandsUtil(StudioFlags.ADBLIB_MIGRATION_DEVICE_EXPLORER.get())
  private var mySupportsTestCommand: Boolean? = null
  private var mySupportsRmForceFlag: Boolean? = null
  private var mySupportsTouchCommand: Boolean? = null
  private var mySupportsSuRootCommand: Boolean? = null
  private var myIsRoot: Boolean? = null
  private var mySupportsCpCommand: Boolean? = null
  private var myEscapingLs: Boolean? = null
  private var mySupportsMkTempCommand: Boolean? = null
  @Synchronized
  @Throws(
    TimeoutException::class,
    AdbCommandRejectedException::class,
    ShellCommandUnresponsiveException::class,
    IOException::class,
    SyncException::class
  )
  fun supportsTestCommand(): Boolean {
    assertNotDispatchThread()
    if (mySupportsTestCommand == null) {
      mySupportsTestCommand = supportsTestCommandWorker()
    }
    return mySupportsTestCommand!!
  }

  @Synchronized
  @Throws(
    TimeoutException::class,
    AdbCommandRejectedException::class,
    ShellCommandUnresponsiveException::class,
    IOException::class,
    SyncException::class
  )
  fun supportsRmForceFlag(): Boolean {
    assertNotDispatchThread()
    if (mySupportsRmForceFlag == null) {
      mySupportsRmForceFlag = supportsRmForceFlagWorker()
    }
    return mySupportsRmForceFlag!!
  }

  @Synchronized
  @Throws(TimeoutException::class, AdbCommandRejectedException::class, ShellCommandUnresponsiveException::class, IOException::class)
  fun supportsTouchCommand(): Boolean {
    assertNotDispatchThread()
    if (mySupportsTouchCommand == null) {
      mySupportsTouchCommand = supportsTouchCommandWorker()
    }
    return mySupportsTouchCommand!!
  }

  @Synchronized
  @Throws(TimeoutException::class, AdbCommandRejectedException::class, ShellCommandUnresponsiveException::class, IOException::class)
  fun supportsSuRootCommand(): Boolean {
    assertNotDispatchThread()
    if (mySupportsSuRootCommand == null) {
      mySupportsSuRootCommand = supportsSuRootCommandWorker()
    }
    return mySupportsSuRootCommand!!
  }

  @get:Throws(
    TimeoutException::class,
    AdbCommandRejectedException::class,
    ShellCommandUnresponsiveException::class,
    IOException::class
  )
  @get:Synchronized
  val isRoot: Boolean
    get() {
      assertNotDispatchThread()
      if (myIsRoot == null) {
        myIsRoot = isRootWorker
      }
      return myIsRoot!!
    }

  @Synchronized
  @Throws(
    TimeoutException::class,
    AdbCommandRejectedException::class,
    ShellCommandUnresponsiveException::class,
    IOException::class,
    SyncException::class
  )
  fun supportsCpCommand(): Boolean {
    assertNotDispatchThread()
    if (mySupportsCpCommand == null) {
      mySupportsCpCommand = supportsCpCommandWorker()
    }
    return mySupportsCpCommand!!
  }

  @Synchronized
  @Throws(TimeoutException::class, AdbCommandRejectedException::class, ShellCommandUnresponsiveException::class, IOException::class)
  fun hasEscapingLs(): Boolean {
    assertNotDispatchThread()
    if (myEscapingLs == null) {
      myEscapingLs = hasEscapingLsWorker()
    }
    return myEscapingLs!!
  }

  @Synchronized
  @Throws(TimeoutException::class, AdbCommandRejectedException::class, ShellCommandUnresponsiveException::class, IOException::class)
  fun supportsMkTempCommand(): Boolean {
    assertNotDispatchThread()
    if (mySupportsMkTempCommand == null) {
      mySupportsMkTempCommand = supportsMkTempCommandWorker()
    }
    return mySupportsMkTempCommand!!
  }

  @Throws(
    TimeoutException::class,
    AdbCommandRejectedException::class,
    ShellCommandUnresponsiveException::class,
    IOException::class,
    SyncException::class
  )
  private fun supportsTestCommandWorker(): Boolean {
    ScopedRemoteFile(AdbPathUtil.resolve(PROBE_FILES_TEMP_PATH, ".__temp_test_test__file__.tmp")).use { tempFile ->
      // Create the remote file used for testing capability
      tempFile.create()

      // Try the "test" command on it (it should succeed if the command is supported)
      val command = AdbShellCommandBuilder().withText("test -e ").withEscapedPath(tempFile.remotePath).build()
      val commandResult = myShellCommandsUtil.executeCommand(myDevice, command)
      return try {
        commandResult.throwIfError()
        true
      } catch (e: AdbShellCommandException) {
        LOGGER.info(
          String.format(
            "Device \"%s\" does not seem to support the \"test\" command: %s",
            DeviceUtil.toDebugString(myDevice),
            getCommandOutputExtract(commandResult)
          ),
          e
        )
        false
      }
    }
  }

  @Throws(
    TimeoutException::class,
    AdbCommandRejectedException::class,
    ShellCommandUnresponsiveException::class,
    IOException::class,
    SyncException::class
  )
  fun supportsRmForceFlagWorker(): Boolean {
    ScopedRemoteFile(AdbPathUtil.resolve(PROBE_FILES_TEMP_PATH, ".__temp_rm_test_file__.tmp")).use { tempFile ->
      // Create the remote file used for testing capability
      tempFile.create()

      // Try to delete it with "rm -f" (it should work if th command is supported)
      val command = AdbShellCommandBuilder().withText("rm -f ").withEscapedPath(tempFile.remotePath).build()
      val commandResult = myShellCommandsUtil.executeCommand(myDevice, command)
      return try {
        commandResult.throwIfError()
        // If no error, "rm -f" is supported and test file has been deleted, so no need to delete it again.
        tempFile.setDeleteOnClose(false)
        true
      } catch (e: AdbShellCommandException) {
        LOGGER.info(
          String.format(
            "Device \"%s\" does not seem to support \"-f\" flag for rm: %s",
            DeviceUtil.toDebugString(myDevice),
            getCommandOutputExtract(commandResult)
          ),
          e
        )
        false
      }
    }
  }

  @Throws(TimeoutException::class, AdbCommandRejectedException::class, ShellCommandUnresponsiveException::class, IOException::class)
  private fun supportsTouchCommandWorker(): Boolean {
    ScopedRemoteFile(AdbPathUtil.resolve(PROBE_FILES_TEMP_PATH, ".__temp_touch_test_file__.tmp")).use { tempFile ->

      // Try the create the file with the "touch" command
      val command = AdbShellCommandBuilder().withText("touch ").withEscapedPath(tempFile.remotePath).build()
      val commandResult = myShellCommandsUtil.executeCommand(myDevice, command)
      return try {
        commandResult.throwIfError()

        // If "touch" did not work, we want to delete the temporary file
        tempFile.setDeleteOnClose(true)
        true
      } catch (e: AdbShellCommandException) {
        LOGGER.info(
          String.format(
            "Device \"%s\" does not seem to support \"touch\" command: %s",
            DeviceUtil.toDebugString(myDevice),
            getCommandOutputExtract(commandResult)
          ),
          e
        )
        false
      }
    }
  }

  @Throws(TimeoutException::class, AdbCommandRejectedException::class, ShellCommandUnresponsiveException::class, IOException::class)
  private fun supportsSuRootCommandWorker(): Boolean {

    // Try a "su" command ("id") that should always succeed, unless "su" is not supported
    val command = AdbShellCommandBuilder().withSuRootPrefix().withText("id").build()
    val commandResult = myShellCommandsUtil.executeCommand(myDevice, command)
    return try {
      commandResult.throwIfError()
      true
    } catch (e: AdbShellCommandException) {
      LOGGER.info(
        String.format(
          "Device \"%s\" does not seem to support the \"su 0\" command: %s",
          DeviceUtil.toDebugString(myDevice),
          getCommandOutputExtract(commandResult)
        ),
        e
      )
      false
    }
  }

  // Note: The "isRoot" method below does not cache its results in case of negative answer.
  //       This means a round-trip to the device at each call when the device is not root.
  //       By caching the value in this class, we avoid these extra round trips.
  @get:Throws(
    TimeoutException::class,
    AdbCommandRejectedException::class,
    ShellCommandUnresponsiveException::class,
    IOException::class
  )
  private val isRootWorker: Boolean
    private get() = // Note: The "isRoot" method below does not cache its results in case of negative answer.
    //       This means a round-trip to the device at each call when the device is not root.
      //       By caching the value in this class, we avoid these extra round trips.
      myDevice.isRoot

  @Throws(
    TimeoutException::class,
    AdbCommandRejectedException::class,
    ShellCommandUnresponsiveException::class,
    IOException::class,
    SyncException::class
  )
  private fun supportsCpCommandWorker(): Boolean {
    ScopedRemoteFile(AdbPathUtil.resolve(PROBE_FILES_TEMP_PATH, ".__temp_cp_test_file__.tmp")).use { srcFile ->
      ScopedRemoteFile(
        AdbPathUtil.resolve(
          PROBE_FILES_TEMP_PATH, ".__temp_cp_test_file_dst__.tmp"
        )
      ).use { dstFile ->
        // Create the remote file used for testing capability
        srcFile.create()

        // Copy source file to destination file
        val command = AdbShellCommandBuilder()
          .withText("cp ")
          .withEscapedPath(srcFile.remotePath)
          .withText(" ")
          .withEscapedPath(dstFile.remotePath)
          .build()
        val commandResult = myShellCommandsUtil.executeCommand(myDevice, command)
        return try {
          commandResult.throwIfError()

          // If "cp" succeeded, we need to delete the destination file
          dstFile.setDeleteOnClose(true)
          true
        } catch (e: AdbShellCommandException) {
          LOGGER.info(
            String.format(
              "Device \"%s\" does not seem to support the \"cp\" command: %s",
              DeviceUtil.toDebugString(myDevice),
              getCommandOutputExtract(commandResult)
            ),
            e
          )
          false
        }
      }
    }
  }

  @Throws(TimeoutException::class, AdbCommandRejectedException::class, ShellCommandUnresponsiveException::class, IOException::class)
  private fun hasEscapingLsWorker(): Boolean {
    try {
      touchEscapedPath()
    } catch (exception: AdbShellCommandException) {
      LOGGER.info("Device \"" + DeviceUtil.toDebugString(myDevice) + "\" does not seem to support the touch command", exception)
      return false
    }
    try {
      ScopedRemoteFile(ESCAPING_LS_NOT_ESCAPED_PATH).use { file ->
        file.setDeleteOnClose(true)
        return lsEscapedPath()
      }
    } catch (exception: AdbShellCommandException) {
      LOGGER.info("Device \"" + DeviceUtil.toDebugString(myDevice) + "\" does not seem to support the ls command", exception)
      return false
    }
  }

  @Throws(
    TimeoutException::class,
    AdbCommandRejectedException::class,
    ShellCommandUnresponsiveException::class,
    IOException::class,
    AdbShellCommandException::class
  )
  private fun touchEscapedPath() {
    val command = AdbShellCommandBuilder()
      .withText("touch " + ESCAPING_LS_ESCAPED_PATH)
      .build()
    val result = myShellCommandsUtil.executeCommand(myDevice, command)
    result.throwIfError()
    if (!result.output.isEmpty()) {
      throw AdbShellCommandException("Unexpected output from touch")
    }
  }

  @Throws(
    TimeoutException::class,
    AdbCommandRejectedException::class,
    ShellCommandUnresponsiveException::class,
    IOException::class,
    AdbShellCommandException::class
  )
  private fun lsEscapedPath(): Boolean {
    val command = AdbShellCommandBuilder()
      .withText("ls " + ESCAPING_LS_ESCAPED_PATH)
      .build()
    val result = myShellCommandsUtil.executeCommand(myDevice, command)
    result.throwIfError()
    val output = result.output[0]
    return if (output == ESCAPING_LS_ESCAPED_PATH) {
      true
    } else if (output == ESCAPING_LS_NOT_ESCAPED_PATH) {
      false
    } else {
      throw AdbShellCommandException("Unexpected output from ls")
    }
  }

  @Throws(TimeoutException::class, AdbCommandRejectedException::class, ShellCommandUnresponsiveException::class, IOException::class)
  private fun supportsMkTempCommandWorker(): Boolean {

    // Copy source file to destination file
    val command = AdbShellCommandBuilder().withText("mktemp -p ").withEscapedPath(AdbPathUtil.DEVICE_TEMP_DIRECTORY).build()
    val commandResult = myShellCommandsUtil.executeCommand(myDevice, command)
    return try {
      commandResult.throwIfError()
      if (commandResult.output.isEmpty()) {
        throw AdbShellCommandException("Unexpected output from mktemp, assuming not supported")
      }

      // If "mktemp" succeeded, we need to delete the destination file
      val remotePath = commandResult.output[0]
      ScopedRemoteFile(remotePath).use { tempFile -> tempFile.setDeleteOnClose(true) }
      true
    } catch (e: AdbShellCommandException) {
      LOGGER.info(
        String.format(
          "Device \"%s\" does not seem to support the \"cp\" command: %s",
          DeviceUtil.toDebugString(myDevice),
          getCommandOutputExtract(commandResult)
        ),
        e
      )
      false
    }
  }

  /**
   * An [AutoCloseable] wrapper around a temporary file on a remote device.
   * The [.close] method attempts to delete the file from the remote device
   * unless the [setDeletedOnClose(false)][.setDeleteOnClose] is called.
   */
  private inner class ScopedRemoteFile(val remotePath: String) : AutoCloseable {
    private var myDeleteOnClose = false
    fun setDeleteOnClose(value: Boolean) {
      myDeleteOnClose = value
    }

    @Throws(TimeoutException::class, AdbCommandRejectedException::class, SyncException::class, IOException::class)
    fun create() {
      assert(!myDeleteOnClose)
      myDeleteOnClose = createRemoteTemporaryFile()
    }

    override fun close() {
      if (!myDeleteOnClose) {
        return
      }
      try {
        val command = AdbShellCommandBuilder().withText("rm ").withEscapedPath(remotePath).build()
        val commandResult = myShellCommandsUtil.executeCommand(myDevice, command)
        try {
          commandResult.throwIfError()
        } catch (e: AdbShellCommandException) {
          // There is not much we can do if we can't delete the test file other than logging the error.
          LOGGER.warn(
            String.format(
              "Device \"%s\": Error deleting temporary test file \"%s\": %s",
              DeviceUtil.toDebugString(myDevice),
              remotePath,
              getCommandOutputExtract(commandResult)
            ),
            e
          )
        }
      } catch (e: Exception) {
        // There is not much we can do if we can't delete the test file other than logging the error.
        LOGGER.warn(
          String.format(
            "Device \"%s\": Error deleting temporary test file \"%s\"",
            DeviceUtil.toDebugString(myDevice),
            remotePath
          ),
          e
        )
      }
    }

    /**
     * Create an empty file on the remote device by first creating a local empty temporary file,
     * then pushing it to the remote device.
     */
    @Throws(IOException::class, TimeoutException::class, AdbCommandRejectedException::class, SyncException::class)
    private fun createRemoteTemporaryFile(): Boolean {
      val file = FileUtil.createTempFile(remotePath, "", true)
      return try {
        val sync = myDevice.syncService
          ?: throw IOException(
            String.format(
              "Device \"%s\": Unable to open sync connection",
              DeviceUtil.toDebugString(myDevice)
            )
          )
        try {
          LOGGER.trace(
            String.format(
              "Device \"%s\": Uploading temporary file \"%s\" to remote file \"%s\"",
              DeviceUtil.toDebugString(myDevice),
              file,
              remotePath
            )
          )
          sync.pushFile(file.path, remotePath, SyncService.getNullProgressMonitor())
          true
        } finally {
          sync.close()
        }
      } finally {
        try {
          FileUtil.delete(file)
        } catch (e: Exception) {
          LOGGER.warn(
            String.format(
              "Device \"%s\": Error deleting temporary file \"%s\"",
              DeviceUtil.toDebugString(myDevice),
              file
            ),
            e
          )
        }
      }
    }
  }

  companion object {
    private val LOGGER = Logger.getInstance(
      AdbDeviceCapabilities::class.java
    )
    private val PROBE_FILES_TEMP_PATH = AdbPathUtil.resolve(AdbPathUtil.DEVICE_TEMP_DIRECTORY, "device-explorer")
    private val ESCAPING_LS_ESCAPED_PATH = AdbPathUtil.resolve(AdbPathUtil.DEVICE_TEMP_DIRECTORY, "oyX2HCKL\\ acuauQGJ")
    private val ESCAPING_LS_NOT_ESCAPED_PATH = AdbPathUtil.resolve(AdbPathUtil.DEVICE_TEMP_DIRECTORY, "oyX2HCKL acuauQGJ")
    private fun assertNotDispatchThread() {
      val application = ApplicationManager.getApplication()
      assert(application == null || !application.isDispatchThread)
    }

    private fun getCommandOutputExtract(commandResult: AdbShellCommandResult): String {
      val output = commandResult.output
      return if (output.isEmpty()) {
        "[command output is empty]"
      } else output.stream().limit(5).collect(Collectors.joining("\n  ", "\n  ", ""))
    }
  }
}