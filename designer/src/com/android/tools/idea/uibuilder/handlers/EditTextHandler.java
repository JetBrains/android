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
package com.android.tools.idea.uibuilder.handlers;

import com.android.tools.idea.common.model.NlComponent;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.util.text.StringUtil;
import icons.StudioIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;

import static com.android.SdkConstants.*;

public class EditTextHandler extends TextViewHandler {

  private static final Icon DEFAULT_ICON = StudioIcons.LayoutEditor.Palette.EDIT_TEXT;

  // The style, title, and icon mapping should be same as layout_palette.xml file.
  private enum EditTextInputType {
    PLAIN_TEXT("text", "Plain Text", StudioIcons.LayoutEditor.Palette.TEXTFIELD),
    PASSWORD("textPassword", "Password", StudioIcons.LayoutEditor.Palette.PASSWORD_TEXTFIELD),
    PASSWORD_NUMERIC("numberPassword", "Password (Numeric)", StudioIcons.LayoutEditor.Palette.PASSWORD_NUMERIC_TEXTFIELD),
    EMAIL("textEmailAddress", "E-mail", StudioIcons.LayoutEditor.Palette.EMAIL_TEXTFIELD),
    PHONE("phone", "Phone", StudioIcons.LayoutEditor.Palette.PHONE_TEXTFIELD),
    POSTAL_ADDRESS("textPostalAddress", "Postal Address", StudioIcons.LayoutEditor.Palette.POSTAL_ADDRESS_TEXTFIELD),
    MULTILINE_TEXT("textMultiLine", "Multiline Text", StudioIcons.LayoutEditor.Palette.TEXTFIELD_MULTILINE),
    TIME("time", "Time", StudioIcons.LayoutEditor.Palette.TIME_TEXTFIELD),
    DATE("date", "Date", StudioIcons.LayoutEditor.Palette.DATE_TEXTFIELD),
    NUMBER("number", "Number", StudioIcons.LayoutEditor.Palette.NUMBER_TEXTFIELD),
    NUMBER_SIGNED("numberSigned", "Number (Signed)", StudioIcons.LayoutEditor.Palette.NUMBER_SIGNED_TEXTFIELD),
    NUMBER_DECIMAL("numberDecimal", "Number (Decimal)", StudioIcons.LayoutEditor.Palette.NUMBER_DECIMAL_TEXTFIELD);

    @NotNull String typeString;
    @NotNull String title;
    @NotNull Icon icon;
    EditTextInputType(@NotNull String typeString, @NotNull String title, @NotNull Icon icon) {
      this.typeString = typeString;
      this.title = title;
      this.icon = icon;
    }

    /**
     * Returns the {@link EditTextInputType} of this component.
     * @param component component the node to find the EditText input type from
     * @return One of the {@link EditTextInputType} which represent to the inputTypeString, or null if inputType is unknown.
     */
    @Nullable
    private static EditTextInputType getInputType(@NotNull NlComponent component) {
      String inputTypeString = component.getAttribute(ANDROID_URI, ATTR_INPUT_TYPE);
      return Arrays.stream(values())
        .filter(inputType -> inputType.typeString.equals(inputTypeString))
        .findAny()
        .orElse(null);
    }
  }

  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(
      ATTR_INPUT_TYPE,
      ATTR_HINT,
      ATTR_STYLE,
      ATTR_SINGLE_LINE,
      ATTR_SELECT_ALL_ON_FOCUS);
  }

  @NotNull
  @Override
  public String getTitleAttributes(@NotNull NlComponent component) {
    if (!component.getTagName().equals(EDIT_TEXT)) {
      return super.getTitleAttributes(component);
    }
    String title = super.getTitleAttributes(component);
    if (StringUtil.isEmpty(title)) {
      EditTextInputType inputType = EditTextInputType.getInputType(component);
      title = inputType != null ? String.format("(%s)", inputType.title) : "";
    }
    return title;
  }

  @NotNull
  @Override
  public Icon getIcon(@NotNull NlComponent component) {
    if (!component.getTagName().equals(EDIT_TEXT)) {
      return super.getIcon(component);
    }
    EditTextInputType inputType = EditTextInputType.getInputType(component);
    return inputType != null ? inputType.icon : DEFAULT_ICON;
  }

  @NotNull
  @Override
  public String generateBaseId(@NotNull NlComponent component) {
    String inputType = component.getAndroidAttribute(ATTR_INPUT_TYPE);
    return EDIT_TEXT + (inputType == null ? "" : StringUtil.capitalize(inputType));
  }
}
