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
package com.android.tools.idea.templates;

import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.npw.ThemeHelper;
import com.google.common.collect.Maps;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import freemarker.template.TemplateMethodModelEx;
import freemarker.template.TemplateModelException;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Map;

public class FmGetApplicationThemeMethod implements TemplateMethodModelEx {
  private final Map<String, Object> myParamMap;

  public FmGetApplicationThemeMethod(Map<String, Object> paramMap) {
    myParamMap = paramMap;
  }

  @Override
  public Object exec(List arguments) throws TemplateModelException {
    String modulePath = (String)myParamMap.get(TemplateMetadata.ATTR_PROJECT_OUT);
    if (modulePath == null) {
      return null;
    }

    Module module = FmUtil.findModule(modulePath);
    if (module == null) {
      return null;
    }
    ThemeHelper helper = new ThemeHelper(module);
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return null;
    }
    VirtualFile projectFile = module.getProject().getProjectFile();
    if (projectFile == null) {
      return null;
    }
    ConfigurationManager manager = facet.getConfigurationManager();
    Configuration configuration = manager.getConfiguration(projectFile);

    String themeName = helper.getAppThemeName();
    if (themeName == null) {
      return null;
    }

    Map<String, Object> map = Maps.newHashMap();
    map.put("name", themeName);
    map.put("isAppCompat", helper.isAppCompatTheme(themeName));
    Boolean hasActionBar = ThemeHelper.hasActionBar(configuration, themeName);
    addDerivedTheme(map, themeName, "NoActionBar", hasActionBar == Boolean.FALSE, helper, configuration);
    addDerivedTheme(map, themeName, "AppBarOverlay", false, helper, configuration);
    addDerivedTheme(map, themeName, "PopupOverlay", false, helper, configuration);
    return map;
  }

  private static void addDerivedTheme(@NotNull Map<String, Object> map,
                                      @NotNull String themeName,
                                      @NotNull String derivedThemeName,
                                      boolean useBaseThemeAsDerivedTheme,
                                      @NotNull ThemeHelper helper,
                                      @NotNull Configuration configuration) {
    String fullThemeName = useBaseThemeAsDerivedTheme ? themeName : themeName + "." + derivedThemeName;
    boolean exists = ThemeHelper.themeExists(configuration, fullThemeName);
    if (!exists && !helper.isLocalTheme(themeName)) {
      fullThemeName = derivedThemeName;
      exists = helper.isLocalTheme(derivedThemeName);
    }
    map.put("name" + derivedThemeName, fullThemeName);
    map.put("exists" + derivedThemeName, exists);
  }
}
