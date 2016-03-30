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
package com.android.tools.idea.uibuilder.palette;

import com.android.annotations.NonNull;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.api.XmlType;
import com.android.tools.idea.uibuilder.handlers.TextViewHandler;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.google.common.base.Splitter;
import com.intellij.openapi.util.text.StringUtil;
import icons.AndroidIcons;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.AndroidTestCase;
import org.mockito.Mockito;

import javax.swing.*;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.api.PaletteComponentHandler.IN_PLATFORM;
import static com.android.tools.idea.uibuilder.api.PaletteComponentHandler.NO_PREVIEW;

/**
 * Test case base class with assert methods for checking palette items.
 */
public abstract class PaletteTestCase extends AndroidTestCase {
  private static final ViewHandler STANDARD_VIEW = new ViewHandler();
  private static final ViewHandler STANDARD_TEXT = new TextViewHandler();
  private static final ViewHandler STANDARD_LAYOUT = new ViewGroupHandler();
  private static final Splitter SPLITTER = Splitter.on("\n").trimResults();
  private static final double NO_SCALE = 1.0;

  public static Palette.Group assertIsGroup(@NonNull Palette.BaseItem item, String name) {
    assertTrue(item instanceof Palette.Group);
    Palette.Group group = (Palette.Group)item;
    assertEquals(name, group.getName());
    return group;
  }

  public void assertTextViewItem(@NonNull Palette.BaseItem item) {
    assertStandardTextView(item, TEXT_VIEW, IN_PLATFORM);
  }

  public void assertButton(@NonNull Palette.BaseItem item) {
    assertStandardTextView(item, BUTTON, IN_PLATFORM);
  }

  public void assertToggleButton(@NonNull Palette.BaseItem item) {
    assertStandardTextView(item, TOGGLE_BUTTON, IN_PLATFORM);
  }

  public void assertCheckBox(@NonNull Palette.BaseItem item) {
    assertStandardTextView(item, CHECK_BOX, IN_PLATFORM);
  }

  public void assertRadioButton(@NonNull Palette.BaseItem item) {
    assertStandardTextView(item, RADIO_BUTTON, IN_PLATFORM);
  }

  public void assertCheckedTextView(@NonNull Palette.BaseItem item) {
    assertStandardTextView(item, CHECKED_TEXT_VIEW, IN_PLATFORM);
  }

  @Language("XML")
  private static final String SPINNER_XML =
    "<Spinner\n" +
    "  android:layout_width=\"match_parent\"\n" +
    "  android:layout_height=\"wrap_content\">\n" +
    "</Spinner>\n";

  @Language("XML")
  private static final String SPINNER_PREVIEW_XML =
    "<Spinner\n" +
    "  android:layout_width=\"wrap_content\"\n" +
    "  android:layout_height=\"wrap_content\"\n" +
    "  android:entries=\"@android:array/postalAddressTypes\">\n" +
    "</Spinner>\n";

  public void assertSpinner(@NonNull Palette.BaseItem item) {
    checkItem(item, SPINNER, "Spinner", AndroidIcons.Views.Spinner, SPINNER_XML, SPINNER_PREVIEW_XML,
              SPINNER_XML, IN_PLATFORM, NO_SCALE);
    checkComponent(createMockComponent(SPINNER), "Spinner", AndroidIcons.Views.Spinner);
  }

  @Language("XML")
  private static final String LARGE_PROGRESS_XML =
    "<ProgressBar\n" +
    "  style=\"@android:style/Widget.ProgressBar.Large\"\n" +
    "  android:layout_width=\"wrap_content\"\n" +
    "  android:layout_height=\"wrap_content\"\n" +
    "/>\n";

  @Language("XML")
  private static final String LARGE_PROGRESS_PREVIEW_XML =
    "<ProgressBar\n" +
    "  android:id=\"@+id/LargeProgressBar\"\n" +
    "  style=\"@android:style/Widget.ProgressBar.Large\"\n" +
    "  android:layout_width=\"wrap_content\"\n" +
    "  android:layout_height=\"wrap_content\"\n" +
    "/>\n";

  public void assertLargeProgressBarItem(@NonNull Palette.BaseItem item) {
    checkItem(item, "ProgressBar", "ProgressBar (Large)", AndroidIcons.Views.ProgressBar, LARGE_PROGRESS_XML, LARGE_PROGRESS_PREVIEW_XML,
              LARGE_PROGRESS_XML, IN_PLATFORM, NO_SCALE);
    NlComponent component = createMockComponent("ProgressBar");
    Mockito.when(component.getAttribute(null, TAG_STYLE)).thenReturn(ANDROID_STYLE_RESOURCE_PREFIX + "Widget.ProgressBar.Large");
    checkComponent(component, "ProgressBar (Large)", AndroidIcons.Views.ProgressBar);
  }

  @Language("XML")
  private static final String NORMAL_PROGRESS_XML =
    "<ProgressBar\n" +
    "  style=\"@android:style/Widget.ProgressBar\"\n" +
    "  android:layout_width=\"wrap_content\"\n" +
    "  android:layout_height=\"wrap_content\"\n" +
    "/>\n";

