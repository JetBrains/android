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
import com.android.ide.common.resources.ResourceResolver;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintUtilities;
import com.android.tools.idea.uibuilder.model.AndroidDpCoordinate;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.SwingCoordinate;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.idea.uibuilder.scene.draw.DisplayList;
import com.android.tools.idea.uibuilder.scene.draw.DrawTextRegion;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

import static com.android.tools.idea.res.ResourceHelper.resolveStringValue;

/**
 * Support Progress Bar
 */
public class TextViewDecorator extends SceneDecorator {
  private static final String DEFAULT_DIM = "14sp";
  @Override
  public void addContent(@NotNull DisplayList list, long time, @NotNull SceneContext sceneContext, @NotNull SceneComponent component) {
    @AndroidDpCoordinate Rectangle rect = new Rectangle();
    component.fillDrawRect(time, rect);
    @SwingCoordinate int l = sceneContext.getSwingX(rect.x);
    @SwingCoordinate int t = sceneContext.getSwingY(rect.y);
    @SwingCoordinate int w = sceneContext.getSwingDimension(rect.width);
    @SwingCoordinate int h = sceneContext.getSwingDimension(rect.height);
    String text = ConstraintUtilities.getResolvedText(component.getNlComponent());
    NlComponent nlc = component.getNlComponent();

    int size = DrawTextRegion.getFont(nlc, DEFAULT_DIM);

    String alignment = nlc.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_TEXT_ALIGNMENT);
    boolean rtl = component.getScene().isInRTL();
    int align = ConstraintUtilities.getAlignment(alignment, rtl);
    String single = nlc.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_SINGLE_LINE);
    boolean singleLine = Boolean.parseBoolean(single);
    int baseLineOffset = sceneContext.getSwingDimension(component.getBaseline());
    list.add(new DrawTextRegion(l, t, w, h, baseLineOffset, text, singleLine, false, align, DrawTextRegion.TEXT_ALIGNMENT_VIEW_START, size,
                                (float)sceneContext.getScale()));
  }
}
