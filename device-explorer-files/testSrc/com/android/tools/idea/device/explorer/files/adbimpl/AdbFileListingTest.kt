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
import com.android.adblib.ConnectedDevice
import com.android.adblib.SOCKET_CONNECT_TIMEOUT_MS
import com.android.adblib.connectedDevicesTracker
import com.android.adblib.testingutils.CloseablesRule
import com.android.adblib.testingutils.FakeAdbServerProvider
import com.android.adblib.testingutils.TestingAdbSessionHost
import com.android.ddmlib.DdmPreferences
import com.android.ddmlib.ShellCommandUnresponsiveException
import com.android.fakeadbserver.ShellProtocolType
import com.android.fakeadbserver.devicecommandhandlers.SyncCommandHandler
import com.android.tools.idea.device.explorer.files.adbimpl.AdbFileListingEntry.EntryKind
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.TestApplicationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.ide.PooledThreadExecutor
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.runners.Parameterized
import java.time.Duration
import java.util.concurrent.TimeoutException
import java.util.function.Consumer

class AdbFileListingTest {
  private val deviceName = "Test Device"
  private lateinit var device: ConnectedDevice
  private lateinit var deviceState: com.android.fakeadbserver.DeviceState

  private val commands = TestShellCommands()

  @get:Rule
  var thrown = ExpectedException.none()

  @JvmField
  @Rule
  val closeables = CloseablesRule()

  val fakeAdb = closeables.register(FakeAdbServerProvider()
                                      .installDeviceHandler(TestShellCommandHandler(ShellProtocolType.SHELL, commands))
                                      .installDeviceHandler(SyncCommandHandler())
                                      .buildDefault())
  val host = closeables.register(TestingAdbSessionHost())
  val session = AdbSession.create(
    host,
    fakeAdb.createChannelProvider(host),
    Duration.ofMillis(SOCKET_CONNECT_TIMEOUT_MS)
  )

  private val dispatcher = PooledThreadExecutor.INSTANCE.asCoroutineDispatcher()
  private val scope = CoroutineScope(dispatcher)

  private var originalTimeout = 0

  @Before
  fun setUp() {
    // AdbLib makes use of ApplicationManager, so we need to set one up.
    TestApplicationManager.getInstance()

    // We need the DDMLib timeout to be shorter than the test timeout, so that we can test that
    // ShellCommandUnresponsiveException is produced when ADB is slow to respond. The default
    // timeout of 5s is fine. However, the AdbService singleton messes with this timeout, so this
    // test may fail (depending on test execution order) unless we set the timeout ourselves.
    // (And the proper solution of resetting all shared state between tests appears infeasible.)
    originalTimeout = DdmPreferences.getTimeOut()
    DdmPreferences.setTimeOut(5_000)

    fakeAdb.start()
    deviceState = fakeAdb.connectDevice(
      deviceId = "test_device_01", manufacturer = "Google", deviceModel = "Pixel 10", release = "8.0", sdk = "31",
      hostConnectionType = com.android.fakeadbserver.DeviceState.HostConnectionType.USB)

    device = runBlocking {
      withTimeout(Duration.ofSeconds(5).toMillis()) {
        session.connectedDevicesTracker.connectedDevices.first { it.isNotEmpty() }.first()
      }
    }
  }

  @After
  fun tearDown() {
    DdmPreferences.setTimeOut(originalTimeout)
  }

  @Test
  fun test_Nexus7Api23_GetRoot() = runBlocking {
    // Prepare
    TestDevices.NEXUS_7_API23.addCommands(commands)
    val fileListing = AdbFileListing(device, AdbDeviceCapabilities(scope, deviceName, device), dispatcher)

    // Act
    val root = fileListing.root

    // Assert
    assertThat(root).isNotNull()
    assertThat(root.fullPath).isEqualTo("/")
    assertThat(root.name).isEqualTo("")
    assertThat(root.isDirectory).isTrue()
  }

