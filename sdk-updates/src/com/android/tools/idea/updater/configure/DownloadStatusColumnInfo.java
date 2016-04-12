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
package com.android.tools.idea.updater.configure;

import com.intellij.icons.AllIcons;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;

/**
 * ColumnInfo showing an icon indicating whether a row has been selected for download or deletion.
 */
class DownloadStatusColumnInfo extends ColumnInfo<UpdaterTreeNode, Icon> {
  DownloadStatusColumnInfo() {
    super(" ");
  }

  @Override
  public int getWidth(JTable table) {
    return JBUI.scale(30);
  }

  @Nullable
  @Override
  public TableCellRenderer getRenderer(UpdaterTreeNode node) {
    DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
      @Override
      public void setText(String text) {
      }
    };
    IconInfo info = getIconInfo(node);
    renderer.setIcon(info.getIcon());
    renderer.getAccessibleContext().setAccessibleName(info.getName());
    return renderer;
  }

  private static class IconInfo {
    private final Icon myIcon;
    private final String myName;

    public static IconInfo Empty = new IconInfo(null, "Action: no change");

    public IconInfo(Icon icon, String name) {
      myIcon = icon;
      myName = name;
    }

    public Icon getIcon() {
      return myIcon;
    }

    public String getName() {
      return myName;
    }
  }

  @Override
  @Nullable
  public Icon valueOf(UpdaterTreeNode node) {
    return getIconInfo(node).getIcon();
  }

  @NotNull
  public IconInfo getIconInfo(UpdaterTreeNode node) {
    if (node == null || !node.isLeaf()) {
      return IconInfo.Empty;
    }
    if (node.getCurrentState() != node.getInitialState()) {
      if (node.getCurrentState() == NodeStateHolder.SelectedState.NOT_INSTALLED) {
        return new IconInfo(AllIcons.Actions.Delete, "Action: delete local files");
      }
      else if (node.getCurrentState() == NodeStateHolder.SelectedState.INSTALLED) {
        return new IconInfo(AllIcons.Actions.Download, "Action: download files locally");
      }
      else {
        assert false : "Invalid state selected: " + node.getCurrentState();
      }
    }
    return IconInfo.Empty;
  }
}
