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
package com.android.tools.idea.gradle.run

import com.android.ddmlib.IDevice
import com.android.ddmlib.IShellOutputReceiver
import com.android.resources.Density
import com.android.sdklib.AndroidVersion
import com.android.sdklib.AndroidVersion.MIN_RESIZABLE_DEVICE_API
import com.android.sdklib.devices.Abi
import com.android.tools.idea.gradle.run.AndroidDeviceSpecUtilTest.DeviceSpecJson.*
import com.android.tools.idea.run.AndroidDevice
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.util.concurrent.TimeUnit

const val MAX_TIMEOUT_MILLISECONDS: Long = 50_000
private const val EXAMPLE_DEVICE_CONFIG = "  config: mcc310-mnc410-es-rUS,fr-rFR-ldltr-sw411dp-w411dp-h746dp-normal-long-notround" +
                                          "-lowdr-nowidecg-port-notnight-560dpi-finger-keysexposed-nokeys-navhidden-nonav-v27"

@RunWith(Parameterized::class)
class AndroidDeviceSpecUtilTest(private val extractDeviceSpecs: (List<AndroidDevice>, Long, TimeUnit) -> ProcessedDeviceSpec) {

  private var myFile: File? = null


  companion object {
    @JvmStatic
    @Parameterized.Parameters
    fun extractDeviceSpecs() = arrayOf(
      { devices: List<AndroidDevice>, timeout: Long, unit: TimeUnit -> createTargetDeviceSpec(devices, timeout, unit) },
      ::createDeviceSpecs)
  }

  @After
  fun cleanUp() {
    myFile?.delete()
  }

  @Test
  fun createReturnsNullWhenEmptyList() {
    val spec = extractDeviceSpecs(ArrayList(), MAX_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)
    when (spec) {
      is ProcessedDeviceSpec.SingleDeviceSpec.TargetDeviceSpec -> assertThat(spec.deviceSpec).isNull()
      is ProcessedDeviceSpec.MultipleDeviceSpec -> assertThat(spec.deviceSpecs).isEmpty()
      is ProcessedDeviceSpec.SingleDeviceSpec.NoDevices -> {}
    }
  }

  @Test
  fun featureLevelCombination22And17() {
    val api22Device = mockDevice(AndroidVersion(22))
    val api17Device = mockDevice(AndroidVersion(17))

    val spec17And22 = extractDeviceSpecs(listOf(api22Device, api17Device), MAX_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)
    when (spec17And22) {
      is ProcessedDeviceSpec.SingleDeviceSpec.NoDevices -> {}
      is ProcessedDeviceSpec.SingleDeviceSpec.TargetDeviceSpec -> {
        assertThat(spec17And22.deviceSpec.minVersion?.featureLevel).isEqualTo(17)
        assertThat(spec17And22.deviceSpec.commonVersion).isNull()
      }

      is ProcessedDeviceSpec.MultipleDeviceSpec -> {
        assertThat((spec17And22.deviceSpecs[0]).minVersion?.featureLevel).isEqualTo(22)
        assertThat(spec17And22.deviceSpecs[1].minVersion?.featureLevel).isEqualTo(17)
      }
    }
  }

  @Test
  fun featureLevelCombination16And17() {
    val api16Device = mockDevice(AndroidVersion(16))
    val api17Device = mockDevice(AndroidVersion(17))

    val spec16And17 = extractDeviceSpecs(listOf(api16Device, api17Device), MAX_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)
    when (spec16And17) {
      is ProcessedDeviceSpec.SingleDeviceSpec.NoDevices -> {}
      is ProcessedDeviceSpec.SingleDeviceSpec.TargetDeviceSpec -> {
        assertThat(spec16And17.deviceSpec.minVersion?.featureLevel).isEqualTo(16)
        assertThat(spec16And17.deviceSpec.commonVersion).isNull()
      }

      is ProcessedDeviceSpec.MultipleDeviceSpec -> {
        assertThat((spec16And17.deviceSpecs[0]).minVersion?.featureLevel).isEqualTo(16)
        assertThat(spec16And17.deviceSpecs[1].minVersion?.featureLevel).isEqualTo(17)
      }
    }
  }

