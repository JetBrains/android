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
package com.android.tools.idea.gradle.structure.configurables.ui;

import static com.intellij.openapi.util.SystemInfo.isMac;
import static com.intellij.ui.ScrollPaneFactory.createScrollPane;
import static com.intellij.util.BitUtil.isSet;
import static java.awt.Event.CTRL_MASK;
import static java.awt.Event.META_MASK;
import static java.awt.event.KeyEvent.VK_CONTROL;
import static java.awt.event.KeyEvent.VK_META;
import static javax.swing.tree.TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION;

import com.intellij.ui.TreeSpeedSearch;
import com.intellij.util.ui.JBUI;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.TreeSelectionModel;
import org.jetbrains.annotations.NotNull;

public final class UiUtil {
  private UiUtil() {
  }

  public static void revalidateAndRepaint(@NotNull JComponent c) {
    c.doLayout();
    c.revalidate();
    c.repaint();
  }

  @NotNull
  public static JScrollPane setUp(@NotNull JTree tree, @NotNull String name) {
    tree.setExpandsSelectedPaths(true);
    tree.setRootVisible(false);
    tree.setName(name);
    TreeSelectionModel selectionModel = tree.getSelectionModel();
    selectionModel.setSelectionMode(DISCONTIGUOUS_TREE_SELECTION);

    new TreeSpeedSearch(tree);

    JScrollPane scrollPane = createScrollPane(tree);
    scrollPane.setBorder(JBUI.Borders.empty());
    return scrollPane;
  }

  public static boolean isMetaOrCtrlKeyPressed(@NotNull KeyEvent e) {
    return e.getKeyCode() == (isMac ? VK_META : VK_CONTROL);
  }

  public static boolean isMetaOrCtrlKeyPressed(@NotNull MouseEvent e) {
    int modifiers = e.getModifiers();
    return isSet(modifiers, isMac ? META_MASK : CTRL_MASK);
  }
}
