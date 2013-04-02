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

import org.jetbrains.annotations.NotNull;

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
   * Show a manually configured set of previews
   */
  CUSTOM,

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
    ourCurrent = current;
  }

  private static RenderPreviewMode ourCurrent = NONE;
}
