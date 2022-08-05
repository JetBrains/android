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
package com.android.tools.idea.uibuilder.handlers.preference;

import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;
import static com.android.SdkConstants.ATTR_TITLE;

import com.android.tools.idea.common.api.DragType;
import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.common.command.NlWriteCommandActionUtil;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.uibuilder.api.DragHandler;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.XmlType;
import com.android.xml.XmlBuilder;
import java.util.List;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PreferenceCategoryHandler extends ViewGroupHandler {
  @Language("XML")
  @NotNull
  @Override
  public String getXml(@NotNull String tagName, @NotNull XmlType xmlType) {
    switch (xmlType) {
      case COMPONENT_CREATION:
        return new XmlBuilder()
          .startTag(tagName)
          .androidAttribute(ATTR_TITLE, "Preference category")
          .endTag(tagName)
          .toString();
      case PREVIEW_ON_PALETTE:
      case DRAG_PREVIEW:
        return NO_PREVIEW;
      default:
        throw new AssertionError(xmlType);
    }
  }

  @NotNull
  @Override
  public DragHandler createDragHandler(@NotNull ViewEditor editor,
                                       @NotNull SceneComponent preferenceCategory,
                                       @NotNull List<NlComponent > preferences,
                                       @NotNull DragType type) {
    return new PreferenceCategoryDragHandler(editor, this, preferenceCategory, preferences, type);
  }

  @Override
  public boolean onCreate(@Nullable NlComponent parent,
                          @NotNull NlComponent newChild,
                          @NotNull InsertType insertType) {
    if (insertType == InsertType.CREATE) {
      NlWriteCommandActionUtil.run(newChild, "Clearing Width and Height", () -> {
        newChild.removeAndroidAttribute(ATTR_LAYOUT_WIDTH);
        newChild.removeAndroidAttribute(ATTR_LAYOUT_HEIGHT);
      });
    }

    return true;
  }
}
