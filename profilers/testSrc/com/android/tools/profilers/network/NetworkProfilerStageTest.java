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
import static com.google.common.truth.Truth.assertThat;
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
      .add(FakeNetworkService.newHttpDataBuilder(1, 0, 14).build())
      .build();
  private static final String TEST_PAYLOAD_ID = "test";

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
    assertThat(dataList).hasSize(1);

    HttpData data = dataList.get(0);
    HttpData expectedData = FAKE_HTTP_DATA.get(0);

    assertThat(data.getStartTimeUs()).isEqualTo(expectedData.getStartTimeUs());
    assertThat(data.getDownloadingTimeUs()).isEqualTo(expectedData.getDownloadingTimeUs());
    assertThat(data.getEndTimeUs()).isEqualTo(expectedData.getEndTimeUs());
    assertThat(data.getMethod()).isEqualTo(expectedData.getMethod());
    assertThat(data.getUrl()).isEqualTo(expectedData.getUrl());
    assertThat(data.getStackTrace().getTrace()).isEqualTo(expectedData.getStackTrace().getTrace());
    assertThat(data.getResponsePayloadId()).isEqualTo(expectedData.getResponsePayloadId());
    assertThat(data.getResponseField("connId")).isEqualTo(expectedData.getResponseField("connId"));
  }

  @Test
  public void getRadioState() {
    List<RangedSeries<NetworkRadioDataSeries.RadioState>> series = myStage.getRadioState().getSeries();
    assertThat(series).hasSize(1);

    List<SeriesData<NetworkRadioDataSeries.RadioState>> dataList = series.get(0).getSeries();
    assertThat(dataList).hasSize(1);
    assertThat(dataList.get(0).x).isEqualTo(TimeUnit.SECONDS.toMicros(5));
    assertThat(dataList.get(0).value).isEqualTo(NetworkRadioDataSeries.RadioState.HIGH);
  }

  @Test
  public void getName() {
    assertThat(myStage.getName()).isEqualTo("NETWORK");
  }

  @Test
  public void getTrafficAxis() {
    AxisComponentModel axis = myStage.getTrafficAxis();
    assertThat(axis).isNotNull();
    assertThat(axis.getRange()).isEqualTo(myStage.getDetailedNetworkUsage().getTrafficRange());
  }

  @Test
  public void getConnectionsAxis() {
    AxisComponentModel axis = myStage.getConnectionsAxis();
    assertThat(axis).isNotNull();
    assertThat(axis.getRange()).isEqualTo(myStage.getDetailedNetworkUsage().getConnectionsRange());
  }

  @Test
  public void getLegends() {
    NetworkProfilerStage.NetworkStageLegends networkLegends = myStage.getLegends();
    assertThat(networkLegends.getRxLegend().getName()).isEqualTo("Receiving");
    assertThat(networkLegends.getTxLegend().getName()).isEqualTo("Sending");
    assertThat(networkLegends.getConnectionLegend().getName()).isEqualTo("Connections");
    assertThat(networkLegends.getRxLegend().getValue()).isEqualTo("2 B/S");
    assertThat(networkLegends.getTxLegend().getValue()).isEqualTo("1 B/S");
    assertThat(networkLegends.getConnectionLegend().getValue()).isEqualTo("4");

    assertThat(networkLegends.getLegends()).hasSize(3);
  }

  @Test
  public void getTooltipLegends() {
    NetworkProfilerStage.NetworkStageLegends networkLegends = myStage.getTooltipLegends();

    double tooltipTime = TimeUnit.SECONDS.toMicros(10);
    myStage.getStudioProfilers().getTimeline().getTooltipRange().set(tooltipTime, tooltipTime);

    assertThat(networkLegends.getRxLegend().getName()).isEqualTo("Received");
    assertThat(networkLegends.getTxLegend().getName()).isEqualTo("Sent");
    assertThat(networkLegends.getConnectionLegend().getName()).isEqualTo("Connections");
    assertThat(networkLegends.getRxLegend().getValue()).isEqualTo("4 B/S");
    assertThat(networkLegends.getTxLegend().getValue()).isEqualTo("3 B/S");
    assertThat(networkLegends.getConnectionLegend().getValue()).isEqualTo("6");

    assertThat(networkLegends.getLegends()).hasSize(3);
  }

  @Test
  public void getDetailedNetworkUsage() {
    List<RangedContinuousSeries> series = myStage.getDetailedNetworkUsage().getSeries();
    assertThat(series).hasSize(3);
    RangedContinuousSeries receiving = series.get(0);
    RangedContinuousSeries sending = series.get(1);
    RangedContinuousSeries connections = series.get(2);
    assertThat(receiving.getName()).isEqualTo("Receiving");
    assertThat(sending.getName()).isEqualTo("Sending");
    assertThat(connections.getName()).isEqualTo("Connections");

    assertThat(receiving.getSeries()).hasSize(1);
    assertThat(receiving.getSeries().get(0).x).isEqualTo(0);
    assertThat(receiving.getSeries().get(0).value.longValue()).isEqualTo(2);

    assertThat(sending.getSeries()).hasSize(1);
    assertThat(sending.getSeries().get(0).x).isEqualTo(0);
    assertThat(sending.getSeries().get(0).value.longValue()).isEqualTo(1);

    assertThat(connections.getSeries()).hasSize(1);
    assertThat(connections.getSeries().get(0).x).isEqualTo(0);
    assertThat(connections.getSeries().get(0).value.longValue()).isEqualTo(4);
  }

  @Test
  public void getEventMonitor() {
    assertThat(myStage.getEventMonitor()).isNotNull();
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
    assertThat(radioStateUpdated[0]).isTrue();
    assertThat(networkUsageUpdated[0]).isTrue();
    assertThat(trafficAxisUpdated[0]).isTrue();
    assertThat(connectionAxisUpdated[0]).isTrue();
    assertThat(legendsUpdated[0]).isTrue();
    assertThat(tooltipLegendsUpdated[0]).isTrue();
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
    assertThat(radioStateUpdated[0]).isFalse();
    assertThat(networkUsageUpdated[0]).isFalse();
    assertThat(trafficAxisUpdated[0]).isFalse();
    assertThat(connectionAxisUpdated[0]).isFalse();
    assertThat(legendsUpdated[0]).isFalse();
    assertThat(tooltipLegendsUpdated[0]).isFalse();
  }

  @Test
  public void testSelectedConnection() {
    HttpData.Builder builder = new HttpData.Builder(1, 2, 22);
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
    assertThat(payloadFile).isNotNull();
    assertThat(payloadFile.canWrite()).isFalse();
    assertThat(myStage.getSelectedConnection()).isEqualTo(data);
    assertThat(connectionChanged[0]).isEqualTo(true);
  }

  @Test
  public void testSelectedConnectionWhenIdIsEmpty() {
    HttpData.Builder builder = new HttpData.Builder(1, 2, 22);
    builder.setResponseFields("null  =  HTTP/1.1 302 Found \n Content-Type = image/jpeg; ")
      .setResponsePayloadId("");
    HttpData data = builder.build();

    myStage.setSelectedConnection(data);
    assertThat(data.getResponsePayloadFile()).isNull();
  }

  @Test
  public void setSelectionWithGzipEncodingResponsePayload() throws IOException {
    HttpData.Builder builder = new HttpData.Builder(1, 2, 20);
    builder.setResponsePayloadId(TEST_PAYLOAD_ID);
    builder.setResponseFields("null  =  HTTP/1.1 302 Found \n content-encoding=gzip \n");
    HttpData data = builder.build();

    String unzippedPayload = "Unzipped payload";
    byte[] unzippedBytes = unzippedPayload.getBytes(Charset.defaultCharset());
    ByteArrayOutputStream zippedBytes = new ByteArrayOutputStream();
    try (GZIPOutputStream compressor = new GZIPOutputStream(zippedBytes)) {
      compressor.write(unzippedBytes);
    }
    assertThat(zippedBytes.toByteArray().length).isGreaterThan(0);
    assertThat(zippedBytes.toByteArray().length).isNotEqualTo(unzippedBytes.length);
    ByteString zippedBytesString = ByteString.copyFrom(zippedBytes.toByteArray());
    myProfilerService.addFile(TEST_PAYLOAD_ID, zippedBytesString);

    assertThat(myStage.setSelectedConnection(data)).isTrue();
    try (BufferedReader reader = Files.newBufferedReader(data.getResponsePayloadFile().toPath())) {
      String output = reader.readLine();
      assertThat(output).isEqualTo(unzippedPayload);
    }
  }

  @Test
  public void responsePayloadReturnsOriginalBytesIfInvalidGzipContent() throws IOException {
    HttpData.Builder builder = new HttpData.Builder(1, 2, 20);
    builder.setResponsePayloadId(TEST_PAYLOAD_ID);
    builder.setResponseFields("null  =  HTTP/1.1 302 Found \n content-encoding=gzip \n");
    HttpData data = builder.build();

    String unzippedPayload = "Unzipped payload";
    byte[] unzippedBytes = unzippedPayload.getBytes(Charset.defaultCharset());
    ByteString unzippedBytesString = ByteString.copyFrom(unzippedBytes);
    myProfilerService.addFile(TEST_PAYLOAD_ID, unzippedBytesString);

    assertThat(myStage.setSelectedConnection(data)).isTrue();
    try (BufferedReader reader = Files.newBufferedReader(data.getResponsePayloadFile().toPath())) {
      String output = reader.readLine();
      assertThat(output).isEqualTo(unzippedPayload);
    }
  }

  @Test
  public void setSelectionWithGzipEncodingRequestPayload() throws IOException {
    HttpData.Builder builder = new HttpData.Builder(1, 2, 20);
    builder.setRequestPayloadId(TEST_PAYLOAD_ID);
    builder.setRequestFields("content-encoding=gzip \n");
    HttpData data = builder.build();

    String unzippedPayload = "Unzipped payload";
    byte[] unzippedBytes = unzippedPayload.getBytes(Charset.defaultCharset());
    ByteArrayOutputStream zippedBytes = new ByteArrayOutputStream();
    try (GZIPOutputStream compressor = new GZIPOutputStream(zippedBytes)) {
      compressor.write(unzippedBytes);
    }
    assertThat(zippedBytes.toByteArray().length).isGreaterThan(0);
    assertThat(zippedBytes.toByteArray().length).isNotEqualTo(unzippedBytes.length);
    ByteString zippedBytesString = ByteString.copyFrom(zippedBytes.toByteArray());
    myProfilerService.addFile(TEST_PAYLOAD_ID, zippedBytesString);

    myStage.setSelectedConnection(data);
    try (BufferedReader reader = Files.newBufferedReader(data.getRequestPayloadFile().toPath())) {
      String output = reader.readLine();
      assertThat(output).isEqualTo(unzippedPayload);
    }
  }

  @Test
  public void requestPayloadReturnsOriginalBytesIfInvalidGzipContent() throws IOException {
    HttpData.Builder builder = new HttpData.Builder(1, 2, 20);
    builder.setRequestPayloadId(TEST_PAYLOAD_ID);
    builder.setRequestFields(" content-encoding=gzip \n");
    HttpData data = builder.build();

    String unzippedPayload = "Unzipped payload";
    byte[] unzippedBytes = unzippedPayload.getBytes(Charset.defaultCharset());
    ByteString unzippedBytesString = ByteString.copyFrom(unzippedBytes);
    myProfilerService.addFile(TEST_PAYLOAD_ID, unzippedBytesString);

    myStage.setSelectedConnection(data);
    try (BufferedReader reader = Files.newBufferedReader(data.getRequestPayloadFile().toPath())) {
      String output = reader.readLine();
      assertThat(output).isEqualTo(unzippedPayload);
    }
  }

  @Test
  public void setSelectionWithExistingPayloadFile() throws IOException {
    HttpData.Builder builder = new HttpData.Builder(1, 2, 20);
    builder.setResponsePayloadId(TEST_PAYLOAD_ID);
    builder.setResponseFields("null  =  HTTP/1.1 302 Found \n content-encoding=gzip \n");
    HttpData data = builder.build();
    String payload = "payload";
    myProfilerService.addFile(TEST_PAYLOAD_ID, ByteString.copyFrom(payload.getBytes(Charset.defaultCharset())));
    myStage.setSelectedConnection(data);
    // After payload file exists, remove file from profiler service.
    myProfilerService.removeFile(TEST_PAYLOAD_ID);
    // Set to a different selection because set the same selection twice will be skipped.
    myStage.setSelectedConnection(new HttpData.Builder(2, 2, 20).build());

    myStage.setSelectedConnection(data);
    try (BufferedReader reader = Files.newBufferedReader(data.getResponsePayloadFile().toPath())) {
      assertThat(reader.readLine()).isEqualTo(payload);
    }
  }

  private static ByteString gzip(String input) {
    ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    try (GZIPOutputStream compressor = new GZIPOutputStream(byteOutputStream)) {
      compressor.write(input.getBytes());
    }
    catch (IOException ignored) {
    }
    return ByteString.copyFrom(byteOutputStream.toByteArray());
  }

  @Test
  public void testIoExceptionThrownWhenSelectConnection() throws IOException {
    HttpData.Builder builder = new HttpData.Builder(1, 2, 22);
    builder.setResponseFields("null  =  HTTP/1.1 302 Found \n Content-Type = image/jpeg; ")
      .setResponsePayloadId("payloadId");
    HttpData data = builder.build();

    NetworkProfilerStage spyStage = spy(myStage);

    AspectObserver observer = new AspectObserver();
    final boolean[] connectionChanged = {false};
    spyStage.getAspect().addDependency(observer).
      onChange(NetworkProfilerAspect.SELECTED_CONNECTION, () -> connectionChanged[0] = true);

    doThrow(IOException.class).when(spyStage).getConnectionsModel();
    assertThat(spyStage.setSelectedConnection(data)).isFalse();
    assertThat(data.getResponsePayloadFile()).isNull();
    assertThat(spyStage.getSelectedConnection()).isNull();
    assertThat(connectionChanged[0]).isFalse();
  }

  @Test
  public void getProfilerMode() {
    myStage.getStudioProfilers().getTimeline().getSelectionRange().clear();
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.NORMAL);
    myStage.getStudioProfilers().getTimeline().getSelectionRange().set(0, 10);
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.EXPANDED);
  }

  @Test
  public void codeNavigationUnexpandsProfiler() {
    HttpData data = FAKE_HTTP_DATA.get(0);
    assertThat(data.getStackTrace().getCodeLocations()).hasSize(2);
    myStage.getStackTraceModel().setStackFrames(data.getStackTrace().getTrace());

    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.NORMAL);
    myStage.getStudioProfilers().getTimeline().getSelectionRange().set(0, 10);
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.EXPANDED);

    myStage.getStackTraceModel().setSelectedIndex(0);
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.NORMAL);
  }

  @Test
  public void selectionDisabledWithoutAgent() {
    Range selection = myStage.getStudioProfilers().getTimeline().getSelectionRange();

    myProfilerService.setAgentStatus(Profiler.AgentStatusResponse.Status.ATTACHED);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));
    // Need to re-enter the stage again given the device/process can be set and return to the default StudioMonitorStage.
    myStage.getStudioProfilers().setStage(myStage);

    assertThat(myStage.getStudioProfilers().isAgentAttached()).isTrue();
    myStage.getSelectionModel().set(0, 100);
    assertThat(selection.getMin()).isWithin(EPSILON).of(0);
    assertThat(selection.getMax()).isWithin(EPSILON).of(100);

    myProfilerService.setAgentStatus(Profiler.AgentStatusResponse.Status.DETACHED);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));
    assertThat(myStage.getStudioProfilers().isAgentAttached()).isFalse();

    // Attempting to select a range should do nothing.
    myStage.getSelectionModel().set(100, 200);
    assertThat(selection.getMin()).isWithin(EPSILON).of(0);
    assertThat(selection.getMax()).isWithin(EPSILON).of(100);
  }

  @Test
  public void testHasUserUsedSelection() {
    assertThat(myStage.getInstructionsEaseOutModel().getPercentageComplete()).isWithin(0f).of(0f);
    assertThat(myStage.hasUserUsedNetworkSelection()).isFalse();
    myStage.getSelectionModel().setSelectionEnabled(true);
    myStage.getSelectionModel().set(0, 100);
    assertThat(myStage.getInstructionsEaseOutModel().getPercentageComplete()).isWithin(0f).of(1f);
    assertThat(myStage.hasUserUsedNetworkSelection()).isTrue();
  }
}