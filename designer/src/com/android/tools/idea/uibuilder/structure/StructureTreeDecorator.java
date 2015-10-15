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
package com.android.tools.idea.uibuilder.structure;

import com.android.annotations.NonNull;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.palette.NlPaletteItem;
import com.android.tools.idea.uibuilder.palette.NlPaletteModel;
import com.android.tools.lint.detector.api.LintUtils;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import icons.AndroidIcons;

import javax.swing.*;

import static com.android.SdkConstants.*;

public class StructureTreeDecorator {
  private final NlPaletteModel myPaletteModel;

  private static StructureTreeDecorator ourInstance;

  @NonNull
  public static StructureTreeDecorator get() {
    if (ourInstance == null) {
      ourInstance = new StructureTreeDecorator();
    }
    return ourInstance;
  }

  private StructureTreeDecorator() {
    myPaletteModel = NlPaletteModel.get();
  }

  public void decorate(@NonNull NlComponent component, @NonNull SimpleColoredComponent renderer, boolean full) {
    String id = component.getId();
    id = LintUtils.stripIdPrefix(id);
    id = StringUtil.nullize(id);

    String tagName = component.getTagName();
    NlPaletteItem item = myPaletteModel.getItemByTagName(component.getTagName());
    if (item == null) {
      item = myPaletteModel.getItemByTagName(VIEW_TAG);
    }
    String type = null;
    if (item != null) {
      type = item.getStructureTitle();

      // Don't display <Fragment> etc for special XML tags like <requestFocus>
      if (tagName.equals(VIEW_INCLUDE) ||
          tagName.equals(VIEW_MERGE) ||
          tagName.equals(VIEW_FRAGMENT) ||
          tagName.equals(REQUEST_FOCUS)) {
        type = null;
      }
    }

    if (id != null) {
      renderer.append(id, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    }

    if (id == null && type == null)  {
      type = tagName;
    }

    //todo: Add this special case later:
    // For the root node, show the including layout when rendering in included contexts
    //if (ROOT_NODE_TAG.equals(tagName)) {
    //  IncludeReference includeContext = component.getClientProperty(ATTR_RENDER_IN);
    //  if (includeContext != null && includeContext != IncludeReference.NONE) {
    //    type = "Shown in " + includeContext.getFromResourceUrl();
    //  }
    //}

    // Don't display the type if it's obvious from the id (e.g.
    // if the id is button1, don't display (Button) as the type)
    if (type != null && (id == null || !StringUtil.startsWithIgnoreCase(id, type))) {
      renderer.append(id != null ? String.format(" (%1$s)", type) : type, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }

    // Display typical arguments
    StringBuilder fullTitle = new StringBuilder();
    if (item != null && item.getStructureFormat() != null) {
      String format = item.getStructureFormat();
      int start = format.indexOf('%');
      if (start != -1) {
        int end = format.indexOf('%', start + 1);
        if (end != -1) {
          String variable = format.substring(start + 1, end);

          String value = component.getAttribute(ANDROID_URI, variable);
          if (!StringUtil.isEmpty(value)) {
            value = StringUtil.shortenTextWithEllipsis(value, 30, 5);
          }

          if (!StringUtil.isEmpty(value)) {
            String prefix = format.substring(0, start);
            String suffix = format.substring(end + 1);
            if ((value.startsWith(PREFIX_RESOURCE_REF) || value.startsWith(PREFIX_THEME_REF))
                && prefix.length() > 0 && suffix.length() > 0 &&
                prefix.charAt(prefix.length() - 1) == '"' &&
                suffix.charAt(0) == '"') {
              // If the value is a resource, don't surround it with quotes
              prefix = prefix.substring(0, prefix.length() - 1);
              suffix = suffix.substring(1);
            }
            fullTitle.append(prefix).append(value).append(suffix);
          }
        }
      }
    }

    if (fullTitle.length() > 0) {
      renderer.append(fullTitle.toString(), SimpleTextAttributes.GRAY_ATTRIBUTES);
    }

    if (full && item != null) {
      //todo: Annotate icons with lint warnings or errors, if applicable
      Icon icon = item.getIcon();
      if (tagName.equals(LINEAR_LAYOUT) && VALUE_VERTICAL.equals(component.getAttribute(ANDROID_URI, ATTR_ORIENTATION))) {
        icon = AndroidIcons.Views.VerticalLinearLayout;
      }
      renderer.setIcon(icon);
    }
  }
}