  public void assertNormalProgressBarItem(@NonNull Palette.BaseItem item) {
    checkItem(item, "ProgressBar", "ProgressBar", AndroidIcons.Views.ProgressBar, NORMAL_PROGRESS_XML, NORMAL_PROGRESS_XML,
              NORMAL_PROGRESS_XML, IN_PLATFORM, NO_SCALE);
    checkComponent(createMockComponent("ProgressBar"), "ProgressBar", AndroidIcons.Views.ProgressBar);
  }

  @Language("XML")
  private static final String SMALL_PROGRESS_XML =
    "<ProgressBar\n" +
    "  style=\"@android:style/Widget.ProgressBar.Small\"\n" +
    "  android:layout_width=\"wrap_content\"\n" +
    "  android:layout_height=\"wrap_content\"\n" +
    "/>\n";

  @Language("XML")
  private static final String SMALL_PROGRESS_PREVIEW_XML =
    "<ProgressBar\n" +
    "  android:id=\"@+id/SmallProgressBar\"\n" +
    "  style=\"@android:style/Widget.ProgressBar.Small\"\n" +
    "  android:layout_width=\"wrap_content\"\n" +
    "  android:layout_height=\"wrap_content\"\n" +
    "/>\n";

  public void assertSmallProgressBarItem(@NonNull Palette.BaseItem item) {
    checkItem(item, "ProgressBar", "ProgressBar (Small)", AndroidIcons.Views.ProgressBar, SMALL_PROGRESS_XML, SMALL_PROGRESS_PREVIEW_XML,
              SMALL_PROGRESS_XML, IN_PLATFORM, NO_SCALE);
    NlComponent component = createMockComponent("ProgressBar");
    Mockito.when(component.getAttribute(null, TAG_STYLE)).thenReturn(ANDROID_STYLE_RESOURCE_PREFIX + "Widget.ProgressBar.Small");
    checkComponent(component, "ProgressBar (Small)", AndroidIcons.Views.ProgressBar);
  }

  @Language("XML")
  private static final String HORIZONTAL_PROGRESS_XML =
    "<ProgressBar\n" +
    "  style=\"?android:attr/progressBarStyleHorizontal\"\n" +
    "  android:layout_width=\"wrap_content\"\n" +
    "  android:layout_height=\"wrap_content\"\n" +
    "/>\n";

  @Language("XML")
  private static final String HORIZONTAL_PROGRESS_PREVIEW_XML =
    "<ProgressBar\n" +
    "  android:id=\"@+id/HorizontalProgressBar\"\n" +
    "  style=\"?android:attr/progressBarStyleHorizontal\"\n" +
    "  android:layout_width=\"wrap_content\"\n" +
    "  android:layout_height=\"wrap_content\"\n" +
    "/>\n";

  public void assertHorizontalProgressBarItem(@NonNull Palette.BaseItem item) {
    checkItem(item, "ProgressBar", "ProgressBar (Horizontal)", AndroidIcons.Views.ProgressBar, HORIZONTAL_PROGRESS_XML,
              HORIZONTAL_PROGRESS_PREVIEW_XML, HORIZONTAL_PROGRESS_XML, IN_PLATFORM, 2.0);
    NlComponent component = createMockComponent("ProgressBar");
    Mockito.when(component.getAttribute(null, TAG_STYLE)).thenReturn(ANDROID_STYLE_RESOURCE_PREFIX + "Widget.ProgressBar.Horizontal");
    checkComponent(component, "ProgressBar (Horizontal)", AndroidIcons.Views.ProgressBar);
  }

  public void assertSeekBar(@NonNull Palette.BaseItem item) {
    assertStandardView(item, SEEK_BAR, IN_PLATFORM, NO_SCALE);
  }

  public void assertQuickContactBadge(@NonNull Palette.BaseItem item) {
    assertStandardView(item, "QuickContactBadge", IN_PLATFORM, NO_SCALE);
  }

  public void assertRatingBar(@NonNull Palette.BaseItem item) {
    assertStandardView(item, "RatingBar", IN_PLATFORM, 0.4);
  }

  public void assertSwitch(@NonNull Palette.BaseItem item) {
    assertStandardTextView(item, SWITCH, IN_PLATFORM);
  }

  public void assertSpace(@NonNull Palette.BaseItem item) {
    assertNoPreviewView(item, SPACE, IN_PLATFORM);
  }

  @Language("XML")
  private static final String PLAIN_EDIT_TEXT_XML =
    "<EditText\n" +
    "  android:layout_width=\"wrap_content\"\n" +
    "  android:layout_height=\"wrap_content\"\n" +
    "  android:inputType=\"textPersonName\"\n" +
    "  android:text=\"Name\"\n" +
    "  android:ems=\"10\"\n" +
    "/>\n";

  @Language("XML")
  private static final String PLAIN_EDIT_TEXT_PREVIEW_XML =
    "<EditText\n" +
    "  android:text=\"abc\"\n" +
    "  android:layout_width=\"200dip\"\n" +
    "  android:layout_height=\"wrap_content\">\n" +
    "</EditText>\n";

