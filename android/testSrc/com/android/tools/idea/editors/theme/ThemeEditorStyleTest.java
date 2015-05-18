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
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.editors.theme.datamodels.ThemeEditorStyle;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestCase;
import com.android.SdkConstants;
import com.android.tools.idea.configurations.ConfigurationManager;
import org.jetbrains.annotations.NotNull;

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

    ItemResourceValue hasItem = new ItemResourceValue("myColor", false, false);
    ItemResourceValue hasNotItem = new ItemResourceValue("myHasNot", false, false);
    ItemResourceValue hasInParent = new ItemResourceValue("editTextStyle", true, true);

    assertEquals(true, theme.hasItem(new EditedStyleItem(hasItem, theme)));
    assertEquals(false, theme.hasItem(new EditedStyleItem(hasNotItem, theme)));
    assertEquals(true, theme.getParent().hasItem(new EditedStyleItem(hasInParent, theme.getParent())));
    assertEquals(false, theme.hasItem(new EditedStyleItem(hasInParent, theme.getParent())));
  }

  private void doTest(@NotNull String attribute, @NotNull String afterDirectoryName) {
    VirtualFile virtualFile = myFixture.copyFileToProject("themeEditor/apiTestBefore/stylesApi.xml", "res/values/styles.xml");
    myFixture.copyFileToProject("themeEditor/apiTestBefore/stylesApi-v14.xml", "res/values-v14/styles.xml");
    myFixture.copyFileToProject("themeEditor/apiTestBefore/stylesApi-v19.xml", "res/values-v19/styles.xml");
    myFixture.copyFileToProject("themeEditor/apiTestBefore/stylesApi-v21.xml", "res/values-v21/styles.xml");

    ConfigurationManager configurationManager = myFacet.getConfigurationManager();
    Configuration configuration = configurationManager.getConfiguration(virtualFile);
    ThemeResolver themeResolver = new ThemeResolver(configuration);
    ThemeEditorStyle theme = themeResolver.getTheme("@style/Theme.MyTheme");

    assertNotNull(theme);
    theme.setValue(attribute, "myValue");

    myFixture.checkResultByFile("res/values/styles.xml", "themeEditor/" + afterDirectoryName + "/stylesApi.xml", true);
    myFixture.checkResultByFile("res/values-v14/styles.xml", "themeEditor/" + afterDirectoryName + "/stylesApi-v14.xml", true);
    myFixture.checkResultByFile("res/values-v19/styles.xml", "themeEditor/" + afterDirectoryName + "/stylesApi-v19.xml", true);
    myFixture.checkResultByFile("res/values-v21/styles.xml", "themeEditor/" + afterDirectoryName + "/stylesApi-v21.xml", true);
  }

  /**
   * Tests setting a non-framework attribute
   */
  public void testNonFrameworkAttribute() {
    doTest("myAttribute", "apiTestAfter1");
  }

  /**
   * Tests modifying an attribute defined for api < projectMinApi
   */
  public void testSmallApiAttribute() {
    doTest("android:windowIsFloating", "apiTestAfter2");
  }

  /**
   * Tests setting an attribute = projectMinApi
   */
  public void testMinApiAttribute() {
    doTest("android:actionBarSize", "apiTestAfter3");
  }

  /**
   * Tests setting an attribute with api that has no associated values folder
   */
  public void testHighNewApiAttribute() {
    doTest("android:windowOverscan", "apiTestAfter4");
    myFixture.checkResultByFile("res/values-v18/styles.xml", "themeEditor/apiTestAfter4/stylesApi-v18.xml", true);
  }

  /**
   * Tests setting an attribute with api that has an associated values folder
   */
  public void testHighExistingApiAttribute() {
    doTest("android:windowTranslucentStatus", "apiTestAfter5");
  }

  /**
   * Tests modifying an attribute overridden in values folder with api > attribute api
   * but that is not overridden in values folder corresponding to attribute api.
   * The test should modify the attribute where it is overridden but not override it
   * where it is not.
   */
  public void testModifyingOverriddenAttribute() {
    doTest("android:checkedTextViewStyle", "apiTestAfter6");
  }

  /**
   * Tests modifying an attribute defined for api > projectMinApi
   */
  public void testModifyingHighApiAttribute() {
    doTest("android:actionModeStyle", "apiTestAfter7");
  }
}
