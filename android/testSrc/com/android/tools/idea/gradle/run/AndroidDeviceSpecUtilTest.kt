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
import com.android.sdklib.devices.Abi
import com.android.tools.idea.run.AndroidDevice
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.Futures
import org.junit.After
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.io.File
import java.util.concurrent.TimeUnit

const val MAX_TIMEOUT_MILLISECONDS: Long = 50_000
private const val EXAMPLE_DEVICE_CONFIG = "  config: mcc310-mnc410-es-rUS,fr-rFR-ldltr-sw411dp-w411dp-h746dp-normal-long-notround" +
                                          "-lowdr-nowidecg-port-notnight-560dpi-finger-keysexposed-nokeys-navhidden-nonav-v27"

class AndroidDeviceSpecUtilTest {

  private var myFile: File? = null

  @After
  fun cleanUp() {
    myFile?.delete()
  }

  @Test
  fun createReturnsNullWhenEmptyList() {
    val spec = createSpec(ArrayList(), MAX_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)
    assertThat(spec).isNull()
  }

  @Test
  fun featureLevelCombinationIsCorrect() {
    val api22Device = mockDevice(AndroidVersion(22))
    val api16Device = mockDevice(AndroidVersion(16))
    val api17Device = mockDevice(AndroidVersion(17))

    val spec17And22 = createSpec(listOf(api22Device, api17Device), MAX_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)!!
    assertThat(spec17And22.minVersion?.featureLevel).isEqualTo(17)
    assertThat(spec17And22.commonVersion).isNull()

    val spec16And17 = createSpec(listOf(api16Device, api17Device), MAX_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)!!
    assertThat(spec16And17.minVersion?.featureLevel).isEqualTo(16)
    assertThat(spec16And17.commonVersion).isNull()
  }

  @Test
  fun previewFeatureLevelCombinationIsCorrect() {
    val api23Device = mockDevice(AndroidVersion(23))
    val previewMDevice = mockDevice(AndroidVersion(22, "M"))

    val spec2And4 = createSpec(listOf(api23Device, previewMDevice), MAX_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)!!
    assertThat(spec2And4.minVersion?.featureLevel).isEqualTo(23)
    assertThat(spec2And4.commonVersion).isNull()

  }

  @Test
  fun ignoresUnknownVersions() {
    val unknownDevice = mockDevice(AndroidVersion.DEFAULT)
    val specUnknown = createSpec(listOf(unknownDevice), MAX_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)!!
    assertThat(specUnknown.minVersion).isNull()
    assertThat(specUnknown.commonVersion).isNull()

  }
  @Test
  fun ignoresUnknownVersionsCombined() {
    val unknownDevice = mockDevice(AndroidVersion.DEFAULT)
    val api22Device = mockDevice(AndroidVersion(22))
    val spec22AndUnknown = createSpec(listOf(api22Device, unknownDevice), MAX_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)!!
    assertThat(spec22AndUnknown.minVersion).isNull()
    assertThat(spec22AndUnknown.commonVersion).isNull()
  }

  @Test
  fun exampleApi20DeviceWithoutLanguageFetchingIsCorrect() {
    val api20Device = mockDevice(AndroidVersion(20), Density.XXHIGH, listOf(Abi.X86, Abi.X86_64))
    myFile = createJsonFile(false, api20Device)
    assertThat(myFile!!.readText()).isEqualTo("{\"sdk_version\":20,\"screen_density\":480,\"supported_abis\":[\"x86\",\"x86_64\"]}")
  }

  @Test
  fun jsonFileFromPreLDeviceDoesNotContainLanguages() {
    val api20Device = mockDevice(AndroidVersion(20), Density.XXHIGH, listOf(Abi.X86, Abi.X86_64))
    myFile = createJsonFile(true, api20Device)
    assertThat(myFile!!.readText()).isEqualTo("{\"sdk_version\":20,\"screen_density\":480,\"supported_abis\":[\"x86\",\"x86_64\"]}")
  }