  public void assertPlainTextEditText(@NonNull Palette.BaseItem item) {
    checkItem(item, EDIT_TEXT, "Plain Text", AndroidIcons.Views.EditText, PLAIN_EDIT_TEXT_XML, PLAIN_EDIT_TEXT_PREVIEW_XML,
              PLAIN_EDIT_TEXT_XML, IN_PLATFORM, 0.8);
    checkComponent(createMockComponent(EDIT_TEXT), "EditText - \"My value for EditText\"", AndroidIcons.Views.EditText);
  }

  public void assertGridLayout(@NonNull Palette.BaseItem item) {
    assertStandardLayout(item, GRID_LAYOUT, IN_PLATFORM);
  }

  public void assertFrameLayout(@NonNull Palette.BaseItem item) {
    assertStandardLayout(item, FRAME_LAYOUT, IN_PLATFORM);
  }

  public void assertLinearLayoutItem(@NonNull Palette.BaseItem item) {
    checkItem(item, LINEAR_LAYOUT, "LinearLayout (horizontal)", AndroidIcons.Views.LinearLayout,
              STANDARD_LAYOUT.getXml(LINEAR_LAYOUT, XmlType.COMPONENT_CREATION), NO_PREVIEW, NO_PREVIEW, IN_PLATFORM, NO_SCALE);
    checkComponent(createMockComponent(LINEAR_LAYOUT), "LinearLayout (horizontal)", AndroidIcons.Views.LinearLayout);
  }

  @Language("XML")
  private static final String VERTICAL_LINEAR_LAYOUT_XML =
    "<LinearLayout\n" +
    "  android:orientation=\"vertical\"\n" +
    "  android:layout_width=\"match_parent\"\n" +
    "  android:layout_height=\"match_parent\">\n" +
    "</LinearLayout>\n";

  public void assertVerticalLinearLayoutItem(@NonNull Palette.BaseItem item) {
    checkItem(item, LINEAR_LAYOUT, "LinearLayout (vertical)", AndroidIcons.Views.VerticalLinearLayout, VERTICAL_LINEAR_LAYOUT_XML,
              NO_PREVIEW, NO_PREVIEW, IN_PLATFORM, NO_SCALE);
    NlComponent component = createMockComponent(LINEAR_LAYOUT);
    Mockito.when(component.getAttribute(ANDROID_URI, ATTR_ORIENTATION)).thenReturn(VALUE_VERTICAL);
    checkComponent(component, "LinearLayout (vertical)", AndroidIcons.Views.VerticalLinearLayout);
  }

  public void assertRelativeLayout(@NonNull Palette.BaseItem item) {
    assertStandardLayout(item, RELATIVE_LAYOUT, IN_PLATFORM);
  }

  public void assertTableLayout(@NonNull Palette.BaseItem item) {
    assertStandardLayout(item, TABLE_LAYOUT, IN_PLATFORM);
  }

  public void assertTableRow(@NonNull Palette.BaseItem item) {
    assertStandardLayout(item, TABLE_ROW, IN_PLATFORM);
  }

  public void assertFragment(@NonNull Palette.BaseItem item) {
    checkItem(item, VIEW_FRAGMENT, "<fragment>", AndroidIcons.Views.Fragment,
              STANDARD_VIEW.getXml(VIEW_FRAGMENT, XmlType.COMPONENT_CREATION), NO_PREVIEW, NO_PREVIEW, IN_PLATFORM, NO_SCALE);
    checkComponent(createMockComponent(VIEW_FRAGMENT), "<fragment>", AndroidIcons.Views.Fragment);
  }

  public void assertRadioGroup(@NonNull Palette.BaseItem item) {
    checkItem(item, RADIO_GROUP, "RadioGroup", AndroidIcons.Views.RadioGroup, STANDARD_VIEW.getXml(RADIO_GROUP, XmlType.COMPONENT_CREATION),
              NO_PREVIEW, NO_PREVIEW, IN_PLATFORM, NO_SCALE);
    checkComponent(createMockComponent(RADIO_GROUP), "RadioGroup (horizontal)", AndroidIcons.Views.RadioGroup);
  }

  @Language("XML")
  private static final String LIST_VIEW_XML =
    "<ListView\n" +
    "  android:layout_width=\"match_parent\"\n" +
    "  android:layout_height=\"match_parent\">\n" +
    "</ListView>\n";

  @Language("XML")
  private static final String LIST_VIEW_PREVIEW_XML =
    "<ListView\n" +
    "  android:id=\"@+id/ListView\"\n" +
    "  android:layout_width=\"200dip\"\n" +
    "  android:layout_height=\"60dip\"\n" +
    "  android:divider=\"#333333\"\n" +
    "  android:dividerHeight=\"1px\">\n" +
    "</ListView>\n";

  public void assertListView(@NonNull Palette.BaseItem item) {
    checkItem(item, LIST_VIEW, "ListView", AndroidIcons.Views.ListView, LIST_VIEW_XML, LIST_VIEW_PREVIEW_XML, LIST_VIEW_PREVIEW_XML,
              IN_PLATFORM, NO_SCALE);
    checkComponent(createMockComponent(LIST_VIEW), "ListView", AndroidIcons.Views.ListView);
  }

