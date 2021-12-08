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
import com.android.tools.idea.explorer.fs.DeviceState
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.concurrency.AppExecutorUtil
import org.hamcrest.core.IsInstanceOf
import org.jetbrains.ide.PooledThreadExecutor
import org.junit.After
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.rules.TestRule
import java.awt.EventQueue
import java.lang.Exception
import java.nio.file.Files
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class AdbDeviceFileSystemTest {
  private var myParentDisposable: Disposable? = null
  private var myFileSystem: AdbDeviceFileSystem? = null
  private var myMockDevice: MockDdmlibDevice? = null
  private var myCallbackExecutor: ExecutorService? = null

  @Rule
  var thrown = ExpectedException.none()
  @Before
  @Throws(Exception::class)
  fun setUp() {
    myParentDisposable = Disposer.newDisposable()
    myCallbackExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor(
      "EDT Simulation Thread",
      PooledThreadExecutor.INSTANCE,
      1,
      myParentDisposable!!
    )
    myMockDevice = MockDdmlibDevice()
    val taskExecutor = FutureCallbackExecutor(PooledThreadExecutor.INSTANCE)
    val edtExecutor = FutureCallbackExecutor(myCallbackExecutor!!)
    myFileSystem = AdbDeviceFileSystem(myMockDevice!!.iDevice, edtExecutor, taskExecutor)
    val fileNameGenerator: UniqueFileNameGenerator = object : UniqueFileNameGenerator() {
      private var myNextId = 0
      override fun getUniqueFileName(prefix: String, suffix: String): String {
        return String.format("%s%d%s", prefix, myNextId++, suffix)
      }
    }
    UniqueFileNameGenerator.setInstanceOverride(fileNameGenerator)
  }

  @After
  fun cleanUp() {
    if (myParentDisposable != null) {
      Disposer.dispose(myParentDisposable!!)
    }
    UniqueFileNameGenerator.setInstanceOverride(null)
  }

  @Test
  fun test_FileSystem_Has_DeviceName() {
    // Prepare
    assert(myFileSystem != null)
    assert(myMockDevice != null)
    TestDevices.addNexus7Api23Commands(myMockDevice!!.shellCommands)

    // Act/Assert
    myMockDevice!!.name = "foo bar"
    Truth.assertThat(myFileSystem!!.name).isEqualTo(myMockDevice!!.name)
  }

  @Test
  fun test_FileSystem_Is_Device() {
    // Prepare
    assert(myFileSystem != null)
    assert(myMockDevice != null)
    TestDevices.addNexus7Api23Commands(myMockDevice!!.shellCommands)

    // Act/Assert
    Truth.assertThat(myFileSystem!!.device).isEqualTo(myMockDevice!!.iDevice)
    Truth.assertThat(myFileSystem!!.isDevice(myMockDevice!!.iDevice)).isTrue()
  }

  @Test
  fun test_FileSystem_Exposes_DeviceState() {
    // Prepare
    assert(myFileSystem != null)
    assert(myMockDevice != null)
    TestDevices.addNexus7Api23Commands(myMockDevice!!.shellCommands)

    // Act/Assert
    myMockDevice!!.state = null
    Truth.assertThat(myFileSystem!!.deviceState).isEqualTo(DeviceState.DISCONNECTED)
    myMockDevice!!.state = IDevice.DeviceState.BOOTLOADER
    Truth.assertThat(myFileSystem!!.deviceState).isEqualTo(DeviceState.BOOTLOADER)
    myMockDevice!!.state = IDevice.DeviceState.OFFLINE
    Truth.assertThat(myFileSystem!!.deviceState).isEqualTo(DeviceState.OFFLINE)
    myMockDevice!!.state = IDevice.DeviceState.ONLINE
    Truth.assertThat(myFileSystem!!.deviceState).isEqualTo(DeviceState.ONLINE)
    myMockDevice!!.state = IDevice.DeviceState.RECOVERY
    Truth.assertThat(myFileSystem!!.deviceState).isEqualTo(DeviceState.RECOVERY)
    myMockDevice!!.state = IDevice.DeviceState.SIDELOAD
    Truth.assertThat(myFileSystem!!.deviceState).isEqualTo(DeviceState.SIDELOAD)
    myMockDevice!!.state = IDevice.DeviceState.UNAUTHORIZED
    Truth.assertThat(myFileSystem!!.deviceState).isEqualTo(DeviceState.UNAUTHORIZED)
    myMockDevice!!.state = IDevice.DeviceState.DISCONNECTED
    Truth.assertThat(myFileSystem!!.deviceState).isEqualTo(DeviceState.DISCONNECTED)
  }

  @Test
  @Throws(Exception::class)
  fun test_FileSystem_Has_Root() {
    // Prepare
    assert(myFileSystem != null)
    assert(myMockDevice != null)
    TestDevices.addNexus7Api23Commands(myMockDevice!!.shellCommands)

    // Act
    val result = waitForFuture(myFileSystem!!.rootDirectory)

    // Assert
    Truth.assertThat(result).isNotNull()
    Truth.assertThat(result.name).isEqualTo("")
  }

  @Test
  @Throws(Exception::class)
  fun test_FileSystem_Has_DataTopLevelDirectory() {
    // Prepare
    assert(myFileSystem != null)
    assert(myMockDevice != null)
    TestDevices.addNexus7Api23Commands(myMockDevice!!.shellCommands)
    val rootEntry = waitForFuture(myFileSystem!!.rootDirectory)

    // Act
    val result = waitForFuture<List<DeviceFileEntry?>>(rootEntry.entries)

    // Assert
    Truth.assertThat(result).isNotNull()
    Truth.assertThat(result.stream().anyMatch { x: DeviceFileEntry? -> "data" == x!!.name }).isTrue()
  }

  @Test
  @Throws(Exception::class)
  fun test_FileSystem_GetEntry_Returns_Root_ForEmptyPath() {
    // Prepare
    assert(myFileSystem != null)
    assert(myMockDevice != null)
    TestDevices.addNexus7Api23Commands(myMockDevice!!.shellCommands)

    // Act
    val result = waitForFuture(myFileSystem!!.getEntry(""))

    // Assert
    Truth.assertThat(result).isNotNull()
    Truth.assertThat(result.name).isEqualTo("")
  }

  @Test
  @Throws(Exception::class)
  fun test_FileSystem_GetEntry_Returns_Root() {
    // Prepare
    assert(myFileSystem != null)

    // Act
    val result = waitForFuture(myFileSystem!!.getEntry("/"))

    // Assert
    Truth.assertThat(result).isNotNull()
    Truth.assertThat(result.name).isEqualTo("")
  }

  @Test
  @Throws(Exception::class)
  fun test_FileSystem_GetEntry_Returns_LinkInfo() {
    // Prepare
    assert(myFileSystem != null)
    assert(myMockDevice != null)
    TestDevices.addNexus7Api23Commands(myMockDevice!!.shellCommands)

    // Act
    val result = waitForFuture(myFileSystem!!.getEntry("/charger"))

    // Assert
    Truth.assertThat(result).isNotNull()
    Truth.assertThat(result.name).isEqualTo("charger")
    Truth.assertThat(result.symbolicLinkTarget).isEqualTo("/sbin/healthd")
    Truth.assertThat(result.permissions.text).isEqualTo("lrwxrwxrwx")
    Truth.assertThat(result.size).isEqualTo(-1)
    Truth.assertThat(result.lastModifiedDate.text).isEqualTo("1969-12-31 16:00")
  }

  @Test
  @Throws(Exception::class)
  fun test_FileSystem_GetEntry_Returns_DataDirectory() {
    // Prepare
    assert(myFileSystem != null)
    assert(myMockDevice != null)
    TestDevices.addNexus7Api23Commands(myMockDevice!!.shellCommands)

    // Act
    val result = waitForFuture(myFileSystem!!.getEntry("/data"))

    // Assert
    Truth.assertThat(result).isNotNull()
    Truth.assertThat(result.name).isEqualTo("data")
  }

  @Test
  @Throws(Exception::class)
  fun test_FileSystem_GetEntry_Returns_DataAppDirectory() {
    // Prepare
    assert(myFileSystem != null)
    assert(myMockDevice != null)
    TestDevices.addNexus7Api23Commands(myMockDevice!!.shellCommands)

    // Act
    val result = waitForFuture(myFileSystem!!.getEntry("/data/app"))

    // Assert
    Truth.assertThat(result).isNotNull()
    Truth.assertThat(result.name).isEqualTo("app")
  }

  @Test
  @Throws(Exception::class)
  fun test_FileSystem_GetEntries_Returns_DataAppPackages() {
    // Prepare
    assert(myFileSystem != null)
    assert(myMockDevice != null)
    TestDevices.addNexus7Api23Commands(myMockDevice!!.shellCommands)
    val dataEntry = waitForFuture(myFileSystem!!.getEntry("/data/app"))

    // Act
    val result = waitForFuture<List<DeviceFileEntry?>>(dataEntry.entries)

    // Assert
    Truth.assertThat(result).isNotNull()
    val app = result.stream()
      .filter { entry: DeviceFileEntry? -> entry!!.name == "com.example.rpaquay.myapplication-2" }
      .findFirst()
      .orElse(null)
    Truth.assertThat(app).isNotNull()

    // Act
    val appFiles = waitForFuture<List<DeviceFileEntry?>>(
      app!!.entries
    )

    // Assert
    Truth.assertThat(appFiles).isNotNull()
    Truth.assertThat(appFiles.stream().anyMatch { x: DeviceFileEntry? -> x!!.name == "base.apk" }).isTrue()
  }

  @Test
  @Throws(Exception::class)
  fun test_FileSystem_GetEntry_Returns_DataDataDirectory() {
    // Prepare
    assert(myFileSystem != null)
    assert(myMockDevice != null)
    TestDevices.addNexus7Api23Commands(myMockDevice!!.shellCommands)

    // Act
    val result = waitForFuture(myFileSystem!!.getEntry("/data/data"))

    // Assert
    Truth.assertThat(result).isNotNull()
    Truth.assertThat(result.name).isEqualTo("data")
  }

  @Test
  @Throws(Exception::class)
  fun test_FileSystem_GetEntries_Returns_DataDataPackages() {
    // Prepare
    assert(myFileSystem != null)
    assert(myMockDevice != null)
    TestDevices.addNexus7Api23Commands(myMockDevice!!.shellCommands)
    val dataEntry = waitForFuture(myFileSystem!!.getEntry("/data/data"))

    // Act
    val result = waitForFuture<List<DeviceFileEntry?>>(dataEntry.entries)

    // Assert
    Truth.assertThat(result).isNotNull()
    Truth.assertThat(result.stream().anyMatch { x: DeviceFileEntry? -> x!!.name == "com.example.rpaquay.myapplication" }).isTrue()
  }

  @Test
  @Throws(Exception::class)
  fun test_FileSystem_GetEntry_Returns_DataLocalDirectory() {
    // Prepare
    assert(myFileSystem != null)
    assert(myMockDevice != null)
    TestDevices.addNexus7Api23Commands(myMockDevice!!.shellCommands)

    // Act
    val result = waitForFuture(myFileSystem!!.getEntry("/data/local"))

    // Assert
    Truth.assertThat(result).isNotNull()
    Truth.assertThat(result.name).isEqualTo("local")
  }

  @Test
  @Throws(Exception::class)
  fun test_FileSystem_GetEntry_Returns_DataLocalTempDirectory() {
    // Prepare
    assert(myFileSystem != null)
    assert(myMockDevice != null)
    TestDevices.addNexus7Api23Commands(myMockDevice!!.shellCommands)

    // Act
    val result = waitForFuture(myFileSystem!!.getEntry("/data/local/tmp"))

    // Assert
    Truth.assertThat(result).isNotNull()
    Truth.assertThat(result.name).isEqualTo("tmp")
  }

  @Test
  @Throws(Exception::class)
  fun test_FileSystem_GetEntry_Fails_ForInvalidPath() {
    // Prepare
    assert(myFileSystem != null)
    assert(myMockDevice != null)
    TestDevices.addNexus7Api23Commands(myMockDevice!!.shellCommands)

    // Act/Assert
    thrown.expect(ExecutionException::class.java)
    thrown.expectCause(IsInstanceOf.instanceOf(IllegalArgumentException::class.java))
    /*DeviceFileEntry result = */waitForFuture(myFileSystem!!.getEntry("/data/invalid/path"))
  }

  @Test
  @Throws(Exception::class)
  fun test_FileSystem_UploadLocalFile_Works() {
    // Prepare
    assert(myFileSystem != null)
    assert(myMockDevice != null)
    TestDevices.addNexus7Api23Commands(myMockDevice!!.shellCommands)
    val dataEntry = waitForFuture(myFileSystem!!.getEntry("/data/local/tmp"))
    val tempFile = FileUtil.createTempFile("localFile", "tmp").toPath()
    Files.write(tempFile, ByteArray(1024))


    // Act
    val totalBytesRef = AtomicReference<Long>()
    val result = waitForFuture(dataEntry.uploadFile(tempFile, object : FileTransferProgress {
      override fun progress(currentBytes: Long, totalBytes: Long) {
        totalBytesRef.set(totalBytes)
      }

      override fun isCancelled(): Boolean {
        return false
      }
    }))
    // Ensure all progress callbacks have been executed
    myCallbackExecutor!!.submit(EmptyRunnable.getInstance())[TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS]

    // Assert
    Truth.assertThat(result).isEqualTo(Unit)
    Truth.assertThat(totalBytesRef.get()).isEqualTo(1024)
  }

  @Test
  @Throws(Exception::class)
  fun test_FileSystem_DownloadRemoteFile_Works() {
    // Prepare
    assert(myFileSystem != null)
    assert(myMockDevice != null)
    assert(myCallbackExecutor != null)
    TestDevices.addNexus7Api23Commands(myMockDevice!!.shellCommands)
    val deviceEntry = waitForFuture(myFileSystem!!.getEntry("/default.prop"))
    myMockDevice!!.addRemoteFile(deviceEntry.fullPath, deviceEntry.size)
    val tempFile = FileUtil.createTempFile("localFile", "tmp").toPath()

    // Act
    val totalBytesRef = AtomicReference<Long>()
    val result = waitForFuture(deviceEntry.downloadFile(tempFile, object : FileTransferProgress {
      override fun progress(currentBytes: Long, totalBytes: Long) {
        totalBytesRef.set(totalBytes)
      }

      override fun isCancelled(): Boolean {
        return false
      }
    }))
    // Ensure all progress callbacks have been executed
    myCallbackExecutor!!.submit(EmptyRunnable.getInstance())[TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS]

    // Assert
    Truth.assertThat(result).isEqualTo(Unit)
    Truth.assertThat(totalBytesRef.get()).isEqualTo(deviceEntry.size)
    Truth.assertThat(Files.exists(tempFile)).isTrue()
    Truth.assertThat(tempFile.toFile().length()).isEqualTo(deviceEntry.size)
  }

  @Test
  @Throws(Exception::class)
  fun test_FileSystem_UploadSystemFile_ReturnsError() {
    // Prepare
    assert(myFileSystem != null)
    assert(myMockDevice != null)
    TestDevices.addEmulatorApi25Commands(myMockDevice!!.shellCommands)
    myMockDevice!!.addRemoteRestrictedAccessFile("/system/build.prop", 1024)
    val dataEntry = waitForFuture(myFileSystem!!.getEntry("/system"))
    val tempFile = FileUtil.createTempFile("localFile", "tmp").toPath()
    Files.write(tempFile, ByteArray(1024))


    // Act
    val totalBytesRef = AtomicReference<Long>()
    val error = waitForFutureException(dataEntry.uploadFile(tempFile, "build.prop", object : FileTransferProgress {
      override fun progress(currentBytes: Long, totalBytes: Long) {
        totalBytesRef.set(totalBytes)
      }

      override fun isCancelled(): Boolean {
        return false
      }
    }))
    // Ensure all progress callbacks have been executed
    myCallbackExecutor!!.submit(EmptyRunnable.getInstance())[TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS]

    // Assert
    Truth.assertThat(error).isInstanceOf(AdbShellCommandException::class.java)
    Truth.assertThat(error!!.message).isEqualTo("cp: /system/build.prop: Read-only file system")
  }

  @Test
  @Throws(Exception::class)
  fun test_FileSystem_DownloadAccessibleSystemFile_Works() {
    // Prepare
    assert(myFileSystem != null)
    assert(myMockDevice != null)
    assert(myCallbackExecutor != null)
    TestDevices.addEmulatorApi25Commands(myMockDevice!!.shellCommands)
    val deviceEntry = waitForFuture(myFileSystem!!.getEntry("/system/build.prop"))
    myMockDevice!!.addRemoteFile(deviceEntry.fullPath, deviceEntry.size)
    val tempFile = FileUtil.createTempFile("localFile", "tmp").toPath()

    // Act
    val totalBytesRef = AtomicReference<Long>()
    val result = waitForFuture(deviceEntry.downloadFile(tempFile, object : FileTransferProgress {
      override fun progress(currentBytes: Long, totalBytes: Long) {
        totalBytesRef.set(totalBytes)
      }

      override fun isCancelled(): Boolean {
        return false
      }
    }))
    // Ensure all progress callbacks have been executed
    myCallbackExecutor!!.submit(EmptyRunnable.getInstance())[TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS]

    // Assert
    Truth.assertThat(result).isEqualTo(Unit)
    Truth.assertThat(totalBytesRef.get()).isEqualTo(deviceEntry.size)
    Truth.assertThat(Files.exists(tempFile)).isTrue()
    Truth.assertThat(tempFile.toFile().length()).isEqualTo(deviceEntry.size)
  }

  @Test
  @Throws(Exception::class)
  fun test_FileSystem_DownloadRestrictedSystemFile_RecoversFromPullError() {
    // Prepare
    assert(myFileSystem != null)
    assert(myMockDevice != null)
    assert(myCallbackExecutor != null)
    TestDevices.addEmulatorApi25Commands(myMockDevice!!.shellCommands)
    val deviceEntry = waitForFuture(myFileSystem!!.getEntry("/system/build.prop"))
    myMockDevice!!.addRemoteRestrictedAccessFile(deviceEntry.fullPath, deviceEntry.size)
    myMockDevice!!.addRemoteFile("/data/local/tmp/temp0", deviceEntry.size)
    val tempFile = FileUtil.createTempFile("localFile", "tmp").toPath()

    // Act
    val totalBytesRef = AtomicReference<Long>()
    val result = waitForFuture(deviceEntry.downloadFile(tempFile, object : FileTransferProgress {
      override fun progress(currentBytes: Long, totalBytes: Long) {
        totalBytesRef.set(totalBytes)
      }

      override fun isCancelled(): Boolean {
        return false
      }
    }))
    // Ensure all progress callbacks have been executed
    myCallbackExecutor!!.submit(EmptyRunnable.getInstance())[TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS]

    // Assert
    Truth.assertThat(result).isEqualTo(Unit)
    Truth.assertThat(totalBytesRef.get()).isEqualTo(deviceEntry.size)
    Truth.assertThat(Files.exists(tempFile)).isTrue()
    Truth.assertThat(tempFile.toFile().length()).isEqualTo(deviceEntry.size)
  }

  companion object {
    private const val TIMEOUT_MILLISECONDS: Long = 30000

    @ClassRule
    val ourLoggerRule: TestRule = DebugLoggerRule()
    @Throws(Exception::class)
    private fun <V> waitForFuture(future: Future<V>): V {
      assert(!EventQueue.isDispatchThread())
      return future[TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS]
    }

    @Throws(Exception::class)
    private fun <V> waitForFutureException(future: Future<V>): Throwable? {
      assert(!EventQueue.isDispatchThread())
      return try {
        future[TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS]
        throw AssertionError("Future should have failed with an exception")
      } catch (e: ExecutionException) {
        e.cause
      }
    }
  }
}