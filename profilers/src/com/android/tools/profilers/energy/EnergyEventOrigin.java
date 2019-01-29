/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.energy;

import org.jetbrains.annotations.NotNull;

/**
 * A class representing the origin of where an energy event occurred, so it can be attributed either to the user's own app or to
 * some third party library they are using.
 */
public enum EnergyEventOrigin {
  ALL ("All"),
  APP_ONLY ("App only"),
  THIRD_PARTY_ONLY("Third-party only"),
  ;

  @NotNull private final String myLabelString;

  EnergyEventOrigin(@NotNull String labelString) {
    myLabelString = labelString;
  }

  @NotNull
  public String getLabelString() {
    return myLabelString;
  }

  /**
   * Returns whether the given event's origin is related to the current app or not.
   *
   * @param appName The qualified name of the current app, e.g. com.example.app
   * @param eventOrigin The stack trace line pointing to where the event was constructed
   */
  public boolean isValid(@NotNull String appName, @NotNull String eventOrigin) {
    if (appName.isEmpty()) {
      return true;
    }
    switch (this) {
      case APP_ONLY:
        return !eventOrigin.isEmpty() && eventOrigin.startsWith(appName);
      case THIRD_PARTY_ONLY:
        return !eventOrigin.isEmpty() && !eventOrigin.startsWith(appName);
      case ALL:
        // fall through
      default:
        return true;
    }
  }
}
