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

import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.property.NlProperty;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.google.common.collect.ImmutableList;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.dom.attrs.AttributeDefinition;
import org.jetbrains.android.dom.attrs.AttributeFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.android.SdkConstants.*;

public class EnumSupportFactory {
  static final String TEXT_APPEARANCE_SUFFIX = "TextAppearance";
  static final List<String> AVAILABLE_TEXT_SIZES = ImmutableList.of("8sp", "10sp", "12sp", "14sp", "18sp", "24sp", "30sp", "36sp");
  static final List<String> AVAILABLE_LINE_SPACINGS = AVAILABLE_TEXT_SIZES;
  static final List<String> AVAILABLE_TYPEFACES = ImmutableList.of("normal", "sans", "serif", "monospace");
  static final List<String> AVAILABLE_SIZES = ImmutableList.of("match_parent", "wrap_content");

  public static boolean supportsProperty(@NotNull NlProperty property) {
    if (getEnumValueOverride(property) != null) {
      return true;
    }
    switch (property.getName()) {
      case ATTR_FONT_FAMILY:
      case ATTR_TYPEFACE:
      case ATTR_TEXT_SIZE:
      case ATTR_LINE_SPACING_EXTRA:
      case ATTR_TEXT_APPEARANCE:
      case ATTR_LAYOUT_HEIGHT:
      case ATTR_LAYOUT_WIDTH:
      case ATTR_DROPDOWN_HEIGHT:
      case ATTR_DROPDOWN_WIDTH:
      case ATTR_ON_CLICK:
        return true;
      case ATTR_ID:
        return false;
      case ATTR_STYLE:
        String tagName = property.getTagName();
        ResourceResolver resolver = property.getResolver();
        return tagName != null && resolver != null && StyleFilter.hasWidgetStyles(property.getModel().getProject(), resolver, tagName);
      default:
        if (property.getName().endsWith(TEXT_APPEARANCE_SUFFIX)) {
          return true;
        }
        if (AndroidDomUtil.getSpecialResourceTypes(property.getName()).contains(ResourceType.ID)) {
          return true;
        }
        AttributeDefinition definition = property.getDefinition();
        Set<AttributeFormat> formats = definition != null ? definition.getFormats() : Collections.emptySet();
        return formats.contains(AttributeFormat.Enum);
    }
  }

  public static EnumSupport create(@NotNull NlProperty property) {
    Map<String, String> values = getEnumValueOverride(property);
    if (values != null) {
      return new SimpleValuePairEnumSupport(property, values);
    }
    switch (property.getName()) {
      case ATTR_FONT_FAMILY:
        return new FontEnumSupport(property);
      case ATTR_TYPEFACE:
        return new SimpleEnumSupport(property, AVAILABLE_TYPEFACES);
      case ATTR_TEXT_SIZE:
        return new SimpleQuantityEnumSupport(property, AVAILABLE_TEXT_SIZES);
      case ATTR_LINE_SPACING_EXTRA:
        return new SimpleQuantityEnumSupport(property, AVAILABLE_LINE_SPACINGS);
      case ATTR_TEXT_APPEARANCE:
        return new TextAppearanceEnumSupport(property);
      case ATTR_LAYOUT_HEIGHT:
      case ATTR_LAYOUT_WIDTH:
      case ATTR_DROPDOWN_HEIGHT:
      case ATTR_DROPDOWN_WIDTH:
        return new SimpleQuantityEnumSupport(property, AVAILABLE_SIZES);
      case ATTR_ON_CLICK:
        return new OnClickEnumSupport(property);
      case ATTR_STYLE:
        return new StyleEnumSupport(property);
      default:
        if (property.getName().endsWith(TEXT_APPEARANCE_SUFFIX)) {
          return new TextAppearanceEnumSupport(property);
        }
        else if (AndroidDomUtil.getSpecialResourceTypes(property.getName()).contains(ResourceType.ID)) {
          return new IdEnumSupport(property);
        }
        else {
          return new AttributeDefinitionEnumSupport(property);
        }
    }
  }

  @Nullable
  private static Map<String, String> getEnumValueOverride(@NotNull NlProperty property) {
    Collection<NlComponent> components = property.getComponents();
    if (components.isEmpty()) {
      return null;
    }
    if (property.getName().startsWith(ATTR_LAYOUT_RESOURCE_PREFIX)) {
      Map<NlComponent, NlComponent> parents = new IdentityHashMap<>();
      components.stream()
        .map(NlComponent::getParent)
        .filter(Objects::nonNull)
        .forEach(parent -> parents.put(parent, parent));
      components = parents.keySet();
    }
    Map<String, String> values = null;
    for (NlComponent component : components) {
      ViewHandler handler = NlComponentHelperKt.getViewHandler(component);
      if (handler == null) {
        return null;
      }
      Map<String, String> overrides = handler.getEnumPropertyValues(component).get(property.getName());
      if (overrides == null) {
        return null;
      }
      if (values == null) {
        values = overrides;
      }
      else if (!overrides.equals(values)) {
        return null;
      }
    }
    return values;
  }
}