  @Test
  fun previewFeatureLevelCombinationIsCorrect() {
    val api23Device = mockDevice(AndroidVersion(23))
    val previewMDevice = mockDevice(AndroidVersion(22, "M"))

    val spec2And4 = extractDeviceSpecs(listOf(api23Device, previewMDevice), MAX_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)
    when (spec2And4) {
      is ProcessedDeviceSpec.SingleDeviceSpec.NoDevices -> {}
      is ProcessedDeviceSpec.SingleDeviceSpec.TargetDeviceSpec -> {
        assertThat(spec2And4.deviceSpec.minVersion?.featureLevel).isEqualTo(23)
        assertThat(spec2And4.deviceSpec.commonVersion).isNull()
      }

      is ProcessedDeviceSpec.MultipleDeviceSpec -> {
        assertThat((spec2And4.deviceSpecs[0]).minVersion?.featureLevel).isEqualTo(23)
        assertThat(spec2And4.deviceSpecs[1].minVersion?.featureLevel).isEqualTo(23)
      }
    }
  }

  @Test
  fun ignoresUnknownVersions() {
    val unknownDevice = mockDevice(AndroidVersion.DEFAULT)
    val specUnknown = extractDeviceSpecs(listOf(unknownDevice), MAX_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)
    when (specUnknown) {
      is ProcessedDeviceSpec.SingleDeviceSpec.NoDevices -> {}
      is ProcessedDeviceSpec.SingleDeviceSpec.TargetDeviceSpec -> {
        assertThat(specUnknown.deviceSpec.minVersion).isNull()
        assertThat(specUnknown.deviceSpec.commonVersion).isNull()
      }

      is ProcessedDeviceSpec.MultipleDeviceSpec -> {
        assertThat(specUnknown.deviceSpecs.single().minVersion).isNull()
      }
    }
  }

  @Test
  fun ignoresUnknownVersionsCombined() {
    val unknownDevice = mockDevice(AndroidVersion.DEFAULT)
    val api22Device = mockDevice(AndroidVersion(22))
    val spec22AndUnknown = extractDeviceSpecs(listOf(api22Device, unknownDevice), MAX_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)
    when (spec22AndUnknown) {
      is ProcessedDeviceSpec.SingleDeviceSpec.NoDevices -> {}
      is ProcessedDeviceSpec.SingleDeviceSpec.TargetDeviceSpec -> {
        assertThat(spec22AndUnknown.deviceSpec.minVersion).isNull()
        assertThat(spec22AndUnknown.deviceSpec.commonVersion).isNull()
      }

      is ProcessedDeviceSpec.MultipleDeviceSpec -> {
        assertThat(spec22AndUnknown.deviceSpecs[0].minVersion?.featureLevel).isEqualTo(22)
        assertThat(spec22AndUnknown.deviceSpecs[1].minVersion).isNull()
      }
    }
  }

  @Test
  fun exampleApi20DeviceWithoutLanguageFetchingIsCorrect() {
    val api20Device = mockDevice(AndroidVersion(20), Density.XXHIGH, listOf(Abi.X86, Abi.X86_64))
    val deviceSpecJson = createJsonFile(false, api20Device)

    val jsonContent = when (deviceSpecJson) {
      is TargetDeviceSpec -> deviceSpecJson.jsonFile.readText()
      is MultipleDeviceSpec -> deviceSpecJson.jsonFiles.single().readText()
      NoDeviceSpec -> throw IllegalStateException("No device spec found.")
    }
    assertThat(jsonContent).isEqualTo(
      "{\"sdk_version\":20,\"screen_density\":480,\"supported_abis\":[\"x86\",\"x86_64\"]}")
  }

  @Test
  fun jsonFileFromPreLDeviceDoesNotContainLanguages() {
    val api20Device = mockDevice(AndroidVersion(20), Density.XXHIGH, listOf(Abi.X86, Abi.X86_64))
    val deviceSpecJson = createJsonFile(true, api20Device)

    val jsonContent = when (deviceSpecJson) {
      is TargetDeviceSpec -> deviceSpecJson.jsonFile.readText()
      is MultipleDeviceSpec -> deviceSpecJson.jsonFiles.single().readText()
      NoDeviceSpec -> throw IllegalStateException("No device spec found.")
    }
    assertThat(jsonContent).isEqualTo("{\"sdk_version\":20,\"screen_density\":480,\"supported_abis\":[\"x86\",\"x86_64\"]}")
  }

