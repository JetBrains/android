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
package com.android.tools.profilers.network;

import com.android.tools.adtui.model.*;
import com.android.tools.adtui.model.legend.LegendComponentModel;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profilers.*;
import com.google.common.collect.ImmutableList;
import com.google.protobuf3jarjar.ByteString;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

import static com.android.tools.profiler.proto.NetworkProfiler.ConnectivityData;
import static com.android.tools.profiler.proto.NetworkProfiler.NetworkProfilerData;
import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

public class NetworkProfilerStageTest {
  private static final float EPSILON = 0.00001f;

  private static final ImmutableList<NetworkProfilerData> FAKE_DATA =
    new ImmutableList.Builder<NetworkProfilerData>()
      .add(FakeNetworkService.newSpeedData(0, 1, 2))
      .add(FakeNetworkService.newSpeedData(10, 3, 4))
      .add(FakeNetworkService.newConnectionData(0, 4))
      .add(FakeNetworkService.newConnectionData(10, 6))
      .add(FakeNetworkService.newRadioData(5, ConnectivityData.NetworkType.MOBILE, ConnectivityData.RadioState.HIGH))
      .build();

  private static final ImmutableList<HttpData> FAKE_HTTP_DATA =
    new ImmutableList.Builder<HttpData>()
      .add(FakeNetworkService.newHttpData(7, 0, 7, 14))
      .build();


  private FakeProfilerService myProfilerService = new FakeProfilerService(true);
  @Rule public FakeGrpcChannel myGrpcChannel = new FakeGrpcChannel("NetworkProfilerStageTest", myProfilerService,
                                                                   FakeNetworkService.newBuilder().setNetworkDataList(FAKE_DATA)
                                                                     .setHttpDataList(FAKE_HTTP_DATA).build());

  private NetworkProfilerStage myStage;

  private FakeTimer myTimer;

  @Before
  public void setUp() {
    myTimer = new FakeTimer();
    StudioProfilers profilers = new StudioProfilers(myGrpcChannel.getClient(), new FakeIdeProfilerServices(), myTimer);
    myStage = new NetworkProfilerStage(profilers);
    myStage.getStudioProfilers().getTimeline().getViewRange().set(TimeUnit.SECONDS.toMicros(0), TimeUnit.SECONDS.toMicros(5));
    myStage.getStudioProfilers().setStage(myStage);
  }

  @Test
  public void getConnectionsModel() {
    List<HttpData> dataList = myStage.getConnectionsModel().getData(new Range(TimeUnit.SECONDS.toMicros(0), TimeUnit.SECONDS.toMicros(16)));
    assertEquals(1, dataList.size());
    HttpData data= dataList.get(0);
    assertEquals(FAKE_HTTP_DATA.get(0).getStartTimeUs(), data.getStartTimeUs());
    assertEquals(FAKE_HTTP_DATA.get(0).getDownloadingTimeUs(), data.getDownloadingTimeUs());
    assertEquals(FAKE_HTTP_DATA.get(0).getEndTimeUs(), data.getEndTimeUs());
    assertEquals(FAKE_HTTP_DATA.get(0).getMethod(), data.getMethod());
    assertEquals(FAKE_HTTP_DATA.get(0).getUrl(), data.getUrl());
    assertEquals(FAKE_HTTP_DATA.get(0).getStackTrace().getTrace(), data.getStackTrace().getTrace());
    assertEquals(FAKE_HTTP_DATA.get(0).getResponsePayloadId(), data.getResponsePayloadId());
    assertEquals(FAKE_HTTP_DATA.get(0).getResponseField("connId"), data.getResponseField("connId"));
  }

  @Test
  public void getRadioState() {
    List<RangedSeries<NetworkRadioDataSeries.RadioState>> series = myStage.getRadioState().getSeries();
    assertEquals(1, series.size());

    List<SeriesData<NetworkRadioDataSeries.RadioState>> dataList = series.get(0).getSeries();
    assertEquals(1, dataList.size());
    assertEquals(TimeUnit.SECONDS.toMicros(5), dataList.get(0).x);
    assertEquals(NetworkRadioDataSeries.RadioState.HIGH, dataList.get(0).value);
  }

  @Test
  public void getName() {
    assertEquals("NETWORK", myStage.getName());
  }

  @Test
  public void getTrafficAxis() {
    AxisComponentModel axis = myStage.getTrafficAxis();
    assertNotNull(axis);
    assertEquals(myStage.getDetailedNetworkUsage().getTrafficRange(), axis.getRange());
  }

