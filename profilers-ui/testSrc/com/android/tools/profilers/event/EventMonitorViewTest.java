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

import static com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_DEVICE_NAME;
import static com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_PROCESS;
import static com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_PROCESS_NAME;
import static com.android.tools.profilers.ProfilersTestData.DEFAULT_AGENT_ATTACHED_RESPONSE;
import static com.android.tools.profilers.ProfilersTestData.DEFAULT_AGENT_UNATTACHABLE_RESPONSE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.sdklib.AndroidVersion;
import com.android.tools.adtui.ActivityComponent;
import com.android.tools.adtui.EventComponent;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.idea.transport.faketransport.FakeGrpcServer;
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profilers.FakeIdeProfilerComponents;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.StudioProfilersView;
import com.google.common.truth.Truth;
import com.intellij.testFramework.ApplicationRule;
import java.awt.Component;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.swing.JLabel;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class EventMonitorViewTest {
  private final int myFeatureLevel;
  private final FakeTimer myTimer = new FakeTimer();
  private final FakeTransportService myTransportService;
  private final FakeGrpcServer myGrpcChannel;
  private StudioProfilers myProfilers;
  private FakeIdeProfilerServices myProfilerServices;
  private EventMonitorView myMonitorView;

  @Rule
  public FakeGrpcServer getGrpcChannel() {
    return myGrpcChannel;
  }

  @Rule
  public final ApplicationRule myApplicationRule = new ApplicationRule();

  public EventMonitorViewTest(int featureLevel) {
    myFeatureLevel = featureLevel;
    myTransportService = new FakeTransportService(myTimer, true, myFeatureLevel);
    myGrpcChannel = FakeGrpcServer.createFakeGrpcServer("EventMonitorViewTestChannel", myTransportService);
    myProfilerServices = new FakeIdeProfilerServices();
  }

  @Before
  public void setUp() {
    myProfilerServices.enableEventsPipeline(true);
    myProfilers = new StudioProfilers(new ProfilerClient(myGrpcChannel.getChannel()), myProfilerServices, myTimer);
    myProfilers.setPreferredProcess(FAKE_DEVICE_NAME, FAKE_PROCESS_NAME, null);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));

    // StudioProfilersView initialization needs to happen after the tick, as during setDevice/setProcess the StudioMonitorStage is
    // constructed. If the StudioMonitorStageView is constructed as well, grpc exceptions will be thrown due to lack of various services
    // in the channel, and the tick loop would not complete properly to set the process and agent status.
    StudioProfilersView profilerView = new StudioProfilersView(myProfilers, new FakeIdeProfilerComponents());
    myMonitorView = new EventMonitorView(profilerView, new EventMonitor(myProfilers));

    updateAgentData(DEFAULT_AGENT_ATTACHED_RESPONSE);
  }

  @Test
  public void testDisabledMonitorMessage() {
    // First verify the enabled hierarchy is correct.
    Component[] children = myMonitorView.getComponent().getComponents();
    assertEquals(2, children.length);
    assertTrue(children[0] instanceof EventComponent);
    assertTrue(children[1] instanceof ActivityComponent);

    updateAgentData(DEFAULT_AGENT_UNATTACHABLE_RESPONSE);

    children = myMonitorView.getComponent().getComponents();
    assertEquals(myFeatureLevel < AndroidVersion.VersionCodes.O ? 2 : 1, children.length);
    assertTrue(children[0] instanceof JLabel);
    JLabel label = (JLabel)children[0];
    assertEquals(myMonitorView.getDisabledMessage(), label.getText());

    Truth.assertThat(label.getText()).contains(myFeatureLevel < AndroidVersion.VersionCodes.O ? "Additional" : "error");
  }

  private void updateAgentData(Common.AgentData agentData) {
    long sessionStreamId = myProfilers.getSession().getStreamId();
    myTransportService.addEventToStream(sessionStreamId, Common.Event.newBuilder()
      .setPid(FAKE_PROCESS.getPid())
      .setKind(Common.Event.Kind.AGENT)
      .setAgentData(agentData)
      .build());
    myTimer.tick(FakeTimer.ONE_SECOND_IN_NS);
  }

  @Parameterized.Parameters
  public static List<Integer> getFeatureLevels() {
    return Arrays.asList(AndroidVersion.VersionCodes.O, AndroidVersion.VersionCodes.N);
  }
}