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

import java.awt.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;

public class AdbFileOperationsTest {
  private static final long TIMEOUT_MILLISECONDS = 30_000;
  @NotNull private static final String ERROR_LINE_MARKER = "ERR-ERR-ERR-ERR";
  @NotNull private static final String COMMAND_ERROR_CHECK_SUFFIX = " || echo " + ERROR_LINE_MARKER;

  @Rule
  public ExpectedException thrown= ExpectedException.none();

  @ClassRule
  public static DebugLoggerFactoryRule ourLoggerFactoryRule = new DebugLoggerFactoryRule();

  @Test
  public void testCreateNewFileSuccess() throws Exception {
    // Prepare
    TestShellCommands commands = new TestShellCommands();
    addNexus7Commands(commands);
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
    addNexus7Commands(commands);
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
    addNexus7Commands(commands);
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
    addNexus7Commands(commands);
    IDevice device = commands.createMockDevice();
    Executor taskExecutor = PooledThreadExecutor.INSTANCE;
    AdbFileOperations fileOperations = new AdbFileOperations(device, taskExecutor);

    // Act/Assert
    thrown.expect(ExecutionException.class);
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException.class));
    waitForFuture(fileOperations.createNewFile("/data", "foo.txt"));
  }

  @Test
  public void testCreateNewFileExistError() throws Exception {
    // Prepare
    TestShellCommands commands = new TestShellCommands();
    addNexus7Commands(commands);
    IDevice device = commands.createMockDevice();
    Executor taskExecutor = PooledThreadExecutor.INSTANCE;
    AdbFileOperations fileOperations = new AdbFileOperations(device, taskExecutor);

    // Act/Assert
    thrown.expect(ExecutionException.class);
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException.class));
    waitForFuture(fileOperations.createNewFile("/", "file_contexts"));
  }

  @Test
  public void testCreateNewDirectorySuccess() throws Exception {
    // Prepare
    TestShellCommands commands = new TestShellCommands();
    addNexus7Commands(commands);
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
    addNexus7Commands(commands);
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
    addNexus7Commands(commands);
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
    addNexus7Commands(commands);
    IDevice device = commands.createMockDevice();
    Executor taskExecutor = PooledThreadExecutor.INSTANCE;
    AdbFileOperations fileOperations = new AdbFileOperations(device, taskExecutor);

    // Act

    // Assert
    thrown.expect(ExecutionException.class);
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException.class));
    waitForFuture(fileOperations.createNewDirectory("/data", "foo-dir"));
  }

  @Test
  public void testCreateNewDirectoryExistError() throws Exception {
    // Prepare
    TestShellCommands commands = new TestShellCommands();
    addNexus7Commands(commands);
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
    addNexus7Commands(commands);
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
    addNexus7Commands(commands);
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
    addNexus7Commands(commands);
    IDevice device = commands.createMockDevice();
    Executor taskExecutor = PooledThreadExecutor.INSTANCE;
    AdbFileOperations fileOperations = new AdbFileOperations(device, taskExecutor);

    // Act/Assert
    thrown.expect(ExecutionException.class);
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException.class));
    waitForFuture(fileOperations.deleteFile("/system/app/Street/Street.apk"));
  }

  @Test
  public void testDeleteExistingDirectorySucceeds() throws Exception {
    // Prepare
    TestShellCommands commands = new TestShellCommands();
    addNexus7Commands(commands);
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
    addNexus7Commands(commands);
    IDevice device = commands.createMockDevice();
    Executor taskExecutor = PooledThreadExecutor.INSTANCE;
    AdbFileOperations fileOperations = new AdbFileOperations(device, taskExecutor);

    // Act/Assert
    thrown.expect(ExecutionException.class);
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException.class));
    waitForFuture(fileOperations.deleteRecursive("/config"));
  }

  private static void addNexus7Commands(@NotNull TestShellCommands commands) {
    // These are command + result as run on a Nexus 7, Android 6.0.1, API 23
    addFailedCommand(commands, "test -e '/foo.txt'");
    addFailedCommand(commands, "touch '/foo.txt'", "touch: '/foo.txt': Read-only file system\n");

    addCommand(commands, "test -e '/file_contexts'", "");

    addFailedCommand(commands, "test -e '/sdcard/foo.txt'");
    addCommand(commands, "touch '/sdcard/foo.txt'", "");

    addFailedCommand(commands, "test -e '/data/foo.txt'");
    addFailedCommand(commands, "touch '/data/foo.txt'", "touch: '/data/foo.txt': Permission denied\n");

    addCommand(commands, "mkdir '/sdcard/foo-dir'", "");
    addFailedCommand(commands, "mkdir '/foo-dir'", "mkdir: '/foo-dir': Read-only file system\n");
    addFailedCommand(commands, "mkdir '/data/foo-dir'", "mkdir: '/data/foo-dir': Permission denied\n");
    addFailedCommand(commands, "mkdir '/data'", "mkdir: '/data': File exists\n");

    addCommand(commands, "rm -f /sdcard/foo.txt", "");
    addFailedCommand(commands, "rm -f /sdcard/foo-dir", "rm: sdcard/foo-dir: is a directory\n");
    addFailedCommand(commands, "rm -f /system/app/Street/Street.apk", "rm: /system/app/Street/Street.apk: Read-only file system\n");

    addCommand(commands, "rm -r -f /sdcard/foo-dir", "");
    addFailedCommand(commands, "rm -r -f /config", "rm: /config: Permission denied\n");
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
