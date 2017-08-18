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

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.LayoutTestUtilities;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.api.XmlType;
import com.android.tools.idea.uibuilder.handlers.TextViewHandler;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.xml.XmlBuilder;
import com.google.common.base.Splitter;
import com.intellij.openapi.util.text.StringUtil;
import icons.StudioIcons;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.xml.ws.Holder;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.api.PaletteComponentHandler.IN_PLATFORM;
import static com.android.tools.idea.uibuilder.api.PaletteComponentHandler.NO_PREVIEW;
import static org.mockito.Mockito.when;

/**
 * Test case base class with assert methods for checking palette items.
 */
public abstract class PaletteTestCase extends AndroidTestCase {
  private static final ViewHandler STANDARD_VIEW = new ViewHandler();
  private static final ViewHandler STANDARD_TEXT = new TextViewHandler();
  private static final ViewHandler STANDARD_LAYOUT = new ViewGroupHandler();
  private static final Splitter SPLITTER = Splitter.on("\n").trimResults();

  @NotNull
  public static Palette.Item findItem(@NotNull Palette palette, @NotNull String tagName) {
    Holder<Palette.Item> found = new Holder<>();
    palette.accept(item -> {
      if (item.getTagName().equals(tagName)) {
        found.value = item;
      }
    });
    if (found.value == null) {
      throw new RuntimeException("The item: " + tagName + " was not found on the palette.");
    }
    return found.value;
  }

  public static Palette.Group assertIsGroup(@NotNull Palette.BaseItem item, @NotNull String name) {
    assertTrue(item instanceof Palette.Group);
    Palette.Group group = (Palette.Group)item;
    assertEquals(name, group.getName());
    return group;
  }

  public void assertTextViewItem(@NotNull Palette.BaseItem item) {
    assertStandardTextView(item, TEXT_VIEW, IN_PLATFORM);
  }

  public void assertButton(@NotNull Palette.BaseItem item) {
    assertStandardTextView(item, BUTTON, IN_PLATFORM);
  }

  public void assertToggleButton(@NotNull Palette.BaseItem item) {
    assertStandardTextView(item, TOGGLE_BUTTON, IN_PLATFORM);
  }

  public void assertCheckBox(@NotNull Palette.BaseItem item) {
    assertStandardTextView(item, CHECK_BOX, IN_PLATFORM);
  }

  public void assertRadioButton(@NotNull Palette.BaseItem item) {
    assertStandardTextView(item, RADIO_BUTTON, IN_PLATFORM);
  }

  public void assertCheckedTextView(@NotNull Palette.BaseItem item) {
    assertStandardTextView(item, CHECKED_TEXT_VIEW, IN_PLATFORM);
  }

  @Language("XML")
  private static final String SPINNER_XML =
    "<Spinner\n" +
    "  android:layout_width=\"match_parent\"\n" +
    "  android:layout_height=\"wrap_content\" />\n";

  public void assertSpinner(@NotNull Palette.BaseItem item) {
    checkItem(item, SPINNER, "Spinner", StudioIcons.LayoutEditor.Palette.SPINNER, SPINNER_XML, SPINNER_XML, IN_PLATFORM);
    checkComponent(createMockComponent(SPINNER), "Spinner", StudioIcons.LayoutEditor.Palette.SPINNER);
  }

  @Language("XML")
  private static final String NORMAL_PROGRESS_XML =
    "<ProgressBar\n" +
    "  style=\"?android:attr/progressBarStyle\"\n" +
    "  android:layout_width=\"wrap_content\"\n" +
    "  android:layout_height=\"wrap_content\"\n" +
    "/>\n";

  public void assertNormalProgressBarItem(@NotNull Palette.BaseItem item) {
    checkItem(item, "ProgressBar", "ProgressBar", StudioIcons.LayoutEditor.Palette.PROGRESS_BAR, NORMAL_PROGRESS_XML, NORMAL_PROGRESS_XML,
              IN_PLATFORM);
    checkComponent(createMockComponent("ProgressBar"), "ProgressBar", StudioIcons.LayoutEditor.Palette.PROGRESS_BAR);
  }

  @Language("XML")
  private static final String HORIZONTAL_PROGRESS_XML =
    "<ProgressBar\n" +
    "  style=\"?android:attr/progressBarStyleHorizontal\"\n" +
    "  android:layout_width=\"wrap_content\"\n" +
    "  android:layout_height=\"wrap_content\"\n" +
    "/>\n";

  public void assertHorizontalProgressBarItem(@NotNull Palette.BaseItem item) {
    checkItem(item, "ProgressBar", "ProgressBar (Horizontal)", StudioIcons.LayoutEditor.Palette.PROGRESS_BAR_HORIZONTAL,
              HORIZONTAL_PROGRESS_XML, HORIZONTAL_PROGRESS_XML, IN_PLATFORM);
    NlComponent component = createMockComponent("ProgressBar");
    when(component.getAttribute(null, TAG_STYLE)).thenReturn(ANDROID_STYLE_RESOURCE_PREFIX + "Widget.ProgressBar.Horizontal");
    checkComponent(component, "ProgressBar (Horizontal)", StudioIcons.LayoutEditor.Palette.PROGRESS_BAR_HORIZONTAL);
  }

  public void assertSeekBar(@NotNull Palette.BaseItem item) {
    assertStandardView(item, SEEK_BAR, IN_PLATFORM);
    checkComponent(createMockComponent("SeekBar"), "SeekBar", StudioIcons.LayoutEditor.Palette.SEEK_BAR);
  }