  @Test
  fun languageFetchingCanBeDisabled() {
    val api21Device = mockDevice(AndroidVersion(21), Density.create(340), listOf(Abi.ARMEABI))
    val deviceSpecJson = createJsonFile(false, api21Device)

    val jsonContent = when (deviceSpecJson) {
      is TargetDeviceSpec -> deviceSpecJson.jsonFile.readText()
      is MultipleDeviceSpec -> deviceSpecJson.jsonFiles.single().readText()
      NoDeviceSpec -> throw IllegalStateException("No device spec found.")
    }
    assertThat(jsonContent).isEqualTo("{\"sdk_version\":21,\"screen_density\":340,\"supported_abis\":[\"armeabi\"]}")
  }

  @Test
  fun jsonFileFromPostLDoesContainLanguages() {
    val api21Device = mockDevice(AndroidVersion(21), Density.create(340), listOf(Abi.ARMEABI))
    val deviceSpecJson = createJsonFile(true, api21Device)

    val jsonContent = when (deviceSpecJson) {
      is TargetDeviceSpec -> deviceSpecJson.jsonFile.readText()
      is MultipleDeviceSpec -> deviceSpecJson.jsonFiles.single().readText()
      NoDeviceSpec -> throw IllegalStateException("No device spec found.")
    }
    assertThat(jsonContent).isEqualTo(
      "{\"sdk_version\":21,\"screen_density\":340,\"supported_abis\":[\"armeabi\"],\"supported_locales\":[\"es\",\"fr\"]}")
  }

  @Test
  fun combiningDisjointDevicePropertiesGivesEmpty() {
    val api20Device = mockDevice(AndroidVersion(20), Density.XXHIGH, listOf(Abi.X86, Abi.X86_64))
    val deviceThatDiffersInEveryWay = mockDevice(AndroidVersion(22), Density.create(340), listOf(Abi.ARMEABI))
    val deviceSpecJson = createJsonFile(true, api20Device, deviceThatDiffersInEveryWay)

    when (deviceSpecJson) {
      is TargetDeviceSpec -> {
        assertThat(deviceSpecJson.jsonFile.readText()).isEqualTo("{}")
      }

      is MultipleDeviceSpec -> {
        assertThat(deviceSpecJson.jsonFiles[0].readText()).isEqualTo(
          "{\"sdk_version\":20,\"screen_density\":480,\"supported_abis\":[\"x86\",\"x86_64\"]}")
        assertThat(deviceSpecJson.jsonFiles[1].readText()).isEqualTo(
          "{\"sdk_version\":22,\"screen_density\":340,\"supported_abis\":[\"armeabi\"],\"supported_locales\":[\"es\",\"fr\"]}")
      }

      NoDeviceSpec -> throw IllegalStateException("No device spec found.")
    }
  }

  @Test
  fun combiningDevicesThatOnlyMatchBySdkVersionGivesJustThat() {
    val api22Device = mockDevice(AndroidVersion(22), Density.create(340), listOf(Abi.ARMEABI))
    val deviceThatOnlySharesSdkVersion = mockDevice(AndroidVersion(22), Density.create(260), listOf(Abi.MIPS), config = "")
    val deviceSpecJson = createJsonFile(true, api22Device, deviceThatOnlySharesSdkVersion)

    when (deviceSpecJson) {
      is TargetDeviceSpec -> {
        assertThat(deviceSpecJson.jsonFile.readText()).isEqualTo("{\"sdk_version\":22}")
      }

      is MultipleDeviceSpec -> {
        assertThat(deviceSpecJson.jsonFiles[0].readText()).isEqualTo(
          "{\"sdk_version\":22,\"screen_density\":340,\"supported_abis\":[\"armeabi\"],\"supported_locales\":[\"es\",\"fr\"]}")
        assertThat(deviceSpecJson.jsonFiles[1].readText()).isEqualTo(
          "{\"sdk_version\":22,\"screen_density\":260,\"supported_abis\":[\"mips\"]}")
      }

      NoDeviceSpec -> throw IllegalStateException("No device spec found.")
    }
  }

