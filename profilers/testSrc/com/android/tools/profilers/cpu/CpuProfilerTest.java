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
package com.android.tools.profilers.cpu;

import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profilers.*;
import com.android.tools.profilers.memory.FakeMemoryService;
import com.android.tools.profilers.sessions.SessionsManager;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.File;

import static com.google.common.truth.Truth.assertThat;

public class CpuProfilerTest {

  private static final int FAKE_PID = 1234;

  private static final Common.Session FAKE_SESSION = Common.Session.newBuilder().setSessionId(4321).setPid(FAKE_PID).build();

  private final FakeProfilerService myProfilerService = new FakeProfilerService();

  private final FakeCpuService myCpuService = new FakeCpuService();

  @Rule
  public FakeGrpcChannel myGrpcChannel =
    new FakeGrpcChannel("CpuProfilerTest", myProfilerService, new FakeMemoryService(), myCpuService);

  @Rule public final ExpectedException myExpectedException = ExpectedException.none();

  private CpuProfiler myCpuProfiler;

  private FakeIdeProfilerServices myIdeServices;

  private StudioProfilers myProfilers;

  @Before
  public void setUp() {
    myIdeServices = new FakeIdeProfilerServices();
    myProfilers = new StudioProfilers(myGrpcChannel.getClient(), myIdeServices, new FakeTimer());
  }

  @Test
  public void newMonitor() {
    myCpuProfiler = new CpuProfiler(myProfilers);
    ProfilerMonitor monitor = myCpuProfiler.newMonitor();
    assertThat(monitor).isNotNull();
    assertThat(monitor).isInstanceOf(CpuMonitor.class);
  }

  @Test
  public void startProfilingCallStartMonitoringAppId() {
    myCpuProfiler = new CpuProfiler(myProfilers);
    myCpuProfiler.startProfiling(FAKE_SESSION);
    // Make sure the session of the service was set to FAKE_SESSION by the start monitoring request
    assertThat(myCpuService.getSession()).isEqualTo(FAKE_SESSION);
  }

  @Test
  public void stopProfilingCallStopMonitoringAppId() {
    myCpuProfiler = new CpuProfiler(myProfilers);
    myCpuProfiler.stopProfiling(FAKE_SESSION);
    // Make sure the session of the service was set to FAKE_SESSION by the stop monitoring request
    assertThat(myCpuService.getSession()).isEqualTo(FAKE_SESSION);
  }

  @Test
  public void importedSessionListenerShouldBeRegistered() {
    // Enable the import trace flag
    myIdeServices.enableImportTrace(true);

    myCpuProfiler = new CpuProfiler(myProfilers);
    SessionsManager sessionsManager = myProfilers.getSessionsManager();
    Common.Session session = sessionsManager.createImportedSession("name.trace", Common.SessionMetaData.SessionType.CPU_CAPTURE, 0, 0, 0);
    sessionsManager.update();
    sessionsManager.setSession(session);
    // Makes sure we've successfully selected the session.
    assertThat(sessionsManager.getSelectedSession()).isEqualTo(session);
    // Verify that CpuProfilerStage is open in Import trace mode
    assertThat(myProfilers.getStage()).isInstanceOf(CpuProfilerStage.class);
    CpuProfilerStage cpuProfilerStage = (CpuProfilerStage)myProfilers.getStage();
    assertThat(cpuProfilerStage.isImportTraceMode()).isTrue();
  }

  @Test
  public void importedSessionListenerShouldntBeRegisteredIfFlagIsDisabled() {
    // Enable the import trace flag
    myIdeServices.enableImportTrace(false);

    myCpuProfiler = new CpuProfiler(myProfilers);
    SessionsManager sessionsManager = myProfilers.getSessionsManager();
    Common.Session session = sessionsManager.createImportedSession("name.trace", Common.SessionMetaData.SessionType.CPU_CAPTURE, 0, 0, 0);
    sessionsManager.update();
    // Expect setting the session to fail, because session manager shouldn't be aware of Common.SessionMetaData.SessionType.CPU_CAPTURE,
    // as we didn't register a listener for this type of captures.
    myExpectedException.expect(AssertionError.class);
    sessionsManager.setSession(session);
  }

  @Test
  public void traceImportHandlerShouldBeRegistered() {
    // Enable the import trace flag
    myIdeServices.enableImportTrace(true);

    myCpuProfiler = new CpuProfiler(myProfilers);
    SessionsManager sessionsManager = myProfilers.getSessionsManager();
    File trace = CpuProfilerTestUtils.getTraceFile("valid_trace.trace");

    boolean sessionImportedSuccessfully = sessionsManager.importSessionFromFile(trace);
    assertThat(sessionImportedSuccessfully).isTrue();

    assertThat(myProfilerService.getLastImportedSessionType()).isEqualTo(Common.SessionMetaData.SessionType.CPU_CAPTURE);
  }

  @Test
  public void traceImportHandlerShouldntBeRegisteredIfFlagIsDisabled() {
    // Disable the import trace flag
    myIdeServices.enableImportTrace(false);

    myCpuProfiler = new CpuProfiler(myProfilers);
    SessionsManager sessionsManager = myProfilers.getSessionsManager();
    File trace = CpuProfilerTestUtils.getTraceFile("valid_trace.trace");

    boolean sessionImportedSuccessfully = sessionsManager.importSessionFromFile(trace);
    assertThat(sessionImportedSuccessfully).isFalse();

    assertThat(myProfilerService.getLastImportedSessionType()).isNull();
  }
}
