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
package com.android.tools.idea.rendering.multi;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

/**
 * The {@linkplain RenderPreviewMode} records what type of configurations to
 * render in the layout editor
 */
public enum RenderPreviewMode {
  /**
   * Generate a set of default previews with maximum variation
   */
  DEFAULT,

  /**
   * Preview all the locales
   */
  LOCALES,

  /**
   * Preview all the screen sizes
   */
  SCREENS,

  /**
   * Preview layout as included in other layouts
   */
  INCLUDES,

  /**
   * Preview all the variations of this layout
   */
  VARIATIONS,

  /**
   * Show bi-directional (e.g. left to right and right to left) layouts
   */
  RTL,

  /**
   * Show a manually configured set of previews
   */
  CUSTOM,

  /**
   * Show the layout across major API levels.
   */
  API_LEVELS,

  /**
   * No previews
   */
  NONE;

  /** Gets the current render preview mode (across layouts and projects) */
  @NotNull
  public static RenderPreviewMode getCurrent() {
    return ourCurrent;
  }

  /** Sets the current render preview mode (across layouts and projects). Not persisted. */
  public static void setCurrent(@NotNull RenderPreviewMode current) {
    if (ourCurrent != current) {
      ourCurrent = current;
      ourDeletedIds = null;
    }
  }

  private static Collection<String> ourDeletedIds;

  /**
   * Returns true if the given id has been marked as deleted
   */
  public static boolean isDeletedId(@Nullable String id) {
    return ourDeletedIds != null && id != null && ourDeletedIds.contains(id);
  }

  /**
   * Record the given preview id as deleted. This allows you to semi-persistently
   * delete for example a given locale or screen size, and as you switch to other
   * layouts (and the set of previews are updated for the new layout) the given
   * type of preview continues to stay hidden -- until you switch preview modes.
   */
  public static void deleteId(@Nullable String id) {
    if (id != null) {
      if (ourDeletedIds == null) {
        ourDeletedIds = Sets.newHashSet();
      }
      ourDeletedIds.add(id);
    }
  }

  private static RenderPreviewMode ourCurrent = NONE;

  /**
   * Returns true if this preview mode includes device frames around the layouts
   */
  public boolean showsDeviceFrames() {
    return this == SCREENS;
  }
}
