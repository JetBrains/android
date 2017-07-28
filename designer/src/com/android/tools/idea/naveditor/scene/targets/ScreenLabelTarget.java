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
package com.android.tools.idea.naveditor.scene.targets;

import com.android.tools.idea.naveditor.scene.draw.DrawScreenLabel;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.target.Target;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * {@linkplain ScreenLabelTarget} draws the name of the fragment above the frame.
 */
public class ScreenLabelTarget extends NavBaseTarget {
  // TODO: finalize values for the following constants
  private static final int SPACING = 6;
  static final String FONT_NAME = null;
  static final int FONT_STYLE = Font.PLAIN;
  static final int FONT_SIZE = 24;

  public ScreenLabelTarget(@NotNull SceneComponent component) {
    super(component);
  }

  @Override
  public int getPreferenceLevel() {
    return Target.ANCHOR_LEVEL;
  }

  @Override
  public boolean layout(@NotNull SceneContext sceneTransform,
                        @AndroidDpCoordinate int l,
                        @AndroidDpCoordinate int t,
                        @AndroidDpCoordinate int r,
                        @AndroidDpCoordinate int b) {
    layoutRectangle(l, t - SPACING, r, t);
    return false;
  }

  @Override
  public void render(@NotNull DisplayList list, @NotNull SceneContext sceneContext) {
    String text = getComponent().getNlComponent().getId();
    if(text == null) {
      text = "";
    }

    list.add(new DrawScreenLabel(getSwingLeft(sceneContext),
                                 getSwingTop(sceneContext),
                                 getFrameColor(sceneContext),
                                 new Font(FONT_NAME, FONT_STYLE, (int)(sceneContext.getScale() * FONT_SIZE)),
                                 text));
  }
}
