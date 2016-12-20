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
package com.android.tools.adtui.model;

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
  private List<LegendData> myLegendData;

  private ArrayList<String> myLegends;

  public LegendComponentModel(int frequencyMillis) {
    mFrequencyMillis = frequencyMillis;
    mLastUpdate = 0;
    myLegends = new ArrayList<>();
    myLegendData = new ArrayList<>();
  }

  public ArrayList<String> getValues() {
    return myLegends;
  }

  public List<LegendData> getLegendData() {
    return myLegendData;
  }

  @Override
  public void update(float elapsed) {
      long now = System.currentTimeMillis();
      if (now - mLastUpdate > mFrequencyMillis) {
        mLastUpdate = now;
        myLegends.clear();
        for (LegendData data : myLegendData) {
          myLegends.add(data.get());
        }
        changed(Aspect.LEGEND);
      }
  }

  public void add(LegendData legend) {
    myLegendData.add(legend);
    changed(Aspect.LEGEND);
  }
}
