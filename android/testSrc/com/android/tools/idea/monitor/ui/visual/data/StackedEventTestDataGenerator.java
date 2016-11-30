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

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class StackedEventTestDataGenerator extends TestDataGenerator<EventAction<EventAction.ActivityAction, String>> {

  private ArrayList<EventAction<EventAction.ActivityAction, String>> mData = new ArrayList<>();
  private String mName;


  public StackedEventTestDataGenerator(String name) {
    mName = name;
  }

  @Override
  public SeriesData<EventAction<EventAction.ActivityAction, String>> get(int index) {
    return new SeriesData<>(mTime.get(index), mData.get(index));
  }
  @Override
  public int getSleepTime() {
    //Sleep for up to 1 second.
    return (int)(1000 * Math.random());
  }

  @Override
  public void generateData() {
    boolean createAction = true;
    long endTimeUs = 0;
    long currentTimeUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime());
    long startTimeUs = currentTimeUs;
    //Generate a new event 50% of the time we generate data. Doing this gives us event streams show like
    //real data, instead of just starting and stopping.
    if(Math.random() > .5) {
      //Only when we generate a new event, do we also at the same time add a new entry
      //into our time array to keep our event data
      //consistent with our time lookup.
      mTime.add(TimeUnit.NANOSECONDS.toMicros(System.nanoTime()));

      if (mData.size() > 0) {
        EventAction<EventAction.ActivityAction, String> lastAction = mData.get(mData.size() - 1);
        // If our last action was an activity started action, our next should be an activity completed.
        if (lastAction.getValue() == EventAction.ActivityAction.ACTIVITY_STARTED) {
          createAction = false;
          endTimeUs = currentTimeUs;
          startTimeUs = lastAction.getStartUs();
        }
      }
      mData.add(new EventAction<>(startTimeUs, endTimeUs,
                                  createAction
                                  ? EventAction.ActivityAction.ACTIVITY_STARTED
                                  : EventAction.ActivityAction.ACTIVITY_COMPLETED,
                                  mName));
    }
  }
}
