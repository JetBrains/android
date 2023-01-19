/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.adblib.ddmlibcompatibility

import com.android.adblib.RemoteFileMode
import com.android.ddmlib.IDevice
import com.android.ddmlib.SyncException
import com.android.ddmlib.SyncService
import com.android.ddmlib.testing.FakeAdbRule
import com.android.fakeadbserver.DeviceFileState
import com.android.fakeadbserver.DeviceState
import com.android.tools.idea.testingutils.FakeAdbServiceRule
import com.google.common.truth.Truth
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.TemporaryDirectory
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.TimeUnit

class SyncTest {
  private val projectRule = ProjectRule()
  private val fakeAdbRule = FakeAdbRule()
  private val fakeAdbServiceRule = FakeAdbServiceRule({ projectRule.project }, fakeAdbRule)
  private val tempDirectory = TemporaryDirectory()

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(fakeAdbRule).around(fakeAdbServiceRule).around(tempDirectory)!!

  @Test
  fun pushFileShouldWork() {
    // Prepare
    val (device, deviceState) = prepareSingleDevice()
    val localFilePath = tempDirectory.newPath()
    Files.write(localFilePath, ByteArray(1_024))
    val monitor = MyProgressMonitor()

    // Act
    pushFile(device, localFilePath.toString(), "/foo/bar.bin", monitor)

    // Assert
    Truth.assertThat(monitor.startValue).isEqualTo(1_024)
    Truth.assertThat(monitor.stopCalled).isTrue()
    Truth.assertThat(deviceState.getFile("/foo/bar.bin")).isNotNull()
  }

  @Test
  fun pushFileShouldSupportCancellation() {
    // Prepare
    val (device, _) = prepareSingleDevice()
    val localFilePath = tempDirectory.newPath()
    Files.write(localFilePath, ByteArray(1_024))
    val monitor = object : MyProgressMonitor() {
      override fun start(totalWork: Int) {
        cancelled = true
      }
    }

    val exception = Assert.assertThrows(SyncException::class.java) {
      pushFile(device, localFilePath.toString(), "/foo/bar.bin", monitor)
    }
    Truth.assertThat(exception.errorCode).isEqualTo(SyncException.SyncError.CANCELED)
  }

  @Test
  fun pullFileShouldWork() {
    // Prepare
    val (device, fileState) = prepareSingleDeviceSingleFile()
    val localFilePath = tempDirectory.newPath()
    val monitor = MyProgressMonitor()

    // Act
    pullFile(device, fileState.path, localFilePath.toString(), monitor)

    // Assert
    Truth.assertThat(monitor.startValue).isEqualTo(0)
    Truth.assertThat(monitor.stopCalled).isTrue()
    Truth.assertThat(Files.exists(localFilePath)).isTrue()
    Truth.assertThat(Files.size(localFilePath)).isEqualTo(fileState.bytes.size)
  }

  @Test
  fun pullFileShouldSupportCancellation() {
    // Prepare
    val (device, fileState) = prepareSingleDeviceSingleFile()
    val localFilePath = tempDirectory.newPath()
    val monitor = object : MyProgressMonitor() {
      override fun advance(work: Int) {
        super.advance(work)
        // Cancel after first progress report
        cancelled = true
      }
    }

    val exception = Assert.assertThrows(SyncException::class.java) {
      pullFile(device, fileState.path, localFilePath.toString(), monitor)
    }
    Truth.assertThat(exception.errorCode).isEqualTo(SyncException.SyncError.CANCELED)
  }

  private fun prepareSingleDeviceSingleFile(): Pair<IDevice, DeviceFileState> {
    val (device, deviceState) = prepareSingleDevice()
    val filePath = "/foo/bar.bin"
    val fileBytes = ByteArray(128_000)
    val fileMode = RemoteFileMode.fromPosixPermissions(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
    val fileDate = FileTime.from(1_000_000, TimeUnit.SECONDS)
    val fileState = DeviceFileState(
      filePath,
      fileMode.modeBits,
      (fileDate.toMillis() / 1_000).toInt(),
      fileBytes
    )
    deviceState.createFile(fileState)
    return Pair(device, fileState)
  }

  private fun prepareSingleDevice(): Pair<IDevice, DeviceState> {
    val deviceState = fakeAdbRule.attachDevice("42", "Google", "Pix3l", "versionX", "29")
    val device: IDevice = fakeAdbRule.bridge.devices.single()
    return Pair(device, deviceState)
  }

  private open class MyProgressMonitor : SyncService.ISyncProgressMonitor {
    var startValue = -1
    var stopCalled = false
    var cancelled = false

    override fun start(totalWork: Int) {
      startValue = totalWork
    }

    override fun stop() {
      stopCalled = true
    }

    override fun isCanceled(): Boolean {
      return cancelled
    }

    override fun startSubTask(name: String?) {
    }

    override fun advance(work: Int) {
    }
  }
}