  @Test
  fun test_Nexus7Api23_GetRootChildrenError(): Unit = runBlocking {
    // Prepare
    TestDevices.NEXUS_7_API23.addCommands(commands)
    commands.addError("ls -al /" + TestDevices.COMMAND_ERROR_CHECK_SUFFIX, ShellCommandUnresponsiveException())
    val fileListing = AdbFileListing(device, AdbDeviceCapabilities(scope, deviceName, device), dispatcher)

    // Act
    val root = fileListing.root

    // Assert
    thrown.expect(TimeoutException::class.java)
    fileListing.getChildren(root)
  }

  @Test
  fun test_Nexus7Api23_GetRootChildren(): Unit = runBlocking {
    // Prepare
    TestDevices.NEXUS_7_API23.addCommands(commands)
    val fileListing = AdbFileListing(device, AdbDeviceCapabilities(scope, deviceName, device), dispatcher)

    // Act
    val root = fileListing.root
    val rootEntries = fileListing.getChildren(root)

    // Assert
    assertThat(rootEntries).isNotNull()
    assertThat(rootEntries.find { it.name == "acct" }).isNotNull()
    assertThat(rootEntries.find { it.name == "charger" }).isNotNull()
    assertThat(rootEntries.find { it.name == "vendor" }).isNotNull()
    assertThat(rootEntries.find { it.name == "init" }).isNull()
    assertEntry(rootEntries, "acct") { entry: AdbFileListingEntry ->
      assertThat(entry.isDirectory).isTrue()
      assertThat(entry.isFile).isFalse()
      assertThat(entry.isSymbolicLink).isFalse()
      assertThat(entry.permissions).isEqualTo("drwxr-xr-x")
      assertThat(entry.owner).isEqualTo("root")
      assertThat(entry.group).isEqualTo("root")
      assertThat(entry.date).isEqualTo("2016-11-21")
      assertThat(entry.time).isEqualTo("12:09")
      assertThat(entry.info).isNull()
    }
    assertEntry(rootEntries, "cache") { entry: AdbFileListingEntry ->
      assertThat(entry.isDirectory).isTrue()
      assertThat(entry.isFile).isFalse()
      assertThat(entry.isSymbolicLink).isFalse()
      assertThat(entry.permissions).isEqualTo("drwxrwx---")
      assertThat(entry.owner).isEqualTo("system")
      assertThat(entry.group).isEqualTo("cache")
      assertThat(entry.date).isEqualTo("2016-08-26")
      assertThat(entry.time).isEqualTo("12:12")
      assertThat(entry.info).isNull()
    }
    assertEntry(rootEntries, "charger") { entry: AdbFileListingEntry ->
      assertThat(entry.isDirectory).isFalse()
      assertThat(entry.isFile).isFalse()
      assertThat(entry.isSymbolicLink).isTrue()
      assertThat(entry.permissions).isEqualTo("lrwxrwxrwx")
      assertThat(entry.owner).isEqualTo("root")
      assertThat(entry.group).isEqualTo("root")
      assertThat(entry.date).isEqualTo("1969-12-31")
      assertThat(entry.time).isEqualTo("16:00")
      assertThat(entry.info).isEqualTo("-> /sbin/healthd")
    }
    assertEntry(rootEntries, "etc") { entry: AdbFileListingEntry ->
      assertThat(entry.isDirectory).isFalse()
      assertThat(entry.isFile).isFalse()
      assertThat(entry.isSymbolicLink).isTrue()
      assertThat(entry.permissions).isEqualTo("lrwxrwxrwx")
      assertThat(entry.owner).isEqualTo("root")
      assertThat(entry.group).isEqualTo("root")
      assertThat(entry.date).isEqualTo("2016-11-21")
      assertThat(entry.time).isEqualTo("12:09")
      assertThat(entry.info).isEqualTo("-> /system/etc")
    }
  }

