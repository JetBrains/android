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
package com.android.tools.idea.monitor.ui.visual.data;

import com.android.tools.adtui.StackedEventComponent;
import com.android.tools.adtui.model.EventAction;
import com.android.tools.adtui.model.SeriesData;
import gnu.trove.TLongArrayList;

import java.util.ArrayList;
import java.util.Random;

public class StackedEventTestDataGenerator extends TestDataGenerator<EventAction<StackedEventComponent.Action, String>> {

  private ArrayList<EventAction<StackedEventComponent.Action, String>> mData = new ArrayList<>();
  private String mName;


  public StackedEventTestDataGenerator(String name) {
    mName = name;
  }

  @Override
  public SeriesData<EventAction<StackedEventComponent.Action, String>> get(int index) {
    SeriesData<EventAction<StackedEventComponent.Action, String>> data = new SeriesData<>();
    data.x = mTime.get(index) - mStartTime;
    data.value = mData.get(index);
    return data;
  }
  @Override
  public int getSleepTime() {
    //Sleep for up to 1 second.
    return (int)(1000 * Math.random());
  }

  @Override
  public void generateData() {
    boolean createAction = true;
    long endTime = 0;
    long currentTime = System.currentTimeMillis() - mStartTime;
    long startTime = currentTime;
    //Generate a new event 50% of the time we generate data. Doing this gives us event streams show like
    //real data, instead of just starting and stopping.
    if(mStartTime != 0 && Math.random() > .5) {
      //Only when we generate a new event, do we also at the same time add a new entry
      //into our time array to keep our event data
      //consistent with our time lookup.
      mTime.add(System.currentTimeMillis());

      if (mData.size() > 0) {
        EventAction<StackedEventComponent.Action, String> lastAction = mData.get(mData.size() - 1);
        // If our last action was an activity started action, our next should be an activity completed.
        if (lastAction.getValue() == StackedEventComponent.Action.ACTIVITY_STARTED) {
          createAction = false;
          endTime = currentTime;
          startTime = lastAction.getStart();
        }
      }
      mData.add(new EventAction<>(startTime, endTime,
                                  createAction
                                  ? StackedEventComponent.Action.ACTIVITY_STARTED
                                  : StackedEventComponent.Action.ACTIVITY_COMPLETED,
                                  mName));
    }
  }
}
