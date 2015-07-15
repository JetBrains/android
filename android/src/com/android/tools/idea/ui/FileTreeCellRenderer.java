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
package com.android.tools.idea.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.PlatformIcons;

import javax.swing.*;

/**
 * Custom tree cell renderer for displaying a {@link FileTreeModel}
 */
public class FileTreeCellRenderer extends ColoredTreeCellRenderer {

  @Override
  public void customizeCellRenderer(JTree tree, Object nodeValue, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    if (nodeValue == null) {
      return;
    }

    final FileTreeModel.Node node = (FileTreeModel.Node)nodeValue;

    if (node.existsOnDisk) {
      if (node.isConflicted) {
        append(node.name, SimpleTextAttributes.ERROR_ATTRIBUTES);
      } else {
        append(node.name, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      }
    } else {
      append(node.name, SimpleTextAttributes.REGULAR_ITALIC_ATTRIBUTES);
    }

    if (node.children.isEmpty()) {
      if (node.icon != null) {
        setIcon(node.icon);
      } else {
        setIcon(AllIcons.FileTypes.Any_type);
      }
    } else {
      setIcon(PlatformIcons.FOLDER_ICON);
    }
  }
}
