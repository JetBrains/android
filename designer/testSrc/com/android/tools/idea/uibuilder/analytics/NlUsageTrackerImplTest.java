/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.analytics;

import static com.android.SdkConstants.ATTR_COLLAPSE_PARALLAX_MULTIPLIER;
import static com.android.SdkConstants.ATTR_ELEVATION;
import static com.android.SdkConstants.ATTR_TEXT;
import static com.android.AndroidXConstants.CONSTRAINT_LAYOUT;
import static com.android.SdkConstants.DESIGN_LIB_ARTIFACT;
import static com.android.SdkConstants.EDIT_TEXT;
import static com.android.SdkConstants.LINEAR_LAYOUT;
import static com.android.SdkConstants.PROGRESS_BAR;
import static com.android.SdkConstants.SEEK_BAR;
import static com.android.SdkConstants.TEXT_VIEW;
import static com.android.SdkConstants.TOOLS_NS_NAME_PREFIX;
import static com.android.tools.idea.common.analytics.UsageTrackerUtil.CUSTOM_NAME;
import static com.android.tools.idea.uibuilder.analytics.NlUsageTrackerImpl.convertEditTextViewOption;
import static com.android.tools.idea.uibuilder.analytics.NlUsageTrackerImpl.convertFilterMatches;
import static com.android.tools.idea.uibuilder.analytics.NlUsageTrackerImpl.convertGroupName;
import static com.android.tools.idea.uibuilder.analytics.NlUsageTrackerImpl.convertLinearLayoutViewOption;
import static com.android.tools.idea.uibuilder.analytics.NlUsageTrackerImpl.convertProgressBarViewOption;
import static com.android.tools.idea.uibuilder.analytics.NlUsageTrackerImpl.convertSeekBarViewOption;
import static com.android.tools.idea.uibuilder.analytics.NlUsageTrackerImpl.convertViewOption;
import static com.android.tools.idea.uibuilder.analytics.NlUsageTrackerImpl.getStyleValue;
import static com.google.common.truth.Truth.assertThat;
import static com.google.wireless.android.sdk.stats.LayoutPaletteEvent.ViewOption.CUSTOM_OPTION;
import static com.google.wireless.android.sdk.stats.LayoutPaletteEvent.ViewOption.DATE_EDITOR;
import static com.google.wireless.android.sdk.stats.LayoutPaletteEvent.ViewOption.DECIMAL_NUMBER;
import static com.google.wireless.android.sdk.stats.LayoutPaletteEvent.ViewOption.DISCRETE_SEEK_BAR;
import static com.google.wireless.android.sdk.stats.LayoutPaletteEvent.ViewOption.EMAIL;
import static com.google.wireless.android.sdk.stats.LayoutPaletteEvent.ViewOption.HORIZONTAL_LINEAR_LAYOUT;
import static com.google.wireless.android.sdk.stats.LayoutPaletteEvent.ViewOption.HORIZONTAL_PROGRESS_BAR;
import static com.google.wireless.android.sdk.stats.LayoutPaletteEvent.ViewOption.MULTILINE_TEXT;
import static com.google.wireless.android.sdk.stats.LayoutPaletteEvent.ViewOption.NORMAL;
import static com.google.wireless.android.sdk.stats.LayoutPaletteEvent.ViewOption.NUMBER;
import static com.google.wireless.android.sdk.stats.LayoutPaletteEvent.ViewOption.PASSWORD;
import static com.google.wireless.android.sdk.stats.LayoutPaletteEvent.ViewOption.PASSWORD_NUMERIC;
import static com.google.wireless.android.sdk.stats.LayoutPaletteEvent.ViewOption.PHONE;
import static com.google.wireless.android.sdk.stats.LayoutPaletteEvent.ViewOption.POSTAL_ADDRESS;
import static com.google.wireless.android.sdk.stats.LayoutPaletteEvent.ViewOption.SIGNED_NUMBER;
import static com.google.wireless.android.sdk.stats.LayoutPaletteEvent.ViewOption.TIME_EDITOR;
import static com.google.wireless.android.sdk.stats.LayoutPaletteEvent.ViewOption.VERTICAL_LINEAR_LAYOUT;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.tools.idea.common.analytics.BaseUsageTrackerImplTest;
import com.android.tools.idea.common.editor.DesignerEditorPanel;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.uibuilder.palette.NlPaletteModel;
import com.android.tools.idea.uibuilder.palette.Palette;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.NlScreenViewProvider;
import com.android.tools.idea.uibuilder.type.LayoutFileType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.wireless.android.sdk.stats.AndroidAttribute;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.LayoutFavoriteAttributeChangeEvent;
import com.google.wireless.android.sdk.stats.LayoutPaletteEvent;
import com.google.wireless.android.sdk.stats.SearchOption;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.ServiceContainerUtil;
import java.io.InputStreamReader;
import java.io.Reader;
import org.intellij.lang.annotations.Language;
import com.android.tools.dom.attrs.AttributeDefinition;
import com.android.tools.dom.attrs.AttributeDefinitions;
import org.jetbrains.android.resourceManagers.FrameworkResourceManager;
import org.jetbrains.android.resourceManagers.LocalResourceManager;
import org.jetbrains.android.resourceManagers.ModuleResourceManagers;
import org.jetbrains.annotations.NotNull;

