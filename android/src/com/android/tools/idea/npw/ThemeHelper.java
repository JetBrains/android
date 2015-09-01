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

import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.res2.ResourceItem;
import com.android.resources.ResourceType;
import com.android.tools.idea.rendering.LocalResourceRepository;
import com.android.tools.idea.rendering.ModuleResourceRepository;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Theme utility class for use with templates.
 */
public class ThemeHelper {
  private static final String DEFAULT_THEME_NAME = "AppTheme";
  private static final String APP_COMPAT = "Theme.AppCompat.";

  private Module myModule;
  private LocalResourceRepository myRepository;

  public ThemeHelper(@NotNull Module module) {
    myModule = module;
    myRepository = ModuleResourceRepository.getModuleResources(module, true);
  }

  public Boolean hasDefaultAppCompatTheme() {
    StyleResourceValue theme = getLocalStyleResource(DEFAULT_THEME_NAME);
    if (theme == null) {
      return null;
    }
    return isAppCompatTheme(DEFAULT_THEME_NAME, theme);
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