  @Test
  fun jsonFileFromPreviewDeviceContainsCodeName() {
    val previewDevice = mockDevice(AndroidVersion(29, "R"), Density.XXHIGH, listOf(Abi.X86, Abi.X86_64), config = "")
    val deviceSpecJson = createJsonFile(true, previewDevice)

    val jsonContent = when (deviceSpecJson) {
      is TargetDeviceSpec -> deviceSpecJson.jsonFile.readText()
      is MultipleDeviceSpec -> deviceSpecJson.jsonFiles.single().readText()
      NoDeviceSpec -> throw IllegalStateException("No device spec found.")
    }
    assertThat(jsonContent).isEqualTo(
      "{\"sdk_version\":29,\"codename\":\"R\",\"screen_density\":480,\"supported_abis\":[\"x86\",\"x86_64\"]}")
  }

  @Test
  fun jsonFileFromPrivacySandboxSupportingDevice() {
    val version33ext4 = AndroidVersion(33, null, 4, false)
    val privacySandboxSupportedDevice = mockDevice(version33ext4, Density.XXXHIGH, listOf(Abi.X86_64), supportsPrivacySandbox = true)
    val deviceSpecJson = createJsonFile(true, privacySandboxSupportedDevice)
    val privacySandboxSupportedDeviceSpec = extractDeviceSpecs(listOf(privacySandboxSupportedDevice), MAX_TIMEOUT_MILLISECONDS,
                                                               TimeUnit.MILLISECONDS)

    when (privacySandboxSupportedDeviceSpec) {
      is ProcessedDeviceSpec.SingleDeviceSpec.NoDevices -> {}
      is ProcessedDeviceSpec.SingleDeviceSpec.TargetDeviceSpec -> {
        assertThat(privacySandboxSupportedDeviceSpec.deviceSpec.supportsSdkRuntime).isTrue()
        val jsonFile = (deviceSpecJson as TargetDeviceSpec).jsonFile
        assertThat(jsonFile.readText()).isEqualTo(
          "{\"sdk_version\":33,\"screen_density\":640,\"supported_abis\":[\"x86_64\"],\"sdk_runtime\":{\"supported\":true},\"supported_locales\":[\"es\",\"fr\"]}"
        )
      }

      is ProcessedDeviceSpec.MultipleDeviceSpec -> {
        assertThat(privacySandboxSupportedDeviceSpec.deviceSpecs.single().supportsSdkRuntime).isTrue()
        val jsonFile = (deviceSpecJson as MultipleDeviceSpec).jsonFiles.single()
        assertThat(jsonFile.readText()).isEqualTo(
          "{\"sdk_version\":33,\"screen_density\":640,\"supported_abis\":[\"x86_64\"],\"sdk_runtime\":{\"supported\":true},\"supported_locales\":[\"es\",\"fr\"]}"
        )
      }
    }
  }

  @Test
  fun combiningDevicesWithAndWithoutPrivacySandboxSupport() {
    val version33ext4 = AndroidVersion(33, null, 4, false)
    val supportedPrivacySandboxDevice = mockDevice(version33ext4, Density.XXXHIGH, listOf(Abi.X86_64), supportsPrivacySandbox = true)

    val version33 = AndroidVersion(33)
    val unSupportedPrivacySandboxDevice = mockDevice(version33, Density.XXXHIGH, listOf(Abi.X86_64), supportsPrivacySandbox = false)

    val deviceSpec = extractDeviceSpecs(listOf(supportedPrivacySandboxDevice, unSupportedPrivacySandboxDevice), MAX_TIMEOUT_MILLISECONDS,
                                        TimeUnit.MILLISECONDS)

    when (deviceSpec) {
      is ProcessedDeviceSpec.SingleDeviceSpec.NoDevices -> {}
      is ProcessedDeviceSpec.SingleDeviceSpec.TargetDeviceSpec -> {
        assertThat(deviceSpec.deviceSpec.supportsSdkRuntime).isFalse()
      }

      is ProcessedDeviceSpec.MultipleDeviceSpec -> {
        assertThat(deviceSpec.deviceSpecs[0].supportsSdkRuntime).isTrue()
        assertThat(deviceSpec.deviceSpecs[1].supportsSdkRuntime).isFalse()
      }
    }
  }

