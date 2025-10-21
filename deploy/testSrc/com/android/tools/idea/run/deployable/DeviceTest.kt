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
package com.android.tools.idea.run.deployable

import com.android.adblib.ddmlibcompatibility.testutils.InitAndroidDebugBridgeRule
import com.android.adblib.ddmlibcompatibility.testutils.UseAdbLibAndroidDebugBridgeRule
import com.android.adblib.testingutils.FakeAdbServerProviderRule
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.Client
import com.android.ddmlib.IDevice
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.devicecommandhandlers.DeviceCommandHandler
import com.android.fakeadbserver.services.ShellCommandOutput
import com.android.fakeadbserver.services.StatusWriter
import com.android.sdklib.AndroidApiLevel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.io.IOException
import java.io.PrintWriter
import java.net.Socket
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.regex.Pattern

class DeviceTest {
  private lateinit var myPreOBinder: DeviceBinder
  private lateinit var myOBinder: DeviceBinder

  private val myPreOResult = Collections.synchronizedList(mutableListOf<String>())

  @Volatile
  private var myPreOContinuationLatch = CountDownLatch(1)

  @Volatile
  private var myPreOFinishedLatch = CountDownLatch(1)

  private val myOResult = Collections.synchronizedList(mutableListOf("package:$APP_ID "))

  @Volatile
  private var myOContinuationLatch = CountDownLatch(1)

  @Volatile
  private var myOFinishedLatch = CountDownLatch(1)
  private val myStatPidMap = mutableMapOf<String, List<String>>(getStatLookup(O_VALID_PID) to myOResult)

  private val myApplicationIdResolver = ApplicationIdResolver()

  private val fakeAdbRule = FakeAdbServerProviderRule {
    installDefaultCommandHandlers()
      .installDeviceHandler(
        getShellHandler(
          Pattern.compile("^uid.*"), mapOf(APP_ID to myPreOResult), { myPreOContinuationLatch }, { myPreOFinishedLatch }))
      .installDeviceHandler(getShellHandler(Pattern.compile("^stat.*"), myStatPidMap, { myOContinuationLatch }, { myOFinishedLatch }))
  }

  private val useAdbLibAndroidDebugBridgeRule = UseAdbLibAndroidDebugBridgeRule { fakeAdbRule.adbSession }

  private val initAndroidDebugBridgeRule = InitAndroidDebugBridgeRule { fakeAdbRule.fakeAdb.port }

  @get:Rule
  val ruleChain: RuleChain = RuleChain.outerRule(fakeAdbRule)
    .around(useAdbLibAndroidDebugBridgeRule)
    .around(initAndroidDebugBridgeRule)

  @Before
  fun setup() {
    val preODeviceState = fakeAdbRule.fakeAdb.fakeAdbServer.connectDevice(
      "test_device_N",
      "Google",
      "Nexus Gold",
      "7.1",
      AndroidApiLevel(25),
      DeviceState.HostConnectionType.USB).get()

    val oDeviceState = fakeAdbRule.fakeAdb.fakeAdbServer.connectDevice(
      "test_device_O",
      "Google",
      "Nexus Gold",
      "10.0",
      AndroidApiLevel(26),
      DeviceState.HostConnectionType.USB).get()

    // Test that we obtain 1 device via the ddmlib APIs
    AndroidDebugBridge.terminate()
    val bridge = AndroidDebugBridge.createBridge()
    assertThat(bridge).isNotNull()

    myPreOBinder = DeviceBinder(preODeviceState)
    myOBinder = DeviceBinder(oDeviceState)

    assertThat(myApplicationIdResolver.resolve(myPreOBinder.iDevice, APP_ID)).isEmpty()
    assertThat(myApplicationIdResolver.resolve(myOBinder.iDevice, APP_ID)).isEmpty()
  }

  @After
  fun teardown() {
    myApplicationIdResolver.dispose()
  }

