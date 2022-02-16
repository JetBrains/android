/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.run.configuration.execution

import com.android.ddmlib.Client
import com.android.ddmlib.ClientData
import com.android.ddmlib.IDevice
import com.android.ddmlib.internal.ClientImpl
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.run.DeploymentApplicationService
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.replaceService
import org.mockito.Mockito
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Adds Client with given appId on IDevice.
 *
 * Client become available via [DeploymentApplicationService] and opens a connection for debug when [startClient] is invoked.
 * Client closes connection and "removed" from device when [stopClient] is invoked.
 */
internal class RunnableClient(private val appId: String, val disposable: Disposable) {
  private lateinit var client: Client
  private val clientSocket: ServerSocket = ServerSocket()
  private val isRunning = AtomicBoolean()
  var task: Future<*>? = null
  private val mThreadPoolExecutor = Executors.newCachedThreadPool(
    ThreadFactoryBuilder()
      .setNameFormat("runnable-client-%d")
      .build())

  init {
    clientSocket.reuseAddress = true
    clientSocket.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
  }

  fun startClient(device: IDevice) {
    // Do not reuse RunnableClient
    assert(!this::client.isInitialized)
    client = createMockClient(device, clientSocket.localPort)
    Mockito.`when`(device.isOnline).thenReturn(true)
    isRunning.set(true)

    task = mThreadPoolExecutor.submit {
      try {
        clientSocket.accept().use { socket ->
          while (isRunning.get()) {
            if (socket.getInputStream().available() != 0) {
              socket.getInputStream().read()
            }
          }
        }
      }
      catch (e: IOException) {
        clientSocket.close()
      }
    }

    val testDeploymentApplicationService = object : DeploymentApplicationService {
      override fun findClient(iDevice: IDevice, applicationId: String): List<Client> {
        if (device == iDevice && applicationId == appId) {
          return listOf(client)
        }
        return emptyList()
      }

      override fun getVersion(iDevice: IDevice): Future<AndroidVersion> {
        throw RuntimeException("Not implemented for test")
      }
    }

    ApplicationManager.getApplication()
      .replaceService(DeploymentApplicationService::class.java, testDeploymentApplicationService, disposable)
  }

  fun stopClient() {
    isRunning.set(false)
    clientSocket.close()
    task?.cancel(true)
    mThreadPoolExecutor.shutdownNow()
  }

  private fun createMockClient(device: IDevice, debugPort: Int): Client {
    val mockClient = Mockito.mock(ClientImpl::class.java)

    val clientData = object : ClientData(mockClient, 111) {
      override fun getPackageName() = appId
      override fun getClientDescription() = appId
      override fun getDebuggerConnectionStatus() = DebuggerStatus.WAITING
    }

    Mockito.`when`(mockClient.clientData).thenReturn(clientData)
    Mockito.`when`(mockClient.debuggerListenPort).thenReturn(debugPort)
    Mockito.`when`(mockClient.device).thenReturn(device)

    return mockClient
  }
}