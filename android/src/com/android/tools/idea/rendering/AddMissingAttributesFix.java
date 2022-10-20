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

package com.android.tools.idea.rendering;

import com.android.AndroidXConstants;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import java.util.ArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static com.android.SdkConstants.*;

public class AddMissingAttributesFix extends HtmlLinkManager.CommandLink {
  @NotNull private final XmlFile myFile;
  @Nullable private final ResourceResolver myResourceResolver;

  public AddMissingAttributesFix(@NotNull XmlFile file, @Nullable ResourceResolver resourceResolver) {
    super("Add Size Attributes", file);
    myFile = file;
    myResourceResolver = resourceResolver;
  }

  @NotNull
  public static List<SmartPsiElementPointer<XmlTag>> findViewsMissingSizes(@NotNull XmlFile file, @Nullable ResourceResolver resolver) {
    final List<SmartPsiElementPointer<XmlTag>> missing = new ArrayList<>();
    ApplicationManager.getApplication().runReadAction(() -> {
      Collection<XmlTag> xmlTags = PsiTreeUtil.findChildrenOfType(file, XmlTag.class);
      for (XmlTag tag : xmlTags) {
        if (requiresSize(tag)) {
          if (!definesWidth(tag, resolver) || !definesHeight(tag, resolver)) {
            missing.add(SmartPointerManager.createPointer(tag));
          }
        }
      }
    });

    return missing;
  }


  @Override
  public void run() {
    findViewsMissingSizes(myFile, myResourceResolver).stream()
                                                     .map(SmartPsiElementPointer::getElement)
                                                     .filter(Objects::nonNull)
                                                     .forEach(tag -> {
                                                       if (!definesWidth(tag, myResourceResolver)) {
                                                         tag.setAttribute(ATTR_LAYOUT_WIDTH, ANDROID_URI, getDefaultWidth(tag));
                                                       }
                                                       if (!definesHeight(tag, myResourceResolver)) {
                                                         tag.setAttribute(ATTR_LAYOUT_HEIGHT, ANDROID_URI, getDefaultHeight(tag));
                                                       }
                                                     });
  }

  public static boolean definesHeight(@NotNull XmlTag tag, @Nullable ResourceResolver resourceResolver) {
    XmlAttribute height = tag.getAttribute(ATTR_LAYOUT_HEIGHT, ANDROID_URI);
    boolean definesHeight = height != null;

    if (definesHeight) {
      String value = height.getValue();
      if (value == null || value.isEmpty()) {
        return false;
      }
      return value.equals(VALUE_WRAP_CONTENT) || value.equals(VALUE_FILL_PARENT) || value.equals(VALUE_MATCH_PARENT) ||
             value.startsWith(PREFIX_RESOURCE_REF) || value.startsWith(PREFIX_THEME_REF) || Character.isDigit(value.charAt(0));
    } else if (resourceResolver != null) {
      String style = tag.getAttributeValue(ATTR_STYLE);
      if (style != null) {
        ResourceValue st = resourceResolver.findResValue(style, false);
        if (st instanceof StyleResourceValue) {
          StyleResourceValue styleValue = (StyleResourceValue)st;
          definesHeight = resourceResolver.findItemInStyle(styleValue, ATTR_LAYOUT_HEIGHT, true) != null;
        }
      }
    }

    return definesHeight;
  }

  public static boolean definesWidth(@NotNull XmlTag tag, @Nullable ResourceResolver resourceResolver) {
    XmlAttribute width = tag.getAttribute(ATTR_LAYOUT_WIDTH, ANDROID_URI);
    boolean definesWidth = width != null;

    if (definesWidth) {
      String value = width.getValue();
      if (value == null || value.isEmpty()) {
        return false;
      }
      return value.equals(VALUE_WRAP_CONTENT) || value.equals(VALUE_FILL_PARENT) || value.equals(VALUE_MATCH_PARENT) ||
             value.startsWith(PREFIX_RESOURCE_REF) || value.startsWith(PREFIX_THEME_REF) || Character.isDigit(value.charAt(0));
    } else if (resourceResolver != null) {
      String style = tag.getAttributeValue(ATTR_STYLE);
      if (style != null) {
        ResourceValue st = resourceResolver.findResValue(style, false);
        if (st instanceof StyleResourceValue) {
          StyleResourceValue styleValue = (StyleResourceValue)st;
          definesWidth = resourceResolver.findItemInStyle(styleValue, ATTR_LAYOUT_WIDTH, true) != null;
        }
      }
    }

    return definesWidth;
  }

  @NotNull
  private static String getDefaultWidth(@NotNull XmlTag tag) {
    // Depends on parent and child. For now, just do wrap unless it's a layout
    //String tagName = tag.getName();

    // See Change-Id: I335a3bd8e2d7f7866692898ed73492635a5b61ea
    // For the platform layouts the default value is WRAP_CONTENT (and is
    // defined in the ViewGroup.LayoutParams class). The special cases
    // are accommodated in LayoutParams subclasses in the following cases:
    // Subclass                         width           height
    // FrameLayout.LayoutParams:        MATCH_PARENT,   MATCH_PARENT
    // TableLayout.LayoutParams:        MATCH_PARENT,   WRAP_CONTENT
    // TableRow.LayoutParams:           MATCH_PARENT,   WRAP_CONTENT
    XmlTag parentTag = getParentTag(tag);
    if (parentTag != null) {
      String parent = parentTag.getName();
      if (parent.equals(FRAME_LAYOUT) || parent.equals(TABLE_LAYOUT) || parent.equals(TABLE_ROW)) {
        return VALUE_MATCH_PARENT; // TODO: VALUE_FILL_PARENT?
      }
      // TODO: If custom view, check its parentage!
    }

    return VALUE_WRAP_CONTENT;
  }

  @NotNull
  private static String getDefaultHeight(@NotNull XmlTag tag) {
    // See #getDefaultWidth for a description of the defaults
    XmlTag parentTag = getParentTag(tag);
    if (parentTag != null) {
      String parent = parentTag.getName();
      if (parent.equals(FRAME_LAYOUT) ) {
        return VALUE_MATCH_PARENT;
      }
    }

    return VALUE_WRAP_CONTENT;
  }

  @Nullable
  private static XmlTag getParentTag(@NotNull XmlTag tag) {
    PsiElement parent = tag.getParent();
    if (parent instanceof XmlTag) {
      return (XmlTag)parent;
    }

    return null;
  }

  private static boolean requiresSize(XmlTag tag) {
    XmlTag parentTag = getParentTag(tag);
    if (parentTag != null) {
      String parentName = parentTag.getName();
      if (GRID_LAYOUT.equals(parentName) || AndroidXConstants.FQCN_GRID_LAYOUT_V7.isEquals(parentName)) {
        return false;
      }
    }

    String tagName = tag.getName();
    if (tagName.equals(REQUEST_FOCUS) || tagName.equals(VIEW_MERGE) || tagName.equals(VIEW_INCLUDE)) {
      return false;
    }

    // Data binding: these tags shouldn't specify width/height
    if (tagName.equals(TAG_LAYOUT)
        || tagName.equals(TAG_VARIABLE)
        || tagName.equals(TAG_DATA)
        || tagName.equals(TAG_IMPORT)) {
      return false;
    }

    return true;
  }
}
