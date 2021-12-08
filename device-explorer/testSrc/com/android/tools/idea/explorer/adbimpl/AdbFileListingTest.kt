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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import java.awt.EventQueue
import java.lang.Exception
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

class AdbFileListingTest {
  @Rule
  var thrown = ExpectedException.none()
  @Test
  @Throws(Exception::class)
  fun test_Nexus7Api23_GetRoot() {
    // Prepare
    val commands = TestShellCommands()
    TestDevices.addNexus7Api23Commands(commands)
    val device = commands.createMockDevice()
    val taskExecutor: Executor = PooledThreadExecutor.INSTANCE
    val fileListing = AdbFileListing(device, AdbDeviceCapabilities(device), taskExecutor)

    // Act
    val root = waitForFuture(fileListing.root)

    // Assert
    Truth.assertThat(root).isNotNull()
    Truth.assertThat(root.fullPath).isEqualTo("/")
    Truth.assertThat(root.name).isEqualTo("")
    Truth.assertThat(root.isDirectory).isTrue()
  }

  @Test
  @Throws(Exception::class)
  fun test_Nexus7Api23__GetRootChildrenError() {
    // Prepare
    val commands = TestShellCommands()
    TestDevices.addNexus7Api23Commands(commands)
    commands.addError("ls -al /" + TestDevices.COMMAND_ERROR_CHECK_SUFFIX, ShellCommandUnresponsiveException())
    val device = commands.createMockDevice()
    val taskExecutor: Executor = PooledThreadExecutor.INSTANCE
    val fileListing = AdbFileListing(device, AdbDeviceCapabilities(device), taskExecutor)

    // Act
    val root = waitForFuture(fileListing.root)

    // Assert
    thrown.expect(ExecutionException::class.java)
    thrown.expectCause(IsInstanceOf.instanceOf(ShellCommandUnresponsiveException::class.java))
    waitForFuture(fileListing.getChildren(root))
  }

  @Test
  @Throws(Exception::class)
  fun test_Nexus7Api23_GetRootChildren() {
    // Prepare
    val commands = TestShellCommands()
    TestDevices.addNexus7Api23Commands(commands)
    val device = commands.createMockDevice()
    val taskExecutor: Executor = PooledThreadExecutor.INSTANCE
    val fileListing = AdbFileListing(device, AdbDeviceCapabilities(device), taskExecutor)

    // Act
    val root = waitForFuture(fileListing.root)
    val rootEntries = waitForFuture<List<AdbFileListingEntry?>>(fileListing.getChildren(root))

    // Assert
    Truth.assertThat(rootEntries).isNotNull()
    Truth.assertThat(rootEntries.stream().anyMatch { x: AdbFileListingEntry? -> "acct" == x!!.name }).isTrue()
    Truth.assertThat(rootEntries.stream().anyMatch { x: AdbFileListingEntry? -> "charger" == x!!.name }).isTrue()
    Truth.assertThat(rootEntries.stream().anyMatch { x: AdbFileListingEntry? -> "vendor" == x!!.name }).isTrue()
    Truth.assertThat(rootEntries.stream().anyMatch { x: AdbFileListingEntry? -> "init" == x!!.name }).isFalse()
    assertEntry(rootEntries, "acct") { entry: AdbFileListingEntry? ->
      Truth.assertThat(entry).isNotNull()
      Truth.assertThat(entry!!.isDirectory).isTrue()
      Truth.assertThat(entry.isFile).isFalse()
      Truth.assertThat(entry.isSymbolicLink).isFalse()
      Truth.assertThat(entry.permissions).isEqualTo("drwxr-xr-x")
      Truth.assertThat(entry.owner).isEqualTo("root")
      Truth.assertThat(entry.group).isEqualTo("root")
      Truth.assertThat(entry.date).isEqualTo("2016-11-21")
      Truth.assertThat(entry.time).isEqualTo("12:09")
      Truth.assertThat(entry.info).isNull()
    }
    assertEntry(rootEntries, "cache") { entry: AdbFileListingEntry? ->
      Truth.assertThat(entry).isNotNull()
      Truth.assertThat(entry!!.isDirectory).isTrue()
      Truth.assertThat(entry.isFile).isFalse()
      Truth.assertThat(entry.isSymbolicLink).isFalse()
      Truth.assertThat(entry.permissions).isEqualTo("drwxrwx---")
      Truth.assertThat(entry.owner).isEqualTo("system")
      Truth.assertThat(entry.group).isEqualTo("cache")
      Truth.assertThat(entry.date).isEqualTo("2016-08-26")
      Truth.assertThat(entry.time).isEqualTo("12:12")
      Truth.assertThat(entry.info).isNull()
    }
    assertEntry(rootEntries, "charger") { entry: AdbFileListingEntry? ->
      Truth.assertThat(entry).isNotNull()
      Truth.assertThat(entry!!.isDirectory).isFalse()
      Truth.assertThat(entry.isFile).isFalse()
      Truth.assertThat(entry.isSymbolicLink).isTrue()
      Truth.assertThat(entry.permissions).isEqualTo("lrwxrwxrwx")
      Truth.assertThat(entry.owner).isEqualTo("root")
      Truth.assertThat(entry.group).isEqualTo("root")
      Truth.assertThat(entry.date).isEqualTo("1969-12-31")
      Truth.assertThat(entry.time).isEqualTo("16:00")
      Truth.assertThat(entry.info).isEqualTo("-> /sbin/healthd")
    }
    assertEntry(rootEntries, "etc") { entry: AdbFileListingEntry? ->
      Truth.assertThat(entry).isNotNull()
      Truth.assertThat(entry!!.isDirectory).isFalse()
      Truth.assertThat(entry.isFile).isFalse()
      Truth.assertThat(entry.isSymbolicLink).isTrue()
      Truth.assertThat(entry.permissions).isEqualTo("lrwxrwxrwx")
      Truth.assertThat(entry.owner).isEqualTo("root")
      Truth.assertThat(entry.group).isEqualTo("root")
      Truth.assertThat(entry.date).isEqualTo("2016-11-21")
      Truth.assertThat(entry.time).isEqualTo("12:09")
      Truth.assertThat(entry.info).isEqualTo("-> /system/etc")
    }
  }

