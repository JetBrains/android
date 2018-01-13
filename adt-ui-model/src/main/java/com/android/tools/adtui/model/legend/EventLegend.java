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
package com.android.tools.adtui.model.legend;

import com.android.tools.adtui.model.DurationData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

public class EventLegend<E extends DurationData> implements Legend {
  @NotNull private final String myName;
  /**
   * Formats the DurationData into a String suitable for display.
   */
  @NotNull private final Function<E, String> myFormatter;
  /**
   * myPickData is the {@link DurationData} that the UI has set as the currently hovered-over object.
   */
  @Nullable private E myPickData;

  public EventLegend(@NotNull String name, @NotNull Function<E, String> formatter) {
    myName = name;
    myFormatter = formatter;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  public void setPickData(@Nullable E pickData) {
    myPickData = pickData;
  }

  @Nullable
  @Override
  public String getValue() {
    if (myPickData != null) {
      return myFormatter.apply(myPickData);
    }
    return null;
  }
}
