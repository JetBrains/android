/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.inspectors.common.ui.dataviewer;

import java.awt.image.BufferedImage;
import org.jetbrains.annotations.NotNull;

/**
 * A {@link DataViewer} that targets images.
 */
public interface ImageDataViewer extends DataViewer {
  /**
   * The (width x height) size of the target image data, or {@code null} if the concept of a size
   * doesn't make sense for the file type (e.g. txt, xml)
   */
  @NotNull
  BufferedImage getImage();

  @NotNull
  @Override
  default Style getStyle() { return Style.RAW; }
}
