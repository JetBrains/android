/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.editors.theme.attributes.editors;

import com.android.SdkConstants;
import com.android.tools.idea.editors.theme.ThemeEditorContext;
import com.android.tools.idea.editors.theme.datamodels.ThemeEditorStyle;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.JBColor;
import com.intellij.ui.SimpleTextAttributes;
import java.awt.Component;
import javax.swing.JComboBox;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import javax.swing.ListCellRenderer;
import javax.swing.JList;
import javax.swing.JSeparator;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link ListCellRenderer} to render {@link ThemeEditorStyle} elements.
 */
public class StyleListCellRenderer extends ColoredListCellRenderer {
  private final ThemeEditorContext myContext;

  public StyleListCellRenderer(@NotNull ThemeEditorContext context, @Nullable JComboBox comboBox) {
    super(comboBox);
    myContext = context;
  }

  @Override
  public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
    if (value instanceof JSeparator){
      return (JSeparator)value;
    }
    return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
  }

  @Override
  protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
    if (value instanceof String) {
      append((String)value, SimpleTextAttributes.REGULAR_ATTRIBUTES, true);
      return;
    }

    if (value instanceof ThemeEditorStyle) {
      ThemeEditorStyle style = (ThemeEditorStyle)value;
      ThemeEditorStyle parent = style.getParent();
      String styleName = style.getName();
      String parentName = parent != null ? parent.getName() : null;

      String defaultAppTheme = null;
      final AndroidFacet facet = AndroidFacet.getInstance(myContext.getCurrentContextModule());
      if (facet != null) {
        Manifest manifest = facet.getManifest();
        if (manifest != null && manifest.getApplication() != null && manifest.getApplication().getXmlTag() != null) {
          defaultAppTheme = manifest.getApplication().getXmlTag().getAttributeValue(SdkConstants.ATTR_THEME, SdkConstants.ANDROID_URI);
        }
      }

      if (!style.isProjectStyle()) {
        String simplifiedName = simplifyName(style);
        if (StringUtil.isEmpty(simplifiedName)) {
          append(styleName, SimpleTextAttributes.REGULAR_ATTRIBUTES, true);
        }
        else {
          append(simplifiedName, SimpleTextAttributes.REGULAR_ATTRIBUTES, true);
          append(" [" + styleName + "]", SimpleTextAttributes.GRAY_ATTRIBUTES, false);
        }
      }
      else if (!selected && parentName != null && styleName.startsWith(parentName + ".")) {
        append(parentName + ".", SimpleTextAttributes.GRAY_ATTRIBUTES, false);
        append(styleName.substring(parentName.length() + 1), SimpleTextAttributes.REGULAR_ATTRIBUTES, true);
      }
      else {
        append(styleName, SimpleTextAttributes.REGULAR_ATTRIBUTES, true);
      }

      if (style.getQualifiedName().equals(defaultAppTheme)) {
        append("  -  Default", new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, new JBColor(0xFF4CAF50, 0xFFA5D6A7)), true);
      }
    }
  }

  /**
   * Returns a more user-friendly version of a given themeName.
   * Aimed at framework themes with names of the form Theme.*.Light.*
   * or Theme.*.*
   */
  @NotNull
  private static String simplifyName(@NotNull ThemeEditorStyle theme) {
    String result;
    String name = theme.getQualifiedName();
    String[] pieces = name.split("\\.");
    if (pieces.length > 1 && !"Light".equals(pieces[1])) {
      result = pieces[1];
    }
    else {
      result = "Theme";
    }
    ThemeEditorStyle parent = theme;
    while (parent != null) {
      if ("Theme.Light".equals(parent.getName())) {
        return result + " Light";
      }
      else {
        parent = parent.getParent();
      }
    }
    return result + " Dark";
  }
}
