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
package com.android.tools.idea.npw;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.model.MergedManifest;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ModuleResourceRepository;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Theme utility class for use with templates.
 */
public class ThemeHelper {
  private static final String DEFAULT_THEME_NAME = "AppTheme";    //$NON-NLS-1$
  private static final String ALTERNATE_THEME_NAME = "Theme.App"; //$NON-NLS-1$
  private static final String APP_COMPAT = "Theme.AppCompat.";    //$NON-NLS-1$

  private Module myModule;
  private LocalResourceRepository myRepository;

  public ThemeHelper(@NotNull Module module) {
    myModule = module;
    myRepository = ModuleResourceRepository.getModuleResources(module, true);
  }

  @Nullable
  public String getAppThemeName() {
    String manifestTheme = MergedManifest.get(myModule).getManifestTheme();
    if (manifestTheme != null) {
      if (manifestTheme.startsWith(SdkConstants.STYLE_RESOURCE_PREFIX)) {
        manifestTheme = manifestTheme.substring(SdkConstants.STYLE_RESOURCE_PREFIX.length());
      }
      return manifestTheme;
    }
    if (getLocalStyleResource(DEFAULT_THEME_NAME) != null) {
      return DEFAULT_THEME_NAME;
    }
    if (getLocalStyleResource(ALTERNATE_THEME_NAME) != null) {
      return ALTERNATE_THEME_NAME;
    }
    return null;
  }

  public boolean isAppCompatTheme(@NotNull String themeName) {
    StyleResourceValue theme = getLocalStyleResource(themeName);
    return isAppCompatTheme(themeName, theme);
  }

  public static boolean themeExists(@NotNull Configuration configuration, @NotNull String themeName) {
    return getStyleResource(configuration, themeName) != null;
  }

  public boolean isLocalTheme(@NotNull String themeName) {
    return getLocalStyleResource(themeName) != null;
  }

  public static Boolean hasActionBar(@NotNull Configuration configuration, @NotNull String themeName) {
    StyleResourceValue theme = getStyleResource(configuration, themeName);
    if (theme == null) {
      return null;
    }
    ResourceResolver resolver = configuration.getResourceResolver();
    assert resolver != null;
    ResourceValue value = resolver.findItemInStyle(theme, "windowActionBar", theme.isFramework());
    if (value == null || value.getValue() == null) {
      return true;
    }
    return SdkConstants.VALUE_TRUE.equals(value.getValue());
  }

  @Nullable
  private static StyleResourceValue getStyleResource(@NotNull Configuration configuration, @NotNull String themeName) {
    configuration.setTheme(themeName);
    ResourceResolver resolver = configuration.getResourceResolver();
    assert resolver != null;
    boolean isFramework = themeName.startsWith(SdkConstants.PREFIX_ANDROID);
    if (isFramework) {
      themeName = themeName.substring(SdkConstants.PREFIX_ANDROID.length());
    }
    return resolver.getStyle(themeName, isFramework);
  }

  @Nullable
  private StyleResourceValue getLocalStyleResource(@Nullable String theme) {
    if (theme == null) {
      return null;
    }
    List<ResourceItem> items = myRepository.getResourceItem(ResourceType.STYLE, theme);
    if (items == null || items.isEmpty()) {
      return null;
    }
    return (StyleResourceValue)items.get(0).getResourceValue(false);
  }

  private boolean isAppCompatTheme(@NotNull String themeName, @Nullable StyleResourceValue localTheme) {
    while (localTheme != null) {
      String parentThemeName = localTheme.getParentStyle();
      if (parentThemeName == null) {
        if (themeName.lastIndexOf('.') > 0) {
          parentThemeName = themeName.substring(0, themeName.lastIndexOf('.'));
        }
        else {
          return false;
        }
      }
      themeName = parentThemeName;
      localTheme = getLocalStyleResource(themeName);
    }
    return themeName.startsWith(APP_COMPAT);
  }
}
