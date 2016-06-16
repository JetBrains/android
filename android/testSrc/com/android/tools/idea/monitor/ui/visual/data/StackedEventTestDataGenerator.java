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
import gnu.trove.TLongArrayList;

import java.util.ArrayList;
import java.util.Random;

public class StackedEventTestDataGenerator implements TestDataGenerator<EventAction<StackedEventComponent.Action, String>> {

  private Random mRandom = new Random();
  private ArrayList<EventAction<StackedEventComponent.Action, String>> mData = new ArrayList<>();
  private String mName;


  public StackedEventTestDataGenerator(String name) {
    mName = name;
  }

  @Override
  public EventAction<StackedEventComponent.Action, String> get(int index) {
    return mData.get(index);
  }

  @Override
  public void generateData(long currentTime) {
    boolean createAction = true;
    long endTime = 0;
    long startTime = currentTime;
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
