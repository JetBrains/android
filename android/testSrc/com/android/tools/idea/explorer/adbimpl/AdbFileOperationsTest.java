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
import com.google.common.util.concurrent.ListenableFuture;
import org.hamcrest.core.IsInstanceOf;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.PooledThreadExecutor;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.awt.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.google.common.truth.Truth.assertThat;

@RunWith(Parameterized.class)
public class AdbFileOperationsTest {
  private static final long TIMEOUT_MILLISECONDS = 30_000;
  @NotNull private static final String ERROR_LINE_MARKER = "ERR-ERR-ERR-ERR";
  @NotNull private static final String COMMAND_ERROR_CHECK_SUFFIX = " || echo " + ERROR_LINE_MARKER;

  @NotNull private Consumer<TestShellCommands> mySetupCommands;

  @Parameterized.Parameters
  public static Object[] data() {
    return new Object[]{
      (Consumer<TestShellCommands>)AdbFileOperationsTest::addEmulatorApi10Commands,
      (Consumer<TestShellCommands>)AdbFileOperationsTest::addNexus7Api23Commands,
    };
  }

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @ClassRule
  public static DebugLoggerFactoryRule ourLoggerFactoryRule = new DebugLoggerFactoryRule();

  public AdbFileOperationsTest(@NotNull Consumer<TestShellCommands> setupCommands) {
    mySetupCommands = setupCommands;
  }

  @Test
  public void testCreateNewFileSuccess() throws Exception {
    // Prepare
    TestShellCommands commands = new TestShellCommands();
    mySetupCommands.accept(commands);
    IDevice device = commands.createMockDevice();
    Executor taskExecutor = PooledThreadExecutor.INSTANCE;
    AdbFileOperations fileOperations = new AdbFileOperations(device, taskExecutor);

    // Act
    Void result = waitForFuture(fileOperations.createNewFile("/sdcard", "foo.txt"));

    // Assert
    assertThat(result).isNull();
  }

