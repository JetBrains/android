/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.asdriver.tests.integration

import com.android.adblib.AdbSession
import com.android.adblib.AdbSessionHost
import com.android.tools.testlib.TestLogger
import com.google.common.util.concurrent.MoreExecutors
import com.google.gson.JsonParser
// Android Studio Merge: ignore vendor dependencies
//import com.google.services.firebase.directaccess.client.DirectAccessConnectionManager
//import com.google.services.firebase.directaccess.client.DirectAccessReservationManager
import com.intellij.openapi.util.SystemInfo
// Android Studio Merge: ignore vendor dependencies
import com.android.tools.idea.io.grpc.ManagedChannel
import com.android.tools.idea.io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import com.android.tools.idea.io.grpc.netty.shaded.io.netty.channel.ChannelOption
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking

class RemoteDeviceManager constructor(deviceModel: String, apiLevel: String): AutoCloseable {

  private val deviceStreamingAPIEndpoint = "dns:///devicestreaming.googleapis.com"
  private val metadataServerEndpoint =
    "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/token"
  private val cloudProjectId = "adt-device-testing"
  // Android Studio Merge: ignore vendor dependencies
  // private val directAccessReservationManager: DirectAccessReservationManager
  private val scope: CoroutineScope =
    CoroutineScope(MoreExecutors.directExecutor().asCoroutineDispatcher())
  private val channel: ManagedChannel =
    NettyChannelBuilder.forTarget(deviceStreamingAPIEndpoint)
      .withOption(ChannelOption.TCP_NODELAY, true)
      .build()
  private val oAuthTokenFetcher: () -> String = {
    val client = HttpClient.newHttpClient()
    val request =
      HttpRequest.newBuilder()
        .uri(URI.create(metadataServerEndpoint))
        .GET()
        .header("Metadata-Flavor", "Google")
        .build()
    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    val authResponse = JsonParser.parseString(response.body()).getAsJsonObject()
    authResponse.get("access_token").asString
  }
  // Android Studio Merge: ignore vendor dependencies
  // private val directAccessConnectionManager: DirectAccessConnectionManager
  private val adbSession: AdbSession
  private var remoteDeviceName: String = ""
  private val deviceModel: String
  private val apiLevel: String

  init {
    // Android Studio Merge: ignore vendor dependencies
    //directAccessReservationManager =
    //  DirectAccessReservationManager(cloudProjectId, scope, true, channel, oAuthTokenFetcher)
    adbSession = AdbSession.create(AdbSessionHost())
    // Android Studio Merge: ignore vendor dependencies
    //directAccessConnectionManager =
    //  DirectAccessConnectionManager(
    //    scope,
    //    adbSession,
    //    true,
    //    oAuthTokenFetcher,
    //    channel,
    //    directAccessReservationManager,
    //  )
    this.deviceModel = deviceModel
    this.apiLevel = apiLevel
  }

  fun reserveDevice(model: String, apiLevel: String): String {
    assert(false) {"Android Studio Merge: ignore vendor dependencies"}
    // remoteDeviceName = directAccessReservationManager.createReservation(model, apiLevel).name
    return remoteDeviceName
  }

  fun connectToDevice(deviceName: String) = runBlocking {
    assert(false) {"Android Studio Merge: ignore vendor dependencies"}
    // val connection = directAccessConnectionManager.create(deviceName)
    // connection.connect()
  }

  fun setupRemoteDevice() {
    val deviceName: String = reserveDevice(deviceModel, apiLevel)
    TestLogger.log("Device Reserved: $deviceName")
    connectToDevice(deviceName)
    TestLogger.log("Connected To Device: $deviceName")
  }

  override fun close() {
    // Android Studio Merge: ignore vendor dependencies
    // directAccessReservationManager.cancelReservation(remoteDeviceName, true)
    adbSession.close()
    channel.shutdown()
    runBlocking { scope.coroutineContext.job.cancelAndJoin() }
    TestLogger.log("Remote Device Released.")
  }
}