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
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.diagnostic.Logger;
import org.hamcrest.core.IsInstanceOf;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.PooledThreadExecutor;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.awt.*;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

public class AdbFileListingTest {
  private static final Logger LOGGER = Logger.getInstance(AdbFileListingTest.class);
  private static final long TIMEOUT_MILLISECONDS = 30_000;

  @Rule
  public ExpectedException thrown= ExpectedException.none();

  @ClassRule
  public static DebugLoggerFactoryRule ourLoggerFactoryRule = new DebugLoggerFactoryRule();

  @Test
  public void testGetRoot() throws Exception {
    // Prepare
    IDevice device = mock(IDevice.class);
    Executor taskExecutor = PooledThreadExecutor.INSTANCE;
    AdbFileListing fileListing = new AdbFileListing(device, taskExecutor);

    // Act
    AdbFileListingEntry root = waitForFuture(fileListing.getRoot());

    // Assert
    assertThat(root).isNotNull();
    assertThat(root.getFullPath()).isEqualTo("/");
    assertThat(root.getName()).isEqualTo("");
    assertThat(root.isDirectory()).isTrue();
  }

  @Test
  public void testGetRootChildren() throws Exception {
    // Prepare
    IDevice device = createMockNexus7Device();
    Executor taskExecutor = PooledThreadExecutor.INSTANCE;
    AdbFileListing fileListing = new AdbFileListing(device, taskExecutor);

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
  public void testGetRootChildrenError() throws Exception {
    // Prepare
    TestShellCommands commands = new TestShellCommands();
    commands.addError("ls -l /", new ShellCommandUnresponsiveException());
    IDevice device = commands.createMockDevice();
    Executor taskExecutor = PooledThreadExecutor.INSTANCE;
    AdbFileListing fileListing = new AdbFileListing(device, taskExecutor);

    // Act
    AdbFileListingEntry root = waitForFuture(fileListing.getRoot());

    // Assert
    thrown.expect(ExecutionException.class);
    thrown.expectCause(IsInstanceOf.instanceOf(ShellCommandUnresponsiveException.class));
    waitForFuture(fileListing.getChildren(root));
  }

  @Test
  public void testIsDirectoryLink() throws Exception {
    // Prepare
    IDevice device = createMockNexus7Device();
    Executor taskExecutor = PooledThreadExecutor.INSTANCE;
    AdbFileListing fileListing = new AdbFileListing(device, taskExecutor);

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

  private static IDevice createMockNexus7Device() throws Exception {
    TestShellCommands shellCommands = new TestShellCommands();
    addNexus7Commands(shellCommands);
    return shellCommands.createMockDevice();
  }

  private static void addNexus7Commands(@NotNull TestShellCommands shellCommands) {
    // These are results from a Nexus 7, Android 6.0.1, API 23
    shellCommands.add("ls -l /", "drwxr-xr-x root     root              2016-11-21 12:09 acct\r\n" +
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

  private static IDevice createMockDevice(TestShellCommands shellCommands) throws Exception {
    IDevice device = mock(IDevice.class);

    // Implement the executeShellCommand method
    doAnswer(invocation -> {
      String command = invocation.getArgument(0);
      IShellOutputReceiver receiver = invocation.getArgument(1);

      TestShellCommandResult commandResult = shellCommands.get(command);
      if (commandResult == null) {
        UnsupportedOperationException error = new UnsupportedOperationException(
          String.format("Command \"%s\" not found in mock device. Test case is not setup correctly", command));
        LOGGER.error(error);
        throw error;
      }

      if (commandResult.getError() != null) {
        throw commandResult.getError();
      }

      if (commandResult.getOutput() == null) {
        UnsupportedOperationException error = new UnsupportedOperationException(
          String.format("Command \"%s\" has no result. Test case is not setup correctly", command));
        LOGGER.error(error);
        throw error;
      }

      byte[] bytes = commandResult.getOutput().getBytes(Charset.forName("UTF-8"));
      int chunkSize = 100;
      for (int i = 0; i < bytes.length; i += chunkSize) {
        int count = Math.min(chunkSize, bytes.length - i);
        receiver.addOutput(bytes, i, count);
      }
      receiver.flush();
      return null;
    }).when(device).executeShellCommand(any(), any());

    return device;
  }

  private static <V> V waitForFuture(@NotNull ListenableFuture<V> future) throws Exception {
    assert !EventQueue.isDispatchThread();
    return future.get(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS);
  }
}
