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
package com.android.tools.idea.uibuilder.property.editors.support;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.PropertyTestCase;
import com.google.common.collect.ImmutableList;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.property.editors.support.EnumSupportFactory.*;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class EnumSupportFactoryTest extends PropertyTestCase {
  private static final List<String> AVAILABLE_SIZES_IN_CONSTRAINT_LAYOUT = ImmutableList.of("match_constraint", "wrap_content");

  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  public void testFontFamily() {
    EnumSupport support = checkSupported(myTextView, ATTR_FONT_FAMILY, FontEnumSupport.class);
    checkPossibleDisplayValues(support, AndroidDomUtil.AVAILABLE_FAMILIES);
  }

  public void testTypeface() {
    EnumSupport support = checkSupported(myTextView, ATTR_TYPEFACE, SimpleEnumSupport.class);
    checkPossibleDisplayValues(support, AVAILABLE_TYPEFACES);
  }

  public void testTextSize() {
    EnumSupport support = checkSupported(myTextView, ATTR_TEXT_SIZE, SimpleQuantityEnumSupport.class);
    checkPossibleDisplayValues(support, AVAILABLE_TEXT_SIZES);
  }

  public void testLineSpacing() {
    EnumSupport support = checkSupported(myTextView, ATTR_LINE_SPACING_EXTRA, SimpleQuantityEnumSupport.class);
    checkPossibleDisplayValues(support, AVAILABLE_LINE_SPACINGS);
  }

  public void testTextAppearance() {
    checkSupported(myTextView, ATTR_TEXT_APPEARANCE, TextAppearanceEnumSupport.class);
  }

  public void testLayoutHeight() {
    EnumSupport support = checkSupported(myTextView, ATTR_LAYOUT_HEIGHT, SimpleEnumSupport.class);
    checkPossibleDisplayValues(support, AVAILABLE_SIZES);
  }

  public void testLayoutWidth() {
    EnumSupport support = checkSupported(myTextView, ATTR_LAYOUT_WIDTH, SimpleEnumSupport.class);
    checkPossibleDisplayValues(support, AVAILABLE_SIZES);
  }

  public void testLayoutHeightInConstraintLayout() {
    EnumSupport support = checkSupportedUsingFakeProperty(myButtonInConstraintLayout, ATTR_LAYOUT_HEIGHT, SimpleValuePairEnumSupport.class);
    checkPossibleDisplayValues(support, AVAILABLE_SIZES_IN_CONSTRAINT_LAYOUT);
  }

  public void testLayoutWidthInConstraintLayout() {
    EnumSupport support = checkSupportedUsingFakeProperty(myButtonInConstraintLayout, ATTR_LAYOUT_WIDTH, SimpleValuePairEnumSupport.class);
    checkPossibleDisplayValues(support, AVAILABLE_SIZES_IN_CONSTRAINT_LAYOUT);
  }

  public void testDropdownHeight() {
    EnumSupport support = checkSupported(myAutoCompleteTextView, ATTR_DROPDOWN_HEIGHT, SimpleEnumSupport.class);
    checkPossibleDisplayValues(support, AVAILABLE_SIZES);
  }

  public void testDropdownWidth() {
    EnumSupport support = checkSupported(myAutoCompleteTextView, ATTR_DROPDOWN_WIDTH, SimpleEnumSupport.class);
    checkPossibleDisplayValues(support, AVAILABLE_SIZES);
  }

  public void testOnClick() {
    checkSupported(myButton, ATTR_ON_CLICK, OnClickEnumSupport.class);
  }

  public void testStyle() {
    checkSupported(myButton, ATTR_STYLE, StyleEnumSupport.class);
  }

  public void testTabTextAppearance() {
    checkSupportedUsingFakeProperty(myTabLayout, ATTR_TAB_TEXT_APPEARANCE, TextAppearanceEnumSupport.class);
  }

  public void testSwitchTextAppearance() {
    checkSupported(mySwitch, ATTR_SWITCH_TEXT_APPEARANCE, TextAppearanceEnumSupport.class);
  }

  public void testItemTextAppearance() {
    checkSupportedUsingFakeProperty(myTabLayout, ATTR_ITEM_TEXT_APPEARANCE, TextAppearanceEnumSupport.class);
  }

  public void testLayoutToRightOf() {
    checkSupported(myRelativeLayout, ATTR_LAYOUT_TO_RIGHT_OF, IdEnumSupport.class);
  }

  public void testLayoutToLeftOf() {
    checkSupported(myRelativeLayout, ATTR_LAYOUT_TO_LEFT_OF, IdEnumSupport.class);
  }

  public void testLayoutLeftToLeftOf() {
    checkSupportedUsingFakeProperty(myButtonInConstraintLayout, ATTR_LAYOUT_LEFT_TO_LEFT_OF, IdEnumSupport.class);
  }

  public void testCheckedButton() {
    checkSupported(myRadioGroup, ATTR_CHECKED_BUTTON, IdEnumSupport.class);
  }

  public void testVisibility() {
    EnumSupport support = checkSupported(myTextView, ATTR_VISIBILITY, AttributeDefinitionEnumSupport.class);
    checkPossibleDisplayValues(support, ImmutableList.of("visible", "invisible", "gone"));
  }

  private EnumSupport checkSupported(@NotNull NlComponent component,
                                     @NotNull String propertyName,
                                     @NotNull Class<? extends EnumSupport> expectedSupportClass) {
    NlProperty property = getProperty(component, propertyName);
    assertThat(supportsProperty(property)).isTrue();

    EnumSupport support = create(property);
    assertThat(support).isInstanceOf(expectedSupportClass);
    return support;
  }

  private EnumSupport checkSupportedUsingFakeProperty(@NotNull NlComponent component,
                                                      @NotNull String propertyName,
                                                      @NotNull Class<? extends EnumSupport> expectedSupportClass) {
    AttributeDefinition attribute = new AttributeDefinition(propertyName, null, null, Collections.emptyList());

    NlProperty property = mock(NlProperty.class);
    when(property.getName()).thenReturn(propertyName);
    when(property.getDefinition()).thenReturn(attribute);
    when(property.getComponents()).thenReturn(Collections.singletonList(component));
    when(property.getModel()).thenReturn(myModel);
    when(property.getResolver()).thenReturn(myModel.getConfiguration().getResourceResolver());
    assertThat(supportsProperty(property)).isTrue();

    EnumSupport support = create(property);
    assertThat(support).isInstanceOf(expectedSupportClass);
    return support;
  }

  private static void checkPossibleDisplayValues(@NotNull EnumSupport support, @NotNull List<String> values) {
    List<String> displayValues = support.getAllValues().stream()
      .map(ValueWithDisplayString::getDisplayString)
      .collect(Collectors.toList());
    assertThat(displayValues).containsAllIn(values).inOrder();
  }
}
