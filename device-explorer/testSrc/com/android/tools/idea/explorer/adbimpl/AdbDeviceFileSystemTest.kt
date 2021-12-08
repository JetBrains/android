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
import com.android.tools.idea.adb.AdbShellCommandException
import com.android.tools.idea.concurrency.FutureCallbackExecutor
import com.android.tools.idea.explorer.fs.DeviceState
import com.android.tools.idea.explorer.fs.FileTransferProgress
import com.android.tools.idea.testing.DebugLoggerRule
import com.google.common.truth.Truth.assertThat
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
import java.nio.file.Files
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class AdbDeviceFileSystemTest {
  private lateinit var myParentDisposable: Disposable
  private lateinit var myFileSystem: AdbDeviceFileSystem
  private lateinit var myMockDevice: MockDdmlibDevice
  private lateinit var myCallbackExecutor: ExecutorService

  @get:Rule
  var thrown = ExpectedException.none()
  
  @Before
  fun setUp() {
    myParentDisposable = Disposer.newDisposable()
    myCallbackExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor(
      "EDT Simulation Thread",
      PooledThreadExecutor.INSTANCE,
      1,
      myParentDisposable
    )
    myMockDevice = MockDdmlibDevice()
    val taskExecutor = FutureCallbackExecutor(PooledThreadExecutor.INSTANCE)
    val edtExecutor = FutureCallbackExecutor(myCallbackExecutor)
    myFileSystem = AdbDeviceFileSystem(myMockDevice.iDevice, edtExecutor, taskExecutor)
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
    Disposer.dispose(myParentDisposable)
    UniqueFileNameGenerator.setInstanceOverride(null)
  }

  @Test
  fun test_FileSystem_Has_DeviceName() {
    // Prepare
    TestDevices.addNexus7Api23Commands(myMockDevice.shellCommands)

    // Act/Assert
    myMockDevice.name = "foo bar"
    assertThat(myFileSystem.name).isEqualTo(myMockDevice.name)
  }

  @Test
  fun test_FileSystem_Is_Device() {
    // Prepare
    TestDevices.addNexus7Api23Commands(myMockDevice.shellCommands)

    // Act/Assert
    assertThat(myFileSystem.device).isEqualTo(myMockDevice.iDevice)
    assertThat(myFileSystem.isDevice(myMockDevice.iDevice)).isTrue()
  }

  @Test
  fun test_FileSystem_Exposes_DeviceState() {
    // Prepare
    TestDevices.addNexus7Api23Commands(myMockDevice.shellCommands)

    // Act/Assert
    myMockDevice.state = null
    assertThat(myFileSystem.deviceState).isEqualTo(DeviceState.DISCONNECTED)
    myMockDevice.state = IDevice.DeviceState.BOOTLOADER
    assertThat(myFileSystem.deviceState).isEqualTo(DeviceState.BOOTLOADER)
    myMockDevice.state = IDevice.DeviceState.OFFLINE
    assertThat(myFileSystem.deviceState).isEqualTo(DeviceState.OFFLINE)
    myMockDevice.state = IDevice.DeviceState.ONLINE
    assertThat(myFileSystem.deviceState).isEqualTo(DeviceState.ONLINE)
    myMockDevice.state = IDevice.DeviceState.RECOVERY
    assertThat(myFileSystem.deviceState).isEqualTo(DeviceState.RECOVERY)
    myMockDevice.state = IDevice.DeviceState.SIDELOAD
    assertThat(myFileSystem.deviceState).isEqualTo(DeviceState.SIDELOAD)
    myMockDevice.state = IDevice.DeviceState.UNAUTHORIZED
    assertThat(myFileSystem.deviceState).isEqualTo(DeviceState.UNAUTHORIZED)
    myMockDevice.state = IDevice.DeviceState.DISCONNECTED
    assertThat(myFileSystem.deviceState).isEqualTo(DeviceState.DISCONNECTED)
  }

  @Test
  fun test_FileSystem_Has_Root() {
    // Prepare
    TestDevices.addNexus7Api23Commands(myMockDevice.shellCommands)

    // Act
    val result = waitForFuture(myFileSystem.rootDirectory)

    // Assert
    assertThat(result).isNotNull()
    assertThat(result.name).isEqualTo("")
  }

  @Test
  fun test_FileSystem_Has_DataTopLevelDirectory() {
    // Prepare
    TestDevices.addNexus7Api23Commands(myMockDevice.shellCommands)
    val rootEntry = waitForFuture(myFileSystem.rootDirectory)

    // Act
    val result = waitForFuture(rootEntry.entries)

    // Assert
    assertThat(result).isNotNull()
    assertThat(result.find { it.name == "data" }).isNotNull()
  }

  @Test
  fun test_FileSystem_GetEntry_Returns_Root_ForEmptyPath() {
    // Prepare
    TestDevices.addNexus7Api23Commands(myMockDevice.shellCommands)

    // Act
    val result = waitForFuture(myFileSystem.getEntry(""))

    // Assert
    assertThat(result).isNotNull()
    assertThat(result.name).isEqualTo("")
  }

  @Test
  fun test_FileSystem_GetEntry_Returns_Root() {
    // Act
    val result = waitForFuture(myFileSystem.getEntry("/"))

    // Assert
    assertThat(result).isNotNull()
    assertThat(result.name).isEqualTo("")
  }

  @Test
  fun test_FileSystem_GetEntry_Returns_LinkInfo() {
    // Prepare
    TestDevices.addNexus7Api23Commands(myMockDevice.shellCommands)

    // Act
    val result = waitForFuture(myFileSystem.getEntry("/charger"))

    // Assert
    assertThat(result).isNotNull()
    assertThat(result.name).isEqualTo("charger")
    assertThat(result.symbolicLinkTarget).isEqualTo("/sbin/healthd")
    assertThat(result.permissions.text).isEqualTo("lrwxrwxrwx")
    assertThat(result.size).isEqualTo(-1)
    assertThat(result.lastModifiedDate.text).isEqualTo("1969-12-31 16:00")
  }

  @Test
  fun test_FileSystem_GetEntry_Returns_DataDirectory() {
    // Prepare
    TestDevices.addNexus7Api23Commands(myMockDevice.shellCommands)

    // Act
    val result = waitForFuture(myFileSystem.getEntry("/data"))

    // Assert
    assertThat(result).isNotNull()
    assertThat(result.name).isEqualTo("data")
  }

  @Test
  fun test_FileSystem_GetEntry_Returns_DataAppDirectory() {
    // Prepare
    TestDevices.addNexus7Api23Commands(myMockDevice.shellCommands)

    // Act
    val result = waitForFuture(myFileSystem.getEntry("/data/app"))

    // Assert
    assertThat(result).isNotNull()
    assertThat(result.name).isEqualTo("app")
  }

  @Test
  fun test_FileSystem_GetEntries_Returns_DataAppPackages() {
    // Prepare
    TestDevices.addNexus7Api23Commands(myMockDevice.shellCommands)
    val dataEntry = waitForFuture(myFileSystem.getEntry("/data/app"))

    // Act
    val result = waitForFuture(dataEntry.entries)

    // Assert
    assertThat(result).isNotNull()
    val app = result.find { it.name == "com.example.rpaquay.myapplication-2" }
    checkNotNull(app)

    // Act
    val appFiles = waitForFuture(app.entries)

    // Assert
    assertThat(appFiles).isNotNull()
    assertThat(appFiles.find { it.name == "base.apk" }).isNotNull()
  }

  @Test
  fun test_FileSystem_GetEntry_Returns_DataDataDirectory() {
    // Prepare
    TestDevices.addNexus7Api23Commands(myMockDevice.shellCommands)

    // Act
    val result = waitForFuture(myFileSystem.getEntry("/data/data"))

    // Assert
    assertThat(result).isNotNull()
    assertThat(result.name).isEqualTo("data")
  }

  @Test
  fun test_FileSystem_GetEntries_Returns_DataDataPackages() {
    // Prepare
    TestDevices.addNexus7Api23Commands(myMockDevice.shellCommands)
    val dataEntry = waitForFuture(myFileSystem.getEntry("/data/data"))

    // Act
    val result = waitForFuture(dataEntry.entries)

    // Assert
    assertThat(result).isNotNull()
    assertThat(result.find { it.name == "com.example.rpaquay.myapplication" }).isNotNull()
  }

  @Test
  fun test_FileSystem_GetEntry_Returns_DataLocalDirectory() {
    // Prepare
    TestDevices.addNexus7Api23Commands(myMockDevice.shellCommands)

    // Act
    val result = waitForFuture(myFileSystem.getEntry("/data/local"))

    // Assert
    assertThat(result).isNotNull()
    assertThat(result.name).isEqualTo("local")
  }

  @Test
  fun test_FileSystem_GetEntry_Returns_DataLocalTempDirectory() {
    // Prepare
    TestDevices.addNexus7Api23Commands(myMockDevice.shellCommands)

    // Act
    val result = waitForFuture(myFileSystem.getEntry("/data/local/tmp"))

    // Assert
    assertThat(result).isNotNull()
    assertThat(result.name).isEqualTo("tmp")
  }

  @Test
  fun test_FileSystem_GetEntry_Fails_ForInvalidPath() {
    // Prepare
    TestDevices.addNexus7Api23Commands(myMockDevice.shellCommands)

    // Act/Assert
    thrown.expect(ExecutionException::class.java)
    thrown.expectCause(IsInstanceOf.instanceOf(IllegalArgumentException::class.java))
    /*DeviceFileEntry result = */waitForFuture(myFileSystem.getEntry("/data/invalid/path"))
  }

  @Test
  fun test_FileSystem_UploadLocalFile_Works() {
    // Prepare
    TestDevices.addNexus7Api23Commands(myMockDevice.shellCommands)
    val dataEntry = waitForFuture(myFileSystem.getEntry("/data/local/tmp"))
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
    myCallbackExecutor.submit(EmptyRunnable.getInstance())[TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS]

    // Assert
    assertThat(result).isEqualTo(Unit)
    assertThat(totalBytesRef.get()).isEqualTo(1024)
  }

  @Test
  fun test_FileSystem_DownloadRemoteFile_Works() {
    // Prepare
    TestDevices.addNexus7Api23Commands(myMockDevice.shellCommands)
    val deviceEntry = waitForFuture(myFileSystem.getEntry("/default.prop"))
    myMockDevice.addRemoteFile(deviceEntry.fullPath, deviceEntry.size)
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
    myCallbackExecutor.submit(EmptyRunnable.getInstance()).get(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)

    // Assert
    assertThat(result).isEqualTo(Unit)
    assertThat(totalBytesRef.get()).isEqualTo(deviceEntry.size)
    assertThat(Files.exists(tempFile)).isTrue()
    assertThat(tempFile.toFile().length()).isEqualTo(deviceEntry.size)
  }

  @Test
  fun test_FileSystem_UploadSystemFile_ReturnsError() {
    // Prepare
    TestDevices.addEmulatorApi25Commands(myMockDevice.shellCommands)
    myMockDevice.addRemoteRestrictedAccessFile("/system/build.prop", 1024)
    val dataEntry = waitForFuture(myFileSystem.getEntry("/system"))
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
    myCallbackExecutor.submit(EmptyRunnable.getInstance()).get(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)

    // Assert
    assertThat(error).isInstanceOf(AdbShellCommandException::class.java)
    assertThat(error.message).isEqualTo("cp: /system/build.prop: Read-only file system")
  }

  @Test
  fun test_FileSystem_DownloadAccessibleSystemFile_Works() {
    // Prepare
    TestDevices.addEmulatorApi25Commands(myMockDevice.shellCommands)
    val deviceEntry = waitForFuture(myFileSystem.getEntry("/system/build.prop"))
    myMockDevice.addRemoteFile(deviceEntry.fullPath, deviceEntry.size)
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
    myCallbackExecutor.submit(EmptyRunnable.getInstance())[TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS]

    // Assert
    assertThat(result).isEqualTo(Unit)
    assertThat(totalBytesRef.get()).isEqualTo(deviceEntry.size)
    assertThat(Files.exists(tempFile)).isTrue()
    assertThat(tempFile.toFile().length()).isEqualTo(deviceEntry.size)
  }

  @Test
  fun test_FileSystem_DownloadRestrictedSystemFile_RecoversFromPullError() {
    // Prepare
    TestDevices.addEmulatorApi25Commands(myMockDevice.shellCommands)
    val deviceEntry = waitForFuture(myFileSystem.getEntry("/system/build.prop"))
    myMockDevice.addRemoteRestrictedAccessFile(deviceEntry.fullPath, deviceEntry.size)
    myMockDevice.addRemoteFile("/data/local/tmp/temp0", deviceEntry.size)
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
    myCallbackExecutor.submit(EmptyRunnable.getInstance())[TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS]

    // Assert
    assertThat(result).isEqualTo(Unit)
    assertThat(totalBytesRef.get()).isEqualTo(deviceEntry.size)
    assertThat(Files.exists(tempFile)).isTrue()
    assertThat(tempFile.toFile().length()).isEqualTo(deviceEntry.size)
  }

  companion object {
    private const val TIMEOUT_MILLISECONDS: Long = 30000

    @JvmField
    @ClassRule
    val ourLoggerRule: TestRule = DebugLoggerRule()

    private fun <V> waitForFuture(future: Future<V>): V {
      assert(!EventQueue.isDispatchThread())
      return future.get(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)
    }

    private fun <V> waitForFutureException(future: Future<V>): Throwable {
      assert(!EventQueue.isDispatchThread())
      return try {
        future.get(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)
        throw AssertionError("Future should have failed with an exception")
      } catch (e: ExecutionException) {
        checkNotNull(e.cause)
      }
    }
  }
}