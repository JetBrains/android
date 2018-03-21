/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.logcat;

import com.android.ddmlib.IDevice;
import com.android.ddmlib.logcat.LogCatMessage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class AndroidLogcatServiceTest {
  private static class TestLogcatListener implements AndroidLogcatService.LogcatListener {
    private final String[] EXPECTED_LOGS = {
      "08-18 16:39:11.439: W/DummyFirst(1493): First Line1",
      "08-18 16:39:11.439: W/DummyFirst(1493): First Line2",
      "08-18 16:39:11.439: W/DummyFirst(1493): First Line3",
      "09-20 16:39:11.439: W/DummySecond(1493): Second Line1"};

    private int myCurrentIndex = 0;
    private boolean myCleared;

    @Override
    public void onLogLineReceived(@NotNull LogCatMessage line) {
      assertEquals(EXPECTED_LOGS[myCurrentIndex++], line.toString());
    }

    @Override
    public void onCleared() {
      myCleared = true;
    }

    public void reset() {
      myCurrentIndex = 0;
      myCleared = false;
    }

    public void assertAllReceived() {
      assertEquals(EXPECTED_LOGS.length, myCurrentIndex);
    }

    public void assertNothingReceived() {
      assertEquals(0, myCurrentIndex);
    }

    private void assertCleared() {
      assertTrue(myCleared);
    }
  }

  private TestLogcatListener myLogcatListener;
  private IDevice mockDevice = mock(IDevice.class);
  private AndroidLogcatService myLogcatService;
  private volatile CountDownLatch myExecuteShellCommandLatch;

  private String myBufferSize;
  private Project myProject;

  @Before
  public void setUp() throws Exception {
    doAnswer(invocation -> {
      AndroidLogcatReceiver receiver = (AndroidLogcatReceiver)invocation.getArguments()[1];
      receiver.processNewLine("[ 08-18 16:39:11.439 1493:1595 W/DummyFirst     ]");
      receiver.processNewLine("First Line1");
      receiver.processNewLine("First Line2");
      receiver.processNewLine("First Line3");
      receiver.processNewLine("[ 09-20 16:39:11.439 1493:1595 W/DummySecond     ]");
      receiver.processNewLine("Second Line1");
      receiver.cancel();
      myExecuteShellCommandLatch.countDown();
      return null;
    }).when(mockDevice).executeShellCommand(any(), any(), anyLong(), any());

    myLogcatService = new AndroidLogcatService();
    myLogcatListener = new TestLogcatListener();
    myExecuteShellCommandLatch = new CountDownLatch(1);

    myBufferSize = System.setProperty("idea.cycle.buffer.size", "disabled");
    myProject = mock(Project.class);
  }

  @After
  public void tearDown() {
    Disposer.dispose(myProject);

    if (myBufferSize != null) {
      System.setProperty("idea.cycle.buffer.size", myBufferSize);
    }
    else {
      System.clearProperty("idea.cycle.buffer.size");
    }
  }

  @Test
  public void testDeviceConnectedAfterListening() throws Exception {
    when(mockDevice.isOnline()).thenReturn(false);
    myLogcatService.addListener(mockDevice, myLogcatListener);
    when(mockDevice.isOnline()).thenReturn(true);
    myLogcatService.deviceConnected(mockDevice);

    myExecuteShellCommandLatch.await();
    myLogcatListener.assertAllReceived();

    verify(mockDevice, times(2)).isOnline();
    verify(mockDevice, times(2)).getClientName(1493);
    verify(mockDevice, times(1)).getName();
    verify(mockDevice, times(1)).executeShellCommand(any(), any(), anyLong(), any());
    verifyNoMoreInteractions(mockDevice);
  }

  /**
   * Tests {@link AndroidLogcatService} to make sure that it will receive logs from devices which are connected
   * prior to it's initialization. AndroidLogcatService will start receiving logs in this devices whenever some listener starts listening
   */
  @Test
  public void testDeviceConnectedBeforeListening() throws Exception {
    when(mockDevice.isOnline()).thenReturn(true);
    myLogcatService.addListener(mockDevice, myLogcatListener, true);

    myExecuteShellCommandLatch.await();
    myLogcatListener.assertAllReceived();

    verify(mockDevice, times(1)).isOnline();
    verify(mockDevice, times(2)).getClientName(1493);
    verify(mockDevice, times(1)).getName();
    verify(mockDevice, times(1)).executeShellCommand(any(), any(), anyLong(), any());
    verifyNoMoreInteractions(mockDevice);
  }

  /**
   * Tests {@link AndroidLogcatService#addListener(IDevice, AndroidLogcatService.LogcatListener, boolean)},
   * to make sure that it adds correctly old logs from buffer
   */
  @Test
  public void testAddOldLogs() throws Exception {
    when(mockDevice.isOnline()).thenReturn(true);
    myLogcatService.deviceConnected(mockDevice);
    myLogcatService.addListener(mockDevice, myLogcatListener, true);

    myExecuteShellCommandLatch.await();

    myLogcatListener.assertAllReceived();
    verify(mockDevice, times(2)).isOnline();
    verify(mockDevice, times(2)).getClientName(1493);
    verify(mockDevice, times(1)).getName();
    verify(mockDevice, times(1)).executeShellCommand(any(), any(), anyLong(), any());
    verifyNoMoreInteractions(mockDevice);
  }

  @Test
  public void testDeviceDisconnectedAndConnected() throws Exception {
    when(mockDevice.isOnline()).thenReturn(false);
    myLogcatService.addListener(mockDevice, myLogcatListener);

    when(mockDevice.isOnline()).thenReturn(true);
    myLogcatService.deviceConnected(mockDevice);

    myExecuteShellCommandLatch.await();
    myLogcatListener.assertAllReceived();

    when(mockDevice.isOnline()).thenReturn(false);
    myLogcatService.deviceDisconnected(mockDevice);

    myExecuteShellCommandLatch = new CountDownLatch(1);
    myLogcatListener.reset();

    when(mockDevice.isOnline()).thenReturn(true);
    myLogcatService.deviceConnected(mockDevice);

    myExecuteShellCommandLatch.await();
    myLogcatListener.assertAllReceived();

    verify(mockDevice, times(3)).isOnline();
    verify(mockDevice, times(4)).getClientName(1493);
    verify(mockDevice, times(2)).getName();
    verify(mockDevice, times(2)).executeShellCommand(any(), any(), anyLong(), any());
    verifyNoMoreInteractions(mockDevice);
  }

  @Test
  public void testDeviceChanged() throws Exception {

    when(mockDevice.isOnline()).thenReturn(true);
    myLogcatService.deviceConnected(mockDevice);
    myLogcatService.addListener(mockDevice, myLogcatListener, true);
    myExecuteShellCommandLatch.await();
    myLogcatListener.assertAllReceived();

    myExecuteShellCommandLatch = new CountDownLatch(1);
    myLogcatListener.reset();
    when(mockDevice.isOnline()).thenReturn(false);
    myLogcatService.deviceChanged(mockDevice, 0);
    when(mockDevice.isOnline()).thenReturn(true);
    myLogcatService.deviceChanged(mockDevice, 0);

    myExecuteShellCommandLatch.await();
    myLogcatListener.assertAllReceived();

    verify(mockDevice, times(4)).isOnline();
    verify(mockDevice, times(4)).getClientName(1493);
    verify(mockDevice, times(2)).getName();
    verify(mockDevice, times(2)).executeShellCommand(any(), any(), anyLong(), any());

    verifyNoMoreInteractions(mockDevice);
  }

  @Test
  public void testRemoveListener() throws Exception {
    when(mockDevice.isOnline()).thenReturn(true);
    myLogcatService.deviceConnected(mockDevice);
    myLogcatService.addListener(mockDevice, myLogcatListener, true);
    myExecuteShellCommandLatch.await();
    myLogcatListener.assertAllReceived();

    myLogcatService.removeListener(mockDevice, myLogcatListener);
    when(mockDevice.isOnline()).thenReturn(false);
    myLogcatService.deviceDisconnected(mockDevice);

    // Try to reconnect and make sure that it received nothing
    myExecuteShellCommandLatch = new CountDownLatch(1);
    myLogcatListener.reset();
    when(mockDevice.isOnline()).thenReturn(true);
    myLogcatService.deviceConnected(mockDevice);
    TestLogcatListener otherListener = new TestLogcatListener();
    myLogcatService.addListener(mockDevice, otherListener);

    myExecuteShellCommandLatch.await();
    otherListener.assertAllReceived();
    myLogcatListener.assertNothingReceived();

    verify(mockDevice, times(4)).isOnline();
    verify(mockDevice, times(4)).getClientName(1493);
    verify(mockDevice, times(2)).getName();
    verify(mockDevice, times(2)).executeShellCommand(any(), any(), anyLong(), any());
    verifyNoMoreInteractions(mockDevice);
  }

  /**
   * Tests {@link AndroidLogcatService} to verify that when no one is interested in a device logs, AndroidLogcatService should
   * stop receiving logs from the device. Subsequently, if a listener will be interested in the device, it should receive all logs
   */
  @Test
  public void testNoDeviceListener() throws Exception {
    when(mockDevice.isOnline()).thenReturn(true);
    myLogcatService.deviceConnected(mockDevice);
    myLogcatService.addListener(mockDevice, myLogcatListener, true);

    myExecuteShellCommandLatch.await();
    myLogcatListener.assertAllReceived();

    myLogcatService.removeListener(mockDevice, myLogcatListener);
    myExecuteShellCommandLatch = new CountDownLatch(1);
    myLogcatListener.reset();
    myLogcatService.addListener(mockDevice, myLogcatListener, false);

    myExecuteShellCommandLatch.await();
    myLogcatListener.assertAllReceived();

    verify(mockDevice, times(3)).isOnline();
    verify(mockDevice, times(4)).getClientName(1493);
    verify(mockDevice, times(1)).getName();
    verify(mockDevice, times(2)).executeShellCommand(any(), any(), anyLong(), any());
    verifyNoMoreInteractions(mockDevice);
  }

  @Test
  public void consoleGetsClearedWhenDeviceIsDisconnected() {
    myLogcatService.addListener(mockDevice, myLogcatListener);
    myLogcatService.clearLogcat(mockDevice, myProject);

    myLogcatListener.assertCleared();
  }
}