  public void assertGridView(@NonNull Palette.BaseItem item) {
    assertStandardLayout(item, GRID_VIEW, IN_PLATFORM);
  }

  @Language("XML")
  private static final String EXPANDABLE_LIST_VIEW_XML =
    "<ExpandableListView\n" +
    "  android:layout_width=\"match_parent\"\n" +
    "  android:layout_height=\"match_parent\">\n" +
    "</ExpandableListView>\n";

  @Language("XML")
  private static final String EXPANDABLE_LIST_VIEW_PREVIEW_XML =
    "<ExpandableListView\n" +
    "  android:id=\"@+id/ExpandableListView\"\n" +
    "  android:layout_width=\"200dip\"\n" +
    "  android:layout_height=\"60dip\"\n" +
    "  android:divider=\"#333333\"\n" +
    "  android:dividerHeight=\"1px\">\n" +
    "</ExpandableListView>\n";

  public void assertExpandableListView(@NonNull Palette.BaseItem item) {
    checkItem(item, EXPANDABLE_LIST_VIEW, "ExpandableListView", AndroidIcons.Views.ExpandableListView, EXPANDABLE_LIST_VIEW_XML,
              EXPANDABLE_LIST_VIEW_PREVIEW_XML, EXPANDABLE_LIST_VIEW_PREVIEW_XML, IN_PLATFORM, NO_SCALE);
    checkComponent(createMockComponent(EXPANDABLE_LIST_VIEW), "ExpandableListView", AndroidIcons.Views.ExpandableListView);
  }

  public void assertScrollView(@NonNull Palette.BaseItem item) {
    assertStandardLayout(item, SCROLL_VIEW, IN_PLATFORM);
  }

  public void assertHorizontalScrollView(@NonNull Palette.BaseItem item) {
    assertStandardLayout(item, HORIZONTAL_SCROLL_VIEW, IN_PLATFORM);
  }

  @Language("XML")
  private static final String TAB_HOST_XML =
    "<TabHost\n" +
    "  android:layout_width=\"200dip\"\n" +
    "  android:layout_height=\"300dip\">\n" +
    "  <LinearLayout\n" +
    "    android:layout_width=\"match_parent\"\n" +
    "    android:layout_height=\"match_parent\"\n" +
    "    android:orientation=\"vertical\">\n" +
    "    <TabWidget\n" +
    "      android:id=\"@android:id/tabs\"\n" +
    "      android:layout_width=\"match_parent\"\n" +
    "      android:layout_height=\"wrap_content\">\n" +
    "    </TabWidget>\n" +
    "    <FrameLayout\n" +
    "      android:id=\"@android:id/tabcontent\"\n" +
    "      android:layout_width=\"match_parent\"\n" +
    "      android:layout_height=\"match_parent\">\n" +
    "      <LinearLayout\n" +
    "        android:id=\"@+id/tab1\"\n" +
    "        android:layout_width=\"match_parent\"\n" +
    "        android:layout_height=\"match_parent\"\n" +
    "        android:orientation=\"vertical\">\n" +
    "      </LinearLayout>\n" +
    "      <LinearLayout\n" +
    "        android:id=\"@+id/tab2\"\n" +
    "        android:layout_width=\"match_parent\"\n" +
    "        android:layout_height=\"match_parent\"\n" +
    "        android:orientation=\"vertical\">\n" +
    "      </LinearLayout>\n" +
    "      <LinearLayout\n" +
    "        android:id=\"@+id/tab3\"\n" +
    "        android:layout_width=\"match_parent\"\n" +
    "        android:layout_height=\"match_parent\"\n" +
    "        android:orientation=\"vertical\">\n" +
    "      </LinearLayout>\n" +
    "    </FrameLayout>\n" +
    "  </LinearLayout>\n" +
    "</TabHost>\n";

  public void assertTabHost(@NonNull Palette.BaseItem item) {
    checkItem(item, TAB_HOST, "TabHost", AndroidIcons.Views.TabHost, TAB_HOST_XML, NO_PREVIEW, TAB_HOST_XML, IN_PLATFORM, NO_SCALE);
    checkComponent(createMockComponent(TAB_HOST), "TabHost", AndroidIcons.Views.TabHost);
  }

  public void assertWebView(@NonNull Palette.BaseItem item) {
    assertStandardLayout(item, WEB_VIEW, IN_PLATFORM);
  }

  public void assertSearchView(@NonNull Palette.BaseItem item) {
    assertStandardLayout(item, "SearchView", IN_PLATFORM);
  }

  @Language("XML")
  private static final String IMAGE_BUTTON_XML =
    "<ImageButton\n" +
    "  android:src=\"@android:drawable/btn_star\"\n" +
    "  android:layout_width=\"wrap_content\"\n" +
    "  android:layout_height=\"wrap_content\">\n" +
    "</ImageButton>\n";

