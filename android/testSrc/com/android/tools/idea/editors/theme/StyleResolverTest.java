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

import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.datamodels.ThemeEditorStyle;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestCase;

public class StyleResolverTest extends AndroidTestCase {
  /*
   * The test SDK only includes some resources. It only includes a few incomplete styles.
   */

  public void testGetQualifiedName() {
    StyleResourceValue styleResourceValue = new StyleResourceValue(ResourceType.STYLE, "myStyle", true);
    assertEquals("@android:style/myStyle", StyleResolver.getQualifiedStyleName(styleResourceValue));

    styleResourceValue = new StyleResourceValue(ResourceType.STYLE, "myStyle", false);
    assertEquals("@style/myStyle", StyleResolver.getQualifiedStyleName(styleResourceValue));
  }

  public void testFrameworkStyleRead() {
    VirtualFile myLayout = myFixture.copyFileToProject("themeEditor/layout.xml", "res/layout/layout1.xml");
    Configuration configuration = myFacet.getConfigurationManager().getConfiguration(myLayout);
    StyleResolver styleResolver = new StyleResolver(configuration);

    assertNotNull(styleResolver.getStyle("@android:style/TextAppearance"));

    ThemeEditorStyle style = styleResolver.getStyle("@android:style/Theme.Holo.Light");
    assertEquals("Theme.Holo.Light", style.getName());
    assertEmpty(style.getValues()); // Style shouldn't have the values of the parent.

    try {
      style.setValue("newAttribute", "test");
      fail("Project styles shouldn't be modifiable");
    }
    catch (UnsupportedOperationException ignored) {
    }

    try {
      // We try to set a parent, this should fail because the style is read-only.
      // We use Theme as it's the only parent available on the test SDK.
      style.setParent("Theme");
      fail("Project styles shouldn't be modifiable");
    }
    catch (UnsupportedOperationException ignored) {
    }

    style = style.getParent();
    assertEquals("Theme", style.getName());
    assertNotEmpty(style.getValues());
  }
}