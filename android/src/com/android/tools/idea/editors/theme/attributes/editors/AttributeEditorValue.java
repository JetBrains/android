/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.theme.attributes.editors;

import org.jetbrains.annotations.NotNull;

/**
 * Value that's returned by cell editors in theme editor.
 * Contains a new value and a flag that indicates whether reload of a theme should be forced.
 */
public class AttributeEditorValue {
  private final @NotNull String myResourceValue;

  /**
   * This flag is used to indicate whether reloading of a theme should be forced after
   * editing was done. Without forcing, reloading of a theme would be skipped when
   * a new value is the same as the old one. However, that's not always a desired
   * behaviour - color editor is able to edit color values and return the same reference
   * when underlying color is changed. That's a situation where reloading should be forced.
   */
  private final boolean myForceReload;

  public AttributeEditorValue(@NotNull String resourceValue, boolean forceReload) {
    myResourceValue = resourceValue;
    myForceReload = forceReload;
  }

  public boolean isForceReload() {
    return myForceReload;
  }

  @NotNull
  public String getResourceValue() {
    return myResourceValue;
  }
}
