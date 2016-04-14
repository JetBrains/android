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

import com.android.ide.common.resources.ResourceResolver;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.datamodels.ConfiguredThemeEditorStyle;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestCase;

public class ThemeEditorComponentTest extends AndroidTestCase {
  public void testGetGoodContrastPreviewBackground() {
    VirtualFile myFile = myFixture.copyFileToProject("themeEditor/styles_background.xml", "res/values/styles.xml");
    Configuration configuration = myFacet.getConfigurationManager().getConfiguration(myFile);
    ResourceResolver resourceResolver = configuration.getResourceResolver();
    assertNotNull(resourceResolver);

    ThemeResolver themeResolver = new ThemeResolver(configuration);

    ConfiguredThemeEditorStyle theme1 = themeResolver.getTheme("Theme.Theme1");
    assertNotNull(theme1);
    ConfiguredThemeEditorStyle theme2 = themeResolver.getTheme("Theme.Theme2");
    assertNotNull(theme2);
    ConfiguredThemeEditorStyle theme3 = themeResolver.getTheme("Theme.Theme3");
    assertNotNull(theme3);

    assertEquals(ThemeEditorComponent.ALT_PREVIEW_BACKGROUND,
                 ThemeEditorComponent.getGoodContrastPreviewBackground(theme1, resourceResolver));
    assertEquals(ThemeEditorComponent.PREVIEW_BACKGROUND, ThemeEditorComponent.getGoodContrastPreviewBackground(theme2, resourceResolver));
    assertEquals(ThemeEditorComponent.PREVIEW_BACKGROUND, ThemeEditorComponent.getGoodContrastPreviewBackground(theme3, resourceResolver));
  }
}
