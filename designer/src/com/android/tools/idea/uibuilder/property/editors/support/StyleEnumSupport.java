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

import com.android.annotations.VisibleForTesting;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.tools.idea.common.property.NlProperty;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.android.SdkConstants.*;

public class StyleEnumSupport extends EnumSupport {

  protected final StyleFilter myStyleFilter;

  public StyleEnumSupport(@NotNull NlProperty property) {
    this(property, new StyleFilter(property.getModel().getProject(), property.getResolver()));
  }

  @VisibleForTesting
  StyleEnumSupport(@NotNull NlProperty property, @NotNull StyleFilter styleFilter) {
    super(property);
    myStyleFilter = styleFilter;
  }

  @Override
  @NotNull
  public List<ValueWithDisplayString> getAllValues() {
    String tagName = myProperty.getTagName();
    assert tagName != null;
    return convertStylesToDisplayValues(myStyleFilter.getWidgetStyles(tagName));
  }

  @Override
  @NotNull
  protected ValueWithDisplayString createFromResolvedValue(@NotNull String resolvedValue, @Nullable String value, @Nullable String hint) {
    if (value != null &&
        !value.startsWith(STYLE_RESOURCE_PREFIX) &&
        !value.startsWith(ANDROID_STYLE_RESOURCE_PREFIX) &&
        !value.startsWith(ATTR_REF_PREFIX)) {
      ResourceResolver resolver = myProperty.getResolver();
      ResourceValue resource = resolver.getStyle(value, true);
      String prefix = resource != null ? ANDROID_STYLE_RESOURCE_PREFIX : STYLE_RESOURCE_PREFIX;
      value = prefix + value;
    }
    String display = resolvedValue;
    display = StringUtil.trimStart(display, ANDROID_STYLE_RESOURCE_PREFIX);
    display = StringUtil.trimStart(display, STYLE_RESOURCE_PREFIX);
    return new ValueWithDisplayString(display, value, generateHint(display, value));
  }

  @Nullable
  protected String generateHint(@NotNull String display, @Nullable String value) {
    if (value == null) {
      return "default";
    }
    if (value.endsWith(display)) {
      return null;
    }
    return value;
  }

  @NotNull
  protected List<ValueWithDisplayString> convertStylesToDisplayValues(@NotNull List<StyleResourceValue> styles) {
    List<ValueWithDisplayString> values = new ArrayList<>();
    StyleResourceValue previousStyle = null;
    for (StyleResourceValue style : styles) {
      if (previousStyle != null && (previousStyle.isFramework() != style.isFramework() ||
                                    previousStyle.isUserDefined() != style.isUserDefined())) {
        values.add(ValueWithDisplayString.SEPARATOR);
      }
      previousStyle = style;
      String prefix = style.isFramework() ? ANDROID_STYLE_RESOURCE_PREFIX : STYLE_RESOURCE_PREFIX;
      values.add(createFromResolvedValue(style.getName(), prefix + style.getName(), null));
    }
    return values;
  }
}
