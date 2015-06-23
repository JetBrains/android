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
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.editors.theme.datamodels.ThemeEditorStyle;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestCase;

import java.io.IOException;

public class ThemeResolverTest extends AndroidTestCase {
  /*
   * The test SDK only includes some resources. It only includes a few incomplete styles.
   */

  public void testFrameworkThemeRead() {
    VirtualFile myLayout = myFixture.copyFileToProject("xmlpull/layout.xml", "res/layout/layout1.xml");
    Configuration configuration = myFacet.getConfigurationManager().getConfiguration(myLayout);
    ThemeResolver themeResolver = new ThemeResolver(configuration);

    assertNull(themeResolver.getTheme("@style/Theme.Holo.Light")); // It's system theme and we're not specifying namespace so it will fail.

    ThemeEditorStyle theme = themeResolver.getTheme("@android:style/Theme.Holo.Light");
    assertEquals("Theme.Holo.Light", theme.getName());

    assertEquals(themeResolver.getThemes().size(), themeResolver.getFrameworkThemes().size()); // Only framework themes.
    assertEmpty(themeResolver.getLocalThemes());

    assertNull("Theme resolver shouldn't resolve styles", themeResolver.getTheme("@android:style/TextAppearance"));
  }

  public void testIsTheme() {
    VirtualFile myLayout = myFixture.copyFileToProject("themeEditor/layout.xml", "res/layout/layout.xml");
    Configuration configuration = myFacet.getConfigurationManager().getConfiguration(myLayout);
    ThemeResolver themeResolver = new ThemeResolver(configuration);
    StyleResolver styleResolver = new StyleResolver(configuration);

    ThemeEditorStyle theme = themeResolver.getTheme("@android:style/Theme.Holo.Light");
    ThemeEditorStyle style = styleResolver.getStyle("@android:style/TextAppearance");

    assertFalse(themeResolver.isTheme(style));
    assertTrue(themeResolver.isTheme(theme));
    assertTrue(themeResolver.isTheme(theme.getParent()));
  }

  public void testLocalThemes() throws IOException {
    VirtualFile myLayout = myFixture.copyFileToProject("themeEditor/layout.xml", "res/layout/layout.xml");
    VirtualFile myStyleFile = myFixture.copyFileToProject("themeEditor/styles.xml", "res/values/styles.xml");

    ConfigurationManager configurationManager = myFacet.getConfigurationManager();
    Configuration configuration = configurationManager.getConfiguration(myLayout);
    ThemeResolver themeResolver = new ThemeResolver(configuration);

    assertEquals(1, themeResolver.getLocalThemes().size()); // We don't have any libraries so this will only include the project theme
    assertEquals(2, themeResolver.getProjectThemes().size()); // The Theme.MyTheme + parent Theme
    assertEquals(themeResolver.getThemes().size(), themeResolver.getFrameworkThemes().size() + 1); // One local theme

    assertNull("The theme is an app theme and shouldn't be returned for the android namespace",
               themeResolver.getTheme("@android:style/Theme.MyTheme"));

    ThemeEditorStyle theme = themeResolver.getTheme("@style/Theme.MyTheme");
    assertEquals("Theme.MyTheme", theme.getName());
    assertEquals("Theme", theme.getParent().getName());

    assertEquals(1, theme.getValues().size());
    EditedStyleItem value = theme.getValues().toArray(new EditedStyleItem[0])[0];
    assertEquals("windowBackground", value.getName());
    assertEquals("@drawable/pic", value.getValue());

    // Modify a value.
    theme.setValue("android:windowBackground", "@drawable/other");
    FileDocumentManager.getInstance().saveAllDocuments();
    assertFalse(new String(myStyleFile.contentsToByteArray(), "UTF-8").contains("@drawable/pic"));
    assertTrue(new String(myStyleFile.contentsToByteArray(), "UTF-8").contains("@drawable/other"));

    // Add a value.
    theme.setValue("android:windowBackground2", "@drawable/second_background");
    FileDocumentManager.getInstance().saveAllDocuments();
    assertTrue(new String(myStyleFile.contentsToByteArray(), "UTF-8").contains("@drawable/other"));
    assertTrue(new String(myStyleFile.contentsToByteArray(), "UTF-8").contains("@drawable/second_background"));
  }
}