  @Test
  @Throws(Exception::class)
  fun test_Nexus7Api23_IsDirectoryLink() {
    // Prepare
    val commands = TestShellCommands()
    TestDevices.addNexus7Api23Commands(commands)
    val device = commands.createMockDevice()
    val taskExecutor: Executor = PooledThreadExecutor.INSTANCE
    val fileListing = AdbFileListing(device, AdbDeviceCapabilities(device), taskExecutor)

    // Act
    val root = waitForFuture(fileListing.root)
    val rootEntries = waitForFuture<List<AdbFileListingEntry?>>(fileListing.getChildren(root))

    // Assert
    Truth.assertThat(rootEntries).isNotNull()
    assertDirectoryLink(fileListing, rootEntries, "charger", false)
    assertDirectoryLink(fileListing, rootEntries, "d", true)
    assertDirectoryLink(fileListing, rootEntries, "etc", true)
    assertDirectoryLink(fileListing, rootEntries, "sdcard", true)
    assertDirectoryLink(fileListing, rootEntries, "tombstones", false)
    assertDirectoryLink(fileListing, rootEntries, "vendor", true)
  }

  @Test
  @Throws(Exception::class)
  fun test_EmulatorApi25_GetRoot() {
    // Prepare
    val commands = TestShellCommands()
    TestDevices.addNexus7Api23Commands(commands)
    val device = commands.createMockDevice()
    val taskExecutor: Executor = PooledThreadExecutor.INSTANCE
    val fileListing = AdbFileListing(device, AdbDeviceCapabilities(device), taskExecutor)

    // Act
    val root = waitForFuture(fileListing.root)

    // Assert
    Truth.assertThat(root).isNotNull()
    Truth.assertThat(root.fullPath).isEqualTo("/")
    Truth.assertThat(root.name).isEqualTo("")
    Truth.assertThat(root.isDirectory).isTrue()
  }

