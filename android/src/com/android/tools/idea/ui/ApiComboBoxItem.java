/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.ui;

import com.google.common.base.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * ApiComboBoxItem wraps choices that appear in a combobox where a user should only be able to
 * select them if they're targeting a minimum API (or newer). It is up to the parent UI form to
 * query these combobox items and reject them if necessary.
 */
public class ApiComboBoxItem {
  private String myData;
  private String myLabel;

  public ApiComboBoxItem(@NotNull String data, @NotNull String label) {
    myData = data;
    myLabel = label;
  }

  public final String getData() {
    return myData;
  }

  public final String getLabel() {
    return myLabel;
  }

  @Override
  public final boolean equals(Object obj) {
    if (obj == null || !obj.getClass().equals(getClass())) {
      return false;
    }
    ApiComboBoxItem other = (ApiComboBoxItem)obj;
    return Objects.equal(myData, other.myData) && Objects.equal(myLabel, other.myLabel);
  }

  @Override
  public final int hashCode() {
    return Objects.hashCode(myData, myLabel);
  }

  @Override
  public String toString() {
    return myLabel;
  }
}
