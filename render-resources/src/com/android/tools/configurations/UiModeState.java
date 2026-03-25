/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.configurations;

import static com.android.tools.configurations.ConfigurationListener.CFG_NIGHT_MODE;
import static com.android.tools.configurations.ConfigurationListener.CFG_UI_MODE;

import com.android.resources.NightMode;
import com.android.resources.UiMode;
import org.jetbrains.annotations.NotNull;

/**
 * Encapsulates the bitwise logic for parsing Android UI Mode and Night Mode flags.
 */
public class UiModeState {
  public static final int UI_MODE_TYPE_MASK = 0x0000000f;
  public static final int UI_MODE_NIGHT_YES = 0x00000020;
  public static final int UI_MODE_NIGHT_NO = 0x00000010;
  private static final int UI_MODE_TYPE_APPLIANCE = 0x00000005;
  private static final int UI_MODE_TYPE_CAR = 0x00000003;
  private static final int UI_MODE_TYPE_DESK = 0x00000002;
  private static final int UI_MODE_TYPE_NORMAL = 0x00000001;
  private static final int UI_MODE_TYPE_TELEVISION = 0x00000004;
  private static final int UI_MODE_TYPE_VR_HEADSET = 0x00000007;
  private static final int UI_MODE_TYPE_WATCH = 0x00000006;

  private static final int UI_MODE_NIGHT_MASK = 0x00000030;

  @NotNull private UiMode myUiMode = UiMode.NORMAL;
  @NotNull private NightMode myNightMode = NightMode.NOTNIGHT;
  private int myUiModeFlagValue;

  @NotNull
  public UiMode getUiMode() { return myUiMode; }

  @NotNull
  public NightMode getNightMode() { return myNightMode; }

  public int getUiModeFlagValue() { return myUiModeFlagValue; }

  public int setNightMode(@NotNull NightMode night) {
    if (myNightMode != night) {
      return setUiModeFlagValue((myUiModeFlagValue & UI_MODE_TYPE_MASK) |
                                (night == NightMode.NIGHT ? UI_MODE_NIGHT_YES : UI_MODE_NIGHT_NO));
    }
    return 0;
  }

  public int setUiMode(@NotNull UiMode uiMode) {
    if (myUiMode != uiMode) {
      int newUiTypeFlags = 0;
      switch (uiMode) {
        case NORMAL:
          newUiTypeFlags = UI_MODE_TYPE_NORMAL;
          break;
        case DESK:
          newUiTypeFlags = UI_MODE_TYPE_DESK;
          break;
        case WATCH:
          newUiTypeFlags = UI_MODE_TYPE_WATCH;
          break;
        case TELEVISION:
          newUiTypeFlags = UI_MODE_TYPE_TELEVISION;
          break;
        case APPLIANCE:
          newUiTypeFlags = UI_MODE_TYPE_APPLIANCE;
          break;
        case CAR:
          newUiTypeFlags = UI_MODE_TYPE_CAR;
          break;
        case VR_HEADSET:
          newUiTypeFlags = UI_MODE_TYPE_VR_HEADSET;
          break;
      }
      return setUiModeFlagValue((myUiModeFlagValue & UI_MODE_NIGHT_MASK) | newUiTypeFlags);
    }
    return 0;
  }

  public int setUiModeFlagValue(int uiMode) {
    int modifiedElements = myUiModeFlagValue ^ uiMode;
    myUiModeFlagValue = uiMode;
    int updatedFlags = 0;

    if ((modifiedElements & UI_MODE_NIGHT_MASK) != 0) {
      myNightMode = ((uiMode & UI_MODE_NIGHT_MASK) == UI_MODE_NIGHT_YES) ? NightMode.NIGHT : NightMode.NOTNIGHT;
      updatedFlags |= CFG_NIGHT_MODE;
    }

    if ((modifiedElements & UI_MODE_TYPE_MASK) != 0) {
      switch (uiMode & UI_MODE_TYPE_MASK) {
        case UI_MODE_TYPE_APPLIANCE:
          myUiMode = UiMode.APPLIANCE;
          break;
        case UI_MODE_TYPE_CAR:
          myUiMode = UiMode.CAR;
          break;
        case UI_MODE_TYPE_TELEVISION:
          myUiMode = UiMode.TELEVISION;
          break;
        case UI_MODE_TYPE_WATCH:
          myUiMode = UiMode.WATCH;
          break;
        case UI_MODE_TYPE_DESK:
          myUiMode = UiMode.DESK;
          break;
        case UI_MODE_TYPE_VR_HEADSET:
          myUiMode = UiMode.VR_HEADSET;
          break;
        default:
          myUiMode = UiMode.NORMAL;
      }
      updatedFlags |= CFG_UI_MODE;
    }

    return updatedFlags;
  }

  public void copyFrom(UiModeState other) {
    this.myUiMode = other.myUiMode;
    this.myNightMode = other.myNightMode;
    this.myUiModeFlagValue = other.myUiModeFlagValue;
  }
}