/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.sdk

import com.android.SdkConstants
import com.android.dvlib.DeviceSchema
import com.android.sdklib.devices.DeviceManager
import com.android.sdklib.devices.DeviceWriter
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.utils.ILogger
import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DeviceManagerCacheTest {
  @get:Rule
  val temporaryFolder = TemporaryFolder()

  @Test
  fun getDeviceManagerBacksUpUserDevicesWithNewerSchema() {
    val testSdk = newTestSdk()
    val unsupportedSchemaVersion = DeviceSchema.NS_LATEST_VERSION + 1
    val devicesXml = testSdk.androidFolder.resolve(SdkConstants.FN_DEVICES_XML)
    writeUserDevices(devicesXml, unsupportedSchemaVersion)

    val logger = CapturingLogger()
    val deviceManager = DeviceManagerCache(logger).getDeviceManager(testSdk.sdkHandler)

    assertThat(logger.warnings.any { it.contains("schema version $unsupportedSchemaVersion") }).isTrue()
    // The default/vendor/system-image devices still load instead of the whole scan aborting.
    assertThat(deviceManager.getDevices(DeviceManager.DeviceCategory.DEFAULT)).isNotEmpty()
    assertThat(deviceManager.getDevices(DeviceManager.DeviceCategory.USER)).isEmpty()

    val backupPath = testSdk.androidFolder.resolve(SdkConstants.FN_DEVICES_XML + ".unsupported-schema")
    assertThat(Files.exists(devicesXml)).isFalse()
    assertThat(Files.readString(backupPath)).contains("sdk/devices/$unsupportedSchemaVersion")
  }

  @Test
  fun repeatedBackupKeepsEveryUnsupportedUserDevicesFile() {
    val testSdk = newTestSdk()
    val unsupportedSchemaVersion = DeviceSchema.NS_LATEST_VERSION + 1
    val devicesXml = testSdk.androidFolder.resolve(SdkConstants.FN_DEVICES_XML)

    // First unsupported file, then a second one written later (e.g. by a newer Studio/SDK).
    Files.writeString(devicesXml, "<!-- first --> ${userDevicesXml(unsupportedSchemaVersion)}")
    DeviceManagerCache(CapturingLogger()).getDeviceManager(testSdk.sdkHandler)
    Files.writeString(devicesXml, "<!-- second --> ${userDevicesXml(unsupportedSchemaVersion)}")
    DeviceManagerCache(CapturingLogger()).getDeviceManager(testSdk.sdkHandler)

    val firstBackup = testSdk.androidFolder.resolve(SdkConstants.FN_DEVICES_XML + ".unsupported-schema")
    val secondBackup = testSdk.androidFolder.resolve(SdkConstants.FN_DEVICES_XML + ".unsupported-schema.1")
    assertThat(Files.exists(devicesXml)).isFalse()
    assertThat(Files.readString(firstBackup)).contains("first")
    assertThat(Files.readString(secondBackup)).contains("second")
  }

  @Test
  fun repeatedBackupWithIdenticalContentDoesNotAccumulate() {
    val testSdk = newTestSdk()
    val unsupportedSchemaVersion = DeviceSchema.NS_LATEST_VERSION + 1
    val devicesXml = testSdk.androidFolder.resolve(SdkConstants.FN_DEVICES_XML)

    // The same unsupported file reappears on the next restart (schema still unsupported).
    writeUserDevices(devicesXml, unsupportedSchemaVersion)
    DeviceManagerCache(CapturingLogger()).getDeviceManager(testSdk.sdkHandler)
    writeUserDevices(devicesXml, unsupportedSchemaVersion)
    DeviceManagerCache(CapturingLogger()).getDeviceManager(testSdk.sdkHandler)

    val firstBackup = testSdk.androidFolder.resolve(SdkConstants.FN_DEVICES_XML + ".unsupported-schema")
    val secondBackup = testSdk.androidFolder.resolve(SdkConstants.FN_DEVICES_XML + ".unsupported-schema.1")
    assertThat(Files.exists(devicesXml)).isFalse()
    assertThat(Files.exists(firstBackup)).isTrue()
    assertThat(Files.exists(secondBackup)).isFalse()
  }

  @Test
  fun getDeviceManagerDoesNotThrowOnGetDeviceAfterBackup() {
    val testSdk = newTestSdk()
    writeUserDevices(testSdk.androidFolder.resolve(SdkConstants.FN_DEVICES_XML), DeviceSchema.NS_LATEST_VERSION + 1)

    // getDevice() is the call that aborts the AVD scan in RIDER-139412; it must not throw now.
    val deviceManager = DeviceManagerCache(CapturingLogger()).getDeviceManager(testSdk.sdkHandler)
    val defaultDevice = deviceManager.getDevices(DeviceManager.DeviceCategory.DEFAULT).first()

    assertThat(deviceManager.getDevice(defaultDevice.id, defaultDevice.manufacturer)).isNotNull()
  }

  @Test
  fun saveUserDevicesWritesNewFileAfterUnsupportedUserDevicesWereBackedUp() {
    val testSdk = newTestSdk()
    val unsupportedSchemaVersion = DeviceSchema.NS_LATEST_VERSION + 1
    val devicesXml = testSdk.androidFolder.resolve(SdkConstants.FN_DEVICES_XML)
    writeUserDevices(devicesXml, unsupportedSchemaVersion)

    val deviceManager = DeviceManagerCache(CapturingLogger()).getDeviceManager(testSdk.sdkHandler)
    val userDevice = deviceManager.getDevices(DeviceManager.DeviceCategory.DEFAULT).first()
    deviceManager.addUserDevice(userDevice)
    deviceManager.saveUserDevices()

    assertThat(Files.exists(devicesXml)).isTrue()
    assertThat(Files.readString(devicesXml)).doesNotContain("sdk/devices/$unsupportedSchemaVersion")
  }

  @Test
  fun backupFailureLeavesUnsupportedUserDevicesFileUntouched() {
    assumeTrue(Files.getFileStore(temporaryFolder.root.toPath()).supportsFileAttributeView("posix"))

    val testSdk = newTestSdk()
    val unsupportedSchemaVersion = DeviceSchema.NS_LATEST_VERSION + 1
    val devicesXml = testSdk.androidFolder.resolve(SdkConstants.FN_DEVICES_XML)
    val originalContent = userDevicesXml(unsupportedSchemaVersion)
    Files.writeString(devicesXml, originalContent)
    // Make the folder read-only so the backup move cannot succeed.
    Files.setPosixFilePermissions(testSdk.androidFolder, PosixFilePermissions.fromString("r-xr-xr-x"))

    // Backup is best-effort: it must not throw, and it must leave the file as it was.
    DeviceManagerCache(CapturingLogger()).getDeviceManager(testSdk.sdkHandler)

    assertThat(Files.readString(devicesXml)).isEqualTo(originalContent)
  }

  @Test
  fun getDeviceManagerDoesNotLoadDevicesUntilRequested() {
    val testSdk = newTestSdk()
    val logger = CapturingLogger()

    DeviceManagerCache(logger).getDeviceManager(testSdk.sdkHandler)

    assertThat(logger.warnings).isEmpty()
  }

  @Test
  fun getDeviceManagerReadsUserDevicesWithSupportedSchema() {
    val testSdk = newTestSdk()
    val userDevice = DeviceManager.createInstance(testSdk.sdkHandler, CapturingLogger())
      .getDevices(DeviceManager.DeviceCategory.DEFAULT)
      .first()
    Files.newOutputStream(testSdk.androidFolder.resolve(SdkConstants.FN_DEVICES_XML)).use { output ->
      DeviceWriter.writeToXml(output, listOf(userDevice))
    }

    val logger = CapturingLogger()
    val deviceManager = DeviceManagerCache(logger).getDeviceManager(testSdk.sdkHandler)

    assertThat(logger.warnings).isEmpty()
    assertThat(deviceManager.getDevices(DeviceManager.DeviceCategory.USER).map { it.id }).contains(userDevice.id)
  }

  @Suppress("SameParameterValue")
  private fun writeUserDevices(devicesXml: Path, schemaVersion: Int) {
    Files.writeString(devicesXml, userDevicesXml(schemaVersion))
  }

  private fun userDevicesXml(schemaVersion: Int): String =
    """
    <?xml version="1.0" encoding="UTF-8"?>
    <d:devices xmlns:d="http://schemas.android.com/sdk/devices/$schemaVersion"/>
    """.trimIndent()

  private fun newTestSdk(): TestSdk {
    val sdkRoot = temporaryFolder.newFolder("sdk").toPath()
    val androidFolder = temporaryFolder.newFolder("android-home").toPath()

    return TestSdk(AndroidSdkHandler(sdkRoot, androidFolder), androidFolder)
  }

  private data class TestSdk(val sdkHandler: AndroidSdkHandler, val androidFolder: Path)

  private class CapturingLogger : ILogger {
    val warnings = mutableListOf<String>()

    override fun error(t: Throwable?, msgFormat: String?, vararg args: Any?) = Unit

    override fun warning(msgFormat: String?, vararg args: Any?) {
      warnings += msgFormat?.format(*args) ?: ""
    }

    override fun info(msgFormat: String?, vararg args: Any?) = Unit

    override fun verbose(msgFormat: String?, vararg args: Any?) = Unit
  }
}
