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

import org.jetbrains.annotations.NotNull;

/**
 * Class to wrap keyboard data coming from perfd.
 */
public class KeyboardData {

  /**
   * Text which was entered by the keyboard, such as a single letter, or even a whole word if swiped or
   * selected from a list of recommendations.
   */
  @NotNull private String myData;

  public KeyboardData(@NotNull String data) {
    myData = data;
  }

  @Override
  public String toString() {
    return myData;
  }
}
