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
package com.android.tools.idea.editors.theme.datamodels;

import com.android.ide.common.rendering.api.ItemResourceValue;
import com.android.ide.common.resources.configuration.Configurable;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.tools.idea.editors.theme.ResolutionUtils;
import com.google.common.base.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * {@link ItemResourceValue} associated to the {@link FolderConfiguration} that contains it.
 */
public class ConfiguredItemResourceValue implements Configurable {
  final FolderConfiguration myFolderConfiguration;
  final ItemResourceValue myValue;
  final ThemeEditorStyle mySourceStyle;

  public ConfiguredItemResourceValue(@NotNull FolderConfiguration folderConfiguration,
                                     @NotNull ItemResourceValue value,
                                     @NotNull ThemeEditorStyle sourceStyle) {
    myFolderConfiguration = folderConfiguration;
    myValue = value;
    mySourceStyle = sourceStyle;
  }

  /**
   * Returns the {@link FolderConfiguration} associated to the {@link ItemResourceValue} returned by {@link #getItemResourceValue()}
   */
  @Override
  @NotNull
  public FolderConfiguration getConfiguration() {
    return myFolderConfiguration;
  }

  @NotNull
  public ItemResourceValue getItemResourceValue() {
    return myValue;
  }

  @NotNull
  public ThemeEditorStyle getSourceStyle() {
    return mySourceStyle;
  }

  @Override
  public String toString() {
    return String.format("[%1$s] %2$s = %3$s (%4$s)", myFolderConfiguration, ResolutionUtils.getQualifiedItemName(myValue),
                         ResolutionUtils.getQualifiedValue(myValue), mySourceStyle.getQualifiedName());
  }
}
