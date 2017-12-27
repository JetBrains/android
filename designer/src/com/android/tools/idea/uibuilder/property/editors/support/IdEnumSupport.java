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
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

import static com.android.SdkConstants.ID_PREFIX;
import static com.android.SdkConstants.NEW_ID_PREFIX;

public class IdEnumSupport extends AttributeDefinitionEnumSupport {

  private final IdAnalyzer myIdAnalyzer;

  public IdEnumSupport(@NotNull NlProperty property) {
    this(property, new IdAnalyzer(property));
  }

  @VisibleForTesting
  public IdEnumSupport(@NotNull NlProperty property, @NotNull IdAnalyzer analyzer) {
    super(property);
    myIdAnalyzer = analyzer;
  }

  @NotNull
  @Override
  public List<ValueWithDisplayString> getAllValues() {
    List<ValueWithDisplayString> values = myIdAnalyzer.findIds().stream()
      .map(id -> new ValueWithDisplayString(isEnumValue(id) ? NEW_ID_PREFIX + id : id, NEW_ID_PREFIX + id))
      .collect(Collectors.toList());
    addAttributeDefinitionValues(values);
    return values;
  }

  @Override
  @NotNull
  protected ValueWithDisplayString createFromResolvedValue(@NotNull String resolvedValue, @Nullable String value, @Nullable String hint) {
    if (value != null && !value.startsWith(NEW_ID_PREFIX) && !value.startsWith(ID_PREFIX) && !isEnumValue(value)) {
      value = NEW_ID_PREFIX + value;
    }
    String display = resolvedValue;
    display = StringUtil.trimStart(display, ID_PREFIX);
    display = StringUtil.trimStart(display, NEW_ID_PREFIX);
    if (isEnumValue(display)) {
      display = resolvedValue;
    }
    return new ValueWithDisplayString(display, value, hint);
  }
}
