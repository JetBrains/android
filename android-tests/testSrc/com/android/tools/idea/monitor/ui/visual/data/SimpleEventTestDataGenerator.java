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

import com.android.tools.adtui.model.EventAction;
import com.android.tools.adtui.model.SeriesData;
import com.android.tools.profilers.event.EventActionType;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class SimpleEventTestDataGenerator
  extends TestDataGenerator<EventAction<EventAction.Action, EventActionType>> {

  private ArrayList<EventAction<EventAction.Action, EventActionType>> mData = new ArrayList<>();

  @Override
  public SeriesData<EventAction<EventAction.Action, EventActionType>> get(int index) {
    return new SeriesData<>(mTime.get(index), mData.get(index));
  }

  @Override
  public int getSleepTime() {
    //Sleep for up to 1 second.
    return (int)(1000 * Math.random());
  }

  @Override
  public void generateData() {
    boolean downAction = true;
    long currentTimeUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime());
    long endTimeUs = 0;
    if (Math.random() > 0.5) {
      mTime.add(TimeUnit.NANOSECONDS.toMicros(System.nanoTime()));
      if (mData.size() > 0) {
        EventAction<EventAction.Action, EventActionType> lastAction = mData.get(mData.size() - 1);
        // If our last action was a down action, our next action should be an up action.
        if (lastAction.getValue() == EventAction.Action.DOWN) {
          downAction = false;
          endTimeUs = TimeUnit.NANOSECONDS.toMicros(System.nanoTime());
          currentTimeUs = lastAction.getStartUs();
        }
      }
      mData.add(new EventAction<>(currentTimeUs, endTimeUs,
                                  downAction ? EventAction.Action.DOWN : EventAction.Action.UP,
                                  EventActionType.HOLD));

    }
  }
}