  public void assertImageButton(@NonNull Palette.BaseItem item) {
    checkItem(item, IMAGE_BUTTON, "ImageButton", AndroidIcons.Views.ImageButton, IMAGE_BUTTON_XML, IMAGE_BUTTON_XML, IMAGE_BUTTON_XML,
              IN_PLATFORM, NO_SCALE);
    checkComponent(createMockComponent(IMAGE_BUTTON), "ImageButton", AndroidIcons.Views.ImageButton);
  }

  @Language("XML")
  private static final String IMAGE_VIEW_XML =
    "<ImageView\n" +
    "  android:src=\"@android:drawable/btn_star\"\n" +
    "  android:layout_width=\"wrap_content\"\n" +
    "  android:layout_height=\"wrap_content\">\n" +
    "</ImageView>\n";

  public void assertImageView(@NonNull Palette.BaseItem item) {
    checkItem(item, IMAGE_VIEW, "ImageView", AndroidIcons.Views.ImageView, IMAGE_VIEW_XML, IMAGE_VIEW_XML, IMAGE_VIEW_XML, IN_PLATFORM,
              NO_SCALE);
    checkComponent(createMockComponent(IMAGE_VIEW), "ImageView", AndroidIcons.Views.ImageView);
  }

  public void assertVideoView(@NonNull Palette.BaseItem item) {
    assertNoPreviewView(item, "VideoView", IN_PLATFORM);
  }

  public void assertTimePicker(@NonNull Palette.BaseItem item) {
    assertStandardView(item, "TimePicker", IN_PLATFORM, 0.4);
  }

  public void assertDatePicker(@NonNull Palette.BaseItem item) {
    assertStandardView(item, "DatePicker", IN_PLATFORM, 0.4);
  }

  public void assertCalendarView(@NonNull Palette.BaseItem item) {
    assertStandardView(item, CALENDAR_VIEW, IN_PLATFORM, 0.4);
  }

  public void assertChronometer(@NonNull Palette.BaseItem item) {
    assertStandardView(item, CHRONOMETER, IN_PLATFORM, NO_SCALE);
  }

  public void assertAnalogClock(@NonNull Palette.BaseItem item) {
    assertStandardView(item, "AnalogClock", IN_PLATFORM, 0.6);
  }

  public void assertTextClock(@NonNull Palette.BaseItem item) {
    assertStandardView(item, TEXT_CLOCK, IN_PLATFORM, NO_SCALE);
  }

  public void assertImageSwitcher(@NonNull Palette.BaseItem item) {
    assertStandardLayout(item, IMAGE_SWITCHER, IN_PLATFORM);
  }

  public void assertAdapterViewFlipper(@NonNull Palette.BaseItem item) {
    assertStandardLayout(item, "AdapterViewFlipper", IN_PLATFORM);
  }

  public void assertStackView(@NonNull Palette.BaseItem item) {
    assertStandardLayout(item, STACK_VIEW, IN_PLATFORM);
  }

  public void assertTextSwitcher(@NonNull Palette.BaseItem item) {
    assertStandardLayout(item, TEXT_SWITCHER, IN_PLATFORM);
  }

  public void assertViewAnimator(@NonNull Palette.BaseItem item) {
    assertStandardLayout(item, VIEW_ANIMATOR, IN_PLATFORM);
  }

  public void assertViewFlipper(@NonNull Palette.BaseItem item) {
    assertStandardLayout(item, VIEW_FLIPPER, IN_PLATFORM);
  }

  public void assertViewSwitcher(@NonNull Palette.BaseItem item) {
    assertStandardLayout(item, VIEW_SWITCHER, IN_PLATFORM);
  }

  public void assertIncludeItem(@NonNull Palette.BaseItem item) {
    checkItem(item, VIEW_INCLUDE, "<include>", AndroidIcons.Views.Include, "<include/>\n", NO_PREVIEW, NO_PREVIEW, IN_PLATFORM,
              NO_SCALE);
    checkComponent(createMockComponent(VIEW_INCLUDE), "<include>", AndroidIcons.Views.Include);
  }

  public void assertRequestFocus(@NonNull Palette.BaseItem item) {
    checkItem(item, REQUEST_FOCUS, "<requestFocus>", AndroidIcons.Views.RequestFocus, "<requestFocus/>\n", NO_PREVIEW, NO_PREVIEW,
              IN_PLATFORM, NO_SCALE);
    checkComponent(createMockComponent(REQUEST_FOCUS), "<requestFocus>", AndroidIcons.Views.RequestFocus);
  }

  public void assertViewTag(@NonNull Palette.BaseItem item) {
    checkItem(item, VIEW_TAG, "View", AndroidIcons.Views.Unknown, "<view/>\n", NO_PREVIEW, NO_PREVIEW, IN_PLATFORM, NO_SCALE);
    checkComponent(createMockComponent(VIEW_TAG), "View", AndroidIcons.Views.Unknown);
  }

  public void assertViewStub(@NonNull Palette.BaseItem item) {
    assertNoPreviewView(item, VIEW_STUB, IN_PLATFORM);
  }

