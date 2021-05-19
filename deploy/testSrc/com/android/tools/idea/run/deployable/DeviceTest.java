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
package com.android.tools.idea.run.deployable;

import static com.android.fakeadbserver.DeviceState.DeviceStatus.ONLINE;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertNotNull;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.fakeadbserver.ClientState;
import com.android.fakeadbserver.DeviceState;
import com.android.fakeadbserver.FakeAdbServer;
import com.android.fakeadbserver.devicecommandhandlers.DeviceCommandHandler;
import com.android.fakeadbserver.devicecommandhandlers.JdwpCommandHandler;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DeviceTest {
  private static final String PROCESS_NAME = "com.example.android.notdisplayingbitmaps";
  private static final String APP_ID = "com.example.android.displayingbitmaps";
  private static final String OTHER_APP_ID = "com.some.other.app";
  private static final String INVALID_APP_ID = "com.does.not.exist";

  private static final int PRE_O_VALID_PID = 1025;
  private static final int PRE_O_RESTART_PID = 2025;
  private static final int O_VALID_PID = 1026;
  private static final int O_RESTART_PID = 2026;

  private static final int OTHER_PID = 9999;
  private static final int ANOTHER_PID = 10000;

  private static final int WAIT_TIME_S = 1000;

  private FakeAdbServer myAdbServer;

  private DeviceBinder myPreOBinder;
  private DeviceBinder myOBinder;

  private List<String> myPreOResult;
  private volatile CountDownLatch myPreOContinuationLatch;
  private volatile CountDownLatch myPreOFinishedLatch;

  private List<String> myOResult;
  private volatile CountDownLatch myOContinuationLatch;
  private volatile CountDownLatch myOFinishedLatch;
  private Map<String, List<String>> myStatPidMap;

  private ApplicationIdResolver myApplicationIdResolver;

  @Before
  public void setup() throws Exception {
    myPreOContinuationLatch = new CountDownLatch(1);
    myPreOFinishedLatch = new CountDownLatch(1);
    myPreOResult = Collections.synchronizedList(new ArrayList<>());
    myOContinuationLatch = new CountDownLatch(1);
    myOFinishedLatch = new CountDownLatch(1);
    myOResult = Collections.synchronizedList(new ArrayList<>());
    myOResult.add(String.format("package:%s ", APP_ID));
    myStatPidMap = new HashMap<>(ImmutableMap.of(getStatLookup(O_VALID_PID), myOResult));

    // Build the server and configure it to use the default ADB command handlers.
    FakeAdbServer.Builder builder = new FakeAdbServer.Builder();
    myAdbServer = builder.installDefaultCommandHandlers()
      .addDeviceHandler(new JdwpCommandHandler())
      .addDeviceHandler(
        getShellHandler(
          Pattern.compile("^uid.*"), ImmutableMap.of(APP_ID, myPreOResult), () -> myPreOContinuationLatch, () -> myPreOFinishedLatch))
      .addDeviceHandler(getShellHandler(Pattern.compile("^stat.*"), myStatPidMap, () -> myOContinuationLatch, () -> myOFinishedLatch))
      .build();

    DeviceState preODeviceState = myAdbServer.connectDevice(
      "test_device_N",
      "Google",
      "Nexus Gold",
      "7.1",
      "25",
      DeviceState.HostConnectionType.USB).get();

    DeviceState oDeviceState = myAdbServer.connectDevice(
      "test_device_O",
      "Google",
      "Nexus Gold",
      "10.0",
      "26",
      DeviceState.HostConnectionType.USB).get();

    // Start server execution.
    myAdbServer.start();

    // Test that we obtain 1 device via the ddmlib APIs
    AndroidDebugBridge.enableFakeAdbServerMode(myAdbServer.getPort());
    AndroidDebugBridge.initIfNeeded(true);
    AndroidDebugBridge bridge = AndroidDebugBridge.createBridge();
    assertNotNull("Debug bridge", bridge);

    myApplicationIdResolver = new ApplicationIdResolver();

    myPreOBinder = new DeviceBinder(preODeviceState);
    myOBinder = new DeviceBinder(oDeviceState);

    assertThat(myApplicationIdResolver.resolve(myPreOBinder.getIDevice(), APP_ID)).isEmpty();
    assertThat(myApplicationIdResolver.resolve(myOBinder.getIDevice(), APP_ID)).isEmpty();
  }

  @After
  public void teardown() throws Exception {
    myApplicationIdResolver.dispose();
    myAdbServer.close();
    AndroidDebugBridge.disconnectBridge();
    AndroidDebugBridge.terminate();
    AndroidDebugBridge.disableFakeAdbServerMode();
  }

  @Test
  public void testStartup() throws Exception {
    // Turn devices online.
    myPreOBinder.setStatus(ONLINE);
    myOBinder.setStatus(ONLINE);

    myPreOResult.add(Integer.toString(PRE_O_VALID_PID));

    ClientState preOClient = myPreOBinder.startClient(PRE_O_VALID_PID, 0, PROCESS_NAME, APP_ID, false);
    myPreOBinder.startClient(OTHER_PID, 0, OTHER_APP_ID, false);
    ClientState oClient = myOBinder.startClient(O_VALID_PID, 0, PROCESS_NAME, APP_ID, false);
    myOBinder.startClient(ANOTHER_PID, 0, OTHER_APP_ID, false);

    // Ensure we don't have a bug somewhere.
    assertThat(myApplicationIdResolver.resolve(myPreOBinder.getIDevice(), APP_ID)).isEmpty();
    assertThat(myApplicationIdResolver.resolve(myOBinder.getIDevice(), APP_ID)).isEmpty();
    assertInvalidClients();

    // Release the shell command handler latches to let resolutions go through.
    myPreOContinuationLatch.countDown();
    myOContinuationLatch.countDown();

    assertThat(resolveUntilNotEmpty(myPreOBinder.getIDevice(), APP_ID)).containsExactly(myPreOBinder.findClient(preOClient));
    assertThat(resolveUntilNotEmpty(myOBinder.getIDevice(), APP_ID)).containsExactly(myOBinder.findClient(oClient));
    assertInvalidClients();
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
  public void testClientRespawn() throws Exception {
    // Turn devices online.
    myPreOBinder.setStatus(ONLINE);
    myOBinder.setStatus(ONLINE);

    myPreOResult.add(Integer.toString(PRE_O_VALID_PID));

    // Start up the "old" clients.
    ClientState preOClient = myPreOBinder.startClient(PRE_O_VALID_PID, 0, PROCESS_NAME, APP_ID, false);
    myPreOBinder.startClient(OTHER_PID, 0, OTHER_APP_ID, false);
    ClientState oClient = myOBinder.startClient(O_VALID_PID, 0, PROCESS_NAME, APP_ID, false);
    myOBinder.startClient(ANOTHER_PID, 0, OTHER_APP_ID, false);

    // Now swap out the latches and leave the command handlers on the "old" clients waiting.
    CountDownLatch oldPreOContinuationLatch = myPreOContinuationLatch;
    CountDownLatch oldOContinuationLatch = myOContinuationLatch;

    // Kick off a resolution using initial app states.
    myApplicationIdResolver.resolve(myPreOBinder.getIDevice(), APP_ID);
    myApplicationIdResolver.resolve(myOBinder.getIDevice(), APP_ID);

    // Wait until we finish writing all response to the socket, but didn't close the socket yet.
    myPreOFinishedLatch.await();
    myOFinishedLatch.await();

    // Now attempt to change the clients.
    myPreOBinder.stopClient(preOClient);
    myOBinder.stopClient(oClient);

    myPreOContinuationLatch = new CountDownLatch(1);
    myPreOFinishedLatch = new CountDownLatch(1);
    myOContinuationLatch = new CountDownLatch(1);
    myOFinishedLatch = new CountDownLatch(1);
    myPreOResult.clear();
    myPreOResult.add(Integer.toString(PRE_O_RESTART_PID));
    myStatPidMap.clear();
    myStatPidMap.put(getStatLookup(O_RESTART_PID), myOResult);

    // Now start new Clients with new PIDs.
    preOClient = myPreOBinder.startClient(PRE_O_RESTART_PID, 0, APP_ID, false);
    oClient = myOBinder.startClient(O_RESTART_PID, 0, APP_ID, false);

    // Now resolve with new Clients.
    assertThat(resolveUntilNotEmpty(myPreOBinder.getIDevice(), APP_ID)).containsExactly(myPreOBinder.findClient(preOClient));
    assertThat(resolveUntilNotEmpty(myOBinder.getIDevice(), APP_ID)).containsExactly(myOBinder.findClient(oClient));
    assertInvalidClients();

    // Release the stale shell command handler latches to let stale resolutions go through.
    oldPreOContinuationLatch.countDown();
    oldOContinuationLatch.countDown();

    // Ensure that our resolutions don't change.
    assertThat(myApplicationIdResolver.resolve(myPreOBinder.getIDevice(), APP_ID)).containsExactly(myPreOBinder.findClient(preOClient));
    assertThat(myApplicationIdResolver.resolve(myOBinder.getIDevice(), APP_ID)).containsExactly(myOBinder.findClient(oClient));
    assertInvalidClients();
  }

  private void assertInvalidClients() {
    assertThat(myApplicationIdResolver.resolve(myPreOBinder.getIDevice(), INVALID_APP_ID)).isEmpty();
    assertThat(myApplicationIdResolver.resolve(myOBinder.getIDevice(), INVALID_APP_ID)).isEmpty();
  }

  @NotNull
  private List<Client> resolveUntilNotEmpty(@NotNull IDevice device, @NotNull String appId) throws Exception {
    long startTime = System.currentTimeMillis();
    while (TimeUnit.SECONDS.toMillis(WAIT_TIME_S) >= System.currentTimeMillis() - startTime) {
      List<Client> client = myApplicationIdResolver.resolve(device, appId);
      if (!client.isEmpty()) {
        return client;
      }
      //noinspection BusyWait
      Thread.sleep(50);
    }
    throw new TimeoutException();
  }

  @NotNull
  private static String getStatLookup(int pid) {
    return String.format("/proc/%d", pid);
  }

  @NotNull
  private static DeviceCommandHandler getShellHandler(@NotNull Pattern commandPattern,
                                                      @Nullable Map<String, List<String>> appIdToPidsMap,
                                                      @NotNull Supplier<CountDownLatch> continuationLatchSupplier,
                                                      @NotNull Supplier<CountDownLatch> finishedLatchSupplier) {
    return new DeviceCommandHandler("shell") {
      @Override
      public boolean accept(@NonNull FakeAdbServer server,
                            @NonNull Socket socket,
                            @NonNull DeviceState device,
                            @NonNull String command,
                            @NonNull String args) {
        if (!this.command.equals(command) || !commandPattern.matcher(args).matches()) {
          return false;
        }

        try {
          writeOkay(socket.getOutputStream());
        }
        catch (IOException ignored) {
        }

        if (appIdToPidsMap != null && !appIdToPidsMap.isEmpty()) {
          List<String> pids =
            appIdToPidsMap.entrySet().stream()
              .filter(entry -> args.contains(entry.getKey()))
              .map(entry -> entry.getValue())
              .findFirst().orElse(Collections.emptyList());

          try (PrintWriter pw = new PrintWriter(socket.getOutputStream())) {
            for (String value : pids) {
              pw.write(value);
            }
            pw.flush();

            CountDownLatch finishedLatch = finishedLatchSupplier.get();
            if (finishedLatch != null) {
              finishedLatch.countDown();
            }

            CountDownLatch continuationLatch = continuationLatchSupplier.get();
            if (continuationLatch != null) {
              try {
                continuationLatch.await();
              }
              catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
              }
            }
          }
          catch (IOException ignored) {
          }
        }

        return true;
      }
    };
  }
}
