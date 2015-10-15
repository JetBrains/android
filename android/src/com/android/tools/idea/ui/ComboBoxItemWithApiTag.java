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

/**
 * ComboBoxItemWithApiTag wraps choices that appear in a combobox where a user should only be able to
 * select them if they're targeting a minimum API (or newer). It is up to the parent UI form to
 * query these combobox items and reject them if necessary.
 * TODO: This seems backwards, throwing data on a combobox item and then using instanceof checks in
 * various classes to cast and pull API info out later. Let's investigate how this is used and see
 * if there's a better way.
 */
public class ComboBoxItemWithApiTag {
  public Object id;
  public String label;
  public int minApi;
  public int minBuildApi;

  public ComboBoxItemWithApiTag(@NotNull Object id, @NotNull String label, int minApi, int minBuildApi) {
    this.id = id;
    this.label = label;
    this.minApi = minApi;
    this.minBuildApi = minBuildApi;
  }

  @Override
  public String toString() {
    return label;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null || !obj.getClass().equals(getClass())) {
      return false;
    }
    ComboBoxItemWithApiTag other = (ComboBoxItemWithApiTag)obj;
    return Objects.equal(id, other.id) && Objects.equal(label, other.label);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id, label);
  }
}
