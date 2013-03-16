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

import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

import static com.android.SdkConstants.*;

public class AddMissingAttributesFix extends WriteCommandAction<Void> {
  @NotNull private final XmlFile myFile;

  public AddMissingAttributesFix(@NotNull Project project, @NotNull XmlFile file) {
    super(project, "Add Size Attributes", file);
    myFile = file;
  }

  @NotNull
  public List<XmlTag> findViewsMissingSizes() {
    final List<XmlTag> missing = Lists.newArrayList();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        @SuppressWarnings("unchecked")
        Collection<XmlTag> xmlTags = PsiTreeUtil.collectElementsOfType(myFile, XmlTag.class);
        for (XmlTag tag : xmlTags) {
          if (requiresSize(tag)) {
            if (!definesWidth(tag) || !definesHeight(tag)) {
              missing.add(tag);
            }
          }
        }
      }
    });

    return missing;
  }


  @Override
  protected void run(Result<Void> result) throws Throwable {
    final List<XmlTag> missing = findViewsMissingSizes();
    for (XmlTag tag : missing) {
      if (!definesWidth(tag)) {
        tag.setAttribute(ATTR_LAYOUT_WIDTH, ANDROID_URI, getDefaulWidth(tag));
      }
      if (!definesHeight(tag)) {
        tag.setAttribute(ATTR_LAYOUT_HEIGHT, ANDROID_URI, getDefaultHeight(tag));
      }
    }
  }

  public static boolean definesHeight(XmlTag tag) {
    return tag.getAttribute(ATTR_LAYOUT_HEIGHT, ANDROID_URI) != null;
  }

  public static boolean definesWidth(XmlTag tag) {
    return tag.getAttribute(ATTR_LAYOUT_WIDTH, ANDROID_URI) != null;
  }

  @NotNull
  private static String getDefaulWidth(@NotNull XmlTag tag) {
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
      if (GRID_LAYOUT.equals(parentName) || FQCN_GRID_LAYOUT_V7.equals(parentName)) {
        return false;
      }

      if (parentName.equals(REQUEST_FOCUS) || parentName.equals(VIEW_FRAGMENT) || parentName.equals(VIEW_MERGE)) {
        return false;
      }
      // TODO: What are the other exceptions?

      if (parentName.equals(VIEW_INCLUDE)) {
        return false;
      }
    }

    return true;
  }
}
