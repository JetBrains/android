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

import com.android.tools.idea.uibuilder.property.NlProperty;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class AttributeDefinitionEnumSupport extends EnumSupport {

  public AttributeDefinitionEnumSupport(@NotNull NlProperty property) {
    super(property);
  }

  @Override
  @NotNull
  public List<ValueWithDisplayString> getAllValues() {
    List<ValueWithDisplayString> values = new ArrayList<>();
    addAttributeDefinitionValues(values);
    return values;
  }

  protected void addAttributeDefinitionValues(@NotNull List<ValueWithDisplayString> values) {
    AttributeDefinition definition = myProperty.getDefinition();
    if (definition == null) {
      return;
    }
    for (String stringValue : definition.getValues()) {
      values.add(new ValueWithDisplayString(stringValue, stringValue));
    }
  }

  protected boolean isEnumValue(@NotNull String value) {
    AttributeDefinition definition = myProperty.getDefinition();
    if (definition == null) {
      return false;
    }
    for (String stringValue : definition.getValues()) {
      if (value.equals(stringValue)) {
        return true;
      }
    }
    return false;
  }
}
