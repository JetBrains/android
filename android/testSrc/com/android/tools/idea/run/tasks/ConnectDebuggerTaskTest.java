/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.run.tasks;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.IDevice;
import com.android.fakeadbserver.DeviceState;
import com.android.fakeadbserver.FakeAdbServer;
import com.android.fakeadbserver.devicecommandhandlers.JdwpCommandHandler;
import com.android.testutils.TestUtils;
import com.android.tools.idea.run.AndroidProcessHandler;
import com.android.tools.idea.run.ConsolePrinter;
import com.android.tools.idea.run.LaunchInfo;
import com.android.tools.idea.run.ProcessHandlerConsolePrinter;
import com.android.tools.idea.run.editor.AndroidDebugger;
import com.android.tools.idea.run.editor.AndroidJavaDebugger;
import com.android.tools.idea.run.util.LaunchStatus;
import com.android.tools.idea.run.util.ProcessHandlerLaunchStatus;
import com.google.common.collect.ImmutableSet;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.android.utils.FileUtils.join;

public class ConnectDebuggerTaskTest extends AndroidTestCase {

  @Override
  protected boolean shouldRunTest() {
    // Ignore this class: b/36808469
    return false;
  }

  private static final String TEST_DEVICE_ID = "test_device_001";
  private static final String TEST_MANUFACTURER = "Google";
  private static final String TEST_MODEL = "Nexus Silver";
  private static final String TEST_RELEASE = "8.0";
  private static final String TEST_SDK = "26";
  private static final String TEST_APP_ID = "com.example.test";
  private static final int TEST_PID = 1111;
  private static final int TEST_UID = 2222;

  private IDevice myDevice;
  private LaunchStatus myLaunchStatus;
  private ConsolePrinter myConsolePrinter;
  private FakeAdbServer myServer;
  private DeviceState myDeviceState;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    // Setup the server to default configuration.
    FakeAdbServer.Builder builder = new FakeAdbServer.Builder();
    builder.installDefaultCommandHandlers();

    // Add the debug commands handler
    builder.setDeviceCommandHandler(JdwpCommandHandler.COMMAND, JdwpCommandHandler::new);
    myServer = builder.build();
    myServer.start();

    // Connect the debug bridge to the fake adb server
    AndroidDebugBridge.enableFakeAdbServerMode(myServer.getPort());
    AndroidDebugBridge.initIfNeeded(true);
    AndroidDebugBridge bridge =
      AndroidDebugBridge.createBridge(new File(TestUtils.getSdk(), join("platform-tools", "adb")).getCanonicalPath(), false);
    assertNotNull("Failed to create debug bridge", bridge);

    // Connect a fake test device
    TargetDeviceChangeListener deviceListener = TargetDeviceChangeListener.createOneShotListener(IDevice.CHANGE_STATE);
    myDeviceState = myServer.connectDevice(TEST_DEVICE_ID,
                                           TEST_MANUFACTURER,
                                           TEST_MODEL,
                                           TEST_RELEASE,
                                           TEST_SDK,
                                           DeviceState.HostConnectionType.USB).get();
    myDeviceState.setDeviceStatus(DeviceState.DeviceStatus.ONLINE);

    // Wait for the device to get recognized by ddmlib.
    deviceListener.waitForDeviceChange();

    // Find our fake device in ddmlib
    Optional<IDevice> deviceOptional = Arrays.stream(bridge.getDevices())
      .filter(device -> device.getSerialNumber().equals(TEST_DEVICE_ID))
      .findFirst();
    assertTrue(deviceOptional.isPresent());
    //noinspection OptionalGetWithoutIsPresent
    myDevice = deviceOptional.get();

    // Add process handlers for the fake device associated with our test application's application id
    AndroidProcessHandler processHandler = new AndroidProcessHandler(TEST_APP_ID);
    processHandler.addTargetDevice(myDevice);
    myConsolePrinter = new ProcessHandlerConsolePrinter(processHandler);
    myLaunchStatus = new ProcessHandlerLaunchStatus(processHandler);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      //// See if we started a fake process
      //Client client = myDevice.getClient(TEST_APP_ID);
      //if (client != null) {
      //  // Kill process and wait for the process to be removed.
      //  TargetDeviceChangeListener deviceListener = TargetDeviceChangeListener.createOneShotListener(IDevice.CHANGE_CLIENT_LIST);
      //  client.kill();
      //  deviceListener.waitForDeviceChange();
      //}
      //
      //// Disconnect and wait for the device to be removed.
      //TargetDeviceChangeListener deviceListener = TargetDeviceChangeListener.createOneShotListener(IDevice.CHANGE_STATE);
      //myServer.disconnectDevice(TEST_DEVICE_ID);
      //deviceListener.waitForDeviceChange();