  public void assertGestureOverlayView(@NonNull Palette.BaseItem item) {
    assertStandardLayout(item, GESTURE_OVERLAY_VIEW, IN_PLATFORM);
  }

  public void assertTextureView(@NonNull Palette.BaseItem item) {
    assertNoPreviewView(item, TEXTURE_VIEW, IN_PLATFORM);
  }

  public void assertSurfaceView(@NonNull Palette.BaseItem item) {
    assertNoPreviewView(item, SURFACE_VIEW, IN_PLATFORM);
  }

  public void assertNumberPicker(@NonNull Palette.BaseItem item) {
    assertNoPreviewView(item, "NumberPicker", IN_PLATFORM);
  }

  @Language("XML")
  private static final String ZOOM_BUTTON_XML =
    "<ZoomButton\n" +
    "  android:src=\"@android:drawable/btn_plus\"\n" +
    "  android:layout_width=\"wrap_content\"\n" +
    "  android:layout_height=\"wrap_content\">\n" +
    "</ZoomButton>\n";

  public void assertZoomButton(@NonNull Palette.BaseItem item) {
    checkItem(item, ZOOM_BUTTON, "ZoomButton", AndroidIcons.Views.ZoomButton, ZOOM_BUTTON_XML, ZOOM_BUTTON_XML, ZOOM_BUTTON_XML,
              IN_PLATFORM, NO_SCALE);
    checkComponent(createMockComponent(ZOOM_BUTTON), "ZoomButton", AndroidIcons.Views.ZoomButton);
  }

  public void assertZoomControls(@NonNull Palette.BaseItem item) {
    assertStandardView(item, "ZoomControls", IN_PLATFORM, 0.6);
  }

  public void assertDialerFilter(@NonNull Palette.BaseItem item) {
    assertStandardLayout(item, DIALER_FILTER, IN_PLATFORM);
  }

  public void assertAdView(@NonNull Palette.BaseItem item) {
    assertNoPreviewView(item, AD_VIEW, ADS_ARTIFACT);
  }

  public void assertMapFragment(@NonNull Palette.BaseItem item) {
    assertNoPreviewView(item, MAP_FRAGMENT, MAPS_ARTIFACT);
  }

  public void assertMapView(@NonNull Palette.BaseItem item) {
    assertNoPreviewView(item, MAP_VIEW, MAPS_ARTIFACT);
  }

  public void assertCoordinatorLayoutItem(@NonNull Palette.BaseItem item) {
    assertStandardLayout(item, COORDINATOR_LAYOUT, DESIGN_LIB_ARTIFACT);
  }

  public void assertAppBarLayoutItem(@NonNull Palette.BaseItem item) {
    assertStandardLayout(item, APP_BAR_LAYOUT, DESIGN_LIB_ARTIFACT);
  }

  public void assertNestedScrollViewItem(@NonNull Palette.BaseItem item) {
    assertStandardLayout(item, NESTED_SCROLL_VIEW, SUPPORT_LIB_ARTIFACT);
  }

  @Language("XML")
  private static final String FLOATING_ACTION_BUTTON_XML =
    "<android.support.design.widget.FloatingActionButton\n" +
    "  android:src=\"@android:drawable/ic_input_add\"\n" +
    "  android:layout_width=\"wrap_content\"\n" +
    "  android:layout_height=\"wrap_content\"\n" +
    "  android:clickable=\"true\"\n" +
    "  app:fabSize=\"mini\">\n" +
    "</android.support.design.widget.FloatingActionButton>\n";

  public void assertFloatingActionButtonItem(@NonNull Palette.BaseItem item) {
    checkItem(item, FLOATING_ACTION_BUTTON, "FloatingActionButton", AndroidIcons.Views.FloatingActionButton, FLOATING_ACTION_BUTTON_XML,
              FLOATING_ACTION_BUTTON_XML, FLOATING_ACTION_BUTTON_XML, DESIGN_LIB_ARTIFACT, NO_SCALE);
    checkComponent(createMockComponent(FLOATING_ACTION_BUTTON), "FloatingActionButton", AndroidIcons.Views.FloatingActionButton);
  }

  public void assertTextInputLayoutItem(@NonNull Palette.BaseItem item) {
    assertLimitedHeightLayout(item, TEXT_INPUT_LAYOUT, DESIGN_LIB_ARTIFACT);
  }

  public void assertCardView(@NonNull Palette.BaseItem item) {
    assertLimitedHeightLayout(item, CARD_VIEW, CARD_VIEW_LIB_ARTIFACT);
  }

  public void assertGridLayoutV7(@NonNull Palette.BaseItem item) {
    assertStandardLayout(item, GRID_LAYOUT_V7, GRID_LAYOUT_LIB_ARTIFACT);
  }

  public void assertRecyclerView(@NonNull Palette.BaseItem item) {
    assertStandardLayout(item, RECYCLER_VIEW, RECYCLER_VIEW_LIB_ARTIFACT);
  }

