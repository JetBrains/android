/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.analytics.energy;

import com.android.tools.profilers.energy.EnergyDuration;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class with metadata related to a range of energy data.
 */
public final class EnergyRangeMetadata {

  private final List<EnergyEventCount> myEventCounts = new ArrayList<>();

  public EnergyRangeMetadata(@NotNull List<EnergyDuration> energyDurations) {
    Map<EnergyDuration.Kind, Integer> kindCounts = new HashMap<>();
    for (EnergyDuration energyDuration : energyDurations) {
      kindCounts.compute(energyDuration.getKind(), (kind, count) -> (count == null) ? 1 : (count + 1));
    }

    for (Map.Entry<EnergyDuration.Kind, Integer> entry : kindCounts.entrySet()) {
      myEventCounts.add(new EnergyEventCount(entry.getKey(), entry.getValue()));
    }
  }

  @NotNull
  public List<EnergyEventCount> getEventCounts() {
    return myEventCounts;
  }

  public static final class EnergyEventCount {
    private final EnergyDuration.Kind myKind;
    private final int myCount;

    public EnergyEventCount(@NotNull EnergyDuration.Kind kind, int count) {
      myKind = kind;
      myCount = count;
    }

    @NotNull
    public EnergyDuration.Kind getKind() {
      return myKind;
    }

    public int getCount() {
      return myCount;
    }
  }
}