  @Test
  fun densityOptimizationDisabledForResizableAndMultipleDevices() {
    val lowDensityDevice = mockDevice(AndroidVersion.DEFAULT, Density.LOW)
    val highDensityDevice = mockDevice(AndroidVersion.DEFAULT, Density.HIGH)
    val resizableDevice = mockDevice(AndroidVersion(MIN_RESIZABLE_DEVICE_API), resizeable = true)

    val spec1 = extractDeviceSpecs(listOf(highDensityDevice), MAX_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)
    when (spec1) {
      is ProcessedDeviceSpec.SingleDeviceSpec.NoDevices -> {}
      is ProcessedDeviceSpec.SingleDeviceSpec.TargetDeviceSpec -> {
        assertThat(spec1.deviceSpec.density).isNotNull()
      }
      is ProcessedDeviceSpec.MultipleDeviceSpec -> {
        assertThat(spec1.deviceSpecs[0].density).isNotNull()
      }
    }

    val spec2 = extractDeviceSpecs(listOf(resizableDevice), MAX_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)
    when (spec2) {
      is ProcessedDeviceSpec.SingleDeviceSpec.NoDevices -> {}
      is ProcessedDeviceSpec.SingleDeviceSpec.TargetDeviceSpec -> {
        assertThat(spec2.deviceSpec.density).isNull()
      }
      is ProcessedDeviceSpec.MultipleDeviceSpec -> {
        assertThat(spec2.deviceSpecs[0].density).isNull()
      }
    }

    val spec3 = extractDeviceSpecs(listOf(highDensityDevice, resizableDevice), MAX_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)
    when (spec3) {
      is ProcessedDeviceSpec.SingleDeviceSpec.NoDevices -> {}
      is ProcessedDeviceSpec.SingleDeviceSpec.TargetDeviceSpec -> {
        assertThat(spec3.deviceSpec.density).isNull()
      }
      is ProcessedDeviceSpec.MultipleDeviceSpec -> {
        assertThat(spec3.deviceSpecs[0].density).isNotNull()
        assertThat(spec3.deviceSpecs[1].density).isNull()
      }
    }

    val spec4 = extractDeviceSpecs(listOf(highDensityDevice, lowDensityDevice), MAX_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)
    when (spec4) {
      is ProcessedDeviceSpec.SingleDeviceSpec.NoDevices -> {}
      is ProcessedDeviceSpec.SingleDeviceSpec.TargetDeviceSpec -> {
        assertThat(spec4.deviceSpec.density).isNull()
      }
      is ProcessedDeviceSpec.MultipleDeviceSpec -> {
        assertThat(spec4.deviceSpecs[0].density).isNotNull()
        assertThat(spec4.deviceSpecs[1].density).isNotNull()
      }
    }
  }

  @Test
  fun `test density injection disabled for resizable emulator`() {
    val resizeableDevice = mockDevice(version = AndroidVersion.DEFAULT, density = Density.HIGH, resizeable = true)
    val specResizable = extractDeviceSpecs(listOf(resizeableDevice), MAX_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)

    val specs = when (specResizable) {
      is ProcessedDeviceSpec.SingleDeviceSpec.NoDevices -> {}
      is ProcessedDeviceSpec.SingleDeviceSpec.TargetDeviceSpec -> specResizable.deviceSpec.density
      is ProcessedDeviceSpec.MultipleDeviceSpec -> specResizable.deviceSpecs.single().density
    }
    assertThat(specs).isNull()
  }

