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
package com.android.tools.idea.testartifacts.instrumented.testsuite.adapter

import com.android.ddmlib.IDevice
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.testartifacts.instrumented.testsuite.api.AndroidTestResultListener
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDevice
import com.android.tools.idea.testartifacts.instrumented.testsuite.model.AndroidDeviceType
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ProjectRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.Mockito.times
import org.mockito.MockitoAnnotations.openMocks


@RunWith(JUnit4::class)
class GradleTestResultAdapterTest {

  @get:Rule val projectRule = ProjectRule()

  @Mock private lateinit var mockDevice1: IDevice
  @Mock private lateinit var mockDevice2: IDevice
  @Mock private lateinit var mockListener: AndroidTestResultListener

  @Before
  fun setup() {
    openMocks(this)
    `when`(mockDevice1.serialNumber).thenReturn("mockDevice1SerialNumber")
    `when`(mockDevice1.avdName).thenReturn("mockDevice1AvdName")
    `when`(mockDevice1.version).thenReturn(AndroidVersion(29))
    `when`(mockDevice1.isEmulator).thenReturn(true)
    `when`(mockDevice2.serialNumber).thenReturn("mockDevice2SerialNumber")
    `when`(mockDevice2.avdName).thenReturn("mockDevice2AvdName")
    `when`(mockDevice2.version).thenReturn(AndroidVersion(29))
    `when`(mockDevice2.isEmulator).thenReturn(true)
  }

  @Test
  fun testScheduleTestSuite() {
    val deviceList:ArrayList<IDevice> = ArrayList()
    deviceList.add(mockDevice1)
    deviceList.add(mockDevice2)
    val gradleTestResultAdapter = GradleTestResultAdapter(deviceList, mockListener)

    gradleTestResultAdapter.scheduleTestSuite()

    val captor: ArgumentCaptor<AndroidDevice> = ArgumentCaptor.forClass(AndroidDevice::class.java)
    verify(mockListener, times(2)).onTestSuiteScheduled(capture(captor))
    assertThat(captor.allValues).containsAllOf(device(1), device(2))
  }

  private fun device(deviceNum: Int,
    id: String = "mockDevice${deviceNum}SerialNumber",
                     name: String = "mockDevice${deviceNum}AvdName"): AndroidDevice {
    return AndroidDevice(id, name, name, AndroidDeviceType.LOCAL_EMULATOR, AndroidVersion(29))
  }

  private fun <T> capture(argumentCaptor: ArgumentCaptor<T>): T = argumentCaptor.capture()
}