public class NlUsageTrackerImplTest extends BaseUsageTrackerImplTest {
  private static final String ATTR_CUSTOM_NAME = "MyCustomPropertyName";

  @Language("XML")
  private static final String DISCRETE_SEEK_BAR_XML = "<SeekBar\n" +
                                                      "    style=\"@style/Widget.AppCompat.SeekBar.Discrete\"\n" +
                                                      "    android:layout_width=\"wrap_content\"\n" +
                                                      "    android:layout_height=\"wrap_content\"\n" +
                                                      "    android:max=\"10\"\n" +
                                                      "    android:progress=\"3\"\n" +
                                                      "/>";

  private NlModel myModel;
  private AttributeDefinition myCollapseParallaxMultiplierDefinition;
  private AttributeDefinition myElevationDefinition;
  private AttributeDefinition myTextDefinition;
  private AttributeDefinition myCustomDefinition;

  public void testPaletteDropLogging() {
    NlUsageTracker tracker = getUsageTracker();

    tracker.logDropFromPalette(CONSTRAINT_LAYOUT.defaultName(), "<" + CONSTRAINT_LAYOUT.defaultName() + "/>", "All", -1);
    AndroidStudioEvent studioEvent = getLastLogUsage();
    LayoutPaletteEvent logged = studioEvent.getLayoutEditorEvent().getPaletteEvent();
    assertThat(logged.getView().getTagName()).isEqualTo("ConstraintLayout");
    assertThat(logged.getViewOption()).isEqualTo(NORMAL);
    assertThat(logged.getSelectedGroup()).isEqualTo(LayoutPaletteEvent.ViewGroup.ALL_GROUPS);
    assertThat(logged.getSearchOption()).isEqualTo(SearchOption.NONE);
  }

  public void testPaletteDropTextEditLogging() {
    NlUsageTracker tracker = getUsageTracker();

    @Language("XML")
    String representation = "            <EditText\n" +
                            "              android:layout_width=\"wrap_content\"\n" +
                            "              android:layout_height=\"wrap_content\"\n" +
                            "              android:inputType=\"textEmailAddress\"\n" +
                            "              android:ems=\"10\"\n" +
                            "            />\n";

    tracker.logDropFromPalette(EDIT_TEXT, representation, "All", -1);
    AndroidStudioEvent studioEvent = getLastLogUsage();
    LayoutPaletteEvent logged = studioEvent.getLayoutEditorEvent().getPaletteEvent();
    assertThat(logged.getView().getTagName()).isEqualTo(EDIT_TEXT);
    assertThat(logged.getViewOption()).isEqualTo(EMAIL);
    assertThat(logged.getSelectedGroup()).isEqualTo(LayoutPaletteEvent.ViewGroup.ALL_GROUPS);
    assertThat(logged.getSearchOption()).isEqualTo(SearchOption.NONE);
  }

  public void testPaletteDropCustomViewLogging() {
    String tag = "com.acme.MyCustomControl";
    NlUsageTracker tracker = getUsageTracker();

    tracker.logDropFromPalette(tag, "<" + tag + "/>", "Advanced", 1);
    AndroidStudioEvent studioEvent = getLastLogUsage();
    LayoutPaletteEvent logged = studioEvent.getLayoutEditorEvent().getPaletteEvent();
    assertThat(logged.getView().getTagName()).isEqualTo(CUSTOM_NAME);
    assertThat(logged.getViewOption()).isEqualTo(NORMAL);
    assertThat(logged.getSelectedGroup()).isEqualTo(LayoutPaletteEvent.ViewGroup.ADVANCED);
    assertThat(logged.getSearchOption()).isEqualTo(SearchOption.SINGLE_MATCH);
  }