  @Test
  public void getConnectionsAxis() {
    AxisComponentModel axis = myStage.getConnectionsAxis();
    assertNotNull(axis);
    assertEquals(myStage.getDetailedNetworkUsage().getConnectionsRange(), axis.getRange());
  }

  @Test
  public void getLegends() {
    NetworkProfilerStage.NetworkStageLegends networkLegends = myStage.getLegends();
    assertEquals("Receiving", networkLegends.getRxLegend().getName());
    assertEquals("Sending", networkLegends.getTxLegend().getName());
    assertEquals("Connections", networkLegends.getConnectionLegend().getName());
    assertEquals("2 B/S", networkLegends.getRxLegend().getValue());
    assertEquals("1 B/S", networkLegends.getTxLegend().getValue());
    assertEquals("4", networkLegends.getConnectionLegend().getValue());

    assertEquals(3, networkLegends.getLegends().size());
  }

  @Test
  public void getTooltipLegends() {
    NetworkProfilerStage.NetworkStageLegends networkLegends = myStage.getTooltipLegends();

    double tooltipTime = TimeUnit.SECONDS.toMicros(10);
    myStage.getStudioProfilers().getTimeline().getTooltipRange().set(tooltipTime, tooltipTime);

    assertEquals("Received", networkLegends.getRxLegend().getName());
    assertEquals("Sent", networkLegends.getTxLegend().getName());
    assertEquals("Connections", networkLegends.getConnectionLegend().getName());
    assertEquals("4 B/S", networkLegends.getRxLegend().getValue());
    assertEquals("3 B/S", networkLegends.getTxLegend().getValue());
    assertEquals("6", networkLegends.getConnectionLegend().getValue());

    assertEquals(3, networkLegends.getLegends().size());
  }

  @Test
  public void getDetailedNetworkUsage() {
    List<RangedContinuousSeries> series = myStage.getDetailedNetworkUsage().getSeries();
    assertEquals(3, series.size());
    RangedContinuousSeries receiving = series.get(0);
    RangedContinuousSeries sending = series.get(1);
    RangedContinuousSeries connections = series.get(2);
    assertEquals("Receiving", receiving.getName());
    assertEquals("Sending", sending.getName());
    assertEquals("Connections", connections.getName());

    assertEquals(1, receiving.getSeries().size());
    assertEquals(0, receiving.getSeries().get(0).x);
    assertEquals(2, receiving.getSeries().get(0).value.longValue());

    assertEquals(1, sending.getSeries().size());
    assertEquals(0, sending.getSeries().get(0).x);
    assertEquals(1, sending.getSeries().get(0).value.longValue());

    assertEquals(1, connections.getSeries().size());
    assertEquals(0, connections.getSeries().get(0).x);
    assertEquals(4, connections.getSeries().get(0).value.longValue());
  }

  @Test
  public void getEventMonitor() {
    assertNotNull(myStage.getEventMonitor());
  }

  @Test
  public void updaterRegisteredCorrectly() {
    AspectObserver observer = new AspectObserver();

    final boolean[] radioStateUpdated = {false};
    myStage.getRadioState().addDependency(observer).onChange(
      StateChartModel.Aspect.STATE_CHART, () -> radioStateUpdated[0] = true);

    final boolean[] networkUsageUpdated = {false};
    myStage.getDetailedNetworkUsage().addDependency(observer).onChange(
      LineChartModel.Aspect.LINE_CHART, () -> networkUsageUpdated[0] = true);

    final boolean[] trafficAxisUpdated = {false};
    myStage.getTrafficAxis().addDependency(observer).onChange(
      AxisComponentModel.Aspect.AXIS, () -> trafficAxisUpdated[0] = true);

    final boolean[] connectionAxisUpdated = {false};
    myStage.getConnectionsAxis().addDependency(observer).onChange(
      AxisComponentModel.Aspect.AXIS, () -> connectionAxisUpdated[0] = true);

    final boolean[] legendsUpdated = {false};
    myStage.getLegends().addDependency(observer).onChange(
      LegendComponentModel.Aspect.LEGEND, () -> legendsUpdated[0] = true);

    final boolean[] tooltipLegendsUpdated = {false};
    myStage.getTooltipLegends().addDependency(observer).onChange(
      LegendComponentModel.Aspect.LEGEND, () -> tooltipLegendsUpdated[0] = true
    );

    myTimer.tick(1);
    assertTrue(radioStateUpdated[0]);
    assertTrue(networkUsageUpdated[0]);
    assertTrue(trafficAxisUpdated[0]);
    assertTrue(connectionAxisUpdated[0]);
    assertTrue(legendsUpdated[0]);
    assertTrue(tooltipLegendsUpdated[0]);
  }

