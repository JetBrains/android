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
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.google.common.truth.Truth.assertThat;

@RunWith(Parameterized.class)
public class AdbFileOperationsTest {
  private static final long TIMEOUT_MILLISECONDS = 30_000;

  @NotNull private Consumer<TestShellCommands> mySetupCommands;

  @Parameterized.Parameters
  public static Object[] data() {
    return new Object[]{
      (Consumer<TestShellCommands>)TestDevices::addEmulatorApi10Commands,
      (Consumer<TestShellCommands>)TestDevices::addNexus7Api23Commands,
    };
  }

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @ClassRule
  public static DebugLoggerFactoryRule ourLoggerFactoryRule = new DebugLoggerFactoryRule();

  public AdbFileOperationsTest(@NotNull Consumer<TestShellCommands> setupCommands) {
    mySetupCommands = setupCommands;
  }

  @NotNull
  private AdbFileOperations setupMockDevice() throws Exception {
    TestShellCommands commands = new TestShellCommands();
    mySetupCommands.accept(commands);
    IDevice device = commands.createMockDevice();
    Executor taskExecutor = PooledThreadExecutor.INSTANCE;
    return new AdbFileOperations(device, new AdbDeviceCapabilities(device), taskExecutor);
  }

  @Test
  public void testCreateNewFileSuccess() throws Exception {
    // Prepare
    AdbFileOperations fileOperations = setupMockDevice();

    // Act
    Void result = waitForFuture(fileOperations.createNewFile("/sdcard", "foo.txt"));

    // Assert
    assertThat(result).isNull();
  }

  @Test
  public void testCreateNewFileRunAsSuccess() throws Exception {
    // Prepare
    AdbFileOperations fileOperations = setupMockDevice();

    // Act
    Void result = waitForFuture(fileOperations.createNewFileRunAs("/data/data/com.example.rpaquay.myapplication",
                                                                  "NewTextFile.txt",
                                                                  "com.example.rpaquay.myapplication"));

    // Assert
    assertThat(result).isNull();
  }