  @Test
  fun `test abi injection enabled for resizable emulator`() {
    val resizeableDevice = mockDevice(version = AndroidVersion.DEFAULT,
                                      density = Density.XXHIGH,
                                      resizeable = true,
                                      abis = listOf(Abi.X86, Abi.X86_64))
    val specResizable = extractDeviceSpecs(listOf(resizeableDevice), MAX_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)

    val deviceSpec = when (specResizable) {
      is ProcessedDeviceSpec.SingleDeviceSpec.NoDevices -> error("No Device Spec")
      is ProcessedDeviceSpec.SingleDeviceSpec.TargetDeviceSpec -> specResizable.deviceSpec
      is ProcessedDeviceSpec.MultipleDeviceSpec -> specResizable.deviceSpecs.single()
    }
    assertThat(deviceSpec.abis).contains("x86")
    assertThat(deviceSpec.abis).contains("x86_64")
  }

  @Test
  fun `test preferred ABI is respected`() {
    val preferredAbiDevice = mockDevice(version = AndroidVersion.DEFAULT, density = Density.XXHIGH, resizeable = true,
                                        abis = listOf(Abi.X86, Abi.RISCV64), preferredAbi = Abi.RISCV64.toString())
    val spec = extractDeviceSpecs(listOf(preferredAbiDevice), MAX_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)

    val specs = when (spec) {
      is ProcessedDeviceSpec.SingleDeviceSpec.NoDevices -> emptyList()
      is ProcessedDeviceSpec.SingleDeviceSpec.TargetDeviceSpec -> spec.deviceSpec.abis
      is ProcessedDeviceSpec.MultipleDeviceSpec -> spec.deviceSpecs.single().abis
    }
    assertThat(specs).containsExactly(Abi.RISCV64.toString())
  }

  @Test
  fun `test no preferred ABI falls back to supported ABIs`() {
    val preferredAbiDevice = mockDevice(version = AndroidVersion.DEFAULT, density = Density.XXHIGH, resizeable = true,
                                        abis = listOf(Abi.X86, Abi.RISCV64))
    val spec = extractDeviceSpecs(listOf(preferredAbiDevice), MAX_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)

    val specs = when (spec) {
      is ProcessedDeviceSpec.SingleDeviceSpec.NoDevices -> emptyList()
      is ProcessedDeviceSpec.SingleDeviceSpec.TargetDeviceSpec -> spec.deviceSpec.abis
      is ProcessedDeviceSpec.MultipleDeviceSpec -> spec.deviceSpecs.single().abis
    }
    assertThat(specs).containsExactly(Abi.X86.toString(), Abi.RISCV64.toString())
  }

  @Test
  fun `test multiple ABIs creates warning`() {
    val device1 = mockDevice(version = AndroidVersion.DEFAULT, density = Density.XXHIGH, resizeable = true,
                             abis = listOf(Abi.X86, Abi.X86_64))
    val device2 = mockDevice(version = AndroidVersion.DEFAULT, density = Density.XXHIGH, resizeable = true,
                             abis = listOf(Abi.X86, Abi.RISCV64), preferredAbi = Abi.RISCV64.toString())
    val spec = extractDeviceSpecs(listOf(device1, device2), MAX_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)

    when (spec) {
      is ProcessedDeviceSpec.SingleDeviceSpec.NoDevices -> {}
      is ProcessedDeviceSpec.SingleDeviceSpec.TargetDeviceSpec -> {
        assertThat(spec.deviceSpec.abis).isEmpty()
      }

      is ProcessedDeviceSpec.MultipleDeviceSpec -> {
        assertThat(spec.deviceSpecs[0].abis).containsExactly(Abi.X86.toString(), Abi.X86_64.toString())
        assertThat(spec.deviceSpecs[1].abis).containsExactly(Abi.RISCV64.toString())
      }
    }
  }

  @Test
  fun testWriteJsonTempFileGivesProperNameAndContent() {
    val spec = AndroidDeviceSpecImpl(
      commonVersion = AndroidVersion(20),
      minVersion = AndroidVersion(20),
      density = Density.XXHIGH,
      abis = listOf("x86", "x86_64"),
      deviceSerials = emptyList(),
    )

    val tempFile = ProcessedDeviceSpec.SingleDeviceSpec.TargetDeviceSpec(spec)
      .writeToJsonTempFile(writeLanguages = true)
    val fileName = tempFile.name
    val fileContent = tempFile.readText()

    assertThat(fileName).isEqualTo("target-device-spec.json")
    assertThat(fileContent).contains(
      "{\"sdk_version\":20,\"screen_density\":480,\"supported_abis\":[\"x86\",\"x86_64\"]}"
    )
  }

