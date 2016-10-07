/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.tools.idea.rendering.AttributeSnapshot;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class NlPropertiesSorter {
  private enum SortOrder {
    id,
    layout_width,
    layout_height,
    layout_constraint,
    layout_margin,
    padding,
    opacity,
    elevation,
    modified,
    accessibility,
    theme,
    styles,
    important,
    expert;

    public static SortOrder of(@NotNull String item, boolean isMainClass, boolean isModifiedAttribute) {
      for (SortOrder order : values()) {
        if (order.name().equalsIgnoreCase(item)) {
          return order;
        }
      }

      if (isMainClass || isModifiedAttribute) {
        return modified;
      }

      return expert;
    }
  }

  public List<NlPropertyItem> sort(@NotNull List<NlPropertyItem> groupedProperties, @NotNull List<NlComponent> components) {
    final String tagName = NlPropertiesGrouper.getCommonTagName(components);
    final Set<String> modifiedAttributeNames = getModifiedAttributes(components);

    Collections.sort(groupedProperties, Comparator
      .comparing((NlPropertyItem property) -> SortOrder.of(property.getName(),
                                                           property.getName().equalsIgnoreCase(tagName),
                                                           modifiedAttributeNames.contains(property.getName())))
      .thenComparing(NlPropertyItem::getName)
      .thenComparing(NlPropertyItem::getNamespace));
    return groupedProperties;
  }

  @NotNull
  public static Set<String> getModifiedAttributes(@NotNull List<NlComponent> components) {
    List<AttributeSnapshot> attrs = components.get(0).getAttributes();
    Set<String> modifiedAttrs = Sets.newHashSetWithExpectedSize(attrs.size());
    for (NlComponent component : components) {
      attrs = component.getAttributes();
      for (AttributeSnapshot snapshot : attrs) {
        modifiedAttrs.add(snapshot.name);
      }
    }
    return modifiedAttrs;
  }
}