  public void testAddFavoriteLogging() {
    initNeleModelMocks();
    NlUsageTracker tracker = getUsageTracker();

    tracker.logFavoritesChange(ATTR_TEXT, "", ImmutableList.of(ATTR_COLLAPSE_PARALLAX_MULTIPLIER, ATTR_TEXT), myFacet);
    AndroidStudioEvent studioEvent = getLastLogUsage();
    LayoutFavoriteAttributeChangeEvent logged = studioEvent.getLayoutEditorEvent().getFavoriteChangeEvent();
    assertThat(logged.getAdded().getAttributeNamespace()).isEqualTo(AndroidAttribute.AttributeNamespace.ANDROID);
    assertThat(logged.getAdded().getAttributeName()).isEqualTo(ATTR_TEXT);
    assertThat(logged.hasRemoved()).isFalse();
    assertThat(logged.getActiveCount()).isEqualTo(2);
    assertThat(logged.getActive(0).getAttributeNamespace()).isEqualTo(AndroidAttribute.AttributeNamespace.APPLICATION);
    assertThat(logged.getActive(0).getAttributeName()).isEqualTo(ATTR_COLLAPSE_PARALLAX_MULTIPLIER);
    assertThat(logged.getActive(1).getAttributeNamespace()).isEqualTo(AndroidAttribute.AttributeNamespace.ANDROID);
    assertThat(logged.getActive(1).getAttributeName()).isEqualTo(ATTR_TEXT);
  }

  public void testAddCustomFavoriteLogging() {
    initNeleModelMocks();
    NlUsageTracker tracker = getUsageTracker();

    tracker.logFavoritesChange(TOOLS_NS_NAME_PREFIX + ATTR_CUSTOM_NAME, "",
                               ImmutableList.of(ATTR_CUSTOM_NAME, TOOLS_NS_NAME_PREFIX + ATTR_COLLAPSE_PARALLAX_MULTIPLIER), myFacet);
    AndroidStudioEvent studioEvent = getLastLogUsage();
    LayoutFavoriteAttributeChangeEvent logged = studioEvent.getLayoutEditorEvent().getFavoriteChangeEvent();
    assertThat(logged.getAdded().getAttributeNamespace()).isEqualTo(AndroidAttribute.AttributeNamespace.TOOLS);
    assertThat(logged.getAdded().getAttributeName()).isEqualTo(CUSTOM_NAME);
    assertThat(logged.hasRemoved()).isFalse();
    assertThat(logged.getActiveCount()).isEqualTo(2);
    assertThat(logged.getActive(0).getAttributeNamespace()).isEqualTo(AndroidAttribute.AttributeNamespace.APPLICATION);
    assertThat(logged.getActive(0).getAttributeName()).isEqualTo(CUSTOM_NAME);
    assertThat(logged.getActive(1).getAttributeNamespace()).isEqualTo(AndroidAttribute.AttributeNamespace.TOOLS);
    assertThat(logged.getActive(1).getAttributeName()).isEqualTo(ATTR_COLLAPSE_PARALLAX_MULTIPLIER);
  }

  public void testRemoveFavoriteLogging() {
    initNeleModelMocks();
    NlUsageTracker tracker = getUsageTracker();

    tracker.logFavoritesChange("", TOOLS_NS_NAME_PREFIX + ATTR_TEXT, ImmutableList.of(ATTR_ELEVATION), myFacet);
    AndroidStudioEvent studioEvent = getLastLogUsage();
    LayoutFavoriteAttributeChangeEvent logged = studioEvent.getLayoutEditorEvent().getFavoriteChangeEvent();
    assertThat(logged.getRemoved().getAttributeNamespace()).isEqualTo(AndroidAttribute.AttributeNamespace.TOOLS);
    assertThat(logged.getRemoved().getAttributeName()).isEqualTo(ATTR_TEXT);
    assertThat(logged.hasAdded()).isFalse();
    assertThat(logged.getActiveCount()).isEqualTo(1);
    assertThat(logged.getActive(0).getAttributeNamespace()).isEqualTo(AndroidAttribute.AttributeNamespace.ANDROID);
    assertThat(logged.getActive(0).getAttributeName()).isEqualTo(ATTR_ELEVATION);
  }

