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

import com.android.tools.idea.ui.MultilineColoredTreeCellRenderer;
import com.intellij.icons.AllIcons;
import com.intellij.ide.errorTreeView.*;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Consumer;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
* Renders elements in the "Messages" window. This renderer does not add the message type as a prefix (e.g. "Information:");
*/
class MessageTreeRenderer extends MultilineColoredTreeCellRenderer {

  private static final Pattern ourLinkPattern = Pattern.compile("<a\\s+href=['\"]([^'\"]+)['\"]>(.+?)</a>");

  @Override
  protected void initComponent(@NotNull JTree tree,
                               Object value,
                               boolean selected,
                               boolean expanded,
                               boolean leaf,
                               int row,
                               boolean hasFocus)
  {
    ErrorTreeElement element = getElement(value);

    if (element != null) {
      String[] text = element.getText();
      if (text == null) {
        text = ArrayUtil.EMPTY_STRING_ARRAY;
      }
      if (text.length > 0 && text[0] == null) {
        text[0] = "";
      }

      // Check for html hyperlinks declaration at the given text and configure them to be shown accordingly.

      final Consumer<String> hyperlinkAction;
      if (element instanceof SimpleMessageElement && element.getData() instanceof Consumer) {
        // We perform IS-A SimpleMessageElement check here because NavigatableMessageElement.getData() throws an NPE easily.
        //noinspection unchecked
        hyperlinkAction = (Consumer<String>)element.getData();
      }
      else {
        hyperlinkAction = null;
      }

      int start;
      for (int i = 0; i < text.length; i++) {
        String line = text[i];
        Matcher matcher = ourLinkPattern.matcher(line);
        start = 0;
        while (start < line.length()) {
          boolean matched = matcher.find(start);
          if (matched) {
            if (matcher.start() > start) {
              append(line.substring(start, matcher.start()));
            }
            final String href = matcher.group(1);
            append(matcher.group(2), SimpleTextAttributes.LINK_ATTRIBUTES, hyperlinkAction == null ? null : new Runnable() {
              @Override
              public void run() {
                hyperlinkAction.consume(href);
              }
            });
            start = matcher.end();
          }
          else {
            if (start < line.length()) {
              append(line.substring(start));
            }
            break;
          }
        }
        if (i < text.length - 1) {
          appendLineBreak();
        }
      }
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