  @Test
  @Throws(Exception::class)
  fun test_EmulatorApi25_GetRootChildrenError() {
    // Prepare
    val commands = TestShellCommands()
    TestDevices.addEmulatorApi25Commands(commands)
    commands.addError("su 0 sh -c 'ls -al /'" + TestDevices.COMMAND_ERROR_CHECK_SUFFIX, ShellCommandUnresponsiveException())
    val device = commands.createMockDevice()
    val taskExecutor: Executor = PooledThreadExecutor.INSTANCE
    val fileListing = AdbFileListing(device, AdbDeviceCapabilities(device), taskExecutor)

    // Act
    val root = waitForFuture(fileListing.root)

    // Assert
    thrown.expect(ExecutionException::class.java)
    thrown.expectCause(IsInstanceOf.instanceOf(ShellCommandUnresponsiveException::class.java))
    waitForFuture(fileListing.getChildren(root))
  }

  @Test
  @Throws(Exception::class)
  fun test_EmulatorApi25_GetRootChildren() {
    // Prepare
    val commands = TestShellCommands()
    TestDevices.addEmulatorApi25Commands(commands)
    val device = commands.createMockDevice()
    val taskExecutor: Executor = PooledThreadExecutor.INSTANCE
    val fileListing = AdbFileListing(device, AdbDeviceCapabilities(device), taskExecutor)

    // Act
    val root = waitForFuture(fileListing.root)
    val rootEntries = waitForFuture<List<AdbFileListingEntry?>>(fileListing.getChildren(root))

    // Assert
    Truth.assertThat(rootEntries).isNotNull()
    Truth.assertThat(rootEntries.stream().anyMatch { x: AdbFileListingEntry? -> "acct" == x!!.name }).isTrue()
    Truth.assertThat(rootEntries.stream().anyMatch { x: AdbFileListingEntry? -> "charger" == x!!.name }).isTrue()
    Truth.assertThat(rootEntries.stream().anyMatch { x: AdbFileListingEntry? -> "vendor" == x!!.name }).isTrue()
    Truth.assertThat(rootEntries.stream().anyMatch { x: AdbFileListingEntry? -> "init" == x!!.name }).isTrue()
    assertEntry(rootEntries, "acct") { entry: AdbFileListingEntry? ->
      Truth.assertThat(entry).isNotNull()
      Truth.assertThat(entry!!.isDirectory).isTrue()
      Truth.assertThat(entry.isFile).isFalse()
      Truth.assertThat(entry.isSymbolicLink).isFalse()
      Truth.assertThat(entry.permissions).isEqualTo("drwxr-xr-x")
      Truth.assertThat(entry.owner).isEqualTo("root")
      Truth.assertThat(entry.group).isEqualTo("root")
      Truth.assertThat(entry.date).isEqualTo("2017-03-06")
      Truth.assertThat(entry.time).isEqualTo("21:15")
      Truth.assertThat(entry.info).isNull()
    }
    assertEntry(rootEntries, "cache") { entry: AdbFileListingEntry? ->
      Truth.assertThat(entry).isNotNull()
      Truth.assertThat(entry!!.isDirectory).isTrue()
      Truth.assertThat(entry.isFile).isFalse()
      Truth.assertThat(entry.isSymbolicLink).isFalse()
      Truth.assertThat(entry.permissions).isEqualTo("drwxrwx---")
      Truth.assertThat(entry.owner).isEqualTo("system")
      Truth.assertThat(entry.group).isEqualTo("cache")
      Truth.assertThat(entry.date).isEqualTo("2016-12-10")
      Truth.assertThat(entry.time).isEqualTo("21:19")
      Truth.assertThat(entry.info).isNull()
    }
    assertEntry(rootEntries, "charger") { entry: AdbFileListingEntry? ->
      Truth.assertThat(entry).isNotNull()
      Truth.assertThat(entry!!.isDirectory).isFalse()
      Truth.assertThat(entry.isFile).isFalse()
      Truth.assertThat(entry.isSymbolicLink).isTrue()
      Truth.assertThat(entry.permissions).isEqualTo("lrwxrwxrwx")
      Truth.assertThat(entry.owner).isEqualTo("root")
      Truth.assertThat(entry.group).isEqualTo("root")
      Truth.assertThat(entry.date).isEqualTo("1969-12-31")
      Truth.assertThat(entry.time).isEqualTo("16:00")
      Truth.assertThat(entry.info).isEqualTo("-> /sbin/healthd")
    }
    assertEntry(rootEntries, "etc") { entry: AdbFileListingEntry? ->
      Truth.assertThat(entry).isNotNull()
      Truth.assertThat(entry!!.isDirectory).isFalse()
      Truth.assertThat(entry.isFile).isFalse()
      Truth.assertThat(entry.isSymbolicLink).isTrue()
      Truth.assertThat(entry.permissions).isEqualTo("lrwxrwxrwx")
      Truth.assertThat(entry.owner).isEqualTo("root")
      Truth.assertThat(entry.group).isEqualTo("root")
      Truth.assertThat(entry.date).isEqualTo("1969-12-31")
      Truth.assertThat(entry.time).isEqualTo("16:00")
      Truth.assertThat(entry.info).isEqualTo("-> /system/etc")
    }
  }

