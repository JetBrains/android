/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.adtui.model.event;

/**
 * This class stores the key, or string that was passed to use from PerfD
 * for a key action. That string is then rendered in the UI as part of the event.
 */
public class KeyboardAction extends EventAction<SimpleEventType> {
  private KeyboardData myData;

  /**
   * @param start down time of the key, or start time of the soft keyboard string
   * @param end   up time of the key, or end time of the soft keyboard string often the same as the start time.
   * @param data  key character from a keyboard, or the string returned from the Softkeyboard InputConnection class.
   */
  public KeyboardAction(long start, long end, KeyboardData data) {
    super(start, end, SimpleEventType.KEYBOARD);
    myData = data;
  }

  public KeyboardData getData() {
    return myData;
  }
}
