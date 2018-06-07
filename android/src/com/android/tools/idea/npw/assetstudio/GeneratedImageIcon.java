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
package com.android.tools.idea.npw.assetstudio;

import com.android.resources.Density;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

/** A {@link GeneratedIcon} that is defined by a {@link BufferedImage} at a given density. */
public class GeneratedImageIcon extends GeneratedIcon {
  @NotNull private final String name;
  @Nullable private final Path outputPath;
  @NotNull private final IconCategory category;
  @NotNull private final Density density;
  @NotNull private final BufferedImage image;

  public GeneratedImageIcon(@NotNull String name, @Nullable Path outputPath, @NotNull IconCategory category, @NotNull Density density,
                            @NotNull BufferedImage image) {
    this.name = name;
    this.outputPath = outputPath;
    this.category = category;
    this.density = density;
    this.image = image;
  }

  @Override
  @NotNull
  public String getName() {
    return name;
  }

  @Override
  @Nullable
  public Path getOutputPath() {
    return outputPath;
  }

  @Override
  @NotNull
  public IconCategory getCategory() {
    return category;
  }

  @NotNull
  public Density getDensity() {
    return density;
  }

  @NotNull
  public BufferedImage getImage() {
    return image;
  }
}