  @Test
  fun languageFetchingCanBeDisabled() {
    val api21Device = mockDevice(AndroidVersion(21), Density.DPI_340, listOf(Abi.ARMEABI))
    myFile = createJsonFile(false, api21Device)
    assertThat(myFile!!.readText()).isEqualTo("{\"sdk_version\":21,\"screen_density\":340,\"supported_abis\":[\"armeabi\"]}")
  }

  @Test
  fun jsonFileFromPostLDoesContainLanguages() {
    val api21Device = mockDevice(AndroidVersion(21), Density.DPI_340, listOf(Abi.ARMEABI))
    myFile = createJsonFile(true, api21Device)
    assertThat(myFile!!.readText())
      .isEqualTo("{\"sdk_version\":21,\"screen_density\":340,\"supported_abis\":[\"armeabi\"],\"supported_locales\":[\"es\",\"fr\"]}")
  }

  @Test
  fun combiningDisjointDevicePropertiesGivesEmpty() {
    val api20Device = mockDevice(AndroidVersion(20), Density.XXHIGH, listOf(Abi.X86, Abi.X86_64))
    val deviceThatDiffersInEveryWay = mockDevice(AndroidVersion(22), Density.DPI_340, listOf(Abi.ARMEABI))
    myFile = createJsonFile(true, api20Device, deviceThatDiffersInEveryWay)
    assertThat(myFile!!.readText()).isEqualTo("{}")
  }

  @Test
  fun combiningDevicesThatOnlyMatchBySdkVersionGivesJustThat() {
    val api22Device = mockDevice(AndroidVersion(22), Density.DPI_340, listOf(Abi.ARMEABI))
    val deviceThatOnlySharesSdkVersion = mockDevice(AndroidVersion(22), Density.DPI_260, listOf(Abi.MIPS), config = "")

    myFile = createJsonFile(true, api22Device, deviceThatOnlySharesSdkVersion)
    assertThat(myFile!!.readText()).isEqualTo("{\"sdk_version\":22}")
  }

  @Test
  fun jsonFileFromPreviewDeviceContainsCodeName() {
    val previewDevice = mockDevice(AndroidVersion(29, "R"), Density.XXHIGH, listOf(Abi.X86, Abi.X86_64), config = "")
    myFile = createJsonFile(true, previewDevice)
    assertThat(myFile!!.readText()).isEqualTo(
      "{\"sdk_version\":29,\"codename\":\"R\",\"screen_density\":480,\"supported_abis\":[\"x86\",\"x86_64\"]}")
  }

  private fun createJsonFile(fetchLanguages: Boolean, vararg devices: AndroidDevice): File {
    val spec = createSpec(devices.asList(), MAX_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)
    return spec!!.writeToJsonTempFile(fetchLanguages)
  }

  private fun mockDevice(
    version: AndroidVersion,
    density: Density = Density.DPI_260,
    abis: List<Abi> = listOf(Abi.MIPS),
    config: String = EXAMPLE_DEVICE_CONFIG
  ): AndroidDevice {
    val device = mock(AndroidDevice::class.java)
    `when`(device.version).thenReturn(version)
    `when`(device.density).thenReturn(density.dpiValue)
    `when`(device.abis).thenReturn(abis)
    val launchedDevice = mock(IDevice::class.java)
    `when`(launchedDevice.version).thenReturn(version)
    if (config.isNotEmpty()) {
      `when`(launchedDevice.executeShellCommand(Mockito.anyString(),
                                                Mockito.any(),
                                                Mockito.anyLong(),
                                                Mockito.any())).thenAnswer {
        // get the 2nd arg (the receiver to feed it the lines).
        val receiver = it.arguments[1] as IShellOutputReceiver
        val byteArray = "$config\n".toByteArray(Charsets.UTF_8)
        receiver.addOutput(byteArray, 0, byteArray.size)
      }
    }
    `when`(device.launchedDevice).thenReturn(Futures.immediateFuture(launchedDevice))
    return device
  }

}
