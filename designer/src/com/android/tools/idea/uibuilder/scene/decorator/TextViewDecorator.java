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
package com.android.tools.idea.uibuilder.scene.decorator;

import com.android.SdkConstants;
import com.android.tools.idea.common.scene.decorator.SceneDecorator;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintUtilities;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.draw.DrawTextRegion;
import com.android.tools.idea.uibuilder.handlers.constraint.drawing.decorator.TextWidgetConstants;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Support Progress Bar
 */
public class TextViewDecorator extends SceneDecorator {
  private static final String DEFAULT_DIM = "14sp";
  @Override
  public void addContent(@NotNull DisplayList list, long time, @NotNull SceneContext sceneContext, @NotNull SceneComponent component) {
    @AndroidDpCoordinate Rectangle rect = new Rectangle();
    component.fillDrawRect(time, rect);
    @SwingCoordinate int l = sceneContext.getSwingXDip(rect.x);
    @SwingCoordinate int t = sceneContext.getSwingYDip(rect.y);
    @SwingCoordinate int w = sceneContext.getSwingDimensionDip(rect.width);
    @SwingCoordinate int h = sceneContext.getSwingDimensionDip(rect.height);
    String text = component.getNlComponent().getTagName();
    NlComponent nlc = component.getNlComponent();

    int size = DrawTextRegion.getFont(nlc, DEFAULT_DIM);

    String single = nlc.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_SINGLE_LINE);
    boolean singleLine = Boolean.parseBoolean(single);
    int baseLineOffset = sceneContext.getSwingDimensionDip(component.getBaseline());
    int mode = component.isSelected() ? DecoratorUtilities.ViewStates.SELECTED_VALUE : DecoratorUtilities.ViewStates.NORMAL_VALUE;
    list.add(new DrawTextRegion(l, t, w, h, mode, baseLineOffset, text, singleLine, false, TextWidgetConstants.TEXT_ALIGNMENT_CENTER,
                                DrawTextRegion.TEXT_ALIGNMENT_VIEW_START, size, (float)sceneContext.getScale()));
  }
}