  @Test
  fun testStartup() {
    // Turn devices online.
    myPreOBinder.setStatus(DeviceState.DeviceStatus.ONLINE)
    myOBinder.setStatus(DeviceState.DeviceStatus.ONLINE)

    myPreOResult.add(PRE_O_VALID_PID.toString())

    val preOClient = myPreOBinder.startClient(PRE_O_VALID_PID, 0, PROCESS_NAME, APP_ID, false)
    myPreOBinder.startClient(OTHER_PID, 0, OTHER_APP_ID, false)
    val oClient = myOBinder.startClient(O_VALID_PID, 0, PROCESS_NAME, APP_ID, false)
    myOBinder.startClient(ANOTHER_PID, 0, OTHER_APP_ID, false)

    // Ensure we don't have a bug somewhere.
    assertThat(myApplicationIdResolver.resolve(myPreOBinder.iDevice, APP_ID)).isEmpty()
    assertThat(myApplicationIdResolver.resolve(myOBinder.iDevice, APP_ID)).isEmpty()
    assertInvalidClients()

    // Release the shell command handler latches to let resolutions go through.
    myPreOContinuationLatch.countDown()
    myOContinuationLatch.countDown()

    assertThat(resolveUntilNotEmpty(myPreOBinder.iDevice, APP_ID)).containsExactly(myPreOBinder.findClient(preOClient))
    assertThat(resolveUntilNotEmpty(myOBinder.iDevice, APP_ID)).containsExactly(myOBinder.findClient(oClient))
    assertInvalidClients()
  }

  /**
   * This test attempts to check the validity of the asynchronous operation of the resolvers. It does so by:
   * 1) Starting a package name resolution on a set of "old" clients.
   * 2) Leaving those resolutions hanging.
   * 3) Shutting down the "old" clients and starting up the "new" clients in its place.
   * 4) Successfully resolve the new clients.
   * 5) Release the resolutions on the "old" clients and ensure that they don't show up in the results.
   */
  @Test
  fun testClientRespawn() {
    // Turn devices online.
    myPreOBinder.setStatus(DeviceState.DeviceStatus.ONLINE)
    myOBinder.setStatus(DeviceState.DeviceStatus.ONLINE)

    myPreOResult.add(PRE_O_VALID_PID.toString())

    // Start up the "old" clients.
    val preOClient = myPreOBinder.startClient(PRE_O_VALID_PID, 0, PROCESS_NAME, APP_ID, false)
    myPreOBinder.startClient(OTHER_PID, 0, OTHER_APP_ID, false)
    val oClient = myOBinder.startClient(O_VALID_PID, 0, PROCESS_NAME, APP_ID, false)
    myOBinder.startClient(ANOTHER_PID, 0, OTHER_APP_ID, false)

    // Now swap out the latches and leave the command handlers on the "old" clients waiting.
    val oldPreOContinuationLatch = myPreOContinuationLatch
    val oldOContinuationLatch = myOContinuationLatch

    // Kick off a resolution using initial app states.
    myApplicationIdResolver.resolve(myPreOBinder.iDevice, APP_ID)
    myApplicationIdResolver.resolve(myOBinder.iDevice, APP_ID)

    // Wait until we finish writing all response to the socket, but didn't close the socket yet.
    myPreOFinishedLatch.await(WAIT_TIME_S.toLong(), TimeUnit.SECONDS)
    myOFinishedLatch.await(WAIT_TIME_S.toLong(), TimeUnit.SECONDS)

    // Now attempt to change the clients.
    myPreOBinder.stopClient(preOClient)
    myOBinder.stopClient(oClient)

    myPreOContinuationLatch = CountDownLatch(1)
    myPreOFinishedLatch = CountDownLatch(1)
    myOContinuationLatch = CountDownLatch(1)
    myOFinishedLatch = CountDownLatch(1)
    myPreOResult.clear()
    myPreOResult.add(PRE_O_RESTART_PID.toString())
    myStatPidMap.clear()
    myStatPidMap[getStatLookup(O_RESTART_PID)] = myOResult

    // Now start new Clients with new PIDs.
    val newPreOClient = myPreOBinder.startClient(PRE_O_RESTART_PID, 0, APP_ID, false)
    val newOClient = myOBinder.startClient(O_RESTART_PID, 0, APP_ID, false)

    // Now resolve with new Clients.
    assertThat(resolveUntilNotEmpty(myPreOBinder.iDevice, APP_ID)).containsExactly(myPreOBinder.findClient(newPreOClient))
    assertThat(resolveUntilNotEmpty(myOBinder.iDevice, APP_ID)).containsExactly(myOBinder.findClient(newOClient))
    assertInvalidClients()

    // Release the stale shell command handler latches to let stale resolutions go through.
    oldPreOContinuationLatch.countDown()
    oldOContinuationLatch.countDown()

    // Ensure that our resolutions don't change.
    assertThat(myApplicationIdResolver.resolve(myPreOBinder.iDevice, APP_ID)).containsExactly(myPreOBinder.findClient(newPreOClient))
    assertThat(myApplicationIdResolver.resolve(myOBinder.iDevice, APP_ID)).containsExactly(myOBinder.findClient(newOClient))
    assertInvalidClients()
  }

