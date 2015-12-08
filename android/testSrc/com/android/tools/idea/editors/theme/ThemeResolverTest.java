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

import com.android.ide.common.rendering.api.ItemResourceValue;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredElement;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredThemeEditorStyle;
import com.android.tools.idea.rendering.multi.CompatibilityRenderTarget;
import com.google.common.collect.Iterables;
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

    assertNull(themeResolver.getTheme("Theme.Holo.Light")); // It's system theme and we're not specifying namespace so it will fail.

    ConfiguredThemeEditorStyle theme = themeResolver.getTheme("android:Theme.Holo.Light");
    assertEquals("Theme.Holo.Light", theme.getName());

    assertEquals(themeResolver.getThemesCount(), themeResolver.getFrameworkThemes().size()); // Only framework themes.
    assertEmpty(themeResolver.getLocalThemes());

    assertNull("Theme resolver shouldn't resolve styles", themeResolver.getTheme("android:TextAppearance"));
  }

  public void testLocalThemes() throws IOException {
    VirtualFile myLayout = myFixture.copyFileToProject("themeEditor/layout.xml", "res/layout/layout.xml");
    VirtualFile myStyleFile = myFixture.copyFileToProject("themeEditor/styles.xml", "res/values/styles.xml");

    ConfigurationManager configurationManager = myFacet.getConfigurationManager();
    Configuration configuration = configurationManager.getConfiguration(myLayout);
    ThemeResolver themeResolver = new ThemeResolver(configuration);

    assertEquals(1, themeResolver.getLocalThemes().size()); // We don't have any libraries so this will only include the project theme
    assertEquals(0, themeResolver.getExternalLibraryThemes().size()); // No library themes

    assertNull("The theme is an app theme and shouldn't be returned for the android namespace",
               themeResolver.getTheme("android:Theme.MyTheme"));

    ConfiguredThemeEditorStyle theme = themeResolver.getTheme("Theme.MyTheme");
    assertEquals("Theme.MyTheme", theme.getName());
    assertEquals("Theme", theme.getParent().getName());

    assertEquals(1, theme.getConfiguredValues().size());
    ConfiguredElement<ItemResourceValue> value = Iterables.get(theme.getConfiguredValues(), 0);
    assertEquals("windowBackground", value.getElement().getName());
    assertEquals("@drawable/pic", value.getElement().getValue());

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

  /** Check that, after a configuration update, the resolver updates the list of themes */
  public void testConfigurationUpdate() {
    myFixture.copyFileToProject("themeEditor/attributeResolution/styles-v17.xml", "res/values-v17/styles.xml");
    myFixture.copyFileToProject("themeEditor/attributeResolution/styles-v19.xml", "res/values-v19/styles.xml");
    VirtualFile file = myFixture.copyFileToProject("themeEditor/attributeResolution/styles-v20.xml", "res/values-v20/styles.xml");

    ConfigurationManager configurationManager = myFacet.getConfigurationManager();
    Configuration configuration = configurationManager.getConfiguration(file);

    ThemeEditorContext context = new ThemeEditorContext(configuration);
    ThemeResolver resolver = context.getThemeResolver();
    assertNotNull(resolver.getTheme("V20OnlyTheme"));
    assertNotNull(resolver.getTheme("V19OnlyTheme"));
    assertNotNull(resolver.getTheme("V17OnlyTheme"));

    // Set API level 17 and check that only the V17 theme can be resolved
    //noinspection ConstantConditions
    configuration
      .setTarget(new CompatibilityRenderTarget(configurationManager.getHighestApiTarget(), 17, configurationManager.getHighestApiTarget()));
    resolver = context.getThemeResolver();
    assertNull(resolver.getTheme("V20OnlyTheme"));
    assertNull(resolver.getTheme("V19OnlyTheme"));
    assertNotNull(resolver.getTheme("V17OnlyTheme"));
  }
}