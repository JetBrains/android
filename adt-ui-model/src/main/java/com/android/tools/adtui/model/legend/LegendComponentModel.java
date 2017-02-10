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
import com.android.tools.adtui.model.Updatable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class LegendComponentModel extends AspectModel<LegendComponentModel.Aspect> implements Updatable {

  public enum Aspect {
    LEGEND,
  }

  private int mFrequencyMillis;

  private long mLastUpdate;

  @NotNull
  private List<Legend> myLegends;

  public LegendComponentModel(int updateFrequencyMs) {
    mFrequencyMillis = updateFrequencyMs;
    mLastUpdate = 0;
    myLegends = new ArrayList<>();
  }

  @NotNull
  public List<Legend> getLegends() {
    return myLegends;
  }

  @Override
  public void update(long elapsedNs) {
    long now = System.currentTimeMillis();
    if (now - mLastUpdate > mFrequencyMillis) {
      mLastUpdate = now;
      changed(Aspect.LEGEND);
    }
  }

  public void add(@NotNull Legend legend) {
    myLegends.add(legend);
    changed(Aspect.LEGEND);
  }

  public void remove(@NotNull Legend legend) {
    myLegends.remove(legend);
    changed(Aspect.LEGEND);
  }
}
