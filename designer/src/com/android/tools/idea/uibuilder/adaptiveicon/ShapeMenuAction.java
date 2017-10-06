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
package com.android.tools.idea.uibuilder.adaptiveicon;

import com.android.tools.adtui.actions.DropDownAction;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Action for changing the shape of the adaptive icon when previewing
 */
public class ShapeMenuAction extends DropDownAction {
  public enum AdaptiveIconShape {
    SQUARE("Square", "M50,0L100,0 100,100 0,100 0,0z"),
    CIRCLE("Circle", "M50 0C77.6 0 100 22.4 100 50C100 77.6 77.6 100 50 100C22.4 100 0 77.6 0 50C0 22.4 22.4 0 50 0Z"),
    ROUNDED_CORNERS("Rounded Corners",
                    "M50,0L92,0C96.42,0 100,4.58 100 8L100,92C100, 96.42 96.42 100 92 100L8 100C4.58, 100 0 96.42 0 92L0 8 C 0 4.42 4.42 0 8 0L50 0Z"),
    SQUIRCLE("Squircle", "M50,0 C10,0 0,10 0,50 0,90 10,100 50,100 90,100 100,90 100,50 100,10 90,0 50,0 Z");

    private final String myName;
    private final String myPathDescription;

    AdaptiveIconShape(@NotNull String name, @NotNull String pathDescription) {
      myName = name;
      myPathDescription = pathDescription;
    }

    @NotNull
    public String getPathDescription() {
      return myPathDescription;
    }

    @NotNull
    public static AdaptiveIconShape getDefaultShape() {
      return SQUARE;
    }
  }

  private final NlDesignSurface mySurface;

  public ShapeMenuAction(@NotNull NlDesignSurface surface) {
    super("", "Adaptive Icon Shape", null);
    mySurface = surface;
    for (AdaptiveIconShape shape : AdaptiveIconShape.values()) {
      add(new SetShapeAction(mySurface, shape));
    }
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    e.getPresentation().setText(mySurface.getAdaptiveIconShape().myName);
  }

  private static class SetShapeAction extends AnAction {
    private final AdaptiveIconShape myShape;
    private final NlDesignSurface mySurface;

    private SetShapeAction(@NotNull NlDesignSurface surface, @NotNull AdaptiveIconShape shape) {
      super(shape.myName);
      mySurface = surface;
      myShape = shape;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      mySurface.setAdaptiveIconShape(myShape);
      NlModel model = mySurface.getModel();
      if (model != null) {
        model.notifyModified(NlModel.ChangeType.CONFIGURATION_CHANGE);
      }
    }
  }
}
