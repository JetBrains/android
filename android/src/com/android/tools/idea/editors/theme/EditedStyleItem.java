/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ItemResourceValue;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Wrapper for {@link com.android.ide.common.rendering.api.ResourceValue} that allows to keep track of modifications and source so we can
 * serialize modifications back to the style file.
 */
public class EditedStyleItem {
  private final ThemeEditorStyle mySourceTheme;
  private ItemResourceValue myItemResourceValue;
  private String myNormalizedValue;
  private boolean myModified;
  // True if the value is a reference and not an actual value.
  private boolean isValueReference;
  private boolean isAttr;

  public EditedStyleItem(@NotNull ItemResourceValue itemResourceValue, @NotNull ThemeEditorStyle sourceTheme) {
    myItemResourceValue = itemResourceValue;
    mySourceTheme = sourceTheme;
    parseValue(myItemResourceValue.getRawXmlValue(), myItemResourceValue.isFramework());
  }

  public void setValue(@Nullable String value) {
    boolean isFramework =
      value != null && (value.startsWith(SdkConstants.ANDROID_PREFIX) || value.startsWith(SdkConstants.ANDROID_THEME_PREFIX));

    myItemResourceValue = new ItemResourceValue(myItemResourceValue.getName(), myItemResourceValue.isFrameworkAttr(), value, isFramework);
    parseValue(myItemResourceValue.getRawXmlValue(), isFramework);
    myModified = true;
  }


  /**
   * Parses the passed value and sets the normalized value string.
   * @param value The possibly non normalized value.
   * @param isFramework True if this value is a framework reference.
   */
  void parseValue(@Nullable String value, boolean isFramework) {
    if (SdkConstants.NULL_RESOURCE.equals(value)) {
      myNormalizedValue = value;
      return;
    }

    ResourceValue resource = ResourceValue.parse(value, true, true, true);
    if (!resource.isValidReference()) {
      myNormalizedValue = value;
      return;
    }

    isValueReference = true;
    isAttr = SdkConstants.RESOURCE_CLZ_ATTR.equals(resource.getResourceType());
    StringBuilder valueBuilder = new StringBuilder().append(resource.getPrefix());
    if (Strings.isNullOrEmpty(resource.getPackage())) {
      // Sometimes framework values won't include the package so we add it here.
      if (isFramework) {
        valueBuilder.append(SdkConstants.ANDROID_PKG).append(':');
      }
    } else {
      valueBuilder.append(resource.getPackage()).append(':');
    }
    valueBuilder.append(resource.getResourceType()).append('/').append(resource.getResourceName());

    myNormalizedValue = valueBuilder.toString();
  }

  @Nullable
  public String getValue() {
    return myNormalizedValue;
  }

  @Nullable
  public String getRawXmlValue() {
    return myNormalizedValue;
  }

  @NotNull
  public String getName() {
    return myItemResourceValue.getName();
  }

  /**
   * Returns whether this value has been modified since this resource value was loaded.
   */
  public boolean isModified() {
    return myModified;
  }

  @NotNull
  public ThemeEditorStyle getSourceStyle() {
    return mySourceTheme;
  }

  @NotNull
  public ItemResourceValue getItemResourceValue() {
    return myItemResourceValue;
  }

  /**
   * Returns whether this attribute value points to a reference.
   */
  public boolean isValueReference() {
    return isValueReference;
  }

  /**
   * Returns whether this attribute value points to an attr reference.
   */
  public boolean isAttr() {
    return isAttr;
  }

  @Override
  public String toString() {
    return String.format("[%1$s] %2$s = %3$s", mySourceTheme, getName(), getValue());
  }

  @NotNull
  public String getQualifiedName() {
    return (getItemResourceValue().isFrameworkAttr() ? SdkConstants.PREFIX_ANDROID : "") + getName();
  }

  public String getAttrPropertyName() {
    if (!isAttr()) {
      return "";
    }

    String propertyName = Splitter.on('/').limit(2).splitToList(getValue()).get(1);
    return (getValue().startsWith(SdkConstants.ANDROID_THEME_PREFIX) ?
      SdkConstants.PREFIX_ANDROID :
      "") + propertyName;
  }
}
