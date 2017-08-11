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

import com.android.tools.idea.common.model.NlComponent;
import com.google.common.collect.ImmutableList;
import com.intellij.util.xml.ResolvingConverter;
import com.intellij.util.xml.XmlName;
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
  private final AttributeDefinition myParentTagDefinition;
  private final AttributeDefinition myMockupDefinition;
  private final AttributeDefinition myMockupCropDefinition;
  private final AttributeDefinition myMockupOpacityDefinition;

  public NlDesignProperties() {
    myContextDefinition = getDefinitionByName(ATTR_CONTEXT);
    myListItemDefinition = getDefinitionByName(ATTR_LISTITEM);
    myShowInItemDefinition = getDefinitionByName(ATTR_SHOW_IN);
    myOpenDrawerItemDefinition = getDefinitionByName(ATTR_OPEN_DRAWER);
    myLayoutDefinition = getDefinitionByName(ATTR_LAYOUT);
    myParentTagDefinition = getDefinitionByName(ATTR_PARENT_TAG);
    myMockupDefinition = getDefinitionByName(ATTR_MOCKUP);
    myMockupCropDefinition = getDefinitionByName(ATTR_MOCKUP_CROP);
    myMockupOpacityDefinition = getDefinitionByName(ATTR_MOCKUP_OPACITY);
  }

  @NotNull
  public List<NlPropertyItem> getKnownProperties(@NotNull List<NlComponent> components, @NotNull NlPropertiesManager propertiesManager) {
    return ImmutableList.of(
      create(myContextDefinition, components, propertiesManager),
      create(myListItemDefinition, components, propertiesManager),
      create(myShowInItemDefinition, components, propertiesManager),
      create(myOpenDrawerItemDefinition, components, propertiesManager),
      create(myLayoutDefinition, components, propertiesManager),
      create(myParentTagDefinition, components, propertiesManager),
      create(myLayoutDefinition, components, propertiesManager),
      create(myMockupDefinition, components, propertiesManager),
      create(myMockupCropDefinition, components, propertiesManager),
      create(myMockupOpacityDefinition, components, propertiesManager));
  }

  private static NlPropertyItem create(@NotNull AttributeDefinition definition,
                                       @NotNull List<NlComponent> components,
                                       @NotNull NlPropertiesManager propertiesManager) {
    return NlPropertyItem.create(new XmlName(definition.getName(), TOOLS_URI), definition, components, propertiesManager);
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

