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
package com.android.tools.idea.explorer.adbimpl;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.tools.idea.explorer.adbimpl.AdbFileListingEntry.EntryKind;
import com.google.common.util.concurrent.ListenableFuture;
import org.hamcrest.core.IsInstanceOf;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.PooledThreadExecutor;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.awt.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.android.tools.idea.explorer.adbimpl.TestDevices.COMMAND_ERROR_CHECK_SUFFIX;
import static com.google.common.truth.Truth.assertThat;

public class AdbFileListingTest {
  private static final long TIMEOUT_MILLISECONDS = 30_000;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @ClassRule
  public static DebugLoggerFactoryRule ourLoggerFactoryRule = new DebugLoggerFactoryRule();

  @Test
  public void test_Nexus7Api23_GetRoot() throws Exception {
    // Prepare
    TestShellCommands commands = new TestShellCommands();
    TestDevices.addNexus7Api23Commands(commands);
    IDevice device = commands.createMockDevice();
    Executor taskExecutor = PooledThreadExecutor.INSTANCE;
    AdbFileListing fileListing = new AdbFileListing(device, new AdbDeviceCapabilities(device), taskExecutor);

    // Act
    AdbFileListingEntry root = waitForFuture(fileListing.getRoot());

    // Assert
    assertThat(root).isNotNull();
    assertThat(root.getFullPath()).isEqualTo("/");
    assertThat(root.getName()).isEqualTo("");
    assertThat(root.isDirectory()).isTrue();
  }

  @Test
  public void test_Nexus7Api23__GetRootChildrenError() throws Exception {
    // Prepare
    TestShellCommands commands = new TestShellCommands();
    TestDevices.addNexus7Api23Commands(commands);
    commands.addError("ls -l /" + COMMAND_ERROR_CHECK_SUFFIX, new ShellCommandUnresponsiveException());
    IDevice device = commands.createMockDevice();
    Executor taskExecutor = PooledThreadExecutor.INSTANCE;
    AdbFileListing fileListing = new AdbFileListing(device, new AdbDeviceCapabilities(device), taskExecutor);

    // Act
    AdbFileListingEntry root = waitForFuture(fileListing.getRoot());

    // Assert
    thrown.expect(ExecutionException.class);
    thrown.expectCause(IsInstanceOf.instanceOf(ShellCommandUnresponsiveException.class));
    waitForFuture(fileListing.getChildren(root));
  }

