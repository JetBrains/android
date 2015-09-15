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

import com.android.ide.common.resources.configuration.Configurable;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import org.jetbrains.annotations.NotNull;

/**
 * Class that contains any object that has a {@link FolderConfiguration} associated. This class is used to contain
 * attribute values when they come from a specific folder or theme parents when there are multiple of them coming from
 * different configurations.
 */
public class ConfiguredElement<T> implements Configurable {
  final FolderConfiguration myFolderConfiguration;
  final T myValue;

  private ConfiguredElement(@NotNull FolderConfiguration folderConfiguration, @NotNull T value) {
    myFolderConfiguration = folderConfiguration;
    myValue = value;
  }

  /**
   * Returns the {@link FolderConfiguration} associated to the element returned by {@link #getElement()}
   */
  @Override
  @NotNull
  public FolderConfiguration getConfiguration() {
    return myFolderConfiguration;
  }

  /**
   * Returns the configured element
   */
  @NotNull
  public T getElement() {
    return myValue;
  }

  /**
   * Factory method to create new ConfiguredElement instances
   *
   * @param folderConfiguration the {@link FolderConfiguration} associated to the given value
   * @param value               the value for this element
   */
  public static <T> ConfiguredElement<T> create(@NotNull FolderConfiguration folderConfiguration, @NotNull T value) {
    return new ConfiguredElement<T>(folderConfiguration, value);
  }
}
