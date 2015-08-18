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
import org.jetbrains.annotations.Nullable;

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
    return 30;
  }

  @Nullable
  @Override
  public TableCellRenderer getRenderer(UpdaterTreeNode node) {
    DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
      @Override
      public void setText(String text) {
      }
    };
    renderer.setIcon(valueOf(node));
    return renderer;
  }

  @Override
  @Nullable
  public Icon valueOf(UpdaterTreeNode node) {
    if (!node.isLeaf()) {
      return null;
    }
    if (node.getCurrentState() != node.getInitialState()) {
      if (node.getCurrentState() == NodeStateHolder.SelectedState.NOT_INSTALLED) {
        return AllIcons.Actions.Delete;
      }
      else if (node.getCurrentState() == NodeStateHolder.SelectedState.INSTALLED) {
        return AllIcons.Actions.Download;
      }
      else {
        assert false : "Invalid state selected: " + node.getCurrentState();
      }
    }
    return null;
  }
}
