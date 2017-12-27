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
package com.android.tools.idea.uibuilder.statelist;

import android.R;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

enum State {
  PRESSED("Pressed", R.attr.state_pressed),
  FOCUSED("Focused", R.attr.state_focused),
  HOVERED("Hovered", R.attr.state_hovered),
  SELECTED("Selected", R.attr.state_selected),
  CHECKABLE("Checkable", R.attr.state_checkable),
  CHECKED("Checked", R.attr.state_checked),
  ENABLED("Enabled", R.attr.state_enabled),
  ACTIVATED("Activated", R.attr.state_activated),
  WINDOW_FOCUSED("Window Focused", R.attr.state_window_focused);

  private final String myText;
  private final int myIntValue;

  State(@NotNull String text, int intValue) {
    myText = text;
    myIntValue = intValue;
  }

  @NotNull
  final String getText() {
    return myText;
  }

  final int getIntValue() {
    return myIntValue;
  }

  @NotNull
  @Override
  public final String toString() {
    return "state_" + myText.toLowerCase(Locale.ROOT).replace(' ', '_');
  }

  @Nullable
  static State valueOfString(@NotNull String string) {
    Optional<State> optionalValue = Arrays.stream(values())
      .filter(value -> value.toString().equals(string))
      .findFirst();

    return optionalValue.orElse(null);
  }
}