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

  public ArrayList<String> getLegends() {
    return myLegends;
  }

  public enum Aspect {
    LEGENDS,
    VALUES,
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
        changed(Aspect.VALUES);
      }
    //    for (int i = 0; i < mLegendRenderData.size(); ++i) {
    //      LegendRenderData data = mLegendRenderData.get(i);
    //      JLabel label = mLabelsToDraw.get(i);
    //      if (data.hasData()) {
    //        label.setText();
    //      }
    //      else {
    //        label.setText(data.getLabel());
    //      }
    //      Dimension preferredSize = label.getPreferredSize();
    //      if (preferredSize.getWidth() < LABEL_MIN_WIDTH_PX) {
    //        preferredSize.width = LABEL_MIN_WIDTH_PX;
    //        label.setPreferredSize(preferredSize);
    //      }
    //      label.setBounds(0, 0, preferredSize.width, preferredSize.height);
    //    }
    //
    //    // As we adjust the size of the label we need to adjust our own size
    //    // to tell our parent to give us enough room to draw.
    //    Dimension newSize = getLegendPreferredSize();
    //    if (newSize != getPreferredSize()) {
    //      setPreferredSize(newSize);
    //      // Set the minimum height of the component to avoid hiding all the labels
    //      // in case they are longer than the component's total width
    //      setMinimumSize(new Dimension(getMinimumSize().width, newSize.height));
    //      revalidate();
    //    }
    //  }
    //}
    //
    //@Override
    //  return super.getPreferredSize();
    //}

  }

  /**
   * Clears existing LegendRenderData and adds new ones.
   */
  public void setLegendData(List<LegendData> data) {
    myLegendData = new ArrayList<>(data);
    changed(Aspect.LEGENDS);
  }
}