  private fun assertInvalidClients() {
    assertThat(myApplicationIdResolver.resolve(myPreOBinder.iDevice, INVALID_APP_ID)).isEmpty()
    assertThat(myApplicationIdResolver.resolve(myOBinder.iDevice, INVALID_APP_ID)).isEmpty()
  }

  private fun resolveUntilNotEmpty(device: IDevice, appId: String): List<Client> {
    val startTime = System.currentTimeMillis()
    while (TimeUnit.SECONDS.toMillis(WAIT_TIME_S.toLong()) >= System.currentTimeMillis() - startTime) {
      val client = myApplicationIdResolver.resolve(device, appId)
      if (client.isNotEmpty()) {
        return client
      }
      //noinspection BusyWait
      Thread.sleep(50)
    }
    throw TimeoutException()
  }

  companion object {
    private const val PROCESS_NAME = "com.example.android.notdisplayingbitmaps"
    private const val APP_ID = "com.example.android.displayingbitmaps"
    private const val OTHER_APP_ID = "com.some.other.app"
    private const val INVALID_APP_ID = "com.does.not.exist"

    private const val PRE_O_VALID_PID = 1025
    private const val PRE_O_RESTART_PID = 2025
    private const val O_VALID_PID = 1026
    private const val O_RESTART_PID = 2026

    private const val OTHER_PID = 9999
    private const val ANOTHER_PID = 10000

    private const val WAIT_TIME_S = 10

    private fun getStatLookup(pid: Int): String {
      return "/proc/$pid"
    }

    private fun getShellHandler(
      commandPattern: Pattern,
      appIdToPidsMap: Map<String, List<String>>?,
      continuationLatchSupplier: () -> CountDownLatch?,
      finishedLatchSupplier: () -> CountDownLatch?
    ): DeviceCommandHandler {
      return object : DeviceCommandHandler("shell") {
        override fun accept(
          server: FakeAdbServer,
          socketScope: CoroutineScope,
          socket: Socket,
          device: DeviceState,
          command: String,
          args: String,
          statusWriter: StatusWriter,
          shellCommandOutputProvider: (() -> ShellCommandOutput)?
        ): Boolean {
          if (this.command != command || !commandPattern.matcher(args).matches()) {
            return false
          }

          try {
            writeOkay(socket.outputStream)
          }
          catch (_: IOException) {
          }

          if (appIdToPidsMap != null && appIdToPidsMap.isNotEmpty()) {
            val pids = appIdToPidsMap.entries
                         .firstOrNull { args.contains(it.key) }
                         ?.value
                       ?: emptyList()

            try {
              PrintWriter(socket.outputStream).use { pw ->
                for (value in pids) {
                  pw.write(value)
                }
                pw.flush()

                finishedLatchSupplier()?.countDown()

                try {
                  continuationLatchSupplier()?.await()
                }
                catch (_: InterruptedException) {
                  Thread.currentThread().interrupt()
                }
              }
            }
            catch (_: IOException) {
            }
          }
          return true
        }
      }
    }
  }
}
