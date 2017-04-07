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
package com.android.tools.idea.uibuilder.handlers.menu;

import com.android.resources.ResourceType;
import com.android.tools.idea.uibuilder.api.InsertType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.model.NlAttributesHolder;
import com.android.tools.idea.uibuilder.model.NlComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.SdkConstants.ATTR_ICON;
import static com.android.SdkConstants.DRAWABLE_PREFIX;

final class SearchItemHandler extends MenuHandler {
  private static final String SEARCH_ICON = "ic_search_black_24dp";

  static boolean handles(@NotNull NlAttributesHolder item) {
    return (DRAWABLE_PREFIX + SEARCH_ICON).equals(item.getAndroidAttribute(ATTR_ICON));
  }

  @Override
  public boolean onCreate(@NotNull ViewEditor editor,
                          @Nullable NlComponent parent,
                          @NotNull NlComponent newChild,
                          @NotNull InsertType type) {
    if (!super.onCreate(editor, parent, newChild, type)) {
      return false;
    }

    if (type.equals(InsertType.CREATE)) {
      if (editor.getMinSdkVersion().getApiLevel() < 11) {
        newChild.setAndroidAttribute("actionViewClass", "android.support.v7.widget.SearchView");
      }
      else {
        newChild.setAndroidAttribute("actionViewClass", "android.widget.SearchView");
      }

      if (!editor.moduleContainsResource(ResourceType.DRAWABLE, SEARCH_ICON)) {
        editor.copyVectorAssetToMainModuleSourceSet(SEARCH_ICON);
      }
    }

    return true;
  }
}