  @Language("XML")
  private static final String DISCRETE_SEEK_BAR_XML =
    "<SeekBar\n" +
    "  style=\"@style/Widget.AppCompat.SeekBar.Discrete\"\n" +
    "  android:layout_width=\"wrap_content\"\n" +
    "  android:layout_height=\"wrap_content\"\n" +
    "  android:max=\"10\"\n" +
    "  android:progress=\"3\"\n" +
    "/>\n";

  public void assertDiscreteSeekBar(@NotNull Palette.BaseItem item) {
    checkItem(item, "SeekBar", "SeekBar (Discrete)", StudioIcons.LayoutEditor.Palette.SEEK_BAR_DISCRETE, DISCRETE_SEEK_BAR_XML,
              DISCRETE_SEEK_BAR_XML, IN_PLATFORM);
    NlComponent component = createMockComponent("SeekBar");
    when(component.getAttribute(null, TAG_STYLE)).thenReturn(ANDROID_STYLE_RESOURCE_PREFIX + "Widget.Material.SeekBar.Discrete");
    checkComponent(component, "SeekBar (Discrete)", StudioIcons.LayoutEditor.Palette.SEEK_BAR_DISCRETE);
  }

  @Language("XML")
  private static final String QUICK_CONTACT_BADGE_XML =
    "<QuickContactBadge\n" +
    "    android:src=\"@android:drawable/btn_star\"\n" +
    "    android:layout_width=\"wrap_content\"\n" +
    "    android:layout_height=\"wrap_content\" />\n";

  public void assertQuickContactBadge(@NotNull Palette.BaseItem item) {
    checkItem(item, QUICK_CONTACT_BADGE, "QuickContactBadge", StudioIcons.LayoutEditor.Palette.QUICK_CONTACT_BADGE, QUICK_CONTACT_BADGE_XML,
              QUICK_CONTACT_BADGE_XML, IN_PLATFORM);
    checkComponent(createMockComponent(QUICK_CONTACT_BADGE), "QuickContactBadge", StudioIcons.LayoutEditor.Palette.QUICK_CONTACT_BADGE);
  }

  public void assertRatingBar(@NotNull Palette.BaseItem item) {
    assertStandardView(item, "RatingBar", IN_PLATFORM);
  }

  public void assertSwitch(@NotNull Palette.BaseItem item) {
    assertStandardTextView(item, SWITCH, IN_PLATFORM);
  }

