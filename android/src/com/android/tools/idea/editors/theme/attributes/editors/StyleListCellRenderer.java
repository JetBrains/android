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
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * A {@link ListCellRenderer} to render {@link ThemeEditorStyle} elements.
 */
public class StyleListCellRenderer extends JPanel implements ListCellRenderer {
  private final ThemeEditorContext myContext;
  private final SimpleColoredComponent myStyleNameLabel = new SimpleColoredComponent();
  private final SimpleColoredComponent myDefaultLabel = new SimpleColoredComponent();

  public StyleListCellRenderer(ThemeEditorContext context) {
    myContext = context;

    setLayout(new BoxLayout(this, BoxLayout.LINE_AXIS));

    myStyleNameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
    myDefaultLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
    myDefaultLabel.append("DEFAULT", SimpleTextAttributes.GRAY_ATTRIBUTES);
    myDefaultLabel.setTextAlign(SwingConstants.RIGHT);

    add(myStyleNameLabel);
    add(Box.createHorizontalGlue());
    add(myDefaultLabel);
  }

  @Override
  @Nullable
  public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
    if (value instanceof JSeparator) {
      return (JSeparator)value;
    }

    if (isSelected) {
      setBackground(list.getSelectionBackground());
      myStyleNameLabel.setForeground(list.getSelectionForeground());
      myDefaultLabel.setForeground(list.getSelectionForeground());
    } else {
      setBackground(list.getBackground());
      myStyleNameLabel.setForeground(list.getForeground());
      myDefaultLabel.setForeground(list.getForeground());
    }

    myStyleNameLabel.clear();

    if (value instanceof String) {
      myStyleNameLabel.append((String)value);
      myDefaultLabel.setVisible(false);
      return this;
    }
    if (!(value instanceof ThemeEditorStyle)) {
      return null;
    }

    ThemeEditorStyle style = (ThemeEditorStyle)value;
    ThemeEditorStyle parent = style.getParent();
    String styleName = style.getName();
    String parentName = parent != null ? parent.getName() : null;

    String defaultAppTheme = null;
    final AndroidFacet facet = AndroidFacet.getInstance(myContext.getCurrentContextModule());
    if (facet != null) {
      Manifest manifest = facet.getManifest();
      if (manifest != null && manifest.getApplication() != null && manifest.getApplication().getXmlTag() != null) {
        defaultAppTheme = manifest.getApplication()
          .getXmlTag().getAttributeValue(SdkConstants.ATTR_THEME, SdkConstants.ANDROID_URI);
      }
    }

    if (!style.isProjectStyle()) {
      String simplifiedName = simplifyName(style);
      if (StringUtil.isEmpty(simplifiedName)) {
        myStyleNameLabel.append(styleName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
      else {
        myStyleNameLabel.append(simplifiedName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        myStyleNameLabel.append(" [" + styleName + "]", SimpleTextAttributes.GRAY_ATTRIBUTES);
      }
    }
    else if (!isSelected && parentName != null && styleName.startsWith(parentName + ".")) {
      myStyleNameLabel.append(parentName + ".", SimpleTextAttributes.GRAY_ATTRIBUTES);
      myStyleNameLabel.append(styleName.substring(parentName.length() + 1), SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }
    else {
      myStyleNameLabel.append(styleName, SimpleTextAttributes.REGULAR_ATTRIBUTES);
    }

    myDefaultLabel.setVisible(style.getQualifiedName().equals(defaultAppTheme));

    return this;
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
