/*
 * Copyright (C) 2013 The Android Open Source Project
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
import org.jetbrains.annotations.Nullable;

/**
 * ApiComboBoxItem wraps choices that appear in a combobox where a user should only be able to
 * select them if they're targeting a minimum API (or newer). It is up to the parent UI form to
 * query these combobox items and reject them if necessary.
 *
 * @param <T> The type of item this class wraps around.
 */
public class ApiComboBoxItem<T> {
  private T myData;
  private String myLabel;
  private int myMinApi;
  private int myMinBuildApi;

  public ApiComboBoxItem(@NotNull T data, @NotNull String label, int minApi, int minBuildApi) {
    myData = data;
    myLabel = label;
    myMinApi = minApi;
    myMinBuildApi = minBuildApi;
  }

  public final T getData() {
    return myData;
  }

  public final String getLabel() {
    return myLabel;
  }

  public final int getMinApi() {
    return myMinApi;
  }

  public final int getMinBuildApi() {
    return myMinBuildApi;
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

  /**
   * Validate this feature against the current API levels set for the project. Returns an error
   * string if invalid, or {@code null} if there are no issues.
   */
  @Nullable
  public final String validate(int projectApi, int projectBuildApi) {
    if (myMinApi > projectApi) {
      return String
        .format("The feature \"%1$s\" requires a minimum API level of %2$d (project currently set to %3$d)", myLabel, myMinApi, projectApi);
    }
    if (myMinBuildApi > projectBuildApi) {
      return String
        .format("The feature \"%1$s\" requires a minimum build API level of %2$d (project currently set to %3$d)", myLabel, myMinBuildApi,
                projectBuildApi);
    }

    return null;
  }

  @Override
  public String toString() {
    return myLabel;
  }
}
