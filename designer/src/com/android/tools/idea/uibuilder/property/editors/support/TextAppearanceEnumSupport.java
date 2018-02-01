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
import com.android.ide.common.resources.ResourceResolver;
import com.android.tools.idea.common.property.NlProperty;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.SdkConstants.*;

public class TextAppearanceEnumSupport extends StyleEnumSupport {
  static final String TEXT_APPEARANCE = "TextAppearance";
  static final Pattern TEXT_APPEARANCE_PATTERN = Pattern.compile("^((@(\\w+:)?)style/)?TextAppearance(\\.(.+))?$");

  public TextAppearanceEnumSupport(@NotNull NlProperty property) {
    super(property);
  }

  @VisibleForTesting
  TextAppearanceEnumSupport(@NotNull NlProperty property, @NotNull StyleFilter styleFilter) {
    super(property, styleFilter);
  }

  @Override
  @NotNull
  public List<ValueWithDisplayString> getAllValues() {
    return convertStylesToDisplayValues(myStyleFilter.getStylesDerivedFrom(TEXT_APPEARANCE, true));
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
      if (resource == null) {
        resource = resolver.getStyle(value, false);
      }
      if (resource == null && !value.startsWith(TEXT_APPEARANCE)) {
        resource = resolver.getStyle(TEXT_APPEARANCE + "." + value, true);
      }
      if (resource == null && !value.startsWith(TEXT_APPEARANCE)) {
        resource = resolver.getStyle(TEXT_APPEARANCE + "." + value, false);
      }
      if (resource == null) {
        value = STYLE_RESOURCE_PREFIX + value;
      }
      else {
        value = (resource.isFramework() ? ANDROID_STYLE_RESOURCE_PREFIX : STYLE_RESOURCE_PREFIX) + resource.getName();
      }
    }
    String display = resolvedValue;
    Matcher matcher = TEXT_APPEARANCE_PATTERN.matcher(resolvedValue);
    if (matcher.matches()) {
      display = matcher.group(5);
      if (display == null) {
        display = TEXT_APPEARANCE;
      }
    }
    else {
      display = StringUtil.trimStart(display, ANDROID_STYLE_RESOURCE_PREFIX);
      display = StringUtil.trimStart(display, STYLE_RESOURCE_PREFIX);
    }
    return new ValueWithDisplayString(display, value, generateHint(display, value));
  }
}
