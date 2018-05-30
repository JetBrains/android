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
package com.android.tools.adtui.model.legend;

import com.android.tools.adtui.model.AspectModel;
import com.android.tools.adtui.model.updater.Updatable;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class LegendComponentModel extends AspectModel<LegendComponentModel.Aspect> implements Updatable {

  public enum Aspect {
    LEGEND,
  }

  @NotNull
  private final List<Legend> myLegends;
  private final Map<Legend, String> myLegendStringMap;
  private final long mUpdateFrequencyNs;
  private long mElapsedNs;

  public LegendComponentModel(int updateFrequencyMs) {
    mUpdateFrequencyNs = TimeUnit.MILLISECONDS.toNanos(updateFrequencyMs);
    myLegends = new ArrayList<>();
    myLegendStringMap = new HashMap<>();
    // Set elapsedNs to full value to ensure update loop always triggers the first time
    mElapsedNs = mUpdateFrequencyNs;
  }

  @NotNull
  public List<Legend> getLegends() {
    return myLegends;
  }

  @Override
  public void update(long elapsedNs) {
    mElapsedNs += elapsedNs;
    if (mElapsedNs >= mUpdateFrequencyNs) {
      mElapsedNs = 0;

      boolean changed = false;
      for (Legend legend : myLegends) {
        if (myLegends.contains(legend)) {
          String newValue = legend.getValue();
          String oldValue = myLegendStringMap.put(legend, newValue);
          if (!Objects.equals(oldValue, newValue)) {
            changed = true;
          }
        }
        else {
          // We should still execute this even if the values are null (and not roll this into the previous branch).
          // The reason is because the legend might've gotten added, and there were no values associated with it,
          // we still want to fire an aspect changed so that the name label is updated.
          changed = true;
          myLegendStringMap.put(legend, legend.getValue());
        }
      }

      if (changed) {
        changed(Aspect.LEGEND);
      }
    }
  }

  public void add(@NotNull Legend legend) {
    myLegends.add(legend);
    changed(Aspect.LEGEND);
  }

  public void remove(@NotNull Legend legend) {
    myLegends.remove(legend);
    myLegendStringMap.remove(legend);
    changed(Aspect.LEGEND);
  }
}
