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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies;

import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.ui.TreeUIHelper;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.TreeSelectionModel;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

import static com.intellij.openapi.util.SystemInfo.isMac;
import static com.intellij.ui.ScrollPaneFactory.createScrollPane;
import static com.intellij.util.BitUtil.isSet;
import static java.awt.Event.CTRL_MASK;
import static java.awt.Event.META_MASK;
import static java.awt.event.KeyEvent.VK_CONTROL;
import static java.awt.event.KeyEvent.VK_META;
import static javax.swing.tree.TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION;

public final class UiUtil {
  private UiUtil() {
  }

  @NotNull
  public static JBSplitter createMainVerticalSplitter() {
    return new OnePixelSplitter(false, "psd.dependencies.main.vertical.splitter.proportion", .75f);
  }

  @NotNull
  public static JScrollPane setUp(@NotNull Tree tree) {
    tree.setExpandsSelectedPaths(true);
    tree.setRootVisible(false);
    TreeSelectionModel selectionModel = tree.getSelectionModel();
    selectionModel.setSelectionMode(DISCONTIGUOUS_TREE_SELECTION);

    TreeUIHelper.getInstance().installTreeSpeedSearch(tree, path -> {
      Object last = path.getLastPathComponent();
      return last != null ? last.toString() : "";
    }, true);

    JScrollPane scrollPane = createScrollPane(tree);
    scrollPane.setBorder(IdeBorderFactory.createEmptyBorder());
    return scrollPane;
  }

  public static boolean isMetaOrCtrlKeyPressed(@NotNull KeyEvent e) {
    int keyCode = e.getKeyCode();
    if (isMac) {
      return keyCode == VK_META;
    }
    return keyCode == VK_CONTROL;
  }

  public static boolean isMetaOrCtrlKeyPressed(@NotNull MouseEvent e) {
    int modifiers = e.getModifiers();
    if (isMac) {
      return isSet(modifiers, META_MASK);
    }
    return isSet(modifiers, CTRL_MASK);
  }
}
