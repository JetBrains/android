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

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ItemResourceValue;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredElement;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredThemeEditorStyle;
import com.android.tools.idea.rendering.multi.CompatibilityRenderTarget;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ConfiguredThemeEditorStyleTest extends AndroidTestCase {

  public ConfiguredThemeEditorStyleTest() {
    super(false);
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.copyFileToProject("themeEditor/manifestWithApi.xml", SdkConstants.FN_ANDROID_MANIFEST_XML);
  }

  @Override
  protected boolean requireRecentSdk() {
    return true;
  }

  public void testGetStyleResourceUrl() {
    VirtualFile myFile = myFixture.copyFileToProject("themeEditor/styles_1.xml", "res/values/styles.xml");
    myFixture.copyFileToProject("themeEditor/attrs.xml", "res/values/attrs.xml");

    Configuration configuration = myFacet.getConfigurationManager().getConfiguration(myFile);

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

    Configuration configuration = myFacet.getConfigurationManager().getConfiguration(myFile);

    ThemeResolver themeResolver = new ThemeResolver(configuration);
    ConfiguredThemeEditorStyle theme = themeResolver.getTheme("AppTheme");
    assertNotNull(theme);
    ConfiguredThemeEditorStyle parent = theme.getParent();
    assertNotNull(parent);

    FolderConfiguration defaultConfig = new FolderConfiguration();
    ConfiguredElement<ItemResourceValue> hasItem =
      ConfiguredElement.create(defaultConfig, new ItemResourceValue("myColor", false, "?android:attr/colorBackground", false));
    ConfiguredElement<ItemResourceValue> hasNotItem =
      ConfiguredElement.create(defaultConfig, new ItemResourceValue("myHasNot", false, "?android:attr/colorBackground", false));
    ConfiguredElement<ItemResourceValue> hasInParent =
      ConfiguredElement.create(defaultConfig, new ItemResourceValue("editTextStyle", true, "?android:attr/colorBackground", true));
    assertTrue(theme.hasItem(new EditedStyleItem(hasItem, theme)));
    assertFalse(theme.hasItem(new EditedStyleItem(hasNotItem, theme)));
    assertTrue(theme.getParent().hasItem(new EditedStyleItem(hasInParent, parent)));
    assertFalse(theme.hasItem(new EditedStyleItem(hasInParent, parent)));
  }

  private void doTestForParentApi(@NotNull String newParent, @NotNull String afterDirectoryName) {
    VirtualFile virtualFile = myFixture.copyFileToProject("themeEditor/apiTestBefore/stylesApi.xml", "res/values/styles.xml");
    myFixture.copyFileToProject("themeEditor/apiTestBefore/stylesApi-v14.xml", "res/values-v14/styles.xml");
    myFixture.copyFileToProject("themeEditor/apiTestBefore/stylesApi-v19.xml", "res/values-v19/styles.xml");
    myFixture.copyFileToProject("themeEditor/apiTestBefore/stylesApi-v21.xml", "res/values-v21/styles.xml");

    ConfigurationManager configurationManager = myFacet.getConfigurationManager();
    Configuration configuration = configurationManager.getConfiguration(virtualFile);
    ThemeResolver themeResolver = new ThemeResolver(configuration);
    ConfiguredThemeEditorStyle theme = themeResolver.getTheme("Theme.MyTheme");

    assertNotNull(theme);
    theme.setParent(newParent);

    myFixture.checkResultByFile("res/values/styles.xml", "themeEditor/" + afterDirectoryName + "/stylesApi.xml", true);
    myFixture.checkResultByFile("res/values-v14/styles.xml", "themeEditor/" + afterDirectoryName + "/stylesApi-v14.xml", true);
    myFixture.checkResultByFile("res/values-v19/styles.xml", "themeEditor/" + afterDirectoryName + "/stylesApi-v19.xml", true);
    myFixture.checkResultByFile("res/values-v21/styles.xml", "themeEditor/" + afterDirectoryName + "/stylesApi-v21.xml", true);
  }

  /**
   * Test setting a low-api attributes and parent in a theme defined only in higher api files
   */
  public void testSettingInHighApiTheme() {
    VirtualFile virtualFile = myFixture.copyFileToProject("themeEditor/apiTestBefore/stylesApi.xml", "res/values/styles.xml");
    myFixture.copyFileToProject("themeEditor/apiTestBefore/stylesApi-v14.xml", "res/values-v14/styles.xml");
    myFixture.copyFileToProject("themeEditor/apiTestBefore/stylesApi-v19.xml", "res/values-v19/styles.xml");
    myFixture.copyFileToProject("themeEditor/apiTestBefore/stylesApi-v21.xml", "res/values-v21/styles.xml");

    ConfigurationManager configurationManager = myFacet.getConfigurationManager();
    Configuration configuration = configurationManager.getConfiguration(virtualFile);
    ThemeResolver themeResolver = new ThemeResolver(configuration);
    ConfiguredThemeEditorStyle theme = themeResolver.getTheme("Theme.MyOtherTheme");

    assertNotNull(theme);
    theme.setValue("android:windowIsFloating", "holo_purple");
    theme.setValue("android:actionBarDivider", "myValue");
    theme.setParent("android:Theme.Holo.Light.DarkActionBar");


    myFixture.checkResultByFile("res/values/styles.xml", "themeEditor/apiTestAfter8/stylesApi.xml", true);
    myFixture.checkResultByFile("res/values-v14/styles.xml", "themeEditor/apiTestAfter8/stylesApi-v14.xml", true);
    myFixture.checkResultByFile("res/values-v19/styles.xml", "themeEditor/apiTestAfter8/stylesApi-v19.xml", true);
    myFixture.checkResultByFile("res/values-v21/styles.xml", "themeEditor/apiTestAfter8/stylesApi-v21.xml", true);
  }

  /**
   * Tests setting a non-framework parent
   */
  public void testNonFrameworkParent() {
    doTestForParentApi("MyStyle", "apiParentTestAfter1");
  }

  /**
   * Tests setting a parent defined for api < projectMinApi
   */
  public void testSmallApiParent() {
    doTestForParentApi("android:Theme.Light", "apiParentTestAfter2");
  }

  /**
   * Tests setting a parent with api = projectMinApi
   */
  public void testMinApiParent() {
    doTestForParentApi("android:Theme.Holo", "apiParentTestAfter3");
  }

  /**
   * Tests setting a parent with api that has no associated values folder
   */
  public void testHighNewApiParent() {
    doTestForParentApi("android:Theme.Holo.NoActionBar.Overscan", "apiParentTestAfter4");
    myFixture.checkResultByFile("res/values-v18/styles.xml", "themeEditor/apiParentTestAfter4/stylesApi-v18.xml", true);
  }

  /**
   * Tests setting a parent with api that has an associated values folder
   */
  public void testHighExistingApiParent() {
    doTestForParentApi("android:Theme.Holo.NoActionBar.TranslucentDecor", "apiParentTestAfter5");
  }

  /**
   * Tests the isPublic() method
   */
  public void testIsPublic() {
    VirtualFile myFile = myFixture.copyFileToProject("themeEditor/styles_1.xml", "res/values/styles.xml");

    Configuration configuration = myFacet.getConfigurationManager().getConfiguration(myFile);

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

  private void checkSetValue(VirtualFile file, ItemResourceValue item, String... answerFolders) {
    Configuration configuration = myFacet.getConfigurationManager().getConfiguration(file);
    ThemeResolver themeResolver = new ThemeResolver(configuration);
    ConfiguredThemeEditorStyle style = themeResolver.getTheme("AppTheme");
    assertNotNull(style);
    style.setValue(ResolutionUtils.getQualifiedItemName(item), item.getValue());
    // LocalResourceRepositories haven't updated yet
    myFacet.refreshResources();

    HashSet<String> modifiedFolders = new HashSet<String>(Arrays.asList(answerFolders));
    int valuesFound = 0;
    for (ConfiguredElement<ItemResourceValue> value : style.getConfiguredValues()) {
      if (item.equals(value.getElement())) {
        valuesFound++;
        assertTrue(modifiedFolders.contains(value.getConfiguration().getUniqueKey()));
      }
    }
    assertEquals(modifiedFolders.size(), valuesFound);
  }

  /**
   * Tests setValue method for following cases:
   * values, values-v21, values-night, value-port, values-port-v21
   * setValue("colorAccent", "#000000")
   */
  public void testSetValue() {
    VirtualFile file = myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles_2.xml", "res/values/styles.xml");
    myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles_2.xml", "res/values-v21/styles.xml");
    myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles_2.xml", "res/values-night/styles.xml");
    myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles_2.xml", "res/values-port/styles.xml");
    myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles_2.xml", "res/values-port-v21/styles.xml");

    ItemResourceValue item = new ItemResourceValue("colorAccent", false, "#000000", false);
    checkSetValue(file, item, "", "-v21", "-night", "-port", "-port-v21");
  }

  /**
   * Tests setValue method for following cases:
   * values, values-v21, values-night, value-port, values-port-v21
   * setValue("android:colorAccent", "#000000"), android:colorAccent is defined in API 21
   */
  public void testSetValueAndroidAttribute() {
    VirtualFile file = myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles_2.xml", "res/values/styles.xml");
    myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles_3.xml", "res/values-v21/styles.xml");
    myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles_2.xml", "res/values-night/styles.xml");
    myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles_2.xml", "res/values-port/styles.xml");
    myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles_2.xml", "res/values-port-v21/styles.xml");

    ItemResourceValue item = new ItemResourceValue("colorAccent", true, "#000000", false);
    checkSetValue(file, item, "-night-v21", "-v21", "-port-v21");
  }

  /**
   * Tests setValue method for following cases:
   * values, values-v21, values-night, value-port, values-port-v21
   * setValue("android:colorFocusedHighlight", "?android:attr/colorAccent"), colorAccent - v21, colorFocusedHighlight - v14
   */
  public void testSetValueAndroidAttributeValue() {
    VirtualFile file = myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles_2.xml", "res/values/styles.xml");
    myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles_3.xml", "res/values-v21/styles.xml");
    myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles_2.xml", "res/values-night/styles.xml");
    myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles_2.xml", "res/values-port/styles.xml");
    myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles_2.xml", "res/values-port-v21/styles.xml");

    ItemResourceValue item = new ItemResourceValue("colorAccent", true, "?android:attr/colorAccent", false);
    checkSetValue(file, item, "-night-v21", "-v21", "-port-v21");
  }

  /**
   * Tests setValue method for copying from right FolderConfiguration
   * Tests following cases:
   * values, values-v17, values-v19, values-v22
   * setValue("android:colorAccent", "#000000");
   * Should create values-v21 based on values-v19, and modify values-v22
   */
  public void testSetValueCopy() {
    VirtualFile file = myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles_3.xml", "res/values/styles.xml");
    myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles_3.xml", "res/values-v17/styles.xml");
    myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles_4.xml", "res/values-v19/styles.xml");
    myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles_3.xml", "res/values-v22/styles.xml");
    ItemResourceValue item = new ItemResourceValue("colorAccent", true, "#000000", false);
    checkSetValue(file, item, "-v21", "-v22");

    myFixture.checkResultByFile("res/values-v21/styles.xml", "themeEditor/themeEditorStyle/styles_4_modified.xml", true);
    myFixture.checkResultByFile("res/values-v22/styles.xml", "themeEditor/themeEditorStyle/styles_3_modified.xml", true);
  }

  /**
   * Tests setValue method for an attribute having api level below than Minimum Sdk
   * Tests following cases:
   * values, values-v17, values-v19, values-v22
   * setValue("android:colorBackgroundCacheHint", "#000000");
   */
  public void testSetValueMinSdk() {
    VirtualFile file = myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles_3.xml", "res/values/styles.xml");
    myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles_3.xml", "res/values-v17/styles.xml");
    myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles_4.xml", "res/values-v19/styles.xml");
    myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles_3.xml", "res/values-v22/styles.xml");
    ItemResourceValue item = new ItemResourceValue("colorBackgroundCacheHint", true, "#000000", false);
    checkSetValue(file, item, "", "-v17", "-v19", "-v22");
  }

  private void checkSetParent(VirtualFile file, String newParent, String... answerFolders) {
    Configuration configuration = myFacet.getConfigurationManager().getConfiguration(file);
    ThemeResolver themeResolver = new ThemeResolver(configuration);

    ConfiguredThemeEditorStyle theme = themeResolver.getTheme("AppTheme");
    assertNotNull(theme);
    theme.setParent(newParent);
    // LocalResourceRepositories haven't updated yet
    myFacet.refreshResources();

    HashSet<String> modifiedFolders = new HashSet<String>(Arrays.asList(answerFolders));
    int valuesFound = 0;
    for (ConfiguredElement<String> value : theme.getParentNames()) {
      if (newParent.equals(value.getElement())) {
        valuesFound++;
        assertTrue(modifiedFolders.contains(value.getConfiguration().getUniqueKey()));
      }
    }
    assertEquals(modifiedFolders.size(), valuesFound);
  }

  /**
   * Tests {@link ConfiguredThemeEditorStyle#setParent(String)}
   * Tests following cases:
   * values, values-v21, values-night, value-port, values-port-v21
   * setParent("newParent")
   */
  public void testSetParent() {
    VirtualFile file = myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles_2.xml", "res/values/styles.xml");
    myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles_2.xml", "res/values-v21/styles.xml");
    myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles_2.xml", "res/values-night/styles.xml");
    myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles_2.xml", "res/values-port/styles.xml");
    myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles_2.xml", "res/values-port-v21/styles.xml");
    checkSetParent(file, "newParent", "", "-v21", "-night", "-port", "-port-v21");
  }

  /**
   * Tests {@link ConfiguredThemeEditorStyle#setParent(String)}
   * Tests following cases:
   * values, values-v21, values-night, value-port, values-port-v21
   * setParent("android:Theme.Material"), where android:Theme.Material is defined in Api Level 21
   */
  public void testSetParentAndroidParent() {
    VirtualFile file = myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles_2.xml", "res/values/styles.xml");
    myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles_3.xml", "res/values-v21/styles.xml");
    myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles_2.xml", "res/values-night/styles.xml");
    myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles_2.xml", "res/values-port/styles.xml");
    myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles_2.xml", "res/values-port-v21/styles.xml");
    checkSetParent(file, "android:Theme.Material", "-night-v21", "-v21", "-port-v21");
  }

  /**
   * Tests {@link ConfiguredThemeEditorStyle#setParent(String)} for copying from right folder.
   * Tests following cases:
   * values, values-v17, values-v19, values-v22
   * setParent("android:Theme.Material"), where android:Theme.Material is defined in Api Level 21
   */
  public void testSetParentCopy() {
    VirtualFile file = myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles_3.xml", "res/values/styles.xml");
    myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles_3.xml", "res/values-v17/styles.xml");
    myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles_4.xml", "res/values-v19/styles.xml");
    myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles_3.xml", "res/values-v22/styles.xml");
    checkSetParent(file, "android:Theme.Material", "-v21", "-v22");

    myFixture.checkResultByFile("res/values-v21/styles.xml", "themeEditor/themeEditorStyle/styles_4_parent_modified.xml", true);
    myFixture.checkResultByFile("res/values-v22/styles.xml", "themeEditor/themeEditorStyle/styles_3_parent_modified.xml", true);
  }

  public void testGetParentNames() {
    myFixture.copyFileToProject("themeEditor/attributeResolution/styles_base.xml", "res/values/styles.xml");
    myFixture.copyFileToProject("themeEditor/attributeResolution/styles-v17.xml", "res/values-v17/styles.xml");
    myFixture.copyFileToProject("themeEditor/attributeResolution/styles-v19.xml", "res/values-v19/styles.xml");
    VirtualFile file = myFixture.copyFileToProject("themeEditor/attributeResolution/styles-v20.xml", "res/values-v20/styles.xml");

    ConfigurationManager configurationManager = myFacet.getConfigurationManager();
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

    ConfigurationManager configurationManager = myFacet.getConfigurationManager();
    Configuration configuration = configurationManager.getConfiguration(virtualFile);
    ThemeResolver resolver = new ThemeResolver(configuration);
    ConfiguredThemeEditorStyle theme = resolver.getTheme("ATheme.Red");
    assertNotNull(theme);

    HashSet<String> parents = Sets.newHashSet();
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
      addModuleWithAndroidFacet(projectBuilder, modules, "moduleA", true);
    }
    else if (testName.equals("getConfiguredValues")) {
      addModuleWithAndroidFacet(projectBuilder, modules, "moduleA", true);
      addModuleWithAndroidFacet(projectBuilder, modules, "moduleB", true);
    }
  }

  public void testGetParentNamesWithDependency() {
    VirtualFile virtualFile = myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles.xml", "res/values/styles.xml");
    myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles_1.xml", "additionalModules/moduleA/res/values-v19/styles.xml");

    ConfigurationManager configurationManager = myFacet.getConfigurationManager();
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

    ConfigurationManager configurationManager = myFacet.getConfigurationManager();
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
    ConfigurationManager configurationManager = myFacet.getConfigurationManager();
    Configuration configuration = configurationManager.getConfiguration(virtualFile);
    ThemeResolver resolver = new ThemeResolver(configuration);
    ConfiguredThemeEditorStyle theme = resolver.getTheme("AppTheme");
    assertNotNull(theme);
    assertEquals(3, theme.getConfiguredValues().size());
  }
}
