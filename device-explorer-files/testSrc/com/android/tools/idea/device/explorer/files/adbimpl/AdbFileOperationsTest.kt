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
package com.android.tools.idea.device.explorer.files.adbimpl

import com.android.adblib.AdbSession
import com.android.adblib.SOCKET_CONNECT_TIMEOUT_MS
import com.android.adblib.connectedDevicesTracker
import com.android.adblib.testingutils.CloseablesRule
import com.android.adblib.testingutils.FakeAdbServerProvider
import com.android.adblib.testingutils.TestingAdbSessionHost
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.ShellProtocolType
import com.android.fakeadbserver.devicecommandhandlers.SyncCommandHandler
import com.android.tools.idea.adb.AdbShellCommandException
import com.android.tools.idea.testing.DebugLoggerRule
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.TestApplicationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.ide.PooledThreadExecutor
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.Duration

@RunWith(Parameterized::class)
class AdbFileOperationsTest(private val testDevice: TestDevices) {
  @get:Rule
  val thrown = ExpectedException.none()

  val shellCommands = TestShellCommands()

  @JvmField
  @Rule
  val closeables = CloseablesRule()

  val fakeAdb = closeables.register(FakeAdbServerProvider()
                                      .installDefaultCommandHandlers()
                                      .installDeviceHandler(TestShellCommandHandler(ShellProtocolType.SHELL, shellCommands))
                                      .installDeviceHandler(SyncCommandHandler())
                                      .build().start())
  val host = closeables.register(TestingAdbSessionHost())
  val session = closeables.register(AdbSession.create(
    host,
    fakeAdb.createChannelProvider(host),
    Duration.ofMillis(SOCKET_CONNECT_TIMEOUT_MS)))

  val dispatcher = PooledThreadExecutor.INSTANCE.asCoroutineDispatcher()
  val scope = CoroutineScope(dispatcher)

  @Before
  fun setUp() {
    // AdbLib makes use of ApplicationManager, so we need to set one up.
    TestApplicationManager.getInstance()
  }

  private fun setupMockDevice(): AdbFileOperations {
    testDevice.addCommands(shellCommands)

    fakeAdb.connectDevice(
      deviceId = "test_device_01", manufacturer = "Google", deviceModel = "Pixel 10", release = "8.0", sdk = "31",
      hostConnectionType = DeviceState.HostConnectionType.USB)

    val device = runBlocking {
      withTimeout(Duration.ofSeconds(5).toMillis()) {
        session.connectedDevicesTracker.connectedDevices.first { it.isNotEmpty() }.first()
      }
    }

    return AdbFileOperations(device, AdbDeviceCapabilities(scope, "Test Device", device), dispatcher)
  }

  @Test
  fun testCreateNewFileSuccess() = runBlocking {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act
    val result = fileOperations.createNewFile("/sdcard", "foo.txt")

    // Assert
    assertThat(result).isEqualTo(Unit)
  }

  @Test
  fun testCreateNewFileRunAsSuccess() = runBlocking {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act
    val result =
      fileOperations.createNewFileRunAs(
        "/data/data/com.example.rpaquay.myapplication",
        "NewTextFile.txt",
        "com.example.rpaquay.myapplication"
      )


    // Assert
    assertThat(result).isEqualTo(Unit)
  }

  @Test
  fun testCreateNewFileInvalidFileNameError() = runBlocking {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act/Assert
    thrown.expect(AdbShellCommandException::class.java)
    fileOperations.createNewFile("/", "fo/o.txt")
  }

  @Test
  fun testCreateNewFileReadOnlyError() = runBlocking {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act/Assert
    thrown.expect(AdbShellCommandException::class.java)
    fileOperations.createNewFile("/", "foo.txt")
  }

  @Test
  fun testCreateNewFilePermissionError() = runBlocking {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act/Assert
    thrown.expect(AdbShellCommandException::class.java)
    fileOperations.createNewFile("/system", "foo.txt")
  }

  @Test
  fun testCreateNewFileExistError() = runBlocking {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act/Assert
    thrown.expect(AdbShellCommandException::class.java)
    fileOperations.createNewFile("/", "default.prop")
  }

