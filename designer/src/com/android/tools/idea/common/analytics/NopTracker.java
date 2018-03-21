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
package com.android.tools.idea.common.analytics;

import com.android.tools.idea.common.property.NlProperty;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.uibuilder.property.NlPropertiesPanel.PropertiesViewMode;
import com.google.wireless.android.sdk.stats.LayoutEditorEvent;
import com.google.wireless.android.sdk.stats.LayoutEditorRenderResult;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * No-op tracker used when stats tracking is disabled
 */
class NopTracker implements NlUsageTracker {
  @Override
  public void logAction(@NotNull LayoutEditorEvent.LayoutEditorEventType eventType) {
  }

  @Override
  public void logRenderResult(@Nullable LayoutEditorRenderResult.Trigger trigger, @NotNull RenderResult result, long totalRenderTimeMs) {
  }


  @Override
  public void logDropFromPalette(@NotNull String viewTagName,
                                 @NotNull String representation,
                                 @NotNull String selectedGroup,
                                 int filterMatches) {
  }

  @Override
  public void logPropertyChange(@NotNull NlProperty property,
                                @NotNull PropertiesViewMode propertiesMode,
                                int filterMatches) {
  }

  @Override
  public void logFavoritesChange(@NotNull String addedPropertyName,
                                 @NotNull String removedPropertyName,
                                 @NotNull List<String> currentFavorites,
                                 @NotNull AndroidFacet facet) {
  }
}