      // Stop the server and close the debug bridge
      myServer.stop();
      AndroidDebugBridge.terminate();
      AndroidDebugBridge.disconnectBridge();
      myServer.awaitServerTermination();
    }
    finally {
      super.tearDown();
    }
  }

  public void testWaitForClientCancelled() {
    myLaunchStatus.terminateLaunch("Cancelled");
    assertNull(getConnectDebuggerTask(false).waitForClient());
  }

  public void testWaitForClientOffline() {
    myDeviceState.setDeviceStatus(DeviceState.DeviceStatus.OFFLINE);
    assertNull(getConnectDebuggerTask(false).waitForClient());
  }

  // Test timeout waiting for client to appear
  public void testWaitForClientNormalAppTimeoutProcess() {
    assertNull(getConnectDebuggerTask(false).waitForClient());
  }

  // Test timeout when client is there but not ready for debugging
  public void testWaitForClientNormalAppTimeoutDebugger() throws Exception {
    startClient(false);

    assertNull(getConnectDebuggerTask(false).waitForClient());
  }

  public void testWaitForClientNormalAppNotReadyToDebug() throws Exception {
    startClient(true);

    TestConnectDebuggerTask testTask = getConnectDebuggerTask(false);
    testTask.setNotReadyToDebug();

    assertNull(testTask.waitForClient());
  }

  public void testWaitForClientNormalAppSuccess() throws Exception {
    startClient(true);
    assertNotNull(getConnectDebuggerTask(false).waitForClient());
  }

  public void testWaitForClientNormalAppSuccessAfterDelay() {
    assertNotNull(getConnectDebuggerTask(false, (count) -> {
      if (count == 3) {
        startClient(true);
      }
    }).waitForClient());
  }

  // Test timeout waiting for client to appear
  public void testWaitForClientInstantAppTimeoutProcess() {
    assertNull(getConnectDebuggerTask(true).waitForClient());
  }

  public void testWaitForClientInstantAppNotReadyToDebug() throws Exception {
    startClient(false);
    TestConnectDebuggerTask testTask = getConnectDebuggerTask(true);
    testTask.setNotReadyToDebug();

    assertNull(testTask.waitForClient());
  }

  public void testWaitForClientInstantAppSuccess() throws Exception {
    startClient(false);
    assertNotNull(getConnectDebuggerTask(true).waitForClient());
  }

  public void testWaitForClientInstantAppSuccessAfterDelay() throws Exception {
    assertNotNull(getConnectDebuggerTask(true, (count) -> {
      if (count == 3) {
        startClient(false);
      }
    }).waitForClient());
  }

  private void startClient(boolean isWaiting) throws InterruptedException {
    // If the client is waiting for debugger attachment then we wait for the debugger status change, otherwise we just wait for the
    // application ID to be returned
    int desiredEvent = isWaiting ? Client.CHANGE_DEBUGGER_STATUS : Client.CHANGE_NAME;
    // Start client and wait until ddmlib has picked up the change
    TargetClientChangeListener clientListener = TargetClientChangeListener.createOneShotListener(desiredEvent);
    myDeviceState.startClient(TEST_PID, TEST_UID, TEST_APP_ID, isWaiting);
    clientListener.waitForClientChange();
  }

  @NotNull
  private TestConnectDebuggerTask getConnectDebuggerTask(boolean attachToRunningProcess) {
    return getConnectDebuggerTask(attachToRunningProcess, (ignored) -> {
    });
  }

  @NotNull
  private TestConnectDebuggerTask getConnectDebuggerTask(boolean attachToRunningProcess, @NotNull Tickable onTick) {
    return new TestConnectDebuggerTask(ImmutableSet.of(TEST_APP_ID),
                                       new AndroidJavaDebugger(),
                                       getProject(),
                                       false,
                                       attachToRunningProcess,
                                       onTick);
  }

  /* Derived class used to exercise ConnectDebuggerTask's waitForClient method */
  private class TestConnectDebuggerTask extends ConnectDebuggerTask {
    private boolean myReadyToDebug = true;
    private int myTickCount = 0;
    @NotNull private final Tickable myOnTick;

    public TestConnectDebuggerTask(@NotNull Set<String> applicationIds,
                                   @NotNull AndroidDebugger debugger,
                                   @NotNull Project project,
                                   boolean monitorRemoteProcess,
                                   boolean attachToRunningProcess,
                                   @NotNull Tickable onTick) {
      super(applicationIds, debugger, project, monitorRemoteProcess, attachToRunningProcess);
      myOnTick = onTick;
    }

    @Nullable
    @Override
    public ProcessHandler launchDebugger(@NotNull LaunchInfo currentLaunchInfo,
                                         @NotNull Client client,
                                         @NotNull ProcessHandlerLaunchStatus state,
                                         @NotNull ProcessHandlerConsolePrinter printer) {
      return null;
    }

    @Nullable
    public Client waitForClient() {
      return waitForClient(myDevice, myLaunchStatus, myConsolePrinter);
    }

    public void setNotReadyToDebug() {
      myReadyToDebug = false;
    }

    @Override
    public boolean isReadyForDebugging(@NotNull Client client, @NotNull ConsolePrinter printer) {
      return myReadyToDebug;
    }

    // No need to sleep when testing
    @Override
    protected void sleep(long sleepFor, @NotNull TimeUnit unit) {
      try {
        myOnTick.run(myTickCount);
      }
      catch (InterruptedException e) {
        e.printStackTrace();
      }
      myTickCount++;
    }
  }

  public interface Tickable {
    void run(int count) throws InterruptedException;
  }

  /**
   * Listener that waits until the desired device connects or changes state.
   */
  private static final class TargetDeviceChangeListener implements AndroidDebugBridge.IDeviceChangeListener {

    private final int myMask;

    private CountDownLatch mDeviceConnectionCountdown = new CountDownLatch(1);

    public static TargetDeviceChangeListener createOneShotListener(int mask) {
      TargetDeviceChangeListener deviceListener = new TargetDeviceChangeListener(mask);
      AndroidDebugBridge.addDeviceChangeListener(deviceListener);
      return deviceListener;
    }

    private TargetDeviceChangeListener(int mask) {
      myMask = mask;
    }

    @Override
    public void deviceConnected(@NotNull IDevice iDevice) {
      signalIfRelevantEvent(iDevice, IDevice.CHANGE_STATE);
    }

    @Override
    public void deviceDisconnected(@NotNull IDevice iDevice) {
      signalIfRelevantEvent(iDevice, IDevice.CHANGE_STATE);
    }

    @Override
    public void deviceChanged(@NotNull IDevice iDevice, int changeMask) {
      signalIfRelevantEvent(iDevice, changeMask);
    }

    public void waitForDeviceChange() throws InterruptedException {
      mDeviceConnectionCountdown.await();
    }

    private void signalIfRelevantEvent(@NotNull IDevice iDevice, int changeMask) {
      if ((changeMask & myMask) != 0 && iDevice.getSerialNumber().equals(TEST_DEVICE_ID)) {
        mDeviceConnectionCountdown.countDown();
        AndroidDebugBridge.removeDeviceChangeListener(this);
      }
    }
  }

  /**
   * Listener that waits until the desired client changes state.
   */
  private static final class TargetClientChangeListener implements AndroidDebugBridge.IClientChangeListener {

    private final int myMask;
    private final CountDownLatch mClientConnectionCountdown = new CountDownLatch(1);

    public static TargetClientChangeListener createOneShotListener(int mask) {
      TargetClientChangeListener clientListener = new TargetClientChangeListener(mask);
      AndroidDebugBridge.addClientChangeListener(clientListener);
      return clientListener;
    }

    private TargetClientChangeListener(int mask) {
      myMask = mask;
    }

    @Override
    public void clientChanged(@NotNull Client client, int changeMask) {
      if ((changeMask & myMask) != 0 && client.getClientData().getPid() == TEST_PID) {
        mClientConnectionCountdown.countDown();
      }
    }

    public void waitForClientChange() throws InterruptedException {
      mClientConnectionCountdown.await();
      AndroidDebugBridge.removeClientChangeListener(this);
    }
  }
}
