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
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;

import static com.android.SdkConstants.*;

public class EditTextHandler extends TextViewHandler {

  private static final Icon DEFAULT_ICON = AndroidIcons.Views.EditText;

  // The style, title, and icon mapping should be same as layout_palette.xml file.
  private enum EditTextInputType {
    PLAIN_TEXT("textPersonName", "Plain Text", DEFAULT_ICON),
    PASSWORD("textPassword", "Password", AndroidIcons.Views.EditTextPassword),
    PASSWORD_NUMERIC("numberPassword", "Password (Numeric)", AndroidIcons.Views.EditTextPasswordNumeric),
    E_MAIL("textEmailAddress", "E-mail", AndroidIcons.Views.EditTextEmail),
    PHONE("phone", "Phone", AndroidIcons.Views.EditTextPhone),
    POSTAL_ADDRESS("textPostalAddress", "Postal Address", AndroidIcons.Views.EditTextPostalAddress),
    MULTILINE_TEXT("textMultiLine", "Multiline Text", AndroidIcons.Views.EditTextMultiline),
    TIME("time", "Time", AndroidIcons.Views.EditTextTime),
    DATE("date", "Date", AndroidIcons.Views.EditTextDate),
    NUMBER("number", "Number", AndroidIcons.Views.EditTextNumber),
    NUMBER_SIGNED("numberSigned", "Number (Signed)", AndroidIcons.Views.EditTextSigned),
    NUMBER_DECIMAL("numberDecimal", "Number (Decimal)", AndroidIcons.Views.EditTextNumberDecimal);

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
}
