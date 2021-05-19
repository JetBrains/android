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
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations.initMocks
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit

const val MAX_TIMEOUT_MILLISECONDS: Long = 50_000

class AndroidDeviceSpecUtilTest {
  @Mock
  private lateinit var myDevice1: AndroidDevice
  @Mock
  private lateinit var myDevice2: AndroidDevice
  @Mock
  private lateinit var myDevice3: AndroidDevice
  @Mock
  private lateinit var myDevice4: AndroidDevice
  @Mock
  private lateinit var myDevice5: AndroidDevice
  @Mock
  private lateinit var myDevice6: AndroidDevice

  @Mock
  private lateinit var myLaunchedDevice1: IDevice
  @Mock
  private lateinit var myLaunchedDevice2: IDevice
  @Mock
  private lateinit var myLaunchedDevice3: IDevice
  @Mock
  private lateinit var myLaunchedDevice4: IDevice
  @Mock
  private lateinit var myLaunchedDevice5: IDevice
  @Mock
  private lateinit var myLaunchedDevice6: IDevice

  private var myFile: File? = null

  @Before
  fun setUp() {
    initMocks(this)

    `when`(myDevice1.version).thenReturn(AndroidVersion(20))
    `when`(myLaunchedDevice1.version).thenReturn(AndroidVersion(20))
    `when`(myDevice1.density).thenReturn(Density.XXHIGH.dpiValue)
    `when`(myDevice1.abis).thenReturn(arrayListOf(Abi.X86, Abi.X86_64))
    `when`(myDevice1.launchedDevice).thenReturn(Futures.immediateFuture(myLaunchedDevice1))

    `when`(myDevice2.version).thenReturn(AndroidVersion(22))
    `when`(myLaunchedDevice2.version).thenReturn(AndroidVersion(22))
    `when`(myDevice2.density).thenReturn(Density.DPI_340.dpiValue)
    `when`(myDevice2.abis).thenReturn(arrayListOf(Abi.ARMEABI))
    `when`(myDevice2.launchedDevice).thenReturn(Futures.immediateFuture(myLaunchedDevice2))
    setupDeviceConfig(myLaunchedDevice2, "  config: mcc310-mnc410-es-rUS,fr-rFR-ldltr-sw411dp-w411dp-h746dp-normal-long-notround" +
                      "-lowdr-nowidecg-port-notnight-560dpi-finger-keysexposed-nokeys-navhidden-nonav-v27")

    `when`(myDevice3.version).thenReturn(AndroidVersion(16))
    `when`(myLaunchedDevice3.version).thenReturn(AndroidVersion(16))
    `when`(myDevice3.density).thenReturn(Density.DPI_260.dpiValue)
    `when`(myDevice3.abis).thenReturn(arrayListOf(Abi.MIPS))
    `when`(myDevice3.launchedDevice).thenReturn(Futures.immediateFuture(myLaunchedDevice3))

    `when`(myDevice4.version).thenReturn(AndroidVersion(16, "codename"))
    `when`(myLaunchedDevice4.version).thenReturn(AndroidVersion(16, "codename"))
    `when`(myDevice4.density).thenReturn(Density.DPI_260.dpiValue)
    `when`(myDevice4.abis).thenReturn(arrayListOf(Abi.MIPS))
    `when`(myDevice4.launchedDevice).thenReturn(Futures.immediateFuture(myLaunchedDevice4))

    `when`(myDevice5.version).thenReturn(AndroidVersion(22))
    `when`(myLaunchedDevice5.version).thenReturn(AndroidVersion(22))
    `when`(myDevice5.density).thenReturn(Density.DPI_260.dpiValue)
    `when`(myDevice5.abis).thenReturn(arrayListOf(Abi.MIPS))
    `when`(myDevice5.launchedDevice).thenReturn(Futures.immediateFuture(myLaunchedDevice5))

    `when`(myDevice6.version).thenReturn(AndroidVersion(29, "R"))
    `when`(myLaunchedDevice6.version).thenReturn(AndroidVersion(29, "R"))
    `when`(myDevice6.density).thenReturn(Density.XXHIGH.dpiValue)
    `when`(myDevice6.abis).thenReturn(arrayListOf(Abi.X86, Abi.X86_64))
    `when`(myDevice6.launchedDevice).thenReturn(Futures.immediateFuture(myLaunchedDevice6))
  }

