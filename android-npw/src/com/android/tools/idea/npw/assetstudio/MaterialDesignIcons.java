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

import com.android.SdkConstants;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.android.SdkConstants.DOT_XML;
import static com.android.tools.idea.npw.assetstudio.BuiltInImages.getJarFilePath;
import static com.android.tools.idea.npw.assetstudio.BuiltInImages.getResourcesNames;

/**
 * Methods for accessing library of material design icons.
 */
public final class MaterialDesignIcons {
  private static final String DEFAULT_ICON_NAME = "/action/ic_android_black_24dp.xml";
  private static final String PATH = "images/material_design_icons";
  private static final Pattern CATEGORY_PATTERN = Pattern.compile(PATH + "/(\\w+)/");

  /** Do not instantiate - all methods are static. */
  private MaterialDesignIcons() {
  }

  @Nullable
  public static String getPathForBasename(@NotNull String basename) {
    return getBasenameToPathMap(path -> getResourcesNames(path, DOT_XML)).get(basename);
  }

  @NotNull
  @VisibleForTesting
  public static Map<String, String> getBasenameToPathMap(@NotNull Function<String, List<String>> generator) {
    ImmutableMap.Builder<String, String> builder = new ImmutableMap.Builder<>();
    int dotXmlLength = DOT_XML.length();

    for (String category : getCategories()) {
      String path = PATH + '/' + category;

      for (String name : generator.apply(path)) {
        builder.put(name.substring(0, name.length() - dotXmlLength), path + '/' + name);
      }
    }

    return builder.build();
  }

  @NotNull
  private static Collection<String> getCategories() {
    return getCategories(getResourceUrl(PATH));
  }

  @NotNull
  public static URL getIcon(@NotNull String iconName, @NotNull String categoryName) {
    return getResourceUrl(getIconDirectoryPath(categoryName) + iconName);
  }

  @NotNull
  public static URL getDefaultIcon() {
    URL url = getResourceUrl(PATH + DEFAULT_ICON_NAME);
    assert url != null;
    return url;
  }

  @VisibleForTesting
  public static Collection<String> getCategories(@Nullable URL url) {
    if (url == null) {
      return Collections.emptyList();
    }

    switch (url.getProtocol()) {
      case "file":
        return getCategoriesFromFile(new File(url.getPath()));
      case "jar":
        try {
          String jarPath = getJarFilePath(url);
          try (ZipFile jarFile = new ZipFile(jarPath)) {
            return getCategoriesFromJar(jarFile);
          }
        } catch (IOException e) {
          return Collections.emptyList();
        }
      default:
        return Collections.emptyList();
    }
  }

  @NotNull
  @VisibleForTesting
  public static Collection<String> getCategoriesFromFile(@NotNull File file) {
    String[] array = file.list();

    if (array == null) {
      return Collections.emptyList();
    }

    List<String> list = Arrays.asList(array);
    list.sort(String::compareTo);

    return list;
  }

  @NotNull
  @VisibleForTesting
  public static Collection<String> getCategoriesFromJar(@NotNull ZipFile jar) {
    return jar.stream()
        .map(MaterialDesignIcons::getCategory)
        .filter(Objects::nonNull)
        .sorted()
        .collect(Collectors.toList());
  }

  @Nullable
  private static String getCategory(@NotNull ZipEntry entry) {
    Matcher matcher = CATEGORY_PATTERN.matcher(entry.getName());
    return matcher.matches() ? matcher.group(1) : null;
  }

  @NotNull
  private static String getIconDirectoryPath(String categoryName) {
    return PATH + StringUtil.toLowerCase(categoryName) + '/';
  }

  private static URL getResourceUrl(String iconPath) {
    return MaterialDesignIcons.class.getClassLoader().getResource(iconPath);
  }
}
