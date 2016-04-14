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
package com.android.tools.idea.npw.assetstudio.icon;

import com.android.assetstudiolib.GraphicGenerator;
import com.android.assetstudiolib.NotificationIconGenerator;
import com.android.resources.Density;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Class that wraps the complex, nested {@code Map<String, Map<String, BufferedImage>>} returned
 * by a {@link GraphicGenerator} and provides a more user-friendly API for interacting with it.
 *
 * The original map looks something like this:
 * <pre>
 *   category1
 *     path1: image
 *     path2: image
 *     path3: image
 *   cagegory2
 *     path1: image
 *     path2: image
 * </pre>
 *
 * although particular organization schemes and layouts differ slightly across icon types. In
 * addition to simplifying the underlying data structure, this class also attempts to make it
 * easier to interact with the different icon layouts.
 */
public final class CategoryIconMap {
  private static final Filter ACCEPT_ALL = new Filter() {
    @Override
    public boolean accept(@NotNull String category) {
      return true;
    }
  };

  private static final Map<Density, Pattern> DENSITY_PATTERNS;

  static {
    // Create regex patterns that search an icon path and find a valid density
    // Paths look like: /mipmap-hdpi/, /drawable-xxdpi/, /drawable-xxxdpi-v9/
    // Therefore, we search for the density value surrounded by symbols (especially to distinguish
    // xdpi, xxdpi, and xxxdpi)
    ImmutableMap.Builder<Density, Pattern> builder = ImmutableMap.builder();
    for (Density density : Density.values()) {
      builder.put(density, Pattern.compile(String.format(".*[^a-z]%s[^a-z].*", density.getResourceValue()), Pattern.CASE_INSENSITIVE));
    }
    DENSITY_PATTERNS = builder.build();
  }

  @NotNull private final Map<String, Map<String, BufferedImage>> myCategoryMap;

  public CategoryIconMap(@NotNull Map<String, Map<String, BufferedImage>> categoryMap) {
    myCategoryMap = categoryMap;
  }

  /**
   * Convert the path to a density, if possible. Output paths don't always map cleanly to density
   * values, such as the path for the "web" icon, so in those cases, {@code null} is returned.
   */
  @Nullable
  public static Density pathToDensity(@NotNull String iconPath) {

    iconPath = FileUtil.toSystemIndependentName(iconPath);
    // Strip off the filename, in case the user names their icon "xxxhdpi" etc.
    // but leave the trailing slash, as it's used in the regex pattern
    iconPath = iconPath.substring(0, iconPath.lastIndexOf('/') + 1);

    for (Density density : Density.values()) {
      if (DENSITY_PATTERNS.get(density).matcher(iconPath).matches()) {
        return density;
      }
    }

    return null;
  }

  /**
   * Returns all icons as a single map of densities to images. Note that this may exclude images
   * that don't map neatly to any {@link Density}.
   */
  @NotNull
  public Map<Density, BufferedImage> toDensityMap() {
    return toDensityMap(ACCEPT_ALL);
  }

  /**
   * Like {@link #toDensityMap()} but with a filter for stripping out unwanted categories. This is
   * useful for icon sets organized by API.
   */
  @NotNull
  public Map<Density, BufferedImage> toDensityMap(@NotNull Filter filter) {
    Map<Density, BufferedImage> densityImageMap = Maps.newHashMap();
    for (String category : myCategoryMap.keySet()) {
      if (filter.accept(category)) {
        Map<String, BufferedImage> pathImageMap = myCategoryMap.get(category);
        for (String path : pathImageMap.keySet()) {
          Density density = pathToDensity(path);
          if (density != null) {
            BufferedImage image = pathImageMap.get(path);
            densityImageMap.put(density, image);
          }
        }
      }
    }

    return densityImageMap;
  }

  /**
   * Returns all icons as a single map of file paths to images. This is very useful when writing
   * icons to disk.
   */
  @NotNull
  public Map<File, BufferedImage> toFileMap(@NotNull File rootDir) {
    Map<File, BufferedImage> outputMap = Maps.newHashMap();
    for (Map<String, BufferedImage> pathImageMap : myCategoryMap.values()) {
      for (Map.Entry<String, BufferedImage> pathImageEntry : pathImageMap.entrySet()) {
        outputMap.put(new File(rootDir, pathImageEntry.getKey()), pathImageEntry.getValue());
      }
    }
    return outputMap;
  }


  /**
   * Category filter used when flattening our nested maps into a single-level map.
   */
  public interface Filter {
    boolean accept(@NotNull String category);
  }

  /**
   * Useful filter when flattening icons generated by {@link NotificationIconGenerator}.
   */
  public static final class NotificationFilter implements Filter {
    @NotNull private final NotificationIconGenerator.Version myVersion;

    public NotificationFilter(@NotNull NotificationIconGenerator.Version version) {
      myVersion = version;
    }

    @Override
    public boolean accept(@NotNull String category) {
      return myVersion.getDisplayName().equals(category);
    }
  }
}
