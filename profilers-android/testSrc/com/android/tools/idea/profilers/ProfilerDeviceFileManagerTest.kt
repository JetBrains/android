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
package com.android.tools.idea.profilers

import com.android.ddmlib.IDevice
import com.android.sdklib.devices.Abi
import com.android.tools.idea.profilers.ProfilerDeviceFileManager.DEVICE_DIR
import com.google.common.truth.Truth.assertThat

import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify

class ProfilerDeviceFileManagerTest {
  @JvmField
  @Rule
  val temporaryFolder = TemporaryFolder()

  private lateinit var mockDevice: IDevice

  @Before
  fun setUp() {
    mockDevice = mock(IDevice::class.java)
  }

  @Test
  fun testCopyNonExecutableFileToDevice() {
    temporaryFolder.apply {
      newFolder("dev")
      newFile("dev/perfa.jar")
    }

    val hostFile = ProfilerHostFileBuilder("perfa.jar")
      .setReleaseDir("release")
      .setDevDir("dev")
      .setExecutable(false)
      .setHomePathSupplier(temporaryFolder.root::getAbsolutePath)
      .build()

    val hostPathCaptor: ArgumentCaptor<String> = ArgumentCaptor.forClass(String::class.java)
    val devicePathCaptor: ArgumentCaptor<String> = ArgumentCaptor.forClass(String::class.java)

    ProfilerDeviceFileManager(mockDevice).copyFileToDevice(hostFile)
    verify(mockDevice, times(1)).pushFile(hostPathCaptor.capture(), devicePathCaptor.capture())

    val expectedPaths = listOf(
      Pair("dev/perfa.jar", "perfa.jar")
    ).map { (host, device) ->
      // maps from relative paths to absolute paths
      Pair("${temporaryFolder.root.absolutePath}/$host", DEVICE_DIR + device)
    }

    assertThat(hostPathCaptor.allValues).containsExactlyElementsIn(expectedPaths.map { it.first })
    assertThat(devicePathCaptor.allValues).containsExactlyElementsIn(expectedPaths.map { it.second })
  }

  @Test
  fun testCopyExecutableAbiIndependentFileToDevice() {
    temporaryFolder.apply {
      newFolder("dev")

      listOf(Abi.X86, Abi.ARMEABI, Abi.ARMEABI_V7A).forEach {
        newFolder("dev", it.toString())
      }
      listOf(Abi.ARMEABI, Abi.ARMEABI_V7A).forEach {
        newFile("dev/$it/perfd")
      }
    }

    val hostFile = ProfilerHostFileBuilder("perfd")
      .setReleaseDir("release")
      .setDevDir("dev")
      .setExecutable(true)
      .setHomePathSupplier(temporaryFolder.root::getAbsolutePath)
      .build()

    `when`(mockDevice.abis).thenReturn(listOf(
      // it will be ignored, because there is no perfd under it.
      Abi.X86,
      // it will be used.
      Abi.ARMEABI_V7A,
      // it will be ignored, because we only need one ABI. |IDevice#getAbis| are sorted in preferred order,
      // so it should choose |Abi.ARMEABI_V7A| instead.
      Abi.ARMEABI
    ).map { it.toString() })

    val hostPathCaptor: ArgumentCaptor<String> = ArgumentCaptor.forClass(String::class.java)
    val devicePathCaptor: ArgumentCaptor<String> = ArgumentCaptor.forClass(String::class.java)

    ProfilerDeviceFileManager(mockDevice).copyFileToDevice(hostFile)
    verify(mockDevice, times(1)).pushFile(hostPathCaptor.capture(), devicePathCaptor.capture())

    val expectedPaths = listOf(
      Pair("dev/${Abi.ARMEABI_V7A}/perfd", "perfd")
    ).map { (host, device) ->
      // maps from relative paths to absolute paths
      Pair("${temporaryFolder.root.absolutePath}/$host", DEVICE_DIR + device)
    }

    assertThat(hostPathCaptor.allValues).containsExactlyElementsIn(expectedPaths.map { it.first })
    assertThat(devicePathCaptor.allValues).containsExactlyElementsIn(expectedPaths.map { it.second })
  }

  @Test
  fun testCopyExecutableAbiDependentFileToDevice() {
    temporaryFolder.apply {
      newFolder("dev")

      listOf(Abi.X86, Abi.X86_64, Abi.ARMEABI, Abi.ARMEABI_V7A).forEach {
        newFolder("dev", it.toString())
      }
      listOf(Abi.X86_64, Abi.ARMEABI, Abi.ARMEABI_V7A).forEach {
        newFile("dev/${it}/simpleperf")
      }
    }

    val hostFile = ProfilerHostFileBuilder("simpleperf")
      .setReleaseDir("release")
      .setDevDir("dev")
      .setExecutable(true)
      .setOnDeviceAbiFileNameFormat("simpleperf_%s")
      .setHomePathSupplier(temporaryFolder.root::getAbsolutePath)
      .build()

    `when`(mockDevice.abis).thenReturn(listOf(
      // it will be ignored, because there is no simpleperf under it.
      Abi.X86,
      // it will be used.
      Abi.ARMEABI,
      // it will be ignored, because we only need one ABI per CPU arch.
      // It should choose |Abi.ARMEABI| instead, because it is more preferred and has the same CPU arch.
      Abi.ARMEABI_V7A,
      // it will be used.
      Abi.X86_64
    ).map { it.toString() })

    val hostPathCaptor: ArgumentCaptor<String> = ArgumentCaptor.forClass(String::class.java)
    val devicePathCaptor: ArgumentCaptor<String> = ArgumentCaptor.forClass(String::class.java)

    ProfilerDeviceFileManager(mockDevice).copyFileToDevice(hostFile)
    verify(mockDevice, times(2)).pushFile(hostPathCaptor.capture(), devicePathCaptor.capture())

    val expectedAbis = listOf(
      Abi.ARMEABI,
      Abi.X86_64
    )

    val expectedHostPaths = expectedAbis.map { "${temporaryFolder.root.absolutePath}/dev/${it}/simpleperf" }
    assertThat(hostPathCaptor.allValues).containsExactlyElementsIn(expectedHostPaths)

    val expectedDevicePaths = expectedAbis.map { "${DEVICE_DIR}simpleperf_${it.cpuArch}" }
    assertThat(devicePathCaptor.allValues).containsExactlyElementsIn(expectedDevicePaths)
  }
}