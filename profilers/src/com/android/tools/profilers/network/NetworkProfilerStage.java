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
import com.android.tools.adtui.model.formatter.BaseAxisFormatter;
import com.android.tools.adtui.model.formatter.NetworkTrafficFormatter;
import com.android.tools.adtui.model.formatter.SingleUnitAxisFormatter;
import com.android.tools.profilers.ProfilerMode;
import com.android.tools.profilers.Stage;
import com.android.tools.profilers.StudioProfilers;
import com.google.common.annotations.VisibleForTesting;
import com.android.tools.profilers.event.EventMonitor;
import com.google.protobuf3jarjar.ByteString;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class NetworkProfilerStage extends Stage {

  private static final BaseAxisFormatter TRAFFIC_AXIS_FORMATTER = new NetworkTrafficFormatter(1, 5, 5);
  private static final BaseAxisFormatter CONNECTIONS_AXIS_FORMATTER = new SingleUnitAxisFormatter(1, 5, 1, "");

  // If null, means no connection to show in the details pane.
  @Nullable
  private HttpData mySelectedConnection;

  private AspectModel<NetworkProfilerAspect> myAspect = new AspectModel<>();

  StateChartModel<NetworkRadioDataSeries.RadioState> myRadioState;

  private final NetworkConnectionsModel myConnectionsModel =
    new RpcNetworkConnectionsModel(getStudioProfilers().getClient().getNetworkClient(), getStudioProfilers().getProcessId());

  private final NetworkRadioDataSeries myRadioDataSeries =
    new NetworkRadioDataSeries(getStudioProfilers().getClient().getNetworkClient(), getStudioProfilers().getProcessId());
  private final NetworkMonitor myMonitor;
  private final LineChartModel myNetworkData;
  private final LegendComponentModel myLegends;
  private final AxisComponentModel myTrafficAxis;
  private final AxisComponentModel myConnectionsAxis;
  private final EventMonitor myEventMonitor;

  public NetworkProfilerStage(StudioProfilers profilers) {
    super(profilers);

    myRadioState = new StateChartModel<>();
    myRadioState.addSeries(new RangedSeries<>(getStudioProfilers().getTimeline().getViewRange(), getRadioDataSeries()));

    Range viewRange = profilers.getTimeline().getViewRange();
    Range dataRange = profilers.getTimeline().getDataRange();


    Range trafficRange = new Range(0, 4); //TODO: Why 4?
    Range connectionsRange = new Range(0, 5);

    myMonitor = new NetworkMonitor(profilers);
    RangedContinuousSeries receivedSeries = new RangedContinuousSeries(NetworkTrafficDataSeries.Type.BYTES_RECEIVED.getLabel(),
                                                                       viewRange,
                                                                       trafficRange,
                                                                       myMonitor.getSpeedSeries(NetworkTrafficDataSeries.Type.BYTES_RECEIVED));
    RangedContinuousSeries sentSeries = new RangedContinuousSeries(NetworkTrafficDataSeries.Type.BYTES_SENT.getLabel(),
                                                                   viewRange,
                                                                   trafficRange,
                                                                   myMonitor.getSpeedSeries(NetworkTrafficDataSeries.Type.BYTES_SENT));
    RangedContinuousSeries connectionSeries = new RangedContinuousSeries("Connections",
                                                                         viewRange,
                                                                         connectionsRange,
                                                                         myMonitor.getOpenConnectionsSeries());

    myNetworkData = new LineChartModel();
    myNetworkData.add(receivedSeries);
    myNetworkData.add(sentSeries);
    myNetworkData.add(connectionSeries);

    myTrafficAxis = new AxisComponentModel(trafficRange, TRAFFIC_AXIS_FORMATTER, AxisComponentModel.AxisOrientation.RIGHT);
    myTrafficAxis.clampToMajorTicks(true);

    myConnectionsAxis = new AxisComponentModel(connectionsRange, CONNECTIONS_AXIS_FORMATTER, AxisComponentModel.AxisOrientation.LEFT);
    myConnectionsAxis.clampToMajorTicks(true);

    myLegends = new LegendComponentModel(100);
    ArrayList<LegendData> legendData = new ArrayList<>();
    legendData.add(new LegendData(receivedSeries, TRAFFIC_AXIS_FORMATTER, dataRange));
    legendData.add(new LegendData(sentSeries, TRAFFIC_AXIS_FORMATTER, dataRange));
    legendData.add(new LegendData(connectionSeries, CONNECTIONS_AXIS_FORMATTER, dataRange));
    myLegends.setLegendData(legendData);

    myEventMonitor = new EventMonitor(profilers);
  }

  @Override
  public ProfilerMode getProfilerMode() {
    boolean noSelection = getStudioProfilers().getTimeline().getSelectionRange().isEmpty();
    return mySelectedConnection == null && noSelection ? ProfilerMode.NORMAL : ProfilerMode.EXPANDED;
  }

  @NotNull
  public NetworkConnectionsModel getConnectionsModel() {
    return myConnectionsModel;
  }

  @NotNull
  public NetworkRadioDataSeries getRadioDataSeries() {
    return myRadioDataSeries;
  }

  /**
   * Sets the active connection, or clears the previously selected active connection if given data is null.
   */
  public void setSelectedConnection(@Nullable HttpData data) {
    if (data != null && data.getResponsePayloadId() != null && data.getResponsePayloadFile() == null) {
      ByteString payload = getConnectionsModel().requestResponsePayload(data);
      try {
        File file = getConnectionPayload(payload, data);
        data.setResponsePayloadFile(file);
      } catch (IOException e) {
        return;
      }
    }
    mySelectedConnection = data;
    getStudioProfilers().modeChanged();
    getAspect().changed(NetworkProfilerAspect.ACTIVE_CONNECTION);
  }

  @VisibleForTesting
  File getConnectionPayload(@NotNull ByteString payload, @NotNull HttpData data) throws IOException {
    String contentType = data.getResponseField(HttpData.FIELD_CONTENT_TYPE);
    String extension = contentType == null ? "" : HttpData.guessFileExtensionFromContentType(contentType);
    File file = FileUtil.createTempFile(data.getResponsePayloadId(), extension, true);
    FileUtil.writeToFile(file, payload.toByteArray());
    return file;
  }

  /**
   * Returns the active connection, or {@code null} if no request is currently selected.
   */
  @Nullable
  public HttpData getSelectedConnection() {
    return mySelectedConnection;
  }

  @NotNull
  public AspectModel<NetworkProfilerAspect> getAspect() {
    return myAspect;
  }

  public StateChartModel<NetworkRadioDataSeries.RadioState> getRadioState() {
    return myRadioState;
  }

  @Override
  public void enter() {
    myMonitor.enter();
    myEventMonitor.enter();
    getStudioProfilers().getUpdater().register(myRadioState);
    getStudioProfilers().getUpdater().register(myNetworkData);
    getStudioProfilers().getUpdater().register(myTrafficAxis);
    getStudioProfilers().getUpdater().register(myConnectionsAxis);
    getStudioProfilers().getUpdater().register(myLegends);
  }

  @Override
  public void exit() {
    myMonitor.exit();
    myEventMonitor.exit();
    getStudioProfilers().getUpdater().unregister(myRadioState);
    getStudioProfilers().getUpdater().unregister(myNetworkData);
    getStudioProfilers().getUpdater().unregister(myTrafficAxis);
    getStudioProfilers().getUpdater().unregister(myConnectionsAxis);
    getStudioProfilers().getUpdater().unregister(myLegends);
  }

  public String getName() {
    return myMonitor.getName();
  }

  public LineChartModel getNetworkData() {
    return myNetworkData;
  }

  public AxisComponentModel getTrafficAxis() {
    return myTrafficAxis;
  }

  public AxisComponentModel getConnectionsAxis() {
    return myConnectionsAxis;
  }

  public LegendComponentModel getLegends() {
    return myLegends;
  }

  public EventMonitor getEventMonitor() {
    return myEventMonitor;
  }
}