  public void assertSpace(@NotNull Palette.BaseItem item) {
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

  public void assertPlainTextEditText(@NotNull Palette.BaseItem item) {
    checkItem(item, EDIT_TEXT, "Plain Text", StudioIcons.LayoutEditor.Palette.TEXTFIELD, PLAIN_EDIT_TEXT_XML, PLAIN_EDIT_TEXT_XML,
              IN_PLATFORM);
    NlComponent component = createMockComponent(EDIT_TEXT);
    when(component.getAttribute(ANDROID_URI, ATTR_INPUT_TYPE)).thenReturn("textPersonName");
    checkComponent(component, "EditText (Plain Text)", StudioIcons.LayoutEditor.Palette.TEXTFIELD);

    // Check has text attribute case
    when(component.getAttribute(ANDROID_URI, ATTR_TEXT)).thenReturn("My Text Value");
    checkComponent(component, "EditText - \"My Text Value\"", StudioIcons.LayoutEditor.Palette.TEXTFIELD);
  }

  @Language("XML")
  private static final String PASSWORD_EDIT_TEXT_XML =
    "<EditText\n" +
    "  android:layout_width=\"wrap_content\"\n" +
    "  android:layout_height=\"wrap_content\"\n" +
    "  android:inputType=\"textPassword\"\n" +
    "  android:ems=\"10\"\n" +
    "/>\n";

  public void assertPasswordEditText(@NotNull Palette.BaseItem item) {
    checkItem(item, EDIT_TEXT, "Password", StudioIcons.LayoutEditor.Palette.PASSWORD_TEXTFIELD, PASSWORD_EDIT_TEXT_XML,
              PASSWORD_EDIT_TEXT_XML, IN_PLATFORM);
    NlComponent component = createMockComponent(EDIT_TEXT);
    when(component.getAttribute(ANDROID_URI, ATTR_INPUT_TYPE)).thenReturn("textPassword");
    checkComponent(component, "EditText (Password)", StudioIcons.LayoutEditor.Palette.PASSWORD_TEXTFIELD);
  }

  @Language("XML")
  private static final String PASSWORD_NUMERIC_EDIT_TEXT_XML =
    "<EditText\n" +
    "  android:layout_width=\"wrap_content\"\n" +
    "  android:layout_height=\"wrap_content\"\n" +
    "  android:inputType=\"numberPassword\"\n" +
    "  android:ems=\"10\"\n" +
    "/>\n";

  public void assertPasswordNumericEditText(@NotNull Palette.BaseItem item) {
    checkItem(item, EDIT_TEXT, "Password (Numeric)", StudioIcons.LayoutEditor.Palette.PASSWORD_NUMERIC_TEXTFIELD,
              PASSWORD_NUMERIC_EDIT_TEXT_XML, PASSWORD_NUMERIC_EDIT_TEXT_XML, IN_PLATFORM);
    NlComponent component = createMockComponent(EDIT_TEXT);
    when(component.getAttribute(ANDROID_URI, ATTR_INPUT_TYPE)).thenReturn("numberPassword");
    checkComponent(component, "EditText (Password (Numeric))", StudioIcons.LayoutEditor.Palette.PASSWORD_NUMERIC_TEXTFIELD);
  }

  @Language("XML")
  private static final String EMAIL_EDIT_TEXT_XML =
    "<EditText\n" +
    "  android:layout_width=\"wrap_content\"\n" +
    "  android:layout_height=\"wrap_content\"\n" +
    "  android:inputType=\"textEmailAddress\"\n" +
    "  android:ems=\"10\"\n" +
    "/>\n";

  public void assertEmailEditText(@NotNull Palette.BaseItem item) {
    checkItem(item, EDIT_TEXT, "E-mail", StudioIcons.LayoutEditor.Palette.EMAIL_TEXTFIELD, EMAIL_EDIT_TEXT_XML,
              EMAIL_EDIT_TEXT_XML, IN_PLATFORM);
    NlComponent component = createMockComponent(EDIT_TEXT);
    when(component.getAttribute(ANDROID_URI, ATTR_INPUT_TYPE)).thenReturn("textEmailAddress");
    checkComponent(component, "EditText (E-mail)", StudioIcons.LayoutEditor.Palette.EMAIL_TEXTFIELD);
  }

  @Language("XML")
  private static final String PHONE_EDIT_TEXT_XML =
    "<EditText\n" +
    "  android:layout_width=\"wrap_content\"\n" +
    "  android:layout_height=\"wrap_content\"\n" +
    "  android:inputType=\"phone\"\n" +
    "  android:ems=\"10\"\n" +
    "/>\n";

  public void assertPhoneEditText(@NotNull Palette.BaseItem item) {
    checkItem(item, EDIT_TEXT, "Phone", StudioIcons.LayoutEditor.Palette.PHONE_TEXTFIELD, PHONE_EDIT_TEXT_XML, PHONE_EDIT_TEXT_XML,
              IN_PLATFORM);
    NlComponent component = createMockComponent(EDIT_TEXT);
    when(component.getAttribute(ANDROID_URI, ATTR_INPUT_TYPE)).thenReturn("phone");
    checkComponent(component, "EditText (Phone)", StudioIcons.LayoutEditor.Palette.PHONE_TEXTFIELD);
  }

  @Language("XML")
  private static final String POSTAL_ADDRESS_EDIT_TEXT_XML =
    "<EditText\n" +
    "  android:layout_width=\"wrap_content\"\n" +
    "  android:layout_height=\"wrap_content\"\n" +
    "  android:inputType=\"textPostalAddress\"\n" +
    "  android:ems=\"10\"\n" +
    "/>\n";

  public void assertPostalAddressEditText(@NotNull Palette.BaseItem item) {
    checkItem(item, EDIT_TEXT, "Postal Address", StudioIcons.LayoutEditor.Palette.POSTAL_ADDRESS_TEXTFIELD, POSTAL_ADDRESS_EDIT_TEXT_XML,
              POSTAL_ADDRESS_EDIT_TEXT_XML, IN_PLATFORM);
    NlComponent component = createMockComponent(EDIT_TEXT);
    when(component.getAttribute(ANDROID_URI, ATTR_INPUT_TYPE)).thenReturn("textPostalAddress");
    checkComponent(component, "EditText (Postal Address)", StudioIcons.LayoutEditor.Palette.POSTAL_ADDRESS_TEXTFIELD);
  }

  @Language("XML")
  private static final String MULTILINE_TEXT_EDIT_TEXT_XML =
    "<EditText\n" +
    "  android:layout_width=\"wrap_content\"\n" +
    "  android:layout_height=\"wrap_content\"\n" +
    "  android:inputType=\"textMultiLine\"\n" +
    "  android:ems=\"10\"\n" +
    "/>\n";

  public void assertMultilineTextEditText(@NotNull Palette.BaseItem item) {
    checkItem(item, EDIT_TEXT, "Multiline Text", StudioIcons.LayoutEditor.Palette.TEXTFIELD_MULTILINE, MULTILINE_TEXT_EDIT_TEXT_XML,
              MULTILINE_TEXT_EDIT_TEXT_XML, IN_PLATFORM);
    NlComponent component = createMockComponent(EDIT_TEXT);
    when(component.getAttribute(ANDROID_URI, ATTR_INPUT_TYPE)).thenReturn("textMultiLine");
    checkComponent(component, "EditText (Multiline Text)", StudioIcons.LayoutEditor.Palette.TEXTFIELD_MULTILINE);
  }

  @Language("XML")
  private static final String TIME_EDIT_TEXT_XML =
    "<EditText\n" +
    "  android:layout_width=\"wrap_content\"\n" +
    "  android:layout_height=\"wrap_content\"\n" +
    "  android:inputType=\"time\"\n" +
    "  android:ems=\"10\"\n" +
    "/>\n";

  public void assertTimeEditText(@NotNull Palette.BaseItem item) {
    checkItem(item, EDIT_TEXT, "Time", StudioIcons.LayoutEditor.Palette.TIME_TEXTFIELD, TIME_EDIT_TEXT_XML, TIME_EDIT_TEXT_XML,
              IN_PLATFORM);
    NlComponent component = createMockComponent(EDIT_TEXT);
    when(component.getAttribute(ANDROID_URI, ATTR_INPUT_TYPE)).thenReturn("time");
    checkComponent(component, "EditText (Time)", StudioIcons.LayoutEditor.Palette.TIME_TEXTFIELD);
  }

  @Language("XML")
  private static final String DATE_EDIT_TEXT_XML =
    "<EditText\n" +
    "  android:layout_width=\"wrap_content\"\n" +
    "  android:layout_height=\"wrap_content\"\n" +
    "  android:inputType=\"date\"\n" +
    "  android:ems=\"10\"\n" +
    "/>\n";

  public void assertDateEditText(@NotNull Palette.BaseItem item) {
    checkItem(item, EDIT_TEXT, "Date", StudioIcons.LayoutEditor.Palette.DATE_TEXTFIELD, DATE_EDIT_TEXT_XML, DATE_EDIT_TEXT_XML,
              IN_PLATFORM);
    NlComponent component = createMockComponent(EDIT_TEXT);
    when(component.getAttribute(ANDROID_URI, ATTR_INPUT_TYPE)).thenReturn("date");
    checkComponent(component, "EditText (Date)", StudioIcons.LayoutEditor.Palette.DATE_TEXTFIELD);
  }

  @Language("XML")
  private static final String NUMBER_EDIT_TEXT_XML =
    "<EditText\n" +
    "  android:layout_width=\"wrap_content\"\n" +
    "  android:layout_height=\"wrap_content\"\n" +
    "  android:inputType=\"number\"\n" +
    "  android:ems=\"10\"\n" +
    "/>\n";

  public void assertNumberEditText(@NotNull Palette.BaseItem item) {
    checkItem(item, EDIT_TEXT, "Number", StudioIcons.LayoutEditor.Palette.NUMBER_TEXTFIELD, NUMBER_EDIT_TEXT_XML, NUMBER_EDIT_TEXT_XML,
              IN_PLATFORM);
    NlComponent component = createMockComponent(EDIT_TEXT);
    when(component.getAttribute(ANDROID_URI, ATTR_INPUT_TYPE)).thenReturn("number");
    checkComponent(component, "EditText (Number)", StudioIcons.LayoutEditor.Palette.NUMBER_TEXTFIELD);
  }

  @Language("XML")
  private static final String NUMBER_SIGNED_EDIT_TEXT_XML =
    "<EditText\n" +
    "  android:layout_width=\"wrap_content\"\n" +
    "  android:layout_height=\"wrap_content\"\n" +
    "  android:inputType=\"numberSigned\"\n" +
    "  android:ems=\"10\"\n" +
    "/>\n";

  public void assertNumberSignedEditText(@NotNull Palette.BaseItem item) {
    checkItem(item, EDIT_TEXT, "Number (Signed)", StudioIcons.LayoutEditor.Palette.NUMBER_SIGNED_TEXTFIELD, NUMBER_SIGNED_EDIT_TEXT_XML,
              NUMBER_SIGNED_EDIT_TEXT_XML, IN_PLATFORM);
    NlComponent component = createMockComponent(EDIT_TEXT);
    when(component.getAttribute(ANDROID_URI, ATTR_INPUT_TYPE)).thenReturn("numberSigned");
    checkComponent(component, "EditText (Number (Signed))", StudioIcons.LayoutEditor.Palette.NUMBER_SIGNED_TEXTFIELD);
  }

  @Language("XML")
  private static final String NUMBER_DECIMAL_EDIT_TEXT_XML =
    "<EditText\n" +
    "  android:layout_width=\"wrap_content\"\n" +
    "  android:layout_height=\"wrap_content\"\n" +
    "  android:inputType=\"numberDecimal\"\n" +
    "  android:ems=\"10\"\n" +
    "/>\n";

  public void assertNumberDecimalEditText(@NotNull Palette.BaseItem item) {
    checkItem(item, EDIT_TEXT, "Number (Decimal)", StudioIcons.LayoutEditor.Palette.NUMBER_DECIMAL_TEXTFIELD, NUMBER_DECIMAL_EDIT_TEXT_XML,
              NUMBER_DECIMAL_EDIT_TEXT_XML, IN_PLATFORM);
    NlComponent component = createMockComponent(EDIT_TEXT);
    when(component.getAttribute(ANDROID_URI, ATTR_INPUT_TYPE)).thenReturn("numberDecimal");
    checkComponent(component, "EditText (Number (Decimal))", StudioIcons.LayoutEditor.Palette.NUMBER_DECIMAL_TEXTFIELD);
  }

  final void assertConstraintLayout(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, "android.support.constraint.ConstraintLayout", CONSTRAINT_LAYOUT_LIB_ARTIFACT);
  }