  @Test
  fun test_Nexus7Api23_IsDirectoryLink(): Unit = runBlocking {
    // Prepare
    TestDevices.NEXUS_7_API23.addCommands(commands)
    val fileListing = AdbFileListing(device, AdbDeviceCapabilities(scope, deviceName, device), dispatcher)

    // Act
    val root = fileListing.root
    val rootEntries = fileListing.getChildren(root)

    // Assert
    assertThat(rootEntries).isNotNull()
    assertDirectoryLink(fileListing, rootEntries, "charger", false)
    assertDirectoryLink(fileListing, rootEntries, "d", true)
    assertDirectoryLink(fileListing, rootEntries, "etc", true)
    assertDirectoryLink(fileListing, rootEntries, "sdcard", true)
    assertDirectoryLink(fileListing, rootEntries, "tombstones", false)
    assertDirectoryLink(fileListing, rootEntries, "vendor", true)
  }

  @Test
  fun test_EmulatorApi25_GetRoot(): Unit = runBlocking {
    // Prepare
    TestDevices.NEXUS_7_API23.addCommands(commands)
    val fileListing = AdbFileListing(device, AdbDeviceCapabilities(scope, deviceName, device), dispatcher)

    // Act
    val root = fileListing.root

    // Assert
    assertThat(root).isNotNull()
    assertThat(root.fullPath).isEqualTo("/")
    assertThat(root.name).isEqualTo("")
    assertThat(root.isDirectory).isTrue()
  }

  @Test
  fun test_EmulatorApi25_GetRootChildrenError(): Unit = runBlocking {
    // Prepare
    TestDevices.EMULATOR_API25.addCommands(commands)
    commands.addError("su 0 sh -c 'ls -al /'" + TestDevices.COMMAND_ERROR_CHECK_SUFFIX, ShellCommandUnresponsiveException())
    val fileListing = AdbFileListing(device, AdbDeviceCapabilities(scope, deviceName, device), dispatcher)

    // Act
    val root = fileListing.root

    // Assert
    thrown.expect(TimeoutException::class.java)
    fileListing.getChildren(root)
  }

  @Test
  fun test_EmulatorApi25_GetRootChildren(): Unit = runBlocking {
    // Prepare
    TestDevices.EMULATOR_API25.addCommands(commands)
    val fileListing = AdbFileListing(device, AdbDeviceCapabilities(scope, deviceName, device), dispatcher)

    // Act
    val root = fileListing.root
    val rootEntries = fileListing.getChildren(root)

    // Assert
    assertThat(rootEntries).isNotNull()
    assertThat(rootEntries.find { it.name == "acct" }).isNotNull()
    assertThat(rootEntries.find { it.name == "charger" }).isNotNull()
    assertThat(rootEntries.find { it.name == "vendor" }).isNotNull()
    assertThat(rootEntries.find { it.name == "init" }).isNotNull()
    assertEntry(rootEntries, "acct") { entry: AdbFileListingEntry ->
      assertThat(entry.isDirectory).isTrue()
      assertThat(entry.isFile).isFalse()
      assertThat(entry.isSymbolicLink).isFalse()
      assertThat(entry.permissions).isEqualTo("drwxr-xr-x")
      assertThat(entry.owner).isEqualTo("root")
      assertThat(entry.group).isEqualTo("root")
      assertThat(entry.date).isEqualTo("2017-03-06")
      assertThat(entry.time).isEqualTo("21:15")
      assertThat(entry.info).isNull()
    }
    assertEntry(rootEntries, "cache") { entry: AdbFileListingEntry ->
      assertThat(entry.isDirectory).isTrue()
      assertThat(entry.isFile).isFalse()
      assertThat(entry.isSymbolicLink).isFalse()
      assertThat(entry.permissions).isEqualTo("drwxrwx---")
      assertThat(entry.owner).isEqualTo("system")
      assertThat(entry.group).isEqualTo("cache")
      assertThat(entry.date).isEqualTo("2016-12-10")
      assertThat(entry.time).isEqualTo("21:19")
      assertThat(entry.info).isNull()
    }
    assertEntry(rootEntries, "charger") { entry: AdbFileListingEntry ->
      assertThat(entry.isDirectory).isFalse()
      assertThat(entry.isFile).isFalse()
      assertThat(entry.isSymbolicLink).isTrue()
      assertThat(entry.permissions).isEqualTo("lrwxrwxrwx")
      assertThat(entry.owner).isEqualTo("root")
      assertThat(entry.group).isEqualTo("root")
      assertThat(entry.date).isEqualTo("1969-12-31")
      assertThat(entry.time).isEqualTo("16:00")
      assertThat(entry.info).isEqualTo("-> /sbin/healthd")
    }
    assertEntry(rootEntries, "etc") { entry: AdbFileListingEntry ->
      assertThat(entry).isNotNull()
      assertThat(entry.isDirectory).isFalse()
      assertThat(entry.isFile).isFalse()
      assertThat(entry.isSymbolicLink).isTrue()
      assertThat(entry.permissions).isEqualTo("lrwxrwxrwx")
      assertThat(entry.owner).isEqualTo("root")
      assertThat(entry.group).isEqualTo("root")
      assertThat(entry.date).isEqualTo("1969-12-31")
      assertThat(entry.time).isEqualTo("16:00")
      assertThat(entry.info).isEqualTo("-> /system/etc")
    }
  }

