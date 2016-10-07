/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tools.adtui;

import java.util.ArrayList;
import java.util.List;

public class EventData {

  private int mEventId;

  private List<Event> mEvents = new ArrayList<Event>();

  public Event start(long time, int type) {
    Event event = new Event(type, mEventId, time, -1);
    mEvents.add(event);
    mEventId++;
    return event;
  }

  public int size() {
    return mEvents.size();
  }

  public Event get(int i) {
    return mEvents.get(i);
  }

  public static class Event {

    int type;
    int id;
    long from;
    long to;

    private Event(int type, int id, long from, long to) {
      this.type = type;
      this.id = id;
      this.from = from;
      this.to = to;
    }

    public void stop(long time) {
      assert to == -1;
      to = time;
    }
  }
}
