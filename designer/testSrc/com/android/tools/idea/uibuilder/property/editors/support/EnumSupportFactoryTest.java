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

import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.ResourceValueMap;
import com.android.resources.ResourceType;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.property.NlProperty;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.property.editors.support.EnumSupportFactory.*;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class EnumSupportFactoryTest extends AndroidTestCase {

  @Mock
  private NlProperty myProperty;
  @Mock
  private AttributeDefinition myDefinition;
  @Mock
  private NlModel myModel;
  @Mock
  private ResourceResolver myResolver;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    when(myProperty.getModel()).thenReturn(myModel);
    when(myProperty.getDefinition()).thenReturn(myDefinition);
    when(myProperty.getResolver()).thenReturn(myResolver);
    when(myModel.getModule()).thenReturn(myModule);
    when(myModel.getProject()).thenReturn(getProject());
  }

  public void testFontFamily() {
    EnumSupport support = checkSupported(ATTR_FONT_FAMILY, SimpleEnumSupport.class);
    checkPossibleDisplayValues(support, AndroidDomUtil.AVAILABLE_FAMILIES);
  }

  public void testTypeface() {
    EnumSupport support = checkSupported(ATTR_TYPEFACE, SimpleEnumSupport.class);
    checkPossibleDisplayValues(support, AVAILABLE_TYPEFACES);
  }

  public void testTextSize() {
    EnumSupport support = checkSupported(ATTR_TEXT_SIZE, SimpleQuantityEnumSupport.class);
    checkPossibleDisplayValues(support, AVAILABLE_TEXT_SIZES);
  }

  public void testLineSpacing() {
    EnumSupport support = checkSupported(ATTR_LINE_SPACING_EXTRA, SimpleQuantityEnumSupport.class);
    checkPossibleDisplayValues(support, AVAILABLE_LINE_SPACINGS);
  }

  public void testTextAppearance() {
    checkSupported(ATTR_TEXT_APPEARANCE, TextAppearanceEnumSupport.class);
  }

  public void testLayoutHeight() {
    EnumSupport support = checkSupported(ATTR_LAYOUT_HEIGHT, SimpleQuantityEnumSupport.class);
    checkPossibleDisplayValues(support, AVAILABLE_SIZES);
  }

  public void testLayoutWidth() {
    EnumSupport support = checkSupported(ATTR_LAYOUT_WIDTH, SimpleQuantityEnumSupport.class);
    checkPossibleDisplayValues(support, AVAILABLE_SIZES);
  }

  public void testDropdownHeight() {
    EnumSupport support = checkSupported(ATTR_DROPDOWN_HEIGHT, SimpleQuantityEnumSupport.class);
    checkPossibleDisplayValues(support, AVAILABLE_SIZES);
  }

  public void testDropdownWidth() {
    EnumSupport support = checkSupported(ATTR_DROPDOWN_WIDTH, SimpleQuantityEnumSupport.class);
    checkPossibleDisplayValues(support, AVAILABLE_SIZES);
  }

  public void testOnClick() {
    checkSupported(ATTR_ON_CLICK, OnClickEnumSupport.class);
  }

  public void testStyle() {
    StyleResourceValue checkboxStyle = new StyleResourceValue(ResourceType.STYLE, "Widget.CompoundButton.CheckBox", true);
    ResourceValueMap frameworkResources = ResourceValueMap.create();
    frameworkResources.put(checkboxStyle.getName(), checkboxStyle);
    ResourceValueMap projectResources = ResourceValueMap.create();

    when(myProperty.getTagName()).thenReturn(CHECK_BOX);
    when(myResolver.getFrameworkResources()).thenReturn(Collections.singletonMap(ResourceType.STYLE, frameworkResources));
    when(myResolver.getProjectResources()).thenReturn(Collections.singletonMap(ResourceType.STYLE, projectResources));

    checkSupported(ATTR_STYLE, StyleEnumSupport.class);
  }

  public void testTabTextAppearance() {
    checkSupported(ATTR_TAB_TEXT_APPEARANCE, TextAppearanceEnumSupport.class);
  }

  public void testSwitchTextAppearance() {
    checkSupported(ATTR_SWITCH_TEXT_APPEARANCE, TextAppearanceEnumSupport.class);
  }

  public void testItemTextAppearance() {
    checkSupported(ATTR_ITEM_TEXT_APPEARANCE, TextAppearanceEnumSupport.class);
  }

  public void testLayoutToRightOf() {
    checkSupported(ATTR_LAYOUT_TO_RIGHT_OF, IdEnumSupport.class);
  }

  public void testLayoutToLeftOf() {
    checkSupported(ATTR_LAYOUT_TO_LEFT_OF, IdEnumSupport.class);
  }

  public void testLayoutLeftToLeftOf() {
    checkSupported(ATTR_LAYOUT_LEFT_TO_LEFT_OF, IdEnumSupport.class);
  }

  public void testCheckedButton() {
    checkSupported(ATTR_CHECKED_BUTTON, IdEnumSupport.class);
  }

  public void testVisibility() {
    when(myDefinition.getFormats()).thenReturn(Collections.singleton(AttributeFormat.Enum));
    when(myDefinition.getValues()).thenReturn(new String[]{"visible", "invisible", "gone"});
    checkSupported(ATTR_VISIBILITY, AttributeDefinitionEnumSupport.class);
  }

  private EnumSupport checkSupported(@NotNull String propertyName, @NotNull Class<? extends EnumSupport> expectedSupportClass) {
    when(myProperty.getName()).thenReturn(propertyName);
    assertThat(supportsProperty(myProperty)).isTrue();

    EnumSupport support = create(myProperty);
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
