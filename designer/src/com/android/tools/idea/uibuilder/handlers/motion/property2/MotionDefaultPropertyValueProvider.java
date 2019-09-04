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
package com.android.tools.idea.uibuilder.handlers.motion.property2;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionSceneTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.MotionSceneUtils;
import com.android.tools.idea.uibuilder.handlers.motion.editor.NlComponentTag;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.MotionSceneAttrs;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.MotionAttributes;
import com.android.tools.idea.uibuilder.handlers.motion.editor.ui.Utils;
import com.android.tools.idea.uibuilder.property2.DefaultPropertyValueProvider;
import com.android.tools.idea.uibuilder.property2.NelePropertyItem;
import com.intellij.psi.xml.XmlTag;
import java.util.HashMap;
import java.util.List;
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
  public String provideDefaultValue(@NotNull NelePropertyItem property) {
    MotionAttributes attrs = getMotionAttributesForTag(property);
    if (attrs == null) {
      return null;
    }
    HashMap<String, MotionAttributes.DefinedAttribute> map = attrs.getAttrMap();
    MotionAttributes.DefinedAttribute attr = map.get(property.getName());
    if (attr == null) {
      return null;
    }
    return attr.getValue();
  }

  @Nullable
  public static MotionAttributes getMotionAttributesForTag(@NotNull NelePropertyItem property) {
    Object optional1 = property.getOptionalValue1();
    if (!(optional1 instanceof MotionSceneTag)) {
      return null;
    }
    MotionSceneTag motionTag = (MotionSceneTag)optional1;
    if (!motionTag.getTagName().equals(MotionSceneAttrs.Tags.CONSTRAINT)) {
      return null;
    }
    XmlTag tag = motionTag.getXmlTag();
    if (tag == null) {
      return null;
    }
    String constraintId = Utils.stripID(tag.getAttributeValue(ATTR_ID, ANDROID_URI));
    if (constraintId.isEmpty()) {
      return null;
    }
    List<NlComponent> components = property.getComponents();
    if (components.isEmpty()) {
      return null;
    }
    NlComponent motionLayout = components.get(0);
    NlComponent component = motionLayout.getChildren().stream().filter(view -> constraintId.equals(view.getId())).findFirst().orElse(null);
    if (component == null) {
      return null;
    }
    return (MotionAttributes)component.getClientProperty(MotionSceneUtils.MOTION_LAYOUT_PROPERTIES);

  }

  @Override
  public boolean hasDefaultValuesChanged() {
    return false;
  }

  @Override
  public void clearCache() {
  }
}
