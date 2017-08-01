/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.scene.decorator;

import com.android.SdkConstants;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.decorator.SceneDecorator;
import com.android.tools.idea.common.scene.decorator.SceneDecoratorFactory;
import com.android.tools.idea.uibuilder.handlers.constraint.draw.ConstraintLayoutDecorator;
import com.android.tools.idea.uibuilder.handlers.grid.draw.GridLayoutDecorator;
import com.android.tools.idea.uibuilder.handlers.grid.draw.GridLayoutV7Decorator;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@link SceneDecoratorFactory} for layout editor components.
 */
public class NlSceneDecoratorFactory extends SceneDecoratorFactory {
  private static final NlSceneFrameFactory FRAME_FRACTORY = new NlSceneFrameFactory();

  private static Map<String, Constructor<? extends SceneDecorator>> ourConstructorMap = new HashMap<>();

  static {
    try {
      ourConstructorMap.put(SdkConstants.CLASS_CONSTRAINT_LAYOUT, ConstraintLayoutDecorator.class.getConstructor());
      ourConstructorMap.put(SdkConstants.PROGRESS_BAR, ProgressBarDecorator.class.getConstructor());
      ourConstructorMap.put(SdkConstants.BUTTON, ButtonDecorator.class.getConstructor());
      ourConstructorMap.put(SdkConstants.TEXT_VIEW, TextViewDecorator.class.getConstructor());
      ourConstructorMap.put(SdkConstants.IMAGE_VIEW, ImageViewDecorator.class.getConstructor());
      ourConstructorMap.put(SdkConstants.CHECK_BOX, CheckBoxDecorator.class.getConstructor());
      ourConstructorMap.put(SdkConstants.RADIO_BUTTON, RadioButtonDecorator.class.getConstructor());
      ourConstructorMap.put(SdkConstants.SEEK_BAR, SeekBarDecorator.class.getConstructor());
      ourConstructorMap.put(SdkConstants.SWITCH, SwitchDecorator.class.getConstructor());
      ourConstructorMap.put(SdkConstants.LINEAR_LAYOUT, LinearLayoutDecorator.class.getConstructor());
      ourConstructorMap.put(SdkConstants.GRID_LAYOUT, GridLayoutDecorator.class.getConstructor());
      ourConstructorMap.put(SdkConstants.CLASS_GRID_LAYOUT_V7, GridLayoutV7Decorator.class.getConstructor());
    }
    catch (NoSuchMethodException e) {
      // ignore invalid component
    }
  }

  @NotNull
  @Override
  public SceneDecorator get(@NotNull NlComponent component) {
    String tag = component.getTagName();
    if (tag.equalsIgnoreCase(SdkConstants.VIEW_MERGE)) {
      String parentTag = component.getAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_PARENT_TAG);
      if (parentTag != null) {
        tag = parentTag;
      }
    }

    String className = NlComponentHelperKt.getMostSpecificClass(component, ourConstructorMap.keySet());
    if (className != null) {
      SceneDecorator decorator = get(className).orElse(BASIC_DECORATOR);
      decorator.setFrameFactory(FRAME_FRACTORY);
      return decorator;
    }

    SceneDecorator decorator = get(tag).orElse(BASIC_DECORATOR);
    decorator.setFrameFactory(FRAME_FRACTORY);
    return decorator;
  }

  @NotNull
  @Override
  protected Map<String, Constructor<? extends SceneDecorator>> getConstructorMap() {
    return ourConstructorMap;
  }
}
