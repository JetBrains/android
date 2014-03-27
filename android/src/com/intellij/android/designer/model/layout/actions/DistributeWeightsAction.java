/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.intellij.android.designer.model.layout.actions;

import com.android.utils.XmlUtils;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.model.layout.RadLinearLayout;
import com.intellij.designer.designSurface.DesignerEditorPanel;
import icons.AndroidDesignerIcons;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.SdkConstants.*;

public class DistributeWeightsAction extends LayoutAction {
  private final RadViewComponent myLayout;
  private final List<? extends RadViewComponent> mySelectedChildren;

  public DistributeWeightsAction(@NotNull DesignerEditorPanel designer,
                                 @NotNull RadViewComponent layout,
                                 @NotNull List<? extends RadViewComponent> selectedChildren) {
    super(designer, "Distribute Weights Evenly", null, AndroidDesignerIcons.DistributeWeights);
    myLayout = layout;
    mySelectedChildren = selectedChildren;
  }

  @Override
  protected void performWriteAction() {
    distributeWeights(myLayout, mySelectedChildren);
  }

  static void distributeWeights(RadViewComponent parentNode, List<? extends RadViewComponent> targets) {
    // Any XML to get weight sum?
    String weightSum = parentNode.getTag().getAttributeValue(ATTR_WEIGHT_SUM, ANDROID_URI);
    double sum = -1.0;
    if (weightSum != null && !weightSum.isEmpty()) {
      // Distribute
      try {
        sum = Double.parseDouble(weightSum);
      }
      catch (NumberFormatException nfe) {
        // Just keep using the default
      }
    }
    int numTargets = targets.size();
    double share;
    if (sum <= 0.0) {
      // The sum will be computed from the children, so just
      // use arbitrary amount
      share = 1.0;
    }
    else {
      share = sum / numTargets;
    }
    String value = XmlUtils.formatFloatAttribute((float)share);
    String sizeAttribute = ((RadLinearLayout)parentNode.getLayout()).isHorizontal() ? ATTR_LAYOUT_WIDTH : ATTR_LAYOUT_HEIGHT;
    for (RadViewComponent target : targets) {
      target.getTag().setAttribute(ATTR_LAYOUT_WEIGHT, ANDROID_URI, value);
      // Also set the width/height to 0dp to ensure actual equal
      // size (without this, only the remaining space is
      // distributed)
      if (VALUE_WRAP_CONTENT.equals(target.getTag().getAttributeValue(sizeAttribute, ANDROID_URI))) {
        target.getTag().setAttribute(sizeAttribute, ANDROID_URI, VALUE_ZERO_DP);
      }
    }
  }
}