  @Test
  public void updaterUnregisteredCorrectlyOnExit() {
    myStage.exit();
    AspectObserver observer = new AspectObserver();

    final boolean[] radioStateUpdated = {false};
    myStage.getRadioState().addDependency(observer).onChange(
      StateChartModel.Aspect.STATE_CHART, () -> radioStateUpdated[0] = true);

    final boolean[] networkUsageUpdated = {false};
    myStage.getDetailedNetworkUsage().addDependency(observer).onChange(
      LineChartModel.Aspect.LINE_CHART, () -> networkUsageUpdated[0] = true);

    final boolean[] trafficAxisUpdated = {false};
    myStage.getTrafficAxis().addDependency(observer).onChange(
      AxisComponentModel.Aspect.AXIS, () -> trafficAxisUpdated[0] = true);

    final boolean[] connectionAxisUpdated = {false};
    myStage.getConnectionsAxis().addDependency(observer).onChange(
      AxisComponentModel.Aspect.AXIS, () -> connectionAxisUpdated[0] = true);

    final boolean[] legendsUpdated = {false};
    myStage.getLegends().addDependency(observer).onChange(
      LegendComponentModel.Aspect.LEGEND, () -> legendsUpdated[0] = true);

    final boolean[] tooltipLegendsUpdated = {false};
    myStage.getTooltipLegends().addDependency(observer).onChange(
      LegendComponentModel.Aspect.LEGEND, () -> tooltipLegendsUpdated[0] = true);

    myTimer.tick(1);
    assertFalse(radioStateUpdated[0]);
    assertFalse(networkUsageUpdated[0]);
    assertFalse(trafficAxisUpdated[0]);
    assertFalse(connectionAxisUpdated[0]);
    assertFalse(legendsUpdated[0]);
    assertFalse(tooltipLegendsUpdated[0]);
  }

  @Test
  public void testSelectedConnection() {
    HttpData.Builder builder = new HttpData.Builder(1, 2, 22, 22);
    builder.setResponseFields("null  =  HTTP/1.1 302 Found \n Content-Type = image/jpeg; ")
      .setResponsePayloadId("payloadId");
    HttpData data = builder.build();

    AspectObserver observer = new AspectObserver();
    final boolean[] connectionChanged = {false};
    myStage.getAspect().addDependency(observer).onChange(NetworkProfilerAspect.SELECTED_CONNECTION, () ->
      connectionChanged[0] = true
    );

    myStage.setSelectedConnection(data);
    File payloadFile = data.getResponsePayloadFile();
    assertNotNull(payloadFile);
    assertFalse(payloadFile.canWrite());
    assertEquals(data, myStage.getSelectedConnection());
    assertEquals(true, connectionChanged[0]);
  }

  @Test
  public void testSelectedConnectionWhenIdIsEmpty() {
    HttpData.Builder builder = new HttpData.Builder(1, 2, 22, 22);
    builder.setResponseFields("null  =  HTTP/1.1 302 Found \n Content-Type = image/jpeg; ")
      .setResponsePayloadId("");
    HttpData data = builder.build();

    myStage.setSelectedConnection(data);
    assertNull(data.getResponsePayloadFile());
  }

  @Test
  public void getConnectionPayloadRespectsContentEncodingGzip() throws IOException {
    HttpData.Builder builder = new HttpData.Builder(1, 2, 20, 20);
    builder.setResponsePayloadId("test");
    builder.setResponseFields("null  =  HTTP/1.1 302 Found \n content-encoding=gzip \n");
    HttpData data = builder.build();

    String unzippedPayload = "Unzipped payload";
    byte[] unzippedBytes = unzippedPayload.getBytes(Charset.defaultCharset());

    ByteArrayOutputStream zippedBytes = new ByteArrayOutputStream();
    try (GZIPOutputStream compressor = new GZIPOutputStream(zippedBytes)) {
      compressor.write(unzippedBytes);
    }
    assertTrue(zippedBytes.toByteArray().length > 0);
    assertNotEquals(unzippedBytes.length, zippedBytes.toByteArray().length);
    ByteString zippedBytesString = ByteString.copyFrom(zippedBytes.toByteArray());

    File file = myStage.getConnectionPayload(zippedBytesString, data);
    try (BufferedReader reader = Files.newBufferedReader(file.toPath())) {
      String output = reader.readLine();
      assertEquals(unzippedPayload, output);
    }
  }

