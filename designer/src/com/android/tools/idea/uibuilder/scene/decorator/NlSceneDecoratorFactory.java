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

import com.android.AndroidXConstants;
import com.android.SdkConstants;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.decorator.SceneDecorator;
import com.android.tools.idea.common.scene.decorator.SceneDecoratorFactory;
import com.android.tools.idea.uibuilder.handlers.constraint.draw.ConstraintLayoutDecorator;
import com.android.tools.idea.uibuilder.handlers.grid.draw.GridLayoutDecorator;
import com.android.tools.idea.uibuilder.handlers.grid.draw.GridLayoutV7Decorator;
import com.android.tools.idea.uibuilder.handlers.motion.MotionLayoutDecorator;
import com.android.tools.idea.uibuilder.handlers.relative.draw.RelativeLayoutDecorator;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

/**
 * A {@link SceneDecoratorFactory} for layout editor components.
 */
public class NlSceneDecoratorFactory extends SceneDecoratorFactory {
  private static final NlSceneFrameFactory FRAME_FRACTORY = new NlSceneFrameFactory();
  protected static final SceneDecorator BASIC_NL_DECORATOR = new UnknownViewDecorator();

  private static Map<String, Constructor<? extends SceneDecorator>> ourConstructorMap = new HashMap<>();
  private static Map<String, SceneDecorator> ourSceneMap = new HashMap<>();

  static {
    try {
      ourConstructorMap.put(AndroidXConstants.CLASS_CONSTRAINT_LAYOUT.oldName(), ConstraintLayoutDecorator.class.getConstructor());
      ourConstructorMap.put(AndroidXConstants.CLASS_CONSTRAINT_LAYOUT.newName(), ConstraintLayoutDecorator.class.getConstructor());
      ourConstructorMap.put(AndroidXConstants.CLASS_MOTION_LAYOUT.oldName(), MotionLayoutDecorator.class.getConstructor());
      ourConstructorMap.put(AndroidXConstants.CLASS_MOTION_LAYOUT.newName(), MotionLayoutDecorator.class.getConstructor());
      ourConstructorMap.put(SdkConstants.PROGRESS_BAR, ProgressBarDecorator.class.getConstructor());
      ourConstructorMap.put(SdkConstants.BUTTON, ButtonDecorator.class.getConstructor());
      ourConstructorMap.put(SdkConstants.TOGGLE_BUTTON, ToggleButtonDecorator.class.getConstructor());
      ourConstructorMap.put(SdkConstants.TEXT_VIEW, TextViewDecorator.class.getConstructor());
      ourConstructorMap.put(SdkConstants.IMAGE_VIEW, ImageViewDecorator.class.getConstructor());
      ourConstructorMap.put(SdkConstants.CHECK_BOX, CheckBoxDecorator.class.getConstructor());
      ourConstructorMap.put(SdkConstants.RADIO_BUTTON, RadioButtonDecorator.class.getConstructor());
      ourConstructorMap.put(SdkConstants.SEEK_BAR, SeekBarDecorator.class.getConstructor());
      ourConstructorMap.put(SdkConstants.SWITCH, SwitchDecorator.class.getConstructor());
      ourConstructorMap.put(SdkConstants.LINEAR_LAYOUT, LinearLayoutDecorator.class.getConstructor());
      ourConstructorMap.put(SdkConstants.GRID_LAYOUT, GridLayoutDecorator.class.getConstructor());
      ourConstructorMap.put(AndroidXConstants.CLASS_GRID_LAYOUT_V7.oldName(), GridLayoutV7Decorator.class.getConstructor());
      ourConstructorMap.put(AndroidXConstants.CLASS_GRID_LAYOUT_V7.newName(), GridLayoutV7Decorator.class.getConstructor());
      ourConstructorMap.put(SdkConstants.RELATIVE_LAYOUT, RelativeLayoutDecorator.class.getConstructor());
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
      SceneDecorator decorator = get(className).orElse(BASIC_NL_DECORATOR);
      decorator.setFrameFactory(FRAME_FRACTORY);
      return decorator;
    }

    SceneDecorator decorator = get(tag).orElse(BASIC_NL_DECORATOR);
    decorator.setFrameFactory(FRAME_FRACTORY);
    return decorator;
  }

  @NotNull
  private static Optional<SceneDecorator> get(@NotNull String key) {
    if (ourConstructorMap.containsKey(key)) {
      if (!ourSceneMap.containsKey(key)) {
        try {
          ourSceneMap.put(key, ourConstructorMap.get(key).newInstance());
        }
        catch (Exception e) {
          ourSceneMap.put(key, null);
        }
      }
      return Optional.of(ourSceneMap.get(key));
    }
    return Optional.empty();
  }
}
