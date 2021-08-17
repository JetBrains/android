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

import static com.android.tools.profilers.network.NetworkTrafficDataSeries.Type.BYTES_RECEIVED;
import static com.android.tools.profilers.network.NetworkTrafficDataSeries.Type.BYTES_SENT;

import com.android.tools.adtui.model.AspectModel;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.EaseOutModel;
import com.android.tools.adtui.model.Interpolatable;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangeSelectionListener;
import com.android.tools.adtui.model.RangeSelectionModel;
import com.android.tools.adtui.model.axis.AxisComponentModel;
import com.android.tools.adtui.model.axis.ClampedAxisComponentModel;
import com.android.tools.adtui.model.formatter.BaseAxisFormatter;
import com.android.tools.adtui.model.formatter.NetworkTrafficFormatter;
import com.android.tools.adtui.model.formatter.SingleUnitAxisFormatter;
import com.android.tools.adtui.model.legend.LegendComponentModel;
import com.android.tools.adtui.model.legend.SeriesLegend;
import com.android.tools.inspectors.common.api.stacktrace.CodeLocation;
import com.android.tools.inspectors.common.api.stacktrace.CodeNavigator;
import com.android.tools.inspectors.common.api.stacktrace.StackTraceModel;
import com.android.tools.profilers.ProfilerAspect;
import com.android.tools.profilers.ProfilerMode;
import com.android.tools.profilers.StreamingStage;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.event.EventMonitor;
import com.android.tools.profilers.network.httpdata.HttpData;
import com.google.wireless.android.sdk.stats.AndroidProfilerEvent;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NetworkProfilerStage extends StreamingStage implements CodeNavigator.Listener {
  private static final String HAS_USED_NETWORK_SELECTION = "network.used.selection";

  private static final BaseAxisFormatter TRAFFIC_AXIS_FORMATTER = new NetworkTrafficFormatter(1, 5, 5);
  private static final BaseAxisFormatter CONNECTIONS_AXIS_FORMATTER = new SingleUnitAxisFormatter(1, 5, 1, "");

  // If null, means no connection to show in the details pane.
  @Nullable
  private HttpData mySelectedConnection;

  // Intentionally local field, to prevent GC from cleaning it and removing weak listeners
  @SuppressWarnings("FieldCanBeLocal") private AspectObserver myAspectObserver = new AspectObserver();
  private AspectModel<NetworkProfilerAspect> myAspect = new AspectModel<>();

  private final NetworkConnectionsModel myConnectionsModel;

  private final DetailedNetworkUsage myDetailedNetworkUsage;
  private final NetworkStageLegends myLegends;
  private final NetworkStageLegends myTooltipLegends;
  private final ClampedAxisComponentModel myTrafficAxis;
  private final ClampedAxisComponentModel myConnectionsAxis;
  private final EventMonitor myEventMonitor;
  private final StackTraceModel myStackTraceModel;
  private final RangeSelectionModel myRangeSelectionModel;
  private final HttpDataFetcher myHttpDataFetcher;
  private final EaseOutModel myInstructionsEaseOutModel;

  public NetworkProfilerStage(StudioProfilers profilers) {
    super(profilers);

    myDetailedNetworkUsage = new DetailedNetworkUsage(profilers);

    myTrafficAxis = new ClampedAxisComponentModel.Builder(myDetailedNetworkUsage.getTrafficRange(), TRAFFIC_AXIS_FORMATTER).build();
    myConnectionsAxis =
      new ClampedAxisComponentModel.Builder(myDetailedNetworkUsage.getConnectionsRange(), CONNECTIONS_AXIS_FORMATTER).build();

    myLegends = new NetworkStageLegends(myDetailedNetworkUsage, getTimeline().getDataRange(), false);
    myTooltipLegends = new NetworkStageLegends(myDetailedNetworkUsage, getTimeline().getTooltipRange(), true);

    myEventMonitor = new EventMonitor(profilers);

    myStackTraceModel = new StackTraceModel(profilers.getIdeServices().getCodeNavigator());

    myRangeSelectionModel = new RangeSelectionModel(getTimeline().getSelectionRange(), getTimeline().getViewRange());
    profilers.addDependency(myAspectObserver)
      .onChange(ProfilerAspect.AGENT, () -> myRangeSelectionModel.setSelectionEnabled(profilers.isAgentAttached()));
    myRangeSelectionModel.setSelectionEnabled(profilers.isAgentAttached());
    myRangeSelectionModel.addListener(new RangeSelectionListener() {
      @Override
      public void selectionCreated() {
        setProfilerMode(ProfilerMode.EXPANDED);
        profilers.getIdeServices().getFeatureTracker().trackSelectRange();
        profilers.getIdeServices().getTemporaryProfilerPreferences().setBoolean(HAS_USED_NETWORK_SELECTION, true);
        myInstructionsEaseOutModel.setCurrentPercentage(1);
      }

      @Override
      public void selectionCleared() {
        setProfilerMode(ProfilerMode.NORMAL);
      }
    });

    myConnectionsModel =
      profilers.getIdeServices().getFeatureConfig().isUnifiedPipelineEnabled() ?
      new RpcNetworkConnectionsModel(profilers.getClient().getTransportClient(), profilers.getSession()) :
      new LegacyRpcNetworkConnectionsModel(profilers.getClient().getTransportClient(),
                                           profilers.getClient().getNetworkClient(),
                                           profilers.getSession());

    myHttpDataFetcher = new HttpDataFetcher(myConnectionsModel, getTimeline().getSelectionRange());
    myInstructionsEaseOutModel = new EaseOutModel(profilers.getUpdater(), PROFILING_INSTRUCTIONS_EASE_OUT_NS);
  }

  public boolean hasUserUsedNetworkSelection() {
    return getStudioProfilers().getIdeServices().getTemporaryProfilerPreferences().getBoolean(HAS_USED_NETWORK_SELECTION, false);
  }

  @NotNull
  public NetworkConnectionsModel getConnectionsModel() {
    return myConnectionsModel;
  }

  @NotNull
  public RangeSelectionModel getRangeSelectionModel() {
    return myRangeSelectionModel;
  }

  @NotNull
  public StackTraceModel getStackTraceModel() {
    return myStackTraceModel;
  }

  @NotNull
  public HttpDataFetcher getHttpDataFetcher() {
    return myHttpDataFetcher;
  }

  /**
   * Sets the active connection, or clears the previously selected active connection if given data is null.
   */
  public boolean setSelectedConnection(@Nullable HttpData data) {
    if (Objects.equals(mySelectedConnection, data)) {
      return false;
    }

    mySelectedConnection = data;
    getAspect().changed(NetworkProfilerAspect.SELECTED_CONNECTION);
    getStudioProfilers().getIdeServices().getFeatureTracker().trackSelectNetworkRequest();

    return true;
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

  @Override
  public void enter() {
    myEventMonitor.enter();

    getStudioProfilers().getUpdater().register(myDetailedNetworkUsage);
    getStudioProfilers().getUpdater().register(myTrafficAxis);
    getStudioProfilers().getUpdater().register(myConnectionsAxis);

    getStudioProfilers().getIdeServices().getCodeNavigator().addListener(this);
    getStudioProfilers().getIdeServices().getFeatureTracker().trackEnterStage(getStageType());
  }

  @Override
  public void exit() {
    myEventMonitor.exit();

    getStudioProfilers().getUpdater().unregister(myDetailedNetworkUsage);
    getStudioProfilers().getUpdater().unregister(myTrafficAxis);
    getStudioProfilers().getUpdater().unregister(myConnectionsAxis);

    getStudioProfilers().getIdeServices().getCodeNavigator().removeListener(this);

    myRangeSelectionModel.clearListeners();
  }

  @Override
  public AndroidProfilerEvent.Stage getStageType() {
    return AndroidProfilerEvent.Stage.NETWORK_STAGE;
  }

  @NotNull
  public String getName() {
    return "NETWORK";
  }

  @NotNull
  public DetailedNetworkUsage getDetailedNetworkUsage() {
    return myDetailedNetworkUsage;
  }

  @NotNull
  public AxisComponentModel getTrafficAxis() {
    return myTrafficAxis;
  }

  @NotNull
  public AxisComponentModel getConnectionsAxis() {
    return myConnectionsAxis;
  }

  @NotNull
  public NetworkStageLegends getLegends() {
    return myLegends;
  }

  @NotNull
  public NetworkStageLegends getTooltipLegends() {
    return myTooltipLegends;
  }

  @NotNull
  public EaseOutModel getInstructionsEaseOutModel() {
    return myInstructionsEaseOutModel;
  }

  @NotNull
  public EventMonitor getEventMonitor() {
    return myEventMonitor;
  }

  @Override
  public void onNavigated(@NotNull CodeLocation location) {
    setProfilerMode(ProfilerMode.NORMAL);
  }

  public static class NetworkStageLegends extends LegendComponentModel {

    private final SeriesLegend myRxLegend;
    private final SeriesLegend myTxLegend;
    private final SeriesLegend myConnectionLegend;

    public NetworkStageLegends(DetailedNetworkUsage usage, Range range, boolean tooltip) {
      super(range);
      myRxLegend = new SeriesLegend(usage.getRxSeries(), TRAFFIC_AXIS_FORMATTER, range, BYTES_RECEIVED.getLabel(tooltip),
                                    Interpolatable.SegmentInterpolator);

      myTxLegend = new SeriesLegend(usage.getTxSeries(), TRAFFIC_AXIS_FORMATTER, range, BYTES_SENT.getLabel(tooltip),
                                    Interpolatable.SegmentInterpolator);
      myConnectionLegend = new SeriesLegend(usage.getConnectionSeries(), CONNECTIONS_AXIS_FORMATTER, range,
                                            Interpolatable.SteppedLineInterpolator);

      add(myRxLegend);
      add(myTxLegend);
      add(myConnectionLegend);
    }

    public SeriesLegend getRxLegend() {
      return myRxLegend;
    }

    public SeriesLegend getTxLegend() {
      return myTxLegend;
    }

    public SeriesLegend getConnectionLegend() {
      return myConnectionLegend;
    }
  }
}
