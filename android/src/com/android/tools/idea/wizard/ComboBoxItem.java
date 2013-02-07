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
package com.android.tools.idea.wizard;

import org.jetbrains.annotations.NotNull;

/**
 * ComboBoxItem is a container class for an item of a {@link JComboBox} that's backed by a
 * {@link Parameter} representing an enumerated type. It has a humnan-readable label, an
 * ID in parameter space, and optional API restrictions.
*/
public class ComboBoxItem {
  public Object id;
  public String label;
  public int minApi;
  public int minBuildApi;

  public ComboBoxItem(@NotNull Object id, @NotNull String label, int minApi, int minBuildApi) {
    this.id = id;
    this.label = label;
    this.minApi = minApi;
    this.minBuildApi = minBuildApi;
  }

  @Override
  public String toString() {
    return label;
  }
}
