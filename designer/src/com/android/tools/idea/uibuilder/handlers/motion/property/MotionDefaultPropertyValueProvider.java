/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.property;

import static com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs.Tags.CUSTOM_ATTRIBUTE;
import static com.android.tools.idea.uibuilder.handlers.motion.property.MotionLayoutAttributesModel.getMotionSelection;
import static com.android.tools.idea.uibuilder.handlers.motion.property.MotionLayoutAttributesModel.getSubTag;

import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionAttributes;
import com.android.tools.idea.uibuilder.property.DefaultPropertyValueProvider;
import com.android.tools.idea.uibuilder.property.NlPropertyItem;
import java.util.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Default property value provider for Constraint tags in a motion scene file.
 *
 * A constraint may inherit values from a base ConstraintSet or from the view
 * attributes from the layout file.
 */
public class MotionDefaultPropertyValueProvider implements DefaultPropertyValueProvider {

  @Nullable
  @Override
  public String provideDefaultValue(@NotNull NlPropertyItem property) {
    MotionSelection selection = getMotionSelection(property);
    if (selection == null) {
      return null;
    }
    MotionAttributes attrs = selection.getMotionAttributes();
    if (attrs == null) {
      return null;
    }
    HashMap<String, MotionAttributes.DefinedAttribute> map = attrs.getAttrMap();
    MotionAttributes.DefinedAttribute attr = map.get(property.getName());
    if (attr == null) {
      return null;
    }
    if (attr.isCustomAttribute() != (CUSTOM_ATTRIBUTE.equals(getSubTag(property)))) {
      return null;
    }
    return attr.getValue();
  }

  @Override
  public boolean hasDefaultValuesChanged() {
    return false;
  }

  @Override
  public void clearCache() {
  }
}