  public void assertGridLayout(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, GRID_LAYOUT, IN_PLATFORM);
  }

  public void assertFlexboxLayout(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, FLEXBOX_LAYOUT, FLEXBOX_LAYOUT_LIB_ARTIFACT);
  }

  public void assertFrameLayout(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, FRAME_LAYOUT, IN_PLATFORM);
  }

  @Language("XML")
  private static final String HORIZONTAL_LINEAR_LAYOUT_XML =
    "<LinearLayout\n" +
    "  android:orientation=\"horizontal\"\n" +
    "  android:layout_width=\"match_parent\"\n" +
    "  android:layout_height=\"match_parent\">\n" +
    "</LinearLayout>\n";

  public void assertLinearLayoutItem(@NotNull Palette.BaseItem item) {
    checkItem(item, LINEAR_LAYOUT, "LinearLayout (horizontal)", StudioIcons.LayoutEditor.Palette.LINEAR_LAYOUT_HORZ,
              HORIZONTAL_LINEAR_LAYOUT_XML, NO_PREVIEW, IN_PLATFORM);
    checkComponent(createMockComponent(LINEAR_LAYOUT), "LinearLayout (horizontal)", StudioIcons.LayoutEditor.Palette.LINEAR_LAYOUT_HORZ);
  }

  @Language("XML")
  private static final String VERTICAL_LINEAR_LAYOUT_XML =
    "<LinearLayout\n" +
    "  android:orientation=\"vertical\"\n" +
    "  android:layout_width=\"match_parent\"\n" +
    "  android:layout_height=\"match_parent\">\n" +
    "</LinearLayout>\n";

  public void assertVerticalLinearLayoutItem(@NotNull Palette.BaseItem item) {
    checkItem(item, LINEAR_LAYOUT, "LinearLayout (vertical)", StudioIcons.LayoutEditor.Palette.LINEAR_LAYOUT_VERT,
              VERTICAL_LINEAR_LAYOUT_XML, NO_PREVIEW, IN_PLATFORM);
    NlComponent component = createMockComponent(LINEAR_LAYOUT);
    when(component.resolveAttribute(ANDROID_URI, ATTR_ORIENTATION)).thenReturn(VALUE_VERTICAL);
    checkComponent(component, "LinearLayout (vertical)", StudioIcons.LayoutEditor.Palette.LINEAR_LAYOUT_VERT);
  }

  public void assertRelativeLayout(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, RELATIVE_LAYOUT, IN_PLATFORM);
  }

  public void assertTableLayout(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, TABLE_LAYOUT, IN_PLATFORM);
  }

  public void assertTableRow(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, TABLE_ROW, IN_PLATFORM);
  }

  public void assertFragment(@NotNull Palette.BaseItem item) {
    checkItem(item, VIEW_FRAGMENT, "<fragment>", StudioIcons.LayoutEditor.Palette.FRAGMENT,
              STANDARD_VIEW.getXml(VIEW_FRAGMENT, XmlType.COMPONENT_CREATION), NO_PREVIEW, IN_PLATFORM);
    checkComponent(createMockComponent(VIEW_FRAGMENT), "<fragment>", StudioIcons.LayoutEditor.Palette.FRAGMENT);
  }

  public void assertRadioGroup(@NotNull Palette.BaseItem item) {
    checkItem(item, RADIO_GROUP, "RadioGroup", StudioIcons.LayoutEditor.Palette.RADIO_GROUP,
              STANDARD_VIEW.getXml(RADIO_GROUP, XmlType.COMPONENT_CREATION), NO_PREVIEW, IN_PLATFORM);
    checkComponent(createMockComponent(RADIO_GROUP), "RadioGroup (horizontal)", StudioIcons.LayoutEditor.Palette.RADIO_GROUP);
  }

  @Language("XML")
  private static final String LIST_VIEW_XML =
    "<ListView\n" +
    "  android:layout_width=\"match_parent\"\n" +
    "  android:layout_height=\"match_parent\" />\n";

  @Language("XML")
  private static final String LIST_VIEW_PREVIEW_XML =
    "<ListView\n" +
    "  android:id=\"@+id/ListView\"\n" +
    "  android:layout_width=\"200dip\"\n" +
    "  android:layout_height=\"60dip\"\n" +
    "  android:divider=\"#333333\"\n" +
    "  android:dividerHeight=\"1px\" />\n";

  public void assertListView(@NotNull Palette.BaseItem item) {
    checkItem(item, LIST_VIEW, "ListView", StudioIcons.LayoutEditor.Palette.LIST_VIEW, LIST_VIEW_XML, LIST_VIEW_PREVIEW_XML, IN_PLATFORM);
    checkComponent(createMockComponent(LIST_VIEW), "ListView", StudioIcons.LayoutEditor.Palette.LIST_VIEW);
  }

  public void assertGridView(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, GRID_VIEW, IN_PLATFORM);
  }

  @Language("XML")
  private static final String EXPANDABLE_LIST_VIEW_XML =
    "<ExpandableListView\n" +
    "  android:layout_width=\"match_parent\"\n" +
    "  android:layout_height=\"match_parent\" />\n";

  @Language("XML")
  private static final String EXPANDABLE_LIST_VIEW_PREVIEW_XML =
    "<ExpandableListView\n" +
    "  android:id=\"@+id/ExpandableListView\"\n" +
    "  android:layout_width=\"200dip\"\n" +
    "  android:layout_height=\"60dip\"\n" +
    "  android:divider=\"#333333\"\n" +
    "  android:dividerHeight=\"1px\" />\n";

  public void assertExpandableListView(@NotNull Palette.BaseItem item) {
    checkItem(item, EXPANDABLE_LIST_VIEW, "ExpandableListView", StudioIcons.LayoutEditor.Palette.EXPANDABLE_LIST_VIEW,
              EXPANDABLE_LIST_VIEW_XML, EXPANDABLE_LIST_VIEW_PREVIEW_XML, IN_PLATFORM);
    checkComponent(createMockComponent(EXPANDABLE_LIST_VIEW), "ExpandableListView", StudioIcons.LayoutEditor.Palette.EXPANDABLE_LIST_VIEW);
  }

  public void assertScrollView(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, SCROLL_VIEW, IN_PLATFORM);
  }

  public void assertHorizontalScrollView(@NotNull Palette.BaseItem item) {
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
    "      android:layout_height=\"wrap_content\" />\n" +
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

  public void assertTabHost(@NotNull Palette.BaseItem item) {
    checkItem(item, TAB_HOST, "TabHost", StudioIcons.LayoutEditor.Palette.TAB_HOST, TAB_HOST_XML, TAB_HOST_XML, IN_PLATFORM);
    checkComponent(createMockComponent(TAB_HOST), "TabHost", StudioIcons.LayoutEditor.Palette.TAB_HOST);
  }

  public void assertWebView(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, WEB_VIEW, IN_PLATFORM);
  }

  public void assertSearchView(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, "SearchView", IN_PLATFORM);
  }

  public void assertViewPager(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, "android.support.v4.view.ViewPager", SUPPORT_LIB_ARTIFACT);
  }

  @Language("XML")
  private static final String IMAGE_BUTTON_XML =
    "<ImageButton\n" +
    "    android:src=\"@android:drawable/btn_star\"\n" +
    "    android:layout_width=\"wrap_content\"\n" +
    "    android:layout_height=\"wrap_content\" />\n";

  public void assertImageButton(@NotNull Palette.BaseItem item) {
    checkItem(item, IMAGE_BUTTON, "ImageButton", StudioIcons.LayoutEditor.Palette.IMAGE_BUTTON, IMAGE_BUTTON_XML, IMAGE_BUTTON_XML,
              IN_PLATFORM);
    checkComponent(createMockComponent(IMAGE_BUTTON), "ImageButton", StudioIcons.LayoutEditor.Palette.IMAGE_BUTTON);
  }

  @Language("XML")
  private static final String IMAGE_VIEW_XML =
    "<ImageView\n" +
    "    android:src=\"@android:drawable/btn_star\"\n" +
    "    android:layout_width=\"wrap_content\"\n" +
    "    android:layout_height=\"wrap_content\" />\n";

  public void assertImageView(@NotNull Palette.BaseItem item) {
    checkItem(item, IMAGE_VIEW, "ImageView", StudioIcons.LayoutEditor.Palette.IMAGE_VIEW, IMAGE_VIEW_XML, IMAGE_VIEW_XML, IN_PLATFORM);
    checkComponent(createMockComponent(IMAGE_VIEW), "ImageView", StudioIcons.LayoutEditor.Palette.IMAGE_VIEW);
  }

  public void assertVideoView(@NotNull Palette.BaseItem item) {
    assertNoPreviewView(item, "VideoView", IN_PLATFORM);
  }

  public void assertTimePicker(@NotNull Palette.BaseItem item) {
    assertStandardView(item, "TimePicker", IN_PLATFORM);
  }

  public void assertDatePicker(@NotNull Palette.BaseItem item) {
    assertStandardView(item, "DatePicker", IN_PLATFORM);
  }

  public void assertCalendarView(@NotNull Palette.BaseItem item) {
    assertStandardView(item, CALENDAR_VIEW, IN_PLATFORM);
  }

  public void assertChronometer(@NotNull Palette.BaseItem item) {
    assertStandardView(item, CHRONOMETER, IN_PLATFORM);
  }

  public void assertTextClock(@NotNull Palette.BaseItem item) {
    assertStandardView(item, TEXT_CLOCK, IN_PLATFORM);
  }

  public void assertImageSwitcher(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, IMAGE_SWITCHER, IN_PLATFORM);
  }

  public void assertAdapterViewFlipper(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, "AdapterViewFlipper", IN_PLATFORM);
  }

  public void assertStackView(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, STACK_VIEW, IN_PLATFORM);
  }

  public void assertTextSwitcher(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, TEXT_SWITCHER, IN_PLATFORM);
  }

  public void assertViewAnimator(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, VIEW_ANIMATOR, IN_PLATFORM);
  }

  public void assertViewFlipper(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, VIEW_FLIPPER, IN_PLATFORM);
  }

  public void assertViewSwitcher(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, VIEW_SWITCHER, IN_PLATFORM);
  }

  public void assertIncludeItem(@NotNull Palette.BaseItem item) {
    checkItem(item, VIEW_INCLUDE, "<include>", StudioIcons.LayoutEditor.Palette.INCLUDE, "<include/>\n", NO_PREVIEW, IN_PLATFORM);
    checkComponent(createMockComponent(VIEW_INCLUDE), "<include>", StudioIcons.LayoutEditor.Palette.INCLUDE);
  }

  public void assertRequestFocus(@NotNull Palette.BaseItem item) {
    checkItem(item, REQUEST_FOCUS, "<requestFocus>", StudioIcons.LayoutEditor.Palette.REQUEST_FOCUS, "<requestFocus/>\n", NO_PREVIEW,
              IN_PLATFORM);
    checkComponent(createMockComponent(REQUEST_FOCUS), "<requestFocus>", StudioIcons.LayoutEditor.Palette.REQUEST_FOCUS);
  }

  public void assertViewTag(@NotNull Palette.BaseItem item) {
    checkItem(item, VIEW_TAG, "<view>", StudioIcons.LayoutEditor.Palette.VIEW, "<view/>\n", NO_PREVIEW, IN_PLATFORM);
    checkComponent(createMockComponent(VIEW_TAG), "<view>", StudioIcons.LayoutEditor.Palette.VIEW);
  }

  public void assertViewStub(@NotNull Palette.BaseItem item) {
    assertNoPreviewView(item, VIEW_STUB, IN_PLATFORM);
  }

  public void assertTextureView(@NotNull Palette.BaseItem item) {
    assertNoPreviewView(item, TEXTURE_VIEW, IN_PLATFORM);
  }

  public void assertSurfaceView(@NotNull Palette.BaseItem item) {
    assertNoPreviewView(item, SURFACE_VIEW, IN_PLATFORM);
  }

  public void assertNumberPicker(@NotNull Palette.BaseItem item) {
    assertNoPreviewView(item, "NumberPicker", IN_PLATFORM);
  }

  public void assertAdView(@NotNull Palette.BaseItem item) {
    assertNoPreviewView(item, AD_VIEW, ADS_ARTIFACT);
  }

  public void assertMapView(@NotNull Palette.BaseItem item) {
    assertNoPreviewView(item, MAP_VIEW, MAPS_ARTIFACT);
  }

  public void assertCoordinatorLayoutItem(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, COORDINATOR_LAYOUT, DESIGN_LIB_ARTIFACT);
  }

  public void assertAppBarLayoutItem(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, APP_BAR_LAYOUT, DESIGN_LIB_ARTIFACT);
  }

  public void assertTabLayout(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, TAB_LAYOUT, DESIGN_LIB_ARTIFACT);
  }

  public void assertTabItem(@NotNull Palette.BaseItem item) {
    assertNoPreviewView(item, TAB_ITEM, DESIGN_LIB_ARTIFACT);
  }

  public void assertNestedScrollViewItem(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, NESTED_SCROLL_VIEW, SUPPORT_LIB_ARTIFACT);
  }

  @Language("XML")
  private static final String FLOATING_ACTION_BUTTON_XML =
    "<android.support.design.widget.FloatingActionButton\n" +
    "  android:src=\"@android:drawable/ic_input_add\"\n" +
    "  android:layout_width=\"wrap_content\"\n" +
    "  android:layout_height=\"wrap_content\"\n" +
    "  android:clickable=\"true\" />\n";

  public void assertFloatingActionButtonItem(@NotNull Palette.BaseItem item) {
    checkItem(item, FLOATING_ACTION_BUTTON, "FloatingActionButton", StudioIcons.LayoutEditor.Palette.FLOATING_ACTION_BUTTON,
              FLOATING_ACTION_BUTTON_XML, FLOATING_ACTION_BUTTON_XML, DESIGN_LIB_ARTIFACT);
    checkComponent(createMockComponent(FLOATING_ACTION_BUTTON), "FloatingActionButton",
                   StudioIcons.LayoutEditor.Palette.FLOATING_ACTION_BUTTON);
  }

  public void assertTextInputLayoutItem(@NotNull Palette.BaseItem item) {
    checkItem(item, TEXT_INPUT_LAYOUT, STANDARD_VIEW.getTitle(TEXT_INPUT_LAYOUT), STANDARD_LAYOUT.getIcon(TEXT_INPUT_LAYOUT),
              TEXT_INPUT_LAYOUT_XML, NO_PREVIEW, DESIGN_LIB_ARTIFACT);
  }

  public void assertCardView(@NotNull Palette.BaseItem item) {
    assertLimitedHeightLayout(item, CARD_VIEW, CARD_VIEW_LIB_ARTIFACT);
  }

  public void assertGridLayoutV7(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, GRID_LAYOUT_V7, GRID_LAYOUT_LIB_ARTIFACT);
  }

  public void assertRecyclerView(@NotNull Palette.BaseItem item) {
    assertStandardLayout(item, RECYCLER_VIEW, RECYCLER_VIEW_LIB_ARTIFACT);
  }

  @Language("XML")
  private static final String TEXT_INPUT_LAYOUT_XML =
    "<android.support.design.widget.TextInputLayout\n" +
    "  android:layout_width=\"match_parent\"\n" +
    "  android:layout_height=\"wrap_content\">\n" +
    "  <android.support.design.widget.TextInputEditText\n" +
    "    android:layout_width=\"match_parent\"\n" +
    "    android:layout_height=\"wrap_content\"\n" +
    "    android:hint=\"hint\" />\n" +
    "  </android.support.design.widget.TextInputLayout>\n";

  @Language("XML")
  private static final String TOOLBAR_XML =
    "<android.support.v7.widget.Toolbar\n" +
    "  android:layout_width=\"match_parent\"\n" +
    "  android:layout_height=\"wrap_content\"\n" +
    "  android:background=\"?attr/colorPrimary\"\n" +
    "  android:theme=\"?attr/actionBarTheme\"\n" +
    "  android:minHeight=\"?attr/actionBarSize\" />\n";

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
    "    android:style=\"?attr/toolbarNavigationButtonStyle\" />\n" +
    "  <TextView\n" +
    "    android:text=\"v7 Toolbar\"\n" +
    "    android:textAppearance=\"@style/TextAppearance.Widget.AppCompat.Toolbar.Title\"\n" +
    "    android:layout_width=\"wrap_content\"\n" +
    "    android:layout_height=\"wrap_content\"\n" +
    "    android:gravity=\"center_vertical\"\n" +
    "    android:ellipsize=\"end\"\n" +
    "    android:maxLines=\"1\" />\n" +
    "  <ImageButton\n" +
    "    android:src=\"@drawable/abc_ic_menu_moreoverflow_mtrl_alpha\"\n" +
    "    android:layout_width=\"40dp\"\n" +
    "    android:layout_height=\"wrap_content\"\n" +
    "    android:layout_gravity=\"right\"\n" +
    "    android:style=\"?attr/toolbarNavigationButtonStyle\"\n" +
    "    android:tint=\"?attr/actionMenuTextColor\" />\n" +
    "</android.support.v7.widget.Toolbar>\n";

  public void assertToolbarV7(@NotNull Palette.BaseItem item) {
    checkItem(item, TOOLBAR_V7, "Toolbar", StudioIcons.LayoutEditor.Palette.TOOLBAR, TOOLBAR_XML, TOOLBAR_PREVIEW_XML,
              APPCOMPAT_LIB_ARTIFACT);
    checkComponent(createMockComponent(TOOLBAR_V7), "Toolbar", StudioIcons.LayoutEditor.Palette.TOOLBAR);
  }

  private void assertStandardView(@NotNull Palette.BaseItem item,
                                  @NotNull String tag,
                                  @NotNull String expectedGradleCoordinate) {
    @Language("XML")
    String xml = STANDARD_VIEW.getXml(tag, XmlType.COMPONENT_CREATION);
    checkItem(item, tag, STANDARD_VIEW.getTitle(tag), STANDARD_VIEW.getIcon(tag), xml, xml, expectedGradleCoordinate);
    checkComponent(createMockComponent(tag), STANDARD_VIEW.getTitle(tag), STANDARD_VIEW.getIcon(tag));
  }

  private void assertStandardLayout(@NotNull Palette.BaseItem item, @NotNull String tag, @NotNull String expectedGradleCoordinate) {
    checkItem(item, tag, STANDARD_VIEW.getTitle(tag), STANDARD_LAYOUT.getIcon(tag), STANDARD_LAYOUT.getXml(tag, XmlType.COMPONENT_CREATION),
              NO_PREVIEW, expectedGradleCoordinate);
    checkComponent(createMockComponent(tag), STANDARD_VIEW.getTitle(tag), STANDARD_LAYOUT.getIcon(tag));
  }

  private void assertNoPreviewView(@NotNull Palette.BaseItem item, @NotNull String tag, @NotNull String expectedGradleCoordinate) {
    checkItem(item, tag, STANDARD_VIEW.getTitle(tag), STANDARD_VIEW.getIcon(tag), STANDARD_VIEW.getXml(tag, XmlType.COMPONENT_CREATION),
              NO_PREVIEW, expectedGradleCoordinate);
    checkComponent(createMockComponent(tag), STANDARD_VIEW.getTitle(tag), STANDARD_VIEW.getIcon(tag));
  }

  private void assertStandardTextView(@NotNull Palette.BaseItem item, @NotNull String tag, @NotNull String expectedGradleCoordinate) {
    @Language("XML")
    String xml = STANDARD_TEXT.getXml(tag, XmlType.COMPONENT_CREATION);
    checkItem(item, tag, STANDARD_TEXT.getTitle(tag), STANDARD_TEXT.getIcon(tag), xml, xml, expectedGradleCoordinate);
    NlComponent component = createMockComponent(tag);
    checkComponent(component, String.format("%1$s", tag), STANDARD_TEXT.getIcon(tag));

    when(component.getAttribute(ANDROID_URI, ATTR_TEXT)).thenReturn("My value for " + tag);
    checkComponent(component, String.format("%1$s - \"My value for %1$s\"", tag), STANDARD_TEXT.getIcon(tag));
  }

  private void assertLimitedHeightLayout(@NotNull Palette.BaseItem item, @NotNull String tag, @NotNull String expectedGradleCoordinate) {
    @Language("XML")
    String xml = new XmlBuilder()
      .startTag(tag)
      .androidAttribute(ATTR_LAYOUT_WIDTH, VALUE_MATCH_PARENT)
      .androidAttribute(ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT)
      .endTag(tag)
      .toString();

    checkItem(item, tag, STANDARD_VIEW.getTitle(tag), STANDARD_LAYOUT.getIcon(tag), xml, NO_PREVIEW, expectedGradleCoordinate);
    checkComponent(createMockComponent(tag), STANDARD_VIEW.getTitle(tag), STANDARD_LAYOUT.getIcon(tag));
  }

  private static void checkItem(@NotNull Palette.BaseItem base,
                                @NotNull String expectedTag,
                                @NotNull String expectedTitle,
                                @NotNull Icon expectedIcon,
                                @NotNull @Language("XML") String expectedXml,
                                @NotNull @Language("XML") String expectedDragPreviewXml,
                                @NotNull String expectedGradleCoordinateId) {
    assertTrue(base instanceof Palette.Item);
    Palette.Item item = (Palette.Item)base;

    assertEquals(expectedTag + ".Tag", expectedTag, item.getTagName());
    assertEquals(expectedTag + ".Title", expectedTitle, item.getTitle());
    assertEquals(expectedTag + ".Icon", expectedIcon, item.getIcon());
    assertEquals(expectedTag + ".XML", formatXml(expectedXml), formatXml(item.getXml()));
    assertEquals(expectedTag + ".DragPreviewXML", formatXml(expectedDragPreviewXml), formatXml(item.getDragPreviewXml()));
    assertEquals(expectedTag + ".GradleCoordinateId", expectedGradleCoordinateId, item.getGradleCoordinateId());
  }

  private void checkComponent(@NotNull NlComponent component, @NotNull String expectedTitle, @NotNull Icon expectedIcon) {
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

  private static NlComponent createMockComponent(@NotNull String tag) {
    NlComponent component = LayoutTestUtilities.createMockComponent();
    when(component.getTagName()).thenReturn(tag);
    return component;
  }

  @NotNull
  @Language("XML")
  private static String formatXml(@NotNull @Language("XML") String xml) {
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
