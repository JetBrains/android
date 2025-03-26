/*
 * Copyright (C) 2018 The Android Open Source Project
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
@file:JvmName("AndroidDeviceSpecUtil")

package com.android.tools.idea.gradle.run

import com.android.ide.common.repository.AgpVersion
import com.android.ide.common.util.getLanguages
import com.android.resources.Density
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.run.AndroidDevice
import com.android.tools.idea.run.AndroidDeviceSpec
import com.google.common.collect.Ordering
import com.google.gson.stream.JsonWriter
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.io.FileUtil
import java.io.File
import java.io.StringWriter
import java.io.Writer
import java.time.Duration
import java.util.concurrent.TimeUnit

sealed class ProcessedDeviceSpec {

  sealed class SingleDeviceSpec : ProcessedDeviceSpec() {
    object NoDevices : SingleDeviceSpec()
    class TargetDeviceSpec(val deviceSpec: AndroidDeviceSpec) : SingleDeviceSpec() {
      fun writeToJsonTempFile(
        writeLanguages: Boolean,
        moduleAgpVersions: List<AgpVersion> = emptyList(),
      ): File {
        return writeSingleJsonFile("target-device-spec.json", deviceSpec, writeLanguages, moduleAgpVersions)
      }
    }
  }

  class MultipleDeviceSpec(val deviceSpecs: List<AndroidDeviceSpec>) : ProcessedDeviceSpec() {
    fun writeToMultipleJsonTempFiles(
      writeLanguages: Boolean,
      moduleAgpVersions: List<AgpVersion> = emptyList()
    ): List<File> {
      return deviceSpecs.mapIndexed { index, spec ->
        writeSingleJsonFile("device-spec-$index.json", spec, writeLanguages, moduleAgpVersions)
      }
    }
  }

  companion object {
    private fun writeSingleJsonFile(
      filename: String,
      spec: AndroidDeviceSpec,
      writeLanguages: Boolean,
      moduleAgpVersions: List<AgpVersion>
    ): File {
      val jsonString = StringWriter().use {
        spec.writeJson(writeLanguages, it, moduleAgpVersions)
        it.flush()
        it.toString()
      }
      log.info("Device spec file generated: $jsonString")

      val tempDir = File(System.getProperty("java.io.tmpdir"))
      val tempFile = File(tempDir, filename)
      FileUtil.writeToFile(tempFile, jsonString)
      tempFile.deleteOnExit()
      return tempFile
    }
  }
}


data class AndroidDeviceSpecImpl @JvmOverloads constructor(
  /**
   * The common version of the device or devices.
   * Null when combining multiple devices with different versions, or when the version is unknown.
   */
  override val commonVersion: AndroidVersion?,
  /**
   * The minimum version of the device or devices.
   * Null if the device version is unknown.
   */
  override val minVersion: AndroidVersion?,
  override val density: Density? = null,
  override val abis: List<String> = emptyList(),
  val supportsSdkRuntimeProvider: () -> Boolean = { false },
  override val deviceSerials: List<String> = emptyList(),
  val languagesProvider: () -> List<String> = { emptyList() }) : AndroidDeviceSpec {
  override val supportsSdkRuntime: Boolean get() = supportsSdkRuntimeProvider()
  override val languages: List<String> get() = languagesProvider()
}

const val DEVICE_SPEC_TIMEOUT_SECONDS = 10L

/**
 * Creates an [AndroidDeviceSpec] instance from a list of [devices][AndroidDevice], or `null` if the list of
 * devices is empty.
 *
 * If the [fetchLanguages] parameter is `true`, a request is made to the device to retrieve the list
 * of installed languages using the [getLanguages] method. The [timeout] and [unit]
 * parameters are used in that case to ensure each request has a timeout.
 */

