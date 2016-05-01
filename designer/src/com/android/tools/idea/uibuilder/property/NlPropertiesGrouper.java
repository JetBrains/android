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
import com.android.tools.idea.uibuilder.property.NlPropertyAccumulator.PropertyNamePrefixAccumulator;
import com.android.tools.idea.uibuilder.property.ptable.PTableItem;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.SdkConstants.*;

public class NlPropertiesGrouper {
  public List<PTableItem> group(@NotNull List<NlPropertyItem> properties, @NotNull final NlComponent component) {
    final String className = component.getTagName();

    List<PTableItem> result = Lists.newArrayListWithExpectedSize(properties.size());

    // group theme attributes together
    NlPropertyAccumulator themePropertiesAccumulator = new NlPropertyAccumulator(
      "Theme", p -> p != null && (p.getParentStylables().contains("Theme") || p.getName().equalsIgnoreCase("theme")));

    // group attributes that correspond to this component together
    NlPropertyAccumulator customViewPropertiesAccumulator = new NlPropertyAccumulator(
      className, p -> p != null && p.getParentStylables().contains(className));

    // group margin, padding and layout attributes together
    NlPropertyAccumulator marginPropertiesAccumulator = new NlMarginPropertyAccumulator("Margin", ATTR_LAYOUT_LEFT_MARGIN, ATTR_LAYOUT_RIGHT_MARGIN, ATTR_LAYOUT_TOP_MARGIN, ATTR_LAYOUT_BOTTOM_MARGIN);
    NlPropertyAccumulator paddingPropertiesAccumulator = new NlMarginPropertyAccumulator("Padding", ATTR_PADDING, ATTR_PADDING_LEFT, ATTR_PADDING_RIGHT, ATTR_PADDING_START, ATTR_PADDING_END, ATTR_PADDING_TOP, ATTR_PADDING_BOTTOM);
    NlPropertyAccumulator layoutViewPropertiesAccumulator = new NlMarginPropertyAccumulator("Layout", ATTR_LAYOUT_MARGIN, ATTR_LAYOUT_MARGIN_LEFT, ATTR_LAYOUT_MARGIN_RIGHT, ATTR_LAYOUT_MARGIN_START, ATTR_LAYOUT_MARGIN_END, ATTR_LAYOUT_MARGIN_TOP, ATTR_LAYOUT_MARGIN_BOTTOM);

    PropertyNamePrefixAccumulator constraintPropertiesAccumulator = new PropertyNamePrefixAccumulator("Constraints", "layout_constraint");

    List<NlPropertyAccumulator> accumulators = ImmutableList.of(customViewPropertiesAccumulator, themePropertiesAccumulator,
                                                                marginPropertiesAccumulator, paddingPropertiesAccumulator,
                                                                layoutViewPropertiesAccumulator, constraintPropertiesAccumulator);
    for (NlPropertyItem p : properties) {
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
