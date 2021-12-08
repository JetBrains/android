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

import com.android.tools.idea.adb.AdbShellCommandException
import com.android.tools.idea.testing.DebugLoggerRule
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.ListenableFuture
import org.hamcrest.core.IsInstanceOf
import org.jetbrains.ide.PooledThreadExecutor
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.awt.EventQueue
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

@RunWith(Parameterized::class)
class AdbFileOperationsTest(private val mySetupCommands: Consumer<TestShellCommands>) {
  @get:Rule
  val thrown = ExpectedException.none()
  
  private fun setupMockDevice(): AdbFileOperations {
    val commands = TestShellCommands()
    mySetupCommands.accept(commands)
    val device = commands.createMockDevice()
    val taskExecutor = PooledThreadExecutor.INSTANCE
    return AdbFileOperations(device, AdbDeviceCapabilities(device), taskExecutor)
  }

  @Test
  fun testCreateNewFileSuccess() {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act
    val result = waitForFuture(fileOperations.createNewFile("/sdcard", "foo.txt"))

    // Assert
    assertThat(result).isEqualTo(Unit)
  }

  @Test
  fun testCreateNewFileRunAsSuccess() {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act
    val result = waitForFuture(
      fileOperations.createNewFileRunAs(
        "/data/data/com.example.rpaquay.myapplication",
        "NewTextFile.txt",
        "com.example.rpaquay.myapplication"
      )
    )

    // Assert
    assertThat(result).isEqualTo(Unit)
  }

  @Test
  fun testCreateNewFileInvalidFileNameError() {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act/Assert
    thrown.expect(ExecutionException::class.java)
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException::class.java))
    waitForFuture(fileOperations.createNewFile("/", "fo/o.txt"))
  }

  @Test
  fun testCreateNewFileReadOnlyError() {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act/Assert
    thrown.expect(ExecutionException::class.java)
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException::class.java))
    waitForFuture(fileOperations.createNewFile("/", "foo.txt"))
  }

  @Test
  fun testCreateNewFilePermissionError() {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act/Assert
    thrown.expect(ExecutionException::class.java)
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException::class.java))
    waitForFuture(fileOperations.createNewFile("/system", "foo.txt"))
  }

  @Test
  fun testCreateNewFileExistError() {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act/Assert
    thrown.expect(ExecutionException::class.java)
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException::class.java))
    waitForFuture(fileOperations.createNewFile("/", "default.prop"))
  }

  @Test
  fun testCreateNewDirectorySuccess() {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act
    val result = waitForFuture(fileOperations.createNewDirectory("/sdcard", "foo-dir"))

    // Assert
    assertThat(result).isEqualTo(Unit)
  }

  @Test
  fun testCreateNewDirectoryRunAsSuccess() {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act
    val result = waitForFuture(
      fileOperations.createNewDirectoryRunAs(
        "/data/data/com.example.rpaquay.myapplication",
        "foo-dir",
        "com.example.rpaquay.myapplication"
      )
    )

    // Assert
    assertThat(result).isEqualTo(Unit)
  }

  @Test
  fun testCreateNewDirectoryInvalidNameError() {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act

    // Assert
    thrown.expect(ExecutionException::class.java)
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException::class.java))
    waitForFuture(fileOperations.createNewDirectory("/", "fo/o-dir"))
  }

  @Test
  fun testCreateNewDirectoryReadOnlyError() {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act

    // Assert
    thrown.expect(ExecutionException::class.java)
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException::class.java))
    waitForFuture(fileOperations.createNewDirectory("/", "foo-dir"))
  }

  @Test
  fun testCreateNewDirectoryPermissionError() {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act

    // Assert
    thrown.expect(ExecutionException::class.java)
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException::class.java))
    waitForFuture(fileOperations.createNewDirectory("/system", "foo-dir"))
  }

  @Test
  fun testCreateNewDirectoryExistError() {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act

    // Assert
    thrown.expect(ExecutionException::class.java)
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException::class.java))
    waitForFuture(fileOperations.createNewDirectory("/", "data"))
  }

  @Test
  fun testDeleteExistingFileSuccess() {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act
    val result = waitForFuture(fileOperations.deleteFile("/sdcard/foo.txt"))

    // Assert
    assertThat(result).isEqualTo(Unit)
  }

  @Test
  fun testDeleteExistingFileRunAsSuccess() {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act
    val result = waitForFuture(
      fileOperations.deleteFileRunAs(
        "/data/data/com.example.rpaquay.myapplication/NewTextFile.txt",
        "com.example.rpaquay.myapplication"
      )
    )

    // Assert
    assertThat(result).isEqualTo(Unit)
  }

  @Test
  fun testDeleteExistingDirectoryAsFileError() {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act/Assert
    thrown.expect(ExecutionException::class.java)
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException::class.java))
    waitForFuture(fileOperations.deleteFile("/sdcard/foo-dir"))
  }

  @Test
  fun testDeleteExistingReadOnlyFileError() {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act/Assert
    thrown.expect(ExecutionException::class.java)
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException::class.java))
    waitForFuture(fileOperations.deleteFile("/system/bin/sh"))
  }

  @Test
  fun testDeleteExistingDirectorySucceeds() {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act
    val result = waitForFuture(fileOperations.deleteRecursive("/sdcard/foo-dir"))

    // Assert
    assertThat(result).isEqualTo(Unit)
  }

  @Test
  fun testDeleteExistingDirectoryRunAsSucceeds() {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act
    val result = waitForFuture(
      fileOperations.deleteRecursiveRunAs(
        "/data/data/com.example.rpaquay.myapplication/foo-dir",
        "com.example.rpaquay.myapplication"
      )
    )

    // Assert
    assertThat(result).isEqualTo(Unit)
  }

  @Test
  fun testDeleteExistingDirectoryPermissionError() {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act/Assert
    thrown.expect(ExecutionException::class.java)
    thrown.expectCause(IsInstanceOf.instanceOf(AdbShellCommandException::class.java))
    waitForFuture(fileOperations.deleteRecursive("/config"))
  }

  @Test
  fun testListPackages() {
    // Prepare
    val fileOperations = setupMockDevice()

    // Act
    val result = waitForFuture(fileOperations.listPackages())

    // Assert
    assertThat(result).isNotNull()
    assertThat(result).contains("com.example.rpaquay.myapplication")
  }

  companion object {
    private const val TIMEOUT_MILLISECONDS: Long = 30000

    @SuppressWarnings("unused")
    @JvmStatic
    @Parameterized.Parameters
    fun data(): Array<Any> {
      return arrayOf(
        Consumer { commands: TestShellCommands ->
          TestDevices.addEmulatorApi10Commands(commands)
        },
        Consumer { commands: TestShellCommands ->
          TestDevices.addNexus7Api23Commands(commands)
        }
      )
    }

    @JvmField
    @ClassRule
    var ourLoggerRule = DebugLoggerRule()

    private fun <V> waitForFuture(future: ListenableFuture<V>): V {
      assert(!EventQueue.isDispatchThread())
      return future.get(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)
    }
  }
}