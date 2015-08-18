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
package com.android.tools.idea.gradle.invoker.messages;

import com.intellij.icons.AllIcons;
import com.intellij.ide.errorTreeView.*;
import com.intellij.ui.MultilineTreeCellRenderer;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

import static com.intellij.util.ArrayUtil.EMPTY_STRING_ARRAY;

/**
* Renders elements in the "Messages" window. This renderer does not add the message type as a prefix (e.g. "Information:");
*/
class MessageTreeRenderer extends MultilineTreeCellRenderer {
  @Override
  protected void initComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
    ErrorTreeElement element = getElement(value);

    if (element != null) {
      String[] text = element.getText();
      if (text == null) {
        text = EMPTY_STRING_ARRAY;
      }
      if (text.length > 0 && text[0] == null) {
        text[0] = "";
      }
      setText(text, "");
    }

    Icon icon = null;

    if (element instanceof GroupingElement) {
      GroupingElement groupingElement = (GroupingElement)element;
      icon = groupingElement.getFile() != null ? groupingElement.getFile().getFileType().getIcon() : AllIcons.FileTypes.Java;
    }
    else if (element instanceof SimpleMessageElement || element instanceof NavigatableMessageElement) {
      ErrorTreeElementKind kind = element.getKind();
      icon = getIconFor(kind);
    }
    if (icon == null) {
      icon = EmptyIcon.ICON_16;
    }
    setIcon(icon);
  }

  @NotNull
  private static Icon getIconFor(@NotNull ErrorTreeElementKind kind) {
    switch (kind) {
      case ERROR:
        return AllIcons.General.BalloonError;
      case WARNING:
        return AllIcons.General.BalloonWarning;
      case INFO:
        return AllIcons.General.BalloonInformation;
      default:
        return EmptyIcon.ICON_16;
    }
  }

  @Nullable
  private static ErrorTreeElement getElement(@Nullable Object value) {
    if (value instanceof DefaultMutableTreeNode) {
      Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
      if (userObject instanceof ErrorTreeNodeDescriptor) {
        return ((ErrorTreeNodeDescriptor)userObject).getElement();
      }
    }
    return null;
  }
}
