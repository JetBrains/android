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
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.idea.uibuilder.scene.draw.DisplayList;
import com.android.tools.idea.uibuilder.scene.draw.DrawRegion;
import com.android.tools.idea.uibuilder.scene.draw.DrawTextRegion;
import com.android.tools.sherpa.drawing.ColorSet;
import com.android.tools.sherpa.drawing.decorator.TextWidget;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

import static com.android.tools.idea.res.ResourceHelper.resolveStringValue;

/**
 * Support Progress Bar
 */
public class TextViewDecorator extends SceneDecorator {
  private static final String DEFAULT_DIM = "15sp";

  @Override
  public void buildListComponent(@NotNull DisplayList list, long time, @NotNull SceneContext sceneContext, @NotNull SceneComponent component) {
    super.buildListComponent(list, time, sceneContext, component);
    Rectangle rect = new Rectangle();
    component.fillDrawRect(time, rect);
    int l = sceneContext.getSwingX(rect.x);
    int t = sceneContext.getSwingY(rect.y);
    int w = sceneContext.getSwingDimension(rect.width);
    int h = sceneContext.getSwingDimension(rect.height);
    String text = ConstraintUtilities.getResolvedText(component.getNlComponent());
    NlComponent nlc = component.getNlComponent();
    Configuration configuration = nlc.getModel().getConfiguration();
    ResourceResolver resourceResolver = configuration.getResourceResolver();

    Integer size = null;

    if (resourceResolver != null) {
      String textSize = nlc.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_TEXT_SIZE);
      if (textSize != null) {
        size = ViewEditor.resolveDimensionPixelSize(resourceResolver, textSize, configuration);
      }
    }

    if (size == null) {
      // With the specified string, this method cannot return null
      //noinspection ConstantConditions
      size = ViewEditor.resolveDimensionPixelSize(resourceResolver, DEFAULT_DIM, configuration);
    }

    String alignment = nlc.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_TEXT_ALIGNMENT);
    int align = ConstraintUtilities.getAlignment(alignment);
    String single = nlc.getAttribute(SdkConstants.ANDROID_URI, SdkConstants.ATTR_SINGLE_LINE);
    boolean singleLine = Boolean.parseBoolean(single);
    int baseLineOffset = sceneContext.getSwingDimension(component.getBaseline());
    int scaleSize =  sceneContext.getSwingDimension(size);
    list.add(new DrawTextRegion(l, t, w, h, baseLineOffset, text, singleLine, false, align, DrawTextRegion.TEXT_ALIGNMENT_VIEW_START, scaleSize));
  }
}
