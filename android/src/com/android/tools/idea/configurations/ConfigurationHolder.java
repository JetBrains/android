/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.configurations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Holder for configurations
 */
public interface ConfigurationHolder {
  /**
   * Returns the current configuration, if any (should never return null when
   * a file is being rendered; can only be null if no file is showing)
   */
  @Nullable
  Configuration getConfiguration();

  /**
   * Sets the given configuration to be used for rendering
   *
   * @param configuration the configuration to use
   */
  void setConfiguration(@NotNull Configuration configuration);
}
