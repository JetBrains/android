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

import com.android.tools.adtui.model.*;
import com.android.tools.adtui.model.formatter.EnergyAxisFormatter;
import com.android.tools.adtui.model.legend.FixedLegend;
import com.android.tools.adtui.model.legend.LegendComponentModel;
import com.android.tools.adtui.model.legend.SeriesLegend;
import com.android.tools.profiler.proto.EnergyProfiler.EnergyEvent;
import com.android.tools.profiler.proto.Profiler;
import com.android.tools.profiler.protobuf3jarjar.ByteString;
import com.android.tools.profilers.*;
import com.android.tools.profilers.event.EventMonitor;
import com.android.tools.profilers.stacktrace.CodeLocation;
import com.android.tools.profilers.stacktrace.CodeNavigator;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class EnergyProfilerStage extends Stage implements CodeNavigator.Listener {
  private static final String HAS_USED_ENERGY_SELECTION = "energy.used.selection";

  @NotNull private final DetailedEnergyUsage myDetailedUsage;
  @NotNull private final AxisComponentModel myAxis;
  @NotNull private final EventMonitor myEventMonitor;
  @NotNull private final EnergyLegends myLegends;
  @NotNull private final EnergyLegends myTooltipLegends;
  @NotNull private final EnergyEventLegends myEventLegends;
  @NotNull private final SelectionModel mySelectionModel;
  @NotNull private final EnergyEventsFetcher myFetcher;
  @NotNull private final StateChartModel<EnergyEvent> myEventModel;
  @NotNull private final EaseOutModel myInstructionsEaseOutModel;

  // Intentionally local field, to prevent GC from cleaning it and removing weak listeners
  @SuppressWarnings("FieldCanBeLocal") private AspectObserver myAspectObserver = new AspectObserver();
  private AspectModel<EnergyProfilerAspect> myAspect = new AspectModel<>();

  @Nullable private EnergyDuration mySelectedDuration;

  public EnergyProfilerStage(@NotNull StudioProfilers profilers) {
    super(profilers);
    myDetailedUsage = new DetailedEnergyUsage(profilers);
    myAxis = new AxisComponentModel(myDetailedUsage.getUsageRange(), EnergyAxisFormatter.DEFAULT);
    myEventMonitor = new EventMonitor(profilers);
    myLegends = new EnergyLegends(myDetailedUsage, profilers.getTimeline().getDataRange());
    myTooltipLegends = new EnergyLegends(myDetailedUsage, profilers.getTimeline().getTooltipRange());
    myEventLegends = new EnergyEventLegends();
    mySelectionModel = new SelectionModel(profilers.getTimeline().getSelectionRange());
    mySelectionModel.setSelectionEnabled(profilers.isAgentAttached());
    profilers.addDependency(myAspectObserver)
      .onChange(ProfilerAspect.AGENT, () -> mySelectionModel.setSelectionEnabled(profilers.isAgentAttached()));
    mySelectionModel.addListener(new SelectionListener() {
      @Override
      public void selectionCreated() {
        setProfilerMode(ProfilerMode.EXPANDED);
        profilers.getIdeServices().getFeatureTracker().trackSelectRange();
        profilers.getIdeServices().getTemporaryProfilerPreferences().setBoolean(HAS_USED_ENERGY_SELECTION, true);
        myInstructionsEaseOutModel.setCurrentPercentage(1);
      }

      @Override
      public void selectionCleared() {
        setProfilerMode(ProfilerMode.NORMAL);
      }
    });
    myFetcher = new EnergyEventsFetcher(profilers.getClient().getEnergyClient(), profilers.getSession(), profilers.getTimeline().getSelectionRange());

    EnergyEventsDataSeries sourceSeries = new EnergyEventsDataSeries(profilers.getClient(), profilers.getSession());

    myEventModel = new StateChartModel<>();
    Range range = profilers.getTimeline().getViewRange();
    // StateChart renders series in reverse order
    myEventModel.addSeries(
      new RangedSeries<>(range, new MergedEnergyEventsDataSeries(sourceSeries, EnergyDuration.Kind.ALARM, EnergyDuration.Kind.JOB)));
    myEventModel.addSeries(new RangedSeries<>(range, new MergedEnergyEventsDataSeries(sourceSeries, EnergyDuration.Kind.WAKE_LOCK)));
    myEventModel.addSeries(new RangedSeries<>(range, new MergedEnergyEventsDataSeries(sourceSeries, EnergyDuration.Kind.LOCATION)));

    myInstructionsEaseOutModel = new EaseOutModel(profilers.getUpdater(), PROFILING_INSTRUCTIONS_EASE_OUT_NS);
  }

  @Override
  public void enter() {
    myEventMonitor.enter();

    getStudioProfilers().getUpdater().register(myAxis);
    getStudioProfilers().getUpdater().register(myDetailedUsage);
    getStudioProfilers().getUpdater().register(myEventModel);
    getStudioProfilers().getUpdater().register(myLegends);
    getStudioProfilers().getUpdater().register(myTooltipLegends);

    getStudioProfilers().getIdeServices().getCodeNavigator().addListener(this);
  }

  @Override
  public void exit() {
    myEventMonitor.exit();

    getStudioProfilers().getUpdater().unregister(myAxis);
    getStudioProfilers().getUpdater().unregister(myDetailedUsage);
    getStudioProfilers().getUpdater().unregister(myEventModel);
    getStudioProfilers().getUpdater().unregister(myLegends);
    getStudioProfilers().getUpdater().unregister(myTooltipLegends);

    getStudioProfilers().getIdeServices().getCodeNavigator().removeListener(this);
  }

  @NotNull
  public EnergyEventsFetcher getEnergyEventsFetcher() {
    return myFetcher;
  }

  @NotNull
  public SelectionModel getSelectionModel() {
    return mySelectionModel;
  }

  @NotNull
  public DetailedEnergyUsage getDetailedUsage() {
    return myDetailedUsage;
  }

  @NotNull StateChartModel<EnergyEvent> getEventModel() {
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
  public EnergyLegends getLegends() {
    return myLegends;
  }

  @NotNull
  public EnergyLegends getTooltipLegends() {
    return myTooltipLegends;
  }

  @NotNull
  public EnergyEventLegends getEventLegends() {
    return myEventLegends;
  }

  @NotNull
  public String getName() {
    return "ENERGY";
  }

  @NotNull
  public AspectModel<EnergyProfilerAspect> getAspect() {
    return myAspect;
  }

  public void setSelectedDuration(@Nullable EnergyDuration duration) {
    if (Objects.equals(mySelectedDuration, duration)) {
      return;
    }
    mySelectedDuration = duration;
    myAspect.changed(EnergyProfilerAspect.SELECTED_EVENT_DURATION);
  }

  @Nullable
  public EnergyDuration getSelectedDuration() {
    return mySelectedDuration;
  }

  @NotNull
  public EaseOutModel getInstructionsEaseOutModel() {
    return myInstructionsEaseOutModel;
  }

  public boolean hasUserUsedEnergySelection() {
    return getStudioProfilers().getIdeServices().getTemporaryProfilerPreferences().getBoolean(HAS_USED_ENERGY_SELECTION, false);
  }

  @NotNull
  public ByteString requestBytes(@NotNull String id) {
    if (StringUtil.isEmpty(id)) {
      return ByteString.EMPTY;
    }

    Profiler.BytesRequest request = Profiler.BytesRequest.newBuilder()
      .setId(id)
      .setSession(getStudioProfilers().getSession())
      .build();

    Profiler.BytesResponse response = getStudioProfilers().getClient().getProfilerClient().getBytes(request);
    return response.getContents();
  }

  @Override
  public void onNavigated(@NotNull CodeLocation location) {
    setProfilerMode(ProfilerMode.NORMAL);
  }

  public static class EnergyLegends extends LegendComponentModel {

    @NotNull private final SeriesLegend myCpuLegend;
    @NotNull private final SeriesLegend myNetworkLegend;

    EnergyLegends(DetailedEnergyUsage detailedUsage, Range range) {
      super(ProfilerMonitor.LEGEND_UPDATE_FREQUENCY_MS);
      myCpuLegend = new SeriesLegend(detailedUsage.getCpuUsageSeries(), EnergyAxisFormatter.DEFAULT, range, "CPU",
                                     Interpolatable.SegmentInterpolator);
      myNetworkLegend = new SeriesLegend(detailedUsage.getNetworkUsageSeries(), EnergyAxisFormatter.DEFAULT, range, "NETWORK",
                                         Interpolatable.SegmentInterpolator);

      add(myCpuLegend);
      add(myNetworkLegend);
    }

    @NotNull
    public SeriesLegend getCpuLegend() {
      return myCpuLegend;
    }

    @NotNull
    public SeriesLegend getNetworkLegend() {
      return myNetworkLegend;
    }
  }

  public static class EnergyEventLegends extends LegendComponentModel {
    @NotNull private final FixedLegend locationLegend;
    @NotNull private final FixedLegend wakeLockLegend;
    @NotNull private final FixedLegend alarmAndJobLegend;

    EnergyEventLegends() {
      super(ProfilerMonitor.LEGEND_UPDATE_FREQUENCY_MS);
      locationLegend = new FixedLegend("Location Event");
      wakeLockLegend = new FixedLegend("Wake Locks");
      alarmAndJobLegend = new FixedLegend("Alarms & Jobs");

      add(locationLegend);
      add(wakeLockLegend);
      add(alarmAndJobLegend);
    }

    @NotNull
    public FixedLegend getWakeLockLegend() {
      return wakeLockLegend;
    }

    @NotNull
    public FixedLegend getLocationLegend() {
      return locationLegend;
    }

    @NotNull
    public FixedLegend getAlarmAndJobLegend() {
      return alarmAndJobLegend;
    }
  }
}