  @Test
  fun whenLsEscapes(): Unit = runBlocking {
    TestDevices.addWhenLsEscapesCommands(commands)
    val listing = AdbFileListing(device, AdbDeviceCapabilities(scope, deviceName, device), dispatcher)
    val dir = AdbFileListingEntry(
      "/sdcard/dir",
      EntryKind.DIRECTORY,
      "drwxrwx--x",
      "root",
      "sdcard_rw",
      "2018-01-10",
      "12:56",
      "4096",
      null
    )
    assertThat(listing.getChildrenRunAs(dir, null)[0].name).isEqualTo("dir with spaces")
  }

  @Test
  fun whenLsDoesNotEscape(): Unit = runBlocking {
    TestDevices.addWhenLsDoesNotEscapeCommands(commands)
    val listing = AdbFileListing(device, AdbDeviceCapabilities(scope, deviceName, device), dispatcher)
    val dir = AdbFileListingEntry(
      "/sdcard/dir",
      EntryKind.DIRECTORY,
      "drwxrwx--x",
      "root",
      "sdcard_rw",
      "2018-01-10",
      "15:00",
      "4096",
      null
    )
    assertThat(listing.getChildrenRunAs(dir, null)[0].name).isEqualTo("dir with spaces")
  }

  @Test
  fun test_EmulatorApi25_IsDirectoryLink(): Unit = runBlocking {
    // Prepare
    TestDevices.EMULATOR_API25.addCommands(commands)
    val fileListing = AdbFileListing(device, AdbDeviceCapabilities(scope, deviceName, device), dispatcher)

    // Act
    val root = fileListing.root
    val rootEntries = fileListing.getChildren(root)

    // Assert
    assertThat(rootEntries).isNotNull()
    assertDirectoryLink(fileListing, rootEntries, "charger", false)
    assertDirectoryLink(fileListing, rootEntries, "d", true)
    assertDirectoryLink(fileListing, rootEntries, "etc", true)
    assertDirectoryLink(fileListing, rootEntries, "sdcard", true)
    assertDirectoryLink(fileListing, rootEntries, "system", false)
    assertDirectoryLink(fileListing, rootEntries, "vendor", true)
  }

  companion object {
    private suspend fun assertDirectoryLink(
      fileListing: AdbFileListing,
      entries: List<AdbFileListingEntry>,
      name: String,
      value: Boolean
    ) {
      val entry = checkNotNull(entries.find { it.name == name })
      assertThat(fileListing.isDirectoryLink(entry)).isEqualTo(value)
    }

    private fun assertEntry(
      entries: List<AdbFileListingEntry>,
      name: String,
      consumer: Consumer<AdbFileListingEntry>
    ) {
      val entry = checkNotNull(entries.find { it.name == name })
      consumer.accept(entry)
    }

    @SuppressWarnings("unused")
    @JvmStatic
    @Parameterized.Parameters(name="{0}")
    fun data(): Array<Any?> = arrayOf(DeviceInterfaceLibrary.DDMLIB, DeviceInterfaceLibrary.ADBLIB)
  }
}