  @Test
  fun testCreateNewDirectorySuccess() = runBlocking {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act
    val result = fileOperations.createNewDirectory("/sdcard", "foo-dir")

    // Assert
    assertThat(result).isEqualTo(Unit)
  }

  @Test
  fun testCreateNewDirectoryRunAsSuccess() = runBlocking {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act
    val result =
      fileOperations.createNewDirectoryRunAs(
        "/data/data/com.example.rpaquay.myapplication",
        "foo-dir",
        "com.example.rpaquay.myapplication"
      )


    // Assert
    assertThat(result).isEqualTo(Unit)
  }

  @Test
  fun testCreateNewDirectoryInvalidNameError() = runBlocking {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act

    // Assert
    thrown.expect(AdbShellCommandException::class.java)
    fileOperations.createNewDirectory("/", "fo/o-dir")
  }

  @Test
  fun testCreateNewDirectoryReadOnlyError() = runBlocking {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act

    // Assert
    thrown.expect(AdbShellCommandException::class.java)
    fileOperations.createNewDirectory("/", "foo-dir")
  }

  @Test
  fun testCreateNewDirectoryPermissionError() = runBlocking {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act

    // Assert
    thrown.expect(AdbShellCommandException::class.java)
    fileOperations.createNewDirectory("/system", "foo-dir")
  }

  @Test
  fun testCreateNewDirectoryExistError() = runBlocking {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act

    // Assert
    thrown.expect(AdbShellCommandException::class.java)
    fileOperations.createNewDirectory("/", "data")
  }

  @Test
  fun testDeleteExistingFileSuccess() = runBlocking {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act
    val result = fileOperations.deleteFile("/sdcard/foo.txt")

    // Assert
    assertThat(result).isEqualTo(Unit)
  }

  @Test
  fun testDeleteExistingFileRunAsSuccess() = runBlocking {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act
    val result =
      fileOperations.deleteFileRunAs(
        "/data/data/com.example.rpaquay.myapplication/NewTextFile.txt",
        "com.example.rpaquay.myapplication"
      )


    // Assert
    assertThat(result).isEqualTo(Unit)
  }

  @Test
  fun testDeleteExistingDirectoryAsFileError() = runBlocking {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act/Assert
    thrown.expect(AdbShellCommandException::class.java)
    fileOperations.deleteFile("/sdcard/foo-dir")
  }

  @Test
  fun testDeleteExistingReadOnlyFileError() = runBlocking {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act/Assert
    thrown.expect(AdbShellCommandException::class.java)
    fileOperations.deleteFile("/system/bin/sh")
  }

  @Test
  fun testDeleteExistingDirectorySucceeds() = runBlocking {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act
    val result = fileOperations.deleteRecursive("/sdcard/foo-dir")

    // Assert
    assertThat(result).isEqualTo(Unit)
  }

  @Test
  fun testDeleteExistingDirectoryRunAsSucceeds() = runBlocking {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act
    val result =
      fileOperations.deleteRecursiveRunAs(
        "/data/data/com.example.rpaquay.myapplication/foo-dir",
        "com.example.rpaquay.myapplication"
      )


    // Assert
    assertThat(result).isEqualTo(Unit)
  }

  @Test
  fun testDeleteExistingDirectoryPermissionError() = runBlocking {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act/Assert
    thrown.expect(AdbShellCommandException::class.java)
    fileOperations.deleteRecursive("/config")
  }

  @Test
  fun testListPackages() = runBlocking {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act
    val result = fileOperations.listPackages()

    // Assert
    assertThat(result).isNotNull()
    assertThat(result).contains("com.example.rpaquay.myapplication")
  }

  companion object {
    @SuppressWarnings("unused")
    @JvmStatic
    @Parameterized.Parameters(name="{0}")
    fun data(): List<Array<Any>> =
      listOf(arrayOf(TestDevices.EMULATOR_API10), arrayOf(TestDevices.NEXUS_7_API23))

    @JvmField
    @ClassRule
    var ourLoggerRule = DebugLoggerRule()
  }
}