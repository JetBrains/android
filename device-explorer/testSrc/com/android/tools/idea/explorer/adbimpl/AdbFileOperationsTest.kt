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

import com.android.tools.idea.explorer.adbimpl.AdbDeviceFileSystem.name
import com.android.tools.idea.explorer.adbimpl.AdbDeviceFileSystem.device
import com.android.tools.idea.explorer.adbimpl.AdbDeviceFileSystem.isDevice
import com.android.tools.idea.explorer.adbimpl.AdbDeviceFileSystem.deviceState
import com.android.tools.idea.explorer.adbimpl.AdbDeviceFileSystem.rootDirectory
import com.android.tools.idea.explorer.fs.DeviceFileEntry.name
import com.android.tools.idea.explorer.fs.DeviceFileEntry.entries
import com.android.tools.idea.explorer.adbimpl.AdbDeviceFileSystem.getEntry
import com.android.tools.idea.explorer.fs.DeviceFileEntry.symbolicLinkTarget
import com.android.tools.idea.explorer.fs.DeviceFileEntry.permissions
import com.android.tools.idea.explorer.fs.DeviceFileEntry.Permissions.text
import com.android.tools.idea.explorer.fs.DeviceFileEntry.size
import com.android.tools.idea.explorer.fs.DeviceFileEntry.lastModifiedDate
import com.android.tools.idea.explorer.fs.DeviceFileEntry.DateTime.text
import com.android.tools.idea.explorer.fs.DeviceFileEntry.uploadFile
import com.android.tools.idea.explorer.fs.DeviceFileEntry.fullPath
import com.android.tools.idea.explorer.fs.DeviceFileEntry.downloadFile
import com.android.tools.idea.explorer.adbimpl.AdbFileListing.root
import com.android.tools.idea.explorer.adbimpl.AdbFileListing.getChildren
import com.android.tools.idea.explorer.adbimpl.AdbFileListing.getChildrenRunAs
import com.android.tools.idea.explorer.adbimpl.AdbFileListing.isDirectoryLink
import com.android.tools.idea.explorer.adbimpl.AdbFileOperations.createNewFile
import com.android.tools.idea.explorer.adbimpl.AdbFileOperations.createNewFileRunAs
import com.android.tools.idea.explorer.adbimpl.AdbFileOperations.createNewDirectory
import com.android.tools.idea.explorer.adbimpl.AdbFileOperations.createNewDirectoryRunAs
import com.android.tools.idea.explorer.adbimpl.AdbFileOperations.deleteFile
import com.android.tools.idea.explorer.adbimpl.AdbFileOperations.deleteFileRunAs
import com.android.tools.idea.explorer.adbimpl.AdbFileOperations.deleteRecursive
import com.android.tools.idea.explorer.adbimpl.AdbFileOperations.deleteRecursiveRunAs
import com.android.tools.idea.explorer.adbimpl.AdbFileOperations.listPackages
import com.android.tools.idea.explorer.adbimpl.AdbDeviceFileSystem
import com.android.tools.idea.explorer.adbimpl.MockDdmlibDevice
import java.util.concurrent.ExecutorService
import kotlin.Throws
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.explorer.adbimpl.UniqueFileNameGenerator
import com.google.common.truth.Truth
import com.android.ddmlib.IDevice
import com.android.tools.idea.explorer.fs.DeviceFileEntry
import com.android.tools.idea.explorer.adbimpl.AdbDeviceFileSystemTest
import java.lang.IllegalArgumentException
import com.android.tools.idea.explorer.fs.FileTransferProgress
import com.android.tools.idea.adb.AdbShellCommandException
import com.android.tools.idea.testing.DebugLoggerRule
import java.lang.AssertionError
import com.android.tools.idea.explorer.adbimpl.TestShellCommands
import com.android.tools.idea.explorer.adbimpl.AdbFileListing
import com.android.tools.idea.explorer.adbimpl.AdbDeviceCapabilities
import com.android.tools.idea.explorer.adbimpl.AdbFileListingEntry
import com.android.tools.idea.explorer.adbimpl.AdbFileListingTest
import com.android.ddmlib.ShellCommandUnresponsiveException
import com.android.tools.idea.explorer.adbimpl.AdbFileListingEntry.EntryKind
import com.android.tools.idea.explorer.adbimpl.AdbFileOperations
import com.android.tools.idea.explorer.adbimpl.AdbFileOperationsTest
import com.google.common.util.concurrent.ListenableFuture
import org.hamcrest.core.IsInstanceOf
import org.jetbrains.ide.PooledThreadExecutor
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.awt.EventQueue
import java.lang.Exception
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