  @Test
  @Throws(Exception::class)
  fun whenLsEscapes() {
    val commands = TestShellCommands()
    TestDevices.addWhenLsEscapesCommands(commands)
    val device = commands.createMockDevice()
    val listing = AdbFileListing(device, AdbDeviceCapabilities(device), PooledThreadExecutor.INSTANCE)
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
    Truth.assertThat(waitForFuture(listing.getChildrenRunAs(dir, null))[0].name).isEqualTo("dir with spaces")
  }

  @Test
  @Throws(Exception::class)
  fun whenLsDoesNotEscape() {
    val commands = TestShellCommands()
    TestDevices.addWhenLsDoesNotEscapeCommands(commands)
    val device = commands.createMockDevice()
    val listing = AdbFileListing(device, AdbDeviceCapabilities(device), PooledThreadExecutor.INSTANCE)
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
    Truth.assertThat(waitForFuture(listing.getChildrenRunAs(dir, null))[0].name).isEqualTo("dir with spaces")
  }

  @Test
  @Throws(Exception::class)
  fun test_EmulatorApi25_IsDirectoryLink() {
    // Prepare
    val commands = TestShellCommands()
    TestDevices.addEmulatorApi25Commands(commands)
    val device = commands.createMockDevice()
    val taskExecutor: Executor = PooledThreadExecutor.INSTANCE
    val fileListing = AdbFileListing(device, AdbDeviceCapabilities(device), taskExecutor)

    // Act
    val root = waitForFuture(fileListing.root)
    val rootEntries = waitForFuture<List<AdbFileListingEntry?>>(fileListing.getChildren(root))

    // Assert
    Truth.assertThat(rootEntries).isNotNull()
    assertDirectoryLink(fileListing, rootEntries, "charger", false)
    assertDirectoryLink(fileListing, rootEntries, "d", true)
    assertDirectoryLink(fileListing, rootEntries, "etc", true)
    assertDirectoryLink(fileListing, rootEntries, "sdcard", true)
    assertDirectoryLink(fileListing, rootEntries, "system", false)
    assertDirectoryLink(fileListing, rootEntries, "vendor", true)
  }

  companion object {
    private const val TIMEOUT_MILLISECONDS: Long = 30000
    @Throws(Exception::class)
    private fun assertDirectoryLink(
      fileListing: AdbFileListing,
      entries: List<AdbFileListingEntry?>,
      name: String,
      value: Boolean
    ) {
      val entry = entries.stream().filter { x: AdbFileListingEntry? -> name == x!!.name }.findFirst().orElse(null)
      Truth.assertThat(entry).isNotNull()
      Truth.assertThat(waitForFuture(fileListing.isDirectoryLink(entry!!))).isEqualTo(value)
    }

    private fun assertEntry(
      entries: List<AdbFileListingEntry?>,
      name: String,
      consumer: Consumer<AdbFileListingEntry?>
    ) {
      val entry = entries.stream().filter { x: AdbFileListingEntry? -> name == x!!.name }.findFirst().orElse(null)
      Truth.assertThat(entry).isNotNull()
      consumer.accept(entry)
    }

    @Throws(Exception::class)
    private fun <V> waitForFuture(future: ListenableFuture<V>): V {
      assert(!EventQueue.isDispatchThread())
      return future[TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS]
    }
  }
}