  @Test
  public void test_Nexus7Api23_GetRootChildren() throws Exception {
    // Prepare
    TestShellCommands commands = new TestShellCommands();
    TestDevices.addNexus7Api23Commands(commands);
    IDevice device = commands.createMockDevice();
    Executor taskExecutor = PooledThreadExecutor.INSTANCE;
    AdbFileListing fileListing = new AdbFileListing(device, new AdbDeviceCapabilities(device), taskExecutor);

    // Act
    AdbFileListingEntry root = waitForFuture(fileListing.getRoot());
    List<AdbFileListingEntry> rootEntries = waitForFuture(fileListing.getChildren(root));

    // Assert
    assertThat(rootEntries).isNotNull();
    assertThat(rootEntries.stream().anyMatch(x -> "acct".equals(x.getName()))).isTrue();
    assertThat(rootEntries.stream().anyMatch(x -> "charger".equals(x.getName()))).isTrue();
    assertThat(rootEntries.stream().anyMatch(x -> "vendor".equals(x.getName()))).isTrue();
    assertThat(rootEntries.stream().anyMatch(x -> "init".equals(x.getName()))).isFalse();

    assertEntry(rootEntries, "acct", entry -> {
      assertThat(entry).isNotNull();
      assertThat(entry.isDirectory()).isTrue();
      assertThat(entry.isFile()).isFalse();
      assertThat(entry.isSymbolicLink()).isFalse();
      assertThat(entry.getPermissions()).isEqualTo("drwxr-xr-x");
      assertThat(entry.getOwner()).isEqualTo("root");
      assertThat(entry.getGroup()).isEqualTo("root");
      assertThat(entry.getDate()).isEqualTo("2016-11-21");
      assertThat(entry.getTime()).isEqualTo("12:09");
      assertThat(entry.getInfo()).isNull();
    });

    assertEntry(rootEntries, "cache", entry -> {
      assertThat(entry).isNotNull();
      assertThat(entry.isDirectory()).isTrue();
      assertThat(entry.isFile()).isFalse();
      assertThat(entry.isSymbolicLink()).isFalse();
      assertThat(entry.getPermissions()).isEqualTo("drwxrwx---");
      assertThat(entry.getOwner()).isEqualTo("system");
      assertThat(entry.getGroup()).isEqualTo("cache");
      assertThat(entry.getDate()).isEqualTo("2016-08-26");
      assertThat(entry.getTime()).isEqualTo("12:12");
      assertThat(entry.getInfo()).isNull();
    });

    assertEntry(rootEntries, "charger", entry -> {
      assertThat(entry).isNotNull();
      assertThat(entry.isDirectory()).isFalse();
      assertThat(entry.isFile()).isFalse();
      assertThat(entry.isSymbolicLink()).isTrue();
      assertThat(entry.getPermissions()).isEqualTo("lrwxrwxrwx");
      assertThat(entry.getOwner()).isEqualTo("root");
      assertThat(entry.getGroup()).isEqualTo("root");
      assertThat(entry.getDate()).isEqualTo("1969-12-31");
      assertThat(entry.getTime()).isEqualTo("16:00");
      assertThat(entry.getInfo()).isEqualTo("-> /sbin/healthd");
    });

    assertEntry(rootEntries, "etc", entry -> {
      assertThat(entry).isNotNull();
      assertThat(entry.isDirectory()).isFalse();
      assertThat(entry.isFile()).isFalse();
      assertThat(entry.isSymbolicLink()).isTrue();
      assertThat(entry.getPermissions()).isEqualTo("lrwxrwxrwx");
      assertThat(entry.getOwner()).isEqualTo("root");
      assertThat(entry.getGroup()).isEqualTo("root");
      assertThat(entry.getDate()).isEqualTo("2016-11-21");
      assertThat(entry.getTime()).isEqualTo("12:09");
      assertThat(entry.getInfo()).isEqualTo("-> /system/etc");
    });
  }

  @Test
  public void test_Nexus7Api23_IsDirectoryLink() throws Exception {
    // Prepare
    TestShellCommands commands = new TestShellCommands();
    TestDevices.addNexus7Api23Commands(commands);
    IDevice device = commands.createMockDevice();
    Executor taskExecutor = PooledThreadExecutor.INSTANCE;
    AdbFileListing fileListing = new AdbFileListing(device, new AdbDeviceCapabilities(device), taskExecutor);

    // Act
    AdbFileListingEntry root = waitForFuture(fileListing.getRoot());
    List<AdbFileListingEntry> rootEntries = waitForFuture(fileListing.getChildren(root));

    // Assert
    assertThat(rootEntries).isNotNull();
    assertDirectoryLink(fileListing, rootEntries, "charger", false);
    assertDirectoryLink(fileListing, rootEntries, "d", true);
    assertDirectoryLink(fileListing, rootEntries, "etc", true);
    assertDirectoryLink(fileListing, rootEntries, "sdcard", true);
    assertDirectoryLink(fileListing, rootEntries, "tombstones", false);
    assertDirectoryLink(fileListing, rootEntries, "vendor", true);
  }

  @Test
  public void test_EmulatorApi25_GetRoot() throws Exception {
    // Prepare
    TestShellCommands commands = new TestShellCommands();
    TestDevices.addNexus7Api23Commands(commands);
    IDevice device = commands.createMockDevice();
    Executor taskExecutor = PooledThreadExecutor.INSTANCE;
    AdbFileListing fileListing = new AdbFileListing(device, new AdbDeviceCapabilities(device), taskExecutor);

    // Act
    AdbFileListingEntry root = waitForFuture(fileListing.getRoot());

    // Assert
    assertThat(root).isNotNull();
    assertThat(root.getFullPath()).isEqualTo("/");
    assertThat(root.getName()).isEqualTo("");
    assertThat(root.isDirectory()).isTrue();
  }