fun createDeviceSpecs(
  devices: List<AndroidDevice>,
  timeout: Long = DEVICE_SPEC_TIMEOUT_SECONDS,
  unit: TimeUnit = TimeUnit.SECONDS,
): ProcessedDeviceSpec.MultipleDeviceSpec {

  if (devices.isEmpty()) return ProcessedDeviceSpec.MultipleDeviceSpec(emptyList())

  val deviceSpecList = devices.map { device ->
    var density: Density? = null
    val version = if (device.version == AndroidVersion.DEFAULT) null else device.version

    if (device.supportsMultipleScreenFormats()) {
      log.info("Creating spec for resizable device ${device.name}")
    }
    else {
      density = Density.create(device.density)
    }
    val preferredAbi = device.appPreferredAbi
    val abis = if (StudioFlags.RISC_V.get() && preferredAbi != null) {
      listOf(preferredAbi)
    }
    else {
      device.abis.map { it.toString() }
    }
    log.info("Creating spec for device ${device.name} with ABIs: ${abis.ifEmpty { "<none specified>" }}")
    val deviceSerial = devices.mapNotNull { if (device.isRunning) device.launchedDevice.get().serialNumber else null }
    val supportsSdkRuntime = device.supportsSdkRuntime
    if (supportsSdkRuntime) {
      log.info("Creating spec for ${device.name}.")
    }
    else {
      log.info("Creating spec for ${device.name} without privacy sandbox support.")
    }

    AndroidDeviceSpecImpl(version, version, density, abis, supportsSdkRuntimeProvider = { supportsSdkRuntime },
                          languagesProvider = { combineDeviceLanguages(listOf(device), timeout, unit) }, deviceSerials = deviceSerial)
  }
  return ProcessedDeviceSpec.MultipleDeviceSpec(deviceSpecList)
}

@JvmOverloads
fun createTargetDeviceSpec(devices: List<AndroidDevice>,
                           timeout: Long = DEVICE_SPEC_TIMEOUT_SECONDS,
                           unit: TimeUnit = TimeUnit.SECONDS,
                           notification: (title: String, message: String) -> Unit = { _, _ -> }): ProcessedDeviceSpec.SingleDeviceSpec {
  if (devices.isEmpty()) {
    return ProcessedDeviceSpec.SingleDeviceSpec.NoDevices
  }

  val versions = devices.map { it.version }.toSet()
  val hasUnknownVersions = versions.contains(AndroidVersion.DEFAULT) // Find the common value of the device version to pass to the build.
  // Null if there are multiple distinct versions, or the version is unknown.
  val version = if (hasUnknownVersions) null else versions.singleOrNull() // Find the minimum value of the build API level for making other decisions
  // If the API level of any device is not known, do not commit to a version
  val minVersion = if (hasUnknownVersions) null else versions.minWithOrNull(Ordering.natural())!!

  var density: Density? = null
  var abis: List<String> = emptyList() // If we are building for only one physical device, pass the density and the ABI
  if (devices.size == 1) {
    val device = devices[0]

    if (device.supportsMultipleScreenFormats()) {
      log.info("Creating spec for resizable device")
    }
    else {
      density = Density.create(device.density)
    }

    val preferredAbi = device.appPreferredAbi
    abis = if (StudioFlags.RISC_V.get() && preferredAbi != null) {
      listOf(preferredAbi)
    }
    else { // Note: the abis are returned in their preferred order which should be maintained while passing it on to Gradle.
      device.abis.map { it.toString() }
    }
    log.info("Creating spec for " + device.name + " with ABIs: " + abis.ifEmpty { "<none specified>" })
  }
  else {
    if (StudioFlags.RISC_V.get() && devices.any { device -> device.appPreferredAbi != null && device.abis.size > 1 }) {
      notification.invoke("Preferred ABI", "Preferred ABI may not be respected when building for multiple devices.")
    }
    log.info("Creating spec for multiple devices")
  }

  val deviceSerials = devices.mapNotNull { if (it.isRunning) it.launchedDevice.get().serialNumber else null }
  val allDevicesSupportSdkRuntime = devices.all { it.supportsSdkRuntime }
  if (allDevicesSupportSdkRuntime) {
    log.info("Creating spec for privacy sandbox enabled device.")
  }
  else {
    log.info("Creating spec for device without privacy sandbox support.")
  }

  val deviceSpec = AndroidDeviceSpecImpl(version, minVersion, density, abis, supportsSdkRuntimeProvider = { allDevicesSupportSdkRuntime },
                                         languagesProvider = { combineDeviceLanguages(devices, timeout, unit) },
                                         deviceSerials = deviceSerials)

  return ProcessedDeviceSpec.SingleDeviceSpec.TargetDeviceSpec(deviceSpec)
}