  private NlUsageTracker getUsageTracker() {
    NlDesignSurface surface = mock(NlDesignSurface.class);
    when(surface.getLayoutType()).thenReturn(LayoutFileType.INSTANCE);
    when(surface.getScreenViewProvider()).thenReturn(NlScreenViewProvider.RENDER_AND_BLUEPRINT);
    NlAnalyticsManager analyticsManager = new NlAnalyticsManager(surface);
    analyticsManager.setEditorModeWithoutTracking(DesignerEditorPanel.State.SPLIT);
    when(surface.getAnalyticsManager()).thenReturn(analyticsManager);
    when(surface.getScale()).thenReturn(0.50);
    Configuration configuration = getConfigurationMock();
    when(surface.getConfigurations()).thenReturn(ImmutableList.of(configuration));

    return new NlUsageTrackerImpl(SYNC_EXECUTOR, surface, usageTracker::logNow);
  }

  private void initNeleModelMocks() {
    ModuleResourceManagers moduleResourceManagers = mock(ModuleResourceManagers.class);
    FrameworkResourceManager frameworkResourceManager = mock(FrameworkResourceManager.class);
    LocalResourceManager localResourceManager = mock(LocalResourceManager.class);
    AttributeDefinitions systemAttributeDefinitions = mock(AttributeDefinitions.class);
    AttributeDefinitions localAttributeDefinitions = mock(AttributeDefinitions.class);

    myElevationDefinition = new AttributeDefinition(ResourceNamespace.ANDROID, ATTR_ELEVATION);
    myTextDefinition = new AttributeDefinition(ResourceNamespace.ANDROID, ATTR_TEXT);
    myCustomDefinition =
        new AttributeDefinition(ResourceNamespace.RES_AUTO, ATTR_CUSTOM_NAME, "com.acme:CustomLibrary", null);
    myCollapseParallaxMultiplierDefinition =
        new AttributeDefinition(ResourceNamespace.RES_AUTO, ATTR_COLLAPSE_PARALLAX_MULTIPLIER, DESIGN_LIB_ARTIFACT, null);

    myModel = mock(NlModel.class);
    when(myModel.getFacet()).thenReturn(myFacet);

    ServiceContainerUtil.replaceService(myModule, ModuleResourceManagers.class, moduleResourceManagers, getTestRootDisposable());

    when(moduleResourceManagers.getLocalResourceManager()).thenReturn(localResourceManager);
    when(moduleResourceManagers.getFrameworkResourceManager()).thenReturn(frameworkResourceManager);
    when(localResourceManager.getAttributeDefinitions()).thenReturn(localAttributeDefinitions);
    when(frameworkResourceManager.getAttributeDefinitions()).thenReturn(systemAttributeDefinitions);
    when(localAttributeDefinitions.getAttrs())
        .thenReturn(ImmutableSet.of(ResourceReference.attr(ResourceNamespace.RES_AUTO, ATTR_COLLAPSE_PARALLAX_MULTIPLIER)));
    when(localAttributeDefinitions.getAttrDefinition(ResourceReference.attr(ResourceNamespace.RES_AUTO, ATTR_COLLAPSE_PARALLAX_MULTIPLIER)))
        .thenReturn(myCollapseParallaxMultiplierDefinition);
    when(localAttributeDefinitions.getAttrDefinition(ResourceReference.attr(ResourceNamespace.RES_AUTO, ATTR_CUSTOM_NAME)))
        .thenReturn(myCustomDefinition);
    when(systemAttributeDefinitions.getAttrs())
        .thenReturn(ImmutableSet.of(
            ResourceReference.attr(ResourceNamespace.ANDROID, ATTR_ELEVATION),
            ResourceReference.attr(ResourceNamespace.ANDROID, ATTR_TEXT)));
    when(systemAttributeDefinitions.getAttrDefinition(ResourceReference.attr(ResourceNamespace.ANDROID, ATTR_ELEVATION)))
        .thenReturn(myElevationDefinition);
    when(systemAttributeDefinitions.getAttrDefinition(ResourceReference.attr(ResourceNamespace.ANDROID, ATTR_TEXT)))
        .thenReturn(myTextDefinition);
  }

  private NlComponent getComponentMock(@NotNull String tagName) {
    NlComponent component = mock(NlComponent.class);
    when(component.getModel()).thenReturn(myModel);
    when(component.getTagName()).thenReturn(tagName);
    return component;
  }