@RunWith(Parameterized::class)
class AdbFileOperationsTest(private val mySetupCommands: Consumer<TestShellCommands>) {
  @Rule
  var thrown = ExpectedException.none()
  @Throws(Exception::class)
  private fun setupMockDevice(): AdbFileOperations {
    val commands = TestShellCommands()
    mySetupCommands.accept(commands)
    val device = commands.createMockDevice()
    val taskExecutor: Executor = PooledThreadExecutor.INSTANCE
    return AdbFileOperations(device, AdbDeviceCapabilities(device), taskExecutor)
  }

  @Test
  @Throws(Exception::class)
  fun testCreateNewFileSuccess() {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act
    val result = waitForFuture(fileOperations.createNewFile("/sdcard", "foo.txt"))

    // Assert
    Truth.assertThat(result).isEqualTo(Unit)
  }

  @Test
  @Throws(Exception::class)
  fun testCreateNewFileRunAsSuccess() {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act
    val result = waitForFuture(
      fileOperations.createNewFileRunAs(
        "/data/data/com.example.rpaquay.myapplication",
        "NewTextFile.txt",
        "com.example.rpaquay.myapplication"
      )
    )

    // Assert
    Truth.assertThat(result).isEqualTo(Unit)
  }

  @Test
  @Throws(Exception::class)
  fun testCreateNewFileInvalidFileNameError() {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act/Assert
    thrown.expect(ExecutionException::class.java)
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException::class.java))
    waitForFuture(fileOperations.createNewFile("/", "fo/o.txt"))
  }

  @Test
  @Throws(Exception::class)
  fun testCreateNewFileReadOnlyError() {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act/Assert
    thrown.expect(ExecutionException::class.java)
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException::class.java))
    waitForFuture(fileOperations.createNewFile("/", "foo.txt"))
  }

  @Test
  @Throws(Exception::class)
  fun testCreateNewFilePermissionError() {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act/Assert
    thrown.expect(ExecutionException::class.java)
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException::class.java))
    waitForFuture(fileOperations.createNewFile("/system", "foo.txt"))
  }

  @Test
  @Throws(Exception::class)
  fun testCreateNewFileExistError() {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act/Assert
    thrown.expect(ExecutionException::class.java)
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException::class.java))
    waitForFuture(fileOperations.createNewFile("/", "default.prop"))
  }

  @Test
  @Throws(Exception::class)
  fun testCreateNewDirectorySuccess() {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act
    val result = waitForFuture(fileOperations.createNewDirectory("/sdcard", "foo-dir"))

    // Assert
    Truth.assertThat(result).isEqualTo(Unit)
  }

  @Test
  @Throws(Exception::class)
  fun testCreateNewDirectoryRunAsSuccess() {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act
    val result = waitForFuture(
      fileOperations.createNewDirectoryRunAs(
        "/data/data/com.example.rpaquay.myapplication",
        "foo-dir",
        "com.example.rpaquay.myapplication"
      )
    )

    // Assert
    Truth.assertThat(result).isEqualTo(Unit)
  }

  @Test
  @Throws(Exception::class)
  fun testCreateNewDirectoryInvalidNameError() {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act

    // Assert
    thrown.expect(ExecutionException::class.java)
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException::class.java))
    waitForFuture(fileOperations.createNewDirectory("/", "fo/o-dir"))
  }

  @Test
  @Throws(Exception::class)
  fun testCreateNewDirectoryReadOnlyError() {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act

    // Assert
    thrown.expect(ExecutionException::class.java)
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException::class.java))
    waitForFuture(fileOperations.createNewDirectory("/", "foo-dir"))
  }

  @Test
  @Throws(Exception::class)
  fun testCreateNewDirectoryPermissionError() {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act

    // Assert
    thrown.expect(ExecutionException::class.java)
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException::class.java))
    waitForFuture(fileOperations.createNewDirectory("/system", "foo-dir"))
  }

  @Test
  @Throws(Exception::class)
  fun testCreateNewDirectoryExistError() {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act

    // Assert
    thrown.expect(ExecutionException::class.java)
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException::class.java))
    waitForFuture(fileOperations.createNewDirectory("/", "data"))
  }

  @Test
  @Throws(Exception::class)
  fun testDeleteExistingFileSuccess() {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act
    val result = waitForFuture(fileOperations.deleteFile("/sdcard/foo.txt"))

    // Assert
    Truth.assertThat(result).isEqualTo(Unit)
  }

  @Test
  @Throws(Exception::class)
  fun testDeleteExistingFileRunAsSuccess() {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act
    val result = waitForFuture(
      fileOperations.deleteFileRunAs(
        "/data/data/com.example.rpaquay.myapplication/NewTextFile.txt",
        "com.example.rpaquay.myapplication"
      )
    )

    // Assert
    Truth.assertThat(result).isEqualTo(Unit)
  }

  @Test
  @Throws(Exception::class)
  fun testDeleteExistingDirectoryAsFileError() {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act/Assert
    thrown.expect(ExecutionException::class.java)
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException::class.java))
    waitForFuture(fileOperations.deleteFile("/sdcard/foo-dir"))
  }

  @Test
  @Throws(Exception::class)
  fun testDeleteExistingReadOnlyFileError() {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act/Assert
    thrown.expect(ExecutionException::class.java)
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException::class.java))
    waitForFuture(fileOperations.deleteFile("/system/bin/sh"))
  }

  @Test
  @Throws(Exception::class)
  fun testDeleteExistingDirectorySucceeds() {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act
    val result = waitForFuture(fileOperations.deleteRecursive("/sdcard/foo-dir"))

    // Assert
    Truth.assertThat(result).isEqualTo(Unit)
  }

  @Test
  @Throws(Exception::class)
  fun testDeleteExistingDirectoryRunAsSucceeds() {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act
    val result = waitForFuture(
      fileOperations.deleteRecursiveRunAs(
        "/data/data/com.example.rpaquay.myapplication/foo-dir",
        "com.example.rpaquay.myapplication"
      )
    )

    // Assert
    Truth.assertThat(result).isEqualTo(Unit)
  }

  @Test
  @Throws(Exception::class)
  fun testDeleteExistingDirectoryPermissionError() {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act/Assert
    thrown.expect(ExecutionException::class.java)
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException::class.java))
    waitForFuture(fileOperations.deleteRecursive("/config"))
  }

  @Test
  @Throws(Exception::class)
  fun testListPackages() {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act
    val result = waitForFuture<List<String?>>(fileOperations.listPackages())

    // Assert
    Truth.assertThat(result).isNotNull()
    Truth.assertThat(result).contains("com.example.rpaquay.myapplication")
  }

  companion object {
    private const val TIMEOUT_MILLISECONDS: Long = 30000
    @Parameterized.Parameters
    fun data(): Array<Any> {
      return arrayOf(
        Consumer { commands: TestShellCommands? ->
          TestDevices.addEmulatorApi10Commands(
            commands!!
          )
        },
        Consumer { commands: TestShellCommands? ->
          TestDevices.addNexus7Api23Commands(
            commands!!
          )
        }
      )
    }

    @ClassRule
    var ourLoggerRule = DebugLoggerRule()
    @Throws(Exception::class)
    private fun <V> waitForFuture(future: ListenableFuture<V>): V {
      assert(!EventQueue.isDispatchThread())
      return future[TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS]
    }
  }
}