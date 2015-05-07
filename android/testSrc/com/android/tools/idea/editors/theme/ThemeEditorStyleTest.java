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

public class ThemeEditorStyleTest extends AndroidTestCase {

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
}
