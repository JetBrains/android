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
package com.android.tools.idea.designer;

import com.intellij.designer.model.MetaModel;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.palette.PaletteItem;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

public class AndroidMetaModel extends MetaModel {
  static final String ATTR_RESIZE = "resize";
  static final String ATTR_FILL = "fill";

  private ResizePolicy myResizePolicy;
  private FillPolicy myFillPolicy;

  public AndroidMetaModel(Class<RadComponent> model, String target, String tag) {
    super(model, target, tag);
  }

  public void initializeFrom(@NotNull Element element) {
    String resize = element.getAttributeValue(ATTR_RESIZE);
    if (resize != null) {
      ResizePolicy resizePolicy = ResizePolicy.get(resize);
      if (resizePolicy != null) {
        myResizePolicy = resizePolicy;
      }
    }

    String fill = element.getAttributeValue(ATTR_FILL);
    if (fill != null) {
      FillPolicy fillPolicy = FillPolicy.get(fill);
      if (fillPolicy != null) {
        myFillPolicy = fillPolicy;
      }
    }
  }

  @NotNull
  public ResizePolicy getResizePolicy() {
    PaletteItem paletteItem = getPaletteItem();
    if (paletteItem instanceof AndroidVariationPaletteItem) {
      AndroidVariationPaletteItem item = (AndroidVariationPaletteItem)paletteItem;
      ResizePolicy policy = item.getResizePolicy();
      if (policy != null) {
        return policy;
      }
    }
    return myResizePolicy != null ? myResizePolicy : ResizePolicy.full();
  }

  @NotNull
  public FillPolicy getFillPolicy() {
    PaletteItem paletteItem = getPaletteItem();
    if (paletteItem instanceof AndroidVariationPaletteItem) {
      AndroidVariationPaletteItem item = (AndroidVariationPaletteItem)paletteItem;
      FillPolicy fill = item.getFillPolicy();
      if (fill != null) {
        return fill;
      }
    }
    return myFillPolicy != null ? myFillPolicy : FillPolicy.NONE;
  }
}