/**
 * Retrieve the list of installed languages on each device and merge them into a set.
 *
 * Note: We should be able to cache the result of this call to improve performance,
 * i.e. to avoid issuing an ADB call every time, but we first need to define a
 * reasonable cache validation policy, which is not trivial. See b/78452155.
 */
private fun combineDeviceLanguages(devices: List<AndroidDevice>, timeout: Long, unit: TimeUnit): List<String> {
  return try {
    // Note: the "getLanguages" method below uses Duration.Zero as an infinite timeout value.
    // So we must ensure to never use 0 "nanos" if the caller wants the minimal timeout.
    val nanos = Math.max(1, unit.toNanos(timeout))
    val duration = Duration.ofNanos(nanos)
    val languageSets = devices.map {
      it.launchedDevice.get(timeout, unit).getLanguages(duration)
    }
    // Note: If we get an empty list from any device, we want to return an empty list instead of the
    // union of all languages, to ensure we don't have missing language splits on that device.
    if (languageSets.any { it.isEmpty() }) {
      emptyList()
    }
    else {
      languageSets.flatten().sorted()
    }
  }
  catch (e: Exception) {
    throw RuntimeException("Error retrieving list of installed languages on device", e)
  }
}

/**
 * message DeviceSpec {
 *   // Supported ABI architectures in the order of preference.
 *   // The values should be the string as reported by the platform, e.g.
 *   // "armeabi-v7a" or "x86_64".
 *   repeated string supported_abis = 1;
 *
 *   // All installed locales represented as BCP-47 strings.
 *   repeated string supported_locales = 2;
 *
 *   // List of device features returned by the package manager utility.
 *   repeated string device_features = 3;
 *
 *   // List of OpenGL extension strings supported by the device.
 *   repeated string gl_extensions = 4;
 *
 *   // Screen dpi.
 *   uint32 screen_density = 5;
 *
 *   uint32 sdk_version = 6;
 * }
 */
private fun AndroidDeviceSpec.writeJson(writeLanguages: Boolean, out: Writer, moduleAgpVersions: List<AgpVersion>) {
  JsonWriter(out).use { writer ->
    writer.beginObject()
    commonVersion?.let {
      writer.name("sdk_version").value(it.apiLevel.toLong())
      it.codename?.let { codename ->
        writer.name("codename").value(codename)
      }
    }
    density?.let {
      if (it.dpiValue > 0) {
        writer.name("screen_density").value(it.dpiValue.toLong())
      }
    }
    if (!abis.isEmpty()) {
      writer.name("supported_abis")
      writer.beginArray()
      abis.forEach {
        writer.value(it)
      }
      writer.endArray()
    }
    if (supportsSdkRuntime && // The DeviceConfig 'sdk_runtime' field exists in > AGP 7.4.0, the field is not recognised by older AGP versions.
        moduleAgpVersions.all { it.isAtLeast(7, 4, 0) }) {
      writer.name("sdk_runtime").beginObject().name("supported").value(supportsSdkRuntime).endObject()
    }
    if (writeLanguages) {
      if (!languages.isEmpty()) {
        writer.name("supported_locales")
        writer.beginArray()
        languages.forEach {
          writer.value(it)
        }
        writer.endArray()
      }
    }
    writer.endObject()
  }
}

private val log: Logger
  get() = Logger.getInstance(AndroidDeviceSpec::class.java)