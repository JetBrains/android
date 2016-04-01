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

import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.property.ptable.PTableItem;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public class NlPropertiesGrouper {
  public List<PTableItem> group(@NotNull List<NlProperty> properties, @NotNull final NlComponent component) {
    final String className = component.getTagName();

    List<PTableItem> result = Lists.newArrayListWithExpectedSize(properties.size());

    // group theme attributes together
    NlPropertyAccumulator themePropertiesAccumulator = new NlPropertyAccumulator("Theme", new Predicate<NlProperty>() {
      @Override
      public boolean apply(@Nullable NlProperty p) {
        return p != null && (p.getParentStylables().contains("Theme") || p.getName().equalsIgnoreCase("theme"));
      }
    });

    // group attributes that correspond to this component together
    NlPropertyAccumulator customViewPropertiesAccumulator = new NlPropertyAccumulator(className, new Predicate<NlProperty>() {
      @Override
      public boolean apply(@Nullable NlProperty p) {
        return p != null && p.getParentStylables().contains(className);
      }
    });

    // group margin, padding and layout attributes together
    NlPropertyAccumulator marginPropertiesAccumulator = new NlPropertyAccumulator.PropertyNamePrefixAccumulator("Margin", "margin");
    NlPropertyAccumulator paddingPropertiesAccumulator = new NlPropertyAccumulator.PropertyNamePrefixAccumulator("Padding", "padding");
    NlPropertyAccumulator layoutViewPropertiesAccumulator = new NlPropertyAccumulator.PropertyNamePrefixAccumulator("Layout", "layout");

    List<NlPropertyAccumulator> accumulators = ImmutableList.of(customViewPropertiesAccumulator, themePropertiesAccumulator,
                                                                marginPropertiesAccumulator, paddingPropertiesAccumulator,
                                                                layoutViewPropertiesAccumulator);

    Set<String> modifiedAttrs = NlPropertiesSorter.getModifiedAttributes(component);

    for (NlProperty p : properties) {
      if (modifiedAttrs.contains(p.getName())) {
        result.add(p);
        continue;
      }

      boolean added = false;
      for (NlPropertyAccumulator accumulator : accumulators) {
        added = accumulator.process(p);
        if (added) {
          break;
        }
      }

      if (!added) {
        result.add(p);
      }
    }

    for (NlPropertyAccumulator accumulator : accumulators) {
      if (accumulator.hasItems()) {
        result.add(accumulator.getGroupNode());
      }
    }

    return result;
  }
}
