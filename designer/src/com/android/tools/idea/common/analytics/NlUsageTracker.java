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

import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.uibuilder.palette.PaletteMode;
import com.android.tools.idea.uibuilder.property.NlPropertiesPanel;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.google.wireless.android.sdk.stats.LayoutEditorEvent;
import com.google.wireless.android.sdk.stats.LayoutEditorRenderResult;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.tools.idea.common.model.NlModel.*;

/**
 * Interface for usage tracking in the layout editor. Not that implementations of these methods should aim to return immediately.
 */
public interface NlUsageTracker {
  /**
   * Logs a layout editor event in the usage tracker. Note that rendering actions should be logged through the
   * {@link #logRenderResult(ChangeType, RenderResult, long)} method so it contains additional information about the render result.
   */
  void logAction(@NotNull LayoutEditorEvent.LayoutEditorEventType eventType);

  /**
   * Logs a render action.
   *
   * @param trigger The event that triggered the render action or null if not known.
   */
  void logRenderResult(@Nullable LayoutEditorRenderResult.Trigger trigger, @NotNull RenderResult result, long totalRenderTimeMs);

  /**
   * Logs a component drop from the palette to either the design surface of the component tree.
   *
   * @param viewTagName The tag name of the component dropped in the layout editor.
   * @param representation The XML representation of the component.
   * @param paletteMode The mode used in the palette during the drop: icon & name, or icon only.
   * @param selectedGroup The group used to find this component on the palette.
   * @param filterMatches The number of matches if attribute name was selected by a filter or -1 if not filtered.
   */
  void logDropFromPalette(@NotNull String viewTagName,
                          @NotNull String representation,
                          @NotNull PaletteMode paletteMode,
                          @NotNull String selectedGroup,
                          int filterMatches);

  /**
   * Logs a property change action through either the inspector or the property table.
   *
   * @param property The property that was changed.
   * @param propertiesMode The mode used: inspector or property table.
   * @param filterMatches The number of matches if attribute name was selected by a filter or -1 if not filtered.
   */
  void logPropertyChange(@NotNull NlProperty property,
                         @NotNull NlPropertiesPanel.PropertiesViewMode propertiesMode,
                         int filterMatches);

  /**
   * Logs a change in the set of favorite properties shown on the inspector.
   *
   * @param addedPropertyName The name of a newly added property or the empty string.
   * @param removedPropertyName The name of a newly removed property or the empty string.
   * @param currentFavorites The names of the currently selected favorite properties.
   */
  void logFavoritesChange(@NotNull String addedPropertyName,
                          @NotNull String removedPropertyName,
                          @NotNull List<String> currentFavorites,
                          @NotNull AndroidFacet facet);
}