  @Test
  public void test_EmulatorApi25_GetRootChildrenError() throws Exception {
    // Prepare
    TestShellCommands commands = new TestShellCommands();
    TestDevices.addEmulatorApi25Commands(commands);
    commands.addError("su 0 sh -c 'ls -l /'" + COMMAND_ERROR_CHECK_SUFFIX, new ShellCommandUnresponsiveException());
    IDevice device = commands.createMockDevice();
    Executor taskExecutor = PooledThreadExecutor.INSTANCE;
    AdbFileListing fileListing = new AdbFileListing(device, new AdbDeviceCapabilities(device), taskExecutor);

    // Act
    AdbFileListingEntry root = waitForFuture(fileListing.getRoot());

    // Assert
    thrown.expect(ExecutionException.class);
    thrown.expectCause(IsInstanceOf.instanceOf(ShellCommandUnresponsiveException.class));
    waitForFuture(fileListing.getChildren(root));
  }

  @Test
  public void test_EmulatorApi25_GetRootChildren() throws Exception {
    // Prepare
    TestShellCommands commands = new TestShellCommands();
    TestDevices.addEmulatorApi25Commands(commands);
    IDevice device = commands.createMockDevice();
    Executor taskExecutor = PooledThreadExecutor.INSTANCE;
    AdbFileListing fileListing = new AdbFileListing(device, new AdbDeviceCapabilities(device), taskExecutor);

    // Act
    AdbFileListingEntry root = waitForFuture(fileListing.getRoot());
    List<AdbFileListingEntry> rootEntries = waitForFuture(fileListing.getChildren(root));

    // Assert
    assertThat(rootEntries).isNotNull();
    assertThat(rootEntries.stream().anyMatch(x -> "acct".equals(x.getName()))).isTrue();
    assertThat(rootEntries.stream().anyMatch(x -> "charger".equals(x.getName()))).isTrue();
    assertThat(rootEntries.stream().anyMatch(x -> "vendor".equals(x.getName()))).isTrue();
    assertThat(rootEntries.stream().anyMatch(x -> "init".equals(x.getName()))).isTrue();

    assertEntry(rootEntries, "acct", entry -> {
      assertThat(entry).isNotNull();
      assertThat(entry.isDirectory()).isTrue();
      assertThat(entry.isFile()).isFalse();
      assertThat(entry.isSymbolicLink()).isFalse();
      assertThat(entry.getPermissions()).isEqualTo("drwxr-xr-x");
      assertThat(entry.getOwner()).isEqualTo("root");
      assertThat(entry.getGroup()).isEqualTo("root");
      assertThat(entry.getDate()).isEqualTo("2017-03-06");
      assertThat(entry.getTime()).isEqualTo("21:15");
      assertThat(entry.getInfo()).isNull();
    });

    assertEntry(rootEntries, "cache", entry -> {
      assertThat(entry).isNotNull();
      assertThat(entry.isDirectory()).isTrue();
      assertThat(entry.isFile()).isFalse();
      assertThat(entry.isSymbolicLink()).isFalse();
      assertThat(entry.getPermissions()).isEqualTo("drwxrwx---");
      assertThat(entry.getOwner()).isEqualTo("system");
      assertThat(entry.getGroup()).isEqualTo("cache");
      assertThat(entry.getDate()).isEqualTo("2016-12-10");
      assertThat(entry.getTime()).isEqualTo("21:19");
      assertThat(entry.getInfo()).isNull();
    });

    assertEntry(rootEntries, "charger", entry -> {
      assertThat(entry).isNotNull();
      assertThat(entry.isDirectory()).isFalse();
      assertThat(entry.isFile()).isFalse();
      assertThat(entry.isSymbolicLink()).isTrue();
      assertThat(entry.getPermissions()).isEqualTo("lrwxrwxrwx");
      assertThat(entry.getOwner()).isEqualTo("root");
      assertThat(entry.getGroup()).isEqualTo("root");
      assertThat(entry.getDate()).isEqualTo("1969-12-31");
      assertThat(entry.getTime()).isEqualTo("16:00");
      assertThat(entry.getInfo()).isEqualTo("-> /sbin/healthd");
    });

    assertEntry(rootEntries, "etc", entry -> {
      assertThat(entry).isNotNull();
      assertThat(entry.isDirectory()).isFalse();
      assertThat(entry.isFile()).isFalse();
      assertThat(entry.isSymbolicLink()).isTrue();
      assertThat(entry.getPermissions()).isEqualTo("lrwxrwxrwx");
      assertThat(entry.getOwner()).isEqualTo("root");
      assertThat(entry.getGroup()).isEqualTo("root");
      assertThat(entry.getDate()).isEqualTo("1969-12-31");
      assertThat(entry.getTime()).isEqualTo("16:00");
      assertThat(entry.getInfo()).isEqualTo("-> /system/etc");
    });
  }

