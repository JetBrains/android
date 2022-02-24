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
import com.intellij.concurrency.AsyncFutureResultImpl
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
 * Client closes connection and "removed" from [DeploymentApplicationService] device when [stopClient] is invoked.
 */
internal class RunnableClientsService(testDisposable: Disposable) {
  private val deviceToRunnableClients: MutableMap<IDevice, MutableMap<String, RunnableClient>> = mutableMapOf()
  private val deploymentApplicationService = TestDeploymentApplicationService()

  init {
    ApplicationManager.getApplication()
      .replaceService(DeploymentApplicationService::class.java, deploymentApplicationService, testDisposable)
  }

  fun stop() {
    deviceToRunnableClients.entries.forEach { (device, clients) ->
      clients.keys.forEach { appId -> stopClient(device, appId) }
    }
  }

  fun startClient(device: IDevice, appId: String): Client {
    val clients = deviceToRunnableClients.computeIfAbsent(device) { mutableMapOf() }
    val runnableClient = RunnableClient.start(device, appId)
    clients[appId] = runnableClient
    deploymentApplicationService.addClient(device, appId, runnableClient.client)
    return runnableClient.client
  }

  fun stopClient(device: IDevice, appId: String) {
    val runnableClient = deviceToRunnableClients[device]?.get(appId) ?: throw RuntimeException("Client is not started")
    runnableClient.stopClient()
    deploymentApplicationService.removeClient(device, appId)
    deviceToRunnableClients[device]!!.remove(appId)
  }
}

private class RunnableClient private constructor(private val device: IDevice, private val appId: String) {
  lateinit var client: Client
  private val clientSocket: ServerSocket = ServerSocket()
  private val isRunning = AtomicBoolean()
  var task: Future<*>? = null
  private val mThreadPoolExecutor = Executors.newCachedThreadPool(
    ThreadFactoryBuilder()
      .setNameFormat("runnable-client-%d")
      .build())

  companion object {
    fun start(device: IDevice, appId: String): RunnableClient {
      val runnableClient = RunnableClient(device, appId)
      runnableClient.doStartClient()
      return runnableClient
    }
  }

  init {
    clientSocket.reuseAddress = true
    clientSocket.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
  }

  private fun doStartClient() {
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

private class TestDeploymentApplicationService : DeploymentApplicationService {

  private val deviceToClients: MutableMap<IDevice, MutableMap<String, Client>> = mutableMapOf()

  fun addClient(device: IDevice, appId: String, client: Client) {
    val clients = deviceToClients.computeIfAbsent(device) { mutableMapOf() }
    clients[appId] = client
  }

  fun removeClient(device: IDevice, appId: String) {
    deviceToClients[device]?.remove(appId)
  }

  override fun findClient(iDevice: IDevice, applicationId: String): List<Client> {
    return deviceToClients[iDevice]?.get(applicationId)?.let { listOf(it) } ?: emptyList()
  }

  override fun getVersion(iDevice: IDevice): Future<AndroidVersion> {
    return AsyncFutureResultImpl()
  }
}