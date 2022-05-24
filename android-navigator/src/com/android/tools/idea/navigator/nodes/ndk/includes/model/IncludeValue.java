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
package com.android.tools.idea.navigator.nodes.ndk.includes.model;

import org.jetbrains.annotations.NotNull;


/**
 * Base include expression. Defines how this include should be sorted among others at the same level.
 */
abstract public class IncludeValue {

  @NotNull
  public abstract String getSortKey();

  /**
   * Defines the sort order of include expression types relative to each other.
   */
  protected enum SortOrderKey {
    SHADOWING_INCLUDE_EXPRESSION("[sort-a]"),
    // Sort NDK packages before other packaging families
    NDK_PACKAGING_FAMILY("[sort-d]"),
    OTHER_PACKAGING_FAMILY("[sort-f]"),
    SIMPLE_INCLUDE("[sort-q]"),
    PACKAGING("[sort-q]");

    @NotNull final String myKey;

    SortOrderKey(@NotNull String key) {
      myKey = key;
    }
  }
}
