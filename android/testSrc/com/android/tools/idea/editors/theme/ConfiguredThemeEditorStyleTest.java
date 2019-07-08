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

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_LIBRARY;
import static com.android.ide.common.rendering.api.ResourceNamespace.ANDROID;
import static com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.StyleItemResourceValue;
import com.android.ide.common.rendering.api.StyleItemResourceValueImpl;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredElement;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredThemeEditorStyle;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.rendering.multi.CompatibilityRenderTarget;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

public class ConfiguredThemeEditorStyleTest extends AndroidTestCase {

  @Override
  protected boolean providesCustomManifest() {
    return true;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.copyFileToProject("themeEditor/manifestWithApi.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
  }

  public void testGetStyleResourceUrl() {
    VirtualFile myFile = myFixture.copyFileToProject("themeEditor/styles_1.xml", "res/values/styles.xml");
    myFixture.copyFileToProject("themeEditor/attrs.xml", "res/values/attrs.xml");

    Configuration configuration = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(myFile);

    ThemeResolver themeResolver = new ThemeResolver(configuration);
    ConfiguredThemeEditorStyle theme = themeResolver.getTheme("AppTheme");
    assertNotNull(theme);
    assertEquals("@style/AppTheme", theme.getStyleResourceUrl());
    theme = themeResolver.getTheme("android:Theme");
    assertNotNull(theme);
    assertEquals("@android:style/Theme", theme.getStyleResourceUrl());
  }

  public void testHasItem() {
    VirtualFile myFile = myFixture.copyFileToProject("themeEditor/styles_1.xml", "res/values/styles.xml");
    myFixture.copyFileToProject("themeEditor/attrs.xml", "res/values/attrs.xml");

    Configuration configuration = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(myFile);

    ThemeResolver themeResolver = new ThemeResolver(configuration);
    ConfiguredThemeEditorStyle theme = themeResolver.getTheme("AppTheme");
    assertNotNull(theme);
    ConfiguredThemeEditorStyle parent = theme.getParent();
    assertNotNull(parent);

    FolderConfiguration defaultConfig = new FolderConfiguration();
    ConfiguredElement<StyleItemResourceValue> hasItem =
      ConfiguredElement.create(defaultConfig,
                               new StyleItemResourceValueImpl(RES_AUTO, "myColor", "?android:attr/colorBackground", null));
    ConfiguredElement<StyleItemResourceValue> hasNotItem =
      ConfiguredElement.create(defaultConfig,
                               new StyleItemResourceValueImpl(RES_AUTO, "myHasNot", "?android:attr/colorBackground", null));
    ConfiguredElement<StyleItemResourceValue> hasInParent =
      ConfiguredElement.create(defaultConfig,
                               new StyleItemResourceValueImpl(ANDROID, "editTextStyle", "?android:attr/colorBackground", null));
    assertTrue(theme.hasItem(new EditedStyleItem(hasItem, theme)));
    assertFalse(theme.hasItem(new EditedStyleItem(hasNotItem, theme)));
    assertTrue(theme.getParent().hasItem(new EditedStyleItem(hasInParent, parent)));
    assertFalse(theme.hasItem(new EditedStyleItem(hasInParent, parent)));
  }

  /**
   * Tests the isPublic() method
   */
  public void testIsPublic() {
    VirtualFile myFile = myFixture.copyFileToProject("themeEditor/styles_1.xml", "res/values/styles.xml");

    Configuration configuration = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(myFile);

    ThemeResolver themeResolver = new ThemeResolver(configuration);

    // Non-framework themes are always public
    ConfiguredThemeEditorStyle projectTheme = themeResolver.getTheme("AppTheme");
    assertNotNull(projectTheme);
    assertTrue(projectTheme.isPublic());

    ConfiguredThemeEditorStyle frameworkPublicTheme = themeResolver.getTheme("android:Theme.Material");
    assertNotNull(frameworkPublicTheme);
    assertTrue(frameworkPublicTheme.isPublic());

    ConfiguredThemeEditorStyle frameworkPrivateTheme = themeResolver.getTheme("android:Theme.Material.Dialog.NoFrame");
    assertNotNull(frameworkPrivateTheme);
    assertFalse(frameworkPrivateTheme.isPublic());
  }

  public void testGetParentNames() {
    myFixture.copyFileToProject("themeEditor/attributeResolution/styles_base.xml", "res/values/styles.xml");
    myFixture.copyFileToProject("themeEditor/attributeResolution/styles-v17.xml", "res/values-v17/styles.xml");
    myFixture.copyFileToProject("themeEditor/attributeResolution/styles-v19.xml", "res/values-v19/styles.xml");
    VirtualFile file = myFixture.copyFileToProject("themeEditor/attributeResolution/styles-v20.xml", "res/values-v20/styles.xml");

    ConfigurationManager configurationManager = ConfigurationManager.getOrCreateInstance(myModule);
    Configuration configuration = configurationManager.getConfiguration(file);

    ThemeResolver resolver = new ThemeResolver(configuration);
    ConfiguredThemeEditorStyle theme = resolver.getTheme("AppTheme");
    assertNotNull(theme);

    Collection<ConfiguredElement<String>> parents = theme.getParentNames();
    assertSize(2, parents);
    ImmutableList<String> parentNames = ImmutableList.of(
      Iterables.get(parents, 0).getElement(),
      Iterables.get(parents, 1).getElement()
    );
    assertContainsElements(parentNames, "Base.V20", "Base.V17");

    // Set API 17 and try the same resolution
    //noinspection ConstantConditions
    configuration
      .setTarget(new CompatibilityRenderTarget(configurationManager.getHighestApiTarget(), 17, configurationManager.getHighestApiTarget()));
    parents = theme.getParentNames();
    assertSize(2, parents);
    parentNames = ImmutableList.of(
      Iterables.get(parents, 0).getElement(),
      Iterables.get(parents, 1).getElement()
    );
    assertContainsElements(parentNames, "Base.V20", "Base.V17");
  }

  /**
   * Test {@link ConfiguredThemeEditorStyle#getParentNames()} for a style:
   * <style name="ATheme.Red"></style>, i.e parent defined in the name
   */
  public void testGetParentNamesParenInName() {
    VirtualFile virtualFile = myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles.xml", "res/values/styles.xml");
    myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles_1.xml", "res/values-v19/styles.xml");

    ConfigurationManager configurationManager = ConfigurationManager.getOrCreateInstance(myModule);
    Configuration configuration = configurationManager.getConfiguration(virtualFile);
    ThemeResolver resolver = new ThemeResolver(configuration);
    ConfiguredThemeEditorStyle theme = resolver.getTheme("ATheme.Red");
    assertNotNull(theme);

    HashSet<String> parents = new HashSet<>();
    for (ConfiguredElement<String> parent : theme.getParentNames()) {
      parents.add(parent.getElement());
    }
    assertEquals(2, parents.size());
    assertContainsElements(parents, "ATheme", "BTheme");
  }

  @Override
  protected void configureAdditionalModules(@NotNull TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder,
                                            @NotNull List<MyAdditionalModuleData> modules) {
    final String testName = getTestName(true);

    // Add moduleA for the tests below
    if (testName.equals("getParentNamesWithDependency") || testName.equals("themeOverride")) {
      addModuleWithAndroidFacet(projectBuilder, modules, "moduleA", PROJECT_TYPE_LIBRARY);
    }
    else if (testName.equals("getConfiguredValues")) {
      addModuleWithAndroidFacet(projectBuilder, modules, "moduleA", PROJECT_TYPE_LIBRARY);
      addModuleWithAndroidFacet(projectBuilder, modules, "moduleB", PROJECT_TYPE_LIBRARY);
    }
  }

  public void testGetParentNamesWithDependency() {
    VirtualFile virtualFile = myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles.xml", "res/values/styles.xml");
    myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles_1.xml", "additionalModules/moduleA/res/values-v19/styles.xml");

    ConfigurationManager configurationManager = ConfigurationManager.getOrCreateInstance(myModule);
    Configuration configuration = configurationManager.getConfiguration(virtualFile);
    ThemeResolver resolver = new ThemeResolver(configuration);
    ConfiguredThemeEditorStyle theme = resolver.getTheme("AppTheme");

    assertNotNull(theme);
    assertEquals(2, theme.getParentNames().size());
  }

  /**
   * Test that the main theme will override the one from the library
   */
  public void testThemeOverride() {
    VirtualFile virtualFile = myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles.xml", "res/values/styles.xml");
    myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles_1.xml", "additionalModules/moduleA/res/values/styles.xml");

    ConfigurationManager configurationManager = ConfigurationManager.getOrCreateInstance(myModule);
    Configuration configuration = configurationManager.getConfiguration(virtualFile);
    ThemeResolver resolver = new ThemeResolver(configuration);
    ConfiguredThemeEditorStyle theme = resolver.getTheme("AppTheme");

    assertNotNull(theme);
    assertEquals(1, theme.getParentNames().size());
    // We expect only the main app parent to be available
    assertEquals("ATheme", theme.getParentNames().iterator().next().getElement());
  }

  /**
   * Tests {@link ConfiguredThemeEditorStyle#getConfiguredValues()}
   * Tests values coming from different modules.
   * Dependency used in the test: mainModule -> moduleA, mainModule -> moduleB
   */
  public void testGetConfiguredValues() {
    myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles_4.xml", "additionalModules/moduleB/res/values-v19/styles.xml");
    VirtualFile virtualFile = myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles_3.xml",
                                                          "additionalModules/moduleA/res/values/styles.xml");
    ConfigurationManager configurationManager = ConfigurationManager.getOrCreateInstance(myModule);
    Configuration configuration = configurationManager.getConfiguration(virtualFile);
    ThemeResolver resolver = new ThemeResolver(configuration);
    ConfiguredThemeEditorStyle theme = resolver.getTheme("AppTheme");
    assertNotNull(theme);
    assertEquals(3, theme.getConfiguredValues().size());
  }
}
