/*
 * Copyright (C) 2019 The Android Open Source Project
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
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.io.grpc.netty.NettyChannelBuilder
import com.android.tools.idea.transport.TransportProxy
import com.android.tools.profiler.proto.Common
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.LinkedBlockingDeque


fun mockTransportProxy(): TransportProxy {
  val mockProxy: TransportProxy = mock()
  val channel = NettyChannelBuilder.forTarget("someTarget").usePlaintext().build()
  whenever(mockProxy.transportChannel).thenReturn(channel)
  whenever(mockProxy.bytesCache).thenReturn(mutableMapOf())
  whenever(mockProxy.eventQueue).thenReturn(LinkedBlockingDeque<Common.Event>())

  val mockDevice: IDevice = mock()
  whenever(mockDevice.serialNumber).thenReturn("Serial")
  whenever(mockDevice.name).thenReturn("Device")
  whenever(mockDevice.version).thenReturn(AndroidVersion(1, "API"))
  whenever(mockDevice.isOnline).thenReturn(true)
  whenever(mockDevice.clients).thenReturn(arrayOf())
  whenever(mockProxy.device).thenReturn(mockDevice)

  return mockProxy
}