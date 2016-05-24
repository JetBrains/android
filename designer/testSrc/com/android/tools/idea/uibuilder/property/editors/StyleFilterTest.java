/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property.editors;

import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.templates.AndroidGradleTestCase;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

public class StyleFilterTest extends AndroidGradleTestCase {
  private static final boolean FRAMEWORK = true;
  private static final boolean USER_DEFINED = true;
  private static final boolean IS_DERIVED_STYLE = true;
  private static final boolean FILTERED_OUT = true;

  private ResourceResolver myResolver;
  private StyleFilter myFilter;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    loadProject("projects/uibuilder/property");
    generateSources(false);

    VirtualFile file = getProject().getBaseDir().findFileByRelativePath("app/src/main/res/layout/activity_main.xml");
    assertNotNull(file);
    Configuration configuration = myAndroidFacet.getConfigurationManager().getConfiguration(file);
    myResolver = configuration.getResourceResolver();
    assertNotNull(myResolver);
    myFilter = new StyleFilter(myResolver);
  }

  public void testTextAppearances() {
    List<StyleResourceValue> styles = myFilter.getStylesDerivedFrom("TextAppearance", true).sequential().collect(Collectors.toList());
    assertTextAppearances(styles);

    // User defined TextAppearances are included
    assertStyle("Text2", !FRAMEWORK, USER_DEFINED, IS_DERIVED_STYLE, !FILTERED_OUT);

    // User defined TextAppearance is included
    assertStyle("TextAppearance", !FRAMEWORK, USER_DEFINED, IS_DERIVED_STYLE, !FILTERED_OUT);

    // The framework TextAppearance is included
    assertStyle("TextAppearance", FRAMEWORK, !USER_DEFINED, IS_DERIVED_STYLE, !FILTERED_OUT);

    // AppCompat TextAppearances are included
    assertStyle("TextAppearance.AppCompat.Light.Widget.PopupMenu.Small", !FRAMEWORK, !USER_DEFINED, IS_DERIVED_STYLE, !FILTERED_OUT);

    // TextAppearances starting with "Base." are filtered out
    assertStyle("Base.TextAppearance.AppCompat.Display2", !FRAMEWORK, !USER_DEFINED, IS_DERIVED_STYLE, FILTERED_OUT);

    // Styles not inheriting from TextAppearance are filtered out
    assertStyle("AppTheme", !FRAMEWORK, USER_DEFINED, !IS_DERIVED_STYLE, FILTERED_OUT);

    // Recursive styles are not included and do not cause an endless loop
    assertStyle("Ying", !FRAMEWORK, USER_DEFINED, !IS_DERIVED_STYLE, FILTERED_OUT);
    assertStyle("Yang", !FRAMEWORK, USER_DEFINED, !IS_DERIVED_STYLE, FILTERED_OUT);
  }

  private void assertTextAppearances(@NotNull List<StyleResourceValue> styles) {
    assertStyle(styles.get(0), "Text2", !FRAMEWORK, USER_DEFINED, IS_DERIVED_STYLE, !FILTERED_OUT);
    assertStyle(styles.get(1), "Text34", !FRAMEWORK, USER_DEFINED, IS_DERIVED_STYLE, !FILTERED_OUT);
    assertStyle(styles.get(2), "TextAppearance", !FRAMEWORK, USER_DEFINED, IS_DERIVED_STYLE, !FILTERED_OUT);
    assertStyle(styles.get(3), "TextAppearance.AppCompat", !FRAMEWORK, !USER_DEFINED, IS_DERIVED_STYLE, !FILTERED_OUT);

    // All library styles should be sorted
    String previousStyleName = styles.get(3).getName();
    int index;
    for (index = 4; index < styles.size() && !styles.get(index).isFramework(); index++) {
      StyleResourceValue style = styles.get(index);
      boolean expectedFilteredOut = style.getName().startsWith("Base.");
      assertStyle(styles.get(index), style.getName(), !FRAMEWORK, !USER_DEFINED, IS_DERIVED_STYLE, expectedFilteredOut);
      assertTrue(previousStyleName.compareTo(style.getName()) <= 0);
    }

    // All framework styles should be sorted
    previousStyleName = styles.get(index).getName();
    for (; index < styles.size(); index++) {
      StyleResourceValue style = styles.get(index);
      boolean expectedFilteredOut = style.getName().startsWith("Base.");
      assertStyle(styles.get(index), style.getName(), FRAMEWORK, !USER_DEFINED, IS_DERIVED_STYLE, expectedFilteredOut);
      assertTrue(previousStyleName.compareTo(style.getName()) <= 0);
    }

    assertEquals(styles.size(), index);
  }

  private void assertStyle(@NotNull String name, boolean isFramework, boolean userDefined, boolean isDerived, boolean filteredOut) {
    StyleResourceValue style;
    if (isFramework) {
      style = (StyleResourceValue)(myResolver.getFrameworkResources().get(ResourceType.STYLE).get(name));
    }
    else {
      style = (StyleResourceValue)(myResolver.getProjectResources().get(ResourceType.STYLE).get(name));
    }
    assertNotNull(style);
    assertStyle(style, name, isFramework, userDefined, isDerived, filteredOut);
  }

  private void assertStyle(@NotNull StyleResourceValue style,
                           @NotNull String name,
                           boolean isFramework,
                           boolean userDefined,
                           boolean isDerived,
                           boolean filteredOut) {
    assertEquals(name, style.getName());
    assertEquals(name, isFramework, style.isFramework());
    assertEquals(name, userDefined, style.isUserDefined());
    assertEquals(name, isDerived, myFilter.isDerived(style));
    assertEquals(name, filteredOut, !myFilter.filter(style));
  }
}
