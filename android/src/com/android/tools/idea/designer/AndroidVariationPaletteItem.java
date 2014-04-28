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
import com.intellij.designer.model.VariationPaletteItem;
import com.intellij.designer.palette.PaletteItem;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

public class AndroidVariationPaletteItem extends VariationPaletteItem {
  private ResizePolicy myResizePolicy;
  private FillPolicy myFillPolicy;

  public AndroidVariationPaletteItem(PaletteItem defaultItem, MetaModel model, Element element) {
    super(defaultItem, model, element);

    String resize = element.getAttributeValue(AndroidMetaModel.ATTR_RESIZE);
    if (resize != null) {
      myResizePolicy = ResizePolicy.get(resize);
    }

    String fill = element.getAttributeValue(AndroidMetaModel.ATTR_FILL);
    if (fill != null) {
      myFillPolicy = FillPolicy.get(fill);
    }
  }

  @Nullable
  public ResizePolicy getResizePolicy() {
    return myResizePolicy;
  }

  public FillPolicy getFillPolicy() {
    return myFillPolicy;
  }
}
