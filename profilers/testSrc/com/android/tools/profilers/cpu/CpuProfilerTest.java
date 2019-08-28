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
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.stdui.CommonTextFieldModel;
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel;
import com.android.tools.idea.transport.faketransport.commands.StopCpuTrace;
import com.android.tools.profiler.proto.Commands;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Cpu;
import com.android.tools.profiler.proto.Memory;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.FakeProfilerService;
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.ProfilersTestData;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.memory.FakeMemoryService;
import com.android.tools.profilers.memory.MemoryProfiler;
import com.android.tools.profilers.sessions.SessionsManager;
import com.google.common.truth.Truth;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public final class CpuProfilerTest {
  @Parameterized.Parameters
  public static Collection<Boolean> useUnifiedPipeline() {
    return Arrays.asList(false, true);
  }

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

  private final boolean myUnifiedPipeline;

  public CpuProfilerTest(boolean useUnifiedPipeline) {
    myUnifiedPipeline = useUnifiedPipeline;
  }

  @Before
  public void setUp() {
    myIdeServices = new FakeIdeProfilerServices();
    myIdeServices.enableEventsPipeline(myUnifiedPipeline);
    myProfilers = new StudioProfilers(new ProfilerClient(myGrpcChannel.getName()), myIdeServices, myTimer);
  }

  @Test
  public void startProfilingCallStartMonitoringAppId() {
    Assume.assumeFalse("Unified pipeline does not go through StartMonitoringApp", myUnifiedPipeline);

    myCpuProfiler = new CpuProfiler(myProfilers);
    myCpuProfiler.startProfiling(FAKE_SESSION);
    // Make sure the session of the service was set to FAKE_SESSION by the start monitoring request
    assertThat(myCpuService.getSession()).isEqualTo(FAKE_SESSION);
  }

  @Test
  public void stopProfilingCallStopMonitoringAppId() {
    Assume.assumeFalse("Unified pipeline does not go through StopMonitoringApp", myUnifiedPipeline);

    myCpuProfiler = new CpuProfiler(myProfilers);
    myCpuProfiler.stopProfiling(FAKE_SESSION);
    // Make sure the session of the service was set to FAKE_SESSION by the stop monitoring request
    assertThat(myCpuService.getSession()).isEqualTo(FAKE_SESSION);
  }

  @Test
  public void stopMonitoringStopsOngoingTraces() {
    myCpuProfiler = new CpuProfiler(myProfilers);

    myCpuProfiler.stopProfiling(FAKE_SESSION);
    if (myUnifiedPipeline) {
      StopCpuTrace stopCpuTrace = (StopCpuTrace)myTransportService.getRegisteredCommand(Commands.Command.CommandType.STOP_CPU_TRACE);
      assertThat(stopCpuTrace.getLastTraceInfo()).isEqualTo(Cpu.CpuTraceInfo.getDefaultInstance());

      myTransportService.addEventToStream(
        FAKE_SESSION.getStreamId(),
        Common.Event.newBuilder()
          .setTimestamp(1).setGroupId(1).setPid(FAKE_SESSION.getPid()).setKind(Common.Event.Kind.CPU_TRACE)
          .setCpuTrace(Cpu.CpuTraceData.newBuilder().setTraceStarted(
            Cpu.CpuTraceData.TraceStarted.newBuilder().setTraceInfo(Cpu.CpuTraceInfo.newBuilder().setTraceId(1).setToTimestamp(-1))))
          .build());
      myCpuProfiler.stopProfiling(FAKE_SESSION);
      assertThat(stopCpuTrace.getLastTraceInfo()).isNotEqualTo(Cpu.CpuTraceInfo.getDefaultInstance());
    }
    else {
      // No ongoing so we don't expect stop tracing to be called.
      assertThat(myCpuService.getStartStopCapturingSession()).isNull();

      // There is an ongoing trace so stop tracing should be called.
      myCpuService.addTraceInfo(Cpu.CpuTraceInfo.newBuilder().setTraceId(1).setFromTimestamp(10).setToTimestamp(-1).build());
      myCpuProfiler.stopProfiling(FAKE_SESSION);
      assertThat(myCpuService.getStartStopCapturingSession()).isEqualTo(FAKE_SESSION);
    }
  }

  @Test
  public void importedSessionListenerShouldBeRegistered() {
    Assume.assumeFalse("Unified pipeline import cannot yet be tested because of dependencies on TransportService.getInstance().",
                       myUnifiedPipeline);

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
    Assume.assumeFalse("Unified pipeline import cannot yet be tested because of dependencies on TransportService.getInstance().",
                       myUnifiedPipeline);

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
    Assume.assumeFalse("Unified pipeline import cannot yet be tested because of dependencies on TransportService.getInstance().",
                       myUnifiedPipeline);

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
    Assume.assumeFalse("Unified pipeline import cannot yet be tested because of dependencies on TransportService.getInstance().",
                       myUnifiedPipeline);

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
    Assume.assumeFalse("Unified pipeline import cannot yet be tested because of dependencies on TransportService.getInstance().",
                       myUnifiedPipeline);

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
    Assume.assumeFalse("Unified pipeline import cannot yet be tested because of dependencies on TransportService.getInstance().",
                       myUnifiedPipeline);

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

  @Test
  public void testGetTraceInfoFromSession() {
    Assume.assumeTrue(myUnifiedPipeline);

    Common.Session session = myProfilers.getSession();

    // Insert a completed CpuTraceInfo.
    Cpu.CpuTraceInfo info1 = Cpu.CpuTraceInfo.newBuilder()
      .setTraceId(1).setFromTimestamp(1).setToTimestamp(2)
      .setStartStatus(Cpu.TraceStartStatus.newBuilder().setStatus(Cpu.TraceStartStatus.Status.SUCCESS))
      .setStopStatus(Cpu.TraceStopStatus.newBuilder().setStatus(Cpu.TraceStopStatus.Status.SUCCESS))
      .build();
    myTransportService.addEventToStream(
      session.getStreamId(),
      Common.Event.newBuilder().setGroupId(1).setPid(session.getPid())
        .setIsEnded(true).setKind(Common.Event.Kind.CPU_TRACE).setTimestamp(1)
        .setCpuTrace(Cpu.CpuTraceData.newBuilder().setTraceEnded(Cpu.CpuTraceData.TraceEnded.newBuilder().setTraceInfo(info1))).build());

    List<Cpu.CpuTraceInfo> infos = CpuProfiler.getTraceInfoFromSession(myProfilers.getClient(), session, myUnifiedPipeline);
    assertThat(infos).containsExactly(info1);

    // Insert a not yet completed info followed up by a generic end event.
    Cpu.CpuTraceInfo info2 = Cpu.CpuTraceInfo.newBuilder()
      .setTraceId(5).setFromTimestamp(5).setToTimestamp(-1)
      .setStartStatus(Cpu.TraceStartStatus.newBuilder().setStatus(Cpu.TraceStartStatus.Status.SUCCESS))
      .build();
    myTransportService.addEventToStream(
      session.getStreamId(),
      Common.Event.newBuilder().setGroupId(5).setPid(session.getPid()).setKind(Common.Event.Kind.CPU_TRACE).setTimestamp(5)
        .setCpuTrace(Cpu.CpuTraceData.newBuilder().setTraceStarted(Cpu.CpuTraceData.TraceStarted.newBuilder().setTraceInfo(info2)))
        .build());
    myTransportService.addEventToStream(
      session.getStreamId(),
      Common.Event.newBuilder()
        .setTimestamp(10).setGroupId(5).setKind(Common.Event.Kind.CPU_TRACE).setPid(session.getPid()).setIsEnded(true).build());
    infos = CpuProfiler.getTraceInfoFromSession(myProfilers.getClient(), session, myUnifiedPipeline);
    assertThat(infos)
      .containsExactly(info1, info2.toBuilder().setToTimestamp(session.getEndTimestamp())
        .setStopStatus(Cpu.TraceStopStatus.newBuilder().setStatus(Cpu.TraceStopStatus.Status.APP_PROCESS_DIED)).build());
  }
}
