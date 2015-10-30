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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.idea.uibuilder.api.InsertType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.api.XmlType;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.google.common.collect.Sets;
import com.intellij.openapi.util.text.StringUtil;
import icons.AndroidIcons;
import org.intellij.lang.annotations.Language;

import javax.swing.*;

import static com.android.SdkConstants.*;

/**
 * Handler for the {@code <fragment>} tag
 */
public final class FragmentHandler extends ViewHandler {

  @Override
  @NonNull
  public String getTitle(@NonNull String tagName) {
    return "<fragment>";
  }

  @Override
  @NonNull
  public String getTitle(@NonNull NlComponent component) {
    return "<fragment>";
  }

  @NonNull
  @Override
  public String getTitleAttributes(@NonNull NlComponent component) {
    String name = component.getAttribute(ANDROID_URI, ATTR_NAME);
    return StringUtil.isEmpty(name) ? "" : "- " + name;
  }

  @Override
  @NonNull
  public Icon getIcon(@NonNull String tagName) {
    return AndroidIcons.Views.Fragment;
  }

  @Override
  @NonNull
  public Icon getIcon(@NonNull NlComponent component) {
    return AndroidIcons.Views.Fragment;
  }

  @Override
  @Language("XML")
  @NonNull
  public String getXml(@NonNull String tagName, @NonNull XmlType xmlType) {
    switch (xmlType) {
      case PREVIEW_ON_PALETTE:
      case DRAG_PREVIEW:
        return NO_PREVIEW;
      default:
        return super.getXml(tagName, xmlType);
    }
  }

  @Override
  public boolean onCreate(@NonNull ViewEditor editor,
                          @Nullable NlComponent parent,
                          @NonNull NlComponent newChild,
                          @NonNull InsertType insertType) {
    if (insertType == InsertType.CREATE) { // NOT InsertType.CREATE_PREVIEW
      if (newChild.getAttribute(ANDROID_URI, ATTR_NAME) != null) {
        return true;
      }
      String src = editor.displayClassInput(Sets.newHashSet(CLASS_FRAGMENT, CLASS_V4_FRAGMENT), null);
      if (src != null) {
        newChild.setAttribute(ANDROID_URI, ATTR_NAME, src);
        return true;
      }
      else {
        // Remove the view; the insertion was canceled
        return false;
      }
    }
    return true;
  }
}
