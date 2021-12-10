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
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.hamcrest.core.IsInstanceOf
import org.jetbrains.ide.PooledThreadExecutor
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExpectedException
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.concurrent.ExecutionException
import java.util.function.Consumer

@RunWith(Parameterized::class)
class AdbFileOperationsTest(private val mySetupCommands: Consumer<TestShellCommands>) {
  @get:Rule
  val thrown = ExpectedException.none()
  
  private fun setupMockDevice(): AdbFileOperations {
    val commands = TestShellCommands()
    mySetupCommands.accept(commands)
    val device = commands.createMockDevice()
    return AdbFileOperations(device, AdbDeviceCapabilities(device), PooledThreadExecutor.INSTANCE.asCoroutineDispatcher())
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
  }
}