  @Test
  public void whenLsEscapes() throws Exception {
    TestShellCommands commands = new TestShellCommands();
    TestDevices.addWhenLsEscapesCommands(commands);

    IDevice device = commands.createMockDevice();
    AdbFileListing listing = new AdbFileListing(device, new AdbDeviceCapabilities(device), PooledThreadExecutor.INSTANCE);

    AdbFileListingEntry dir = new AdbFileListingEntry(
      "/sdcard/dir",
      EntryKind.DIRECTORY,
      "drwxrwx--x",
      "root",
      "sdcard_rw",
      "2018-01-10",
      "12:56",
      "4096",
      null);

    assertThat(waitForFuture(listing.getChildrenRunAs(dir, null)).get(0).getName()).isEqualTo("dir with spaces");
  }

  @Test
  public void whenLsDoesNotEscape() throws Exception {
    TestShellCommands commands = new TestShellCommands();
    TestDevices.addWhenLsDoesNotEscapeCommands(commands);

    IDevice device = commands.createMockDevice();
    AdbFileListing listing = new AdbFileListing(device, new AdbDeviceCapabilities(device), PooledThreadExecutor.INSTANCE);

    AdbFileListingEntry dir = new AdbFileListingEntry(
      "/sdcard/dir",
      EntryKind.DIRECTORY,
      "drwxrwx--x",
      "root",
      "sdcard_rw",
      "2018-01-10",
      "15:00",
      "4096",
      null);

    assertThat(waitForFuture(listing.getChildrenRunAs(dir, null)).get(0).getName()).isEqualTo("dir with spaces");
  }

  @Test
  public void test_EmulatorApi25_IsDirectoryLink() throws Exception {
    // Prepare
    TestShellCommands commands = new TestShellCommands();
    TestDevices.addEmulatorApi25Commands(commands);
    IDevice device = commands.createMockDevice();
    Executor taskExecutor = PooledThreadExecutor.INSTANCE;
    AdbFileListing fileListing = new AdbFileListing(device, new AdbDeviceCapabilities(device), taskExecutor);

    // Act
    AdbFileListingEntry root = waitForFuture(fileListing.getRoot());
    List<AdbFileListingEntry> rootEntries = waitForFuture(fileListing.getChildren(root));

    // Assert
    assertThat(rootEntries).isNotNull();
    assertDirectoryLink(fileListing, rootEntries, "charger", false);
    assertDirectoryLink(fileListing, rootEntries, "d", true);
    assertDirectoryLink(fileListing, rootEntries, "etc", true);
    assertDirectoryLink(fileListing, rootEntries, "sdcard", true);
    assertDirectoryLink(fileListing, rootEntries, "system", false);
    assertDirectoryLink(fileListing, rootEntries, "vendor", true);
  }

  private static void assertDirectoryLink(@NotNull AdbFileListing fileListing,
                                          @NotNull List<AdbFileListingEntry> entries,
                                          @NotNull String name,
                                          boolean value) throws Exception {
    AdbFileListingEntry entry = entries.stream().filter(x -> name.equals(x.getName())).findFirst().orElse(null);
    assertThat(entry).isNotNull();
    assertThat(waitForFuture(fileListing.isDirectoryLink(entry))).isEqualTo(value);
  }

  private static void assertEntry(@NotNull List<AdbFileListingEntry> entries,
                                  @NotNull String name,
                                  @NotNull Consumer<AdbFileListingEntry> consumer) {
    AdbFileListingEntry entry = entries.stream().filter(x -> name.equals(x.getName())).findFirst().orElse(null);
    assertThat(entry).isNotNull();
    consumer.accept(entry);
  }

  private static <V> V waitForFuture(@NotNull ListenableFuture<V> future) throws Exception {
    assert !EventQueue.isDispatchThread();
    return future.get(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS);
  }
}