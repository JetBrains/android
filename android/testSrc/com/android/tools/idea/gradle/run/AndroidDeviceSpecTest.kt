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

import com.android.resources.Density
import com.android.sdklib.AndroidVersion
import com.android.sdklib.devices.Abi
import com.android.tools.idea.run.AndroidDevice
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations.initMocks
import java.io.File
import java.util.*

class AndroidDeviceSpecTest {
  @Mock
  private lateinit var myDevice1: AndroidDevice
  @Mock
  private lateinit var myDevice2: AndroidDevice
  @Mock
  private lateinit var myDevice3: AndroidDevice

  private var myFile: File? = null

  @Before
  fun setUp() {
    initMocks(this)

    `when`(myDevice1.version).thenReturn(AndroidVersion(20))
    `when`(myDevice1.density).thenReturn(Density.XXHIGH.dpiValue)
    `when`(myDevice1.abis).thenReturn(arrayListOf(Abi.X86, Abi.X86_64))

    `when`(myDevice2.version).thenReturn(AndroidVersion(22))
    `when`(myDevice2.density).thenReturn(Density.DPI_340.dpiValue)
    `when`(myDevice2.abis).thenReturn(arrayListOf(Abi.ARMEABI))

    `when`(myDevice3.version).thenReturn(AndroidVersion(16))
    `when`(myDevice3.density).thenReturn(Density.DPI_260.dpiValue)
    `when`(myDevice3.abis).thenReturn(arrayListOf(Abi.MIPS))
  }

  @After
  fun cleanUp() {
    myFile?.delete()
  }

  @Test
  fun createReturnsNullWhenEmptyList() {
    val spec = AndroidDeviceSpec.create(ArrayList())
    assertThat(spec).isNull()
  }

  @Test
  fun jsonFileFromDevice1IsCorrect() {
    myFile = createJsonFile(myDevice1)
    assertThat(myFile!!.readText()).contains("{\"sdk_version\":20,\"screen_density\":480,\"supported_abis\":[\"x86\",\"x86_64\"]}")
  }

  @Test
  fun jsonFileFromDevice1And2IsCorrect() {
    myFile = createJsonFile(myDevice1, myDevice2)
    assertThat(myFile!!.readText()).contains("{\"sdk_version\":20}")
  }

  @Test
  fun jsonFileFromDevice1And3IsCorrect() {
    myFile = createJsonFile(myDevice1, myDevice3)
    assertThat(myFile!!.readText()).contains("{\"sdk_version\":16}")
  }

  private fun createJsonFile(vararg devices: AndroidDevice): File {
    val spec = AndroidDeviceSpec.create(devices.asList())
    return spec!!.writeToJsonTempFile()
  }
}
