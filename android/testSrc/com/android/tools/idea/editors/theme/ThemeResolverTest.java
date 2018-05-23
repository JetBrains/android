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

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.StyleItemResourceValue;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredElement;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredThemeEditorStyle;
import com.android.tools.idea.rendering.multi.CompatibilityRenderTarget;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.google.common.collect.Iterables;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestCase;

import java.io.IOException;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ThemeResolverTest extends AndroidTestCase {
  /*
   * The test SDK only includes some resources. It only includes a few incomplete styles.
   */

  public void testFrameworkThemeRead() {
    VirtualFile myLayout = myFixture.copyFileToProject("xmlpull/layout.xml", "res/layout/layout1.xml");
    Configuration configuration = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(myLayout);
    ThemeResolver themeResolver = new ThemeResolver(configuration);

    // It's system theme and we're not specifying namespace so it will fail.
    assertNull(themeResolver.getTheme(ResourceReference.style(ResourceNamespace.RES_AUTO, "Theme.Holo.Light")));

    ConfiguredThemeEditorStyle theme = themeResolver.getTheme(ResourceReference.style(ResourceNamespace.ANDROID, "Theme.Holo.Light"));
    assertEquals("Theme.Holo.Light", theme.getName());

    assertEquals(themeResolver.getThemesCount(), themeResolver.getFrameworkThemes().size()); // Only framework themes.
    assertEmpty(themeResolver.getLocalThemes());

    assertNull("Theme resolver shouldn't resolve styles",
               themeResolver.getTheme(ResourceReference.style(ResourceNamespace.ANDROID, "TextAppearance")));
  }

  public void testLocalThemes() throws IOException {
    doTestLocalThemes();
  }

  public void testLocalThemesNamespaced() throws IOException {
    enableNamespacing("com.example.app");
    doTestLocalThemes();
  }

  private void doTestLocalThemes() throws IOException {
    VirtualFile myLayout = myFixture.copyFileToProject("themeEditor/layout.xml", "res/layout/layout.xml");
    VirtualFile myStyleFile = myFixture.copyFileToProject("themeEditor/styles.xml", "res/values/styles.xml");

    ConfigurationManager configurationManager = ConfigurationManager.getOrCreateInstance(myModule);
    Configuration configuration = configurationManager.getConfiguration(myLayout);
    ThemeResolver themeResolver = new ThemeResolver(configuration);

    assertEquals(1, themeResolver.getLocalThemes().size()); // There are no libraries, so this will only include the project theme.
    assertEquals(0, themeResolver.getExternalLibraryThemes().size()); // No library themes.

    assertNull("The theme is an app theme and shouldn't be returned for the android namespace",
               themeResolver.getTheme(ResourceReference.style(ResourceNamespace.ANDROID, "Theme.MyTheme")));

    ResourceNamespace moduleNamespace = ResourceRepositoryManager.getOrCreateInstance(myModule).getNamespace();
    ConfiguredThemeEditorStyle theme = themeResolver.getTheme(ResourceReference.style(moduleNamespace, "Theme.MyTheme"));
    assertNotNull(theme);
    assertEquals("Theme.MyTheme", theme.getName());
    assertEquals("Theme", theme.getParent().getName());

    assertEquals(1, theme.getConfiguredValues().size());
    ConfiguredElement<StyleItemResourceValue> value = Iterables.get(theme.getConfiguredValues(), 0);
    assertEquals("windowBackground", value.getElement().getAttr().getName());
    assertEquals("@drawable/pic", value.getElement().getValue());

    // Modify a value.
    theme.setValue("android:windowBackground", "@drawable/other");
    FileDocumentManager.getInstance().saveAllDocuments();
    assertFalse(new String(myStyleFile.contentsToByteArray(), UTF_8).contains("@drawable/pic"));
    assertTrue(new String(myStyleFile.contentsToByteArray(), UTF_8).contains("@drawable/other"));

    // Add a value.
    theme.setValue("android:windowBackground2", "@drawable/second_background");
    FileDocumentManager.getInstance().saveAllDocuments();
    assertTrue(new String(myStyleFile.contentsToByteArray(), UTF_8).contains("@drawable/other"));
    assertTrue(new String(myStyleFile.contentsToByteArray(), UTF_8).contains("@drawable/second_background"));
  }

  /** Check that, after a configuration update, the resolver updates the list of themes */
  public void testConfigurationUpdate() {
    myFixture.copyFileToProject("themeEditor/attributeResolution/styles-v17.xml", "res/values-v17/styles.xml");
    myFixture.copyFileToProject("themeEditor/attributeResolution/styles-v19.xml", "res/values-v19/styles.xml");
    VirtualFile file = myFixture.copyFileToProject("themeEditor/attributeResolution/styles-v20.xml", "res/values-v20/styles.xml");

    ConfigurationManager configurationManager = ConfigurationManager.getOrCreateInstance(myModule);
    Configuration configuration = configurationManager.getConfiguration(file);
    ResourceNamespace moduleNamespace = ResourceRepositoryManager.getOrCreateInstance(myModule).getNamespace();

    ThemeEditorContext context = new ThemeEditorContext(configuration);
    ThemeResolver resolver = context.getThemeResolver();
    assertNotNull(resolver.getTheme(ResourceReference.style(moduleNamespace, "V20OnlyTheme")));
    assertNotNull(resolver.getTheme(ResourceReference.style(moduleNamespace, "V19OnlyTheme")));
    assertNotNull(resolver.getTheme(ResourceReference.style(moduleNamespace, "V17OnlyTheme")));

    // Set API level 17 and check that only the V17 theme can be resolved
    //noinspection ConstantConditions
    configuration
      .setTarget(new CompatibilityRenderTarget(configurationManager.getHighestApiTarget(), 17, null));
    context = new ThemeEditorContext(configuration);
    resolver = context.getThemeResolver();
    assertNull(resolver.getTheme(ResourceReference.style(moduleNamespace, "V20OnlyTheme")));
    assertNull(resolver.getTheme(ResourceReference.style(moduleNamespace, "V19OnlyTheme")));
    assertNotNull(resolver.getTheme(ResourceReference.style(moduleNamespace, "V17OnlyTheme")));
  }
}
