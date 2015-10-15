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
import com.android.tools.idea.editors.theme.datamodels.ThemeEditorStyle;
import com.android.tools.idea.rendering.multi.CompatibilityRenderTarget;
import com.android.tools.idea.tests.gui.theme.ThemeEditorTestUtils;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertNotEquals;

public class ThemeEditorStyleTest extends AndroidTestCase {

  public ThemeEditorStyleTest() {
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

  public void testHasItem() {
    VirtualFile myFile = myFixture.copyFileToProject("themeEditor/styles_1.xml", "res/values/styles.xml");
    myFixture.copyFileToProject("themeEditor/attrs.xml", "res/values/attrs.xml");

    Configuration configuration = myFacet.getConfigurationManager().getConfiguration(myFile);

    ThemeResolver themeResolver = new ThemeResolver(configuration);
    ThemeEditorStyle theme = themeResolver.getTheme("@style/AppTheme");
    assertNotNull(theme);
    ThemeEditorStyle parent = theme.getParent();
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

  private void doTestForAttributeValuePairApi(@NotNull String attribute, @NotNull String value, @NotNull String afterDirectoryName) {
    VirtualFile virtualFile = myFixture.copyFileToProject("themeEditor/apiTestBefore/stylesApi.xml", "res/values/styles.xml");
    myFixture.copyFileToProject("themeEditor/apiTestBefore/stylesApi-v14.xml", "res/values-v14/styles.xml");
    myFixture.copyFileToProject("themeEditor/apiTestBefore/stylesApi-v19.xml", "res/values-v19/styles.xml");
    myFixture.copyFileToProject("themeEditor/apiTestBefore/stylesApi-v21.xml", "res/values-v21/styles.xml");

    ConfigurationManager configurationManager = myFacet.getConfigurationManager();
    Configuration configuration = configurationManager.getConfiguration(virtualFile);
    ThemeResolver themeResolver = new ThemeResolver(configuration);
    ThemeEditorStyle theme = themeResolver.getTheme("@style/Theme.MyTheme");

    assertNotNull(theme);
    theme.setValue(attribute, value);

    myFixture.checkResultByFile("res/values/styles.xml", "themeEditor/" + afterDirectoryName + "/stylesApi.xml", true);
    myFixture.checkResultByFile("res/values-v14/styles.xml", "themeEditor/" + afterDirectoryName + "/stylesApi-v14.xml", true);
    myFixture.checkResultByFile("res/values-v19/styles.xml", "themeEditor/" + afterDirectoryName + "/stylesApi-v19.xml", true);
    myFixture.checkResultByFile("res/values-v21/styles.xml", "themeEditor/" + afterDirectoryName + "/stylesApi-v21.xml", true);
  }

  private void doTestForAttributeApi(@NotNull String attribute, @NotNull String afterDirectoryName) {
    doTestForAttributeValuePairApi(attribute, "myValue", afterDirectoryName);
  }

  private void doTestForParentApi(@NotNull String newParent, @NotNull String afterDirectoryName) {
    VirtualFile virtualFile = myFixture.copyFileToProject("themeEditor/apiTestBefore/stylesApi.xml", "res/values/styles.xml");
    myFixture.copyFileToProject("themeEditor/apiTestBefore/stylesApi-v14.xml", "res/values-v14/styles.xml");
    myFixture.copyFileToProject("themeEditor/apiTestBefore/stylesApi-v19.xml", "res/values-v19/styles.xml");
    myFixture.copyFileToProject("themeEditor/apiTestBefore/stylesApi-v21.xml", "res/values-v21/styles.xml");

    ConfigurationManager configurationManager = myFacet.getConfigurationManager();
    Configuration configuration = configurationManager.getConfiguration(virtualFile);
    ThemeResolver themeResolver = new ThemeResolver(configuration);
    ThemeEditorStyle theme = themeResolver.getTheme("@style/Theme.MyTheme");

    assertNotNull(theme);
    theme.setParent(newParent);

    myFixture.checkResultByFile("res/values/styles.xml", "themeEditor/" + afterDirectoryName + "/stylesApi.xml", true);
    myFixture.checkResultByFile("res/values-v14/styles.xml", "themeEditor/" + afterDirectoryName + "/stylesApi-v14.xml", true);
    myFixture.checkResultByFile("res/values-v19/styles.xml", "themeEditor/" + afterDirectoryName + "/stylesApi-v19.xml", true);
    myFixture.checkResultByFile("res/values-v21/styles.xml", "themeEditor/" + afterDirectoryName + "/stylesApi-v21.xml", true);
  }

  private void doTestForValueApi(@NotNull String value, @NotNull String afterDirectoryName) {
    doTestForAttributeValuePairApi("android:colorBackground", value, afterDirectoryName);
  }

  /**
   * Tests setting a non-framework attribute
   */
  public void testNonFrameworkAttribute() {
    doTestForAttributeApi("myAttribute", "apiTestAfter1");
  }

  /**
   * Tests modifying an attribute defined for api < projectMinApi
   */
  public void testSmallApiAttribute() {
    doTestForAttributeApi("android:windowIsFloating", "apiTestAfter2");
  }

  /**
   * Tests setting an attribute = projectMinApi
   */
  public void testMinApiAttribute() {
    doTestForAttributeApi("android:actionBarSize", "apiTestAfter3");
  }

  /**
   * Tests setting an attribute with api that has no associated values folder
   */
  public void testHighNewApiAttribute() {
    doTestForAttributeApi("android:windowOverscan", "apiTestAfter4");
    myFixture.checkResultByFile("res/values-v18/styles.xml", "themeEditor/apiTestAfter4/stylesApi-v18.xml", true);
  }

  /**
   * Tests setting an attribute with api that has an associated values folder
   */
  public void testHighExistingApiAttribute() {
    doTestForAttributeApi("android:windowTranslucentStatus", "apiTestAfter5");
  }

  /**
   * Tests modifying an attribute overridden in values folder with api > attribute api
   * but that is not overridden in values folder corresponding to attribute api.
   * The test should modify the attribute where it is overridden but not override it
   * where it is not.
   */
  public void testModifyingOverriddenAttribute() {
    doTestForAttributeApi("android:checkedTextViewStyle", "apiTestAfter6");
  }

  /**
   * Tests modifying an attribute defined for api > projectMinApi
   */
  public void testModifyingHighApiAttribute() {
    doTestForAttributeApi("android:actionModeStyle", "apiTestAfter7");
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
    ThemeEditorStyle theme = themeResolver.getTheme("@style/Theme.MyOtherTheme");

    assertNotNull(theme);
    theme.setValue("android:windowIsFloating", "holo_purple");
    theme.setValue("android:actionBarDivider", "myValue");
    theme.setParent("@android:style/Theme.Holo.Light.DarkActionBar");


    myFixture.checkResultByFile("res/values/styles.xml", "themeEditor/apiTestAfter8/stylesApi.xml", true);
    myFixture.checkResultByFile("res/values-v14/styles.xml", "themeEditor/apiTestAfter8/stylesApi-v14.xml", true);
    myFixture.checkResultByFile("res/values-v19/styles.xml", "themeEditor/apiTestAfter8/stylesApi-v19.xml", true);
    myFixture.checkResultByFile("res/values-v21/styles.xml", "themeEditor/apiTestAfter8/stylesApi-v21.xml", true);
  }

  /**
   * Tests setting a non-framework parent
   */
  public void testNonFrameworkParent() {
    doTestForParentApi("@style/MyStyle", "apiParentTestAfter1");
  }

  /**
   * Tests setting a parent defined for api < projectMinApi
   */
  public void testSmallApiParent() {
    doTestForParentApi("@android:style/Theme.Light", "apiParentTestAfter2");
  }

  /**
   * Tests setting a parent with api = projectMinApi
   */
  public void testMinApiParent() {
    doTestForParentApi("@android:style/Theme.Holo", "apiParentTestAfter3");
  }

  /**
   * Tests setting a parent with api that has no associated values folder
   */
  public void testHighNewApiParent() {
    doTestForParentApi("@android:style/Theme.Holo.NoActionBar.Overscan", "apiParentTestAfter4");
    myFixture.checkResultByFile("res/values-v18/styles.xml", "themeEditor/apiParentTestAfter4/stylesApi-v18.xml", true);
  }

  /**
   * Tests setting a parent with api that has an associated values folder
   */
  public void testHighExistingApiParent() {
    doTestForParentApi("@android:style/Theme.Holo.NoActionBar.TranslucentDecor", "apiParentTestAfter5");
  }

  /**
   * Tests setting a value defined for api < projectMinApi
   */
  public void testSmallApiValue() {
    doTestForValueApi("@android:color/darker_gray", "apiValueTestAfter1");
  }

  /**
   * Tests setting a value with api = projectMinApi
   */
  public void testMinApiValue() {
    doTestForValueApi("@android:drawable/dialog_holo_dark_frame", "apiValueTestAfter2");
  }

  /**
   * Tests setting a value with api that has no associated values folder
   */
  public void testHighNewApiValue() {
    doTestForValueApi("@android:style/Theme.Holo.NoActionBar.Overscan", "apiValueTestAfter3");
    myFixture.checkResultByFile("res/values-v18/styles.xml", "themeEditor/apiValueTestAfter3/stylesApi-v18.xml", true);
  }

  /**
   * Tests setting a value with api that has an associated values folder
   */
  public void testHighExistingApiValue() {
    doTestForValueApi("@android:style/Theme.Holo.NoActionBar.TranslucentDecor", "apiValueTestAfter4");
  }

  /**
   * Tests with attribute api < value api
   */
  public void testSmallerAttribute() {
    doTestForAttributeValuePairApi("android:mediaRouteButtonStyle", "@android:style/Theme.Holo.NoActionBar.TranslucentDecor",
                                   "apiPairTestAfter1");
  }

  /**
   * Tests with attribute api > value api
   */
  public void testSmallerValue() {
    doTestForAttributeValuePairApi("android:windowOverscan", "@android:color/holo_red_dark", "apiPairTestAfter2");
  }

  /**
   * Tests the isPublic() method
   */
  public void testIsPublic() {
    VirtualFile myFile = myFixture.copyFileToProject("themeEditor/styles_1.xml", "res/values/styles.xml");

    Configuration configuration = myFacet.getConfigurationManager().getConfiguration(myFile);

    ThemeResolver themeResolver = new ThemeResolver(configuration);

    // Non-framework themes are always public
    ThemeEditorStyle projectTheme = themeResolver.getTheme("@style/AppTheme");
    assertNotNull(projectTheme);
    assertTrue(projectTheme.isPublic());

    ThemeEditorStyle frameworkPublicTheme = themeResolver.getTheme("@android:style/Theme.Material");
    assertNotNull(frameworkPublicTheme);
    assertTrue(frameworkPublicTheme.isPublic());

    ThemeEditorStyle frameworkPrivateTheme = themeResolver.getTheme("@android:style/Theme.Material.Dialog.NoFrame");
    assertNotNull(frameworkPrivateTheme);
    assertFalse(frameworkPrivateTheme.isPublic());
  }

  /**
   * Tests attributes present in multiple qualifiers
   */
  public void testQualifiers() {
    myFixture.copyFileToProject("themeEditor/qualifiers/stylesApi-v14.xml", "res/values-v14/styles.xml");
    myFixture.copyFileToProject("themeEditor/qualifiers/stylesApi-v19.xml", "res/values-v19/styles.xml");
    VirtualFile myFile = myFixture.copyFileToProject("themeEditor/qualifiers/stylesApi-v21.xml", "res/values-v21/styles.xml");

    Configuration configuration = myFacet.getConfigurationManager().getConfiguration(myFile);
    assertNotNull(configuration.getTarget());
    configuration.setTarget(new CompatibilityRenderTarget(configuration.getTarget(), 22, null));

    ThemeEditorStyle myTheme = ResolutionUtils.getStyle(configuration, "@style/Theme.MyTheme", null);
    assertNotNull(myTheme);
    Set<String> expectedAttributes = Sets.newHashSet("actionModeStyle", "windowIsFloating", "checkedTextViewStyle");
    for(EditedStyleItem item : ThemeEditorTestUtils.getStyleLocalValues(myTheme)) {
      assertTrue(expectedAttributes.remove(item.getName()));

      if ("windowIsFloating".equals(item.getName())) {
        Set<String> seenConfigurations = Sets.newHashSet();
        // We should have selected the highest
        assertEquals("-v21", item.getSelectedValueConfiguration().getUniqueKey());
        // In the non-selected list we have 2 from the local theme and 1 inherited from @android:style/Theme for "default"
        assertSize(3, item.getNonSelectedItemResourceValues());
        seenConfigurations.add(item.getSelectedValueConfiguration().toString());
        // The other values can not be default or repeated
        for(ConfiguredElement value : item.getNonSelectedItemResourceValues()) {
          String configName = value.getConfiguration().toString();
          assertFalse(seenConfigurations.contains(configName));
          seenConfigurations.add(configName);
        }
      }
      else if ("actionModeStyle".equals(item.getName())) {
        // actionModeStyle is only in two configurations v21 and v14 but it has different values
        assertSize(3, item.getNonSelectedItemResourceValues());
        assertEquals("-v21", item.getSelectedValueConfiguration().getUniqueKey());
        assertEquals("@null", item.getValue());
        assertTrue(Iterables.any(item.getNonSelectedItemResourceValues(), new Predicate<ConfiguredElement<ItemResourceValue>>() {
          @Override
          public boolean apply(@Nullable ConfiguredElement<ItemResourceValue> input) {
            assert input != null;
            return "-v14".equals(input.getConfiguration().getUniqueKey()) &&
                   "@style/ActionModeStyle".equals(input.getElement().getValue());
          }
        }));
      }
    }
    assertEmpty(expectedAttributes);

    // Test with a v14 configuration
    configuration.setTarget(new CompatibilityRenderTarget(configuration.getTarget(), 14, null));
    myTheme = ResolutionUtils.getStyle(configuration, "@style/Theme.MyTheme", null);
    assertNotNull(myTheme);

    Collection<EditedStyleItem> values = ThemeEditorTestUtils.getStyleLocalValues(myTheme);

    assertSize(3, values);
    for(EditedStyleItem item : values) {
      if ("windowIsFloating".equals(item.getName())) {
        // We should have selected the v14
        assertEquals("-v14", item.getSelectedValueConfiguration().getUniqueKey());
        assertSize(3, item.getNonSelectedItemResourceValues());

        for(ConfiguredElement value : item.getNonSelectedItemResourceValues()) {
          assertNotEquals("-v14", value.getConfiguration().getUniqueKey());
        }
      }
    }
  }

  /**
   * Test that setValue only affects the specified folders
   */
  public void testSetValueFolderFiltering() {
    VirtualFile myFile = myFixture.copyFileToProject("themeEditor/styles_1.xml", "res/values/styles.xml");
    myFixture.copyFileToProject("themeEditor/styles_1.xml", "res/values-v14/styles.xml");
    myFixture.copyFileToProject("themeEditor/styles_1.xml", "res/values-land/styles.xml");

    Configuration configuration = myFacet.getConfigurationManager().getConfiguration(myFile);
    ThemeResolver themeResolver = new ThemeResolver(configuration);

    ThemeEditorStyle style = themeResolver.getTheme("@style/AppTheme");
    assertNotNull(style);

    // This should modify not modify the default folder
    //noinspection ConstantConditions
    style.setValue(
      ImmutableList.of(FolderConfiguration.getConfigForQualifierString("land"), FolderConfiguration.getConfigForQualifierString("v14")),
      "myColor", "modified");
    myFixture.checkResultByFile("res/values/styles.xml", "themeEditor/styles_1.xml", true);
    myFixture.checkResultByFile("res/values-v14/styles.xml", "themeEditor/setValueAfter/styles_myColor_modified.xml", true);
    myFixture.checkResultByFile("res/values-land/styles.xml", "themeEditor/setValueAfter/styles_myColor_modified.xml", true);
  }

  /**
   * Test that setParent only affects the specified folders
   */
  public void testSetParent() {
    VirtualFile myFile = myFixture.copyFileToProject("themeEditor/styles_1.xml", "res/values/styles.xml");
    myFixture.copyFileToProject("themeEditor/styles_1.xml", "res/values-v14/styles.xml");
    myFixture.copyFileToProject("themeEditor/styles_1.xml", "res/values-land/styles.xml");

    Configuration configuration = myFacet.getConfigurationManager().getConfiguration(myFile);
    ThemeResolver themeResolver = new ThemeResolver(configuration);

    ThemeEditorStyle style = themeResolver.getTheme("@style/AppTheme");
    assertNotNull(style);

    // This should modify not modify the default folder
    //noinspection ConstantConditions
    style.setParent(
      ImmutableList.of(FolderConfiguration.getConfigForQualifierString("land"), FolderConfiguration.getConfigForQualifierString("v14")),
      "@android:style/Theme.Modified");
    myFixture.checkResultByFile("res/values/styles.xml", "themeEditor/styles_1.xml", true);
    myFixture.checkResultByFile("res/values-v14/styles.xml", "themeEditor/setValueAfter/styles_parent_modified.xml", true);
    myFixture.checkResultByFile("res/values-land/styles.xml", "themeEditor/setValueAfter/styles_parent_modified.xml", true);
  }

  public void testGetParentNames() {
    myFixture.copyFileToProject("themeEditor/attributeResolution/styles_base.xml", "res/values/styles.xml");
    myFixture.copyFileToProject("themeEditor/attributeResolution/styles-v17.xml", "res/values-v17/styles.xml");
    myFixture.copyFileToProject("themeEditor/attributeResolution/styles-v19.xml", "res/values-v19/styles.xml");
    VirtualFile file = myFixture.copyFileToProject("themeEditor/attributeResolution/styles-v20.xml", "res/values-v20/styles.xml");

    ConfigurationManager configurationManager = myFacet.getConfigurationManager();
    Configuration configuration = configurationManager.getConfiguration(file);

    ThemeResolver resolver = new ThemeResolver(configuration);
    ThemeEditorStyle theme = resolver.getTheme("@style/AppTheme");
    assertNotNull(theme);

    Collection<ConfiguredElement<String>> parents = theme.getParentNames();
    assertSize(2, parents);
    ImmutableList<String> parentNames = ImmutableList.of(
      Iterables.get(parents, 0).getElement(),
      Iterables.get(parents, 1).getElement()
    );
    assertContainsElements(parentNames, "@style/Base.V20", "@style/Base.V17");

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
    assertContainsElements(parentNames, "@style/Base.V20", "@style/Base.V17");
  }

  @Override
  protected void configureAdditionalModules(@NotNull TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder,
                                            @NotNull List<MyAdditionalModuleData> modules) {
    final String testName = getTestName(true);

    // Add moduleA for the tests below
    if (testName.equals("getParentNamesWithDependency") || testName.equals("themeOverride")) {
      addModuleWithAndroidFacet(projectBuilder, modules, "moduleA", true);
    }
  }

  public void testGetParentNamesWithDependency() {
    VirtualFile virtualFile = myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles.xml", "res/values/styles.xml");
    myFixture.copyFileToProject("themeEditor/themeEditorStyle/styles_1.xml", "additionalModules/moduleA/res/values-v19/styles.xml");

    ConfigurationManager configurationManager = myFacet.getConfigurationManager();
    Configuration configuration = configurationManager.getConfiguration(virtualFile);
    ThemeResolver resolver = new ThemeResolver(configuration);
    ThemeEditorStyle theme = resolver.getTheme("@style/AppTheme");

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
    ThemeEditorStyle theme = resolver.getTheme("@style/AppTheme");

    assertNotNull(theme);
    assertEquals(1, theme.getParentNames().size());
    // We expect only the main app parent to be available
    assertEquals("@style/ATheme", theme.getParentNames().iterator().next().getElement());
  }

}
