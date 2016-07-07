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

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.templates.AndroidGradleTestCase;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    myFilter = new StyleFilter(getProject(), myResolver);
  }

  public void testTextAppearances() {
    List<StyleResourceValue> styles = myFilter.getStylesDerivedFrom("TextAppearance", true).collect(Collectors.toList());
    assertStylesSorted("TextAppearance", styles, 3, 50, 200,
                       ImmutableList.of("Text2", "Text34", "TextAppearance"),
                       ImmutableList.of("TextAppearance.AppCompat",
                                        "TextAppearance.AppCompat.Body1",
                                        "TextAppearance.AppCompat.Body2",
                                        "TextAppearance.AppCompat.Display1",
                                        "TextAppearance.AppCompat.Display2",
                                        "TextAppearance.AppCompat.Display3",
                                        "TextAppearance.AppCompat.Display4"),
                       ImmutableList.of("TextAppearance",
                                        "TextAppearance.DeviceDefault",
                                        "TextAppearance.Material",
                                        "TextAppearance.Material.Small"));

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

  public void testStylesByTagName() {
    assertTagStyle(SdkConstants.BUTTON, 0, 6, 46,
                   ImmutableList.of(),
                   ImmutableList.of("Widget.AppCompat.Button", "Widget.AppCompat.Button.Borderless", "Widget.AppCompat.Button.Small"),
                   ImmutableList.of("Widget.Button", "Widget.Button.Small", "Widget.Material.Button"));
    assertTagStyle(SdkConstants.PROGRESS_BAR, 0, 2, 57,
                   ImmutableList.of(),
                   ImmutableList.of("Widget.AppCompat.ProgressBar", "Widget.AppCompat.ProgressBar.Horizontal"),
                   ImmutableList.of("Widget.ProgressBar", "Widget.Material.ProgressBar"));
    assertTagStyle(SdkConstants.RADIO_BUTTON, 0, 1, 7,
                   ImmutableList.of(),
                   ImmutableList.of("Widget.AppCompat.CompoundButton.RadioButton"),
                   ImmutableList.of("Widget.CompoundButton.RadioButton", "Widget.Material.CompoundButton.RadioButton"));
    assertTagStyle(SdkConstants.CHECK_BOX, 0, 1, 7,
                   ImmutableList.of(),
                   ImmutableList.of("Widget.AppCompat.CompoundButton.CheckBox"),
                   ImmutableList.of("Widget.CompoundButton.CheckBox", "Widget.Material.CompoundButton.CheckBox"));
    assertTagStyle(SdkConstants.SWITCH, 0, 0, 2,
                   ImmutableList.of(),
                   ImmutableList.of(),
                   ImmutableList.of("Widget.CompoundButton.Switch"));
    assertTagStyle(SdkConstants.TEXT_VIEW, 0, 1, 24,
                   ImmutableList.of(),
                   ImmutableList.of("Widget.AppCompat.TextView.SpinnerItem"),
                   ImmutableList.of("Widget.TextView", "Widget.Material.TextView"));
    assertTagStyle(SdkConstants.APP_BAR_LAYOUT, 0, 1, 0,
                   ImmutableList.of(),
                   ImmutableList.of("Widget.Design.AppBarLayout"),
                   ImmutableList.of());
    assertTagStyle(SdkConstants.COLLAPSING_TOOLBAR_LAYOUT, 0, 1, 0,
                   ImmutableList.of(),
                   ImmutableList.of("Widget.Design.CollapsingToolbar"),
                   ImmutableList.of());
    assertTagStyle(SdkConstants.COORDINATOR_LAYOUT, 0, 1, 0,
                   ImmutableList.of(),
                   ImmutableList.of("Widget.Design.CoordinatorLayout"),
                   ImmutableList.of());
    assertTagStyle(SdkConstants.FLOATING_ACTION_BUTTON, 0, 1, 0,
                   ImmutableList.of(),
                   ImmutableList.of("Widget.Design.FloatingActionButton"),
                   ImmutableList.of());
    assertTagStyle(SdkConstants.NAVIGATION_VIEW, 0, 1, 0,
                   ImmutableList.of(),
                   ImmutableList.of("Widget.Design.NavigationView"),
                   ImmutableList.of());
    assertTagStyle(SdkConstants.SNACKBAR, 0, 1, 0,
                   ImmutableList.of(),
                   ImmutableList.of("Widget.Design.Snackbar"),
                   ImmutableList.of());
    assertTagStyle(SdkConstants.TAB_LAYOUT, 0, 1, 0,
                   ImmutableList.of(),
                   ImmutableList.of("Widget.Design.TabLayout"),
                   ImmutableList.of());
    assertTagStyle(SdkConstants.TEXT_INPUT_LAYOUT, 0, 1, 0,
                   ImmutableList.of(),
                   ImmutableList.of("Widget.Design.TextInputLayout"),
                   ImmutableList.of());
  }

  private void assertTagStyle(@NotNull String tagName, int userCount, int libraryCount, int frameworkCount,
                              List<String> userSample, List<String> librarySample, List<String> frameworkSample) {
    List<StyleResourceValue> styles = myFilter.getWidgetStyles(tagName).collect(Collectors.toList());
    assertStylesSorted(tagName, styles, userCount, libraryCount, frameworkCount, userSample, librarySample, frameworkSample);
    boolean expectHasStyles = userCount + libraryCount + frameworkCount > 0;
    assertEquals(tagName + " hasWidgetStyles", expectHasStyles, StyleFilter.hasWidgetStyles(getProject(), myResolver, tagName));
  }

  private void assertStylesSorted(@NotNull String context,
                                  @NotNull List<StyleResourceValue> styles,
                                  int minUserCount,
                                  int minLibraryCount,
                                  int minFrameworkCount,
                                  List<String> userSample,
                                  List<String> librarySample,
                                  List<String> frameworkSample) {
    Set<String> userSampleStyles = new HashSet<>(userSample);
    Set<String> librarySampleStyles = new HashSet<>(librarySample);
    Set<String> frameworkSampleStyles = new HashSet<>(frameworkSample);

    String previousStyleName = "";
    int index = 0;

    // All user styles should be sorted
    for (; index < styles.size() && styles.get(index).isUserDefined(); index++) {
      StyleResourceValue style = styles.get(index);
      assertStyle(styles.get(index), style.getName(), !FRAMEWORK, USER_DEFINED, IS_DERIVED_STYLE, false);
      assertTrue(previousStyleName.compareTo(style.getName()) < 0);
      userSampleStyles.remove(style.getName());
      previousStyleName = style.getName();
    }
    int actualUserCount = index;
    assertTrue(context + " user style count, actual: " + actualUserCount, minUserCount <= actualUserCount);
    assertEmpty(context, userSampleStyles);

    // All library styles should be sorted
    int previousIndex = index;
    previousStyleName = "";
    for (; index < styles.size() && !styles.get(index).isFramework(); index++) {
      StyleResourceValue style = styles.get(index);
      assertStyle(styles.get(index), style.getName(), !FRAMEWORK, !USER_DEFINED, IS_DERIVED_STYLE, false);
      assertTrue(previousStyleName.compareTo(style.getName()) < 0);
      librarySampleStyles.remove(style.getName());
      previousStyleName = style.getName();
    }
    int actualLibraryCount = index - previousIndex;
    assertTrue(context + " library style count, actual: " + actualLibraryCount, minLibraryCount <= actualLibraryCount);
    assertEmpty(context, librarySampleStyles);

    // All framework styles should be sorted
    previousIndex = index;
    previousStyleName = "";
    for (; index < styles.size(); index++) {
      StyleResourceValue style = styles.get(index);
      assertStyle(styles.get(index), style.getName(), FRAMEWORK, !USER_DEFINED, IS_DERIVED_STYLE, false);
      assertTrue(previousStyleName.compareTo(style.getName()) < 0);
      frameworkSampleStyles.remove(style.getName());
      previousStyleName = style.getName();
    }
    int actualFrameworkCount = index - previousIndex;
    assertTrue(context + " framework style count, actual: " + actualFrameworkCount , minFrameworkCount <= actualFrameworkCount);
    assertEmpty(context, frameworkSampleStyles);

    assertEquals("All styles should be in 3 sections", styles.size(), index);
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
