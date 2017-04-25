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
package com.android.tools.adtui.ptable.simple;

import com.android.tools.adtui.ptable.PTableItem;
import com.android.tools.adtui.ptable.StarState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SimpleItem extends PTableItem {
  private String myName;
  private String myValue;
  private StarState myStarState;

  public SimpleItem(@NotNull String name, @Nullable String value) {
    this(name, value, StarState.STAR_ABLE);
  }

  public SimpleItem(@NotNull String name, @Nullable String value, @NotNull StarState starState) {
    myName = name;
    myValue = value;
    myStarState = starState;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Nullable
  @Override
  public String getValue() {
    return myValue;
  }

  @NotNull
  @Override
  public StarState getStarState() {
    return myStarState;
  }

  @Override
  public void setStarState(@NotNull StarState starState) {
    myStarState = starState;
  }

  @Nullable
  @Override
  public String getResolvedValue() {
    return myValue;
  }

  @Override
  public boolean isDefaultValue(@Nullable String value) {
    return value == null;
  }

  @Override
  public void setValue(@Nullable Object value) {
    myValue = (String)value;
  }
}
