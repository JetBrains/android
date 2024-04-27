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
package com.android.tools.idea.actions;

import com.intellij.ui.IconManager;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

enum Kind {
  ANNOTATION("Annotation", PlatformIcons.ANNOTATION_TYPE_ICON, "AnnotationType"),
  CLASS("Class", IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Class)),
  ENUM("Enum", PlatformIcons.ENUM_ICON),
  INTERFACE("Interface", PlatformIcons.INTERFACE_ICON);

  private final String myName;
  private final Icon myIcon;
  private final String myTemplateName;

  Kind(String name, Icon icon) {
    this(name, icon, name);
  }

  Kind(String name, Icon icon, String templateName) {
    myName = name;
    myIcon = icon;
    myTemplateName = templateName;
  }

  String getName() {
    return myName;
  }

  Icon getIcon() {
    return myIcon;
  }

  String getTemplateName() {
    return myTemplateName;
  }

  @Nullable
  static Kind valueOfText(String text) {
    for (Kind kind : values()) {
      if (kind.getTemplateName().equals(text)) {
        return kind;
      }
    }

    return null;
  }
}
