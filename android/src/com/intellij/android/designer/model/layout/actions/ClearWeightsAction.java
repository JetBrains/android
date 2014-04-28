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

import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.model.layout.RadLinearLayout;
import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.psi.xml.XmlAttribute;
import icons.AndroidDesignerIcons;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.SdkConstants.*;

public class ClearWeightsAction extends LayoutAction {
  private final RadViewComponent myLayout;
  private final List<? extends RadViewComponent> mySelectedChildren;

  public ClearWeightsAction(@NotNull DesignerEditorPanel designer,
                            @NotNull RadViewComponent layout,
                            @NotNull List<? extends RadViewComponent> selectedChildren) {
    super(designer, "Clear All Weights", null, AndroidDesignerIcons.ClearWeights);
    myLayout = layout;
    mySelectedChildren = selectedChildren;
  }

  @Override
  protected void performWriteAction() {
    clearWeights(myLayout, mySelectedChildren);
  }

  static void clearWeights(RadViewComponent parentNode, List<? extends RadViewComponent> targets) {
    // Clear attributes
    String sizeAttribute = ((RadLinearLayout) parentNode.getLayout()).isHorizontal() ?
                           ATTR_LAYOUT_WIDTH : ATTR_LAYOUT_HEIGHT;
    for (RadViewComponent target : targets) {
      XmlAttribute weight = target.getTag().getAttribute(ATTR_LAYOUT_WEIGHT, ANDROID_URI);
      if (weight != null) {
        weight.delete();
      }
      XmlAttribute size = target.getTag().getAttribute(sizeAttribute, ANDROID_URI);
      if (size != null && size.getValue().startsWith("0")) {
        size.setValue(VALUE_WRAP_CONTENT);
      }
    }
  }
}