  @Test
  fun testWriteMultipleJsonTempFilesGivesProperNamesAndContents() {
    val specApi21 = AndroidDeviceSpecImpl(
      commonVersion = AndroidVersion(21),
      minVersion = AndroidVersion(21),
      density = Density.LOW,
      abis = listOf("arm"),
      deviceSerials = emptyList()
    )
    val specApi29 = AndroidDeviceSpecImpl(
      commonVersion = AndroidVersion(29),
      minVersion = AndroidVersion(29),
      density = Density.XXHIGH,
      abis = listOf("x86"),
      deviceSerials = emptyList()
    )

    val files = ProcessedDeviceSpec
      .MultipleDeviceSpec(listOf(specApi21, specApi29)).writeToMultipleJsonTempFiles(false)

    val file1 = files[0]
    assertThat(file1.name).isEqualTo("device-spec-0.json")
    assertThat(file1.readText()).contains("{\"sdk_version\":21,\"screen_density\":120,\"supported_abis\":[\"arm\"]}")

    val file2 = files[1]
    assertThat(file2.name).isEqualTo("device-spec-1.json")
    assertThat(file2.readText()).contains("{\"sdk_version\":29,\"screen_density\":480,\"supported_abis\":[\"x86\"]}")
  }

  private sealed class DeviceSpecJson {
    class TargetDeviceSpec(val jsonFile: File) : DeviceSpecJson()
    class MultipleDeviceSpec(val jsonFiles: List<File>) : DeviceSpecJson()
    object NoDeviceSpec : DeviceSpecJson()

  }

  private fun createJsonFile(fetchLanguages: Boolean, vararg devices: AndroidDevice): DeviceSpecJson {
    return when (val spec = extractDeviceSpecs(devices.asList(), MAX_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)) {
      is ProcessedDeviceSpec.SingleDeviceSpec.TargetDeviceSpec -> {
        val file = spec.writeToJsonTempFile(fetchLanguages)
        TargetDeviceSpec(file)
      }

      is ProcessedDeviceSpec.MultipleDeviceSpec -> {
        val files = spec.writeToMultipleJsonTempFiles(fetchLanguages)
        MultipleDeviceSpec(files)
      }

      is ProcessedDeviceSpec.SingleDeviceSpec.NoDevices -> {
        NoDeviceSpec
      }
    }
  }

  private fun mockDevice(
    version: AndroidVersion,
    density: Density = Density.create(260),
    abis: List<Abi> = listOf(Abi.MIPS),
    preferredAbi: String? = null,
    config: String = EXAMPLE_DEVICE_CONFIG,
    resizeable: Boolean = false,
    supportsPrivacySandbox: Boolean = false,
  ): AndroidDevice {
    val device = mock(AndroidDevice::class.java)
    whenever(device.version).thenReturn(version)
    whenever(device.density).thenReturn(density.dpiValue)
    whenever(device.abis).thenReturn(abis)
    whenever(device.appPreferredAbi).thenReturn(preferredAbi)
    whenever(device.supportsMultipleScreenFormats()).thenReturn(resizeable)
    whenever(device.supportsSdkRuntime).thenReturn(supportsPrivacySandbox)
    val launchedDevice = mock(IDevice::class.java)
    whenever(launchedDevice.version).thenReturn(version)
    if (config.isNotEmpty()) {
      whenever(launchedDevice.executeShellCommand(Mockito.anyString(),
                                                  Mockito.any(),
                                                  Mockito.anyLong(),
                                                  Mockito.any())).thenAnswer {
        // get the 2nd arg (the receiver to feed it the lines).
        val receiver = it.arguments[1] as IShellOutputReceiver
        val byteArray = "$config\n".toByteArray(Charsets.UTF_8)
        receiver.addOutput(byteArray, 0, byteArray.size)
      }
    }
    whenever(device.launchedDevice).thenReturn(Futures.immediateFuture(launchedDevice))
    return device
  }
}