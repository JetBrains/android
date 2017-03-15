/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.tools.idea.uibuilder.property.NlProperty;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class FontEnumSupport extends EnumSupport {

  public FontEnumSupport(@NotNull NlProperty property) {
    super(property);
  }

  @NotNull
  @Override
  public List<ValueWithDisplayString> getAllValues() {
    List<ValueWithDisplayString> values = new ArrayList<>();
    for (String stringValue : AndroidDomUtil.AVAILABLE_FAMILIES) {
      values.add(new ValueWithDisplayString(stringValue, stringValue));
    }
    ResourceResolver resolver = myProperty.getResolver();
    if (resolver != null) {
      for (String font : resolver.getProjectResources().get(ResourceType.FONT).keySet()) {
        values.add(new ValueWithDisplayString(font, "@font/" + font));
      }
    }
    return values;
  }

  @NotNull
  @Override
  protected ValueWithDisplayString createFromResolvedValue(@NotNull String resolvedValue, @Nullable String value, @Nullable String hint) {
    return new ValueWithDisplayString(resolvedValue, value);
  }
}
