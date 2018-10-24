/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.idea.common.property2.api.PropertiesTable;
import com.android.tools.idea.uibuilder.handlers.motion.AttrName;
import com.android.tools.idea.uibuilder.handlers.motion.timeline.MotionSceneModel;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

public class MotionLayoutPropertyProvider {
  @NotNull
  public PropertiesTable<MotionPropertyItem> getProperties(@NotNull MotionSceneModel.KeyFrame keyFrame)  {
    return PropertiesTable.Companion.create(getPropertiesImpl(keyFrame));
  }

  private static Table<String, String, MotionPropertyItem> getPropertiesImpl(@NotNull MotionSceneModel.KeyFrame keyFrame) {
    HashMap<AttrName, Object> attributes = new HashMap<>();
    keyFrame.fill(attributes);
    Table<String, String, MotionPropertyItem> properties = HashBasedTable.create(3, 20);
    attributes.forEach((attribute, value) -> addProperty(properties, attribute, keyFrame));
    keyFrame.getCustomAttributes().forEach(attribute -> addCustomProperty(properties, attribute));
    return properties;
  }

  private static void addProperty(@NotNull Table<String, String, MotionPropertyItem> properties,
                                  @NotNull AttrName name,
                                  @NotNull MotionSceneModel.BaseTag tag) {
    MotionPropertyItem item = new MotionPropertyItem(name, tag);
    properties.put(item.getNamespace(), item.getName(), item);
  }

  private static void addCustomProperty(@NotNull Table<String, String, MotionPropertyItem> properties,
                                        @NotNull MotionSceneModel.CustomAttributes attribute) {
    String name = attribute.getAttributeName();
    if (StringUtil.isNotEmpty(name)) {
      name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
      MotionPropertyItem item = new MotionPropertyItem(AttrName.motionAttr(name), attribute);
      properties.put(item.getNamespace(), name, item);
    }
  }
}