  private fun setupDeviceConfig(device: IDevice, config: String) {
    `when`(device.executeShellCommand(Mockito.anyString(),
                                      Mockito.any(),
                                      Mockito.anyLong(),
                                      Mockito.any())).thenAnswer {
      // get the 2nd arg (the receiver to feed it the lines).
      val receiver = it.arguments[1] as IShellOutputReceiver
      val byteArray = "$config\n".toByteArray(Charsets.UTF_8)
      receiver.addOutput(byteArray, 0, byteArray.size)
    }
  }

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
  fun featureLevelCorrect() {
    assertThat(myDevice2.version.featureLevel).isEqualTo(22)
    assertThat(myDevice3.version.featureLevel).isEqualTo(16)
    assertThat(myDevice4.version.featureLevel).isEqualTo(17)

    val spec2And4 = createSpec(listOf(myDevice2, myDevice4), MAX_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)!!
    assertThat(spec2And4.minVersion.featureLevel).isEqualTo(17)
    assertThat(spec2And4.commonVersion).isNull()

    val spec3And4 = createSpec(listOf(myDevice3, myDevice4), MAX_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)!!
    assertThat(spec3And4.minVersion.featureLevel).isEqualTo(16)
    assertThat(spec3And4.commonVersion).isNull()
  }

  @Test
  fun jsonFileFromDevice1IsCorrect() {
    myFile = createJsonFile(false, myDevice1)
    assertThat(myFile!!.readText()).isEqualTo("{\"sdk_version\":20,\"screen_density\":480,\"supported_abis\":[\"x86\",\"x86_64\"]}")
  }

  @Test
  fun jsonFileFromPreLDeviceDoesNotContainLanguages() {
    myFile = createJsonFile(true, myDevice1)
    assertThat(myFile!!.readText()).isEqualTo("{\"sdk_version\":20,\"screen_density\":480,\"supported_abis\":[\"x86\",\"x86_64\"]}")
  }

  @Test
  fun jsonFileFromDevice2IsCorrect() {
    myFile = createJsonFile(true, myDevice2)
    assertThat(myFile!!.readText())
      .isEqualTo("{\"sdk_version\":22,\"screen_density\":340,\"supported_abis\":[\"armeabi\"],\"supported_locales\":[\"es\",\"fr\"]}")
  }

  @Test
  fun jsonFileFromDevice2DoesNotContainLanguages() {
    myFile = createJsonFile(false, myDevice2)
    assertThat(myFile!!.readText()).isEqualTo("{\"sdk_version\":22,\"screen_density\":340,\"supported_abis\":[\"armeabi\"]}")
  }

  @Test
  fun jsonFileFromDevice1And2IsCorrect() {
    myFile = createJsonFile(true, myDevice1, myDevice2)
    assertThat(myFile!!.readText()).isEqualTo("{}")
  }

  @Test
  fun jsonFileFromDevice2And5IsCorrectAndShouldNotContainLanguages() {
    myFile = createJsonFile(true, myDevice2, myDevice5)
    assertThat(myFile!!.readText()).isEqualTo("{\"sdk_version\":22}")
  }

  @Test
  fun jsonFileFromPreviewDeviceContainsCodeName() {
    myFile = createJsonFile(true, myDevice6)
    assertThat(myFile!!.readText()).isEqualTo("{\"sdk_version\":29,\"codename\":\"R\",\"screen_density\":480,\"supported_abis\":[\"x86\",\"x86_64\"]}")
  }

  private fun createJsonFile(fetchLanguages: Boolean, vararg devices: AndroidDevice): File {
    val spec = createSpec(devices.asList(), MAX_TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)
    return spec!!.writeToJsonTempFile(fetchLanguages)
  }
}
