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

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.FakeProfilerService;
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.ProfilerMonitor;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.memory.FakeMemoryService;
import com.android.tools.profilers.sessions.SessionsManager;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class CpuProfilerTest {

  private static final int FAKE_PID = 1234;

  private static final Common.Session FAKE_SESSION = Common.Session.newBuilder().setSessionId(4321).setPid(FAKE_PID).build();

  private final FakeTimer myTimer = new FakeTimer();
  private final FakeTransportService myTransportService = new FakeTransportService(myTimer);
  private final FakeProfilerService myProfilerService = new FakeProfilerService(myTimer);

  private final FakeCpuService myCpuService = new FakeCpuService();

  @Rule
  public FakeGrpcChannel myGrpcChannel =
    new FakeGrpcChannel("CpuProfilerTest", myTransportService, myProfilerService, new FakeMemoryService(), myCpuService);

  @Rule public final ExpectedException myExpectedException = ExpectedException.none();

  private CpuProfiler myCpuProfiler;

  private FakeIdeProfilerServices myIdeServices;

  private StudioProfilers myProfilers;

  @Before
  public void setUp() {
    myIdeServices = new FakeIdeProfilerServices();
    myProfilers = new StudioProfilers(new ProfilerClient(myGrpcChannel.getName()), myIdeServices, myTimer);
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
  public void stopMonitoringStopsOngoingTraces() {
    myCpuProfiler = new CpuProfiler(myProfilers);
    assertThat(myCpuService.getStartStopCapturingSession()).isNull();

    myCpuProfiler.stopProfiling(FAKE_SESSION);
    // No ongoing so we don't expect stop tracing to be called.
    assertThat(myCpuService.getStartStopCapturingSession()).isNull();

    // There is an ongoing trace so stop tracing should be called.
    myCpuService.addTraceInfo(Cpu.CpuTraceInfo.newBuilder().setTraceId(1).setFromTimestamp(10).setToTimestamp(-1).build());
    myCpuProfiler.stopProfiling(FAKE_SESSION);
    assertThat(myCpuService.getStartStopCapturingSession()).isEqualTo(FAKE_SESSION);
  }

  @Test
  public void importedSessionListenerShouldBeRegistered() {
    // Enable the import trace flag
    myIdeServices.enableImportTrace(true);

    myCpuProfiler = new CpuProfiler(myProfilers);
    File trace = CpuProfilerTestUtils.getTraceFile("valid_trace.trace");
    SessionsManager sessionsManager = myProfilers.getSessionsManager();
    // Importing a session from a trace file should select a Common.SessionMetaData.SessionType.CPU_CAPTURE session
    sessionsManager.importSessionFromFile(trace);

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
    Common.Session session =
      sessionsManager.createImportedSessionLegacy("name.trace", Common.SessionMetaData.SessionType.CPU_CAPTURE, 0, 0, 0);
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

  @Test
  public void referenceToTraceFilesAreSavedPerSession() {
    // Enable the import trace flag
    myIdeServices.enableImportTrace(true);

    myCpuProfiler = new CpuProfiler(myProfilers);
    File trace1 = CpuProfilerTestUtils.getTraceFile("valid_trace.trace");
    SessionsManager sessionsManager = myProfilers.getSessionsManager();
    sessionsManager.importSessionFromFile(trace1);
    Common.Session session1 = sessionsManager.getSelectedSession();

    File trace2 = CpuProfilerTestUtils.getTraceFile("basic.trace");
    sessionsManager.importSessionFromFile(trace2);
    Common.Session session2 = sessionsManager.getSelectedSession();


    assertThat(myCpuProfiler.getTraceFile(session1)).isEqualTo(trace1);
    assertThat(myCpuProfiler.getTraceFile(session2)).isEqualTo(trace2);
  }

  @Test
  public void importedSessionsStartTimeShouldBeTraceCreationTime() throws IOException {
    // Enable the import trace flag
    myIdeServices.enableImportTrace(true);

    myCpuProfiler = new CpuProfiler(myProfilers);
    File trace = CpuProfilerTestUtils.getTraceFile("valid_trace.trace");
    long traceCreationTime =
      Files.readAttributes(Paths.get(trace.getPath()), BasicFileAttributes.class).creationTime().to(TimeUnit.NANOSECONDS);


    SessionsManager sessionsManager = myProfilers.getSessionsManager();
    // Imported session's start time should be equal to the imported trace file creation time
    sessionsManager.importSessionFromFile(trace);
    assertThat(sessionsManager.getSelectedSession().getStartTimestamp()).isEqualTo(traceCreationTime);
  }
}
