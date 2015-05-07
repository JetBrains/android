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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestCase;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Collection;
import java.util.Scanner;

public class ThemeEditorUtilsTest extends AndroidTestCase {

  private String sdkPlatformPath;
  @Override
  protected boolean requireRecentSdk() {
    return true;
  }

  private void compareWithAns(String doc, String ansPath) throws FileNotFoundException {
    assertNotNull(doc);
    Scanner in = new Scanner(new File(ansPath));
    String ansDoc = "";
    while (in.hasNext()) {
      ansDoc += in.nextLine();
    }

    ansDoc = String.format(ansDoc, sdkPlatformPath);

    doc = StringUtil.replace(doc, "\n", "");
    assertEquals(ansDoc, doc);
  }

  public void testGenerateToolTipText() throws FileNotFoundException {
    VirtualFile myFile = myFixture.copyFileToProject("themeEditor/styles_1.xml", "res/values/styles.xml");
    myFixture.copyFileToProject("themeEditor/attrs.xml", "res/values/attrs.xml");

    Configuration configuration = myFacet.getConfigurationManager().getConfiguration(myFile);

    sdkPlatformPath = getTestSdkPath();
    if (!sdkPlatformPath.endsWith("/")) sdkPlatformPath += "/";
    sdkPlatformPath += "platforms/android-" + configuration.getTarget().getVersion().getApiLevel();

    ThemeResolver themeResolver = new ThemeResolver(configuration);
    ThemeEditorStyle theme = themeResolver.getTheme("@style/AppTheme");
    assertNotNull(theme);
    Collection<ItemResourceValue> values = theme.getValues();
    assertEquals(7, values.size());

    for (ItemResourceValue item : values) {
      String doc = ThemeEditorUtils.generateToolTipText(item, myModule, configuration);
      compareWithAns(doc, myFixture.getTestDataPath() + "/themeEditor/tooltipDocAns/" + item.getName() + ".ans");
    }
  }

  public void testGetDisplayHtml() {
    VirtualFile myFile = myFixture.copyFileToProject("themeEditor/styles_1.xml", "res/values/styles.xml");
    myFixture.copyFileToProject("themeEditor/attrs.xml", "res/values/attrs.xml");

    Configuration configuration = myFacet.getConfigurationManager().getConfiguration(myFile);

    ThemeResolver themeResolver = new ThemeResolver(configuration);
    ThemeEditorStyle theme = themeResolver.getTheme("@style/AppTheme");
    assertNotNull(theme);

    Collection<ItemResourceValue> values = theme.getValues();
    assertEquals(7, values.size());
    for (ItemResourceValue item : values) {
      String displayHtml = ThemeEditorUtils.getDisplayHtml(new EditedStyleItem(item, theme));
      if ("myDeprecated".equals(item.getName())) {
        assertEquals("<html><body><strike>myDeprecated</strike></body></html>", displayHtml);
      } else {
        assertEquals(item.getName(), displayHtml);
      }
    }
  }
}
