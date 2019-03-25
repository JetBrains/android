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
package com.android.tools.idea.transport.poller;


import static com.google.common.truth.Truth.assertThat;
import static junit.framework.TestCase.fail;

import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.idea.transport.TransportClient;
import com.android.tools.idea.transport.faketransport.FakeGrpcServer;
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.profiler.proto.Common;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;

public class TransportEventPollerTest {
  private static final long TIMEOUT_MILLISECONDS = 10000;

  FakeTimer myTimer = new FakeTimer();
  FakeTransportService myTransportService = new FakeTransportService(myTimer, true);
  @Rule public FakeGrpcServer myGrpcServer = FakeGrpcServer.createFakeGrpcServer(
    "TransportEventPollerTestChannel", myTransportService, myTransportService);

  /**
   * Tests that a newly created listener with already-connected device+process
   * will receive the stream connected and process started events
   */
  @Test
  public void testStreamAndProcessListeners() {
    TransportClient transportClient = new TransportClient(myGrpcServer.getName());
    CountDownLatch latch = new CountDownLatch(2);
    TransportEventPoller transportEventPoller = TransportEventPollerFactory.getInstance().createPoller(
      transportClient.getTransportStub(),
      TimeUnit.MILLISECONDS.toNanos(250));

    // Create listener for STREAM connected
    TransportEventListener streamConnectedListener = new TransportEventListener.Builder(Common.Event.Kind.STREAM,
      event -> {
        assertThat(event.getStream().getStreamConnected().getStream().getStreamId()).isEqualTo(FakeTransportService.FAKE_DEVICE_ID);
        latch.countDown();
      }, MoreExecutors.directExecutor())
      .setFilter(event -> event.getStream().hasStreamConnected())
      .build();
    transportEventPoller.registerListener(streamConnectedListener);

    // Create listener for PROCESS started
    TransportEventListener processStartedListener = new TransportEventListener.Builder(Common.Event.Kind.PROCESS,
      event -> {
        assertThat(event.getProcess().getProcessStarted().getProcess().getPid()).isEqualTo(1);
        assertThat(event.getProcess().getProcessStarted().getProcess().getDeviceId()).isEqualTo(FakeTransportService.FAKE_DEVICE_ID);
        latch.countDown();
      }, MoreExecutors.directExecutor())
      .setFilter(event -> event.getProcess().hasProcessStarted())
      .build();
    transportEventPoller.registerListener(processStartedListener);

    // Receive
    try {
      assertThat(latch.await(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)).isEqualTo(true);
    }
    catch (InterruptedException e) {
      e.printStackTrace();
      fail("Test interrupted");
    }
  }

  /**
   * Tests that listener receives events from both before and after it was created
   */
  @Test
  public void testEventListeners() {
    TransportClient transportClient = new TransportClient(myGrpcServer.getName());
    CountDownLatch eventLatch = new CountDownLatch(3);
    CountDownLatch waitLatch = new CountDownLatch(1);
    TransportEventPoller transportEventPoller = TransportEventPollerFactory.getInstance().createPoller(
      transportClient.getTransportStub(),
      TimeUnit.MILLISECONDS.toNanos(250));
    List<Common.Event> expectedEvents = new ArrayList<>();

    // First event exists before listener is registered
    Common.Event echoEvent = Common.Event.newBuilder()
      .setTimestamp(0)
      .setKind(Common.Event.Kind.ECHO)
      .build();
    expectedEvents.add(echoEvent);
    myTransportService.addEventToEventGroup(FakeTransportService.FAKE_DEVICE_ID, 123, echoEvent);

    // Create listener for ECHO event
    TransportEventListener echoListener = new TransportEventListener.Builder(Common.Event.Kind.ECHO,
      event -> {
        assertThat(event).isEqualTo(expectedEvents.remove(0));
        waitLatch.countDown();
        eventLatch.countDown();
      }, MoreExecutors.directExecutor())
      .build();
    transportEventPoller.registerListener(echoListener);

    // Wait for the first event to be received
    try {
      assertThat(waitLatch.await(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)).isEqualTo(true);
    }
    catch (InterruptedException e) {
      e.printStackTrace();
      fail("Test interrupted");
    }

    // Second event created after first is received
    Common.Event echoEvent2 = Common.Event.newBuilder()
      .setTimestamp(3)
      .setKind(Common.Event.Kind.ECHO)
      .build();
    expectedEvents.add(echoEvent2);
    myTransportService.addEventToEventGroup(FakeTransportService.FAKE_DEVICE_ID, 456, echoEvent2);

    // Third event with the same group ID
    Common.Event echoEvent3 = Common.Event.newBuilder()
      .setTimestamp(3)
      .setKind(Common.Event.Kind.ECHO)
      .build();
    expectedEvents.add(echoEvent3);
    myTransportService.addEventToEventGroup(FakeTransportService.FAKE_DEVICE_ID, 456, echoEvent3);

    // Receive the last 2 events
    try {
      assertThat(eventLatch.await(TIMEOUT_MILLISECONDS, TimeUnit.MILLISECONDS)).isEqualTo(true);
    }
    catch (InterruptedException e) {
      e.printStackTrace();
      fail("Test interrupted");
    }
  }
}
