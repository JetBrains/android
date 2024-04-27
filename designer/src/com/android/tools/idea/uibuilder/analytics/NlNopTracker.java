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
package com.android.tools.idea.uibuilder.analytics;

import com.android.tools.idea.uibuilder.property.NlPropertyItem;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * No-op tracker used when stats tracking is disabled.
 */
class NlNopTracker implements NlUsageTracker {
  @Override
  public void logDropFromPalette(@NotNull String viewTagName,
                                 @NotNull String representation,
                                 @NotNull String selectedGroup,
                                 int filterMatches) {
  }

  @Override
  public void logPropertyChange(@NotNull NlPropertyItem property,
                                int filterMatches) {
  }

  @Override
  public void logFavoritesChange(@NotNull String addedPropertyName,
                                 @NotNull String removedPropertyName,
                                 @NotNull List<String> currentFavorites,
                                 @NotNull AndroidFacet facet) {
  }
}
