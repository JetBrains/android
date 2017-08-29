/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.npw.assetstudio;

import com.android.annotations.Nullable;
import java.awt.image.BufferedImage;

/**
 * The context used during graphic generation.
 */
public interface GraphicGeneratorContext {
  /**
   * Loads the given image resource, as requested by the graphic generator.
   *
   * @param path The path to the resource, relative to the general "resources" path, as defined by
   *   the context implementer.
   * @return The loaded image resource, or null if there was an error.
   */
  @Nullable
  BufferedImage loadImageResource(String path);
}
