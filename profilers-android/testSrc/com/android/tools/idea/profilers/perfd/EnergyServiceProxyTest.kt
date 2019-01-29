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
package com.android.tools.idea.profilers.perfd

import com.android.ddmlib.IDevice
import com.android.sdklib.AndroidVersion
import com.android.tools.profiler.proto.EnergyServiceGrpc
import com.android.tools.profiler.proto.NetworkServiceGrpc
import com.google.common.truth.Truth.assertThat
import io.grpc.MethodDescriptor
import io.grpc.inprocess.InProcessChannelBuilder
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.util.stream.Collectors

class EnergyServiceProxyTest {
  @Test
  fun testBindServiceContainsAllMethods() {
    val mockDevice = mock(IDevice::class.java)
    `when`(mockDevice.serialNumber).thenReturn("Serial")
    `when`(mockDevice.name).thenReturn("Device")
    `when`(mockDevice.version).thenReturn(AndroidVersion(1, "API"))
    `when`(mockDevice.isOnline).thenReturn(true)
    `when`(mockDevice.clients).thenReturn(arrayOfNulls(0))
    val channel = InProcessChannelBuilder.forName("EnergyServiceProxyTest").build()
    val proxy = EnergyServiceProxy(mockDevice, channel)

    val serverDefinition = proxy.serviceDefinition
    val allMethods = EnergyServiceGrpc.getServiceDescriptor().methods
    val definedMethods = serverDefinition.methods.map { method -> method.methodDescriptor }.toSet()
    assertThat(definedMethods.size).isEqualTo(allMethods.size)
    definedMethods.containsAll(allMethods)
  }
}