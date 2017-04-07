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
package com.android.tools.idea.uibuilder.scene.draw;

import com.android.tools.idea.uibuilder.scene.SceneComponent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * Supply notches behavior to ConstraintLayout children
 */
public class ConstraintLayoutComponentNotchProvider implements Notch.Provider {

  @Override
  public void fill(@NotNull SceneComponent component, @NotNull SceneComponent target,
                   @NotNull ArrayList<Notch> horizontalNotches, @NotNull ArrayList<Notch> verticalNotches) {
    int x1 = component.getDrawX();
    int x2 = x1 + component.getDrawWidth();
    // int midX = x1 + (x2 - x1) / 2 - target.getDrawWidth() / 2;
    // horizontalNotches.add(new Notch.Horizontal(midX, x1 + (x2 - x1) / 2));
    horizontalNotches.add(new Notch.SmallHorizontal(component, x1, x1));
    horizontalNotches.add(new Notch.SmallHorizontal(component, x2, x2));
    horizontalNotches.add(new Notch.SmallHorizontal(component, x1 - target.getDrawWidth(), x1));
    horizontalNotches.add(new Notch.SmallHorizontal(component, x2 - target.getDrawWidth(), x2));

    int y1 = component.getDrawY();
    int y2 = y1 + component.getDrawHeight();
    // int midY = y1 + (y2 - y1) / 2 - target.getDrawHeight() / 2;
    // verticalNotches.add(new Notch.Vertical(midY, y1 + (y2 - y1) / 2));
    verticalNotches.add(new Notch.SmallVertical(component, y1, y1));
    verticalNotches.add(new Notch.SmallVertical(component, y2, y2));
    verticalNotches.add(new Notch.SmallVertical(component, y1 - target.getDrawHeight(), y1));
    verticalNotches.add(new Notch.SmallVertical(component, y2 - target.getDrawHeight(), y2));
  }
}
