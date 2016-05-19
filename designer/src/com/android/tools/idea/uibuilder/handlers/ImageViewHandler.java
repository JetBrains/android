/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers;

import com.android.resources.ResourceType;
import com.android.tools.idea.uibuilder.api.*;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.google.common.collect.ImmutableList;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

import static com.android.SdkConstants.*;

/**
 * Handler for the {@code <ImageView>} widget
 */
public class ImageViewHandler extends ViewHandler {
  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(
      ATTR_SRC,
      ATTR_CONTENT_DESCRIPTION,
      ATTR_BACKGROUND,
      ATTR_SCALE_TYPE,
      ATTR_ADJUST_VIEW_BOUNDS,
      ATTR_CROP_TO_PADDING);
  }

  @Override
  @NotNull
  @Language("XML")
  public String getXml(@NotNull String tagName, @NotNull XmlType xmlType) {
    return new XmlBuilder()
      .startTag(tagName)
      .androidAttribute(ATTR_SRC, getSampleImageSrc())
      .androidAttribute(ATTR_LAYOUT_WIDTH, VALUE_WRAP_CONTENT)
      .androidAttribute(ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT)
      .endTag(tagName)
      .toString();
  }

  @Override
  public boolean onCreate(@NotNull ViewEditor editor,
                          @Nullable NlComponent parent,
                          @NotNull NlComponent newChild,
                          @NotNull InsertType insertType) {
    if (insertType == InsertType.CREATE) { // NOT InsertType.CREATE_PREVIEW
      String src = editor.displayResourceInput(EnumSet.of(ResourceType.DRAWABLE));
      if (src != null) {
        newChild.setAttribute(ANDROID_URI, ATTR_SRC, src);
        return true;
      }
      else {
        // Remove the view; the insertion was canceled
        return false;
      }
    }

    // Fallback if dismissed or during previews etc
    if (insertType.isCreate()) {
      newChild.setAttribute(ANDROID_URI, ATTR_SRC, getSampleImageSrc());
    }

    return true;
  }

  /**
   * Returns a source attribute value which points to a sample image. This is typically
   * used to provide an initial image shown on ImageButtons, etc. There is no guarantee
   * that the source pointed to by this method actually exists.
   *
   * @return a source attribute to use for sample images, never null
   */
  @NotNull
  public String getSampleImageSrc() {
    // Builtin graphics available since v1:
    return "@android:drawable/btn_star"; //$NON-NLS-1$
  }
}