  @Language("XML")
  private static final String TOOLBAR_XML =
    "<android.support.v7.widget.Toolbar\n" +
    "  android:layout_width=\"match_parent\"\n" +
    "  android:layout_height=\"wrap_content\"\n" +
    "  android:background=\"?attr/colorPrimary\"\n" +
    "  android:theme=\"?attr/actionBarTheme\"\n" +
    "  android:minHeight=\"?attr/actionBarSize\">\n" +
    "</android.support.v7.widget.Toolbar>\n";

  @Language("XML")
  private static final String TOOLBAR_PREVIEW_XML =
    "<android.support.v7.widget.Toolbar\n" +
    "  android:layout_width=\"match_parent\"\n" +
    "  android:layout_height=\"wrap_content\"\n" +
    "  android:background=\"?attr/colorPrimary\"\n" +
    "  android:theme=\"?attr/actionBarTheme\"\n" +
    "  android:minHeight=\"?attr/actionBarSize\"\n" +
    "  app:contentInsetStart=\"0dp\"\n" +
    "  app:contentInsetLeft=\"0dp\">\n" +
    "  <ImageButton\n" +
    "    android:src=\"?attr/homeAsUpIndicator\"\n" +
    "    android:layout_width=\"wrap_content\"\n" +
    "    android:layout_height=\"wrap_content\"\n" +
    "    android:tint=\"?attr/actionMenuTextColor\"\n" +
    "    android:style=\"?attr/toolbarNavigationButtonStyle\"\n" +
    "  />\n" +
    "  <TextView\n" +
    "    android:text=\"v7 Toolbar\"\n" +
    "    android:textAppearance=\"@style/TextAppearance.Widget.AppCompat.Toolbar.Title\"\n" +
    "    android:layout_width=\"wrap_content\"\n" +
    "    android:layout_height=\"wrap_content\"\n" +
    "    android:gravity=\"center_vertical\"\n" +
    "    android:ellipsize=\"end\"\n" +
    "    android:maxLines=\"1\"\n" +
    "  />\n" +
    "  <ImageButton\n" +
    "    android:src=\"@drawable/abc_ic_menu_moreoverflow_mtrl_alpha\"\n" +
    "    android:layout_width=\"40dp\"\n" +
    "    android:layout_height=\"wrap_content\"\n" +
    "    android:layout_gravity=\"right\"\n" +
    "    android:style=\"?attr/toolbarNavigationButtonStyle\"\n" +
    "    android:tint=\"?attr/actionMenuTextColor\"\n" +
    "  />\n" +
    "</android.support.v7.widget.Toolbar>\n";

  public void assertToolbarV7(@NonNull Palette.BaseItem item) {
    checkItem(item, TOOLBAR_V7, "Toolbar", AndroidIcons.Views.Toolbar, TOOLBAR_XML, TOOLBAR_PREVIEW_XML, TOOLBAR_PREVIEW_XML,
              APPCOMPAT_LIB_ARTIFACT, 0.5);
    checkComponent(createMockComponent(TOOLBAR_V7), "Toolbar", AndroidIcons.Views.Toolbar);
  }

  public void assertBrowseFragment(@NonNull Palette.BaseItem item) {
    assertNoPreviewView(item, BROWSE_FRAGMENT, LEANBACK_V17_ARTIFACT);
  }

  public void assertDetailsFragment(@NonNull Palette.BaseItem item) {
    assertNoPreviewView(item, DETAILS_FRAGMENT, LEANBACK_V17_ARTIFACT);
  }

  public void assertPlaybackOverlayFragment(@NonNull Palette.BaseItem item) {
    assertNoPreviewView(item, PLAYBACK_OVERLAY_FRAGMENT, LEANBACK_V17_ARTIFACT);
  }

  public void assertSearchFragment(@NonNull Palette.BaseItem item) {
    assertNoPreviewView(item, SEARCH_FRAGMENT, LEANBACK_V17_ARTIFACT);
  }

  private void assertStandardView(@NonNull Palette.BaseItem item,
                                  @NonNull String tag,
                                  @NonNull String expectedGradleCoordinate,
                                  double expectedScale) {
    @Language("XML")
    String xml = STANDARD_VIEW.getXml(tag, XmlType.COMPONENT_CREATION);
    checkItem(item, tag, tag, STANDARD_VIEW.getIcon(tag), xml, xml, xml, expectedGradleCoordinate, expectedScale);
    checkComponent(createMockComponent(tag), tag, STANDARD_VIEW.getIcon(tag));
  }

  private void assertStandardLayout(@NonNull Palette.BaseItem item, @NonNull String tag, @NonNull String expectedGradleCoordinate) {
    checkItem(item, tag, STANDARD_VIEW.getTitle(tag), STANDARD_LAYOUT.getIcon(tag), STANDARD_LAYOUT.getXml(tag, XmlType.COMPONENT_CREATION),
              NO_PREVIEW, NO_PREVIEW, expectedGradleCoordinate, NO_SCALE);
    checkComponent(createMockComponent(tag), STANDARD_VIEW.getTitle(tag), STANDARD_LAYOUT.getIcon(tag));
  }

