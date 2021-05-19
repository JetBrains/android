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

import static com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_DEVICE_NAME;
import static com.android.tools.idea.transport.faketransport.FakeTransportService.FAKE_PROCESS_NAME;
import static com.android.tools.profiler.proto.NetworkProfiler.NetworkProfilerData;
import static com.android.tools.profilers.ProfilersTestData.DEFAULT_AGENT_ATTACHED_RESPONSE;
import static com.android.tools.profilers.ProfilersTestData.DEFAULT_AGENT_DETACHED_RESPONSE;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.FakeTimer;
import com.android.tools.adtui.model.LineChartModel;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangedContinuousSeries;
import com.android.tools.adtui.model.axis.AxisComponentModel;
import com.android.tools.adtui.model.legend.LegendComponentModel;
import com.android.tools.idea.protobuf.ByteString;
import com.android.tools.idea.transport.faketransport.FakeGrpcChannel;
import com.android.tools.idea.transport.faketransport.FakeTransportService;
import com.android.tools.profiler.proto.Network;
import com.android.tools.profilers.FakeIdeProfilerServices;
import com.android.tools.profilers.FakeProfilerService;
import com.android.tools.profilers.ProfilerClient;
import com.android.tools.profilers.ProfilerMode;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.cpu.FakeCpuService;
import com.android.tools.profilers.event.FakeEventService;
import com.android.tools.profilers.memory.FakeMemoryService;
import com.android.tools.profilers.network.httpdata.HttpData;
import com.android.tools.profilers.network.httpdata.Payload;
import com.android.tools.profilers.network.httpdata.StackTrace;
import com.google.common.collect.ImmutableList;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class NetworkProfilerStageTest {
  private static final float EPSILON = 0.00001f;

  private static final ImmutableList<NetworkProfilerData> FAKE_DATA =
    new ImmutableList.Builder<NetworkProfilerData>()
      .add(FakeNetworkService.newSpeedData(0, 1, 2))
      .add(FakeNetworkService.newSpeedData(10, 3, 4))
      .add(FakeNetworkService.newSpeedData(100, 3000000, 4000000))
      .add(FakeNetworkService.newConnectionData(0, 4))
      .add(FakeNetworkService.newConnectionData(10, 6))
      .add(FakeNetworkService.newConnectionData(100, 8000))
      .add(FakeNetworkService.newRadioData(5, Network.NetworkTypeData.NetworkType.MOBILE))
      .build();

  private static final String TEST_PAYLOAD_ID = "test";
  private static final ImmutableList<HttpData> FAKE_HTTP_DATA =
    new ImmutableList.Builder<HttpData>()
      .add(TestHttpData.newBuilder(1, 1, 14)
             .setRequestPayloadId(TEST_PAYLOAD_ID)
             .setResponsePayloadId(TEST_PAYLOAD_ID).build())
      .build();

  private FakeTimer myTimer = new FakeTimer();
  private FakeTransportService myTransportService = new FakeTransportService(myTimer, true);
  @Rule public FakeGrpcChannel myGrpcChannel =
    new FakeGrpcChannel("NetworkProfilerStageTest", myTransportService, new FakeProfilerService(myTimer),
                        new FakeEventService(), new FakeCpuService(), new FakeMemoryService(),
                        FakeNetworkService.newBuilder().setNetworkDataList(FAKE_DATA)
                          .setHttpDataList(FAKE_HTTP_DATA).build());

  private NetworkProfilerStage myStage;


  private StudioProfilers myStudioProfilers;

  @Before
  public void setUp() {
    myStudioProfilers = new StudioProfilers(new ProfilerClient(myGrpcChannel.getChannel()), new FakeIdeProfilerServices(), myTimer);
    myStudioProfilers.setPreferredProcess(FAKE_DEVICE_NAME, FAKE_PROCESS_NAME, null);
    myStage = new NetworkProfilerStage(myStudioProfilers);
    myStage.getTimeline().getViewRange().set(TimeUnit.SECONDS.toMicros(0), TimeUnit.SECONDS.toMicros(5));
    myStage.getStudioProfilers().setStage(myStage);

    // TODO remove once we remove the legacy pipeline codebase.
    for (HttpData httpData : FAKE_HTTP_DATA) {
      String stackTrace = TestHttpData.fakeStackTrace(httpData.getId());
      myTransportService.addFile(TestHttpData.fakeStackTraceId(stackTrace), ByteString.copyFromUtf8(stackTrace));
    }
  }

  @Test
  public void getConnectionsModel() {
    String samplePayloadContent = "Sample Contents";
    myTransportService.addFile(TEST_PAYLOAD_ID, ByteString.copyFromUtf8(samplePayloadContent));

    NetworkConnectionsModel connectionsModel = myStage.getConnectionsModel();
    List<HttpData> dataList = connectionsModel.getData(new Range(TimeUnit.SECONDS.toMicros(0), TimeUnit.SECONDS.toMicros(16)));
    assertThat(dataList).hasSize(1);

    HttpData data = dataList.get(0);
    HttpData expectedData = FAKE_HTTP_DATA.get(0);

    assertThat(data.getRequestStartTimeUs()).isEqualTo(expectedData.getRequestStartTimeUs());
    assertThat(data.getRequestCompleteTimeUs()).isEqualTo(expectedData.getRequestCompleteTimeUs());
    assertThat(data.getResponseStartTimeUs()).isEqualTo(expectedData.getResponseStartTimeUs());
    assertThat(data.getResponseCompleteTimeUs()).isEqualTo(expectedData.getResponseCompleteTimeUs());
    assertThat(data.getConnectionEndTimeUs()).isEqualTo(expectedData.getConnectionEndTimeUs());
    assertThat(data.getMethod()).isEqualTo(expectedData.getMethod());
    assertThat(data.getUrl()).isEqualTo(expectedData.getUrl());
    assertThat(data.getTrace()).isEqualTo(expectedData.getTrace());
    assertThat(data.getRequestPayloadId()).isEqualTo(expectedData.getRequestPayloadId());
    assertThat(data.getResponsePayloadId()).isEqualTo(expectedData.getResponsePayloadId());
    assertThat(data.getResponseHeader().getField("connId")).isEqualTo(expectedData.getResponseHeader().getField("connId"));

    assertThat(Payload.newRequestPayload(connectionsModel, data).getBytes())
      .isEqualTo(Payload.newRequestPayload(connectionsModel, data).getBytes());
    assertThat(Payload.newResponsePayload(connectionsModel, data).getBytes())
      .isEqualTo(Payload.newResponsePayload(connectionsModel, data).getBytes());
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
    assertThat(networkLegends.getRxLegend().getValue()).isEqualTo("2 B/s");
    assertThat(networkLegends.getTxLegend().getValue()).isEqualTo("1 B/s");
    assertThat(networkLegends.getConnectionLegend().getValue()).isEqualTo("4");

    assertThat(networkLegends.getLegends()).hasSize(3);
  }

  @Test
  public void setTrafficTooltip() {
    myStage.enter();
    myStage.setTooltip(new NetworkTrafficTooltip(myStage));
    assertThat(myStage.getTooltip()).isInstanceOf(NetworkTrafficTooltip.class);
    NetworkTrafficTooltip tooltip = (NetworkTrafficTooltip)myStage.getTooltip();

    double tooltipTime = TimeUnit.SECONDS.toMicros(10);
    myStage.getTimeline().getTooltipRange().set(tooltipTime, tooltipTime);

    NetworkProfilerStage.NetworkStageLegends networkLegends = tooltip.getLegends();
    assertThat(networkLegends.getRxLegend().getName()).isEqualTo("Received");
    assertThat(networkLegends.getTxLegend().getName()).isEqualTo("Sent");
    assertThat(networkLegends.getConnectionLegend().getName()).isEqualTo("Connections");
    assertThat(networkLegends.getRxLegend().getValue()).isEqualTo("4 B/s");
    assertThat(networkLegends.getTxLegend().getValue()).isEqualTo("3 B/s");
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
    assertThat(networkUsageUpdated[0]).isTrue();
    assertThat(trafficAxisUpdated[0]).isTrue();
    assertThat(connectionAxisUpdated[0]).isTrue();
    assertThat(legendsUpdated[0]).isFalse();
    assertThat(tooltipLegendsUpdated[0]).isFalse();

    myStage.getTimeline().getViewRange().set(TimeUnit.SECONDS.toMicros(1), TimeUnit.SECONDS.toMicros(2));
    assertThat(networkUsageUpdated[0]).isTrue();

    // Make sure the axis lerps correctly when we move the range there.
    myStage.getTimeline().getDataRange().setMax(TimeUnit.SECONDS.toMicros(101));
    myStage.getTimeline().getViewRange().set(TimeUnit.SECONDS.toMicros(99), TimeUnit.SECONDS.toMicros(101));
    myTimer.tick(100);
    assertThat(legendsUpdated[0]).isTrue();
    assertThat(trafficAxisUpdated[0]).isTrue();
    assertThat(connectionAxisUpdated[0]).isTrue();
    myStage.getTimeline().getTooltipRange().set(TimeUnit.SECONDS.toMicros(100), TimeUnit.SECONDS.toMicros(100));
    assertThat(tooltipLegendsUpdated[0]).isTrue();
  }

  @Test
  public void updaterUnregisteredCorrectlyOnExit() {
    myStage.exit();
    AspectObserver observer = new AspectObserver();

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
    assertThat(networkUsageUpdated[0]).isFalse();
    assertThat(trafficAxisUpdated[0]).isFalse();
    assertThat(connectionAxisUpdated[0]).isFalse();
    assertThat(legendsUpdated[0]).isFalse();
    assertThat(tooltipLegendsUpdated[0]).isFalse();
  }

  @Test
  public void testSelectedConnection() {
    HttpData.Builder builder = TestHttpData.newBuilder(1, 2, 22);
    builder.setResponseFields("null  =  HTTP/1.1 302 Found \n Content-Type = image/jpeg; ")
      .setResponsePayloadId(TEST_PAYLOAD_ID);
    HttpData data = builder.build();
    String content = "Unzipped payload";
    myTransportService.addFile(TEST_PAYLOAD_ID, ByteString.copyFrom(content.getBytes(Charset.defaultCharset())));

    AspectObserver observer = new AspectObserver();
    final boolean[] connectionChanged = {false};
    myStage.getAspect().addDependency(observer).onChange(NetworkProfilerAspect.SELECTED_CONNECTION, () ->
      connectionChanged[0] = true
    );

    myStage.setSelectedConnection(data);
    assertThat(myStage.getSelectedConnection()).isEqualTo(data);
    Payload selectedConnectionPayload = Payload.newResponsePayload(myStage.getConnectionsModel(), myStage.getSelectedConnection());
    assertThat(selectedConnectionPayload.getBytes().toString(StandardCharsets.UTF_8)).isEqualTo(content);
    assertThat(connectionChanged[0]).isEqualTo(true);
  }

  @Test
  public void testSelectedConnectionWhenIdIsEmpty() {
    HttpData.Builder builder = TestHttpData.newBuilder(1, 2, 22);
    builder.setResponseFields("null  =  HTTP/1.1 302 Found \n Content-Type = image/jpeg; ")
      .setResponsePayloadId("");
    HttpData data = builder.build();

    myStage.setSelectedConnection(data);
    assertThat(Payload.newResponsePayload(myStage.getConnectionsModel(), data).getBytes()).isEmpty();
  }

  @Test
  public void setSelectionWithGzipEncodingResponsePayload() throws IOException {
    HttpData.Builder builder = TestHttpData.newBuilder(1, 2, 20);
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
    myTransportService.addFile(TEST_PAYLOAD_ID, zippedBytesString);

    assertThat(myStage.setSelectedConnection(data)).isTrue();
    String output = Payload.newResponsePayload(myStage.getConnectionsModel(), data).getBytes().toString(StandardCharsets.UTF_8);
    assertThat(output).isEqualTo(unzippedPayload);
  }

  @Test
  public void responsePayloadReturnsOriginalBytesIfInvalidGzipContent() throws IOException {
    HttpData.Builder builder = TestHttpData.newBuilder(1, 2, 20);
    builder.setResponsePayloadId(TEST_PAYLOAD_ID);
    builder.setResponseFields("null  =  HTTP/1.1 302 Found \n content-encoding=gzip \n");
    HttpData data = builder.build();

    String unzippedPayload = "Unzipped payload";
    byte[] unzippedBytes = unzippedPayload.getBytes(Charset.defaultCharset());
    ByteString unzippedBytesString = ByteString.copyFrom(unzippedBytes);
    myTransportService.addFile(TEST_PAYLOAD_ID, unzippedBytesString);

    assertThat(myStage.setSelectedConnection(data)).isTrue();
    String output = Payload.newResponsePayload(myStage.getConnectionsModel(), data).getBytes().toString(StandardCharsets.UTF_8);
    assertThat(output).isEqualTo(unzippedPayload);
  }

  @Test
  public void setSelectionWithGzipEncodingRequestPayload() throws IOException {
    HttpData.Builder builder = TestHttpData.newBuilder(1, 2, 20);
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
    myTransportService.addFile(TEST_PAYLOAD_ID, zippedBytesString);

    myStage.setSelectedConnection(data);
    String output = Payload.newRequestPayload(myStage.getConnectionsModel(), data).getBytes().toString(StandardCharsets.UTF_8);
    assertThat(output).isEqualTo(unzippedPayload);
  }

  @Test
  public void requestPayloadReturnsOriginalBytesIfInvalidGzipContent() throws IOException {
    HttpData.Builder builder = TestHttpData.newBuilder(1, 2, 20);
    builder.setRequestPayloadId(TEST_PAYLOAD_ID);
    builder.setRequestFields(" content-encoding=gzip \n");
    HttpData data = builder.build();

    String unzippedPayload = "Unzipped payload";
    byte[] unzippedBytes = unzippedPayload.getBytes(Charset.defaultCharset());
    ByteString unzippedBytesString = ByteString.copyFrom(unzippedBytes);
    myTransportService.addFile(TEST_PAYLOAD_ID, unzippedBytesString);

    myStage.setSelectedConnection(data);
    String output = Payload.newRequestPayload(myStage.getConnectionsModel(), data).getBytes().toString(StandardCharsets.UTF_8);
    assertThat(output).isEqualTo(unzippedPayload);
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
  public void getProfilerMode() {
    myStage.getTimeline().getSelectionRange().clear();
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.NORMAL);
    myStage.getTimeline().getSelectionRange().set(0, 10);
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.EXPANDED);
  }

  @Test
  public void codeNavigationUnexpandsProfiler() {
    HttpData data = FAKE_HTTP_DATA.get(0);
    StackTrace stackTrace = new StackTrace(data);
    assertThat(stackTrace.getTrace()).isEqualTo(data.getTrace());
    assertThat(stackTrace.getCodeLocations()).hasSize(2);
    myStage.getStackTraceModel().setStackFrames(stackTrace.getTrace());

    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.NORMAL);
    myStage.getTimeline().getSelectionRange().set(0, 10);
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.EXPANDED);

    myStage.getStackTraceModel().setSelectedIndex(0);
    assertThat(myStage.getProfilerMode()).isEqualTo(ProfilerMode.NORMAL);
  }

  @Test
  public void selectionDisabledWithoutAgent() {
    Range selection = myStage.getTimeline().getSelectionRange();

    myTransportService.setAgentStatus(DEFAULT_AGENT_ATTACHED_RESPONSE);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));
    // Need to re-enter the stage again given the device/process can be set and return to the default StudioMonitorStage.
    myStage.getStudioProfilers().setStage(myStage);

    assertThat(myStage.getStudioProfilers().isAgentAttached()).isTrue();

    myStage.getRangeSelectionModel().set(0, 100);
    assertThat(selection.getMin()).isWithin(EPSILON).of(0);
    assertThat(selection.getMax()).isWithin(EPSILON).of(100);

    myTransportService.setAgentStatus(DEFAULT_AGENT_DETACHED_RESPONSE);
    myTimer.tick(TimeUnit.SECONDS.toNanos(1));
    assertThat(myStage.getStudioProfilers().isAgentAttached()).isFalse();

    // Attempting to select a range should do nothing.
    myStage.getRangeSelectionModel().set(100, 200);
    assertThat(selection.getMin()).isWithin(EPSILON).of(0);
    assertThat(selection.getMax()).isWithin(EPSILON).of(100);
  }

  @Test
  public void testHasUserUsedSelection() {
    assertThat(myStage.getInstructionsEaseOutModel().getPercentageComplete()).isWithin(0f).of(0f);
    assertThat(myStage.hasUserUsedNetworkSelection()).isFalse();
    myStage.getRangeSelectionModel().setSelectionEnabled(true);
    myStage.getRangeSelectionModel().set(0, 100);
    assertThat(myStage.getInstructionsEaseOutModel().getPercentageComplete()).isWithin(0f).of(1f);
    assertThat(myStage.hasUserUsedNetworkSelection()).isTrue();
  }
}