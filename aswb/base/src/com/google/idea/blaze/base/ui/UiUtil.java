/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.ui;

import com.google.common.collect.Lists;
import com.intellij.util.ui.GridBag;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import javax.annotation.Nullable;
import javax.swing.Box;
import javax.swing.JComponent;

/** A collection of UI utility methods. */
public final class UiUtil {

  public static final int INSETS = 7;

  private UiUtil() {}

  public static Box createBox(Component... components) {
    return createBox(Lists.newArrayList(components));
  }

  /** Puts all the given components in order in a box, aligned left. */
  public static Box createBox(Iterable<? extends Component> components) {
    Box box = Box.createVerticalBox();
    box.setAlignmentX(0);
    for (Component component : components) {
      if (component instanceof JComponent) {
        ((JComponent) component).setAlignmentX(0);
      }
      box.add(component);
    }
    return box;
  }

  /** Puts all the given components in order in a horizontal box. */
  public static Box createHorizontalBox(int gap, Component... components) {
    return createHorizontalBox(gap, Lists.newArrayList(components));
  }

  public static Box createHorizontalBox(int gap, Iterable<Component> components) {
    Box box = Box.createHorizontalBox();
    for (Component component : components) {
      box.add(component);
      box.add(Box.createRigidArea(new Dimension(gap, 0)));
    }
    return box;
  }

  
  public static GridBag getLabelConstraints(int indentLevel) {
    Insets insets = new Insets(INSETS, INSETS + INSETS * indentLevel, 0, INSETS);
    return new GridBag().anchor(GridBagConstraints.WEST).weightx(0).insets(insets);
  }

  
  public static GridBag getFillLineConstraints(int indentLevel) {
    Insets insets = new Insets(INSETS, INSETS + INSETS * indentLevel, 0, INSETS);
    return new GridBag()
        .weightx(1)
        .coverLine()
        .fillCellHorizontally()
        .anchor(GridBagConstraints.WEST)
        .insets(insets);
  }

  public static void fillBottom(JComponent component) {
    component.add(
        Box.createVerticalGlue(), new GridBag().weightx(1).weighty(1).fillCell().coverLine());
  }

  public static void setEnabledRecursive(@Nullable Component component, boolean enabled) {
    if (component == null) {
      return;
    }
    component.setEnabled(enabled);
    if (component instanceof Container) {
      for (Component child : ((Container) component).getComponents()) {
        setEnabledRecursive(child, enabled);
      }
    }
  }

  public static void setPreferredWidth(JComponent component, int width) {
    int height = component.getPreferredSize().height;
    component.setPreferredSize(new Dimension(width, height));
  }
}
