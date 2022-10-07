// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.profilers.energy;

import com.android.tools.adtui.model.AspectModel;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.adtui.model.EaseOutModel;
import com.android.tools.adtui.model.Interpolatable;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.RangeSelectionListener;
import com.android.tools.adtui.model.RangeSelectionModel;
import com.android.tools.adtui.model.RangedSeries;
import com.android.tools.adtui.model.StateChartModel;
import com.android.tools.adtui.model.axis.AxisComponentModel;
import com.android.tools.adtui.model.axis.ResizingAxisComponentModel;
import com.android.tools.adtui.model.formatter.EnergyAxisFormatter;
import com.android.tools.adtui.model.legend.Legend;
import com.android.tools.adtui.model.legend.LegendComponentModel;
import com.android.tools.adtui.model.legend.SeriesLegend;
import com.android.tools.adtui.model.updater.Updatable;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profiler.proto.TransportServiceGrpc;
import com.android.tools.profilers.ProfilerAspect;
import com.android.tools.profilers.StreamingStage;
import com.android.tools.profilers.StudioProfilers;
import com.android.tools.profilers.analytics.energy.EnergyEventMetadata;
import com.android.tools.profilers.analytics.energy.EnergyRangeMetadata;
import com.android.tools.profilers.event.EventMonitor;
import com.google.common.collect.ImmutableList;
import com.google.wireless.android.sdk.stats.AndroidProfilerEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EnergyProfilerStage extends StreamingStage {
  private static final String HAS_USED_ENERGY_SELECTION = "energy.used.selection";
  private static final String ENERGY_EVENT_ORIGIN_INDEX = "energy.event.origin";

  @NotNull private final DetailedEnergyUsage myDetailedUsage;
  @NotNull private final ResizingAxisComponentModel myAxis;
  @NotNull private final EventMonitor myEventMonitor;
  @NotNull private final EnergyUsageLegends myLegends;
  @NotNull private final EnergyUsageLegends myUsageTooltipLegends;
  @NotNull private final RangeSelectionModel myRangeSelectionModel;
  @NotNull private final StateChartModel<Common.Event> myEventModel;
  @NotNull private final EaseOutModel myInstructionsEaseOutModel;
  @NotNull private final Updatable myUpdatable;

  // Intentionally local field, to prevent GC from cleaning it and removing weak listeners
  @SuppressWarnings("FieldCanBeLocal") private AspectObserver myAspectObserver = new AspectObserver();
  private AspectModel<EnergyProfilerAspect> myAspect = new AspectModel<>();

  @Nullable private EnergyDuration mySelectedDuration;

  public EnergyProfilerStage(@NotNull StudioProfilers profilers) {
    super(profilers);
    myDetailedUsage = new DetailedEnergyUsage(profilers);
    myAxis = new ResizingAxisComponentModel.Builder(myDetailedUsage.getUsageRange(), EnergyAxisFormatter.DEFAULT)
      .setMarkerRange(EnergyMonitor.AXIS_MARKER_RANGE).build();
    myEventMonitor = new EventMonitor(profilers);
    myLegends = new EnergyUsageLegends(myDetailedUsage, getTimeline().getDataRange());
    myUsageTooltipLegends = new EnergyUsageLegends(myDetailedUsage, getTimeline().getTooltipRange());

    myRangeSelectionModel = new RangeSelectionModel(getTimeline().getSelectionRange(), getTimeline().getViewRange());
    myRangeSelectionModel.setSelectionEnabled(profilers.isAgentAttached());
    profilers.addDependency(myAspectObserver)
      .onChange(ProfilerAspect.AGENT, () -> myRangeSelectionModel.setSelectionEnabled(profilers.isAgentAttached()));
    myRangeSelectionModel.addListener(new RangeSelectionListener() {
      @Override
      public void selectionCreated() {
        trackRangeSelection(profilers, myRangeSelectionModel.getSelectionRange());
        profilers.getIdeServices().getTemporaryProfilerPreferences().setBoolean(HAS_USED_ENERGY_SELECTION, true);
        myInstructionsEaseOutModel.setCurrentPercentage(1);
      }
    });

    myEventModel = createEventChartModel(profilers);

    myInstructionsEaseOutModel = new EaseOutModel(profilers.getUpdater(), PROFILING_INSTRUCTIONS_EASE_OUT_NS);

    myUpdatable = elapsedNs -> getTimeline().getTooltipRange().changed(Range.Aspect.RANGE);
  }

  @Override
  public void enter() {
    myEventMonitor.enter();

    getStudioProfilers().getUpdater().register(myDetailedUsage);
    getStudioProfilers().getUpdater().register(myUpdatable);

    getStudioProfilers().getIdeServices().getFeatureTracker().trackEnterStage(getStageType());
  }

  @Override
  public void exit() {
    myEventMonitor.exit();

    getStudioProfilers().getUpdater().unregister(myDetailedUsage);
    getStudioProfilers().getUpdater().unregister(myUpdatable);
  }

  @Override
  public AndroidProfilerEvent.Stage getStageType() {
    return AndroidProfilerEvent.Stage.ENERGY_STAGE;
  }

  @NotNull
  public RangeSelectionModel getRangeSelectionModel() {
    return myRangeSelectionModel;
  }

  @NotNull
  public DetailedEnergyUsage getDetailedUsage() {
    return myDetailedUsage;
  }

  @NotNull
  StateChartModel<Common.Event> getEventModel() {
    return myEventModel;
  }

  @NotNull
  public AxisComponentModel getAxis() {
    return myAxis;
  }

  @NotNull
  public EventMonitor getEventMonitor() {
    return myEventMonitor;
  }

  @NotNull
  public EnergyUsageLegends getLegends() {
    return myLegends;
  }

  @NotNull
  public EnergyUsageLegends getUsageTooltipLegends() {
    return myUsageTooltipLegends;
  }

  @NotNull
  public String getName() {
    return "ENERGY";
  }

  @NotNull
  public AspectModel<EnergyProfilerAspect> getAspect() {
    return myAspect;
  }

  @Nullable
  public EnergyDuration getSelectedDuration() {
    return mySelectedDuration;
  }

  /**
   * Sets the selected duration, if the given duration is the same as existing or not valid by filter then it is ignored.
   */
  public void setSelectedDuration(@Nullable EnergyDuration duration) {
    if (Objects.equals(mySelectedDuration, duration) || !canSelectDuration(duration)) {
      return;
    }
    mySelectedDuration = duration;
    myAspect.changed(EnergyProfilerAspect.SELECTED_EVENT_DURATION);

    if (mySelectedDuration != null) {
      getStudioProfilers().getIdeServices().getFeatureTracker()
        .trackSelectEnergyEvent(new EnergyEventMetadata(mySelectedDuration.getEventList()));
    }
  }

  @NotNull
  public EaseOutModel getInstructionsEaseOutModel() {
    return myInstructionsEaseOutModel;
  }

  public boolean hasUserUsedEnergySelection() {
    return getStudioProfilers().getIdeServices().getTemporaryProfilerPreferences().getBoolean(HAS_USED_ENERGY_SELECTION, false);
  }

  @NotNull
  public EnergyEventOrigin getEventOrigin() {
    int savedOriginOrdinal = getStudioProfilers().getIdeServices().getTemporaryProfilerPreferences()
      .getInt(ENERGY_EVENT_ORIGIN_INDEX, EnergyEventOrigin.ALL.ordinal());
    return EnergyEventOrigin.values()[savedOriginOrdinal];
  }

  public void setEventOrigin(@NotNull EnergyEventOrigin origin) {
    if (getEventOrigin() != origin) {
      getStudioProfilers().getIdeServices().getTemporaryProfilerPreferences().setInt(ENERGY_EVENT_ORIGIN_INDEX, origin.ordinal());
      // As the selected duration is in the stage, update it before the table view update because the table view need reflect the selection.
      if (!canSelectDuration(getSelectedDuration())) {
        setSelectedDuration(null);
      }
      myAspect.changed(EnergyProfilerAspect.SELECTED_ORIGIN_FILTER);
    }
  }

  private boolean canSelectDuration(@Nullable EnergyDuration duration) {
    return duration == null || !filterByOrigin(ImmutableList.of(duration)).isEmpty();
  }

  @NotNull
  public List<EnergyDuration> filterByOrigin(@NotNull List<EnergyDuration> list) {
    String appName = getStudioProfilers().getSelectedAppName();
    return list.stream().filter(duration -> getEventOrigin().isValid(appName, duration.getCalledBy()))
      .collect(Collectors.toList());
  }

  private static void trackRangeSelection(@NotNull StudioProfilers profilers, Range selectionRange) {
    profilers.getIdeServices().getFeatureTracker().trackSelectRange();
    long minNs = TimeUnit.MICROSECONDS.toNanos((long)selectionRange.getMin());
    long maxNs = TimeUnit.MICROSECONDS.toNanos((long)selectionRange.getMax());
    List<EnergyDuration> energyDurations = new ArrayList<>();

    Transport.GetEventGroupsRequest request = Transport.GetEventGroupsRequest.newBuilder()
      .setStreamId(profilers.getSession().getStreamId())
      .setPid(profilers.getSession().getPid())
      .setKind(Common.Event.Kind.ENERGY_EVENT)
      .setFromTimestamp(minNs)
      .setToTimestamp(maxNs)
      .build();
    Transport.GetEventGroupsResponse response = profilers.getClient().getTransportClient().getEventGroups(request);
    for (Transport.EventGroup group : response.getGroupsList()) {
      energyDurations.add(new EnergyDuration(group.getEventsList()));
    }
    if (!energyDurations.isEmpty()) {
      profilers.getIdeServices().getFeatureTracker().trackSelectEnergyRange(new EnergyRangeMetadata(energyDurations));
    }
  }

  private StateChartModel<Common.Event> createEventChartModel(StudioProfilers profilers) {
    StateChartModel<Common.Event> stateChartModel = new StateChartModel<>();
    Range range = getTimeline().getViewRange();
    TransportServiceGrpc.TransportServiceBlockingStub transportClient = profilers.getClient().getTransportClient();
    long streamId = profilers.getSession().getStreamId();
    int pid = profilers.getSession().getPid();

    // StateChart renders series in reverse order
    // TODO(b/122964201) Pass data range as 3rd param to RangedSeries to only show data from current session
    stateChartModel.addSeries(
      new RangedSeries<>(range, new MergedEnergyEventsDataSeries(transportClient, streamId, pid,
                                                                 kind -> kind == EnergyDuration.Kind.ALARM ||
                                                                         kind == EnergyDuration.Kind.JOB)));
    stateChartModel.addSeries(
      new RangedSeries<>(range, new MergedEnergyEventsDataSeries(transportClient, streamId, pid,
                                                                 kind -> kind == EnergyDuration.Kind.WAKE_LOCK)));
    stateChartModel.addSeries(
      new RangedSeries<>(range, new MergedEnergyEventsDataSeries(transportClient, streamId, pid,
                                                                 kind -> kind == EnergyDuration.Kind.LOCATION)));
    return stateChartModel;
  }

  public static class EnergyUsageLegends extends LegendComponentModel {

    @NotNull private final SeriesLegend myCpuLegend;
    @NotNull private final SeriesLegend myNetworkLegend;
    @NotNull private final SeriesLegend myLocationLegend;

    EnergyUsageLegends(DetailedEnergyUsage detailedUsage, Range range) {
      super(range);
      myCpuLegend = new SeriesLegend(detailedUsage.getCpuUsageSeries(), EnergyAxisFormatter.LEGEND_FORMATTER, range, "CPU",
                                     Interpolatable.SegmentInterpolator);
      myNetworkLegend = new SeriesLegend(detailedUsage.getNetworkUsageSeries(), EnergyAxisFormatter.LEGEND_FORMATTER, range, "Network",
                                         Interpolatable.SegmentInterpolator);
      myLocationLegend = new SeriesLegend(detailedUsage.getLocationUsageSeries(), EnergyAxisFormatter.LEGEND_FORMATTER, range, "Location",
                                          Interpolatable.SegmentInterpolator);

      add(myCpuLegend);
      add(myNetworkLegend);
      add(myLocationLegend);
    }

    @NotNull
    public Legend getCpuLegend() {
      return myCpuLegend;
    }

    @NotNull
    public Legend getNetworkLegend() {
      return myNetworkLegend;
    }

    @NotNull
    public SeriesLegend getLocationLegend() {
      return myLocationLegend;
    }
  }
}
