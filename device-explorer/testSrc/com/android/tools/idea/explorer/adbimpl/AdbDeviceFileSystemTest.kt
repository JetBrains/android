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
import com.intellij.testFramework.UsefulTestCase.assertThrows
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.jetbrains.ide.PooledThreadExecutor
import org.junit.After
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.rules.TestRule
import java.nio.file.Files
import java.util.concurrent.ExecutorService
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
    val edtExecutor = FutureCallbackExecutor(myCallbackExecutor)
    myFileSystem = AdbDeviceFileSystem(myMockDevice.iDevice, edtExecutor, PooledThreadExecutor.INSTANCE.asCoroutineDispatcher())
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
  fun test_FileSystem_Has_Root(): Unit = runBlocking {
    // Prepare
    TestDevices.addNexus7Api23Commands(myMockDevice.shellCommands)

    // Act
    val result = myFileSystem.rootDirectory()

    // Assert
    assertThat(result).isNotNull()
    assertThat(result.name).isEqualTo("")
  }

  @Test
  fun test_FileSystem_Has_DataTopLevelDirectory(): Unit = runBlocking {
    // Prepare
    TestDevices.addNexus7Api23Commands(myMockDevice.shellCommands)
    val rootEntry = myFileSystem.rootDirectory()

    // Act
    val result = rootEntry.entries()

    // Assert
    assertThat(result).isNotNull()
    assertThat(result.find { it.name == "data" }).isNotNull()
  }

  @Test
  fun test_FileSystem_GetEntry_Returns_Root_ForEmptyPath(): Unit = runBlocking {
    // Prepare
    TestDevices.addNexus7Api23Commands(myMockDevice.shellCommands)

    // Act
    val result = myFileSystem.getEntry("")

    // Assert
    assertThat(result).isNotNull()
    assertThat(result.name).isEqualTo("")
  }

  @Test
  fun test_FileSystem_GetEntry_Returns_Root(): Unit = runBlocking {
    // Act
    val result = myFileSystem.getEntry("/")

    // Assert
    assertThat(result).isNotNull()
    assertThat(result.name).isEqualTo("")
  }

  @Test
  fun test_FileSystem_GetEntry_Returns_LinkInfo(): Unit = runBlocking {
    // Prepare
    TestDevices.addNexus7Api23Commands(myMockDevice.shellCommands)

    // Act
    val result = myFileSystem.getEntry("/charger")

    // Assert
    assertThat(result).isNotNull()
    assertThat(result.name).isEqualTo("charger")
    assertThat(result.symbolicLinkTarget).isEqualTo("/sbin/healthd")
    assertThat(result.permissions.text).isEqualTo("lrwxrwxrwx")
    assertThat(result.size).isEqualTo(-1)
    assertThat(result.lastModifiedDate.text).isEqualTo("1969-12-31 16:00")
  }

  @Test
  fun test_FileSystem_GetEntry_Returns_DataDirectory(): Unit = runBlocking {
    // Prepare
    TestDevices.addNexus7Api23Commands(myMockDevice.shellCommands)

    // Act
    val result = myFileSystem.getEntry("/data")

    // Assert
    assertThat(result).isNotNull()
    assertThat(result.name).isEqualTo("data")
  }

  @Test
  fun test_FileSystem_GetEntry_Returns_DataAppDirectory(): Unit = runBlocking {
    // Prepare
    TestDevices.addNexus7Api23Commands(myMockDevice.shellCommands)

    // Act
    val result = myFileSystem.getEntry("/data/app")

    // Assert
    assertThat(result).isNotNull()
    assertThat(result.name).isEqualTo("app")
  }

  @Test
  fun test_FileSystem_GetEntries_Returns_DataAppPackages(): Unit = runBlocking {
    // Prepare
    TestDevices.addNexus7Api23Commands(myMockDevice.shellCommands)
    val dataEntry = myFileSystem.getEntry("/data/app")

    // Act
    val result = dataEntry.entries()

    // Assert
    assertThat(result).isNotNull()
    val app = result.find { it.name == "com.example.rpaquay.myapplication-2" }
    checkNotNull(app)

    // Act
    val appFiles = app.entries()

    // Assert
    assertThat(appFiles).isNotNull()
    assertThat(appFiles.find { it.name == "base.apk" }).isNotNull()
  }

  @Test
  fun test_FileSystem_GetEntry_Returns_DataDataDirectory(): Unit = runBlocking {
    // Prepare
    TestDevices.addNexus7Api23Commands(myMockDevice.shellCommands)

    // Act
    val result = myFileSystem.getEntry("/data/data")

    // Assert
    assertThat(result).isNotNull()
    assertThat(result.name).isEqualTo("data")
  }

  @Test
  fun test_FileSystem_GetEntries_Returns_DataDataPackages(): Unit = runBlocking {
    // Prepare
    TestDevices.addNexus7Api23Commands(myMockDevice.shellCommands)
    val dataEntry = myFileSystem.getEntry("/data/data")

    // Act
    val result = dataEntry.entries()

    // Assert
    assertThat(result).isNotNull()
    assertThat(result.find { it.name == "com.example.rpaquay.myapplication" }).isNotNull()
  }

  @Test
  fun test_FileSystem_GetEntry_Returns_DataLocalDirectory(): Unit = runBlocking {
    // Prepare
    TestDevices.addNexus7Api23Commands(myMockDevice.shellCommands)

    // Act
    val result = myFileSystem.getEntry("/data/local")

    // Assert
    assertThat(result).isNotNull()
    assertThat(result.name).isEqualTo("local")
  }

  @Test
  fun test_FileSystem_GetEntry_Returns_DataLocalTempDirectory(): Unit = runBlocking {
    // Prepare
    TestDevices.addNexus7Api23Commands(myMockDevice.shellCommands)

    // Act
    val result = myFileSystem.getEntry("/data/local/tmp")

    // Assert
    assertThat(result).isNotNull()
    assertThat(result.name).isEqualTo("tmp")
  }

  @Test
  fun test_FileSystem_GetEntry_Fails_ForInvalidPath(): Unit = runBlocking {
    // Prepare
    TestDevices.addNexus7Api23Commands(myMockDevice.shellCommands)

    // Act/Assert
    thrown.expect(IllegalArgumentException::class.java)
    /*DeviceFileEntry result = */myFileSystem.getEntry("/data/invalid/path")
  }

  @Test
  fun test_FileSystem_UploadLocalFile_Works(): Unit = runBlocking {
    // Prepare
    TestDevices.addNexus7Api23Commands(myMockDevice.shellCommands)
    val dataEntry = myFileSystem.getEntry("/data/local/tmp")
    val tempFile = FileUtil.createTempFile("localFile", "tmp").toPath()
    Files.write(tempFile, ByteArray(1024))


    // Act
    val totalBytesRef = AtomicReference<Long>()
    val result = dataEntry.uploadFile(tempFile, object : FileTransferProgress {
      override fun progress(currentBytes: Long, totalBytes: Long) {
        totalBytesRef.set(totalBytes)
      }

      override fun isCancelled(): Boolean {
        return false
      }
    })
    // Ensure all progress callbacks have been executed
    myCallbackExecutor.submit(EmptyRunnable.getInstance()).get(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)

    // Assert
    assertThat(result).isEqualTo(Unit)
    assertThat(totalBytesRef.get()).isEqualTo(1024)
  }

  @Test
  fun test_FileSystem_DownloadRemoteFile_Works(): Unit = runBlocking {
    // Prepare
    TestDevices.addNexus7Api23Commands(myMockDevice.shellCommands)
    val deviceEntry = myFileSystem.getEntry("/default.prop")
    myMockDevice.addRemoteFile(deviceEntry.fullPath, deviceEntry.size)
    val tempFile = FileUtil.createTempFile("localFile", "tmp").toPath()

    // Act
    val totalBytesRef = AtomicReference<Long>()
    val result = deviceEntry.downloadFile(tempFile, object : FileTransferProgress {
      override fun progress(currentBytes: Long, totalBytes: Long) {
        totalBytesRef.set(totalBytes)
      }

      override fun isCancelled(): Boolean {
        return false
      }
    })
    // Ensure all progress callbacks have been executed
    myCallbackExecutor.submit(EmptyRunnable.getInstance()).get(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)

    // Assert
    assertThat(result).isEqualTo(Unit)
    assertThat(totalBytesRef.get()).isEqualTo(deviceEntry.size)
    assertThat(Files.exists(tempFile)).isTrue()
    assertThat(tempFile.toFile().length()).isEqualTo(deviceEntry.size)
  }

  @Test
  fun test_FileSystem_UploadSystemFile_ReturnsError(): Unit = runBlocking {
    // Prepare
    TestDevices.addEmulatorApi25Commands(myMockDevice.shellCommands)
    myMockDevice.addRemoteRestrictedAccessFile("/system/build.prop", 1024)
    val dataEntry = myFileSystem.getEntry("/system")
    val tempFile = FileUtil.createTempFile("localFile", "tmp").toPath()
    val uploadFileSize = 1100
    Files.write(tempFile, ByteArray(uploadFileSize))

    // Act
    val totalBytesRef = AtomicReference<Long>()
    assertThrows(AdbShellCommandException::class.java, "cp: /system/build.prop: Read-only file system") {
      runBlocking {
        dataEntry.uploadFile(tempFile, "build.prop", object : FileTransferProgress {
          override fun progress(currentBytes: Long, totalBytes: Long) {
            totalBytesRef.set(totalBytes)
          }

          override fun isCancelled(): Boolean {
            return false
          }
        })
      }
    }
    // Ensure all progress callbacks have been executed
    myCallbackExecutor.submit(EmptyRunnable.getInstance()).get(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)

    // The bytes get sent; the failure happens afterward when trying to copy from /tmp
    assertThat(totalBytesRef.get()).isEqualTo(uploadFileSize)
  }

  @Test
  fun test_FileSystem_DownloadAccessibleSystemFile_Works(): Unit = runBlocking {
    // Prepare
    TestDevices.addEmulatorApi25Commands(myMockDevice.shellCommands)
    val deviceEntry = myFileSystem.getEntry("/system/build.prop")
    myMockDevice.addRemoteFile(deviceEntry.fullPath, deviceEntry.size)
    val tempFile = FileUtil.createTempFile("localFile", "tmp").toPath()

    // Act
    val totalBytesRef = AtomicReference<Long>()
    val result = deviceEntry.downloadFile(tempFile, object : FileTransferProgress {
      override fun progress(currentBytes: Long, totalBytes: Long) {
        totalBytesRef.set(totalBytes)
      }

      override fun isCancelled(): Boolean {
        return false
      }
    })
    // Ensure all progress callbacks have been executed
    myCallbackExecutor.submit(EmptyRunnable.getInstance()).get(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)

    // Assert
    assertThat(result).isEqualTo(Unit)
    assertThat(totalBytesRef.get()).isEqualTo(deviceEntry.size)
    assertThat(Files.exists(tempFile)).isTrue()
    assertThat(tempFile.toFile().length()).isEqualTo(deviceEntry.size)
  }

  @Test
  fun test_FileSystem_DownloadRestrictedSystemFile_RecoversFromPullError(): Unit = runBlocking {
    // Prepare
    TestDevices.addEmulatorApi25Commands(myMockDevice.shellCommands)
    val deviceEntry = myFileSystem.getEntry("/system/build.prop")
    myMockDevice.addRemoteRestrictedAccessFile(deviceEntry.fullPath, deviceEntry.size)
    myMockDevice.addRemoteFile("/data/local/tmp/temp0", deviceEntry.size)
    val tempFile = FileUtil.createTempFile("localFile", "tmp").toPath()

    // Act
    val totalBytesRef = AtomicReference<Long>()
    val result = deviceEntry.downloadFile(tempFile, object : FileTransferProgress {
      override fun progress(currentBytes: Long, totalBytes: Long) {
        totalBytesRef.set(totalBytes)
      }

      override fun isCancelled(): Boolean {
        return false
      }
    })
    // Ensure all progress callbacks have been executed
    myCallbackExecutor.submit(EmptyRunnable.getInstance()).get(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)

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
  }
}