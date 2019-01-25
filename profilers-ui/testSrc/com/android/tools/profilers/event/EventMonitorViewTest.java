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
package com.android.tools.profilers.event;

import static com.android.tools.profilers.FakeTransportService.FAKE_DEVICE_NAME;
import static com.android.tools.profilers.FakeTransportService.FAKE_PROCESS_NAME;
import static com.android.tools.profilers.ProfilersTestData.DEFAULT_AGENT_ATTACHED_RESPONSE;
import static com.android.tools.profilers.ProfilersTestData.DEFAULT_AGENT_DETACHED_RESPONSE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.adtui.ActivityComponent;
import com.android.tools.adtui.EventComponent;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.profilers.FakeGrpcServer;
import com.android.tools.profilers.FakeIdeProfilerComponents;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.FakeProfilerService;
import com.android.tools.profilers.FakeTransportService;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.StudioProfilersView;
import java.awt.Component;
import java.util.concurrent.TimeUnit;
import javax.swing.JLabel;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class EventMonitorViewTest {

  private FakeTimer myTimer = new FakeTimer();
  private final FakeTransportService myTransportService = new FakeTransportService(myTimer, true);

  @Rule
  public FakeGrpcServer myGrpcChannel =
    FakeGrpcServer.createFakeGrpcServer("EventMonitorViewTestChannel", myTransportService, new FakeProfilerService(myTimer));

  private EventMonitorView myMonitorView;

  @Before
  public void setUp() {
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices(), myTimer);
    myTransportService.setAgentStatus(DEFAULT_AGENT_ATTACHED_RESPONSE);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));
    profilers.setPreferredProcess(FAKE_DEVICE_NAME, FAKE_PROCESS_NAME, null);
    // StudioProfilersView initialization needs to happen after the tick, as during setDevice/setProcess the StudioMonitorStage is
    // constructed. If the StudioMonitorStageView is constructed as well, grpc exceptions will be thrown due to lack of various services
    // in the channel, and the tick loop would not complete properly to set the process and agent status.
    StudioProfilersView profilerView = new StudioProfilersView(profilers, new FakeIdeProfilerComponents());
    myMonitorView = new EventMonitorView(profilerView, new EventMonitor(profilers));
  }

  @Test
  public void testDisabledMonitorMessage() {
    // First verify the enabled hierarchy is correct.
    Component[] children = myMonitorView.getComponent().getComponents();
    assertEquals(2, children.length);
    assertTrue(children[0] instanceof EventComponent);
    assertTrue(children[1] instanceof ActivityComponent);

    myTransportService.setAgentStatus(DEFAULT_AGENT_DETACHED_RESPONSE);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));

    children = myMonitorView.getComponent().getComponents();
    assertEquals(1, children.length);
    assertTrue(children[0] instanceof JLabel);
    JLabel label = (JLabel)children[0];
    assertEquals(myMonitorView.getDisabledMessage(), label.getText());
  }
}