  @Test
  public void getConnectionPayloadReturnsOriginalBytesIfInvalidGzipContent() throws IOException {
    HttpData.Builder builder = new HttpData.Builder(1, 2, 20, 20);
    builder.setResponsePayloadId("test");
    builder.setResponseFields("null  =  HTTP/1.1 302 Found \n content-encoding=gzip \n");
    HttpData data = builder.build();

    String unzippedPayload = "Unzipped payload";
    byte[] unzippedBytes = unzippedPayload.getBytes(Charset.defaultCharset());
    ByteString unzippedBytesString = ByteString.copyFrom(unzippedBytes);

    File file = myStage.getConnectionPayload(unzippedBytesString, data);
    try (BufferedReader reader = Files.newBufferedReader(file.toPath())) {
      String output = reader.readLine();
      assertEquals(unzippedPayload, output);
    }
  }

  private ByteString gzip(String input) {
    ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    try (GZIPOutputStream compressor = new GZIPOutputStream(byteOutputStream)) {
      compressor.write(input.getBytes());
    } catch (IOException ignored) {}
    return ByteString.copyFrom(byteOutputStream.toByteArray());
  }

  @Test
  public void testIoExceptionThrownWhenSelectConnection() throws IOException {
    HttpData.Builder builder = new HttpData.Builder(1, 2, 22, 22);
    builder.setResponseFields("null  =  HTTP/1.1 302 Found \n Content-Type = image/jpeg; ")
      .setResponsePayloadId("payloadId");
    HttpData data = builder.build();

    NetworkProfilerStage spyStage = spy(myStage);

    AspectObserver observer = new AspectObserver();
    final boolean[] connectionChanged = {false};
    spyStage.getAspect().addDependency(observer).
      onChange(NetworkProfilerAspect.SELECTED_CONNECTION, () -> connectionChanged[0] = true);

    doThrow(IOException.class).when(spyStage).getConnectionPayload(any(ByteString.class), any(HttpData.class));
    spyStage.setSelectedConnection(data);
    assertNull(data.getResponsePayloadFile());
    assertNull(spyStage.getSelectedConnection());
    assertEquals(false, connectionChanged[0]);
  }

  @Test
  public void getProfilerMode() {
    myStage.getStudioProfilers().getTimeline().getSelectionRange().clear();
    assertEquals(ProfilerMode.NORMAL, myStage.getProfilerMode());
    myStage.getStudioProfilers().getTimeline().getSelectionRange().set(0, 10);
    assertEquals(ProfilerMode.EXPANDED, myStage.getProfilerMode());
  }

  @Test
  public void codeNavigationUnexpandsProfiler() {
    HttpData data = FAKE_HTTP_DATA.get(0);
    assertEquals(2, data.getStackTrace().getCodeLocations().size());
    myStage.getStackTraceModel().setStackFrames(data.getStackTrace().getTrace());

    assertEquals(ProfilerMode.NORMAL, myStage.getProfilerMode());
    myStage.getStudioProfilers().getTimeline().getSelectionRange().set(0, 10);
    assertEquals(ProfilerMode.EXPANDED, myStage.getProfilerMode());

    myStage.getStackTraceModel().setSelectedIndex(0);
    assertEquals(ProfilerMode.NORMAL, myStage.getProfilerMode());
  }

  @Test
  public void selectionDisabledWithoutAgent() {
    Range selection = myStage.getStudioProfilers().getTimeline().getSelectionRange();

    myProfilerService.setAgentStatus(Profiler.AgentStatusResponse.Status.ATTACHED);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));
    // Need to re-enter the stage again given the device/process can be set and return to the default StudioMonitorStage.
    myStage.getStudioProfilers().setStage(myStage);

    assertTrue(myStage.getStudioProfilers().isAgentAttached());
    myStage.getSelectionModel().set(0, 100);
    assertEquals(0, selection.getMin(), EPSILON);
    assertEquals(100, selection.getMax(), EPSILON);

    myProfilerService.setAgentStatus(Profiler.AgentStatusResponse.Status.DETACHED);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));
    assertFalse(myStage.getStudioProfilers().isAgentAttached());

    // Attempting to select a range should do nothing.
    myStage.getSelectionModel().set(100, 200);
    assertEquals(0, selection.getMin(), EPSILON);
    assertEquals(100, selection.getMax(), EPSILON);
  }
}