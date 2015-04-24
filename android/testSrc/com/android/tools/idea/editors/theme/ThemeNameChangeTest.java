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
import com.android.tools.idea.editors.theme.datamodels.ThemeEditorStyle;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestCase;

import java.util.Collection;

/**
 * Tests the theme name changing feature in the Theme Editor
 */
public class ThemeNameChangeTest extends AndroidTestCase {

  /**
   * Tests that changing the name of a theme propagates to that theme's children
   */
  public void testThemeNameChange() {
    VirtualFile myLayout = myFixture.copyFileToProject("themeEditor/layout.xml", "res/layout/layout.xml");
    myFixture.copyFileToProject("themeEditor/themeNameChangeStyles.xml", "res/values/styles.xml");

    ConfigurationManager configurationManager = myFacet.getConfigurationManager();
    Configuration configuration = configurationManager.getConfiguration(myLayout);
    ThemeResolver themeResolver = new ThemeResolver(configuration);
    ThemeEditorStyle theme = themeResolver.getTheme("@style/TestTheme");

    assertNotNull(theme);
    theme.setName("NewName");

    configuration.setTheme(null);
    themeResolver = new ThemeResolver(configuration);

    // Checks the theme with the old name does not exist anymore
    theme = themeResolver.getTheme("@style/TestTheme");
    assertNull(theme);

    // Checks the theme with the new name exists
    theme = themeResolver.getTheme("@style/NewName");
    assertNotNull(theme);
    assertTrue(themeResolver.isTheme(theme));

    // Checks the children of the theme have been updated
    ThemeEditorStyle childTheme = themeResolver.getTheme("@style/NewChildTheme");
    assertNotNull(childTheme);
    assertTrue(theme.equals(childTheme.getParent()));

    // Checks that the renaming correctly handles the Parent.Child naming convention
    childTheme = themeResolver.getTheme("@style/TestTheme.Child");
    assertNull(childTheme);
    childTheme = themeResolver.getTheme("@style/NewName.Child");
    assertNotNull(childTheme);

    childTheme = themeResolver.getTheme("@style/TestTheme.OtherChild");
    // The name of this child theme is not modified
    // Because it explicitly specifies its parent in the xml file
    // So the Parent.Child convention is ignored in that case
    assertNotNull(childTheme);
    assertTrue(theme.equals(childTheme.getParent()));
  }

  /**
   * Tests that nothing happens when changing a theme's name to its current name
   */
  public void testNoChange() {
    VirtualFile myLayout = myFixture.copyFileToProject("themeEditor/layout.xml", "res/layout/layout.xml");
    myFixture.copyFileToProject("themeEditor/themeNameChangeStyles.xml", "res/values/styles.xml");

    ConfigurationManager configurationManager = myFacet.getConfigurationManager();
    Configuration configuration = configurationManager.getConfiguration(myLayout);
    ThemeResolver themeResolver = new ThemeResolver(configuration);
    ThemeEditorStyle theme = themeResolver.getTheme("@style/TestTheme");

    assertNotNull(theme);
    theme.setName("TestTheme");

    configuration.setTheme(null);
    ThemeResolver newThemeResolver = new ThemeResolver(configuration);
    Collection<ThemeEditorStyle> oldThemes = themeResolver.getThemes();
    Collection<ThemeEditorStyle> newThemes = newThemeResolver.getThemes();
    assertTrue(oldThemes.size() == newThemes.size());
    for (ThemeEditorStyle oldTheme : oldThemes) {
      assertTrue(newThemes.contains(oldTheme));
    }
  }
}
