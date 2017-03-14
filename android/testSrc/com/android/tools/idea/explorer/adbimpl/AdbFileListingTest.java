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

import static com.google.common.truth.Truth.assertThat;

public class AdbFileListingTest {
  @NotNull private static final String ERROR_LINE_MARKER = "ERR-ERR-ERR-ERR";
  @NotNull private static final String COMMAND_ERROR_CHECK_SUFFIX = " || echo " + ERROR_LINE_MARKER;
  private static final long TIMEOUT_MILLISECONDS = 30_000;

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @ClassRule
  public static DebugLoggerFactoryRule ourLoggerFactoryRule = new DebugLoggerFactoryRule();

  @Test
  public void test_Nexus7Api23_GetRoot() throws Exception {
    // Prepare
    TestShellCommands commands = new TestShellCommands();
    addNexus7Api23Commands(commands);
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
    addNexus7Api23Commands(commands);
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
    addNexus7Api23Commands(commands);
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
    addNexus7Api23Commands(commands);
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
    addNexus7Api23Commands(commands);
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
    addEmulatorApi25Commands(commands);
    commands.addError("su 0 ls -l /" + COMMAND_ERROR_CHECK_SUFFIX, new ShellCommandUnresponsiveException());
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
    addEmulatorApi25Commands(commands);
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
  public void test_EmulatorApi25_IsDirectoryLink() throws Exception {
    // Prepare
    TestShellCommands commands = new TestShellCommands();
    addEmulatorApi25Commands(commands);
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

  private static void addNexus7Api23Commands(@NotNull TestShellCommands shellCommands) {
    shellCommands.setDescription("Nexus 7, Android 6.0.1, API 23");
    addFailedCommand(shellCommands, "su 0 id", "/system/bin/sh: su: not found\n");
    addCommand(shellCommands, "ls -l /", "drwxr-xr-x root     root              2016-11-21 12:09 acct\r\n" +
                                         "drwxrwx--- system   cache             2016-08-26 12:12 cache\r\n" +
                                         "lrwxrwxrwx root     root              1969-12-31 16:00 charger -> /sbin/healthd\r\n" +
                                         "dr-x------ root     root              2016-11-21 12:09 config\r\n" +
                                         "lrwxrwxrwx root     root              2016-11-21 12:09 d -> /sys/kernel/debug\r\n" +
                                         "drwxrwx--x system   system            2016-11-21 12:10 data\r\n" +
                                         "-rw-r--r-- root     root          564 1969-12-31 16:00 default.prop\r\n" +
                                         "drwxr-xr-x root     root              2016-11-21 14:04 dev\r\n" +
                                         "lrwxrwxrwx root     root              2016-11-21 12:09 etc -> /system/etc\r\n" +
                                         "-rw-r--r-- root     root        21429 1969-12-31 16:00 file_contexts\r\n" +
                                         "drwxrwx--x system   system            2016-11-21 12:09 firmware\r\n" +
                                         "-rw-r----- root     root         3447 1969-12-31 16:00 fstab.flo\r\n" +
                                         "lstat '//init' failed: Permission denied\r\n" +
                                         "-rwxr-x--- root     root          852 1969-12-31 16:00 init.environ.rc\r\n" +
                                         "-rwxr-x--- root     root           79 1969-12-31 16:00 init.flo.diag.rc\r\n" +
                                         "-rwxr-x--- root     root        15962 1969-12-31 16:00 init.flo.rc\r\n" +
                                         "-rwxr-x--- root     root         8086 1969-12-31 16:00 init.flo.usb.rc\r\n" +
                                         "-rwxr-x--- root     root        26830 1969-12-31 16:00 init.rc\r\n" +
                                         "-rwxr-x--- root     root         1921 1969-12-31 16:00 init.trace.rc\r\n" +
                                         "-rwxr-x--- root     root         9283 1969-12-31 16:00 init.usb.configfs.rc\r\n" +
                                         "-rwxr-x--- root     root         5339 1969-12-31 16:00 init.usb.rc\r\n" +
                                         "-rwxr-x--- root     root          342 1969-12-31 16:00 init.zygote32.rc\r\n" +
                                         "drwxr-xr-x root     system            2016-11-21 12:09 mnt\r\n" +
                                         "drwxr-xr-x root     root              1969-12-31 16:00 oem\r\n" +
                                         "lstat '//persist' failed: Permission denied\r\n" +
                                         "dr-xr-xr-x root     root              1969-12-31 16:00 proc\r\n" +
                                         "-rw-r--r-- root     root         3405 1969-12-31 16:00 property_contexts\r\n" +
                                         "drwxr-xr-x root     root              1969-12-31 16:00 res\r\n" +
                                         "drwx------ root     root              2016-07-01 17:00 root\r\n" +
                                         "drwxr-x--- root     root              1969-12-31 16:00 sbin\r\n" +
                                         "lrwxrwxrwx root     root              2016-11-21 12:09 sdcard -> /storage/self/primary\r\n" +
                                         "-rw-r--r-- root     root          596 1969-12-31 16:00 seapp_contexts\r\n" +
                                         "-rw-r--r-- root     root           51 1969-12-31 16:00 selinux_version\r\n" +
                                         "-rw-r--r-- root     root       149405 1969-12-31 16:00 sepolicy\r\n" +
                                         "-rw-r--r-- root     root         9769 1969-12-31 16:00 service_contexts\r\n" +
                                         "drwxr-xr-x root     root              2016-11-21 12:10 storage\r\n" +
                                         "dr-xr-xr-x root     root              2016-11-21 12:09 sys\r\n" +
                                         "drwxr-xr-x root     root              2016-08-26 12:02 system\r\n" +
                                         "lrwxrwxrwx root     root              2016-11-21 12:09 tombstones -> /data/tombstones\r\n" +
                                         "-rw-r--r-- root     root         2195 1969-12-31 16:00 ueventd.flo.rc\r\n" +
                                         "-rw-r--r-- root     root         4587 1969-12-31 16:00 ueventd.rc\r\n" +
                                         "lrwxrwxrwx root     root              2016-11-21 12:09 vendor -> /system/vendor\r\n");

    shellCommands.add("ls -l -d /charger/", "/charger/: Permission denied\r\n");
    shellCommands.add("ls -l -d /d/", "drwxr-xr-x root     root              1969-12-31 16:00\r\n");
    shellCommands.add("ls -l -d /etc/", "drwxr-xr-x root     root              2016-08-26 12:00\r\n");
    shellCommands.add("ls -l -d /sdcard/", "drwxrwx--x root     sdcard_rw          2014-02-10 17:16\r\n");
    shellCommands.add("ls -l -d /tombstones/", "/tombstones/: Permission denied\r\n");
    shellCommands.add("ls -l -d /vendor/", "drwxr-xr-x root     shell             2013-06-15 12:54\r\n");
  }

  private static void addEmulatorApi25Commands(@NotNull TestShellCommands shellCommands) {
    shellCommands.setDescription("Emulator Pixel, Android 7.1, API 25");
    addCommand(shellCommands, "su 0 id",
               "uid=0(root) gid=0(root) groups=0(root),1004(input),1007(log),1011(adb),1015(sdcard_rw),1028(sdcard_r)," +
               "3001(net_bt_admin),3002(net_bt),3003(inet),3006(net_bw_stats),3009(readproc) context=u:r:su:s0\n");
    addCommand(shellCommands, "su 0 ls -l /", "total 3688\n" +
                                              "drwxr-xr-x  29 root   root         0 2017-03-06 21:15 acct\n" +
                                              "drwxrwx---   6 system cache     4096 2016-12-10 21:19 cache\n" +
                                              "lrwxrwxrwx   1 root   root        13 1969-12-31 16:00 charger -> /sbin/healthd\n" +
                                              "drwxr-xr-x   3 root   root         0 2017-03-06 21:15 config\n" +
                                              "lrwxrwxrwx   1 root   root        17 1969-12-31 16:00 d -> /sys/kernel/debug\n" +
                                              "drwxrwx--x  36 system system    4096 2017-03-06 21:15 data\n" +
                                              "-rw-r--r--   1 root   root       928 1969-12-31 16:00 default.prop\n" +
                                              "drwxr-xr-x  14 root   root      3000 2017-03-06 21:15 dev\n" +
                                              "lrwxrwxrwx   1 root   root        11 1969-12-31 16:00 etc -> /system/etc\n" +
                                              "-rw-r--r--   1 root   root     76613 1969-12-31 16:00 file_contexts.bin\n" +
                                              "-rw-r-----   1 root   root       943 1969-12-31 16:00 fstab.goldfish\n" +
                                              "-rw-r-----   1 root   root       968 1969-12-31 16:00 fstab.ranchu\n" +
                                              "-rwxr-x---   1 root   root   1486420 1969-12-31 16:00 init\n" +
                                              "-rwxr-x---   1 root   root       887 1969-12-31 16:00 init.environ.rc\n" +
                                              "-rwxr-x---   1 root   root      2924 1969-12-31 16:00 init.goldfish.rc\n" +
                                              "-rwxr-x---   1 root   root      2368 1969-12-31 16:00 init.ranchu.rc\n" +
                                              "-rwxr-x---   1 root   root     25583 1969-12-31 16:00 init.rc\n" +
                                              "-rwxr-x---   1 root   root      9283 1969-12-31 16:00 init.usb.configfs.rc\n" +
                                              "-rwxr-x---   1 root   root      5715 1969-12-31 16:00 init.usb.rc\n" +
                                              "-rwxr-x---   1 root   root       411 1969-12-31 16:00 init.zygote32.rc\n" +
                                              "drwxr-xr-x  10 root   system     220 2017-03-06 21:15 mnt\n" +
                                              "drwxr-xr-x   2 root   root         0 1969-12-31 16:00 oem\n" +
                                              "dr-xr-xr-x 189 root   root         0 2017-03-06 21:15 proc\n" +
                                              "-rw-r--r--   1 root   root      4757 1969-12-31 16:00 property_contexts\n" +
                                              "drwx------   2 root   root         0 2016-10-04 07:46 root\n" +
                                              "drwxr-x---   2 root   root         0 1969-12-31 16:00 sbin\n" +
                                              "lrwxrwxrwx   1 root   root        21 1969-12-31 16:00 sdcard -> /storage/self/primary\n" +
                                              "-rw-r--r--   1 root   root       758 1969-12-31 16:00 seapp_contexts\n" +
                                              "-rw-r--r--   1 root   root        79 1969-12-31 16:00 selinux_version\n" +
                                              "-rw-r--r--   1 root   root    177921 1969-12-31 16:00 sepolicy\n" +
                                              "-rw-r--r--   1 root   root     11167 1969-12-31 16:00 service_contexts\n" +
                                              "drwxr-xr-x   5 root   root       100 2017-03-06 21:15 storage\n" +
                                              "dr-xr-xr-x  12 root   root         0 2017-03-06 21:15 sys\n" +
                                              "drwxr-xr-x  16 root   root      4096 1969-12-31 16:00 system\n" +
                                              "-rw-r--r--   1 root   root       323 1969-12-31 16:00 ueventd.goldfish.rc\n" +
                                              "-rw-r--r--   1 root   root       323 1969-12-31 16:00 ueventd.ranchu.rc\n" +
                                              "-rw-r--r--   1 root   root      4853 1969-12-31 16:00 ueventd.rc\n" +
                                              "lrwxrwxrwx   1 root   root        14 1969-12-31 16:00 vendor -> /system/vendor\n");

    shellCommands.add("su 0 ls -l -d /charger/", "ls: /charger/: Not a directory\n");
    shellCommands.add("su 0 ls -l -d /d/", "drwx------ 14 root root 0 2017-03-06 21:15 /d/\n");
    shellCommands.add("su 0 ls -l -d /etc/", "drwxr-xr-x 7 root root 4096 2016-11-14 14:08 /etc/\n");
    shellCommands.add("su 0 ls -l -d /sdcard/", "drwxrwx--x 13 root sdcard_rw 4096 2017-03-06 23:30 /sdcard/\n");
    shellCommands.add("su 0 ls -l -d /tombstones/", "ls: /tombstones/: No such file or directory\n");
    shellCommands.add("su 0 ls -l -d /system/", "drwxr-xr-x 16 root root 4096 1969-12-31 16:00 /system/\n");
    shellCommands.add("su 0 ls -l -d /vendor/", "drwxr-xr-x 3 root shell 4096 2016-11-14 14:01 /vendor/\n");
  }

  @SuppressWarnings("SameParameterValue")
  private static void addCommand(@NotNull TestShellCommands commands, @NotNull String command, @NotNull String result) {
    commands.add(command + COMMAND_ERROR_CHECK_SUFFIX, result);
  }

  @SuppressWarnings("SameParameterValue")
  private static void addFailedCommand(@NotNull TestShellCommands commands, @NotNull String command, @NotNull String result) {
    addCommand(commands, command, result + ERROR_LINE_MARKER + "\n");
  }

  private static <V> V waitForFuture(@NotNull ListenableFuture<V> future) throws Exception {
    assert !EventQueue.isDispatchThread();
    return future.get(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS);
  }
}