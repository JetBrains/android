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

import com.android.ddmlib.Client
import com.android.ddmlib.IDevice
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.protobuf.ByteString
import com.android.tools.idea.transport.TransportProxy
import com.android.tools.profiler.proto.Common
import io.grpc.ManagedChannel
import io.grpc.netty.NettyChannelBuilder
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.util.concurrent.BlockingDeque
import java.util.concurrent.LinkedBlockingDeque


fun mockTransportProxy(): TransportProxy {
  val mockProxy = mock<TransportProxy>(TransportProxy::class.java)

  `when`<ManagedChannel>(mockProxy.transportChannel).thenReturn(NettyChannelBuilder.forTarget("someTarget").build())
  `when`<Map<String, ByteString>>(mockProxy.bytesCache).thenReturn(mutableMapOf())
  `when`<BlockingDeque<Common.Event>>(mockProxy.eventQueue).thenReturn(LinkedBlockingDeque<Common.Event>())

  val mockDevice = mock<IDevice>(IDevice::class.java)
  `when`<String>(mockDevice.serialNumber).thenReturn("Serial")
  `when`<String>(mockDevice.name).thenReturn("Device")
  `when`<AndroidVersion>(mockDevice.version).thenReturn(AndroidVersion(1, "API"))
  `when`<Boolean>(mockDevice.isOnline).thenReturn(true)
  `when`<Array<Client>>(mockDevice.clients).thenReturn(arrayOf())
  `when`<IDevice>(mockProxy.device).thenReturn(mockDevice)

  return mockProxy
}