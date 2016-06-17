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

import com.android.tools.adtui.SimpleEventComponent;
import com.android.tools.adtui.model.EventAction;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.idea.monitor.ui.events.view.EventSegment;
import gnu.trove.TLongArrayList;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SimpleEventTestDataGenerator
  extends TestDataGenerator<EventAction<SimpleEventComponent.Action, EventSegment.EventActionType>> {

  private ArrayList<EventAction<SimpleEventComponent.Action, EventSegment.EventActionType>> mData = new ArrayList<>();

  @Override
  public SeriesData<EventAction<SimpleEventComponent.Action, EventSegment.EventActionType>> get(int index) {
    SeriesData<EventAction<SimpleEventComponent.Action, EventSegment.EventActionType>> data = new SeriesData<>();
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
    boolean downAction = true;
    long currentTime = System.currentTimeMillis() - mStartTime;
    long endTime = 0;
    if (mStartTime != 0 && Math.random() > 0.5) {
      mTime.add(System.currentTimeMillis());
      if (mData.size() > 0) {
        EventAction<SimpleEventComponent.Action, EventSegment.EventActionType> lastAction = mData.get(mData.size() - 1);
        // If our last action was a down action, our next action should be an up action.
        if (lastAction.getValue() == SimpleEventComponent.Action.DOWN) {
          downAction = false;
          endTime = System.currentTimeMillis() - mStartTime;
          currentTime = lastAction.getStart();
        }
      }
      mData.add(new EventAction<>(currentTime, endTime,
                                  downAction ? SimpleEventComponent.Action.DOWN : SimpleEventComponent.Action.UP,
                                  EventSegment.EventActionType.HOLD));

    }
  }
}
