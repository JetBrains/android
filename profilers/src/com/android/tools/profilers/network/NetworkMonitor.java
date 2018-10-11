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

import com.android.tools.adtui.model.axis.AxisComponentModel;
import com.android.tools.adtui.model.Interpolatable;
import com.android.tools.adtui.model.Range;
import com.android.tools.adtui.model.axis.ClampedAxisComponentModel;
import com.android.tools.adtui.model.formatter.BaseAxisFormatter;
import com.android.tools.adtui.model.formatter.NetworkTrafficFormatter;
import com.android.tools.adtui.model.legend.LegendComponentModel;
import com.android.tools.adtui.model.legend.SeriesLegend;
import com.android.tools.profilers.ProfilerMonitor;
import com.android.tools.profilers.ProfilerTooltip;
import com.android.tools.profilers.StudioProfilers;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.profilers.network.NetworkTrafficDataSeries.Type.BYTES_RECEIVED;
import static com.android.tools.profilers.network.NetworkTrafficDataSeries.Type.BYTES_SENT;

public class NetworkMonitor extends ProfilerMonitor {

  private static final BaseAxisFormatter BANDWIDTH_AXIS_FORMATTER_L1 = new NetworkTrafficFormatter(1, 2, 5);
  private final NetworkUsage myNetworkUsage;
  private final NetworkLegends myLegends;
  private final ClampedAxisComponentModel myTrafficAxis;
  private final NetworkLegends myTooltipLegends;

  public NetworkMonitor(@NotNull StudioProfilers profilers) {
    super(profilers);

    myNetworkUsage = new NetworkUsage(profilers);

    myTrafficAxis = new ClampedAxisComponentModel.Builder(myNetworkUsage.getTrafficRange(), BANDWIDTH_AXIS_FORMATTER_L1).build();
    myLegends = new NetworkLegends(myNetworkUsage, getTimeline().getDataRange(), false);
    myTooltipLegends = new NetworkLegends(myNetworkUsage, getTimeline().getTooltipRange(), true);
  }

  @Override
  @NotNull
  public String getName() {
    return "NETWORK";
  }

  @Override
  public ProfilerTooltip buildTooltip() {
    return new NetworkMonitorTooltip(this);
  }

  @Override
  public void exit() {
    myProfilers.getUpdater().unregister(myNetworkUsage);
    myProfilers.getUpdater().unregister(myTrafficAxis);
  }

  @Override
  public void enter() {
    myProfilers.getUpdater().register(myNetworkUsage);
    myProfilers.getUpdater().register(myTrafficAxis);
  }

  @Override
  public void expand() {
    myProfilers.setStage(new NetworkProfilerStage(myProfilers));
  }

  public AxisComponentModel getTrafficAxis() {
    return myTrafficAxis;
  }

  public NetworkUsage getNetworkUsage() {
    return myNetworkUsage;
  }

  public NetworkLegends getLegends() {
    return myLegends;
  }

  public NetworkLegends getTooltipLegends() {
    return myTooltipLegends;
  }

  public static class NetworkLegends extends LegendComponentModel {

    @NotNull private final SeriesLegend myRxLegend;
    @NotNull private final SeriesLegend myTxLegend;

    public NetworkLegends(@NotNull NetworkUsage usage, @NotNull Range range, boolean hightlight) {
      super(range);
      myTxLegend = new SeriesLegend(usage.getTxSeries(), BANDWIDTH_AXIS_FORMATTER_L1, range, BYTES_SENT.getLabel(hightlight),
                                    Interpolatable.SegmentInterpolator);
      myRxLegend = new SeriesLegend(usage.getRxSeries(), BANDWIDTH_AXIS_FORMATTER_L1, range, BYTES_RECEIVED.getLabel(hightlight),
                                    Interpolatable.SegmentInterpolator);
      add(myTxLegend);
      add(myRxLegend);
    }

    @NotNull
    public SeriesLegend getRxLegend() {
      return myRxLegend;
    }

    @NotNull
    public SeriesLegend getTxLegend() {
      return myTxLegend;
    }
  }
}
