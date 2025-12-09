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
import com.android.tools.asdriver.tests.Workspace.Companion.getRoot
import com.android.tools.testlib.AndroidSdk
import com.android.tools.testlib.Display
import com.android.tools.testlib.Emulator
import com.android.tools.testlib.TestFileSystem
import com.android.tools.testlib.TestLogger
import com.google.common.util.concurrent.MoreExecutors
import com.google.gson.JsonParser
// Android Studio Merge: ignore vendor dependencies
//import com.google.services.firebase.directaccess.client.DirectAccessConnectionManager
//import com.google.services.firebase.directaccess.client.DirectAccessReservationManager
import com.intellij.openapi.util.SystemInfo
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

class AndroidDeviceManager {

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
  private var deviceName: String = "No Device Reserved Yet."
  private var curEmulatorName: String = "emu0"
  private var nextPort: Int = 8554

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
  }

  fun reserveDevice(model: String, apiLevel: String): String {
    assert(false) {"Android Studio Merge: ignore vendor dependencies"}
    //deviceName = directAccessReservationManager.createReservation(model, apiLevel).name
    return deviceName
  }

  fun connectToDevice(deviceName: String) = runBlocking {
    assert(false) {"Android Studio Merge: ignore vendor dependencies"}
    // val connection = directAccessConnectionManager.create(deviceName)
    // connection.connect()
  }

  fun tearDown() {
    if (deviceName != "No Device Reserved Yet.") {
      // Android Studio Merge: ignore vendor dependencies
      // directAccessReservationManager.cancelReservation(deviceName, true)
      TestLogger.log("Remote Device Released.")
    }
    runBlocking { scope.coroutineContext.job.cancelAndJoin() }
    adbSession.close()
    channel.shutdown()
  }

  fun runAndroidDevice(
    fileSystem: TestFileSystem,
    sdk: AndroidSdk,
    display: Display,
    systemImage: Emulator.SystemImage = Emulator.DEFAULT_EMULATOR_SYSTEM_IMAGE,
    extraEmulatorFlags: List<String> = emptyList(),
  ): Emulator {
    val platform = System.getProperty("intellij.plugin.test.platform")
    if (SystemInfo.isWindows || ("sherlock-sdk" == platform)) {
      val deviceName: String = reserveDevice("akita", "34")
      TestLogger.log("Device Reserved: $deviceName")
      connectToDevice(deviceName)
      TestLogger.log("Connected To Device: $deviceName")
      // TODO android merge: constructor was private, i had to skip those changes/there was no alternative
      return Emulator.start(fileSystem, sdk, display, null, nextPort++, extraEmulatorFlags)
    } else {
      TestLogger.log("Emulator#runEmulator")
      val systemImageDir = getRoot(systemImage.path)
      Emulator.createEmulator(fileSystem, curEmulatorName, systemImageDir)

      val emulator =
        Emulator.start(fileSystem, sdk, display, curEmulatorName, nextPort, extraEmulatorFlags)
      return emulator
    }
  }
}