  @Test
  public void testCreateNewFileInvalidFileNameError() throws Exception {
    // Prepare
    AdbFileOperations fileOperations = setupMockDevice();

    // Act/Assert
    thrown.expect(ExecutionException.class);
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException.class));
    waitForFuture(fileOperations.createNewFile("/", "fo/o.txt"));
  }

  @Test
  public void testCreateNewFileReadOnlyError() throws Exception {
    // Prepare
    AdbFileOperations fileOperations = setupMockDevice();

    // Act/Assert
    thrown.expect(ExecutionException.class);
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException.class));
    waitForFuture(fileOperations.createNewFile("/", "foo.txt"));
  }

  @Test
  public void testCreateNewFilePermissionError() throws Exception {
    // Prepare
    AdbFileOperations fileOperations = setupMockDevice();

    // Act/Assert
    thrown.expect(ExecutionException.class);
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException.class));
    waitForFuture(fileOperations.createNewFile("/system", "foo.txt"));
  }

  @Test
  public void testCreateNewFileExistError() throws Exception {
    // Prepare
    AdbFileOperations fileOperations = setupMockDevice();

    // Act/Assert
    thrown.expect(ExecutionException.class);
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException.class));
    waitForFuture(fileOperations.createNewFile("/", "default.prop"));
  }

  @Test
  public void testCreateNewDirectorySuccess() throws Exception {
    // Prepare
    AdbFileOperations fileOperations = setupMockDevice();

    // Act
    Void result = waitForFuture(fileOperations.createNewDirectory("/sdcard", "foo-dir"));

    // Assert
    assertThat(result).isNull();
  }

  @Test
  public void testCreateNewDirectoryRunAsSuccess() throws Exception {
    // Prepare
    AdbFileOperations fileOperations = setupMockDevice();

    // Act
    Void result = waitForFuture(fileOperations.createNewDirectoryRunAs("/data/data/com.example.rpaquay.myapplication",
                                                                       "foo-dir",
                                                                       "com.example.rpaquay.myapplication"));

    // Assert
    assertThat(result).isNull();
  }

  @Test
  public void testCreateNewDirectoryInvalidNameError() throws Exception {
    // Prepare
    AdbFileOperations fileOperations = setupMockDevice();

    // Act

    // Assert
    thrown.expect(ExecutionException.class);
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException.class));
    waitForFuture(fileOperations.createNewDirectory("/", "fo/o-dir"));
  }

  @Test
  public void testCreateNewDirectoryReadOnlyError() throws Exception {
    // Prepare
    AdbFileOperations fileOperations = setupMockDevice();

    // Act

    // Assert
    thrown.expect(ExecutionException.class);
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException.class));
    waitForFuture(fileOperations.createNewDirectory("/", "foo-dir"));
  }

  @Test
  public void testCreateNewDirectoryPermissionError() throws Exception {
    // Prepare
    AdbFileOperations fileOperations = setupMockDevice();

    // Act

    // Assert
    thrown.expect(ExecutionException.class);
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException.class));
    waitForFuture(fileOperations.createNewDirectory("/system", "foo-dir"));
  }

  @Test
  public void testCreateNewDirectoryExistError() throws Exception {
    // Prepare
    AdbFileOperations fileOperations = setupMockDevice();

    // Act

    // Assert
    thrown.expect(ExecutionException.class);
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException.class));
    waitForFuture(fileOperations.createNewDirectory("/", "data"));
  }

  @Test
  public void testDeleteExistingFileSuccess() throws Exception {
    // Prepare
    AdbFileOperations fileOperations = setupMockDevice();

    // Act
    Void result = waitForFuture(fileOperations.deleteFile("/sdcard/foo.txt"));

    // Assert
    assertThat(result).isNull();
  }

  @Test
  public void testDeleteExistingFileRunAsSuccess() throws Exception {
    // Prepare
    AdbFileOperations fileOperations = setupMockDevice();

    // Act
    Void result = waitForFuture(fileOperations.deleteFileRunAs("/data/data/com.example.rpaquay.myapplication/NewTextFile.txt",
                                                               "com.example.rpaquay.myapplication"));

    // Assert
    assertThat(result).isNull();
  }

  @Test
  public void testDeleteExistingDirectoryAsFileError() throws Exception {
    // Prepare
    AdbFileOperations fileOperations = setupMockDevice();

    // Act/Assert
    thrown.expect(ExecutionException.class);
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException.class));
    waitForFuture(fileOperations.deleteFile("/sdcard/foo-dir"));
  }

  @Test
  public void testDeleteExistingReadOnlyFileError() throws Exception {
    // Prepare
    AdbFileOperations fileOperations = setupMockDevice();

    // Act/Assert
    thrown.expect(ExecutionException.class);
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException.class));
    waitForFuture(fileOperations.deleteFile("/system/bin/sh"));
  }

  @Test
  public void testDeleteExistingDirectorySucceeds() throws Exception {
    // Prepare
    AdbFileOperations fileOperations = setupMockDevice();

    // Act
    Void result = waitForFuture(fileOperations.deleteRecursive("/sdcard/foo-dir"));

    // Assert
    assertThat(result).isNull();
  }

  @Test
  public void testDeleteExistingDirectoryRunAsSucceeds() throws Exception {
    // Prepare
    AdbFileOperations fileOperations = setupMockDevice();

    // Act
    Void result = waitForFuture(fileOperations.deleteRecursiveRunAs("/data/data/com.example.rpaquay.myapplication/foo-dir",
                                                                    "com.example.rpaquay.myapplication"));

    // Assert
    assertThat(result).isNull();
  }

  @Test
  public void testDeleteExistingDirectoryPermissionError() throws Exception {
    // Prepare
    AdbFileOperations fileOperations = setupMockDevice();

    // Act/Assert
    thrown.expect(ExecutionException.class);
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException.class));
    waitForFuture(fileOperations.deleteRecursive("/config"));
  }

  @Test
  public void testListPackages() throws Exception {
    // Prepare
    AdbFileOperations fileOperations = setupMockDevice();

    // Act
    List<String> result = waitForFuture(fileOperations.listPackages());

    // Assert
    assertThat(result).isNotNull();
    assertThat(result).contains("com.example.rpaquay.myapplication");
  }

  private static <V> V waitForFuture(@NotNull ListenableFuture<V> future) throws Exception {
    assert !EventQueue.isDispatchThread();
    return future.get(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS);
  }
}