  @Test
  public void testCreateNewFileInvalidFileNameError() throws Exception {
    // Prepare
    TestShellCommands commands = new TestShellCommands();
    mySetupCommands.accept(commands);
    IDevice device = commands.createMockDevice();
    Executor taskExecutor = PooledThreadExecutor.INSTANCE;
    AdbFileOperations fileOperations = new AdbFileOperations(device, taskExecutor);

    // Act/Assert
    thrown.expect(ExecutionException.class);
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException.class));
    waitForFuture(fileOperations.createNewFile("/", "fo/o.txt"));
  }

  @Test
  public void testCreateNewFileReadOnlyError() throws Exception {
    // Prepare
    TestShellCommands commands = new TestShellCommands();
    mySetupCommands.accept(commands);
    IDevice device = commands.createMockDevice();
    Executor taskExecutor = PooledThreadExecutor.INSTANCE;
    AdbFileOperations fileOperations = new AdbFileOperations(device, taskExecutor);

    // Act/Assert
    thrown.expect(ExecutionException.class);
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException.class));
    waitForFuture(fileOperations.createNewFile("/", "foo.txt"));
  }

  @Test
  public void testCreateNewFilePermissionError() throws Exception {
    // Prepare
    TestShellCommands commands = new TestShellCommands();
    mySetupCommands.accept(commands);
    IDevice device = commands.createMockDevice();
    Executor taskExecutor = PooledThreadExecutor.INSTANCE;
    AdbFileOperations fileOperations = new AdbFileOperations(device, taskExecutor);

    // Act/Assert
    thrown.expect(ExecutionException.class);
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException.class));
    waitForFuture(fileOperations.createNewFile("/system", "foo.txt"));
  }

  @Test
  public void testCreateNewFileExistError() throws Exception {
    // Prepare
    TestShellCommands commands = new TestShellCommands();
    mySetupCommands.accept(commands);
    IDevice device = commands.createMockDevice();
    Executor taskExecutor = PooledThreadExecutor.INSTANCE;
    AdbFileOperations fileOperations = new AdbFileOperations(device, taskExecutor);

    // Act/Assert
    thrown.expect(ExecutionException.class);
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException.class));
    waitForFuture(fileOperations.createNewFile("/", "default.prop"));
  }

  @Test
  public void testCreateNewDirectorySuccess() throws Exception {
    // Prepare
    TestShellCommands commands = new TestShellCommands();
    mySetupCommands.accept(commands);
    IDevice device = commands.createMockDevice();
    Executor taskExecutor = PooledThreadExecutor.INSTANCE;
    AdbFileOperations fileOperations = new AdbFileOperations(device, taskExecutor);

    // Act
    Void result = waitForFuture(fileOperations.createNewDirectory("/sdcard", "foo-dir"));

    // Assert
    assertThat(result).isNull();
  }

  @Test
  public void testCreateNewDirectoryInvalidNameError() throws Exception {
    // Prepare
    TestShellCommands commands = new TestShellCommands();
    mySetupCommands.accept(commands);
    IDevice device = commands.createMockDevice();
    Executor taskExecutor = PooledThreadExecutor.INSTANCE;
    AdbFileOperations fileOperations = new AdbFileOperations(device, taskExecutor);

    // Act

    // Assert
    thrown.expect(ExecutionException.class);
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException.class));
    waitForFuture(fileOperations.createNewDirectory("/", "fo/o-dir"));
  }

  @Test
  public void testCreateNewDirectoryReadOnlyError() throws Exception {
    // Prepare
    TestShellCommands commands = new TestShellCommands();
    mySetupCommands.accept(commands);
    IDevice device = commands.createMockDevice();
    Executor taskExecutor = PooledThreadExecutor.INSTANCE;
    AdbFileOperations fileOperations = new AdbFileOperations(device, taskExecutor);

    // Act

    // Assert
    thrown.expect(ExecutionException.class);
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException.class));
    waitForFuture(fileOperations.createNewDirectory("/", "foo-dir"));
  }

  @Test
  public void testCreateNewDirectoryPermissionError() throws Exception {
    // Prepare
    TestShellCommands commands = new TestShellCommands();
    mySetupCommands.accept(commands);
    IDevice device = commands.createMockDevice();
    Executor taskExecutor = PooledThreadExecutor.INSTANCE;
    AdbFileOperations fileOperations = new AdbFileOperations(device, taskExecutor);

    // Act

    // Assert
    thrown.expect(ExecutionException.class);
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException.class));
    waitForFuture(fileOperations.createNewDirectory("/system", "foo-dir"));
  }

  @Test
  public void testCreateNewDirectoryExistError() throws Exception {
    // Prepare
    TestShellCommands commands = new TestShellCommands();
    mySetupCommands.accept(commands);
    IDevice device = commands.createMockDevice();
    Executor taskExecutor = PooledThreadExecutor.INSTANCE;
    AdbFileOperations fileOperations = new AdbFileOperations(device, taskExecutor);

    // Act

    // Assert
    thrown.expect(ExecutionException.class);
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException.class));
    waitForFuture(fileOperations.createNewDirectory("/", "data"));
  }

  @Test
  public void testDeleteExistingFileSuccess() throws Exception {
    // Prepare
    TestShellCommands commands = new TestShellCommands();
    mySetupCommands.accept(commands);
    IDevice device = commands.createMockDevice();
    Executor taskExecutor = PooledThreadExecutor.INSTANCE;
    AdbFileOperations fileOperations = new AdbFileOperations(device, taskExecutor);

    // Act
    Void result = waitForFuture(fileOperations.deleteFile("/sdcard/foo.txt"));

    // Assert
    assertThat(result).isNull();
  }

  @Test
  public void testDeleteExistingDirectoryAsFileError() throws Exception {
    // Prepare
    TestShellCommands commands = new TestShellCommands();
    mySetupCommands.accept(commands);
    IDevice device = commands.createMockDevice();
    Executor taskExecutor = PooledThreadExecutor.INSTANCE;
    AdbFileOperations fileOperations = new AdbFileOperations(device, taskExecutor);

    // Act/Assert
    thrown.expect(ExecutionException.class);
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException.class));
    waitForFuture(fileOperations.deleteFile("/sdcard/foo-dir"));
  }

  @Test
  public void testDeleteExistingReadOnlyFileError() throws Exception {
    // Prepare
    TestShellCommands commands = new TestShellCommands();
    mySetupCommands.accept(commands);
    IDevice device = commands.createMockDevice();
    Executor taskExecutor = PooledThreadExecutor.INSTANCE;
    AdbFileOperations fileOperations = new AdbFileOperations(device, taskExecutor);

    // Act/Assert
    thrown.expect(ExecutionException.class);
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException.class));
    waitForFuture(fileOperations.deleteFile("/system/bin/sh"));
  }

  @Test
  public void testDeleteExistingDirectorySucceeds() throws Exception {
    // Prepare
    TestShellCommands commands = new TestShellCommands();
    mySetupCommands.accept(commands);
    IDevice device = commands.createMockDevice();
    Executor taskExecutor = PooledThreadExecutor.INSTANCE;
    AdbFileOperations fileOperations = new AdbFileOperations(device, taskExecutor);

    // Act
    Void result = waitForFuture(fileOperations.deleteRecursive("/sdcard/foo-dir"));

    // Assert
    assertThat(result).isNull();
  }

  @Test
  public void testDeleteExistingDirectoryPermissionError() throws Exception {
    // Prepare
    TestShellCommands commands = new TestShellCommands();
    mySetupCommands.accept(commands);
    IDevice device = commands.createMockDevice();
    Executor taskExecutor = PooledThreadExecutor.INSTANCE;
    AdbFileOperations fileOperations = new AdbFileOperations(device, taskExecutor);

    // Act/Assert
    thrown.expect(ExecutionException.class);
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException.class));
    waitForFuture(fileOperations.deleteRecursive("/config"));
  }

  /**
   * These are command + result as run on a Nexus 7, Android 6.0.1, API 23
   */
  private static void addNexus7Api23Commands(@NotNull TestShellCommands commands) {
    commands.setDescription("Nexus 7, Android 6.0.1, API 23");

    // "test" capability detection
    addCommand(commands, "echo >/data/local/tmp/device-explorer/.__temp_test_test__file__.tmp", "");
    addCommand(commands, "test -e /data/local/tmp/device-explorer/.__temp_test_test__file__.tmp", "");
    addCommand(commands, "rm /data/local/tmp/device-explorer/.__temp_test_test__file__.tmp", "");

    // "rm -f" capability detection
    addCommand(commands, "echo >/data/local/tmp/device-explorer/.__temp_rm_test_file__.tmp", "");
    addCommand(commands, "rm -f /data/local/tmp/device-explorer/.__temp_rm_test_file__.tmp", "");

    // "touch" capability detection
    addCommand(commands, "touch /data/local/tmp/device-explorer/.__temp_touch_test_file__.tmp", "");
    addCommand(commands, "rm /data/local/tmp/device-explorer/.__temp_touch_test_file__.tmp", "");

    addFailedCommand(commands, "test -e /foo.txt");

    addFailedCommand(commands, "touch /foo.txt", "touch: '/foo.txt': Read-only file system\n");

    addCommand(commands, "test -e /default.prop", "");

    addFailedCommand(commands, "test -e /sdcard/foo.txt");
    addCommand(commands, "touch /sdcard/foo.txt", "");

    addFailedCommand(commands, "test -e /system/foo.txt");
    addFailedCommand(commands, "touch /system/foo.txt", "touch: '/system/foo.txt': Read-only file system\n");

    addCommand(commands, "mkdir /sdcard/foo-dir", "");
    addFailedCommand(commands, "mkdir /foo-dir", "mkdir: '/foo-dir': Read-only file system\n");
    addFailedCommand(commands, "mkdir /system/foo-dir", "mkdir: '/system/foo-dir': Read-only file system\n");
    addFailedCommand(commands, "mkdir /data", "mkdir: '/data': File exists\n");

    addCommand(commands, "rm -f /sdcard/foo.txt", "");
    addFailedCommand(commands, "rm -f /sdcard/foo-dir", "rm: sdcard/foo-dir: is a directory\n");
    addFailedCommand(commands, "rm -f /system/bin/sh", "rm: /system/bin/sh: Read-only file system\n");

    addCommand(commands, "rm -r -f /sdcard/foo-dir", "");
    addFailedCommand(commands, "rm -r -f /config", "rm: /config: Permission denied\n");
  }

  /**
   * Add commands from a Nexus emulator, Android 2.3.7, API 10
   */
  private static void addEmulatorApi10Commands(@NotNull TestShellCommands commands) {
    commands.setDescription("Nexus 5, Android 2.3.7, API 10");

    // "test" capability detection
    addCommand(commands, "echo >/data/local/tmp/device-explorer/.__temp_test_test__file__.tmp", "");
    addFailedCommand(commands, "test -e /data/local/tmp/device-explorer/.__temp_test_test__file__.tmp", "test: not found\n");
    addCommand(commands, "rm /data/local/tmp/device-explorer/.__temp_test_test__file__.tmp", "");

    // "touch" capability detection
    addFailedCommand(commands, "touch /data/local/tmp/device-explorer/.__temp_touch_test_file__.tmp", "touch: not found\n");

    // "rm -f" capability detection
    addCommand(commands, "echo >/data/local/tmp/device-explorer/.__temp_rm_test_file__.tmp", "");
    addFailedCommand(commands, "rm -f /data/local/tmp/device-explorer/.__temp_rm_test_file__.tmp", "rm failed for -f, Read-only file system\n");
    addCommand(commands, "rm /data/local/tmp/device-explorer/.__temp_rm_test_file__.tmp", "");

    addFailedCommand(commands, "ls -d -a /foo.txt", "/foo.txt: No such file or directory\n");

    addFailedCommand(commands, "echo -n >/foo.txt", "cannot create /foo.txt: read-only file system\n");

    addCommand(commands, "ls -d -a /default.prop", "");

    addFailedCommand(commands, "ls -d -a /sdcard/foo.txt", "/sdcard/foo.txt: No such file or directory\n");
    addCommand(commands, "echo -n >/sdcard/foo.txt", "");

    addFailedCommand(commands, "ls -d -a /system/foo.txt", "/system/foo.txt: No such file or directory\n");
    addFailedCommand(commands, "echo -n >/system/foo.txt", "cannot create /system/foo.txt: read-only file system\n");

    addCommand(commands, "mkdir /sdcard/foo-dir", "");
    addFailedCommand(commands, "mkdir /foo-dir", "mkdir failed for /foo-dir, Read-only file system\n");
    addFailedCommand(commands, "mkdir /system/foo-dir", "mkdir: '/data/foo-dir': Permission denied\n");
    addFailedCommand(commands, "mkdir /data", "mkdir failed for /data, File exists\n");

    addCommand(commands, "rm /sdcard/foo.txt", "");
    addFailedCommand(commands, "rm /sdcard/foo-dir", "rm failed for /sdcard/foo-dir, Is a directory\n");
    addFailedCommand(commands, "rm /system/bin/sh", "rm failed for /system/bin/sh, Read-only file system\n");

    addCommand(commands, "rm -r /sdcard/foo-dir", "");
    addFailedCommand(commands, "rm -r /config", "rm failed for /config, Read-only file system\n");
  }

  private static void addFailedCommand(@NotNull TestShellCommands commands, @NotNull String command) {
    addFailedCommand(commands, command, "");
  }

  private static void addFailedCommand(@NotNull TestShellCommands commands, @NotNull String command, @NotNull String result) {
    addCommand(commands, command, result + ERROR_LINE_MARKER + "\n");
  }

  private static void addCommand(@NotNull TestShellCommands commands, @NotNull String command, @NotNull String result) {
    commands.add(command + COMMAND_ERROR_CHECK_SUFFIX, result);
  }

  private static <V> V waitForFuture(@NotNull ListenableFuture<V> future) throws Exception {
    assert !EventQueue.isDispatchThread();
    return future.get(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS);
  }
}