  public void testConvertGroupName() {
    assertThat(convertGroupName("All")).isEqualTo(LayoutPaletteEvent.ViewGroup.ALL_GROUPS);
    assertThat(convertGroupName("Widgets")).isEqualTo(LayoutPaletteEvent.ViewGroup.WIDGETS);
    assertThat(convertGroupName("Text")).isEqualTo(LayoutPaletteEvent.ViewGroup.TEXT);
    assertThat(convertGroupName("Layouts")).isEqualTo(LayoutPaletteEvent.ViewGroup.LAYOUTS);
    assertThat(convertGroupName("Containers")).isEqualTo(LayoutPaletteEvent.ViewGroup.CONTAINERS);
    assertThat(convertGroupName("Images")).isEqualTo(LayoutPaletteEvent.ViewGroup.IMAGES);
    assertThat(convertGroupName("Date")).isEqualTo(LayoutPaletteEvent.ViewGroup.DATES);
    assertThat(convertGroupName("Transitions")).isEqualTo(LayoutPaletteEvent.ViewGroup.TRANSITIONS);
    assertThat(convertGroupName("Advanced")).isEqualTo(LayoutPaletteEvent.ViewGroup.ADVANCED);
    assertThat(convertGroupName("Design")).isEqualTo(LayoutPaletteEvent.ViewGroup.DESIGN);
    assertThat(convertGroupName("AppCompat")).isEqualTo(LayoutPaletteEvent.ViewGroup.APP_COMPAT);
    assertThat(convertGroupName("Helpers")).isEqualTo(LayoutPaletteEvent.ViewGroup.HELPERS);
    assertThat(convertGroupName("MyGroup")).isEqualTo(LayoutPaletteEvent.ViewGroup.CUSTOM);
  }

  public void testAllGroupsOnPaletteAreRecognized() throws Exception {
    Palette palette = getPalette(getProject());
    palette.accept(new Palette.Visitor() {
      @Override
      public void visit(@NotNull Palette.Item item) {
      }

      @Override
      public void visit(@NotNull Palette.Group group) {
        assertThat(convertGroupName(group.getName())).isNotEqualTo(LayoutPaletteEvent.ViewGroup.CUSTOM);
      }
    });
  }

  public void testConvertViewOption() {
    assertThat(convertViewOption(PROGRESS_BAR, "<ProgressBar/>")).isEqualTo(NORMAL);
    assertThat(convertViewOption(PROGRESS_BAR, "<ProgressBar style=\"?android:attr/progressBarStyleHorizontal\"/>"))
      .isEqualTo(HORIZONTAL_PROGRESS_BAR);
    assertThat(convertViewOption(PROGRESS_BAR, "<ProgressBar style=\"unknown\"/>")).isEqualTo(CUSTOM_OPTION);
    assertThat(convertViewOption(SEEK_BAR, "<SeekBar/>")).isEqualTo(NORMAL);
    assertThat(convertViewOption(SEEK_BAR, DISCRETE_SEEK_BAR_XML)).isEqualTo(DISCRETE_SEEK_BAR);
    assertThat(convertViewOption(SEEK_BAR, "<SeekBar style=\"unknown\"/>")).isEqualTo(CUSTOM_OPTION);
    assertThat(convertViewOption(EDIT_TEXT, "<EditText/>")).isEqualTo(NORMAL);
    assertThat(convertViewOption(EDIT_TEXT, "<EditText android:inputType=\"textPassword\"/>")).isEqualTo(PASSWORD);
    assertThat(convertViewOption(EDIT_TEXT, "<EditText android:inputType=\"unknown\"/>")).isEqualTo(CUSTOM_OPTION);
    assertThat(convertViewOption(LINEAR_LAYOUT, "<LinearLayout/>")).isEqualTo(HORIZONTAL_LINEAR_LAYOUT);
    assertThat(convertViewOption(LINEAR_LAYOUT, "<LinearLayout android:orientation=\"vertical\"/>")).isEqualTo(VERTICAL_LINEAR_LAYOUT);
    assertThat(convertViewOption(LINEAR_LAYOUT, "<LinearLayout android:orientation=\"unknown\"/>")).isEqualTo(CUSTOM_OPTION);
    assertThat(convertViewOption(TEXT_VIEW, "<TextView/>")).isEqualTo(NORMAL);
  }

  public void testConvertProgressBarViewOption() {
    assertThat(convertProgressBarViewOption("<ProgressBar/>")).isEqualTo(NORMAL);
    assertThat(convertProgressBarViewOption("<ProgressBar style=\"?android:attr/progressBarStyle\"/>")).isEqualTo(NORMAL);
    assertThat(convertProgressBarViewOption("<ProgressBar style=\"?android:attr/progressBarStyleHorizontal\"/>"))
      .isEqualTo(HORIZONTAL_PROGRESS_BAR);
    assertThat(convertProgressBarViewOption("<ProgressBar style=\"unknown\"/>")).isEqualTo(CUSTOM_OPTION);
  }

