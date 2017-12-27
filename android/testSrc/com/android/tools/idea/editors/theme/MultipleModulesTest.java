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
package com.android.tools.idea.editors.theme;

import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredThemeEditorStyle;
import com.android.tools.idea.editors.theme.datamodels.ThemeEditorStyle;
import com.google.common.collect.Collections2;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_LIBRARY;

public class MultipleModulesTest extends AndroidTestCase {

  // Additional module names
  public static final String USEDLIBRARY = "usedlibrary";
  public static final String UNUSEDLIBRARY = "unusedlibrary";

  @Override
  protected void configureAdditionalModules(@NotNull TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder,
                                            @NotNull List<MyAdditionalModuleData> modules) {
    addModuleWithAndroidFacet(projectBuilder, modules, USEDLIBRARY, PROJECT_TYPE_LIBRARY, true);
    addModuleWithAndroidFacet(projectBuilder, modules, UNUSEDLIBRARY, PROJECT_TYPE_LIBRARY, false);
  }

  public void testThemeResolver() {
    final VirtualFile appThemes = myFixture.copyFileToProject("themeEditor/multimodulesProject/app_themes.xml", "res/values/themes.xml");
    myFixture.copyFileToProject("themeEditor/multimodulesProject/unusedlibrary_themes.xml",
                                getAdditionalModulePath(UNUSEDLIBRARY) + "/res/values/themes.xml");
    myFixture.copyFileToProject("themeEditor/multimodulesProject/usedlibrary_themes.xml",
                                getAdditionalModulePath(USEDLIBRARY) + "/res/values/themes.xml");

    // Main theme module
    final Configuration configuration1 = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(appThemes);
    final ThemeResolver resolver1 = new ThemeResolver(configuration1);
    // Available themes should contain from used library, but, more importantly, shouldn't contain themes from unused library (not a dependency)
    assertContainsElements(themeNames(resolver1.getLocalThemes()), "AppTheme", "LibraryDependentTheme", "UsedLibraryTheme");

    // Used library module
    final Configuration configuration2 = ConfigurationManager.getOrCreateInstance(myAdditionalModules.get(0)).getConfiguration(appThemes);
    final ThemeResolver resolver2 = new ThemeResolver(configuration2);
    assertContainsElements(themeNames(resolver2.getLocalThemes()), "UsedLibraryTheme");

    // Unused library module
    final Configuration configuration3 = ConfigurationManager.getOrCreateInstance(myAdditionalModules.get(1)).getConfiguration(appThemes);
    final ThemeResolver resolver3 = new ThemeResolver(configuration3);
    assertContainsElements(themeNames(resolver3.getLocalThemes()), "UnusedLibraryTheme");
  }

  private static Collection<String> themeNames(Collection<ConfiguredThemeEditorStyle> styles) {
    return Collections2.transform(styles, ThemeEditorStyle::getName);
  }
}