  private void assertNoPreviewView(@NonNull Palette.BaseItem item, @NonNull String tag, @NonNull String expectedGradleCoordinate) {
    checkItem(item, tag, STANDARD_VIEW.getTitle(tag), STANDARD_VIEW.getIcon(tag), STANDARD_VIEW.getXml(tag, XmlType.COMPONENT_CREATION),
              NO_PREVIEW, NO_PREVIEW, expectedGradleCoordinate, NO_SCALE);
    checkComponent(createMockComponent(tag), STANDARD_VIEW.getTitle(tag), STANDARD_VIEW.getIcon(tag));
  }

  private void assertStandardTextView(@NonNull Palette.BaseItem item, @NonNull String tag, @NonNull String expectedGradleCoordinate) {
    @Language("XML")
    String xml = STANDARD_TEXT.getXml(tag, XmlType.COMPONENT_CREATION);
    checkItem(item, tag, STANDARD_TEXT.getTitle(tag), STANDARD_TEXT.getIcon(tag), xml, xml, xml, expectedGradleCoordinate, NO_SCALE);
    checkComponent(createMockComponent(tag), String.format("%1$s - \"My value for %1$s\"", tag), STANDARD_TEXT.getIcon(tag));
  }

  private void assertLimitedHeightLayout(@NonNull Palette.BaseItem item, @NonNull String tag, @NonNull String expectedGradleCoordinate) {
    @Language("XML")
    String xml = String.format(
      "<%1$s\n" +
      "  android:layout_width=\"match_parent\"\n" +
      "  android:layout_height=\"wrap_content\">\n" +
      "</%1$s>\n", tag);
    checkItem(item, tag, STANDARD_VIEW.getTitle(tag), STANDARD_LAYOUT.getIcon(tag), xml, NO_PREVIEW, NO_PREVIEW,
              expectedGradleCoordinate, NO_SCALE);
    checkComponent(createMockComponent(tag), STANDARD_VIEW.getTitle(tag), STANDARD_LAYOUT.getIcon(tag));
  }

  private static void checkItem(@NonNull Palette.BaseItem base,
                                @NonNull String expectedTag,
                                @NonNull String expectedTitle,
                                @NonNull Icon expectedIcon,
                                @NonNull @Language("XML") String expectedXml,
                                @NonNull @Language("XML") String expectedPreviewXml,
                                @NonNull @Language("XML") String expectedDragPreviewXml,
                                @NonNull String expectedGradleCoordinate,
                                double expectedScale) {
    assertTrue(base instanceof Palette.Item);
    Palette.Item item = (Palette.Item)base;

    assertEquals(expectedTag + ".Tag", expectedTag, item.getTagName());
    assertEquals(expectedTag + ".Title", expectedTitle, item.getTitle());
    assertEquals(expectedTag + ".Icon", expectedIcon, item.getIcon());
    assertEquals(expectedTag + ".XML", expectedXml, formatXml(item.getXml()));
    assertEquals(expectedTag + ".PreviewXML", expectedPreviewXml, formatXml(item.getPreviewXml()));
    assertEquals(expectedTag + ".DesignPreviewXML", expectedDragPreviewXml, formatXml(item.getDragPreviewXml()));
    assertEquals(expectedTag + ".GradleCoordinate", expectedGradleCoordinate, item.getGradleCoordinate());
    assertEquals(expectedTag + ".PreviewScale", expectedScale, item.getPreviewScale());
  }

  private void checkComponent(@NonNull NlComponent component, @NonNull String expectedTitle, @NonNull Icon expectedIcon) {
    ViewHandlerManager handlerManager = ViewHandlerManager.get(getProject());
    ViewHandler handler = handlerManager.getHandlerOrDefault(component);
    String title = handler.getTitle(component);
    String attrs = handler.getTitleAttributes(component);
    if (!StringUtil.isEmpty(attrs)) {
      title += " " + attrs;
    }
    assertEquals(component.getTagName() + ".Component.Title", expectedTitle, title);
    assertEquals(component.getTagName() + ".Component.Icon", expectedIcon, handler.getIcon(component));
  }

  private static NlComponent createMockComponent(@NonNull String tag) {
    NlComponent component = Mockito.mock(NlComponent.class);
    Mockito.when(component.getTagName()).thenReturn(tag);
    Mockito.when(component.getAttribute(ANDROID_URI, ATTR_TEXT)).thenReturn("My value for " + tag);
    return component;
  }

  @NonNull
  @Language("XML")
  private static String formatXml(@NonNull @Language("XML") String xml) {
    if (xml.equals(NO_PREVIEW)) {
      return xml;
    }
    StringBuilder text = new StringBuilder();
    int indent = 0;
    for (String line : SPLITTER.split(xml)) {
      if (!line.isEmpty()) {
        boolean decrementIndent = line.startsWith("</") || line.startsWith("/>");
        if (decrementIndent && indent > 0) {
          indent--;
        }
        for (int index = 0; index < indent; index++) {
          text.append("  ");
        }
        text.append(line).append("\n");
        if (!decrementIndent && line.startsWith("<")) {
          indent++;
        }
      }
    }
    return text.toString();
  }
}