  public void testConvertSeekBarViewOption() {
    assertThat(convertSeekBarViewOption("<SeekBar/>")).isEqualTo(NORMAL);
    assertThat(convertSeekBarViewOption(DISCRETE_SEEK_BAR_XML)).isEqualTo(DISCRETE_SEEK_BAR);
    assertThat(convertSeekBarViewOption("<SeekBar style=\"unknown\"/>")).isEqualTo(CUSTOM_OPTION);
  }

  public void testConvertEditTextViewOption() {
    assertThat(convertEditTextViewOption("<EditText/>")).isEqualTo(NORMAL);
    assertThat(convertEditTextViewOption("<EditText android:inputType=\"textPassword\"/>")).isEqualTo(PASSWORD);
    assertThat(convertEditTextViewOption("<EditText android:inputType=\"numberPassword\"/>")).isEqualTo(PASSWORD_NUMERIC);
    assertThat(convertEditTextViewOption("<EditText android:inputType=\"textEmailAddress\"/>")).isEqualTo(EMAIL);
    assertThat(convertEditTextViewOption("<EditText android:inputType=\"phone\"/>")).isEqualTo(PHONE);
    assertThat(convertEditTextViewOption("<EditText android:inputType=\"textPostalAddress\"/>")).isEqualTo(POSTAL_ADDRESS);
    assertThat(convertEditTextViewOption("<EditText android:inputType=\"textMultiLine\"/>")).isEqualTo(MULTILINE_TEXT);
    assertThat(convertEditTextViewOption("<EditText android:inputType=\"time\"/>")).isEqualTo(TIME_EDITOR);
    assertThat(convertEditTextViewOption("<EditText android:inputType=\"date\"/>")).isEqualTo(DATE_EDITOR);
    assertThat(convertEditTextViewOption("<EditText android:inputType=\"number\"/>")).isEqualTo(NUMBER);
    assertThat(convertEditTextViewOption("<EditText android:inputType=\"numberSigned\"/>")).isEqualTo(SIGNED_NUMBER);
    assertThat(convertEditTextViewOption("<EditText android:inputType=\"numberDecimal\"/>")).isEqualTo(DECIMAL_NUMBER);
    assertThat(convertEditTextViewOption("<EditText android:inputType=\"unknown\"/>")).isEqualTo(CUSTOM_OPTION);
  }

  public void testConvertLinearLayoutViewOption() {
    assertThat(convertLinearLayoutViewOption("<LinearLayout/>")).isEqualTo(HORIZONTAL_LINEAR_LAYOUT);
    assertThat(convertLinearLayoutViewOption("<LinearLayout android:orientation=\"horizontal\"/>")).isEqualTo(HORIZONTAL_LINEAR_LAYOUT);
    assertThat(convertLinearLayoutViewOption("<LinearLayout android:orientation=\"vertical\"/>")).isEqualTo(VERTICAL_LINEAR_LAYOUT);
    assertThat(convertLinearLayoutViewOption("<LinearLayout android:orientation=\"unknown\"/>")).isEqualTo(CUSTOM_OPTION);
  }

  public void testConvertFilterMatches() {
    assertThat(convertFilterMatches(-1)).isEqualTo(SearchOption.NONE);
    assertThat(convertFilterMatches(0)).isEqualTo(SearchOption.NONE);
    assertThat(convertFilterMatches(1)).isEqualTo(SearchOption.SINGLE_MATCH);
    assertThat(convertFilterMatches(2)).isEqualTo(SearchOption.MULTIPLE_MATCHES);
    assertThat(convertFilterMatches(117)).isEqualTo(SearchOption.MULTIPLE_MATCHES);
  }

  public void testGetStyleValueFromSeekBar() {
    assertThat(getStyleValue("<SeekBar/>")).isNull();
    assertThat(getStyleValue(DISCRETE_SEEK_BAR_XML)).isEqualTo("@style/Widget.AppCompat.SeekBar.Discrete");
  }

  private static Palette getPalette(@NotNull Project project) throws Exception {
    String id = LayoutFileType.INSTANCE.getPaletteId();
    assertNotNull(id);
    try (Reader reader = new InputStreamReader(NlPaletteModel.class.getResourceAsStream(NlPaletteModel.getPaletteFileNameFromId(id)))) {
      return Palette.parse(reader, new ViewHandlerManager(project));
    }
  }
}
