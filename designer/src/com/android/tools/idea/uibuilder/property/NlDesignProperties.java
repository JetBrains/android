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
package com.android.tools.idea.uibuilder.property;

import com.android.tools.idea.uibuilder.model.NlComponent;
import com.google.common.collect.ImmutableList;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.ToolsAttributeUtil;
import org.jetbrains.android.dom.converters.StaticEnumConverter;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

import static com.android.SdkConstants.*;

public class NlDesignProperties {
  private final AttributeDefinition myContextDefinition;
  private final AttributeDefinition myListItemDefinition;
  private final AttributeDefinition myShowInItemDefinition;
  private final AttributeDefinition myOpenDrawerItemDefinition;
  private final AttributeDefinition myLayoutDefinition;

  public NlDesignProperties() {
    myContextDefinition = getDefinitionByName(ATTR_CONTEXT);
    myListItemDefinition = getDefinitionByName(ATTR_LISTITEM);
    myShowInItemDefinition = getDefinitionByName(ATTR_SHOW_IN);
    myOpenDrawerItemDefinition = getDefinitionByName(ATTR_OPEN_DRAWER);
    myLayoutDefinition = getDefinitionByName(ATTR_LAYOUT);
  }

  @NotNull
  public List<NlProperty> getKnownProperties(@NotNull List<NlComponent> components) {
    return ImmutableList.of(
      new NlPropertyItem(components, TOOLS_URI, myContextDefinition),
      new NlPropertyItem(components, TOOLS_URI, myListItemDefinition),
      new NlPropertyItem(components, TOOLS_URI, myShowInItemDefinition),
      new NlPropertyItem(components, TOOLS_URI, myOpenDrawerItemDefinition),
      new NlPropertyItem(components, TOOLS_URI, myLayoutDefinition));
  }

  private static AttributeDefinition getDefinitionByName(@NotNull String name) {
    AttributeDefinition definition = ToolsAttributeUtil.getAttrDefByName(name);
    assert definition != null;
    ResolvingConverter converter = ToolsAttributeUtil.getConverter(definition);
    // TODO: Figure out how to provide the correct reference editor depending on the converter.
    if (converter instanceof StaticEnumConverter) {
      Collection variants = converter.getVariants(null);
      for (Object variant : variants) {
        definition.addValue(variant.toString());
      }
    }
    return definition;
  }
}

